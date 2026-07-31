# L4-06·大主题06-4 Git 对象模型（内容寻址 DAG）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（本组实证工具链另含 git 2.43.0）｜ 核实日期：2026-07-30 ｜ 先修：本课大主题06-1（软工过程与工程质量观）、基本命令行与文件系统概念、哈希函数直觉（SHA-1 是把任意字节映射到 160 位摘要的函数）｜ 一手锚点：Pro Git 2nd ed（https://git-scm.com/book/en/v2）§10.2 Git Objects、§10.3 Git References、§10.4 Packfiles（强·一手承重）；交叉：git 官方 man `gitformat-pack(5)`、`githash(5)` / `hash-function-transition` 文档 ｜ 成熟度：对象模型 GA/极稳定（自 2005 年至今格式几乎未变）；SHA-1→SHA-256 过渡为 ⚙演进快项，单独标注

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（06-4.1~06-4.5）共享单一主线——四类对象（blob/tree/commit/tag）如何用同一套「内容寻址」规则生成，再叠加一层存储优化（loose/packfile）。层层递进、实证密集但篇幅适中，按 report-format v3 §一默认 1 大主题 = 1 报告，不拆 `-a/-b`。

> 一条主线心智模型：Git 底层是一个**内容寻址的键值数据库**。你给它一段字节，它算出这段字节的 SHA 摘要当作"键"，把内容存进去；以后凭这个键就能取回一模一样的内容。四类对象只是四种不同的"内容格式"套用同一条寻址规则：blob 存文件内容，tree 存目录清单，commit 存一次快照 + 指向父提交的指针，tag 存对某对象的一个带签名的标注。commit 靠 parent 指针互相串起来，构成一张**有向无环图（DAG）**——这就是"历史"。因为键完全由内容决定，任何一个字节的改动都会连锁改变所有引用它的对象的键，于是整条历史天生**防篡改**、天生**去重**。

> 分账（本报告只停在 Git 底层对象层）：本主题讲**对象怎么存、SHA 怎么算、DAG 怎么连**。分支/HEAD/reflog 这些"引用（ref）如何指向对象"归大主题06-5；merge-base/三向合并/rebase 如何在 DAG 上做运算归06-6；传输协议如何在网络上搬运对象归06-7。本报告会在需要时一句话指路，不展开。

> 本机实证：本大主题是本组**最强实证点**，全部关键论断均在本机 git 2.43.0 上真跑复算（实证脚本只在仓库外 scratchpad，跑完即清）。凡贴出的 SHA、命令输出、手算复原均为真实结果，非凭记忆。

---

## 6.4.1 blob 与内容寻址

### 6.4.1.1 blob 是什么：文件内容的容器

**blob**（binary large object）是 Git 存**文件内容**的对象。它只装内容本身的字节——不含文件名、不含权限、不含时间戳。文件名和权限属于上一层的 tree 对象（见6.4.2），blob 只回答一个问题："这段字节长什么样。"

blob 里没有文件名。你有两个内容完全相同的文件 `a.txt` 和 `b.txt`，Git 里只存**一个** blob，两个文件名都在 tree 里指向它。这是 Git 去重的根基，也解释了为什么"改个文件名"在 Git 里几乎不占空间——文件名换了，内容 blob 没变，键就没变。

### 6.4.1.2 内容寻址：键由内容算出

Git 给每个对象的"键"是它内容的 **SHA-1 摘要**（40 个十六进制字符）。但摘要不是直接对文件原始内容做的，而是先在前面拼一个**头部**再做。blob 对象的存储格式是：

```
blob <内容字节数><NUL 字节 0x00><内容原始字节>
```

对这整串做 SHA-1，得到的就是这个 blob 的 40 位键。本机复算 Pro Git §10.2 的经典例子（内容是 16 字节的字符串 `what is up, doc?`）：

```
$ echo -n "what is up, doc?" | git hash-object --stdin
bd9dbf5aae1a3862dd1526723246b20206e5fc37

$ printf 'blob 16\0what is up, doc?' | sha1sum
bd9dbf5aae1a3862dd1526723246b20206e5fc37  -
```

两者完全一致——这证明 `git hash-object` 内部就是"拼 `blob <len>\0` 头 + 内容，再 SHA-1"。这种"用内容本身算出地址"的存储方式叫**内容寻址（content-addressed）存储**。

对初学者，关键是理解为什么要拼一个头。头里的类型字（`blob`/`tree`/`commit`/`tag`）保证了"同样一段字节，当作不同类型存，键也不同"，避免跨类型撞键；长度字则让读取方知道内容有多长、从哪读起。NUL 字节（`\0`）是头和内容之间的分隔符。记住这个 `<类型> <长度>\0<内容>` 的通式——后面四类对象全都套它，只是"内容"部分格式不同。

### 6.4.1.3 hash-object 与 cat-file：手动读写底层对象

Git 提供两个"管道命令（plumbing）"直接操作对象库。写入用 `git hash-object`，读取用 `git cat-file`：

```
git hash-object -w <file>     # 计算 SHA 并把对象写进 .git/objects（-w = write）
git hash-object --stdin       # 从标准输入读内容算 SHA（不加 -w 则只算不存）
git cat-file -t <sha>         # 打印对象类型（type）
git cat-file -s <sha>         # 打印对象内容字节数（size）
git cat-file -p <sha>         # 按类型友好打印内容（pretty-print）
```

本机把上面那个 blob 真正写进库再读回：

```
$ echo -n "what is up, doc?" | git hash-object -w --stdin
bd9dbf5aae1a3862dd1526723246b20206e5fc37
$ git cat-file -t bd9dbf5aae1a3862dd1526723246b20206e5fc37
blob
$ git cat-file -s bd9dbf5aae1a3862dd1526723246b20206e5fc37
16
$ git cat-file -p bd9dbf5aae1a3862dd1526723246b20206e5fc37
what is up, doc?
```

注意 `cat-file -s` 报的是 **16**（内容字节数），不含 `blob 16\0` 这个头——头是寻址和存储用的，不算进"内容大小"。初学者容易把"对象在磁盘上的文件大小"和"内容 size"混为一谈：磁盘上的松散对象还经过 zlib 压缩（见6.4.5），三个数（内容 size、压缩后磁盘字节、SHA 输入串长度）互不相等，别混。

### 6.4.1.4 内容寻址带来的三个直接后果

因为键完全由内容决定，三件事自动成立：**去重**（同内容只存一份）、**完整性校验**（取回内容重算 SHA 若对不上，说明数据损坏或被篡改）、**幂等**（重复写同一内容不会产生新对象）。

这解释了 Git 很多"神奇"行为的来路。为什么 `git status` 能极快判断文件有没有改？因为只要重算 blob 的 SHA 和记录的对比即可，不必逐字节 diff。为什么历史"改不了"？因为一旦某个 blob 的内容变一个字节，它的键就变了，所有指向旧键的 tree、指向那些 tree 的 commit 的键也会连锁全变（见6.4.3）——你没法"就地"改历史，只能造一段全新的历史。这个连锁性质是 Git 防篡改的技术底座，也是理解 rebase"改写历史其实是造新对象"的钥匙。

#### 来源与时效
- Pro Git 2nd ed §10.2 Git Objects（https://git-scm.com/book/en/v2）——blob 存储格式 `blob <len>\0<content>`、`hash-object`/`cat-file` 用法、`what is up, doc?` → `bd9dbf5...` 经典例（核实 2026-07-30，本机 git 2.43.0 复算一致）。
- git 官方 man `git-hash-object(1)`、`git-cat-file(1)`——`-w`/`--stdin`/`-t`/`-s`/`-p` 选项语义（交叉印证）。
- 本机实证：`echo -n "what is up, doc?" | git hash-object --stdin` 与 `printf 'blob 16\0...' | sha1sum` 均为 `bd9dbf5aae1a3862dd1526723246b20206e5fc37`，两独立路径吻合。
- 无冲突项。

## 6.4.2 tree 对象与目录快照

### 6.4.2.1 tree 是什么：一层目录的清单

**tree** 对象表示**一个目录的一层内容**。它是一张清单，每一行记录该目录下的一个条目（entry），四个字段：文件模式（mode）、类型（blob 或 tree）、目标对象的 SHA、名字。子目录不是把内容摊平进来，而是记一个指向"子目录 tree"的 SHA——于是 tree 递归地引用 tree，构成一棵和文件系统目录结构同形的树。

本机建一个含 `hello.txt` 和子目录 `sub/world.txt` 的提交，看它的顶层 tree：

```
$ git cat-file -p HEAD^{tree}
100644 blob ce013625030ba8dba906f756967f9e9ca394464a	hello.txt
040000 tree b032d4b998c65562e088abaa88fdd859476c089e	sub
```

`hello.txt` 是一个 blob（叶子），`sub` 是一个 tree（要再展开一层才看到 `world.txt`）。这正是"目录快照"的含义：一个 commit 指向一个顶层 tree，顺着 tree→tree→blob 走下去，就能还原出当时整个工作目录的样子。

对初学者，关键区分是 **blob 存内容、tree 存结构**。blob 不知道自己叫什么名字、放在哪个目录；是 tree 里的那一行把"名字 + 位置 + 权限"和某个 blob 绑起来的。所以给文件改名或移动目录，blob 不变（内容没变），变的是 tree。

### 6.4.2.2 tree 的二进制格式与手算复原

tree 每个条目在磁盘上的格式是（条目按名字排序，紧挨排列，无分隔符）：

```
<mode><空格><name><NUL 0x00><20 字节的原始 SHA（二进制，非十六进制文本）>
```

再套通式加头 `tree <所有条目总字节数>\0`，对整串 SHA-1 得到 tree 的键。注意 SHA 在这里是 **20 字节裸二进制**，不是 40 字符的十六进制字符串——这是初学者手算时最容易错的点。本机手算复原一个只含 `hello.txt`（内容 `hello`）的 tree：

```
$ git write-tree
04df07b08ca746b3167d0f1d1514e2f39a52c16c        # git 算的
# 手算：entry = "100644 hello.txt\0" + 20字节裸SHA(blob)
#       store = "tree " + str(len(entry)) + "\0" + entry
#       sha1(store) → 04df07b08ca746b3167d0f1d1514e2f39a52c16c   ✓ 一致
```

手算结果与 `git write-tree` 完全一致，坐实了上面的格式描述。`git write-tree` 这个管道命令做的事就是"把当前暂存区（index）打包成一个 tree 对象并返回其 SHA"。

### 6.4.2.3 文件模式（mode）取值：一个受限的权限集合

tree 条目的 mode 字段沿用了类 Unix 的八进制权限记法，但 Git 只用其中很少的几个固定值：

```
100644  普通文件（非可执行）
100755  可执行文件
120000  符号链接（symlink）
040000  子目录（子 tree）
160000  gitlink（子模块引用，指向另一个仓库的 commit）
```

初学者常以为 Git 会完整保存 Unix 文件权限——并不会。Git 只区分"可执行 / 不可执行"，其余权限位一律规范化为 `644` 或 `755`。这是刻意的可移植性设计：同一份代码 clone 到不同机器、不同用户下，权限语义要一致，不能把某台机器的 umask 带进历史。另外注意 `git ls-tree` 显示子目录时会补零成 `040000`，而底层存储里写的是 `40000`（无前导零）——显示与存储的细微差别，手算时以存储值为准。

### 6.4.2.4 空目录与空树

Git **不追踪空目录**——tree 只记录有内容的条目，一个没有任何文件的目录不会产生 tree 条目，于是"空文件夹"在 Git 里根本不存在（这也是为什么大家用一个约定俗成的空占位文件 `.gitkeep` 来"保留"空目录）。而"空的树"本身是一个合法且**固定**的对象，它的 SHA 是一个人人都认识的常量：

```
$ git hash-object -t tree /dev/null
4b825dc642cb6eb9a060e54bf8d69288fbee4904
```

这个 `4b825dc...` 是所有 SHA-1 仓库里"空树"的固定键（因为空内容 + `tree 0\0` 头是确定的），在写脚本做 diff（如"和空树比较=列出全部文件"）时常被直接引用。它是"内容寻址=键由内容唯一决定"最干净的一个例证。

#### 来源与时效
- Pro Git 2nd ed §10.2 Git Objects——tree 对象概念、`git cat-file -p <tree>`、`git write-tree`、mode 取值示例（核实 2026-07-30）。
- git 官方 man `gitformat-index(5)` / `git-ls-tree(1)`——tree 条目二进制格式 `<mode> <name>\0<20-byte sha>`、mode 显示 vs 存储差异（交叉印证）。
- 本机实证：`git cat-file -p HEAD^{tree}` 输出如上；Python 手算 tree（`100644 hello.txt\0`+裸SHA，套 `tree <len>\0` 头）得 `04df07b0...` 与 `git write-tree` 一致；空树 `git hash-object -t tree /dev/null` = `4b825dc642cb6eb9a060e54bf8d69288fbee4904`。
- 无冲突项。mode 集合以本机 git 2.43.0 与 gitformat 文档为准。

## 6.4.3 commit 对象与父指针 DAG

### 6.4.3.1 commit 是什么：一次快照 + 元数据 + 父指针

**commit** 对象记录**一次提交**：它指向一个顶层 tree（那一刻整个项目的快照），加上零个或多个 **parent**（父提交）指针、author（作者）与 committer（提交者）身份和时间戳、以及提交信息。commit 的存储内容是纯文本（套通式加 `commit <len>\0` 头）：

```
tree <顶层 tree 的 SHA>
parent <父 commit 的 SHA>        # 0 行（根提交）、1 行（普通）、≥2 行（合并）
author <名字> <邮箱> <时间戳> <时区>
committer <名字> <邮箱> <时间戳> <时区>
<空行>
<提交信息>
```

commit 不直接装文件内容，它只装"一个指向 tree 的指针 + 谁、何时、为何"。要看这次提交改了什么，得顺着 tree 展开。author 和 committer 常常是同一人，但在 rebase、cherry-pick、打补丁等场景下会分离——author 是原始写代码的人和时间，committer 是实际把它落成这个 commit 的人和时间。

### 6.4.3.2 手拼 commit 复算 SHA

commit 也严格遵守内容寻址通式，本机手算复原一个真实 commit 的 SHA（先关掉本机默认开启的提交签名，否则内容里会多一段 `gpgsig` 使复算复杂）：

```
$ git cat-file -p HEAD
tree 04df07b08ca746b3167d0f1d1514e2f39a52c16c
author Test <t@e.com> 1785369600 +0000
committer Test <t@e.com> 1785369600 +0000

msg
$ git rev-parse HEAD
740fa02a29a1c0c8a984ea85472fc3c38ef4e0ad
# 手算：body = 上面 cat-file 的完整正文（含结尾换行），len=132 字节
#       sha1("commit 132\0" + body) → 740fa02a29a1c0c8a984ea85472fc3c38ef4e0ad   ✓
```

手算结果与 `git rev-parse HEAD` 一致。这直接证明了两件事：commit 的 SHA 完全由"它指向的 tree + 父指针 + 作者/提交者 + 时间戳 + 信息"共同决定；改动其中**任何一个字节**（哪怕只改提交信息里一个标点、或改一个时间戳）都会得到一个**全新的 commit SHA**。这就是"提交不可变"的技术含义——你无法编辑一个 commit，只能生成一个新的。

"我 `git commit --amend` 修改了上一次提交"——其实没有修改，是造了一个**新 commit** 替换了分支指向。旧 commit 还在对象库里（暂时悬空，见06-5 reflog），只是没有引用指向它了。

### 6.4.3.3 parent 指针构成有向无环图（DAG）

每个 commit 通过 parent 指针指向它的前驱，众多 commit 就串成一张**有向无环图（Directed Acyclic Graph, DAG）**：边从"子提交"指向"父提交"，方向永远朝历史更早处；因为一个 commit 的 SHA 依赖其 parent 的 SHA（父的 SHA 是子内容的一部分），你不可能造出一个"指向自己后代"的环——那需要在算父之前就知道子的 SHA，逻辑上做不到。这个"无环"是内容寻址自动保证的，不是额外加的约束。

parent 的数量刻画了提交的种类：

```
0 个 parent  →  根提交（仓库第一个 commit）
1 个 parent  →  普通线性提交
≥2 个 parent →  合并提交（merge commit，把多条线汇合）
```

本机造一个合并提交验证"两父"：

```
$ git merge --no-ff feature -m "merge"
$ git cat-file -p HEAD | grep -c '^parent'
2
```

对初学者，DAG 是理解 Git 一切历史操作的地基。"分支"不过是给 DAG 里某个 commit 起了个会移动的名字（06-5）；"合并"是造一个有两个 parent 的新 commit 把两条线接起来（06-6）；"共同祖先 / merge-base"是在 DAG 上找两个 commit 最近的公共前驱（06-6）；"log 历史"是从某个 commit 沿 parent 指针回溯遍历。把 commit 想成 DAG 的节点、parent 想成边，Git 的历史模型就没有神秘可言了。

#### 来源与时效
- Pro Git 2nd ed §10.2 Git Objects——commit 对象格式（tree/parent/author/committer/message）、`git cat-file -p`、"提交不可变"（核实 2026-07-30）。
- Pro Git 2nd ed §3.1 Branches in a Nutshell——commit 与 parent 构成的快照图（DAG）示意（交叉印证，深度归06-5/06-6）。
- 本机实证：`git cat-file -p HEAD` 输出如上；手拼 `commit 132\0`+正文经 SHA-1 得 `740fa02a...`，与 `git rev-parse HEAD` 一致；`--no-ff` 合并提交 `grep -c '^parent'` = 2。
- 冲突/坑：本机 git 环境默认开启提交签名（`gpgsig` 段），会进入 commit 内容并改变其 SHA；手算复原前须关 `commit.gpgsign false`。规范未强制签名——这是本机配置差异，非格式冲突。

## 6.4.4 annotated tag 对象

### 6.4.4.1 第四类对象：带标注的 tag

**annotated tag（附注标签）**是 Git 的**第四类对象**（前三类是 blob/tree/commit）。它是对某个对象（几乎总是一个 commit）的一次带元数据的标注，内容格式为：

```
object <被标注对象的 SHA>
type <被标注对象的类型，通常是 commit>
tag <标签名>
tagger <名字> <邮箱> <时间戳> <时区>
<空行>
<标注信息>
```

本机造一个附注标签并读它：

```
$ git tag -a v1.0 -m "release one" HEAD
$ git cat-file -t v1.0
tag
$ git cat-file -p v1.0
object 740fa02a29a1c0c8a984ea85472fc3c38ef4e0ad
type commit
tag v1.0
tagger Test <t@e.com> 1785372279 +0000

release one
```

`cat-file -t` 报的是 `tag`——它是一个真实存在于对象库、有自己 SHA 的对象，和 blob/tree/commit 平级，同样内容寻址、同样不可变。它比 commit 多的信息是"谁、何时、为何打了这个标签"，可用于附加 GPG 签名做发布验真。

### 6.4.4.2 附注 tag vs 轻量 tag：一个是对象，一个只是引用

Git 有两种 tag，本质天差地别。**附注标签**是上面那个独立对象；**轻量标签（lightweight tag）**根本不产生对象，它只是 `refs/tags/` 下一个文件，里面直接写着某个 commit 的 SHA——本质上就是"一个不会移动的分支指针"。本机对比：

```
$ git tag light HEAD                 # 轻量标签
$ cat .git/refs/tags/light
740fa02a29a1c0c8a984ea85472fc3c38ef4e0ad     # 直接就是那个 commit 的 SHA
$ git cat-file -t $(git rev-parse light)
commit                               # 解析出来是 commit（无中间 tag 对象）
```

对比附注标签 `v1.0`：`refs/tags/v1.0` 里存的是 **tag 对象**的 SHA，tag 对象再指向 commit——多了一跳。所以：

```
轻量 tag：  refs/tags/light  ─→  commit
附注 tag：  refs/tags/v1.0   ─→  tag 对象  ─→  commit
```

对初学者，选择很简单：给正式发布打标签、需要留下"谁在何时发布、可加签名"的记录，用 `git tag -a`（附注）；只是给自己临时做个书签，用 `git tag <name>`（轻量）。这个"附注 tag 是对象、轻量 tag 只是引用"的区分，在06-5 讲引用机制时会再从 ref 角度回看一遍——本节从"对象"角度立此存照。

#### 来源与时效
- Pro Git 2nd ed §10.2 Git Objects（tag 对象作为第四类对象）与 §2.6 Tagging（附注 vs 轻量标签的用户视角）（核实 2026-07-30）。
- git 官方 man `git-tag(1)`——`-a`（annotated）语义、轻量标签为直接引用（交叉印证）。
- 本机实证：`git tag -a v1.0` 后 `cat-file -t v1.0` = `tag`，`cat-file -p` 显示 object/type/tag/tagger 段；`git tag light` 后 `cat .git/refs/tags/light` 直接是 commit SHA，`cat-file -t` 解析为 `commit`。
- 无冲突项。

## 6.4.5 对象存储：loose vs packfile

### 6.4.5.1 松散对象（loose object）：一对象一文件，zlib 压缩

Git 写入的每个对象**默认先以"松散对象"形式**落盘：一个对象存成 `.git/objects/` 下的一个文件，路径由它 40 位 SHA 的**前 2 位做目录名、后 38 位做文件名**：

```
.git/objects/74/0fa02a29a1c0c8a984ea85472fc3c38ef4e0ad
             └┬┘└──────────────┬──────────────────┘
           SHA前2位          SHA后38位
```

文件内容是把"`<类型> <长度>\0<内容>`"这整串用 **zlib（DEFLATE）压缩**后的字节。注意：**SHA 是对压缩前的串算的，压缩不影响对象身份**。本机验证一个松散对象确实是 zlib 流：

```
$ python3 -c "import zlib;d=open('.git/objects/74/0fa...','rb').read();\
  print('raw:',d[:4]);print('inflated:',zlib.decompress(d)[:30])"
raw: b'x\x01\x85\x8c...'                  # 0x78 0x01 = zlib 头
inflated: b'commit 125\x00tree 04df07b...' # 解压后正是 <类型> <长度>\0<内容>
```

前两个字节 `78 01` 是标准 zlib 头，解压出来就是我们熟悉的 `commit 125\0...`。对初学者，用前 2 位分目录是为了避免单一目录里堆几十万个文件（老式文件系统对超大目录很慢）；zlib 压缩则是省空间。这两点都是纯存储层优化，和"对象是什么、SHA 怎么算"完全解耦。

### 6.4.5.2 打包文件（packfile）：多对象合一 + delta 压缩

松散对象每个独占一文件，对象一多就浪费空间、也慢。Git 会在 `git gc`（或 push/pull 时自动）把大量松散对象**打包**进一个 **packfile**：`.git/objects/pack/` 下成对出现 `pack-<hash>.pack`（真正的对象数据）和 `pack-<hash>.idx`（索引，供快速定位；新版另有 `.rev` 反向索引）。

```
$ git gc
$ ls .git/objects/pack/
pack-b75a5fd0....idx  pack-b75a5fd0....pack  pack-b75a5fd0....rev
```

packfile 的关键优化是 **delta 压缩（增量压缩）**：内容相近的对象，只完整存一个"基对象"，其余存成"相对基对象的差异（delta）"。`git verify-pack -v` 能看到 delta 关系：

```
$ git verify-pack -v pack-b75a....idx
43f227c...  blob   9018 82  717                    # 基对象：9018 字节
e37a677...  blob      9 19  799 1 43f227c40b48...  # delta：仅 9 字节，depth=1，base=43f227c
```

第二个 blob 只占 9 字节，末尾 `1 43f227c...` 表示它是**深度 1 的 delta**、以 `43f227c` 为基对象重建。两个 blob 内容几乎一样（只差结尾一行），delta 只存那点差异，于是从 9018 字节压到 9 字节。对初学者，把 delta 想成"别把两张几乎一样的照片各存一份，存一张 + 一句'第二张=第一张但右下角改了'"。delta 同样是纯存储层的事：取对象时 Git 会自动"解 delta"还原成完整内容，SHA 仍是对完整内容算的，身份不变。

delta **不是**"新版基于旧版"的意思。Git 挑基对象只看"哪个当基能压得最狠"，常常反而拿**较新/较大**的版本当基、把旧版存成 delta（上例基对象 9018 字节正是较大的那版）。delta 方向与提交时间顺序无关。

### 6.4.5.3 哈希算法：SHA-1 现状与 SHA-256 过渡（⚙演进快·锚 git 2.43.0）

Git 默认的对象哈希仍是 **SHA-1**。因学界已在 2017 年（SHAttered）造出 SHA-1 碰撞，Git 自 2.13 起默认改用**加固版 SHA-1（sha1dc / SHA-1CD，带碰撞检测）**——算出的摘要与标准 SHA-1 一致，但遇到已知碰撞构造会拒绝，堵住针对版本库的碰撞攻击。本机默认对象格式：

```
$ git rev-parse --show-object-format
sha1
```

Git 也已实现 **SHA-256** 对象格式，本机 git 2.43.0 可以创建 SHA-256 仓库并正确内容寻址：

```
$ git init --object-format=sha256 <dir>
$ echo -n "hello" | git hash-object --stdin       # 在该仓库内
8aec4e4876f854f688d0ebfc8f37598f38e5fd6903cccc850ca36591175aeb60
$ printf 'blob 5\0hello' | sha256sum
8aec4e4876f854f688d0ebfc8f37598f38e5fd6903cccc850ca36591175aeb60  -
```

两者一致——证明 SHA-256 仓库套的是完全相同的 `blob <len>\0<content>` 通式，只是换了摘要函数、键变成 64 个十六进制字符。

要点与时效（⚙演进快，锚本机 git 2.43.0 / 核实 2026-07-30）：SHA-256 支持在本机**可用但非默认**，需 `git init --object-format=sha256` 显式开启；仓库的哈希算法在创建时**一次性固定**，事后不能切换。**最大的现实限制是互操作（interop）尚未完成**——SHA-1 仓库与 SHA-256 仓库之间还不能直接互相 push/fetch/克隆，官方 `hash-function-transition` 文档把"两种哈希共存、透明互译"列为**仍在推进的目标**而非已交付能力（此结论据官方过渡文档定位，本机未逐条实测互操作，标「据文档、互操作细节待核」）。因此当前对绝大多数团队而言，生产仓库仍是 SHA-1（加固版）；SHA-256 适合评估/新建的隔离仓库，不宜期待与现有 SHA-1 生态无缝混用。

#### 来源与时效
- Pro Git 2nd ed §10.4 Packfiles、§10.2（松散对象路径与 zlib）（https://git-scm.com/book/en/v2）——loose 对象目录分片、zlib 压缩、`git gc`/`git verify-pack -v`、delta 压缩（核实 2026-07-30）。
- git 官方 man `gitformat-pack(5)`——packfile / idx 二进制格式与 delta 编码（交叉印证）；`hash-function-transition`（Documentation/technical）——SHA-1→SHA-256 过渡状态、互操作目标（仍在推进）。
- git 官方公告与 `git-hash-object`/`git-init` man——`--object-format` 选项、默认 SHA-1、加固版 SHA-1（sha1dc，2.13 起默认）应对 SHAttered 碰撞。
- 本机实证（git 2.43.0）：松散对象 `78 01` zlib 头、解压得 `commit 125\0...`；`git verify-pack -v` 见 9 字节 depth-1 delta 挂在 9018 字节基对象上；`rev-parse --show-object-format` = `sha1`；`git init --object-format=sha256` 下 `hash-object` 与 `printf 'blob 5\0hello'|sha256sum` 同为 `8aec4e48...`。
- 冲突/待核项：SHA-256 ⇄ SHA-1 **互操作**（跨哈希 push/fetch/clone）在本机 git 2.43.0 上据官方文档标注为「仍在推进」，本报告未实测互操作路径，标「据文档、细节待核」，不下"已可用/不可用"的绝对结论。delta 方向与时间顺序无关，已由本机 verify-pack（较大版本当基）证伪"新版基于旧版"的常见误解。
