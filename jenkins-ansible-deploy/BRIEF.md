# Jenkins + Ansible 部署流水线样板

## 1. 范围

一个 git 仓库同时包含 Jenkins 流水线和 Ansible 代码。部署对象是 Ansible inventory 中 `deploy_targets` 下的**主机组**，在 Jenkins 的构建参数页上多选；部署阶段可以勾选跳过；部署前、部署后各可选择 0 到任意个**额外任务**，参数页上只显示所选任务的参数，按参数类型（字符串、整数、布尔、枚举）填写；任务失败时按 `abort`（停止流水线）或 `warn`（标记不稳定后继续）处理。主机和主机组的参数（端口、目录、版本号等）只在 inventory 中管理，参数页不能覆盖。参数页给出两种实现：方案 D 用 Active Choices 插件在同一页上实时显示所选任务的表单；方案 C 只用 Jenkins 自带功能，构建开始后用 `input` 步骤弹出表单。两种方案都接受一个 YAML 格式的完整计划 `DEPLOY_PLAN`，供 API 和上游 job 触发。额外任务和部署本身都是占位实现：只接收参数、校验、输出将要执行的动作，不连接、不改动目标主机。

“方案 C”“方案 D”只是这两种实现的名称。

不包含：多环境 inventory、动态 inventory、回滚、人工确认式的失败策略、任务并行执行。

## 2. 环境

| 组件 | 版本 |
| --- | --- |
| Jenkins | 2.580.1（Java 21） |
| Jenkins 插件 | `ansible` 635.v34b_48e979c86、`uno-choice`（Active Choices）2.8.11、`pipeline-utility-steps` 3.810.va_7672d206740、`credentials-binding` 728.v902a_273b_8947、`workflow-cps` 4395.v738456a_5da_6b_、`pipeline-input-step` 560.v56198a_642157、`workflow-aggregator` 608.v67378e9d3db_1；全部 65 个插件及版本见 `examples/tests/jenkins_server/plugins.txt` |
| ansible-core | 2.21.5 |
| ansible-lint | 26.10.1 |
| Python | 3.13；测试用 pytest 9.1.1、playwright 1.64.0、PyYAML 6.0.1、cryptography 50.0.1 |

Jenkinsfile 从 git 加载，运行在 Groovy 沙箱中，**不需要管理员审批任何脚本**。

在真实 Jenkins 上使用需要：

1. 安装插件 `workflow-aggregator`、`ansible`、`pipeline-utility-steps`、`credentials-binding`，方案 D 另装 `uno-choice`；
2. Jenkins 节点上安装 ansible-core 2.21.5，`ansible-playbook` 在 `PATH` 中；
3. 两个凭据：`ansible-vault-password`（类型 Secret text，ansible-vault 口令）和 `deploy-ssh-key`（类型 SSH Username with private key，连接目标主机）。ID 在 Jenkinsfile 开头的 `settings` 中修改；不需要 SSH 时把 `sshCredentialsId` 设为 `null`；
4. 新建 Pipeline job，选 “Pipeline script from SCM”，Script Path 填 `jenkins-ansible-deploy/examples/Jenkinsfile`（方案 D）或 `jenkins-ansible-deploy/examples/Jenkinsfile.no-plugin`（方案 C）。只让受信任的分支构建这个 job：流水线 `load` 仓库中的 Groovy 代码、运行仓库中的 playbook，并且持有 vault 口令和 SSH 私钥；
5. 点一次 “Build Now”：这次构建只登记构建参数，结果为 NOT_BUILT。之后用 “Build with Parameters”。

运行本目录的测试：

```bash
cd jenkins-ansible-deploy/examples
pip install ansible-core==2.21.5 ansible-lint==26.10.1 pytest==9.1.1 playwright==1.64.0 PyYAML==6.0.1 cryptography==50.0.1
python3 -m pytest tests -m 'not jenkins'            # 只需要 Ansible，约 2 分钟

# 集成测试：启动一个只监听 127.0.0.1、没有登录的本地 Jenkins（仅用于测试），需要 Java 21 和 git
python3 tests/jenkins_server/setup_jenkins.py --home /tmp/jenkins-test
export JENKINS_URL=http://127.0.0.1:8080             # 上一条命令最后打印的两行
export DEPLOY_REPO_URL=file:///tmp/jenkins-test/repo
python3 -m playwright install chromium               # 或者 export CHROMIUM_PATH=<已安装的 chromium 可执行文件>
python3 -m pytest tests                               # 全部
```

`setup_jenkins.py` 会下载 Jenkins 2.580.1 和 `plugins.txt` 中的插件（校验 sha256），把 `jenkins-ansible-deploy/` 复制进 `<home>/repo` 的 git 仓库，启动 Jenkins，创建上面两个凭据（vault 口令取自 `tests/example-vault-password.txt`，SSH 私钥随机生成）。Jenkins 已在运行时再执行一次，只把当前文件重新提交到 `<home>/repo`。集成测试为每个测试新建 job，测试结束后删除；只校验计划、互不影响的测试共用一个 job。

## 3. 示例代码

```
examples/
  Jenkinsfile                方案 D：Active Choices，同一页上选择并填写
  Jenkinsfile.no-plugin      方案 C：只用 Jenkins 自带参数类型 + input 步骤
  jenkins/
    deploy-engine.groovy     两个 Jenkinsfile 用 load 加载的共用逻辑
    tasks.yml                额外任务目录（Jenkins 侧元数据）
  ansible/
    ansible.cfg              inventory = inventory，roles_path = roles
    .ansible-lint            给 ansible-lint 语法检查用的示例变量
    inventory/               hosts.yml、group_vars/、host_vars/
    deploy.yml               部署（role app_deploy）
    run-task.yml             运行一个额外任务（role 名由 Jenkins 传入）
    check-inventory.yml      不连接主机，校验每台主机的 inventory 变量
    roles/                   app_deploy，以及额外任务 task_a、task_b、task_c、check_c、check_a
  tests/                     pytest：Ansible 测试、Jenkins 集成测试、浏览器测试
```

### 3.1 一次构建的流程

```
Prepare            load deploy-engine.groovy
                   读 tasks.yml、每个任务 role 的 argument_specs.yml 与 defaults/main.yml、inventory/hosts.yml
                   检查两个凭据存在
                   properties()：禁止并发构建，登记构建参数（下一次构建起生效）
                   把选择（或 DEPLOY_PLAN）校验成执行计划；有错误则一次列出并失败，任何 playbook 都不运行
                   （方案 C：这一步放在不分配执行器的 stage “Task options” 中，那里可能等待 input；
                    Prepare 先 stash 本目录，Run 不再检出而是 unstash，运行的就是校验过的文件）
Pre-deploy tasks   每个任务一个 stage “pre: <id>”：ansible-playbook run-task.yml -e @参数文件 [-l 所选组]
Deploy             SKIP_DEPLOY 时跳过；否则 ansible-playbook deploy.yml -l 所选组
Post-deploy tasks  同部署前
最后               打印结果汇总，归档 deploy-report.json
```

### 3.2 Ansible：inventory

`inventory/hosts.yml` 只写主机与组的关系。`deploy_targets` 的子组就是参数页上的发布对象：

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

版本号 `app_version` 也是组变量：发布新版本 = 在 git 中修改 `group_vars/<组>.yml` 并提交，Jenkins 部署 git 中的状态。inventory 的目录与变量分层、敏感信息、校验方法的调研结论见 [attachments/inventory-practices.md](attachments/inventory-practices.md)。

### 3.3 Ansible：部署与额外任务

`deploy.yml` 对 `deploy_targets` 运行 role `app_deploy`，Jenkins 用 `--limit 'web:api'` 把范围缩小到所选的组。`roles/app_deploy/meta/argument_specs.yml` 声明部署需要的主机变量及类型（`app_port: int, required` 等），Ansible 在 role 的第一个任务之前自动按它校验（`.reference-cache/ansible/ansible@2.21.5/lib/ansible/playbook/role/__init__.py:363-371`）。

每个额外任务是一个 role：

| 任务 | 运行位置 | 参数（类型，默认值） |
| --- | --- | --- |
| `task_a` | 所选主机组 | `task_a_message`（str，必填）、`task_a_repeat`（int，1，范围 1–5）、`task_a_delay_seconds`（int，0） |
| `task_b` | Jenkins 节点本机 | `task_b_path`（str，`inventory/hosts.yml`，相对 Ansible 目录，不能是绝对路径或含 `..`）、`task_b_min_bytes`（int，1） |
| `task_c` | 所选主机组 | `task_c_mode`（str，choices `plan`/`apply`，`plan`）、`task_c_verbose`（bool，false）、`task_c_label`（str，空） |
| `check_c` | 所选主机组 | 无参数 |
| `check_a` | 所选主机组 | `check_a_min_connections`（int，必填）：每台主机的 `app_max_connections` 至少为多少 |

一个任务 role 的三个文件，以 `task_a` 为例：

```yaml
# roles/task_a/meta/argument_specs.yml：参数名、类型、必填、默认值、说明
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
# roles/task_a/defaults/main.yml：运行时真正生效的默认值，与上面的 default 保持一致
task_a_repeat: 1
task_a_delay_seconds: 0
```

```yaml
# roles/task_a/tasks/main.yml
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

`run-task.yml` 运行任意一个任务：

```yaml
- name: "Task {{ task_role }}"
  hosts: "{{ task_hosts }}"
  gather_facts: false
  tasks:
    - name: "Run {{ task_role }}"
      ansible.builtin.include_role:
        name: "{{ task_role }}"
```

不经过 Jenkins 也可以直接运行（在 `examples/ansible` 目录下）：

```bash
echo '{"task_role": "task_a", "task_hosts": "deploy_targets", "task_a_message": "hello", "task_a_repeat": 2}' > /tmp/vars.json
ansible-playbook run-task.yml --limit api --vault-password-file ../tests/example-vault-password.txt -e @/tmp/vars.json
```

输出节选：

```
    "msg": "api-01: hello (1/2)"
    "msg": "api-01: hello (2/2)"
```

### 3.4 Jenkins：任务目录 `jenkins/tasks.yml`

只放 Jenkins 需要、Ansible 不需要的元数据；参数的定义在 role 里，Jenkins 读取 role 的文件生成表单：

```yaml
tasks:
  task_a:
    description: 在所选主机组上运行的示例任务
    phases: [pre]            # 允许的阶段：pre / post
    run_on: targets          # targets：所选主机组；controller：Jenkins 节点本机
    policy: abort            # 默认失败策略：abort / warn
    timeout_minutes: 5       # 超时按任务失败处理
  task_b:
    description: 在 Jenkins 节点本机检查文件的示例任务
    phases: [pre]
    run_on: controller
    policy: abort
    timeout_minutes: 5
```

（`task_c`、`check_c`、`check_a` 同理，见文件。）参数页上选择任务时，同一阶段按这个文件中的顺序执行。

### 3.5 Jenkins：Jenkinsfile

两个 Jenkinsfile 的 `settings` 相同，部署相关的 stage 也相同。方案 D（`Jenkinsfile`）：

```groovy
Map settings = [
    baseDir: 'jenkins-ansible-deploy/examples',                  // 本目录在仓库中的路径
    ansible: [vaultCredentialsId: 'ansible-vault-password',      // Secret text：ansible-vault 口令
              sshCredentialsId  : 'deploy-ssh-key'],             // SSH Username with private key：连接目标主机；不需要时设为 null
]

def engine
Map plan
List results = []

pipeline {
    agent any

    stages {
        stage('Prepare') {
            steps {
                script {
                    engine = load "${settings.baseDir}/jenkins/deploy-engine.groovy"
                    plan = engine.resolvePlan(engine.prepareRequest(settings, params, 'activeChoices'))
                }
            }
        }
        stage('Pre-deploy tasks') {
            when { expression { plan?.pre } }
            steps { script { engine.runTasks(plan, 'pre', results) } }
        }
        stage('Deploy') {
            when { expression { plan && !plan.skipDeploy } }
            steps { script { engine.deploy(plan, results) } }
        }
        stage('Post-deploy tasks') {
            when { expression { plan?.post } }
            steps { script { engine.runTasks(plan, 'post', results) } }
        }
    }

    post {
        always { script { engine?.report(results) } }
    }
}
```

方案 C（`Jenkinsfile.no-plugin`）顶层是 `agent none`，分三个 stage：`Prepare`（`agent any`，调用 `prepareRequest(settings, params, 'basic')`，再 `stash` 本目录）、`Task options`（不分配执行器，调用 `resolvePlan`，这里可能等待 `input`）、`Run`（`agent any`，`skipDefaultCheckout()`，`when { beforeAgent true; expression { plan } }`，先 `unstash`，再是部署前、部署、部署后三个 stage，`post` 中调用 `report`）。原因见 4.10。

两个 Jenkinsfile 都是声明式（Declarative）流水线，`properties()` 和每个任务的 `stage()` 在 `script {}` 中经引擎调用：构建参数和 stage 都要按运行时读到的任务目录、inventory 生成，`parameters {}` 指令做不到（`script` 是声明式流水线留出的“出口”，`.reference-cache/jenkins-infra/jenkins.io@d471394/content/doc/book/pipeline/syntax.adoc:1995-1997`）。不要再加 `parameters {}` 或 `options {}` 指令：声明式和 `properties` 步骤都会在下一次运行时移除自己上次设置的 job 属性（`.reference-cache/jenkinsci/workflow-multibranch-plugin@842.v3a_b_59b_57b_e6e/src/main/java/org/jenkinsci/plugins/workflow/multibranch/JobPropertyStep.java:137-158`），同类属性会互相覆盖。

（两个文件中每个 stage 的写法相同，只是多了注释和换行。）

共用代码用 `load` 从同一次检出中读取，而不是 Jenkins 的共享库（Shared Library）：引擎、任务目录、Ansible 代码在同一个提交中，一起变更、一起回退，也不需要管理员配置全局库。代价是没有共享库的版本标签，多条流水线要共用时需各自复制或改成共享库。

### 3.6 构建参数

方案 D（`Jenkinsfile`）：

| 参数 | 类型 | 作用 |
| --- | --- | --- |
| `TARGET_GROUPS` | Active Choices 勾选框 | 主机组，选项 `web (web-01, web-02)` 等来自 inventory |
| `SKIP_DEPLOY` | 布尔 | 跳过部署，部署前后任务照常执行 |
| `PRE_TASKS` / `POST_TASKS` | Active Choices 勾选框 | 该阶段允许的任务，来自 `tasks.yml` |
| `PRE_TASK_OPTIONS` / `POST_TASK_OPTIONS` | Active Choices 动态 HTML | 随勾选实时生成：每个所选任务一组，含失败策略下拉框和各参数输入框，预填 role 的默认值；整数为数字框，布尔和枚举为下拉框，必填参数名后标“（必填）”；文本框和数字框的 `placeholder` 是参数名（必填时加“（必填）”），也是它们的可访问名称 |
| `DEPLOY_PLAN` | 多行文本 | 非空时忽略上面所有参数 |

方案 C（`Jenkinsfile.no-plugin`）：每个主机组一个勾选框 `TARGET_<组>`，`SKIP_DEPLOY`，每个阶段的每个任务一个勾选框 `PRE_<任务>` / `POST_<任务>`，`EDIT_TASK_OPTIONS`（默认勾选），`DEPLOY_PLAN`。构建开始后，若勾选了任务且 `EDIT_TASK_OPTIONS` 为真，流水线暂停并弹出只含所选任务的表单（字段名形如 `pre.task_a.task_a_repeat`），点“开始”后继续，点“Abort”则构建 ABORTED（`test_no_plugin_abort_in_form_aborts_the_build`）。等待包在 30 分钟的 `timeout` 中，期间不占执行器（`test_no_plugin_waiting_for_input_holds_no_executor`）。

`DEPLOY_PLAN` 格式（两种方案相同）：

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

从 API 或上游 job 触发：向 job 的 `buildWithParameters` 接口 POST，只传 `DEPLOY_PLAN` 一个参数。集成测试大多这样触发构建（`examples/tests/jenkins_server/client.py` 的 `start_build`）；少数用例直接赋值 Active Choices 参数来模拟页面提交，见 [attachments/parameter-page.md](attachments/parameter-page.md) 第 3.5 节。Active Choices 插件说明这些参数只供人在页面上填写，通过 API 或脚本触发时不保证可用（`.reference-cache/jenkinsci/active-choices-plugin@2.8.11/README.md:570-573`），所以不要用 API 直接传 `TARGET_GROUPS` 等参数。

参数页两种方案的界面细节与提交值的格式见 [attachments/parameter-page.md](attachments/parameter-page.md)。

### 3.7 实际输出

用下面的 `DEPLOY_PLAN` 运行方案 D 的 job（在参数页上做同样的选择，结果相同）：

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
[deploy] 凭据检查通过
[deploy] 使用 DEPLOY_PLAN，忽略参数页上的其他选择
[deploy] 本次执行计划：
  主机组：[web, api]
  部署：执行
  部署前任务：
    - task_a [abort] [task_a_message:hello, task_a_repeat:1, task_a_delay_seconds:0]
    - task_b [abort] [task_b_path:inventory/hosts.yml, task_b_min_bytes:1]
  部署后任务：
    - check_a [warn] [check_a_min_connections:150]
[Pipeline] { (pre: task_a)
[deploy] $ ansible-playbook jenkins-ansible-deploy/examples/ansible/run-task.yml -l web:api --private-key <工作区>/ssh<随机数>.key -u deploy --vault-password-file <工作区>/vault<随机数>.password -e @.deploy-run/pre-task_a.json
    "msg": "web-01: hello (1/1)"
    "msg": "api-01: hello (1/1)"
    "msg": "web-02: hello (1/1)"
[Pipeline] { (pre: task_b)
[deploy] $ ansible-playbook jenkins-ansible-deploy/examples/ansible/run-task.yml --private-key <工作区>/ssh<随机数>.key -u deploy --vault-password-file <工作区>/vault<随机数>.password -e @.deploy-run/pre-task_b.json
    "msg": "inventory/hosts.yml: 458 bytes"
[Pipeline] { (Deploy)
[deploy] $ ansible-playbook jenkins-ansible-deploy/examples/ansible/deploy.yml -l web:api --private-key <工作区>/ssh<随机数>.key -u deploy --vault-password-file <工作区>/vault<随机数>.password
    "msg": "deploy web 2.3.1 to web-01 (192.0.2.11) dir=/opt/apps/web port=8080 max_connections=100 user=app"
    "msg": "deploy web 2.3.1 to web-02 (192.0.2.12) dir=/opt/apps/web port=8080 max_connections=200 user=app"
    "msg": "deploy api 1.8.0 to api-01 (192.0.2.13) dir=/opt/apps/api port=9000 max_connections=300 user=app"
[Pipeline] { (post: check_a)
[deploy] $ ansible-playbook jenkins-ansible-deploy/examples/ansible/run-task.yml -l web:api --private-key <工作区>/ssh<随机数>.key -u deploy --vault-password-file <工作区>/vault<随机数>.password -e @.deploy-run/post-check_a.json
    "msg": "web-01: app_max_connections=100 < 150"
    "msg": "api-01: app_max_connections=300"
    "msg": "web-02: app_max_connections=200"
WARNING: [deploy] 任务 check_a 失败：Ansible playbook execution failed（warn：继续执行）
[deploy] 结果：
  pre    task_a           abort PASSED     4s
  pre    task_b           abort PASSED     2s
  deploy deploy           abort PASSED     1s
  post   check_a          warn  FAILED     3s -> continued
```

`task_b` 运行在 Jenkins 节点本机，命令里没有 `-l`；`web-02` 的 `max_connections=200` 来自 `host_vars/web-02.yml`。每个任务在 Jenkins 中是一个 stage（`pre: task_a`、`post: check_a` 等）。`deploy-report.json` 中每一项含 `id`、`name`、`phase`、`policy`、`params`、`seconds`、`status`（`PASSED` / `FAILED` / `ABORTED`：手动中止 / `ERROR`：插件内部错误），失败项另有 `message` 与 `outcome`（`stopped`：abort 策略停止了流水线；`continued`：warn 策略继续执行）。

### 3.8 新增一个额外任务

1. 在 `ansible/roles/<任务名>/` 下建 role：
   - `meta/argument_specs.yml` 写参数：名称以 `<任务名>_` 开头，不能与 inventory 变量、Ansible 保留字、`task_role` / `task_hosts` 重名；类型只用 `str`、`int`、`bool`；`choices` 只用于 `str`；非必填参数写 `default`。
   - `defaults/main.yml` 写每个非必填参数的默认值，与 `default` 相同，用字面值（不含 `{{`）。
   - `tasks/main.yml` 写任务；条件不满足时用 `assert` / `fail` 让它失败。
2. 在 `jenkins/tasks.yml` 中加一项：`description`（不用全角标点和 `@ = + "`，原因见第 5 节）、`phases`、`run_on`、`policy`、`timeout_minutes`。
3. 运行 `python3 -m pytest tests -m 'not jenkins'`：`test_catalog.py` 检查上面这些约定。引擎在 `Prepare` 中也检查参数名前缀、类型、`choices`、默认值和目录字段，不符合时构建失败。
4. 提交后先运行一次构建（任意参数），参数页从下一次构建起出现新任务。

### 3.9 新增一个主机或主机组

主机组：在 `hosts.yml` 的 `deploy_targets.children` 下加组和主机，加 `group_vars/<组>.yml`（`app_deploy` 需要的全部变量）和每台主机的 `host_vars/<主机>.yml`。然后运行 `ansible-playbook check-inventory.yml --vault-password-file <口令文件>`，确认每台主机的变量齐全、类型正确。参数页同样从下一次构建起出现新组。

## 4. 要点

### 4.1 Jenkins 只能在流水线里实现 abort / warn

规则：每个任务单独调用一次 `ansiblePlaybook`，用 `try/catch` 捕获失败，再按策略调用 `error`（FAILURE，后续 stage 跳过）或 `unstable`（UNSTABLE，继续）。

机制：Ansible 插件在 playbook 返回非 0 时抛出 `hudson.AbortException("Ansible playbook execution failed")`，没有“失败也继续”的选项，也拿不到退出码（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java:328`）。所以参数校验失败、断言失败、主机不可达都表现为同一个异常。实现见 `examples/jenkins/deploy-engine.groovy` 的 `runTask`。

证据：`test_warn_failure_marks_build_unstable_and_continues`（UNSTABLE，随后运行了 `deploy.yml`）、`test_abort_failure_stops_before_deploy_and_later_tasks`（FAILURE，只运行了一次 `run-task.yml`，报告中该任务 `outcome` 为 `stopped`）。

不这样做：在一次 `ansiblePlaybook` 中运行所有任务，就只有一个结果，无法逐个任务决定阻塞或非阻塞。

### 4.2 超时按任务失败处理，手动中止不能被当成失败

规则：每个任务包在 `timeout(time: timeout_minutes)` 中；捕获到 `InterruptedException` 时，已耗时达到超时才按任务失败处理，否则把该任务记为 `ABORTED` 并重新抛出。

机制：`timeout` 中止 `ansiblePlaybook` 与手动中止构建时，从插件抛出的都是普通的 `java.lang.InterruptedException`，不带中止原因；能区分原因的 `FlowInterruptedException.getCauses()` 在沙箱中需要管理员审批。所以引擎只能用已耗时区分（`deploy-engine.groovy` 的 `runTask` 中 `catch (InterruptedException e)` 一段）。

证据：`test_timeout_counts_as_task_failure_and_follows_the_policy`（`timeout_minutes: 1`、任务等待 90 秒、策略 warn：控制台 `[deploy] 任务 task_a 失败：超过 1 分钟未完成（warn：继续执行）`，构建 UNSTABLE 并继续部署）；`test_manual_abort_during_a_task_aborts_the_build_instead_of_continuing`（任务运行中点中止：构建 ABORTED，没有继续部署，报告中该任务为 `ABORTED`）。

不这样做：用 `catchError(catchInterruptions: false)` 包住 `timeout { ansiblePlaybook(...) }`，手动中止也会被捕获，后面的步骤继续执行。实验流水线（沙箱中运行，任务暂停 120 秒，第 5 秒手动中止）：

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

控制台节选：

```
Pausing for 120 seconds
Aborted by unknown
java.lang.InterruptedException
	at org.jenkinsci.plugins.ansible.CLIRunner.execute(CLIRunner.java:52)
Setting overall build result to UNSTABLE
AFTER-WRAPPER elapsedMs=6036 currentResult=UNSTABLE
Finished: ABORTED
```

`catchInterruptions: false` 只对 `FlowInterruptedException` 生效（`.reference-cache/jenkinsci/workflow-basic-steps-plugin@1098.v808b_fd7f8cf4/src/main/java/org/jenkinsci/plugins/workflow/steps/CatchErrorStep.java:229`），而 `ansiblePlaybook` 抛出的不是它。

### 4.3 参数用 JSON 文件传给 Ansible

规则：任务参数写入 `.deploy-run/<阶段>-<id>.json`，用 `extras: '-e @文件'` 传入，不用 `ansiblePlaybook` 的 `extraVars`。

机制：`extraVars` 的每个值先被转成字符串（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/workflow/AnsiblePlaybookStep.java:390-432`；Map 只用于 `[value: …, hidden: …]` 包装一个标量，见同文件 `:399-409`），除 `true` / `false` 外都以 `-e key=value` 传入（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:119-149`）；列表等非标量值被跳过（同文件 `:122-126`）。`key=value` 形式的值在 Ansible 中是字符串。

证据：同一任务分别用 `-e task_a_repeat=2` 和 `-e '{"task_a_repeat": 2, ...}'` 运行：

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

### 4.4 只接受登记过的任务名和参数名，拒绝模板语法

规则：计划中的任务名必须在 `tasks.yml` 中，参数名必须在该 role 的 `argument_specs.yml` 中；字符串参数不能含 `{{`、`{%`、`{#`；参数名以任务名为前缀，不与 inventory 变量、Ansible 保留字、引擎变量重名。

机制：Ansible 会渲染 extra vars 中的 Jinja 表达式，而 extra vars 的优先级最高。不加限制时，能触发构建的人可以在 Jenkins 节点上执行命令，或传入 `ansible_connection` 之类的变量改变连接方式；参数名与 inventory 变量重名时，会静默覆盖 inventory 中的值。

证据：直接把含模板的值交给 Ansible：

```
$ cat vars.json
{"task_role":"task_a","task_hosts":"deploy_targets","task_a_message":"{{ lookup(\"pipe\", \"echo INJECTED\") }}"}
$ ansible-playbook run-task.yml --limit api -e @vars.json
    "msg": "api-01: INJECTED (1/1)"
```

经过引擎时被拒绝：`test_invalid_plan_fails_before_any_playbook_runs` 的 `template-expression`、`template-statement`、`template-comment`、`undeclared-param` 几例，构建 FAILURE 且没有运行任何 playbook。参数名冲突由 `test_option_names_cannot_override_inventory_reserved_or_engine_variables` 检查。

### 4.5 所有校验在运行 playbook 之前完成，并一次列出

规则：`deploy-engine.groovy` 的 `resolvePlan` / `buildPlan` / `buildTask` / `checkValue` 把问题收集到列表，最后调用一次 `error`。可选字段用 `== null` 判断是否提供：Groovy 中 `0`、`''`、`[]`、`[:]` 为假，用 `?:` 会把它们当成未提供而跳过类型检查。

覆盖的问题（`test_invalid_plan_fails_before_any_playbook_runs` 每种一例，共 30 例）：主机组未知、未选、不是列表；`skip_deploy` 不是布尔；阶段不是列表；条目不是任务名或映射；未知任务；任务不能用于该阶段；未知字段；未知或空的策略；必填参数缺失或为空白；`params` 不是映射；未声明的参数；整数、枚举、布尔、字符串类型不符；三种模板语法；同一阶段 `id` 重复、`id` 非法或为空；`timeout_minutes` 不是正整数；计划的未知字段；`DEPLOY_PLAN` 不是映射或不是合法 YAML。表单提交的值另有 4 种（`test_invalid_form_values_fail_before_any_playbook_runs`）。

证据：`test_invalid_plan_lists_all_problems_at_once`，控制台节选：

```
ERROR: [deploy] 计划有误：
  - 未知主机组 [nope]，可用主机组 [web, api, worker]
  - pre[0]：未知任务 'no_such_task'，可用任务 [task_a, task_b, task_c, check_c, check_a]
  - pre[1]：未知策略 'ask'，只能是 abort 或 warn
```

不这样做：参数写错要到部署到一半才发现。Ansible 的 argument_specs 校验仍然保留，作为第二道检查。

### 4.6 参数的类型、说明来自 argument_specs，运行时默认值来自 defaults/main.yml

规则：不在 Jenkins 中重复声明任务参数。`deploy-engine.groovy` 的 `loadContext` 用 `readYaml` 读取每个任务 role 的 `meta/argument_specs.yml`（名称、类型、必填、choices、说明）和 `defaults/main.yml`（默认值）。argument_specs 中也写 `default`，与 `defaults/main.yml` 相同。

机制：role 开始执行前，Ansible 按 `argument_specs.yml` 自动插入一个 `validate_argument_spec` 任务（`.reference-cache/ansible/ansible@2.21.5/lib/ansible/playbook/role/__init__.py:363-371`）。argument_specs 中的 `default` 用于文档，文档要求它与代码中的默认值一致，而实际默认值总是取自 role 的 defaults（`.reference-cache/ansible/ansible-documentation@b16dcb8/docs/docsite/rst/playbook_guide/playbooks_reuse_roles.rst:399-401`）。不写 `default` 时，`ansible-doc -t role` 显示的默认值是 null。

证据：`test_documented_defaults_equal_role_defaults_and_cover_every_optional_option`；`test_role_defaults_apply_when_jenkins_passes_no_value`；参数页上的预填值即 defaults（`test_selecting_a_task_shows_only_its_fields_prefilled_with_role_defaults`）。

### 4.7 第一次运行只登记参数；`params` 必须在 `properties()` 之前读取

规则：Jenkinsfile 把 `params` 作为实参传给 `prepareRequest`：实参在函数执行前求值，也就早于函数中的 `properties()`。参数尚未登记时（`params` 中没有 `DEPLOY_PLAN`，两种参数页都登记它）构建结果设为 NOT_BUILT，不运行任何 playbook。

机制：`params` 每次读取时，除了本次构建提交的值，还会用 job **当前**的参数定义补上默认值（`.reference-cache/jenkinsci/workflow-cps-plugin@4395.v738456a_5da_6b_/plugin/src/main/java/org/jenkinsci/plugins/workflow/cps/ParamsVariable.java:59-75`）。`properties()` 执行后，第一次运行的 `params` 就有了 `DEPLOY_PLAN=''` 等默认值，无法判断参数页是否提交过。

证据：在 `properties()` 之后读取 `params` 时，第一次运行得到 `ERROR: [deploy] 计划有误： - 没有选择主机组`、结果 FAILURE；按现在的写法为 NOT_BUILT（`test_first_build_registers_parameters_from_catalog_and_inventory_and_runs_nothing`）。

### 4.8 Ansible 的配置通过 `ANSIBLE_CONFIG` 传入

规则：`deploy-engine.groovy` 的 `runPlaybook` 用 `withEnv(["ANSIBLE_CONFIG=<工作区>/<baseDir>/ansible/ansible.cfg"])` 包住每次 `ansiblePlaybook` 调用，并统一传入 `vaultCredentialsId` 与 `credentialsId`。

机制：插件在 Jenkins 工作区根目录（仓库根目录）启动 ansible-playbook（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/CLIRunner.java:47`），那里没有 `ansible.cfg`。ansible-core 先查 `ANSIBLE_CONFIG`，再查当前目录的 `ansible.cfg`（`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:266-296`）；`ansible.cfg` 中 `inventory`、`roles_path` 的相对路径以该文件所在目录为基准展开：读到的值以配置文件为来源（`.reference-cache/ansible/ansible@2.21.5/lib/ansible/config/manager.py:672`、`:697`），来源文件成为基准（同文件 `:128`），`pathlist` / `pathspec` 类型的值据此展开（同文件 `:190-202`，`.reference-cache/ansible/ansible@2.21.5/lib/ansible/utils/path.py:49-56`）。所以同一个文件既给 Jenkins 用，也给在 `examples/ansible` 下手工运行用。详见 [attachments/inventory-practices.md](attachments/inventory-practices.md) 第 1.2 节。

证据：第 3.7 节的命令行没有 `-i`，inventory 与 roles 仍被找到。

### 4.9 每次运行都需要 vault 口令，包括只在本机运行的任务；凭据先检查

规则：所有 `ansiblePlaybook` 调用都传 `vaultCredentialsId`。`prepareRequest` 先用 `withCredentials` 绑定一次两个凭据，凭据不存在时构建在运行任何 playbook 之前失败。

插件接受 Secret text 和 Secret file 两种 vault 凭据，示例的检查按 Secret text 绑定；用 Secret file 时把 `checkCredentials` 中的 `string(...)` 换成 `file(...)`。

机制：插件把 Secret text / Secret file 凭据写成工作区中的临时文件，以 `--vault-password-file` 传入，结束后删除（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AbstractAnsibleInvocation.java:268-280`、`:318`）。加载 inventory 时就要解密 `group_vars/all/vault.yml`，与 play 作用于哪些主机无关。凭据 ID 写错时插件不报错，只是不传口令（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java:303-317`），随后 ansible-playbook 因无法解密而失败，看起来与任务失败相同：只有 `warn` 任务时，构建只会变成 UNSTABLE。

证据：`test_every_run_needs_the_vault_password_even_on_localhost`：不给口令运行 `task_b`（只作用于 localhost）失败，`[ERROR]: Attempting to decrypt but no vault secrets found.`，没有运行任何任务。`test_missing_vault_credential_stops_the_build_before_any_playbook_runs`：删除凭据后构建 FAILURE，没有运行任何 playbook。

### 4.10 方案 C 等待 `input` 时不占执行器

规则：`Jenkinsfile.no-plugin` 顶层 `agent none`；读取文件和登记参数的 `Prepare`、运行 playbook 的 `Run` 各自 `agent any`；可能等待 `input` 的 `Task options` 不分配 agent。`Run` 的 `when` 加 `beforeAgent true`，计划为空时不分配执行器。`Prepare` 把本目录 `stash`，`Run` 用 `skipDefaultCheckout()` 关闭默认检出，改为 `unstash`。

机制：顶层 `agent none` 时不会无谓地分配执行器（`.reference-cache/jenkins-infra/jenkins.io@d471394/content/doc/book/pipeline/syntax.adoc:365`）；`beforeAgent true` 让 `when` 在进入 agent 之前求值（同文件 `:1176-1177`）。`resolvePlan` 只用 `readYaml(text:)`、`input`、`timeout`、`echo`、`error`，都不需要工作区。

证据：`test_no_plugin_waiting_for_input_holds_no_executor`：构建停在 `input` 时，Jenkins 的忙碌执行器数为 0。

不这样做：顶层 `agent any` 时，等人填写表单的 30 分钟内一直占着一个执行器和工作区。`Run` 若照常检出，检出的是分支的最新提交，而不是 `Prepare` 读取并校验过的那一个。实验：一个同样结构的 job，在构建等待 `input` 时向分支推送新提交，控制台节选：

```
Checking out Revision 982843ad1a41f22acf98f372c35e5bed428cc2cb (refs/remotes/origin/main)
A HEAD=982843ad1a41f22acf98f372c35e5bed428cc2cb
Proceed or Abort
Checking out Revision 207de2cb2d4013bcfcc6bb97a22b007c3acadb29 (refs/remotes/origin/main)
C HEAD=207de2cb2d4013bcfcc6bb97a22b007c3acadb29
```

改用 `stash` / `unstash` 后，`test_no_plugin_runs_the_files_it_validated_even_if_the_branch_moves_while_waiting`：等待期间向测试仓库提交对 `hosts.yml` 的修改，`task_b` 报告的仍是原文件的大小。

### 4.11 禁止同一 job 的并发构建

规则：`properties()` 中加 `disableConcurrentBuilds()`。新构建在上一次结束前排队等待。

机制：见 `.reference-cache/jenkins-infra/jenkins.io@d471394/content/doc/book/pipeline/syntax.adoc:624-626`。不这样做，两次构建可能同时部署同一批主机。

## 5. 陷阱

| 做法 | 现象 | 解决 |
| --- | --- | --- |
| 在 `properties()` 之后读取 `params` 判断是否第一次运行 | 第一次运行报“没有选择主机组”并失败 | 把 `params` 作为实参传给 `prepareRequest`（4.7） |
| 用 `catchError(catchInterruptions: false)` 区分超时与手动中止 | 手动中止后流水线继续执行 | `ansiblePlaybook` 抛出普通 `InterruptedException`，按已耗时区分（4.2） |
| Active Choices 勾选框的文字含全角标点（`（`、`：`、`！`）或 `@ = + "` | 勾选框旁显示 `web&#xff08;web-01&#xff09;` 之类的实体文字 | 勾选框文字只用半角符号；`tasks.yml` 的 `description` 同样。原因：沙箱脚本的返回值都经过插件的 HTML 过滤（`.reference-cache/jenkinsci/active-choices-plugin@2.8.11/src/main/java/org/biouno/unochoice/model/GroovyScript.java:191-192`、`:232-250`），把这些字符转成实体；勾选框的文字随后又被页面模板转义一次。HTML 表单（`*_TASK_OPTIONS`）中的实体会被浏览器还原，不受影响 |
| 动态 HTML 表单用 `fieldset` / `legend` / `label`、`title` 或 `aria-*` 属性 | 这些标签和属性消失，输入框没有可访问名称 | 过滤器只放行基本标签及 `input`、`select`、`option`、`textarea` 和少数属性（`.reference-cache/jenkinsci/active-choices-plugin@2.8.11/src/main/java/org/biouno/unochoice/util/SafeHtmlExtendedMarkupFormatter.java:54-59`）；分组用 `div` 加 `style`，必填用文字“（必填）”标出，文本框和数字框用 `placeholder` 写参数名作为可访问名称。下拉框没有能保留下来的命名属性；需要读屏软件时用方案 C 的 `input` 表单 |
| 给下拉框加 `class="jenkins-select__input"` 想得到 Jenkins 的样式 | 下拉箭头消失 | 这个类依赖外层的 `<div class="jenkins-select">`，而过滤器会去掉 `div` 的 `class`；不加类，用浏览器默认样式 |
| 方案 C 的 `Run` stage 再次默认检出 | 等待 `input` 期间分支有新提交时，运行的文件与校验过的不同 | `Prepare` 中 `stash`，`Run` 中 `skipDefaultCheckout()` 加 `unstash`（4.10） |
| 先填写某个任务的参数，再勾选另一个任务 | 已填写的值全部恢复为默认值 | 改变勾选会重新生成整个表单。先勾选全部任务，再填写参数（`test_changing_the_selection_regenerates_the_form_and_drops_typed_values`） |
| 方案 C 中只有一个输入项 | `input` 返回该值本身而不是 Map，按 Map 处理时报错 | 只有一个输入项时自己组成 Map（`basicSelection`）；`input` 的行为见 `.reference-cache/jenkinsci/pipeline-input-step-plugin@560.v56198a_642157/src/main/java/org/jenkinsci/plugins/workflow/support/steps/input/InputStepExecution.java:256-260`。`check_c` 没有参数，所以只勾选它时表单只有一项（`test_no_plugin_form_with_a_single_field_returns_its_value`） |
| 用 `x ?: 默认值` 取可选字段 | `timeout_minutes: 0`、`policy: ''`、`params: []` 不报错，静默改用默认值 | Groovy 中 `0`、`''`、`[]` 为假；用 `x == null ? 默认值 : x`，再做类型检查（4.5） |
| 在 `DEPLOY_PLAN` 中给字符串参数写未加引号的 `2026-01-01`、`yes` | 不加检查时到达 Ansible 的是 `Thu Jan 01 00:00:00 UTC 2026`、`true` | YAML 解析时已变成日期、布尔；引擎只接受字符串和整数，提示加引号 |
| 表单中某个值含 `,@@` | 值被切断，后半段被当成另一个字段 | 提交值用 `,@@` 切分，无法区分；引擎在字段重复时报错（`test_invalid_form_values_fail_before_any_playbook_runs`） |
| 在部署前、部署后运行同一任务，并要求 id 全局唯一 | 计划校验报 id 重复 | id 只需在一个阶段内唯一：stage 名、参数文件名、报告都带阶段（`test_skipped_deploy_still_runs_pre_and_post_tasks`） |
| 用 `extraVars` 或 `-e key=value` 传整数 | 任务中 `>=` 比较报 `'>=' not supported between instances of '_AnsibleTaggedStr' and 'int'` | 用 JSON 文件 `-e @file`（4.3） |
| 只在 argument_specs 中写 `default`，没有 `defaults/main.yml` | `'xxx' is undefined` | 运行时默认值写在 `defaults/main.yml`，argument_specs 中的 `default` 与之相同（4.6） |
| `assert` 的 `success_msg` 引用失败时不存在的值，如 `stat.size` | 断言失败时报 `object of type 'dict' has no attribute 'size'`，真正的失败原因被掩盖 | ansible-core 2.21 中 `success_msg` 在断言失败时也会被渲染；加 `default`，如 `task_b_file.stat.size \| default(0)` |
| 用 Jinja 测试 `is between(1, 5)` | `No test named 'between'` | 写成两个比较：`x >= 1`、`x <= 5` |
| `ansible-lint` 检查 `run-task.yml` | `syntax-check[specific]: ... 'task_role' is undefined` | 在 `ansible/.ansible-lint` 中用 `extra_vars` 给出示例值（`.reference-cache/ansible/ansible-lint@26.10.1/src/ansiblelint/runner.py:449-450`） |
| 非阻塞任务失败后看到控制台中的 `FATAL: command execution failed` 和 Java 堆栈 | 误以为构建失败 | playbook 失败时插件抛出的 `AbortException` 是 `IOException` 的子类（`.reference-cache/jenkinsci/jenkins@2.580.1/core/src/main/java/hudson/AbortException.java:36`），被插件自己的 `catch (IOException)` 先打印成 FATAL 和堆栈再抛出（`.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86/src/main/java/org/jenkinsci/plugins/ansible/AnsiblePlaybookBuilder.java:327-333`）。以构建结果为准；真正原因看它上方 Ansible 的 `fatal: [主机]: FAILED! => {... "msg": ...}` |

## 6. 检查

修改后运行：

```bash
cd jenkins-ansible-deploy/examples
python3 -m pytest tests -m 'not jenkins'      # inventory、playbook、任务目录约定、ansible-lint
python3 -m pytest tests                        # 再加 Jenkins 与浏览器集成测试，需要第 2 节的本地 Jenkins
```

只检查 inventory（在 `examples/ansible` 下）：

```bash
ansible-inventory --graph deploy_targets
ansible-playbook check-inventory.yml --vault-password-file ../tests/example-vault-password.txt
ANSIBLE_VAULT_PASSWORD_FILE=../tests/example-vault-password.txt ansible-lint --offline --profile production
```

`check-inventory.yml` 不连接主机，对每台发布对象按 `app_deploy` 的 argument_specs 校验，一次报告所有主机的问题，例如把 `web` 的 `app_port` 改成 `eighty`、删掉 `api` 的 `app_version` 时：

```
argument 'app_port' is of type str and we were unable to convert to int
missing required arguments: app_version
```

`web-01`、`web-02`、`api-01` 失败，`worker-01`、`worker-02` 通过（`test_check_inventory_reports_every_broken_host_in_one_run`）。

## 7. 附件

- [attachments/inventory-practices.md](attachments/inventory-practices.md)：inventory 管理的调研结论——目录与变量分层、变量优先级与命名、ansible-vault 与 Jenkins 凭据、`ansible-inventory` / argument_specs / ansible-lint 校验及其在 CI 中的用法。设计或修改 inventory 前读。
- [attachments/parameter-page.md](attachments/parameter-page.md)：两种参数页的界面与实现细节——Active Choices 参数的定义、表单 HTML、提交值的格式与解析，方案 C 的 `input` 表单。修改参数页或排查参数传递问题时读。

## 8. 出处

文中 `.reference-cache/<owner>/<name>@<version>/<path>:<行号>` 指下表中上游仓库在该版本的同一路径与行号：

| 出处前缀 | 上游仓库 | 标签或分支 | 提交 |
| --- | --- | --- | --- |
| `.reference-cache/ansible/ansible@2.21.5` | https://github.com/ansible/ansible | `v2.21.5` | `3d36adf9a541aba1ed5f37aa124170f1f2df0a65` |
| `.reference-cache/ansible/ansible-documentation@b16dcb8` | https://github.com/ansible/ansible-documentation | `stable-2.21` | `b16dcb8e8b5b5658ae9691b8632b7046e879606b` |
| `.reference-cache/ansible/ansible-lint@26.10.1` | https://github.com/ansible/ansible-lint | `v26.10.1` | `9bce2bfb8f4a984e6b13767bc0d6bf0a49492164` |
| `.reference-cache/jenkinsci/jenkins@2.580.1` | https://github.com/jenkinsci/jenkins | `jenkins-2.580.1` | `500c80a425077d987b97f62be32887d4f32e9ffb` |
| `.reference-cache/jenkinsci/ansible-plugin@635.v34b_48e979c86` | https://github.com/jenkinsci/ansible-plugin | `635.v34b_48e979c86` | `34b48e979c862f28ee5bec47e929953267a71a0f` |
| `.reference-cache/jenkinsci/active-choices-plugin@2.8.11` | https://github.com/jenkinsci/active-choices-plugin | `uno-choice-2.8.11` | `d8564cbc7ea72c6020a7942370581774e9804b07` |
| `.reference-cache/jenkinsci/workflow-cps-plugin@4395.v738456a_5da_6b_` | https://github.com/jenkinsci/workflow-cps-plugin | `4395.v738456a_5da_6b_` | `738456a5da6b30900f4b87dcdbf49d8555fff293` |
| `.reference-cache/jenkinsci/workflow-basic-steps-plugin@1098.v808b_fd7f8cf4` | https://github.com/jenkinsci/workflow-basic-steps-plugin | `1098.v808b_fd7f8cf4` | `808bfd7f8cf4fb7d1b32e20db9bcb586cf9ac891` |
| `.reference-cache/jenkinsci/pipeline-input-step-plugin@560.v56198a_642157` | https://github.com/jenkinsci/pipeline-input-step-plugin | `560.v56198a_642157` | `56198a642157f4648edd05bee6470b729ec09df7` |
| `.reference-cache/jenkinsci/pipeline-utility-steps-plugin@3.810.va_7672d206740` | https://github.com/jenkinsci/pipeline-utility-steps-plugin | `3.810.va_7672d206740` | `a7672d2067401a3e191836df55b4282036edb01d` |
| `.reference-cache/jenkinsci/workflow-multibranch-plugin@842.v3a_b_59b_57b_e6e` | https://github.com/jenkinsci/workflow-multibranch-plugin | `842.v3a_b_59b_57b_e6e` | `3ab59b57be6e59ebfa0e273a5743b02529476334` |
| `.reference-cache/jenkins-infra/jenkins.io@d471394` | https://github.com/jenkins-infra/jenkins.io | `master` | `d4713943384e705fd7dd2f645555083b247e61b4` |

缓存是完整检出后删除 `.git`、CI 与打包配置、二进制文件得到的，文本文件的路径和行号与上游一致。
