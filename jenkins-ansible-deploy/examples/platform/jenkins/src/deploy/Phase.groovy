package deploy

/** Where an extra task runs relative to the deployment. The parameter names are a contract with API callers. */
enum Phase {
    PRE('pre', 'PRE_TASKS', 'PRE_TASK_OPTIONS', 'PRE_', 'Pre-deploy'),
    POST('post', 'POST_TASKS', 'POST_TASK_OPTIONS', 'POST_', 'Post-deploy')

    final String label
    final String tasksParameter
    final String optionsParameter
    final String basicParameterPrefix
    final String title

    Phase(String label, String tasksParameter, String optionsParameter, String basicParameterPrefix, String title) {
        this.label = label
        this.tasksParameter = tasksParameter
        this.optionsParameter = optionsParameter
        this.basicParameterPrefix = basicParameterPrefix
        this.title = title
    }
}
