package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Turns a DEPLOY_PLAN document, or a form selection drafted into the same shape, into a Plan; every problem goes to errors. */
class PlanParser {
    private static final List<String> PLAN_KEYS = ['targets', 'skip_deploy', 'pre', 'post']
    private static final List<String> ENTRY_KEYS = ['name', 'id', 'policy', 'params', 'timeout_minutes']

    @NonCPS
    static Plan parse(Map raw, Catalog catalog, AccessMode mode, List<String> errors) {
        List<String> keys = mode.deploys() ? PLAN_KEYS : PLAN_KEYS - 'skip_deploy'
        List unknownKeys = raw.keySet().findAll { !(it in keys) } as List
        if (unknownKeys) errors << "unknown keys ${unknownKeys}, expected ${keys}".toString()
        Object targets = raw.targets == null ? [] : raw.targets
        if (!(targets instanceof List)) {
            errors << 'targets must be a list'
            targets = []
        }
        List unknownGroups = targets.findAll { !catalog.groups.containsKey(it) } as List
        if (unknownGroups) errors << "unknown groups ${unknownGroups}, expected ${catalog.groups.keySet()}".toString()
        if (raw.skip_deploy != null && !(raw.skip_deploy instanceof Boolean)) errors << 'skip_deploy must be true or false'
        Map<String, List<PlannedTask>> tasks = [:]
        int entries = 0
        Phase.values().each { Phase phase ->
            Object items = raw[phase.label] == null ? [] : raw[phase.label]
            tasks[phase.label] = []
            if (!(items instanceof List)) {
                errors << "${phase.label} must be a list".toString()
                return
            }
            entries += items.size()
            List<String> ids = []
            items.eachWithIndex { Object item, int index ->
                PlannedTask planned = PlanParser.entry("${phase.label}[${index}]", phase, item, catalog, mode, errors)
                if (planned == null) return
                // ids need to be unique within a phase only: stage names, file names and the report all carry the phase
                if (planned.id in ids) errors << "${phase.label}[${index}]: id '${planned.id}' is used twice; give repeated tasks their own id".toString()
                ids << planned.id
                tasks[phase.label] << planned
            }
        }
        if (entries > Limits.MAX_PLAN_ENTRIES) errors << "the plan has ${entries} tasks, at most ${Limits.MAX_PLAN_ENTRIES} are allowed".toString()
        boolean deploy = mode.deploys() && raw.skip_deploy != true
        Plan plan = new Plan(targets: targets.collect { it.toString() }, deploy: deploy, tasks: tasks)
        boolean needsTargets = deploy || tasks.values().any { List<PlannedTask> planned -> planned.any { it.task.runOn == RunLocation.TARGETS } }
        if (needsTargets && !plan.targets) errors << 'no group selected: deploying and tasks that run on targets need at least one group'
        return plan
    }

    @NonCPS
    static PlannedTask entry(String where, Phase phase, Object item, Catalog catalog, AccessMode mode, List<String> errors) {
        Object entry = item instanceof String ? [name: item] : item
        if (!(entry instanceof Map) || !(entry.name instanceof String)) {
            errors << "${where}: expected a task name or a mapping with name".toString()
            return null
        }
        List badKeys = entry.keySet().findAll { !(it in ENTRY_KEYS) } as List
        if (badKeys) errors << "${where}: unknown keys ${badKeys}, expected ${ENTRY_KEYS}".toString()
        TaskDefinition task = catalog.task(entry.name)
        if (task == null) {
            errors << "${where}: unknown task '${entry.name}', expected one of ${catalog.tasks.collect { it.name }}".toString()
            return null
        }
        if (!mode.allows(task.access)) errors << "${where}: task ${task.name} writes, and this job is ${mode.label}".toString()
        if (!(phase in task.phases)) errors << "${where}: task ${task.name} cannot run in ${phase.label}, only in ${task.phases.collect { it.label }}".toString()
        String id = entry.id == null ? task.name : String.valueOf(entry.id)
        if (!(id ==~ /[A-Za-z0-9_-]{1,${Limits.MAX_ID_CHARACTERS}}/)) {
            errors << "${where}: id '${id}' may hold only letters, digits, _ and -, at most ${Limits.MAX_ID_CHARACTERS} characters".toString()
        }
        FailurePolicy policy = entry.policy == null ? task.policy : Labels.find(FailurePolicy.values() as List, entry.policy, "${where}: policy", errors)
        int timeoutMinutes = entry.timeout_minutes == null ? task.timeoutMinutes : Catalog.minutes(entry.timeout_minutes, "${where}: timeout_minutes", errors)
        Object given = entry.params == null ? [:] : entry.params
        if (!(given instanceof Map)) {
            errors << "${where}: params must be a mapping".toString()
            given = [:]
        }
        List unknownParams = given.keySet().findAll { task.parameter(it) == null } as List
        if (unknownParams) errors << "${where}: task ${task.name} takes no parameters ${unknownParams}, expected ${task.parameters.collect { it.name }}".toString()
        Map<String, Object> params = [:]
        task.parameters.each { ParameterDefinition parameter ->
            Object value = given[parameter.name] != null ?
                    Values.check("${where}: parameter ${parameter.name}", given[parameter.name], parameter, errors) : parameter.defaultValue
            if (parameter.required && (value == null || value.toString().trim().isEmpty())) {
                errors << "${where}: task ${task.name} needs parameter ${parameter.name}".toString()
            }
            if (value != null) params[parameter.name] = value
        }
        return new PlannedTask(id: id, task: task, phase: phase, policy: policy, timeoutMinutes: timeoutMinutes, params: params)
    }
}
