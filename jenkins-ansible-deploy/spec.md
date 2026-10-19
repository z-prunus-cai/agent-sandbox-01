# 规格：Jenkins + Ansible 部署流水线样板

## 1. 目标

一套放在同一个 git 仓库里的 Jenkins 流水线与 Ansible 代码样板，满足：

1. 部署对象是 Ansible inventory 中的**主机组**。构建参数页上从主机组列表中**多选**本次的发布对象。
2. 部署阶段可以在构建参数页上**勾选跳过**。
3. 部署前、部署后各可选择 **0 到任意个额外任务**。参数页上只显示所选任务的参数输入框，可逐项填写；不同任务的参数类型不同。
4. 主机与主机组的各种参数（端口、路径、版本号等）**只在 inventory 中管理**，参数页不能覆盖。

## 2. 平台与版本

| 组件 | 版本 |
| --- | --- |
| Jenkins | 2.580.1 |
| Jenkins Ansible 插件（`ansible`） | 635.v34b_48e979c86 |
| Active Choices 插件（`uno-choice`） | 2.8.11 |
| ansible-core | 2.21.5 |
| 其他 Jenkins 插件（Pipeline、`pipeline-utility-steps` 等） | 与 Jenkins 2.580.1 兼容的最新版本，实验时记录确切版本 |
| Python | 3.13（运行 ansible-core 和测试） |

Jenkinsfile 从 SCM 加载，运行在 Groovy 沙箱中，**不需要任何脚本审批**。

## 3. 交付物

目录 `jenkins-ansible-deploy/`，文档用中文，代码标识符用英文：

| 路径 | 内容 |
| --- | --- |
| `BRIEF.md`、`attachments/` | 说明文档 |
| `examples/Jenkinsfile` | Active Choices 版：使用 Active Choices，单页参数表单 |
| `examples/Jenkinsfile.no-plugin` | 无插件版：不装插件，勾选任务后在构建开始时弹出参数表单（两步式） |
| `examples/jenkins/` | 两个 Jenkinsfile 共用的引擎代码与任务目录 |
| `examples/ansible/` | inventory、部署 playbook、额外任务（每个任务一个 role） |
| `tests/` | 自动化测试 |

## 4. 行为

### 4.1 输入（构建参数）

| 参数 | Active Choices 版 | 无插件版 |
| --- | --- | --- |
| 发布对象 | 主机组多选列表，选项来自 inventory | 每个主机组一个勾选框，来自 inventory |
| 跳过部署 | `SKIP_DEPLOY` 勾选框 | 同左 |
| 部署前任务 | 多选列表 + 随选择实时变化的参数表单 | 每个任务一个勾选框；构建开始后用 `input` 步骤弹出所选任务的表单 |
| 部署后任务 | 同上 | 同上 |
| 完整计划 | `DEPLOY_PLAN`（YAML 文本），非空时忽略上面所有参数，供 API / 上游 job 触发 | 同左 |

`DEPLOY_PLAN` 格式：

```yaml
targets: [group_1, group_2]
skip_deploy: false
pre:
  - task_a                                    # 全部默认值
  - {name: task_b, id: b_1, policy: warn, params: {key: value}}
post:
  - {name: check_a, timeout_minutes: 5}
```

### 4.2 任务

- 任务是**抽象占位任务**（如 `task_a`、`check_a`），不调用任何外部工具；只演示参数接收、校验、成功与失败。
- 每个任务是一个 Ansible role，参数（名称、类型、必填、默认值、说明）用 role 的 `meta/argument_specs.yml` 声明。
- 任务目录（Jenkins 侧元数据）：说明、允许的阶段（pre / post）、默认失败策略、超时、运行位置（`targets`：所选主机组；`controller`：Jenkins 节点本机，可访问工作区）。
- 参数类型至少覆盖：字符串、整数、布尔、枚举（choices）。
- 一个阶段内的执行顺序：参数页选择时按任务目录的顺序；`DEPLOY_PLAN` 中按列表顺序。同一任务可在一个阶段内用不同 `id` 运行多次（仅 `DEPLOY_PLAN`）。
- 失败策略两种：`abort`（停止流水线，FAILURE）与 `warn`（标记 UNSTABLE，继续）。每次构建可逐个任务修改。
- 每个任务有超时，超时按任务失败处理，再交给策略。手动中止构建不能被当作任务失败而继续。

### 4.3 部署

- 部署 playbook 只作用于所选主机组；主机参数全部来自 inventory（`group_vars` / `host_vars`）。
- 部署本身也是占位：校验主机参数齐全、类型正确，并输出将要执行的动作，不改动目标主机。
- 跳过部署时，部署前、部署后任务照常按选择执行。

### 4.4 校验（在任何 playbook 运行之前，一次列出全部问题后使构建失败）

- 主机组不在 inventory 中，或在需要目标主机时未选主机组；
- 未知任务、任务不允许用于该阶段、未知策略、`id` 不合法或重复；
- 未声明的参数名、必填参数为空、整数不是整数、枚举值不在 choices 中；
- 参数值含 Jinja 模板语法（`{{`、`{%`、`{#`）；
- `DEPLOY_PLAN` 结构错误（非映射、未知字段、类型错误）。

### 4.5 输出

- 每个任务在 Jenkins 中显示为独立 stage，名称含阶段与 `id`；
- 控制台末尾打印任务结果汇总；归档 JSON 报告（任务 id、阶段、策略、参数、耗时、结果）。

### 4.6 inventory 管理（结论 + 示例）

覆盖三个主题，结论需有 Ansible 文档或源码出处，或实验结果：

1. 目录与变量分层：inventory 结构、`group_vars` / `host_vars`、变量优先级、命名；
2. 敏感信息：ansible-vault，以及 Jenkins 凭据如何把 vault 密码交给 Ansible；
3. 校验与审查：`ansible-inventory --graph` / `--list` / `--host`、变量校验、在 CI 中检查 inventory。

示例 inventory 只有一套（不区分环境），包含多个主机组；主机地址使用文档保留地址（RFC 5737）。

## 5. 约束

- 只接受登记过的任务名和参数名；参数通过 JSON 文件以 `-e @file` 传给 Ansible，保持类型；
- 任务参数名不得与 inventory 变量名、Ansible 保留字冲突；
- 仓库中不出现真实凭据；vault 示例使用明确标注的示例口令；
- 不需要 Jenkins 管理员审批任何脚本。

## 6. 非目标

- 多环境（dev / stg / prod）inventory；
- 动态 inventory（云主机自动发现）；
- 回滚、人工确认（ask）策略、任务并行执行；
- 在参数页上覆盖 inventory 中的主机参数；
- 额外任务的真实实现（数据库变更、外部系统调用等）。

## 7. 未指定项与假设

- 未指定：主机组的具体名称与数量。示例假设 3 个主机组，每组 1–2 台主机。
- 未指定：部署的版本号来源。示例假设版本号作为主机组变量保存在 inventory 中，随 git 提交变更。
- 未指定：Jenkins 节点到目标主机的连接方式。示例假设 SSH 私钥保存在 Jenkins 凭据中，通过 `ansiblePlaybook` 的 `credentialsId` 传入；实验中用本机 `local` 连接代替。
- 未指定：任务在部署跳过时是否仍执行。示例假设仍执行（见 4.3）。
