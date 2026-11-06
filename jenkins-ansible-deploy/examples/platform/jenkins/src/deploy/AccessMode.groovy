package deploy

import com.cloudbees.groovy.cps.NonCPS

/** What a job may do: a read-only job offers only read tasks and never deploys. */
enum AccessMode {
    READ_ONLY('read-only'),
    READ_WRITE('read-write')

    final String label

    AccessMode(String label) {
        this.label = label
    }

    @NonCPS
    boolean allows(TaskAccess access) {
        switch (this) {
            case READ_ONLY: return access == TaskAccess.READ
            case READ_WRITE: return true
        }
        throw new IllegalStateException(name())
    }

    @NonCPS
    boolean deploys() {
        switch (this) {
            case READ_ONLY: return false
            case READ_WRITE: return true
        }
        throw new IllegalStateException(name())
    }
}
