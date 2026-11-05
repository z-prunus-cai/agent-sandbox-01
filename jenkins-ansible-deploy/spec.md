# 规格：Jenkins + Ansible 部署流水线样板

## 1. 目标

一套放在同一个 git 仓库里的 Jenkins 流水线与 Ansible 代码样板，满足：

1. 部署对象是 Ansible inventory 中的**主机组**。构建参数页上从主机组列表中**多选**本次的发布对象。
2. 部署阶段可以在构建参数页上**勾选跳过**。
3. 部署前、部署后各可选择 **0 到任意个额外任务**。参数页上只显示所选任务的参数输入框，可逐项填写；不同任务的参数类型不同。
4. 主机与主机组的各种参数（端口、路径、版本号等）**只在 inventory 中管理**，参数页不能覆盖。
5. **业务与基础设施分开**：新增一个业务任务只需新增一个任务目录，不修改引擎、流水线骨架、通用 playbook 和测试框架。
6. **同一台 Jenkins 上两个 job 共用一个引擎**：只读 job 只能运行只读任务，没有部署阶段；读写 job 可以运行全部任务和部署。
7. **执行前复核**：任务可以声明"执行前需要复核"。引擎先让任务生成要执行的内容并显示出来，等有人确认后才执行；执行的内容必须与复核时显示的完全一致。

## 2. 平台与版本

| 组件 | 版本 |
| --- | --- |
| Jenkins | 2.580.1 |
| Jenkins Ansible 插件（`ansible`） | 635.v34b_48e979c86 |
| Active Choices 插件（`uno-choice`） | 2.8.11 |
| Pipeline: Groovy Libraries（`pipeline-groovy-lib`，提供 `library` 步骤） | 与 Jenkins 2.580.1 兼容的最新版本 |
| Pipeline: Input Step（`pipeline-input-step`） | 与 Jenkins 2.580.1 兼容的最新版本 |
| ansible-core | 2.21.5 |
| 其他 Jenkins 插件（Pipeline、`pipeline-utility-steps` 等） | 与 Jenkins 2.580.1 兼容的最新版本，实验时记录确切版本 |
| Python | 3.13（运行 ansible-core 和测试） |

Jenkinsfile 从 SCM 加载，运行在 Groovy 沙箱中，**不需要任何脚本审批，也不需要管理员配置全局共享库**。

## 3. 交付物

目录 `jenkins-ansible-deploy/`，文档用中文；代码标识符、代码注释、构建日志用英文。

`examples/` 是可以整体复制到仓库根目录的样板，其中的路径都相对于 `examples/`：

| 路径 | 内容 | 归属 |
| --- | --- | --- |
| `pipelines/readonly/Jenkinsfile` | 只读 job：声明模式、参数页形式、凭据 ID，其余交给引擎 | 基础设施 |
| `pipelines/readwrite/Jenkinsfile` | 读写 job：同上 | 基础设施 |
| `platform/jenkins/` | 引擎：共享库（`vars/` 定义整条流水线，`src/` 放类和枚举），用 `library` 步骤从同一仓库、同一提交加载 | 基础设施 |
| `platform/ansible/` | `ansible.cfg`、运行任意任务的 `run-task.yml`、部署入口 `deploy.yml`、inventory 检查 `check-inventory.yml` | 基础设施 |
| `business/inventory/` | 主机组、`group_vars`、`host_vars`、vault 文件 | 业务 |
| `business/deploy/` | 部署本身（一个 role） | 业务 |
| `business/tasks/<任务名>/` | 一个额外任务（一个 role，带 Jenkins 元数据和自己的测试） | 业务 |
| `BRIEF.md`、`attachments/` | 说明文档 | — |
| `tests/` | 测试框架与通用测试 | 基础设施 |

## 4. 行为

### 4.1 两个 job

| | 只读 job | 读写 job |
| --- | --- | --- |
| 可选任务 | 只有 `access: read` 的任务 | 全部任务 |
| 部署阶段 | 没有；参数页上没有"跳过部署" | 有 |
| `DEPLOY_PLAN` 中出现写任务或部署 | 计划校验失败，不运行任何 playbook | 允许 |
| 凭据 | 来自 Jenkins 凭据，Jenkinsfile 中按 ID 引用 | 同左 |

两个 job 的凭据机制与现在相同。只读 job 的只读由引擎的计划校验保证；如果要防止有人修改只读 Jenkinsfile 中的模式，可以在 Jenkins 中给只读 job 配置权限更小的凭据 ID，代码不需要修改。

两种参数页形式（Active Choices 版、无插件版）都由引擎提供，Jenkinsfile 选择其中一种；两个示例 Jenkinsfile 都使用 Active Choices 版。

### 4.2 输入（构建参数）

| 参数 | Active Choices 版 | 无插件版 |
| --- | --- | --- |
| 发布对象 | 主机组多选列表，选项来自 inventory | 每个主机组一个勾选框，来自 inventory |
| 跳过部署（仅读写 job） | `SKIP_DEPLOY` 勾选框 | 同左 |
| 部署前任务 | 多选列表 + 随选择实时变化的参数表单 | 每个任务一个勾选框；构建开始后用 `input` 步骤弹出所选任务的表单 |
| 部署后任务 | 同上 | 同上 |
| 完整计划 | `DEPLOY_PLAN`（YAML 文本），非空时忽略上面所有参数，供 API / 上游 job 触发 | 同左 |

参数名是对外契约，写死在代码中，不从其他名称推导。

`DEPLOY_PLAN` 格式（读写 job；只读 job 不接受 `skip_deploy`）：

```yaml
targets: [group_1, group_2]
skip_deploy: false
pre:
  - task_a                                    # 全部默认值
  - {name: task_b, id: b_1, policy: warn, params: {key: value}}
post:
  - {name: check_a, timeout_minutes: 5}
```

### 4.3 任务

- 任务是**抽象占位任务**（如 `task_a`、`check_a`），不调用任何外部工具；只演示参数接收、校验、成功、失败与复核。
- 每个任务是 `business/tasks/` 下的一个目录，即一个 Ansible role：
  - 参数（名称、类型、必填、说明）在 `meta/argument_specs.yml` 中声明，默认值在 `defaults/main.yml` 中；
  - Jenkins 元数据在 `meta/task.yml` 中：说明、允许的阶段（pre / post）、读写（`read` / `write`）、默认失败策略、超时、运行位置（`targets`：所选主机组；`controller`：Jenkins 节点本机）、同一阶段内的排序号、是否需要执行前复核。
- 引擎扫描 `business/tasks/*/meta/task.yml` 发现任务，没有集中登记的任务列表。
- 元数据中的每个字段都是有限取值，未知值在准备阶段报错；排序号在任务之间不得重复。
- 参数类型至少覆盖：字符串、整数、布尔、枚举（choices）。
- 一个阶段内的执行顺序：参数页选择时按排序号；`DEPLOY_PLAN` 中按列表顺序。同一任务可在一个阶段内用不同 `id` 运行多次（仅 `DEPLOY_PLAN`）。
- 失败策略两种：`abort`（停止流水线，FAILURE）与 `warn`（标记 UNSTABLE，继续）。每次构建可逐个任务修改。
- 每个任务有超时，超时按任务失败处理，再交给策略。手动中止构建不能被当作任务失败而继续。

### 4.4 执行前复核

- 声明需要复核的任务分两步运行：
  1. **生成**：引擎以生成模式运行该任务，任务把要执行的内容写入引擎指定的复核文件，不改动任何东西；
  2. **执行**：确认后，引擎以执行模式运行该任务，任务按复核文件执行。
- 引擎在生成后计算复核文件的 sha256，并把全文和 sha256 显示在确认提示中；执行前重新计算，不一致则拒绝执行。复核文件归档为构建产物。
- 能运行该 job 的人都可以确认或中止；确认人记入报告。
- 等待确认时不占执行器；等待有超时，超时或中止时该任务不执行，构建结果为 ABORTED。
- Jenkins 重启后，等待中的确认继续有效。

### 4.5 部署

- 部署 playbook 只作用于所选主机组；主机参数全部来自 inventory（`group_vars` / `host_vars`）。
- 部署本身也是占位：校验主机参数齐全、类型正确，并输出将要执行的动作，不改动目标主机。
- 跳过部署时，部署前、部署后任务照常按选择执行。

### 4.6 校验（在任何 playbook 运行之前，一次列出全部问题后使构建失败）

- 主机组不在 inventory 中，或在需要目标主机时未选主机组；
- 未知任务、任务不允许用于该阶段、只读 job 中的写任务或部署、未知策略、`id` 不合法或重复；
- 未声明的参数名、必填参数为空、整数不是整数、枚举值不在 choices 中；
- 参数值含 Jinja 模板语法（`{{`、`{%`、`{#`）；
- `DEPLOY_PLAN` 结构错误（非映射、未知字段、类型错误）；
- 输入超过上限：`DEPLOY_PLAN` 的长度、计划中的任务条目数、字符串参数的长度、`id` 的长度。所有上限定义在一处；
- 任务参数名与 inventory 变量、Ansible 保留变量、引擎变量重名（运行时用 vault 口令读取 inventory 检查）。

### 4.7 输出

- 每个任务在 Jenkins 中显示为独立 stage，名称含阶段与 `id`；
- 控制台末尾打印任务结果汇总；归档 JSON 报告（任务 id、阶段、策略、参数、耗时、结果、复核任务的确认人）；
- 任务结果是一组封闭的状态：成功、失败后停止、失败后继续、超时、中止、内部错误。

### 4.8 inventory 管理（结论 + 示例）

覆盖三个主题，结论需有 Ansible 文档或源码出处，或实验结果：

1. 目录与变量分层：inventory 结构、`group_vars` / `host_vars`、变量优先级、命名；
2. 敏感信息：ansible-vault，以及 Jenkins 凭据如何把 vault 密码交给 Ansible；
3. 校验与审查：`ansible-inventory --graph` / `--list` / `--host`、变量校验、在 CI 中检查 inventory。

示例 inventory 只有一套（不区分环境），包含多个主机组；主机地址使用文档保留地址（RFC 5737）。

## 5. 约束

- 只接受登记过的任务名和参数名；参数通过 JSON 文件以 `-e @file` 传给 Ansible，保持类型；
- 任务参数名不得与 inventory 变量名、Ansible 保留字、引擎变量冲突；
- 仓库中不出现真实凭据；vault 示例使用明确标注的示例口令；
- 不需要 Jenkins 管理员审批任何脚本，也不需要管理员配置全局共享库；
- 有限取值（阶段、读写、失败策略、运行位置、参数类型、参数页形式、job 模式、任务结果）在引擎中是枚举，不用字符串比较；
- 测试用一条命令运行：测试自己启动本地 Jenkins、创建以 `examples/` 为根的 git 仓库，不依赖预先设置的环境变量。

## 6. 非目标

- 多环境（dev / stg / prod）inventory；
- 动态 inventory（云主机自动发现）；
- 回滚、任务并行执行；
- 在参数页上覆盖 inventory 中的主机参数；
- 额外任务的真实实现（数据库变更、外部系统调用等）；
- 参数页的无障碍支持。

## 7. 未指定项与假设

- 未指定：主机组的具体名称与数量。示例假设 3 个主机组，每组 1–2 台主机。
- 未指定：部署的版本号来源。示例假设版本号作为主机组变量保存在 inventory 中，随 git 提交变更。
- 未指定：Jenkins 节点到目标主机的连接方式。示例假设 SSH 私钥保存在 Jenkins 凭据中，通过 `ansiblePlaybook` 的 `credentialsId` 传入；实验中用本机 `local` 连接代替。
- 未指定：任务在部署跳过时是否仍执行。示例假设仍执行（见 4.5）。
- 未指定：只读 job 是否使用与读写 job 不同的凭据。示例假设两个 job 引用相同的凭据 ID。
- 未指定：复核等待的超时。示例假设 30 分钟，可在任务元数据中修改，上限与任务超时相同。
