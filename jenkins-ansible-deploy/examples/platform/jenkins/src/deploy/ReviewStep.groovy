package deploy

/** The two runs of a task that asks for a review; the label reaches the role as task_review_mode. */
enum ReviewStep {
    GENERATE('generate'),
    EXECUTE('execute')

    final String label

    ReviewStep(String label) {
        this.label = label
    }
}
