# Review before run: the user interface flow

This attachment uses screenshots to show how a task that needs review runs on Jenkins: the build stops before the task executes and shows in full what will be executed; the task executes only after someone clicks **Run**, and clicking **Abort** stops it. The mechanism, rules and tests are in sections 3.10 and 4.15 of BRIEF.md; this document covers only the user interface.

The screenshots come from the local Jenkins used by the tests (started by `tests/jenkins_server/server.py`, user interface language English, login not enabled, so the user is shown as `anonymous`). The job uses `pipelines/readwrite/Jenkinsfile`, and the task is the example task `task_d`: it writes each comma-separated item of the parameter `task_d_items` as one line `change <item>` of the review file, and once confirmed it prints `applied change <item>` for each line of the review file.

## 1. Start the build

On the **Build with Parameters** page, check `SKIP_DEPLOY`, check `PRE_SHOW_MORE` and then `task_d` in the less-used pre-deploy tasks (`task_d` has `menu: more`), fill in `task_d_items` in the form that then appears, and click **Build**. When triggering through the API, setting `DEPLOY_PLAN` to `{skip_deploy: true, pre: [{name: task_d, params: {task_d_items: 'restart cache, rotate logs'}}]}` has the same effect.

![Start the build](review-before-run/01-build-with-parameters.png)

## 2. The build stops for review

`task_d` first runs once in generate mode and writes the review file, then the build pauses. On the Stages page, `pre: task_d` shows a pause icon, with the review prompt and the **Run** / **Abort** buttons on the right:

![Stages page](review-before-run/02-stages-waiting.png)

The prompt on the Stages page folds the line breaks into one line. The console has the full prompt: the task to execute, the sha256 of the review file and its full text, with line breaks kept; the last line is the two links **Run or Abort**. Review here:

![Console](review-before-run/03-console-waiting.png)

The page opened by **Paused for Input** in the left menu also folds the prompt into one line, which makes long content hard to read:

![Confirmation page](review-before-run/04-input-page.png)

The review file is also archived as the build artifact `.deploy-run/review/pre-task_d.txt`, which can be opened or downloaded on its own:

![Review file](review-before-run/05-review-file.png)

The wait holds no executor: **Build Executor Status** on the home page shows `0/2`.

![Executors idle](review-before-run/06-executors-idle.png)

## 3. Click Run: execute from the review file

The console shows `Approved by anonymous`, `[deploy] task_d confirmed by anonymous`, `verifySha256` (the review file has the same sha256 as at confirmation) in turn; then `task_d` runs in execute mode and prints the changes of the review file line by line:

![Console after confirmation](review-before-run/07-console-confirmed.png)

![Stages page after confirmation](review-before-run/08-stages-confirmed.png)

In the report `deploy-report.json`, the task's `reviewer` is the person who confirmed:

![Report](review-before-run/09-report.png)

## 4. Click Abort: nothing is executed

The build is ABORTED, the console has `Rejected by anonymous`, the task did not run in execute mode, and in the report the task is `aborted`, `not confirmed`. The result is the same when the wait exceeds `review.timeout_minutes`.

![Console after abort](review-before-run/10-console-aborted.png)

![Stages page after abort](review-before-run/11-stages-aborted.png)

## 5. Build history

Builds that ran successfully after confirmation are green; aborted or timed-out builds are gray:

![Build history](review-before-run/12-job-history.png)

## Who can click Run and Abort

The engine's `input` does not set `submitter`. On a Jenkins with security enabled, people with Job/Build permission on this job can click **Run** and **Abort**, people with only Job/Cancel permission can click only **Abort**, and people who can only view can click neither; Jenkins administrators can click both. The basis and the test coverage are in section 4.15 of BRIEF.md.
