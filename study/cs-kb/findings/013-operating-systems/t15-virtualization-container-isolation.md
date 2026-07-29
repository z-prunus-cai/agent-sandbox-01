# L4-01·大主题15 虚拟化与容器隔离

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-29 ｜ 先修：本课大主题01（OS 作为资源虚拟化器、双模式/特权级、受保护指令）、大主题03（中断/陷入/受限直接执行）、大主题09（地址翻译与多级页表、TLB）、大主题05（CPU 调度——cgroup 的 CPU 限额建在调度器之上）｜ 一手锚点：MIT 6.1810 2024 Fall「Virtual Machines」「Meltdown」讲义（pdos.csail.mit.edu/6.1810，核实 2026-07-25）；OSTEP 网页版 附录/VMM 概念（pages.cs.wisc.edu/~remzi/OSTEP/，核实 2026-07-25）；Linux man-pages `namespaces(7)`、`cgroups(7)`、`user_namespaces(7)`、`clone(2)`、`unshare(2)`、`setns(2)`（man7.org，核实 2026-07-29）；Linux 内核官方文档 admin-guide/cgroup-v2（docs.kernel.org，核实 2026-07-29）；Intel SDM Vol.3C「VMX」/ AMD64 APM Vol.2「SVM」（机制命名 EPT/NPT，低层细节标「待核」）；Meltdown（Lipp et al. 2018）、Spectre（Kocher et al. 2019）原始论文 ｜ 成熟度：硬件虚拟化（VT-x/AMD-V）、二级地址翻译（EPT/NPT）、namespaces 均 GA/稳定；**⚙演进快·锚版本**：cgroup **v2**（unified hierarchy，锚 Linux 6.18.5 / kernel.org 现行文档 @2026-07-29，容器运行时默认逐步全面转 v2，随发行版与内核版本变）；推测执行漏洞缓解现状随微码/内核持续变动，硬标日期

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（15.1–15.5）沿一条清晰主线递进——"如何把一台机器骗成多台（15.1 硬件虚拟化）→ 虚拟机的内存怎么翻译两层（15.2 内存虚拟化）→ 比虚拟机更轻的隔离怎么做（15.3 namespaces 切视图 + 15.4 cgroups 切资源）→ 这些隔离在推测执行面前为何会漏、怎么补（15.5 侧信道）"。五节篇幅适中、互为前后依赖，按 report-format v3 §一默认 1 大主题 = 1 报告，不拆 `-a/-b`。

> 本报告一条主线心智模型：**隔离有两个层次，代价与强度成反比**。虚拟机（VM）在硬件层复制一整台机器——每个客户机跑自己的完整内核，靠 CPU 的 VMX/SVM 扩展做"陷入并模拟"、靠 EPT/NPT 做两层地址翻译，隔离最强但每台都背一个内核、开销大。容器不复制机器，而是让所有容器**共享同一个宿主内核**，靠 Linux 的两组内核机制拼出"看起来像独占一台机器"的错觉：**namespaces 负责"看得见什么"**（切割进程号、挂载点、网络栈等命名视图），**cgroups 负责"能用多少"**（限制并计量 CPU/内存/IO）。容器轻快，但共享内核意味着内核一旦被侧信道击穿（15.5），隔离就可能漏。

> 下游边界（本报告只在交界处一句指路）：本报告讲**内核提供的隔离原语**（hypervisor 陷入模拟、EPT/NPT、namespaces、cgroups）；**Docker/containerd/runc 容器运行时、OCI 镜像、Kubernetes 编排** 归 L5-08「容器与云原生」，本课不深挖，只说"容器 = namespaces + cgroups + 根文件系统切换（pivot_root）+ 能力/seccomp 限制，这几块内核积木被运行时组装起来"。

> 本机实证（可选、已取）：namespace 视图 `lsns`、`ls -l /proc/self/ns`、`unshare` 造新 PID namespace；cgroup 层级 `/sys/fs/cgroup` 与 `/proc/mounts`；CPU 侧信道现状 `/sys/devices/system/cpu/vulnerabilities/*` 均在本机真跑：Linux 6.18.5 x86_64 @2026-07-29，真实输出贴入正文。**重要如实标注**：本机是 Firecracker/QEMU 类**客户机**（`hypervisor` flag 存在、`/proc/cpuinfo` 不暴露 `vmx`/`svm`），故 15.1/15.2 的硬件虚拟化不能在本机内部再嵌套演示；且本机 cgroup 为 **v1 混合布局**（legacy 各控制器独立挂载 + 一个只委派了 cpuset/hugetlb 的 `unified` cgroup2 挂载），并非纯 v2，故 15.4 的 `memory.max`/`cpu.max` 本机不存在，相应处以文档为准并标注。实证是多来源比对的补充，不替代比对。

---

## 15.1 硬件虚拟化

### 15.1.1 虚拟机监控器（VMM/hypervisor）与"陷入并模拟"的基本思想

硬件虚拟化要解决的问题是：让一台物理机上**同时**跑多个各自以为独占整机的操作系统（客户机 OS，guest OS）。做这件事的软件叫**虚拟机监控器（Virtual Machine Monitor, VMM）**，也叫 **hypervisor**。经典教材（OSTEP 附录、6.1810「Virtual Machines」）给出的核心机制是 **trap-and-emulate（陷入并模拟）**：让客户机 OS 运行在**降权**的模式下（它以为自己在内核态 ring 0，实际被放在权限更低的位置），当它执行一条**敏感指令**（想读写硬件、改页表、开关中断这类只有真正内核才能干的操作）时，CPU 自动**陷入（trap）** 到 VMM；VMM 在真正的特权级上**模拟（emulate）** 出这条指令"本应产生的效果"，再把控制权交还客户机。客户机全程感觉不到自己被降了权。

对初学者，最贴切的类比是"沙盒里的国王"：客户机 OS 自以为是国王（能发号施令操作硬件），但它其实住在 VMM 搭的沙盒里；每当它想真的动硬件，动作就被沙盒拦下、由 VMM 这个"真国王"代为执行一个安全的等效版本。为什么非要"陷入"？因为直接让客户机碰真硬件，多个客户机就会互相踩踏、也无法隔离；而每条指令都软件解释又太慢。trap-and-emulate 是折中：**绝大多数普通指令（算术、访存、跳转）让 CPU 原生全速直跑，只有少数敏感指令才陷入让 VMM 接管**——这就是"受限直接执行"思想（大主题03）在整机层面的翻版。

### 15.1.2 Popek-Goldberg 判据与 x86 的经典"不可虚拟化"问题

trap-and-emulate 能不能干净地工作，有个经典判据：**Popek 与 Goldberg（1974）** 证明，一个体系结构可被高效虚拟化的**充分条件**是——所有**敏感指令（sensitive，会影响或依赖机器全局状态/特权配置的指令）都必须是特权指令（privileged，在非特权模式执行就会陷入）**。只要满足这点，把客户机降权跑，敏感指令就都会自动陷入到 VMM，VMM 才有机会接管。

问题在于早期 **x86 违反了这个判据**：它有一批"敏感但不特权"的指令（经典说法是 17 条，如 `POPF`——在用户态执行时会**静默丢弃**对中断标志位的修改而不报错、以及 `SGDT`/`SIDT`/`SMSW` 等能读出真实硬件状态的指令）。这些指令在降权的客户机里执行**既不陷入、又暴露/破坏了真实状态**，VMM 根本没机会拦截，trap-and-emulate 直接失效。历史上因此出现两条绕路：**二进制翻译（binary translation，VMware 早期用）** 在运行时改写客户机代码把这些坏指令替换掉；**半虚拟化（paravirtualization，Xen 早期用）** 修改客户机内核源码，把敏感操作改成显式调用 VMM 的 hypercall。两者都能用但都别扭。真正的干净解法是 **Intel VT-x（VMX 扩展，2005）** 和 **AMD-V（SVM 扩展）**：CPU 新增一个**根模式/非根模式**的正交维度，客户机跑在"VMX 非根模式"里，敏感操作可配置为陷入到"VMX 根模式"的 VMM（一次陷入叫 **VM-exit**，返回叫 **VM-entry**），于是 x86 重新变得可被 trap-and-emulate 干净虚拟化。

初学者记住一句话即可：**"x86 曾经不能被经典 trap-and-emulate 干净虚拟化，是靠 CPU 加了 VT-x/AMD-V 硬件扩展才补上的。"** 本机 `/proc/cpuinfo` 的 flags 里**没有** `vmx`/`svm`（因为本机自己就是客户机，宿主没把嵌套虚拟化透传进来），所以本机内部无法再演示 VM-entry/exit，这一点如实标注。

### 15.1.3 trap-and-emulate 的时序

把一次敏感指令的处理拆成时序（概念示意，非某具体 ISA 逐周期）：

```
1. 客户机在降权模式全速执行普通指令 …… (CPU 原生直跑，无 VMM 介入)
2. 客户机执行一条敏感/特权指令 (如 写 CR3 换页表 / 访问设备寄存器)
3. CPU 自动陷入 → VM-exit：保存客户机状态到 VMCS/VMCB，切到 VMM(根模式)
4. VMM 读出 exit 原因，模拟该指令的等效效果 (维护该客户机的虚拟硬件状态)
5. VMM 执行 VM-entry：恢复客户机状态，把控制权交还客户机下一条指令
6. 客户机继续全速直跑，直到下一次敏感指令 …… (回到步骤 1)
```

这里 **VMCS（Intel Virtual Machine Control Structure）/ VMCB（AMD Virtual Machine Control Block）** 是一块内存结构，保存客户机与宿主的寄存器快照、以及"哪些事件该触发 VM-exit"的配置位。理解这张时序图的关键收获：**VM-exit 是昂贵的**（要保存/恢复大量状态、切模式、刷部分缓存），所以硬件虚拟化性能优化的核心目标始终是"**减少 VM-exit 次数**"——这也是 15.2 内存虚拟化非要引入 EPT/NPT 硬件二级翻译的根本动机（否则客户机每次改页表都得 VM-exit 一次，慢到不可用）。

### 15.1.4 Type-1（裸金属）与 Type-2（宿主型）hypervisor

按 VMM 跑在哪里，分两型。**Type-1（裸金属，bare-metal）** 直接跑在硬件之上、自己就是最底层的那层软件，客户机都跑在它上面——代表有 **Xen、VMware ESXi、微软 Hyper-V**。**Type-2（宿主型，hosted）** 作为一个普通程序跑在一个**已有的宿主操作系统**之上，借宿主 OS 管理真实硬件——代表有 **VMware Workstation、VirtualBox、QEMU（纯软件模式）**。

两型的取舍是初学重点。Type-1 少一层、性能与隔离更好、但要自己带驱动，多用于数据中心/云；Type-2 装起来像装个普通 App、能复用宿主的驱动和桌面，多用于开发者本机。一个必须点明的"归类争议"：**Linux 的 KVM 是个混合体**——它是宿主内核里的一个模块，把整个 Linux 内核**变成**了一个 hypervisor（借助 VT-x/AMD-V 直接跑客户机），配合用户态的 QEMU 做设备模拟。因为它既有完整宿主 OS（像 Type-2）、又让内核直接充当 hypervisor 直驱硬件虚拟化扩展（像 Type-1），学界/厂商对把它归 Type-1 还是 Type-2 并无统一说法，两种归类都能见到，**此处如实并记、不强行归一**。对初学者只需记住："KVM = Linux 内核自己当 hypervisor + QEMU 补设备模拟"这一事实，比纠结归第几型更有用。

#### 来源与时效（本小主题末集中列）
- 锚点：MIT 6.1810 2024 Fall「Virtual Machines」讲义（trap-and-emulate、VMM 概念、VT-x 陷入模拟，核实 2026-07-25）；OSTEP 网页版 VMM/虚拟机附录（VMM 作为"OS 的 OS"、机器级虚拟化，核实 2026-07-25）；Popek & Goldberg (1974)「Formal Requirements for Virtualizable Third Generation Architectures」（敏感指令 ⊆ 特权指令判据）；Intel SDM Vol.3C「VMX」/ AMD64 APM Vol.2「SVM」（VMX 根/非根模式、VMCS/VMCB、VM-entry/exit 命名——**低层字段与逐周期细节标「待核」**，未开原文逐条核对）。
- 本机实证：`/proc/cpuinfo` flags 不含 `vmx`/`svm`、含 `hypervisor` @Linux 6.18.5 2026-07-29（本机为客户机，硬件虚拟化不可本机内嵌演示，如实标注）。
- 两来源一致 / 分账：6.1810 与 OSTEP 对 trap-and-emulate 与 Type-1/2 表述一致。Type-1/2 的"教科书两分法"是通俗分类而非严格标准，KVM 归类学界无统一说法，两边都记。x86 经典不可虚拟化"17 条敏感指令"的具体条数常见于二手综述，此数字标「⚠常见说法，未逐条一手核对」。

## 15.2 内存虚拟化

### 15.2.1 两层地址翻译的问题：多了一层"客户机物理地址"

普通机器里地址翻译只有一层：**虚拟地址（VA）→ 物理地址（PA）**，由页表描述、MMU 硬件走。虚拟机里凭空多出一层：客户机 OS 有它自己的一套页表，把**客户机虚拟地址（GVA）→ 客户机物理地址（GPA）**；但"客户机物理地址"是假的——它并不是真实内存的地址，只是 VMM 分给这台客户机的一段"看起来从 0 开始的内存"。所以还需要第二步 **客户机物理地址（GPA）→ 宿主物理地址（HPA，真实内存）**，这一步由 VMM 掌握。合起来是：

```
GVA  --(客户机页表, 客户机 OS 维护)-->  GPA  --(VMM 维护的映射)-->  HPA
```

难点在于：MMU 硬件原本只认"一层页表"。客户机 OS 以为自己在正常地设页表（GVA→GPA），但这套页表里填的"物理地址"其实是 GPA、不能直接喂给真 MMU。怎么让硬件走完两层，是"内存虚拟化"的全部命题。历史上先有软件方案（影子页表 15.2.2），后有硬件方案（EPT/NPT 15.2.3）。

### 15.2.2 影子页表（shadow page table）：软件合并两层

硬件二级翻译出现之前，VMM 用**影子页表**软件地把两层"压平成一层"。思路：VMM 在背后偷偷维护一套**真正喂给 MMU** 的页表（影子页表），它直接映射 **GVA→HPA**（把客户机的 GVA→GPA 和 VMM 的 GPA→HPA 两层预先合成好）。客户机 OS 自己那套 GVA→GPA 页表则被 VMM 设成**只读/不直接生效**：客户机一旦试图修改它的页表，就触发 VM-exit 陷入 VMM，VMM 据此**同步更新**对应的影子页表，再返回。

对初学者，影子页表就是"VMM 替 MMU 记的一本合账"：客户机记两本分账（GVA→GPA、以及 VMM 心里的 GPA→HPA），MMU 只认一本总账，于是 VMM 帮忙把两本合成一本给 MMU 看，并盯着客户机改账的动作实时对账。它的致命缺点也就在这："盯着改账"意味着客户机**每一次页表修改、每一次缺页**都要陷入 VMM 一趟（VM-exit 很贵，见 15.1.3），页表操作密集的负载会被拖垮；而且每个客户机进程都要一套影子页表，内存开销也大。这直接催生了硬件二级翻译。

### 15.2.3 EPT / NPT：硬件二级地址翻译（second-level address translation）

现代做法是让 **MMU 硬件原生走两层**。Intel 叫 **EPT（Extended Page Tables，扩展页表）**，AMD 叫 **NPT（Nested Page Tables，嵌套页表，也叫 RVI）**。开启后，CPU 里同时装着两套页表基址：客户机页表（GVA→GPA，仍由客户机 OS 自由维护）和 EPT/NPT（GPA→HPA，由 VMM 维护）。一次地址翻译由硬件自动串联两层完成，**客户机改自己的页表不再需要陷入 VMM**——因为 GVA→GPA 那层现在客户机可以随便改，硬件会自动再过一遍 EPT 得到真实地址。这把 15.2.2 里"每次改页表都 VM-exit"的巨大开销一举消除，是硬件虚拟化真正实用的关键一步。

代价是"页表走查（page walk）"变长了。没有虚拟化时，x86-64 四级页表最坏走 4 次访存；有了 EPT，翻译 GVA 的**每一级**客户机页表指针本身又是个 GPA、还要再过一遍 EPT（也是多级）。结果是一次 TLB 未命中的最坏页表走查次数大致变成"两维相乘"级别：

```
无虚拟化:      最多 4 次内存访问 (四级页表)
EPT/NPT 开启:  最坏约 (4+1) × (4+1) - 1 = 24 次内存访问 (二维嵌套走查)
```

上式的"约 24 次"是常被引用的量级估算（4 级客户机 × 每级再走 4 级 EPT 加各级本身），**具体系数依页表级数、大页与实现而变，标「待核·量级估算」**，未开 Intel SDM 逐条核对。正因页表走查变贵，TLB 在虚拟化下更关键——CPU 于是引入 **VPID（Intel）/ ASID（AMD）** 给 TLB 项打上"属于哪个客户机"的标签，使 VM-entry/exit 时**不必整体刷 TLB**，只需按标签区分，进一步省开销。EPT 项里还带权限位，是实现内存隔离（客户机之间、客户机与 VMM 之间）的落点。

初学者抓两点即可：**"二级翻译 = 硬件帮你把 GVA→GPA→HPA 一次走完，代价是页表走查步数近似平方式增长，靠 TLB + 大页 + VPID/ASID 来摊薄。"** 机制名（EPT/NPT/RVI/VPID/ASID）按厂商手册命名，低层格式细节本报告不深挖、标「待核」。

#### 来源与时效（本小主题末集中列）
- 锚点：MIT 6.1810「Virtual Machines」讲义（影子页表 vs 硬件二级翻译动机、减少 VM-exit，核实 2026-07-25）；OSTEP VM 内存虚拟化概念（GVA/GPA/HPA 两层、VMM 掌握第二层，核实 2026-07-25）；Intel SDM Vol.3C「EPT」/ AMD64 APM Vol.2「Nested Paging」（EPT/NPT/VPID/ASID 命名——**格式与逐级细节、页表走查系数标「待核」**，未逐条一手核对，日期 2026-07-29）。
- 本机实证：本机为客户机、`/proc/cpuinfo` 无 `vmx`/`svm`，EPT/NPT 不可本机内演示，如实标注。
- 两来源一致 / 待核：6.1810 与 OSTEP 一致把"每次页表操作都陷入"列为影子页表的核心缺陷、把硬件二级翻译列为解法。EPT 走查"约 24 次访存"为常见量级估算，硬标「待核」；VPID/ASID 作用（免全刷 TLB）多来源一致但具体行为以 SDM/APM 为准。

## 15.3 Linux namespaces

### 15.3.1 namespace 是什么，以及创建它的三个系统调用

**namespace（命名空间）** 是 Linux 内核提供的隔离机制：它把某一类**全局系统资源**包裹起来，让"处在同一个 namespace 里的进程"看到这份资源的一份**独立副本/独立视图**，而看不到别的 namespace 里的同类资源。`namespaces(7)` 一句话定义："a namespace wraps a global system resource in an abstraction that makes it appear to the processes within the namespace that they have their own isolated instance of the global resource." 这正是容器"以为自己独占一台机器"的视图错觉来源——**namespaces 管的是"看得见什么"**（cgroups 才管"能用多少"，见 15.4）。

操作 namespace 只有三个系统调用，初学者务必记住这三件事各干什么：

```
clone(2)   —— 创建新进程的同时，把它放进(若干)新建的 namespace (带 CLONE_NEW* 标志)
unshare(2) —— 让"当前"进程脱离原 namespace、进入新建的 namespace (不新建进程)
setns(2)   —— 让当前进程加入一个"已存在"的 namespace (docker exec 进容器就靠它)
```

其中 `clone` 的 `CLONE_NEWPID`/`CLONE_NEWNET`/`CLONE_NEWNS` 等标志各对应一种 namespace 类型（下节逐一）。命令行工具 `unshare`/`nsenter` 就是对 `unshare(2)`/`setns(2)` 的封装。每个进程当前所属的各类 namespace，都在 `/proc/[pid]/ns/` 下暴露成一组符号链接，链接目标形如 `pid:[4026531836]` 这样的 **inode 号**——两个进程的某类 namespace 是不是同一个，比较这个 inode 号即可。

### 15.3.2 八种 namespace 逐一：各隔离了什么

`namespaces(7)`（现行内核）共定义 **8 种** namespace。prompt 清单点名前 7 种，第 8 种 time 也一并列全：

**mnt（mount，挂载点）**——隔离**挂载点列表**，每个 mnt namespace 有自己的一套文件系统挂载视图。容器换根文件系统（配合 `pivot_root`/`chroot`）就靠它；`CLONE_NEWNS` 是最早（2002，Linux 2.4.19）引入的 namespace，标志名里没有 `MNT` 是历史原因。

**pid（进程号）**——隔离 **PID 号空间**，每个 pid namespace 里进程号从 1 重新开始，第一个进程是该 namespace 的"init"（PID 1），负责收养孤儿与回收僵尸。容器里 `ps` 看到自己是 PID 1、看不到宿主进程，就是这一层。

**net（network，网络）**——隔离**整个网络栈**：网络设备、IP 地址、路由表、端口号、防火墙规则。每个 net namespace 有自己独立的 `lo` 和网卡；容器间通过 veth 虚拟网卡对 + 网桥互联（落地属 L5-08）。

**uts（UNIX Time-sharing System）**——隔离 **hostname 与 NIS domain name**。名字来历古怪，只要记住"容器能有自己的主机名"即可（`uname -n` 在容器里返回容器名）。

**ipc（进程间通信）**——隔离 **System V IPC 对象与 POSIX 消息队列**（共享内存段、信号量、消息队列），使一个 namespace 里的进程无法访问另一个的 IPC 对象。

**user（用户）**——隔离 **UID/GID 映射与 capability（能力）**。这是隔离里最特殊、也是"非特权容器"的基石（见 15.3.3）：一个进程可以在 namespace **内**是 root（UID 0、握全 capability），映射到宿主上却是个普通非特权用户。

**cgroup**——隔离**进程看到的 cgroup 根**（`/proc/[pid]/cgroup` 里的路径），让容器看不到宿主的 cgroup 层级全貌、只看到以自己为根的相对视图（Linux 4.6 引入）。注意区分：cgroup **namespace** 隔离的是"cgroup 层级的视图"，而 **cgroups 机制本身**（15.4）做的是资源限额，两者是不同的东西。

**time**——隔离 **系统的单调时钟与启动时钟偏移**（`CLOCK_MONOTONIC`/`CLOCK_BOOTTIME`），使容器可有不同的启动时间基准（Linux 5.6 引入，2020）。本机 `lsns` 与 `/proc/self/ns/` 都能看到 `time` 这一项，佐证内核已支持。

对初学者，一句话串起来：**容器 = "把这 8 类视图各切一份给一组进程"**。你不需要一次全切——`docker run` 默认切 mnt/pid/net/uts/ipc（还常配 user），而某些轻隔离场景可以只切其中几种。

### 15.3.3 user namespace 与"非特权容器"

user namespace 值得单独讲，因为它回答了"普通用户为什么也能创建容器"这个关键问题。它维护一张 **UID/GID 映射表**（`/proc/[pid]/uid_map`），把 namespace 内的 UID 段映射到宿主的 UID 段。典型映射是"容器内 UID 0（root）→ 宿主 UID 100000"这类：进程在容器里**自认为是 root、握有该 user namespace 内的全部 capability**，因此能挂载文件系统、创建其它 namespace、改主机名；可一旦它的操作触及**该 namespace 之外**的资源，内核按它在宿主上的真实身份（那个普通 UID）来判权，于是干不了任何越界的坏事。

这就是 **rootless/非特权容器（unprivileged container）** 的原理：不需要真正的宿主 root，就能获得一个"我在里面是 root"的隔离环境。初学者易错点：**"容器内的 root 不是宿主的 root"**——很多提权/逃逸误解都源于混淆这两个 root。本机可直接演示——下节用 `unshare --user --map-root-user` 在无宿主 root 时造出"内部 root"。

### 15.3.4 本机实证：查看与创建 namespace

本机 `lsns`（Linux 6.18.5 @2026-07-29）列出当前各 namespace，可见 8 类齐全（含 time）：

```
        NS TYPE   NPROCS PID USER COMMAND
4026531832 mnt        79   1 root /process_api ...
4026531833 net        80   1 root ...
4026531834 time       80   1 root ...
4026531835 cgroup     80   1 root ...
4026531836 pid        80   1 root ...
4026531837 user       80   1 root ...
4026531838 uts        80   1 root ...
4026531839 ipc        80   1 root ...
```

`ls -l /proc/self/ns/` 把当前进程各 namespace 暴露成符号链接，链接目标里的数字就是上表的 namespace inode 号（用于比较两个进程是否同 namespace）：

```
cgroup -> cgroup:[4026531835]
pid    -> pid:[4026531836]
net    -> net:[4026531833]
user   -> user:[4026531837]
...
```

用 `unshare` 现造一个新的 user+pid+mnt namespace，演示"内部 root"与"PID 从 1 重排"（真实输出）：

```
$ unshare --user --map-root-user --pid --fork --mount-proc sh -c \
      'echo inside; ps -e | head; readlink /proc/self/ns/pid'
inside
    PID TTY          TIME CMD
      1 ?        00:00:00 sh          <- 新 pid namespace 里 shell 就是 PID 1
      2 ?        00:00:00 ps
      3 ?        00:00:00 head
pid:[4026532296]                       <- 与宿主 pid:[4026531836] 不同的 inode
```

新 pid namespace 里 `ps` 只看到 namespace 内的三个进程、且 shell 是 PID 1；`/proc/self/ns/pid` 的 inode（4026532296）与宿主（4026531836）不同，证明确实进了一个新 pid namespace。`--map-root-user` 用 user namespace 把当前非特权用户映射成 namespace 内 root——这正是 15.3.3 说的非特权容器机制。

#### 来源与时效（本小主题末集中列）
- 锚点：Linux man-pages `namespaces(7)`（8 种 namespace 类型、`/proc/[pid]/ns`、隔离语义定义，核实 2026-07-29）；`clone(2)`/`unshare(2)`/`setns(2)`（三个创建/加入接口与 `CLONE_NEW*` 标志，核实 2026-07-29）；`user_namespaces(7)`（uid_map、capability 隔离、非特权容器，核实 2026-07-29）。
- 本机实证：`lsns`、`ls -l /proc/self/ns`、`unshare --user --map-root-user --pid --fork --mount-proc` @Linux 6.18.5 2026-07-29，真实输出如上（8 类齐全含 time；新 pid namespace 内 PID 1 + inode 不同）。
- 两来源一致 / 版本：man-pages 与本机 `lsns` 对"8 类 namespace"一致；引入版本（mnt 2.4.19、user 3.8 完善、cgroup 4.6、time 5.6）按 man-pages「VERSIONS」，与常见二手综述一致。time namespace 属较新特性，硬标其为 5.6 引入。

## 15.4 cgroups (v2)

### 15.4.1 cgroup 是什么，以及 v1 → v2 的演进 ⚙演进快·锚版本

**cgroups（control groups，控制组）** 是 Linux 内核用来**限制、计量、隔离一组进程的资源用量**（CPU、内存、IO、进程数……）的机制。如果说 namespaces 管"看得见什么"，cgroups 就管"能用多少"——两者合起来才构成完整的容器隔离。`cgroups(7)` 的定义：cgroup 把进程组织成分层的组，把各种资源的使用限制施加到这些组上。

演进是本节重点，**⚙演进快·锚 Linux 6.18.5 / kernel.org cgroup-v2 现行文档 @2026-07-29**。**cgroup v1（legacy）** 的设计是"**每个资源控制器一套独立的层级树**"——CPU 一棵树、内存一棵树、IO 一棵树，互不相干，一个进程在不同树里可以处在完全不同的组。这导致协调困难、语义混乱。**cgroup v2（unified hierarchy，统一层级）** 用**唯一一棵树**：所有控制器共用同一层级，一个进程在整棵树里只有一个位置。v2 自 Linux 4.5（2016）稳定，此后逐步成为主流——systemd 与主流发行版（Fedora 31+ 2019、后续 Debian/Ubuntu/RHEL）默认转向 v2，容器运行时也逐步默认 v2。**本机如实标注**：本机是 **v1 混合布局**——`/proc/mounts` 显示 `cpu`/`cpuacct`/`memory`/`devices`/`freezer`/`blkio`/`pids` 各自 `cgroup`（v1）独立挂载，外加一个 `cgroup2` 的 `unified` 挂载但只委派了 `cpuset`/`hugetlb`：

```
tmpfs   /sys/fs/cgroup            tmpfs   ...
cgroup  /sys/fs/cgroup/cpu        cgroup  rw,...,cpu       <- v1 各控制器独立挂载
cgroup  /sys/fs/cgroup/memory     cgroup  rw,...,memory
cgroup  /sys/fs/cgroup/pids       cgroup  rw,...,pids
cgroup2 /sys/fs/cgroup/unified    cgroup2 rw,relatime      <- 仅委派 cpuset/hugetlb
```

故本机看不到 v2 的 `memory.max`/`cpu.max`（它们在纯 v2 系统才有）；本节的 v2 接口以 kernel.org 现行文档为准并标注"本机为 v1，未本机演示对应文件"。

### 15.4.2 cgroup v2 的核心接口文件与"无内部进程"规则

在 cgroup v2 里，操作就是**读写 cgroup 目录下的文本文件**。核心几个：

```
cgroup.controllers       —— (只读) 本组当前"可用"的控制器列表
cgroup.subtree_control   —— (读写) 打开/关闭要下放给"子组"的控制器 (+cpu -memory ...)
cgroup.procs             —— (读写) 本组包含的进程 PID；写入 PID 即把进程移入本组
```

这几个文件的用法很直白，把一个进程放进某个 cgroup，就 `echo $PID > 某组/cgroup.procs`；要让某组能限制子组的 CPU，就往它的 `cgroup.subtree_control` 写 `+cpu`。v2 有一条初学者常踩的硬性约束——**"无内部进程（no internal processes）规则"**：一个既开启了控制器、又有子组的 cgroup，**自己不能直接容纳进程**，进程只能待在**叶子**组里。原因是资源分配语义需要清晰——不允许"一个组既和自己的子组竞争、又给子组分配"这种自我矛盾的结构。（根 cgroup 是唯一豁免。）v1 没有这条规则，这也是 v1→v2 迁移时最常见的报错来源。

### 15.4.3 CPU 限额与计量

v2 的 CPU 控制器提供两类分配方式。**带宽上限（硬限）用 `cpu.max`**，格式是"配额 周期"两个数（微秒）：

```
cpu.max:  "50000 100000"   —— 每 100ms 周期内最多用 50ms CPU 时间 = 限到 0.5 个 CPU
cpu.max:  "max 100000"     —— max 表示不限 (默认)
```

**相对权重（软限，按比例分）用 `cpu.weight`**（默认 100，范围 1–10000）：多个组争抢同一 CPU 时，按权重比例分配，谁忙不忙时富余的算力仍可被别人借用——这是"份额"语义，对应大主题05 的比例份额调度。计量则读 **`cpu.stat`**（累计 `usage_usec`、被限流的 `throttled_usec` 等）。

初学者抓住"**上限 vs 权重**"这对区别：`cpu.max` 是"最多不许超过"（哪怕机器闲着也不给你多用），`cpu.weight` 是"抢的时候按比例分"（机器闲着你能多吃）。容器的 `--cpus=0.5` 底层就是设 `cpu.max` 的配额/周期比。

### 15.4.4 内存限额与计量

v2 内存控制器的核心文件：

```
memory.max      —— 硬上限 (hard limit)：超过且回收不回来 → 触发本组内 OOM kill
memory.high     —— 软上限 (throttle)：超过则强力回收 + 限流该组，但不直接 OOM
memory.current  —— (只读) 当前用量
memory.stat     —— (只读) 分项计量 (anon/file/slab 等)
```

`memory.high` 与 `memory.max` 的分工是 v2 的一个进步：`high` 先温和地"踩刹车"（回收+限流，给用户空间反应机会），`max` 才是"硬墙"（撞上就 OOM）。这比 v1 只有一个硬限、一超就 OOM 更可控。本机为 v1，对应文件是 `memory.limit_in_bytes`，本机读到的是"无限制"哨兵值：

```
$ cat /sys/fs/cgroup/memory/memory.limit_in_bytes
9223372036854771712        # ≈ 2^63，表示未设限
```

初学者记住：**内存限的是"回收不回来的部分"**——页缓存等可回收内存超限时内核先尝试回收，回收不动才 OOM。容器 `--memory=512m` 底层就是设 `memory.max`。

### 15.4.5 IO 限额与计量

v2 的 IO 控制器（`io`）对**块设备**做限速与计量，按 `设备号 参数` 的格式写 `io.max`：

```
io.max:  "8:0 rbps=1048576 wbps=1048576 riops=1000 wiops=1000"
         设备 8:0：读/写各限 1MB/s、读/写各限 1000 IOPS
io.stat: (只读) 每设备的累计读写字节/次数
```

另有 `io.weight` 按比例分 IO 带宽（配合 BFQ/blk 层）。初学者只需知道"IO 也能像 CPU 一样又限上限（`io.max`）又按权重分（`io.weight`）、且计量在 `io.stat`"即可；具体限速在不同 IO 调度器/设备上的精度差异较大，属实现细节不展开。v2 相比 v1 的 `blkio` 的一大改进是能把"直接 IO"和"缓冲写回（writeback）"统一归因到发起进程组（v1 里 buffered write 的归因长期是个痛点）。

#### 来源与时效（本小主题末集中列）
- 锚点：Linux 内核官方文档 admin-guide **cgroup-v2**（unified hierarchy、`cgroup.controllers`/`subtree_control`、no-internal-processes 规则、`cpu.max`/`cpu.weight`/`memory.max`/`memory.high`/`io.max` 语义，docs.kernel.org 现行，核实 2026-07-29）；Linux man-pages `cgroups(7)`（cgroup 定义、v1/v2 差异总览，核实 2026-07-29）。
- 本机实证：`/proc/mounts`（v1 混合布局：各控制器独立 `cgroup` 挂载 + `unified` cgroup2 仅委派 cpuset/hugetlb）、`/sys/fs/cgroup/memory/memory.limit_in_bytes = 9223372036854771712`（v1 未设限哨兵值）@Linux 6.18.5 2026-07-29。**本机为 v1，v2 的 `memory.max`/`cpu.max` 未能本机演示，如实标注、以官方文档为准。**
- 两来源一致 / ⚙演进：kernel.org 与 man-pages 对 v1(独立层级)/v2(统一层级) 分野一致。v2 稳定于 4.5、发行版默认转 v2 的时间线（Fedora 31 = 2019）为常见记载，硬标「⚙演进快·随发行版/内核版本变·锚 2026-07-29」。文件默认值/格式以内核文档为准，未逐字段一手核对处不编造。

## 15.5 隔离的侧信道代价

### 15.5.1 为什么"逻辑上隔离好了"仍会漏：推测执行侧信道

前面 15.1–15.4 讲的隔离都是**逻辑隔离**：页表权限位、namespace 视图、cgroup 限额，都假设"CPU 忠实地按架构语义执行、不该看的就真看不到"。2018 年的 Meltdown/Spectre 击碎了这个假设：现代 CPU 为了性能会**推测执行（speculative execution）**——在还没确认某分支/某访问是否合法之前，就**乱序、预测性地**先把后续指令跑起来；如果预测错了，架构上会把结果**丢弃回滚**，寄存器/内存看起来没变。问题在于：**回滚不干净**——这些"本不该发生"的推测访问会在 **CPU 缓存等微架构状态**里留下痕迹，攻击者用**时间侧信道（timing side channel）** 测量"某地址访问快还是慢（在不在缓存里）"就能把痕迹读出来，从而**间接推断出本无权访问的数据**。

对初学者，一句话心智模型：**"CPU 偷偷抢跑了不该跑的活儿，事后假装没跑过，但它抢跑时碰过的东西在缓存里留了指纹，攻击者靠量时间读指纹。"** 这类攻击可怕之处在于它绕过的不是某个软件 bug，而是**硬件的性能优化本身**，因此几乎所有高性能 CPU 都受影响、且软件层的逻辑隔离对它天然无效——这是"共享内核/共享硬件"的容器与云多租户场景必须正视的隔离代价。

### 15.5.2 Meltdown（CVE-2017-5754）：越权读内核内存

**Meltdown** 利用的是"推测执行**先访存、后查权限**"的时间窗口。攻击者在用户态发出一条读**内核**地址的指令（架构上一定会因权限不足而失败/抛异常），但在异常真正落地、把结果丢弃之前的那一小段推测窗口里，CPU 已经**用读到的内核数据去索引一个用户态数组**，从而把该数据的值"编码"进了缓存的访问模式。异常回滚后寄存器是干净的，但缓存里被碰过的那一行留了痕迹，攻击者随后逐行测时间就能还原出那个内核字节。反复做，就能把整片内核内存（在早期设计里内核被完整映射进每个进程的地址空间）读出来。

Meltdown 的关键点：它**主要影响 Intel（及部分 ARM）**、成因是"权限检查滞后于推测访存"，**打破的正是用户态/内核态这条最基本的隔离边界**——这也是为什么它的软件缓解（KPTI，15.5.4）针对的就是"内核不该被映射进用户页表"。

### 15.5.3 Spectre：v1 边界检查绕过与 v2 分支目标注入

**Spectre** 比 Meltdown 更普遍、更难根治，利用的是**分支预测**下的推测执行。两个经典变体：

**Spectre v1（CVE-2017-5753，Bounds Check Bypass，边界检查绕过）**——攻击者先"训练"分支预测器让 `if (x < array_len)` 这类边界检查倾向于预测为真，然后传入越界的 `x`；CPU 推测性地跳过检查、用越界 `x` 去访存并把结果编码进缓存。典型受害代码形如：

```
if (x < array1_size)              // 边界检查——被推测性绕过
    y = array2[array1[x] * 4096]; // 用越界 array1[x] 索引 array2，留缓存指纹
```

**Spectre v2（CVE-2017-5715，Branch Target Injection，分支目标注入）**——攻击者污染**间接分支预测器**，诱使受害者（如内核）在推测执行时跳到攻击者选定的"gadget"代码去，从而把敏感数据泄进缓存。

Spectre 的要害：它利用的是**合法进程自己代码里的推测执行**（不像 Meltdown 直接越权访存），因此**跨隔离边界**（进程间、客户机↔hypervisor、甚至 JS 沙箱）都可能中招，且**无法靠单一硬件补丁根治**，只能一类一类打补丁。常见软件/硬件缓解：v1 用 `lfence` 之类的**推测屏障**堵住关键分支；v2 用 **retpoline**（把间接跳转换成不可被注入预测的返回蹦床）以及 **IBRS/IBPB/STIBP**（一组控制间接分支推测的 CPU 特性/微码）。

### 15.5.4 KPTI 缓解与它的性能代价

针对 Meltdown 的主力软件缓解是 **KPTI（Kernel Page-Table Isolation，内核页表隔离，前身叫 KAISER）**。思路直截了当：既然 Meltdown 能推测性读到"映射在当前地址空间里的内核内存"，那就**在用户态运行时，把内核页几乎全部从用户页表里移除**——用户态那份页表只留极少量陷入/返回必需的跳板代码。这样即便发生推测性越权访存，内核地址在当前页表里根本没有映射，也就没什么可泄的。代价是：**每次用户态↔内核态切换（系统调用、中断、异常）都要切换一次页表（换 CR3）**，而换页表会**刷 TLB**，导致系统调用密集的负载明显变慢。

这个代价靠 **PCID（Process-Context Identifier，进程上下文标识符）** 大幅摊薄：给 TLB 项打上"属于哪个地址空间"的标签，换 CR3 时不必整体刷 TLB、只按标签区分，于是 KPTI 的切页表开销被显著降低。本机 `/proc/cpuinfo` flags 里有 `pcid`/`invpcid`，正是这条优化路径依赖的硬件支持。初学者记住这条因果链：**Meltdown → 内核不能再映进用户页表（KPTI）→ 每次进出内核多一次切页表 → 靠 PCID 免全刷 TLB 来压低这笔开销。**

### 15.5.5 本机现状实证 ⚙演进快·锚日期

推测执行漏洞的"是否受影响/如何缓解"随 CPU 型号、微码、内核版本持续变化，**必须硬标日期、不凭记忆**。本机 `/sys/devices/system/cpu/vulnerabilities/*`（Linux 6.18.5，Intel Xeon 客户机 @2026-07-29，真实输出节选）：

```
meltdown:          Not affected
spectre_v1:        Mitigation: usercopy/swapgs barriers and __user pointer sanitization
spectre_v2:        Mitigation: Enhanced / Automatic IBRS; IBPB: conditional; ... BHI: Vulnerable
spec_store_bypass: Mitigation: Speculative Store Bypass disabled via prctl
mds:               Not affected
l1tf:              Not affected
```

本机现状要点，如实标注：**Meltdown 显示 "Not affected"**——这台较新的 Xeon 在硬件层已修复 Meltdown（CPU 通过 `RDCL_NO` 声明不受影响），因此**本机内核并未真的为它启用 KPTI**（15.5.4 讲的是机制原理，本机恰好不需要，不要据此以为"KPTI 到处都开着"）。Spectre v1/v2 仍需运行时缓解、内核已启用（v2 用 Enhanced IBRS 等）；但 `spectre_v2` 行末 **`BHI: Vulnerable`** 提示"分支历史注入"这一较新子变体在本配置下仍未完全缓解——这正是"Spectre 家族只能逐类打补丁、难以根治"的活例证。这些结论只对"本机此刻的型号+微码+内核 6.18.5 @2026-07-29"成立，换机器/更新微码都会变。

#### 来源与时效（本小主题末集中列）
- 锚点：MIT 6.1810「Meltdown」讲义（推测执行 + 缓存时间侧信道 + KPTI/KAISER 缓解思路，核实 2026-07-25）；Meltdown 原始论文（Lipp et al., USENIX Security 2018）；Spectre 原始论文（Kocher et al., IEEE S&P 2019，含 v1 Bounds Check Bypass / v2 Branch Target Injection）；Linux 内核 admin-guide「hw-vuln」文档与 `/sys/devices/system/cpu/vulnerabilities` 接口语义（docs.kernel.org，核实 2026-07-29）。
- 本机实证：`/sys/devices/system/cpu/vulnerabilities/*`（meltdown=Not affected、spectre_v2 含 BHI: Vulnerable 等）、`/proc/cpuinfo` 含 `pcid`/`invpcid`/`ibrs_enhanced` @Linux 6.18.5 Intel Xeon 客户机 2026-07-29，真实输出如上。
- 两来源一致 / ⚙演进硬标：6.1810 与原始论文对 Meltdown（越权访存+缓存侧信道）、Spectre（分支预测下推测）、KPTI（移除内核映射）机制一致。CVE 编号（Meltdown 2017-5754、Spectre v1 2017-5753 / v2 2017-5715）为公开定名。**"是否受影响 / 缓解状态"硬标「⚙演进快·随微码/内核/型号变·锚本机 2026-07-29」**，Meltdown 在本机为 Not affected（硬件已修、KPTI 未实际启用）——不要外推为通用结论。
