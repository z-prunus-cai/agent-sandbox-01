# 给已有 Jenkins 部署流水线加上 Ansible 部署前/部署后检查

需求：给已有的 Jenkins 部署流水线加上用 Ansible 执行的部署前检查和部署后检查，并且：

1. **可设置阻塞或非阻塞**：检查不通过时，可以停止流水线，也可以只记录然后继续；
2. **每次部署可任意选择**：这次部署前查 a、b，部署后查 x、y，下次只查一部分或换别的；
3. **检查可以接受参数**。

本目录只有三个文件，说明和演示放在一起：

| 文件 | 内容 |
| --- | --- |
| `README.md` | 本文：结论、原理、接入步骤、演示方法、调研发现、测试记录、文档下载脚本 |
| [`Jenkinsfile`](Jenkinsfile) | 可以直接运行的示例流水线。包含三部分：检查目录（按项目修改）、流水线本身（`[新增]` 标出接入检查新加的部分）、检查引擎（解析选择、运行检查、处理失败） |
| [`checks.yml`](checks.yml) | 所有检查 playbook，每个检查是一个 play，用 tag 选择；另含一个演示用的部署 play |

---

## 1. 结论

**三个需求都能满足，原有的部署步骤不用改。** 在已有流水线的部署 stage 前后各加一个检查 stage 即可。本目录的示例已在 Jenkins 2.580.1 + Ansible 插件 635.v34b_48e979c86 + ansible-core 2.21.5 上实际运行验证（见[第 8 节](#8-测试记录)）。

关键前提：Jenkins 的 Ansible 插件只要遇到 playbook 失败，就让流水线失败。它**没有“失败也继续”的选项**（没有类似 `sh(returnStatus: true)` 的参数），也拿不到 ansible-playbook 的退出码。所以“阻塞 / 非阻塞”不能交给插件或 Ansible，**只能在 Jenkins 流水线中实现**：捕获失败，再按策略决定停止还是继续。

具体做法：

- **检查写在 Ansible 里，只判断“通过 / 不通过”**：每个检查是 `checks.yml` 中的一个 play，用 `assert`、`wait_for`、`uri` 等模块判断条件，不满足就失败。
- **失败后怎么处理由 Jenkins 决定**：每个检查有一个策略（`abort` / `warn` / `ignore` / `ask`），同一个检查这次可以是阻塞的，下次可以是非阻塞的，不需要改 playbook。
- **在构建参数页上选择检查**：部署前、部署后检查各有一个可多选的列表，选中哪些检查，下方就只显示这些检查的“失败时怎么处理”和参数输入框（用 [Active Choices](https://plugins.jenkins.io/uno-choice/) 插件实现）。通过 API 或上游 job 触发时，改用 YAML 格式的 `CHECK_PLAN` 参数。
- **只接受登记过的检查名和参数名**：所有检查及其允许的参数都登记在 Jenkinsfile 的 `checkCatalog()` 中，其他的一律拒绝。这是为了安全，原因见[第 6 节](#6-调研发现插件与-ansible-的实际行为)。

---

## 2. 每次部署怎么选

在 “Build with Parameters” 页面上：

1. **`PRE_CHECKS`**：部署前检查的多选列表，按住 Ctrl（macOS 上是 ⌘）点击可以选多个。默认选中 `checkCatalog()` 中 `defaults` 列出的检查，全部取消表示不做部署前检查。
2. **`PRE_CHECK_OPTIONS`**：根据上面的选择**实时生成**的表单，每个选中的检查一组：
   - “失败时”下拉框：`abort` / `warn` / `ignore` / `ask`，默认值为该检查的默认策略；
   - 该检查的每个参数一个输入框，预先填好默认值，旁边显示说明；数字参数是数字输入框；必填参数标红色 `*`。
   没有选中的检查不会出现在表单里；取消选择后，它的输入框随之消失。
3. **`POST_CHECKS`** / **`POST_CHECK_OPTIONS`**：部署后检查，用法相同。
4. **`CHECK_PLAN`**：YAML 格式的完整计划，一般留空。非空时忽略上面四个参数，主要用于 API 或上游 job 触发，也可以让同一检查跑多次（用 `id` 区分）、单独设置 `limit`、`timeout_minutes`：

```yaml
pre:
  - disk_space                                     # 全部用默认值
  - {name: port_reachable, id: db,    policy: warn, params: {target_port: 5432}}
  - {name: port_reachable, id: redis, params: {target_port: 6379}}
post:
  - {name: http_health, policy: ask, limit: web1}  # 只检查 web1，失败时人工决定
  - {name: app_version, policy: abort}
```

从其他 job 或 API 触发时，Active Choices 参数没有界面可用（插件文档也说明这些参数是给人在页面上填写的），建议传 `CHECK_PLAN`。也可以直接给 `PRE_CHECKS` / `POST_CHECKS` 传逗号分隔的检查名，这时使用默认参数，检查名后可以加 `:策略`：

```groovy
build job: 'deploy', parameters: [
    string(name: 'APP_VERSION', value: '1.2.3'),
    string(name: 'PRE_CHECKS', value: 'disk_space,port_reachable:warn'),   // 使用默认参数
    text(name: 'CHECK_PLAN', value: '''
post:
  - {name: http_health, policy: warn, params: {max_attempts: 20}}
'''),                                                                       // 非空时以它为准
]
```

示例自带的检查：

| 检查 | 阶段 | 默认策略 | 参数 |
| --- | --- | --- | --- |
| `disk_space` | pre | abort | `path`、`min_free_mb` |
| `port_reachable` | pre / post | abort | `target_host`、`target_port`、`timeout_seconds` |
| `service_running` | pre / post | warn | `service_name`（必填） |
| `http_health` | post | abort | `url`、`expected_status`、`body_contains`、`max_attempts`、`delay_seconds` |
| `app_version` | post | warn | `version_file`、`expected_version`（留空时使用本次部署的 `APP_VERSION`） |

---

## 3. 三个需求分别如何实现

### 3.1 阻塞 / 非阻塞

| 策略 | 检查失败时 | 构建结果 | 后续 stage | 使用的 Jenkins 步骤 |
| --- | --- | --- | --- | --- |
| `abort` | 阻塞：停止流水线 | FAILURE | 跳过 | `error` |
| `warn` | 非阻塞：记录警告后继续 | UNSTABLE（该 stage 显示黄色） | 继续 | `unstable` |
| `ignore` | 非阻塞：只记录 | 不变 | 继续 | `echo` |
| `ask` | 暂停，等人决定 | 选“继续”为 UNSTABLE；选“中止”或 30 分钟无人处理为 ABORTED | 取决于选择 | `input` + `timeout` |

核心逻辑在 Jenkinsfile 的 `runOneCheck()` 中，简化如下：

```groovy
try {
    timeout(time: check.timeoutMinutes, unit: 'MINUTES') {
        ansiblePlaybook(playbook: 'checks.yml', tags: check.name, extras: "-e @${varsFile}", ...)
    }
} catch (hudson.AbortException e) {          // playbook 返回非 0 = 检查不通过
    failure = e.message
}
if (failure) {
    switch (check.policy) {
        case 'abort':  error "检查 ${check.id} 未通过（阻塞）"; break
        case 'warn':   unstable "检查 ${check.id} 未通过（非阻塞）"; break
        case 'ignore': echo "检查 ${check.id} 未通过（已忽略）"; break
        case 'ask':
            timeout(time: 30, unit: 'MINUTES') { input message: '检查未通过，是否继续？', ok: '继续' }
            unstable "检查 ${check.id} 未通过（人工确认继续）"
    }
}
```

如果只有一两个固定的检查，不需要这套框架，直接用 Jenkins 自带的步骤就够了：

```groovy
stage('Pre-check: disk') {
    steps {
        warnError('磁盘检查未通过') {        // 非阻塞：失败时标记 UNSTABLE 并继续
            ansiblePlaybook(playbook: 'checks.yml', tags: 'disk_space', inventory: 'hosts.ini')
        }
    }
}
```

不过 `warnError` / `catchError` 默认会捕获**所有**异常，包括手动中止（`catchInterruptions` 默认为 `true`），而且实现不了 `ask`。所以示例改用 `try/catch`，只捕获检查失败（`AbortException`）和超时，手动中止照常让构建变成 ABORTED。

部署后检查的 `abort` 并不能撤销已经完成的部署，它的作用是让构建失败、阻止后续 stage（如推广到下一个环境）。需要回滚的话，可以在 `post { failure { ... } }` 里调用回滚 playbook。注意部署前检查失败也会进入 `failure`，这时并没有部署，所以回滚前要先判断部署是否已经执行。

### 3.2 每次部署任意选择

检查列表和参数表单由 Active Choices 插件的两类参数实现：

| 参数 | Active Choices 类型 | 作用 |
| --- | --- | --- |
| `PRE_CHECKS` / `POST_CHECKS` | `activeChoice`，`PT_MULTI_SELECT`（多选列表） | Groovy 脚本从 `checkCatalog()` 生成选项，`defaults` 中的检查默认选中 |
| `PRE_CHECK_OPTIONS` / `POST_CHECK_OPTIONS` | `activeChoiceHtml`，`ET_FORMATTED_HTML`，引用 `PRE_CHECKS` / `POST_CHECKS` | 选择变化时重新运行脚本，按选中的检查生成 HTML 表单 |

两个脚本都由 Jenkinsfile 第 3 部分的 `checkParameters()` 生成，并把 `checkCatalog()` 的内容嵌入脚本中，所以**检查目录只需维护一份**：新增检查或参数后，列表和表单自动跟着变化。脚本在 Groovy 沙箱中运行，不需要管理员审批。

如果更喜欢勾选框而不是多选列表，把 `checkParameters()` 中的 `PT_MULTI_SELECT` 改成 `PT_CHECKBOX` 即可。Jenkins 没有“下拉后勾选多项”的控件，多选列表需要按住 Ctrl / ⌘ 点击。

表单的值是怎么传给流水线的：Active Choices 会把表单中所有 `name="value"` 的输入框的值用逗号连成一个字符串。如果参数值本身含逗号，直接按逗号切分就会错位。所以每个输入框前都放了一个隐藏的标记字段（如 `@@disk_space.path`），提交后的值形如：

```
@@disk_space.policy,abort,@@disk_space.path,/var,@@disk_space.min_free_mb,2048
```

`parseOptions()` 按 `,@@` 切分，所以值中的逗号不受影响。插件拼接时还会按 JSON 规则转义引号和反斜杠，解析时会还原；随后按 `checkCatalog()` 中默认值的类型把数字参数转回数字。

`Resolve checks` 阶段最先运行：它把这些参数解析成本次的检查计划，并与 `checkCatalog()` 核对。检查名拼错、阶段不对、策略不存在、参数不认识、必填参数为空、数字参数不是数字、id 重复等问题会**一次全部列出**，构建在部署开始前就失败，不会部署到一半才发现配置写错。

之后每个检查是一个子 stage（如 `pre: disk_space`），在 Pipeline Graph View 中分别显示，见第 5 节。

### 3.3 检查参数

每个检查的参数按以下顺序合并，后面的覆盖前面的：

1. `checkCatalog()` 中该检查 `params` 的默认值。**这里同时也是白名单**，没有列出的参数名会被拒绝；
2. 本次部署在表单中填写的值，或 `CHECK_PLAN` 中该条目的 `params`；
3. 流水线自动加入的变量：`deploy_version`（即 `APP_VERSION`）、`check_id`、`check_phase`。

合并后的参数写入工作区的 `.deploy-checks/<阶段>-<id>.json`，再通过 `-e @文件` 传给 Ansible。用文件传，是因为插件的 `extraVars` 只支持字符串、数字、布尔，而且会把数字变成字符串（见第 6 节）；用 JSON 文件则数字、布尔、列表都能保持原类型。

注意：默认值也是以 extra vars 的形式传入的，在 Ansible 中优先级最高，会覆盖 inventory 的 `group_vars` / `host_vars`。如果某个参数需要按环境取不同的值，可以在 `checkCatalog()` 中按环境给出不同的默认值；或者修改引擎，不传值为 `null` 的参数，同时让 playbook 回退到 inventory 变量（如 `{{ target_port | default(db_port) }}`）。

### 3.4 超时

每个检查都包在 `timeout(time: timeout_minutes)` 中，超时按检查失败处理，再交给策略决定。最好在 playbook 内部也设置更短的超时（如 `wait_for` 的 `timeout`、`uri` 的重试次数），把 Jenkins 的 `timeout` 当作最后一道保险。

---

## 4. 接入到已有流水线

1. 安装插件：`ansible`、`pipeline-utility-steps`（提供 `readYaml` / `writeJSON`）、`uno-choice`（Active Choices）、Pipeline（`workflow-aggregator`）。建议同时安装 `pipeline-graph-view`，用来查看每个检查的结果（见第 5 节）。
2. 把 `checks.yml` 放进部署仓库，删掉不需要的检查，或按第 5 节新增检查。删掉最后那个 `demo | deploy` play。
3. 把 Jenkinsfile 的第 1 部分（检查目录）和第 3 部分（检查引擎）复制到你的 Jenkinsfile 中，然后修改：
   - `checksPlaybook()`：改为 `checks.yml` 在仓库中的路径；
   - `checkCatalog()`：改为你的检查、默认值和参数说明；
   - `ansibleArgs()`：改为与部署相同的 `inventory`、`credentialsId`、`installation` 等。
4. 把原有的 `parameters { ... }` 改写成文件开头的 `properties([parameters([...] + checkParameters())])`。检查参数需要根据检查目录生成，只能用 `properties()` 定义；而 `properties()` 和 `parameters {}` 不能同时使用，所以原有参数也要一起移过来（写法基本相同，只是参数之间用逗号分隔）。
5. 在流水线中加入第 2 部分标记 `[新增]` 的内容：两个变量、`Resolve checks` / `Pre-deploy checks` / `Post-deploy checks` 三个 stage，以及 `post { always { reportChecks(...) } }`。原有的 Deploy stage 不用动。
6. 运行一次，让 Jenkins 记录新的参数定义。之后 “Build with Parameters” 页面上就会出现检查列表和表单。

所有脚本（流水线本身和参数页上的两个脚本）都能在 Groovy 沙箱中运行，**不需要任何脚本审批**。如果有多条流水线要共用，可以把第 3 部分移到共享库的 `vars/` 中。

---

## 5. 演示

### 在 Jenkins 中运行

演示把 Jenkins 节点本机当作目标主机（`ansibleArgs()` 中的 `inventoryContent`），不需要其他服务器。

1. 在 Jenkins 节点上安装 `ansible-core`，确保 `ansible-playbook` 在 PATH 中；安装第 4 节列出的插件。
2. 启动演示应用，供部署后检查访问：
   ```bash
   mkdir -p /tmp/demo-app && cd /tmp/demo-app && python3 -m http.server 18080
   ```
3. 新建一个 Pipeline job，选择 “Pipeline script from SCM”，仓库填本仓库，Script Path 填 `jenkins-ansible-checks/Jenkinsfile`。
4. 点 “Build Now” 运行一次，让 Jenkins 记录参数定义（这次不运行任何检查）。之后打开 “Build with Parameters”，可以试试：
   - 默认选择直接点 Build：部署前 2 项、部署后 2 项检查全部通过，结果为 SUCCESS；
   - 部署前多选 `service_running`，在表单中把 `service_name` 填为 `nginx`，“失败时”保持 `warn`：没有 nginx 时构建为 UNSTABLE，部署照常执行；
   - 把 `disk_space` 的 `min_free_mb` 改为 `99999999`：构建为 FAILURE，部署被跳过；
   - 把 `http_health` 的“失败时”改为 `ask`，并停掉演示应用：构建会暂停，等你选择继续或中止。

每次构建都会归档 `deploy-checks-report.json`，内容是每个检查的 id、阶段、策略、参数、耗时、结果，以及失败后的处理（`continued` / `ignored` / `aborted`）。控制台日志末尾有汇总：

```
[checks] 检查结果：
  pre   disk_space           abort   PASSED     1s
  pre   db                   warn    FAILED     3s -> continued
  pre   web                  abort   PASSED     1s
  post  http_health          abort   PASSED     1s
  post  app_version          abort   FAILED     1s -> aborted
```

### Jenkins 界面上的显示

以下内容在 Jenkins 2.580.1 上实际截图确认过。

**构建参数页（Build with Parameters）**：从上到下依次是 `APP_VERSION`；`PRE_CHECKS` 多选列表，每项显示“检查名 — 说明”，默认检查已选中（灰色底）；`PRE_CHECK_OPTIONS`，每个选中的检查一个带边框的分组，组内第一行是“失败时”下拉框，下面是各参数的输入框，输入框下方是参数说明；`POST_CHECKS` / `POST_CHECK_OPTIONS` 同上；最后是 `CHECK_PLAN` 多行文本框。改变列表中的选择后，下方表单会立即刷新，只显示选中的检查。

**构建结果**：构建列表中的图标与策略对应。全部通过或 `ignore` 显示绿色 ✓；`warn` 或 `ask` 选择继续显示橙色 !（UNSTABLE）；`abort` 显示红色 ✗；`ask` 选择中止或手动中止显示灰色（ABORTED）。

**Pipeline Graph View**（[pipeline-graph-view](https://plugins.jenkins.io/pipeline-graph-view/) 插件，构建页面的 “Stages” 入口；新版本 Jenkins 推荐使用）：**显示效果最好，推荐使用。**

- 顶部的流程图中，每个检查是一个独立节点，按阶段分组，例如 `Pre-deploy… (3)` 下面是 `pre: disk_space`、`pre: db`、`pre: web`。节点的颜色和图标就是检查结果：通过为绿色 ✓，`warn` 失败为橙色 !，`abort` 失败为红色 ✗。
- 阻塞检查失败后，后面的 `Deploy`、`Post-deploy checks` 显示为“已跳过”（»），流程图中会画一条绕过它们的线。
- 左侧是树形列表，同样按阶段嵌套显示每个检查及其耗时。点击某个检查，右侧会显示它的步骤和日志；打开失败的构建时，会自动定位到失败的那一步。
- `ask` 等待期间，该检查节点显示为蓝色暂停图标，右侧直接显示提示“检查 xxx 未通过：… 是否继续？”以及 **继续** / **Abort** 两个按钮，点击即可，不需要打开控制台。

**Stage View**（经典的 [pipeline-stage-view](https://plugins.jenkins.io/pipeline-stage-view/) 插件，job 首页的表格）：**能用，但不适合这种动态的 stage。**

- 嵌套的检查 stage 会被平铺成与 `Deploy` 同级的列，例如 `Pre-deploy checks | pre: disk_space | Deploy | …`。
- 列由最近一次构建决定。每次选择的检查不同，列就不同；而 Stage View 只显示与最近一次构建 stage 结构相同的构建。所以实测中，经常**只显示最近一次构建那一行**，没法横向对比历史构建。
- `ask` 等待期间，对应格子显示为蓝色斜纹，标注 “paused for …”。

**控制台日志**：每个检查开始时打印一行 `[checks] <id>（说明）策略=… 参数=…`，末尾打印检查结果汇总表。有两点要注意：

- 检查失败时，插件会打印 `FATAL: command execution failed` 和 Java 堆栈（见第 6 节第 3 条）。这些信息在 Graph View 的步骤日志里也能看到，但不代表构建失败。
- 所有检查都在同一个 `checks.yml` 中，所以没被选中的检查也会打印一行空的 `PLAY [check | xxx]` 标题，下面没有任何任务。这不影响结果。

**归档文件**：job 首页和构建页面上有 `deploy-checks-report.json` 的链接，点 “view” 可以直接在浏览器中查看。

### 不经过 Jenkins 单独运行检查

检查就是普通的 playbook，用 `-t` 选择检查，用 `-e` 传参数：

```bash
cd jenkins-ansible-checks
ansible-playbook -i localhost, -c local checks.yml -t disk_space -e '{"path": "/", "min_free_mb": 1024}'
ansible-playbook -i localhost, -c local checks.yml -t http_health \
    -e '{"url": "http://127.0.0.1:18080/health", "expected_status": 200, "body_contains": "ok", "max_attempts": 3, "delay_seconds": 2}'
```

### 新增一个检查

1. 在 `checks.yml` 中新增一个 play，`tags` 写检查名；条件不满足时让它失败（用 `assert` / `fail`），并在注释中写明参数。
2. 在 Jenkinsfile 的 `checkCatalog()` 中登记：`phases`、默认 `policy`、`timeout_minutes`、全部 `params` 及默认值，需要时加上 `required`。参数名不要使用 Ansible 的保留字（见第 6 节）。
3. 下次部署时在 `PRE_CHECKS` / `POST_CHECKS` 中写上它的名字即可。

---

## 6. 调研发现：插件与 Ansible 的实际行为

以下结论来自插件源码（[jenkinsci/ansible-plugin](https://github.com/jenkinsci/ansible-plugin) 提交 `27b52e9`）和实际运行结果，不只是文档里的描述。

| # | 事实 | 依据 | 影响 |
| - | --- | --- | --- |
| 1 | 插件提供三个 Pipeline 步骤：`ansiblePlaybook`、`ansibleAdhoc`、`ansibleVault` | 插件文档 | 检查用 `ansiblePlaybook` 运行 |
| 2 | playbook 失败时抛出 `hudson.AbortException("Ansible playbook execution failed")`。**没有 `returnStatus` / `propagate` 之类的选项**，退出码也拿不到：断言失败（rc=2）和主机不可达（rc=4）看起来一样 | `AnsiblePlaybookBuilder.java:326-328` | 非阻塞只能用 `try/catch`、`catchError` 或 `warnError` 实现；主机不可达也算检查失败 |
| 3 | 失败时插件会先在日志中打印 `FATAL: command execution failed` 和堆栈，然后才抛出异常 | `AnsiblePlaybookBuilder.java:330-333` | **非阻塞检查失败时日志里也有 FATAL 和堆栈**，这不代表构建失败，以构建结果为准。真正的失败原因看它上面 Ansible 输出的 `fatal: ... msg` |
| 4 | `extraVars` 只支持 String / Boolean / Number；列表或 Map 会被丢弃，只打印一行 `[WARN] Omitting extra var`。参数以 `-e key=value` 形式传入，所以**数字到了 Ansible 里会变成字符串**；值默认 `hidden: true`，在日志中显示为 `********` | `AnsiblePlaybookStep.java:390-433`、`AbstractAnsibleInvocation.java:119-150` | 示例改为把参数写入 JSON 文件，用 `extras: '-e @file'` 传入 |
| 5 | `extraVars` 的值和 `extras` 字符串都会经过 `envVars.expand`（`${VAR}` 会被替换成 Jenkins 环境变量）；`extras` 还会按空白字符拆分 | `AbstractAnsibleInvocation.java:129,161` | 传给 `extras` 的路径不能带空格 |
| 6 | ansible-playbook 的工作目录是 Jenkins 工作区；`inventoryContent` 会被写到临时文件再传给 `-i` | `CLIRunner.java:47`；构建日志 | playbook 和变量文件可以用相对于仓库根目录的路径 |
| 7 | 构建的环境变量会传给 ansible 进程，playbook 中可用 `lookup('env', 'BUILD_TAG')` 读取 | 插件 README | 示例改用显式变量 `deploy_version`，更清晰 |
| 8 | 支持 `tags`、`skippedTags`、`limit`、`checkMode`、`forks`、`credentialsId`、`vaultCredentialsId` 等参数 | 步骤参考 | 示例用 `tags` 选择检查、用 `limit` 限制主机 |
| 9 | `timeout` 取消正在运行的 `ansiblePlaybook` 时，抛出的是**普通的 `java.lang.InterruptedException`**，而不是带超时原因的 `FlowInterruptedException` | 构建日志 | 无法通过异常区分“超时”和“手动中止”，示例用已耗时判断 |

另外两个 Ansible 本身的坑，都实测过：

- **Ansible 会渲染 extra vars 中的 Jinja 表达式。** 在 ansible-core 2.21.5 中用 `-e @v.json` 传入 `{"path": "/tmp/{{ lookup('pipe', 'echo INJECTED') }}"}`，输出为 `/tmp/INJECTED`。如果构建参数原样传给 Ansible，能触发构建的人就能**在 Jenkins 节点上执行任意命令**。再加上 extra vars 优先级最高，传入 `ansible_ssh_common_args`、`ansible_connection` 之类的变量还能改变 Ansible 的连接方式。所以示例**只接受登记过的检查名和参数名，并拒绝包含 `{{`、`{%`、`{#` 的值**。
- **有些变量名是 Ansible 的保留字**，例如 `port`、`retries`。用它们当参数名会出现 `Found variable using reserved name` 警告，所以示例用的是 `target_port`、`max_attempts`。可以用下面的命令查看全部保留字：
  `python3 -c "from ansible.vars.reserved import get_reserved_names; print(sorted(get_reserved_names()))"`

还有一个 Jenkins 沙箱的限制：Jenkinsfile 中不能 `new` 一个异常（例如 `new IllegalArgumentException(...)`），否则需要管理员审批。所以示例的校验函数把问题收集到列表里，最后统一调用 `error` 步骤。

Active Choices 插件（2.8.11，根据源码 [jenkinsci/active-choices-plugin](https://github.com/jenkinsci/active-choices-plugin) 和实测）：

| # | 事实 | 依据 | 影响 |
| - | --- | --- | --- |
| 1 | 动态 HTML 参数（`ET_FORMATTED_HTML`）的输出会经过 HTML 过滤：允许 `input` / `select` / `option` / `textarea` 和 `style` 等属性，**不允许 `<script>` 和 `onchange` 之类的事件属性** | `SafeHtmlExtendedMarkupFormatter.java` | 表单不能依赖自定义 JavaScript，示例只用普通输入框和下拉框 |
| 2 | 提交时，参数中所有 `name="value"` 的输入框被收集成 JSON 数组，再用 `join(",", true)` 连成一个字符串：值之间用逗号分隔，值内的 `"`、`\` 按 JSON 规则转义 | `AbstractUnoChoiceParameter.java:168-180`；构建 #23 | 示例在每个输入框前加隐藏标记 `@@检查名.字段` 来切分，并还原转义（构建 #25 验证了含逗号、引号、反斜杠、中文的值原样传到 Ansible） |
| 3 | 列表默认选中项通过在选项上加后缀 `:selected` 指定；脚本返回 Map 时，后缀要加在 Map 的值（显示文字）上，加在键上无效 | `selectContent.jelly` 中 `isSelected(iter.value)`；实测 | 见 `choicesScript()` |
| 4 | 沙箱中不允许对 `StringBuilder` 使用 `<<` | 实测（`RejectedAccessException`） | 生成 HTML 时改用 List 拼接 |
| 5 | 脚本出错时插件不在日志中报错，页面上直接显示 fallback 脚本的结果 | 实测 | 调试时可以在 “Script Console” 中用 `p.script.secureScript.evaluate(...)` 运行参数脚本，查看具体异常 |
| 6 | 插件文档说明这些参数是给人在页面上填写的，通过插件、API 或脚本触发时不一定可用 | 插件 README “Known Limitations” | 自动触发时传 `CHECK_PLAN`，或直接给 `PRE_CHECKS` 传检查名 |

---

## 7. 其他方案对比

| 方案 | 做法 | 优点 | 缺点 | 适用场景 |
| --- | --- | --- | --- | --- |
| **A. 每个检查单独调用一次 ansiblePlaybook，由 Jenkins 决定策略（推荐，即本示例）** | 见上 | 每个检查的结果、策略、超时、`limit` 互相独立；playbook 与策略解耦；Jenkins 界面中每个检查一个 stage | 每个检查都要启动一次 ansible-playbook（几秒） | 大多数情况 |
| B. 一次调用跑完所有选中的检查 | `ansiblePlaybook(playbook: 'checks.yml', tags: 'disk_space,port_reachable')` | 只调用一次，速度快，改动最少 | 一次调用只有一个结果，阻塞 / 非阻塞只能整批设置；不同检查的参数混在同一个变量空间里 | 检查都很轻量、策略都相同 |
| C. 在 Ansible 内部处理阻塞 | 任务上写 `ignore_errors: "{{ not blocking }}"`，或用 `block/rescue` 记录失败后继续 | 不依赖 Jenkins，命令行中也能用 | 非阻塞失败时 playbook 仍返回 0，Jenkins 不知道有检查失败，无法标记 UNSTABLE；实现不了 `ask` | 检查必须脱离 Jenkins 运行 |
| D. 不改原 job，加一个外层编排 job | 新 job：先跑检查，再 `build job: '原部署 job'`，然后跑部署后检查 | 原 Jenkinsfile 一行都不用改 | 多一个 job，参数要透传；等待期间外层 job 占用一个执行器 | 原流水线不能改，或归其他团队维护 |

---

## 8. 测试记录

环境：Jenkins 2.580.1（内置节点）、Ansible 插件 635.v34b_48e979c86、Active Choices 2.8.11、pipeline-utility-steps、ansible-core 2.21.5。Jenkinsfile 从 Git 仓库加载，因此运行在 Groovy 沙箱中，**没有批准任何脚本**。

**参数页（Active Choices 版本，即当前版本）**：以下构建通过浏览器自动操作 “Build with Parameters” 页面提交，或通过 API 触发。

| 构建 | 场景 | 操作 | 结果 |
| --- | --- | --- | --- |
| — | 打开参数页 | 不做改动 | 部署前默认选中 `disk_space`、`port_reachable`，部署后默认选中 `http_health`、`app_version`；表单只显示这 4 个检查 |
| — | 改变选择 | 部署前改选 `disk_space` + `service_running`，部署后只选 `http_health` | 表单立即刷新，只显示这 3 个检查；`service_name` 标为必填 |
| #22 | 表单提交 | 改 `min_free_mb=2048`、`service_name=nginx`（策略 warn）、`body_contains=status: ok`、`max_attempts=2` | UNSTABLE；计划中 `min_free_mb` 为数字 2048，`service_running` 策略为 warn，失败后继续部署 |
| #25 | 特殊字符 | `body_contains` 填 `a,"b" c\d, @@x 中文/é`，策略改为 ignore | 值原样传给 Ansible，检查失败后按 ignore 处理，构建 SUCCESS |
| #24 | Jinja 注入 | `path` 填 `/tmp/{{ lookup("pipe", "id") }}` | FAILURE，提示参数不能包含 Jinja 语法，没有运行任何 playbook |
| #26 | 必填参数为空 | 选择 `service_running`，不填 `service_name` | FAILURE，提示必须提供 `service_name` |
| #19 | API 只传检查名 | `PRE_CHECKS=disk_space` | 使用默认参数运行，SUCCESS |
| #28 | API 传 `CHECK_PLAN` | 同一检查自定义 `id`、策略 warn、部署后默认参数 | `db` 失败后继续，构建 UNSTABLE |

**检查引擎**：以下 #1–#16 在上一版（检查选择用文本参数）上运行，检查的执行和失败处理逻辑与当前版本相同。

| 构建 | 场景 | 输入 | 结果 |
| --- | --- | --- | --- |
| #1 | 默认检查 | 参数全部留空 | SUCCESS，部署前 2 项、部署后 2 项全部通过 |
| #2 | 非阻塞 `warn` | `PRE_CHECKS=disk_space, service_running:warn`（环境中没有 nginx） | **UNSTABLE**，部署继续执行 |
| #3 | 阻塞 `abort`（部署前） | `disk_space` 要求 99999999 MB | **FAILURE**，部署和部署后检查被跳过 |
| #4 | `ignore` | `service_running:ignore` | **SUCCESS**，失败只记录在汇总中 |
| #5 | 阻塞 `abort`（部署后） | `http_health` 请求 `/nope` | FAILURE（部署已执行） |
| #6 | `CHECK_PLAN` | 同一检查用不同 `id` 跑两次、使用 `limit`、部署后覆盖策略 | `db` 失败后继续（warn）；`web` 带 `-l localhost`；部署后 `app_version` 期望 9.9，实际 3.1.0，阻塞失败 |
| #10 | 计划校验 | 拼错检查名、阶段不对、未知策略、未登记参数（含 `ansible_ssh_common_args`）、缺少必填参数、Jinja 注入、重复 id | FAILURE，一次列出全部 7 个问题，没有运行任何 playbook |
| #11 | YAML 类型错误 | 参数写成了 YAML 列表 | FAILURE，提示必须是映射 |
| #12 | 多余参数 | 给没有选择的检查传了参数 | FAILURE，提示该检查不在本次计划中 |
| #13 | 超时 | `timeout_minutes: 1`，检查本身需要 150 秒 | 60 秒后按检查失败处理，`warn` → UNSTABLE，部署继续 |
| #14 | `ask` → 继续 | 点击“继续” | UNSTABLE，部署继续执行 |
| #15 | `ask` → 中止 | 点击“中止” | **ABORTED**，没有部署 |
| #16 | 检查运行中手动中止 | 检查运行时点击“中止” | **ABORTED**，没有被当作检查失败而继续部署 |

（#7–#9 是修复沙箱限制之前的运行，见第 6 节最后一段，修复后重跑为 #10–#12。）

## 9. 注意事项

- **新增或修改参数定义后，要先运行一次构建**，参数页才会显示新的列表和表单（参数定义是在构建运行时由 `properties()` 写入 job 配置的）。
- **`ask` 等待期间会占用执行器**，因为检查运行在 `agent` 中。如果可能等很久，改用 `warn`，或者把 `input` 移到不占用 agent 的 stage。
- **主机不可达也算检查失败**（第 6 节第 2 条）。如果想把“连不上”和“检查不通过”分开处理，可以在 play 开头加一个 `ansible.builtin.ping`，或在任务上加 `ignore_unreachable`。
- **检查应该是只读的**：任务加上 `changed_when: false`，执行命令的任务加上 `check_mode: false`，这样即使有人用 `checkMode: true` 调用也能正常运行。
- **检查之间没有依赖**。如果要加快速度，可以把 `runChecks()` 改成用 `parallel` 并行运行；`abort` 失败会导致 `parallel` 失败，可以配合 `failFast` 使用。

---

## 附录：下载 Jenkins / Ansible 文档

调研时把以下文档下载到了本地。**这些文档不提交到 git**，`jenkins-ansible-checks/upstream/` 已写入 `.gitignore`，需要时用下面的脚本重新下载。

| 下载后的目录 | 内容 | 来源 |
| --- | --- | --- |
| `upstream/jenkins/handbook/` | Jenkins 官方文档全集（359 篇 `.adoc`）：User Handbook、Pipeline（语法、共享库、最佳实践）、教程、管理、升级、插件开发 | [jenkins-infra/jenkins.io](https://github.com/jenkins-infra/jenkins.io) 的 `content/doc` |
| `upstream/jenkins/pipeline-steps/` | 16 个核心插件的 Pipeline 步骤参考（`sh`、`catchError`、`timeout`、`input`、`readYaml`、`build`、`parallel`、`withCredentials` 等），以及所有插件的步骤索引 | <https://www.jenkins.io/doc/pipeline/steps/>（转为 Markdown） |
| `upstream/ansible/docsite/` | Ansible 官方文档全集（411 篇 `.rst`）：入门、Inventory、Playbook（错误处理、block、tags、check mode、变量）、Vault、命令行、Collections、升级指南等 | [ansible/ansible-documentation](https://github.com/ansible/ansible-documentation) 的 `docs/docsite/rst` |
| `upstream/ansible/builtin-plugins/` | 所有 `ansible.builtin` 模块与插件的参考（71 个模块、122 个过滤器、82 个测试、25 个 lookup 等），以及 60 个 Playbook 关键字 | 本地 `ansible-doc` 生成 |
| `upstream/ansible-plugin/` | Jenkins Ansible 插件文档：README、步骤参数参考、各字段的帮助 | [jenkinsci/ansible-plugin](https://github.com/jenkinsci/ansible-plugin) |
| `upstream/SOURCES.txt` | 各来源的提交号和下载时间 | 脚本生成 |

说明：

- docs.ansible.com 下载时返回 429（限流），所以 Ansible 文档改从源码仓库获取，内容相同。
- 源码仓库里不包含构建时才生成的页面：各 collection（如 `community.general`）的模块文档、Playbook 关键字列表、配置项列表。前两者中 `ansible.builtin` 的部分由 `ansible-doc` 生成补上；配置项可以用 `ansible-config list` 查看。
- 文档为原始格式（`.adoc` / `.rst`），用文本编辑器即可阅读，GitHub 上也能直接渲染。

下载方法：安装依赖后，在仓库根目录运行下面的命令。它会从本文中提取下方的脚本并执行；也可以把脚本复制出来保存为 `fetch-docs.sh` 再运行：

```bash
pip install beautifulsoup4 markdownify ansible-core
bash <(sed -n '/^#!\/usr\/bin\/env bash$/,/^# fetch-docs end$/p' jenkins-ansible-checks/README.md) jenkins-ansible-checks/upstream
```

<details>
<summary>fetch-docs 脚本</summary>

```bash
#!/usr/bin/env bash
# 把 Jenkins、Ansible、Jenkins Ansible 插件的文档下载到指定目录。
#
# 依赖：git、curl、python3（含 beautifulsoup4、markdownify，用于把 HTML 转成 Markdown），
#       ansible-core（用 ansible-doc 生成 ansible.builtin 文档；没有安装则跳过这一项）。
#   pip install beautifulsoup4 markdownify ansible-core
#
# 用法：bash fetch-docs.sh <输出目录>
set -euo pipefail

OUT="${1:?用法：bash fetch-docs.sh <输出目录>}"
WORK="$(mktemp -d)"
trap 'rm -rf -- "${WORK:?}"' EXIT
mkdir -p "$OUT"
SOURCES="$OUT/SOURCES.txt"
echo "下载时间：$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$SOURCES"

# 稀疏克隆一个仓库的某个子目录，并记录提交号
sparse_clone() {  # <仓库 URL> <子目录> <本地目录名>
    git clone -q --depth 1 --filter=blob:none --sparse "$1" "$WORK/$3"
    git -C "$WORK/$3" sparse-checkout set "$2"
    echo "$1 $2 @ $(git -C "$WORK/$3" rev-parse HEAD)" >> "$SOURCES"
}

# 把 jenkins.io 的页面转成 Markdown，只保留正文
html_to_md() {  # <URL> <输出文件>
    curl -sSfL "$1" -o "$WORK/page.html"
    python3 -I - "$WORK/page.html" "$2" "$1" <<'EOF'
import re, sys
from bs4 import BeautifulSoup
from markdownify import markdownify
src, dst, url = sys.argv[1:4]
soup = BeautifulSoup(open(src, encoding="utf-8").read(), "html.parser")
for tag in soup(["script", "style", "nav", "header", "footer", "noscript", "svg"]):
    tag.decompose()
main = (soup.select_one("#main-content") or soup.select_one("main")
        or soup.select_one(".container .col-lg-9") or soup.body)
text = re.sub(r"\n{3,}", "\n\n", markdownify(str(main), heading_style="ATX", code_language="groovy"))
with open(dst, "w", encoding="utf-8") as f:
    f.write(f"<!-- Source: {url} -->\n\n" + text.strip() + "\n")
EOF
}

echo "==> Jenkins 文档"
sparse_clone https://github.com/jenkins-infra/jenkins.io.git content/doc jenkins.io
mkdir -p "$OUT/jenkins"
rm -rf -- "${OUT:?}/jenkins/handbook"
cp -r "$WORK/jenkins.io/content/doc" "$OUT/jenkins/handbook"

echo "==> Jenkins Pipeline 步骤参考"
mkdir -p "$OUT/jenkins/pipeline-steps"
for p in workflow-basic-steps workflow-durable-task-step workflow-cps workflow-scm-step \
         pipeline-input-step pipeline-utility-steps pipeline-build-step pipeline-stage-step \
         pipeline-milestone-step pipeline-model-definition credentials-binding ssh-agent \
         lockable-resources git timestamper ws-cleanup; do
    html_to_md "https://www.jenkins.io/doc/pipeline/steps/$p/" "$OUT/jenkins/pipeline-steps/$p.md"
done
html_to_md https://www.jenkins.io/doc/pipeline/steps/ "$OUT/jenkins/pipeline-steps/INDEX-all-plugins.md"

echo "==> Ansible 文档"
sparse_clone https://github.com/ansible/ansible-documentation.git docs/docsite/rst ansible-documentation
mkdir -p "$OUT/ansible"
rm -rf -- "${OUT:?}/ansible/docsite"
cp -r "$WORK/ansible-documentation/docs/docsite/rst" "$OUT/ansible/docsite"

if command -v ansible-doc >/dev/null; then
    echo "==> ansible.builtin 模块与插件参考（ansible-doc）"
    echo "ansible-doc: $(ansible-doc --version | head -1)" >> "$SOURCES"
    python3 -I - "$OUT/ansible/builtin-plugins" <<'EOF'
import json, os, subprocess, sys
out = sys.argv[1]
env = dict(os.environ, ANSIBLE_NOCOLOR="1", ANSIBLE_PAGER="cat", PAGER="cat")
def doc(*args):
    return subprocess.run(["ansible-doc", *args], capture_output=True, text=True, env=env)
for kind in ["module", "filter", "test", "lookup", "callback", "connection", "inventory",
             "become", "cache", "shell", "strategy", "vars"]:
    names = sorted(n for n in json.loads(doc("-t", kind, "-l", "-j").stdout or "{}")
                   if n.startswith("ansible.builtin."))
    os.makedirs(os.path.join(out, kind), exist_ok=True)
    for name in names:
        result = doc("-t", kind, name)
        if result.returncode == 0 and result.stdout.strip():
            path = os.path.join(out, kind, name.removeprefix("ansible.builtin.") + ".txt")
            with open(path, "w") as f:
                f.write(result.stdout)
    print(f"    {kind}: {len(names)}")
keywords = sorted(json.loads(doc("-t", "keyword", "-l", "-j").stdout or "{}"))
with open(os.path.join(out, "keywords.txt"), "w") as f:
    f.write(doc("-t", "keyword", *keywords).stdout)
EOF
else
    echo "==> 未安装 ansible-core，跳过 ansible.builtin 参考"
fi

echo "==> Jenkins Ansible 插件文档"
git clone -q --depth 1 https://github.com/jenkinsci/ansible-plugin.git "$WORK/ansible-plugin"
echo "https://github.com/jenkinsci/ansible-plugin @ $(git -C "$WORK/ansible-plugin" rev-parse HEAD)" >> "$SOURCES"
P="$OUT/ansible-plugin"
mkdir -p "$P/docs" "$P/help"
cp "$WORK/ansible-plugin/README.md" "$WORK/ansible-plugin/LICENSE.md" "$P/"
cp -r "$WORK/ansible-plugin/docs/images" "$P/docs/"
(cd "$WORK/ansible-plugin/src/main/resources/org/jenkinsci/plugins/ansible" &&
    find . -name 'help*.html' -exec cp --parents {} "$P/help/" \;)
html_to_md https://www.jenkins.io/doc/pipeline/steps/ansible/ "$P/jenkins-io-pipeline-steps-ansible.md"

echo "完成：$OUT"
cat "$SOURCES"
# fetch-docs end
```

</details>
