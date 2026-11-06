package deploy

import com.cloudbees.groovy.cps.NonCPS

/** What a build was asked to do, read from its parameters before properties() replaces them. */
enum RequestKind {
    // Every form registers DEPLOY_PLAN, so its absence means the job has never registered its parameters
    UNREGISTERED('unregistered'),
    PLAN_TEXT('plan text'),
    FORM('form')

    final String label

    RequestKind(String label) {
        this.label = label
    }

    @NonCPS
    static RequestKind of(Map requested) {
        if (!requested.containsKey(ParameterPage.PLAN_PARAMETER)) return UNREGISTERED
        return requested[ParameterPage.PLAN_PARAMETER]?.toString()?.trim() ? PLAN_TEXT : FORM
    }
}
