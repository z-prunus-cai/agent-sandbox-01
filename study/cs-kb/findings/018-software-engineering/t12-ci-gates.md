# L4-06·大主题06-12 CI 持续集成与门禁

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（本组实证工具链另含 git 2.43.0）｜ 核实日期：2026-07-30 ｜ 先修：本课大主题06-3（静态检查与 linter 门禁定位）、大主题06-6（三向合并、merge-base）、大主题06-7（分布式协作、PR 评审门禁、传输协议）、大主题06-8（分支策略：trunk-based 与频繁集成是 CI 的一体两面）、大主题06-9/06-11（测试分层、TDD——门禁里跑的就是这些测试）｜ 一手锚点：Pro Git 2nd ed（https://git-scm.com/book/en/v2）§8.3 Git Hooks + git 2.43.0 自带的 `.git/hooks/*.sample` 脚本（**强·一手，仅承重「本地门禁机制」这一层**）｜ 成熟度：**⚙演进快·锚版本**——工具侧（GitHub Actions / GitLab CI）全部 ⚠、不承重、仅指路；「CI 核心实践」为 ⚠工具无关概念、锚 Fowler 原文

> 承重与分账（务必先读）：本大主题横跨两层，必须分开记账。**第一层是「工具无关的实践」**——「频繁集成、主干始终可构建可发布」，它是一个方法学主张，唯一的准一手锚点是 Martin Fowler「Continuous Integration」文章（⚠准一手、非规范），一律标年份、点明它是经验主张而非标准。**第二层是「本地门禁机制」git hooks**——这一层有真正的一手：Pro Git §8.3 与 git 自带的 sample 脚本，且本报告在本机做了硬实证（见 6.12.4）。**第三层是「托管 CI 平台」**（GitHub Actions / GitLab CI 等）——这一层演进极快，具体关键字/默认值全部来自各家官方文档（⚠二手），一律打 ⚠、标「⚙演进快·锚版本·随时变」、**不承重、仅指路**，本报告只用它们给「概念长什么样」举例，不为任何具体语法/默认值背书。

> 本机实证：6.12.4 git hooks 为一手承重项，已在临时 repo（git 2.43.0）写 `.git/hooks/pre-commit` 脚本做「最小 CI 门禁」硬实证，贴真实命令与输出；其余小主题的托管平台行为**本机不可实证**（无 runner/生产环境），未做实机验证（如实标注），YAML 片段仅为说明用的示意、非取证输出。

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（06-12.1~06-12.5）共享一条主线——「频繁集成要靠自动门禁兜底（1→2），门禁跑得快要靠制品与缓存（3），门禁能前移到本地靠 git hooks（4），门禁要可信要靠可复现构建与依赖锁定（5）」，篇幅适中且单一主题，按 report-format v3 §一默认 1 大主题 = 1 报告，不拆 `-a/-b`。

> 一条主线心智模型：持续集成（CI）回答的核心问题是「**怎样让很多人频繁往同一份代码上合，而主干还能始终保持健康、随时可发布**」。答案分两半：一半是**人的纪律**——每人每天至少把改动合回主干一次，别让分支漂太远（这是大主题06-8 trunk-based 的另一面）；另一半是**机器的门禁**——每次合并前都自动跑一遍构建、测试、静态检查，只有全绿才准进主干。本大主题讲的就是这套「机器门禁」：它由哪些关卡组成（6.12.2）、怎么跑得快（6.12.3）、怎么把关卡提前到你按下 commit 的那一刻（6.12.4）、以及怎么保证「这次绿了、下次在别的机器上还是绿」（6.12.5）。

---

## 6.12.1 CI 核心实践：频繁集成与主干始终可发布

### 6.12.1.1 什么是持续集成（Fowler 的定义）

持续集成（Continuous Integration，CI）是一种软件开发实践。Martin Fowler 站点的「Continuous Integration」文章（martinfowler.com，⚠准一手、非规范；**最近一次大改标注为 2024-01-18**）给的定义是：团队每个成员**至少每天一次**把自己的改动与同事的改动合并到一份共享代码库里。注意这个定义的重心在「**集成的频率**」——它说的不是某个工具或某个服务器，而是「大家多久把代码合到一起一次」这件事本身。

对初学者，先破除一个最常见的误解：CI **不等于**「装了 Jenkins / GitHub Actions」。工具只是用来自动执行 CI 的手段；CI 本身是一条纪律——「别攒着一大堆改动几周才合一次，而是拆小、每天合」。之所以强调频繁，是因为**集成的痛苦随分叉时间指数增长**：两份代码分开得越久、各自改得越多，合并时冲突越大、越难查（这正是大主题06-6 三向合并、大主题06-8「分支寿命要短」讲的同一件事的下游后果）。CI 的口号可以概括成「如果合并很痛，那就更频繁地合，痛到不值得攒」。

### 6.12.1.2 主干始终可构建、可发布

CI 的第二个核心主张是：**主干（mainline / trunk，即通常的 `main` 分支）要始终保持在「可构建、可发布」的健康状态**。Fowler 文章明确指出，做好 CI 之后，「把最新版本发布到生产」应当**纯粹是一个商业决策**，因为主干时时刻刻都是可部署的——技术上任何时刻都能发，发不发只看业务要不要发。

要让这个主张成立，Fowler 列了一组配套实践，其中和「主干健康」直接相关的几条是：

- 构建要**自动化**（automated build）——一条命令就能从源码得到可运行产物，不靠人工点步骤。
- 构建要**自测**（self-testing build）——构建过程里自动跑测试套件，不只是编译通过。
- 每次推送都**在集成机上触发一次主干构建**（每次 commit 都验证），而不是只在开发者本机跑。
- **构建坏了立刻修**（fix broken builds immediately）——红色的主干是团队第一优先级，因为主干一红，所有人后续的集成都建立在坏地基上。

对初学者，关键直觉是「**主干红了会传染**」：只要主干处于构建失败状态，任何在此之上继续开发、继续合并的人都会被连累——他们分不清自己引入的新问题和主干原有的问题。所以 CI 文化里「不许在主干红着的时候下班」「谁弄红谁负责立刻修或回滚」是硬规矩。这也解释了为什么 6.12.2 的自动门禁是 CI 的必需品：光靠「大家自觉别推坏代码」在多人协作下不可靠，必须有台机器每次都无差别地拦一道。

### 6.12.1.3 快速构建与「10 分钟构建」经验值

Fowler 文章把「保持构建快速」（keep the build fast）列为一条核心实践，并给出一个广为流传的经验目标：**构建（含测试）大致控制在 10 分钟量级**。这条不是标准、更不是硬性阈值，而是一个「体感拐点」的经验值。

对初学者要讲清它背后的道理，而不是记住「10 分钟」这个数字。CI 的价值来自**反馈快**：你合一次代码、几分钟内就知道「绿了还是红了」，才敢频繁合。如果一次构建要跑一小时，开发者就会本能地攒着改动少合（因为每次合都要等很久），于是又滑回「大批量、低频集成」的老路，CI 的好处荡然无存。所以「构建要快」不是为了省机器时间，而是为了**保住频繁集成这条纪律本身**——反馈越慢，纪律越难维持。这也正是 6.12.3「制品与缓存」存在的理由：缓存依赖、复用制品，本质都是在为「10 分钟」这个目标服务。

### 6.12.1.4 隐藏半成品：feature flag 与 CI 的配套

Fowler 列的实践里有一条常被初学者忽略：**把进行中的工作（work-in-progress）藏起来**，典型手段是 feature flag（功能开关，见大主题06-8.3）。它回答的是一个看似矛盾的问题——既然要求「每天合回主干」「主干随时可发布」，那没做完的功能怎么办？

答案是「合进去但关掉」。半成品的代码可以每天合回主干（满足频繁集成），但用一个开关把它对用户隐藏（保住主干可发布）。对初学者，这里的关键辨析是**「集成」与「发布」是两回事**：feature flag 让「代码进入主干」和「功能对用户可见」解耦——代码可以先集成、后发布，甚至集成了永远不发布（比如做实验）。这正好为下一个大主题06-13（CD：持续交付 vs 持续部署）埋下伏笔：CI 保证「主干随时能发」，要不要真的自动发到生产、怎么发，是 CD 的话题。

#### 来源与时效（本小主题末集中列）

- ⚠准一手（**非规范·锚年份·经验主张**）：Martin Fowler「Continuous Integration」（https://martinfowler.com/articles/continuousIntegration.html，页面标注最近大改 **2024-01-18**）——CI 定义（每人至少每天合一次）、11 条核心实践（单一源码库、自动化构建、自测构建、每天合回主干、每次 commit 触发主干构建、坏了立刻修、构建保持快速/10 分钟、feature flag 藏半成品、类生产测试环境、变更可见、自动化部署）、「发布是纯商业决策」表述。核实 2026-07-30。
- 交叉核对：与本课大主题06-8（trunk-based：<3 活跃分支、分支寿命<1 天、频繁集成——DORA/《Accelerate》⚠）互为一体两面；与 6.12.4 git hooks（一手）在「门禁前移」上呼应。
- 冲突/分歧：CI 是否「必然更好」为经验主张，Fowler 与 DORA 给经验证据、非规范背书；本报告只记「有较强经验支撑、依赖强自动化前置条件」，不作绝对定论。

## 6.12.2 流水线门禁阶段：构建→单测→静态检查→合并

### 6.12.2.1 什么是流水线门禁（gate / pipeline stage）

流水线门禁（pipeline / gate）是把「一次改动进主干之前必须通过的自动检查」串成的有序关卡。典型顺序是：

构建（build） → 单元测试（test） → 静态检查（lint / static analysis） → 合并（merge）

每一关都是一道「非零退出就拦下」的关口：任一关失败，整条流水线判红，改动**不准进主干**。这套东西是 6.12.1「主干始终健康」的执行机构——把「主干必须可构建、必须自测通过」这些主张，变成一台每次都无差别执行的机器。

对初学者，最好的心智模型是「**关卡由粗到细、由快到慢**」：先跑最便宜、最能快速证伪的检查（能不能编译？装不装得上依赖？），再跑单元测试（大主题06-9 的金字塔底座，快而多），再跑更慢的集成/端到端测试和静态分析。这个顺序不是随意的——**让最容易失败、最便宜的检查先跑，坏改动就能最早被拦下、最省时间**（对应 6.12.1.3「构建要快」和大主题06-3「越早发现越省」）。

### 6.12.2.2 阶段里跑的是什么：复用前面大主题的产物

门禁的每一关，跑的都是本课前面大主题已经讲过的东西，CI 只是把它们**自动化、强制化**：

- 构建关——大主题06-3 的编译期检查（类型、语法）在这里第一次统一执行。
- 单测关——大主题06-9 测试金字塔的底层（unit）、大主题06-11 TDD 写出来的测试，在这里对每次改动回归。
- 静态检查关——大主题06-3.4 的 linter / 静态分析器（如类型检查器、代码规范检查）在这里当门禁跑。
- 合并关——大主题06-7 的 PR 评审门禁（人工关）+ 前面几关全绿（机器关），两者都过才允许合入主干。

对初学者要点破：**CI 门禁 = 人工评审门禁 + 自动检查门禁 的叠加**。大主题06-7 讲的 PR 评审是「人来看」，本大主题讲的自动关卡是「机器来跑」；成熟团队通常要求两者都通过（GitHub/GitLab 上体现为「required reviews + required status checks 都满足才允许 merge」，⚠具体配置项名随平台版本变，锚各家文档）。

### 6.12.2.3 托管平台上门禁长什么样（⚠示意·锚版本·随时变）

在托管 CI 平台上，门禁通常写成一个声明式的 YAML 流水线。**以下均为说明概念用的示意，不为任何具体关键字/默认值背书，语法随平台版本演进**（⚙演进快·锚版本·随时变，核实 2026-07-30）。

GitHub Actions（docs.github.com/actions，⚠二手）的模型是「workflow 里含多个 job，job 里含多个 step；job 之间用 `needs` 声明依赖，形成先后顺序」。一个把「构建→测试」串起来的最小示意：

```yaml
# .github/workflows/ci.yml —— 概念示意，非取证输出（GitHub Actions 语法，⚠锚版本）
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: make build          # 构建关
  test:
    needs: build                 # 声明依赖 → build 绿了才跑 test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: make test           # 单测关（非零退出即判红，拦下合并）
```

GitLab CI（docs.gitlab.com，⚠二手）的模型不同：它用顶层 `stages` 列出阶段的顺序，每个 job 用 `stage` 声明自己属于哪一阶段。据 GitLab CI/CD YAML 官方文档（核实 2026-07-30）：**同一 stage 内的 job 并行跑；下一 stage 的 job 要等上一 stage 全部成功后才开始**；若不写 `stages`，默认阶段为 `.pre`、`build`、`test`、`deploy`、`.post`。示意：

```yaml
# .gitlab-ci.yml —— 概念示意，非取证输出（GitLab CI 语法，⚠锚版本）
stages: [build, test]          # 阶段顺序：build 全绿后才进 test
build-job:
  stage: build
  script: [make build]
test-job:
  stage: test
  script: [make test]
```

对初学者，两点辨析很重要。其一，**两家的默认粒度相反**：GitHub Actions 里 job 默认并行、要靠 `needs` 显式串起先后；GitLab CI 里 stage 默认串行、同 stage 内 job 才并行（GitLab 也有 `needs` 关键字可打破 stage 顺序、形成 DAG 流水线，据其官方文档「`needs` 让 job 早于 stage 顺序执行」）。其二，**别背这些关键字**——它们随平台版本变（比如 action 的 `@v4` 版本号会涨），本报告只借它们说明「门禁 = 有序关卡」这个概念，具体写法务必以你当下用的平台版本文档为准。

### 6.12.2.4 门禁失败即拦：退出码是通用契约

无论在本地 git hook 还是托管平台，门禁「拦不拦」的判据高度一致：**被调用的脚本/步骤以非零退出码结束，就算失败、就拦下**。这是 Unix 的通用约定，也是 CI 能把任意工具（编译器、测试框架、linter）串进流水线的基础——CI 不需要理解每个工具在做什么，只看它「退出码是不是 0」。

对初学者，这解释了为什么写 CI 脚本时「**让失败真的返回非零**」如此关键。一个常见的坑是：脚本里跑了检查、打印了错误信息，但最后 `exit 0` 或没检查中间命令的退出码，导致门禁「假绿」——检查其实失败了，流水线却放行。这与 6.12.4 git hooks 用的是同一套契约（hook 非零退出即阻止操作），也是下一节本机实证要演示的核心机制。

#### 来源与时效（本小主题末集中列）

- ⚠二手（**⚙演进快·锚版本·随时变·不承重·仅指路**）：GitHub Actions 官方文档（https://docs.github.com/actions，workflow/job/step/`needs` 模型、artifacts 与 job 的关系）；GitLab CI/CD YAML 官方文档（https://docs.gitlab.com/ee/ci/yaml/，`stages`/`stage`/`needs`/`rules` 语义、「同 stage 并行、stage 间串行」默认行为、默认阶段 `.pre/build/test/deploy/.post`）。所锚为 2026-07-30 时点文档，具体关键字/默认值随版本变，未做本机实证。
- 概念锚点：门禁「构建→测试→静态检查→合并」的顺序理由回指大主题06-3（越早越省）、06-9（金字塔快在底）；「退出码非零即拦」与 6.12.4 一手实证同源。
- 冲突/分歧：GitHub Actions（job 默认并行、`needs` 串联）与 GitLab CI（stage 默认串行、同 stage 并行）默认粒度相反，两边都记、不混用。

## 6.12.3 制品与构建缓存：加速门禁的两种数据搬运

### 6.12.3.1 制品（artifact）：把一次构建的产物保存并传递

制品（artifact，构建产物）是流水线某一步产出、需要**保留下来或传给后续步骤/给人下载**的文件集合——典型如编译好的二进制、打好的包、测试报告、覆盖率报告。托管平台通常提供上传/下载制品的机制（GitHub Actions 里是 `actions/upload-artifact` 与 `actions/download-artifact`；GitLab CI 里是 job 的 `artifacts` 关键字，⚠均锚各家文档、随版本变）。

对初学者，先建立「**为什么需要制品**」的画面：托管 CI 里每个 job 通常跑在**全新、彼此隔离的环境**里（GitHub Actions 文档明确「每个 job 跑在新环境，一个 job 造的文件不会自动出现在另一个 job」）。所以 build job 编译出的二进制，test job 默认是看不到的——必须由 build 把它作为制品上传，test 再下载。制品就是**跨 job、跨隔离环境搬运「构建结果」的官方通道**。它也是连接本大主题和大主题06-13 CD 的纽带：能被部署到各环境的，正是流水线产出的那个不可变制品（build once, deploy many）。

### 6.12.3.2 缓存（cache）：复用上次的依赖，别每次重下

构建缓存（cache）是把「构建过程中可重复利用、但重算/重下很慢的中间产物」存起来、下次直接复用——最典型的就是**第三方依赖**（`node_modules`、pip/maven 下载的包、编译中间产物）。GitHub Actions 用 `actions/cache` action，GitLab CI 用 job 的 `cache` 关键字（⚠均锚各家文档、随版本变，核实 2026-07-30）。

对初学者，缓存的动机直接服务于 6.12.1.3「构建要快」：一次 CI 跑里，从零下载所有依赖往往是最耗时的一步；如果依赖没变（由 6.12.5 的 lockfile 判定「变没变」），就没必要每次重下。缓存命中就能把几分钟压到几秒，直接帮流水线守住「10 分钟」目标。

### 6.12.3.3 制品 vs 缓存：一字之差、用途相反

制品和缓存都是「把文件存下来跨步骤用」，初学者极易混淆，但两者定位相反，必须分清（GitHub Actions 文档专门对比过这两者）：

- 制品（artifact）——**用途是「传递你关心的结果」**：build 产出、test/deploy 消费；也给人下载留档。它是流水线的**输出**，正确性重要，一般绑定到某一次具体的 run。
- 缓存（cache）——**用途是「加速、复用不关心具体内容的中间物」**：典型是依赖包，跨不同 run、不同流水线共享。它是**优化**，可有可无——缓存丢了顶多变慢，不影响结果正确性。

一句话记忆法：**制品是你要的东西、缓存是为了跑得快**。判据是「丢了会怎样」——制品丢了后续步骤拿不到该拿的结果（错误）；缓存丢了只是这次慢一点、重新下载即可（不影响正确性）。这条区分也解释了一个安全直觉：**缓存不该影响构建结果**——如果你的构建「依赖缓存里恰好有某个东西」才能成功，那就说明构建不可复现了，问题该由 6.12.5 的依赖锁定来治，而不是靠缓存兜底。

#### 来源与时效（本小主题末集中列）

- ⚠二手（**⚙演进快·锚版本·随时变·不承重·仅指路**）：GitHub Actions 官方文档「Workflow artifacts」/「Caching dependencies」（https://docs.github.com/actions，`actions/upload-artifact`、`actions/download-artifact`、`actions/cache`；「每 job 全新隔离环境」「artifact 传结果 / cache 加速」的对比）；GitLab CI/CD YAML 文档（`artifacts` 与 `cache` 关键字，「cache 在 pipeline/job 间共享、在 artifacts 之前恢复」）。所锚为 2026-07-30 时点，action 版本号/关键字随版本变，未做本机实证。
- 概念锚点：缓存服务于 6.12.1.3「构建要快」；制品的不可变性回指大主题06-13 CD「build once, deploy many」；「缓存不该影响结果」指向 6.12.5 可复现构建。

## 6.12.4 git hooks 本地门禁：把关卡前移到 commit/push（★本机硬实证）

### 6.12.4.1 什么是 git hook：git 在关键时刻回调的脚本

git hook（钩子）是 git 在执行某些操作的关键时刻**自动调用的脚本**。Pro Git 2nd ed §8.3「Git Hooks」（一手）与 git 官方 githooks(5) man 页描述：每个 git 仓库初始化时，`.git/hooks/` 目录下会放一批**以 `.sample` 结尾的示例脚本**；把某个脚本去掉 `.sample` 后缀、并赋予可执行权限，git 就会在对应时机调用它。本机实证（git 2.43.0，`git init` 后 `ls .git/hooks/`）确认默认放置的 sample 有：

```
applypatch-msg  commit-msg  fsmonitor-watchman  post-update
pre-applypatch  pre-commit  pre-merge-commit  pre-push  pre-rebase
pre-receive  prepare-commit-msg  push-to-checkout  sendemail-validate  update
```

对初学者，把 hook 理解成「git 生命周期里的**事件回调**」：你不主动调它，是 git 在「你正要提交」「你正要推送」这些节点上帮你调。它是把 6.12.2 那套「构建/测试/lint 门禁」**前移到开发者本机、在坏代码离开你电脑之前就拦住**的手段——比等推到服务器、等托管 CI 跑完再红，反馈快得多。

关键分账：hook 分两类。**客户端 hook**（pre-commit、pre-push 等）跑在开发者本机，**不随 clone 分发**（`.git/hooks` 不在版本控制里），因此它是「本地便利/自律」而非「团队强制门禁」——真正不可绕过的门禁必须放在服务端（下 6.12.4.4）。**服务端 hook**（pre-receive、update、post-update）跑在接收推送的远端仓库上，才是集中强制点。

### 6.12.4.2 pre-commit 与 pre-push 的触发时机（一手核实）

据 Pro Git §8.3 与 git 2.43.0 自带 sample 脚本头部注释（一手），两个最常用于本地门禁的客户端 hook：

- **pre-commit**——在 `git commit` 时、**在录入提交信息之前**被调用，**不带参数**。若脚本以**非零退出**，则**中止本次提交**。用途：检查「即将被提交」的内容（跑测试、跑 linter、拦禁止的标记）。本机 `pre-commit.sample` 头部原文即写：「Called by "git commit" with no arguments. The hook should exit with non-zero status after issuing an appropriate message if it wants to stop the commit.」
- **pre-push**——在 `git push` 时、**远端状态已检查、但尚未传输任何数据之前**被调用；带**两个参数**（远端名、远端 URL），并从**标准输入**逐行收到将要推送的引用信息（格式 `<local ref> <local oid> <remote ref> <remote oid>`）。若脚本非零退出，则**不推送任何东西**。本机 `pre-push.sample` 头部原文即写：「Called by "git push" ... before anything has been pushed. If this script exits with a non-zero status nothing will be pushed.」

对初学者，抓住两点。其一，**判据统一是退出码**——和 6.12.2.4 托管平台完全同一套契约：脚本非零退出即「拦」。其二，**两者拦的粒度不同**：pre-commit 拦「单次提交」，适合放快检查（lint、快单测）；pre-push 拦「一批要推出去的提交」，适合放稍重一点、但仍希望在推送前跑的检查（比如整套单测）——因为 push 频率比 commit 低，容忍更慢一点的门禁。

### 6.12.4.3 本机硬实证：用 pre-commit 写一个最小 CI 门禁

在临时 repo（写仓库外 scratchpad，git 2.43.0）里写一个 `.git/hooks/pre-commit`，让它当「最小 CI 门禁」：关卡一是一个 lint——拒绝提交含 `FIXME` 标记的暂存文件；关卡二是一个单测——跑一段 Python 断言。脚本：

```sh
#!/bin/sh
echo "[pre-commit] running minimal CI gate..."
# gate 1: lint —— 暂存区里含 FIXME 就拦
if git diff --cached --name-only | xargs grep -l "FIXME" 2>/dev/null; then
    echo "[pre-commit] FAIL: staged files contain FIXME"
    exit 1
fi
# gate 2: unit test
if ! python3 -c "assert 2 + 2 == 4"; then
    echo "[pre-commit] FAIL: unit test failed"
    exit 1
fi
echo "[pre-commit] PASS"
exit 0
```

赋予可执行权限（`chmod +x .git/hooks/pre-commit`）后，两次真实提交的输出（本机实证，2026-07-30）：

```
=== 尝试 1：暂存文件含 FIXME（应被拦） ===
[pre-commit] running minimal CI gate...
app.py
[pre-commit] FAIL: staged files contain FIXME
exit code = 1                      # ← 提交被中止，git log 里没有这次提交

=== 尝试 2：干净文件（应通过） ===
[pre-commit] running minimal CI gate...
[pre-commit] PASS
[master (root-commit) 4895ad2] add app
 1 file changed, 2 insertions(+)   # ← 提交成功落库
exit code = 0
```

对初学者，这个实证坐实了整个大主题的核心机制：**门禁 = 一个在关键时刻被回调、以退出码表决「放行/拦下」的脚本**。托管 CI 平台本质就是把同一件事搬到服务器、并加上 UI、并行、缓存、制品等工程化包装——但内核就是这段十几行的脚本。理解了这段，就理解了 CI 门禁的骨。

### 6.12.4.4 本地 hook 的边界：可被 `--no-verify` 绕过，故非强制门禁

客户端 hook 有一个**必须让初学者知道的边界**：它可以被开发者轻易绕过。`git commit --no-verify`（等价 `-n`）会**跳过 pre-commit 和 commit-msg hook**；`git push --no-verify` 会跳过 pre-push hook。本机实证（2026-07-30）：对上面那个会拦 FIXME 的 pre-commit，用 `--no-verify` 提交一个含 FIXME 的文件——

```
=== --no-verify 绕过（hook 被跳过） ===
[master 0288b4e] bypass with FIXME
 1 file changed, 2 insertions(+)   # ← hook 完全没跑，含 FIXME 的提交照样落库
exit code = 0
```

可见 hook 一行都没执行、坏内容照进。这带出一条关键结论：**客户端 git hook 是「本地便利」，不是「团队强制门禁」**——它帮自律的开发者在本机早点发现问题，但既拦不住存心绕过的人，也不随 `git clone` 分发给别人（`.git/hooks` 不进版本控制，新克隆的人默认没有你的 hook）。

对初学者，正确的心智是**分层防御**：本地 hook 做「快而可绕」的早筛（省往返），真正不可绕过的强制门禁必须放在**服务端**——要么是服务端 hook（pre-receive/update，跑在远端仓库上、开发者无法用 `--no-verify` 跳过），要么是托管平台的「required status checks」（PR 必须 CI 全绿+评审通过才允许 merge，见 6.12.2.2）。这也是为什么严肃团队不把安全性/合规性检查只放在 pre-commit：它只是第一道软拦截，不是最后一道硬防线。（补充：`pre-commit` 这个词在业界也常指一个流行的**同名跨语言 hook 管理框架** pre-commit.com，⚠二手、非 git 内建——它用一个受版本控制的配置文件统一安装管理 hook，缓解了「hook 不随 clone 分发」的痛点，但仍是客户端 hook、仍可被 `--no-verify` 绕过，不改变上述边界结论。）

#### 来源与时效（本小主题末集中列）

- 一手（**承重**）：Pro Git 2nd ed §8.3 Git Hooks（https://git-scm.com/book/en/v2/Customizing-Git-Git-Hooks，客户端/服务端 hook 分类、pre-commit/pre-push/pre-receive 时机、非零退出即中止、`.git/hooks/*.sample` 机制、hook 不随 clone 分发）；git 官方 githooks(5) man（同上语义，本机 man 页未安装、按在线 git 文档核实）；**本机 git 2.43.0 自带 `pre-commit.sample`/`pre-push.sample` 头部注释**（触发时机、参数、stdin 格式、退出码语义——一手原文，已引用）。核实 2026-07-30。
- 本机硬实证（git 2.43.0 @2026-07-30，脚本与命令写仓库外 scratchpad）：pre-commit 拦含 FIXME 提交（exit 1、log 无该提交）、放行干净提交（exit 0、成功落库）、`--no-verify` 完全跳过 hook（含 FIXME 照样落库）——三段真实输出已贴正文。
- ⚠二手（不承重、仅指路）：pre-commit.com（同名 hook 管理框架，非 git 内建）。
- 冲突/分歧：无一手层冲突；「本地 hook 是否算门禁」需按客户端/服务端分账——客户端可绕、非强制，服务端/托管 required checks 才强制。

## 6.12.5 构建可复现与依赖锁定：让「这次绿」到「处处绿」

### 6.12.5.1 为什么需要可复现构建

可复现构建（reproducible / deterministic build）指：**同一份源码，在不同时间、不同机器上构建，都得到相同结果**。它是 CI 可信的地基——如果构建不可复现，那么「CI 上绿了」这件事就不保真：可能在 CI 机器上绿、在你机器上红，或者今天绿、明天因为某个上游依赖悄悄发了新版就红。门禁的意义在于「绿=安全」，而这个等式只有在构建可复现时才成立。

对初学者，最常见的破坏可复现性的元凶是**依赖的版本漂移**。假设你的项目声明「依赖某库 `^1.2`（1.2 及以上、2.0 以下都行）」——这是一个**版本范围**，不是一个确定版本。CI 今天装到 1.2.3，明天该库发了 1.9.0，CI 就装到 1.9.0，构建输入其实变了。于是出现经典惨案：「我本机好好的，CI 上莫名其妙挂了」，或反过来。可复现构建要解决的就是这种「声明的是范围、实际装的却飘」的不确定性。

### 6.12.5.2 lockfile：把「实际解析出的确切版本」钉死

依赖锁定（dependency locking）的核心工具是 **lockfile（锁文件）**：它是包管理器生成的一份元数据，记录**某一时刻解析出的、每一个直接与间接（传递）依赖的确切版本**（通常还含完整性校验哈希）。有了 lockfile，后续任何一次安装都复现同一套依赖集，不受上游注册表新发版本影响。各生态的 lockfile（⚠具体格式随工具版本演进，锚 2026-07-30）：

```
npm        →  package-lock.json     （配合 `npm ci` 按锁文件精确安装）
Python/poetry → poetry.lock
Rust/cargo →  Cargo.lock            （配合 `--locked` 强制用锁文件版本）
Ruby       →  Gemfile.lock
Go         →  go.mod + go.sum       （go.sum 存依赖的加密哈希）
pnpm       →  pnpm-lock.yaml
```

对初学者，关键区分是「**清单（manifest）vs 锁文件（lockfile）**」：

- 清单（如 `package.json`、`pyproject.toml`、`Cargo.toml`）——写的是人类意图，常是**版本范围**（「我要 1.2 以上」）。
- 锁文件（如 `package-lock.json`、`Cargo.lock`）——写的是机器解析结果，是**确切版本 + 哈希**（「实际就装 1.2.3，哈希是 …」）。

正确用法是**把 lockfile 一并提交进版本控制**，让 CI 和所有开发者都从同一份锁文件安装。这样「装什么版本」就从「范围里飘」变成「锁文件钉死」，CI 的绿才可复现。这也解释了 6.12.3.2 缓存的命中判定——缓存键通常就基于 lockfile 的哈希：lockfile 没变，说明依赖没变，缓存可安全复用；lockfile 一变，缓存自动失效、重新装。

### 6.12.5.3 「按锁文件精确安装」的命令与完整性校验

光有 lockfile 还不够——还得用「**严格按锁文件安装、不许偷偷更新**」的命令，否则安装工具可能顺手把锁文件改了、又漂了。各生态的严格安装开关（⚠锚 2026-07-30、随工具版本变）：

- npm：`npm ci`——按 `package-lock.json` 精确安装，忽略 `package.json` 里的范围；锁文件与清单不一致时直接报错，而非默默更新。
- Python/pip：`pip install --require-hashes`——要求每个依赖都带固定哈希，装到的包哈希对不上就**失败**（把完整性校验变成强制）。
- Rust/cargo：`--locked`——要求严格使用 `Cargo.lock` 里的版本，锁文件需要变动时报错而非自动改。

对初学者，这里的核心思想是「**把校验前移成硬失败**」：与其信任「装到的应该是对的」，不如让安装工具拿锁文件里的**哈希**逐一核对——哈希是内容的指纹，只要包内容有一丁点不同（哪怕版本号一样、内容被上游篡改），哈希就对不上、安装立即失败。这既保可复现（版本钉死），又保供应链完整性（内容防篡改）。反面教材是「lock 文件漂移（lockfile drift）」——清单和锁文件不同步，导致 CI 装的和你以为的不一致，可复现性和安全性一起破功；用上面这些严格命令就是为了让漂移「早失败、别蒙混」。

### 6.12.5.4 可复现的边界：锁定依赖只是第一层

必须给初学者交代边界：**依赖锁定解决的是「依赖版本可复现」，不等于「构建 100% 位对位可复现（bit-for-bit reproducible）」**。真正严格的可复现构建还要控制更多变量——构建时的时间戳、文件顺序、随机种子、绝对路径、编译器版本、环境变量、locale 等，任何一个混进产物都会让两次构建的字节不同。业界有专门的 Reproducible Builds 运动（reproducible-builds.org，⚠二手）在推进这类「位对位可复现」，手段包括固定时间戳（`SOURCE_DATE_EPOCH`）、清除路径等。

对初学者，分清两个层次即可：**第一层「依赖锁定」是绝大多数团队的日常刚需、门槛低、收益大**（lockfile + 严格安装命令就能拿到），本大主题讲的就是这一层；**第二层「位对位可复现」是更高的追求**（多见于安全敏感、需要可验证供应链的场景），成本高、涉及工具链方方面面，本报告只指出它存在、点到即止，不做纵深。记住主线即可：可复现构建的第一步、也是 CI 可信的最低要求，就是**把依赖钉死并校验哈希**。

#### 来源与时效（本小主题末集中列）

- ⚠二手/工具文档（**⚙演进快·锚版本·随时变·不承重·仅指路**，均核实 2026-07-30）：各包管理器官方文档——npm（`package-lock.json`、`npm ci`）、Python Poetry（`poetry.lock`）与 pip（`--require-hashes`）、Cargo（`Cargo.lock`、`--locked`）、Go（`go.mod`/`go.sum` 哈希）、pnpm（`pnpm-lock.yaml`）；lockfile 通用语义（记录直接+传递依赖确切版本与完整性哈希、确定性安装）为多来源交叉共识。具体命令行为/文件格式随工具版本演进，未做本机实证。
- ⚠二手（前沿·仅指路）：Reproducible Builds 项目（https://reproducible-builds.org，位对位可复现、`SOURCE_DATE_EPOCH`）——本报告只指路、不展开。
- 概念锚点：可复现构建服务于 6.12.1「主干可发布」、6.12.3.2 缓存键基于 lockfile 哈希、大主题06-13 CD「build once, deploy many」的制品不可变前提。
- 待核/不凭记忆：各命令的精确行为细节与默认值随工具版本变，本报告只锚「概念+ 2026-07-30 时点用法」，具体以你当下工具版本文档为准。
