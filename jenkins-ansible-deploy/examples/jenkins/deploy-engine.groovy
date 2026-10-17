// 部署引擎：两个 Jenkinsfile 用 load 步骤加载本文件，共用这里的全部逻辑。
//
//   prepare()                     读取任务目录、各任务 role 的参数声明与默认值、inventory 中的主机组
//   activeChoicesParameters()     方案 D 的构建参数（Active Choices 插件）
//   activeChoicesSelection()      把方案 D 的参数页选择转成计划草稿
//   basicParameters()             方案 C 的构建参数（只用 Jenkins 自带参数类型）
//   basicSelection()              把方案 C 的勾选结果转成计划草稿，需要时用 input 步骤弹出参数表单
//   resolvePlan()                 校验计划草稿（或 DEPLOY_PLAN），与默认值合并，得到本次的执行计划
//   runTasks() / deploy()         执行额外任务与部署
//   report()                      打印结果汇总并归档 deploy-report.json

// ===================================================================================
// 读取任务目录与 inventory
// ===================================================================================

Map prepare(Map settings) {
    String base = settings.baseDir
    Map catalog = readYaml(file: "${base}/jenkins/tasks.yml")
    Map tasks = [:]
    for (String name in (catalog.tasks ?: [:]).keySet()) {
        String roleDir = "${base}/ansible/roles/${name}"
        Map spec = fileExists("${roleDir}/meta/argument_specs.yml") ? readYaml(file: "${roleDir}/meta/argument_specs.yml") : null
        Map defaults = fileExists("${roleDir}/defaults/main.yml") ? (readYaml(file: "${roleDir}/defaults/main.yml") ?: [:]) : [:]
        tasks[name] = [meta: catalog.tasks[name], spec: spec, defaults: defaults]
    }
    Map inventory = readYaml(file: "${base}/ansible/inventory/hosts.yml")
    List errors = []
    Map context = settings + [tasks: taskDefinitions(tasks, errors), groups: targetGroups(inventory, errors)]
    if (errors) {
        error "[deploy] 任务目录或 inventory 有误：\n  - " + errors.join('\n  - ')
    }
    return context
}

// 把任务目录、argument_specs、defaults 合并成一个任务定义；发现的问题追加到 errors
@NonCPS
Map taskDefinitions(Map raw, List errors) {
    Map supportedTypes = [str: 'str', path: 'str', int: 'int', bool: 'bool']
    Map result = [:]
    raw.each { String name, Map item ->
        Map meta = item.meta ?: [:]
        if (item.spec == null) {
            errors << "任务 ${name}：缺少 ansible/roles/${name}/meta/argument_specs.yml"
        }
        Map options = item.spec?.argument_specs?.main?.options ?: [:]
        List params = []
        options.each { String key, Map option ->
            String type = supportedTypes[(option.type ?: 'str').toString()]
            if (type == null) {
                errors << "任务 ${name} 的参数 ${key}：不支持类型 ${option.type}，只支持 ${supportedTypes.keySet()}"
                return
            }
            if (option.choices && type != 'str') {
                errors << "任务 ${name} 的参数 ${key}：choices 只能用于 str 类型"
            }
            Object defaultValue = item.defaults[key]
            if (containsTemplate(defaultValue)) {
                errors << "任务 ${name} 的参数 ${key}：defaults/main.yml 中的默认值不能含模板语法"
            }
            params << [name       : key, type: type, required: option.required == true,
                       description: descriptionText(option.description),
                       choices    : option.choices ? option.choices.collect { it.toString() } : null,
                       default    : defaultValue]
        }
        if (!(meta.policy in ['abort', 'warn'])) {
            errors << "任务 ${name}：policy 只能是 abort 或 warn"
        }
        if (!(meta.run_on in ['targets', 'controller'])) {
            errors << "任务 ${name}：run_on 只能是 targets 或 controller"
        }
        if (!meta.phases || !(meta.phases as List).every { it in ['pre', 'post'] }) {
            errors << "任务 ${name}：phases 只能包含 pre、post"
        }
        result[name] = [name          : name, description: (meta.description ?: name).toString(),
                        phases        : (meta.phases ?: []) as List, runOn: meta.run_on, policy: meta.policy,
                        timeoutMinutes: (meta.timeout_minutes ?: 10) as int, params: params]
    }
    return result
}

@NonCPS
String descriptionText(Object description) {
    return description instanceof List ? description.join(' ') : (description ?: '').toString()
}

// 主机组 = inventory 中 deploy_targets 的子组；返回 [组名: [主机名, ...]]
@NonCPS
Map targetGroups(Map inventory, List errors) {
    Map children = inventory?.all?.children?.deploy_targets?.children
    if (!(children instanceof Map) || children.isEmpty()) {
        errors << 'inventory/hosts.yml 中没有 all.children.deploy_targets.children'
        return [:]
    }
    Map groups = [:]
    children.each { String group, Object body ->
        groups[group] = ((body instanceof Map ? body.hosts : null) ?: [:]).keySet() as List
    }
    return groups
}

// ===================================================================================
// 方案 D：Active Choices 参数页
// ===================================================================================

// 参数页上的脚本在打开页面时运行，看不到工作区，所以把主机组和任务定义作为字面量嵌入脚本。
// 脚本在 Groovy 沙箱中运行，不需要管理员审批；但返回值会经过插件的 HTML 过滤，全角标点和 @ = + " 被转成
// HTML 实体。HTML 表单中的实体由浏览器还原，勾选框的文字却会原样显示实体，所以勾选框文字只用半角符号。
List activeChoicesParameters(Map context) {
    String groups = groovyLiteral(context.groups)
    List result = [
        activeChoice(name: 'TARGET_GROUPS', randomName: 'choice-target-groups', choiceType: 'PT_CHECKBOX',
                filterable: false, filterLength: 1,
                description: '发布对象：inventory 中 deploy_targets 下的主机组，可多选',
                script: groovyScript(script: [classpath: [], sandbox: true, script: """def groups = ${groups}
return groups.collectEntries { name, hosts -> [(name): name + ' (' + hosts.join(', ') + ')'] }
"""], fallbackScript: [classpath: [], sandbox: true, script: 'return ["（主机组列表生成失败）"]'])),
        booleanParam(name: 'SKIP_DEPLOY', defaultValue: false, description: '勾选后跳过部署，只执行所选的部署前、部署后任务'),
    ]
    [[phase: 'pre', title: '部署前'], [phase: 'post', title: '部署后']].each { Map p ->
        String name = p.phase.toUpperCase() + '_TASKS'
        String tasks = groovyLiteral(context.tasks.values().findAll { p.phase in it.phases })
        result << activeChoice(name: name, randomName: "choice-${p.phase}-tasks", choiceType: 'PT_CHECKBOX',
                filterable: false, filterLength: 1, description: "${p.title}额外任务，可多选，同一阶段按列表顺序执行",
                script: groovyScript(script: [classpath: [], sandbox: true, script: """def tasks = ${tasks}
return tasks.collectEntries { [(it.name): it.name + ' — ' + it.description] }
"""], fallbackScript: [classpath: [], sandbox: true, script: 'return ["（任务列表生成失败）"]']))
        result << activeChoiceHtml(name: p.phase.toUpperCase() + '_TASK_OPTIONS', randomName: "choice-${p.phase}-task-options",
                choiceType: 'ET_FORMATTED_HTML', omitValueField: true, referencedParameters: name,
                description: "所选${p.title}任务的失败策略和参数",
                script: groovyScript(script: [classpath: [], sandbox: true, script: optionsFormScript(tasks, name)],
                        fallbackScript: [classpath: [], sandbox: true, script: 'return "（参数表单生成失败）"']))
    }
    result << deployPlanParameter()
    return result
}

// 生成参数表单的脚本。Active Choices 提交时把所有 name="value" 的输入框的值用逗号连成一个字符串，
// 所以每个输入框前放一个隐藏的标记 @@任务名.参数名，解析时按标记切分，值中有逗号也不会错位。
@NonCPS
String optionsFormScript(String tasks, String referencedParameter) {
    return """def tasks = ${tasks}
def esc = { v -> String.valueOf(v == null ? '' : v).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace("'", '&#39;') }
def select = { List options, Object current ->
    '<select name="value" class="jenkins-select__input">' + options.collect { o ->
        '<option value="' + esc(o) + '"' + (String.valueOf(o) == String.valueOf(current) ? ' selected' : '') + '>' + esc(o) + '</option>'
    }.join('') + '</select>'
}
def selected = String.valueOf(${referencedParameter} ?: '').tokenize(',').collect { it.trim() }
def chosen = tasks.findAll { it.name in selected }
if (!chosen) {
    return '<div>未选择任务</div>'
}
def html = []
chosen.each { t ->
    // 插件的 HTML 过滤会去掉 fieldset、legend 等标签，分组只用 div
    html << '<div style="border:1px solid #d1d5db;border-radius:6px;padding:8px 14px;margin:0 0 10px 0">'
    html << '<div style="margin-bottom:6px"><b>' + esc(t.name) + '</b> ' + esc(t.description) + '</div><table>'
    html << '<tr><td style="padding:3px 12px 3px 0">失败时</td><td>'
    html << '<input type="hidden" name="value" value="@@' + esc(t.name) + '.policy">' + select(['abort', 'warn'], t.policy)
    html << ' abort：停止流水线；warn：标记不稳定后继续</td></tr>'
    t.params.each { p ->
        html << '<tr><td style="padding:3px 12px 3px 0">' + esc(p.name) + (p.required ? ' <span style="color:#dc2626">*</span>' : '') + '</td><td>'
        html << '<input type="hidden" name="value" value="@@' + esc(t.name) + '.' + esc(p.name) + '">'
        if (p.choices) {
            html << select(p.choices, p.default)
        } else if (p.type == 'bool') {
            html << select(['true', 'false'], p.default == true)
        } else {
            html << '<input type="' + (p.type == 'int' ? 'number' : 'text') + '" name="value" value="' + esc(p.default) + '" class="jenkins-input" style="width:320px">'
        }
        html << ' ' + esc(p.description) + '</td></tr>'
    }
    html << '</table></div>'
}
return html.join('')
"""
}

// 方案 D：把参数页上的选择转成计划草稿（结构与 DEPLOY_PLAN 相同）。
// 参数尚未登记（第一次运行）或提供了 DEPLOY_PLAN 时返回 null
Map activeChoicesSelection(Map context, Map params) {
    if (!params.containsKey('TARGET_GROUPS') || params.DEPLOY_PLAN?.trim()) {
        return null
    }
    return selectionPlan(context, csv(params.TARGET_GROUPS), params.SKIP_DEPLOY == true,
            [pre : [names: csv(params.PRE_TASKS), values: parseOptions(params.PRE_TASK_OPTIONS)],
             post: [names: csv(params.POST_TASKS), values: parseOptions(params.POST_TASK_OPTIONS)]])
}

// 表单提交的值形如 "@@task_a.policy,abort,@@task_a.task_a_repeat,2"，转成 [task_a.policy: abort, ...]。
// Active Choices 拼接时按 JSON 字符串规则转义了每个值（" 变成 \"，\ 变成 \\），这里还原。
@NonCPS
Map parseOptions(Object text) {
    Map values = [:]
    (text ?: '').toString().split('(?:^|,)@@').each { String part ->
        int comma = part.indexOf(',')
        if (comma > 0) {
            values[part.substring(0, comma)] = jsonUnescape(part.substring(comma + 1))
        } else if (part) {
            values[part] = ''
        }
    }
    return values
}

@NonCPS
String jsonUnescape(String text) {
    Map simple = ['"': '"', '\\': '\\', '/': '/', 'b': '\b', 'f': '\f', 'n': '\n', 'r': '\r', 't': '\t']
    return text.replaceAll(/\\(u[0-9a-fA-F]{4}|.)/) { String all, String code ->
        code.length() == 5 ? String.valueOf((char) Integer.parseInt(code.substring(1), 16)) : (simple[code] ?: all)
    }
}

// ===================================================================================
// 方案 C：只用 Jenkins 自带参数类型（不装插件）
// ===================================================================================

// 参数页上每个主机组、每个任务一个勾选框。Jenkins 自带的参数页是静态的，不能随勾选显示或隐藏输入框，
// 所以任务参数改在构建开始后用 input 步骤填写（EDIT_TASK_OPTIONS）。
List basicParameters(Map context) {
    List result = []
    context.groups.each { String group, List hosts ->
        result << booleanParam(name: "TARGET_${group}", defaultValue: false, description: "发布对象：主机组 ${group}（${hosts.join(', ')}）")
    }
    result << booleanParam(name: 'SKIP_DEPLOY', defaultValue: false, description: '勾选后跳过部署，只执行所选的部署前、部署后任务')
    ['pre', 'post'].each { String phase ->
        context.tasks.values().findAll { phase in it.phases }.each { Map task ->
            result << booleanParam(name: "${phase.toUpperCase()}_${task.name}", defaultValue: false,
                    description: "${phase == 'pre' ? '部署前' : '部署后'}任务：${task.description}")
        }
    }
    result << booleanParam(name: 'EDIT_TASK_OPTIONS', defaultValue: true,
            description: '构建开始后弹出表单，填写所选任务的失败策略和参数；不勾选则全部使用默认值')
    result << deployPlanParameter()
    return result
}

// 方案 C：把勾选结果转成计划草稿。参数尚未登记（第一次运行）或提供了 DEPLOY_PLAN 时返回 null
Map basicSelection(Map context, Map params) {
    if (!params.containsKey('EDIT_TASK_OPTIONS') || params.DEPLOY_PLAN?.trim()) {
        return null
    }
    List targets = context.groups.keySet().findAll { params["TARGET_${it}"] == true } as List
    Map phases = [:]
    ['pre', 'post'].each { String phase ->
        phases[phase] = [names : context.tasks.values().findAll { phase in it.phases && params["${phase.toUpperCase()}_${it.name}"] == true }*.name,
                         values: [:]]
    }
    List names = []
    List inputs = []
    if (params.EDIT_TASK_OPTIONS) {
        ['pre', 'post'].each { String phase ->
            phases[phase].names.each { String name ->
                taskInputs(phase, context.tasks[name]).each { Map field ->
                    names << field.name
                    inputs << field.input
                }
            }
        }
    }
    if (inputs) {
        Object answer = timeout(time: 30, unit: 'MINUTES') {
            input(message: '填写所选任务的失败策略和参数', ok: '开始', parameters: inputs)
        }
        // 只有一个输入项时 input 直接返回该值，否则返回 [输入项名称: 值]
        Map answers = inputs.size() == 1 ? [(names[0]): answer] : answer
        answers.each { String key, Object value ->
            int dot = key.indexOf('.')
            phases[key.substring(0, dot)].values[key.substring(dot + 1)] = String.valueOf(value)
        }
    }
    return selectionPlan(context, targets, params.SKIP_DEPLOY == true, phases)
}

// 一个任务在 input 表单中的输入项：[[name: 输入项名称, input: 参数定义], ...]，名称形如 pre.task_a.policy
List taskInputs(String phase, Map task) {
    String prefix = "${phase}.${task.name}."
    List fields = [[name : prefix + 'policy',
                    input: choice(name: prefix + 'policy', choices: [task.policy] + (['abort', 'warn'] - task.policy),
                            description: "${task.name}：失败时 abort 停止流水线，warn 标记不稳定后继续")]]
    task.params.each { Map p ->
        String name = prefix + p.name
        String description = "${task.name}：${p.description}${p.required ? '（必填）' : ''}"
        if (p.choices) {
            String current = String.valueOf(p.default)
            fields << [name: name, input: choice(name: name, choices: [current] + (p.choices - current), description: description)]
        } else if (p.type == 'bool') {
            fields << [name: name, input: booleanParam(name: name, defaultValue: p.default == true, description: description)]
        } else {
            fields << [name: name, input: string(name: name, defaultValue: p.default == null ? '' : String.valueOf(p.default), description: description)]
        }
    }
    return fields
}

// ===================================================================================
// 计划：两种参数页共用的转换与校验
// ===================================================================================

def deployPlanParameter() {
    return text(name: 'DEPLOY_PLAN', defaultValue: '', description: '''YAML 格式的完整计划，非空时忽略上面所有参数，供 API / 上游 job 触发：
targets: [web, api]
skip_deploy: false
pre:
  - task_b
  - {name: task_a, policy: warn, params: {task_a_message: hello}}
post:
  - {name: check_a, id: check_a_web, params: {check_a_min_connections: 50}}''')
}

// 表单中的值都是字符串（parseOptions 的结果，[任务名.参数名: 值]），按参数类型转换后组成计划草稿。
// 同一阶段的任务按任务目录的顺序排列，与选择的先后无关；未知任务名放在最后，由 buildPlan 报错
@NonCPS
Map selectionPlan(Map context, List targets, boolean skipDeploy, Map phases) {
    Map plan = [targets: targets, skip_deploy: skipDeploy]
    phases.each { String phase, Map selection ->
        List names = (context.tasks.keySet().findAll { it in selection.names } as List) + selection.names.findAll { !context.tasks.containsKey(it) }
        plan[phase] = names.collect { String name ->
            Map entry = [name: name]
            Map task = context.tasks[name]
            if (task == null) {
                return entry
            }
            if (selection.values["${name}.policy"]) {
                entry.policy = selection.values["${name}.policy"]
            }
            Map params = [:]
            task.params.each { Map p ->
                String key = "${name}.${p.name}"
                if (selection.values.containsKey(key)) {
                    params[p.name] = fromFormValue(selection.values[key], p)
                }
            }
            if (params) {
                entry.params = params
            }
            return entry
        }
    }
    return plan
}

// 空字符串表示未填写（使用默认值）；整数无法转换时原样保留字符串，由 buildPlan 报错
@NonCPS
Object fromFormValue(String value, Map param) {
    if (value == '' && param.type != 'str') {
        return null
    }
    if (param.type == 'int' && value.trim() ==~ /-?\d+/) {
        return value.trim().toLong()
    }
    if (param.type == 'bool' && value in ['true', 'false']) {
        return value == 'true'
    }
    return value
}

// 得到本次执行计划：DEPLOY_PLAN 非空时以它为准，否则用参数页的选择（draft）。
// 计划有误时一次列出全部问题，让构建在运行任何 playbook 之前失败。第一次运行（draft 为 null）返回 null。
Map resolvePlan(Map context, Map params, Map draft) {
    if (draft == null && !params.DEPLOY_PLAN?.trim()) {
        currentBuild.result = 'NOT_BUILT'
        currentBuild.description = '已登记构建参数，请用 Build with Parameters 重新运行'
        echo "[deploy] ${currentBuild.description}"
        return null
    }
    List errors = []
    Map plan = null
    if (params.DEPLOY_PLAN?.trim()) {
        echo '[deploy] 使用 DEPLOY_PLAN，忽略参数页上的其他选择'
        Object raw = readYaml(text: params.DEPLOY_PLAN)
        if (raw instanceof Map) {
            plan = buildPlan(raw, context, errors)
        } else {
            errors << 'DEPLOY_PLAN 必须是 YAML 映射'
        }
    } else {
        plan = buildPlan(draft, context, errors)
    }
    if (errors) {
        error '[deploy] 计划有误：\n  - ' + errors.join('\n  - ')
    }
    echo describePlan(plan)
    return plan
}

// 校验计划草稿，并与任务的默认参数合并；发现的问题追加到 errors
@NonCPS
Map buildPlan(Map raw, Map context, List errors) {
    List unknownKeys = raw.keySet().findAll { !(it in ['targets', 'skip_deploy', 'pre', 'post']) } as List
    if (unknownKeys) {
        errors << "不支持的字段 ${unknownKeys}，可用字段 [targets, skip_deploy, pre, post]"
    }
    Object targets = raw.targets ?: []
    if (!(targets instanceof List)) {
        errors << 'targets 必须是列表'
        targets = []
    }
    List unknownGroups = targets.findAll { !context.groups.containsKey(it) } as List
    if (unknownGroups) {
        errors << "未知主机组 ${unknownGroups}，可用主机组 ${context.groups.keySet()}"
    }
    if (raw.skip_deploy != null && !(raw.skip_deploy instanceof Boolean)) {
        errors << 'skip_deploy 必须是 true 或 false'
    }
    Map plan = [targets: targets.collect { it.toString() }, skipDeploy: raw.skip_deploy == true, pre: [], post: [],
                baseDir: context.baseDir, ansible: context.ansible]
    ['pre', 'post'].each { String phase ->
        Object entries = raw[phase] ?: []
        if (!(entries instanceof List)) {
            errors << "${phase} 必须是列表"
            return
        }
        // id 在一个阶段内唯一即可：stage 名、参数文件名、报告都带阶段
        List ids = []
        entries.eachWithIndex { Object item, int index ->
            Map task = buildTask("${phase}[${index}]", phase, item, context, errors)
            if (task == null) {
                return
            }
            if (task.id in ids) {
                errors << "${phase}[${index}]：id '${task.id}' 重复；同一任务运行多次时用 id 区分"
            }
            ids << task.id
            plan[phase] << task
        }
    }
    boolean needsTargets = !plan.skipDeploy || (plan.pre + plan.post).any { it.runOn == 'targets' }
    if (needsTargets && !plan.targets) {
        errors << '没有选择主机组（部署和 run_on: targets 的任务需要至少一个主机组）'
    }
    return plan
}

@NonCPS
Map buildTask(String where, String phase, Object item, Map context, List errors) {
    Object entry = item instanceof String ? [name: item] : item
    if (!(entry instanceof Map) || !entry.name) {
        errors << "${where}：应为任务名，或包含 name 的映射"
        return null
    }
    List badKeys = entry.keySet().findAll { !(it in ['name', 'id', 'policy', 'params', 'timeout_minutes']) } as List
    if (badKeys) {
        errors << "${where}：不支持的字段 ${badKeys}，可用字段 [name, id, policy, params, timeout_minutes]"
    }
    Map task = context.tasks[entry.name.toString()]
    if (task == null) {
        errors << "${where}：未知任务 '${entry.name}'，可用任务 ${context.tasks.keySet()}"
        return null
    }
    if (!(phase in task.phases)) {
        errors << "${where}：任务 ${task.name} 不能用于 ${phase}，只能用于 ${task.phases}"
    }
    String id = (entry.id ?: task.name).toString()
    if (!(id ==~ /[A-Za-z0-9_-]+/)) {
        errors << "${where}：id '${id}' 只能包含字母、数字、_、-"
    }
    String policy = (entry.policy ?: task.policy).toString()
    if (!(policy in ['abort', 'warn'])) {
        errors << "${where}：未知策略 '${policy}'，只能是 abort 或 warn"
    }
    // 用 containsKey 而不是 ?:，否则 0 会被当成未填写而改用默认值
    Object timeoutMinutes = entry.containsKey('timeout_minutes') ? entry.timeout_minutes : task.timeoutMinutes
    if (!(timeoutMinutes instanceof Integer) || timeoutMinutes < 1) {
        errors << "${where}：timeout_minutes 必须是正整数"
        timeoutMinutes = task.timeoutMinutes
    }
    Object given = entry.params ?: [:]
    if (!(given instanceof Map)) {
        errors << "${where}：params 必须是映射"
        given = [:]
    }
    Map declared = task.params.collectEntries { [(it.name): it] }
    List unknownParams = given.keySet().findAll { !declared.containsKey(it) } as List
    if (unknownParams) {
        errors << "${where}：任务 ${task.name} 不接受参数 ${unknownParams}，可用参数 ${declared.keySet()}"
    }
    Map params = [:]
    task.params.each { Map p ->
        Object value = given[p.name] != null ? checkValue("${where}：参数 ${p.name}", given[p.name], p, errors) : p.default
        if (p.required && (value == null || value.toString().trim().isEmpty())) {
            errors << "${where}：任务 ${task.name} 必须提供参数 ${p.name}"
        }
        if (value != null) {
            params[p.name] = value
        }
    }
    return [id     : id, name: task.name, phase: phase, description: task.description, runOn: task.runOn,
            policy : policy, timeoutMinutes: timeoutMinutes, params: params]
}

// 按参数类型检查一个值；问题追加到 errors。Ansible 会渲染 extra vars 中的 Jinja 表达式，
// 能触发构建的人借此可以在 Jenkins 节点上执行命令，所以拒绝含模板语法的值。
@NonCPS
Object checkValue(String label, Object value, Map param, List errors) {
    switch (param.type) {
        case 'int':
            if (value instanceof Integer || value instanceof Long) {
                return value
            }
            errors << "${label} 应为整数，实际为 '${value}'"
            return value
        case 'bool':
            if (value instanceof Boolean) {
                return value
            }
            errors << "${label} 应为 true 或 false，实际为 '${value}'"
            return value
        default:
            if (value instanceof Map || value instanceof List) {
                errors << "${label} 应为字符串"
                return value
            }
            String text = value.toString()
            if (param.choices && !(text in param.choices)) {
                errors << "${label} 只能是 ${param.choices}，实际为 '${text}'"
            }
            if (containsTemplate(text)) {
                errors << "${label} 不能包含模板语法（{{、{%、{#）"
            }
            return text
    }
}

@NonCPS
boolean containsTemplate(Object value) {
    String text = value instanceof String ? value : ''
    return text.contains('{{') || text.contains('{%') || text.contains('{#')
}

@NonCPS
String describePlan(Map plan) {
    List lines = ['[deploy] 本次执行计划：',
                  "  主机组：${plan.targets ?: '（无）'}",
                  "  部署：${plan.skipDeploy ? '跳过' : '执行'}"]
    ['pre', 'post'].each { String phase ->
        lines << "  ${phase == 'pre' ? '部署前' : '部署后'}任务：${plan[phase] ? '' : '（无）'}"
        plan[phase].each { Map t ->
            lines << "    - ${t.id} [${t.policy}] ${t.params}"
        }
    }
    return lines.join('\n')
}

// ===================================================================================
// 执行
// ===================================================================================

// 依次执行一个阶段的任务，每个任务一个 stage；结果追加到 results
def runTasks(Map plan, String phase, List results) {
    for (Map task in plan[phase]) {
        stage("${phase}: ${task.id}") {
            runTask(plan, task, results)
        }
    }
}

def runTask(Map plan, Map task, List results) {
    echo "[deploy] ${task.id}（${task.description}）策略=${task.policy} 参数=${task.params}"
    // 参数写进 JSON 文件，用 -e @file 传给 Ansible：ansiblePlaybook 的 extraVars 会把数字变成字符串
    String varsFile = ".deploy-run/${task.phase}-${task.id}.json"
    writeJSON file: varsFile, json: task.params + [task_role: task.name,
                                                    task_hosts: task.runOn == 'targets' ? 'deploy_targets' : 'localhost']
    Map args = [playbook: "${plan.baseDir}/ansible/run-task.yml", extras: "-e @${varsFile}"]
    if (task.runOn == 'targets') {
        args.limit = plan.targets.join(':')
    }
    long started = System.currentTimeMillis()
    String failure = null
    try {
        timeout(time: task.timeoutMinutes, unit: 'MINUTES') {
            runPlaybook(plan, args)
        }
    } catch (hudson.AbortException e) {
        // ansible-playbook 返回非 0（断言失败、参数校验失败、主机不可达等）时，插件抛出 AbortException
        failure = e.message
    } catch (InterruptedException e) {
        // 超时按任务失败处理，手动中止照常抛出。timeout 中止 ansiblePlaybook 时抛出的异常不带超时原因，
        // 所以用已耗时区分
        if (System.currentTimeMillis() - started < task.timeoutMinutes * 60000L) {
            throw e
        }
        failure = "超过 ${task.timeoutMinutes} 分钟未完成"
    }
    Map result = [id     : task.id, name: task.name, phase: task.phase, policy: task.policy, params: task.params,
                  seconds: (int) ((System.currentTimeMillis() - started) / 1000), status: failure ? 'FAILED' : 'PASSED']
    results << result
    if (failure == null) {
        return
    }
    result.message = failure
    if (task.policy == 'abort') {
        result.outcome = 'aborted'
        error "[deploy] 任务 ${task.id} 失败：${failure}（abort：停止流水线）"
    }
    result.outcome = 'continued'
    unstable "[deploy] 任务 ${task.id} 失败：${failure}（warn：继续执行）"
}

def deploy(Map plan, List results) {
    long started = System.currentTimeMillis()
    Map result = [id: 'deploy', name: 'deploy', phase: 'deploy', policy: 'abort', params: [targets: plan.targets]]
    results << result
    try {
        runPlaybook(plan, [playbook: "${plan.baseDir}/ansible/deploy.yml", limit: plan.targets.join(':')])
        result.status = 'PASSED'
    } catch (hudson.AbortException e) {
        result.status = 'FAILED'
        result.message = e.message
        throw e
    } finally {
        result.seconds = (int) ((System.currentTimeMillis() - started) / 1000)
    }
}

// 所有 ansible-playbook 调用的公共部分：ansible.cfg、vault 密码、SSH 私钥
def runPlaybook(Map plan, Map args) {
    Map common = [vaultCredentialsId: plan.ansible.vaultCredentialsId]
    if (plan.ansible.sshCredentialsId) {
        common.credentialsId = plan.ansible.sshCredentialsId
    }
    withEnv(["ANSIBLE_CONFIG=${env.WORKSPACE}/${plan.baseDir}/ansible/ansible.cfg"]) {
        ansiblePlaybook(common + args)
    }
}

// 打印结果汇总，并归档 deploy-report.json
def report(List results) {
    if (!results) {
        return
    }
    echo formatReport(results)
    writeJSON file: 'deploy-report.json', json: results, pretty: 2
    archiveArtifacts artifacts: 'deploy-report.json'
}

@NonCPS
String formatReport(List results) {
    List lines = ['[deploy] 结果：']
    results.each { Map r ->
        lines << "  ${r.phase.padRight(6)} ${r.id.padRight(16)} ${r.policy.padRight(5)} ${(r.status ?: '-').padRight(6)}" +
                " ${String.valueOf(r.seconds).padLeft(4)}s${r.outcome ? ' -> ' + r.outcome : ''}"
    }
    return lines.join('\n')
}

// 把 Map / List / 字符串 / 数字 / 布尔转成 Groovy 字面量，用于嵌入参数页脚本
@NonCPS
String groovyLiteral(Object value) {
    if (value instanceof Map) {
        return value ? '[' + value.collect { k, v -> groovyLiteral(k.toString()) + ': ' + groovyLiteral(v) }.join(', ') + ']' : '[:]'
    }
    if (value instanceof Collection) {
        return '[' + value.collect { groovyLiteral(it) }.join(', ') + ']'
    }
    if (value instanceof Number || value instanceof Boolean || value == null) {
        return String.valueOf(value)
    }
    return "'" + value.toString().replace('\\', '\\\\').replace("'", "\\'").replace('\n', '\\n') + "'"
}

@NonCPS
List csv(Object value) {
    return (value ?: '').toString().tokenize(',').collect { it.trim() }.findAll { it }
}

return this
