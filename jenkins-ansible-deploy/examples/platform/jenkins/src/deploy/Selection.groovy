package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Drafts what was chosen on a form into the shape of a DEPLOY_PLAN document, so PlanParser checks both the same way. */
class Selection {

    /**
     * @param chosen phase -> [names: selected task names, values: ["task.field": text from the form]]
     */
    @NonCPS
    static Map draft(Catalog catalog, List<String> targets, boolean skipDeploy, Map<Phase, Map> chosen, List<String> errors) {
        Map raw = [targets: targets]
        if (skipDeploy) raw.skip_deploy = true
        chosen.each { Phase phase, Map selection ->
            List<String> names = selection.names
            selection.values.keySet().findAll { !(it.tokenize('.')[0] in names) }.each {
                errors << "form field ${it} belongs to a task that is not selected".toString()
            }
            // a phase runs in catalog order; unknown names keep their place at the end for PlanParser to report
            List<String> ordered = catalog.tasks.collect { it.name }.findAll { it in names } + names.findAll { catalog.task(it) == null }
            raw[phase.label] = ordered.collect { String name -> Selection.entry(name, catalog.task(name), selection.values) }
        }
        return raw
    }

    @NonCPS
    static Map entry(String name, TaskDefinition task, Map<String, String> values) {
        Map entry = [name: name]
        if (task == null) return entry
        Map params = [:]
        values.each { String key, String value ->
            if (!key.startsWith(name + '.')) return
            String field = key.substring(name.length() + 1)
            if (field == 'policy') {
                entry.policy = value
                return
            }
            ParameterDefinition parameter = task.parameter(field)
            Object converted = parameter == null ? value : Values.fromForm(value, parameter)
            if (converted != null) params[field] = converted
        }
        if (params) entry.params = params
        return entry
    }

    @NonCPS
    static List<String> splitList(Object value) {
        return (value ?: '').toString().tokenize(',').collect { it.trim() }.findAll { it }
    }
}
