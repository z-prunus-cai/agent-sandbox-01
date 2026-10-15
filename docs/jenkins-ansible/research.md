# 给已有 Jenkins 部署流水线添加 Ansible 部署前/部署后检查

## 需求

给一个已有的 Jenkins 部署流水线加上用 Ansible 执行的部署前检查和部署后检查：

1. **阻塞 / 非阻塞可配置**：检查不通过时，可以停止流水线，也可以只记录然后继续。
2. **每次部署可以任意选择**：比如这次部署前检查 a、b，部署后检查 x、y；下一次只做其中一部分，或换成别的检查。
3. **检查可以带参数**。

## 结论

- Jenkins Ansible 插件本身**没有“检查”这个概念**，也没有类似 `sh(returnStatus: true)` 的选项。只要 `ansiblePlaybook` 退出码不是 0，插件就抛出 `AbortException`，流水线随之失败。所以“阻塞 / 非阻塞”只能在 Jenkins 这一层实现：捕获这个异常，再根据策略调用 `error` / `unstable` / `input`。
- 推荐的做法：
  - **每个检查写成一个独立的 playbook**，用 `ansible.builtin.assert`、`wait_for`、`uri` 等模块判断条件，不满足就让 playbook 失败。
  - 用一个**检查目录** `catalog.yaml` 登记所有可用的检查，包括 playbook 路径、默认策略、允许的参数和默认值。
  - 用一个**共享库步骤** `deployChecks` 读取本次部署的选择，逐个调用 `ansiblePlaybook`，再按策略处理失败。
  - 每次部署选哪些检查，通过**构建参数**指定。
- 对已有流水线的改动：加载共享库，加 4 个参数、3 个 stage 和一个 `post`。原有的 Deploy 阶段和部署 playbook 都不用改。
- 示例在 [`examples/deploy-checks`](../../examples/deploy-checks) 下，已在本地 Jenkins 2.580.1 + Ansible 插件 635.v34b_48e979c86 + ansible-core 2.21.5 上实际跑了 20 次构建，覆盖了所有策略和各种错误情况（见[第 6 节](#6-验证)）。

---

## 1. 下载的文档

文档放在 [`docs/jenkins-ansible/upstream`](upstream)，索引和来源见 [README](README.md)。包括：

- **Jenkins 官方文档全集**（jenkins.io `/doc`，359 篇），以及 Pipeline 核心插件的步骤参考；
- **Ansible 官方文档全集**（docs.ansible.com 的源文件，411 篇），以及用 ansible-core 2.21.5 的 `ansible-doc` 生成的全部 `ansible.builtin` 模块、插件和 Playbook 关键字参考；
- **Jenkins Ansible 插件文档**：README、步骤参考、各字段的在线帮助。

本报告主要用到的文档：[`catchError` / `warnError` / `unstable` / `timeout`](upstream/jenkins/pipeline-steps/workflow-basic-steps.md)、[`input`](upstream/jenkins/pipeline-steps/pipeline-input-step.md)、[`readYaml`](upstream/jenkins/pipeline-steps/pipeline-utility-steps.md)、[Pipeline 语法](upstream/jenkins/handbook/book/pipeline/syntax.adoc)、[共享库](upstream/jenkins/handbook/book/pipeline/shared-libraries.adoc)、[Ansible 错误处理](upstream/ansible/docsite/playbook_guide/playbooks_error_handling.rst)、[变量优先级](upstream/ansible/docsite/reference_appendices/general_precedence.rst)，以及 [`assert`](upstream/ansible/builtin-plugins/module/assert.txt)、[`uri`](upstream/ansible/builtin-plugins/module/uri.txt)、[`wait_for`](upstream/ansible/builtin-plugins/module/wait_for.txt) 等模块。

## 2. Ansible 插件中与本需求有关的事实

下面的结论来自插件源码（提交 `27b52e9`）和实际运行结果，不只是文档里的描述。

| # | 事实 | 依据 | 影响 |
| - | --- | --- | --- |
| 1 | 提供三个 Pipeline 步骤：`ansiblePlaybook`、`ansibleAdhoc`、`ansibleVault` | [步骤参考](upstream/ansible-plugin/jenkins-io-pipeline-steps-ansible.md) | 检查用 `ansiblePlaybook` 来跑；只想快速看一个命令结果时也可以用 `ansibleAdhoc` |
| 2 | playbook 失败时抛出 `hudson.AbortException("Ansible playbook execution failed")`。**没有 `returnStatus` / `propagate` 之类的选项**，退出码也拿不到：断言失败（rc=2）和主机不可达（rc=4）看起来一样 | `AnsiblePlaybookBuilder.java:326-328` | 非阻塞只能用 `try/catch`、`catchError` 或 `warnError` 实现。主机不可达也会被算作“检查失败” |
| 3 | 失败时插件会先在日志里打印 `FATAL: command execution failed` 和堆栈，然后才抛出异常（`AbortException` 是 `IOException` 的子类，被插件自己的 `catch (IOException)` 打印后再抛出） | `AnsiblePlaybookBuilder.java:330-333`；构建 #2 日志 | **非阻塞检查失败时日志里也会有 FATAL 和堆栈**，但这不代表构建失败，构建结果以 UNSTABLE / SUCCESS 为准 |
| 4 | `extraVars` 只支持 String / Boolean / Number 类型；列表或 Map 会被丢弃，只打印一行 `[WARN] Omitting extra var`。参数以 `-e key=value` 形式传入，所以**数字到了 Ansible 里会变成字符串**；值默认 `hidden: true`，日志中显示为 `********` | `AnsiblePlaybookStep.java:390-433`、`AbstractAnsibleInvocation.java:119-150` | 检查参数改为写入 JSON 文件，再用 `extras: '-e @file.json'` 传入，这样能保留数字、布尔、列表等类型 |
| 5 | `extraVars` 的值和 `extras` 字符串都会经过 `envVars.expand`，`${VAR}` 会被替换成 Jenkins 环境变量的值；`extras` 还会按空白字符拆分 | `AbstractAnsibleInvocation.java:129,161` | 传给 `extras` 的文件路径不能带空格（示例使用工作区内的相对路径） |
| 6 | ansible-playbook 的工作目录是 Jenkins 工作区 | `CLIRunner.java:47` | playbook 和变量文件都可以用相对于仓库根目录的路径 |
| 7 | 构建的环境变量会传给 ansible 进程，playbook 中可以用 `lookup('env', 'BUILD_TAG')` 读取 | 插件 README | 可以借此传上下文；但示例里用显式传入的变量（`deploy_version`）更清晰 |
| 8 | 支持 `limit`、`tags`、`skippedTags`、`checkMode`、`forks`、`credentialsId`、`vaultCredentialsId` 等参数 | 步骤参考 | 每个检查可以单独指定 `limit`，只检查部分主机 |
| 9 | 当 `timeout` 取消正在运行的 `ansiblePlaybook` 时，抛出的是**普通的 `java.lang.InterruptedException`**（来自 `Proc.join`），而不是带 `ExceededTimeout` 原因的 `FlowInterruptedException` | 构建 #14 日志 | 不能只靠异常类型区分“超时”和“手动中止”。示例结合已耗时一起判断（见 4.4） |

另外有两个 Ansible 本身的坑，都实测过：

- **Ansible 会渲染 extra vars 中的 Jinja 表达式。** 例如在 ansible-core 2.21.5 下传入 `-e @v.json`，其中 `{"path": "/tmp/{{ lookup('pipe', 'echo INJECTED') }}"}`，输出是 `/tmp/INJECTED`。也就是说，如果构建参数原样传给 Ansible，能触发构建的人就能**在 Jenkins 节点上执行任意命令**。所以示例拒绝任何包含 `{{`、`{%`、`{#` 的参数值。
- **有些变量名是 Ansible 的保留字**，比如 `port`、`retries`，用作变量会产生 `Found variable using reserved name` 警告。检查参数命名时应避开，示例中用的是 `target_port`、`max_attempts`。可以用下面的命令检查：
  `python3 -c "from ansible.vars.reserved import get_reserved_names; print(sorted(get_reserved_names()))"`

## 3. 方案结构

```
已有仓库
├── Jenkinsfile                      ← 改动：加载共享库，加 4 个参数、3 个 stage、post
├── ansible/deploy.yml               ← 原部署 playbook，不改
├── ansible/checks/*.yml             ← 新增：每个检查一个 playbook
└── checks/catalog.yaml              ← 新增：检查目录（可用检查、默认策略、参数白名单与默认值）

共享库（单独的仓库，或像示例一样放在子目录中）
└── vars/deployChecks.groovy         ← 新增：resolve / runPhase / report
```

流水线结构：

```
Resolve checks ──► Pre-deploy checks ──► Deploy（原有）──► Post-deploy checks ──► post { always: report }
（解析并校验计划）  （每个检查一个子 stage）                （每个检查一个子 stage）   （汇总并归档 JSON）
```

几个设计决定：

- **把解析和校验放在最前面**：检查名、参数名写错，或者参数里带了 Jinja，构建会在部署开始前就失败，不会出现部署了一半才发现检查配置错误的情况。
- **每个检查单独调用一次 `ansiblePlaybook`**：这样每个检查有自己的结果、策略、超时、`limit`，在 Stage View / Blue Ocean 里也各自显示为一个 stage。代价是每次调用都要启动一次 ansible-playbook（几秒钟），对部署来说可以接受。
- **由 Jenkins 决定阻塞还是非阻塞，playbook 只负责判断是否通过**：同一个检查 playbook 这次可以是阻塞的，下次可以是非阻塞的，不需要改 playbook。

## 4. 三个需求分别如何实现

### 4.1 阻塞 / 非阻塞

每个检查有一个 `policy`。默认值在 `catalog.yaml` 中定义，每次部署可以覆盖。失败时的处理如下：

| 策略 | 检查失败时 | 构建结果 | 后续 stage | 使用的 Jenkins 步骤 |
| --- | --- | --- | --- | --- |
| `abort` | 阻塞：停止流水线 | FAILURE | 跳过 | `error` |
| `warn` | 非阻塞：记录警告，继续执行 | UNSTABLE（当前 stage 显示为黄色） | 继续 | `unstable` |
| `ignore` | 非阻塞：只记录 | 不变 | 继续 | `echo` |
| `ask` | 暂停，等人决定 | 选“继续”为 UNSTABLE；选“中止”或等待超时为 ABORTED | 取决于选择 | `input` + `timeout` |

核心逻辑（简化后，完整代码见 [`deployChecks.groovy`](../../examples/deploy-checks/jenkins-shared-library/vars/deployChecks.groovy)）：

```groovy
try {
    timeout(time: check.timeoutMinutes, unit: 'MINUTES') {
        ansiblePlaybook(playbook: check.playbook, inventory: ..., extras: "-e @${varsFile}")
    }
} catch (hudson.AbortException e) {          // playbook 返回非 0
    failure = e.message
}
if (failure) {
    switch (check.policy) {
        case 'abort':  error "检查 ${check.id} 未通过（阻塞）"; break
        case 'warn':   unstable "检查 ${check.id} 未通过（非阻塞）"; break
        case 'ignore': echo "检查 ${check.id} 未通过（已忽略）"; break
        case 'ask':
            timeout(time: 30, unit: 'MINUTES') { input message: "检查未通过，是否继续？", ok: '继续' }
            unstable "检查 ${check.id} 未通过（人工确认继续）"
    }
}
```

Jenkins 自带的两个步骤也能实现非阻塞：`warnError('msg') { ... }`，等价于 `catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE')`；以及 `catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') { ... }`。只有一两个固定的检查时，直接用它们最简单：

```groovy
stage('Pre-check: disk') {
    steps {
        warnError('磁盘检查未通过') {             // 非阻塞
            ansiblePlaybook(playbook: 'checks/disk_space.yml', inventory: 'hosts.ini')
        }
    }
}
```

但它们会捕获**所有**异常，包括用户手动中止（默认 `catchInterruptions: true`），而且没法实现 `ask`。示例改用 `try/catch`，只捕获 `AbortException`（检查失败）和超时，手动中止照常让构建变成 ABORTED（构建 #18 验证过）。

关于部署后检查的“阻塞”：部署已经完成了，`abort` 并不能撤销部署，它的作用是让构建失败，并阻止后面的 stage（比如推广到下一个环境）继续执行。需要回滚的话，可以在 `post { failure { ... } }` 中调用回滚 playbook。注意部署前检查失败也会进入 `failure`，所以需要先判断部署是否已经执行过。

### 4.2 每次部署任意选择检查

用 4 个构建参数，从简单到复杂：

| 参数 | 类型 | 格式 | 示例 |
| --- | --- | --- | --- |
| `PRE_CHECKS` | string | `名称[:策略]`，用逗号或空格分隔。留空使用 `catalog.yaml` 中的 `defaults.pre`，填 `none` 表示不检查 | `disk_space, service_running:warn` |
| `POST_CHECKS` | string | 同上 | `http_health, app_version:abort` |
| `CHECK_PARAMS` | text（YAML） | 按检查名覆盖参数 | `disk_space: {path: /var, min_free_mb: 2048}` |
| `CHECK_PLAN` | text（YAML） | 高级用法：完整的计划，非空时忽略上面三个参数。可以让同一个检查跑多次（用 `id` 区分），也可以单独设置 `limit`、`timeout_minutes` | 见下 |

```yaml
# CHECK_PLAN 示例
pre:
  - disk_space                                     # 全部用默认值
  - {name: port_reachable, id: db,    policy: warn, params: {target_port: 5432}}
  - {name: port_reachable, id: redis, params: {target_port: 6379}}
post:
  - {name: http_health, policy: ask, limit: web1}  # 只检查 web1，失败时人工决定
  - {name: app_version, policy: abort}
```

从其他 job 触发时也一样传这些参数：

```groovy
build job: 'deploy', parameters: [
    string(name: 'APP_VERSION', value: '1.2.3'),
    string(name: 'PRE_CHECKS', value: 'disk_space'),
    string(name: 'POST_CHECKS', value: 'http_health:warn'),
    text(name: 'CHECK_PARAMS', value: 'disk_space: {min_free_mb: 4096}'),
]
```

其他选择方式：

- 每个检查一个 `booleanParam`：界面上是勾选框，最直观；但策略和参数要另外配置，而且每新增一个检查都要改 Jenkinsfile。
- [Active Choices](https://plugins.jenkins.io/uno-choice/) 插件：可以根据 `catalog.yaml` 动态生成多选框。需要额外安装插件，并且要在脚本审批中批准它的 Groovy 脚本。

### 4.3 检查参数

每个检查的参数值按以下顺序合并，后面的覆盖前面的：

1. `catalog.yaml` 中 `checks.<name>.params` 的默认值。**这里同时也是白名单**，没有列出的参数名会被拒绝。
2. 本次部署的 `CHECK_PARAMS`，或 `CHECK_PLAN` 中该条目的 `params`。
3. 共享库自动加入的上下文变量：调用方传入的 `context`（示例中是 `deploy_version`），以及 `check_id`、`check_phase`。

合并结果写入 `.deploy-checks/<phase>-<id>.json`，再通过 `-e @文件` 传给 Ansible。因为是 JSON，数字、布尔、列表都能保持原来的类型（对比第 2 节第 4 条）。另外，用 `required` 列出的参数如果最终值为空，构建会在 `Resolve checks` 阶段就失败。

**为什么要有参数白名单？** extra vars 在 Ansible 变量优先级中最高。如果允许传任意变量名，就可以传入 `ansible_ssh_common_args`、`ansible_become_password`、`ansible_connection` 等连接变量，改变 Ansible 的连接方式，甚至在 Jenkins 节点上执行命令。再加上第 2 节提到的 Jinja 渲染问题，示例的做法是：**只接受目录中登记过的检查名和参数名，并拒绝包含模板语法的值**。

一个需要注意的地方：catalog 中的默认值同样是以 extra vars 的形式传入的，优先级最高，会覆盖 inventory 中的 `group_vars` / `host_vars`。如果某个参数需要按环境取不同的值，可以考虑两种做法：在 Jenkinsfile 中按环境拼好 `CHECK_PARAMS` 的默认值；或者改造共享库，让值为 `null` 的参数不传给 Ansible，同时让 playbook 回退到 inventory 变量，比如写成 `{{ target_port | default(db_port) }}`。

### 4.4 超时

每个检查包在 `timeout(time: timeout_minutes)` 中，超时按检查失败处理，再交给 `policy` 决定（构建 #17：`warn` 策略的检查超时后，构建为 UNSTABLE，部署继续）。

前面第 2 节第 9 条提到，插件在超时被取消时抛出的是普通的 `InterruptedException`。示例的判断方式是：如果异常是带原因的 `FlowInterruptedException`，就看原因是否为 `ExceededTimeout`；否则，已耗时达到超时时间就视为超时，没达到就视为手动中止，原样抛出。最好在 playbook 内部也设置更短的超时（如 `wait_for.timeout`、`uri` 的重试次数），把 Jenkins 的 `timeout` 当作最后一道保险。

## 5. 其他方案对比

| 方案 | 做法 | 优点 | 缺点 | 适用场景 |
| --- | --- | --- | --- | --- |
| **A. 每个检查一个 playbook，由 Jenkins 决定策略（推荐，即示例）** | 见上 | 每个检查的结果、策略、超时、`limit` 互相独立；playbook 与策略解耦；Jenkins 界面中每个检查一个 stage | 每个检查都要启动一次 ansible-playbook | 大多数情况 |
| B. 一个检查 playbook，用 tags 选择 | `ansiblePlaybook(playbook: 'checks.yml', tags: params.PRE_CHECKS)` | 只调用一次，速度快；改动最少 | 一次调用只能有一个结果，阻塞/非阻塞只能按整批设置；检查参数会混在同一个变量空间里 | 检查都很轻量、策略都相同 |
| C. 在 Ansible 内部处理阻塞 | 每个检查任务写 `ignore_errors: "{{ not blocking }}"`，或用 `block/rescue` 记录失败后继续 | 不依赖 Jenkins，也能在命令行中使用 | 非阻塞失败后 playbook 仍然返回 0，Jenkins 不知道有检查失败，没法标记 UNSTABLE（除非额外解析输出）；实现不了 `ask` | 检查必须脱离 Jenkins 运行 |
| D. 不改原 job，加一个外层编排 job | 新建 job：先跑检查，再 `build job: '原部署 job'`，然后跑部署后检查 | 原有 Jenkinsfile 一行都不用改 | 多一个 job，参数要透传；`build` 等待期间外层 job 会占用一个执行器 | 原流水线不能改，或归其他团队维护 |

方案 B 中，一个 playbook 里不同 tag 的检查想要不同策略，可以用 `block` + `rescue` + `set_stats` 收集失败项，再让 Jenkins 解析输出。但这会把方案 A 中 Jenkins 已经能做的事再在 Ansible 里实现一遍，不推荐。

## 6. 验证

环境：Jenkins 2.580.1（内置节点）、Ansible 插件 635.v34b_48e979c86、pipeline-utility-steps、ansible-core 2.21.5。共享库按“全局受信任库”配置，`inventories/demo` 指向本机。

| 构建 | 场景 | 输入 | 结果 |
| --- | --- | --- | --- |
| #1, #15 | 默认检查 | 所有参数留空 | SUCCESS，pre 2 项、post 2 项全部通过 |
| #2 | 非阻塞 `warn` | `PRE_CHECKS=disk_space, service_running:warn`（容器中没有 nginx） | **UNSTABLE**，Deploy 继续执行 |
| #3 | 阻塞 `abort`（部署前） | `disk_space` 要求 99999999 MB | **FAILURE**，Deploy 和部署后检查被跳过 |
| #4 | `ignore` | `service_running:ignore` | **SUCCESS**，失败只记录在汇总中 |
| #5 | 阻塞 `abort`（部署后） | `http_health` 请求 `/nope` | FAILURE（部署已执行） |
| #6, #19 | `CHECK_PLAN` | 同一检查用不同 `id` 跑两次、`limit`、在部署后覆盖策略 | `db` 失败后继续（warn），`web` 带 `-l localhost`，`deploy_version` 自动传入 |
| #7 | `ask` → 继续 | `service_running:ask`，点击“继续” | UNSTABLE，Deploy 继续执行 |
| #8 | `ask` → 中止 | 点击“中止” | **ABORTED**，没有部署 |
| #9 | 计划校验 | 拼错的检查名、阶段不对、未知策略、未登记的参数（包括 `ansible_ssh_common_args`）、缺少必填参数、重复 id | FAILURE，一次列出全部 6 个错误，没有执行任何 playbook |
| #10 | Jinja 注入 | `CHECK_PARAMS=disk_space: {path: "{{ lookup('pipe','id') }}"}` | FAILURE，参数被拒绝 |
| #11 | 多余的参数 | 给没有选择的检查传了 `CHECK_PARAMS` | FAILURE，提示该检查不在本次计划中 |
| #16 | YAML 类型错误 | `CHECK_PARAMS` 写成了列表 | FAILURE，提示必须是映射 |
| #17 | 超时 | `timeout_minutes: 1`，检查本身需要 150 秒 | 60 秒后按检查失败处理，`warn` → UNSTABLE，Deploy 继续 |
| #18 | 检查过程中手动中止 | 检查运行时点击“中止” | **ABORTED**，没有被当作检查失败而继续部署 |
| #20 | 简写 + 参数 | `PRE_CHECKS=disk_space:warn port_reachable`，`CHECK_PARAMS` 覆盖两个检查 | 符合预期 |

每次构建都会归档 `deploy-checks-report.json`，内容是每个检查的 id、阶段、策略、参数、耗时、状态，以及失败后的处理结果（`continued` / `ignored` / `aborted`）。

## 7. 注意事项

- **`parameters {}` 要在第一次构建后才生效**：Jenkinsfile 中新增的参数，需要先跑一次（或手动 “Build Now” 一次）后才会出现在 “Build with Parameters” 页面。
- **共享库应配置为受信任库**（Manage Jenkins → System → Global Trusted Pipeline Libraries）。`deployChecks` 用到了 `hudson.AbortException`、`FlowInterruptedException.getCauses()`、`System.currentTimeMillis()` 等，如果放在沙箱中运行，需要逐个审批。
- **`ask` 等待期间会一直占用执行器**，因为检查是在 `agent` 中执行的。如果等待时间可能很长，可以把 `ask` 换成 `warn`，或者把 `input` 移到不占用 agent 的 stage 中。
- **主机不可达也算检查失败**（见第 2 节第 2 条）。如果想把“连不上”和“检查不通过”分开处理，可以在 playbook 开头加一个 `ansible.builtin.ping`，或者在检查任务上加 `ignore_unreachable`。
- **检查 playbook 应该是只读的**：使用 `changed_when: false`；需要执行命令时，加上 `check_mode: false`。这样就算有人用 `checkMode: true` 调用，检查也能正常运行。
- **检查之间互相独立，没有先后依赖**。如果需要加快速度，可以把 `runPhase` 改成用 `parallel` 并行执行各个检查。`abort` 失败会导致 `parallel` 失败，可以配合 `failFast` 使用。
- 插件在检查失败时打印的 `FATAL` 和堆栈只是日志噪音（第 2 节第 3 条），实际的失败原因看它上面 Ansible 输出的 `fatal: ... msg`。

## 8. 如何新增一个检查

1. 在 `ansible/checks/` 下新建 playbook，用 `assert` / `fail` / 模块自身的失败来表示检查不通过，在文件头注释中写明参数。
2. 在 `checks/catalog.yaml` 中登记：`playbook`、`phases`、默认 `policy`、`timeout_minutes`、`params`（全部参数及默认值），以及需要时的 `required`。
3. 不需要修改 Jenkinsfile 或共享库。下次部署时在 `PRE_CHECKS` / `POST_CHECKS` 中写上它的名字即可。
