# L4-06·大主题06-6 三向合并与 merge-base（rebase vs merge）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（本组实证工具链另含 git 2.43.0，本报告全部实证在该版本上真跑）｜ 核实日期：2026-07-30 ｜ 先修：本课大主题06-4（Git 对象模型：commit 是带 tree + parent(s) 的不可变快照）、大主题06-5（引用/分支/HEAD：分支只是指向 commit 的可移动指针，reflog 记录指针移动史）｜ 一手锚点：Pro Git 2nd ed（https://git-scm.com/book/en/v2）§3.2 Basic Branching and Merging、§3.6 Rebasing、§7.8 Advanced Merging（强·一手承重）；交叉：git 官方 man git-merge-base / git-merge / git-rebase ｜ 成熟度：GA/稳定（合并与 rebase 的机制多年稳定；仅默认合并策略名从 `recursive` 演进为 `ort`，见 6.6.2.3，本机 2.43.0 已默认 `ort`）

> 一条主线心智模型：合并要回答的问题是「两条分叉的历史，如何合成一个既包含双方改动、又不误删对方工作的新快照」。答案的钥匙是**共同祖先（merge-base）**——它是双方「上次还一致」的参照点，有了它，Git 就能对每一处改动判断「这是谁改的、相对基点改了什么」，从而只把**真正的改动**合进来。merge 与 rebase 是围绕这把钥匙的两种史观：merge **保留**分叉的真实历史并用一个两父 commit 把它们缝合；rebase **抹掉**分叉、把你的提交逐个重放到对方之上、得到一条直线。二者最终快照可以完全相同，差别只在历史长什么样，以及 rebase 因为**改写了提交**而带来的协作红线。

> 分账（本报告只停在 git 合并机制层，交界处一句指路）：本主题讲**merge-base 求取 + 三向合并算法 + fast-forward/真合并/rebase 的机制 + 冲突标记的语法含义**。「冲突的**语义**解决」——即两边都改了同一行、到底该取哪个业务含义——属人工判断，不是 git 能自动做的，本报告讲到「标记出现、如何标记为已解决」为止（见 6.6.5.4 教学边界）。「分布式协作里 push/fetch 与远程分支如何触发合并」归本课大主题06-7；「团队该选 merge 还是 rebase 作分支策略」归大主题06-8，本报告只把 rebase vs merge 作为**机制与经验取舍**讲清、不作绝对定论。

> 本机实证：本大主题是本组硬实证脊柱之一，按 prompt「本机实证鼓励」，本报告在 git 2.43.0 上真跑了 merge-base 求取、非冲突三向合并、fast-forward、`--no-ff`、制造冲突看标记、rebase 观察 SHA 改写六组实验，正文各处贴**真实命令 + 真实输出**。实证脚本只在 scratchpad、未入库。

---

## 6.6.1 merge-base 与共同祖先

### 6.6.1.1 merge-base 是两条历史的最近公共祖先

两个分支各自往前走了几步后，它们的提交历史构成一张 DAG（有向无环图，见大主题06-4）。**merge-base** 就是这张图里、能同时被两个分支尖端沿 parent 指针回溯到的、**最靠近尖端的那个共同祖先 commit**。直觉上它就是「两条路上一次分岔前的那个点」——双方最后一次还完全一致的状态。

Pro Git §3.2 在讲三向合并时点明了它的角色：当要合并的分支不是当前分支的直接祖先（即历史已经分叉），Git 要用「两个分支尖端指向的两个快照，以及**这两者的共同祖先（common ancestor）**」来做合并。这个共同祖先就是 merge-base。

对初学者，把它想成两个人协作改同一份文档：merge-base 是「你俩上次同步、内容一模一样的那个版本」。只有知道这个共同起点，才能分别算出「你在起点基础上改了什么」和「他在起点基础上改了什么」，再把两份改动合起来。没有这个起点，你拿到两份不同的文档，根本分不清哪些行是新增、哪些是对方删掉的。

### 6.6.1.2 git merge-base 求基点（实证）

命令的基本形式是给两个 commit（通常是两个分支名），返回它们的 merge-base：

```
git merge-base <commit-A> <commit-B>
```

在本机 git 2.43.0 上，构造一段分叉历史（`main` 与 `feature` 都从 `C0` 出发，各自提交一次），求基点：

```
$ git log --all --oneline --graph
* 2845261 C-feature
| * d41570c C-main
|/
* 3aad993 C0
$ git merge-base main feature
3aad9933a1482c6e02cbadcc8b56b687f62850cb
$ git rev-parse main~1 feature~1
3aad9933a1482c6e02cbadcc8b56b687f62850cb
3aad9933a1482c6e02cbadcc8b56b687f62850cb
```

输出的 `3aad993…` 正是 `C0`（也等于 `main~1` 和 `feature~1`，即两边各回退一步都到达的那个提交）。这坐实了 merge-base 就是分岔前的共同祖先。

初学者要注意 merge-base 是**按图结构算出来的、不是记录下来的**：commit 对象里只存 parent 指针，Git 是顺着两个尖端各自的祖先集合去找交集里最近的那个。所以你随时可以对任意两个 commit 问 merge-base，不必是「当前正在合并」的时刻。

### 6.6.1.3 可能有多个 merge-base（criss-cross 情形）

merge-base 不一定唯一。当历史里出现「交叉合并」（两条分支互相合过对方一次，形成 criss-cross 结构）时，可能存在**多个**同样「最近」的共同祖先。`git merge-base` 默认只回一个「最优」基点，加 `-a`（`--all`）会把所有最优基点都列出来：

```
git merge-base -a <commit-A> <commit-B>
```

本机上对简单分叉历史求 `-a`，只返回一个（因为没有交叉），与不加 `-a` 一致：

```
$ git merge-base -a main feature
3aad9933a1482c6e02cbadcc8b56b687f62850cb
```

初学者不必被「多个 merge-base」吓到：它只在复杂交叉历史里出现，而现代默认合并策略（`ort`，见 6.6.2.3）能自己处理这种情况——它会把多个基点先递归合成一个「虚拟基点」再做三向合并。日常单次分叉合并，merge-base 就是唯一的那个分岔点。

### 6.6.1.4 为什么合并的第一步必然是求基点

有了 6.6.1.1 的直觉就能理解：合并的本质是「把双方各自相对同一起点的改动合起来」，而这个「同一起点」只能是 merge-base。如果 Git 不找基点、直接拿两个尖端快照对比，它会把「一方新增的行」和「另一方本来就有、对方没动的行」混为一谈，无法区分「新增/删除/未改」。基点提供的正是第三个参照，把「二选一的猜测」变成「三方可判定的合成」——这就是下一节三向合并的核心。

这里还要分清一个边界。如果一个分支的尖端**本身就是**另一个分支尖端的祖先（历史没分叉），那么 merge-base 就等于那个较旧的尖端，此时根本不需要三向合成，直接快进即可（见 6.6.3.1）。所以「要不要真合并」这个判断，也是先看 merge-base 落在哪里。

#### 来源与时效（本小主题末集中列，不逐项脚注）
- 一手：Pro Git 2nd ed §3.2 Basic Branching and Merging（"common ancestor of the two" 作三向合并基点的定义，核实 2026-07-30 在线版）。
- 一手交叉：git 官方 man git-merge-base（`-a/--all` 语义、多 merge-base 情形）；本机 git 2.43.0 实证（构造分叉历史 `git merge-base` / `git merge-base -a` / `git rev-parse`，真实输出如上）。
- 无冲突项。多 merge-base 与 `ort` 虚拟基点处理为一手 + 实证一致。

## 6.6.2 三向合并算法

### 6.6.2.1 三向 vs 两向：第三个参照解决歧义

**三向合并（three-way merge）**指合并时同时看**三个**版本：共同祖先 `base`（=merge-base）、当前分支的版本 `ours`、被合并进来的版本 `theirs`。之所以叫「三向」，就是因为比「两向 diff」多了 `base` 这个参照。

两向合并（只对比 ours 和 theirs）的致命问题是分不清「新增」和「删除」：假设 base 有 A、B 两行，ours 是 A、B（没动），theirs 是 A（删掉了 B）。只看 ours 和 theirs，Git 无法判断到底是「theirs 删了 B」还是「ours 加了 B」——这两种解释对应相反的合并结果。引入 base 后答案唯一：base 里有 B、theirs 里没有，说明是 theirs **删除**了 B，那结果就应该删掉 B。

初学者可以记一个口诀式的判据，对每一处都比较 ours 和 theirs 相对 base 的变化——**只有一方改了就采纳那一方的改动，双方都没改就保持原样，双方都改且改得不一样就是冲突**（交给 6.6.5）。这就是三向合并的全部直觉。

### 6.6.2.2 base/ours/theirs 三方 diff 合成结果

机制上，Git 先求出 base（6.6.1），然后分别计算 `base→ours` 和 `base→theirs` 两份 diff，再把这两份 diff 逐块（hunk）叠加到 base 上。

```
base（merge-base 快照）
  ├─ diff(base → ours)     当前分支相对基点的改动
  └─ diff(base → theirs)   被合入分支相对基点的改动
合成：两份 diff 落在不同位置 → 都采纳；落在同一位置且内容不同 → 冲突
```

关键在于「落点是否重叠」。两份 diff 若触碰的是文件里**不同的、互不相邻的区域**，Git 能自动把两边都合进来（见 6.6.2.5 实证）；只有当两边**改动了同一块**且结果不一致时，才产生冲突。这解释了一个初学者常有的误解——「只要两人都改了同一个文件就会冲突」是错的，改同一文件的不同部位通常能自动合并，冲突是「改同一处且冲突」才发生。

### 6.6.2.3 默认合并策略：ort（曾名 recursive）

Git 的三向合并由「合并策略（merge strategy）」实现。本机 git 2.43.0 的默认策略是 **`ort`**（"Ostensibly Recursive's Twin"），它在 2021 年前后取代了旧默认 `recursive`。本机实证里合并输出明确写着用的是 `ort`：

```
$ git merge --no-edit feature
Auto-merging f.txt
Merge made by the 'ort' strategy.
```

`ort` 与 `recursive` 对普通用户行为基本一致，都是「递归三向合并」——遇到多个 merge-base（6.6.1.3）时先把它们递归合成一个虚拟基点，再对该虚拟基点做三向合并。`ort` 是重写实现，主要优点是更快、更少临时文件、冲突处理更干净。

对初学者不必深究策略内部，只需记两点时效事实：其一，**默认策略名从 `recursive` 变成了 `ort`**，看到旧教程写 "Merge made by the 'recursive' strategy" 属正常历史差异；其二，日常合并你几乎不需要手动指定策略，Git 自动选 `ort`。其它策略（`ours`/`octopus`/`resolve`）是特殊场景用，广度上知道存在即可。

### 6.6.2.4 合并结果是快照，不是 diff 的累加

有一个初学者直觉必须纠正过来。Git 合并**不是**把 diff「打补丁」式地拼起来存下来，而是算出合并后的**完整文件内容**，据此生成一个新的 tree 快照，再包成一个 commit（见大主题06-4：commit 指向一个完整 tree 快照）。diff 只是 Git 用来**推导**该采纳哪些改动的中间手段，最终存的是结果快照本身。

Pro Git §3.6 用一句话点破了这一层（在对比 merge 与 rebase 时）：无论走 merge 还是 rebase，「你最终得到的那个提交所指向的**快照是同一个**——不同的只是历史」。这句话之所以成立，正是因为合并存的是结果快照而非过程；两条不同路径只要采纳了相同的双方改动，就会算出**字节一致**的最终快照。

理解这点能消除很多困惑，比如「合并后为什么 `git show` 合并 commit 看起来怪怪的」——因为它展示的是相对两个父的差异，而底层存的是一个完整快照。

### 6.6.2.5 实证：非冲突三向合并自动合成双方改动

构造 base 为三行 `a/b/c`，`feature` 改**第三行**、`main` 改**第一行**（两处不重叠），合并应自动成功并同时保留两处改动：

```
$ git merge --no-edit feature
Auto-merging f.txt
Merge made by the 'ort' strategy.
 f.txt | 2 +-
 1 file changed, 1 insertion(+), 1 deletion(-)
$ cat f.txt
a-main
b
c-feat
$ git cat-file -p HEAD | grep parent
parent 2cc6622c978c08c252060f8ad2971c21f8466995
parent 75b187b2063de748a6ad8cac7239c227754a2cca
```

结果 `a-main / b / c-feat` 同时含 main 改的第一行和 feature 改的第三行，无需人工干预——这正是 6.6.2.2「两份 diff 落点不重叠则都采纳」的直接证据。同时合并 commit 有**两个 parent**（对应 6.6.3.2 的两父结构）。

#### 来源与时效
- 一手：Pro Git 2nd ed §3.2（三向合并用两尖端 + 共同祖先）、§3.6（"the same snapshot — it's only the history that is different"，核实 2026-07-30 在线版）、§7.8 Advanced Merging（策略与冲突处理背景）。
- 一手交叉：git 官方 man git-merge（默认策略、`-s` 策略列表）；本机 git 2.43.0 实证（非冲突三向合并输出、`git cat-file -p HEAD` 两父，真实输出如上）。
- 时效标注：默认策略 `recursive`→`ort` 的更名是真实演进，本机 2.43.0 默认 `ort`（实证坐实）。策略内部「虚拟基点」细节讲到直觉为止，不做专家纵深。

## 6.6.3 快进 vs 真合并

### 6.6.3.1 fast-forward：历史没分叉时只移指针

**快进（fast-forward, FF）**发生在一个特殊前提下：当前分支的尖端是被合并分支尖端的**祖先**（即当前分支自分叉后**没有任何新提交**，历史其实没分叉）。此时 Git 无需做三向合成，直接把当前分支指针**向前移动**到对方尖端即可。

Pro Git §3.2 的原话是：当你要合并的提交「可以顺着当前提交的历史到达」时，Git「因为没有分叉的工作要合并，就直接把指针前移——这称为 fast-forward」。本机实证，`main` 停在 C0、`topic` 在其后又提交了 C1/C2，合并 `topic`：

```
$ git merge topic
Updating 1197153..1bd9ef4
Fast-forward
 f.txt | 2 ++
 1 file changed, 2 insertions(+)
$ git rev-parse main topic
1bd9ef4175bbfea9389b1da870e9ed746187b126
1bd9ef4175bbfea9389b1da870e9ed746187b126
$ git log --oneline --graph
* 1bd9ef4 C2
* 207135e C1
* 1197153 C0
```

输出关键词是 `Fast-forward`，且合并后 `main` 与 `topic` 指向**同一个 SHA**、历史是一条直线、**没有产生新的合并 commit**。

初学者要抓住的一点是，FF 不创建任何新提交，只是让分支指针「追上」对方。因为没有分叉，也就没有需要三向合成的东西，自然也不会冲突。

### 6.6.3.2 真合并：生成有两个父的 merge commit

当历史**确实分叉**（双方自 merge-base 后各有新提交）时，无法只移指针，Git 走三向合并（6.6.2）并创建一个新的**合并提交（merge commit）**。它的特殊之处是——用 Pro Git §3.2 的话说——「有不止一个父提交（more than one parent）」，通常是两个：一个指向 ours 原尖端，一个指向 theirs 尖端。

6.6.2.5 的实证里已经看到合并 commit 带两个 `parent` 行。这两个 parent 指针正是「保留分叉真实历史」的载体：顺着它们回溯，你能同时看到两条分支各自的提交序列，DAG 在这里从「两股」重新汇成「一股」。

初学者可以对照着记，**FF = 移指针、无新 commit、历史仍是直线；真合并 = 建一个两父 commit、历史图上出现「Y 字形合流」**。判断走哪条，全看 merge-base 是否等于当前分支尖端（等于就能 FF，见 6.6.1.4）。

### 6.6.3.3 --no-ff：即使能快进也强制建合并 commit

有时即便满足 FF 条件，你也想**保留「这里发生过一次合并」的痕迹**（比如让 feature 分支的合入在历史上清晰可见）。`--no-ff` 就是强制 Git 不快进、总是创建合并 commit：

```
git merge --no-ff <branch>
```

本机实证，同样是「main 是 topic 祖先、本可 FF」的场景，加 `--no-ff`：

```
$ git merge --no-ff --no-edit topic
Merge made by the 'ort' strategy.
 f.txt | 1 +
 1 file changed, 1 insertion(+)
$ git log --oneline --graph
*   7d1c0e1 Merge branch 'topic'
|\
| * 207135e C1
|/
* 1197153 C0
```

对比 6.6.3.1，这次没有 `Fast-forward` 字样、而是生成了 `Merge branch 'topic'` 这个合并 commit，历史图出现了分叉再合流的 `|\ … |/` 结构。这就是团队常说的「保留 feature 分支的合并气泡」的做法。

### 6.6.3.4 --ff-only：只接受能快进的合并

与 `--no-ff` 相反，`--ff-only` 要求「只有能快进时才合并，否则直接失败」：

```
git merge --ff-only <branch>
```

它的用途是**当护栏**——在你希望保持线性历史、不想意外产生合并 commit 的工作流里，若 `--ff-only` 失败，就提醒你「历史已分叉，需要先 rebase 或明确 merge」，而不是悄悄生成一个合并 commit。三者关系可以一句话收束：默认「能 FF 就 FF、否则真合并」；`--no-ff` 「一律真合并」；`--ff-only` 「只许 FF、否则报错」。

#### 来源与时效
- 一手：Pro Git 2nd ed §3.2（fast-forward 定义"moving the pointer forward"、merge commit "more than one parent"，核实 2026-07-30 在线版）。
- 一手交叉：git 官方 man git-merge（`--ff` / `--no-ff` / `--ff-only` 三态语义）；本机 git 2.43.0 实证（`Fast-forward` 输出、`--no-ff` 生成合并 commit 及历史图，真实输出如上）。
- 无冲突项。`--ff-only` 未在本机单独跑，语义据一手 man，标为文档核实。

## 6.6.4 rebase 线性改写与黄金规则

### 6.6.4.1 rebase 做什么：把提交重放到新基点上

**变基（rebase）**是与 merge 平行的另一种整合方式。merge 把两条分叉「合流」成一个两父 commit；rebase 则把你分支上的提交**逐个「重放（replay）」到目标分支的尖端之上**，得到一条**线性**历史，仿佛你的工作是从对方最新状态出发才开始的。

Pro Git §3.6 把 rebase 的内部步骤讲得很清楚：找到两分支的共同祖先 → 取出你当前分支每个提交引入的 diff 并存成临时文件 → 把当前分支重置到目标分支尖端 → 依次把这些 diff 逐个应用上去。换句话说，rebase 是「拿你相对基点的每一处改动，重新在新基点上做一遍」。

对初学者可以打个类比，merge 像把两条支流汇成一条并在汇口立个「合流碑」（合并 commit）；rebase 像把你那条支流整段**搬起来、接到主流的下游末端**，让整体看起来是一条直流，看不出曾经分过叉。

### 6.6.4.2 rebase 改写提交、SHA 全变（实证）

rebase 的重放意味着它**创建的是新的提交对象**（内容也许相同，但 parent 变了，因此 commit 的 SHA 变了），原来的提交被「抛弃」（仅靠 reflog 还能找回）。本机实证：`feature` 有 F1/F2 两个提交、`main` 上另有 M1，把 feature rebase 到 main：

```
$ git log --all --oneline --graph        # rebase 前
* f627ac9 F2
* 0aaba88 F1
| * b2eb378 M1
|/
* 6784214 C0
$ git rebase main
Successfully rebased and updated refs/heads/feature.
$ git log --oneline --graph              # rebase 后：直线，F1/F2 换了新 SHA
* 64d70e7 F2
* 4f0ac0d F1
* b2eb378 M1
* 6784214 C0
```

对比可见 F1 从 `0aaba88` 变成 `4f0ac0d`、F2 从 `f627ac9` 变成 `64d70e7`——**同样的改动、全新的提交对象**，且历史被拉成一条直线（feature 现在直接接在 M1 之上，分叉消失）。原尖端并没有真正丢失，reflog 仍可找回：

```
$ git reflog feature | head -3
64d70e7 feature@{0}: rebase (finish): refs/heads/feature onto b2eb378...
f627ac9 feature@{1}: commit: F2
0aaba88 feature@{2}: commit: F1
```

这条实证同时呼应大主题06-5：`f627ac9`（旧 F2）仍在 reflog 里，是 rebase 出错时的「后悔药」。

### 6.6.4.3 merge vs rebase：最终快照可相同，历史不同

一个反直觉但关键的事实（6.6.2.4 已提过）：merge 与 rebase 若整合的是同一对改动，**最终快照可以字节一致**，差别只在历史形态。Pro Git §3.6 原话：「你最终得到的那个提交所指向的快照是同一个——不同的只是历史。」

区别落在「历史长什么样」：

```
merge 后：保留分叉的真实记录，有一个两父合并 commit（Y 字形）
rebase 后：线性、干净，看不出曾经并行开发（一条直线）
```

因此选 merge 还是 rebase，本质是**你想要一份「忠实记录并行发生过什么」的历史，还是一份「便于线性阅读、像顺序开发」的历史**。Pro Git §3.6 明确把它定性为团队偏好、无绝对对错：「每个团队、每个项目都不同……由你来决定哪种最适合你的具体情形。」这是**经验主张、非规范**，随团队与项目而变，本报告不作绝对定论。

初学者别把「rebase 更干净」当成「rebase 更好」：干净是以**丢弃真实历史、改写提交**为代价换来的，而改写提交正是下一节红线的来源。

### 6.6.4.4 黄金规则及其边界（易误解，据一手核实）

rebase 的核心红线（Pro Git §3.6 称为「The Golden Rule of Rebasing / 变基的黄金规则」）原文是：

```
Do not rebase commits that exist outside your repository and that people may have based work on.
（不要变基那些已经存在于你仓库之外、且他人可能已经在其基础上开展工作的提交。）
```

为什么这是红线？因为 rebase **改写提交、换掉 SHA**（6.6.4.2）。如果这些提交已经推送出去、别人已经拉取并在其上工作，你一改写，同一段历史就出现了「新旧两套 SHA」；别人再想合并/推送时，Git 会把你改写后的提交当成一批「新提交」，导致重复提交、混乱的合并，Pro Git 原文调侃「people will hate you」。

这条规则最容易被误解的正是**边界**——「什么算『公共/已推送』」。据 Pro Git §3.6 的措辞，判据不是字面的「有没有 push」，而是**「是否存在于你仓库之外、且他人可能已基于它工作」**。落到实践：你**只在本地、从未推送**的提交，随便 rebase（清理提交、压缩、改顺序）都安全；一旦提交**已推送到共享远程、且可能被他人拉取并作为基础**，就不该 rebase。换言之，判定核心是「这些提交是否已成为他人工作的基础」，而非机械地看 push 动作本身。这是经验判据，团队约定（如「个人 feature 分支即使推了也可 force-push 重写，但共享分支绝不」）会在此之上再加细则。

### 6.6.4.5 折中实践：本地 rebase 清理、已推送的绝不动

Pro Git §3.6 给出的推荐折中，可作初学者的默认心法：

```
rebase local changes before pushing to clean up your work,
but never rebase anything that you've pushed somewhere.
（推送前用 rebase 清理你的本地工作，但绝不 rebase 任何你已经推送出去的东西。）
```

这句话把 6.6.4.4 的红线变成一条可操作的日常规则：在提交进入公共视野**之前**，rebase 是整理历史的好工具（合并零碎提交、让 feature 顺滑地接在最新主干上、避免无谓的合并 commit）；一旦推送、可能被他人依赖，就切换到 merge、不再改写。这也解释了为什么很多团队用「本地 rebase + 合并时 merge」的组合，而不是二选一。

#### 来源与时效
- 一手：Pro Git 2nd ed §3.6 Rebasing（rebase 重放步骤、"the same snapshot — it's only the history that is different"、黄金规则原文 "Do not rebase commits that exist outside your repository and that people may have based work on"、折中建议原文，核实 2026-07-30 在线版逐字引用）。
- 一手交叉：git 官方 man git-rebase（重放/改写语义）；本机 git 2.43.0 实证（rebase 前后 SHA 改写、线性化、reflog 找回旧尖端，真实输出如上）。
- 冲突/取舍记账：merge vs rebase 「哪个更好」是**经验主张、非规范**，Pro Git 明言随团队/项目而定，本报告不作绝对定论；黄金规则的「公共/已推送」边界据 Pro Git 措辞核实为「已存在于仓库之外且他人可能已基于其工作」，非字面「是否 push」。

## 6.6.5 冲突标记与解决

### 6.6.5.1 冲突何时发生：双方改了同一处且不一致

合并冲突（merge conflict）发生在 6.6.2.2 说的那种情形：`ours` 和 `theirs` **相对 base 改动了同一块内容、且改得不一样**，Git 无法替你判断该取哪个，于是**停下**、把该文件标为冲突、要求人工裁决。只涉及不同区域的改动会自动合并（6.6.2.5），不会冲突。

本机实证，`main` 把第二行改成 `line2-main`、`feature` 把同一行改成 `line2-feature`，合并必冲突：

```
$ git merge feature
Auto-merging f.txt
CONFLICT (content): Merge conflict in f.txt
Automatic merge failed; fix conflicts and then commit the result.
$ echo exit=$?
exit=1
```

注意合并**失败会返回非零退出码**（这里 `exit=1`），这也是 CI/脚本里判断「合并是否干净」的依据。

### 6.6.5.2 冲突标记区块的含义

Git 会把冲突处就地写成三段式标记，直接嵌进文件内容里。本机实证冲突后的文件：

```
line1
<<<<<<< HEAD
line2-main
=======
line2-feature
>>>>>>> feature
line3
```

三个标记的含义可以逐一对应到下面几行。

```
<<<<<<< HEAD        冲突区开始；到 ======= 之间是「当前分支（ours / HEAD）」的版本
=======            分隔线；上方是 ours、下方是 theirs
>>>>>>> feature    冲突区结束；======= 到这里之间是「被合并分支（theirs）」的版本，标签为其来源
```

Pro Git §3.2 的描述与此一致：`HEAD`（你当前分支）的版本是「`=======` 之上的部分」，被合入分支的版本是「下面的部分」。未冲突的行（这里的 `line1`、`line3`）不带标记、原样保留——标记只包住真正打架的那一块。

初学者最该记住的一点，是这些 `<<<<<<<` / `=======` / `>>>>>>>` **只是 Git 临时写进文件的普通文本**，不是什么特殊格式；它们必须在解决后被**全部删掉**，否则残留的标记会当成代码/内容被提交进去（这是新手最常见的事故）。

### 6.6.5.3 解决流程：编辑 → add 标记已解决 → commit

标准解决流程是三步，命令层面：

```
# 1. 手工编辑冲突文件，选定最终内容，删掉全部 <<<<<<< ======= >>>>>>> 标记
# 2. 把解决后的文件标记为「已解决」
git add <file>
# 3. 完成合并（生成合并 commit）
git commit
```

`git status` 在冲突期间会把冲突文件标为 `UU`（both modified），提示你还有未解决项：

```
$ git status --short
UU f.txt
```

`git add` 在这里的语义不是「加新文件」，而是**告诉 Git「这个文件的冲突我已经处理好了」**——它把文件从「冲突未决」状态移出。全部冲突文件都 `add` 后，`git commit` 才能收尾。若中途想放弃整次合并、回到合并前状态，用：

```
git merge --abort
```

（本机实证中用它把仓库恢复到冲突前，验证了它能干净回退。）

### 6.6.5.4 教学边界：语义解决属人工判断

这里要划清本报告的边界。Git 能做的是**机械地检测冲突、标记出双方版本**；但「两个版本到底该取哪个、或如何融合成一个业务上正确的结果」——这是**语义层面的判断，属人工**，Git 无法也不应替你决定。

举例，若 ours 把税率改成 `0.08`、theirs 改成 `0.10`，Git 只会把两个数都摆在标记里给你看；到底哪个对，取决于业务需求，只有人（结合需求/沟通）能定。所以本大主题讲到「冲突如何被检测、如何被标记、如何标记为已解决并收尾」为止，**不讲**具体怎么做语义裁决——那不是 git 机制问题。这与 prompt 划定的教学边界一致。

### 6.6.5.5 conflictStyle：diff3/zdiff3 把 base 也显示出来

默认冲突标记只显示 ours 和 theirs 两方。把 `merge.conflictStyle` 设为 `diff3` 或更新的 `zdiff3`，标记里会**多显示一段共同祖先（base）的原始内容**，用一条额外的分隔标出：

```
<<<<<<< HEAD
（ours 版本）
||||||| merge-base
（base 原始版本）
=======
（theirs 版本）
>>>>>>> feature
```

多出来的 `||||||| merge-base` 段落极有助于解决冲突——你能看到「双方各自把这段从什么改成了什么」，从而更容易融合，而不是只在两个结果里二选一。`zdiff3`（zealous diff3，git 2.35+ 引入）是 `diff3` 的改进，会把双方共同的行移出冲突区、让冲突块更小。本机默认是两方式（未显示 base），此项为文档核实、标为默认可切换项。

初学者不必一上来就改这个配置，但值得知道它存在：当一个冲突「怎么看都不对劲、不知道对方到底改了什么」时，切到 `diff3`/`zdiff3` 看一眼 base，往往豁然开朗。

#### 来源与时效
- 一手：Pro Git 2nd ed §3.2（冲突标记 `<<<<<<< HEAD` / `=======` / `>>>>>>>` 格式与「HEAD 在 ======= 之上」描述）、§7.8 Advanced Merging（`merge.conflictStyle` = `diff3`、`git merge --abort`、`git add` 标记已解决，核实 2026-07-30 在线版）。
- 一手交叉：git 官方 man git-merge（冲突呈现、`--abort`）、git-config（`merge.conflictStyle` 取值 `merge`/`diff3`/`zdiff3`）；本机 git 2.43.0 实证（`CONFLICT` 输出、退出码非零、真实冲突标记、`UU` 状态、`--abort` 回退，真实输出如上）。
- 时效标注：`zdiff3` 为 git 2.35+ 引入，本机 2.43.0 支持；默认 conflictStyle 为两方式 `merge`，diff3/zdiff3 需显式配置（本机未切换实测该样式输出，标为文档核实）。语义冲突解决属人工，为教学边界、不展开。
