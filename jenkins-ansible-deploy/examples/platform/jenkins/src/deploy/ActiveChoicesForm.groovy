package deploy

import com.cloudbees.groovy.cps.NonCPS

// Active Choices scripts run when the page opens, without the workspace, so data goes in as literals. The plugin turns
// full-width punctuation and @ = + " into entities, which a checkbox label shows as written: labels use half-width punctuation.
class ActiveChoicesForm {
    private static final Map<String, String> JSON_ESCAPES = ['"': '"', '\\': '\\', '/': '/', 'b': '\b', 'f': '\f', 'n': '\n', 'r': '\r', 't': '\t']

    @NonCPS
    static String groupsScript(Map<String, List<String>> groups) {
        return "def groups = ${literal(groups)}\nreturn groups.collectEntries { name, hosts -> [(name): name + ' (' + hosts.join(', ') + ')'] }\n"
    }

    @NonCPS
    static String tasksScript(List<TaskDefinition> tasks) {
        return "return ${literal(labels(tasks))}"
    }

    /**
     * The tasks behind "show more": none until the referenced checkbox is ticked, which sends "on" (an empty string when
     * not). A build started through the API leaves the checkbox unbound while Jenkins computes this list's default.
     */
    @NonCPS
    static String moreTasksScript(List<TaskDefinition> tasks, String showMoreParameter) {
        return """def shown = ''
try {
    shown = ${showMoreParameter}
} catch (MissingPropertyException unbound) {
}
if (!shown) {
    return [:]
}
return ${literal(labels(tasks))}
"""
    }

    @NonCPS
    static Map<String, String> labels(List<TaskDefinition> tasks) {
        return tasks.collectEntries { [(it.name): it.name + ' - ' + it.description] }
    }

    /**
     * Each input follows a hidden marker "@@<form key>": the plugin joins all inputs named "value" with commas. The
     * sanitizer drops fieldset, legend, label, title and aria-*, so groups are divs and "required" is written out.
     */
    @NonCPS
    static String optionsScript(List<TaskDefinition> tasks, List<String> referencedParameters) {
        List<Map> data = tasks.collect { TaskDefinition task ->
            [name     : task.name, description: task.description, policy: task.policy.label, order: task.order,
             policyKey: FormField.POLICY.key(task.name), orderKey: FormField.ORDER.key(task.name),
             params   : task.parameters.collect { ActiveChoicesForm.input(task, it) }]
        }
        return """def tasks = ${literal(data)}
def style = ${literal(FormStyle.STYLES)}
def policies = ${literal(FailurePolicy.values().collect { it.label })}
def escapeHtml = { value -> groovy.xml.XmlUtil.escapeXml(String.valueOf(value == null ? '' : value)) }
def marker = { key -> '<input type="hidden" name="value" value="@@' + escapeHtml(key) + '">' }
def select = { List options, Object current ->
    '<select name="value" style="' + style.select + '">' + options.collect { option ->
        '<option value="' + escapeHtml(option) + '"' + (String.valueOf(option) == String.valueOf(current) ? ' selected' : '') + '>' + escapeHtml(option) + '</option>'
    }.join('') + '</select>'
}
def hint = { text -> '<span style="' + style.hint + '">' + escapeHtml(text) + '</span>' }
def selected = [${referencedParameters.join(', ')}].collectMany { String.valueOf(it ?: '').tokenize(',') }.collect { it.trim() }
def chosen = tasks.findAll { it.name in selected }
if (!chosen) {
    return '<div>No task selected</div>'
}
def html = []
chosen.each { task ->
    html << '<div style="' + style.group + '">'
    html << '<div style="' + style.title + '"><b>' + escapeHtml(task.name) + '</b> ' + escapeHtml(task.description) + '</div><table>'
    html << '<tr><td style="' + style.label + '">Order</td><td style="' + style.value + '">' + marker(task.orderKey)
    html << '<input type="number" name="value" value="' + escapeHtml(task.order) + '" placeholder="order" class="jenkins-input" style="' + style.number + '">'
    html << hint('smaller numbers run first in this phase') + '</td></tr>'
    html << '<tr><td style="' + style.label + '">On failure</td><td style="' + style.value + '">' + marker(task.policyKey) + select(policies, task.policy)
    html << hint('abort: stop the pipeline; warn: mark unstable and continue') + '</td></tr>'
    task.params.each { param ->
        html << '<tr><td style="' + style.label + '">' + escapeHtml(param.label) + '</td><td style="' + style.value + '">' + marker(param.key)
        if (param.choices) {
            html << select(param.choices, param.default)
        } else {
            html << '<input type="' + param.inputType + '" name="value" value="' + escapeHtml(param.default) + '"' +
                    ' placeholder="' + escapeHtml(param.label) + '" class="jenkins-input" style="' + param.style + '">'
        }
        html << hint(param.description) + '</td></tr>'
    }
    html << '</table></div>'
}
return html.join('')
"""
    }

    /** How the options form shows one parameter, decided here so the sandboxed script compares no type names. */
    @NonCPS
    static Map input(TaskDefinition task, ParameterDefinition parameter) {
        Map field = [key: FormField.key(task.name, parameter.name), label: parameter.name + (parameter.required ? ' (required)' : ''),
                     description: parameter.description, choices: parameter.choices, default: parameter.defaultValue, inputType: 'text',
                     style: FormStyle.STYLES.text]
        switch (parameter.type) {
            case ParameterType.STRING: return field
            case ParameterType.INTEGER: return field + [inputType: 'number', style: FormStyle.STYLES.number]
            case ParameterType.BOOLEAN: return field + [choices: ['true', 'false'], default: String.valueOf(parameter.defaultValue == true)]
        }
        throw new IllegalStateException(String.valueOf(parameter.type))
    }

    /**
     * Reads "@@task_a.policy,abort,@@task_a.task_a_repeat,2" into [task_a.policy: abort, ...]. The plugin escapes each
     * value as a JSON string. A value holding ",@@" would split into a false field, which is reported, never guessed.
     */
    @NonCPS
    static Map<String, String> parseOptions(Object text, List<String> errors) {
        Map<String, String> values = [:]
        List<String> parts = (text ?: '').toString().split('(?:^|,)@@') as List
        parts.eachWithIndex { String part, int index ->
            if (!part && index == 0) return
            int comma = part.indexOf(',')
            String key = comma > 0 ? part.substring(0, comma) : part
            if (FormField.split(key) == null) {
                errors << "form field '${key}' is not valid: a value must not contain ,@@".toString()
                return
            }
            if (values.containsKey(key)) errors << "form field ${key} appears more than once: a value must not contain ,@@".toString()
            values[key] = comma > 0 ? ActiveChoicesForm.jsonUnescape(part.substring(comma + 1)) : ''
        }
        return values
    }

    @NonCPS
    static String jsonUnescape(String text) {
        Map<String, String> escapes = JSON_ESCAPES
        return text.replaceAll(/\\(u[0-9a-fA-F]{4}|.)/) { String all, String code ->
            code.length() == 5 ? String.valueOf((char) Integer.parseInt(code.substring(1), 16)) : (escapes[code] ?: all)
        }
    }

    @NonCPS
    static String literal(Object value) {
        if (value instanceof Map) {
            return value ? '[' + value.collect { key, entry -> ActiveChoicesForm.literal(key.toString()) + ': ' + ActiveChoicesForm.literal(entry) }.join(', ') + ']' : '[:]'
        }
        if (value instanceof Collection) return '[' + value.collect { ActiveChoicesForm.literal(it) }.join(', ') + ']'
        if (value instanceof Number || value instanceof Boolean || value == null) return String.valueOf(value)
        return "'" + value.toString().replace('\\', '\\\\').replace("'", "\\'").replace('\n', '\\n').replace('\r', '\\r') + "'"
    }
}
