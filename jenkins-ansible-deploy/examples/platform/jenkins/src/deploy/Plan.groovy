package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Everything a build will run, validated before the first playbook. */
class Plan implements Serializable {
    List<String> targets
    boolean deploy
    Map<Phase, List<PlannedTask>> tasks

    @NonCPS
    String describe() {
        List<String> lines = ['[deploy] plan:', "  groups: ${targets ?: '(none)'}", "  deploy: ${deploy ? 'yes' : 'no'}"]
        Phase.values().each { Phase phase ->
            lines << "  ${phase.title} tasks:${tasks[phase] ? '' : ' (none)'}".toString()
            tasks[phase].each { PlannedTask planned ->
                lines << "    - ${planned.id} [${planned.policy.label}] ${planned.params}".toString()
            }
        }
        return lines.join('\n')
    }
}
