package deploy

import com.cloudbees.groovy.cps.NonCPS

/** One task under business/tasks: its meta/task.yml and its parameters. */
class TaskDefinition implements Serializable {
    String name
    String description
    List<Phase> phases
    TaskAccess access
    TaskMenu menu
    RunLocation runOn
    FailurePolicy policy
    int timeoutMinutes
    int order
    // minutes to wait for the review, or null for a task that runs without one
    Integer reviewTimeoutMinutes
    List<ParameterDefinition> parameters

    @NonCPS
    ParameterDefinition parameter(String parameterName) {
        return parameters.find { it.name == parameterName }
    }
}
