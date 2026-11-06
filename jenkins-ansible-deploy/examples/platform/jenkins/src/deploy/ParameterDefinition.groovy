package deploy

/** One parameter of a task, from the role's argument_specs and defaults/main.yml. */
class ParameterDefinition implements Serializable {
    String name
    ParameterType type
    boolean required
    String description
    List<String> choices
    Object defaultValue
}
