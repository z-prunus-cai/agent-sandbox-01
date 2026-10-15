// deployChecks：在部署流水线中按需运行 Ansible 检查（部署前 / 部署后）。
//
// 用法（详见 examples/deploy-checks/Jenkinsfile）：
//
//   def plan = deployChecks.resolve(
//       catalogFile: 'examples/deploy-checks/checks/catalog.yaml',
//       pre: params.PRE_CHECKS, post: params.POST_CHECKS,
//       params: params.CHECK_PARAMS, plan: params.CHECK_PLAN)
//   deployChecks.runPhase(phase: 'pre', checks: plan.pre, results: results,
//       ansible: [inventory: 'hosts.ini', credentialsId: 'ssh-key'],
//       context: [deploy_version: params.APP_VERSION])
//   deployChecks.report(results)
//
// 每个检查是一次独立的 ansiblePlaybook 调用，失败后的处理由检查的 policy 决定：
//   abort  阻塞：停止流水线（stage 与构建为 FAILURE）
//   warn   非阻塞：stage 与构建标记为 UNSTABLE，继续执行
//   ignore 非阻塞：只记录结果，不改变构建状态
//   ask    暂停等待人工决定：继续（标记 UNSTABLE）或中止

import groovy.json.JsonOutput

import hudson.AbortException
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

@groovy.transform.Field
static final List POLICIES = ['abort', 'warn', 'ignore', 'ask']

@groovy.transform.Field
static final List ENTRY_KEYS = ['name', 'id', 'policy', 'params', 'limit', 'timeout_minutes']

// 解析本次部署要运行的检查，返回 [pre: [...], post: [...]]。
// 计划有问题（未知检查名、未知参数、缺少必填参数等）时直接让构建失败，
// 所以应在部署开始前调用。
//
// 参数：
//   catalogFile  检查目录文件（YAML）
//   pre / post   简写形式的检查列表，如 "disk_space, service_running:warn"；
//                留空使用目录中的 defaults，填 none 表示不运行
//   params       YAML 文本，按检查名覆盖参数，如 "disk_space: {min_free_mb: 2048}"
//   plan         YAML 文本，完整计划；非空时忽略 pre / post / params
Map resolve(Map args) {
    Map catalog = readYaml(file: args.catalogFile) as Map
    String planText = args.plan?.toString()?.trim()
    Map plan
    try {
        Map rawPlan
        if (planText) {
            if (args.pre?.toString()?.trim() || args.post?.toString()?.trim() || args.params?.toString()?.trim()) {
                echo '[deployChecks] 已提供 CHECK_PLAN，忽略 PRE_CHECKS / POST_CHECKS / CHECK_PARAMS'
            }
            rawPlan = requireMap(readYaml(text: planText), 'CHECK_PLAN')
        } else {
            String paramsText = args.params?.toString()?.trim()
            Map overrides = paramsText ? requireMap(readYaml(text: paramsText), 'CHECK_PARAMS') : [:]
            rawPlan = shorthandPlan(args.pre?.toString(), args.post?.toString(), overrides, catalog)
        }
        plan = buildPlan(rawPlan, catalog)
    } catch (IllegalArgumentException e) {
        error "[deployChecks] ${e.message}"
    }
    echo describe(plan)
    return plan
}

// 依次运行某个阶段（pre 或 post）的检查，每个检查一个 stage。
//
// 参数：
//   phase    'pre' 或 'post'，只用于显示
//   checks   resolve() 返回的 plan.pre 或 plan.post
//   results  List，每个检查的结果会追加到这里，供 report() 使用；
//            即使 abort 策略中止了流水线，已运行检查的结果也会保留
//   ansible  传给 ansiblePlaybook 的公共参数（inventory、credentialsId、installation 等）
//   context  额外传给每个检查的变量，如 [deploy_version: '1.2.3']
//   askTimeoutMinutes  ask 策略等待人工决定的最长时间，默认 30，超时视为中止
void runPhase(Map args) {
    List checks = args.checks ?: []
    List results = args.results
    if (results == null) {
        error '[deployChecks] runPhase() 需要传入 results 列表'
    }
    if (checks.isEmpty()) {
        echo "[deployChecks] ${args.phase}: 本次部署未选择任何检查"
        return
    }
    for (int i = 0; i < checks.size(); i++) {
        Map check = checks[i]
        stage("${args.phase}: ${check.id}") {
            runOne(check, args.ansible ?: [:], args.context ?: [:], results,
                    (args.askTimeoutMinutes ?: 30) as int)
        }
    }
}

// 打印检查结果汇总，并把结果写入 deploy-checks-report.json 归档。
void report(List results) {
    if (!results) {
        return
    }
    echo formatReport(results)
    writeFile file: 'deploy-checks-report.json', text: JsonOutput.prettyPrint(JsonOutput.toJson(results))
    archiveArtifacts artifacts: 'deploy-checks-report.json', allowEmptyArchive: true
}

private void runOne(Map check, Map ansibleArgs, Map context, List results, int askTimeoutMinutes) {
    echo "[deployChecks] ${check.id}（${check.description}）策略=${check.policy} 参数=${JsonOutput.toJson(check.params)}"

    // 参数写进 JSON 文件再用 -e @file 传给 Ansible：能保留数字、布尔、列表等类型。
    // ansiblePlaybook 的 extraVars 只支持标量，并且会把数字转成字符串。
    String varsFile = ".deploy-checks/${check.phase}-${check.id}.json"
    Map vars = [:]
    vars.putAll(context)
    vars.putAll(check.params)
    vars.check_id = check.id
    vars.check_phase = check.phase
    writeFile file: varsFile, text: JsonOutput.toJson(vars)

    Map playbookArgs = [:]
    playbookArgs.putAll(ansibleArgs)
    playbookArgs.playbook = check.playbook
    playbookArgs.extras = ((ansibleArgs.extras ?: '') + " -e @${varsFile}").trim()
    if (check.limit) {
        playbookArgs.limit = check.limit
    }

    long started = System.currentTimeMillis()
    String failure = null
    try {
        timeout(time: check.timeoutMinutes, unit: 'MINUTES') {
            ansiblePlaybook(playbookArgs)
        }
    } catch (AbortException e) {
        // ansible-playbook 返回非 0（断言失败、主机不可达等）时插件抛出 AbortException
        failure = e.message
    } catch (InterruptedException e) {
        // 检查超时算作检查失败；手动中止构建等其他中断照常向上抛出。
        // timeout 取消 ansiblePlaybook 时插件抛出的是普通 InterruptedException，
        // 不带超时原因，所以同时用已耗时判断。
        long elapsed = System.currentTimeMillis() - started
        boolean timedOut = e instanceof FlowInterruptedException ?
                isTimeout((FlowInterruptedException) e) : elapsed >= check.timeoutMinutes * 60000L
        if (!timedOut) {
            throw e
        }
        failure = "超过 ${check.timeoutMinutes} 分钟未完成"
    }

    Map result = [id: check.id, name: check.name, phase: check.phase, policy: check.policy,
                  params: check.params, durationSeconds: (System.currentTimeMillis() - started).intdiv(1000)]
    if (failure == null) {
        result.status = 'PASSED'
        results << result
        return
    }

    String message = "检查 ${check.id} 未通过：${failure}"
    result.status = 'FAILED'
    result.message = failure
    results << result

    switch (check.policy) {
        case 'abort':
            result.outcome = 'aborted'
            error "${message}（阻塞检查，停止流水线）"
            break
        case 'warn':
            result.outcome = 'continued'
            unstable "${message}（非阻塞检查，继续执行）"
            break
        case 'ignore':
            result.outcome = 'ignored'
            echo "[deployChecks] ${message}（已忽略）"
            break
        case 'ask':
            // 点击“中止”或等待超时都会中止构建
            result.outcome = 'aborted'
            timeout(time: askTimeoutMinutes, unit: 'MINUTES') {
                input message: "${message}\n是否继续？", ok: '继续'
            }
            result.outcome = 'continued'
            unstable "${message}（人工确认继续）"
            break
    }
}

@NonCPS
private static Map requireMap(Object value, String label) {
    if (!(value instanceof Map)) {
        throw new IllegalArgumentException("${label} 必须是 YAML 映射（key: value），实际是 ${value?.getClass()?.simpleName}")
    }
    return (Map) value
}

@NonCPS
private static boolean isTimeout(FlowInterruptedException e) {
    return e.causes.any { it.getClass().simpleName == 'ExceededTimeout' }
}

// 把 PRE_CHECKS / POST_CHECKS / CHECK_PARAMS 转成与 CHECK_PLAN 相同的结构
@NonCPS
private static Map shorthandPlan(String pre, String post, Map overrides, Map catalog) {
    Map defaults = (catalog.defaults ?: [:]) as Map
    Map plan = [:]
    ['pre': pre, 'post': post].each { phase, text ->
        String value = text?.trim()
        List names
        if (!value) {
            names = (defaults[phase] ?: []) as List
        } else if (value.equalsIgnoreCase('none')) {
            names = []
        } else {
            names = value.split(/[,\s]+/).findAll { it } as List
        }
        plan[phase] = names.collect { String item ->
            List parts = item.split(':', 2) as List
            Map entry = [name: parts[0]]
            if (parts.size() > 1) {
                entry.policy = parts[1]
            }
            if (overrides[parts[0]] != null) {
                entry.params = overrides[parts[0]]
            }
            return entry
        }
    }
    Set selected = plan.values().flatten().collect { it.name } as Set
    Set unused = overrides.keySet().findAll { !(it in selected) } as Set
    if (unused) {
        throw new IllegalArgumentException("CHECK_PARAMS 中的 ${unused} 不在本次选择的检查里")
    }
    return plan
}

// 校验计划并与目录中的默认值合并
@NonCPS
private static Map buildPlan(Map rawPlan, Map catalog) {
    Map checks = (catalog.checks ?: [:]) as Map
    List errors = []
    Map plan = [pre: [], post: []]
    Set unknownPhases = (rawPlan ?: [:]).keySet().findAll { !(it in ['pre', 'post']) } as Set
    if (unknownPhases) {
        errors << "未知阶段 ${unknownPhases}，只支持 pre / post"
    }
    ['pre', 'post'].each { String phase ->
        Object entries = rawPlan?.get(phase)
        if (entries == null) {
            return
        }
        if (!(entries instanceof List)) {
            errors << "${phase} 必须是列表"
            return
        }
        Set ids = [] as Set
        entries.eachWithIndex { Object item, int index ->
            String where = "${phase}[${index}]"
            Map entry = item instanceof String ? [name: item] : item
            if (!(entry instanceof Map) || !entry.name) {
                errors << "${where}：应为检查名或包含 name 的映射"
                return
            }
            Set badKeys = entry.keySet().findAll { !(it in ENTRY_KEYS) } as Set
            if (badKeys) {
                errors << "${where}：不支持的字段 ${badKeys}，可用字段 ${ENTRY_KEYS}"
            }
            Map definition = checks[entry.name] as Map
            if (definition == null) {
                errors << "${where}：未知检查 '${entry.name}'，可用检查 ${checks.keySet()}"
                return
            }
            List phases = (definition.phases ?: ['pre', 'post']) as List
            if (!(phase in phases)) {
                errors << "${where}：检查 '${entry.name}' 不能用于 ${phase}，只能用于 ${phases}"
            }
            String id = (entry.id ?: entry.name).toString()
            if (!(id ==~ /[A-Za-z0-9_.-]+/)) {
                errors << "${where}：id '${id}' 只能包含字母、数字、_ . -"
            } else if (!ids.add(id)) {
                errors << "${where}：id '${id}' 重复；同一检查运行多次时请用 id 区分"
            }
            String policy = (entry.policy ?: definition.policy ?: 'abort').toString()
            if (!(policy in POLICIES)) {
                errors << "${where}：未知策略 '${policy}'，可用策略 ${POLICIES}"
            }
            Map defaultParams = (definition.params ?: [:]) as Map
            Object given = entry.params ?: [:]
            if (!(given instanceof Map)) {
                errors << "${where}：params 必须是映射"
                return
            }
            Set unknownParams = given.keySet().findAll { !defaultParams.containsKey(it) } as Set
            if (unknownParams) {
                errors << "${where}：检查 '${entry.name}' 不接受参数 ${unknownParams}，可用参数 ${defaultParams.keySet()}"
            }
            Map merged = [:]
            merged.putAll(defaultParams)
            merged.putAll(given)
            ((definition.required ?: []) as List).each { String key ->
                if (merged[key] == null || merged[key].toString().trim().isEmpty()) {
                    errors << "${where}：检查 '${entry.name}' 必须提供参数 ${key}"
                }
            }
            given.each { key, value ->
                if (containsTemplate(value)) {
                    // Ansible 会渲染 extra vars 中的 Jinja 表达式，可在 Jenkins 节点上执行命令
                    errors << "${where}：参数 ${key} 不能包含 Jinja 模板语法（{{ / {% / {#）"
                }
            }
            plan[phase] << [
                id            : id,
                name          : entry.name.toString(),
                phase         : phase,
                description   : (definition.description ?: '').toString(),
                playbook      : definition.playbook.toString(),
                policy        : policy,
                params        : merged,
                limit         : entry.limit?.toString(),
                timeoutMinutes: (entry.timeout_minutes ?: definition.timeout_minutes ?: 10) as int,
            ]
        }
    }
    if (errors) {
        throw new IllegalArgumentException("检查计划有误：\n  - " + errors.join('\n  - '))
    }
    return plan
}

@NonCPS
private static boolean containsTemplate(Object value) {
    if (value instanceof Map) {
        return value.any { k, v -> containsTemplate(k) || containsTemplate(v) }
    }
    if (value instanceof Collection) {
        return value.any { containsTemplate(it) }
    }
    return value != null && value.toString() =~ /\{[{%#]/
}

@NonCPS
private static String describe(Map plan) {
    List lines = ['[deployChecks] 本次部署的检查计划：']
    ['pre', 'post'].each { String phase ->
        List checks = plan[phase] as List
        lines << "  ${phase}: ${checks ? '' : '（无）'}"
        checks.each { Map c ->
            lines << "    - ${c.id} [${c.policy}] ${JsonOutput.toJson(c.params)}${c.limit ? ' limit=' + c.limit : ''}"
        }
    }
    return lines.join('\n')
}

@NonCPS
private static String formatReport(List results) {
    List lines = ['[deployChecks] 检查结果：']
    results.each { Map r ->
        String outcome = r.outcome ? " -> ${r.outcome}" : ''
        lines << String.format('  %-5s %-24s %-7s %-6s %4ds%s', r.phase, r.id, r.policy, r.status,
                r.durationSeconds, outcome)
    }
    return lines.join('\n')
}
