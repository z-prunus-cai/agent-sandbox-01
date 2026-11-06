# Managing the Ansible inventory

Versions covered: ansible-core 2.21.5, ansible-lint 26.10.1, Jenkins Ansible plugin 635.v34b_48e979c86.

Sources are cited in two ways. Upstream source code and documentation are written as `<path>` of `<owner>/<name>@<version>`, that is, the path in the upstream repository at that version. For code, the class and method (or function) are named as well; for documentation, the section title is named as well. The upstream repositories and versions are listed in [BRIEF.md section 8](../BRIEF.md#8-sources). Example code is cited by file and by class and method (or function) name. All commands in this document apply to the example directory `examples/`. Unless stated otherwise, run them in `examples/platform/ansible/`. When a behavior can only be seen after changing a file, make the change in a copy of the example and run it there; do not change the example itself. The vault password file is `tests/example-vault-password.txt`; it contains the example password `example-only-password`. From `examples/platform/ansible/` it is written as `../../../tests/example-vault-password.txt`.

Terms: **inventory** is the data source that describes the managed hosts and how they are grouped. A **host group** (group) is a set of hosts; a group can contain child groups, and every group is a descendant of `all`. A **variable layer** is one of the places that give a host a value, for example `group_vars`, `host_vars`, or the command line. A **role** is a directory that holds reusable tasks, default variables and metadata.

## Conclusions

1. Use a directory inventory: set `inventory = ../../business/inventory` in `ansible.cfg`. `hosts.yml` only describes hosts and how they map to groups. All variables go in `business/inventory/group_vars/` and `business/inventory/host_vars/`. Do not create `group_vars/` or `host_vars/` in `examples/platform/ansible/`, where the playbooks are.
2. When Ansible does not run in `examples/platform/ansible` (which is the case in Jenkins), point the environment variable `ANSIBLE_CONFIG` at `examples/platform/ansible/ansible.cfg`. It takes precedence over an `ansible.cfg` in the current directory. Relative paths in the configuration are resolved against the directory of the configuration file, so the result is the same whatever directory you run from. When running in `examples/platform/ansible` you can leave it out.
3. Variable precedence from low to high: role defaults -> `group_vars/all` -> child groups (`web` etc.) -> `host_vars` -> extra vars. The `default` in `meta/argument_specs.yml` is documentation only and has no effect at run time. The example still writes it and keeps it equal to `defaults/main.yml` (checked by `test_documented_defaults_equal_role_defaults_and_cover_every_optional_option` in `test_tasks.py`).
4. A host belongs to only one of the groups `web`, `api` and `worker`. Variables with the same name in sibling groups are merged in alphabetical order of the group name, and the result is not obvious.
5. Naming: variable names use only lowercase letters, digits and underscores. Variables a role defines itself get the prefix `<role name>_`. Application parameters in the inventory start with `app_`. Encrypted values start with `vault_`. The first rule is checked by the ansible-lint rule `var-naming`. The tasks under `business/tasks/` are not in a `roles/` directory, so ansible-lint does not check their prefixes; the prefixes of task parameters and of `defaults/main.yml` are checked by the engine and by `test_tasks.py` (see 1.5).
6. Sensitive values go in `group_vars/all/vault.yml`, which is encrypted as a whole file. `main.yml` refers to them with `app_api_token: "{{ vault_app_api_token }}"`.
7. On the Jenkins side, store the vault password as a "Secret text" credential and give it to the plugin through `vaultCredentialsId`. (The plugin also accepts "Secret file", but the engine's credential check `TaskRunner.checkCredentials` binds it as Secret text; to use Secret file, change the `string(...)` in it to `file(...)`, see BRIEF section 4.9.) The plugin writes the password to a temporary file with mode 0400 and passes it to ansible-playbook with `--vault-password-file`. After the process ends, it deletes the file in a `finally` block.
8. `ansible-inventory --graph` does not read variables and does not need the vault password. `--list` and `--host` need the password and print the decrypted plain text, so they are not run in CI.
9. `check-inventory.yml` uses `ansible.builtin.validate_argument_spec` to validate every host against the parameter declaration of `deploy`. It does not connect to the hosts and reports all problems in one run.
10. ansible-lint runs in `examples/` as `ansible-lint --offline --profile production`, and it must be given the vault password; otherwise `vault.yml` is not checked.
11. When validation fails, the error message prints the value of the variable, and `no_log` cannot hide that line. So secret variables are declared in the spec only with `type: str` and `required: true`.

## 1. Directory layout and variable layers

### 1.1 Directory inventory; the hosts file holds only hosts and groups

Rule: use the whole `business/inventory/` directory as one inventory source, and do not write `vars:` in `hosts.yml`. The contents of the example `hosts.yml` and of the variable files are in [BRIEF.md section 3.2](../BRIEF.md#32-inventory).

Mechanism: a directory can be a single inventory source. Ansible combines the sources in the directory and reads them from the top level down in alphabetical order. When inventory sources are loaded, directories named `group_vars` and `host_vars` are always skipped. These two directories are read by the default vars plugin `host_group_vars`; a vars plugin is a plugin that supplies variables for hosts and groups outside the inventory. These variable files can use structures such as lists and dictionaries. All files in a group directory (such as `group_vars/all/`) are read in lexical order. `ansible_host` is the actual address used to connect, which is not the same thing as the host alias.

Sources: `docs/docsite/rst/inventory_guide/intro_inventory.rst` of `ansible/ansible-documentation@b16dcb8`, section "Organizing inventory in a directory"; `lib/ansible/inventory/manager.py` of `ansible/ansible@2.21.5`, module-level constant `IGNORED_ALWAYS`; `docs/docsite/rst/inventory_guide/intro_inventory.rst` of `ansible/ansible-documentation@b16dcb8`, sections "Adding variables to inventory", "Organizing host and group variables" and "Connecting to hosts: behavioral inventory parameters".

What happens otherwise: if variables are written under `vars:` in `hosts.yml`, they and variables of the same name in `group_vars` sit in two different precedence layers ("Inventory file or script group vars" is lower than "Inventory group_vars/*": `docs/docsite/rst/playbook_guide/playbooks_variables.rst` of `ansible/ansible-documentation@b16dcb8`, section "Understanding variable precedence"), and looking up one value means checking two places.

### 1.2 Set the configuration with ANSIBLE_CONFIG; relative paths are resolved against the configuration file

Rule: `examples/platform/ansible/ansible.cfg` contains only two relative paths: `inventory = ../../business/inventory` and `roles_path = ../../business/tasks:../../business`. Jenkins and the tests both select this file through `ANSIBLE_CONFIG`. When you run by hand in `examples/platform/ansible`, Ansible finds it in the current directory, so you need not set it. On the Jenkins side, `TaskRunner.playbook` wraps every `ansiblePlaybook` call in `withEnv(["ANSIBLE_CONFIG=${script.env.WORKSPACE}/platform/ansible/ansible.cfg"])` (method `playbook` in `examples/platform/jenkins/src/deploy/TaskRunner.groovy`). The playbook is written as a path relative to the workspace (`platform/ansible/deploy.yml` in method `deploy` of the same file). The tests run Ansible the same way (`run_ansible` in `tests/conftest.py`).

Mechanism: **Search order**: ansible-core looks for `ANSIBLE_CONFIG`, then `ansible.cfg` in the current directory, then `~/.ansible.cfg`, then `/etc/ansible/ansible.cfg`, and uses only the first one it finds. If the current directory is world-writable, that directory is skipped (`docs/docsite/rst/reference_appendices/general_precedence.rst` of `ansible/ansible-documentation@b16dcb8`, section "Configuration settings"; `lib/ansible/config/manager.py` of `ansible/ansible@2.21.5`, function `find_ini_config_file`). **Relative paths**: a value read from a configuration file has that file as its origin. `inventory` has type `pathlist` and `roles_path` has type `pathspec`; relative paths in values of these two types are expanded against the directory of the origin file (`lib/ansible/config/manager.py` of `ansible/ansible@2.21.5`, method `ConfigManager.get_config_value_and_origin`; `lib/ansible/config/base.yml` of `ansible/ansible@2.21.5`, keys `DEFAULT_HOST_LIST.type` and `DEFAULT_ROLES_PATH.type`; `lib/ansible/config/manager.py` of `ansible/ansible@2.21.5`, function `ensure_type` (docstring) and function `_ensure_type`; `lib/ansible/utils/path.py` of `ansible/ansible@2.21.5`, function `unfrackpath`). **Plugin side**: the plugin copies the step's environment variables (including values set by `withEnv`) to the child process and starts ansible-playbook with the step's working directory as cwd. `TaskRunner.playbook` does not set `inventory`, so the plugin does not add `-i` (field `envVars` of class `AnsiblePlaybookExecution` in `src/main/java/org/jenkinsci/plugins/ansible/workflow/AnsiblePlaybookStep.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`; constructor `AbstractAnsibleInvocation` in `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`; method `execute` in `src/main/java/org/jenkinsci/plugins/ansible/CLIRunner.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`; method `appendInventory` in `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`).

What you see when running from another directory: that directory has its own `ansible.cfg` (`inventory = other-inventory`). With `ANSIBLE_CONFIG=<repo>/platform/ansible/ansible.cfg` set, the output of `ansible-config dump --only-changed` (excerpt):

```text
CONFIG_FILE() = <repo>/platform/ansible/ansible.cfg
DEFAULT_HOST_LIST(<repo>/platform/ansible/ansible.cfg) = ['<repo>/business/inventory']
DEFAULT_ROLES_PATH(<repo>/platform/ansible/ansible.cfg) = ['<repo>/business/tasks', '<repo>/business']
```

In this case `ansible-inventory --graph deploy_targets` lists all 5 hosts. Without `ANSIBLE_CONFIG`, `CONFIG_FILE` becomes that directory's own `ansible.cfg`, and the output of `ansible-inventory --graph deploy_targets` is:

```text
[WARNING]: No inventory was parsed, only implicit localhost is available
[ERROR]: Pattern must be valid group name when using --graph
```

Output of a deployment run the Jenkins way, from the workspace root (the `examples/` level) (excerpt, exit code 0):

```text
$ ANSIBLE_CONFIG=$PWD/platform/ansible/ansible.cfg ansible-playbook --vault-password-file ../tests/example-vault-password.txt platform/ansible/deploy.yml --limit web
    "msg": "deploy web 2.3.1 to web-01 (192.0.2.11) dir=/opt/apps/web port=8080 max_connections=100 user=app"
    "msg": "deploy web 2.3.1 to web-02 (192.0.2.12) dir=/opt/apps/web port=8080 max_connections=200 user=app"
```

What happens otherwise: if you rely on `ansible.cfg` in the current directory, a run from the workspace root reads some other configuration, or falls back to the default `/etc/ansible/hosts`. The inventory parses as empty, and only the implicit localhost is left.

### 1.3 group_vars / host_vars only in the inventory directory

Rule: use only `business/inventory/group_vars/` and `business/inventory/host_vars/`. Do not create `examples/platform/ansible/group_vars/`.

Mechanism: `host_group_vars` looks for these two directories next to the inventory source; when ansible-playbook runs, it also looks next to the playbook directory. When both places have a variable with the same name, the one next to the playbook wins (`docs/docsite/rst/inventory_guide/intro_inventory.rst` of `ansible/ansible-documentation@b16dcb8`, section "Organizing host and group variables"; `docs/docsite/rst/playbook_guide/playbooks_variables.rst` of `ansible/ansible-documentation@b16dcb8`, section "Understanding variable precedence"). The inventory source of the example is the directory `examples/business/inventory`; next to it is `business/inventory/group_vars/`. All playbooks are directly in `examples/platform/ansible/`, so the one next to the playbooks is `examples/platform/ansible/group_vars/`. ansible-playbook sets the playbook's directory as the base directory. Commands without a playbook (`ansible`, `ansible-inventory`) use the current directory as the base directory by default; it can also be set with `--playbook-dir` (`lib/ansible/playbook/__init__.py` of `ansible/ansible@2.21.5`, method `Playbook._load_playbook_data`; `lib/ansible/parsing/dataloader.py` of `ansible/ansible@2.21.5`, method `DataLoader.__init__`; `lib/ansible/vars/manager.py` of `ansible/ansible@2.21.5`, method `VariableManager.get_vars`; `docs/docsite/rst/inventory_guide/intro_inventory.rst` of `ansible/ansible-documentation@b16dcb8`, section "Organizing host and group variables").

What happens otherwise: if `app_max_connections: 999` is written in `platform/ansible/group_vars/web.yml` (at the same level as the playbooks), all three commands read 999 when run in `platform/ansible/` (output excerpt):

```text
== ansible-playbook deploy.yml ==
    "msg": "deploy web 2.3.1 to web-01 (192.0.2.11) dir=/opt/apps/web port=8080 max_connections=999 user=app"
== ansible ad hoc ==
    "app_max_connections": 999
== ansible-inventory --host ==
    "app_max_connections": 999,
```

When run from the `examples/` level (the Jenkins workspace root) with `ANSIBLE_CONFIG` pointing at `platform/ansible/ansible.cfg` (as in Jenkins):

```text
== ansible-playbook platform/ansible/deploy.yml ==
    "msg": "deploy web 2.3.1 to web-01 (192.0.2.11) dir=/opt/apps/web port=8080 max_connections=999 user=app"
== ansible ad hoc ==
    "app_max_connections": 100
== ansible-inventory --host ==
    "app_max_connections": 100,
```

In other words: such a directory is read by every playbook in `platform/ansible/` and overrides the values in `business/inventory/group_vars/`, but whether the review commands see it depends on the current directory. The result is that the deployment actually uses 999 while a review from the workspace root sees 100, and the inventory is no longer the single source of host parameters.

### 1.4 Variable precedence

Rule: define each variable in one layer only. Values shared by all hosts go in `group_vars/all/main.yml`; values for one group go in `group_vars/<group>.yml`; exceptions for individual hosts go in `host_vars/<host>.yml`. Do not put host parameters in a role's `vars/`, and do not override host parameters on the command line.

Mechanism: the official list is ordered from low to high. The table lists only the layers that matter for the example:

| Layer (low -> high) | Source |
| --- | --- |
| role defaults (the role's `defaults/main.yml`) | `docs/docsite/rst/playbook_guide/playbooks_variables.rst` of `ansible/ansible-documentation@b16dcb8`, section "Understanding variable precedence" |
| inventory `group_vars/all` | Same as above |
| inventory `group_vars/*` | Same as above |
| inventory `host_vars/*` | Same as above |
| role vars (the role's `vars/main.yml`) | Same as above |
| extra vars (variables passed with `-e` on the command line) | Same as above |

Additional rules: inside the inventory, from low to high, the order is the `all` group -> parent group -> child group -> host. Groups at the same level are merged in alphabetical order of the group name, and a group merged later overrides one merged earlier (`docs/docsite/rst/inventory_guide/intro_inventory.rst` of `ansible/ansible-documentation@b16dcb8`, section "How variables are merged"). Role defaults can be overridden by any other source (`docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst` of `ansible/ansible-documentation@b16dcb8`, section "Role directory structure"). argument_specs is the parameter specification a role declares in `meta/argument_specs.yml`. The `default` in it is for documentation only; the actual default value always comes from the role defaults (`docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst` of `ansible/ansible-documentation@b16dcb8`, section "Specification format").

host_vars is higher than group_vars:

```text
$ ansible-inventory --vault-password-file ../../../tests/example-vault-password.txt --host web-02
{
    "ansible_host": "192.0.2.12",
    "app_api_token": "{{ vault_app_api_token }}",
    "app_base_dir": "/opt/apps",
    "app_max_connections": 200,
    "app_name": "web",
    "app_port": 8080,
    "app_run_user": "app",
    "app_version": "2.3.1",
    "vault_app_api_token": "example-only-token"
}
```

`app_max_connections` of `web-02` comes from `host_vars/web-02.yml` and is 200; for `web-01` it comes from `group_vars/web.yml` and is 100.

A child group is higher than `all`: the example has no variable with the same name in more than one layer. If you add the line `app_run_user: web` at the end of `business/inventory/group_vars/web.yml` (the value in `all` is `app`), the output is:

```text
$ ansible web-01,api-01 --vault-password-file ../../../tests/example-vault-password.txt -m ansible.builtin.debug -a var=app_run_user
web-01 | SUCCESS => {
    "app_run_user": "web"
}
api-01 | SUCCESS => {
    "app_run_user": "app"
}
```

Extra vars are higher than host_vars. When passed in `key=value` form, the value is a string:

```text
$ ansible web-02 --vault-password-file ../../../tests/example-vault-password.txt -m ansible.builtin.debug -a var=app_max_connections -e app_max_connections=500
web-02 | SUCCESS => {
    "app_max_connections": "500"
}
```

The documentation states that values in `key=value` form are treated as strings; to keep the type, pass a JSON or YAML file (`-e @file`). This is how the example passes task parameters (`docs/docsite/rst/playbook_guide/playbooks_variables.rst` of `ansible/ansible-documentation@b16dcb8`, sections "Key-value format" and "Vars from a JSON or YAML file"; the call that runs `run-task.yml` in method `attempt` of `examples/platform/jenkins/src/deploy/TaskRunner.groovy`).

Role defaults have the lowest precedence: run `task_c` with `run-task.yml`, passing the parameter file with `-e @task-c-localhost.json`, whose content is `{"task_role": "task_c", "task_hosts": "localhost"}`. The example prints `mode=plan`; this value comes from `business/tasks/task_c/defaults/main.yml`. After adding `task_c_mode: apply` to `business/inventory/group_vars/all/main.yml`, the output becomes `mode=apply`:

```text
== original example ==
    "msg": "localhost: task_c mode=plan label="
== copy with task_c_mode in group_vars/all ==
    "msg": "localhost: task_c mode=apply label="
```

The `default` in argument_specs has no effect: the argument_specs of `task_c` give every parameter a `default` (for example `default: plan`), but without `business/tasks/task_c/defaults/main.yml` the same run exits with code 2 (output excerpt):

```text
TASK [task_c : Validating arguments against arg spec 'main' - Example task C - has a choice and a boolean parameter] ***
ok: [localhost]
TASK [task_c : Run in the selected mode] ***************************************
[ERROR]: Task failed: Finalization of task args for 'ansible.builtin.debug' failed: Error while resolving value for 'msg': 'task_c_mode' is undefined
```

The implicit localhost also gets `group_vars/all`. When the inventory does not define localhost, Ansible creates an "implicit localhost" automatically. It belongs to no group, but it reads `host_vars` and the variables of the `all` group (`docs/docsite/rst/inventory_guide/implicit_localhost.rst` of `ansible/ansible-documentation@b16dcb8`, section "Implicit 'localhost'"):

```text
$ ansible localhost --vault-password-file ../../../tests/example-vault-password.txt -m ansible.builtin.debug -a var=app_base_dir
localhost | SUCCESS => {
    "app_base_dir": "/opt/apps"
}
```

`ansible all --list-hosts` lists only the 5 hosts, and localhost is not among them. It follows that tasks that run on the Jenkins node itself (`run_on: controller` in `meta/task.yml`; the engine passes `task_hosts: localhost`) also load `group_vars/all/vault.yml`, and so also need the vault password.

What happens otherwise: when the same variable is defined in several layers, a reviewer can hardly tell which value is finally used. If a host belongs to both `web` and `api`, conflicting variables of the two groups are merged in alphabetical order, so `web` overrides `api`. If a parameter has a `default` only in argument_specs, the run fails with an undefined-variable error (see the output in the paragraph on the argument_specs `default` above).

### 1.5 Variable naming

Rule: variable names use only lowercase letters, digits and underscores, and start with a letter. Variables a role defines itself (`defaults/`, `vars/`, `register`, `set_fact`) have the prefix `<role name>_`, for example `task_c_mode`, `task_b_file`. Application parameters in the inventory start with `app_` (`app_version`, `app_max_connections`); they are declared as required in the argument_specs of `deploy` and get no value in the role defaults. Encrypted values start with `vault_`.

Mechanism: Ansible variable names may contain only letters, digits and underscores, may not start with a digit, and may not be a Python keyword or a playbook keyword. Variables that start with an underscore are not private (`docs/docsite/rst/playbook_guide/playbooks_variables.rst` of `ansible/ansible-documentation@b16dcb8`, section "Creating valid variable names"). ansible-lint is a static checker for Ansible content. Its `var-naming` rule checks that names match `^[a-z_][a-z0-9_]*$`; `var-naming[no-role-prefix]` requires variables defined in a role to start with `role_name_`, except variables that start with `ansible_`. Files in `group_vars`, `host_vars`, `vars` and `defaults` directories are all checked as vars files, but only files that belong to a role are checked for the prefix. Sources: `src/ansiblelint/rules/var_naming.md` of `ansible/ansible-lint@26.10.1`, section "var-naming" (top of the file) and section "Settings"; `src/ansiblelint/rules/var_naming.py` of `ansible/ansible-lint@26.10.1`, methods `VariableNamingRule.get_var_naming_matcherror` and `VariableNamingRule.matchyaml`; `src/ansiblelint/config.py` of `ansible/ansible-lint@26.10.1`, module-level constant `DEFAULT_KINDS`. ansible-lint treats a directory as a role only if its path contains a `roles` directory and it has subdirectories such as `tasks`, `meta`, `vars`, `defaults` or `handlers` (`src/ansiblelint/file_utils.py` of `ansible/ansible-lint@26.10.1`, function `find_role_dir`). The example's tasks are in `business/tasks/<task>/` and the deploy role is in `business/deploy/`; neither path contains `roles`, so `no-role-prefix` does not check them. Instead, the prefixes of task parameters are checked by the engine (method `parameters` in `examples/platform/jenkins/src/deploy/Catalog.groovy`) and by `test_option_names_cannot_override_inventory_reserved_or_engine_variables` in `tests/test_tasks.py`; `test_documented_defaults_equal_role_defaults_and_cover_every_optional_option` in the same file requires `defaults/main.yml` to contain only optional parameters declared in argument_specs. Variables defined by `register` and `set_fact` are not checked automatically.

What you see: with `appMaxConn: 100` in `business/inventory/group_vars/web.yml` and `retry_count: 3` in `business/tasks/task_c/defaults/main.yml`, running ansible-lint as in 3.3 exits with code 2 and reports only the first one (output excerpt):

```text
var-naming[pattern]: Variables names should match ^[a-z_][a-z0-9_]*$ regex. (appMaxConn) (vars: appMaxConn)
business/inventory/group_vars/web.yml:5:1
```

`retry_count` is not reported by ansible-lint. It is caught by `test_documented_defaults_equal_role_defaults_and_cover_every_optional_option` (`[task_c]`) in `test_tasks.py`: `defaults/main.yml` contains a variable that argument_specs does not declare.

What happens otherwise: a role's `vars/` and `defaults/` are imported into the play scope, and roles that run later can read them too (`docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst` of `ansible/ansible-documentation@b16dcb8`, section "Role directory structure"; `docs/docsite/rst/playbook_guide/playbooks_variables.rst` of `ansible/ansible-documentation@b16dcb8`, section "Tips on where to set variables"). Without a prefix, one role's variable changes what another role reads for a variable of the same name; a variable of the same name in the inventory also overrides the role's default (see 1.4).

## 2. Sensitive data

### 2.1 ansible-vault: encrypt whole files, refer through the vault_ prefix

ansible-vault is the symmetric encryption tool that comes with Ansible. It can encrypt a single value or a whole file.

Rule: split `group_vars/all/` into two files, and do not use `encrypt_string` to encrypt single values. `main.yml` is plain text and names every variable; sensitive values are written as references, for example `app_api_token: "{{ vault_app_api_token }}"`. `vault.yml` is encrypted as a whole file and holds only variables with the `vault_` prefix.

Mechanism and sources: the documentation recommends this approach: put one plain-text file and one encrypted file in the group directory, give the variables in the encrypted file the `vault_` prefix, and refer to them from the plain-text file with Jinja. This way the variable names can still be found with `grep`, and the file names are free to choose (`docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst` of `ansible/ansible-documentation@b16dcb8`, section "Keep vaulted variables safely visible"). With whole-file encryption the password can be rotated with `rekey`; with single-value encryption it cannot (`docs/docsite/rst/vault_guide/vault_encrypting_content.rst` of `ansible/ansible-documentation@b16dcb8`, sections "Advantages and disadvantages of encrypting files" and "Advantages and disadvantages of encrypting variables"). An encrypted file is decrypted whenever it is loaded, so every command that uses this inventory needs the password (footnote of section "Encrypting content with Ansible Vault" in `docs/docsite/rst/vault_guide/vault_encrypting_content.rst` of `ansible/ansible-documentation@b16dcb8`; `docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst` of `ansible/ansible-documentation@b16dcb8`, section "Keep vaulted variables safely visible"). Leading and trailing whitespace is stripped after the password file is read, so a trailing newline does not affect the password. If the password file is executable, Ansible runs it and uses its output as the password (`lib/ansible/parsing/vault/__init__.py` of `ansible/ansible@2.21.5`, method `FileVaultSecret._read_file` and function `get_file_vault_secret`).

The start of the encrypted file and the decrypted content:

```text
$ head -c 26 ../../business/inventory/group_vars/all/vault.yml; echo; ansible-vault view --vault-password-file ../../../tests/example-vault-password.txt ../../business/inventory/group_vars/all/vault.yml
$ANSIBLE_VAULT;1.1;AES256

# Only sensitive variables with the vault_ prefix; the whole file is encrypted with ansible-vault
vault_app_api_token: example-only-token
```

What happens otherwise: if the whole `main.yml` is encrypted, the variable names cannot be searched, and code review cannot see the changes (`docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst` of `ansible/ansible-documentation@b16dcb8`, section "Keep vaulted variables safely visible"). With single-value encryption, rotating the password means re-encrypting each value one by one.

### 2.2 no_log

`no_log` is a task-level (or play-level) keyword that hides the result and the arguments of that task.

Rule: every task that uses a secret variable in its arguments or result gets `no_log: true`, and only on that single task. The example has no such task: the output of `deploy` does not contain `app_api_token`.

Sources: `docs/docsite/rst/reference_appendices/logging.rst` of `ansible/ansible-documentation@b16dcb8`, section "Protecting sensitive data with no_log"; `docs/docsite/rst/reference_appendices/faq.rst` of `ansible/ansible-documentation@b16dcb8`, section "How do I keep secret data in my playbook?" (it recommends using it only on single tasks and says it does not affect the output of `ANSIBLE_DEBUG`).

What you see with it: write a temporary playbook in `platform/ansible/` (`show-token.yml`, not part of the template) that prints `app_api_token` with the same `debug` task, once without `no_log: true` and once with it. Output of `ansible-playbook --vault-password-file ../../../tests/example-vault-password.txt show-token.yml` (excerpt):

```text
TASK [Without no_log] **********************************************************
ok: [web-01] => {
    "msg": "token is example-only-token"
}

TASK [With no_log] *************************************************************
ok: [web-01]
```

Limit: `no_log` only hides the task result. It does not hide the `[ERROR]` line that ansible-core 2.21.5 prints (see the output in 3.2 when a secret variable has the wrong type).

### 2.3 How the Jenkins Ansible plugin gives the vault password to ansible-playbook

Rule: the vault password is stored in the Jenkins credential store as a "Secret text" credential (the plugin also accepts "Secret file"; `TaskRunner.checkCredentials` checks it with a Secret text binding). `TaskRunner.playbook` adds `vaultCredentialsId: credentials.vaultCredentialsId` to every `ansiblePlaybook` call (method `playbook` in `examples/platform/jenkins/src/deploy/TaskRunner.groovy`; the credential ID is passed in by the `vaultCredentialsId` parameter when the Jenkinsfile calls `deployPipeline`). `vaultTmpPath` keeps its default value.

Mechanism and sources: **Credential type**: the README says "Secret text" or "Secret file" can be used, and the drop-down list offers only `FileCredentials` and `StringCredentials` (`README.md` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`, section "Vault Credentials"; method `doFillVaultCredentialsIdItems` of class `DescriptorImpl` in `src/main/java/org/jenkinsci/plugins/ansible/workflow/AnsiblePlaybookStep.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`). The credential is looked up by ID as `StandardCredentials`, so credentials of other types are found too; but only the two types above produce `--vault-password-file`, and other types are silently skipped (method `perform` in `src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`; method `appendVaultPasswordFile` in `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`). **Writing the temporary file**: the password file is written under `vaultTmpPath`; when that is not set, it is written in the step's workspace. The file name looks like `vault*.password` and the mode is 0400. For Secret file the file content is copied; for Secret text the plain-text password is written. 0400 has no execute bit, so ansible reads it as an ordinary password file (method `appendVaultPasswordFile` in `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`; the `FileCredentials` and `StringCredentials` overloads of method `createVaultPasswordFile` in `src/main/java/org/jenkinsci/plugins/ansible/Utils.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`; `README.md` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`, section "Arguments"). **How it is passed**: only the path of the password file appears on the command line (`--vault-password-file <path>`), not the password itself (method `appendVaultPasswordFile` in `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`; method `buildCommandLine` in `src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookInvocation.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`). **When it is deleted**: after the ansible-playbook process ends, the plugin deletes the password file and the SSH private key file in a `finally` block, on both success and failure. If deletion fails, it only prints a warning that starts with `[WARNING] temp file`; the build does not fail (method `execute` in `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`; method `deleteTempFile` in `src/main/java/org/jenkinsci/plugins/ansible/Utils.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`).

What happens otherwise: if a credential of another type is chosen (for example Username with password), the plugin reports no error; it just does not add `--vault-password-file`. ansible-playbook then fails with `[ERROR]: Attempting to decrypt but no vault secrets found.` and exit code 4 (see the output without a password in 3.1 and 3.2).

## 3. Validation and review

### 3.1 ansible-inventory --graph / --list / --host

Mechanism: `ansible-inventory` can run only one of `--list`, `--host` and `--graph` at a time. `--graph` only walks groups and hosts; it reads variables only when `--vars` is added. `--list` and `--host` print the merged variables of each host, and templates in the values are not rendered.

Sources: `lib/ansible/cli/inventory.py` of `ansible/ansible@2.21.5`, methods `InventoryCLI.init_parser` and `InventoryCLI._graph_group`.

| Command | Needs vault password | Output | Use |
| --- | --- | --- | --- |
| `--graph [group]` | No | Tree of groups and hosts | View group relationships in CI and in review |
| `--graph --vars` | Yes | Tree plus variables | Not used |
| `--list` | Yes | All groups, hosts and variables, including decrypted plain text | Only for local troubleshooting |
| `--host <host>` | Yes | Merged variables of one host, including plain text | Only for local troubleshooting |

Output without a password (run in `examples/platform/ansible/`):

```text
$ ansible-inventory --graph
@all:
  |--@ungrouped:
  |--@deploy_targets:
  |  |--@web:
  |  |  |--web-01
  |  |  |--web-02
  |  |--@api:
  |  |  |--api-01
  |  |--@worker:
  |  |  |--worker-01
  |  |  |--worker-02
$ ansible-inventory --list
[ERROR]: Attempting to decrypt but no vault secrets found.
$ ansible-inventory --host web-02
[ERROR]: Attempting to decrypt but no vault secrets found.
$ ansible-inventory --graph --vars
[ERROR]: Attempting to decrypt but no vault secrets found.
$ ansible-inventory --graph no_such_group
[ERROR]: Pattern must be valid group name when using --graph
```

The exit codes of these commands are 0, 4, 4, 4 and 5, in that order. With the password, the output of `ansible-inventory --vault-password-file ../../../tests/example-vault-password.txt --list --yaml` shows the secret value in plain text for every host (excerpt):

```text
            web-01:
              ansible_host: 192.0.2.11
              app_api_token: '{{ vault_app_api_token }}'
              app_base_dir: /opt/apps
              app_max_connections: 100
              vault_app_api_token: example-only-token
```

What happens otherwise: if `--list` or `--host` runs in CI with the password, the secrets go into the build log in plain text. `app_api_token` in the `--host` output is an unrendered template. To see the actual value, use `ansible <host> -m ansible.builtin.debug -a var=<variable>`.

### 3.2 Validate every host with argument_specs + validate_argument_spec

Rule: `business/deploy/meta/argument_specs.yml` is the single source of the host variable specification. `check-inventory.yml` reads this specification and runs `ansible.builtin.validate_argument_spec` on every host in `deploy_targets`. Secret variables are declared only with `type: str` and `required: true`; they get no `choices` and are not declared as any other type. Do not use the play-level `validate_argspec` available since 2.20; it is still a tech preview (`docs/docsite/rst/playbook_guide/playbooks_variables_validation.rst` of `ansible/ansible-documentation@b16dcb8`, section "Play Argument Validation").

```yaml
# examples/platform/ansible/check-inventory.yml (comments removed)
- name: Check inventory
  hosts: deploy_targets
  gather_facts: false
  vars:
    deploy_spec: "{{ lookup('ansible.builtin.file', playbook_dir ~ '/../../business/deploy/meta/argument_specs.yml') | from_yaml }}"
  tasks:
    - name: Validate host variables against the deploy argument spec
      ansible.builtin.validate_argument_spec:
        argument_spec: "{{ deploy_spec.argument_specs.main.options }}"
```

Mechanism: when a role has argument_specs, Ansible automatically inserts a validation task with the `always` tag before the role runs. So when `deploy.yml` runs, the same specification is checked again (`docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst` of `ansible/ansible-documentation@b16dcb8`, section "Role argument validation"; `lib/ansible/playbook/role/__init__.py` of `ansible/ansible@2.21.5`, method `Role._create_validation_task`). The action plugin of `validate_argument_spec` declares that it does not need a connection to the host. It takes the variables listed in the spec from the host variables and renders templates before validating, so vault references are decrypted (attribute `_requires_connection` of class `ActionModule`, method `ActionModule.get_args_from_task_vars` and method `ActionModule.run` in `lib/ansible/plugins/action/validate_argument_spec.py` of `ansible/ansible@2.21.5`). The example in the module documentation also reads the file with the `ansible.builtin.file` lookup and parses it with `from_yaml` to get the specification (`lib/ansible/modules/validate_argument_spec.py` of `ansible/ansible@2.21.5`, module-level string `EXAMPLES`).

When everything passes, the exit code is 0. The host addresses are all in the documentation range 192.0.2.0/24 and there are no real hosts, yet every host still returns `ok`, which shows that the validation does not connect to the hosts. Running `platform/ansible/check-inventory.yml` from the workspace root (`examples/`) with `ANSIBLE_CONFIG` gives the same result. Output (excerpt):

```text
$ ansible-playbook --vault-password-file ../../../tests/example-vault-password.txt check-inventory.yml
ok: [web-01]
ok: [web-02]
ok: [api-01]
ok: [worker-02]
ok: [worker-01]
```

When there are problems, all of them are listed in one run. For example, when `group_vars/api.yml` has no `app_version`, `app_port` in `group_vars/worker.yml` is `nine-thousand`, and `host_vars/web-01.yml` has `app_max_connections: unlimited`, the exit code is 2 and the output is (excerpt):

```text
fatal: [web-01]: FAILED! => {"argument_errors": ["argument 'app_max_connections' is of type str and we were unable to convert to int: \"'unlimited'\" cannot be converted to an int"]
fatal: [api-01]: FAILED! => {"argument_errors": ["missing required arguments: app_version"]
fatal: [worker-01]: FAILED! => {"argument_errors": ["argument 'app_port' is of type str and we were unable to convert to int: \"'nine-thousand'\" cannot be converted to an int"]
fatal: [worker-02]: FAILED! => {"argument_errors": ["argument 'app_port' is of type str and we were unable to convert to int: \"'nine-thousand'\" cannot be converted to an int"]
web-02                     : ok=1    changed=0    unreachable=0    failed=0    skipped=0    rescued=0    ignored=0
```

Running `ansible-playbook check-inventory.yml` without a password exits with code 4 and the error `[ERROR]: Attempting to decrypt but no vault secrets found.`. When the reference in `main.yml` is mistyped as `vault_app_api_tokn`, validation reports it as undefined: `fatal: [web-01]: FAILED! => {"changed": false, "msg": "Task failed: 'vault_app_api_tokn' is undefined"}`.

Why "secret variables are declared only with `type: str`": the format description of role argument_specs does not list a `no_log` field (`docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst` of `ansible/ansible-documentation@b16dcb8`, section "Specification format"). What you see otherwise: change the spec of `app_api_token` to `type: int` and add `no_log: true`, also add `no_log: true` to the validation task, and run `ansible-playbook --vault-password-file ../../../tests/example-vault-password.txt check-inventory.yml --limit web-01`. The exit code is 2. The task result is hidden, but the `[ERROR]` line still prints the plain text (excerpt):

```text
[ERROR]: Task failed: Action failed: Validation of arguments failed:
argument 'app_api_token' is of type str and we were unable to convert to int: "'example-only-token'" cannot be converted to an int
fatal: [web-01]: FAILED! => {"censored": "the output has been hidden due to the fact that 'no_log: true' was specified for this result", "changed": false}
```

The plugin writes both stdout and stderr of ansible-playbook to the build log (method `execute` in `src/main/java/org/jenkinsci/plugins/ansible/CLIRunner.java` of `jenkinsci/ansible-plugin@635.v34b_48e979c86`).

What happens otherwise: without this step, a missing variable or a wrong type only shows up when the corresponding host group is deployed, and only for the groups that were selected. If a secret variable uses `choices` or a non-string type, the secret appears in the Jenkins console as soon as validation fails.

### 3.3 ansible-lint

Rule: run `ansible-lint --offline --profile production` in `examples/`, with both `ANSIBLE_CONFIG` and `ANSIBLE_VAULT_PASSWORD_FILE` set (`test_ansible_lint_passes_with_production_profile` in `tests/test_inventory.py`; the test sets both environment variables itself). `examples/.ansible-lint` has two parts. `run-task.yml` needs the two variables `task_role` and `task_hosts`, and `extra_vars` gives them example values. Every file under `business/tasks/` has a directory named `tasks` in its path, and by default ansible-lint treats all such files as task lists (`src/ansiblelint/config.py` of `ansible/ansible-lint@26.10.1`, module-level constant `DEFAULT_KINDS`); `kinds:` sets the kind for `meta/argument_specs.yml`, `meta/task.yml` and `tests/*.yml` (`tests/cases.yml` holds a task's test cases and is plain YAML); `defaults/` and `tasks/` already get the right kinds from `DEFAULT_KINDS`, which matches them before the catch-all rule for directories named `tasks`.

Mechanism and sources: `--offline` means the dependencies in `requirements.yml` are not installed and schemas are not refreshed. `--profile` selects a rule set, and `production` is the strictest one (`src/ansiblelint/cli.py` of `ansible/ansible-lint@26.10.1`, function `get_cli_parser`; `docs/profiles.md` of `ansible/ansible-lint@26.10.1`, sections "Profiles" and "production"). ansible-lint reads `.ansible-lint` from the current directory. Internally it runs `ansible-playbook --syntax-check` on each playbook, and passes `extra_vars` along when they are configured (`docs/configuring.md` of `ansible/ansible-lint@26.10.1`, section "Using local configuration files"; `docs/usage.md` of `ansible/ansible-lint@26.10.1`, section "Vaults"; `src/ansiblelint/runner.py` of `ansible/ansible-lint@26.10.1`, method `Runner._get_ansible_syntax_check_matches`). The password comes from the same places as for ansible-playbook (for example `ANSIBLE_VAULT_PASSWORD_FILE`); interactive input is not supported. If the configured password file is executable, checking untrusted content may run code from it (`docs/usage.md` of `ansible/ansible-lint@26.10.1`, section "Vaults").

The following commands run at the `examples/` level, each prefixed with `ANSIBLE_CONFIG=$PWD/platform/ansible/ansible.cfg`.

1. Without a password, `vault.yml` is skipped with only a warning, and the exit code is 0:

   ```text
   $ ansible-lint --offline --profile production
   WARNING  Ignored exception from JinjaRule.matchyaml while processing business/inventory/group_vars/all/vault.yml (vars): Decryption failed (no vault secrets were found that could decrypt).
   WARNING  Ignored exception from VariableNamingRule.matchyaml while processing business/inventory/group_vars/all/vault.yml (vars): Decryption failed (no vault secrets were found that could decrypt).

   Passed: 0 failure(s), 0 warning(s) in <n> files processed of <m> encountered. Profile 'production' was required, and it passed.
   ```
2. With `ANSIBLE_VAULT_PASSWORD_FILE=$PWD/../tests/example-vault-password.txt` added, the warnings go away and the check still passes.
3. When `vault.yml` contains a name that breaks the rule, such as `vaultBackupKey: example-only-value`, the result without a password is still `Passed` with exit code 0; with the password, the exit code is 2:

   ```text
   var-naming[pattern]: Variables names should match ^[a-z_][a-z0-9_]*$ regex. (vaultBackupKey) (vars: vaultBackupKey)
   business/inventory/group_vars/all/vault.yml:3:1
   ```
4. Without `.ansible-lint`, the exit code is 2: `syntax-check[specific]: Error processing keyword 'name': 'task_role' is undefined` (`platform/ansible/run-task.yml:3:9`); every file under `business/tasks/*/meta/` is treated as a task list and gets its own `schema[tasks]` error.

What happens otherwise: without the password, a wrong variable name in `vault.yml` is not found. Without the `extra_vars` in `.ansible-lint`, `run-task.yml` fails the syntax check; without `kinds:`, the files under `business/tasks/*/meta/` are checked against the task list schema and fail.

### 3.4 Running in CI

The example's automated tests (`tests/`) do the CI checks of the inventory. They run Ansible the Jenkins way: `ANSIBLE_CONFIG` points at `examples/platform/ansible/ansible.cfg`, the password comes from `ANSIBLE_VAULT_PASSWORD_FILE`, and task parameters are passed with `-e @file` (`run_ansible` in `tests/conftest.py`). The checks related to the inventory:

1. Run `ansible-inventory --graph deploy_targets` without a password and assert the group tree in the output (`test_deploy_targets_graph_lists_every_group_with_its_hosts_without_vault_password`);
2. Run `ansible-inventory --list` without a password; it must fail (`test_listing_variables_without_vault_password_fails`);
3. `ansible-playbook check-inventory.yml` passes for all hosts; on a broken copy, it reports all problems in one run (`test_check_inventory_passes_for_every_deploy_target`, `test_check_inventory_reports_every_broken_host_in_one_run`);
4. Run `ansible-lint --offline --profile production` (`test_ansible_lint_passes_with_production_profile`).

The tests use `--host` to read variables only for assertions and do not print the output. In CI logs, do not run `ansible-inventory --list` or `--host` and print the result (see 3.1).

## Common commands

Run the following commands in `examples/platform/ansible/` (except ansible-lint, see the comment). When running from another directory, first set `ANSIBLE_CONFIG=<path>/examples/platform/ansible/ansible.cfg`. Commands that need the password are written here with `--ask-vault-pass` (interactive input). In CI, the password is passed by the plugin with `--vault-password-file`, or provided through the environment variable `ANSIBLE_VAULT_PASSWORD_FILE`.

```bash
# Tree of groups and hosts (no password needed)
ansible-inventory --graph deploy_targets
# Merged variables of one host (needs the password; output contains plain text; local use only)
ansible-inventory --ask-vault-pass --host web-02
# Actual value of one variable (template rendered; needs the password)
ansible web-02 --ask-vault-pass -m ansible.builtin.debug -a var=app_max_connections
# Configuration file, inventory and roles paths actually in effect
ansible-config dump --only-changed
# Validate the variables of all deploy targets (needs the password; does not connect to hosts)
ansible-playbook --ask-vault-pass check-inventory.yml

# Static check (run in examples/; it reads .ansible-lint from the current directory; the password comes from an environment variable)
ANSIBLE_CONFIG=$PWD/platform/ansible/ansible.cfg ANSIBLE_VAULT_PASSWORD_FILE=<password file> ansible-lint --offline --profile production

# Edit, view and rotate the password of the encrypted file
ansible-vault edit ../../business/inventory/group_vars/all/vault.yml
ansible-vault view ../../business/inventory/group_vars/all/vault.yml
ansible-vault rekey ../../business/inventory/group_vars/all/vault.yml
```
