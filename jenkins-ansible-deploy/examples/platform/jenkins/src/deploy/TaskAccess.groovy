package deploy

/** What a task declares it does to the hosts. */
enum TaskAccess {
    READ('read'),
    WRITE('write')

    final String label

    TaskAccess(String label) {
        this.label = label
    }
}
