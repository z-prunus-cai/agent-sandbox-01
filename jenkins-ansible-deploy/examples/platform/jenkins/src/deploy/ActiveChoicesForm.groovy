package deploy

import com.cloudbees.groovy.cps.NonCPS

/**
 * The Active Choices parameter page. Its scripts run when the page opens and cannot see the workspace, so groups and
 * tasks are embedded as literals. The plugin turns full-width punctuation and @ = + " into HTML entities in returned
 * HTML; a form shows them decoded, a checkbox label shows the entity, so checkbox labels stay ASCII.
 */
class ActiveChoicesForm {

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
     * The plugin submits every input named "value" joined by commas, so each input follows a hidden marker
     * "@@task.field"; splitting on the markers keeps commas inside values. The plugin's sanitizer drops fieldset,
     * legend, label, title and aria-* attributes, so groups are divs and "required" is written out.
     */
    @NonCPS
    static String optionsScript(List<TaskDefinition> tasks, List<String> referencedParameters) {
        List<Map> data = tasks.collect { TaskDefinition task ->
            [name: task.name, description: task.description, policy: task.policy.label, order: task.order,
             params: task.parameters.collect { [name: it.name, type: it.type.label, required: it.required, description: it.description,
                                                choices: it.choices, default: it.defaultValue] }]
        }
        return """def tasks = ${literal(data)}
def policies = ${literal(FailurePolicy.values().collect { it.label })}
def escapeHtml = { value -> groovy.xml.XmlUtil.escapeXml(String.valueOf(value == null ? '' : value)) }
def select = { List options, Object current ->
    '<select name="value">' + options.collect { option ->
        '<option value="' + escapeHtml(option) + '"' + (String.valueOf(option) == String.valueOf(current) ? ' selected' : '') + '>' + escapeHtml(option) + '</option>'
    }.join('') + '</select>'
}
def selected = [${referencedParameters.join(', ')}].collectMany { String.valueOf(it ?: '').tokenize(',') }.collect { it.trim() }
def chosen = tasks.findAll { it.name in selected }
if (!chosen) {
    return '<div>No task selected</div>'
}
def html = []
chosen.each { task ->
    html << '<div style="border:1px solid #d1d5db;border-radius:6px;padding:8px 14px;margin:0 0 10px 0">'
    html << '<div style="margin-bottom:6px"><b>' + escapeHtml(task.name) + '</b> ' + escapeHtml(task.description) + '</div><table>'
    html << '<tr><td style="padding:3px 12px 3px 0">On failure</td><td>'
    html << '<input type="hidden" name="value" value="@@' + escapeHtml(task.name) + '.policy">' + select(policies, task.policy)
    html << ' abort: stop the pipeline; warn: mark unstable and continue</td></tr>'
    html << '<tr><td style="padding:3px 12px 3px 0">Order</td><td>'
    html << '<input type="hidden" name="value" value="@@' + escapeHtml(task.name) + '.order">'
    html << '<input type="number" name="value" value="' + escapeHtml(task.order) + '" placeholder="order" class="jenkins-input" style="width:120px">'
    html << ' smaller numbers run first in this phase</td></tr>'
    task.params.each { param ->
        html << '<tr><td style="padding:3px 12px 3px 0">' + escapeHtml(param.name) + (param.required ? ' (required)' : '') + '</td><td>'
        html << '<input type="hidden" name="value" value="@@' + escapeHtml(task.name) + '.' + escapeHtml(param.name) + '">'
        if (param.choices) {
            html << select(param.choices, param.default)
        } else if (param.type == 'bool') {
            html << select(['true', 'false'], param.default == true)
        } else {
            html << '<input type="' + (param.type == 'int' ? 'number' : 'text') + '" name="value" value="' + escapeHtml(param.default) + '"' +
                    ' placeholder="' + escapeHtml(param.name + (param.required ? ' (required)' : '')) + '" class="jenkins-input" style="width:320px">'
        }
        html << ' ' + escapeHtml(param.description) + '</td></tr>'
    }
    html << '</table></div>'
}
return html.join('')
"""
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
            if (!(key ==~ /[^.]+\.[^.]+/)) {
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
        Map simple = ['"': '"', '\\': '\\', '/': '/', 'b': '\b', 'f': '\f', 'n': '\n', 'r': '\r', 't': '\t']
        return text.replaceAll(/\\(u[0-9a-fA-F]{4}|.)/) { String all, String code ->
            code.length() == 5 ? String.valueOf((char) Integer.parseInt(code.substring(1), 16)) : (simple[code] ?: all)
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
