package deploy

import com.cloudbees.groovy.cps.NonCPS

/** The build parameters of a job, and reading back what was chosen on them. Uses pipeline steps through script. */
class ParameterPage implements Serializable {
    public static final String TARGETS_PARAMETER = 'TARGET_GROUPS'
    public static final String BASIC_TARGET_PREFIX = 'TARGET_'
    public static final String SKIP_DEPLOY_PARAMETER = 'SKIP_DEPLOY'
    public static final String EDIT_OPTIONS_PARAMETER = 'EDIT_TASK_OPTIONS'
    public static final String PLAN_PARAMETER = 'DEPLOY_PLAN'

    private final def script
    private final Catalog catalog
    // labels, not enums: no enum may be part of the build state (see PlannedTask)
    private final String modeLabel
    private final String formLabel

    ParameterPage(def script, Catalog catalog, AccessMode mode, ParameterForm form) {
        this.script = script
        this.catalog = catalog
        this.modeLabel = mode.label
        this.formLabel = form.label
    }

    @NonCPS
    private AccessMode mode() {
        return Labels.of(AccessMode.values() as List, modeLabel)
    }

    @NonCPS
    private ParameterForm form() {
        return Labels.of(ParameterForm.values() as List, formLabel)
    }

    List definitions() {
        List result = []
        switch (form()) {
            case ParameterForm.ACTIVE_CHOICES:
                result << script.activeChoice(name: TARGETS_PARAMETER, randomName: 'choice-target-groups', choiceType: 'PT_CHECKBOX',
                        filterable: false, filterLength: 1, description: "Deploy targets: groups under ${RunLocation.TARGETS.hosts} in the inventory",
                        script: sandboxed(ActiveChoicesForm.groupsScript(catalog.groups), 'return ["(group list failed)"]'))
                break
            case ParameterForm.BASIC:
                catalog.groups.each { String group, List<String> hosts ->
                    result << script.booleanParam(name: BASIC_TARGET_PREFIX + group, defaultValue: false,
                            description: "Deploy target: group ${group} (${hosts.join(', ')})")
                }
                break
            default:
                throw new IllegalStateException(form().name())
        }
        if (mode().deploys()) {
            result << script.booleanParam(name: SKIP_DEPLOY_PARAMETER, defaultValue: false,
                    description: 'Skip the deployment and run only the selected tasks')
        }
        for (Phase phase in Phase.values()) {
            List<TaskDefinition> tasks = catalog.tasksFor(phase, mode())
            switch (form()) {
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
                default:
                    throw new IllegalStateException(form().name())
            }
        }
        if (form() == ParameterForm.BASIC) {
            result << script.booleanParam(name: EDIT_OPTIONS_PARAMETER, defaultValue: true,
                    description: 'Ask for the failure policy and parameters of the selected tasks after the build starts; unticked, every task uses its defaults')
        }
        result << script.text(name: PLAN_PARAMETER, defaultValue: '',
                description: 'A complete plan in YAML; when not empty, every other parameter is ignored. For the API and upstream jobs.')
        return result
    }

    /** Reads the form into a DEPLOY_PLAN-shaped draft. The basic form asks for task options with input, holding no executor. */
    Map draft(Map requested, List<String> errors) {
        // no switch on the form here: its value would stay in the build state while the basic form waits (see PlannedTask)
        return basicForm() ? basicDraft(requested, errors) : activeChoicesDraft(requested, errors)
    }

    @NonCPS
    private boolean basicForm() {
        switch (form()) {
            case ParameterForm.ACTIVE_CHOICES: return false
            case ParameterForm.BASIC: return true
        }
        throw new IllegalStateException(form().name())
    }

    @NonCPS
    private Map activeChoicesDraft(Map requested, List<String> errors) {
        Map<Phase, Map> chosen = [:]
        for (Phase phase in Phase.values()) {
            chosen[phase] = [names : Selection.splitList(ParameterPage.bounded(requested, phase.tasksParameter, errors)) +
                                     Selection.splitList(ParameterPage.bounded(requested, phase.moreTasksParameter, errors)),
                             values: ActiveChoicesForm.parseOptions(ParameterPage.bounded(requested, phase.optionsParameter, errors), errors)]
        }
        return Selection.draft(catalog, Selection.splitList(ParameterPage.bounded(requested, TARGETS_PARAMETER, errors)), skipDeploy(requested), chosen, errors)
    }

    /** A form value, bounded like DEPLOY_PLAN before it is split; an oversized value is reported and read as empty. */
    @NonCPS
    static String bounded(Map requested, String name, List<String> errors) {
        String value = requested[name] == null ? '' : requested[name].toString()
        if (value.length() <= Limits.MAX_PLAN_CHARACTERS) return value
        errors << "${name} is longer than ${Limits.MAX_PLAN_CHARACTERS} characters".toString()
        return ''
    }

    private Map basicDraft(Map requested, List<String> errors) {
        List<String> targets = catalog.groups.keySet().findAll { requested[BASIC_TARGET_PREFIX + it] == true } as List
        // keyed by phase label: this method waits in input, and no enum may be part of the build state then
        Map<String, Map> chosen = basicSelection(requested)
        List fields = []
        if (requested[EDIT_OPTIONS_PARAMETER]) {
            for (String phaseLabel in chosen.keySet()) {
                for (String name in chosen[phaseLabel].names) fields.addAll(inputFields(phaseLabel, catalog.task(name)))
            }
        }
        if (fields) {
            Object answer = script.timeout(time: Limits.OPTIONS_FORM_TIMEOUT_MINUTES, unit: 'MINUTES') {
                script.input(message: 'Failure policy and parameters of the selected tasks', ok: 'Start', parameters: fields.collect { it.input })
            }
            // with a single field input returns its value, otherwise [field name: value]
            Map answers = fields.size() == 1 ? [(fields[0].name): answer] : answer
            answers.each { String key, Object value ->
                // the input form's keys are "phase.task.field": the phase, then a form key of the task
                int dot = key.indexOf('.')
                chosen[key.substring(0, dot)].values[key.substring(dot + 1)] = String.valueOf(value)
            }
        }
        return Selection.draft(catalog, targets, skipDeploy(requested), ParameterPage.byPhase(chosen), errors)
    }

    @NonCPS
    private Map<String, Map> basicSelection(Map requested) {
        return (Phase.values() as List).collectEntries { Phase phase ->
            [(phase.label): [names : catalog.tasksFor(phase, mode()).findAll { requested[phase.basicParameterPrefix + it.name] == true }.collect { it.name },
                             values: [:]]]
        }
    }

    @NonCPS
    static Map<Phase, Map> byPhase(Map<String, Map> chosen) {
        return chosen.collectEntries { String label, Map entry -> [(Labels.of(Phase.values() as List, label)): entry] }
    }

    /** [[name: field name such as pre.task_a.policy, input: parameter definition], ...] */
    private List<Map> inputFields(String phase, TaskDefinition task) {
        String policyName = FormField.phasedKey(phase, task.name, FormField.POLICY.label)
        List<String> policies = FailurePolicy.values().collect { it.label }
        List<Map> fields = [[name : policyName,
                             input: script.choice(name: policyName, choices: defaultFirst(policies, task.policy.label),
                                     description: "${task.name}: on failure, ${FailurePolicy.help()}")]]
        for (ParameterDefinition parameter in task.parameters) {
            String name = FormField.phasedKey(phase, task.name, parameter.name)
            String description = "${task.name}: ${parameter.description}${parameter.required ? ParameterDefinition.REQUIRED_SUFFIX : ''}"
            fields << [name: name, input: inputField(name, parameter, description)]
        }
        return fields
    }

    private Object inputField(String name, ParameterDefinition parameter, String description) {
        if (parameter.choices) return script.choice(name: name, choices: defaultFirst(parameter.choices, parameter.defaultValue), description: description)
        switch (parameter.type) {
            case ParameterType.BOOLEAN:
                return script.booleanParam(name: name, defaultValue: parameter.defaultValue == true, description: description)
            case ParameterType.STRING:
            case ParameterType.INTEGER:
                return script.string(name: name, defaultValue: parameter.defaultValue == null ? '' : String.valueOf(parameter.defaultValue), description: description)
        }
        throw new IllegalStateException(String.valueOf(parameter.type))
    }

    @NonCPS
    private boolean skipDeploy(Map requested) {
        return mode().deploys() && requested[SKIP_DEPLOY_PARAMETER] == true
    }

    // Active Choices scripts run in the Groovy sandbox, so they need no approval
    private Object sandboxed(String text, String fallback) {
        return script.groovyScript(script: [classpath: [], sandbox: true, script: text], fallbackScript: [classpath: [], sandbox: true, script: fallback])
    }

    // a choice parameter defaults to its first option
    @NonCPS
    static List<String> defaultFirst(List<String> options, Object current) {
        return current == null ? options : [String.valueOf(current)] + (options - String.valueOf(current))
    }
}
