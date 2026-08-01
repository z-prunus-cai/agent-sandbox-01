# L5-13·大主题2 Linux namespaces（视图隔离）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L4-01 OS（进程/文件系统/权限）、L5-13·大主题1 容器 vs VM 与隔离模型 ｜一手锚点：Linux man-pages namespaces(7)/user_namespaces(7)/unshare(1)/pid_namespaces(7)/network_namespaces(7)（man-pages 6.x 系列）、内核 sched.h CLONE_NEW* 定义、OCI Runtime Spec v1.3.0（2025-11-04）§Linux Namespaces ｜成熟度：GA（time ns 为最新成员，Linux 5.6 起）

namespace 是 Linux 内核提供的「视图隔离」原语：它把某一类**全局系统资源**（进程号、网络栈、挂载表、主机名……）包装成一份**独立副本**，让处于同一个 namespace 里的进程以为自己独占这类资源，看不到别的 namespace 里的同类资源。容器「看起来像一台独立机器」，一半靠的就是 namespaces（另一半是 cgroups 管资源额度，见本课大主题3）。

namespaces 完全是 L4-01 讲的操作系统能力——由内核实现、通过系统调用暴露，本身并不属于「容器」这个概念。容器只是把 namespaces + cgroups + 根文件系统切换（pivot_root）等 OS 原语**组合工程化**后的产物。runc 之类的容器运行时创建容器时，本质就是替你调用这些原语（回指大主题5）。

---

## 13-2.1 namespaces 总览

### namespace 是什么：全局资源的隔离副本

一个 namespace 就是内核对某一类全局资源做的一层「分身」。默认情况下全机器所有进程共享同一份全局资源（同一张进程表、同一个网络栈）；创建一个新 namespace 后，加入它的进程改用一份**新的、独立的**同类资源，对该资源的增删改只在本 namespace 内可见。

判断两个进程是否在同一个 namespace，看的是内核给每个 namespace 分配的**唯一标识（inode 号）**。每个进程在 `/proc/<pid>/ns/` 下为每类 namespace 暴露一个符号链接，链接目标形如 `net:[4026531833]`，方括号里就是该 namespace 的 inode 号；两个进程若某类链接指向同一 inode，就在同一个该类 namespace 中。

本机实测，当前 shell 的 8 类 namespace 链接（外加 `pid_for_children`、`time_for_children` 两个「子进程将加入」的占位链接）：

```
$ ls -l /proc/self/ns/
cgroup -> cgroup:[4026531835]
ipc    -> ipc:[4026531839]
mnt    -> mnt:[4026531832]
net    -> net:[4026531833]
pid    -> pid:[4026531836]
pid_for_children  -> pid:[4026531836]
time   -> time:[4026531834]
time_for_children -> time:[4026531834]
user   -> user:[4026531837]
uts    -> uts:[4026531838]
```

这里 4026531832~839 这一段是「初始 namespace」（initial/root namespace）的固定 inode 号——机器启动时内核创建的第一套 namespace，所有普通进程默认都在里面。初学者容易把 namespace 想成一个「文件夹」或「配置」，更准确的心智是：它是内核里的一个对象，`/proc/<pid>/ns/<type>` 只是它对外的一个句柄；只要还有进程或挂载/打开的文件描述符引用它，它就存活，最后一个引用消失时内核回收它。

### 八种 namespace 类型

截至基线内核 6.18.5，Linux 共有 **8 种** namespace。每种对应一个 `clone(2)`/`unshare(2)` 的 `CLONE_NEW*` 标志位，隔离一类资源：

| 类型 | CLONE 标志 | 隔离的资源 | 引入内核版本 |
|------|-----------|-----------|-------------|
| mnt（mount） | CLONE_NEWNS | 挂载点列表（文件系统视图） | 2.4.19 |
| uts | CLONE_NEWUTS | 主机名（hostname）与域名（NIS/domainname） | 2.6.19 |
| ipc | CLONE_NEWIPC | System V IPC 对象、POSIX 消息队列 | 2.6.19 |
| pid | CLONE_NEWPID | 进程 ID 号空间 | 2.6.24 |
| net（network） | CLONE_NEWNET | 网络设备、IP、端口、路由、防火墙表等整套网络栈 | 2.6.24 起，约 2.6.29 完整 |
| user | CLONE_NEWUSER | UID/GID 映射、capabilities | 3.8 完整（3.5+ 起分批） |
| cgroup | CLONE_NEWCGROUP | cgroup 根目录视图（进程看到的 cgroup 层级根） | 4.6 |
| time | CLONE_NEWTIME | 单调时钟与开机时钟（boottime）偏移 | 5.6 |

本机实测这 8 个标志确实都在内核头文件里定义（等价于「本机内核支持 8 种」）：

```
$ grep -Eo "CLONE_NEW[A-Z]+" /usr/include/linux/sched.h | sort -u
CLONE_NEWCGROUP
CLONE_NEWIPC
CLONE_NEWNET
CLONE_NEWNS
CLONE_NEWPID
CLONE_NEWTIME
CLONE_NEWUSER
CLONE_NEWUTS
```

要提醒初学者两个易错点。其一，mount namespace 的标志叫 `CLONE_NEWNS` 而不是 `CLONE_NEWMNT`——因为它是历史上第一个 namespace（2002 年，2.4.19），当时还没想到会有更多种，就用了泛化的名字 `NS`。其二，「8 种」是随内核版本演进的：time namespace 直到 2020 年的 5.6 才加入，更老的内核只有 7 种甚至更少；数字会变，判断某台机器到底支持几种，应看它 `/proc/self/ns/` 的实际链接或内核头文件，不要背死数字。⚙演进快·锚版本·随时变（锚 6.18.5，核实 2026-08-01）。

### 操作 namespace 的三个系统调用与 unshare/nsenter 工具

进程与 namespace 的关系由三个系统调用支配：

```
clone(2)   —— 创建新进程，同时把它放进若干个新 namespace（CLONE_NEW* 标志）
unshare(2) —— 让「当前」进程离开原 namespace、进入新建的 namespace
setns(2)   —— 让当前进程加入一个「已存在」的 namespace（凭 /proc/<pid>/ns/<type> 的 fd）
```

命令行上，`unshare(1)` 工具封装 `unshare(2)`（也内部用 clone 处理 pid ns 的 fork 语义），`nsenter(1)` 封装 `setns(2)`（「钻进」某个已有进程的 namespace，调试容器常用）。容器运行时创建容器时用 clone/unshare 造出一套新 namespace，`docker exec` / `kubectl exec` 进入运行中的容器时则是 setns 加入其已有的 namespace。

一个关键约束帮助建立正确心智：**pid namespace 对调用 unshare 的进程本身不生效，只对它之后 fork 出的子进程生效**。原因是一个已经在运行的进程不能中途改变自己的 PID，所以 `unshare --pid` 必须配合 `--fork`，让新的子进程成为新 pid namespace 里的 1 号进程。这正是上面 `/proc/self/ns/` 里为什么单独有 `pid_for_children`：它记录「我的下一个子进程会进入哪个 pid namespace」，与我自己所在的 `pid` 可以不同。time namespace 同理有 `time_for_children`。

本机 `lsns` 可俯瞰全机 namespace（TYPE 列即 8 种，NS 列即 inode 号，NPROCS 是成员进程数）：

```
$ lsns | head
        NS TYPE   NPROCS PID USER COMMAND
4026531832 mnt        79   1 root /process_api ...
4026531833 net        80   1 root ...
4026531834 time       80   1 root ...
4026531835 cgroup     80   1 root ...
4026531836 pid        80   1 root ...
4026531837 user       80   1 root ...
4026531838 uts        80   1 root ...
4026531839 ipc        80   1 root ...
4026531860 mnt         1  39 root kdevtmpfs
```

inode 4026531860 是一个**非初始**的 mnt namespace，只有 kdevtmpfs 一个内核线程在其中——说明本机除了初始 namespace 外确实存在别的 namespace，验证了「同类型可以有多个独立副本」这一核心事实。

#### 来源与时效

- Linux man-pages namespaces(7)（man-pages 6.x；「Namespace types」表与 `/proc/[pid]/ns/` 一节）——8 种类型、CLONE 标志、inode 标识、三系统调用的一手定义。核实 2026-08-01。
- 内核头文件 `/usr/include/linux/sched.h` 的 `CLONE_NEW*` 定义（本机 gcc13.3/内核 6.18.5）——本机实测 8 个标志齐备，与 man page 交叉印证。
- unshare(1)、nsenter(1)、lsns(8) man page（util-linux 2.39.3，本机实测版本）——命令封装关系与 `pid_for_children` 语义。
- 引入版本按 namespaces(7) 各类型说明与内核提交历史（mnt 2.4.19、time 5.6 等），time ns 为最新成员；⚙演进快·锚版本·随时变：「共 8 种」随内核演进，以本机 `/proc/self/ns/` 为准。
- 冲突/口径差异：net namespace 的「引入版本」不同来源写 2.6.24 或 2.6.29——因为它是分批合入、到 2.6.29 才功能完整；两个数字都对，指的是「开始」与「完成」两个时间点，本表已两边标注。

## 13-2.2 PID namespace 与 mount namespace

### PID namespace：进程号空间的隔离

pid namespace 给进程一套**独立的 PID 编号空间**。新建 pid namespace 里的第一个进程 PID 为 1，扮演该 namespace 的「init」；namespace 内的进程只能看到并用 kill 影响本 namespace 及其子 namespace 的进程，看不到宿主或兄弟 namespace 的进程。这就是「容器里 `ps` 只看得到自己那几个进程、且主进程 PID 是 1」的机制根。

本机实测（须 `--fork`，见 13-2.1 的约束；`--mount-proc` 让容器内的 `/proc` 反映新 pid namespace）：

```
$ unshare --pid --mount --fork --mount-proc sh -c 'echo "PID=$$"; ps -e -o pid,comm'
PID=1
    PID COMMAND
      1 sh
      2 ps
```

shell 的 `$$` 是 1，`ps` 只列出 namespace 内的两三个进程——外面那 80 个进程完全不可见。

一个重要的双向不对称，初学者务必记住：**同一个进程在不同 namespace 里有不同的 PID**。容器里 PID 为 1 的进程，在宿主的初始 pid namespace 里是另一个较大的 PID，宿主仍能看到并管理它。pid namespace 是**有层级**的（可嵌套，最多 32 层），父 namespace 能看到子 namespace 的全部进程，反之不行——这与 net/uts 等「平级、互不可见」的 namespace 不同。另一个易错点：pid namespace 里的 1 号进程享有和真正 init 一样的「特殊待遇」——它若退出，内核会杀死该 namespace 内所有其他进程；且它默认不会收到自己没装处理函数的信号。这解释了为什么容器主进程需要正确处理 SIGTERM，否则 `docker stop` 可能要等超时后被 SIGKILL 强杀。

### mount namespace：挂载点视图的隔离

mount namespace 隔离的是进程看到的**挂载点列表**——也就是「哪个文件系统挂在哪个目录上」这张表。在新 mount namespace 里挂载/卸载文件系统，默认只在本 namespace 内可见，不影响宿主。这让每个容器能拥有自己的根文件系统和 `/proc`、`/sys`、`/tmp` 等挂载，互不干扰。

上面 PID 实测里的 `--mount --mount-proc` 正是配套：如果不新建 mount namespace 就 `mount -t proc proc /proc`，会污染宿主的 `/proc`；有了 mount namespace，这次挂载被关在容器内，`ps` 读到的才是新 pid namespace 的进程列表。这也顺带说明 PID 与 mount 两个 namespace 常常搭配使用——光隔离 PID 号不够，还得隔离 `/proc` 这个「进程信息的挂载视图」，容器里看到的进程表才自洽。

易错点在于「挂载传播（mount propagation）」：mount namespace 并非默认完全隔离。挂载点有 shared/private/slave/unbindable 几种传播类型，若某挂载是 shared，新 namespace 里对它的操作可能**传播回**宿主或其他 namespace。容器运行时通常会把根挂载设为 `rprivate`（递归私有）来杜绝泄漏。初学者只需先记住结论：mount namespace 给你独立的挂载视图，但「有多独立」取决于挂载传播设置，不是无脑全隔离。另外，mount namespace 只隔离「挂载点」，不等于隔离「文件内容」——容器能看到什么文件，取决于它的根文件系统（pivot_root/chroot 换根，见大主题5），mount namespace 负责的是让这次换根和后续挂载对外不可见。

#### 来源与时效

- Linux man-pages pid_namespaces(7)——PID 1 特殊语义、层级嵌套、`/proc` 与 `--mount-proc` 关系、信号语义。核实 2026-08-01。
- Linux man-pages mount_namespaces(7)——挂载点隔离、挂载传播（shared/private/slave）语义。核实 2026-08-01。
- unshare(1) man page（util-linux 2.39.3）——`--pid --fork --mount --mount-proc` 各选项，与本机实测输出交叉印证（$$=1、ps 仅见 namespace 内进程）。
- 本机实测（内核 6.18.5）：`unshare --pid --mount --fork --mount-proc` 复现容器内 PID 1 与隔离的进程视图。
- 冲突/口径差异：pid namespace 最大嵌套层数常引作 32（内核 `MAX_PID_NS_LEVEL`），此为编译期常量、不同配置理论上可变，属实现细节，标「以内核配置为准」。

## 13-2.3 network namespace

### network namespace：整套网络栈的隔离

network namespace 隔离的是**一整套网络栈**——网络设备（网卡）、IPv4/IPv6 地址、路由表、端口号空间、防火墙（netfilter/iptables）规则、`/proc/net` 与 `/sys/class/net` 等，全部独立。新建的 network namespace 里初始只有一个**未启用的回环设备 lo**，没有任何真实网卡，因此默认「与世隔绝」。这就是「每个 Pod 有自己独立的 IP 和端口空间」的机制根（回指大主题9 K8s 网络：K8s「每 Pod 一 IP」正是每个 Pod 一个 network namespace）。

本机实测，新 network namespace 里只有 lo、且外部无真实接口：

```
$ unshare --net sh -c 'cat /proc/net/dev'
Inter-|   Receive ...                        |  Transmit ...
 face |bytes    packets ...                   |bytes    packets ...
    lo:       0       0 ...                    0       0 ...
```

只有一行 `lo`，一个真实网卡都没有——两个不同容器都能各自监听「同一个」80 端口而不冲突，就是因为端口空间隶属各自的 network namespace。

既然新 namespace 只有孤立的 lo，容器怎么联网？答案是**虚拟网络设备对（veth pair）**——像一根虚拟网线，两端分别插在宿主 namespace 和容器 namespace，配上 IP 和路由后容器就能通过宿主转发/桥接上网。这正是 Docker 的 `docker0` 网桥、K8s 的 CNI 插件在底层做的事：为容器建 network namespace、造 veth、接网桥、配路由。

工具上有两条常见路径，效果都是操作 network namespace，易错点是别把它们当成两种不同的隔离机制：

```
unshare --net <cmd>        # 起一个新进程，放进匿名的新 network namespace
ip netns add <name>        # 创建一个「有名字」的持久 network namespace（挂在 /run/netns/<name>）
ip netns exec <name> <cmd> # 在该命名 namespace 里运行命令
```

区别只是「匿名、随进程消亡」还是「命名、可持久存在供反复进入」，底层同为 network namespace。另一个易错点：想让容器有网，不是把宿主网卡「共享」进去，而是**移动或新建**设备——一块物理网卡在同一时刻只能属于一个 network namespace，把它 `ip link set <dev> netns <ns>` 移进容器后，宿主就看不到它了；所以真实部署几乎都用 veth 虚拟对而非搬物理网卡。

#### 来源与时效

- Linux man-pages network_namespaces(7)——隔离范围（设备/地址/路由/端口/防火墙/`/proc/net`）、新 namespace 仅含未启用 lo。核实 2026-08-01。
- Linux man-pages ip-netns(8)（iproute2）——命名 network namespace 与 `/run/netns`、`ip netns exec`、veth 移动语义。核实 2026-08-01。
- 本机实测（内核 6.18.5，util-linux 2.39.3）：`unshare --net` 后 `/proc/net/dev` 仅见 lo，印证「只含孤立回环」。
- Kubernetes 官方文档（Cluster Networking，⚙演进快·锚版本 v1.36）——「每 Pod 一 IP」与 Pod 内容器共享同一 network namespace 的模型；作机制用途回指，不承重底层定义。
- 冲突/口径差异：无实质冲突；`unshare --net`（匿名）与 `ip netns`（命名持久）是同一机制的两种句柄，非两套机制。

## 13-2.4 user namespace 与 rootless

### user namespace：UID/GID 映射与非特权容器 root

user namespace 隔离的是**用户与组 ID、以及 capabilities（内核细分的特权）**。它的杀手锏能力是 UID/GID **映射**：容器内的 uid 0（root）可以映射到宿主上的一个**普通非特权 uid**。于是进程「在容器内是 root、拥有容器内的全部 capabilities」，但「在宿主看来只是个普通用户」，逃逸后能造成的破坏被大幅收窄。这是 **rootless 容器**（无需真正 root 即可跑容器）的基石，也是纵深防御的关键一环（回指大主题12 容器安全）。

映射关系记录在 `/proc/<pid>/uid_map` 与 `gid_map`，每行三列：

```
容器内起始ID   宿主上起始ID   映射长度
```

本机实测（`--map-root-user` 把容器内 root 映射到调用者）：

```
$ unshare --user --map-root-user sh -c 'id -u; cat /proc/self/uid_map'
0
         0          0          1
```

进程在新 user namespace 内 `id -u` 报 0（root），`uid_map` 的一行 `0 0 1` 表示「容器内 uid 0 ← 映射到 → 宿主 uid 0，共映射 1 个」。这里要如实说明本机特例：本会话本就以 root（uid 0）运行，所以映射目标恰好是 0。若由一个**普通用户**（如 uid 1000）执行同一命令，这一行会是 `0 1000 1`——容器内 root 实际是宿主的 1000，这才是 rootless 的典型形态。本机因是 root，无法就地复现「非特权→容器 root」的对比，此处以映射语义与 user_namespaces(7) 交叉说明，实测仅坐实映射机制本身。

user namespace 是**唯一可以由非特权进程创建**的 namespace（在多数发行版默认开启 `unprivileged_userns_clone` 的前提下）。其余 7 种 namespace 通常需要 `CAP_SYS_ADMIN`；而一旦你先建了 user namespace 并在其中成为「root」，你就在这个 user namespace 内**获得了全套 capabilities**，从而能接着创建其它 namespace。rootless 容器正是走这条路：先开 user namespace 拿到「本地 root」，再用这身份去建 pid/net/mnt 等 namespace。

易错点有三。其一，**「容器 root ≠ 宿主 root」**：容器内 root 的 capabilities 只在本 user namespace 及其管辖的资源内有效，对宿主拥有的、未映射进来的资源无权。其二，容器内看到的**未映射 UID 显示为 65534（nobody/overflow uid）**——文件属主的 uid 若不在映射范围内，进程会看到一个占位的 overflow id，这常让初学者困惑「我明明是 root 怎么访问不了这个文件」。其三，配置映射本身受限：非特权地建立超过一行的映射，通常要借助 setuid 的 `newuidmap`/`newgidmap`（依赖 `/etc/subuid`、`/etc/subgid` 配额），`--map-root-user` 只是「单行、映射自己」的便捷特例。

#### 来源与时效

- Linux man-pages user_namespaces(7)——UID/GID 映射（`uid_map`/`gid_map` 三列语义）、user namespace 内的 capabilities、overflow uid（65534）、可被非特权进程创建。核实 2026-08-01。
- Linux man-pages subuid(5)/subgid(5) 与 newuidmap(1)/newgidmap(1)——多行映射的配额与设权工具。核实 2026-08-01。
- 本机实测（内核 6.18.5）：`unshare --user --map-root-user` 得容器内 `id -u=0` 与 `uid_map: 0 0 1`；本机为 root，故目标为 0，非特权场景（映射到调用者真实 uid）以规范语义说明、未就地复现，如实标注。
- OCI Runtime Spec v1.3.0（2025-11-04）§Linux Namespaces / uidMappings、gidMappings——容器化落地中映射字段的一手佐证。
- 冲突/口径差异：不同发行版对「非特权创建 user namespace」默认是否放开不一致（历史上 Debian/RHEL 曾默认关闭 `kernel.unprivileged_userns_clone` 或用其他开关），属发行版策略而非内核语义差异；本机实测可创建，标「随发行版/内核参数而变」。

## 13-2.5 UTS、IPC、cgroup、time namespace

### UTS namespace：主机名与域名隔离

UTS namespace 隔离两个标识符：**主机名（hostname）** 与 **NIS 域名（domainname）**。（UTS = UNIX Time-sharing System，取自 `uname()` 返回的 `utsname` 结构，名字是历史遗留，别被误导以为和「时间」有关。）它让每个容器能有自己的主机名而不影响宿主。

本机实测，容器内改主机名对外不可见：

```
$ unshare --uts sh -c 'hostname nsdemo-host; hostname'
nsdemo-host
$ hostname       # 宿主
vm
```

容器内看到 `nsdemo-host`，宿主仍是 `vm`。这个 namespace 最简单，是理解「视图隔离」的最佳入门例子：改的只是「这台机器叫什么」这个字符串的可见副本。

### IPC namespace：System V IPC 与 POSIX 消息队列隔离

IPC namespace 隔离**进程间通信对象**：System V 的消息队列、信号量、共享内存段，以及 POSIX 消息队列。不同 IPC namespace 里的这些对象互不可见，避免容器间通过共享内存/信号量意外互相干扰。

本机实测，宿主建的消息队列在新 IPC namespace 内消失：

```
$ ipcmk -Q                     # 宿主建一个消息队列
Message queue id: 0
$ ipcs -q                      # 宿主可见
key        msqid  ...
0x220b4438 0      ...
$ unshare --ipc sh -c 'ipcs -q'   # 新 IPC namespace 内
------ Message Queues --------
key        msqid  ...           # 空，看不到宿主那个队列
```

新 namespace 里消息队列列表为空，印证 IPC 对象随 namespace 隔离。易错点：IPC namespace **不**隔离基于文件/socket 的通信（Unix domain socket、管道、共享文件走的是 mount/net namespace 或文件权限），它只管 SysV IPC 和 POSIX mqueue 这一类「内核对象型」IPC。

### cgroup namespace：cgroup 根视图隔离

cgroup namespace 隔离的是进程看到的 **cgroup 层级的「根」**——即 `/proc/<pid>/cgroup` 里显示的路径和 `/sys/fs/cgroup` 挂载所呈现的根位置。它让容器内进程看到的自己的 cgroup 路径是相对「容器根」的，而**看不到宿主完整的 cgroup 树**，避免暴露宿主的 cgroup 布局。

要分清两个不同概念（初学者极易混淆）：**cgroups 本身**是资源限额机制（CPU、内存额度，是本课大主题3 的主题）；**cgroup namespace** 只是给这套机制的**目录视图**加一层隔离，本身不设置任何限额。换句话说，cgroups 管「你能用多少资源」，cgroup namespace 管「你能看到这棵 cgroup 树的哪一段」。它是 8 种里第二新的（4.6 引入），目的就是让容器里的 cgroup 视图不泄漏宿主信息。

### time namespace：时钟偏移隔离

time namespace 允许为一组进程设置**独立的时钟偏移**，作用于两个时钟：`CLOCK_MONOTONIC`（单调时钟）与 `CLOCK_BOOTTIME`（含休眠的开机时钟）。它是最年轻的 namespace（Linux 5.6，2020）。

本机实测，给 boottime 加 3600 秒偏移：

```
$ unshare --time --boottime 3600 sh -c 'cat /proc/self/timens_offsets'
monotonic           0         0
boottime         3600         0
```

`timens_offsets` 两行分别是 monotonic 与 boottime 的偏移（秒、纳秒），这里 boottime 被抬高了 3600 秒。它的典型用途是容器/CRIU 迁移后让容器内的「开机以来时间」保持连续。

**time namespace 不隔离「墙上时钟」（`CLOCK_REALTIME`，即年月日那个当前时间）**。全机器共享同一个 wall-clock，容器**不能**把自己的日期设成和宿主不同的年份。它只能偏移 monotonic/boottime 这类「相对流逝时间」。很多初学者误以为「time namespace = 容器有独立日期时钟」，这是错的。

综观这四种：UTS/IPC 是最早一批（2.6.19）、语义简单；cgroup（4.6）、time（5.6）是后来补的、用途更专门。它们和 PID/mount/net/user 一起，构成容器「像独立机器」的完整视图幻觉——而这一切都是 L4-01 讲的操作系统内核能力，容器只是把它们组合起来用。

#### 来源与时效

- Linux man-pages uts_namespaces(7)、ipc_namespaces(7)、cgroup_namespaces(7)、time_namespaces(7)——各自隔离范围的一手定义（UTS=hostname/domainname；IPC=SysV IPC+POSIX mqueue；cgroup=根视图；time=monotonic/boottime 偏移且不含 realtime）。核实 2026-08-01。
- 本机实测（内核 6.18.5，util-linux 2.39.3）：UTS 改名隔离、IPC 消息队列隔离、time `--boottime 3600` 偏移，均已就地复现并贴真实输出。
- Linux man-pages namespaces(7) 类型表——cgroup ns 4.6、time ns 5.6 的引入版本；⚙演进快·锚版本·随时变（锚 6.18.5）。
- 冲突/口径差异：无实质冲突。需强调的分账是「cgroups（限额机制，大主题3）vs cgroup namespace（视图隔离，本节）」为两个不同对象，勿混为一谈；time namespace 明确不含 `CLOCK_REALTIME`，与「容器可任意设日期」的常见误解相反。
