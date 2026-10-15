# Jenkins 与 Ansible 文档（本地副本）

- 研究报告：[research.md](research.md)，内容是如何给已有的 Jenkins 部署流水线加上部署前/部署后检查。这些检查每次部署可以自由选择、可以设置为阻塞或非阻塞，并且可以带参数。
- 可运行的示例：[../../examples/deploy-checks](../../examples/deploy-checks)

**`upstream/` 目录只保存在本地，不提交到 git**（已写入 `.gitignore`）。需要时运行下面的命令下载：

```bash
pip install beautifulsoup4 markdownify ansible-core   # 只需要装一次
docs/jenkins-ansible/fetch-docs.sh                    # 下载到 docs/jenkins-ansible/upstream/
```

下载到的是各项目最新的官方文档原文，没有改动。每个来源对应的提交号会记录在 `upstream/SOURCES.txt` 中。下面的说明以 2026-10-15 下载的版本为准。Jenkins 文档是 AsciiDoc（`.adoc`），Ansible 文档是 reStructuredText（`.rst`），用文本编辑器就能阅读。本目录和 [research.md](research.md) 中指向 `upstream/` 的链接，在下载之后才能打开。

```
upstream/
├── jenkins/
│   ├── handbook/          Jenkins 官方文档全集（jenkins.io 的 /doc 部分）
│   └── pipeline-steps/    Pipeline 步骤参考（核心插件）
├── ansible/
│   ├── docsite/           Ansible 官方文档全集（docs.ansible.com 的源文件）
│   └── builtin-plugins/   ansible.builtin 所有模块和插件的参考（用 ansible-doc 生成）
└── ansible-plugin/        Jenkins Ansible 插件文档
```

## Jenkins

### upstream/jenkins/handbook：Jenkins 官方文档全集

来源：[jenkins-infra/jenkins.io](https://github.com/jenkins-infra/jenkins.io) 仓库中的 `content/doc` 目录，提交 `533ecccc`，对应 <https://www.jenkins.io/doc/>。共 359 篇 `.adoc` 文档，附带配图。

| 入口 | 内容 |
| --- | --- |
| [index.adoc](upstream/jenkins/handbook/index.adoc) | 文档首页 |
| [book/](upstream/jenkins/handbook/book) | User Handbook：安装、使用、管理、安全、扩展、故障排查等 |
| [book/pipeline/](upstream/jenkins/handbook/book/pipeline) | Pipeline 章节：[语法](upstream/jenkins/handbook/book/pipeline/syntax.adoc)、[Jenkinsfile](upstream/jenkins/handbook/book/pipeline/jenkinsfile.adoc)、[共享库](upstream/jenkins/handbook/book/pipeline/shared-libraries.adoc)、[最佳实践](upstream/jenkins/handbook/book/pipeline/pipeline-best-practices.adoc)、[CPS 方法不匹配](upstream/jenkins/handbook/book/pipeline/cps-method-mismatches.adoc) 等 |
| [pipeline/tour/](upstream/jenkins/handbook/pipeline/tour) | 入门教程（Guided Tour） |
| [tutorials/](upstream/jenkins/handbook/tutorials) | 教程 |
| [administration/](upstream/jenkins/handbook/administration)、[upgrade-guide/](upstream/jenkins/handbook/upgrade-guide) | 管理与升级指南 |
| [developer/](upstream/jenkins/handbook/developer) | 插件开发文档 |

### upstream/jenkins/pipeline-steps：Pipeline 步骤参考

jenkins.io 上的步骤参考页面是根据各插件的代码自动生成的，不在上面那个仓库里，所以单独从 <https://www.jenkins.io/doc/pipeline/steps/> 下载，并转成了 Markdown。下面只列出 Pipeline 核心插件和常用插件。其他插件的步骤可以在 [INDEX-all-plugins.md](upstream/jenkins/pipeline-steps/INDEX-all-plugins.md) 中查找。

| 文件 | 包含的步骤 |
| --- | --- |
| [workflow-basic-steps.md](upstream/jenkins/pipeline-steps/workflow-basic-steps.md) | `error`、`catchError`、`warnError`、`unstable`、`timeout`、`retry`、`sleep`、`echo`、`writeFile`、`readFile`、`archiveArtifacts` 等 |
| [workflow-durable-task-step.md](upstream/jenkins/pipeline-steps/workflow-durable-task-step.md) | `node`、`sh`、`bat`、`powershell`、`ws` |
| [workflow-cps.md](upstream/jenkins/pipeline-steps/workflow-cps.md) | `load`、`parallel` 等 |
| [pipeline-model-definition.md](upstream/jenkins/pipeline-steps/pipeline-model-definition.md) | Declarative 相关步骤 |
| [pipeline-input-step.md](upstream/jenkins/pipeline-steps/pipeline-input-step.md) | `input` |
| [pipeline-build-step.md](upstream/jenkins/pipeline-steps/pipeline-build-step.md) | `build`（触发其他 job） |
| [pipeline-stage-step.md](upstream/jenkins/pipeline-steps/pipeline-stage-step.md)、[pipeline-milestone-step.md](upstream/jenkins/pipeline-steps/pipeline-milestone-step.md) | `stage`、`milestone` |
| [pipeline-utility-steps.md](upstream/jenkins/pipeline-steps/pipeline-utility-steps.md) | `readYaml`、`readJSON`、`writeJSON` 等 |
| [workflow-scm-step.md](upstream/jenkins/pipeline-steps/workflow-scm-step.md)、[git.md](upstream/jenkins/pipeline-steps/git.md) | `checkout`、`git` |
| [credentials-binding.md](upstream/jenkins/pipeline-steps/credentials-binding.md)、[ssh-agent.md](upstream/jenkins/pipeline-steps/ssh-agent.md) | `withCredentials`、`sshagent` |
| [lockable-resources.md](upstream/jenkins/pipeline-steps/lockable-resources.md)、[timestamper.md](upstream/jenkins/pipeline-steps/timestamper.md)、[ws-cleanup.md](upstream/jenkins/pipeline-steps/ws-cleanup.md) | `lock`、`timestamps`、`cleanWs` |

## Ansible

### upstream/ansible/docsite：Ansible 官方文档全集

来源：[ansible/ansible-documentation](https://github.com/ansible/ansible-documentation) 仓库中的 `docs/docsite/rst` 目录，提交 `8f66c43b`（devel 分支），也就是 <https://docs.ansible.com> 的源文件。共 411 篇 `.rst`。下载时 docs.ansible.com 返回 429（限流），所以改从源码仓库获取。

| 入口 | 内容 |
| --- | --- |
| [getting_started/](upstream/ansible/docsite/getting_started)、[installation_guide/](upstream/ansible/docsite/installation_guide) | 入门、安装 |
| [inventory_guide/](upstream/ansible/docsite/inventory_guide) | Inventory |
| [playbook_guide/](upstream/ansible/docsite/playbook_guide) | Playbook：变量、条件、循环、[错误处理](upstream/ansible/docsite/playbook_guide/playbooks_error_handling.rst)、[block](upstream/ansible/docsite/playbook_guide/playbooks_blocks.rst)、[tags](upstream/ansible/docsite/playbook_guide/playbooks_tags.rst)、[check mode](upstream/ansible/docsite/playbook_guide/playbooks_checkmode.rst)、模板等 |
| [command_guide/](upstream/ansible/docsite/command_guide) | `ansible`、`ansible-playbook` 等命令行工具 |
| [vault_guide/](upstream/ansible/docsite/vault_guide) | Ansible Vault |
| [module_plugin_guide/](upstream/ansible/docsite/module_plugin_guide)、[plugins/](upstream/ansible/docsite/plugins) | 模块与插件概念 |
| [collections_guide/](upstream/ansible/docsite/collections_guide) | Collections |
| [reference_appendices/](upstream/ansible/docsite/reference_appendices) | 参考：[变量优先级](upstream/ansible/docsite/reference_appendices/general_precedence.rst)、[特殊变量](upstream/ansible/docsite/reference_appendices/special_variables.rst)、[常见返回值](upstream/ansible/docsite/reference_appendices/common_return_values.rst)、FAQ 等 |
| [porting_guides/](upstream/ansible/docsite/porting_guides) | 各版本升级说明 |
| [dev_guide/](upstream/ansible/docsite/dev_guide)、[network/](upstream/ansible/docsite/network)、[os_guide/](upstream/ansible/docsite/os_guide)、[tips_tricks/](upstream/ansible/docsite/tips_tricks) | 开发、网络设备、Windows/BSD、技巧 |

注意：有几类页面是在构建文档时自动生成的，所以不在这个仓库里：各 collection 的模块文档（如 `community.general`）、Playbook 关键字列表、配置项列表。其中 `ansible.builtin` 模块和 Playbook 关键字可以看下面的 builtin-plugins；配置项可以用 `ansible-config list` 查看。

### upstream/ansible/builtin-plugins：ansible.builtin 模块与插件参考

用本地安装的 **ansible-core 2.21.5**，通过 `ansible-doc -t <类型> <名称>` 为每个 `ansible.builtin` 插件生成纯文本文档。内容与 docs.ansible.com 上对应的页面一致，包括参数说明、示例和返回值。

| 目录 | 数量 | 例子 |
| --- | --- | --- |
| [module/](upstream/ansible/builtin-plugins/module) | 71 | `assert`、`fail`、`uri`、`wait_for`、`command`、`copy`、`service_facts` |
| [filter/](upstream/ansible/builtin-plugins/filter) | 122 | `default`、`to_json`、`b64decode`、`regex_replace` |
| [test/](upstream/ansible/builtin-plugins/test) | 82 | `match`、`version`、`failed` |
| [lookup/](upstream/ansible/builtin-plugins/lookup) | 25 | `env`、`file`、`pipe` |
| callback / connection / inventory / become / cache / shell / strategy / vars | 31 | `default` 回调、`ssh`、`local`、`sudo` 等 |
| [keywords.txt](upstream/ansible/builtin-plugins/keywords.txt) | 60 | 全部 Playbook 关键字（`until`、`retries`、`ignore_errors`、`timeout` 等） |

生成新版本的文档时，运行 `ansible-doc -t module ansible.builtin.uri` 即可，其他类型同理。

## Jenkins Ansible 插件

### upstream/ansible-plugin

来源：[jenkinsci/ansible-plugin](https://github.com/jenkinsci/ansible-plugin)，提交 `27b52e94`（2026-10-06）。下载时最新发布的版本是 `635.v34b_48e979c86`，要求 Jenkins ≥ 2.516.3。

| 文件 | 内容 |
| --- | --- |
| [README.md](upstream/ansible-plugin/README.md) | 插件官方文档：全局配置、Adhoc、Playbook、Vault、Job DSL、Pipeline 用法、extraVars |
| [jenkins-io-pipeline-steps-ansible.md](upstream/ansible-plugin/jenkins-io-pipeline-steps-ansible.md) | `ansiblePlaybook`、`ansibleAdhoc`、`ansibleVault` 三个步骤的参数参考 |
| [help/](upstream/ansible-plugin/help) | 每个配置项的在线帮助 |
