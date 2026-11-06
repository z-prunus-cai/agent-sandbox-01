package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Checks one parameter value against its definition, the same way for defaults, DEPLOY_PLAN and form input. */
class Values {

    // Ansible reads a string that starts with this header as a template with the delimiters the header names
    public static final String JINJA_OVERRIDE = '#jinja2:'

    // Ansible renders Jinja in extra vars, so a template would run code on the Jenkins node; control and format characters
    // could forge log lines. Types are taken as YAML read them, so Ansible receives exactly what was checked
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
                if (text.contains('{{') || text.contains('{%') || text.contains('{#') || text.startsWith(JINJA_OVERRIDE)) {
                    errors << "${where} must not contain template syntax ({{, {%, {# or a leading ${JINJA_OVERRIDE})".toString()
                }
                if (text.find(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}&&[^\t]]/) != null) errors << "${where} must not contain control or format characters".toString()
                if (text.length() > Limits.MAX_STRING_PARAMETER_CHARACTERS) {
                    errors << "${where} is longer than ${Limits.MAX_STRING_PARAMETER_CHARACTERS} characters".toString()
                }
                return text
        }
        throw new IllegalStateException(String.valueOf(parameter.type))
    }

    /** A problem list as printed: values in it were not all accepted, so control, format and line-break characters print as backslash-u escapes. */
    @NonCPS
    static String problems(List<String> errors, String separator) {
        return errors.collect { String.valueOf(it).replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/) { String c -> String.format('\\u%04x', c.codePointAt(0)) } }.join(separator)
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
