package deploy

/** How a job asks for task parameters: on the parameter page with Active Choices, or with input after the build starts. */
enum ParameterForm {
    ACTIVE_CHOICES('active-choices'),
    BASIC('basic')

    final String label

    ParameterForm(String label) {
        this.label = label
    }
}
