package deploy

/** What a failed task does to the build: abort stops it, warn marks it unstable and goes on. */
enum FailurePolicy {
    ABORT('abort'),
    WARN('warn')

    final String label

    FailurePolicy(String label) {
        this.label = label
    }
}
