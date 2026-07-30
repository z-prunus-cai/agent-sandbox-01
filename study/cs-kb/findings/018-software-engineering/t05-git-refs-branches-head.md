# L4-06·大主题06-5 Git 引用/分支/HEAD 指针机制

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（本组实证工具链另含 git 2.43.0）｜ 核实日期：2026-07-30 ｜ 先修：本课大主题06-4（Git 对象模型——blob/tree/commit/tag 四类对象与内容寻址 DAG，尤其 commit 是不可变、由 SHA 命名的节点）｜ 一手锚点：Pro Git 2nd ed（https://git-scm.com/book/en/v2）§3.1 Git Branching - Branches in a Nutshell、§10.3 Git Internals - Git References（强·一手承重）；交叉：git 官方 man git-check-ref-format(1)、gitrevisions(7)、git-reflog(1)、git-symbolic-ref(1)（一手交叉核对）｜ 成熟度：GA/稳定（引用机制是 git 数十年不变的地基；唯一演进项是 SHA-1→SHA-256 使得 hash 长度可变，本报告在 06-5.1 点明）

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（06-5.1~06-5.5）共享单一主线——「引用（ref）就是一个存着 SHA 的名字」：分支（06-5.1）是可移动的 ref、HEAD（06-5.2）是指向 ref 的 ref、tag（06-5.3）是不移动的 ref、reflog（06-5.4）是记录 ref 移动历史的日志、远程跟踪引用（06-5.5）是缓存远端 ref 的本地 ref。层层围绕同一机制、篇幅适中，按 report-format v3 §一默认 1 大主题 = 1 报告，不拆 `-a/-b`。

> 一条主线心智模型：大主题06-4 讲的是 git 的**对象库**（一堆用 SHA 命名、不可变、内容寻址的 blob/tree/commit/tag），那是「事实」；本大主题讲的是**引用层**（refs），那是「给事实起的、可以移动的人类可读名字」。commit 的 40 位 SHA 没人记得住，也无法表达「最新」这种会变的概念——引用就是解决这两件事的：一个引用 = 一个名字 + 它当前指向的对象 SHA。分支、HEAD、tag、远程跟踪引用全都是「引用」这一个机制的不同用法，reflog 则是这些引用每次改指向时留下的黑匣子。理解了「引用只是存了 SHA 的小文件」，git 的分支为何"极其廉价"、detached HEAD 为何危险、reflog 为何能当后悔药，就都通了。

> 本机实证：本报告所有关键论断（分支文件 41 字节、HEAD 内容形态、detached 态、tag 两种形态、reflog 可恢复、远程跟踪引用、散文件 vs packed-refs）均已在本机 git 2.43.0 临时 repo 实跑取证，命令与真实输出贴入正文并集中列于各章末来源。实证脚本只在仓库外 scratchpad，未入库。

---

## 6.5.1 refs/heads：分支就是指向 commit 的轻量指针

### 6.5.1.1 引用（ref）是什么：一个名字映射到一个 SHA

**引用（reference，简称 ref）**是 git 里对 40 位十六进制 SHA-1 对象名的一个人类可读别名。物理上，绝大多数引用就是 `.git/refs/` 目录下的一个**普通文本文件**，文件名是引用名、文件内容是它当前指向的那个对象的 SHA 外加一个换行符。git 把引用按用途分目录存放：

```
.git/refs/heads/    → 本地分支（branch）
.git/refs/tags/     → 标签（tag）
.git/refs/remotes/  → 远程跟踪引用（remote-tracking ref）
```

Pro Git §10.3 把引用称为"给 SHA-1 值起的、好记的名字（a file that contains a SHA-1 value）"，并明确 git 用这些引用文件来实现"分支只是一个指针"这件事。对初学者，最有用的一句话是：**引用不是对象，它是指向对象的名字**——对象（commit 等）存在对象库里、不可变、用 SHA 命名；引用存在 refs 里、可以随时改指向别的 SHA。两层分开，是 git 全部灵活性的根源。

### 6.5.1.2 分支 = refs/heads/ 下一个存着 commit SHA 的文件

一条**本地分支（branch）**在物理上就是 `.git/refs/heads/<分支名>` 这一个文件，内容是该分支当前**顶端 commit** 的 SHA。所谓"在分支上提交"，本质就是：新建一个 commit 对象，然后把这个分支文件里的 SHA **改写**成新 commit 的 SHA。分支因此是"可移动的指针"，而 commit 图本身不动。

本机实证（git 2.43.0，临时 repo，一次提交后）：

```
$ cat .git/HEAD
ref: refs/heads/master
$ cat .git/refs/heads/master
61073ccf647590e79de6d48c2403692ed51bff20
$ git rev-parse HEAD
61073ccf647590e79de6d48c2403692ed51bff20
```

分支文件里那一行，和 `git rev-parse HEAD` 解析出的当前 commit SHA 完全一致——这就坐实了"分支文件 = 一个 SHA"。对初学者，这解释了为什么 git 建分支"零成本、瞬间完成"：`git branch feat` 只是写了一个 41 字节的小文件，既不拷贝代码、也不拷贝历史，只是又起了一个指向同一个 commit 的名字。这与集中式版本控制里"建分支 = 复制整棵目录"的昂贵操作是根本区别。

### 6.5.1.3 "41 字节"的由来：40 hex + 1 换行（且随 hash 算法可变）

分支文件的大小在 SHA-1 仓库里是 **41 字节**：40 个十六进制字符表示 SHA-1 摘要，再加末尾 1 个换行符（`\n`）。

```
$ wc -c .git/refs/heads/master
41 .git/refs/heads/master
```

本机实证确认为 41 字节，与 round3b 清单里的"约 41 字节"一致。给初学者补两个易错点。其一，这 41 字节是**松散引用（loose ref，即散文件形态）**下的数值；一旦引用被打包进 `packed-refs`（见 06-5.1.4），单条引用就不再是独立文件，"41 字节"这个说法只适用于散文件形态。其二，"41 = 40 + 1"里的 40 是 **SHA-1** 的十六进制长度；git 正在从 SHA-1 迁移到 **SHA-256**（256 位 = 64 个十六进制字符），在 SHA-256 仓库里同一个引用文件会是 65 字节（64 + 1）。所以"41 字节"是**特定于 SHA-1 的经验数值、不是恒定常数**，应理解为"hex 摘要长度 + 一个换行"。本机 git 2.43.0 默认仍是 SHA-1 仓库（`git init` 不加 `--object-format=sha256` 时），故本机实测 41 字节。

### 6.5.1.4 散文件 vs packed-refs：同一个引用的两种存在形态

引用有**两种物理存在形态**，这是初学者极易踩的坑。默认情况下每个引用是 `.git/refs/` 下一个独立的**松散引用文件**（散文件）；但当引用很多、或执行了 `git gc`/`git pack-refs` 后，git 会把这些散文件**打包**进仓库根部的单个 `.git/packed-refs` 文本文件，并**删除**对应的散文件以提速与省 inode。

本机实证（同一 repo，`git pack-refs --all` 前后对比）：

```
$ ls .git/refs/heads/ .git/refs/tags/       # 打包前：散文件
master
v1-anno  v1-light
$ git pack-refs --all
$ ls .git/refs/heads/                        # 打包后：散文件没了
(空)
$ cat .git/packed-refs
# pack-refs with: peeled fully-peeled sorted
d0ebb560fcc1ab1958c6034f7bc57f40a2922580 refs/heads/master
ed2e1dfb54aa1ba3b582d48010bee50000add125 refs/tags/v1-anno
^d0ebb560fcc1ab1958c6034f7bc57f40a2922580
d0ebb560fcc1ab1958c6034f7bc57f40a2922580 refs/tags/v1-light
```

打包后 `.git/refs/heads/` 目录空了，但 `git branch`、`git rev-parse master` 照常工作——因为 git 查一个引用时会**先看散文件、找不到再查 packed-refs**。这就是为什么"`cat .git/refs/heads/master` 有时报 No such file"却分支明明存在：它被打包进 packed-refs 了，不是丢了。（packed-refs 里以 `^` 开头的那一行是附注 tag 的"peeled"目标，见 06-5.3。）Pro Git §10.3 专门用一节讲 packed-refs，正是因为它是理解"引用去哪了"的关键。

### 6.5.1.5 引用命名规则：git-check-ref-format 约束

引用名不是任意字符串，git 用 **git-check-ref-format** 规则约束合法引用名，避免和路径、修订语法冲突。几条对初学者最实用的禁令：不能含空格或 `~^:?*[`、不能含连续两个点 `..`、不能以 `/` 结尾或含连续 `//`、任一路径段不能以 `.` 开头或以 `.lock` 结尾、不能是单独的 `@`。

本机实证（git 2.43.0）：

```
$ git check-ref-format refs/heads/feature/x   ; echo $?   # 合法
0
$ git check-ref-format refs/heads/foo..bar    ; echo $?   # 连续两点，非法
1
$ git check-ref-format refs/heads/foo.lock    ; echo $?   # .lock 结尾，非法
1
$ git check-ref-format refs/heads/foo/        ; echo $?   # 斜杠结尾，非法
1
```

对初学者这解释了两件常见现象：其一，分支名里可以有 `/`（如 `feature/login`），因为 git 允许多级路径段——它在 `.git/refs/heads/` 下就是子目录，不是"子分支"这种特殊概念，只是名字带斜杠。其二，`.lock` 后缀被禁，是因为 git 更新引用时会先创建 `<ref>.lock` 文件做原子锁，若允许引用叫这名字就会冲突。命名规则本质是为"引用是文件、更新要原子、名字要能被修订语法解析"这三件事服务的。

#### 来源与时效
- Pro Git 2nd ed §10.3「Git Internals - Git References」（强·一手承重，核实 2026-07-30，git-scm.com/book/en/v2）：引用是 `.git/refs/` 下存 SHA-1 的文件、分支即 refs/heads 下的指针文件、HEAD 与 packed-refs 机制。
- Pro Git 2nd ed §3.1「Branches in a Nutshell」（强·一手承重）：分支是"轻量可移动指针（lightweight movable pointer to a commit）"、建分支只写一个小文件故极廉价。
- git 官方 man git-check-ref-format(1)（一手交叉，核实 2026-07-30）：合法引用名规则（禁 `..`、`.lock` 结尾、斜杠结尾等）。
- 本机 git 2.43.0 实测（scratchpad 临时 repo）：分支文件内容 = 40 hex + 换行、`wc -c` = 41 字节、`pack-refs --all` 后散文件消失而 packed-refs 出现、check-ref-format 各案例退出码。
- 冲突/分账：round3b 与 Pro Git 均用"约 41 字节"；本报告点明该数值特定于 SHA-1、SHA-256 仓库为 65 字节，不作恒定常数断言（SHA-256 侧为标准推算，非本机实测，本机默认 SHA-1）。

## 6.5.2 HEAD 与 detached HEAD

### 6.5.2.1 HEAD：指向"当前所在分支"的符号引用

**HEAD** 是 git 里一个特殊引用，回答"我现在在哪个分支上、下一次提交会移动谁"这个问题。正常情况下 HEAD 是一个**符号引用（symbolic reference）**——它不直接存 commit 的 SHA，而是存另一个引用的**名字**。物理上它是 `.git/HEAD` 文件，内容形如 `ref: refs/heads/<分支名>`。

下面在本机 git 2.43.0 观察正常态 HEAD 的内容与解析结果。

```
$ cat .git/HEAD
ref: refs/heads/master
$ git symbolic-ref HEAD
refs/heads/master
```

对初学者，这是理解 git 的关键一环：HEAD 是"指针的指针"。分支（refs/heads/master）指向一个 commit；HEAD 指向分支。于是 `git commit` 的完整动作是——建新 commit → 把 **HEAD 当前指向的那个分支**的文件改写成新 SHA → 分支就前进了一格，HEAD 因为始终指着这个分支名，也就"跟着"前进。`git checkout <分支>` / `git switch <分支>` 做的则是改写 `.git/HEAD` 里那行 `ref:`，把它指到另一个分支。

### 6.5.2.2 符号引用 vs 直接引用：两种指向方式

引用有两种指向方式。**直接引用（direct ref）**文件里直接是一个 40 位 SHA（分支、tag 都是这种）。**符号引用（symbolic ref）**文件里是 `ref: <另一个引用名>`，它指向的是"名字"而非"对象"，解析时要再跟一跳才拿到 SHA。HEAD 是符号引用最典型的例子。

对初学者，区分二者的意义在于"跟随（follow）"这个动作。当你 `git rev-parse HEAD`，git 读到 `ref: refs/heads/master`，发现是符号引用，就继续去读 `refs/heads/master`，读到 40 位 SHA 才停——这个"一路跟到底层对象"的过程叫**解引用（dereference）**。`git symbolic-ref HEAD` 则只跟一跳、只告诉你 HEAD 指的那个"分支名"，不继续解析到 SHA。理解"符号引用要多跟一跳"，就能解释下一节 detached HEAD 里 HEAD 内容形态的突变。

### 6.5.2.3 detached HEAD：HEAD 直接指向 commit、脱离任何分支

当你 `git checkout <某个 commit 的 SHA>`、或 checkout 一个 tag、或 checkout 远程跟踪引用时，git 进入 **detached HEAD（分离头指针）**状态：此时 `.git/HEAD` **不再**是 `ref: refs/heads/...`，而是**直接**存了那个 commit 的 40 位 SHA。HEAD 从"指向分支"变成"直接指向 commit"，脱离了所有分支。

本机实证（从 master 上 checkout 一个历史 commit）：

```
$ git checkout 61073ccf647590e79de6d48c2403692ed51bff20
$ cat .git/HEAD
61073ccf647590e79de6d48c2403692ed51bff20
$ git symbolic-ref HEAD
fatal: ref HEAD is not a symbolic ref
```

对比 06-5.2.1：正常态 `cat .git/HEAD` 是 `ref: refs/heads/master`，且 `git symbolic-ref HEAD` 能成功返回分支名；detached 态 `cat .git/HEAD` 变成裸 SHA，`git symbolic-ref HEAD` 直接报错"不是符号引用"。这一对实测把"分离"的本质讲透了：HEAD 从"跟着一个分支跑"变成"钉死在一个 commit 上"。

### 6.5.2.4 detached HEAD 为什么危险：新提交无分支收留、易被 GC 回收

在 detached HEAD 状态提交，新 commit **不会被任何分支引用**：HEAD 会跟着往前指，但没有分支文件记录它。一旦你切回别的分支，HEAD 也离开这些新 commit，它们就成了**无引用可达的悬空 commit（dangling commit）**——短期内还能靠 reflog（06-5.4）找回，但长期无引用的对象会被 `git gc` 当垃圾回收删除。

对初学者，git 在你进入 detached HEAD 时会打印大段警告，原因正在于此：detached HEAD 本身不是错误（临时看看旧版本、做个实验很有用），但**在它上面提交却不新建分支收留，等于往一个随时会被清扫的临时状态里写东西**。安全做法是：想基于旧 commit 继续开发，就先 `git switch -c <新分支>`（或 `git checkout -b`），把新分支这个"名字"钉在当前 commit 上，新提交就有了永久的引用归属，不再靠 reflog 续命。

#### 来源与时效
- Pro Git 2nd ed §3.1「Branches in a Nutshell」/ §3.4-相关（强·一手承重，核实 2026-07-30）：HEAD 是"指向当前所在本地分支的指针"、commit 移动 HEAD 指向的分支、checkout 切换 HEAD。
- Pro Git 2nd ed §10.3「Git References - The HEAD」（强·一手承重）：HEAD 通常是符号引用（symbolic reference），内容为 `ref: refs/heads/...`。
- git 官方 man git-symbolic-ref(1)、git-checkout(1)（一手交叉，核实 2026-07-30）：symbolic-ref 读写符号引用；detached HEAD 定义与"新提交无分支可达、可能被 gc 回收"的告警语义。
- 本机 git 2.43.0 实测：正常态 `.git/HEAD` = `ref: refs/heads/master` 且 `symbolic-ref HEAD` 返回分支名；detached 态 `.git/HEAD` = 裸 SHA 且 `symbolic-ref HEAD` 报 "not a symbolic ref"。
- 交叉一致，无冲突。

## 6.5.3 tags：轻量 vs 附注

### 6.5.3.1 轻量 tag：refs/tags 下一个直接指向 commit 的固定引用

**轻量标签（lightweight tag）**在物理上和分支几乎一模一样：`.git/refs/tags/<名字>` 下一个文件，内容是它指向对象的 SHA。区别只在语义与位置——它放在 `refs/tags/` 而非 `refs/heads/`，且**约定俗成不移动**（分支随提交前进，tag 一旦打上就固定标记某个 commit）。它本身不产生任何新对象，就是一个"钉在某 commit 上、不会动的书签"。

本机实证（`git tag v1-light`）：

```
$ cat .git/refs/tags/v1-light
d0ebb560fcc1ab1958c6034f7bc57f40a2922580
$ git cat-file -t d0ebb560fcc1ab1958c6034f7bc57f40a2922580
commit
```

引用文件内容是一个 SHA，`cat-file -t` 表明它直接指向一个 **commit** 对象。对初学者，轻量 tag 就理解成"一个不许移动的分支"：结构相同，但你不会在它上面继续提交、也不带任何附加信息。它适合临时或私人的标记。

### 6.5.3.2 附注 tag：refs/tags 指向一个独立的 tag 对象（第四类对象）

**附注标签（annotated tag）**多了一层。`git tag -a` 会先在**对象库**里创建一个 **tag 对象**（就是大主题06-4 讲的第四类 git 对象），里面记录：所指对象的 SHA、对象类型、标签名、**标注者（tagger）与时间戳**、以及一段**标注消息**；然后 `refs/tags/<名字>` 这个引用指向的是**这个 tag 对象**，而不是直接指向 commit。

本机实证（`git tag -a v1-anno -m "annotated tag msg"`）：

```
$ cat .git/refs/tags/v1-anno
ed2e1dfb54aa1ba3b582d48010bee50000add125
$ git cat-file -t ed2e1dfb54aa1ba3b582d48010bee50000add125
tag
$ git cat-file -p v1-anno
object d0ebb560fcc1ab1958c6034f7bc57f40a2922580
type commit
tag v1-anno
tagger t <t@t.com> 1785372269 +0000

annotated tag msg
```

对比 06-5.3.1 的关键差异一眼可见：轻量 tag 的引用直接指向 commit（`cat-file -t` = `commit`）；附注 tag 的引用指向一个 tag 对象（`cat-file -t` = `tag`），tag 对象内部再用 `object` 行指向真正的 commit。这就是"多一层间接"。（这条回指大主题06-4.4：附注 tag 对象是四类对象之一，本报告从"引用层"角度再看一遍它如何被 refs/tags 引用。）

### 6.5.3.3 何时用哪种：附注 tag 是发布版本的推荐做法

两者的取舍很清楚。**附注 tag** 是一个完整对象，带作者、日期、消息，还可被 GPG 签名，能被校验来源——因此发布版本（如 `v1.0.0`）推荐用附注 tag，它把"谁、何时、为何打了这个标"记进了不可变对象里。**轻量 tag** 只是个引用、不带任何元信息，适合临时或本地私用的书签。

对初学者补一个 packed-refs 里的观察点（回指 06-5.1.4）：当引用被打包，附注 tag 在 `packed-refs` 里会出现**两行**——第一行是引用名到 tag 对象 SHA 的映射，紧跟一行以 `^` 开头的 **peeled（剥离）**行，给出该 tag 对象最终指向的 commit SHA。本机实测的 packed-refs 中：

```
ed2e1dfb54aa1ba3b582d48010bee50000add125 refs/tags/v1-anno
^d0ebb560fcc1ab1958c6034f7bc57f40a2922580
```

`ed2e1...` 是 tag 对象、`^d0ebb...` 是它剥到底的 commit。git 预存这个"剥离"结果是为了快速回答"这个 tag 最终对应哪个 commit"而不必每次去读 tag 对象。轻量 tag 因为本就直接指向 commit，则没有 `^` 行。

#### 来源与时效
- Pro Git 2nd ed §2.6「Git Basics - Tagging」（强·一手承重，核实 2026-07-30）：轻量 vs 附注 tag 的区别、附注 tag 是存在数据库里的完整对象（含 tagger/日期/消息、可签名）、轻量 tag 只是指向 commit 的引用、发布推荐用附注 tag。
- Pro Git 2nd ed §10.3「Git References - Tags」与 §10.2 Git Objects（tag 对象为第四类对象，强·一手承重）：附注 tag 对象的字段（object/type/tag/tagger/message）。
- git 官方 man git-tag(1)（一手交叉，核实 2026-07-30）：`-a` 创建附注 tag、tag 对象内容、peeled 引用。
- 本机 git 2.43.0 实测：轻量 tag `cat-file -t` = commit；附注 tag `cat-file -t` = tag 且 `cat-file -p` 显示 object/type/tag/tagger/消息；packed-refs 中附注 tag 带 `^` peeled 行、轻量 tag 不带。
- 交叉一致，无冲突。回指大主题06-4.4（tag 对象作为第四类对象的对象层视角）。

## 6.5.4 reflog 与可恢复性

### 6.5.4.1 reflog：记录引用每次移动的本地日志

**reflog（reference log，引用日志）**是 git 在**本地**为引用维护的一份"移动历史"：每当 HEAD 或某个分支改变指向（提交、checkout、reset、merge、rebase、branch 删除前的位置……），git 就在对应的日志文件里追加一行，记录"从哪个 SHA、变到哪个 SHA、谁、何时、因何操作"。它物理上存在 `.git/logs/` 下（HEAD 的日志是 `.git/logs/HEAD`）。

下面在本机 git 2.43.0 观察 reflog 输出与底层日志文件。

```
$ git reflog -n 4
d0ebb56 HEAD@{0}: checkout: moving from 61073cc... to master
61073cc HEAD@{1}: checkout: moving from master to 61073cc...
d0ebb56 HEAD@{2}: commit: second
61073cc HEAD@{3}: commit (initial): first
$ cat .git/logs/HEAD
0000000000000000000000000000000000000000 61073cc... t <t@t.com> 1785372258 +0000  commit (initial): first
61073cc... d0ebb56... t <t@t.com> 1785372268 +0000  commit: second
...
```

`HEAD@{0}` 是"HEAD 最近一次的位置"、`HEAD@{1}` 是"上一次"，以此类推——这套 `<ref>@{n}` 语法（来自 gitrevisions）让你能引用"任意时刻的引用位置"。对初学者关键的一点：reflog **记录的是引用的移动，不是对象本身**——对象一直在对象库里（只要没被 gc），reflog 只是帮你把"曾经指向过它的名字"找回来。第一行 commit 的"旧值"是全 0 的 SHA，表示"此前引用不存在"。

### 6.5.4.2 用 reflog 找回悬空 commit：git 的"后悔药"

reflog 最重要的用途是**灾难恢复**。当你误删分支、`git reset --hard` 丢了提交、或在 detached HEAD（06-5.2.4）上提交后切走，那些 commit 变得没有任何分支/tag 可达，普通 `git log` 再也看不到它们——但只要它们曾被某引用指向过，reflog 里就留着它们的 SHA，可据此重新建分支把它们"钉"回来。

本机实证（提交后删除分支，再用 reflog 找回）：

```
$ git branch -D tmp
Deleted branch tmp (was 6f2734e).
$ git reflog | grep would-be-lost
6f2734e HEAD@{1}: commit: would-be-lost
# 那个 commit 6f2734e 已无分支可达，但 reflog 仍记着它：
# 需要时 git branch recover 6f2734e 即可把它救回
```

对初学者，这是把握 git 安全感的核心：**在 git 里，只要东西曾被提交过，几乎总能找回来**。`git reset --hard` 之所以敢用，正因为它移动的是引用、丢的是"名字"，对象还在、reflog 还记着旧位置。三个必须点明的边界：其一，reflog 是**纯本地**的，`clone`/`push` 不会传输它，别人的仓库看不到你的 reflog；其二，reflog 条目会**过期**（HEAD 可达条目默认约 90 天、不可达对象的条目默认约 30 天，由 `gc.reflogExpire` / `gc.reflogExpireUnreachable` 控制，具体天数以本机 git 配置为准，此处为 git 文档默认值、未在本机改配置核对故标经验默认），过期加 gc 后就真找不回了；其三，reflog 保护的前提是"曾被引用指向过"——从未 `git add`/`commit` 的东西不在此列。

#### 来源与时效
- Pro Git 2nd ed §7.10「Git Tools - Reset Demystified」及 §2.x 相关（强·一手承重，核实 2026-07-30）：reflog 记录 HEAD/分支移动、`HEAD@{n}` 语法、用 reflog 恢复丢失提交。
- git 官方 man git-reflog(1)（一手交叉，核实 2026-07-30）：reflog 本地性、`<ref>@{n}` 引用、`gc.reflogExpire`（默认 90 天）/`gc.reflogExpireUnreachable`（默认 30 天）过期语义。
- git 官方 man gitrevisions(7)（一手交叉）：`<refname>@{<n>}` 表示该引用第 n 个先前值。
- 本机 git 2.43.0 实测：`git reflog` 列出 HEAD 移动含 commit/checkout；`.git/logs/HEAD` 每行"旧SHA 新SHA 作者 时间 操作"；删除分支后其顶端 commit 仍现于 reflog、可据以恢复。
- 待核/经验默认：reflog 过期天数（90/30 天）为 git 官方文档默认值，本机未改配置未逐项实测，标经验默认、非本机核对数值。

## 6.5.5 远程跟踪引用（remote-tracking refs）

### 6.5.5.1 refs/remotes/*：本地缓存的远端引用状态快照

**远程跟踪引用（remote-tracking reference）**是 git 在你本地维护的、对**远端仓库引用状态**的一份**只读缓存**，存在 `.git/refs/remotes/<远端名>/<分支名>`，典型如 `refs/remotes/origin/master`。它记录的是"上一次你和 origin 通信（clone/fetch/pull）时，origin 上那个分支指向哪个 commit"——是一个**快照**，不是实时的。

本机实证（本地 file:// 协议 clone 后）：

```
$ git branch -a
* master
  remotes/origin/HEAD -> origin/master
  remotes/origin/master
$ find .git/refs -type f | sort
.git/refs/heads/master
.git/refs/remotes/origin/HEAD
```

对初学者最关键的澄清：`origin/master` **不是你的分支**，你不能"切到它上面提交"（真去 `checkout origin/master` 会进入 detached HEAD，见 06-5.2.3）——它是"origin 那边 master 长啥样"的本地记录。你自己的工作在本地分支（`refs/heads/master`）上；远程跟踪引用只是让你能离线看到、并比较"我和远端差了几个提交"。git 之所以能在断网时告诉你"ahead 2, behind 1"，靠的就是这份本地缓存。

### 6.5.5.2 远程跟踪引用如何更新：fetch 按 refspec 映射

远程跟踪引用**只在你主动通信时更新**——主要是 `git fetch`（及包含 fetch 的 `git pull`）。更新遵循 `remote.<名>.fetch` 里配置的 **refspec**：它把远端的引用**映射**到本地 `refs/remotes/` 下的名字。`git clone` 会自动写好默认 refspec。

本机实测的默认 refspec：

```
$ git config --get-all remote.origin.fetch
+refs/heads/*:refs/remotes/origin/*
```

这条 refspec 读作"把远端 `refs/heads/` 下所有分支，映射到本地 `refs/remotes/origin/` 下的同名引用"。对初学者拆解三点。冒号左边 `refs/heads/*` 是**源**（远端的分支）、右边 `refs/remotes/origin/*` 是**目标**（本地缓存放哪）、`*` 是通配的一一对应。开头的 `+` 号表示**允许非快进（non-fast-forward）更新**——即哪怕远端分支被改写了历史，本地缓存也强制跟上（缓存本就该忠实反映远端，不必守快进规则）。这解释了为什么本地分支被别人 force-push 后 `git fetch` 能正确更新 `origin/master`。（refspec 语法与 fetch/push 的完整机制归大主题06-7，本报告只讲它如何驱动远程跟踪引用的更新，点到边界。）

### 6.5.5.3 origin/HEAD 与"本地分支 → 远程跟踪引用"的关联

clone 时还会建一个符号引用 `refs/remotes/origin/HEAD`，指向远端的默认分支（如 `origin/master`），用来记住"origin 的默认分支是哪个"，于是 `git checkout origin` 之类能解析到默认分支。

下面在本机 git 2.43.0 观察 origin/HEAD 这个符号引用。

```
$ git symbolic-ref refs/remotes/origin/HEAD
refs/remotes/origin/master
```

对初学者再补一层常混淆的关系——**上游跟踪（upstream tracking）**。本地分支可以"关联"一个远程跟踪引用作为它的上游（`master` 的上游设为 `origin/master`），关联信息存在 `.git/config` 的 `branch.<名>.remote` / `branch.<名>.merge`，而不是存在引用文件里。有了这层关联，`git status` 才能说"你的 master 领先 origin/master 两个提交"，`git push` / `git pull` 才能不带参数就知道推/拉到哪。要点是把三样东西分清：本地分支（refs/heads/，你提交的地方）、远程跟踪引用（refs/remotes/，远端状态的只读缓存）、上游关联（config 里的配置，把前两者连起来）。

#### 来源与时效
- Pro Git 2nd ed §3.5「Git Branching - Remote Branches」（强·一手承重，核实 2026-07-30）：远程跟踪引用是"远端分支状态的本地引用、你不能移动、fetch 时自动更新"、`<remote>/<branch>` 形态、上游/tracking branch 概念。
- Pro Git 2nd ed §10.3「Git References - Remotes」（强·一手承重）：远程引用存于 refs/remotes、为只读、与本地可写分支的区别。
- Pro Git 2nd ed §10.5「The Refspec」（一手交叉）：默认 fetch refspec `+refs/heads/*:refs/remotes/origin/*` 的语义、`+` 表示允许非快进更新。
- 本机 git 2.43.0 实测（本地 file:// clone）：`git branch -a` 列出 `remotes/origin/master` 与 `origin/HEAD -> origin/master`；`refs/remotes/origin/HEAD` 为符号引用指向 origin/master；默认 fetch refspec 为 `+refs/heads/*:refs/remotes/origin/*`。
- 交叉一致，无冲突。refspec/传输协议纵深下沉大主题06-7（本报告仅停在"refspec 如何驱动远程跟踪引用更新"）。
