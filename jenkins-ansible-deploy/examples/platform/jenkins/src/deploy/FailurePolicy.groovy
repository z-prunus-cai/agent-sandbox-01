package deploy

import com.cloudbees.groovy.cps.NonCPS

/** What a failed task does to the build; the meaning is the help text the task form shows. */
enum FailurePolicy {
    ABORT('abort', 'stop the pipeline'),
    WARN('warn', 'mark the build unstable and continue')

    final String label
    final String meaning

    FailurePolicy(String label, String meaning) {
        this.label = label
        this.meaning = meaning
    }

    @NonCPS
    static String help() {
        return values().collect { it.label + ': ' + it.meaning }.join('; ')
    }
}
