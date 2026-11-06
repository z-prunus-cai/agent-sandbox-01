package deploy

import com.cloudbees.groovy.cps.NonCPS

/** One task under business/tasks: its meta/task.yml and its parameters. */
class TaskDefinition implements Serializable {
    String name
    String description
    int timeoutMinutes
    int order
    // minutes to wait for the review, or null for a task that runs without one
    Integer reviewTimeoutMinutes
    List<ParameterDefinition> parameters

    // Finite values are kept as labels: after a Jenkins restart the build state is read back under a sandbox that
    // refuses the static call each Groovy enum makes when its class loads, so no enum may be part of that state
    private List<String> phaseLabels

    @NonCPS
    List<Phase> getPhases() {
        return phaseLabels?.collect { Labels.of(Phase.values() as List, it) }
    }

    @NonCPS
    void setPhases(List<Phase> values) {
        phaseLabels = values?.collect { it?.label }
    }

    private String accessLabel

    @NonCPS
    TaskAccess getAccess() {
        return Labels.of(TaskAccess.values() as List, accessLabel)
    }

    @NonCPS
    void setAccess(TaskAccess value) {
        accessLabel = value?.label
    }

    private String menuLabel

    @NonCPS
    TaskMenu getMenu() {
        return Labels.of(TaskMenu.values() as List, menuLabel)
    }

    @NonCPS
    void setMenu(TaskMenu value) {
        menuLabel = value?.label
    }

    private String runOnLabel

    @NonCPS
    RunLocation getRunOn() {
        return Labels.of(RunLocation.values() as List, runOnLabel)
    }

    @NonCPS
    void setRunOn(RunLocation value) {
        runOnLabel = value?.label
    }

    private String policyLabel

    @NonCPS
    FailurePolicy getPolicy() {
        return Labels.of(FailurePolicy.values() as List, policyLabel)
    }

    @NonCPS
    void setPolicy(FailurePolicy value) {
        policyLabel = value?.label
    }

    @NonCPS
    ParameterDefinition parameter(String parameterName) {
        return parameters.find { it.name == parameterName }
    }
}
