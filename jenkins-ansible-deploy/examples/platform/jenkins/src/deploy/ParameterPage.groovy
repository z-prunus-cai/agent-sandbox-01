package deploy

import com.cloudbees.groovy.cps.NonCPS

/** The build parameters of a job, and reading back what was chosen on them. Uses pipeline steps through script. */
class ParameterPage implements Serializable {
    public static final String TARGETS_PARAMETER = 'TARGET_GROUPS'
    public static final String SKIP_DEPLOY_PARAMETER = 'SKIP_DEPLOY'
    public static final String PLAN_PARAMETER = 'DEPLOY_PLAN'

    private final def script
    private final Catalog catalog
    // a label, not an enum: no enum may be part of the build state (see PlannedTask)
    private final String modeLabel

    ParameterPage(def script, Catalog catalog, AccessMode mode) {
        this.script = script
        this.catalog = catalog
        this.modeLabel = mode.label
    }

    @NonCPS
    private AccessMode mode() {
        return Labels.of(AccessMode.values() as List, modeLabel)
    }

    List definitions() {
        List result = []
        result << script.activeChoice(name: TARGETS_PARAMETER, randomName: 'choice-target-groups', choiceType: 'PT_CHECKBOX',
                filterable: false, filterLength: 1, description: "Deploy targets: groups under ${RunLocation.TARGETS.hosts} in the inventory",
                script: sandboxed(ActiveChoicesForm.groupsScript(catalog.groups), 'return ["(group list failed)"]'))
        if (mode().deploys()) {
            result << script.booleanParam(name: SKIP_DEPLOY_PARAMETER, defaultValue: false,
                    description: 'Skip the deployment and run only the selected tasks')
        }
        for (Phase phase in Phase.values()) {
            List<TaskDefinition> tasks = catalog.tasksFor(phase, mode())
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
        }
        result << script.text(name: PLAN_PARAMETER, defaultValue: '',
                description: 'A complete plan in YAML; when not empty, every other parameter is ignored. For the API and upstream jobs.')
        return result
    }

    /** Reads the parameter page into a DEPLOY_PLAN-shaped draft. */
    @NonCPS
    Map draft(Map requested, List<String> errors) {
        Map<Phase, Map> chosen = [:]
        for (Phase phase in Phase.values()) {
            List<String> names = Selection.splitList(Limits.bounded(phase.tasksParameter, requested[phase.tasksParameter], errors)) +
                    Selection.splitList(Limits.bounded(phase.moreTasksParameter, requested[phase.moreTasksParameter], errors))
            // counted before any task is read, like DEPLOY_PLAN
            if (names.size() > Limits.MAX_PLAN_ENTRIES) {
                errors << "${phase.label}: ${names.size()} tasks selected, at most ${Limits.MAX_PLAN_ENTRIES} are allowed".toString()
                names = []
            }
            chosen[phase] = [names : names,
                             values: ActiveChoicesForm.parseOptions(Limits.bounded(phase.optionsParameter, requested[phase.optionsParameter], errors), errors)]
        }
        return Selection.draft(catalog, Selection.splitList(Limits.bounded(TARGETS_PARAMETER, requested[TARGETS_PARAMETER], errors)), skipDeploy(requested), chosen, errors)
    }

    @NonCPS
    private boolean skipDeploy(Map requested) {
        return mode().deploys() && requested[SKIP_DEPLOY_PARAMETER] == true
    }

    // Active Choices scripts run in the Groovy sandbox, so they need no approval
    private Object sandboxed(String text, String fallback) {
        return script.groovyScript(script: [classpath: [], sandbox: true, script: text], fallbackScript: [classpath: [], sandbox: true, script: fallback])
    }
}
