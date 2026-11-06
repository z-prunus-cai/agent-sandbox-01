package deploy

import com.cloudbees.groovy.cps.NonCPS

/**
 * The fields of a task on the forms besides its parameters. Form keys are "task.field" on the Active Choices page and
 * "phase.task.field" in the input form; task names hold no dot, so the first dots split them.
 */
enum FormField {
    POLICY('policy'),
    ORDER('order')

    final String label

    FormField(String label) {
        this.label = label
    }

    @NonCPS
    String key(String task) {
        return FormField.key(task, label)
    }

    /** The field a key part names, or null for a task parameter. */
    @NonCPS
    static FormField of(String field) {
        return values().find { it.label == field }
    }

    @NonCPS
    static String key(String task, String field) {
        return task + '.' + field
    }

    @NonCPS
    static String phasedKey(String phase, String task, String field) {
        return phase + '.' + key(task, field)
    }

    /** [task: ..., field: ...], or null when the key is not "task.field". */
    @NonCPS
    static Map<String, String> split(String key) {
        int dot = key.indexOf('.')
        if (dot < 1 || dot == key.length() - 1) return null
        return [task: key.substring(0, dot), field: key.substring(dot + 1)]
    }
}
