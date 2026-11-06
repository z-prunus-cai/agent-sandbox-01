package deploy

import com.cloudbees.groovy.cps.NonCPS

/** One parameter of a task, from the role's argument_specs and defaults/main.yml. */
class ParameterDefinition implements Serializable {
    String name
    boolean required
    String description
    List<String> choices
    Object defaultValue

    // Finite values are kept as labels: after a Jenkins restart the build state is read back under a sandbox that
    // refuses the static call each Groovy enum makes when its class loads, so no enum may be part of that state
    private String typeLabel

    @NonCPS
    ParameterType getType() {
        return Labels.of(ParameterType.values() as List, typeLabel)
    }

    @NonCPS
    void setType(ParameterType value) {
        typeLabel = value?.label
    }
}
