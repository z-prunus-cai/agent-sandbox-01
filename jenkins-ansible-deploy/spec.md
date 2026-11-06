# Spec: Jenkins + Ansible deployment pipeline template

## 1. Goals

A template of Jenkins pipeline and Ansible code kept in one git repository, that meets these requirements:

1. Deployments target **host groups** of the Ansible inventory. On the build parameter page, the targets of this run are picked from the list of host groups with **multiple selection**.
2. The deploy stage can be **skipped with a checkbox** on the build parameter page.
3. Before and after the deployment, **0 or more extra tasks** can be selected for each. The parameter page shows input boxes only for the parameters of the selected tasks, and they can be filled in one by one; different tasks have parameters of different types.
4. Host and host group settings (ports, paths, version numbers, etc.) are **managed only in the inventory**. The parameter page cannot override them.
5. **Business and infrastructure are separate**: adding a business task only adds one task directory. It does not change the engine, the pipeline skeleton, the shared playbooks or the test framework.
6. **Two jobs on the same Jenkins share one engine**: the read-only job can run only read-only tasks and has no deploy stage; the read-write job can run every task and the deployment.
7. **Review before run**: a task can declare "needs review before it runs". The engine first has the task generate what it will do and shows it, and runs it only after someone confirms. What runs must be exactly what was shown at review time.

## 2. Platform and versions

| Component | Version |
| --- | --- |
| Jenkins | 2.580.1 |
| Jenkins Ansible plugin (`ansible`) | 635.v34b_48e979c86 |
| Active Choices plugin (`uno-choice`) | 2.8.11 |
| Pipeline: Groovy Libraries (`pipeline-groovy-lib`, provides the `library` step) | Latest version compatible with Jenkins 2.580.1 |
| Pipeline: Input Step (`pipeline-input-step`) | Latest version compatible with Jenkins 2.580.1 |
| ansible-core | 2.21.5 |
| Other Jenkins plugins (Pipeline, `pipeline-utility-steps`, etc.) | Latest versions compatible with Jenkins 2.580.1; record the exact versions during experiments |
| Python | 3.13 (runs ansible-core and the tests) |

The Jenkinsfile is loaded from SCM and runs in the Groovy sandbox. It **needs no Jenkins administrator approval of any script and no administrator-configured global library**.

## 3. Deliverables

Directory `jenkins-ansible-deploy/`. Every file of the deliverable (documents, code, comments, task metadata, tests) and the build log are in English and ASCII only.

`examples/` is a template that can be copied as a whole to the root of a repository. Paths in the table are relative to `examples/`, except the last two rows, which are relative to `jenkins-ansible-deploy/`:

| Path | Content | Owner |
| --- | --- | --- |
| `pipelines/readonly/Jenkinsfile` | Read-only job: declares the mode, the parameter page form and the credential IDs; everything else is left to the engine | Infrastructure |
| `pipelines/readwrite/Jenkinsfile` | Read-write job: same as above | Infrastructure |
| `platform/jenkins/` | Engine: a shared library (`vars/` defines the whole pipeline, `src/` holds classes and enums), loaded with the `library` step from the same repository and the same commit | Infrastructure |
| `platform/ansible/` | `ansible.cfg`, `run-task.yml` that runs any task, the deploy entry point `deploy.yml`, the inventory check `check-inventory.yml` | Infrastructure |
| `business/inventory/` | Host groups, `group_vars`, `host_vars`, vault files | Business |
| `business/deploy/` | The deployment itself (one role) | Business |
| `business/tasks/<task name>/` | One extra task (one role, with Jenkins metadata and its own tests) | Business |
| `BRIEF.md`, `attachments/` | Documentation | - |
| `tests/` | Test framework and shared tests | Infrastructure |

## 4. Behavior

### 4.1 Two jobs

| | Read-only job | Read-write job |
| --- | --- | --- |
| Selectable tasks | Only tasks with `access: read` | All tasks |
| Deploy stage | None; the parameter page has no "skip deployment" | Yes |
| A write task or the deployment appears in `DEPLOY_PLAN` | Plan validation fails, no playbook runs | Allowed |
| Credentials | From Jenkins credentials, referenced by ID in the Jenkinsfile | Same as the read-only job |

Both jobs get credentials the same way: from Jenkins credentials referenced by ID. The read-only guarantee of the read-only job comes from the engine's plan validation. To keep someone from changing the mode in the read-only Jenkinsfile, configure in Jenkins a credential ID with fewer permissions for the read-only job; no code change is needed.

Both parameter page forms (the Active Choices form and the plugin-free form) are provided by the engine, and the Jenkinsfile picks one of them. Both example Jenkinsfiles use the Active Choices form.

### 4.2 Inputs (build parameters)

| Parameter | Active Choices form | Plugin-free form |
| --- | --- | --- |
| Targets | Multiple-selection list of host groups, options from the inventory | One checkbox per host group, from the inventory |
| Skip deployment (read-write job only) | `SKIP_DEPLOY` checkbox | Same as the Active Choices form |
| Pre-deploy tasks | The checkbox list of main tasks is always shown; the list of less-used tasks appears only after "Show more" is checked; both lists have a search box; a parameter form that changes live with the selection, where each selected task can be given a run order | One checkbox per task; after the build starts, an `input` step shows the form for the selected tasks |
| Post-deploy tasks | Same as above | Same as above |
| Full plan | `DEPLOY_PLAN` (YAML text); when not empty, all the parameters above are ignored; for triggering from the API or an upstream job | Same as the Active Choices form |

Parameter names are an external contract. They are hard-coded and not derived from other names.

`DEPLOY_PLAN` format (read-write job; the read-only job does not accept `skip_deploy`):

```yaml
targets: [group_1, group_2]
skip_deploy: false
pre:
  - task_a                                    # all defaults
  - {name: task_b, id: b_1, policy: warn, params: {key: value}}
post:
  - {name: check_a, timeout_minutes: 5}
```

### 4.3 Tasks

- Tasks are **abstract placeholder tasks** (such as `task_a`, `check_a`) and call no external tool. They only show how parameters are received and validated, and how success, failure and review work.
- Each task is a directory under `business/tasks/`, that is, one Ansible role:
  - The parameters (name, type, required, description) are declared in `meta/argument_specs.yml`, the defaults in `defaults/main.yml`;
  - The Jenkins metadata is in `meta/task.yml`: description, allowed phases (pre / post), access (`read` / `write`), main or less-used on the parameter page (`main` / `more`), default failure policy, timeout, where it runs (`targets`: the selected host groups; `controller`: the Jenkins node itself), order number within a phase, and whether it needs review before it runs.
- The engine finds tasks by scanning `business/tasks/*/meta/task.yml`. There is no central list of tasks.
- Every metadata field has a limited set of values. Unknown values are reported in the prepare stage. Order numbers must not repeat between tasks.
- Parameter types cover at least: string, integer, boolean, enum (choices).
- Run order within a phase: in the Active Choices form, by the run order filled in for each task on the parameter form (the default is the task's order number; tasks given equal numbers keep the order of their order numbers); dependencies between tasks are not checked, and the run plan is printed before anything runs. In the plugin-free form, by order number. In `DEPLOY_PLAN`, by list order. The same task can run several times in one phase with different `id`s (`DEPLOY_PLAN` only).
- Two failure policies: `abort` (stop the pipeline, FAILURE) and `warn` (mark UNSTABLE, continue). They can be changed per task for each build.
- Each task has a timeout. A timeout counts as a task failure and is then handled by the policy. A manual abort of the build must not be treated as a task failure and continued.

### 4.4 Review before run

- A task that declares review runs in two steps:
  1. **Generate**: the engine runs the task in generate mode. The task writes what it will do to the review file named by the engine, and changes nothing;
  2. **Execute**: after confirmation, the engine runs the task in execute mode, and the task acts on the review file.
- After generation, the engine computes the sha256 of the review file and shows the full text and the sha256 in the confirmation prompt. Before execution it computes it again and refuses to execute if it differs. The review file is archived as a build artifact.
- Anyone who can run the job can confirm or abort. The person who confirmed is recorded in the report.
- Waiting for confirmation holds no executor. The wait has a timeout. On timeout or abort the task does not execute and the build result is ABORTED.
- A confirmation that is waiting stays valid after Jenkins restarts.

### 4.5 Deployment

- The deploy playbook acts only on the selected host groups. All host settings come from the inventory (`group_vars` / `host_vars`).
- The deployment itself is also a placeholder: it checks that the host settings are complete and have the right types, and prints the actions it would take. It does not change the target hosts.
- When the deployment is skipped, pre-deploy and post-deploy tasks still run as selected.

### 4.6 Validation (before any playbook runs; lists all problems at once, then fails the build)

- A host group is not in the inventory, or no host group is selected when target hosts are needed;
- Unknown task, task not allowed in that phase, write task or deployment in the read-only job, unknown policy, invalid or repeated `id`;
- Undeclared parameter name, empty required parameter, integer that is not an integer, enum value not in choices;
- Parameter value contains Jinja template syntax (`{{`, `{%`, `{#`);
- `DEPLOY_PLAN` has a wrong structure (not a mapping, unknown field, wrong type);
- Input over a limit: the length of `DEPLOY_PLAN`, the number of task entries in the plan, the length of string parameters, the length of `id`. All limits are defined in one place;
- A task parameter name is the same as an inventory variable, an Ansible reserved variable or an engine variable (checked at run time by reading the inventory with the vault password).

### 4.7 Output

- Each task shows as its own stage in Jenkins, named with the phase and the `id`;
- A summary of task results is printed at the end of the console. A JSON report is archived (task id, phase, policy, parameters, duration, result, and for reviewed tasks the person who confirmed);
- The task result is one of a closed set of states: success, failed and stopped, failed and continued, timed out and stopped, timed out and continued, aborted, internal error.

### 4.8 Inventory management (conclusions + examples)

Covers three topics. Each conclusion needs a source in the Ansible documentation or source code, or an experiment result:

1. Directory and variable layers: inventory structure, `group_vars` / `host_vars`, variable precedence, naming;
2. Secrets: ansible-vault, and how Jenkins credentials pass the vault password to Ansible;
3. Validation and review: `ansible-inventory --graph` / `--list` / `--host`, variable validation, checking the inventory in CI.

There is only one example inventory (no separate environments). It contains several host groups. Host addresses use documentation addresses (RFC 5737).

## 5. Constraints

- Only registered task names and parameter names are accepted. Parameters are passed to Ansible in a JSON file with `-e @file`, so their types are kept;
- Task parameter names must not collide with inventory variable names, Ansible reserved words or engine variables;
- No real credentials appear in the repository. Vault examples use clearly marked example passwords;
- No Jenkins administrator approval of any script and no administrator-configured global library;
- Values from a limited set (phase, access, failure policy, where a task runs, parameter type, parameter page form, job mode, task result) are enums in the engine, not string comparisons;
- Tests run with one command: the tests start a local Jenkins themselves and create a git repository with `examples/` as its root, and do not depend on environment variables set beforehand.

## 6. Non-goals

- Inventories for several environments (dev / stg / prod);
- Dynamic inventory (automatic discovery of cloud hosts);
- Rollback, running tasks in parallel;
- Overriding inventory host settings on the parameter page;
- Real implementations of the extra tasks (database changes, calls to external systems, etc.);
- Accessibility support of the parameter page.

## 7. Unspecified items and assumptions

- Unspecified: the names and number of host groups. The example assumes 3 host groups with 1-2 hosts each.
- Unspecified: where the version number of the deployment comes from. The example assumes the version is kept as a host group variable in the inventory and changes with git commits.
- Unspecified: how the Jenkins node connects to the target hosts. The example assumes an SSH private key kept in Jenkins credentials and passed in through the `credentialsId` of `ansiblePlaybook`; experiments use the `local` connection instead.
- Unspecified: whether tasks still run when the deployment is skipped. The example assumes they still run (see 4.5).
- Unspecified: whether the read-only job uses different credentials from the read-write job. The example assumes both jobs reference the same credential ID.
- Unspecified: the timeout of the review wait. The example assumes 30 minutes, changeable in the task metadata, with the same upper limit as the task timeout.
