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
            selection.values.keySet().findAll { !(FormField.split(it)?.task in names) }.each {
                errors << "form field ${it} belongs to a task that is not selected".toString()
            }
            // a phase runs by the order chosen on the form, else catalog order; unknown names go last for PlanParser to report
            List<TaskDefinition> known = catalog.tasks.findAll { it.name in names }
            Map<String, Integer> chosenOrder = known.collectEntries { TaskDefinition task ->
                [(task.name): Selection.order(task, selection.values[FormField.ORDER.key(task.name)], errors)]
            }
            List<TaskDefinition> sorted = [] + known
            sorted.sort { chosenOrder[it.name] }
            List<String> ordered = sorted.collect { it.name } + names.findAll { catalog.task(it) == null }
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
            Map<String, String> parts = FormField.split(key)
            if (parts?.task != name) return
            FormField field = FormField.of(parts.field)
            if (field == null) {
                ParameterDefinition parameter = task.parameter(parts.field)
                Object converted = parameter == null ? value : Values.fromForm(value, parameter)
                if (converted != null) params[parts.field] = converted
                return
            }
            switch (field) {
                case FormField.POLICY:
                    entry.policy = value
                    break
                case FormField.ORDER:
                    // Selection.draft has already sorted by it
                    break
                default:
                    throw new IllegalStateException(field.name())
            }
        }
        if (params) entry.params = params
        return entry
    }

    /** The order typed on the form, or the catalog order when the field is empty or absent. */
    @NonCPS
    static int order(TaskDefinition task, String value, List<String> errors) {
        if (value == null || value.trim() == '') return task.order
        if (value.trim().isInteger()) return value.trim().toInteger()
        errors << "form field ${FormField.ORDER.key(task.name)} must be an integer, got '${value}'".toString()
        return task.order
    }

    @NonCPS
    static List<String> splitList(Object value) {
        return (value ?: '').toString().tokenize(',').collect { it.trim() }.findAll { it }
    }
}
