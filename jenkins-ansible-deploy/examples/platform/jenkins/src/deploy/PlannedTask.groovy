package deploy

import com.cloudbees.groovy.cps.NonCPS

/** One task as this build runs it: the catalog definition with the policy, timeout and values chosen for the build. */
class PlannedTask implements Serializable {
    String id
    TaskDefinition task
    int timeoutMinutes
    Map<String, Object> params

    // Finite values are kept as labels: after a Jenkins restart the build state is read back under a sandbox that
    // refuses the static call each Groovy enum makes when its class loads, so no enum may be part of that state
    private String phaseLabel

    @NonCPS
    Phase getPhase() {
        return Labels.of(Phase.values() as List, phaseLabel)
    }

    @NonCPS
    void setPhase(Phase value) {
        phaseLabel = value?.label
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
    String stageName() {
        return "${phase.label}: ${id}"
    }
}
