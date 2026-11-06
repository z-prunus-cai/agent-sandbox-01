package deploy

/** Where a task shows on the Active Choices page: always, or after "show more" is ticked. */
enum TaskMenu {
    MAIN('main'),
    MORE('more')

    final String label

    TaskMenu(String label) {
        this.label = label
    }
}
