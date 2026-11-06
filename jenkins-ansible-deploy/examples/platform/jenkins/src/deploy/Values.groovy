package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Checks one parameter value against its definition, the same way for defaults, DEPLOY_PLAN and form input. */
class Values {

    /**
     * Ansible renders Jinja in extra vars, so a template in a value would run code on the Jenkins node.
     * YAML reads unquoted yes, 2026-01-01 and 1.0 as boolean, date and decimal: a str parameter takes only text and integers.
     */
    @NonCPS
    static Object check(String where, Object value, ParameterDefinition parameter, List<String> errors) {
        switch (parameter.type) {
            case ParameterType.INTEGER:
                if (!(value instanceof Integer || value instanceof Long)) errors << "${where} must be an integer, got '${value}'".toString()
                return value
            case ParameterType.BOOLEAN:
                if (!(value instanceof Boolean)) errors << "${where} must be true or false, got '${value}'".toString()
                return value
            case ParameterType.STRING:
                if (!(value instanceof String || value instanceof Integer || value instanceof Long)) {
                    errors << "${where} must be a string (quote it in YAML), got '${value}'".toString()
                    return value
                }
                String text = value.toString()
                if (parameter.choices && !(text in parameter.choices)) errors << "${where} must be one of ${parameter.choices}, got '${text}'".toString()
                if (text.contains('{{') || text.contains('{%') || text.contains('{#')) {
                    errors << "${where} must not contain template syntax ({{, {%, {#)".toString()
                }
                if (text.length() > Limits.MAX_STRING_PARAMETER_CHARACTERS) {
                    errors << "${where} is longer than ${Limits.MAX_STRING_PARAMETER_CHARACTERS} characters".toString()
                }
                return text
        }
        throw new IllegalStateException(String.valueOf(parameter.type))
    }

    /** Form fields are text: an empty integer or boolean means "use the default"; an integer that does not parse stays text for check. */
    @NonCPS
    static Object fromForm(String value, ParameterDefinition parameter) {
        switch (parameter.type) {
            case ParameterType.STRING: return value
            case ParameterType.INTEGER:
                if (value == '') return null
                return value.trim().isLong() ? value.trim().toLong() : value
            case ParameterType.BOOLEAN:
                if (value == '') return null
                return value in ['true', 'false'] ? value == 'true' : value
        }
        throw new IllegalStateException(String.valueOf(parameter.type))
    }
}
