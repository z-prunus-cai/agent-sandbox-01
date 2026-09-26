# strict-review 工作约定

本分支用于存放对 `lilradish-lite-strangler` 的 strict-review 审查结果。以下规则由仓库所有者设定，每次审查都要遵守。

## 1. 被审代码只放本地，不入库

- 被审代码由压缩包解压到仓库根目录下的 `lilradish-lite-strangler/`，**禁止提交**。
- 双重保护：`.gitignore` 忽略该目录；本地 `.git/info/exclude` 也有同样的条目。
- 审查结果中引用代码时，只写路径和行号，外加定位问题所需的最少片段，不整段复制源码。

## 2. reference-cache：只提交复建步骤，不提交缓存

- 由 Claude 负责准备 reference-cache（已获授权）。它是 skill 使用的 `.reference-cache/`，按 `<source>/<name>@<version>/` 组织。
- 建缓存、加条目都按 `reviews/rules/reference-cache.md` 执行：版本精确锁定、整树克隆、删掉无用内容、写 `SOURCE`、最后加只读锁，新增条目需先经审批。
- 云端容器里没有共享缓存库，所以采用该规则中的第 3 种形态：仓库根目录下的本地目录，目录内放一个只含 `*` 的 `.gitignore`。
- 缓存内容**不提交**，根目录的 `.gitignore` 也忽略 `.reference-cache/`。
- **提交的是复建步骤**，放在 `reviews/reference-cache.md`：每个条目写明来源、名称、版本（tag 或 commit），以及它 `SOURCE` 里的完整命令链，任何人照着做都能重建出同样的缓存。
- 每次审查前补齐缺少的条目（预热）；审查后，子 agent 报告的缓存缺口放到下一批补齐。每次新增条目，都同步更新复建步骤。

## 规则文件（随本分支提交）

skill 依赖的两份规则放在 `reviews/rules/`，它们是 skill 路径下对应文件的来源：

| 文件 | skill 读取的位置 | 用途 |
|---|---|---|
| `reviews/rules/spock-test-guidelines.md` | `~/.claude/rules/spock-test-guidelines.md` | 视角 3（测试）的强制标准 |
| `reviews/rules/reference-cache.md` | `~/.claude/rules/reference-cache.md` | 缓存的建立与条目新增流程 |

skill 本身放在 `.claude/skills/strict-review/SKILL.md`（上传原文，未改动）。这是 Claude Code 项目级 skill 的标准位置，新会话克隆本分支后会自动加载，可直接用 `/strict-review` 调用，无需安装。

两份规则则不同：新容器里 `~/.claude/rules/` 不会自动存在，每次会话开始时先安装：

```sh
mkdir -p ~/.claude/rules && cp reviews/rules/*.md ~/.claude/rules/
```

## 3. 审查结果按次提交到本分支

- 审查范围由仓库所有者逐次指定。
- 每次审查的结果提交为 `reviews/<YYYY-MM-DD>-<范围简称>.md`，内容包括：
  - 覆盖信息（审查目标、agent 数量、发现条数、本次生效的降级项）；
  - 每一轮的发现，原样收录，一轮一个代码块，不去重、不改写、不重新排序；
  - 当次提议的缓存新增条目。
- 同时提交 `reviews/<YYYY-MM-DD>-reviewed-files.json`，列出当日每次审查所审的每个文件的路径和 SHA-256，用来确认结果对应的是哪一版代码。路径相对于 `lilradish-lite-strangler/lite`。
- 讲解（walkthrough）在对话中进行，不写进结果文件，除非仓库所有者另有要求。
- 所有审查都只提供判断，不修改被审代码。

## 4. 当前环境下的降级项（每次都要写进覆盖信息）

- 环境中没有 `strict-reviewer` 这个 agent 类型，改用通用 agent，并下发 skill 规定的子 agent prompt 原文。
- 视角 3（测试）只在审查范围包含测试时才派出。范围排除测试时，每次审查只有 2 个 agent（视角 1 和视角 2 各一个）；skill 规定至少 3 个，这一点写进当次的覆盖信息。
- 被审代码没有 git 历史，不能用“已暂存文件”作为默认目标，必须明确指定范围。
