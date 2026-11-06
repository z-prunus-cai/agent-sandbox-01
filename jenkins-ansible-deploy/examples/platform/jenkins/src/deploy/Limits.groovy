package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Bounds on everything a build accepts. MAX_PLAN_CHARACTERS bounds DEPLOY_PLAN and every value of the parameter page. */
class Limits {
    public static final int MAX_PLAN_CHARACTERS = 20000
    public static final int MAX_PLAN_ENTRIES = 50
    public static final int MAX_STRING_PARAMETER_CHARACTERS = 1000
    public static final int MAX_ID_CHARACTERS = 64
    public static final int MAX_TIMEOUT_MINUTES = 240
    // the review prompt shows at most this much of the review file; the archived file holds all of it
    public static final int MAX_REVIEW_CHARACTERS = 20000

    /** A text input, bounded before it is parsed; an oversized one is reported and read as empty. */
    @NonCPS
    static String bounded(String name, Object value, List<String> errors) {
        String text = value == null ? '' : value.toString()
        if (text.length() <= MAX_PLAN_CHARACTERS) return text
        errors << "${name} is longer than ${MAX_PLAN_CHARACTERS} characters".toString()
        return ''
    }
}
