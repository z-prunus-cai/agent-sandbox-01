package deploy

import com.cloudbees.groovy.cps.NonCPS

/** One row of the report: a task or the deployment, and how it ended. */
class TaskResult implements Serializable {
    public static final String DEPLOY = 'deploy'

    String id
    String name
    String phase
    Map<String, Object> params
    int seconds
    String message
    String reviewer

    // Finite values are kept as labels: after a Jenkins restart the build state is read back under a sandbox that
    // refuses the static call each Groovy enum makes when its class loads, so no enum may be part of that state
    private String policyLabel

    @NonCPS
    FailurePolicy getPolicy() {
        return Labels.of(FailurePolicy.values() as List, policyLabel)
    }

    @NonCPS
    void setPolicy(FailurePolicy value) {
        policyLabel = value?.label
    }

    private String outcomeLabel

    @NonCPS
    TaskOutcome getOutcome() {
        return Labels.of(TaskOutcome.values() as List, outcomeLabel)
    }

    @NonCPS
    void setOutcome(TaskOutcome value) {
        outcomeLabel = value?.label
    }

    @NonCPS
    static TaskResult of(PlannedTask planned) {
        return new TaskResult(id: planned.id, name: planned.task.name, phase: planned.phase.label, policy: planned.policy,
                params: planned.params)
    }

    @NonCPS
    static TaskResult deploy(List<String> targets) {
        return new TaskResult(id: DEPLOY, name: DEPLOY, phase: DEPLOY, policy: FailurePolicy.ABORT, params: [targets: targets])
    }

    @NonCPS
    Map toMap() {
        return [id: id, name: name, phase: phase, policy: policy.label, params: params, outcome: outcome?.label, seconds: seconds,
                message: message, reviewer: reviewer]
    }

    @NonCPS
    static String format(List<TaskResult> results) {
        List<String> lines = ['[deploy] results:']
        results.each { TaskResult row ->
            lines << "  ${row.phase.padRight(6)} ${row.id.padRight(16)} ${row.policy.label.padRight(5)} ${(row.outcome?.label ?: '-').padRight(19)} ${String.valueOf(row.seconds).padLeft(4)}s".toString()
        }
        return lines.join('\n')
    }
}
