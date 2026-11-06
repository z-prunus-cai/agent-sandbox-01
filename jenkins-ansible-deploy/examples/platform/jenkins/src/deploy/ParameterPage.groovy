package deploy

/** The build parameters of a job, and reading back what was chosen on them. Uses pipeline steps through script. */
class ParameterPage implements Serializable {
    public static final String TARGETS_PARAMETER = 'TARGET_GROUPS'
    public static final String BASIC_TARGET_PREFIX = 'TARGET_'
    public static final String SKIP_DEPLOY_PARAMETER = 'SKIP_DEPLOY'
    public static final String EDIT_OPTIONS_PARAMETER = 'EDIT_TASK_OPTIONS'

    private final def script
    private final Catalog catalog
    private final AccessMode mode
    private final ParameterForm form

    ParameterPage(def script, Catalog catalog, AccessMode mode, ParameterForm form) {
        this.script = script
        this.catalog = catalog
        this.mode = mode
        this.form = form
    }

    List definitions() {
        List result = []
        switch (form) {
            case ParameterForm.ACTIVE_CHOICES:
                result << script.activeChoice(name: TARGETS_PARAMETER, randomName: 'choice-target-groups', choiceType: 'PT_CHECKBOX',
                        filterable: false, filterLength: 1, description: "Deploy targets: groups under ${Catalog.TARGETS_GROUP} in the inventory",
                        script: sandboxed(ActiveChoicesForm.groupsScript(catalog.groups), 'return ["(group list failed)"]'))
                break
            case ParameterForm.BASIC:
                catalog.groups.each { String group, List<String> hosts ->
                    result << script.booleanParam(name: BASIC_TARGET_PREFIX + group, defaultValue: false,
                            description: "Deploy target: group ${group} (${hosts.join(', ')})")
                }
                break
        }
        if (mode.deploys()) {
            result << script.booleanParam(name: SKIP_DEPLOY_PARAMETER, defaultValue: false,
                    description: 'Skip the deployment and run only the selected tasks')
        }
        for (Phase phase in Phase.values()) {
            List<TaskDefinition> tasks = catalog.tasksFor(phase, mode)
            switch (form) {
                case ParameterForm.ACTIVE_CHOICES:
                    List<TaskDefinition> more = tasks.findAll { it.menu == TaskMenu.MORE }
                    List<String> lists = [phase.tasksParameter]
                    result << script.activeChoice(name: phase.tasksParameter, randomName: "choice-${phase.label}-tasks", choiceType: 'PT_CHECKBOX',
                            filterable: true, filterLength: 1, description: "${phase.title} tasks",
                            script: sandboxed(ActiveChoicesForm.tasksScript(tasks.findAll { it.menu == TaskMenu.MAIN }), 'return ["(task list failed)"]'))
                    if (more) {
                        lists << phase.moreTasksParameter
                        result << script.booleanParam(name: phase.showMoreParameter, defaultValue: false,
                                description: "Show the ${more.size()} less used ${phase.title.toLowerCase()} tasks")
                        // a list that empties when the box is unticked: the plugin cannot fold part of one checkbox list
                        result << script.reactiveChoice(name: phase.moreTasksParameter, randomName: "choice-${phase.label}-more-tasks",
                                choiceType: 'PT_CHECKBOX', filterable: true, filterLength: 1, referencedParameters: phase.showMoreParameter,
                                description: "Less used ${phase.title.toLowerCase()} tasks",
                                script: sandboxed(ActiveChoicesForm.moreTasksScript(more, phase.showMoreParameter), 'return ["(task list failed)"]'))
                    }
                    result << script.activeChoiceHtml(name: phase.optionsParameter, randomName: "choice-${phase.label}-task-options",
                            choiceType: 'ET_FORMATTED_HTML', omitValueField: true, referencedParameters: lists.join(','),
                            description: "Order, failure policy and parameters of the selected ${phase.title.toLowerCase()} tasks",
                            script: sandboxed(ActiveChoicesForm.optionsScript(tasks, lists), 'return "(form failed)"'))
                    break
                case ParameterForm.BASIC:
                    for (TaskDefinition task in tasks) {
                        result << script.booleanParam(name: phase.basicParameterPrefix + task.name, defaultValue: false,
                                description: "${phase.title} task: ${task.description}")
                    }
                    break
            }
        }
        if (form == ParameterForm.BASIC) {
            result << script.booleanParam(name: EDIT_OPTIONS_PARAMETER, defaultValue: true,
                    description: 'Ask for the failure policy and parameters of the selected tasks after the build starts; unticked, every task uses its defaults')
        }
        result << script.text(name: Limits.PLAN_PARAMETER, defaultValue: '',
                description: 'A complete plan in YAML; when not empty, every other parameter is ignored. For the API and upstream jobs.')
        return result
    }

    /** Reads the form into a DEPLOY_PLAN-shaped draft. The basic form asks for task options with input, holding no executor. */
    Map draft(Map requested, List<String> errors) {
        switch (form) {
            case ParameterForm.ACTIVE_CHOICES:
                Map<Phase, Map> chosen = [:]
                for (Phase phase in Phase.values()) {
                    chosen[phase] = [names : Selection.splitList(requested[phase.tasksParameter]) + Selection.splitList(requested[phase.moreTasksParameter]),
                                     values: ActiveChoicesForm.parseOptions(requested[phase.optionsParameter], errors)]
                }
                return Selection.draft(catalog, Selection.splitList(requested[TARGETS_PARAMETER]), skipDeploy(requested), chosen, errors)
            case ParameterForm.BASIC:
                return basicDraft(requested, errors)
        }
        throw new IllegalStateException(form.name())
    }

    private Map basicDraft(Map requested, List<String> errors) {
        List<String> targets = catalog.groups.keySet().findAll { requested[BASIC_TARGET_PREFIX + it] == true } as List
        Map<Phase, Map> chosen = [:]
        List fields = []
        for (Phase phase in Phase.values()) {
            List<String> names = catalog.tasksFor(phase, mode).findAll { requested[phase.basicParameterPrefix + it.name] == true }.collect { it.name }
            chosen[phase] = [names: names, values: [:]]
            if (requested[EDIT_OPTIONS_PARAMETER]) {
                for (String name in names) fields.addAll(inputFields(phase, catalog.task(name)))
            }
        }
        if (fields) {
            Object answer = script.timeout(time: Limits.OPTIONS_FORM_TIMEOUT_MINUTES, unit: 'MINUTES') {
                script.input(message: 'Failure policy and parameters of the selected tasks', ok: 'Start', parameters: fields.collect { it.input })
            }
            // with a single field input returns its value, otherwise [field name: value]
            Map answers = fields.size() == 1 ? [(fields[0].name): answer] : answer
            answers.each { String key, Object value ->
                int dot = key.indexOf('.')
                Phase phase = Phase.values().find { it.label == key.substring(0, dot) }
                chosen[phase].values[key.substring(dot + 1)] = String.valueOf(value)
            }
        }
        return Selection.draft(catalog, targets, skipDeploy(requested), chosen, errors)
    }

    /** [[name: field name such as pre.task_a.policy, input: parameter definition], ...] */
    private List<Map> inputFields(Phase phase, TaskDefinition task) {
        String prefix = "${phase.label}.${task.name}."
        List<String> policies = FailurePolicy.values().collect { it.label }
        List<Map> fields = [[name : prefix + 'policy',
                             input: script.choice(name: prefix + 'policy', choices: defaultFirst(policies, task.policy.label),
                                     description: "${task.name}: on failure, abort stops the pipeline, warn marks it unstable and continues")]]
        for (ParameterDefinition parameter in task.parameters) {
            String name = prefix + parameter.name
            String description = "${task.name}: ${parameter.description}${parameter.required ? ' (required)' : ''}"
            if (parameter.choices) {
                fields << [name: name, input: script.choice(name: name, choices: defaultFirst(parameter.choices, parameter.defaultValue), description: description)]
            } else if (parameter.type == ParameterType.BOOLEAN) {
                fields << [name: name, input: script.booleanParam(name: name, defaultValue: parameter.defaultValue == true, description: description)]
            } else {
                fields << [name: name, input: script.string(name: name, defaultValue: parameter.defaultValue == null ? '' : String.valueOf(parameter.defaultValue), description: description)]
            }
        }
        return fields
    }

    private boolean skipDeploy(Map requested) {
        return mode.deploys() && requested[SKIP_DEPLOY_PARAMETER] == true
    }

    // Active Choices scripts run in the Groovy sandbox, so they need no approval
    private Object sandboxed(String text, String fallback) {
        return script.groovyScript(script: [classpath: [], sandbox: true, script: text], fallbackScript: [classpath: [], sandbox: true, script: fallback])
    }

    // a choice parameter defaults to its first option
    @com.cloudbees.groovy.cps.NonCPS
    static List<String> defaultFirst(List<String> options, Object current) {
        return current == null ? options : [String.valueOf(current)] + (options - String.valueOf(current))
    }
}
