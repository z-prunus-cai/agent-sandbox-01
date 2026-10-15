# 示例：可选、可配置阻塞策略、带参数的部署前/部署后检查

设计说明和测试记录见 [docs/jenkins-ansible/research.md](../../docs/jenkins-ansible/research.md)。

```
examples/deploy-checks/
├── Jenkinsfile                           已有部署流水线 + 接入检查（改动处标记为 [新增]）
├── checks/catalog.yaml                   检查目录：可用的检查、默认策略、参数白名单与默认值
├── ansible/
│   ├── deploy.yml                        代表原有的部署 playbook（演示用）
│   ├── inventories/demo/hosts.ini        演示 inventory（本机）
│   └── checks/                           每个检查一个 playbook
│       ├── disk_space.yml                pre：磁盘剩余空间
│       ├── port_reachable.yml            pre/post：依赖端口是否可连接
│       ├── service_running.yml           pre/post：系统服务是否在运行
│       ├── http_health.yml               post：HTTP 健康检查
│       └── app_version.yml               post：已部署版本号是否正确
└── jenkins-shared-library/vars/deployChecks.groovy   共享库步骤
```

## 接入到已有流水线

1. 把 `jenkins-shared-library/` 放到一个 Git 仓库中，在 **Manage Jenkins → System → Global Trusted Pipeline Libraries** 中登记，名称填 `deploy-checks`。如果像本示例这样放在子目录中，就在 “Library Path” 里填写这个子目录。
2. 把 `checks/catalog.yaml` 和 `ansible/checks/` 复制到部署仓库，修改 `catalog.yaml` 中的 playbook 路径和默认值。
3. 按 `Jenkinsfile` 中标记为 `[新增]` 的部分修改原有流水线：
   - 在文件开头加上 `@Library('deploy-checks') _`；
   - 添加 4 个参数；
   - 在部署 stage 前后加上 `Resolve checks`、`Pre-deploy checks`、`Post-deploy checks` 三个 stage；
   - 添加 `post { always { deployChecks.report(...) } }`；
   - 在 `ansibleArgs()` 中填写与部署时相同的 inventory / credentialsId / installation。
4. 运行一次，让 Jenkins 记录新增的参数。之后就可以在 “Build with Parameters” 页面中选择检查了。

需要的插件：`ansible`、`pipeline-utility-steps`（提供 `readYaml`）、`workflow-aggregator`（Pipeline）。

## 每次部署怎么选

| 想要的效果 | 参数 |
| --- | --- |
| 使用默认检查 | 全部留空 |
| 部署前只查磁盘，部署后不检查 | `PRE_CHECKS=disk_space`，`POST_CHECKS=none` |
| 服务检查不通过也继续部署 | `PRE_CHECKS=disk_space, service_running:warn` |
| 健康检查失败时人工决定 | `POST_CHECKS=http_health:ask` |
| 修改检查参数 | `CHECK_PARAMS` 填 `disk_space: {path: /var, min_free_mb: 4096}` |
| 同一检查跑两次 / 只检查部分主机 | 使用 `CHECK_PLAN`，见 Jenkinsfile 中该参数的说明 |

策略：`abort` 阻塞（FAILURE，停止）· `warn` 非阻塞（UNSTABLE，继续）· `ignore` 非阻塞（只记录）· `ask` 失败时人工选择继续或中止。

## 单独运行检查 playbook

检查就是普通的 playbook，不经过 Jenkins 也可以直接运行：

```bash
cd examples/deploy-checks/ansible
ansible-playbook -i inventories/demo/hosts.ini checks/disk_space.yml -e '{"path": "/", "min_free_mb": 1024}'
```

## 新增一个检查

1. 在 `ansible/checks/` 下写一个 playbook，条件不满足时让它失败（用 `assert` / `fail`）。
2. 在 `checks/catalog.yaml` 中登记这个检查，写明它允许的参数和默认值。参数名不要使用 Ansible 的保留字（如 `port`、`retries`）。
3. 不需要修改 Jenkinsfile 或共享库。
