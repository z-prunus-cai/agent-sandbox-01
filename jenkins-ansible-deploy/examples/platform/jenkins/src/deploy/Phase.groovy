package deploy

/** Where an extra task runs relative to the deployment. The parameter names are a contract with API callers. */
enum Phase {
    PRE('pre', 'PRE_TASKS', 'PRE_SHOW_MORE', 'PRE_MORE_TASKS', 'PRE_TASK_OPTIONS', 'PRE_', 'Pre-deploy'),
    POST('post', 'POST_TASKS', 'POST_SHOW_MORE', 'POST_MORE_TASKS', 'POST_TASK_OPTIONS', 'POST_', 'Post-deploy')

    final String label
    final String tasksParameter
    final String showMoreParameter
    final String moreTasksParameter
    final String optionsParameter
    final String basicParameterPrefix
    final String title

    Phase(String label, String tasksParameter, String showMoreParameter, String moreTasksParameter, String optionsParameter,
          String basicParameterPrefix, String title) {
        this.label = label
        this.tasksParameter = tasksParameter
        this.showMoreParameter = showMoreParameter
        this.moreTasksParameter = moreTasksParameter
        this.optionsParameter = optionsParameter
        this.basicParameterPrefix = basicParameterPrefix
        this.title = title
    }
}
