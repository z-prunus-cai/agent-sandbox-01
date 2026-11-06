package deploy

import com.cloudbees.groovy.cps.NonCPS

/** One parameter of a task, from the role's argument_specs and defaults/main.yml. */
class ParameterDefinition implements Serializable {
    // written after the name of a required parameter on both parameter page forms
    public static final String REQUIRED_SUFFIX = ' (required)'

    String name
    boolean required
    String description
    List<String> choices
    Object defaultValue

    // finite values are kept as labels behind enum-typed accessors (see PlannedTask)
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
