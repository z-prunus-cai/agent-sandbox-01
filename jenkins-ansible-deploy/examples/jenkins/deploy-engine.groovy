// 部署引擎：两个 Jenkinsfile 用 load 步骤加载本文件。
import groovy.transform.Field

@Field final List PHASES = ['pre', 'post']
@Field final Map PHASE_TITLES = [pre: '部署前', post: '部署后']
@Field final List POLICIES = ['abort', 'warn']
@Field final List RUN_ON = ['targets', 'controller']
@Field final List PARAM_TYPES = ['str', 'int', 'bool']
@Field final List PLAN_KEYS = ['targets', 'skip_deploy', 'pre', 'post']
@Field final List ENTRY_KEYS = ['name', 'id', 'policy', 'params', 'timeout_minutes']
@Field final String TASK_ROLE_VAR = 'task_role'
@Field final String TASK_HOSTS_VAR = 'task_hosts'
@Field final List ENGINE_VARS = [TASK_ROLE_VAR, TASK_HOSTS_VAR]
@Field final List FORMS = ['activeChoices', 'basic']
@Field final String TARGETS_GROUP = 'deploy_targets'
@Field final int INPUT_TIMEOUT_MINUTES = 30
@Field final int MAX_TIMEOUT_MINUTES = 240

// ===================================================================================
// 准备：读取任务目录与 inventory，登记构建参数（需要工作区）
// ===================================================================================

// requested 必须是调用前的 params：properties() 执行后，params 会用 job 新登记的参数定义补上默认值，
// 第一次运行就无法判断参数页是否提交过。Jenkinsfile 把 params 作为实参传入，它在 properties() 之前求值。
Map prepareRequest(Map settings, Map requested, String form) {
    if (!(form in FORMS)) {
        error "[deploy] form 只能是 ${FORMS}"
    }
    Map context = loadContext(settings)
    List definitions = form == 'activeChoices' ? activeChoicesParameters(context) : basicParameters(context)
    properties([disableConcurrentBuilds(), parameters(definitions)])
    // 在登记参数之后检查：凭据有误时，参数页也已经生成
    checkCredentials(settings.ansible)
    return [form: form, context: context, requested: requested]
}

Map loadContext(Map settings) {
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

// 凭据缺失时插件照样运行 ansible-playbook，失败看起来与任务失败相同（warn 任务只会让构建不稳定），所以先检查。
// vault 口令按 Secret text 绑定检查：插件也接受 Secret file，用 Secret file 时把这里的 string 换成 file
def checkCredentials(Map ansible) {
    List bindings = [string(credentialsId: ansible.vaultCredentialsId, variable: 'VAULT_PASSWORD_CHECK')]
    if (ansible.sshCredentialsId) {
        bindings << sshUserPrivateKey(credentialsId: ansible.sshCredentialsId, keyFileVariable: 'SSH_KEY_CHECK')
    }
    withCredentials(bindings) {
        echo '[deploy] 凭据检查通过'
    }
}

@NonCPS
Map taskDefinitions(Map raw, List errors) {
    Map result = [:]
    raw.each { String name, Map item ->
        Map meta = item.meta ?: [:]
        if (item.spec == null) {
            errors << "任务 ${name}：缺少 ansible/roles/${name}/meta/argument_specs.yml"
        }
        List params = []
        (item.spec?.argument_specs?.main?.options ?: [:]).each { String key, Map option ->
            String type = (option.type ?: 'str').toString()
            if (!(type in PARAM_TYPES)) {
                errors << "任务 ${name} 的参数 ${key}：不支持类型 ${option.type}，只支持 ${PARAM_TYPES}"
                return
            }
            // 参数以 extra vars 传入，优先级最高：名称必须带任务名前缀，不能与引擎变量或 ansible_* 重名。
            // 与 inventory 变量、Ansible 特殊变量的冲突由 tests/test_catalog.py 检查（这里读不到解密后的 inventory）
            if (!key.startsWith(name + '_') || key in ENGINE_VARS || key.startsWith('ansible_')) {
                errors << "任务 ${name} 的参数 ${key}：名称必须以 ${name}_ 开头，且不能是 ${ENGINE_VARS} 或 ansible_*"
            }
            if (option.choices && type != 'str') {
                errors << "任务 ${name} 的参数 ${key}：choices 只能用于 str 类型"
            }
            Object defaultValue = item.defaults[key]
            if (containsTemplate(defaultValue)) {
                errors << "任务 ${name} 的参数 ${key}：defaults/main.yml 中的默认值不能含模板语法"
            }
            params << [name       : key, type: type, required: option.required == true,
                       description: (option.description instanceof List ? option.description.join(' ') : (option.description ?: '')).toString(),
                       choices    : option.choices ? option.choices.collect { it.toString() } : null,
                       default    : defaultValue]
        }
        if (!(meta.policy in POLICIES)) {
            errors << "任务 ${name}：policy 只能是 ${POLICIES}"
        }
        if (!(meta.run_on in RUN_ON)) {
            errors << "任务 ${name}：run_on 只能是 ${RUN_ON}"
        }
        if (!(meta.phases instanceof List) || !meta.phases || !meta.phases.every { it in PHASES }) {
            errors << "任务 ${name}：phases 只能包含 ${PHASES}"
        }
        if (!(meta.timeout_minutes instanceof Integer) || meta.timeout_minutes < 1 || meta.timeout_minutes > MAX_TIMEOUT_MINUTES) {
            errors << "任务 ${name}：timeout_minutes 必须是 1 到 ${MAX_TIMEOUT_MINUTES} 的整数"
        }
        result[name] = [name          : name, description: (meta.description ?: name).toString(),
                        phases        : meta.phases instanceof List ? meta.phases : [], runOn: meta.run_on, policy: meta.policy,
                        timeoutMinutes: meta.timeout_minutes, params: params]
    }
    return result
}

// 主机组 = inventory 中 deploy_targets 的子组；返回 [组名: [主机名, ...]]
@NonCPS
Map targetGroups(Map inventory, List errors) {
    Map children = inventory?.all?.children?.get(TARGETS_GROUP)?.children
    if (!(children instanceof Map) || children.isEmpty()) {
        errors << "inventory/hosts.yml 中没有 all.children.${TARGETS_GROUP}.children"
        return [:]
    }
    Map groups = [:]
    children.each { String group, Object body ->
        groups[group] = ((body instanceof Map ? body.hosts : null) ?: [:]).keySet() as List
    }
    return groups
}

// Active Choices 的脚本在 Groovy 沙箱中运行，不需要管理员审批
@NonCPS
Map sandboxedScript(String script, String fallback) {
    return [script: [classpath: [], sandbox: true, script: script], fallbackScript: [classpath: [], sandbox: true, script: fallback]]
}

@NonCPS
String phaseParameter(String phase, String suffix) {
    return "${phase.toUpperCase()}_${suffix}"
}

// ===================================================================================
// 方案 D：Active Choices 参数页
// ===================================================================================

// 参数页上的脚本在打开页面时运行，看不到工作区，所以把主机组和任务定义作为字面量嵌入脚本。
// 脚本在 Groovy 沙箱中运行，不需要管理员审批；但返回值会经过插件的 HTML 过滤，全角标点和 @ = + " 被转成
// HTML 实体。HTML 表单中的实体由浏览器还原，勾选框的文字却会原样显示实体，所以勾选框文字只用半角符号。
List activeChoicesParameters(Map context) {
    List result = [
        activeChoice(name: 'TARGET_GROUPS', randomName: 'choice-target-groups', choiceType: 'PT_CHECKBOX',
                filterable: false, filterLength: 1,
                description: "发布对象：inventory 中 ${TARGETS_GROUP} 下的主机组，可多选",
                script: groovyScript(sandboxedScript("""def groups = ${groovyLiteral(context.groups)}
return groups.collectEntries { name, hosts -> [(name): name + ' (' + hosts.join(', ') + ')'] }
""", 'return ["（主机组列表生成失败）"]'))),
        skipDeployParameter(),
    ]
    PHASES.each { String phase ->
        List tasks = tasksForPhase(context, phase)
        String listName = phaseParameter(phase, 'TASKS')
        result << activeChoice(name: listName, randomName: "choice-${phase}-tasks", choiceType: 'PT_CHECKBOX',
                filterable: false, filterLength: 1, description: "${PHASE_TITLES[phase]}额外任务，可多选，同一阶段按列表顺序执行",
                script: groovyScript(sandboxedScript("return ${groovyLiteral(tasks.collectEntries { [(it.name): it.name + ' — ' + it.description] })}",
                        'return ["（任务列表生成失败）"]')))
        result << activeChoiceHtml(name: phaseParameter(phase, 'TASK_OPTIONS'), randomName: "choice-${phase}-task-options",
                choiceType: 'ET_FORMATTED_HTML', omitValueField: true, referencedParameters: listName,
                description: "所选${PHASE_TITLES[phase]}任务的失败策略和参数",
                script: groovyScript(sandboxedScript(optionsFormScript(tasks.collect { it.subMap(['name', 'description', 'policy', 'params']) }, listName),
                        'return "（参数表单生成失败）"')))
    }
    result << deployPlanParameter()
    return result
}

// 生成参数表单的脚本。Active Choices 提交时把所有 name="value" 的输入框的值用逗号连成一个字符串，
// 所以每个输入框前放一个隐藏的标记 @@任务名.参数名，解析时按标记切分，值中有逗号也不会错位。
// 插件的 HTML 过滤会去掉 fieldset、legend、label、title 和 aria-* 属性，所以分组用 div，必填用文字标出。
@NonCPS
String optionsFormScript(List tasks, String referencedParameter) {
    return """def tasks = ${groovyLiteral(tasks)}
def policies = ${groovyLiteral(POLICIES)}
def escapeHtml = { value -> groovy.xml.XmlUtil.escapeXml(String.valueOf(value == null ? '' : value)) }
def select = { List options, Object current ->
    '<select name="value">' + options.collect { option ->
        '<option value="' + escapeHtml(option) + '"' + (String.valueOf(option) == String.valueOf(current) ? ' selected' : '') + '>' + escapeHtml(option) + '</option>'
    }.join('') + '</select>'
}
def selected = String.valueOf(${referencedParameter} ?: '').tokenize(',').collect { it.trim() }
def chosen = tasks.findAll { it.name in selected }
if (!chosen) {
    return '<div>未选择任务</div>'
}
def html = []
chosen.each { task ->
    html << '<div style="border:1px solid #d1d5db;border-radius:6px;padding:8px 14px;margin:0 0 10px 0">'
    html << '<div style="margin-bottom:6px"><b>' + escapeHtml(task.name) + '</b> ' + escapeHtml(task.description) + '</div><table>'
    html << '<tr><td style="padding:3px 12px 3px 0">失败时</td><td>'
    html << '<input type="hidden" name="value" value="@@' + escapeHtml(task.name) + '.policy">' + select(policies, task.policy)
    html << ' abort：停止流水线；warn：标记不稳定后继续</td></tr>'
    task.params.each { param ->
        html << '<tr><td style="padding:3px 12px 3px 0">' + escapeHtml(param.name) + (param.required ? '（必填）' : '') + '</td><td>'
        html << '<input type="hidden" name="value" value="@@' + escapeHtml(task.name) + '.' + escapeHtml(param.name) + '">'
        if (param.choices) {
            html << select(param.choices, param.default)
        } else if (param.type == 'bool') {
            html << select(['true', 'false'], param.default == true)
        } else {
            // placeholder 是过滤后唯一能给输入框提供可访问名称的属性；下拉框没有这样的属性
            html << '<input type="' + (param.type == 'int' ? 'number' : 'text') + '" name="value" value="' + escapeHtml(param.default) + '"' +
                    ' placeholder="' + escapeHtml(param.name + (param.required ? '（必填）' : '')) + '" class="jenkins-input" style="width:320px">'
        }
        html << ' ' + escapeHtml(param.description) + '</td></tr>'
    }
    html << '</table></div>'
}
return html.join('')
"""
}

@NonCPS
Map activeChoicesSelection(Map context, Map requested, List errors) {
    Map phases = [:]
    PHASES.each { String phase ->
        phases[phase] = [names : splitList(requested[phaseParameter(phase, 'TASKS')]),
                         values: parseOptions(requested[phaseParameter(phase, 'TASK_OPTIONS')], errors)]
    }
    return selectionPlan(context, splitList(requested.TARGET_GROUPS), requested.SKIP_DEPLOY == true, phases, errors)
}

// 表单提交的值形如 "@@task_a.policy,abort,@@task_a.task_a_repeat,2"，转成 [task_a.policy: abort, ...]。
// Active Choices 拼接时按 JSON 字符串规则转义了每个值（" 变成 \"，\ 变成 \\），这里还原。
// 值里含 ",@@" 时会被切成假的字段（字段重复、不是"任务.字段"形式），此时报错而不是猜测。
@NonCPS
Map parseOptions(Object text, List errors) {
    Map values = [:]
    List parts = (text ?: '').toString().split('(?:^|,)@@') as List
    parts.eachWithIndex { String part, int index ->
        if (!part && index == 0) {
            return
        }
        int comma = part.indexOf(',')
        String key = comma > 0 ? part.substring(0, comma) : part
        if (!(key ==~ /[^.]+\.[^.]+/)) {
            errors << "表单字段 '${key}' 无效：参数值中不能含 ,@@"
            return
        }
        if (values.containsKey(key)) {
            errors << "表单字段 ${key} 出现多次：参数值中不能含 ,@@"
        }
        values[key] = comma > 0 ? jsonUnescape(part.substring(comma + 1)) : ''
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

// Jenkins 自带的参数页是静态的，不能随勾选显示或隐藏输入框，所以任务参数在构建开始后用 input 步骤填写
List basicParameters(Map context) {
    List result = context.groups.collect { String group, List hosts ->
        booleanParam(name: "TARGET_${group}", defaultValue: false, description: "发布对象：主机组 ${group}（${hosts.join(', ')}）")
    }
    result << skipDeployParameter()
    PHASES.each { String phase ->
        tasksForPhase(context, phase).each { Map task ->
            result << booleanParam(name: phaseParameter(phase, task.name), defaultValue: false,
                    description: "${PHASE_TITLES[phase]}任务：${task.description}")
        }
    }
    result << booleanParam(name: 'EDIT_TASK_OPTIONS', defaultValue: true,
            description: '构建开始后弹出表单，填写所选任务的失败策略和参数；不勾选则全部使用默认值')
    result << deployPlanParameter()
    return result
}

Map basicSelection(Map context, Map requested, List errors) {
    List targets = context.groups.keySet().findAll { requested["TARGET_${it}"] == true } as List
    Map phases = [:]
    List fields = []
    PHASES.each { String phase ->
        List names = tasksForPhase(context, phase).findAll { requested[phaseParameter(phase, it.name)] == true }*.name
        phases[phase] = [names: names, values: [:]]
        if (requested.EDIT_TASK_OPTIONS) {
            names.each { String name -> fields.addAll(taskInputs(phase, context.tasks[name])) }
        }
    }
    if (fields) {
        Object answer = timeout(time: INPUT_TIMEOUT_MINUTES, unit: 'MINUTES') {
            input(message: '填写所选任务的失败策略和参数', ok: '开始', parameters: fields*.input)
        }
        // 只有一个输入项时 input 直接返回该值，否则返回 [输入项名称: 值]
        Map answers = fields.size() == 1 ? [(fields[0].name): answer] : answer
        answers.each { String key, Object value ->
            int dot = key.indexOf('.')
            phases[key.substring(0, dot)].values[key.substring(dot + 1)] = String.valueOf(value)
        }
    }
    return selectionPlan(context, targets, requested.SKIP_DEPLOY == true, phases, errors)
}

// 一个任务在 input 表单中的输入项：[[name: 输入项名称, input: 参数定义], ...]，名称形如 pre.task_a.policy
List taskInputs(String phase, Map task) {
    String prefix = "${phase}.${task.name}."
    List fields = [[name : prefix + 'policy',
                    input: choice(name: prefix + 'policy', choices: defaultFirst(POLICIES, task.policy),
                            description: "${task.name}：失败时 abort 停止流水线，warn 标记不稳定后继续")]]
    task.params.each { Map param ->
        String name = prefix + param.name
        String description = "${task.name}：${param.description}${param.required ? '（必填）' : ''}"
        if (param.choices) {
            fields << [name: name, input: choice(name: name, choices: defaultFirst(param.choices, param.default), description: description)]
        } else if (param.type == 'bool') {
            fields << [name: name, input: booleanParam(name: name, defaultValue: param.default == true, description: description)]
        } else {
            fields << [name: name, input: string(name: name, defaultValue: param.default == null ? '' : String.valueOf(param.default), description: description)]
        }
    }
    return fields
}

// choice 参数以第一个选项为默认值
@NonCPS
List defaultFirst(List options, Object current) {
    return current == null ? options : [String.valueOf(current)] + (options - String.valueOf(current))
}

// ===================================================================================
// 计划：两种参数页共用的转换与校验（不需要工作区）
// ===================================================================================

def skipDeployParameter() {
    return booleanParam(name: 'SKIP_DEPLOY', defaultValue: false, description: '勾选后跳过部署，只执行所选的部署前、部署后任务')
}

def deployPlanParameter() {
    return text(name: 'DEPLOY_PLAN', defaultValue: '', description: '''YAML 格式的完整计划，非空时忽略上面所有参数，供 API / 上游 job 触发。格式：
targets: [主机组, ...]
skip_deploy: false
pre:
  - 任务名
  - {name: 任务名, id: 标识, policy: abort 或 warn, timeout_minutes: 分钟, params: {参数名: 值}}
post: 同 pre''')
}

// 得到本次执行计划。只用到不需要工作区的步骤：方案 C 在不分配执行器的 stage 中调用，等待 input 时不占执行器。
// 第一次运行（两种参数页都登记 DEPLOY_PLAN，它不存在即参数尚未登记）返回 null，结果 NOT_BUILT；
// 计划有误时一次列出全部问题，在运行任何 playbook 之前失败。
Map resolvePlan(Map request) {
    Map requested = request.requested
    if (!requested.containsKey('DEPLOY_PLAN')) {
        currentBuild.result = 'NOT_BUILT'
        currentBuild.description = '已登记构建参数，请用 Build with Parameters 重新运行'
        echo "[deploy] ${currentBuild.description}"
        return null
    }
    List errors = []
    Object draft = null
    String planText = requested.DEPLOY_PLAN?.trim()
    if (planText) {
        echo '[deploy] 使用 DEPLOY_PLAN，忽略参数页上的其他选择'
        try {
            draft = readYaml(text: planText)
        } catch (Exception e) {
            errors << "DEPLOY_PLAN 不是合法的 YAML：${e.message}"
        }
        if (!errors && !(draft instanceof Map)) {
            errors << 'DEPLOY_PLAN 必须是 YAML 映射'
        }
    } else if (request.form == 'activeChoices') {
        draft = activeChoicesSelection(request.context, requested, errors)
    } else {
        draft = basicSelection(request.context, requested, errors)
    }
    Map plan = draft instanceof Map ? buildPlan(draft, request.context, errors) : null
    if (errors) {
        error '[deploy] 计划有误：\n  - ' + errors.join('\n  - ')
    }
    echo describePlan(plan)
    return plan
}

// 表单中的值都是字符串（[任务名.字段: 值]），按参数类型转换后组成与 DEPLOY_PLAN 相同结构的计划草稿。
// 同一阶段的任务按任务目录的顺序排列，与选择的先后无关；未知任务名和未声明的字段原样放入，由 buildPlan 报错
@NonCPS
Map selectionPlan(Map context, List targets, boolean skipDeploy, Map phases, List errors) {
    Map plan = [targets: targets, skip_deploy: skipDeploy]
    phases.each { String phase, Map selection ->
        selection.values.keySet().findAll { !(it.tokenize('.')[0] in selection.names) }.each {
            errors << "表单字段 ${it} 属于未选择的任务"
        }
        List names = (context.tasks.keySet().findAll { it in selection.names } as List) +
                selection.names.findAll { !context.tasks.containsKey(it) }
        plan[phase] = names.collect { String name ->
            Map entry = [name: name]
            Map task = context.tasks[name]
            if (task == null) {
                return entry
            }
            Map declared = task.params.collectEntries { [(it.name): it] }
            Map params = [:]
            selection.values.each { String key, String value ->
                if (!key.startsWith(name + '.')) {
                    return
                }
                String field = key.substring(name.length() + 1)
                if (field == 'policy') {
                    entry.policy = value
                } else {
                    params[field] = declared[field] ? fromFormValue(value, declared[field]) : value
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

// 整数、布尔的空字符串表示未填写（使用默认值）；字符串参数的空字符串原样传入。整数无法转换时保留原字符串，由 buildPlan 报错
@NonCPS
Object fromFormValue(String value, Map param) {
    if (value == '' && param.type != 'str') {
        return null
    }
    if (param.type == 'int' && value.trim().isLong()) {
        return value.trim().toLong()
    }
    if (param.type == 'bool' && value in ['true', 'false']) {
        return value == 'true'
    }
    return value
}

// 校验计划草稿，并与任务的默认参数合并；发现的问题追加到 errors。
// 可选字段用 == null 判断是否提供，不用 ?:：Groovy 中 0、''、[] 为假，会被当成未提供而跳过类型检查。
@NonCPS
Map buildPlan(Map raw, Map context, List errors) {
    List unknownKeys = raw.keySet().findAll { !(it in PLAN_KEYS) } as List
    if (unknownKeys) {
        errors << "不支持的字段 ${unknownKeys}，可用字段 ${PLAN_KEYS}"
    }
    Object targets = raw.targets == null ? [] : raw.targets
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
    PHASES.each { String phase ->
        Object entries = raw[phase] == null ? [] : raw[phase]
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
    if (!(entry instanceof Map) || !(entry.name instanceof String)) {
        errors << "${where}：应为任务名，或包含 name 的映射"
        return null
    }
    List badKeys = entry.keySet().findAll { !(it in ENTRY_KEYS) } as List
    if (badKeys) {
        errors << "${where}：不支持的字段 ${badKeys}，可用字段 ${ENTRY_KEYS}"
    }
    Map task = context.tasks[entry.name]
    if (task == null) {
        errors << "${where}：未知任务 '${entry.name}'，可用任务 ${context.tasks.keySet()}"
        return null
    }
    if (!(phase in task.phases)) {
        errors << "${where}：任务 ${task.name} 不能用于 ${phase}，只能用于 ${task.phases}"
    }
    String id = entry.id == null ? task.name : String.valueOf(entry.id)
    if (!(id ==~ /[A-Za-z0-9_-]+/)) {
        errors << "${where}：id '${id}' 只能包含字母、数字、_、-"
    }
    String policy = entry.policy == null ? task.policy : String.valueOf(entry.policy)
    if (!(policy in POLICIES)) {
        errors << "${where}：未知策略 '${policy}'，只能是 ${POLICIES.join(' 或 ')}"
    }
    Object timeoutMinutes = entry.timeout_minutes == null ? task.timeoutMinutes : entry.timeout_minutes
    if (!(timeoutMinutes instanceof Integer) || timeoutMinutes < 1 || timeoutMinutes > MAX_TIMEOUT_MINUTES) {
        errors << "${where}：timeout_minutes 必须是 1 到 ${MAX_TIMEOUT_MINUTES} 的整数"
        timeoutMinutes = task.timeoutMinutes
    }
    Object given = entry.params == null ? [:] : entry.params
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
    task.params.each { Map param ->
        Object value = given[param.name] != null ? checkValue("${where}：参数 ${param.name}", given[param.name], param, errors) : param.default
        if (param.required && (value == null || value.toString().trim().isEmpty())) {
            errors << "${where}：任务 ${task.name} 必须提供参数 ${param.name}"
        }
        if (value != null) {
            params[param.name] = value
        }
    }
    return [id     : id, name: task.name, phase: phase, description: task.description, runOn: task.runOn,
            policy : policy, timeoutMinutes: timeoutMinutes, params: params]
}

// 按参数类型检查一个值；问题追加到 errors。Ansible 会渲染 extra vars 中的 Jinja 表达式，
// 能触发构建的人借此可以在 Jenkins 节点上执行命令，所以拒绝含模板语法的值。
// YAML 会把未加引号的 yes、2026-01-01、1.0 解析成布尔、日期、小数，str 参数只接受字符串和整数，避免静默转换。
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
            if (!(value instanceof String || value instanceof Integer || value instanceof Long)) {
                errors << "${label} 应为字符串（YAML 中请加引号），实际为 '${value}'"
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
    PHASES.each { String phase ->
        lines << "  ${PHASE_TITLES[phase]}任务：${plan[phase] ? '' : '（无）'}"
        plan[phase].each { Map task ->
            lines << "    - ${task.id} [${task.policy}] ${task.params}"
        }
    }
    return lines.join('\n')
}

@NonCPS
List tasksForPhase(Map context, String phase) {
    return context.tasks.values().findAll { phase in it.phases } as List
}

// ===================================================================================
// 执行（需要工作区）
// ===================================================================================

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
    writeJSON file: varsFile, json: task.params + [(TASK_ROLE_VAR) : task.name,
                                                    (TASK_HOSTS_VAR): task.runOn == 'targets' ? TARGETS_GROUP : 'localhost']
    Map args = [playbook: "${plan.baseDir}/ansible/run-task.yml", extras: "-e @${varsFile}"]
    if (task.runOn == 'targets') {
        args.limit = plan.targets.join(':')
    }
    Map result = [id: task.id, name: task.name, phase: task.phase, policy: task.policy, params: task.params]
    results << result
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
        // 超时与手动中止从 ansiblePlaybook 抛出时都是普通的 InterruptedException（catchError 同样分不清）；
        // timeout 的原因 ExceededTimeout 只能经 FlowInterruptedException.getCauses() 取得，它不在沙箱白名单中。
        // 所以用已耗时区分：超时按任务失败处理，手动中止照常抛出。超时后的宽限期内手动中止，也会算作超时
        if (System.currentTimeMillis() - started < task.timeoutMinutes * 60000L) {
            result.status = 'ABORTED'
            result.seconds = elapsedSeconds(started)
            throw e
        }
        failure = "超过 ${task.timeoutMinutes} 分钟未完成"
    } catch (Exception e) {
        // 插件内部的 IO 错误等：记入报告后照常抛出，构建失败
        result.status = 'ERROR'
        result.message = e.toString()
        result.seconds = elapsedSeconds(started)
        throw e
    }
    result.seconds = elapsedSeconds(started)
    result.status = failure ? 'FAILED' : 'PASSED'
    if (failure == null) {
        return
    }
    result.message = failure
    if (task.policy == 'abort') {
        result.outcome = 'stopped'
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
    } catch (InterruptedException e) {
        result.status = 'ABORTED'
        throw e
    } catch (Exception e) {
        result.status = 'ERROR'
        result.message = e.toString()
        throw e
    } finally {
        result.seconds = elapsedSeconds(started)
    }
}

// 插件在工作区根目录启动 ansible-playbook，那里没有 ansible.cfg，所以用 ANSIBLE_CONFIG 指定
def runPlaybook(Map plan, Map args) {
    Map common = [vaultCredentialsId: plan.ansible.vaultCredentialsId]
    if (plan.ansible.sshCredentialsId) {
        common.credentialsId = plan.ansible.sshCredentialsId
    }
    withEnv(["ANSIBLE_CONFIG=${env.WORKSPACE}/${plan.baseDir}/ansible/ansible.cfg"]) {
        ansiblePlaybook(common + args)
    }
}

def report(List results) {
    // 参数文件含本次的参数值，不留在可复用的工作区中
    dir('.deploy-run') {
        deleteDir()
    }
    if (!results) {
        return
    }
    echo formatReport(results)
    writeJSON file: 'deploy-report.json', json: results, pretty: 2
    archiveArtifacts artifacts: 'deploy-report.json'
}

@NonCPS
int elapsedSeconds(long started) {
    return (int) ((System.currentTimeMillis() - started) / 1000)
}

@NonCPS
String formatReport(List results) {
    List lines = ['[deploy] 结果：']
    results.each { Map row ->
        lines << "  ${row.phase.padRight(6)} ${row.id.padRight(16)} ${row.policy.padRight(5)} ${(row.status ?: '-').padRight(7)}" +
                " ${String.valueOf(row.seconds).padLeft(4)}s${row.outcome ? ' -> ' + row.outcome : ''}"
    }
    return lines.join('\n')
}

// 把 Map / List / 字符串 / 数字 / 布尔转成 Groovy 字面量，用于嵌入参数页脚本
@NonCPS
String groovyLiteral(Object value) {
    if (value instanceof Map) {
        return value ? '[' + value.collect { key, entry -> groovyLiteral(key.toString()) + ': ' + groovyLiteral(entry) }.join(', ') + ']' : '[:]'
    }
    if (value instanceof Collection) {
        return '[' + value.collect { groovyLiteral(it) }.join(', ') + ']'
    }
    if (value instanceof Number || value instanceof Boolean || value == null) {
        return String.valueOf(value)
    }
    return "'" + value.toString().replace('\\', '\\\\').replace("'", "\\'").replace('\n', '\\n').replace('\r', '\\r') + "'"
}

@NonCPS
List splitList(Object value) {
    return (value ?: '').toString().tokenize(',').collect { it.trim() }.findAll { it }
}

return this
