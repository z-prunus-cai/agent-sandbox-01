package deploy

import com.cloudbees.groovy.cps.NonCPS

/** The tasks found under business/tasks and the deploy target groups of the inventory. */
class Catalog implements Serializable {
    private static final List<String> TASK_KEYS = ['description', 'phases', 'access', 'menu', 'run_on', 'policy',
                                                   'timeout_minutes', 'order', 'review']
    private static final List<String> OPTION_KEYS = ['type', 'required', 'default', 'choices', 'description']

    List<TaskDefinition> tasks
    Map<String, List<String>> groups

    /**
     * @param sources task name -> [task: meta/task.yml, spec: meta/argument_specs.yml or null, defaults: defaults/main.yml or
     *                null, role: whether tasks/main.yml exists]
     */
    @NonCPS
    static Catalog parse(Map<String, Map> sources, Map inventory, List<String> errors) {
        List<TaskDefinition> tasks = sources.collect { String name, Map source -> Catalog.definition(name, source, errors) }
        tasks.groupBy { it.order }.findAll { order, same -> same.size() > 1 }.each { order, same ->
            errors << "tasks ${same.collect { it.name }} share order ${order}".toString()
        }
        return new Catalog(tasks: tasks.sort { it.order }, groups: targetGroups(inventory, errors))
    }

    @NonCPS
    List<TaskDefinition> tasksFor(Phase phase, AccessMode mode) {
        return tasks.findAll { phase in it.phases && mode.allows(it.access) }
    }

    @NonCPS
    TaskDefinition task(String name) {
        return tasks.find { it.name == name }
    }

    @NonCPS
    List<String> parameterNames() {
        return tasks.collectMany { task -> task.parameters.collect { it.name } }
    }

    @NonCPS
    static TaskDefinition definition(String name, Map source, List<String> errors) {
        String where = "task ${name}"
        // the name is also the role name and the first part of the form keys "task.field"
        if (!(name ==~ /[a-z][a-z0-9_]*/)) errors << "${where}: the directory name may hold only a-z, 0-9 and _, starting with a letter".toString()
        if (!source.role) errors << "${where}: business/tasks/${name}/tasks/main.yml is missing".toString()
        Map meta = source.task instanceof Map ? source.task : [:]
        List unknownKeys = meta.keySet().findAll { !(it in TASK_KEYS) } as List
        if (unknownKeys) errors << "${where}: unknown keys ${unknownKeys} in meta/task.yml, expected ${TASK_KEYS}".toString()
        if (source.spec == null) errors << "${where}: business/tasks/${name}/meta/argument_specs.yml is missing".toString()
        List<Phase> phases = meta.phases instanceof List && meta.phases ?
                meta.phases.collect { Labels.find(Phase.values() as List, it, "${where}: phase", errors) }.findAll { it != null } : []
        if (!phases) errors << "${where}: phases must be a non-empty list of ${Phase.values().collect { it.label }}".toString()
        Map review = meta.review instanceof Map ? meta.review : null
        if (meta.review != null && review == null) errors << "${where}: review must be a mapping with timeout_minutes".toString()
        return new TaskDefinition(
                name: name,
                description: (meta.description ?: name).toString(),
                phases: phases,
                access: Labels.find(TaskAccess.values() as List, meta.access, "${where}: access", errors),
                menu: Labels.find(TaskMenu.values() as List, meta.menu, "${where}: menu", errors),
                runOn: Labels.find(RunLocation.values() as List, meta.run_on, "${where}: run_on", errors),
                policy: Labels.find(FailurePolicy.values() as List, meta.policy, "${where}: policy", errors),
                timeoutMinutes: minutes(meta.timeout_minutes, "${where}: timeout_minutes", errors),
                order: meta.order instanceof Integer ? meta.order : orderError(where, errors),
                reviewTimeoutMinutes: review == null ? null : minutes(review.timeout_minutes, "${where}: review.timeout_minutes", errors),
                parameters: parameters(name, source.spec, source.defaults ?: [:], errors))
    }

    @NonCPS
    static int orderError(String where, List<String> errors) {
        errors << "${where}: order must be an integer".toString()
        return 0
    }

    @NonCPS
    static int minutes(Object value, String where, List<String> errors) {
        if (value instanceof Integer && value >= 1 && value <= Limits.MAX_TIMEOUT_MINUTES) return value
        errors << "${where} must be an integer from 1 to ${Limits.MAX_TIMEOUT_MINUTES}, got '${value}'".toString()
        return 1
    }

    @NonCPS
    static List<ParameterDefinition> parameters(String task, Map spec, Map defaults, List<String> errors) {
        Map options = spec?.argument_specs?.main?.options ?: [:]
        List<String> engineVariables = EngineVariable.labels()
        defaults.keySet().findAll { !options.containsKey(it) }.each {
            errors << "task ${task}: defaults/main.yml sets ${it}, which meta/argument_specs.yml does not declare".toString()
        }
        return options.findAll { key, body -> Catalog.optionShape(task, key, body, errors) }.collect { String name, Map option ->
            String where = "task ${task}: parameter ${name}"
            // Parameters arrive as extra vars, which outrank every other variable, so they carry the task's prefix
            if (!name.startsWith(task + '_') || name in engineVariables || name.startsWith('ansible_') || name in AnsibleNames.RESERVED) {
                errors << "${where}: the name must start with ${task}_ and must not be ${engineVariables}, ansible_* or a name Ansible reserves".toString()
            }
            List unknownKeys = option.keySet().findAll { !(it in OPTION_KEYS) } as List
            if (unknownKeys) errors << "${where}: unknown keys ${unknownKeys}, expected ${OPTION_KEYS}".toString()
            ParameterType type = Labels.find(ParameterType.values() as List, (option.type ?: 'str').toString(), "${where}: type", errors)
            if (option.choices && type != ParameterType.STRING) errors << "${where}: choices are allowed only for str".toString()
            ParameterDefinition parameter = new ParameterDefinition(
                    name: name, type: type, required: option.required == true,
                    description: (option.description instanceof List ? option.description.join(' ') : (option.description ?: '')).toString(),
                    choices: option.choices ? option.choices.collect { it.toString() } : null,
                    defaultValue: defaults[name])
            if (parameter.defaultValue != null && type != null) {
                // defaults reach Ansible as extra vars too, so they pass the same checks and conversion as typed values
                parameter.defaultValue = Values.check("${where}: default in defaults/main.yml", parameter.defaultValue, parameter, errors)
            }
            return parameter
        }
    }

    @NonCPS
    static boolean optionShape(String task, Object name, Object body, List<String> errors) {
        if (name instanceof String && body instanceof Map) return true
        errors << "task ${task}: option '${name}' in meta/argument_specs.yml must be a name with a mapping".toString()
        return false
    }

    @NonCPS
    static Map<String, List<String>> targetGroups(Map inventory, List<String> errors) {
        String targets = RunLocation.TARGETS.hosts
        Map children = inventory?.all?.children?.get(targets)?.children
        if (!(children instanceof Map) || children.isEmpty()) {
            errors << "business/inventory/hosts.yml has no all.children.${targets}.children".toString()
            return [:]
        }
        Map<String, List<String>> groups = children.collectEntries { String group, Object body ->
            [(group): ((body instanceof Map ? body.hosts : null) ?: [:]).keySet() as List]
        }
        groups.findAll { group, hosts -> !hosts }.each { group, hosts ->
            errors << "business/inventory/hosts.yml: group ${group} has no hosts".toString()
        }
        return groups
    }
}
