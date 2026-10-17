# Ansible inventory 管理：调研结论

适用版本：ansible-core 2.21.5、ansible-lint 26.10.1、Jenkins Ansible 插件 635.v34b_48e979c86。

出处写成 `.reference-cache/<owner>/<name>@<version>/<path>:<行号>`，指向本地只读的源码与文档缓存。文中的实验在一份与示例 `examples/ansible/` 结构相同的实验 inventory 上运行，命令和输出都是实际结果。实验用的 vault 口令文件是 `../vault-pass.txt`，内容为示例口令 `example-only-password`。实验 inventory 里的变量：`group_vars/all/main.yml` 有 `app_log_level: info`、`app_db_password: "{{ vault_app_db_password }}"`；加密的 `group_vars/all/vault.yml` 有 `vault_app_db_password: example-secret-value`；`group_vars/web.yml` 有 `app_version: "1.4.2"`、`app_port: 8080`、`app_log_level: debug`；`group_vars/api.yml`、`group_vars/worker.yml` 有 `app_version`、`app_port`；`host_vars/<host>.yml` 有 `ansible_host`（192.0.2.0/24 内的地址），`web-02` 另有 `app_port: 8081`。

术语：**inventory（清单）**是描述被管理主机及其分组的数据源；**主机组（group）**是一组主机，组可以包含子组，所有组都是 `all` 的后代；**变量层**是给主机赋值的不同位置，例如 `group_vars`、`host_vars`、命令行；**role（角色）**是一个目录，里面放可复用的任务、默认变量和元数据。

## 结论

1. 采用目录式 inventory：`ansible.cfg` 中设 `inventory = inventory/`。`hosts.yml` 只描述主机与组的关系；变量一律放在 inventory 目录旁的 `group_vars/`、`host_vars/` 里。playbook 目录旁不放 `group_vars/`、`host_vars/`。
2. 优先级从低到高依次为：role defaults → `group_vars/all` → 父组 → 子组 → `host_vars` → extra vars。`meta/argument_specs.yml` 里的 `default` 只是文档，运行时不会生效。
3. 一台主机只属于 `web`、`api`、`worker` 中的一个组。同级组之间的冲突按组名字母顺序合并，结果不直观。
4. 命名规则：变量名只用小写字母、数字和下划线；role 自己定义的变量加 `app_deploy_` 前缀；加密值统一用 `vault_` 前缀。这三条都用 ansible-lint 的 `var-naming` 规则检查。
5. 敏感值放在 `group_vars/all/vault.yml`，整文件加密；`main.yml` 中用 `"{{ vault_xxx }}"` 引用。
6. Jenkins 侧把 vault 口令存成 “Secret file” 或 “Secret text” 凭据，通过 `vaultCredentialsId` 传给插件。插件把口令写进权限为 0400 的临时文件，用 `--vault-password-file` 交给 ansible-playbook，进程结束后在 `finally` 中删除这个文件。
7. `ansible-inventory --graph` 不读变量，所以不需要 vault 口令。`--list` 和 `--host` 需要口令，而且会把解密后的值以明文输出，因此不在 CI 中运行，也不归档它们的输出。
8. `playbooks/check-inventory.yml` 用 `ansible.builtin.validate_argument_spec` 和 role 的 `meta/argument_specs.yml` 校验每台主机的变量，过程中不连接主机，一次报告所有主机的问题。
9. 校验失败的错误信息会打印变量值，`no_log` 遮不住这一行。所以秘密变量在 spec 中只声明 `type: str` 和 `required: true`。

## 1. 目录与变量分层

### 1.1 目录式 inventory，hosts 文件只放主机与组

规则：`inventory/` 整个目录作为一个 inventory 源，`hosts.yml` 中不写 `vars:`。示例的 `hosts.yml`（与实验所用文件相同）：

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
```

机制：一个目录可以作为单一 inventory 源，Ansible 汇总目录内找到的各个源，从顶层往下、按字母序读取。加载 inventory 源时总是跳过名为 `group_vars`、`host_vars` 的目录；这两个目录由默认的 vars 插件 `host_group_vars` 读取（vars 插件是在 inventory 之外为主机和组提供变量的插件），文件中可以使用列表、字典等结构。组目录（如 `group_vars/all/`）下的所有文件按字典序读取，扩展名可以是 `.yml`、`.yaml`、`.json` 或无扩展名。`ansible_host` 是连接用的实际地址，与主机别名（如 `web-01`）分开。

出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:247`（目录作为单一源）；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:269`（读取顺序）；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:267`（在 `ansible.cfg` 中配置目录）；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/inventory/manager.py:54`（`IGNORED_ALWAYS` 含 `^host_vars$`、`^group_vars$`）；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:287`（单独的变量文件比写在 inventory 源中更稳妥）；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:499`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:501`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:519`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:614`。

不这样做会怎样：变量如果写在 `hosts.yml` 的 `vars:` 下，就会和 `group_vars` 中的同名变量处在两个不同的优先级层（"Inventory file or script group vars" 低于 "Inventory group_vars/*"：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:465`）。查一个值时就得翻两处。

### 1.2 group_vars / host_vars 放在 inventory 目录旁

规则：只用 `inventory/group_vars/` 和 `inventory/host_vars/`。

机制：`host_group_vars` 会在 inventory 源旁边查找这两个目录，用 ansible-playbook 时也会在 playbook 目录旁边查找。两处都有同名变量时，以 playbook 旁的为准。`ansible-inventory` 这类命令没有 playbook，只有显式传 `--playbook-dir` 才会读 playbook 旁的目录。

出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:503`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:530`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:531`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:466`（Inventory group_vars/all 低于 Playbook group_vars/all）；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:472`。

实验：在 `playbooks/group_vars/web.yml` 中写 `app_port: 7000`（inventory 中 `web` 组的值是 8080），输出节选：

```text
$ ansible-playbook --vault-password-file ../vault-pass.txt playbooks/show-port.yml
ok: [web-01] => {
    "app_port": 7000
}
$ ansible-inventory --vault-password-file ../vault-pass.txt --host web-01 | grep app_port
    "app_port": 8080,
$ ansible-inventory --vault-password-file ../vault-pass.txt --playbook-dir playbooks --host web-01 | grep app_port
    "app_port": 7000,
```

不这样做会怎样：playbook 实际用的是 7000，审查用的 `ansible-inventory --host` 却显示 8080。inventory 不再是主机参数的唯一来源。

### 1.3 变量优先级

规则：每个变量只在一层定义。所有主机共用的值放在 `group_vars/all/main.yml`；每组的值放在 `group_vars/<组>.yml`；个别主机的例外放在 `host_vars/<主机>.yml`。不在 role 的 `vars/` 中放主机参数，也不在命令行覆盖主机参数。

机制：官方列表按从低到高排列，下面摘出与本示例相关的几层：

| 层（低 → 高） | 出处行 |
| --- | --- |
| role defaults（role 的 `defaults/main.yml`） | `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:464` |
| inventory `group_vars/all` | `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:466` |
| inventory `group_vars/*` | `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:468` |
| inventory `host_vars/*` | `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:471` |
| role vars（role 的 `vars/main.yml`） | `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:477` |
| extra vars（命令行 `-e` 传入的变量） | `.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:484` |

inventory 内部的顺序（从低到高）是：`all` 组 → 父组 → 子组 → 主机。同一级的组按组名字母顺序合并，后合并的组覆盖前面的。

出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:546`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:549`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:551`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:522`。

role defaults 的优先级最低，其他任何来源都能覆盖它：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:32`。

argument_specs 是 role 在 `meta/argument_specs.yml` 中声明的参数规格。其中的 `default` 只用于文档，变量的实际默认值始终来自 role defaults：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:400`。

实验 1：host_vars 高于 group_vars，子组 `web` 高于父组 `all`。

```text
$ ansible-inventory --vault-password-file ../vault-pass.txt --host web-02
{
    "ansible_host": "192.0.2.12",
    "app_db_password": "{{ vault_app_db_password }}",
    "app_log_level": "debug",
    "app_port": 8081,
    "app_version": "1.4.2",
    "vault_app_db_password": "example-secret-value"
}
```

`app_port` 取的是 `host_vars/web-02.yml` 的 8081，而不是 `web` 组的 8080；`app_log_level` 取的是 `web` 组的 `debug`，而不是 `all` 组的 `info`。

实验 2：extra vars 高于 host_vars。

```text
$ ansible web-02 --vault-password-file ../vault-pass.txt -m ansible.builtin.debug -a var=app_port -e app_port=9999
web-02 | SUCCESS => {
    "app_port": "9999"
}
```

结果是字符串 `"9999"`。文档也写明，`key=value` 形式的 extra vars 一律按字符串处理，需要保持类型时应传 JSON 或 YAML 文件（`-e @file`）。出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:413`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:439`。

实验 3：role defaults 最低。role `demo_role` 的 `defaults/main.yml` 为 `demo_role_retries: 3`，inventory 的 `group_vars/all.yml` 为 `demo_role_retries: 7`。先后运行 `ANSIBLE_ROLES_PATH=roles ansible-playbook -i localhost, site.yml` 和 `ANSIBLE_ROLES_PATH=roles ansible-playbook -i inv/ site.yml`，输出节选：

```text
== without inventory var ==
    "msg": "retries=3"
== with inv/group_vars/all.yml ==
    "msg": "retries=7"
```

实验 4：argument_specs 中的 `default` 不生效。`demo_role` 的 `meta/argument_specs.yml` 声明了 `demo_role_retries`（`type: int`，`default: 3`），但没有 `defaults/main.yml`。输出节选：

```text
TASK [demo_role : Validating arguments against arg spec 'main' - Demo role] ****
ok: [localhost]

TASK [demo_role : Show demo_role_retries] **************************************
[ERROR]: Task failed: Finalization of task args for 'ansible.builtin.debug' failed: Error while resolving value for 'msg': 'demo_role_retries' is undefined
```

命令为 `ANSIBLE_ROLES_PATH=roles ansible-playbook -i localhost, site.yml`，退出码 2。

实验 5：隐式 localhost 也会拿到 `group_vars/all`。inventory 中没有定义 localhost 时，Ansible 会自动创建一个，称为隐式 localhost。它不属于任何组，`all` 模式也选不中它，但它会读取 `host_vars` 和 `all` 组的变量。

出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/implicit_localhost.rst:6`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/implicit_localhost.rst:33`。

```text
$ ansible localhost --vault-password-file ../vault-pass.txt -m ansible.builtin.debug -a var=app_log_level
localhost | SUCCESS => {
    "app_log_level": "info"
}
$ ansible all --list-hosts
  hosts (4):
    web-01
    web-02
    api-01
    worker-01
```

由此可知，在 Jenkins 节点本机（localhost）上运行的任务同样会加载 `group_vars/all/vault.yml`，因此同样需要 vault 口令。

不这样做会怎样：

- 同一变量在多层都有定义时，最终值取决于上表中的层级，审查者很难看出来。
- 一台主机如果同时属于 `web` 和 `api`，两组冲突的变量会按字母顺序由 `web` 覆盖 `api`（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/inventory_guide/intro_inventory.rst:551`）。
- 主机参数如果在 argument_specs 里只写了 `default`，没有放进 inventory 或 role defaults，运行时会报未定义（实验 4）。

### 1.4 变量命名

规则：变量名只用小写字母、数字和下划线，并以字母开头。role 自己定义的变量（`defaults/`、`vars/`、`register`、`set_fact`）以 `<role 名>_` 为前缀，例如 `app_deploy_timeout`。inventory 中供多个 role 共用的主机参数以 `app_` 开头（`app_version`、`app_port`），在 role 中声明为 argument_specs 的必填项，不在 role defaults 中给值。加密值以 `vault_` 开头。

机制：

- Ansible 的变量名只能由字母、数字和下划线组成，不能以数字开头，也不能是 Python 关键字或 playbook 关键字。以下划线开头的变量并不是私有变量。
- ansible-lint 是 Ansible 内容的静态检查工具。它的 `var-naming` 规则检查名称是否匹配 `^[a-z_][a-z0-9_]*$`、是否用了保留名或只读的特殊变量；`var-naming[no-role-prefix]` 要求 role 内定义的变量以 `role_name_` 开头，`ansible_` 开头的连接变量除外。
- 对于 `group_vars`、`host_vars`、`vars`、`defaults` 目录下的文件，ansible-lint 都当作 vars 类文件检查。只有文件属于某个 role 时，才检查前缀。

出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:21`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:23`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/rules/var_naming.md:24`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/rules/var_naming.md:45`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/rules/var_naming.py:185`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/rules/var_naming.py:331`；`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/config.py:54`。

实验：在 `group_vars/web.yml` 中加入 `appMaxConn: 100`，在 `roles/app_deploy/defaults/main.yml` 中写入 `retry_count: 3` 和 `app_deploy_timeout: 30`，输出节选：

```text
$ ANSIBLE_VAULT_PASSWORD_FILE=../vault-pass.txt ansible-lint --offline
var-naming[pattern]: Variables names should match ^[a-z_][a-z0-9_]*$ regex. (appMaxConn) (vars: appMaxConn)
inventory/group_vars/web.yml:5:1

var-naming[no-role-prefix]: Variables names from within roles should use app_deploy_ as a prefix. (vars: retry_count)
roles/app_deploy/defaults/main.yml:2:1
```

退出码为 2。`app_deploy_timeout`，以及 `group_vars` 中的 `app_version`、`app_port`，都没有报错。

不这样做会怎样：role 的 `vars/` 和 `defaults/` 会导入 play 作用域，后面的 role 也能读到（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:42`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables.rst:594`）。不加前缀时，一个 role 的 `vars/` 或 `set_fact` 会改变另一个 role 读到的同名变量；inventory 中的同名变量也会悄悄覆盖 role 默认值，因为它的优先级更高（1.3）。

## 2. 敏感信息

### 2.1 ansible-vault：整文件加密，加 vault_ 前缀间接引用

ansible-vault 是 Ansible 自带的对称加密工具，可以加密单个变量，也可以加密整个文件。

规则：`group_vars/all/` 拆成两个文件：`main.yml` 是明文，写全部变量名，敏感值写成 `"{{ vault_xxx }}"`；`vault.yml` 整文件加密，只放 `vault_` 前缀的变量。不使用 `encrypt_string` 单值加密。

```yaml
# inventory/group_vars/all/main.yml
---
app_log_level: info
app_db_password: "{{ vault_app_db_password }}"
```

机制与出处：

- 文档推荐的做法：在组目录中放一个明文文件和一个加密文件，加密文件里的变量加 `vault_` 前缀，明文文件用 Jinja 引用它们。这样变量名仍然能被 `grep` 找到。这两个文件的名字没有限制。（出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:110`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:116`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:121`）
- 整文件加密可以用 `rekey` 轮换口令，单值加密不行。（出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/vault_guide/vault_encrypting_content.rst:184`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/vault_guide/vault_encrypting_content.rst:35`）
- 加密文件只要被加载就会被解密，Ansible 事先无法知道里面有没有需要的内容。因此使用这份 inventory 的每条命令都必须拿到 vault 口令。（出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/vault_guide/vault_encrypting_content.rst:22`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:123`）
- 口令文件的内容读入后会去掉首尾空白（因此文件末尾的换行不影响口令）。文件如果可执行，Ansible 会执行它，并把输出当作口令。（出处：`.reference-cache/ansible/ansible@2.21.5/lib/ansible/parsing/vault/__init__.py:414`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/parsing/vault/__init__.py:367`）

实验：

```text
$ ansible-vault encrypt --vault-password-file ../vault-pass.txt inventory/group_vars/all/vault.yml && head -c 40 inventory/group_vars/all/vault.yml
$ANSIBLE_VAULT;1.1;AES256
62666534633638
```

不这样做会怎样：如果整个 `main.yml` 都加密，变量名就无法搜索，代码审查也看不到改了什么（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/tips_tricks/ansible_tips_tricks.rst:109`）；如果用 `encrypt_string` 单值加密，轮换口令时只能逐个值重新加密。

### 2.2 no_log

`no_log` 是任务（或 play）关键字，用来隐藏该任务的结果和参数。

规则：凡是在参数或结果中用到秘密变量的任务，都加 `no_log: true`，只加在单个任务上，不加在整个 play 上。

出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/logging.rst:16`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/faq.rst:734`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/faq.rst:744`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/faq.rst:751`（建议只用于单个任务）；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/reference_appendices/faq.rst:753`（不影响 `ANSIBLE_DEBUG` 输出）。

实验：`ansible-playbook --vault-password-file ../vault-pass.txt playbooks/use-secret.yml`，同一个 `debug` 任务分别不加和加 `no_log: true`，输出节选：

```text
TASK [Without no_log] **********************************************************
ok: [web-01] => {
    "msg": "password is example-secret-value"
}

TASK [With no_log] *************************************************************
ok: [web-01]
```

限制：`no_log` 只遮住任务结果，遮不住 ansible-core 2.21.5 打印的 `[ERROR]` 行，详见 3.2 的实验。

### 2.3 Jenkins Ansible 插件如何把 vault 口令交给 ansible-playbook

规则：vault 口令在 Jenkins 凭据库中存为一条 “Secret file” 或 “Secret text” 凭据，流水线用 `ansiblePlaybook` 步骤的 `vaultCredentialsId` 引用它；不设 `inventory` 参数，由 `ansible.cfg` 决定 inventory；`vaultTmpPath` 保持默认。

```groovy
ansiblePlaybook(
    playbook: 'playbooks/check-inventory.yml',
    vaultCredentialsId: 'ansible-vault-password'
)
```

机制与出处：

- **支持的凭据类型**：README 写明 vault 凭据可以是 “Secret text” 或 “Secret file”。下拉框只列出 `FileCredentials` 和 `StringCredentials` 两类。（出处：`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/README.md:188`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/workflow/AnsiblePlaybookStep.java:351`）
- **查找凭据**：按 ID 以 `StandardCredentials` 类型查找，所以其他类型的凭据也能被找到。（出处：`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java:313`）
- **生成参数**：只有上面两类凭据会生成 `--vault-password-file` 参数，其他类型直接跳过，不会给出提示。（出处：`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:272`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:276`）
- **写临时文件**：插件在 `vaultTmpPath` 下写口令文件；没有设置 `vaultTmpPath` 时，写在步骤的工作区（`ws`）。文件名形如 `vault*.password`，写入后权限设为 0400。Secret file 是复制文件内容，Secret text 是写入明文口令。0400 不含执行位，所以 ansible 会把它当普通口令文件读取。（出处：`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:271`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:74`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:76`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:93`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:95`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/README.md:97`）
- **传递方式**：命令行上只出现口令文件的路径（`--vault-password-file <路径>`），不出现口令本身。（出处：`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:275`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookInvocation.java:128`）
- **何时删除**：`execute()` 等 ansible-playbook 进程结束后，在 `finally` 中删除口令文件（同时删除 SSH 私钥文件），无论成功或失败都会执行。删除失败时只在日志中打印一条以 `[WARNING] temp file` 开头的警告，不会让构建失败。（出处：`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:312`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:316`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:318`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:108`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/Utils.java:111`）
- **inventory 参数与工作目录**：步骤不设 `inventory` 时，插件不加 `-i`。进程的工作目录是步骤的 `ws`，而 ansible 会从当前工作目录读取 `ansible.cfg`（工作目录全局可写时跳过）。所以这一步必须在 `examples/ansible` 目录下执行，`inventory = inventory/` 才会生效。（出处：`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:93`；`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/CLIRunner.java:47`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:279`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:285`）

不这样做会怎样：选了其他类型的凭据（例如 Username with password）时，插件不会报错，只是不加 `--vault-password-file`。ansible-playbook 随后报 `[ERROR]: Attempting to decrypt but no vault secrets found.`，退出码 4（见 3.1 和 3.2 的实验）。

## 3. 校验与审查

### 3.1 ansible-inventory --graph / --list / --host

机制：`ansible-inventory` 一次只能执行 `--list`、`--host`、`--graph` 三个动作中的一个。`--graph` 只遍历组和主机，加了 `--vars` 才读取变量。`--list` 和 `--host` 输出的是每台主机合并后的变量，变量值中的模板不做渲染。

出处：`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:61`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:62`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:64`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:248`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:251`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/cli/inventory.py:201`。

| 命令 | 需要 vault 口令 | 输出 | 用途 |
| --- | --- | --- | --- |
| `--graph [组]` | 否 | 组与主机的树 | 在 CI 和审查中查看组关系 |
| `--graph --vars` | 是 | 树加变量 | 不用 |
| `--list` | 是 | 全部组、主机和变量，含解密后的明文 | 只在本地排查时使用 |
| `--host <主机>` | 是 | 单台主机合并后的变量，含明文 | 只在本地排查时使用 |

实验（未提供口令）：

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
$ ansible-inventory --list
[ERROR]: Attempting to decrypt but no vault secrets found.
$ ansible-inventory --host web-02
[ERROR]: Attempting to decrypt but no vault secrets found.
$ ansible-inventory --graph --vars
[ERROR]: Attempting to decrypt but no vault secrets found.
$ ansible-inventory --graph no_such_group
[ERROR]: Pattern must be valid group name when using --graph
```

退出码依次为 0、4、4、4、5。提供口令后，`--list --yaml` 的输出中每台主机都带有 `vault_app_db_password: example-secret-value`（节选）：

```text
            web-01:
              ansible_host: 192.0.2.11
              app_db_password: '{{ vault_app_db_password }}'
              app_log_level: debug
              app_port: 8080
              app_version: 1.4.2
              vault_app_db_password: example-secret-value
```

不这样做会怎样：

- 在 CI 中带口令运行 `--list` 或 `--host`，秘密就会以明文进入构建日志。
- `--host` 输出的 `app_db_password` 是未渲染的模板 `{{ vault_app_db_password }}`，不是实际使用的值。要看实际值，用 `ansible <主机> -m ansible.builtin.debug -a var=<变量>`。

### 3.2 用 argument_specs + validate_argument_spec 校验每台主机

规则：

- `roles/app_deploy/meta/argument_specs.yml` 是主机变量规格的唯一来源。
- `playbooks/check-inventory.yml` 读取这份规格，对 `deploy_targets` 的每台主机运行一次 `ansible.builtin.validate_argument_spec`。
- 秘密变量只声明 `type: str` 和 `required: true`，不加 `choices`，也不声明其他类型（实验 4 表明，spec 中的 `no_log: true` 遮不住错误信息）。
- 不使用 2.20 起的 play 级 `validate_argspec`，它仍是 tech preview：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_variables_validation.rst:6`。

```yaml
# playbooks/check-inventory.yml
---
- name: Validate inventory variables of every deploy target
  hosts: deploy_targets
  gather_facts: false
  vars:
    check_inventory_spec_file: "{{ playbook_dir }}/../roles/app_deploy/meta/argument_specs.yml"
  tasks:
    - name: Validate host variables against app_deploy argument spec
      ansible.builtin.validate_argument_spec:
        argument_spec: "{{ (lookup('ansible.builtin.file', check_inventory_spec_file) | from_yaml).argument_specs.main.options }}"
```

机制：

- role 有 argument_specs 时，Ansible 会在 role 执行前自动插入一个校验任务，带 `always` 标签。所以部署时同一份规格还会再校验一次。（出处：`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:297`；`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:315`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/playbook/role/__init__.py:389`）
- `validate_argument_spec` 的 action 插件声明不需要连接主机。它从主机变量中取出 spec 里列出的变量，渲染模板后再校验，所以 vault 引用会被解密。（出处：`.reference-cache/ansible/ansible@2.21.5/lib/ansible/plugins/action/validate_argument_spec.py:16`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/plugins/action/validate_argument_spec.py:32`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/plugins/action/validate_argument_spec.py:34`；`.reference-cache/ansible/ansible@2.21.5/lib/ansible/plugins/action/validate_argument_spec.py:80`）
- 文档示例中，规格就是用 `ansible.builtin.file` lookup 读文件、再经 `from_yaml` 过滤器解析得到的：`.reference-cache/ansible/ansible@2.21.5/lib/ansible/modules/validate_argument_spec.py:74`。

实验 1：全部通过。示例主机的地址都在 192.0.2.0/24，这些地址上没有可连接的主机，因此所有主机都返回 `ok`，正好说明校验过程没有连接主机。输出节选：

```text
$ ansible-playbook --vault-password-file ../vault-pass.txt playbooks/check-inventory.yml
TASK [Validate host variables against app_deploy argument spec] ****************
ok: [web-01]
ok: [api-01]
ok: [worker-01]
ok: [web-02]
```

实验 2：故意改坏三处：删掉 `api.yml` 中的 `app_version`；把 `worker.yml` 中的 `app_port` 改为 `nine-thousand`；在 `host_vars/web-01.yml` 中设 `app_log_level: verbose`。退出码为 2，一次运行列出全部问题（节选）：

```text
fatal: [web-01]: FAILED! => {"argument_errors": ["value of app_log_level must be one of: debug, info, warning, got: verbose"]
fatal: [api-01]: FAILED! => {"argument_errors": ["missing required arguments: app_version"]
fatal: [worker-01]: FAILED! => {"argument_errors": ["argument 'app_port' is of type str and we were unable to convert to int: \"'nine-thousand'\" cannot be converted to an int"]
web-02                     : ok=1    changed=0    unreachable=0    failed=0    skipped=0    rescued=0    ignored=0
```

实验 3：不提供口令时，运行 `ansible-playbook playbooks/check-inventory.yml` 退出码为 4，报错为 `[ERROR]: Attempting to decrypt but no vault secrets found.`。

实验 4：把 `app_db_password` 的规格临时改成 `type: int`（保留 `no_log: true`），再在校验任务上加 `no_log: true`。运行 `ansible-playbook --vault-password-file ../vault-pass.txt playbooks/check-inventory.yml --limit web-01`，退出码 2。任务结果被遮住了，但 `[ERROR]` 行仍然打印了明文（节选）：

```text
[ERROR]: Task failed: Action failed: Validation of arguments failed:
argument 'app_db_password' is of type str and we were unable to convert to int: "'example-secret-value'" cannot be converted to an int
fatal: [web-01]: FAILED! => {"censored": "the output has been hidden due to the fact that 'no_log: true' was specified for this result", "changed": false}
```

插件会把 ansible-playbook 的 stdout 和 stderr 都写进构建日志（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/CLIRunner.java:50`）。

不这样做会怎样：不做这一步检查，变量缺失或类型错误要等部署到对应主机组时才会暴露，而且只暴露被选中的组；秘密变量如果用了 `choices` 或非字符串类型，一旦校验失败，口令就会出现在 Jenkins 控制台里。

### 3.3 ansible-lint

规则：在 `examples/ansible` 目录下运行 `ansible-lint --offline`，不提供 vault 口令。

机制与出处：

- `--offline` 表示不安装 `requirements.yml` 中的依赖，也不刷新 schema：`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/cli.py:495`。
- ansible-lint 读取口令的来源和 ansible-playbook 相同（环境变量、`ansible.cfg`），不支持交互输入：`.reference-cache/ansible/ansible-lint@26.10.1/docs/usage.md:307`、`.reference-cache/ansible/ansible-lint@26.10.1/docs/usage.md:314`。
- 如果给 lint 配置可执行的口令文件，检查不受信任的内容时就可能执行其中的代码：`.reference-cache/ansible/ansible-lint@26.10.1/docs/usage.md:318`。
- `meta/argument_specs.yml` 被识别为 `role-arg-spec` 类型的文件：`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/config.py:67`。

实验：

```text
$ ansible-lint --offline
WARNING  Ignored exception from JinjaRule.matchyaml while processing inventory/group_vars/all/vault.yml (vars): Decryption failed (no vault secrets were found that could decrypt).
WARNING  Ignored exception from VariableNamingRule.matchyaml while processing inventory/group_vars/all/vault.yml (vars): Decryption failed (no vault secrets were found that could decrypt).

Passed: 0 failure(s), 0 warning(s) in 14 files processed of 15 encountered. Last profile that met the validation criteria was 'production'.
```

退出码为 0。提供口令（`ANSIBLE_VAULT_PASSWORD_FILE=../vault-pass.txt`）时，两条 WARNING 消失，结果同样是 `Passed`。命名违规的实验见 1.4。

不这样做会怎样：给 lint 提供口令，只换来对 `vault.yml` 的命名检查，代价是 CI 的 lint 步骤也要拿到口令。`vault_` 变量改由 3.2 的校验和代码审查保证。`main.yml` 引用了不存在的 `vault_` 变量时，校验会报未定义（实验：把引用写成 `vault_app_db_pasword`）：

```text
$ ansible-playbook --vault-password-file ../vault-pass.txt playbooks/check-inventory.yml --limit web-01
[ERROR]: Task failed: 'vault_app_db_pasword' is undefined
fatal: [web-01]: FAILED! => {"changed": false, "msg": "Task failed: 'vault_app_db_pasword' is undefined"}
```

### 3.4 在 CI 中运行

规则：在 `examples/ansible` 目录下，按下面的顺序执行。任何一步失败，构建就失败。

1. `ansible-inventory --graph deploy_targets`：不需要口令，输出组树供审查；组名写错时退出码非 0。
2. `ansible-lint --offline`：不需要口令，检查命名；lint 内部还会对每个 playbook 运行 `ansible-playbook --syntax-check`（`.reference-cache/ansible/ansible-lint@26.10.1/docs/usage.md:342`）。
3. 用 `ansiblePlaybook` 运行 `playbooks/check-inventory.yml`，传入 `vaultCredentialsId`：校验每台主机的变量。

CI 中不运行 `ansible-inventory --list` 和 `--host`（3.1）。

## 常用命令

以下命令都在 `examples/ansible` 目录下执行，`ansible.cfg` 已指定 inventory 和 roles 路径。需要口令的命令写的是 `--ask-vault-pass`（交互输入）；在 CI 中则由插件传入 `--vault-password-file`。

```bash
# 组与主机的树（不需要口令）
ansible-inventory --graph
ansible-inventory --graph deploy_targets

# 单台主机合并后的变量（需要口令，输出含明文，仅本地使用）
ansible-inventory --ask-vault-pass --host web-01

# 某个变量的实际值（模板已渲染，需要口令）
ansible web-01 --ask-vault-pass -m ansible.builtin.debug -a var=app_port

# 校验全部部署目标的变量（需要口令，不连接主机）
ansible-playbook --ask-vault-pass playbooks/check-inventory.yml

# 静态检查（不需要口令）
ansible-lint --offline

# 编辑、查看、轮换加密文件
ansible-vault edit inventory/group_vars/all/vault.yml
ansible-vault view inventory/group_vars/all/vault.yml
ansible-vault rekey inventory/group_vars/all/vault.yml
```
