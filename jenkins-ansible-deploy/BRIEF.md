# Jenkins + Ansible deployment pipeline template

## 1. Scope

One git repository holds both the Jenkins pipeline and the Ansible code. The deployment targets are the **host groups** under `deploy_targets` in the Ansible inventory, selected (multiple choice) on the Jenkins parameter page. The deploy stage can be skipped with a checkbox. You can pick zero or more **extra tasks** to run before the deployment, and zero or more to run after it. The parameter page shows only the parameters of the selected tasks, filled in by parameter type (string, integer, boolean, choice). When a task fails it is handled as `abort` (stop the pipeline) or `warn` (mark the build unstable and continue). Host and host group parameters are managed only in the inventory; the parameter page cannot override them.

The code is split into **business** and **infrastructure** parts. Each extra task is one directory under `business/tasks/` with its own metadata; the engine discovers it automatically. Adding a business task does not change the engine, the pipeline skeleton, the generic playbooks or the test framework. One Jenkins has **two jobs** that share one engine: the read-only job can run only tasks declared read-only and has no deploy stage; the read-write job can run all tasks and the deployment. A task can declare a **review before it runs**: the engine first has the task write out what it will do and shows it; it runs only after someone confirms, and what runs must be exactly what was shown at review. The parameter page is built with the Active Choices plugin and shows the form of the selected tasks live on the same page. Instead of the page, a complete plan in YAML, `DEPLOY_PLAN`, can be passed, for triggering from the API and from upstream jobs. The extra tasks and the deployment itself are placeholders: they only take parameters, validate them and print the actions they would take; they do not connect to or change the target hosts.

Not included: multi-environment inventories, dynamic inventories, rollback, running tasks in parallel, accessibility support on the parameter page.

## 2. Environment

| Component | Version |
| --- | --- |
| Jenkins | 2.580.1 (Java 21) |
| Jenkins plugins | `ansible` 635.v34b_48e979c86, `uno-choice` (Active Choices) 2.8.11, `pipeline-groovy-lib` 822.vc95f70dea_db_7, `pipeline-utility-steps` 3.810.va_7672d206740, `pipeline-input-step` 560.v56198a_642157, `credentials-binding` 728.v902a_273b_8947, `git` 5.11.0, `workflow-cps` 4395.v738456a_5da_6b_, `script-security` 1436.v1082791135e4, `workflow-aggregator` 608.v67378e9d3db_1, `pipeline-stage-view` (Stage View) 2.41 with its dependencies `pipeline-rest-api` 2.41 and `pipeline-graph-analysis` 254.v0f63a_a_447dca_; every plugin and its version, dependencies included, is pinned in `tests/jenkins_server/plugins.txt` |
| ansible-core | 2.21.5 |
| ansible-lint | 26.10.1 |
| Python | 3.13; tests use pytest 9.1.1, playwright 1.64.0, PyYAML 6.0.1, cryptography 50.0.1 |

The Jenkinsfile is loaded from git and runs in the Groovy sandbox. The engine is loaded dynamically as a shared library from the same git repository and also runs in the sandbox. **No administrator has to approve any script, and no administrator has to configure a global shared library.**

To use it on a real Jenkins:

1. Install the plugins `workflow-aggregator` (includes `workflow-cps`, `pipeline-input-step`, `pipeline-groovy-lib` and the other Pipeline plugins), `git`, `ansible`, `pipeline-utility-steps`, `credentials-binding`, `ssh-credentials`, `uno-choice` (Active Choices, draws the parameter page) and `pipeline-stage-view` 2.41 (Stage View, where reviews are confirmed; it brings `pipeline-rest-api` 2.41 and `pipeline-graph-analysis` 254.v0f63a_a_447dca_). The Stages page in the attached screenshots comes from `pipeline-graph-view` (optional). Per-job permissions need an authorization plugin, for example `matrix-auth`.
2. On every Jenkins node that may run a build, install git and ansible-core 2.21.5 (for example `pip install ansible-core==2.21.5`), with `git` and `ansible-playbook` in `PATH`: the engine's `node` does not name a label, so any node may be picked.
3. Two credentials (scope Global, or in the folder that holds both jobs): `ansible-vault-password` (kind Secret text, the ansible-vault password) and `deploy-ssh-key` (kind SSH Username with private key: the username is the user that logs in on the target hosts, and the public key of the private key is in that user's `~/.ssh/authorized_keys`). The IDs are written in the `deployPipeline(...)` call of both Jenkinsfiles; if SSH is not needed, set `sshCredentialsId` to `null`.
4. Put the contents of `examples/` at the repository root: the Jenkinsfile Script Path, the shared library's `libraryPath` and the playbook paths in the engine are all relative to the repository root. The example `vault.yml` is encrypted with a public example password; replace it with your own: create it under `business/inventory/group_vars/all/` with `ansible-vault create vault.yml --vault-password-file <password file>` (`tests/` is only for testing this template and is not copied with `examples/`; in the other commands in this document, `../../../tests/example-vault-password.txt` means the example password file of this template).
5. Create two Pipeline jobs (any names; the output below uses `deploy-read-write`), both "Pipeline script from SCM", SCM Git, with the repository URL and branch; Script Path `pipelines/readonly/Jenkinsfile` and `pipelines/readwrite/Jenkinsfile` respectively. Let only trusted branches build (Branches to build lists only protected branches, for example `*/main`): the pipeline loads Groovy code from the repository, runs playbooks from the repository, and holds the vault password and the SSH private key. Give Job/Build permission on the read-write job only to people allowed to release; anyone who can run a job can also confirm or abort its review (4.15).
6. Click "Build Now" once for each job: this build only registers the build parameters and ends as NOT_BUILT. After that use "Build with Parameters".

Running the tests (in `jenkins-ansible-deploy/`):

```bash
pip install ansible-core==2.21.5 ansible-lint==26.10.1 pytest==9.1.1 playwright==1.64.0 PyYAML==6.0.1 cryptography==50.0.1
python3 -m playwright install chromium         # on machines that cannot download a browser, use export CHROMIUM_PATH=<installed chromium> instead
python3 -m pytest tests                       # all; the first run downloads Jenkins and the plugins
python3 -m pytest tests -m 'not jenkins'      # only the tests that do not need Jenkins
```

`java` (21) and `git` must be in `PATH`. The tests start their own local Jenkins on a free port, listening only on 127.0.0.1 and without login (for testing only), and stop it at the end. Jenkins 2.580.1, the plugin installation tool plugin-installation-manager 2.15.0 and the plugins in `plugins.txt` are downloaded to `tests/.jenkins/` (ignored by git, sha256 checked); the first run needs access to get.jenkins.io, github.com and updates.jenkins.io (the plugin update site). The tests copy `examples/` into the root of a temporary git repository and create the credentials with the IDs in the read-write Jenkinsfile (both Jenkinsfiles use the same IDs; the vault password comes from `tests/example-vault-password.txt`, the SSH private key is generated at random). Most tests create their own job and delete it at the end; tests that only validate plans and do not affect each other share one job per module.

## 3. Example code

```
examples/                            copy to the repository root to use
  pipelines/                         infrastructure
    readonly/Jenkinsfile             read-only job: loads the engine, declares the mode and credential IDs
    readwrite/Jenkinsfile            read-write job: same as above
  platform/                          infrastructure
    jenkins/                         engine (shared library)
      vars/deployPipeline.groovy     the whole pipeline
      src/deploy/                    classes and enums, see 3.4
    ansible/
      ansible.cfg                    inventory = ../../business/inventory, roles_path = ../../business/tasks:../../business
      run-task.yml                   runs one task (role name passed in by Jenkins)
      deploy.yml                     deployment (role deploy)
      check-inventory.yml            checks each host's inventory variables without connecting to hosts
      check-variable-names.yml       whether task parameter names clash with inventory variables
  business/                          business
    inventory/                       hosts.yml, group_vars/, host_vars/
    deploy/                          the deployment itself (role deploy)
    tasks/<task name>/               one extra task: task_a, task_b, task_c, task_d, check_a, check_c
  .ansible-lint
tests/                               pytest: platform playbook tests, the runner of each task's tests/cases.yml, generic task checks,
                                     Jenkins integration tests, browser tests
```

### 3.1 Flow of one build

```
Jenkinsfile        node: check out, get commit C; load platform/jenkins/ from commit C of the repository with the library step (4.12); call deployPipeline
Prepare            node: check out commit C
                   read business/tasks/*/meta/task.yml and each task's argument_specs.yml, defaults/main.yml, inventory/hosts.yml
                   properties(): disallow concurrent builds, register build parameters (effective from the next build)
                   bind both credentials once with withCredentials to confirm they exist (4.9)
                   run check-variable-names.yml: whether task parameter names clash with inventory variables (4.16)
                   stash all files
                   parameters not registered yet (first build): result NOT_BUILT, end
Plan               no executor: read DEPLOY_PLAN or the values submitted on the parameter page
                   validate into an execution plan; on errors list them all at once and fail, no task runs
pre: <id> ...      one stage per pre-deploy task; each time unstash on an executor, then ansible-playbook run-task.yml -e @params file [-l selected groups]
                   tasks that need review: run in generate mode on an executor -> wait for confirmation without an executor -> run in execute mode on an executor (4.15)
Deploy             no such stage when the deployment is skipped or in the read-only job; otherwise ansible-playbook deploy.yml -l selected groups
post: <id> ...     same as pre-deploy
Report             print the result summary, archive deploy-report.json
```

### 3.2 inventory

`business/inventory/hosts.yml` contains only the relation between hosts and groups. The child groups of `deploy_targets` are the release targets on the parameter page:

```yaml
all:
  children:
    deploy_targets:
      children:
        web:
          hosts:
            web-01:
            web-02:
        api:
          hosts:
            api-01:
        worker:
          hosts:
            worker-01:
            worker-02:
```

All variables are in `group_vars/` and `host_vars/`:

| File | Content |
| --- | --- |
| `group_vars/all/main.yml` | Shared by all hosts: `app_base_dir: /opt/apps`, `app_run_user: app`, `app_api_token: "{{ vault_app_api_token }}"` |
| `group_vars/all/vault.yml` | Whole file encrypted with ansible-vault, contains only `vault_app_api_token`. The example is encrypted with the public example password `example-only-password` (`tests/example-vault-password.txt`) and its content is also an example value; a real project must replace both password and content |
| `group_vars/web.yml` etc. | Application parameters per group: `app_name`, `app_version`, `app_port`, `app_max_connections` |
| `host_vars/<host>.yml` | `ansible_host` (RFC 5737 documentation addresses 192.0.2.11-15); `web-02.yml` also overrides `app_max_connections` to 200 |

The version `app_version` is a group variable too: releasing a new version = changing `group_vars/<group>.yml` in git and committing; Jenkins deploys the state in git. Inventory directory layout and variable layering, secrets and validation methods are in [attachments/inventory-practices.md](attachments/inventory-practices.md).

### 3.3 Deployment and extra tasks

`platform/ansible/deploy.yml` runs role `deploy` (`business/deploy/`) on `deploy_targets`; Jenkins narrows the scope to the selected groups with `--limit 'web:api'`. `business/deploy/meta/argument_specs.yml` declares the host variables the deployment needs and their types (`app_port: int, required` and so on); Ansible validates against it automatically before the role's first task (`Role._prepend_validation_task` in `lib/ansible/playbook/role/__init__.py` of `ansible/ansible@2.21.5`).

Each extra task is one role under `business/tasks/`:

| Task | Read/write | Runs on | Parameters (type, default) |
| --- | --- | --- | --- |
| `task_a` | write | selected host groups | `task_a_message` (str, required), `task_a_repeat` (int, 1, range 1-5), `task_a_delay_seconds` (int, 0) |
| `task_b` | read | the Jenkins node itself | `task_b_path` (str, `business/inventory/hosts.yml`, relative to the template root, must not be absolute or contain `..`), `task_b_min_bytes` (int, 1) |
| `task_c` | write | selected host groups | `task_c_mode` (str, choices `plan`/`apply`, `plan`), `task_c_verbose` (bool, false), `task_c_label` (str, empty) |
| `task_d` | write, review before it runs | the Jenkins node itself | `task_d_items` (str, required): changes, separated by commas |
| `check_c` | read | selected host groups | no parameters |
| `check_a` | read | selected host groups | `check_a_min_connections` (int, required): the lowest `app_max_connections` each host must have |

The four role files of a task directory, with `task_a` as the example (the task's own tests, `tests/cases.yml`, are in 3.8):

```yaml
# business/tasks/task_a/meta/task.yml: metadata Jenkins needs and Ansible does not
description: Example task that runs on the chosen host groups
phases: [pre]            # allowed phases: pre / post
access: write            # read: the read-only job can run it too; write: only the read-write job can run it
menu: main               # main: always shown on the parameter page; more: appears only after "Show more" is ticked
run_on: targets          # targets: the selected host groups; controller: the Jenkins node itself
policy: abort            # default failure policy: abort / warn
timeout_minutes: 5       # 1 to 240; a timeout counts as task failure
order: 10                # default run order within a phase, ascending; must not repeat between tasks
```

A task that needs review also adds:

```yaml
review:
  timeout_minutes: 30    # time to wait for confirmation, 1 to 240; on timeout the build is ABORTED and the task does not run
```

```yaml
# business/tasks/task_a/meta/argument_specs.yml: parameter names, types, required, defaults, descriptions
argument_specs:
  main:
    short_description: Example task A - runs on each host of the chosen host groups
    options:
      task_a_message:
        type: str
        required: true
        description: Text to print
      task_a_repeat:
        type: int
        default: 1
        description: How many times to print it, 1 to 5
      task_a_delay_seconds:
        type: int
        default: 0
        description: Seconds to wait before starting, to show a timeout
```

```yaml
# business/tasks/task_a/defaults/main.yml: the defaults that actually apply at run time; keep them equal to the defaults above
task_a_repeat: 1
task_a_delay_seconds: 0
```

```yaml
# business/tasks/task_a/tasks/main.yml
- name: Check task_a_repeat range
  ansible.builtin.assert:
    that:
      - task_a_repeat >= 1
      - task_a_repeat <= 5
    fail_msg: "task_a_repeat must be between 1 and 5, got {{ task_a_repeat }}"
    quiet: true

- name: Wait before starting
  ansible.builtin.pause:
    seconds: "{{ task_a_delay_seconds }}"
  when: task_a_delay_seconds > 0

- name: Print the message
  ansible.builtin.debug:
    msg: "{{ inventory_hostname }}: {{ task_a_message }} ({{ item }}/{{ task_a_repeat }})"
  loop: "{{ range(1, task_a_repeat + 1) | list }}"
```

`platform/ansible/run-task.yml` runs any one task; the role name and hosts are passed in by the engine:

```yaml
- name: "Task {{ task_role }}"
  hosts: "{{ task_hosts }}"
  gather_facts: false
  tasks:
    - name: "Run {{ task_role }}"
      ansible.builtin.include_role:
        name: "{{ task_role }}"
```

It can also be run directly, without Jenkins (in `examples/platform/ansible`):

```bash
echo '{"task_role": "task_a", "task_hosts": "deploy_targets", "task_a_message": "hello", "task_a_repeat": 2}' > /tmp/vars.json
ansible-playbook run-task.yml --limit api --vault-password-file ../../../tests/example-vault-password.txt -e @/tmp/vars.json
```

Output excerpt:

```
    "msg": "api-01: hello (1/2)"
    "msg": "api-01: hello (2/2)"
```

### 3.4 Engine

The engine is the Jenkins shared library under `platform/jenkins/`: `vars/deployPipeline.groovy` is the whole pipeline, and the classes are under `src/deploy/`. Code that calls pipeline steps is only in `vars/deployPipeline.groovy`, `ParameterPage` and `TaskRunner`, which call steps through the `script` object passed in; the other classes only compute, and all their methods are marked `@NonCPS` (4.13).

| File (`src/deploy/`) | Responsibility |
| --- | --- |
| `Catalog` | Builds the task definitions and host groups from each task directory's three YAML files and `hosts.yml`; checks metadata, parameter declarations and defaults |
| `PlanParser` | Validates `DEPLOY_PLAN` (or the same structure built from the form) into an execution plan `Plan` |
| `Selection`, `ActiveChoicesForm` | Turn the values submitted on the parameter page into the `DEPLOY_PLAN` structure; generate the Active Choices scripts |
| `Values` | Checks one value by parameter type (shared by defaults, `DEPLOY_PLAN` and the form) |
| `ParameterPage` | Build parameter definitions of the parameter page; reads the submitted values into a plan draft (`draft`, waits for nothing) |
| `TaskRunner` | Runs tasks, reviews and the deployment; produces the report |
| `TaskResult` | One row of the report |
| `TaskDefinition`, `ParameterDefinition`, `PlannedTask`, `Plan` | Task and parameter definitions; one task in the plan; the whole plan |
| `Labels` | Finds enum constants by their external spelling; records a problem when not found |
| `FormStyle` | Inline styles of the Active Choices task form (column widths, spacing), kept in one map; see `attachments/parameter-page.md` section 3.3 |
| `FormField` | Keys of form fields (`task.field`); the two fields besides parameters, `policy` and `order` |
| `AnsibleNames` | Names Ansible reserves that a name with a task-name prefix could still hit (keywords and special variables containing `_`); `test_engine_refuses_every_name_ansible_reserves_that_a_task_prefix_can_produce` ensures it covers the names ansible-core treats as such |
| `Limits` | Input limits: `DEPLOY_PLAN` and every parameter page value 20000 characters, 50 tasks in a plan (and per phase on the page), string parameters 1000 characters, `id` 64 characters, timeout 240 minutes; the review prompt shows at most 20000 characters of the review file; `Limits.bounded` applies the length limit before a value is parsed |
| Enums | `Phase`, `FormField`, `TaskAccess`, `TaskMenu`, `AccessMode`, `FailurePolicy`, `RunLocation`, `ParameterType`, `RequestKind`, `ReviewStep`, `TaskOutcome`, `EngineVariable`; the external spelling (values in YAML, build parameter names, results in the report) is written on each constant, and parsing an unknown value is an error |

### 3.5 Jenkinsfile

Read-write job (`pipelines/readwrite/Jenkinsfile`):

```groovy
// The read-write job: every task and the deployment.
// The engine is loaded from the commit this file came from, so one build never mixes two revisions.
String commit
node {
    commit = checkout(scm).GIT_COMMIT
}
library(identifier: "deploy-engine@${commit}",
        retriever: modernSCM(scm: [$class: 'GitSCMSource', remote: scm.userRemoteConfigs[0].url], libraryPath: 'platform/jenkins/'))
deployPipeline(commit: commit, mode: 'read-write',
               vaultCredentialsId: 'ansible-vault-password', sshCredentialsId: 'deploy-ssh-key')
```

The read-only job (`pipelines/readonly/Jenkinsfile`) differs only in `mode: 'read-only'` and the first comment line. If the repository needs credentials to check out, `scm.userRemoteConfigs[0]` also has `credentialsId`; add `credentialsId: scm.userRemoteConfigs[0].credentialsId` to `GitSCMSource`.

The pipeline is Scripted: the build parameters must be generated from the task directories and inventory read at run time, each task gets one stage, and waiting for confirmation must leave the executor; all of this is controlled directly by `node` and `stage` in `deployPipeline`. Do not also set parameters or "Do not allow concurrent builds" in the job configuration: the `properties` step removes the job properties it set itself on the previous run, and properties of the same kind overwrite each other (`Execution.run` in `src/main/java/org/jenkinsci/plugins/workflow/multibranch/JobPropertyStep.java` of `jenkinsci/workflow-multibranch-plugin@842.v3a_b_59b_57b_e6e`).

### 3.6 Build parameters

| Parameter | Type | Purpose |
| --- | --- | --- |
| `TARGET_GROUPS` | Active Choices checkboxes | Host groups; options such as `web (web-01, web-02)` come from the inventory |
| `SKIP_DEPLOY` | Boolean, only in the read-write job | Skip the deployment; pre- and post-deploy tasks run as usual |
| `PRE_TASKS` / `POST_TASKS` | Active Choices checkboxes with a search box | Tasks allowed in this phase, allowed by this job's mode, and with `menu: main`, sorted by `order` |
| `PRE_SHOW_MORE` / `POST_SHOW_MORE` | Boolean, only when the phase has tasks with `menu: more` | When ticked, the list in the next row appears |
| `PRE_MORE_TASKS` / `POST_MORE_TASKS` | Active Choices reactive checkboxes with a search box | Tasks with `menu: more`; empty on the page while `*_SHOW_MORE` is not ticked, and unticking it clears the tasks already selected here. When triggered through the API, values can be passed directly; the engine ignores `*_SHOW_MORE` |
| `PRE_TASK_OPTIONS` / `POST_TASK_OPTIONS` | Active Choices dynamic HTML | Generated live from what is ticked in the two lists: one group per selected task, with a number box for the run order (default `order`; smaller numbers run first, ties are broken by `order`), a failure policy drop-down and an input box per parameter, prefilled with the role's defaults; integers get a number box, booleans and choices a drop-down, and required parameter names are followed by "(required)" |
| `DEPLOY_PLAN` | Multi-line text | When not empty, all the parameters above are ignored |

`DEPLOY_PLAN` format (the read-only job does not accept `skip_deploy`):

```yaml
targets: [web, api]
skip_deploy: false
pre:
  - task_b                                                    # all defaults
  - {name: task_a, policy: warn, params: {task_a_message: hello}}
post:
  - {name: check_c, id: check_c_after}                        # use id to tell apart runs of the same task in one phase
  - {name: check_a, timeout_minutes: 3, params: {check_a_min_connections: 50}}
```

Quote string parameter values in YAML: unquoted `yes`, `2026-01-01`, `1.0` are parsed as boolean, date, float, and the engine rejects such values.

Triggering from the API or an upstream job: POST to the job's `buildWithParameters` endpoint with `DEPLOY_PLAN` as the only parameter. Most integration tests trigger builds this way (`Jenkins.start_build` in `tests/jenkins_server/client.py`); a few cases assign Active Choices parameters directly to simulate a page submission. The Active Choices plugin states that these parameters are meant only for people filling in the page and are not guaranteed to work when triggered through the API or scripts (the "Known Limitations" section of `README.md` in `jenkinsci/active-choices-plugin@2.8.11`), so do not pass `TARGET_GROUPS` and similar parameters directly through the API.

UI details of the parameter page and the format of the submitted values are in [attachments/parameter-page.md](attachments/parameter-page.md).

### 3.7 Output

Run the read-write job with the `DEPLOY_PLAN` below (the same choices on the parameter page give the same result):

```yaml
targets: [web, api]
pre:
  - {name: task_a, params: {task_a_message: hello}}
  - task_b
post:
  - {name: check_a, policy: warn, params: {check_a_min_connections: 150}}
```

Build result UNSTABLE. Console excerpt (`<workspace>` stands for the job's workspace directory, `<random>` is the temporary file name the plugin generates):

```
[Pipeline] { (Prepare)
[deploy] credentials found
[deploy-read-write] $ ansible-playbook platform/ansible/check-variable-names.yml --private-key <workspace>/ssh<random>.key -u deploy --vault-password-file <workspace>/vault<random>.password -e @.deploy-run/variable-names.json
[Pipeline] { (Plan)
[deploy] using DEPLOY_PLAN; every other parameter is ignored
[deploy] plan:
  groups: [web, api]
  deploy: yes
  Pre-deploy tasks:
    - task_a [abort] [task_a_message:hello, task_a_repeat:1, task_a_delay_seconds:0]
    - task_b [abort] [task_b_path:business/inventory/hosts.yml, task_b_min_bytes:1]
  Post-deploy tasks:
    - check_a [warn] [check_a_min_connections:150]
[Pipeline] { (pre: task_a)
[deploy-read-write] $ ansible-playbook platform/ansible/run-task.yml -l web:api --private-key <workspace>/ssh<random>.key -u deploy --vault-password-file <workspace>/vault<random>.password -e @.deploy-run/pre-task_a.json
    "msg": "web-01: hello (1/1)"
    "msg": "web-02: hello (1/1)"
    "msg": "api-01: hello (1/1)"
[Pipeline] { (pre: task_b)
[deploy-read-write] $ ansible-playbook platform/ansible/run-task.yml --private-key <workspace>/ssh<random>.key -u deploy --vault-password-file <workspace>/vault<random>.password -e @.deploy-run/pre-task_b.json
    "msg": "business/inventory/hosts.yml: 421 bytes"
[Pipeline] { (Deploy)
[deploy-read-write] $ ansible-playbook platform/ansible/deploy.yml -l web:api --private-key <workspace>/ssh<random>.key -u deploy --vault-password-file <workspace>/vault<random>.password
    "msg": "deploy web 2.3.1 to web-02 (192.0.2.12) dir=/opt/apps/web port=8080 max_connections=200 user=app"
    "msg": "deploy web 2.3.1 to web-01 (192.0.2.11) dir=/opt/apps/web port=8080 max_connections=100 user=app"
    "msg": "deploy api 1.8.0 to api-01 (192.0.2.13) dir=/opt/apps/api port=9000 max_connections=300 user=app"
[Pipeline] { (post: check_a)
[deploy-read-write] $ ansible-playbook platform/ansible/run-task.yml -l web:api --private-key <workspace>/ssh<random>.key -u deploy --vault-password-file <workspace>/vault<random>.password -e @.deploy-run/post-check_a.json
    "msg": "web-01: app_max_connections=100 < 150"
    "msg": "web-02: app_max_connections=200"
    "msg": "api-01: app_max_connections=300"
WARNING: [deploy] task check_a failed: Ansible playbook execution failed (warn: continuing)
[Pipeline] { (Report)
[deploy] results:
  pre    task_a           abort passed                 1s
  pre    task_b           abort passed                 1s
  deploy deploy           abort passed                 1s
  post   check_a          warn  failed-continued       1s
Finished: UNSTABLE
```

`task_b` runs on the Jenkins node itself, so its command has no `-l`; `max_connections=200` of `web-02` comes from `host_vars/web-02.yml`; the order of output from different hosts in the same play may differ between runs. Each entry in `deploy-report.json` has `id`, `name`, `phase`, `policy`, `params`, `outcome` (`passed`, `failed-stopped`, `failed-continued`, `timed-out-stopped`, `timed-out-continued`, `aborted`, `error`), `seconds`, `message` (failure reason) and `reviewer` (the person who confirmed a reviewed task; `null` for the others).

### 3.8 Adding a business task

Create only one directory `business/tasks/<task name>/`; do not change `platform/`, `pipelines/` or `tests/`. The example tasks (`task_a` ... `check_c`) are placeholders: the engine's own integration tests in `tests/` use them as fixtures, so when you replace them, adapt those tests too. The directory name is the task name; use only lowercase letters, digits and `_`, starting with a letter (it is also the role name, the parameter name prefix, the first part of form field keys and the default `id`):

1. `meta/task.yml`: `description` (ASCII only, without `@ = + "` and without the word `selected`; see section 5 for why), `phases`, `access`, `menu`, `run_on`, `policy`, `timeout_minutes`, `order` (different from every other task); add `review` if the task needs review.
2. `meta/argument_specs.yml`: parameter names start with `<task name>_` and must not clash with inventory variables, names Ansible reserves (names that ansible-core's `ansible.vars.reserved.is_reserved_name` treats as reserved, plus special variables such as `inventory_hostname`, `groups`, `hostvars`, `playbook_dir`) or engine variables (`task_role`, `task_hosts`, `task_review_mode`, `task_review_file`, `task_parameter_names`); use only the types `str`, `int`, `bool`, and no `type` means `str`; `choices` only for `str`; write `default` for parameters that are not required; with no parameters write `options: {}` (see `check_c`).
3. `defaults/main.yml`: the default of each parameter that is not required, equal to `default`, as a literal value (no `{{`).
4. `tasks/main.yml`: the task itself; make it fail with `assert` / `fail` when a condition is not met. If it needs review, write the two modes as in 3.10.
5. `tests/cases.yml`: the task's own tests, a list of cases. `tests/test_task_cases.py` finds every `business/tasks/*/tests/cases.yml`, runs each case once through `run-task.yml` the way the engine does (test id `<task>/<name>`), and fails for a task directory without cases. `name` and `rc` (exit code of `ansible-playbook`: 0 passed, 2 a task failed) are required; optional are `params`, `limit`, `recap_hosts` (exactly the hosts in PLAY RECAP), `recap_failed` (host to failed count), `output` and `absent` (texts that must or must not appear in stdout), and for a task with `review`: `review_mode` (`generate` or `execute`, required there), `review_file` (its text before the run) and `review_file_after` (its text after). An unknown key or a parameter that `meta/argument_specs.yml` does not declare fails the case; the full description is the docstring of `tests/test_task_cases.py`. For example (more in `task_a`, and `task_d` for review):

```yaml
- name: out-of-range
  params: {task_a_message: x, task_a_repeat: 9}
  limit: web
  rc: 2
  output: ['task_a_repeat must be between 1 and 5, got 9']
  absent: ['TASK [task_a : Print the message]']
```

6. Run `python3 -m pytest tests -m 'not jenkins'`: `tests/test_tasks.py` goes through every directory under `business/tasks/` and checks the rules of steps 1-4, and `tests/test_task_cases.py` runs the cases of step 5, so a new task is checked automatically. The engine checks most of them in `Prepare` of every build; if one is broken the build fails and no task runs (`Catalog.parse`; `test_problem_in_a_task_directory_or_the_inventory_stops_the_build_before_any_task_runs` covers the main cases): the directory name, that `tasks/main.yml` and `meta/argument_specs.yml` exist, metadata fields and values, no repeated `order`, the fields of parameter declarations, parameter types and `choices`, the parameter name prefix, engine variables, `ansible_*` and names Ansible reserves, that `defaults/main.yml` sets only declared parameters, the types of defaults, the characters of `description`, at least one host in each host group, and clashes with inventory variables (4.16). That `default` equals `defaults/main.yml` is checked only by the tests.
7. After committing, run one build first (any parameters); the new task appears on the parameter page from the next build on.

Committing only the files of a new task directory, without changing any other file, is enough for a build to run that task: `test_new_task_directory_runs_without_any_platform_change` ensures this. The shared tests that check the task lists of the parameter pages take the expected names, order and labels from `meta/task.yml` (`phase_tasks` and `task_label` in `tests/conftest.py`), so a new task does not make them fail.

### 3.9 Adding a host or host group

Host group: add the group and its hosts under `deploy_targets.children` in `hosts.yml` (a group name holds only letters, digits and `_` and does not start with a digit, a host name only letters, digits, `.`, `_` and `-`, because they go to `--limit` and into comma-separated lists; neither contains the word `selected`, because they are shown in checkbox labels, section 5), add `business/inventory/group_vars/<group>.yml` (all variables role `deploy` needs) and `business/inventory/host_vars/<host>.yml` for each host. Then run `ansible-playbook check-inventory.yml --vault-password-file <password file>` in `examples/platform/ansible` to confirm each host has all variables with correct types. The new group also appears on the parameter page from the next build on.

Rules for variables (reasons in [attachments/inventory-practices.md](attachments/inventory-practices.md)):
- Put variables only in `business/inventory/group_vars/` and `business/inventory/host_vars/`; do not create `group_vars/` or `host_vars/` next to the playbooks.
- Application parameters start with `app_`, encrypted values with `vault_`; secret values go into `group_vars/all/vault.yml` and are referenced in `main.yml` as `"{{ vault_xxx }}"`; in the argument_specs of role `deploy`, secret variables declare only `type: str` and `required: true`; tasks that use secret values add `no_log: true`.
- Do not use the names of task parameters (`<task name>_...`): the engine checks this in `Prepare` of every build and the build fails on a clash (4.16).
- `ansible-inventory --list` / `--host` print the decrypted plain text; do not run them in CI.
- Editing the encrypted file and changing the password (in `examples/business/inventory`):

```bash
ansible-vault edit group_vars/all/vault.yml --vault-password-file <password file>
ansible-vault rekey group_vars/all/vault.yml --vault-password-file <old password file> --new-vault-password-file <new password file>
```

After changing the password, also update the Jenkins credential `ansible-vault-password`.

### 3.10 Review before running

For a task with `review: {timeout_minutes: N}` in `meta/task.yml`, the engine runs it twice, each time passing two extra variables:

| Variable | First run | Second run |
| --- | --- | --- |
| `task_review_mode` | `generate`: write what will be done into the review file, change nothing | `execute`: read the review file and do what it says |
| `task_review_file` | Absolute path of the review file chosen by the engine (on the Jenkins node) | The same file, with the content shown at confirmation |

The implementation of `task_d`:

```yaml
- name: Write the changes to review
  ansible.builtin.copy:
    dest: "{{ task_review_file }}"
    content: "{% for item in task_d_items.split(',') %}change {{ item | trim }}\n{% endfor %}"
    mode: "0644"
  when: task_review_mode == 'generate'

- name: Read the reviewed changes
  ansible.builtin.slurp:
    src: "{{ task_review_file }}"
  register: task_d_reviewed
  when: task_review_mode == 'execute'

- name: Apply each reviewed change
  ansible.builtin.debug:
    msg: "applied {{ item }}"
  loop: "{{ (task_d_reviewed.content | b64decode).splitlines() }}"
  when: task_review_mode == 'execute'
```

Execute mode only reads the review file and does not generate it again from the parameters: when the parameters and the review file differ, the review file is what runs (case `task_d/execute-runs-the-review-file-not-its-parameters` in `business/tasks/task_d/tests/cases.yml`). The review file is on the Jenkins node. A task with `run_on: targets` adds `delegate_to: localhost` and `run_once: true` to the step that writes the file in generate mode, and in execute mode reads it with `lookup('ansible.builtin.file', task_review_file)` (lookups run on the Jenkins node), not with `slurp`, which runs on the target host. In the examples only `task_d` (which runs on the Jenkins node itself) shows review; this way of writing it has no example and no test.

Between the two runs the build pauses at an `input`: the prompt shows the sha256 and the full text of the review file, and the buttons are "Run" and "Abort". Review it in Stage View on the job page, where the cell of the paused stage opens a popup with the prompt and both buttons, or in the console; both keep the line breaks of the review file. After confirmation the console prints `[deploy] task_d confirmed by <user>` and the task's `reviewer` in the report is the person who confirmed. On abort or timeout the build is ABORTED, the task does not run, and the report shows the task as `aborted`, `not confirmed`. The review file is archived as the build artifact `.deploy-run/review/<phase>-<id>.txt`. The UI flow and screenshots are in [attachments/review-before-run.md](attachments/review-before-run.md).

## 4. Key points

### 4.1 abort / warn can only be implemented in the pipeline

Rule: each task calls `ansiblePlaybook` once on its own, catches failure with `try/catch`, then by policy calls `error` (FAILURE, later stages do not run) or `unstable` (UNSTABLE, continue). See `attempt` and `settle` in `TaskRunner`.

Mechanism: when the playbook returns non-zero, the Ansible plugin throws `hudson.AbortException("Ansible playbook execution failed")`; it has no "continue on failure" option and does not expose the exit code (`perform` in `src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`). So a failed parameter check, a failed assertion and an unreachable host all show up as the same exception.

Tests: `test_warn_failure_marks_build_unstable_and_continues` (build UNSTABLE, then `deploy.yml` runs as usual), `test_abort_failure_stops_before_deploy_and_later_tasks` (build FAILURE, `run-task.yml` runs only once, the task is `failed-stopped` in the report).

Not done this way: running all tasks in one `ansiblePlaybook` gives only one result, so you cannot decide blocking or non-blocking per task.

### 4.2 A timeout counts as task failure; a manual abort must not be treated as failure

Rule: each task is wrapped in `timeout(time: timeout_minutes)`; when an `InterruptedException` is caught, it counts as task failure (`timed-out-stopped` / `timed-out-continued`) only if the elapsed time has reached the timeout; otherwise the task is recorded as `aborted` and the exception is rethrown.

Mechanism: when `timeout` aborts `ansiblePlaybook` and when the build is aborted by hand, the plugin throws the same plain `java.lang.InterruptedException` without the cause; `FlowInterruptedException.getCauses()`, which could tell the causes apart, needs administrator approval in the sandbox. So the engine can only tell them apart by elapsed time (the `catch (InterruptedException e)` part of `attempt` in `TaskRunner`). Known gap: a manual abort after the timeout, within the grace period of `timeout`, is also counted as a timeout.

Tests: `test_timeout_counts_as_task_failure_and_follows_the_policy` (`timeout_minutes: 1`, the task waits 90 seconds, policy warn: the console prints `[deploy] task task_a failed: timed out after 1 min (warn: continuing)`, the build is UNSTABLE and continues to deploy); `test_manual_abort_during_a_task_aborts_the_build_instead_of_continuing` (abort clicked while a task runs: build ABORTED, no deployment, the task is `aborted` in the report).

Not done this way: wrapping `timeout { ansiblePlaybook(...) }` in `catchError(catchInterruptions: false)` also catches a manual abort, and the following steps keep running. What happens with this code (pipeline in the sandbox, `pause.yml` pauses 120 seconds, build aborted by hand at second 5):

```groovy
long started = System.currentTimeMillis()
catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE', catchInterruptions: false) {
    timeout(time: 1, unit: 'MINUTES') {
        echo 'BODY-START'
        ansiblePlaybook(playbook: 'pause.yml')
    }
}
echo "AFTER-WRAPPER elapsedMs=${System.currentTimeMillis() - started} currentResult=${currentBuild.currentResult}"
```

Output (console excerpt):

```
Pausing for 120 seconds
Aborted by unknown
java.lang.InterruptedException
	at org.jenkinsci.plugins.ansible.CLIRunner.execute(CLIRunner.java:52)
Setting overall build result to UNSTABLE
AFTER-WRAPPER elapsedMs=6036 currentResult=UNSTABLE
Finished: ABORTED
```

`catchInterruptions: false` applies only to `FlowInterruptedException` (`Callback.onFailure` in `src/main/java/org/jenkinsci/plugins/workflow/steps/CatchErrorStep.java` of `jenkinsci/workflow-basic-steps-plugin@1098.v808b_fd7f8cf4`), and that is not what `ansiblePlaybook` throws.

### 4.3 Parameters are passed to Ansible in a JSON file

Rule: task parameters are written to `.deploy-run/<phase>-<id>.json` and passed with `extras: '-e @file'`, not with the `extraVars` of `ansiblePlaybook`.

Mechanism: each value of `extraVars` is first turned into a string (`AnsiblePlaybookExecution.convertExtraVars` in `src/main/java/org/jenkinsci/plugins/ansible/workflow/AnsiblePlaybookStep.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`); except `true` / `false`, all are passed as `-e key=value`, and non-scalar values such as lists are skipped (`appendExtraVars` in `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`). Values in `key=value` form are strings in Ansible.

What happens: output when the same task is run in `examples/platform/ansible` once with `-e task_a_repeat=2` and once with `-e '{"task_a_repeat": 2, ...}'` (both commands also add `--vault-password-file ../../../tests/example-vault-password.txt`):

```
$ ansible-playbook run-task.yml --limit api -e task_role=task_a -e task_hosts=deploy_targets -e task_a_message=x -e task_a_repeat=2
fatal: [api-01]: FAILED! => {"changed": false, "msg": "Task failed: Error rendering expression: '>=' not supported between instances of '_AnsibleTaggedStr' and 'int'"}
(exit code 2)

$ ansible-playbook run-task.yml --limit api -e '{"task_role":"task_a","task_hosts":"deploy_targets","task_a_message":"x","task_a_repeat":2}'
    "msg": "api-01: x (1/2)"
    "msg": "api-01: x (2/2)"
(exit code 0)
```

`"2"` passes the `int` check of argument_specs (the check only tests whether it can be converted), but the variable itself is still a string, and the comparison fails.

### 4.4 Accept only registered task and parameter names, reject template syntax, limit lengths

Rule: a task name in the plan must be a directory under `business/tasks/`, and a parameter name must be in that task's `argument_specs.yml`; string parameters must not contain `{{`, `{%`, `{#`, must not start with `#jinja2:`, must not contain control, format or line-separator characters (except tab), and must be at most 1000 characters; parameter names have the task name as prefix, are not engine variables, do not start with `ansible_`, are not names Ansible reserves (`AnsibleNames`), and do not clash with inventory variables (4.16); the engine checks all of this before any task runs. All limits are defined once in `Limits`.

Mechanism: Ansible renders Jinja expressions in extra vars, and extra vars have the highest precedence. A string that starts with `#jinja2:` is treated as a template even without `{{`, and its first line can switch to other delimiters (`JINJA2_OVERRIDE` and `is_possibly_template` in `lib/ansible/_internal/_templating/_jinja_bits.py` of `ansible/ansible@2.21.5`). Control, format and line-separator characters (such as newline, `ESC`, `U+202E`, `U+2028`) can fake a line of output in the console and in the review prompt, or make the displayed text differ from the actual bytes; values that were refused still appear in the problem list, so `Values.problems` prints these characters there as `\uXXXX` escapes (`test_problems_print_control_characters_as_escapes_so_they_cannot_fake_a_line`). Without these limits, anyone who can trigger a build can run commands on the Jenkins node, or pass variables such as `ansible_connection` to change how hosts are connected; a parameter name that clashes with an inventory variable silently overrides the inventory value.

What happens: when a value containing a template goes to Ansible directly, without the engine (in `examples/platform/ansible`, with `--vault-password-file ../../../tests/example-vault-password.txt` added):

```
$ cat vars.json
{"task_role":"task_a","task_hosts":"deploy_targets","task_a_message":"{{ lookup(\"pipe\", \"echo INJECTED\") }}"}
$ ansible-playbook run-task.yml --limit api -e @vars.json
    "msg": "api-01: INJECTED (1/1)"
```

Through the engine these values are rejected; the build is FAILURE and no task runs: ensured by the cases `template-expression`, `template-statement`, `template-comment`, `template-override-header`, `control-character`, `format-character`, `undeclared-param`, `string-too-long`, `id-too-long`, `too-many-tasks`, `plan-too-long` of `test_invalid_plan_fails_before_any_task_runs` in `tests/test_pipeline_validation.py`; values exactly at the limit pass (`test_values_at_their_limit_are_accepted`).

### 4.5 All validation finishes before any task runs, and all problems are listed at once

Rule: `Catalog.parse`, `PlanParser.parse` and `Values.check` collect problems into a list and call `error` once at the end. Optional fields are checked with `== null` to see whether they were given: in Groovy `0`, `''`, `[]`, `[:]` are false, and `?:` would treat them as not given and skip the type check.

Problems covered (`test_invalid_plan_fails_before_any_task_runs`): unknown host group, no host group selected (needed for the deployment, or when deployment is skipped but a task runs on the selected host groups), host groups not a list; `skip_deploy` not a boolean; phase not a list; entry neither a task name nor a mapping; unknown task; task not allowed in that phase; unknown field in an entry; unknown or empty policy; required parameter missing or blank; `params` not a mapping; undeclared parameter; integer, choice, boolean or string type mismatch; the three template syntaxes and a leading `#jinja2:`; control, format and line-separator characters; a parameter name that is not a string; repeated `id` in one phase, invalid, empty or too long `id`; `timeout_minutes` not between 1 and 240; string too long; too many tasks (counted before any entry is read); unknown field in the plan; `DEPLOY_PLAN` not a mapping, not valid YAML or too long. Values submitted on the parameter page add: the same field twice (value contains `,@@`), a field of a task that is not ticked, a field that is not a declared parameter of that task, an integer outside 64 bits, a run order that is not an integer, template syntax, `*_TASK_OPTIONS` longer than the length limit of `DEPLOY_PLAN` (`test_invalid_form_values_fail_before_any_task_runs`), any other form value longer than that limit (`test_form_value_longer_than_the_plan_limit_fails_before_any_task_runs`); the read-only job adds: write tasks, the `skip_deploy` field, write tasks ticked in the form (4.14).

Test: `test_invalid_plan_lists_all_problems_at_once`. Output when run with `{targets: [nope], pre: [no_such_task, {name: task_a, policy: ask}]}` (console excerpt):

```
ERROR: [deploy] the plan has problems:
  - unknown groups [nope], expected [web, api, worker]
  - pre[0]: unknown task 'no_such_task', expected one of [task_a, task_b, task_c, check_c, check_a, task_d]
  - pre[1]: policy 'ask' is not one of [abort, warn]
  - pre[1]: task task_a needs parameter task_a_message
Finished: FAILURE
```

Not done this way: a wrong parameter is found only halfway through the deployment. Ansible's argument_specs check is kept as a second check.

### 4.6 Parameter types and descriptions come from argument_specs, run-time defaults from defaults/main.yml

Rule: task parameters are not declared a second time in Jenkins. `readCatalog` in `deployPipeline` reads each task's `meta/argument_specs.yml` (name, type, required, choices, description) and `defaults/main.yml` (defaults) with `readYaml` and passes them to `Catalog.parse`. argument_specs also contains `default`, equal to `defaults/main.yml`.

Mechanism: before a role starts, Ansible automatically inserts a `validate_argument_spec` task based on `argument_specs.yml` (`Role._prepend_validation_task` in `lib/ansible/playbook/role/__init__.py` of `ansible/ansible@2.21.5`). The `default` in argument_specs is for documentation; the documentation requires it to match the default in the code, and the actual default always comes from the role's defaults (the "Specification format" section of `docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst` in `ansible/ansible-documentation@b16dcb8`).

Tests: `test_documented_defaults_equal_role_defaults_and_cover_every_optional_option`; case `task_c/role-defaults-apply-when-no-value-is-passed` in `business/tasks/task_c/tests/cases.yml`; the prefilled values on the parameter page are the defaults (`test_selecting_a_task_shows_only_its_fields_prefilled_with_role_defaults`).

### 4.7 The first run only registers parameters; `params` must be read before `properties()`

Rule: `deployPipeline` first copies `params` (`Map requested = [:] + params`), then calls `properties()`. When the parameters are not registered yet (no `DEPLOY_PLAN` in `requested`; the parameter page always registers it; `RequestKind.of`), the build result is set to NOT_BUILT and no task runs.

Mechanism: each time `params` is read, besides the values submitted for this build it fills in defaults from the job's **current** parameter definitions (`getValue` in `plugin/src/main/java/org/jenkinsci/plugins/workflow/cps/ParamsVariable.java` of `jenkinsci/workflow-cps-plugin@4395.v738456a_5da_6b_`). After `properties()` runs, `params` of the first run already has defaults such as `DEPLOY_PLAN=''`, so you cannot tell whether the parameter page was submitted.

Test: `test_first_build_registers_parameters_and_runs_nothing` (the first build of both jobs is NOT_BUILT with the description `Parameters registered; run again with Build with Parameters`, and no task runs).

### 4.8 The Ansible configuration is passed through `ANSIBLE_CONFIG`

Rule: `playbook` in `TaskRunner` wraps every `ansiblePlaybook` call in `withEnv(["ANSIBLE_CONFIG=<workspace>/platform/ansible/ansible.cfg"])` and always passes `vaultCredentialsId`, and `credentialsId` when `sshCredentialsId` is set.

Mechanism: the plugin starts ansible-playbook in the root of the Jenkins workspace (the repository root) (`execute` in `src/main/java/org/jenkinsci/plugins/ansible/CLIRunner.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`), where there is no `ansible.cfg`. ansible-core looks at `ANSIBLE_CONFIG` first, then at `ansible.cfg` in the current directory (`find_ini_config_file` in `lib/ansible/config/manager.py` of `ansible/ansible@2.21.5`); relative paths of `inventory` and `roles_path` in `ansible.cfg` are expanded relative to the directory of that file (`ConfigManager.get_config_value_and_origin` in the same file, `unfrackpath` in `lib/ansible/utils/path.py` of `ansible/ansible@2.21.5`). So the same file serves both Jenkins and manual runs in `examples/platform/ansible`. Details in section 1.2 of [attachments/inventory-practices.md](attachments/inventory-practices.md).

That is why the command lines in section 3.7 have no `-i` and the inventory and roles are still found.

### 4.9 Every run needs the vault password, including tasks that run only on the local node

Rule: every `ansiblePlaybook` call passes `vaultCredentialsId`. `Prepare` first binds both credentials once with `withCredentials` (`TaskRunner.checkCredentials`); if a credential does not exist, the build fails before any playbook runs and the console shows the credential ID. The plugin accepts vault credentials of kind Secret text and Secret file; the check binds it as Secret text; with Secret file, replace `string(...)` there with `file(...)`.

Mechanism: the plugin writes a Secret text / Secret file credential to a temporary file in the workspace, passes it with `--vault-password-file` and deletes it afterwards (`appendVaultPasswordFile` in `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`). `group_vars/all/vault.yml` must be decrypted already when the inventory is loaded, whatever hosts the play targets. With a wrong credential ID the plugin reports no error and just passes no password (`perform` in `src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`); ansible-playbook then fails because it cannot decrypt, which looks the same as a task failure: if only `warn` tasks run, the build only becomes UNSTABLE.

Tests: `test_every_run_needs_the_vault_password_even_on_localhost`: without the password a task that runs only on the Jenkins node (the first task with `run_on: controller`, `task_b` in the examples) also fails with `[ERROR]: Attempting to decrypt but no vault secrets found.`, and no task runs. `test_missing_vault_credential_stops_the_build_before_any_task_runs`: when the credential does not exist the build is FAILURE, the console shows that credential's ID, and no task runs.

### 4.10 Waiting holds no executor

Rule: `deployPipeline` does not wrap the whole pipeline in `node`: `Prepare`, each task, the deployment and the report each request an executor and release it when done; `Plan` (which waits for nothing) and the review `input` (the engine's only wait) are outside `node`. Each time it enters an executor it first runs `deleteDir()`, then `unstash` of the files `Prepare` stored.

Mechanism: an `input` inside `node` holds the executor and workspace for the whole wait. `unstash` ensures each executor runs the files `Prepare` validated, not the latest commit on the branch.

Test: `test_waiting_for_review_holds_no_executor`: while the build waits for the review, the number of busy Jenkins executors is 0. `test_confirmed_run_uses_the_files_the_build_validated_even_if_the_branch_moved_during_the_review`: a commit that changes `task_d` lands on the branch while the review waits, and the confirmed run still prints the original output, because every executor unstashes the files the build validated.

### 4.11 No concurrent builds of the same job

Rule: add `disableConcurrentBuilds()` in `properties()`. A new build waits in the queue until the previous one ends. Without this, two builds could deploy the same hosts at the same time.

Test: `test_builds_of_one_job_do_not_run_concurrently` (`concurrentBuild` is false for both jobs).

### 4.12 The engine is loaded from the commit of the Jenkinsfile

Rule: the Jenkinsfile first checks out to get commit `C`, then loads the engine with `library(identifier: "deploy-engine@C", retriever: modernSCM(scm: [$class: 'GitSCMSource', remote: <same repository>], libraryPath: 'platform/jenkins/'))`; the engine's `Prepare` also checks out `C`.

Mechanism: the engine, the tasks and the inventory are in the same commit, so they change together and roll back together; no administrator has to configure a global library, and a library loaded dynamically from the repository runs in the sandbox. `modernSCM` resolves the version `C` to that commit; `legacySCM(scm)` ignores the version and always takes the latest commit of the branch.

What happens with each way of writing it: after checkout the Jenkinsfile runs `sleep 15`; meanwhile a new commit is pushed to the branch that changes the library resource file `marker.txt` from `A` to `B`; then the library is loaded and `libraryResource('marker.txt')` is read:

```
legacySCM(libraryPath: 'platform/jenkins/', scm: scm), identifier engine@bbc193a...:
jenkinsfile commit bbc193a0082cef7d960147752e7087a04ebbf487 marker A
Loading library engine@bbc193a0082cef7d960147752e7087a04ebbf487
library marker B

modernSCM(scm: [$class: 'GitSCMSource', remote: ...], libraryPath: 'platform/jenkins/'), identifier engine@ff4e2fc...:
jenkinsfile commit ff4e2fcf07328ad749fee955052d773387d2852b marker A
Loading library engine@ff4e2fcf07328ad749fee955052d773387d2852b
Attempting to resolve ff4e2fcf07328ad749fee955052d773387d2852b from remote references...
Could not find ff4e2fcf07328ad749fee955052d773387d2852b in remote references. Pulling heads to local for deep search...
library marker A
```

Test: `test_engine_comes_from_the_commit_the_jenkinsfile_came_from` checks the template itself the same way: during the wait a new commit that changes the text printed by `Plan.groovy` and the output of `task_b` lands on the branch, and the build still prints the original texts.

### 4.13 Shared library code in the sandbox

A library loaded dynamically from the repository runs in the Groovy sandbox, and the following ways of writing code are rejected or fail. Console messages are quoted as they are; the first row comes from a library with package name `org.example.deploy`, the others from this template's engine (package name `deploy`):

| Code | Result | How the engine writes it |
| --- | --- | --- |
| Enum constants with their own method bodies (`READ_ONLY { boolean allows(...) {...} }`) | `Scripts not permitted to use staticMethod org.example.deploy.AccessMode$1 $INIT java.lang.Object[]` | Constants carry only constructor arguments; methods use `switch (this)` |
| Enum without a constructor | `Scripts not permitted to use new java.util.LinkedHashMap` | Every enum has a constructor with `label` |
| Initial value of a static field refers to a static constant of another class | `Scripts not permitted to use staticMethod deploy.<class> get<field name>`, class initialization fails | Constants are `public static final` fields; related names use an enum (`EngineVariable`) |
| Calling a `private static` method of the same class from a closure, or calling a static method without the class name | `Scripts not permitted to use staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods invokeMethod java.lang.Object java.lang.String java.lang.Object` | Static methods are not `private`, and in closures are written as `ClassName.methodName(...)` |
| A `private` method and a `public` method with the same name | Compile error `Mixing private and public/protected methods of the same name causes multimethods to be disabled` | Rename |
| `new IllegalArgumentException(...)`, `new LinkedHashMap(map)` | `Scripts not permitted to use new ...` | Collect problems into a list, then call `error`; copy a Map with `[:] + map` |
| `list.sort(false) { ... }` (returns a new sorted list) | `Scripts not permitted to use staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods sort java.util.Collection boolean groovy.lang.Closure` | Make a copy, then use `sort { ... }` |
| `text.codePointCount(...)` | `Scripts not permitted to use method java.lang.String codePointCount int int` | Use `length()`: it counts UTF-16 code units, so the character limits in `Limits` are stricter for characters outside the BMP (such as emoji) |
| An enum value in a field of an engine object, or in a variable, parameter or `switch` of a method that is on the stack while an `input` waits (the review is the engine's only `input`) | After a Jenkins restart the build ends with `Scripts not permitted to use staticMethod deploy.TaskOutcome $INIT java.lang.Object[]` (Jenkins reads the build state back under the generic whitelist only: `RiverReader.SandboxedUnmarshaller` in `src/main/java/org/jenkinsci/plugins/workflow/support/pickles/serialization/RiverReader.java` of `jenkinsci/workflow-support-plugin@1015.v785e5a_b_b_8b_22` passes `Whitelist.all()` to `GroovySandbox.runInSandbox`, and loading a Groovy enum class calls its own `$INIT`) | Objects keep the label in a `String` field; the getter and setter of the same name convert, so callers still see the enum (`PlannedTask.phase`, `TaskResult.outcome`); `ParameterPage` converts in its private `mode()`; `Plan.tasks` is keyed by phase label; `runTasks` takes the task list, not the phase; `tests/test_engine_state.py` checks the fields of every class and the parameters, variables, loop and closure parameters and `switch` statements of every CPS method that waits for input or calls one, and `test_waiting_review_survives_a_jenkins_restart` checks the behavior |
| `collection.flatten()` | `Scripts not permitted to use staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods flatten java.util.Collection` | Nested `any` / `collectMany` |
| Spread operator on an array (`values()*.label`) | `No such field found: field ...[] label` | `values().collect { it.label }` |
| A `@NonCPS` method calls a method not marked `@NonCPS` | `expected to call deploy.Catalog.tasksFor but wound up catching deploy.AccessMode.allows` | Every method that only computes is marked `@NonCPS` |

Groovy's `switch` does not check that all constants of an enum are covered: the engine does `throw new IllegalStateException(...)` after every `switch`, so a missed constant fails at once at run time (it also fails if the sandbox rejects this constructor).

### 4.14 What the read-only job guarantees

Rule: with `AccessMode.READ_ONLY`, the parameter page lists only tasks with `access: read` and has no `SKIP_DEPLOY`; `PlanParser` rejects write tasks and the `skip_deploy` field; `Plan.deploy` is false, so there is no deploy stage.

Tests: `tests/test_read_only_job.py`: the parameter page has only read-only tasks; read-only tasks run normally and there is no deployment; write tasks in `DEPLOY_PLAN` (`pre[0]: task task_a writes, and this job is read-only`), `skip_deploy`, and write tasks ticked in the form all fail before any task runs.

Scope: this is a check by the engine. A task's `access` is its author's declaration; a role marked `read` can still write, and anyone who can change the repository can also change `mode` in the read-only Jenkinsfile. If you need a stronger guarantee, give the read-only job credentials with fewer rights in Jenkins (for example an SSH user without write access on the target hosts), and change only the credential ID in the Jenkinsfile.

### 4.15 Review before running: what runs is the file that was reviewed

Rule (`runSteps` in `TaskRunner`):

1. On one executor: `unstash`, run the task in `generate` mode; if the task wrote no review file, the build fails; compute `sha256`, read the full text, archive it, `stash` this one file; leave the executor.
2. Without an executor: `timeout(review.timeout_minutes) { input(message: 'Run <id> as reviewed below? (sha256 ...)\n\n<full text>', ok: 'Run', submitterParameter: 'reviewer') }`. The full text is shown with every control, format and line-separator character except line breaks and tabs as a `\uXXXX` escape (`TaskRunner.shown`, `Values.printable`), so the prompt reads as the file's bytes; a text longer than `Limits.MAX_REVIEW_CHARACTERS` is cut with a note pointing to the archived file. The sha256 is always of the file itself.
3. On another executor: `unstash` the source files and the review file, `verifySha256(file:, hash:)`, then run the task in `execute` mode.

The task's result is decided only by the run in step 3: the result of the generate run is cleared as soon as it passes; if the build is aborted before step 3 starts, the result is `aborted`; if a step between the two runs fails (`sha256`, `readFile`, `unstash`, `verifySha256`), it is `error`. If the generate run fails, it is handled by the task's failure policy and no confirmation is requested.

Who can confirm: when `input` has no `submitter`, anyone with Job/Build permission on the job can confirm, and anyone with Job/Build or Job/Cancel permission can abort (`canSettle` and `canCancel` in `src/main/java/org/jenkinsci/plugins/workflow/support/steps/input/InputStepExecution.java` of `jenkinsci/pipeline-input-step-plugin@560.v56198a_642157`). The Jenkins used for tests has security turned off, so anyone can confirm; this rule has no integration test.

Tests: `tests/test_review.py`: while waiting, the prompt starts with `Run task_d as reviewed below? (sha256 ` and ends with the full text of the review file, the archived review file is `change a\nchange b\n`, and no change is applied yet; after confirmation the console has `[Pipeline] verifySha256`, `applied change a`, `applied change b`, and `reviewer` in the report is the person who confirmed; after an abort the build is ABORTED and the task ran only once, in generate mode; when the build is aborted after confirmation but before execution, the task is `aborted` in the report; when generation fails, no confirmation is requested and the report shows `failed-stopped` (`failed-continued` and UNSTABLE under `warn`); a review file that is a directory makes the task `error`; a hidden character in the review file shows in the prompt as a `\uXXXX` escape (`test_review_prompt_shows_hidden_characters_as_escapes`); a commit pushed while the review waits does not change what runs; while waiting, the number of busy executors is 0. `tests/test_stage_view.py`: the Stage View popup shows the prompt and the review file line by line, with Run and Abort.

Scope: between the two runs the review file passes only through the Jenkins stash; `verifySha256` confirms this once more. The situations that would make it fail cannot be produced from outside, and no test covers them.

### 4.16 Clashes between parameter names and inventory variables are checked at run time

Rule: `Prepare` writes the parameter names of all tasks into `task_parameter_names` and runs `platform/ansible/check-variable-names.yml`: on localhost it collects the variable names of all hosts, intersects them with the parameter names, and fails if the result is not empty.

Mechanism: task parameters are passed as extra vars, which have the highest precedence, so a parameter with the same name as an inventory variable silently overrides that variable. The engine cannot read the decrypted inventory, so the check is left to Ansible; `test_option_names_cannot_override_inventory_reserved_or_engine_variables` in `tests/test_tasks.py` does the same check before commit.

Test: `test_parameter_name_used_by_the_inventory_stops_the_build_before_any_task_runs`: when `host_vars/web-01.yml` has `task_a_message`, the build is FAILURE, the console prints `task parameters also defined in the inventory: ['task_a_message']`, and no task runs.

## 5. Pitfalls

| What you do | What happens | Fix |
| --- | --- | --- |
| Read `params` after `properties()` to decide whether this is the first run | The first run fails with "no host group selected" | Copy `params` first, then call `properties()` (4.7) |
| Use `catchError(catchInterruptions: false)` to tell a timeout from a manual abort | After a manual abort the pipeline keeps running | `ansiblePlaybook` throws a plain `InterruptedException`; tell them apart by elapsed time (4.2) |
| Load the library from the same repository with `legacySCM(scm)` | The library comes from the latest commit of the branch, not the commit of the Jenkinsfile | Use `modernSCM` with the commit ID (4.12) |
| Write shared library code like ordinary Groovy | Sandbox rejections, compile errors or CPS mismatches | See the table in 4.13 |
| A task `description` or name, a host group name or a host name contains the word `selected` | In a build started through the API without `PRE_TASKS` or `TARGET_GROUPS`, that task or group is ticked anyway | `Catalog` refuses such a name or description in `Prepare` (`description-says-selected`, `task-name-says-selected`, `host-name-says-selected`). Reason: for a checkbox list, `getDefaultParameterValue` in `src/main/java/org/biouno/unochoice/ChoiceParameter.java` of `jenkinsci/active-choices-plugin@2.8.11` ticks every choice whose label contains `selected` (`SELECTED_OPTION`) |
| Active Choices checkbox text contains non-ASCII punctuation (such as full-width parentheses, colon or exclamation mark) or `@ = + "` | Entity text such as `web&#xff08;web-01&#xff09;` is shown next to the checkbox | Checkbox text is ASCII only, without `@ = + "`; the same goes for `description` in a task's `meta/task.yml`, which `Catalog` checks in `Prepare` (`description-not-ascii` in `test_problem_in_a_task_directory_or_the_inventory_stops_the_build_before_any_task_runs`, and `test_description_is_ascii_without_characters_the_active_choices_sanitizer_turns_into_entities`). Reason: every return value of a sandboxed script goes through the plugin's HTML filter (`eval` in `src/main/java/org/biouno/unochoice/model/GroovyScript.java` of `jenkinsci/active-choices-plugin@2.8.11`), which turns these characters into entities; the checkbox text is then escaped once more by the page template. Entities in the HTML form (`*_TASK_OPTIONS`) are decoded by the browser and are not affected |
| Use `fieldset` / `legend` / `label`, `title` or `aria-*` attributes in the dynamic HTML form | These tags and attributes disappear | The filter lets through only basic tags plus `input`, `select`, `option`, `textarea` and a few attributes (the `POLICY` constant in `src/main/java/org/biouno/unochoice/util/SafeHtmlExtendedMarkupFormatter.java` of `jenkinsci/active-choices-plugin@2.8.11`); group with `div` plus `style`, and mark required fields with the text "(required)" |
| Fill in the parameters of one task, then tick another task | All values already typed go back to the defaults | Changing the selection regenerates the whole form. Tick all tasks first, then fill in the parameters (`test_changing_the_selection_regenerates_the_form_and_drops_typed_values`) |
| Take an optional field with `x ?: default` | `timeout_minutes: 0`, `policy: ''`, `params: []` raise no error and silently fall back to the default | In Groovy `0`, `''`, `[]` are false; use `x == null ? default : x`, then check the type (4.5) |
| Write unquoted `2026-01-01` or `yes` for a string parameter in `DEPLOY_PLAN` | Without a check, Ansible receives `Thu Jan 01 00:00:00 UTC 2026` and `true` | YAML parsing has already turned them into a date and a boolean; the engine accepts only strings and integers and tells you to add quotes |
| A value in the form contains `,@@` | The value is cut, and the second half is taken as another field | Submitted values are split on `,@@`, which cannot be told apart; the engine reports an error when a field repeats (`test_invalid_form_values_fail_before_any_task_runs`) |
| Run the same task before and after the deployment and require ids to be globally unique | Plan validation reports a repeated id | An id only has to be unique within one phase: the stage name, parameter file name and report all include the phase (`test_skipped_deploy_still_runs_pre_and_post_tasks`) |
| Pass an integer with `extraVars` or `-e key=value` | A `>=` comparison in the task fails with `'>=' not supported between instances of '_AnsibleTaggedStr' and 'int'` | Use a JSON file, `-e @file` (4.3) |
| Write `default` only in argument_specs, without `defaults/main.yml` | `'xxx' is undefined` | Put run-time defaults in `defaults/main.yml`; the `default` in argument_specs is the same (4.6) |
| The `success_msg` of `assert` refers to a value that does not exist on failure, such as `stat.size` | When the assertion fails it reports `object of type 'dict' has no attribute 'size'`, hiding the real cause | In ansible-core 2.21 `success_msg` is rendered even when the assertion fails; add `default`, such as `task_b_file.stat.size \| default(0)` |
| Use the Jinja test `is between(1, 5)` | `No test named 'between'` | Write two comparisons: `x >= 1`, `x <= 5` |
| Refer to a role by path (`roles: [../../business/deploy]`) | Task titles show as `TASK [../../business/deploy : ...]` | Add `business` to `roles_path` and refer to `deploy` by name |
| ansible-lint checks `business/tasks/` | `schema[tasks]: ... is not of type 'array'`: every YAML under a directory named `tasks` is taken as a task list | Set the file kinds by path in `kinds` of `.ansible-lint` |
| ansible-lint checks `run-task.yml` | `syntax-check[specific]: ... 'task_role' is undefined` | Give example values with `extra_vars` in `.ansible-lint` (`Runner._get_ansible_syntax_check_matches` in `src/ansiblelint/runner.py` of `ansible/ansible-lint@26.10.1`) |
| The script of a reactive parameter (`reactiveChoice`) uses the referenced parameter directly, and the build is triggered through the API | Plan validation reports `unknown task '(task list failed)'` | When the API does not pass that parameter, Jenkins runs the script to get the default without binding the referenced parameter; the script fails and the fallback script's return value becomes the parameter value. Catch `MissingPropertyException` in the script and treat it as nothing selected (section 3.6 of [attachments/parameter-page.md](attachments/parameter-page.md)) |
| Use Jinja's `intersect` to get the intersection of two lists and put it in a message | With the same input, the order of names in the message differs each time | `intersect` does not guarantee order; follow it with `sort` (`check-variable-names.yml`) |
| The `copy` of a reviewed task writes the review file | `Destination directory .../.deploy-run/review does not exist` | The engine creates the directory (`TaskRunner` writes a placeholder file with `writeFile` before generation) |
| After a non-blocking task fails, you see `FATAL: command execution failed` and a Java stack trace in the console | You think the build failed | The `AbortException` the plugin throws when a playbook fails is a subclass of `IOException` (class declaration in `core/src/main/java/hudson/AbortException.java` of `jenkinsci/jenkins@2.580.1`), so the plugin's own `catch (IOException)` first prints it as FATAL with a stack trace and then rethrows it (`perform` in `AnsiblePlaybookBuilder`). Go by the build result; the real cause is in Ansible's `fatal: [host]: FAILED! => {... "msg": ...}` above it |

## 6. Checks

After a change, run (in `jenkins-ansible-deploy/`):

```bash
python3 -m pytest tests -m 'not jenkins'      # inventory, playbooks, task directory rules and cases, ansible-lint
python3 -m pytest tests                        # plus Jenkins and browser integration tests
```

Both commands must end with exit code 0.

To check only the inventory (in `examples/platform/ansible`):

```bash
ansible-inventory --graph deploy_targets
ansible-playbook check-inventory.yml --vault-password-file ../../../tests/example-vault-password.txt
```

`check-inventory.yml` does not connect to hosts; it validates each release target against the argument_specs of role `deploy` and reports the problems of all hosts in one run. For example, after changing `app_port` of `web` to `eighty` and deleting `app_version` of `api`:

```
argument 'app_port' is of type str and we were unable to convert to int
missing required arguments: app_version
```

`web-01`, `web-02`, `api-01` fail, `worker-01`, `worker-02` pass (`test_check_inventory_reports_every_broken_host_in_one_run`).

## 7. Attachments

- [attachments/inventory-practices.md](attachments/inventory-practices.md): managing the inventory - directory layout and variable layering, variable precedence and naming, ansible-vault and Jenkins credentials, validation with `ansible-inventory` / argument_specs / ansible-lint and how to use them in CI. Read it before designing or changing the inventory.
- [attachments/parameter-page.md](attachments/parameter-page.md): UI and implementation details of the parameter page - definitions of the Active Choices parameters, the form HTML, the format and parsing of submitted values. Read it when changing the parameter page or tracking down problems with passing parameters.
- [attachments/review-before-run.md](attachments/review-before-run.md): the UI flow of review before running, with a Jenkins screenshot for each step. Read it the first time you use review, or when explaining to a reviewer what to do.

## 8. Sources

In this document, `<path>` of `<owner>/<name>@<version>` means the same path in the upstream repository in the table below, at that tag (or commit):

| Source | Upstream repository | Tag, or branch and commit |
| --- | --- | --- |
| `ansible/ansible@2.21.5` | https://github.com/ansible/ansible | tag `v2.21.5` |
| `ansible/ansible-documentation@b16dcb8` | https://github.com/ansible/ansible-documentation | commit `b16dcb8e8b5b5658ae9691b8632b7046e879606b` on branch `stable-2.21` |
| `ansible/ansible-lint@26.10.1` | https://github.com/ansible/ansible-lint | tag `v26.10.1` |
| `jenkinsci/jenkins@2.580.1` | https://github.com/jenkinsci/jenkins | tag `jenkins-2.580.1` |
| `jenkinsci/ansible-plugin@635.v34b_48e979c86` | https://github.com/jenkinsci/ansible-plugin | tag `635.v34b_48e979c86` |
| `jenkinsci/active-choices-plugin@2.8.11` | https://github.com/jenkinsci/active-choices-plugin | tag `uno-choice-2.8.11` |
| `jenkinsci/workflow-cps-plugin@4395.v738456a_5da_6b_` | https://github.com/jenkinsci/workflow-cps-plugin | tag `4395.v738456a_5da_6b_` |
| `jenkinsci/workflow-basic-steps-plugin@1098.v808b_fd7f8cf4` | https://github.com/jenkinsci/workflow-basic-steps-plugin | tag `1098.v808b_fd7f8cf4` |
| `jenkinsci/pipeline-input-step-plugin@560.v56198a_642157` | https://github.com/jenkinsci/pipeline-input-step-plugin | tag `560.v56198a_642157` |
| `jenkinsci/workflow-multibranch-plugin@842.v3a_b_59b_57b_e6e` | https://github.com/jenkinsci/workflow-multibranch-plugin | tag `842.v3a_b_59b_57b_e6e` |
| `jenkinsci/workflow-support-plugin@1015.v785e5a_b_b_8b_22` | https://github.com/jenkinsci/workflow-support-plugin | tag `1015.v785e5a_b_b_8b_22` |
