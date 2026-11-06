package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Everything a build will run, validated before the first playbook. */
class Plan implements Serializable {
    List<String> targets
    boolean deploy
    // keyed by Phase label: no enum may be part of the build state (see PlannedTask)
    Map<String, List<PlannedTask>> tasks

    @NonCPS
    List<PlannedTask> tasksIn(Phase phase) {
        return tasks[phase.label] ?: []
    }

    @NonCPS
    String describe() {
        List<String> lines = ['[deploy] plan:', "  groups: ${targets ?: '(none)'}", "  deploy: ${deploy ? 'yes' : 'no'}"]
        Phase.values().each { Phase phase ->
            lines << "  ${phase.title} tasks:${tasksIn(phase) ? '' : ' (none)'}".toString()
            tasksIn(phase).each { PlannedTask planned ->
                lines << "    - ${planned.id} [${planned.policy.label}] ${planned.params}".toString()
            }
        }
        return lines.join('\n')
    }
}
