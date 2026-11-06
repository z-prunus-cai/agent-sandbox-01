package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Checks one parameter value against its definition, the same way for defaults, DEPLOY_PLAN and form input. */
class Values {

    // Ansible reads a string that starts with this header as a template with the delimiters the header names
    public static final String JINJA_OVERRIDE = '#jinja2:'
    // control, format, surrogate and line-separator characters: they can fake a console line or hide what text says
    public static final String UNSAFE_CHARACTERS = /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/

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
                if (text.replace('\t', '').find(UNSAFE_CHARACTERS) != null) {
                    errors << "${where} must not contain control, format or line-separator characters".toString()
                }
                if (text.length() > Limits.MAX_STRING_PARAMETER_CHARACTERS) {
                    errors << "${where} is longer than ${Limits.MAX_STRING_PARAMETER_CHARACTERS} characters".toString()
                }
                return text
        }
        throw new IllegalStateException(String.valueOf(parameter.type))
    }

    /** A problem list as printed: values in it were not all accepted, so unsafe characters print as backslash-u escapes. */
    @NonCPS
    static String problems(List<String> errors, String separator) {
        return errors.collect { Values.printable(String.valueOf(it), false) }.join(separator)
    }

    /** Text with every unsafe character as backslash-u escapes, keeping line breaks and tabs if asked to. */
    @NonCPS
    static String printable(String text, boolean keepLines) {
        return text.replaceAll(UNSAFE_CHARACTERS) { String c -> keepLines && (c == '\n' || c == '\t') ? c : Values.escape(c.codePointAt(0)) }
    }

    // a code point above U+FFFF is written as its two UTF-16 halves, so every escape has exactly four digits
    @NonCPS
    static String escape(int codePoint) {
        if (codePoint <= 0xFFFF) return String.format('\\u%04x', codePoint)
        int offset = codePoint - 0x10000
        return String.format('\\u%04x\\u%04x', 0xD800 + (offset >> 10), 0xDC00 + (offset & 0x3FF))
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
