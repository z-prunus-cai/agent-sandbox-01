# L4-06·大主题06-7 分布式协作、PR 评审与传输协议

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（本组实证工具链含 git 2.43.0；本篇实机佐证全部用本机 git 2.43.0 + `file://` 本地协议）｜ 核实日期：2026-07-30 ｜ 先修：06-4 Git 对象模型（内容寻址 DAG、commit/tree/blob）、06-5 引用/分支/HEAD（refs/heads、refs/remotes 远程跟踪引用）、06-6 三向合并与 merge-base（fast-forward vs 真合并、rebase 黄金规则）；L4-02 计算机网络（HTTP、SSH、TCP，本篇传输协议建于其上）｜ 一手锚点：Pro Git 2nd ed（https://git-scm.com/book/en/v2 ，正文核实 2026-07-30）—— ch5 Distributed Git（Distributed Workflows / Contributing / Maintaining）、ch6 GitHub、§10.5 The Refspec、§10.6 Transfer Protocols；准一手 MIT 6.031 Software Construction sp22 Reading 04 Code Review（https://web.mit.edu/6.031/www/sp22/classes/04-code-review/ ，核实 2026-07-30）｜ 成熟度：git 侧机制（分布式模型/refspec/传输协议）GA/稳定；平台侧（PR/MR、保护分支/rulesets）⚙演进快·锚版本·随时变

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（06-7.1~06-7.5）虽横跨"协作拓扑 + 传输机制 + 评审"，但都挂在同一条主线上——先建立"每个仓库都是一份完整历史"的分布式模型（06-7.1），再看这些完整仓库之间怎么按不同拓扑组织协作（06-7.2），然后下沉到"引用如何在两个仓库间对应"的 refspec（06-7.3）与"字节如何在网络上流动"的传输协议（06-7.4），最后回到人这一层：变更进主干前的评审门禁（06-7.5）。refspec 与传输协议虽是本篇最"重"的机制，但停在教辅深度（讲清语法与握手骨架、不逐字节展开协议），篇幅可控，故按 report-format v3「默认 1 大主题 = 1 报告」不产 `-a/-b`。

**在 Git 里，"远程"不是一台高你一等的服务器，而只是"另一份和你结构完全相同的仓库"。** 集中式版本控制（SVN、CVS）里，服务器是唯一的真相来源、你的工作副本只是它的一个残缺快照；而 Git 每次 clone 都把对方仓库的**全部历史**搬到本地，于是每个仓库地位对等——协作因此变成"两份完整仓库之间，按某条 refspec、走某种传输协议，交换彼此缺的对象和引用"。理解了这一层，06-7 后面所有内容（工作流拓扑、refspec、传输协议）都只是这句话的不同侧面。

需要一开始就立清的一条**分账边界**：本篇里 git 侧机制（分布式模型、refspec、传输协议）由 Pro Git 一手承重，稳定、可实证；而"Pull Request / Merge Request"是 **GitHub、GitLab 等平台**造出来的概念，**不是 git 内核的东西**——git 本身只有"把提交从一个仓库搬到另一个"和一个近亲命令 `git request-pull`，并没有"PR"这个对象。所以凡涉及 PR/MR 的评审流程、保护分支、required reviews 这些，一律标 **⚠平台特性 / ⚙演进快·锚版本·随时变**，仅指路、不作一手承重结论；评审背后的**原则**（为什么要评审、评审看什么）则锚 MIT 6.031（准一手）。

---

## 06-7.1 分布式模型：clone/fetch/push 的完整历史复制语义

### 06-7.1.1 分布式 vs 集中式：每个 clone 都是一份完整仓库

Git 是**分布式版本控制系统（DVCS）**：`git clone` 不是"拉一份当前文件的快照"，而是把远端仓库的**整个对象数据库与几乎全部历史**复制到本地。Pro Git 把这句话说得很直白——在 DVCS 里 "clients don't just check out the latest snapshot of the files; rather, they fully mirror the repository, including its full history"（客户端不是只签出最新快照，而是完整镜像整个仓库、含其全部历史）。对照集中式系统（CVS、Subversion、Perforce），那里只有中央服务器保存完整历史，客户端的工作副本只是某一个版本的文件、没有历史——你要看历史、要 diff、要提交，都得连服务器。

把集中式想成"图书馆只有一本原书，你每次只能借走当前这一页的复印件"；分布式则是"每个人手里都有整本书的完整副本"。由此推出 DVCS 的三个直接好处——**离线可用**（历史在本地，断网也能提交、看 log、切分支、diff）、**没有单点故障**（任何一份克隆都能作为新的"中央"恢复整个项目）、**操作快**（绝大多数命令只读本地对象库，不走网络）。代价是初次 clone 要传输全部历史、占更多磁盘。

日常说的"中央仓库"（GitHub 上那个 origin）在 git 眼里**并没有特殊地位**——它只是团队"约定"当作汇合点的一份普通克隆。是团队的社会约定、不是 git 的技术强制，让某个仓库成了"权威"。这正是下一小节各种协作拓扑能成立的根基。

### 06-7.1.2 三个基本动作：clone、fetch、push 各搬运什么

分布式协作的全部网络交互，几乎都归结为三个动作。

```
git clone <url>        # 首次：复制对方整个对象库 + 建立远程跟踪引用 + 签出工作区
git fetch <remote>     # 拉取：把远端"新增"的对象与引用取到本地，只更新 refs/remotes/*，不动你的分支/工作区
git push <remote> ...  # 推送：把本地"新增"的对象与引用上传到远端，前提是能 fast-forward（否则被拒）
```

三者搬运的都是 **Git 对象（commit/tree/blob/tag）加引用（refs）**，而不是文件差异的补丁。传输时只发对方**缺的**对象——这靠 fetch/push 前的一轮"协商"确定（详见 06-7.4）。这里最要紧的初学者认知是：**`fetch` 是"安全"的**——它只把远端状态下载到本地的远程跟踪引用（如 `refs/remotes/origin/main`），**绝不动**你正在工作的本地分支和工作目录；要把取来的更新真正并入你的分支，还得另外 `git merge` 或 `git rebase`（`git pull` = `fetch` + `merge`，把两步合成一步）。

`push` 则相反、是"有风险"的写操作：它默认**只允许 fast-forward**——即你要推的历史必须是远端当前历史的直接延续。如果别人已经先推了新提交、你本地落后，push 会被拒，报 "non-fast-forward"，逼你先 `fetch` 并整合（merge/rebase）再推。这不是刁难，而是防止你**用自己的历史覆盖掉别人已发布的提交**。本机可复现地看到这个约束——集中式工作流里两人竞争推送时，第二人必然先被顶回来（Pro Git ch5 的 Jessica 例子即此）。

### 06-7.1.3 远程跟踪引用：本地缓存的"远端上次长什么样"

`fetch` 把远端分支的位置记录在本地的**远程跟踪引用（remote-tracking refs）** `refs/remotes/<remote>/<branch>` 里（如 `origin/main`）。它是"远端某分支在你**上次 fetch 时**指向哪个 commit"的**本地只读快照**，你不能直接在上面提交——它只是给你一个对照物，让你知道"我领先/落后远端多少"。

本机实证，clone 之后远程跟踪引用被自动建好：

```
$ git clone "file://.../origin" work        # 用 file:// 本地协议 clone
$ git branch -a
* main
  remotes/origin/HEAD -> origin/main
  remotes/origin/dev
  remotes/origin/main
```

初学者最常混的两组名字，务必分清：`main` 是你**本地的、可提交的**分支；`origin/main` 是**远程跟踪引用**，是"我记得的远端 main"。它俩通常有一条**上游（upstream / tracking）关系**——`git status` 里 "Your branch is ahead of 'origin/main' by 2 commits" 那句，就是拿本地 `main` 和 `origin/main` 一比得出的。注意 `origin/main` **只在你 fetch 时才更新**：如果队友刚推了新提交、而你还没 fetch，你本地的 `origin/main` 仍停在旧位置——它反映的是"我上次看到的远端"，不是"此刻真实的远端"。

---

#### 来源与时效
- Pro Git 2nd ed（git-scm.com/book/en/v2，一手·承重），核实 2026-07-30：§1.1 About Version Control（集中式 vs 分布式、"fully mirror the repository, including its full history"）、§2.5 Working with Remotes（clone/fetch/push、`git pull` = fetch+merge）、ch5 Distributed Git（non-fast-forward 拒绝、Jessica 集中式竞争推送例）、§3.5 Remote Branches（远程跟踪引用 `refs/remotes/*` 语义）。
- 本机实证（git 2.43.0 @2026-07-30）：`file://` 协议 clone 后 `git branch -a` 显示自动建立的 `remotes/origin/*` 远程跟踪引用；实证脚本仅存 scratchpad、未入库。
- 交叉核对：Git 官方 `git-clone`/`git-fetch`/`git-push` man page（git 2.43.0）与 Pro Git 一致——fetch 只更新远程跟踪引用、push 默认要求 fast-forward。

## 06-7.2 贡献 / 维护工作流：协作拓扑

### 06-7.2.1 集中式工作流：一个共享中央仓库

最简单的协作拓扑是**集中式工作流（Centralized Workflow）**：团队约定一个中央仓库，人人对它有 push 权限，各自 clone、提交、往回 push。Pro Git 描述为 "one central hub … can accept code, and everyone synchronizes their work with it"。它的核心纪律来自 06-7.1.2 的 fast-forward 约束：谁想推、就必须先把别人已推的工作 fetch 下来并合并，"so as not to overwrite the first developer's changes"（以免覆盖别人的改动）。

这几乎就是"用 Git 模拟 SVN"。它适合小团队、彼此信任、都能直接写主仓库的场景，也最容易上手。缺点是没有一道"提交进主干前的关卡"——人人都能直接改中央仓库，代码质量全靠自觉；一旦团队变大、或要接受外部贡献者的代码，就需要下面两种更有层次的拓扑。

### 06-7.2.2 集成管理者工作流：fork + 拉取请求（GitHub/GitLab 的模型）

**集成管理者工作流（Integration-Manager Workflow）**里，项目有一个"官方"公开仓库，但贡献者**没有**对它的写权限。贡献者的做法是：clone（或在平台上 **fork**）出自己的公开副本、在自己副本上改、push 到自己副本，然后**请求维护者来拉**自己的改动；维护者把贡献者的副本加为一个 remote、在本地 merge、再 push 回官方仓库。Pro Git 点明这正是 "a very common workflow with hub-based tools like GitHub or GitLab, where it's easy to fork a project and push your changes into your fork"。

关键性质是权限的非对称——"Each developer has write access to their own public repository and read access to everyone else's"（各人只写自己的公开仓库、读所有人的）。给初学者的直觉：这一步就把我们熟悉的 GitHub **Pull Request** 落了地——你 fork、改、开 PR，本质就是"我在我自己的副本上做好了，请维护者把它拉过去"。它的最大好处是**解耦节奏**：贡献者随时能继续干活，维护者随时能挑合适的时机拉，"each party can work at their own pace"。注意 PR 这个**界面**是平台造的（⚠平台特性），但它底下的机制就是本工作流描述的这套 fork + fetch + merge，全是纯 git。

### 06-7.2.3 独裁者与副官工作流：分层集成（Linux 内核式）

**独裁者与副官工作流（Dictator and Lieutenants Workflow）**是集成管理者模式的**多级放大**，用于超大或高度分层的项目。结构是：普通开发者在 topic 分支上干活、基于 `master` 做 rebase；**副官（lieutenants）**负责各自的子系统，把开发者的分支并进自己的 `master`；**仁慈独裁者（benevolent dictator）**再把各副官的 `master` 并进自己的 `master`，然后 push 到那个所有人都据以 rebase 的**参考仓库**。

Pro Git 指出 "this kind of workflow isn't common, but can be useful in very big projects, or in highly hierarchical environments"，并给了最著名的例子——**Linux 内核**。给初学者的直觉：这就像公司的汇报层级，代码沿着"开发者 → 副官 → 独裁者"逐级汇聚、每一级都是一道过滤和整合的关卡，让顶层不必直接面对成千上万个贡献者。三种拓扑其实是同一根轴上按团队规模递增的三个点：集中式（人人直推）→ 集成管理者（一层拉取）→ 独裁者副官（多层拉取）。

### 06-7.2.4 git request-pull：内核这套流程的"原生 PR"

在没有 GitHub 界面的年代（也是 Linux 内核至今邮件列表协作的方式），发起一次"请拉我的改动"用的是 git 自带命令 `git request-pull`。它**不传输任何代码**，只生成一段**给人读的摘要文本**：告诉维护者"从哪个基点起、到我发布仓库的哪个 URL/分支、有哪些改动、diffstat 如何"，维护者据此自行 fetch 并合并。

```
git request-pull <start> <url> [<end>]
# 例：从 main 之后、我发布在 <url> 的 feature 分支上的改动
```

本机实证它产出的正是这样一段摘要（节选真实输出，git 2.43.0）：

```
The following changes since commit e0cd6ef... second (...):

are available in the Git repository at:

  file://.../origin feature

for you to fetch changes up to 9e061fd... add feature:
----------------------------------------------------------------
Claude (1):
      add feature
 f.txt | 1 +
 1 file changed, 1 insertion(+)
```

这条命令是"Pull Request"这个词的**词源本体**——GitHub 的 PR 按钮，本质就是把 `git request-pull` 生成的这封"请拉信"配上网页界面、评论和评审。理解了它，就明白 PR 底下没有魔法：一端说"我这有新东西、在这个地址"，另一端 fetch + merge。（本机运行时因 origin 未真收到该 feature 分支而先打了一条 `warn: Are you sure you pushed 'feature' there?` 提示——恰好说明它只是生成文本、并不替你推送。）

---

#### 来源与时效
- Pro Git 2nd ed（一手·承重），核实 2026-07-30：§5.1 Distributed Workflows（Centralized / Integration-Manager / Dictator-and-Lieutenants 三拓扑及原文引句、Linux 内核例）；§5.2 Contributing to a Project、§5.3 Maintaining a Project（fork + 拉取的落地）；ch6 GitHub（fork + PR 是集成管理者工作流在平台上的实现）。
- Git 官方 `git-request-pull` man page（git 2.43.0），核实 2026-07-30：命令语法 `git request-pull <start> <url> [<end>]` 与"生成给维护者的拉取摘要、不传输数据"语义；本机实证真实输出（脚本仅存 scratchpad、未入库）。
- ⚠平台特性（⚙演进快·锚版本·随时变，仅指路）：GitHub Pull Request、GitLab Merge Request 为集成管理者工作流的平台化界面，非 git 内核概念；术语与 UI 随平台演进。

## 06-7.3 refspec 映射：`+src:dst` 如何决定引用对应

### 06-7.3.1 refspec 的格式：`[+]<src>:<dst>`

**refspec** 是告诉 fetch/push"哪些引用对应到哪些引用"的映射规则。Pro Git §10.5 给的精确格式是：一个可选的 `+`，后跟 `<src>:<dst>`，其中 `<src>` 是**远端**一侧的引用模式、`<dst>` 是它们在**本地**被追踪到的位置。

```
[+]<src>:<dst>
```

其中前导 `+` 的含义，原文一句话——"The `+` tells Git to update the reference even if it isn't a fast-forward."（`+` 告诉 Git，即使不是 fast-forward 也更新该引用。）`<src>`/`<dst>` 都可用 `*` 做通配，成对出现时表示"一整批同名映射"。

refspec 就是一条"引用搬运的路线单"——"把远端叫这个名字的引用，搬到本地叫那个名字的位置去"。冒号左边是源、右边是目的地；加不加 `+` 决定"目的地已有值且新旧不能快进时，是硬覆盖还是拒绝"。平时你从没手写过 refspec，是因为 clone 时 git 已经替你在配置里写好了默认那条（下一节）。

### 06-7.3.2 clone 建立的默认 fetch refspec

`git clone`（或 `git remote add origin`）会自动在 `.git/config` 里写下默认的 fetch refspec：

```
[remote "origin"]
    fetch = +refs/heads/*:refs/remotes/origin/*
```

它的含义，Pro Git 原文：Git 抓取服务器上 `refs/heads/` 下的所有引用，写到本地的 `refs/remotes/origin/` 下——即"远端的每个分支 `refs/heads/X`，映射成本地远程跟踪引用 `refs/remotes/origin/X`"。带 `+` 是因为远程跟踪引用只是缓存、允许被远端的历史改写强制刷新（如别人 force-push 后，你 fetch 能同步到）。

本机实证 clone 后这条默认 refspec 确实被写入：

```
$ git config --get-all remote.origin.fetch
+refs/heads/*:refs/remotes/origin/*
```

这条通配 refspec 就是"`git fetch origin` 一声令下、把远端所有分支的最新位置同步到我本地 `origin/*` 缓存里"的那张总路线单。左边 `refs/heads/*` 是远端的真分支，右边 `refs/remotes/origin/*` 是本地的镜像；`*` 让"main、dev、feature……"逐一按名对应，不必一条条写。这也解释了 06-7.1.3 为什么 fetch 只动 `origin/*`——因为默认 refspec 的目的地就写死在 `refs/remotes/origin/*`。

### 06-7.3.3 push refspec：方向相反，且缺省 dst = 删除远端引用

push 的 refspec 方向相反——`<src>` 是**本地**引用、`<dst>` 是**远端**引用。它可以把本地某分支推到远端一个不同名的分支：

```
[remote "origin"]
    push = refs/heads/master:refs/heads/qa/master
```

Pro Git 说明：这会让 `git push origin` 默认把本地 `master` 推到远端的 `qa/master`。命令行上临时指定同理，本机实证把本地 `feature` 推成远端 `newfeat`（dry-run）：

```
$ git push --dry-run "file://.../origin" feature:refs/heads/newfeat
 * [new branch]      feature -> newfeat
```

**把 `<src>` 留空、只写 `:<dst>`，表示删除远端那个引用**。Pro Git 原文——"By leaving off the `<src>` part, this basically says to make the `topic` branch on the remote nothing, which deletes it."

```
git push origin :topic          # 删除远端 topic 分支（旧写法）
git push origin --delete topic  # 等价的新写法（git 1.7.0+）
```

`git push origin main` 这种"只写一个名字"的常见写法，其实是 refspec 的简写，git 把它补全成 `main:main`（本地 main 推到远端 main）。理解"冒号左边是源、右边是目的地"就能推出那条吓人的删除语法——"把'空'推到远端的 topic 上"，等于让远端 topic 变成什么都没有，也就是删除它。push 默认**不带** `+`、要求 fast-forward（保护别人的提交）；只有你确知要改写远端历史时，才用 `+`（或 `--force`）——这正是 06-6 "勿 rebase/force-push 公共分支"黄金规则要守的地方。

---

#### 来源与时效
- Pro Git 2nd ed §10.5 The Refspec（一手·承重），核实 2026-07-30：格式 `[+]<src>:<dst>`、`+` = "update the reference even if it isn't a fast-forward"、默认 `+refs/heads/*:refs/remotes/origin/*`、push refspec `refs/heads/master:refs/heads/qa/master`、缺省 src 删除远端引用（`git push origin :topic`）原文引句。
- Git 官方 `git-fetch`/`git-push` man page 的 `<refspec>` 小节（git 2.43.0），核实 2026-07-30：与 Pro Git 一致，并补充 `--delete` 为 `:dst` 的等价新写法（git 1.7.0+ 引入）。
- 本机实证（git 2.43.0 @2026-07-30）：`git config --get-all remote.origin.fetch` 显示默认 refspec；`git push --dry-run ... feature:refs/heads/newfeat` 显示异名映射。脚本仅存 scratchpad、未入库。

## 06-7.4 传输协议：local / smart-http / ssh 的握手与数据交换

### 06-7.4.1 dumb 协议 vs smart 协议

Git 在网络上搬对象有两大类协议。**dumb（哑）协议**只走只读 HTTP，服务器端"没有任何 Git 专用代码"——客户端靠一连串普通的 HTTP `GET`（先取 `info/refs`、`HEAD`，再逐个取对象或 packfile）把仓库当静态文件目录扒下来。Pro Git 明确它"rarely used today"（如今很少用），因为难以加密、难以做私有权限。**smart（智能）协议**则两端都跑智能进程，能协商、只传对方缺的对象，是现在 HTTP/SSH/git:// 实际都在用的协议。

哑协议像"把仓库当成一个能随便浏览的网盘、你自己一个个文件下载"，笨但服务器零配置；智能协议像"两端各站一个懂 Git 的人，先对个账（你有啥、我要啥），只把差集打包发过来"，快且省带宽。除非维护老式静态镜像，日常遇到的都是 smart 协议。

### 06-7.4.2 四个进程：upload-pack / fetch-pack 与 receive-pack / send-pack

smart 协议的核心是**服务器端与客户端各一个配对进程**，按方向分两组。**下载（fetch/clone）**：服务器端跑 `git-upload-pack`（准备并"上传"对象给你），客户端跑 `git-fetch-pack`（接收）。**上传（push）**：服务器端跑 `git-receive-pack`（接收并落地你推的对象），客户端跑 `git-send-pack`（发送）。

```
下载 fetch/clone:  客户端 fetch-pack   <——  服务器 upload-pack
上传 push:         客户端 send-pack    ——>   服务器 receive-pack
```

一个极易记反的点，务必抓住命名视角——**进程名都是站在"服务器"角度命名的**：你 fetch 时，服务器要**upload**（上传）给你，所以那头叫 `upload-pack`；你 push 时，服务器要**receive**（接收），所以那头叫 `receive-pack`。本机 fetch 的 packet 抓包里能直接看到服务器端 `upload-pack` 亮明身份和能力（下节）。三种网络协议（SSH/HTTP/git://）不过是"用什么通道把客户端进程连到这两个服务器进程"的不同接线方式，协商与打包的核心逻辑是共享的。

### 06-7.4.3 协商 → 打包：want / have / 只传差集

smart 协议交换数据分两步。**第一步握手/协商**：两端先交换各自支持的**能力（capabilities）**和引用列表；客户端据此发一串 `want <sha>`（我要这些引用指向的对象）和 `have <sha>`（我已经有这些），服务器算出差集；客户端发 `done`，服务器把**客户端缺的那些对象打成一个 packfile** 发回。整个过程用带 4 位十六进制长度前缀的 **pkt-line** 分块传输。

本机用 `GIT_TRACE_PACKET=1` 抓 `file://` 协议 fetch 的真实握手（git 2.43.0，节选），能看到**协议版本 2**下服务器端 `upload-pack` 先广播能力：

```
packet:  upload-pack> version 2
packet:  upload-pack> agent=git/2.43.0
packet:  upload-pack> ls-refs=unborn
packet:  upload-pack> fetch=shallow wait-for-done
packet:  upload-pack> object-format=sha1
```

这就是 06-7.1 说的"两份完整仓库只交换彼此缺的部分"在协议层的落地——不是把整个仓库重发，而是**先对账、只发差集打成的一个压缩包**。`want`/`have` 就是对账用的"我要"和"我有"；能协商出最小差集，正是 fetch/push 快的原因。（协议 v2 是 git 2.18+ 引入、现为默认的更高效握手方式；本机可见 `version 2`。逐字节的 pkt-line 编码属专家纵深，本篇点到为止。）

### 06-7.4.4 三种通道：local（file://）、SSH、smart-HTTP

同一套协商+打包逻辑，可跑在三种通道上，日常按 URL 形态区分。

```
本地(local)      /srv/git/proj.git   或   file:///srv/git/proj.git
SSH              git@github.com:user/repo.git   或   ssh://git@host/path.git
smart-HTTP(S)    https://github.com/user/repo.git
```

**local**：src/dst 都在本机文件系统，直接 fork 出 `upload-pack`/`receive-pack`，无网络；`file://` 形式会走一遍完整 smart 协议（利于本地测试，本篇实证即用它）。**SSH**：客户端通过 ssh 在远端启动进程，如 Pro Git 示例 `ssh -x git@server "git-upload-pack 'repo.git'"`；SSH 天然带加密与认证，是**推送**最常用的方式。**smart-HTTP**：把协商包在 HTTP 请求里——先 `GET .../info/refs?service=git-upload-pack` 拿引用与能力，再 `POST .../git-upload-pack` 发 want/have：

```
=> GET  $GIT_URL/info/refs?service=git-upload-pack      # 握手：取引用+能力
=> POST $GIT_URL/git-upload-pack                        # 协商+收 packfile
（push 对应换成 git-receive-pack）
```

**HTTPS** 走 443 端口、防火墙友好、可用 token 认证，最适合公开克隆和 CI；**SSH** 用密钥、无需每次输密码、加密强，最适合日常推送；**local/file://** 只用于本机或共享文件系统。三者**能取到的数据、用的协商逻辑完全一样**，区别只在"接线与认证方式"。这里也是本篇的**跨课边界**：TCP/TLS/SSH 握手、HTTP 报文这些底层细节属 **L4-02 计算机网络**，本篇只停在"git 用哪种通道承载它的协商与 packfile"，不下沉到网络协议本身。

---

#### 来源与时效
- Pro Git 2nd ed §10.6 Transfer Protocols（一手·承重），核实 2026-07-30：dumb vs smart 协议（"rarely used today"）、四进程 `upload-pack`/`fetch-pack` 与 `receive-pack`/`send-pack`、want/have/done 协商与 packfile、SSH 示例 `ssh -x git@server "git-upload-pack ..."`、smart-HTTP 的 `info/refs?service=git-upload-pack` + `POST git-upload-pack`、pkt-line 4 位十六进制长度前缀。
- 本机实证（git 2.43.0 @2026-07-30）：`GIT_TRACE_PACKET=1 git fetch file://...` 抓到协议 v2 下 `upload-pack` 的能力广播（version 2 / agent / ls-refs / fetch / object-format=sha1）；脚本仅存 scratchpad、未入库。
- 交叉核对：Git 官方文档 `gitprotocol-v2`/`gitprotocol-pack`（git 2.43.0）与 Pro Git 一致；协议 v2 为 git 2.18（2018）引入、后成默认。跨课边界：TCP/TLS/SSH/HTTP 底层归 L4-02，本篇不下沉。

## 06-7.5 代码评审门禁：变更进主干前的人工质量关

### 06-7.5.1 代码评审是什么、为什么做

**代码评审（code review）**是"由非原作者的人对源代码做仔细、系统的研读"（MIT 6.031 Reading 04 原文："Code review is careful, systematic study of source code by people who are not the original author of the code."）。它的两大目的：一是**改进代码本身**——找 bug、保证与规范一致；二是**改进程序员**——通过互相读代码传播知识、共同学习。MIT 6.031 引研究称评审可发现 **70–90%** 的软件缺陷，是各大公司的常规实践。

编译器和测试能查出"机器判得出的错"，但**"这段代码难懂""这里命名误导""这个抽象设计得别扭"这类问题，只有另一个人读了才发现**——评审补的正是自动化工具补不了的这块。它同时是团队的"知识扩散"机制：新人通过被评审快速学到团队规范，老人通过评审别人保持对全局的了解。评审要对**代码**不对**人**——目标是让代码更好，不是挑作者的错。

### 06-7.5.2 评审看什么：MIT 6.031 的代码质量清单

MIT 6.031 Reading 04 给出一份评审时逐条对照的代码质量清单，服务于三大目标（safe from bugs / easy to understand / ready for change）。原文条目：

```
1. Don't repeat yourself (DRY)          不要重复自己
2. Comments where needed                 该注释处注释（记录假设，不复述代码）
3. Fail fast                             尽早失败（错误尽早暴露、就近报错）
4. Avoid magic numbers                   避免魔法数字（用具名常量）
5. One purpose for each variable          每个变量只担一个用途
6. Use good names                        用好名字
7. Use whitespace to help the reader      用空白排版帮助阅读
8. Don't use global variables            不用全局变量
9. Functions should return results,       函数应返回结果，
   not print them                         而非打印结果
10. Avoid special-case code               避免特例代码
```

这张单子不是"风格洁癖"，每条都直接挂在一个质量目标上——DRY 让 bug 只需在一处修（safe from bugs）；具名常量、好名字、空白排版让人读得懂（easy to understand）；不用全局变量、函数返回而非打印，让代码解耦、易改（ready for change）。评审时就是拿这张单子逐条问"这段代码违反了哪条"。这些原则是**工具无关、平台无关**的一手教学内容，无论你用什么评审工具都成立。

### 06-7.5.3 平台化的评审门禁：PR/MR 与保护分支 ⚠

在 GitHub、GitLab 等平台上，评审被产品化为 **Pull Request（PR）/ Merge Request（MR）** 流程：贡献者开一个 PR，评审者在**逐行 diff** 上留评论、提出 change request 或 approve，通过后才 merge 进目标分支。配套的**门禁**由**保护分支（protected branch）/ 规则集（rulesets）**实现——可要求"合并前必须有 N 个 approving review""必须通过指定的 status checks（CI）""分支必须是最新的"等，未满足则禁止 merge。

这一整层务必标 **⚠平台特性 / ⚙演进快·锚版本·随时变，非 git 内核概念、不作一手承重**。给初学者的分账直觉：**评审的原则（06-7.5.2）稳定、可一手承重；评审的界面与门禁规则（PR/MR、required reviews、rulesets）是平台功能、随产品迭代**——例如 GitHub 近年在传统 "branch protection rules" 之外新增了功能更强、可叠加生效的 "rulesets"（据 GitHub Docs 2026-07-30 当前状态），两套机制并存。所以本节的正确读法是：**把"为什么评审、评审看什么"当知识记牢；把"某平台今天的按钮怎么点、规则怎么配"当会随时变的操作细节、用时查该平台当版文档**。底层不变的仍是 06-7.2 那套 git 机制——PR 合并到头来就是一次 fetch + merge（或 squash/rebase 变体）写进目标分支。

---

#### 来源与时效
- MIT 6.031 Software Construction sp22 Reading 04 Code Review（https://web.mit.edu/6.031/www/sp22/classes/04-code-review/ ，准一手·承重），核实 2026-07-30：code review 定义原文、目的（找 bug/一致性/传播知识）、"70–90% of defects"、10 条代码质量原则清单（DRY / Comments where needed / Fail fast / Avoid magic numbers / One purpose for each variable / Use good names / Use whitespace / Don't use global variables / Functions return not print / Avoid special-case code）。
- 交叉核对（评审原则）：Pro Git ch6 GitHub（PR 评审流程的平台落地）与 MIT 6.031 一致——评审是"合并前的人工关卡"。
- ⚠平台特性（⚙演进快·锚版本·随时变，仅指路、不承重）：GitHub Docs "About protected branches" / "About rulesets" / "Managing and standardizing pull requests"（docs.github.com，状态核实 2026-07-30，required reviews、rulesets 与 branch protection 并存）；GitLab Merge Request 与 approval rules 同类。术语与规则项随平台迭代，用时查当版文档。
