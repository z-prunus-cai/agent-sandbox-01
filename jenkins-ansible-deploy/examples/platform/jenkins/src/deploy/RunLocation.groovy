package deploy

/** Where a task runs: on every host of the selected groups, or on the Jenkins node itself. */
enum RunLocation {
    TARGETS('targets', 'deploy_targets'),
    CONTROLLER('controller', 'localhost')

    final String label
    final String hosts

    RunLocation(String label, String hosts) {
        this.label = label
        this.hosts = hosts
    }
}
