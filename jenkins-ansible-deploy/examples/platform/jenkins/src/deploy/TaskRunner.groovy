package deploy

import com.cloudbees.groovy.cps.NonCPS

/** Runs tasks and the deployment on an executor each, and the review between, without one. Uses pipeline steps through script. */
class TaskRunner implements Serializable {
    public static final String SOURCE_STASH = 'deploy-source'
    public static final String RUN_DIRECTORY = '.deploy-run'

    private final def script
    private final Map credentials
    final List<TaskResult> results = []

    TaskRunner(def script, Map credentials) {
        this.script = script
        this.credentials = credentials
    }

    // takes the tasks, not the phase: an argument stays in the build state while a review waits, and an enum may not
    void runTasks(Plan plan, List<PlannedTask> tasks) {
        for (PlannedTask planned in tasks) {
            script.stage(planned.stageName()) {
                run(planned, plan.targets)
            }
        }
    }

    private void run(PlannedTask planned, List<String> targets) {
        TaskResult result = TaskResult.of(planned)
        results << result
        try {
            runSteps(planned, targets, result)
        } catch (InterruptedException e) {
            // aborted while waiting for an executor, before any step recorded how the task ended
            if (result.outcome == null) result.outcome = TaskOutcome.ABORTED
            throw e
        } catch (Exception e) {
            // a step outside the playbook failed, such as unstash or verifySha256 before the confirmed run
            if (result.outcome == null) {
                result.outcome = TaskOutcome.ERROR
                result.message = e.toString()
            }
            throw e
        }
    }

    private void runSteps(PlannedTask planned, List<String> targets, TaskResult result) {
        if (planned.task.reviewTimeoutMinutes == null) {
            script.node {
                restoreSource()
                attempt(planned, targets, result, null)
            }
            return
        }
        String reviewFile = "${RUN_DIRECTORY}/review/${planned.phase.label}-${planned.id}.txt"
        String stashName = "review-${planned.phase.label}-${planned.id}"
        String hash = null
        String content = null
        script.node {
            restoreSource()
            // writeFile creates the directory the role writes its review file into
            script.writeFile(file: "${RUN_DIRECTORY}/review/.directory", text: '')
            if (attempt(planned, targets, result, [step: ReviewStep.GENERATE.label, file: reviewFile])) {
                // the generate run passed; only the confirmed run decides how the task ended
                result.outcome = null
                if (!script.fileExists(reviewFile)) {
                    result.outcome = TaskOutcome.ERROR
                    result.message = "wrote no review file at ${EngineVariable.REVIEW_FILE.label}".toString()
                    script.error "[deploy] task ${planned.id} ${result.message}"
                }
                hash = script.sha256(file: reviewFile)
                content = script.readFile(file: reviewFile)
                script.archiveArtifacts(artifacts: reviewFile)
                script.stash(name: stashName, includes: reviewFile)
            }
        }
        if (hash == null) return
        try {
            script.timeout(time: planned.task.reviewTimeoutMinutes, unit: 'MINUTES') {
                result.reviewer = script.input(id: "Review-${planned.phase.label}-${planned.id}",
                        message: "Run ${planned.id} as reviewed below? (sha256 ${hash})\n\n${TaskRunner.shown(content, reviewFile)}", ok: 'Run', submitterParameter: 'reviewer')
            }
        } catch (InterruptedException e) {
            result.outcome = TaskOutcome.ABORTED
            result.message = 'not confirmed'
            throw e
        }
        script.echo "[deploy] ${planned.id} confirmed by ${result.reviewer}"
        script.node {
            restoreSource()
            script.unstash(stashName)
            // the review file must be the one shown for confirmation, whatever happened to the workspace meanwhile
            script.verifySha256(file: reviewFile, hash: hash)
            attempt(planned, targets, result, [step: ReviewStep.EXECUTE.label, file: reviewFile])
        }
    }

    /** Runs one playbook of a task under its timeout and policy; returns whether it passed. */
    private boolean attempt(PlannedTask planned, List<String> targets, TaskResult result, Map review) {
        Map variables = planned.params + [(EngineVariable.TASK_ROLE.label): planned.task.name, (EngineVariable.TASK_HOSTS.label): planned.task.runOn.hosts]
        if (review != null) {
            variables[EngineVariable.REVIEW_MODE.label] = review.step
            variables[EngineVariable.REVIEW_FILE.label] = "${script.env.WORKSPACE}/${review.file}".toString()
        }
        String variablesFile = "${RUN_DIRECTORY}/${planned.phase.label}-${planned.id}.json"
        script.writeJSON(file: variablesFile, json: variables)
        Map args = [playbook: 'platform/ansible/run-task.yml', extras: "-e @${variablesFile}"]
        if (planned.task.runOn == RunLocation.TARGETS) args.limit = targets.join(':')
        script.echo "[deploy] ${planned.id} (${planned.task.description}) policy=${planned.policy.label} params=${planned.params}"
        long started = System.currentTimeMillis()
        boolean passed = false
        String failure = null
        try {
            script.timeout(time: planned.timeoutMinutes, unit: 'MINUTES') {
                playbook(args)
            }
            passed = true
        } catch (hudson.AbortException e) {
            failure = e.message
        } catch (InterruptedException e) {
            // A timeout and a manual abort both arrive as InterruptedException, and the cause that tells them apart
            // is outside the sandbox allowlist: a run that lasted its whole timeout counts as timed out
            if (System.currentTimeMillis() - started < planned.timeoutMinutes * 60000L) {
                finish(result, started, TaskOutcome.ABORTED, null)
                throw e
            }
            finish(result, started, TaskOutcome.timedOut(planned.policy), "timed out after ${planned.timeoutMinutes} min")
            return settle(planned, result)
        } catch (Exception e) {
            finish(result, started, TaskOutcome.ERROR, e.toString())
            throw e
        }
        if (passed) {
            finish(result, started, TaskOutcome.PASSED, null)
            return true
        }
        finish(result, started, TaskOutcome.failed(planned.policy), failure)
        return settle(planned, result)
    }

    private boolean settle(PlannedTask planned, TaskResult result) {
        switch (planned.policy) {
            case FailurePolicy.ABORT:
                script.error "[deploy] task ${planned.id} failed: ${result.message} (abort: stopping the pipeline)"
                return false
            case FailurePolicy.WARN:
                script.unstable "[deploy] task ${planned.id} failed: ${result.message} (warn: continuing)"
                return false
        }
        throw new IllegalStateException(planned.policy.name())
    }

    void deploy(List<String> targets) {
        TaskResult result = TaskResult.deploy(targets)
        results << result
        script.node {
            restoreSource()
            long started = System.currentTimeMillis()
            try {
                playbook([playbook: 'platform/ansible/deploy.yml', limit: targets.join(':')])
                finish(result, started, TaskOutcome.PASSED, null)
            } catch (hudson.AbortException e) {
                finish(result, started, TaskOutcome.FAILED_STOPPED, e.message)
                throw e
            } catch (InterruptedException e) {
                finish(result, started, TaskOutcome.ABORTED, null)
                throw e
            } catch (Exception e) {
                finish(result, started, TaskOutcome.ERROR, e.toString())
                throw e
            }
        }
    }

    /** Parameter files and the report hold this build's values: the report is archived and both leave the reusable workspace. */
    void report() {
        script.node {
            script.dir(RUN_DIRECTORY) {
                if (results) {
                    script.echo TaskResult.format(results)
                    script.writeJSON(file: 'deploy-report.json', json: results.collect { it.toMap() }, pretty: 2)
                    script.archiveArtifacts(artifacts: 'deploy-report.json')
                }
                script.deleteDir()
            }
        }
    }

    // Without the credential the plugin runs ansible-playbook with no vault password, which fails like a task;
    // binding first names the missing ID. A Secret file vault credential needs file(...) instead of string(...)
    void checkCredentials() {
        List bindings = [script.string(credentialsId: credentials.vaultCredentialsId, variable: 'VAULT_PASSWORD_CHECK')]
        if (credentials.sshCredentialsId) {
            bindings << script.sshUserPrivateKey(credentialsId: credentials.sshCredentialsId, keyFileVariable: 'SSH_KEY_CHECK')
        }
        script.withCredentials(bindings) {
            script.echo '[deploy] credentials found'
        }
    }

    /** Runs a platform playbook with the job's credentials; checks run it with only the engine's own variables. */
    void playbook(Map args) {
        Map common = [vaultCredentialsId: credentials.vaultCredentialsId]
        if (credentials.sshCredentialsId) common.credentialsId = credentials.sshCredentialsId
        // the plugin starts ansible-playbook in the workspace root, where no ansible.cfg is found
        script.withEnv(["ANSIBLE_CONFIG=${script.env.WORKSPACE}/platform/ansible/ansible.cfg"]) {
            script.ansiblePlaybook(common + args)
        }
    }

    // every executor gets the files this build validated, not what the branch holds by now
    private void restoreSource() {
        script.deleteDir()
        script.unstash(SOURCE_STASH)
    }

    /** The review file as the prompt shows it: unsafe characters escaped, so it reads as its bytes, and long files cut. */
    @NonCPS
    static String shown(String content, String reviewFile) {
        String text = Values.printable(content, true)
        if (text.length() <= Limits.MAX_REVIEW_CHARACTERS) return text
        return text.substring(0, Limits.MAX_REVIEW_CHARACTERS) + "\n... (cut at ${Limits.MAX_REVIEW_CHARACTERS} characters; the archived ${reviewFile} holds all of it)"
    }

    static void finish(TaskResult result, long started, TaskOutcome outcome, String message) {
        result.outcome = outcome
        result.message = message
        result.seconds = (int) ((System.currentTimeMillis() - started) / 1000)
    }
}
