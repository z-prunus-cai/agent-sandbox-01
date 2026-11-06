package deploy

import com.cloudbees.groovy.cps.NonCPS

/** How one step of the run ended. The labels go into the archived report. */
enum TaskOutcome {
    PASSED('passed'),
    FAILED_STOPPED('failed-stopped'),
    FAILED_CONTINUED('failed-continued'),
    TIMED_OUT_STOPPED('timed-out-stopped'),
    TIMED_OUT_CONTINUED('timed-out-continued'),
    ABORTED('aborted'),
    ERROR('error')

    final String label

    TaskOutcome(String label) {
        this.label = label
    }

    @NonCPS
    static TaskOutcome failed(FailurePolicy policy) {
        switch (policy) {
            case FailurePolicy.ABORT: return FAILED_STOPPED
            case FailurePolicy.WARN: return FAILED_CONTINUED
        }
        throw new IllegalStateException(policy.name())
    }

    @NonCPS
    static TaskOutcome timedOut(FailurePolicy policy) {
        switch (policy) {
            case FailurePolicy.ABORT: return TIMED_OUT_STOPPED
            case FailurePolicy.WARN: return TIMED_OUT_CONTINUED
        }
        throw new IllegalStateException(policy.name())
    }
}
