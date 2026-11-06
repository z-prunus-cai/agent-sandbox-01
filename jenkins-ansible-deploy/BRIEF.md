# Jenkins + Ansible 部署流水线样板

## 1. 范围

一个 git 仓库同时包含 Jenkins 流水线和 Ansible 代码。部署对象是 Ansible inventory 中 `deploy_targets` 下的**主机组**，在 Jenkins 的构建参数页上多选；部署阶段可以勾选跳过；部署前、部署后各可选择 0 到任意个**额外任务**，参数页上只显示所选任务的参数，按参数类型（字符串、整数、布尔、枚举）填写；任务失败时按 `abort`（停止流水线）或 `warn`（标记不稳定后继续）处理。主机和主机组的参数只在 inventory 中管理，参数页不能覆盖。

代码分成**业务**和**基础设施**两部分：每个额外任务是 `business/tasks/` 下自带元数据的一个目录，引擎自动发现，新增业务任务不改引擎、流水线骨架、通用 playbook 和测试框架。同一台 Jenkins 上有**两个 job**，共用一个引擎：只读 job 只能运行声明为只读的任务，没有部署阶段；读写 job 可以运行全部任务和部署。任务可以声明**执行前复核**：引擎先让任务写出要执行的内容并显示出来，有人确认后才执行，执行的必须是复核时显示的那一份。参数页有两种形式：Active Choices 版在同一页上实时显示所选任务的表单；无插件版只用 Jenkins 自带功能，构建开始后用 `input` 步骤弹出表单。两种都接受 YAML 格式的完整计划 `DEPLOY_PLAN`，供 API 和上游 job 触发。额外任务和部署本身都是占位实现：只接收参数、校验、输出将要执行的动作，不连接、不改动目标主机。

不包含：多环境 inventory、动态 inventory、回滚、任务并行执行、参数页的无障碍支持。

## 2. 环境

| 组件 | 版本 |
| --- | --- |
| Jenkins | 2.580.1（Java 21） |
| Jenkins 插件 | `ansible` 635.v34b_48e979c86、`uno-choice`（Active Choices）2.8.11、`pipeline-groovy-lib` 822.vc95f70dea_db_7、`pipeline-utility-steps` 3.810.va_7672d206740、`pipeline-input-step` 560.v56198a_642157、`credentials-binding` 728.v902a_273b_8947、`git` 5.11.0、`workflow-cps` 4395.v738456a_5da_6b_、`script-security` 1436.v1082791135e4、`workflow-aggregator` 608.v67378e9d3db_1；全部 65 个插件及版本见 `tests/jenkins_server/plugins.txt` |
| ansible-core | 2.21.5 |
| ansible-lint | 26.10.1 |
| Python | 3.13；测试用 pytest 9.1.1、playwright 1.64.0、PyYAML 6.0.1、cryptography 50.0.1 |

Jenkinsfile 从 git 加载，运行在 Groovy 沙箱中；引擎作为共享库从同一个 git 仓库动态加载，同样在沙箱中运行。**不需要管理员审批任何脚本，也不需要管理员配置全局共享库。**

在真实 Jenkins 上使用：

1. 安装插件 `workflow-aggregator`（包含 `workflow-cps`、`pipeline-input-step`、`pipeline-groovy-lib` 等 Pipeline 插件）、`git`、`ansible`、`pipeline-utility-steps`、`credentials-binding`、`ssh-credentials`，Active Choices 版另装 `uno-choice`；附件截图中的 Stages 页面来自 `pipeline-graph-view`（可选）；按 job 分配权限需要授权插件，例如 `matrix-auth`。
2. 每个可能运行构建的 Jenkins 节点上安装 git 和 ansible-core 2.21.5（例如 `pip install ansible-core==2.21.5`），`git`、`ansible-playbook` 在 `PATH` 中：引擎的 `node` 不指定标签，任何节点都可能被分到。
3. 两个凭据（作用域 Global，或放在两个 job 所在的文件夹中）：`ansible-vault-password`（类型 Secret text，ansible-vault 口令）和 `deploy-ssh-key`（类型 SSH Username with private key：用户名是在目标主机上登录的用户，私钥对应的公钥放在该用户的 `~/.ssh/authorized_keys` 中）。ID 写在两个 Jenkinsfile 的 `deployPipeline(...)` 调用中；不需要 SSH 时把 `sshCredentialsId` 设为 `null`。
4. 把 `examples/` 的内容放在仓库根目录：Jenkinsfile 的 Script Path、共享库的 `libraryPath`、引擎中的 playbook 路径都相对于仓库根目录。示例的 `vault.yml` 用公开的示例口令加密，换成自己的：在 `business/inventory/group_vars/all/` 下用 `ansible-vault create vault.yml --vault-password-file <口令文件>` 新建（`tests/` 只用于测试本样板，不随 `examples/` 复制；本文其余命令中的 `../../../tests/example-vault-password.txt` 指本样板中的示例口令文件）。
5. 新建两个 Pipeline job（名字任意，下文的输出中是 `deploy-read-write`），都选 “Pipeline script from SCM”，SCM 选 Git，填仓库地址和分支；Script Path 分别填 `pipelines/readonly/Jenkinsfile` 和 `pipelines/readwrite/Jenkinsfile`。只让受信任的分支构建（Branches to build 只填受保护的分支，例如 `*/main`）：流水线加载仓库中的 Groovy 代码、运行仓库中的 playbook，并且持有 vault 口令和 SSH 私钥。读写 job 只给有发布权限的人 Job/Build 权限；能运行一个 job 的人也能确认或中止它的复核（4.15）。
6. 每个 job 点一次 “Build Now”：这次构建只登记构建参数，结果为 NOT_BUILT。之后用 “Build with Parameters”。

运行测试（在 `jenkins-ansible-deploy/` 下）：

```bash
pip install ansible-core==2.21.5 ansible-lint==26.10.1 pytest==9.1.1 playwright==1.64.0 PyYAML==6.0.1 cryptography==50.0.1
python3 -m playwright install chromium         # 不能下载浏览器的机器上改为 export CHROMIUM_PATH=<已安装的 chromium>
python3 -m pytest tests                       # 全部；第一次运行下载 Jenkins 和插件
python3 -m pytest tests -m 'not jenkins'      # 只运行不需要 Jenkins 的测试
```

需要 `java`（21）和 `git` 在 `PATH` 中。测试自己在一个空闲端口上启动只监听 127.0.0.1、没有登录的本地 Jenkins（仅用于测试），结束时停止；Jenkins 2.580.1、插件安装工具 plugin-installation-manager 2.15.0 和 `plugins.txt` 中的插件下载到 `tests/.jenkins/`（git 忽略，校验 sha256），第一次运行需要访问 get.jenkins.io、github.com 和 updates.jenkins.io（插件更新站点）。测试把 `examples/` 复制进一个临时 git 仓库的根目录，按读写 Jenkinsfile 中的 ID 创建凭据（两个 Jenkinsfile 用相同的 ID；vault 口令取自 `tests/example-vault-password.txt`，SSH 私钥随机生成）。多数测试新建自己的 job，结束后删除；只校验计划、互不影响的测试在一个模块内共用一个 job。

## 3. 示例代码

```
examples/                            复制到仓库根目录使用
  pipelines/                         基础设施
    readonly/Jenkinsfile             只读 job：加载引擎，声明模式、参数页形式、凭据 ID
    readwrite/Jenkinsfile            读写 job：同上
  platform/                          基础设施
    jenkins/                         引擎（共享库）
      vars/deployPipeline.groovy     整条流水线
      src/deploy/                    类与枚举，见 3.4
    ansible/
      ansible.cfg                    inventory = ../../business/inventory，roles_path = ../../business/tasks:../../business
      run-task.yml                   运行一个任务（role 名由 Jenkins 传入）
      deploy.yml                     部署（role deploy）
      check-inventory.yml            不连接主机，校验每台主机的 inventory 变量
      check-variable-names.yml       任务参数名与 inventory 变量是否重名
  business/                          业务
    inventory/                       hosts.yml、group_vars/、host_vars/
    deploy/                          部署本身（role deploy）
    tasks/<任务名>/                  一个额外任务：task_a、task_b、task_c、task_d、check_a、check_c
  .ansible-lint
tests/                               pytest：Ansible 测试、通用任务检查、Jenkins 集成测试、浏览器测试
```

### 3.1 一次构建的流程

```
Jenkinsfile        node：检出，得到提交 C；用 library 步骤从仓库的提交 C 加载 platform/jenkins/（4.12）；调用 deployPipeline
Prepare            node：检出提交 C
                   读 business/tasks/*/meta/task.yml 及各任务的 argument_specs.yml、defaults/main.yml、inventory/hosts.yml
                   properties()：禁止并发构建，登记构建参数（下一次构建起生效）
                   用 withCredentials 绑定一次两个凭据，确认存在（4.9）
                   运行 check-variable-names.yml：任务参数名与 inventory 变量是否重名（4.16）
                   stash 全部文件
                   参数尚未登记（第一次构建）：结果 NOT_BUILT，结束
Plan               不分配执行器：读 DEPLOY_PLAN 或表单（无插件版在这里等待 input）
                   校验成执行计划；有错误则一次列出并失败，任何任务都不运行
pre: <id> ...      每个部署前任务一个 stage；每次在一个执行器上 unstash 后 ansible-playbook run-task.yml -e @参数文件 [-l 所选组]
                   需要复核的任务：执行器上以生成模式运行 → 不占执行器等待确认 → 执行器上以执行模式运行（4.15）
Deploy             跳过部署或只读 job 时没有这个 stage；否则 ansible-playbook deploy.yml -l 所选组
post: <id> ...     同部署前
Report             打印结果汇总，归档 deploy-report.json
```

### 3.2 inventory

`business/inventory/hosts.yml` 只写主机与组的关系。`deploy_targets` 的子组就是参数页上的发布对象：

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

变量全部放在 `group_vars/` 与 `host_vars/`：

| 文件 | 内容 |
| --- | --- |
| `group_vars/all/main.yml` | 所有主机共用：`app_base_dir: /opt/apps`、`app_run_user: app`、`app_api_token: "{{ vault_app_api_token }}"` |
| `group_vars/all/vault.yml` | 用 ansible-vault 整个文件加密，只含 `vault_app_api_token`。示例用公开的示例口令 `example-only-password`（`tests/example-vault-password.txt`）加密，内容也是示例值；真实项目要换口令和内容 |
| `group_vars/web.yml` 等 | 每组的应用参数：`app_name`、`app_version`、`app_port`、`app_max_connections` |
| `host_vars/<主机>.yml` | `ansible_host`（RFC 5737 文档地址 192.0.2.11–15）；`web-02.yml` 另把 `app_max_connections` 覆盖为 200 |

版本号 `app_version` 也是组变量：发布新版本 = 在 git 中修改 `group_vars/<组>.yml` 并提交，Jenkins 部署 git 中的状态。inventory 的目录与变量分层、敏感信息、校验方法见 [attachments/inventory-practices.md](attachments/inventory-practices.md)。

### 3.3 部署与额外任务

`platform/ansible/deploy.yml` 对 `deploy_targets` 运行 role `deploy`（`business/deploy/`），Jenkins 用 `--limit 'web:api'` 把范围缩小到所选的组。`business/deploy/meta/argument_specs.yml` 声明部署需要的主机变量及类型（`app_port: int, required` 等），Ansible 在 role 的第一个任务之前自动按它校验（`ansible/ansible@2.21.5` 的 `lib/ansible/playbook/role/__init__.py` 的 `Role._prepend_validation_task`）。

每个额外任务是 `business/tasks/` 下的一个 role：

| 任务 | 读写 | 运行位置 | 参数（类型，默认值） |
| --- | --- | --- | --- |
| `task_a` | write | 所选主机组 | `task_a_message`（str，必填）、`task_a_repeat`（int，1，范围 1–5）、`task_a_delay_seconds`（int，0） |
| `task_b` | read | Jenkins 节点本机 | `task_b_path`（str，`business/inventory/hosts.yml`，相对样板根目录，不能是绝对路径或含 `..`）、`task_b_min_bytes`（int，1） |
| `task_c` | write | 所选主机组 | `task_c_mode`（str，choices `plan`/`apply`，`plan`）、`task_c_verbose`（bool，false）、`task_c_label`（str，空） |
| `task_d` | write，执行前复核 | Jenkins 节点本机 | `task_d_items`（str，必填）：逗号分隔的变更项 |
| `check_c` | read | 所选主机组 | 无参数 |
| `check_a` | read | 所选主机组 | `check_a_min_connections`（int，必填）：每台主机的 `app_max_connections` 至少为多少 |

一个任务目录的四个文件，以 `task_a` 为例：

```yaml
# business/tasks/task_a/meta/task.yml：Jenkins 需要、Ansible 不需要的元数据
description: 在所选主机组上运行的示例任务
phases: [pre]            # 允许的阶段：pre / post
access: write            # read：只读 job 也能运行；write：只有读写 job 能运行
menu: main               # main：参数页上一直显示；more：勾选 “显示更多” 后才出现
run_on: targets          # targets：所选主机组；controller：Jenkins 节点本机
policy: abort            # 默认失败策略：abort / warn
timeout_minutes: 5       # 1 到 240；超时按任务失败处理
order: 10                # 同一阶段的默认执行顺序，从小到大；任务之间不能重复
```

需要复核的任务另加：

```yaml
review:
  timeout_minutes: 30    # 等待确认的时间，1 到 240；超时则构建 ABORTED，任务不执行
```

```yaml
# business/tasks/task_a/meta/argument_specs.yml：参数名、类型、必填、默认值、说明
argument_specs:
  main:
    short_description: 示例任务 A：在所选主机组的每台主机上运行
    options:
      task_a_message:
        type: str
        required: true
        description: 要输出的文字
      task_a_repeat:
        type: int
        default: 1
        description: 输出次数，1 到 5
      task_a_delay_seconds:
        type: int
        default: 0
        description: 开始前等待的秒数，用于演示超时
```

```yaml
# business/tasks/task_a/defaults/main.yml：运行时真正生效的默认值，与上面的 default 保持一致
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

`platform/ansible/run-task.yml` 运行任意一个任务，role 名和主机由引擎传入：

```yaml
- name: "Task {{ task_role }}"
  hosts: "{{ task_hosts }}"
  gather_facts: false
  tasks:
    - name: "Run {{ task_role }}"
      ansible.builtin.include_role:
        name: "{{ task_role }}"
```

不经过 Jenkins 也可以直接运行（在 `examples/platform/ansible` 下）：

```bash
echo '{"task_role": "task_a", "task_hosts": "deploy_targets", "task_a_message": "hello", "task_a_repeat": 2}' > /tmp/vars.json
ansible-playbook run-task.yml --limit api --vault-password-file ../../../tests/example-vault-password.txt -e @/tmp/vars.json
```

输出节选：

```
    "msg": "api-01: hello (1/2)"
    "msg": "api-01: hello (2/2)"
```

### 3.4 引擎

引擎是 `platform/jenkins/` 下的 Jenkins 共享库：`vars/deployPipeline.groovy` 是整条流水线，`src/deploy/` 下是类。只调用流水线步骤的代码在 `vars/deployPipeline.groovy`、`ParameterPage`、`TaskRunner` 中，通过传入的 `script` 对象调用步骤；其余类只做计算，方法都标 `@NonCPS`（4.13）。

| 文件（`src/deploy/`） | 职责 |
| --- | --- |
| `Catalog` | 由各任务目录的三个 YAML 和 `hosts.yml` 得到任务定义与主机组，检查元数据、参数声明和默认值 |
| `PlanParser` | 把 `DEPLOY_PLAN`（或表单整理成的同样结构）校验成执行计划 `Plan` |
| `Selection`、`ActiveChoicesForm` | 把两种参数页提交的值整理成 `DEPLOY_PLAN` 的结构；生成 Active Choices 的脚本 |
| `Values` | 按参数类型检查一个值（默认值、`DEPLOY_PLAN`、表单共用） |
| `ParameterPage` | 两种参数页的构建参数定义；无插件版的 `input` 表单 |
| `TaskRunner` | 运行任务、复核、部署，生成报告 |
| `TaskResult` | 报告中的一行 |
| `TaskDefinition`、`ParameterDefinition`、`PlannedTask`、`Plan` | 任务与参数的定义；计划中的一个任务；整个计划 |
| `Labels` | 按外部拼写查找枚举常量，找不到时记录问题 |
| `FormField` | 表单字段的键：Active Choices 版为 `任务.字段`，无插件版为 `阶段.任务.字段`；除参数外的两个字段 `policy`、`order` |
| `AnsibleNames` | Ansible 保留、且带任务名前缀也可能撞上的名字（含 `_` 的关键字和特殊变量）；`test_engine_refuses_every_name_ansible_reserves_that_a_task_prefix_can_produce` 保证它覆盖 ansible-core 认定的这些名字 |
| `Limits` | 输入上限：`DEPLOY_PLAN` 20000 个字符、计划中 50 个任务、字符串参数 1000 个字符、`id` 64 个字符、超时 240 分钟、无插件版表单等待 30 分钟 |
| 枚举 | `Phase`、`TaskAccess`、`TaskMenu`、`AccessMode`、`FailurePolicy`、`RunLocation`、`ParameterType`、`ParameterForm`、`RequestKind`、`ReviewStep`、`TaskOutcome`、`EngineVariable`；外部拼写（YAML 中的值、构建参数名、报告中的结果）写在每个常量上，解析时遇到未知值报错 |

### 3.5 Jenkinsfile

读写 job（`pipelines/readwrite/Jenkinsfile`）：

```groovy
// The read-write job: every task and the deployment.
// The engine is loaded from the commit this file came from, so one build never mixes two revisions.
String commit
node {
    commit = checkout(scm).GIT_COMMIT
}
library(identifier: "deploy-engine@${commit}",
        retriever: modernSCM(scm: [$class: 'GitSCMSource', remote: scm.userRemoteConfigs[0].url], libraryPath: 'platform/jenkins/'))
deployPipeline(commit: commit, mode: 'read-write', form: 'active-choices',
               vaultCredentialsId: 'ansible-vault-password', sshCredentialsId: 'deploy-ssh-key')
```

只读 job（`pipelines/readonly/Jenkinsfile`）只有 `mode: 'read-only'` 和第一行注释不同。`form` 可以是 `'active-choices'` 或 `'basic'`（无插件版）。仓库需要凭据才能检出时，`scm.userRemoteConfigs[0]` 还有 `credentialsId`，在 `GitSCMSource` 中另加 `credentialsId: scm.userRemoteConfigs[0].credentialsId`。

流水线是脚本式（Scripted）：构建参数要按运行时读到的任务目录和 inventory 生成，每个任务一个 stage，等待确认时要离开执行器，这些都由 `deployPipeline` 中的 `node`、`stage` 直接控制。不要在 job 配置中再设置参数或“禁止并发构建”：`properties` 步骤会在下一次运行时移除自己上次设置的 job 属性，同类属性会互相覆盖（`jenkinsci/workflow-multibranch-plugin@842.v3a_b_59b_57b_e6e` 的 `src/main/java/org/jenkinsci/plugins/workflow/multibranch/JobPropertyStep.java` 的 `Execution.run`）。

### 3.6 构建参数

Active Choices 版：

| 参数 | 类型 | 作用 |
| --- | --- | --- |
| `TARGET_GROUPS` | Active Choices 勾选框 | 主机组，选项 `web (web-01, web-02)` 等来自 inventory |
| `SKIP_DEPLOY` | 布尔，只有读写 job 有 | 跳过部署，部署前后任务照常执行 |
| `PRE_TASKS` / `POST_TASKS` | Active Choices 勾选框，带搜索框 | 该阶段允许、本 job 的模式允许、`menu: main` 的任务，按 `order` 排列 |
| `PRE_SHOW_MORE` / `POST_SHOW_MORE` | 布尔，该阶段有 `menu: more` 的任务时才有 | 勾选后出现下一行的列表 |
| `PRE_MORE_TASKS` / `POST_MORE_TASKS` | Active Choices 联动勾选框，带搜索框 | `menu: more` 的任务；页面上没勾 `*_SHOW_MORE` 时为空，取消勾选会清空这里已选的任务。经 API 触发时可以直接传值，引擎不看 `*_SHOW_MORE` |
| `PRE_TASK_OPTIONS` / `POST_TASK_OPTIONS` | Active Choices 动态 HTML | 随两个列表的勾选实时生成：每个所选任务一组，含执行顺序数字框（默认为 `order`，数字小的先执行，相同时按 `order`）、失败策略下拉框和各参数输入框，预填 role 的默认值；整数为数字框，布尔和枚举为下拉框，必填参数名后标 “(required)” |
| `DEPLOY_PLAN` | 多行文本 | 非空时忽略上面所有参数 |

无插件版：每个主机组一个勾选框 `TARGET_<组>`，`SKIP_DEPLOY`（只有读写 job），每个阶段的每个任务一个勾选框 `PRE_<任务>` / `POST_<任务>`，`EDIT_TASK_OPTIONS`（默认勾选），`DEPLOY_PLAN`。构建开始后，若勾选了任务且 `EDIT_TASK_OPTIONS` 为真，流水线暂停并弹出只含所选任务的表单（字段名形如 `pre.task_a.task_a_repeat`），点 “Start” 后继续，点 “Abort” 则构建 ABORTED（`test_abort_in_the_form_aborts_the_build`）。等待包在 30 分钟的 `timeout` 中，期间不占执行器（`test_waiting_for_the_form_holds_no_executor`）。

`DEPLOY_PLAN` 格式（两种参数页相同；只读 job 不接受 `skip_deploy`）：

```yaml
targets: [web, api]
skip_deploy: false
pre:
  - task_b                                                    # 全部默认值
  - {name: task_a, policy: warn, params: {task_a_message: hello}}
post:
  - {name: check_c, id: check_c_after}                        # 同一阶段运行同一任务多次时用 id 区分
  - {name: check_a, timeout_minutes: 3, params: {check_a_min_connections: 50}}
```

字符串参数的值在 YAML 中加引号：未加引号的 `yes`、`2026-01-01`、`1.0` 会被解析成布尔、日期、小数，引擎拒绝这些值。

从 API 或上游 job 触发：向 job 的 `buildWithParameters` 接口 POST，只传 `DEPLOY_PLAN` 一个参数。集成测试大多这样触发构建（`tests/jenkins_server/client.py` 的 `Jenkins.start_build`）；少数用例直接赋值 Active Choices 参数来模拟页面提交。Active Choices 插件说明这些参数只供人在页面上填写，通过 API 或脚本触发时不保证可用（`jenkinsci/active-choices-plugin@2.8.11` 的 `README.md` 的 “Known Limitations” 一节），所以不要用 API 直接传 `TARGET_GROUPS` 等参数。

参数页两种形式的界面细节与提交值的格式见 [attachments/parameter-page.md](attachments/parameter-page.md)。

### 3.7 输出

用下面的 `DEPLOY_PLAN` 运行读写 job（在参数页上做同样的选择，结果相同）：

```yaml
targets: [web, api]
pre:
  - {name: task_a, params: {task_a_message: hello}}
  - task_b
post:
  - {name: check_a, policy: warn, params: {check_a_min_connections: 150}}
```

构建结果 UNSTABLE，控制台节选（`<工作区>` 代表 job 的工作区目录，`<随机数>` 是插件生成的临时文件名）：

```
[Pipeline] { (Prepare)
[deploy] credentials found
[deploy-read-write] $ ansible-playbook platform/ansible/check-variable-names.yml --private-key <工作区>/ssh<随机数>.key -u deploy --vault-password-file <工作区>/vault<随机数>.password -e @.deploy-run/variable-names.json
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
[deploy-read-write] $ ansible-playbook platform/ansible/run-task.yml -l web:api --private-key <工作区>/ssh<随机数>.key -u deploy --vault-password-file <工作区>/vault<随机数>.password -e @.deploy-run/pre-task_a.json
    "msg": "web-01: hello (1/1)"
    "msg": "web-02: hello (1/1)"
    "msg": "api-01: hello (1/1)"
[Pipeline] { (pre: task_b)
[deploy-read-write] $ ansible-playbook platform/ansible/run-task.yml --private-key <工作区>/ssh<随机数>.key -u deploy --vault-password-file <工作区>/vault<随机数>.password -e @.deploy-run/pre-task_b.json
    "msg": "business/inventory/hosts.yml: 421 bytes"
[Pipeline] { (Deploy)
[deploy-read-write] $ ansible-playbook platform/ansible/deploy.yml -l web:api --private-key <工作区>/ssh<随机数>.key -u deploy --vault-password-file <工作区>/vault<随机数>.password
    "msg": "deploy web 2.3.1 to web-02 (192.0.2.12) dir=/opt/apps/web port=8080 max_connections=200 user=app"
    "msg": "deploy web 2.3.1 to web-01 (192.0.2.11) dir=/opt/apps/web port=8080 max_connections=100 user=app"
    "msg": "deploy api 1.8.0 to api-01 (192.0.2.13) dir=/opt/apps/api port=9000 max_connections=300 user=app"
[Pipeline] { (post: check_a)
[deploy-read-write] $ ansible-playbook platform/ansible/run-task.yml -l web:api --private-key <工作区>/ssh<随机数>.key -u deploy --vault-password-file <工作区>/vault<随机数>.password -e @.deploy-run/post-check_a.json
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

`task_b` 运行在 Jenkins 节点本机，命令里没有 `-l`；`web-02` 的 `max_connections=200` 来自 `host_vars/web-02.yml`；同一个 play 中各主机的输出顺序每次可能不同。`deploy-report.json` 中每一项含 `id`、`name`、`phase`、`policy`、`params`、`outcome`（`passed`、`failed-stopped`、`failed-continued`、`timed-out-stopped`、`timed-out-continued`、`aborted`、`error`）、`seconds`、`message`（失败原因）和 `reviewer`（复核任务的确认人，其余为 `null`）。

### 3.8 新增一个业务任务

只新建一个目录 `business/tasks/<任务名>/`，不改 `platform/`、`pipelines/`、`tests/`。目录名就是任务名，只能用小写字母、数字和 `_`，以字母开头（它也是 role 名、参数名前缀、表单字段键的第一段和默认的 `id`）：

1. `meta/task.yml`：`description`（不用 U+FF01–U+FF5E 的全角字符和 `@ = + "`，原因见第 5 节；`、` `。` 不在此列）、`phases`、`access`、`menu`、`run_on`、`policy`、`timeout_minutes`、`order`（与其他任务不同），需要复核时加 `review`。
2. `meta/argument_specs.yml`：参数名以 `<任务名>_` 开头，不能与 inventory 变量、Ansible 保留的变量名（ansible-core 的 `ansible.vars.reserved.is_reserved_name` 认定的名字，以及 `inventory_hostname`、`groups`、`hostvars`、`playbook_dir` 等特殊变量）、引擎变量（`task_role`、`task_hosts`、`task_review_mode`、`task_review_file`、`task_parameter_names`）重名；类型只用 `str`、`int`、`bool`，不写 `type` 时为 `str`；`choices` 只用于 `str`；非必填参数写 `default`；没有参数时写 `options: {}`（见 `check_c`）。
3. `defaults/main.yml`：每个非必填参数的默认值，与 `default` 相同，用字面值（不含 `{{`）。
4. `tasks/main.yml`：任务本身；条件不满足时用 `assert` / `fail` 让它失败。需要复核时按 3.10 写两种模式。
5. 运行 `python3 -m pytest tests -m 'not jenkins'`：`tests/test_tasks.py` 遍历 `business/tasks/` 下的每个目录检查上面全部约定，新任务自动被检查。引擎在每次构建的 `Prepare` 中检查其中大部分，不符合时构建失败，任何任务都不运行（`Catalog.parse`，`test_problem_in_a_task_directory_or_the_inventory_stops_the_build_before_any_task_runs` 覆盖其中的主要情形）：目录名、`tasks/main.yml` 与 `meta/argument_specs.yml` 存在、元数据的字段与取值、`order` 不重复、参数声明的字段、参数类型与 `choices`、参数名前缀、引擎变量、`ansible_*` 和 Ansible 保留的名字、`defaults/main.yml` 只设已声明的参数、默认值的类型、每个主机组至少一台主机，以及与 inventory 变量重名（4.16）。说明文字的字符、`default` 与 `defaults/main.yml` 一致只由测试检查。
6. 提交后先运行一次构建（任意参数），参数页从下一次构建起出现新任务。

只提交一个新任务目录的文件，不改其他文件，构建就能运行这个任务：由 `test_new_task_directory_runs_without_any_platform_change` 保证。

### 3.9 新增一个主机或主机组

主机组：在 `hosts.yml` 的 `deploy_targets.children` 下加组和主机，加 `business/inventory/group_vars/<组>.yml`（role `deploy` 需要的全部变量）和每台主机的 `business/inventory/host_vars/<主机>.yml`。然后在 `examples/platform/ansible` 下运行 `ansible-playbook check-inventory.yml --vault-password-file <口令文件>`，确认每台主机的变量齐全、类型正确。参数页同样从下一次构建起出现新组。

变量的规则（理由见 [attachments/inventory-practices.md](attachments/inventory-practices.md)）：
- 变量只放在 `business/inventory/group_vars/`、`business/inventory/host_vars/`；playbook 旁不建 `group_vars/`、`host_vars/`。
- 应用参数以 `app_` 开头，加密值以 `vault_` 开头；敏感值写进 `group_vars/all/vault.yml`，在 `main.yml` 中用 `"{{ vault_xxx }}"` 引用；role `deploy` 的 argument_specs 中，敏感变量只声明 `type: str` 和 `required: true`；使用敏感值的任务加 `no_log: true`。
- 不要使用任务参数的名字（`<任务名>_...`）：引擎在每次构建的 `Prepare` 中检查，重名时构建失败（4.16）。
- `ansible-inventory --list` / `--host` 会输出解密后的明文，不在 CI 中运行。
- 修改加密文件和更换口令（在 `examples/business/inventory` 下）：

```bash
ansible-vault edit group_vars/all/vault.yml --vault-password-file <口令文件>
ansible-vault rekey group_vars/all/vault.yml --vault-password-file <旧口令文件> --new-vault-password-file <新口令文件>
```

更换口令后同时更新 Jenkins 凭据 `ansible-vault-password`。

### 3.10 执行前复核

在 `meta/task.yml` 中加 `review: {timeout_minutes: N}` 的任务，引擎分两次运行它，每次多传两个变量：

| 变量 | 第一次 | 第二次 |
| --- | --- | --- |
| `task_review_mode` | `generate`：把要执行的内容写进复核文件，不改动任何东西 | `execute`：读复核文件，按其中的内容执行 |
| `task_review_file` | 引擎指定的复核文件的绝对路径（Jenkins 节点上） | 同一个文件，内容是确认时显示的那一份 |

`task_d` 的实现：

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

执行模式只读复核文件，不再用参数重新生成：参数和复核文件不一致时，执行的是复核文件（`test_reviewed_task_executes_the_review_file_not_its_parameters`）。复核文件在 Jenkins 节点上；`run_on: targets` 的任务在生成模式下写文件的那一步加 `delegate_to: localhost` 和 `run_once: true`，在执行模式下用 `lookup('ansible.builtin.file', task_review_file)` 读它（lookup 在 Jenkins 节点上执行），不要用在目标主机上执行的 `slurp`。示例中只有 `task_d`（运行在 Jenkins 节点本机）演示复核，这种写法没有示例和测试。

两次运行之间，构建暂停在一个 `input` 上：提示中有复核文件的 sha256 和全文，按钮是 “Run” 和 “Abort”。确认后控制台打印 `[deploy] task_d confirmed by <用户>`，报告中该任务的 `reviewer` 是确认人；中止或超时则构建 ABORTED，任务不执行，报告中该任务为 `aborted`、`not confirmed`。复核文件归档为构建产物 `.deploy-run/review/<阶段>-<id>.txt`。界面流程和截图见 [attachments/review-before-run.md](attachments/review-before-run.md)。

## 4. 要点

### 4.1 abort / warn 只能在流水线里实现

规则：每个任务单独调用一次 `ansiblePlaybook`，用 `try/catch` 捕获失败，再按策略调用 `error`（FAILURE，后续 stage 不再运行）或 `unstable`（UNSTABLE，继续）。实现见 `TaskRunner` 的 `attempt` 和 `settle`。

机制：Ansible 插件在 playbook 返回非 0 时抛出 `hudson.AbortException("Ansible playbook execution failed")`，没有“失败也继续”的选项，也拿不到退出码（`jenkinsci/ansible-plugin@635.v34b_48e979c86` 的 `src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java` 的 `perform`）。所以参数校验失败、断言失败、主机不可达都表现为同一个异常。

测试：`test_warn_failure_marks_build_unstable_and_continues`（构建 UNSTABLE，随后照常运行 `deploy.yml`）、`test_abort_failure_stops_before_deploy_and_later_tasks`（构建 FAILURE，`run-task.yml` 只运行一次，报告中该任务为 `failed-stopped`）。

不这样做：在一次 `ansiblePlaybook` 中运行所有任务，就只有一个结果，无法逐个任务决定阻塞或非阻塞。

### 4.2 超时按任务失败处理，手动中止不能被当成失败

规则：每个任务包在 `timeout(time: timeout_minutes)` 中；捕获到 `InterruptedException` 时，已耗时达到超时才按任务失败处理（`timed-out-stopped` / `timed-out-continued`），否则把该任务记为 `aborted` 并重新抛出。

机制：`timeout` 中止 `ansiblePlaybook` 与手动中止构建时，从插件抛出的都是普通的 `java.lang.InterruptedException`，不带中止原因；能区分原因的 `FlowInterruptedException.getCauses()` 在沙箱中需要管理员审批。所以引擎只能用已耗时区分（`TaskRunner` 的 `attempt` 中 `catch (InterruptedException e)` 一段）。已知缺口：超时之后、`timeout` 的宽限期内手动中止，也会被算作超时。

测试：`test_timeout_counts_as_task_failure_and_follows_the_policy`（`timeout_minutes: 1`、任务等待 90 秒、策略 warn：控制台输出 `[deploy] task task_a failed: timed out after 1 min (warn: continuing)`，构建 UNSTABLE 并继续部署）；`test_manual_abort_during_a_task_aborts_the_build_instead_of_continuing`（任务运行中点中止：构建 ABORTED，不继续部署，报告中该任务为 `aborted`）。

不这样做：用 `catchError(catchInterruptions: false)` 包住 `timeout { ansiblePlaybook(...) }`，手动中止也会被捕获，后面的步骤继续执行。这样写时的现象（流水线在沙箱中运行，`pause.yml` 暂停 120 秒，第 5 秒手动中止构建）：

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

输出（控制台节选）：

```
Pausing for 120 seconds
Aborted by unknown
java.lang.InterruptedException
	at org.jenkinsci.plugins.ansible.CLIRunner.execute(CLIRunner.java:52)
Setting overall build result to UNSTABLE
AFTER-WRAPPER elapsedMs=6036 currentResult=UNSTABLE
Finished: ABORTED
```

`catchInterruptions: false` 只对 `FlowInterruptedException` 生效（`jenkinsci/workflow-basic-steps-plugin@1098.v808b_fd7f8cf4` 的 `src/main/java/org/jenkinsci/plugins/workflow/steps/CatchErrorStep.java` 的 `Callback.onFailure`），而 `ansiblePlaybook` 抛出的不是它。

### 4.3 参数用 JSON 文件传给 Ansible

规则：任务参数写入 `.deploy-run/<阶段>-<id>.json`，用 `extras: '-e @文件'` 传入，不用 `ansiblePlaybook` 的 `extraVars`。

机制：`extraVars` 的每个值先被转成字符串（`jenkinsci/ansible-plugin@635.v34b_48e979c86` 的 `src/main/java/org/jenkinsci/plugins/ansible/workflow/AnsiblePlaybookStep.java` 的 `AnsiblePlaybookExecution.convertExtraVars`），除 `true` / `false` 外都以 `-e key=value` 传入，列表等非标量值被跳过（`jenkinsci/ansible-plugin@635.v34b_48e979c86` 的 `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` 的 `appendExtraVars`）。`key=value` 形式的值在 Ansible 中是字符串。

现象：在 `examples/platform/ansible` 下，同一任务分别用 `-e task_a_repeat=2` 和 `-e '{"task_a_repeat": 2, ...}'` 运行（两条命令都另加 `--vault-password-file ../../../tests/example-vault-password.txt`）时的输出：

```
$ ansible-playbook run-task.yml --limit api -e task_role=task_a -e task_hosts=deploy_targets -e task_a_message=x -e task_a_repeat=2
fatal: [api-01]: FAILED! => {"changed": false, "msg": "Task failed: Error rendering expression: '>=' not supported between instances of '_AnsibleTaggedStr' and 'int'"}
（退出码 2）

$ ansible-playbook run-task.yml --limit api -e '{"task_role":"task_a","task_hosts":"deploy_targets","task_a_message":"x","task_a_repeat":2}'
    "msg": "api-01: x (1/2)"
    "msg": "api-01: x (2/2)"
（退出码 0）
```

`"2"` 通过了 argument_specs 的 `int` 校验（校验只检查能否转换），但变量本身仍是字符串，比较时出错。

### 4.4 只接受登记过的任务名和参数名，拒绝模板语法，限制长度

规则：计划中的任务名必须是 `business/tasks/` 下的目录，参数名必须在该任务的 `argument_specs.yml` 中；字符串参数不能含 `{{`、`{%`、`{#`，不能以 `#jinja2:` 开头，不能含控制字符和格式字符（制表符除外），不超过 1000 个字符；参数名以任务名为前缀，不是引擎变量，不以 `ansible_` 开头，不是 Ansible 保留的名字（`AnsibleNames`），不与 inventory 变量重名（4.16）；这些都由引擎在运行任务之前检查。所有上限在 `Limits` 中定义一次。

机制：Ansible 会渲染 extra vars 中的 Jinja 表达式，而 extra vars 的优先级最高。以 `#jinja2:` 开头的字符串即使不含 `{{` 也按模板处理，第一行可以改用别的定界符（`ansible/ansible@2.21.5` 的 `lib/ansible/_internal/_templating/_jinja_bits.py` 中的 `JINJA2_OVERRIDE` 与 `is_possibly_template`）。控制字符和格式字符（如换行、`ESC`、`U+202E`）能在控制台和复核提示中伪造一行输出，或让显示的文字与实际字节不同。不加限制时，能触发构建的人可以在 Jenkins 节点上执行命令，或传入 `ansible_connection` 之类的变量改变连接方式；参数名与 inventory 变量重名时，会静默覆盖 inventory 中的值。

现象：不经过引擎、直接把含模板的值交给 Ansible 时（在 `examples/platform/ansible` 下，另加 `--vault-password-file ../../../tests/example-vault-password.txt`）：

```
$ cat vars.json
{"task_role":"task_a","task_hosts":"deploy_targets","task_a_message":"{{ lookup(\"pipe\", \"echo INJECTED\") }}"}
$ ansible-playbook run-task.yml --limit api -e @vars.json
    "msg": "api-01: INJECTED (1/1)"
```

经过引擎时这些值被拒绝，构建 FAILURE 且不运行任何任务：由 `tests/test_pipeline_validation.py` 中 `test_invalid_plan_fails_before_any_task_runs` 的 `template-expression`、`template-statement`、`template-comment`、`template-override-header`、`control-character`、`format-character`、`undeclared-param`、`string-too-long`、`id-too-long`、`too-many-tasks`、`plan-too-long` 用例保证；正好等于上限的值能通过（`test_values_at_their_limit_are_accepted`）。

### 4.5 所有校验在运行任务之前完成，并一次列出

规则：`Catalog.parse`、`PlanParser.parse`、`Values.check` 把问题收集到列表，最后调用一次 `error`。可选字段用 `== null` 判断是否提供：Groovy 中 `0`、`''`、`[]`、`[:]` 为假，用 `?:` 会把它们当成未提供而跳过类型检查。

覆盖的问题（`test_invalid_plan_fails_before_any_task_runs`）：主机组未知、未选（部署需要，或跳过部署时有任务运行在所选主机组上）、不是列表；`skip_deploy` 不是布尔；阶段不是列表；条目不是任务名或映射；未知任务；任务不能用于该阶段；未知字段；未知或空的策略；必填参数缺失或为空白；`params` 不是映射；未声明的参数；整数、枚举、布尔、字符串类型不符；三种模板语法和 `#jinja2:` 开头；控制字符和格式字符；同一阶段 `id` 重复、`id` 非法、为空或过长；`timeout_minutes` 不在 1 到 240 之间；字符串过长；任务过多；计划的未知字段；`DEPLOY_PLAN` 不是映射、不是合法 YAML 或过长。表单提交的值另有：同一字段出现两次（值中含 `,@@`）、字段属于未勾选的任务、字段不是该任务声明的参数、整数超出 64 位、执行顺序不是整数、模板语法、`*_TASK_OPTIONS` 超过 `DEPLOY_PLAN` 的长度上限（`test_invalid_form_values_fail_before_any_task_runs`）；只读 job 另有：写任务、`skip_deploy` 字段、表单中勾选的写任务（4.14）。

测试：`test_invalid_plan_lists_all_problems_at_once`。用 `{targets: [nope], pre: [no_such_task, {name: task_a, policy: ask}]}` 运行时的输出（控制台节选）：

```
ERROR: [deploy] the plan has problems:
  - unknown groups [nope], expected [web, api, worker]
  - pre[0]: unknown task 'no_such_task', expected one of [task_a, task_b, task_c, check_c, check_a, task_d]
  - pre[1]: policy 'ask' is not one of [abort, warn]
  - pre[1]: task task_a needs parameter task_a_message
Finished: FAILURE
```

不这样做：参数写错要到部署到一半才发现。Ansible 的 argument_specs 校验仍然保留，作为第二道检查。

### 4.6 参数的类型、说明来自 argument_specs，运行时默认值来自 defaults/main.yml

规则：不在 Jenkins 中重复声明任务参数。`deployPipeline` 的 `readCatalog` 用 `readYaml` 读取每个任务的 `meta/argument_specs.yml`（名称、类型、必填、choices、说明）和 `defaults/main.yml`（默认值），交给 `Catalog.parse`。argument_specs 中也写 `default`，与 `defaults/main.yml` 相同。

机制：role 开始执行前，Ansible 按 `argument_specs.yml` 自动插入一个 `validate_argument_spec` 任务（`ansible/ansible@2.21.5` 的 `lib/ansible/playbook/role/__init__.py` 的 `Role._prepend_validation_task`）。argument_specs 中的 `default` 用于文档，文档要求它与代码中的默认值一致，而实际默认值总是取自 role 的 defaults（`ansible/ansible-documentation@b16dcb8` 的 `docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst` 的 “Specification format” 一节）。

测试：`test_documented_defaults_equal_role_defaults_and_cover_every_optional_option`；`test_role_defaults_apply_when_jenkins_passes_no_value`；参数页上的预填值即 defaults（`test_selecting_a_task_shows_only_its_fields_prefilled_with_role_defaults`）。

### 4.7 第一次运行只登记参数；`params` 必须在 `properties()` 之前读取

规则：`deployPipeline` 开头先复制 `params`（`Map requested = [:] + params`），再调用 `properties()`。参数尚未登记时（`requested` 中没有 `DEPLOY_PLAN`，两种参数页都登记它；`RequestKind.of`）构建结果设为 NOT_BUILT，不运行任何任务。

机制：`params` 每次读取时，除了本次构建提交的值，还会用 job **当前**的参数定义补上默认值（`jenkinsci/workflow-cps-plugin@4395.v738456a_5da_6b_` 的 `plugin/src/main/java/org/jenkinsci/plugins/workflow/cps/ParamsVariable.java` 的 `getValue`）。`properties()` 执行后，第一次运行的 `params` 就有了 `DEPLOY_PLAN=''` 等默认值，无法判断参数页是否提交过。

测试：`test_first_build_registers_parameters_and_runs_nothing`（两个 job 的第一次构建都是 NOT_BUILT，描述为 `Parameters registered; run again with Build with Parameters`，不运行任何任务）。

### 4.8 Ansible 的配置通过 `ANSIBLE_CONFIG` 传入

规则：`TaskRunner` 的 `playbook` 用 `withEnv(["ANSIBLE_CONFIG=<工作区>/platform/ansible/ansible.cfg"])` 包住每次 `ansiblePlaybook` 调用，并统一传入 `vaultCredentialsId` 与 `credentialsId`。

机制：插件在 Jenkins 工作区根目录（仓库根目录）启动 ansible-playbook（`jenkinsci/ansible-plugin@635.v34b_48e979c86` 的 `src/main/java/org/jenkinsci/plugins/ansible/CLIRunner.java` 的 `execute`），那里没有 `ansible.cfg`。ansible-core 先查 `ANSIBLE_CONFIG`，再查当前目录的 `ansible.cfg`（`ansible/ansible@2.21.5` 的 `lib/ansible/config/manager.py` 的 `find_ini_config_file`）；`ansible.cfg` 中 `inventory`、`roles_path` 的相对路径以该文件所在目录为基准展开（同文件 `ConfigManager.get_config_value_and_origin`，`ansible/ansible@2.21.5` 的 `lib/ansible/utils/path.py` 的 `unfrackpath`）。所以同一个文件既给 Jenkins 用，也给在 `examples/platform/ansible` 下手工运行用。详见 [attachments/inventory-practices.md](attachments/inventory-practices.md) 第 1.2 节。

所以 3.7 节的命令行没有 `-i`，inventory 与 roles 照样能找到。

### 4.9 每次运行都需要 vault 口令，包括只在本机运行的任务

规则：所有 `ansiblePlaybook` 调用都传 `vaultCredentialsId`。`Prepare` 先用 `withCredentials` 绑定一次两个凭据（`TaskRunner.checkCredentials`），凭据不存在时构建在运行任何 playbook 之前失败，控制台中有凭据的 ID。插件接受 Secret text 和 Secret file 两种 vault 凭据，检查按 Secret text 绑定；用 Secret file 时把其中的 `string(...)` 换成 `file(...)`。

机制：插件把 Secret text / Secret file 凭据写成工作区中的临时文件，以 `--vault-password-file` 传入，结束后删除（`jenkinsci/ansible-plugin@635.v34b_48e979c86` 的 `src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java` 的 `appendVaultPasswordFile`）。加载 inventory 时就要解密 `group_vars/all/vault.yml`，与 play 作用于哪些主机无关。凭据 ID 写错时插件不报错，只是不传口令（`jenkinsci/ansible-plugin@635.v34b_48e979c86` 的 `src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java` 的 `perform`），随后 ansible-playbook 因无法解密而失败，看起来与任务失败相同：只有 `warn` 任务时，构建只会变成 UNSTABLE。

测试：`test_every_run_needs_the_vault_password_even_on_localhost`：不给口令时 `task_b`（只作用于 localhost）也失败，输出 `[ERROR]: Attempting to decrypt but no vault secrets found.`，不运行任何任务。`test_missing_vault_credential_stops_the_build_before_any_task_runs`：凭据不存在时构建 FAILURE，控制台中有该凭据的 ID，不运行任何任务。

### 4.10 等待时不占执行器

规则：`deployPipeline` 不在整个流水线外包 `node`：`Prepare`、每个任务、部署、报告各自申请一个执行器，用完即释放；`Plan`（无插件版的 `input` 表单）和复核的 `input` 在 `node` 之外。每次进入执行器先 `deleteDir()` 再 `unstash` `Prepare` 存下的文件。

机制：`input` 等待时，若在 `node` 之内就一直占着执行器和工作区。`unstash` 保证每个执行器上运行的是 `Prepare` 校验过的那份文件，而不是分支上的最新提交。

测试：`test_waiting_for_the_form_holds_no_executor`、`test_waiting_for_review_holds_no_executor`：构建停在 `input` 时，Jenkins 的忙碌执行器数为 0。`test_runs_the_files_it_validated_even_if_the_branch_moves_while_waiting`：等待期间分支上有修改 `hosts.yml` 的新提交，`task_b` 报告的仍是原文件的大小。

### 4.11 禁止同一 job 的并发构建

规则：`properties()` 中加 `disableConcurrentBuilds()`。新构建在上一次结束前排队等待。不这样做，两次构建可能同时部署同一批主机。

测试：`test_builds_of_one_job_do_not_run_concurrently`（两个 job 的 `concurrentBuild` 都为 false）。

### 4.12 引擎从 Jenkinsfile 所在的提交加载

规则：Jenkinsfile 先检出得到提交 `C`，再用 `library(identifier: "deploy-engine@C", retriever: modernSCM(scm: [$class: 'GitSCMSource', remote: <同一仓库>], libraryPath: 'platform/jenkins/'))` 加载引擎；引擎的 `Prepare` 也检出 `C`。

机制：引擎、任务、inventory 在同一个提交中，一起变更、一起回退；不需要管理员配置全局库，从仓库动态加载的库在沙箱中运行。`modernSCM` 把版本 `C` 解析成该提交；`legacySCM(scm)` 则忽略版本，总是取分支的最新提交。

两种写法的现象：Jenkinsfile 检出后 `sleep 15`，期间向分支推送一个新提交，把库中资源文件 `marker.txt` 从 `A` 改为 `B`，然后加载库并读取 `libraryResource('marker.txt')`：

```
legacySCM(libraryPath: 'platform/jenkins/', scm: scm)，identifier engine@bbc193a…：
jenkinsfile commit bbc193a0082cef7d960147752e7087a04ebbf487 marker A
Loading library engine@bbc193a0082cef7d960147752e7087a04ebbf487
library marker B

modernSCM(scm: [$class: 'GitSCMSource', remote: …], libraryPath: 'platform/jenkins/')，identifier engine@ff4e2fc…：
jenkinsfile commit ff4e2fcf07328ad749fee955052d773387d2852b marker A
Loading library engine@ff4e2fcf07328ad749fee955052d773387d2852b
Attempting to resolve ff4e2fcf07328ad749fee955052d773387d2852b from remote references...
Could not find ff4e2fcf07328ad749fee955052d773387d2852b in remote references. Pulling heads to local for deep search...
library marker A
```

测试：`test_engine_comes_from_the_commit_the_jenkinsfile_came_from` 用同样的方法检查样板本身：等待期间分支上有修改 `Plan.groovy` 输出文字的新提交，构建打印的仍是原来的文字。

### 4.13 沙箱中的共享库代码

从仓库动态加载的库在 Groovy 沙箱中运行，下面几种写法被拒绝或出错。控制台消息原样引用；第一行来自一个包名为 `org.example.deploy` 的库，其余来自本样板的引擎（包名 `deploy`）：

| 写法 | 结果 | 引擎的写法 |
| --- | --- | --- |
| 枚举常量带自己的方法体（`READ_ONLY { boolean allows(...) {...} }`） | `Scripts not permitted to use staticMethod org.example.deploy.AccessMode$1 $INIT java.lang.Object[]` | 常量只带构造参数，方法用 `switch (this)` |
| 没有构造函数的枚举 | `Scripts not permitted to use new java.util.LinkedHashMap` | 每个枚举都有带 `label` 的构造函数 |
| 静态字段的初始值引用另一个类的静态常量 | `Scripts not permitted to use staticMethod deploy.<类> get<字段名>`，类初始化失败 | 常量写成 `public static final` 字段；互相关联的名字用枚举（`EngineVariable`） |
| 闭包中调用同一个类的 `private static` 方法，或不带类名调用静态方法 | `Scripts not permitted to use staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods invokeMethod java.lang.Object java.lang.String java.lang.Object` | 静态方法不用 `private`，在闭包中写成 `类名.方法名(...)` |
| 同名的 `private` 方法和 `public` 方法 | 编译错误 `Mixing private and public/protected methods of the same name causes multimethods to be disabled` | 改名 |
| `new IllegalArgumentException(...)`、`new LinkedHashMap(map)` | `Scripts not permitted to use new ...` | 问题收集到列表后调用 `error`；复制 Map 用 `[:] + map` |
| `list.sort(false) { ... }`（排序后返回新列表） | `Scripts not permitted to use staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods sort java.util.Collection boolean groovy.lang.Closure` | 复制一份再用 `sort { ... }` |
| `text.codePointCount(...)` | `Scripts not permitted to use method java.lang.String codePointCount int int` | 用 `length()`：按 UTF-16 代码单元计数，`Limits` 中的字符数上限对 BMP 以外的字符（如 emoji）更严 |
| `collection.flatten()` | `Scripts not permitted to use staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods flatten java.util.Collection` | 嵌套的 `any` / `collectMany` |
| 对数组用展开运算符（`values()*.label`） | `No such field found: field ...[] label` | `values().collect { it.label }` |
| `@NonCPS` 方法调用没有标 `@NonCPS` 的方法 | `expected to call deploy.Catalog.tasksFor but wound up catching deploy.AccessMode.allows` | 只做计算的方法一律标 `@NonCPS` |

Groovy 的 `switch` 不检查是否覆盖了枚举的所有常量：引擎在每个 `switch` 之后 `throw new IllegalStateException(...)`，漏掉的常量在运行时立即失败（沙箱拒绝这个构造函数时同样失败）。

### 4.14 只读 job 的保证范围

规则：`AccessMode.READ_ONLY` 时，参数页只列出 `access: read` 的任务、没有 `SKIP_DEPLOY`；`PlanParser` 拒绝写任务和 `skip_deploy` 字段；`Plan.deploy` 为假，没有部署阶段。

测试：`tests/test_read_only_job.py`：参数页只含只读任务；只读任务正常运行且没有部署；`DEPLOY_PLAN` 中的写任务（`pre[0]: task task_a writes, and this job is read-only`）、`skip_deploy`、表单中勾选的写任务都在运行任何任务之前失败。

范围：这是引擎的检查。任务的 `access` 是作者的声明，标成 `read` 的 role 照样可以写；能修改仓库的人也能改只读 Jenkinsfile 中的 `mode`。需要更强的保证时，在 Jenkins 中给只读 job 配置权限更小的凭据（例如目标主机上没有写权限的 SSH 用户），Jenkinsfile 中只改凭据 ID。

### 4.15 执行前复核：执行的就是复核过的文件

规则（`TaskRunner` 的 `runSteps`）：

1. 一个执行器上：`unstash`，以 `generate` 模式运行任务；任务没写出复核文件则构建失败；计算 `sha256`，读出全文，归档，`stash` 这一个文件；离开执行器。
2. 不占执行器：`timeout(review.timeout_minutes) { input(message: 'Run <id> as reviewed below? (sha256 …)\n\n<全文>', ok: 'Run', submitterParameter: 'reviewer') }`。
3. 另一个执行器上：`unstash` 源文件和复核文件，`verifySha256(file:, hash:)`，再以 `execute` 模式运行任务。

任务的结果只由第 3 步的运行决定：确认后先清除生成那次运行的结果；第 3 步开始前构建被中止则为 `aborted`，`unstash` 或 `verifySha256` 失败则为 `error`。生成那次运行失败时按任务的失败策略处理，不请求确认。

谁能确认：`input` 不写 `submitter` 时，有该 job 的 Job/Build 权限的人可以确认，有 Job/Build 或 Job/Cancel 权限的人可以中止（`jenkinsci/pipeline-input-step-plugin@560.v56198a_642157` 的 `src/main/java/org/jenkinsci/plugins/workflow/support/steps/input/InputStepExecution.java` 的 `canSettle` 与 `canCancel`）。测试用的 Jenkins 没有开启安全设置，所有人都能确认；这条规则没有集成测试。

测试：`tests/test_review.py`：等待时提示以 `Run task_d as reviewed below? (sha256 ` 开头、以复核文件全文结尾，归档的复核文件为 `change a\nchange b\n`，此时不执行任何变更；确认后控制台有 `[Pipeline] verifySha256`、`applied change a`、`applied change b`，报告中 `reviewer` 为确认人；中止后构建 ABORTED，任务只以生成模式运行过一次；确认后、执行前中止构建，报告中该任务为 `aborted`；生成失败时不请求确认，报告为 `failed-stopped`；等待时忙碌执行器数为 0。

范围：复核文件在两次运行之间只经过 Jenkins 的 stash，`verifySha256` 是对这一点的再次确认；能让它失败的情形无法从外部构造，没有测试覆盖。

### 4.16 参数名与 inventory 变量重名在运行时检查

规则：`Prepare` 把所有任务的参数名写成 `task_parameter_names`，运行 `platform/ansible/check-variable-names.yml`：它在 localhost 上取所有主机的变量名，与参数名求交集，非空时失败。

机制：任务参数以 extra vars 传入，优先级最高，与 inventory 变量同名时会静默覆盖该变量。引擎读不到解密后的 inventory，所以交给 Ansible 检查；`tests/test_tasks.py` 的 `test_option_names_cannot_override_inventory_reserved_or_engine_variables` 在提交前做同样的检查。

测试：`test_parameter_name_used_by_the_inventory_stops_the_build_before_any_task_runs`：`host_vars/web-01.yml` 中有 `task_a_message` 时构建 FAILURE，控制台输出 `task parameters also defined in the inventory: ['task_a_message']`，不运行任何任务。

## 5. 陷阱

| 做法 | 现象 | 解决 |
| --- | --- | --- |
| 在 `properties()` 之后读取 `params` 判断是否第一次运行 | 第一次运行报“没有选择主机组”并失败 | 先复制 `params` 再调用 `properties()`（4.7） |
| 用 `catchError(catchInterruptions: false)` 区分超时与手动中止 | 手动中止后流水线继续执行 | `ansiblePlaybook` 抛出普通 `InterruptedException`，按已耗时区分（4.2） |
| 用 `legacySCM(scm)` 从同一仓库加载库 | 库来自分支的最新提交，不是 Jenkinsfile 所在的提交 | 用 `modernSCM` 加提交号（4.12） |
| 共享库代码照普通 Groovy 写 | 沙箱拒绝、编译错误或 CPS 不匹配 | 见 4.13 的对照表 |
| Active Choices 勾选框的文字含全角标点（`（`、`：`、`！`）或 `@ = + "` | 勾选框旁显示 `web&#xff08;web-01&#xff09;` 之类的实体文字 | 勾选框文字只用半角符号；任务 `meta/task.yml` 的 `description` 同样（`test_description_has_no_characters_the_active_choices_sanitizer_turns_into_entities`）。原因：沙箱脚本的返回值都经过插件的 HTML 过滤（`jenkinsci/active-choices-plugin@2.8.11` 的 `src/main/java/org/biouno/unochoice/model/GroovyScript.java` 的 `eval`），把这些字符转成实体；勾选框的文字随后又被页面模板转义一次。HTML 表单（`*_TASK_OPTIONS`）中的实体会被浏览器还原，不受影响 |
| 动态 HTML 表单用 `fieldset` / `legend` / `label`、`title` 或 `aria-*` 属性 | 这些标签和属性消失 | 过滤器只放行基本标签及 `input`、`select`、`option`、`textarea` 和少数属性（`jenkinsci/active-choices-plugin@2.8.11` 的 `src/main/java/org/biouno/unochoice/util/SafeHtmlExtendedMarkupFormatter.java` 的 `POLICY` 常量）；分组用 `div` 加 `style`，必填用文字 “(required)” 标出 |
| 先填写某个任务的参数，再勾选另一个任务 | 已填写的值全部恢复为默认值 | 改变勾选会重新生成整个表单。先勾选全部任务，再填写参数（`test_changing_the_selection_regenerates_the_form_and_drops_typed_values`） |
| 无插件版中只有一个输入项 | `input` 返回该值本身而不是 Map，按 Map 处理时报错 | 只有一个输入项时自己组成 Map（`ParameterPage` 的 `basicDraft`）；`input` 的行为见 `jenkinsci/pipeline-input-step-plugin@560.v56198a_642157` 的 `src/main/java/org/jenkinsci/plugins/workflow/support/steps/input/InputStepExecution.java` 的 `proceed`。`check_c` 没有参数，所以只勾选它时表单只有一项（`test_form_with_a_single_field_returns_its_value`） |
| 用 `x ?: 默认值` 取可选字段 | `timeout_minutes: 0`、`policy: ''`、`params: []` 不报错，静默改用默认值 | Groovy 中 `0`、`''`、`[]` 为假；用 `x == null ? 默认值 : x`，再做类型检查（4.5） |
| 在 `DEPLOY_PLAN` 中给字符串参数写未加引号的 `2026-01-01`、`yes` | 不加检查时到达 Ansible 的是 `Thu Jan 01 00:00:00 UTC 2026`、`true` | YAML 解析时已变成日期、布尔；引擎只接受字符串和整数，提示加引号 |
| 表单中某个值含 `,@@` | 值被切断，后半段被当成另一个字段 | 提交值用 `,@@` 切分，无法区分；引擎在字段重复时报错（`test_invalid_form_values_fail_before_any_task_runs`） |
| 在部署前、部署后运行同一任务，并要求 id 全局唯一 | 计划校验报 id 重复 | id 只需在一个阶段内唯一：stage 名、参数文件名、报告都带阶段（`test_skipped_deploy_still_runs_pre_and_post_tasks`） |
| 用 `extraVars` 或 `-e key=value` 传整数 | 任务中 `>=` 比较报 `'>=' not supported between instances of '_AnsibleTaggedStr' and 'int'` | 用 JSON 文件 `-e @file`（4.3） |
| 只在 argument_specs 中写 `default`，没有 `defaults/main.yml` | `'xxx' is undefined` | 运行时默认值写在 `defaults/main.yml`，argument_specs 中的 `default` 与之相同（4.6） |
| `assert` 的 `success_msg` 引用失败时不存在的值，如 `stat.size` | 断言失败时报 `object of type 'dict' has no attribute 'size'`，真正的失败原因被掩盖 | ansible-core 2.21 中 `success_msg` 在断言失败时也会被渲染；加 `default`，如 `task_b_file.stat.size \| default(0)` |
| 用 Jinja 测试 `is between(1, 5)` | `No test named 'between'` | 写成两个比较：`x >= 1`、`x <= 5` |
| 按路径引用 role（`roles: [../../business/deploy]`） | 任务标题显示成 `TASK [../../business/deploy : ...]` | 把 `business` 加进 `roles_path`，按名字引用 `deploy` |
| ansible-lint 检查 `business/tasks/` | `schema[tasks]: ... is not of type 'array'`：目录名 `tasks` 下的每个 YAML 都被当成任务列表 | `.ansible-lint` 的 `kinds` 中按路径指定文件类型 |
| ansible-lint 检查 `run-task.yml` | `syntax-check[specific]: ... 'task_role' is undefined` | 在 `.ansible-lint` 中用 `extra_vars` 给出示例值（`ansible/ansible-lint@26.10.1` 的 `src/ansiblelint/runner.py` 的 `Runner._get_ansible_syntax_check_matches`） |
| 联动参数（`reactiveChoice`）的脚本直接使用被引用的参数，再经 API 触发构建 | 计划校验报 `unknown task '(task list failed)'` | API 不传该参数时，Jenkins 在没有绑定被引用参数的情况下运行脚本求默认值，脚本失败后备用脚本的返回值成了参数值；脚本中捕获 `MissingPropertyException`，按未选择处理（[attachments/parameter-page.md](attachments/parameter-page.md) 第 3.6 节） |
| 用 Jinja 的 `intersect` 求两个列表的交集并写进消息 | 同样的输入，消息中名字的顺序每次不同 | `intersect` 不保证顺序；后面接 `sort`（`check-variable-names.yml`） |
| 复核任务的 `copy` 写复核文件 | `Destination directory .../.deploy-run/review does not exist` | 目录由引擎创建（`TaskRunner` 在生成前用 `writeFile` 写一个占位文件） |
| 非阻塞任务失败后看到控制台中的 `FATAL: command execution failed` 和 Java 堆栈 | 误以为构建失败 | playbook 失败时插件抛出的 `AbortException` 是 `IOException` 的子类（`jenkinsci/jenkins@2.580.1` 的 `core/src/main/java/hudson/AbortException.java` 的类声明），被插件自己的 `catch (IOException)` 先打印成 FATAL 和堆栈再抛出（`AnsiblePlaybookBuilder` 的 `perform`）。以构建结果为准；真正原因看它上方 Ansible 的 `fatal: [主机]: FAILED! => {... "msg": ...}` |

## 6. 检查

修改后运行（在 `jenkins-ansible-deploy/` 下）：

```bash
python3 -m pytest tests -m 'not jenkins'      # inventory、playbook、任务目录约定、ansible-lint
python3 -m pytest tests                        # 再加 Jenkins 与浏览器集成测试
```

两条命令都必须以退出码 0 结束。

只检查 inventory（在 `examples/platform/ansible` 下）：

```bash
ansible-inventory --graph deploy_targets
ansible-playbook check-inventory.yml --vault-password-file ../../../tests/example-vault-password.txt
```

`check-inventory.yml` 不连接主机，对每台发布对象按 role `deploy` 的 argument_specs 校验，一次报告所有主机的问题，例如把 `web` 的 `app_port` 改成 `eighty`、删掉 `api` 的 `app_version` 时：

```
argument 'app_port' is of type str and we were unable to convert to int
missing required arguments: app_version
```

`web-01`、`web-02`、`api-01` 失败，`worker-01`、`worker-02` 通过（`test_check_inventory_reports_every_broken_host_in_one_run`）。

## 7. 附件

- [attachments/inventory-practices.md](attachments/inventory-practices.md)：inventory 管理——目录与变量分层、变量优先级与命名、ansible-vault 与 Jenkins 凭据、`ansible-inventory` / argument_specs / ansible-lint 校验及其在 CI 中的用法。设计或修改 inventory 前读。
- [attachments/parameter-page.md](attachments/parameter-page.md)：两种参数页的界面与实现细节——Active Choices 参数的定义、表单 HTML、提交值的格式与解析，无插件版的 `input` 表单。修改参数页或排查参数传递问题时读。
- [attachments/review-before-run.md](attachments/review-before-run.md)：执行前复核的界面流程，每一步配 Jenkins 截图。第一次使用复核，或向复核人说明怎么操作时读。

## 8. 出处

文中 `<owner>/<name>@<version>` 的 `<路径>` 指下表中上游仓库在该标签（或提交）上的同一路径：

| 出处 | 上游仓库 | 标签，或分支与提交 |
| --- | --- | --- |
| `ansible/ansible@2.21.5` | https://github.com/ansible/ansible | 标签 `v2.21.5` |
| `ansible/ansible-documentation@b16dcb8` | https://github.com/ansible/ansible-documentation | 分支 `stable-2.21` 的提交 `b16dcb8e8b5b5658ae9691b8632b7046e879606b` |
| `ansible/ansible-lint@26.10.1` | https://github.com/ansible/ansible-lint | 标签 `v26.10.1` |
| `jenkinsci/jenkins@2.580.1` | https://github.com/jenkinsci/jenkins | 标签 `jenkins-2.580.1` |
| `jenkinsci/ansible-plugin@635.v34b_48e979c86` | https://github.com/jenkinsci/ansible-plugin | 标签 `635.v34b_48e979c86` |
| `jenkinsci/active-choices-plugin@2.8.11` | https://github.com/jenkinsci/active-choices-plugin | 标签 `uno-choice-2.8.11` |
| `jenkinsci/workflow-cps-plugin@4395.v738456a_5da_6b_` | https://github.com/jenkinsci/workflow-cps-plugin | 标签 `4395.v738456a_5da_6b_` |
| `jenkinsci/workflow-basic-steps-plugin@1098.v808b_fd7f8cf4` | https://github.com/jenkinsci/workflow-basic-steps-plugin | 标签 `1098.v808b_fd7f8cf4` |
| `jenkinsci/pipeline-input-step-plugin@560.v56198a_642157` | https://github.com/jenkinsci/pipeline-input-step-plugin | 标签 `560.v56198a_642157` |
| `jenkinsci/pipeline-utility-steps-plugin@3.810.va_7672d206740` | https://github.com/jenkinsci/pipeline-utility-steps-plugin | 标签 `3.810.va_7672d206740` |
| `jenkinsci/workflow-multibranch-plugin@842.v3a_b_59b_57b_e6e` | https://github.com/jenkinsci/workflow-multibranch-plugin | 标签 `842.v3a_b_59b_57b_e6e` |
| `jenkins-infra/jenkins.io@d471394` | https://github.com/jenkins-infra/jenkins.io | 分支 `master` 的提交 `d4713943384e705fd7dd2f645555083b247e61b4` |
