# Ansible inventory 管理

适用版本：ansible-core 2.21.5、ansible-lint 26.10.1、Jenkins Ansible 插件 635.v34b_48e979c86。

出处有两种写法：上游源码与文档写成 `.reference-cache/<owner>/<name>@<version>/<path>:<行号>`，指上游仓库在该版本的同一路径与行号，对应关系见 [BRIEF.md 第 8 节](../BRIEF.md#8-出处)；示例代码按文件与函数名引用。文中实验都在示例目录 `examples/ansible/` 上运行；需要改文件的实验，在该目录的副本上改动后运行。vault 口令文件是 `examples/tests/example-vault-password.txt`，内容为示例口令 `example-only-password`；在 `examples/ansible/` 下写作 `../tests/example-vault-password.txt`。

术语：**inventory（清单）**是描述被管理主机及其分组的数据源；**主机组（group）**是一组主机，组可以包含子组，所有组都是 `all` 的后代；**变量层**是给主机赋值的不同位置，例如 `group_vars`、`host_vars`、命令行；**role（角色）**是一个目录，放可复用的任务、默认变量和元数据。

## 结论

1. 采用目录式 inventory：`ansible.cfg` 中设 `inventory = inventory`。`hosts.yml` 只描述主机与组的关系，变量一律放在 `inventory/group_vars/` 和 `inventory/host_vars/`。playbook 所在的 `examples/ansible/` 下不建 `group_vars/` 和 `host_vars/`。
2. 所有 Ansible 命令都用环境变量 `ANSIBLE_CONFIG` 指向 `examples/ansible/ansible.cfg`。它优先于当前目录下的 `ansible.cfg`；配置里的相对路径以配置文件所在目录为基准，所以从哪个目录运行，结果都一样。
3. 变量优先级从低到高：role defaults → `group_vars/all` → 子组（`web` 等）→ `host_vars` → extra vars。`meta/argument_specs.yml` 里的 `default` 只是文档，运行时不生效；示例仍写出它，并与 `defaults/main.yml` 保持一致（`test_catalog.py` 检查）。
4. 一台主机只属于 `web`、`api`、`worker` 中的一个组。同级组的同名变量按组名字母顺序合并，结果不直观。
5. 命名：变量名只用小写字母、数字和下划线；role 自己定义的变量加 `<role 名>_` 前缀；inventory 中的应用参数以 `app_` 开头；加密值以 `vault_` 开头。前两条由 ansible-lint 的 `var-naming` 规则检查。
6. 敏感值放在整文件加密的 `group_vars/all/vault.yml`，`main.yml` 中写 `app_api_token: "{{ vault_app_api_token }}"` 引用它。
7. Jenkins 侧把 vault 口令存成 “Secret file” 或 “Secret text” 凭据，通过 `vaultCredentialsId` 交给插件。插件把口令写进权限为 0400 的临时文件，用 `--vault-password-file` 传给 ansible-playbook，进程结束后在 `finally` 中删除该文件。
8. `ansible-inventory --graph` 不读变量，不需要 vault 口令。`--list` 和 `--host` 需要口令，且输出解密后的明文，所以不在 CI 中运行。
9. `check-inventory.yml` 用 `ansible.builtin.validate_argument_spec`，按 `app_deploy` 的参数声明校验每台主机。它不连接主机，一次报告全部问题。
10. ansible-lint 用 `ansible-lint --offline --profile production` 运行，并且要提供 vault 口令，否则 `vault.yml` 不会被检查。
11. 校验失败时，错误信息会打印变量的值，`no_log` 也遮不住这一行。因此秘密变量在 spec 中只声明 `type: str` 和 `required: true`。

## 1. 目录与变量分层

### 1.1 目录式 inventory，hosts 文件只放主机与组

规则：把整个 `inventory/` 目录作为一个 inventory 源，`hosts.yml` 中不写 `vars:`。下面是示例 `examples/ansible/inventory/hosts.yml` 的内容（已去掉注释）：

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

变量的分布：`group_vars/all/main.yml`：`app_base_dir`、`app_run_user`、`app_api_token`；`group_vars/all/vault.yml`（加密）：只有 `vault_app_api_token`；`group_vars/web.yml`、`api.yml`、`worker.yml`：各有 `app_name`、`app_version`、`app_port`、`app_max_connections`；`host_vars/<主机>.yml`：`ansible_host`（192.0.2.0/24 内的地址）；`web-02.yml` 另外把 `app_max_connections` 改为 200。

机制：一个目录可以作为单一 inventory 源。Ansible 汇总目录内的各个源，从顶层往下按字母序读取。加载 inventory 源时，名为 `group_vars`、`host_vars` 的目录总是被跳过。这两个目录由默认的 vars 插件 `host_group_vars` 读取；vars 插件是在 inventory 之外为主机和组提供变量的插件。这些变量文件里可以使用列表、字典等结构。组目录（如 `group_vars/all/`）下的所有文件按字典序读取。`ansible_host` 是连接时用的实际地址，与主机别名是两回事。

出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:247`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:269`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/inventory/manager.py:54`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:287`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:499`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:519`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:614`。

不这样做会怎样：如果变量写在 `hosts.yml` 的 `vars:` 下，它和 `group_vars` 中的同名变量就处在两个不同的优先级层（"Inventory file or script group vars" 低于 "Inventory group_vars/*"：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:465`），查一个值要翻两处。

### 1.2 用 ANSIBLE_CONFIG 指定配置，相对路径以配置文件为基准

规则：`examples/ansible/ansible.cfg` 只写两个相对路径：`inventory = inventory` 和 `roles_path = roles`。所有命令都通过 `ANSIBLE_CONFIG` 使用这个文件。Jenkins 侧，`runPlaybook` 在每次 `ansiblePlaybook` 调用外包一层 `withEnv(["ANSIBLE_CONFIG=${env.WORKSPACE}/${plan.baseDir}/ansible/ansible.cfg"])`（`examples/jenkins/deploy-engine.groovy` 的 `runPlaybook` 函数）。playbook 写成相对于工作区的路径（同文件中运行 `deploy.yml` 的调用）。测试用同样的方式运行 Ansible（`examples/tests/conftest.py` 的 `run_ansible`）。

机制：**查找顺序**：ansible-core 依次查找 `ANSIBLE_CONFIG`、当前目录的 `ansible.cfg`、`~/.ansible.cfg`、`/etc/ansible/ansible.cfg`，只用找到的第一个。如果当前目录全局可写，就跳过这个目录（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/general_precedence.rst:33`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:266`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:279`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:296`）。**相对路径**：从配置文件读到的值，以该文件作为来源（origin）。`inventory` 的类型是 `pathlist`，`roles_path` 的类型是 `pathspec`，这两类值里的相对路径以来源文件所在目录为基准展开（`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:672`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/base.yml:807`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/base.yml:1096`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:99`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:202`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/utils/path.py:51`）。**插件侧**：插件把步骤的环境变量（包括 `withEnv` 设的值）复制给子进程，并以步骤工作目录为 cwd 启动 ansible-playbook。`runPlaybook` 不设 `inventory`，所以插件不加 `-i`（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/workflow/AnsiblePlaybookStep.java:385`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:73`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/CLIRunner.java:47`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:93`）。

实验：另建一个目录，在里面放一个 `ansible.cfg`（`inventory = other-inventory`），然后从这个目录运行命令。先设置 `ANSIBLE_CONFIG=<示例>/ansible.cfg`，运行 `ansible-config dump --only-changed`（输出节选）：

```text
CONFIG_FILE() = <仓库>/jenkins-ansible-deploy/examples/ansible/ansible.cfg
DEFAULT_HOST_LIST(<仓库>/jenkins-ansible-deploy/examples/ansible/ansible.cfg) = ['<仓库>/jenkins-ansible-deploy/examples/ansible/inventory']
DEFAULT_ROLES_PATH(<仓库>/jenkins-ansible-deploy/examples/ansible/ansible.cfg) = ['<仓库>/jenkins-ansible-deploy/examples/ansible/roles']
```

此时 `ansible-inventory --graph deploy_targets` 列出全部 5 台主机。不设 `ANSIBLE_CONFIG` 时，`CONFIG_FILE` 变成该目录自己的 `ansible.cfg`，`ansible-inventory --graph deploy_targets` 的结果是：

```text
[WARNING]: No inventory was parsed, only implicit localhost is available
[ERROR]: Pattern must be valid group name when using --graph
```

按 Jenkins 的方式，从工作区根目录运行部署（输出节选，退出码 0）：

```text
$ ANSIBLE_CONFIG=$PWD/examples/ansible/ansible.cfg ansible-playbook --vault-password-file examples/tests/example-vault-password.txt examples/ansible/deploy.yml --limit web
    "msg": "deploy web 2.3.1 to web-01 (192.0.2.11) dir=/opt/apps/web port=8080 max_connections=100 user=app"
    "msg": "deploy web 2.3.1 to web-02 (192.0.2.12) dir=/opt/apps/web port=8080 max_connections=200 user=app"
```

不这样做会怎样：如果依赖当前目录的 `ansible.cfg`，从工作区根目录运行时会读到别的配置，或者落到默认的 `/etc/ansible/hosts`。inventory 解析为空，只剩隐式 localhost。

### 1.3 group_vars / host_vars 只放在 inventory 目录下

规则：只用 `inventory/group_vars/` 和 `inventory/host_vars/`，不建 `examples/ansible/group_vars/`。

机制：`host_group_vars` 会在 inventory 源旁边查找这两个目录；运行 ansible-playbook 时，还会在 playbook 目录旁边查找。两处有同名变量时，以 playbook 旁的为准（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:503`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:531`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:472`）。示例的 inventory 源是 `examples/ansible/inventory` 目录，它旁边的是 `inventory/group_vars/`。三个 playbook 都直接放在 `examples/ansible/` 下，所以 playbook 旁的是 `examples/ansible/group_vars/`。ansible-playbook 把 playbook 所在目录设为基准目录。没有 playbook 的命令（`ansible`、`ansible-inventory`）默认以当前目录为基准目录，也可以用 `--playbook-dir` 指定（`.reference-cache/ansible/ansible@2.21.5/lib/ansible/playbook/__init__.py:62`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/parsing/dataloader.py:57`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/vars/manager.py:212`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:530`）。

实验：在示例副本中新建 `group_vars/web.yml`（与 `inventory/` 同级），写入 `app_max_connections: 999`。在副本的 `ansible/` 目录下运行时，三条命令都读到 999（输出节选）：

```text
== ansible-playbook deploy.yml ==
    "msg": "deploy web 2.3.1 to web-01 (192.0.2.11) dir=/opt/apps/web port=8080 max_connections=999 user=app"
== ansible ad hoc ==
    "app_max_connections": 999
== ansible-inventory --host ==
    "app_max_connections": 999,
```

改为从副本的上一级目录运行，`ANSIBLE_CONFIG` 指向副本的 `ansible.cfg`，与 Jenkins 相同：

```text
== ansible-playbook ansible/deploy.yml ==
    "msg": "deploy web 2.3.1 to web-01 (192.0.2.11) dir=/opt/apps/web port=8080 max_connections=999 user=app"
== ansible ad hoc ==
    "app_max_connections": 100
== ansible-inventory --host ==
    "app_max_connections": 100,
```

实验没有改变结论，只让原因更具体：这样的目录会被三个 playbook 全部读到，并覆盖 `inventory/group_vars/` 中的值；审查命令能不能看到它，却取决于当前目录；结果是部署实际用的是 999，从工作区根目录审查看到的却是 100，inventory 不再是主机参数的唯一来源。

### 1.4 变量优先级

规则：每个变量只在一层定义：所有主机共用的值放在 `group_vars/all/main.yml`；每组的值放在 `group_vars/<组>.yml`；个别主机的例外放在 `host_vars/<主机>.yml`。不在 role 的 `vars/` 中放主机参数，也不在命令行覆盖主机参数。

机制：官方列表按从低到高排列，下表只列与示例相关的几层：

| 层（低 → 高）| 出处 |
| --- | --- |
| role defaults（role 的 `defaults/main.yml`）| `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:464` |
| inventory `group_vars/all` | `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:466` |
| inventory `group_vars/*` | `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:468` |
| inventory `host_vars/*` | `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:471` |
| role vars（role 的 `vars/main.yml`）| `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:477` |
| extra vars（命令行 `-e` 传入的变量）| `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:484` |

补充规则：inventory 内部从低到高依次是 `all` 组 → 父组 → 子组 → 主机。同一级的组按组名字母顺序合并，后合并的覆盖先合并的（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:546`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:551`）。role defaults 可以被其他任何来源覆盖（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:32`）。argument_specs 是 role 在 `meta/argument_specs.yml` 中声明的参数规格。其中的 `default` 只用于文档，实际默认值始终来自 role defaults（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:400`）。

实验 1：host_vars 高于 group_vars。

```text
$ ansible-inventory --vault-password-file ../tests/example-vault-password.txt --host web-02
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

`web-02` 的 `app_max_connections` 取自 `host_vars/web-02.yml`，值为 200；`web-01` 取自 `group_vars/web.yml`，值为 100。

实验 2：子组高于 `all`。示例中没有跨层重名的变量，所以在副本的 `group_vars/web.yml` 末尾加一行 `app_run_user: web`（`all` 中的值是 `app`）：

```text
$ ansible web-01,api-01 --vault-password-file ../tests/example-vault-password.txt -m ansible.builtin.debug -a var=app_run_user
web-01 | SUCCESS => {
    "app_run_user": "web"
}
api-01 | SUCCESS => {
    "app_run_user": "app"
}
```

实验 3：extra vars 高于 host_vars；用 `key=value` 形式传入时，得到的是字符串。

```text
$ ansible web-02 --vault-password-file ../tests/example-vault-password.txt -m ansible.builtin.debug -a var=app_max_connections -e app_max_connections=500
web-02 | SUCCESS => {
    "app_max_connections": "500"
}
```

文档写明，`key=value` 形式的值按字符串处理；要保持类型，就传 JSON 或 YAML 文件（`-e @file`）。示例的任务参数正是用这种方式传入的（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:413`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:439`；`examples/jenkins/deploy-engine.groovy` 中运行 `run-task.yml` 的调用）。

实验 4：role defaults 优先级最低。用 `run-task.yml` 运行 `task_c`，参数文件通过 `-e @task-c-localhost.json` 传入，内容为 `{"task_role": "task_c", "task_hosts": "localhost"}`。原示例输出 `mode=plan`，这个值来自 `roles/task_c/defaults/main.yml`。在副本的 `group_vars/all/main.yml` 中加上 `task_c_mode: apply` 后，输出变为 `mode=apply`：

```text
== original example ==
    "msg": "localhost: task_c mode=plan label="
== copy with task_c_mode in group_vars/all ==
    "msg": "localhost: task_c mode=apply label="
```

实验 5：argument_specs 中的 `default` 不生效。在副本中删去 `roles/task_c/defaults/main.yml`，并在 `task_c` 的 argument_specs 里给每个参数加上 `default`（例如 `default: plan`）。同样运行，退出码为 2（输出节选）：

```text
TASK [task_c : Validating arguments against arg spec 'main' - 示例任务 C：带枚举参数和布尔参数] ***
ok: [localhost]
TASK [task_c : Run in the selected mode] ***************************************
[ERROR]: Task failed: Finalization of task args for 'ansible.builtin.debug' failed: Error while resolving value for 'msg': 'task_c_mode' is undefined
```

实验 6：隐式 localhost 也会拿到 `group_vars/all`。inventory 中没有定义 localhost 时，Ansible 会自动创建一个“隐式 localhost”。它不属于任何组，但会读取 `host_vars` 和 `all` 组的变量（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/implicit_localhost.rst:6`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/implicit_localhost.rst:33`）：

```text
$ ansible localhost --vault-password-file ../tests/example-vault-password.txt -m ansible.builtin.debug -a var=app_base_dir
localhost | SUCCESS => {
    "app_base_dir": "/opt/apps"
}
```

`ansible all --list-hosts` 只列出 5 台主机，其中没有 localhost。由此可知，在 Jenkins 节点本机上运行的任务（`task_hosts: localhost`）同样会加载 `group_vars/all/vault.yml`，因此同样需要 vault 口令。

不这样做会怎样：同一变量在多层都有定义时，审查的人很难看出最终取的是哪个值。一台主机如果同时属于 `web` 和 `api`，两组冲突的变量会按字母顺序由 `web` 覆盖 `api`。参数如果只在 argument_specs 里写了 `default`，运行时会报未定义（实验 5）。

### 1.5 变量命名

规则：变量名只用小写字母、数字和下划线，并以字母开头。role 自己定义的变量（`defaults/`、`vars/`、`register`、`set_fact`）以 `<role 名>_` 为前缀，例如 `task_c_mode`、`task_b_file`。inventory 中的应用参数以 `app_` 开头（`app_version`、`app_max_connections`），在 `app_deploy` 的 argument_specs 中声明为必填项，不在 role defaults 中给值。加密值以 `vault_` 开头。

机制：Ansible 变量名只能由字母、数字和下划线组成，不能以数字开头，也不能是 Python 关键字或 playbook 关键字。以下划线开头的变量并不是私有的（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:21`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:23`）。ansible-lint 是 Ansible 内容的静态检查工具，它的 `var-naming` 规则检查名称是否匹配 `^[a-z_][a-z0-9_]*$`；`var-naming[no-role-prefix]` 要求 role 内定义的变量以 `role_name_` 开头，`ansible_` 开头的变量除外；`group_vars`、`host_vars`、`vars`、`defaults` 目录下的文件都按 vars 文件检查，但只有属于某个 role 的文件才检查前缀。出处：`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/rules/var_naming.md:24`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/rules/var_naming.md:45`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/rules/var_naming.py:185`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/rules/var_naming.py:331`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/config.py:54`。

实验：在副本的 `group_vars/web.yml` 中加 `appMaxConn: 100`，在 `roles/task_c/defaults/main.yml` 中加 `retry_count: 3`，再按 3.3 的方式运行 ansible-lint。退出码为 2（输出节选）：

```text
var-naming[pattern]: Variables names should match ^[a-z_][a-z0-9_]*$ regex. (appMaxConn) (vars: appMaxConn)
inventory/group_vars/web.yml:5:1

var-naming[no-role-prefix]: Variables names from within roles should use task_c_ as a prefix. (vars: retry_count)
roles/task_c/defaults/main.yml:4:1
```

不这样做会怎样：role 的 `vars/` 和 `defaults/` 会导入 play 作用域，后面执行的 role 也能读到（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:42`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:594`）。不加前缀时，一个 role 的变量会改变另一个 role 读到的同名变量；inventory 中的同名变量也会覆盖 role 的默认值（见 1.4）。

## 2. 敏感信息

### 2.1 ansible-vault：整文件加密，vault_ 前缀间接引用

ansible-vault 是 Ansible 自带的对称加密工具，既可以加密单个值，也可以加密整个文件。

规则：把 `group_vars/all/` 拆成两个文件，不使用 `encrypt_string` 单值加密：`main.yml` 是明文，写出全部变量名，敏感值写成引用，例如 `app_api_token: "{{ vault_app_api_token }}"`；`vault.yml` 整文件加密，只放 `vault_` 前缀的变量。

机制与出处：文档推荐这种做法：组目录中放一个明文文件和一个加密文件，加密文件里的变量加 `vault_` 前缀，明文文件用 Jinja 引用它们。这样变量名仍然可以用 `grep` 找到，文件名不限（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:110`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:116`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:121`）。整文件加密可以用 `rekey` 轮换口令，单值加密不行（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/vault_guide/vault_encrypting_content.rst:184`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/vault_guide/vault_encrypting_content.rst:35`）。加密文件只要被加载就会被解密，所以使用这份 inventory 的每条命令都需要口令（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/vault_guide/vault_encrypting_content.rst:22`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:123`）。口令文件读入后会去掉首尾空白，所以末尾的换行不影响口令。如果口令文件是可执行文件，Ansible 会执行它，并把输出当作口令（`.reference-cache/ansible/ansible@2.21.5/lib/ansible/parsing/vault/__init__.py:414`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/parsing/vault/__init__.py:367`）。

实验：

```text
$ head -c 26 inventory/group_vars/all/vault.yml; echo; ansible-vault view --vault-password-file ../tests/example-vault-password.txt inventory/group_vars/all/vault.yml
$ANSIBLE_VAULT;1.1;AES256

# 只放 vault_ 前缀的敏感变量，整个文件用 ansible-vault 加密
vault_app_api_token: example-only-token
```

不这样做会怎样：如果整个 `main.yml` 都加密，变量名就无法搜索，代码审查也看不到改动（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:109`）。如果用单值加密，轮换口令时只能逐个值重新加密。

### 2.2 no_log

`no_log` 是任务（或 play）级别的关键字，用来隐藏该任务的结果和参数。

规则：凡是在参数或结果中用到秘密变量的任务，都加 `no_log: true`，并且只加在单个任务上。示例中没有这样的任务：`app_deploy` 的输出不含 `app_api_token`。

出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/logging.rst:16`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/faq.rst:734`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/faq.rst:751`（建议只用于单个任务）；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/faq.rst:753`（不影响 `ANSIBLE_DEBUG` 的输出）。

实验：在副本中加一个 playbook，用同一个 `debug` 任务输出 `app_api_token`，一次不加 `no_log: true`，一次加上。运行 `ansible-playbook --vault-password-file ../tests/example-vault-password.txt show-token.yml`（输出节选）：

```text
TASK [Without no_log] **********************************************************
ok: [web-01] => {
    "msg": "token is example-only-token"
}

TASK [With no_log] *************************************************************
ok: [web-01]
```

限制：`no_log` 只遮住任务结果，遮不住 ansible-core 2.21.5 打印的 `[ERROR]` 行（见 3.2 实验 4）。

### 2.3 Jenkins Ansible 插件如何把 vault 口令交给 ansible-playbook

规则：vault 口令在 Jenkins 凭据库中存为 “Secret file” 或 “Secret text” 凭据；`runPlaybook` 给每次 `ansiblePlaybook` 调用加上 `vaultCredentialsId: plan.ansible.vaultCredentialsId`（`examples/jenkins/deploy-engine.groovy` 的 `runPlaybook`）；`vaultTmpPath` 保持默认值。

机制与出处：**凭据类型**：README 写明可以用 “Secret text” 或 “Secret file”，下拉框里也只列出 `FileCredentials` 和 `StringCredentials`（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/README.md:188`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/workflow/AnsiblePlaybookStep.java:351`）。按 ID 查找凭据时用的是 `StandardCredentials`，所以其他类型的凭据也能找到；但只有上面两类会生成 `--vault-password-file`，其他类型被直接跳过（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java:313`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:272`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:276`）。**写临时文件**：口令文件写在 `vaultTmpPath` 下；没有设置时，写在步骤的工作区。文件名形如 `vault*.password`，权限为 0400。Secret file 是复制文件内容，Secret text 是写入明文口令。0400 不含执行位，所以 ansible 把它当作普通口令文件读取（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:271`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:74`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:76`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:93`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:95`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/README.md:97`）。**传递方式**：命令行上只出现口令文件的路径（`--vault-password-file <路径>`），不出现口令本身（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:275`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookInvocation.java:128`）。**何时删除**：ansible-playbook 进程结束后，插件在 `finally` 中删除口令文件和 SSH 私钥文件，成功和失败时都会执行。删除失败时只打印一条以 `[WARNING] temp file` 开头的警告，不会让构建失败（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:312`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:316`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:318`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:111`）。

不这样做会怎样：如果选了其他类型的凭据（例如 Username with password），插件不会报错，只是不加 `--vault-password-file`。随后 ansible-playbook 以 `[ERROR]: Attempting to decrypt but no vault secrets found.` 失败，退出码为 4（见 3.1、3.2 的实验）。

## 3. 校验与审查

### 3.1 ansible-inventory --graph / --list / --host

机制：`ansible-inventory` 一次只能执行 `--list`、`--host`、`--graph` 中的一个。`--graph` 只遍历组和主机，加上 `--vars` 才读取变量。`--list` 和 `--host` 输出每台主机合并后的变量，值中的模板不会被渲染。

出处：`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:61`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:62`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:64`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:248`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:251`。

| 命令 | 需要 vault 口令 | 输出 | 用途 |
| --- | --- | --- | --- |
| `--graph [组]` | 否 | 组与主机的树 | 在 CI 和审查中查看组关系 |
| `--graph --vars` | 是 | 树加变量 | 不用 |
| `--list` | 是 | 全部组、主机和变量，含解密后的明文 | 只在本地排查时使用 |
| `--host <主机>` | 是 | 单台主机合并后的变量，含明文 | 只在本地排查时使用 |

实验（在 `examples/ansible/` 下运行，不提供口令）：

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

以上命令的退出码依次为 0、4、4、4、5。提供口令后，`ansible-inventory --vault-password-file ../tests/example-vault-password.txt --list --yaml` 的输出中，每台主机都带有明文的秘密值（节选）：

```text
            web-01:
              ansible_host: 192.0.2.11
              app_api_token: '{{ vault_app_api_token }}'
              app_base_dir: /opt/apps
              app_max_connections: 100
              vault_app_api_token: example-only-token
```

不这样做会怎样：在 CI 中带口令运行 `--list` 或 `--host`，秘密会以明文进入构建日志。`--host` 输出中的 `app_api_token` 是未渲染的模板。要看实际值，用 `ansible <主机> -m ansible.builtin.debug -a var=<变量>`。

### 3.2 用 argument_specs + validate_argument_spec 校验每台主机

规则：`roles/app_deploy/meta/argument_specs.yml` 是主机变量规格的唯一来源；`check-inventory.yml` 读取这份规格，对 `deploy_targets` 中的每台主机运行 `ansible.builtin.validate_argument_spec`。秘密变量只声明 `type: str` 和 `required: true`，不加 `choices`，也不声明为其他类型。不使用 2.20 起提供的 play 级 `validate_argspec`，它目前仍是 tech preview（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables_validation.rst:6`）。

```yaml
# examples/ansible/check-inventory.yml（去掉注释）
- name: Check inventory
  hosts: deploy_targets
  gather_facts: false
  tasks:
    - name: Validate host variables against the app_deploy argument spec
      ansible.builtin.validate_argument_spec:
        argument_spec: "{{ (lookup('ansible.builtin.file', 'roles/app_deploy/meta/argument_specs.yml') | from_yaml).argument_specs.main.options }}"
```

机制：role 有 argument_specs 时，Ansible 会在 role 执行前自动插入一个带 `always` 标签的校验任务。因此运行 `deploy.yml` 时，同一份规格还会再校验一次（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:297`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:315`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/playbook/role/__init__.py:389`）。`validate_argument_spec` 的 action 插件声明自己不需要连接主机。它从主机变量中取出 spec 列出的变量，先渲染模板再校验，所以 vault 引用会被解密（`.reference-cache/ansible/ansible@2.21.5/lib/ansible/plugins/action/validate_argument_spec.py:16`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/plugins/action/validate_argument_spec.py:32`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/plugins/action/validate_argument_spec.py:34`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/plugins/action/validate_argument_spec.py:80`）。模块文档的示例也是用 `ansible.builtin.file` lookup 读文件，再经 `from_yaml` 解析得到规格（`.reference-cache/ansible/ansible@2.21.5/lib/ansible/modules/validate_argument_spec.py:74`）。

实验 1：全部通过。主机地址都在文档保留网段 192.0.2.0/24 内、没有真实主机，所有主机仍然返回 `ok`，说明校验时没有连接主机。退出码为 0；从工作区根目录以 `ANSIBLE_CONFIG` 运行 `examples/ansible/check-inventory.yml`，结果相同（输出节选）：

```text
$ ansible-playbook --vault-password-file ../tests/example-vault-password.txt check-inventory.yml
ok: [web-01]
ok: [web-02]
ok: [api-01]
ok: [worker-02]
ok: [worker-01]
```

实验 2：在副本中做三处改动：删掉 `group_vars/api.yml` 中的 `app_version`；把 `group_vars/worker.yml` 中的 `app_port` 改为 `nine-thousand`；在 `host_vars/web-01.yml` 中加 `app_max_connections: unlimited`。运行后退出码为 2，一次列出了全部问题（节选）：

```text
fatal: [web-01]: FAILED! => {"argument_errors": ["argument 'app_max_connections' is of type str and we were unable to convert to int: \"'unlimited'\" cannot be converted to an int"]
fatal: [api-01]: FAILED! => {"argument_errors": ["missing required arguments: app_version"]
fatal: [worker-01]: FAILED! => {"argument_errors": ["argument 'app_port' is of type str and we were unable to convert to int: \"'nine-thousand'\" cannot be converted to an int"]
fatal: [worker-02]: FAILED! => {"argument_errors": ["argument 'app_port' is of type str and we were unable to convert to int: \"'nine-thousand'\" cannot be converted to an int"]
web-02                     : ok=1    changed=0    unreachable=0    failed=0    skipped=0    rescued=0    ignored=0
```

实验 3：不提供口令运行 `ansible-playbook check-inventory.yml`，退出码为 4，报错 `[ERROR]: Attempting to decrypt but no vault secrets found.`。在副本中把 `main.yml` 里的引用写错成 `vault_app_api_tokn`，校验报未定义：`fatal: [web-01]: FAILED! => {"changed": false, "msg": "Task failed: 'vault_app_api_tokn' is undefined"}`。

实验 4（“秘密变量只声明 `type: str`”这条建议的依据）：role argument_specs 的格式说明中没有列出 `no_log` 字段（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:321`）。在副本中做两处改动：把 `app_api_token` 的规格改成 `type: int`，并加上 `no_log: true`；在校验任务上也加 `no_log: true`。然后运行 `ansible-playbook --vault-password-file ../tests/example-vault-password.txt check-inventory.yml --limit web-01`，退出码为 2。任务结果被遮住了，但 `[ERROR]` 行仍然打印出明文（节选）：

```text
[ERROR]: Task failed: Action failed: Validation of arguments failed:
argument 'app_api_token' is of type str and we were unable to convert to int: "'example-only-token'" cannot be converted to an int
fatal: [web-01]: FAILED! => {"censored": "the output has been hidden due to the fact that 'no_log: true' was specified for this result", "changed": false}
```

插件会把 ansible-playbook 的 stdout 和 stderr 都写进构建日志（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/CLIRunner.java:50`）。

不这样做会怎样：不做这一步，变量缺失或类型错误要等部署到对应主机组时才暴露，而且只有被选中的组会暴露出来。秘密变量如果用了 `choices` 或非字符串类型，一旦校验失败，口令就会出现在 Jenkins 控制台里。

### 3.3 ansible-lint

规则：在 `examples/ansible/` 下运行 `ansible-lint --offline --profile production`，同时设置 `ANSIBLE_CONFIG` 和 `ANSIBLE_VAULT_PASSWORD_FILE`（`examples/tests/test_inventory.py` 的 `test_ansible_lint_passes_with_production_profile`，环境变量由 `examples/tests/conftest.py` 的 `run_ansible` 设置）。`run-task.yml` 需要 `task_role` 和 `task_hosts` 两个变量，`examples/ansible/.ansible-lint` 中的 `extra_vars` 为它们提供示例值。

机制与出处：`--offline` 表示不安装 `requirements.yml` 中的依赖，也不刷新 schema。`--profile` 选择规则集，`production` 是最严格的一档（`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/cli.py:495`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/cli.py:340`；`.reference-cache/ansible/ansible-lint@26.10.1/docs/profiles.md:7`；`.reference-cache/ansible/ansible-lint@26.10.1/docs/profiles.md:97`）。ansible-lint 从当前目录读取 `.ansible-lint`。它内部对每个 playbook 运行 `ansible-playbook --syntax-check`；配置了 `extra_vars` 时会一并传入（`.reference-cache/ansible/ansible-lint@26.10.1/docs/configuring.md:18`；`.reference-cache/ansible/ansible-lint@26.10.1/docs/usage.md:342`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/runner.py:450`）。口令来源与 ansible-playbook 相同（例如 `ANSIBLE_VAULT_PASSWORD_FILE`），不支持交互输入。如果配置的口令文件是可执行文件，那么检查不受信任的内容时，可能执行其中的代码（`.reference-cache/ansible/ansible-lint@26.10.1/docs/usage.md:307`；`.reference-cache/ansible/ansible-lint@26.10.1/docs/usage.md:314`；`.reference-cache/ansible/ansible-lint@26.10.1/docs/usage.md:318`）。

实验在示例副本中进行，以下命令前都带 `ANSIBLE_CONFIG=$PWD/ansible.cfg`。

1. 不提供口令时，`vault.yml` 被跳过，只留下警告，退出码为 0：

   ```text
   $ ansible-lint --offline --profile production
   WARNING  Ignored exception from JinjaRule.matchyaml while processing inventory/group_vars/all/vault.yml (vars): Decryption failed (no vault secrets were found that could decrypt).
   WARNING  Ignored exception from VariableNamingRule.matchyaml while processing inventory/group_vars/all/vault.yml (vars): Decryption failed (no vault secrets were found that could decrypt).

   Passed: 0 failure(s), 0 warning(s) in 36 files processed of 37 encountered. Profile 'production' was required, and it passed.
   ```
2. 加上 `ANSIBLE_VAULT_PASSWORD_FILE=$PWD/../tests/example-vault-password.txt` 后，警告消失，检查同样通过。
3. 在副本的 `vault.yml` 中加入 `vaultBackupKey: example-only-value` 并重新加密。不提供口令时，结果仍是 `Passed`，退出码为 0；提供口令后，退出码为 2：

   ```text
   var-naming[pattern]: Variables names should match ^[a-z_][a-z0-9_]*$ regex. (vaultBackupKey) (vars: vaultBackupKey)
   inventory/group_vars/all/vault.yml:3:1
   ```
4. 删除 `.ansible-lint` 后，退出码为 2：`syntax-check[specific]: Error processing keyword 'name': 'task_role' is undefined`（`run-task.yml:5:9`）。

不这样做会怎样：不提供口令，`vault.yml` 中写错的变量名不会被发现。没有 `.ansible-lint` 中的 `extra_vars`，`run-task.yml` 通不过语法检查。

### 3.4 在 CI 中运行

示例的自动化测试（`examples/tests/`）承担 inventory 的 CI 检查。它们按 Jenkins 的方式运行 Ansible：`ANSIBLE_CONFIG` 指向 `examples/ansible/ansible.cfg`，口令来自 `ANSIBLE_VAULT_PASSWORD_FILE`，任务参数通过 `-e @file` 传入（`examples/tests/conftest.py` 的 `run_ansible`）。与 inventory 相关的检查：

1. 不提供口令运行 `ansible-inventory --graph deploy_targets`，断言输出的组树（`test_deploy_targets_graph_lists_every_group_with_its_hosts_without_vault_password`）；
2. 不提供口令运行 `ansible-inventory --list`，必须失败（`test_listing_variables_without_vault_password_fails`）；
3. `ansible-playbook check-inventory.yml` 全部通过；在改坏的副本上，一次报告所有问题（`test_check_inventory_passes_for_every_deploy_target`、`test_check_inventory_reports_every_broken_host_in_one_run`）；
4. 运行 `ansible-lint --offline --profile production`（`test_ansible_lint_passes_with_production_profile`）。

测试中用 `--host` 读取变量只是为了断言，不会打印输出。在 CI 日志里，不要运行 `ansible-inventory --list` 或 `--host` 并输出结果（见 3.1）。

## 常用命令

以下命令在 `examples/ansible/` 目录下执行。从其他目录执行时，先设置 `ANSIBLE_CONFIG=<路径>/examples/ansible/ansible.cfg`。需要口令的命令这里写的是 `--ask-vault-pass`（交互输入）。在 CI 中，口令由插件通过 `--vault-password-file` 传入，或者通过环境变量 `ANSIBLE_VAULT_PASSWORD_FILE` 提供。

```bash
# 组与主机的树（不需要口令）
ansible-inventory --graph deploy_targets
# 单台主机合并后的变量（需要口令，输出含明文，仅本地使用）
ansible-inventory --ask-vault-pass --host web-02
# 某个变量的实际值（模板已渲染，需要口令）
ansible web-02 --ask-vault-pass -m ansible.builtin.debug -a var=app_max_connections
# 实际生效的配置文件、inventory 与 roles 路径
ansible-config dump --only-changed
# 校验全部部署目标的变量（需要口令，不连接主机）
ansible-playbook --ask-vault-pass check-inventory.yml

# 静态检查（口令通过环境变量提供）
ANSIBLE_VAULT_PASSWORD_FILE=<口令文件> ansible-lint --offline --profile production

# 编辑、查看、轮换加密文件
ansible-vault edit inventory/group_vars/all/vault.yml
ansible-vault view inventory/group_vars/all/vault.yml
ansible-vault rekey inventory/group_vars/all/vault.yml
```
