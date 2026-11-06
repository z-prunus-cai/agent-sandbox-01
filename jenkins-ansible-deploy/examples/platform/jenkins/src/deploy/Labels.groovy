package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Reads the closed values that arrive as text: each enum carries its external spelling in a field named label. */
class Labels {

    @NonCPS
    static <T> T find(List<T> constants, Object text, String what, List<String> errors) {
        T found = constants.find { it.label == text }
        if (found == null) {
            errors << "${what} '${text}' is not one of ${constants.collect { it.label }}".toString()
        }
        return found
    }
}
