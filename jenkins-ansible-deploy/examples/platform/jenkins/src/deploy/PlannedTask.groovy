package deploy

import com.cloudbees.groovy.cps.NonCPS

/** One task as this build runs it: the catalog definition with the policy, timeout and values chosen for the build. */
class PlannedTask implements Serializable {
    String id
    TaskDefinition task
    Phase phase
    FailurePolicy policy
    int timeoutMinutes
    Map<String, Object> params

    @NonCPS
    String stageName() {
        return "${phase.label}: ${id}"
    }
}
