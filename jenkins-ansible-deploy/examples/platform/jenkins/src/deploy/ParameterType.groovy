package deploy

/** The argument_specs types a task parameter may have. */
enum ParameterType {
    STRING('str'),
    INTEGER('int'),
    BOOLEAN('bool')

    final String label

    ParameterType(String label) {
        this.label = label
    }
}
