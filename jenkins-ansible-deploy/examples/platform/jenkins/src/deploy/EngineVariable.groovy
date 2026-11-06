package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Extra vars the engine itself passes to Ansible; run-task.yml and reviewed roles read them by these names. */
enum EngineVariable {
    TASK_ROLE('task_role'),
    TASK_HOSTS('task_hosts'),
    REVIEW_MODE('task_review_mode'),
    REVIEW_FILE('task_review_file'),
    PARAMETER_NAMES('task_parameter_names')

    final String label

    EngineVariable(String label) {
        this.label = label
    }

    @NonCPS
    static List<String> labels() {
        return values().collect { it.label }
    }
}
