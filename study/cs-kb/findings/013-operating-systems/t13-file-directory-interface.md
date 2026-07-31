# L4-01·大主题13 文件与目录接口

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-29 ｜ 先修：本课大主题02（进程与地址空间、fd 表随 fork 复制）、大主题08/09/10（虚拟内存与页缓存所依赖的分页与缺页）、大主题11（I/O 子系统与块设备）｜ 一手锚点：OSTEP 网页版 Ch39「Interlude: Files and Directories」（https://pages.cs.wisc.edu/~remzi/OSTEP/ ，核实 2026-07-25）；Berkeley CS162 现行 L18「File Systems / File Descriptors」；Linux man-pages `open(2)`/`read(2)`/`write(2)`/`lseek(2)`/`fsync(2)`/`stat(2)`/`link(2)`/`symlink(2)`（man-pages 6.x，man7.org，核实 2026-07-29）；POSIX.1-2017（IEEE Std 1003.1-2017）`<fcntl.h>`/`<unistd.h>` 接口语义；Linux 内核文档 `Documentation/filesystems/vfs.rst`（torvalds/linux master，核实 2026-07-29）｜ 成熟度：GA/稳定（open/read/write/lseek/stat/link 接口语义自 POSIX 早期即稳定数十年；VFS 结构名 file/inode/dentry/superblock 在 Linux 长期稳定；页缓存回写参数为 delta 项，标版本）

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（13.1–13.5）围绕单一主线——"进程用什么句柄看文件（13.1 fd 与读写偏移）→ 文件本身在磁盘上由什么描述（13.2 inode 元数据）→ 内核如何用统一接口套住各种文件系统（13.3 VFS）→ 目录项如何指向文件、两种链接的差别（13.4 硬/符号链接）→ 写入为何先进内存、如何强制落盘（13.5 页缓存与 fsync）"。五节层层递进、篇幅适中，按 report-format v3 §一默认 1 大主题 = 1 报告，不拆 `-a/-b`。

> 本报告一条主线心智模型：**一个打开的文件在内核里是"三级间接"——进程手里的文件描述符（一个小整数）只是 per-process fd 表的下标；fd 表项指向一个全局的"打开文件表"表项（记录读写偏移、访问模式等"这次打开"的状态）；打开文件表项再指向内存中的 inode（文件唯一的身份与元数据，指向磁盘上的数据块）。** 目录不过是"文件名→inode 号"的映射表；硬链接是给同一个 inode 再起一个名字，符号链接是一个内容为路径字符串的小文件；而 write 通常先把数据写进内核的页缓存（在内存里）、稍后由内核异步回写磁盘，fsync 是把该文件的脏数据强制刷到稳定存储的显式命令。

> 下游边界（本课不外扩，只在交界处一句指路）：本报告讲**面向用户的文件与目录接口 + 内核里对应的对象结构**。inode 表/位图/数据块在磁盘上如何布局、目录如何组织成树、崩溃后如何保持一致（journaling/fsck/LFS）归本课**大主题14 文件系统实现与崩溃一致性**；块设备/磁盘调度/DMA 归大主题11；页缓存所依赖的分页与缺页机制归大主题09/10，本报告只讲页缓存作为"文件数据在内存中的缓冲"这一面。

> 本报告接口语义（open 标志、lseek whence、link/symlink 行为、fsync 保证）以 Linux man-pages 与 POSIX 交叉核对；OSTEP Ch39 与 CS162 提供机制主线与三级间接图。本机 Linux 6.18.5 x86_64 @2026-07-29 用 `stat`/`ln`/`ln -s`/`ls -li`/`strace`/`/proc/self/fd`/`/proc/filesystems`/`/proc/meminfo` 实跑取证并贴入正文；`strace` 与 `gcc` 本机可用，`perf`/`vmtouch` **未安装（待核）**，故不声称页缓存命中的计数器级证据。本机实证是多来源比对的补充，不替代比对。

---

## 13.1 fd 表与 open/read/write/lseek

### 13.1.1 文件描述符是什么

文件描述符（file descriptor，fd）是内核发给进程、用来指代一个已打开文件的**小的非负整数**。进程调用 `open()` 打开文件，内核返回一个 fd；之后所有 `read`/`write`/`lseek`/`close` 都用这个 fd 指名要操作哪个文件。按 POSIX 约定，每个进程启动时已有三个约定 fd：0 是标准输入、1 是标准输出、2 是标准错误。`open()` 返回**当前未被占用的最小 fd**，所以新打开的文件通常从 3 开始。

对初学者，关键是别把 fd 当成"文件本身"或"文件在磁盘上的地址"——它只是**进程私有的一张小表的下标**。同一个物理文件被两个进程各自 open，两边拿到的 fd 数值可能都是 3，但互不相干；反过来，一个进程里 fd 3 和 fd 4 也可能指向同一个文件的两次不同打开。本机 `ls -l /proc/self/fd` 实测就把这张表列了出来：

```
0 -> /dev/null
1 -> pipe:[40853]
2 -> /tmp/.../bzh8xp2f7.output
3 -> /proc/407/fd
```

每一行左边的数字就是 fd，右边是它当前指向的对象（文件、管道、设备等）。

### 13.1.2 描述符→打开文件表→inode 的三级间接

一个打开的文件在内核里由**三层结构**串起来，这是本大主题最核心的一张图。三层各自独占一行画出来是：

```
[进程 A] fd 表:  fd 3 ─┐
                       ├──► 打开文件表项 #7  (offset=100, flags=O_RDONLY) ──► inode(#1884582)
[进程 A] fd 表:  fd 4 ─┘（同一项，dup 得到）                                      │
                                                                                 ▼
[进程 B] fd 表:  fd 3 ───► 打开文件表项 #9  (offset=0,   flags=O_WRONLY) ──► 同一 inode(#1884582)
```

三层各管一件事，逐层说明：

```
第一级  per-process fd 表：每个进程一张，下标是 fd，表项指向某个打开文件表项。
```

```
第二级  系统级打开文件表（open file table）：每次 open() 新增一项，记录本次打开的读写偏移 offset、访问模式 flags、以及指向 inode 的指针；被多个 fd 共享时有引用计数。
```

```
第三级  内存 inode 表（in-core inode）：每个"文件"唯一一项，记录文件的元数据与数据块位置；无论被打开多少次，内存里只有一份。
```

为什么要三级而不是 fd 直接指 inode？因为"这次打开的状态"（尤其是读写偏移 offset）和"文件本身"是两回事。offset 记录下次 read/write 从文件的第几个字节开始，它属于"某一次打开"，所以放在中间的打开文件表项里，而不是放在 inode 里（否则同一文件的两个打开会互相踩偏移）。

初学者最该记住由此推出的两条行为差异。其一，**父子进程 fork 后共享打开文件表项**：fork 复制的是 fd 表（第一级），子进程的 fd 和父进程的 fd 指向**同一个第二级表项**，因此共享同一个 offset——父进程读了 5 字节，子进程接着读会从第 6 字节开始，这也是 shell 里父子进程写同一个重定向文件不会互相覆盖的原因。`dup()`/`dup2()` 同理：复制出的新 fd 与原 fd 指向同一打开文件表项，共享 offset。其二，**两个进程各自独立 open 同一文件，得到两个不同的第二级表项、两个独立 offset**，但它们指向内存里**同一个 inode**（第三级只有一份）。

### 13.1.3 open：打开或创建文件

`open()` 按路径找到（或创建）文件，在打开文件表里建一项，返回一个 fd。最小形式与带创建的形式各占一行：

```c
int fd = open("orig.txt", O_RDONLY);
```

```c
int fd = open("new.txt", O_WRONLY | O_CREAT | O_TRUNC, 0644);
```

第二参数 flags 是一组按位或的标志：访问模式三选一（`O_RDONLY` 只读 / `O_WRONLY` 只写 / `O_RDWR` 读写），再可选叠加行为标志，常见有 `O_CREAT`（不存在则创建，此时第三参数 mode 给出新文件权限位，会被进程 umask 过滤）、`O_TRUNC`（打开时清空已有内容）、`O_APPEND`（每次写都自动追加到文件末尾）、`O_EXCL`（配合 `O_CREAT`，若文件已存在则失败，用于原子地"独占创建"）。

对初学者，把 open 理解成"登记 + 拿号"：内核在打开文件表里给你登记一次（记下你要怎么用这个文件），发给你一个号（fd）。失败时返回 -1 并置 `errno`（例如权限不够 `EACCES`、文件不存在 `ENOENT`）。用完必须 `close(fd)` 注销，否则 fd 会泄漏、打开文件表项的引用计数降不下去。`O_APPEND` 值得单独记：它保证"定位到末尾 + 写入"这两步对该文件是原子的，多个进程同时往一个日志文件追加不会互相截断，这是普通 lseek 到末尾再写做不到的。

### 13.1.4 read / write：顺序读写并推进偏移

`read()` 从 fd 当前 offset 处读最多 count 字节到缓冲区，`write()` 把缓冲区的 count 字节写到 fd 当前 offset 处；两者都会把 offset 前移实际传输的字节数。签名各占一行：

```c
ssize_t read(int fd, void *buf, size_t count);
```

```c
ssize_t write(int fd, const void *buf, size_t count);
```

返回值是**实际**读/写的字节数（`ssize_t`）。read 返回 0 表示到达文件末尾（EOF），返回正数可能**小于**请求的 count（部分读，对普通文件少见、对管道/socket 常见）；返回 -1 是出错。因此正确的用法是循环读写直到满足需求或 EOF，不能假设一次调用就搬完全部数据。

本机 strace 直接看到 offset 随 read 推进、再被 lseek 拨回、第二次 read 又从头读到同样内容：

```
openat(AT_FDCWD, "orig.txt", O_RDONLY)  = 3
read(3, "hello", 5)                     = 5
lseek(3, 0, SEEK_SET)                   = 0
read(3, "hello", 5)                     = 5
close(3)                                = 0
```

第一次 `read(3,...,5)` 读了 "hello"（offset 从 0 变到 5），若不 lseek，下一次 read 会接着读第 6 字节起的内容；这里插入 `lseek(3,0,SEEK_SET)` 把 offset 拨回 0，所以第二次 read 又读到 "hello"。这正好演示了"offset 是打开文件表项的状态、由 read/write/lseek 共同维护"。

### 13.1.5 lseek：显式移动读写偏移

`lseek()` 不做任何 I/O，只把 fd 的读写 offset 移到指定位置，用于随机访问。签名与三种基准（whence）各占一行：

```c
off_t lseek(int fd, off_t offset, int whence);
```

```
SEEK_SET：新 offset = offset（从文件开头算）
SEEK_CUR：新 offset = 当前 offset + offset（相对当前）
SEEK_END：新 offset = 文件大小 + offset（相对末尾，offset 可为负）
```

返回值是移动后的新 offset（从文件开头计的字节数）。`lseek(fd, 0, SEEK_CUR)` 是一个常用小技巧：不移动位置、只查询"当前 offset 是多少"。

初学者要澄清两点。第一，lseek 移动的是内核里那份 offset，**和磁盘毫无关系**，也不读不写数据，它很廉价。第二，允许把 offset 移到**超过文件当前末尾**的位置再 write，中间跳过的区域会成为"空洞"（hole）——读出来是全 0，但可能不占实际磁盘块，这就是稀疏文件（sparse file）。名字里的 "l" 是历史遗留（早期 `seek` 用 16 位偏移，`lseek` 是 long 版本），今天统一用 lseek。read/write/lseek 都以 offset 为轴心，恰好印证 13.1.2 把 offset 放在中间那一级的设计。

#### 来源与时效
- OSTEP Ch39「Interlude: Files and Directories」（网页版，核实 2026-07-25）：fd 概念、open/read/write/lseek 接口、fd 表→打开文件表→inode 三级间接图、fork/dup 共享 offset。
- Linux man-pages `open(2)`/`read(2)`/`write(2)`/`lseek(2)`（man-pages 6.x，核实 2026-07-29）：flags 完整语义、O_APPEND/O_EXCL 原子性、返回值与 errno、whence 定义、稀疏文件与空洞。
- POSIX.1-2017 `<fcntl.h>`/`<unistd.h>`：接口的规范语义，与 Linux 实现一致。
- 本机实证（Linux 6.18.5 @2026-07-29）：`strace -e openat,read,lseek,close ./demo` 见上文真实输出；`ls -l /proc/self/fd` 见 13.1.1。
- 交叉一致，无冲突。三级间接是规范/实现共识；"打开文件表"在 OSTEP 称 open file table、Linux 内核称 `struct file`（见 13.3），名不同实一致。

## 13.2 inode 与元数据

### 13.2.1 inode 是文件的身份与元数据

inode（index node，索引节点）是文件系统给**每个文件唯一分配的一小块结构**，保存该文件的全部元数据（metadata）以及"数据存在磁盘哪些块"的指针，但**不含文件名**。文件名不在 inode 里，而在目录项里（见 13.4）。每个 inode 有一个在其所在文件系统内唯一的编号——inode number。

对初学者，最好把"文件"拆成三样东西：**名字**（在目录里）、**身份与属性**（inode）、**内容**（数据块）。inode 是中间那个"身份证"，名字通过 inode 号找到它，它再指向内容。一个文件可以有多个名字（硬链接，见 13.4）却只有一个 inode——所以说 inode 才是文件"真身"。本机 `ls -li` 最左列打印的就是 inode 号：

```
1884582 -rw-r--r-- 2 root root 12 Jul 29 22:33 orig.txt
```

开头的 `1884582` 就是这个文件的 inode 号。

### 13.2.2 inode 里都有什么（stat 元数据）

`stat()` 系统调用读出一个文件的 inode 元数据。本机 `stat orig.txt` 的真实输出把字段列全了：

```
File: orig.txt
Size: 12   Blocks: 8   IO Block: 4096   regular file
Device: 254,0   Inode: 1884582   Links: 1
Access: (0644/-rw-r--r--)  Uid: (0/root)  Gid: (0/root)
Access: 2026-07-29 22:33:57 ...   (atime)
Modify: 2026-07-29 22:33:57 ...   (mtime)
Change: 2026-07-29 22:33:57 ...   (ctime)
Birth:  2026-07-29 22:33:57 ...   (crtime)
```

逐项对应 inode 字段：文件类型（普通文件 / 目录 / 符号链接 / 设备等）与权限位（mode，即 `0644` 那部分）；所有者 uid 和组 gid；文件大小 size（字节）；占用的块数 blocks；硬链接计数 links（见 13.4）；以及数据块指针（stat 不直接显示，但 inode 里存着"内容在哪些磁盘块"）。

初学者要抓住"元数据 vs 数据"的分界：inode 存的是**关于文件的信息**，不是文件内容本身。size=12 说明内容 12 字节，但这 12 字节的实际数据在别处（数据块），inode 只存指向它们的指针。还要注意 inode 里**没有文件名**——这不是遗漏，而是刻意设计，正因如此同一 inode 才能挂多个名字。

### 13.2.3 三个时间戳：atime / mtime / ctime

inode 记录三个时间戳，含义各不相同，初学者极易混淆：

```
atime (access time)：文件内容最近一次被读取的时间
mtime (modify time)：文件内容最近一次被修改（写入）的时间
ctime (change time)：inode 元数据最近一次被改变的时间（含权限、属主、链接数变化）
```

mtime 是"内容"变了，ctime 是"inode 本身"变了。改文件内容会同时更新 mtime 和 ctime（因为 size 等元数据也变）；只 `chmod` 改权限则只更新 ctime、不动 mtime（内容没变）。ctime 不是 "creation time"——创建时间是第四个可选戳 crtime/Birth（Linux 上通过较新的 `statx(2)` 才能读，`stat` 命令显示为 Birth）。

atime 有个实践细节：每次读都更新 atime 会造成大量写放大，所以现代 Linux 默认挂载选项是 `relatime`——只在 atime 早于 mtime/ctime、或距上次更新超过约 24 小时时才更新 atime，以减少写入。这属于挂载策略，具体阈值随发行版配置，本机以 `mount` 输出为准。

### 13.2.4 inode 如何指向数据块：直接块与多级间接块

inode 里存一组**块指针**来记录"文件内容在磁盘的哪些块"。经典 Unix/FFS 式设计（OSTEP Ch40 详述，本报告只作接口层铺垫）用"多级索引"：inode 里有若干**直接指针**（direct pointer，各指一个数据块），外加**一级间接指针**（指向一个装满块指针的块）、**二级间接指针**（指向"装满一级间接块指针"的块），乃至三级间接。

这样设计的直觉是"小文件省、大文件也能存"。小文件用几个直接指针就够，元数据开销极小；文件变大时才动用间接块，一级间接块能多指 (块大小/指针大小) 个块，二级再平方，三级再立方，于是用固定大小的 inode 就能覆盖从几字节到很大的文件。代价是访问大文件深处的数据要多跳几次间接块。注意这是**经典实现**思路，ext4 等现代文件系统改用 extent（区段）来描述连续块以更高效，其磁盘布局细节归大主题14；本节只需知道"inode 通过块指针（直接 + 多级间接或 extent）把文件内容的位置记下来"。

#### 来源与时效
- OSTEP Ch39/Ch40（网页版，核实 2026-07-25）：inode 概念、元数据字段、多级间接块指针结构、"inode 不含文件名"。
- Linux man-pages `stat(2)`/`statx(2)`、`inode(7)`（man-pages 6.x，核实 2026-07-29）：`struct stat` 字段（st_mode/st_ino/st_size/st_nlink/st_uid/st_gid/时间戳）、atime/mtime/ctime 精确语义、crtime 需 statx。
- 本机实证（Linux 6.18.5 @2026-07-29）：`stat orig.txt`、`ls -li orig.txt` 真实输出见上文。
- 交叉一致。atime 的 `relatime` 默认为 Linux 挂载策略（内核文档 `Documentation/filesystems/`），非 POSIX 强制，标为实现项；具体阈值以本机 `mount` 为准。

## 13.3 VFS 抽象层

### 13.3.1 VFS 是什么：一套统一的文件接口

VFS（Virtual File System，虚拟文件系统，也叫 virtual filesystem switch）是内核里位于系统调用和具体文件系统之间的一个**抽象层**。它定义了一组统一的对象和操作接口，让 ext4、XFS、Btrfs、NFS、tmpfs、proc 等形形色色的文件系统都实现同一套接口。于是 `open`/`read`/`write`/`stat` 等系统调用只跟 VFS 打交道，不用关心底下到底是哪种文件系统。

对初学者，VFS 就是"面向对象里的接口/抽象基类"落在内核里的样子：VFS 规定了"文件系统必须能干这些事"（打开、读、写、查元数据、列目录……），每个具体文件系统提供自己的实现（函数指针表）。好处是**一次编写、到处适用**——`cat` 读一个 ext4 文件、读一个 U 盘上的 vfat 文件、读 `/proc/meminfo` 这种根本不在磁盘上的虚拟文件，用的都是同一个 `read()`，因为 VFS 把差异藏在了统一接口之下。这也是 Unix "一切皆文件"哲学的落地机制。

本机 `cat /proc/filesystems` 就列出了当前内核注册的一批文件系统类型（VFS 能挂载的种类），节选：

```
nodev  sysfs
nodev  tmpfs
nodev  proc
nodev  cgroup2
       ext4
       overlay
nodev  fuse
```

带 `nodev` 的是不依赖块设备的虚拟/内存文件系统（如 proc、tmpfs、cgroup2），不带的（如 ext4）需要一个真实块设备。

### 13.3.2 VFS 的四个核心对象：superblock / inode / dentry / file

Linux VFS 用四种核心对象把文件系统抽象出来，各管一层，逐个独占一行：

```
superblock（超级块，struct super_block）：代表一个"已挂载的文件系统实例"，记录它的整体信息（类型、块大小、根目录等）。
```

```
inode（索引节点，struct inode）：代表一个"文件对象"（含目录、设备等），持有元数据与指向操作函数表的指针。对应 13.2 的 inode 在内存中的形态。
```

```
dentry（目录项，struct dentry）：代表"路径中的一个名字组件"，把一个名字（如 "orig.txt"）关联到一个 inode，是路径解析的节点。
```

```
file（打开文件对象，struct file）：代表"一个进程对某文件的一次打开"，持有读写偏移 offset 和访问模式——正是 13.1.2 里"打开文件表项"在 Linux 内核中的实体。
```

四者的关系可以顺着一次文件访问串起来：superblock 是"这块盘上的文件系统"，其中每个文件是一个 inode；用户给的是路径字符串，内核沿路径把每一段名字解析成 dentry、每个 dentry 连到一个 inode；进程 open 成功后得到一个 file 对象（fd 表项就指向它），file 里记着这次打开的 offset。

superblock ≈ "整个仓库"，inode ≈ "货物本身及其台账"，dentry ≈ "货架上的标签（名字）"，file ≈ "某人手里正在用的这件货的借用单（含读到哪了）"。dentry 单独抽出来是有原因的——路径解析（把 `/a/b/c` 一段段查下去）非常频繁且慢，内核用 dentry cache（dcache）把"名字→inode"的解析结果缓存起来，避免每次都重新查目录。

### 13.3.3 操作函数表：多态是怎么实现的

VFS 的"接口"落到 C 语言里，是每个对象里挂着一张**函数指针表**（operations 结构）：inode 有 `inode_operations`，file 有 `file_operations`（含 `.read`/`.write`/`.open` 等），superblock 有 `super_operations`。具体文件系统在注册时把这些指针填成自己的实现。当系统调用 `read(fd,...)` 进内核，VFS 找到该 fd 对应的 file 对象，调用 `file->f_op->read(...)`——到底跑 ext4 的读还是 tmpfs 的读，由这张表在运行时决定。

对初学者，这就是用 C 手工实现的**多态/虚函数表**：没有 class 和 virtual 关键字，但"同一个 read 调用，落到不同文件系统的不同函数"的效果一模一样。理解这一点，就理解了为什么给 Linux 加一个新文件系统，只需实现这几张 operations 表、把它注册进 VFS，上层应用一行代码都不用改。这也是"一切皆文件"能统一到设备、管道、socket 的技术底座——它们各自提供自己的 file_operations。

#### 来源与时效
- Linux 内核文档 `Documentation/filesystems/vfs.rst`（torvalds/linux master，核实 2026-07-29）：VFS 定位、superblock/inode/dentry/file 四对象、各 operations 函数指针表、dcache。
- OSTEP Ch39 / CS162 L18：以"统一文件接口 / 一切皆文件"介绍抽象层动机，与 VFS 一致（OSTEP 未用 "VFS" 术语，讲的是同一层抽象）。
- 本机实证（Linux 6.18.5 @2026-07-29）：`cat /proc/filesystems` 见上文；`mount` 输出显示 proc/sysfs/tmpfs/devtmpfs/cgroup2 等多种 VFS 挂载实例。
- 交叉一致。"VFS/四对象/operations 表"是 Linux 内核实现术语；POSIX 只规定用户可见接口不规定 VFS 内部结构，属规范 vs 实现分账，本节四对象命名以 Linux 内核为准。

## 13.4 硬链接 vs 符号链接

### 13.4.1 目录只是"名字→inode 号"的映射

在讲两种链接前先立住一个前提：**目录本身也是一种文件**，它的"内容"是一张表，每个表项把一个文件名映射到一个 inode 号（这样的表项就叫"链接"或目录项 directory entry）。所以"文件名"从来不住在文件里，而住在目录里；一个 inode 可以被多个目录项指向，也就是可以有多个名字。

理解了这一点，两种链接就都好懂了：**硬链接**是在某个目录里再加一条"名字→同一个 inode 号"的映射；**符号链接**则是新建一个独立的小文件，其内容是一段路径字符串。前者共享 inode，后者存路径。

### 13.4.2 硬链接：给同一个 inode 再起一个名字

硬链接（hard link，`ln 源 新名` 或 `link(2)`）不复制文件，只是在目录里新增一个指向**同一 inode**的目录项。于是两个名字完全平等地指向同一份数据与元数据，inode 里的**链接计数**（st_nlink）加 1。删除（`unlink`）某个名字只是去掉一条目录项、把链接计数减 1；只有当链接计数降到 0（且没有进程还打开着它）时，inode 和数据块才真正被回收。

本机实测最能说明问题。对 `orig.txt` 做 `ln orig.txt hard.txt` 后：

```
1884582 -rw-r--r-- 2 root root 12 ... hard.txt
1884582 -rw-r--r-- 2 root root 12 ... orig.txt
```

两行**inode 号相同**（都是 `1884582`），且链接计数那一列都是 `2`（`stat` 里的 Links、`ls -l` 第二列）。这证明 orig.txt 和 hard.txt 不是两份拷贝、而是同一个 inode 的两个名字：改其中一个的内容，另一个立刻"看到"，因为根本是同一份数据。

初学者要记住硬链接的两条硬限制，它们都源于"共享 inode"这个本质：其一，**不能跨文件系统**——inode 号只在本文件系统内唯一，A 盘的目录项没法指向 B 盘的 inode。其二，**通常不能对目录做硬链接**（普通用户被禁止），否则容易造出目录环、破坏文件树的无环结构、让 `..` 变得二义。链接计数正是靠它，"删一个名字不等于删文件"才成立——这也是为什么误删一个硬链接名，只要还有别的名字在，数据就还在。

### 13.4.3 符号链接：内容是一段路径的小文件

符号链接（symbolic link / soft link，`ln -s 目标 名字` 或 `symlink(2)`）是一个**独立的文件**，有自己的 inode，文件类型是"符号链接"，其**内容就是目标的路径字符串**。访问它时，内核发现是符号链接，就取出里面的路径、转去解析那个路径（这叫"跟随链接"，follow）。

本机 `ln -s orig.txt sym.txt` 后：

```
1884583 lrwxrwxrwx 1 root root 8 ... sym.txt -> orig.txt
```

inode 号是 `1884583`，**和 orig.txt（1884582）不同**——它是独立文件；开头类型位是 `l`（symbolic link）；大小是 `8`，恰好是目标路径字符串 `"orig.txt"` 的 8 个字符长度（存的是路径，不是目标内容）。

符号链接因此能突破硬链接的两条限制：它可以**跨文件系统**、也可以**指向目录**，因为它存的只是一段路径、和目标 inode 无关。代价是它更"脆"：如果目标被删除或改名，符号链接就变成**悬空链接**（dangling link），跟随时报 `ENOENT`（No such file or directory）——因为它只记了路径、不持有目标，目标没了它也不知道。硬链接则相反，只要还有一个硬链接名在，数据就不会丢。另一个初学者常踩的点：符号链接可以指向一个**当前不存在**的路径（创建时不检查目标存在），而硬链接创建时目标必须存在。

### 13.4.4 两者对照速记

把区别集中成一张对照，便于记忆：

```
硬链接：同一 inode 多个名字；共享元数据与数据；nlink 计数 +1；不能跨文件系统；一般不能链目录；删名只减计数、计数为 0 才回收。
```

```
符号链接：独立 inode 的小文件；内容是目标路径字符串；可跨文件系统、可指向目录、可指向不存在的目标；目标消失即悬空；跟随时按路径重新解析。
```

硬链接指向"文件的真身（inode）"，符号链接指向"文件的名字（路径）"。前者与目标同生死（靠计数），后者只认路径、目标存亡与它无关。

#### 来源与时效
- OSTEP Ch39（网页版，核实 2026-07-25）：目录=名字→inode 映射、硬链接共享 inode 与链接计数、unlink 减计数、符号链接是存路径的独立文件类型、悬空链接、跨文件系统/目录限制。
- Linux man-pages `link(2)`/`symlink(2)`/`unlink(2)`/`ln(1)`/`symlink(7)`（man-pages 6.x，核实 2026-07-29）：硬链接不能跨文件系统、`link()` 对目录返回 EPERM、符号链接可悬空、内容为路径字符串。
- 本机实证（Linux 6.18.5 @2026-07-29）：`ln`/`ln -s` + `ls -li` 见上文——硬链接同 inode 号且 nlink=2、符号链接异 inode 号且类型 `l`、大小=路径长度 8。
- 交叉一致，无冲突。"通常不能硬链目录"为主流实现（Linux/ext4）行为，POSIX 允许实现层面留有余地（历史上超级用户可为之），本报告按 Linux 默认（普通用户 EPERM）陈述。

## 13.5 页缓存与 fsync

### 13.5.1 write 不等于落盘：页缓存与缓冲写

用户调用 `write()` 成功返回，**并不代表数据已经写到磁盘**。绝大多数情况下，内核只是把数据拷进内存里的**页缓存**（page cache）——一块用空闲内存缓存文件数据的区域——就立即返回；真正写到磁盘由内核稍后异步完成。这叫**缓冲写**或写回（write-back）。读也一样：`read()` 命中页缓存就直接从内存返回，不必碰磁盘。

这样做的原因是磁盘（哪怕 SSD）比内存慢几个数量级。把写攒在内存里有三大好处：写调用立即返回（延迟低）；多次小写可以在内存里合并成一次大写（减少 I/O 次数）；反复读同一文件命中缓存（省掉重复磁盘读）。本机 `/proc/meminfo` 里 `Cached` 就是页缓存占用量，实测：

```
Cached:  641184 kB
```

即约 626 MB 内存正被用作文件数据缓存。这块内存是"可回收"的——一旦程序需要内存，内核会回收干净的缓存页。对初学者，心智模型是"文件的内容在内存里有一份缓存，读写先走内存、内存与磁盘之间由内核异步同步"。

### 13.5.2 脏页与回写

被 write 修改过、但还没同步到磁盘的页缓存页叫**脏页**（dirty page）。内核有专门的回写机制（writeback，历史上是 pdflush，现代 Linux 是 per-bdi 的 writeback 内核线程）在后台把脏页刷到磁盘，触发时机包括：脏页比例/存活时间超过阈值、内存紧张、或用户显式调用 `sync`/`fsync`。本机 `/proc/meminfo` 里能看到脏页与正在回写的量：

```
Dirty:      432 kB
Writeback:    0 kB
```

`Dirty` 是等待写回的脏页量，`Writeback` 是此刻正在写往磁盘的量。控制回写激进程度的内核参数（如 `vm.dirty_ratio`、`vm.dirty_background_ratio`、`vm.dirty_expire_centisecs`）在 `/proc/sys/vm/` 下，其**默认数值随内核版本与发行版而变**，本报告不写死具体值（标"待核"，以本机 `sysctl` 为准），只讲机制。

对初学者，这里埋着一个重要风险：write 返回成功、数据却还在脏页里没落盘时，如果**突然断电或系统崩溃，这部分数据会丢**。日常这没问题（性能优先），但数据库、文件系统日志这类"必须确保落盘"的场景，就需要下面的 fsync 显式把脏页刷下去。

### 13.5.3 fsync：把文件数据强制刷到稳定存储

`fsync()` 把指定 fd 对应文件的所有脏页（数据 + 相关元数据）刷写到底层存储设备，并**在数据真正落到稳定存储后才返回**。签名与其"轻量表亲"各占一行：

```c
int fsync(int fd);
```

```c
int fdatasync(int fd);   // 只保证数据(及影响读取所必需的元数据)落盘，可省去部分元数据同步
```

`fdatasync` 比 `fsync` 少刷一些非关键元数据（例如仅 mtime 变化而 size 不变时），因此可能更快，代价是不保证所有元数据同步。还有一个 `sync()`（以及命令 `sync`）——它请求把**整个系统**的所有脏页回写，粒度粗、不针对单个文件。

初学者要抓住 fsync 的定位：它是"持久性（durability）"的开关。程序若要保证"我 write 的数据在返回后一定不会因崩溃而丢"，就必须在 write 之后调用 fsync（或 fdatasync）并检查其返回值。本机 strace 能看到 fsync 作为一次独立系统调用发出：

```
fsync(3)  = 0
```

两个常见陷阱值得记住。其一，**只 fsync 文件不够**：如果你刚创建一个新文件，还需要对其**父目录**也 fsync，才能保证"这个新文件名"这条目录项也落盘，否则崩溃后文件可能存在但目录里找不到它。其二，历史上 Linux 曾有 fsync 出错后错误状态被清除、下次 fsync 却返回成功的问题（"fsync 错误处理"话题，2018 年前后修订），现代内核已改进，但正确写法始终是**检查 fsync 返回值并把出错当数据可能已丢**来处理。

### 13.5.4 mmap：把文件映射进地址空间

`mmap()` 把一个文件（或其一段）直接映射到进程的虚拟地址空间，之后**像访问内存一样访问文件内容**——读该内存区就是读文件、写该内存区就是改文件，不再显式调用 read/write。签名（文件映射常用形式）单独成行：

```c
void *addr = mmap(NULL, length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, offset);
```

它与页缓存是同一套底层：映射区的页缺页时由内核从页缓存（或磁盘）填入；对 `MAP_SHARED` 映射的写会进入页缓存、成为脏页，最终由回写或 `msync()` 落盘。`MAP_PRIVATE` 则是写时复制（COW）的私有映射，改动不写回原文件。

对初学者，mmap 的价值是"省掉一次拷贝和显式读写"：普通 read 要把数据从页缓存拷到你的用户缓冲区，mmap 让你直接读页缓存本身。它常用于随机访问大文件、加载可执行文件与共享库（程序运行时代码段就是 mmap 进来的）、以及进程间通过 `MAP_SHARED` 共享内存。要落盘同样需要显式同步：对 mmap 写的数据，用 `msync()`（类比 fsync）确保刷到磁盘。mmap 与分页/缺页的机制归大主题09/10，本节只讲它作为"文件访问的另一种接口、与页缓存共享数据"这一面。

#### 来源与时效
- OSTEP Ch39（网页版，核实 2026-07-25）：write 缓冲、页缓存、fsync 强制落盘、创建文件后需 fsync 父目录。
- Linux man-pages `fsync(2)`/`fdatasync(2)`/`sync(2)`/`mmap(2)`/`msync(2)`（man-pages 6.x，核实 2026-07-29）：fsync 落稳定存储后返回、fdatasync 少刷元数据、父目录 fsync 需求、mmap MAP_SHARED/MAP_PRIVATE 语义、msync 落盘。
- Linux 内核文档 `Documentation/admin-guide/sysctl/vm.rst`（回写参数 dirty_ratio 等，核实 2026-07-29）：机制名与参数存在，**具体默认数值随版本，标待核**，以本机 `sysctl vm.dirty_*` 为准，不凭记忆写死。
- 本机实证（Linux 6.18.5 @2026-07-29）：`/proc/meminfo` 的 Cached=641184 kB、Dirty=432 kB、Writeback=0 kB；`strace` 见 `fsync(3)=0`。`perf`/`vmtouch` 本机**未安装（待核）**，故无页缓存命中的计数器级证据。
- 交叉一致。"write 不保证落盘、需 fsync"是规范/实现共识；回写线程实现名（pdflush→per-bdi writeback）为 Linux 演进史项，机制不变。
