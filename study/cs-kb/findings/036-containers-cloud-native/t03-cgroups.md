# L5-13·大主题3 cgroups（资源限额，v1 vs v2）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L4-01 操作系统（进程/调度/内存管理）、本课大主题2 namespaces ｜一手锚点：Linux man cgroups(7)、内核 Documentation/admin-guide/cgroup-v1 与 cgroup-v2、OCI Runtime Spec v1.3.0、Kubernetes 官方文档 v1.36 ｜成熟度：cgroup v1/v2 均为内核 GA 机制；本机基线 cgroup **v1 hybrid**，v2 为现代发行版默认（⚙演进快·锚版本·随时变，见 13-3.5）

如果说 namespaces（大主题2）回答的是"进程能**看见**什么"，那么 cgroups 回答的是"进程能**用掉**多少"。二者是容器隔离的两条腿：namespaces 隔离视图，cgroups 限额资源。cgroups 本身是 Linux 内核提供的通用资源管理原语（衔接 L4-01 操作系统的调度器与内存管理子系统），并非容器专属；容器运行时只是把它当作"给每个容器套一个资源笼子"的工程手段。本报告先讲通用概念与四类主要控制器，再专门用一章做 v1 与 v2 的差异分账。

---

## 13-3.1 cgroup 概念：控制器、层级与任务模型

### 13-3.1.1 什么是 cgroup（control group）

cgroup（control group，控制组）是 Linux 内核的一项功能，它把一组进程聚在一起，作为一个整体去**度量、限制和隔离**它们对系统资源（CPU 时间、内存、块设备 I/O、进程数等）的使用。名字里的 group 指的就是"一组进程"，control 指的是"对这组进程施加资源控制"。

普通进程只受全局调度器和全局内存管理约束——谁抢到算谁的。cgroup 在这之上加了一层"账本+闸门"：内核为每个 cgroup 维护资源用量的统计（账本），并在超过配置的上限时施加限制（闸门，比如降低 CPU 分配、触发内存回收或杀进程）。容器要做到"这个容器最多用 1 个 CPU、512 MB 内存"，靠的正是把容器的所有进程放进一个 cgroup 并设置上限。

### 13-3.1.2 控制器（controller / subsystem）

控制器是真正实现某一类资源管理逻辑的内核模块，一种资源对应一个控制器。man cgroups(7) 里也叫它 subsystem（子系统）。常见控制器包括 cpu（CPU 调度带宽）、cpuacct（CPU 用量统计，v1 独立、v2 并入 cpu）、cpuset（绑定到指定 CPU/内存节点）、memory（内存上限与统计）、pids（进程/线程数上限）、blkio（v1 的块设备 I/O，v2 改名 io）、devices（设备访问白名单，v2 改由 eBPF 实现）、freezer（冻结/解冻整组进程）、hugetlb、net_cls/net_prio（v1 的网络分类，v2 未保留）等。

初学者容易把"cgroup"和"控制器"混为一谈。记住区别：cgroup 是"一组进程"这个容器，控制器是"往这个容器上挂的一种限额能力"。同一个 cgroup 上可以同时启用 cpu、memory、pids 多个控制器，各管一类资源，互不干扰。一个控制器有哪些可调旋钮，就体现为该 cgroup 目录下的一批接口文件（如 cpu 控制器的 `cpu.max`、memory 控制器的 `memory.max`）。

### 13-3.1.3 层级（hierarchy）：cgroup 组成一棵树

cgroups 通过一个基于内核的**伪文件系统**（cgroupfs）暴露给用户空间，通常挂载在 `/sys/fs/cgroup` 下。cgroup 之间是**树状层级**关系：根 cgroup 对应整台机器（或容器可见的根），你在里面 `mkdir` 一个子目录就创建了一个子 cgroup，再 `mkdir` 就是孙 cgroup。子 cgroup 的资源限额受父 cgroup 约束——父给 1 个 CPU，两个子加起来也超不过 1 个。这种"限额沿树向下收紧"的性质，让层级天然适合表达"整台机器 → 某个 pod → pod 里某个容器"的嵌套配额。

一个进程要加入某个 cgroup，做法是把它的 PID 写进该 cgroup 目录下的 `cgroup.procs` 文件（写 TID 到 `tasks` 则是线程级，v1 特性）。一个关键区别在 v1 与 v2 之间：v1 允许"每种控制器各自挂一棵独立的树"（多层级），v2 强制"所有控制器共用同一棵树"（统一层级）——这正是 13-3.5 要展开的核心差异。

### 13-3.1.4 任务模型：进程/线程如何归属

在 cgroup 术语里，"任务"（task）泛指被管理的进程或线程。核心规则是：**每个进程在每一棵 cgroup 层级里，任何时刻只属于恰好一个 cgroup**（不能同时在两个兄弟 cgroup 里）。新建的子进程默认继承父进程所在的 cgroup，直到被显式迁移。

可以直接读 `/proc/self/cgroup` 看当前进程的归属。本机（v1 hybrid）真实输出：

```
7:pids:/
6:blkio:/
5:freezer:/
4:devices:/
3:memory:/process_api/019fbe77-a771-7530-b72c-3b5a6f814719
2:cpuacct:/
1:cpu:/
0::/
```

每行格式是 `层级ID:控制器列表:cgroup路径`。可以看到 v1 下每个（组）控制器各占一行、各有独立的层级 ID 和路径（本进程在 memory 层级里被放到了一个专门的子 cgroup，而在 cpu/pids 等层级里还在根 `/`）——这正是"多层级、同一进程在不同树里位置可以不同"的直观体现。最后那行 `0::/` 是 v2 统一层级的固定格式（层级 ID 恒为 0、控制器列表为空），说明本机同时挂了一个 v2 树。纯 v2 系统则只会有这一行。

#### 来源与时效
- Linux man page cgroups(7)（core 概念：cgroup/subsystem/hierarchy/cgroupfs、/proc/[pid]/cgroup 格式），核实 2026-08-01。
- 内核 Documentation/admin-guide/cgroup-v1/cgroups.rst（v1 概念与 tasks/cgroup.procs 语义）与 cgroup-v2.rst（§Basic Operations，进程归属与统一层级），核实 2026-08-01。
- 本机实证（Linux 6.18.5，util-linux 2.39.3）：`mount | grep cgroup`、`cat /proc/self/cgroup`，确认 v1 hybrid 布局与上述归属行。
- 两来源一致；控制器命名 v1/v2 差异（blkio→io、cpuacct 并入 cpu、devices 转 eBPF）以两份内核文档交叉确认。

---

## 13-3.2 cpu 控制器：份额（shares/weight）vs 配额（quota/period）

### 13-3.2.1 两种截然不同的 CPU 限流语义

cpu 控制器提供两类互补的 CPU 限制手段，初学者务必分清：

一是**相对份额**（v1 叫 shares，v2 叫 weight）：它不是硬上限，而是"当 CPU 不够分时，按比例切分"。给 A 组 shares=2048、B 组 shares=1024，则争抢时 A 拿到 2/3、B 拿到 1/3 的 CPU；但如果 B 空闲，A 可以用满整颗甚至多颗 CPU。份额是"闲时不浪费、忙时按比例"的软性权重。

二是**绝对配额**（quota/period，即 CFS bandwidth，完全公平调度器带宽控制）：它是硬上限，规定"每个周期 period 内，这组进程最多累计运行 quota 微秒的 CPU 时间"。哪怕别的 CPU 全空着，超过配额也会被强制限流（throttle）到下个周期。这才是 Kubernetes CPU **limit** 的落地方式。

份额管"抢不过时怎么分"，配额管"最多能用多少"，独占一行记：

```
份额/权重 = 相对比例（软，可超用空闲）；配额/周期 = 绝对上限（硬，超了被限流）
```

### 13-3.2.2 v1 接口：cpu.shares 与 cpu.cfs_quota_us / cpu.cfs_period_us

在 cgroup v1 下，相对份额是 `cpu.shares`，默认值 1024，取值范围约 2–262144。绝对配额由一对文件表达：`cpu.cfs_period_us`（周期长度，默认 100000，即 100 毫秒）与 `cpu.cfs_quota_us`（每周期配额，默认 -1 表示不限）。要限成"最多 0.5 个 CPU"，就把 quota 设成 period 的一半（如 quota=50000, period=100000）；要"最多 2 个 CPU"，quota=200000、period=100000。

本机（v1）真实默认值实证：

```
$ cat /sys/fs/cgroup/cpu/cpu.shares
1024
$ cat /sys/fs/cgroup/cpu/cpu.cfs_quota_us
-1
$ cat /sys/fs/cgroup/cpu/cpu.cfs_period_us
100000
```

即根 cgroup 默认份额 1024、不设配额（-1）、周期 100ms，与内核文档记载一致。

### 13-3.2.3 v2 接口：cpu.weight 与 cpu.max

cgroup v2 把两个旋钮改名并简化。相对份额变成 `cpu.weight`，取值范围 1–10000、默认 100（数值越大权重越高，语义与 shares 相同但范围不同）。绝对配额合并成一个文件 `cpu.max`，格式为两个数：

```
$MAX $PERIOD
```

例如 `50000 100000` 表示每 100ms 最多 50ms（即 0.5 CPU），`max 100000` 表示不限（默认）。此外 v2 还有 `cpu.weight.nice`（用 nice 值表达权重）和 `cpu.stat`（用量与被限流统计，含 `nr_throttled`、`throttled_usec`）。

一个常见困惑是"weight 默认 100 和 shares 默认 1024 怎么对应"。二者是同一相对份额思想的不同标度：内核与 systemd 约定 weight=100 对应 shares≈1024（即两个系统里的"默认权重"互相对齐）。完整区间的换算公式各实现略有出入，此处不给精确系数以免误导（精确映射待核具体内核/systemd 版本），只需记住"默认对默认、按比例放大缩小"即可。

### 13-3.2.4 易错点：份额只在争抢时生效、配额会造成节流延迟

最典型的误解是"设了 shares 就限住了 CPU"。不会。只有一个 CPU 空闲时，shares 再小的进程也能用满它；shares 仅在多个 cgroup 同时抢同一批 CPU 时才按比例裁决。真要设硬顶必须用 quota/max。

另一个坑是 CFS 配额带来的"节流延迟"：一个多线程程序若在 period 前半段就用光了 quota，剩下的时间会被整体挂起到下个周期，表现为周期性卡顿（尾延迟升高）。这在 Kubernetes 里是著名的 "CPU throttling" 问题，常见缓解是调大 period、放宽 limit 或干脆只设 request（份额）不设 limit（配额）。这一段直接衔接大主题10 的 requests/limits 与大主题3 的资源落地。

#### 来源与时效
- 内核 Documentation/scheduler/sched-bwc.rst（CFS bandwidth：period/quota 语义与节流）与 Documentation/admin-guide/cgroup-v2.rst §CPU（cpu.weight/cpu.max/cpu.stat），核实 2026-08-01。
- 内核 Documentation/admin-guide/cgroup-v1/cpu.rst（cpu.shares、cfs_period_us/cfs_quota_us 默认与范围），核实 2026-08-01。
- 本机实证：`cat /sys/fs/cgroup/cpu/cpu.shares|cpu.cfs_quota_us|cpu.cfs_period_us` → 1024 / -1 / 100000，与文档默认一致。
- weight↔shares 精确换算公式各实现有分歧（内核内部换算 vs systemd 映射），仅默认值对齐（100↔1024）为两来源共识；完整区间系数标「待核」，不编造。

---

## 13-3.3 memory 控制器与 OOM

### 13-3.3.1 内存上限与用量统计

memory 控制器给一组进程设置内存使用上限，并统计其真实用量。超过上限时，内核先尝试在该 cgroup 内做内存回收（丢弃可回收的页缓存等）；回收仍不够，就触发 cgroup 级别的 OOM（out of memory）处理。

v1 的关键文件是 `memory.limit_in_bytes`（上限）、`memory.usage_in_bytes`（当前用量）、`memory.max_usage_in_bytes`（峰值）、`memory.stat`（细分统计）、`memory.failcnt`（触限次数）。默认上限是一个极大值（本机实证）：

```
$ cat /sys/fs/cgroup/memory/memory.limit_in_bytes
9223372036854771712
```

这个约 9.2×10^18 的数就是"实际不限"的哨兵值（接近 2^63，按页对齐取整），意为默认不设内存上限。此外 v1 有 `memory.memsw.limit_in_bytes` 单独控制"内存+交换"的合计上限（需内核开启 swap 记账）。

### 13-3.3.2 v2 的内存旋钮：max / high / low / min

cgroup v2 重新设计了内存接口，提供四个层次的阈值，这是 v2 相对 v1 最实用的改进之一：

`memory.max` 是硬上限（等价 v1 的 limit_in_bytes），超过且回收失败即 OOM。`memory.high` 是**软上限/节流线**：超过它不会立刻杀进程，而是让该 cgroup 的进程在分配内存时被限速并触发激进回收，给了系统"先减速、别急着杀"的缓冲带——v1 没有对应物。`memory.low` 和 `memory.min` 则是**保护线**：low 是"尽量保留"的软保护，min 是"绝不回收"的硬保护，用于保证关键服务的常驻内存不被邻居挤占。配套还有 `memory.current`（用量）、`memory.stat`、`memory.events`（记录 low/high/max/oom/oom_kill 各类事件计数）、`memory.swap.max`（交换上限）。

### 13-3.3.3 OOM：超限时谁被杀

当一个 cgroup 的内存用满 max/limit 且无法通过回收腾出空间时，内核在**该 cgroup 范围内**调用 OOM killer，挑一个（通常是占用最大的）进程杀掉以释放内存。这与全局 OOM 的区别在于"只在这个笼子里挑人杀"，不波及其它容器。被杀进程收到 SIGKILL、无法捕获，表现为容器里进程突然消失、退出码 137（128+9）——这是 Kubernetes 里 `OOMKilled` 状态的由来。

v1 与 v2 在 OOM 行为上有值得记的分歧。v1 通过 `memory.oom_control` 允许**关闭** OOM killer，此时超限进程会被暂停（挂起）而非杀死，等待人工干预。v2 出于"僵住的 cgroup 难以恢复"的考量**移除了完全禁用 OOM 的能力**，转而提供 `memory.oom.group`：置 1 后把整个 cgroup 当作一个不可分割的单元一起杀（避免杀掉半个应用留下损坏状态）。两边设计取向不同，冲突点如实并记。

### 13-3.3.4 易错点：page cache 记账与 limit 的关系

新手常见误区是"我的程序只 malloc 了 200MB，为什么 usage 显示 400MB"。因为 memory 控制器不仅记账匿名内存，也记账该 cgroup 产生的**页缓存**（文件读写缓存）、内核 slab 等。大量读文件会把 usage 顶高，但页缓存在压力下可回收，通常不会直接导致 OOM——真正逼近硬上限并杀进程的多是不可回收的匿名内存。理解这一点才能读懂 `memory.stat` 里 `rss`、`cache` 等分项，也才能解释"限了内存的容器跑 I/O 密集任务时 usage 逼近上限却没被杀"的现象。

#### 来源与时效
- 内核 Documentation/admin-guide/cgroup-v2.rst §Memory（memory.max/high/low/min/current/events/oom.group 语义），核实 2026-08-01。
- 内核 Documentation/admin-guide/cgroup-v1/memory.rst（limit_in_bytes/usage_in_bytes/memsw/oom_control 语义与默认哨兵值），核实 2026-08-01。
- man cgroups(7)（memory 控制器概述），核实 2026-08-01。
- 本机实证：`cat /sys/fs/cgroup/memory/memory.limit_in_bytes` → 9223372036854771712（默认"不限"哨兵值），与 v1 文档一致。
- 冲突/分歧显式记录：v1 可禁用 OOM（oom_control）、v2 不可禁用但提供 oom.group——两份内核文档口径一致，取向差异如实并记。退出码 137 为约定（SIGKILL=9），非编造。

---

## 13-3.4 pids 与 io 控制器

### 13-3.4.1 pids 控制器：限制进程/线程数量

pids 控制器限制一个 cgroup（及其子树）能拥有的进程与线程总数，用来防止 fork 炸弹或失控的进程泄漏拖垮整机。它的旋钮很简单：`pids.max` 设上限（默认 `max`，即不限），`pids.current` 读当前数量。当一个 cgroup 内进程数已达 `pids.max`，再 fork/clone 会失败（返回 EAGAIN），从而把"疯狂造进程"的破坏力关在笼子里。

v1 与 v2 在 pids 上接口几乎相同（`pids.max` / `pids.current` 同名），是少数跨版本一致的控制器。要注意 pids 限的是"数量"而非"资源用量"——它不管这些进程各自吃多少 CPU/内存，纯粹卡个数。这在多租户环境里是防御性的基础闸门。

### 13-3.4.2 io（v2）/ blkio（v1）控制器：块设备 I/O 限流

块设备 I/O 控制器限制一组进程对磁盘等块设备的读写。它同样有"相对权重"和"绝对上限"两种手段，思路和 cpu 控制器平行。

v1 里叫 **blkio**：`blkio.weight` 是相对权重（争抢磁盘带宽时按比例分，默认 500、范围约 10–1000），`blkio.throttle.read_bps_device` / `write_bps_device` / `read_iops_device` / `write_iops_device` 是按设备设的绝对上限（每秒字节数或每秒操作数），格式为"主设备号:次设备号 数值"。

v2 里改名 **io**，接口统一为 `io.weight`（相对权重，范围 1–10000、默认 100，与 cpu.weight 标度对齐）和 `io.max`（按设备设 rbps/wbps/riops/wiops 上限，一行搞定），另有 `io.stat` 统计。改名与合并是 v2"统一命名、每资源一组文件"设计的一部分。

### 13-3.4.3 易错点：I/O 限流只对直接 I/O / 特定 I/O 路径可靠

块设备 I/O 限流有个反直觉的坑：它对**缓冲写**（buffered write，先进 page cache 由内核异步刷盘）的限制并不精确，因为发起写的进程和真正把脏页刷到磁盘的内核回写线程未必记在同一个 cgroup 账上。这正是 cgroup v2 统一层级要解决的问题之一——v2 把 memory 和 io 控制器放在同一棵树上，才能把"谁产生的脏页"和"回写这些页的 I/O"关联起来，让缓冲写限流真正生效。v1 因为 memory 和 blkio 各在独立层级，做不到这种协同。这个例子很好地说明了"为什么要有统一层级"，直接引出下一章。

#### 来源与时效
- 内核 Documentation/admin-guide/cgroup-v2.rst §PID 与 §IO（pids.max/current、io.weight/io.max/io.stat），核实 2026-08-01。
- 内核 Documentation/admin-guide/cgroup-v1/pids.rst 与 blkio-controller.rst（pids 语义、blkio.weight 默认/范围、throttle.*_bps/iops_device 格式），核实 2026-08-01。
- man cgroups(7)（pids、blkio/io 控制器概述），核实 2026-08-01。
- 本机实证：`ls /sys/fs/cgroup/pids/`、`ls /sys/fs/cgroup/blkio/` 存在（v1 hybrid），确认二者以独立层级挂载。
- blkio.weight 默认 500/范围 10–1000、io.weight 默认 100/范围 1–10000 以两份内核文档交叉确认；缓冲写限流需 memory+io 协同为 v2 文档明述，v1 局限两来源一致。

---

## 13-3.5 v1 hybrid vs v2 统一层级（delta 分账）

### 13-3.5.1 基线声明：本机是 v1 hybrid，v2 是现代默认

先做基线锚定与 delta 声明，避免混淆。**本机（Linux 6.18.5）运行的是 cgroup v1 hybrid 模式**：即以 v1（"legacy"）为主、同时挂了一个 v2 统一层级但基本不用于限额。而**当前主流发行版的默认早已切换到纯 cgroup v2**（Fedora 自 31、Debian 自 11、Ubuntu 自 21.10、RHEL 自 9 起默认 v2；systemd 亦以 v2 为默认与推荐）。所以本章讲 v2 时是在讲"现代默认"，讲 v1 时是在讲"本机基线与历史遗留"。这条 delta 必须明写，因为读者在自己机器上大概率看到的是纯 v2 布局，与本机实证不同。cgroup v2 的接口自内核 4.5（2016）稳定，是长期方向；v1 仍受支持但不再增新功能（⚙演进快·锚版本·随时变，发行版默认切换时间表随版本更新，核实 2026-08-01）。

### 13-3.5.2 核心差异：多层级 vs 单一统一层级

这是 v1 与 v2 最根本的结构差异。

**v1：多层级（multiple hierarchies）。** 每个（组）控制器可以挂在自己独立的一棵树上，彼此无关。于是 `/sys/fs/cgroup/` 下每个控制器一个子目录、各是一个独立挂载点。同一个进程在 cpu 树里可以属于 cgroup A、在 memory 树里属于毫不相干的 cgroup B。灵活，但复杂且容易出协同问题（如 13-4.3 说的缓冲写限流）。

**v2：单一统一层级（unified hierarchy）。** 所有控制器共用同一棵树，`/sys/fs/cgroup` 是唯一一个 cgroup2 挂载点。一个进程在整个系统里只有一个 cgroup 归属（`/proc/self/cgroup` 只有 `0::/path` 一行）。控制器不再各自为政，能天然协同。代价是灵活性降低——但实践证明多层级的灵活性弊大于利，故 v2 统一。

本机 hybrid 布局的真实证据：

```
$ grep cgroup /proc/filesystems
nodev	cgroup
nodev	cgroup2

$ mount | grep cgroup
tmpfs on /sys/fs/cgroup type tmpfs (rw,relatime)
cgroup on /sys/fs/cgroup/cpu type cgroup (rw,relatime,cpu)
cgroup on /sys/fs/cgroup/memory type cgroup (rw,relatime,memory)
cgroup on /sys/fs/cgroup/pids type cgroup (rw,relatime,pids)
cgroup on /sys/fs/cgroup/blkio type cgroup (rw,relatime,blkio)
...
cgroup2 on /sys/fs/cgroup/unified type cgroup2 (rw,relatime)
```

可见每个 v1 控制器是一个独立的 `type cgroup` 挂载（多层级），另有一个 `type cgroup2` 挂在 `/sys/fs/cgroup/unified`（hybrid 的标志）。内核同时编译进了 `cgroup` 与 `cgroup2` 两种文件系统。作为对比，**纯 v2 系统**只会有一个 `cgroup2 on /sys/fs/cgroup` 挂载，没有那些 per-controller 的 `type cgroup` 行。

### 13-3.5.3 v2 的控制器开关：cgroup.controllers 与 cgroup.subtree_control

v2 因为所有控制器共树，需要一套机制决定"每个 cgroup 上启用哪些控制器"。两个核心文件：`cgroup.controllers`（只读，列出本 cgroup **可用**的控制器）和 `cgroup.subtree_control`（读写，控制向**子** cgroup 启用哪些控制器，写 `+cpu +memory` 启用、`-cpu` 关闭）。控制器只能"自上而下"逐层启用：父的 subtree_control 里有的，子的 controllers 里才会出现。

本机 hybrid 下 v2 树只被委派了极少控制器（大部分留在 v1）：

```
$ cat /sys/fs/cgroup/unified/cgroup.controllers
cpuset hugetlb
```

即本机 v2 层级只有 cpuset、hugetlb 可用，cpu/memory/io/pids 都还在 v1 侧管——这正是 hybrid"两边分工"的样子。纯 v2 系统这里会列出全部启用的控制器（如 `cpuset cpu io memory hugetlb pids`）。

### 13-3.5.4 "无内部进程"约束（v2 特有）

v2 引入了一条 v1 没有的重要规则：**除根 cgroup 外，一个 cgroup 不能同时"拥有自己的进程"和"向子 cgroup 启用了控制器（分发资源）"**。换句话说，启用了 subtree_control 的内部（非叶）cgroup 里不能直接放进程，进程只能待在叶子节点。

这条约束初学者最容易踩。它的用意是消除 v1 里"父 cgroup 里的进程和子 cgroup 争抢同一份资源、语义含糊"的问题：v2 要求"分发资源的节点"与"消耗资源的节点"分离，资源竞争关系因此始终清晰（只在兄弟叶子之间发生）。实践中容器编排器（systemd、Kubernetes）会自动按此约束建树，但手动操作 cgroupfs 时若把进程写进一个已 `+cpu` 的内部 cgroup，就会写入失败。

### 13-3.5.5 v2 的其它改进：PSI、cgroup.freeze、cgroup.type

除结构统一外，v2 还带来若干实用能力，可作为"为什么迁 v2"的补充理由：

**PSI（Pressure Stall Information，压力阻滞信息）**：每个 cgroup 有 `cpu.pressure`、`memory.pressure`、`io.pressure`，量化"因资源不足而被迫等待的时间比例"，比单纯看用量更能反映真实过载程度，是现代自动伸缩/驱逐决策的重要输入。**冻结**：v2 用 `cgroup.freeze`（写 1 冻结整组进程、0 解冻）取代 v1 的独立 freezer 控制器。**cgroup.type**：v2 支持 threaded 类型的 cgroup，做线程级（而非进程级）的资源控制，弥补 v1 用 `tasks` 文件做线程控制被取消后的能力。此外 v2 用 eBPF（bpf 程序）取代了 v1 的 devices 控制器来做设备访问控制，net_cls/net_prio 也未在 v2 保留（改由其它机制）。这些差异在把工作负载从 v1 迁到 v2 时都可能需要适配。

### 13-3.5.6 容器与编排如何落到 v1/v2：OCI 与 Kubernetes 映射

回到工程落地。OCI Runtime Spec 在 config.json 的 `linux.resources` 下用与内核大体对应的字段声明限额（如 `cpu.shares`/`cpu.quota`/`cpu.period`、`memory.limit`、`pids.limit`、`blockIO.weight` 等），由低层运行时（runc/crun，见大主题5）负责把这些抽象字段**翻译成当前系统实际是 v1 还是 v2 的对应文件写入**。也就是说容器镜像和 spec 不关心底层是 v1 还是 v2，运行时做适配。

Kubernetes 侧（v1.36，⚙演进快·锚版本·随时变，核实 2026-08-01）：Pod 的 `requests.cpu` 落到相对份额（v1 `cpu.shares` / v2 `cpu.weight`），`limits.cpu` 落到 CFS 配额（v1 `cpu.cfs_quota_us` / v2 `cpu.max`），`limits.memory` 落到内存硬上限（v1 `memory.limit_in_bytes` / v2 `memory.max`），超限即 OOMKilled。requests/limits 与 QoS 的完整规则见大主题10；此处只需记住"K8s 的资源声明最终就是通过 cgroups 这一层落地的"，机制根仍是 L4-01 操作系统的资源管理原语。Kubernetes 自 1.25 起将 cgroup v2 支持标为 GA 并推荐使用（版本细节⚙演进快·随时变，具体最低内核/发行版要求待核当前版本文档）。

#### 来源与时效
- 内核 Documentation/admin-guide/cgroup-v2.rst（统一层级、cgroup.controllers/subtree_control、"no internal processes" 约束、PSI、cgroup.freeze、cgroup.type、eBPF devices），核实 2026-08-01。
- man cgroups(7)（v1 多层级 vs v2 单一层级、cgroup v2 挂载与 controllers 语义、hybrid 模式说明），核实 2026-08-01。
- OCI Runtime Spec v1.3.0（2025-11-04），config-linux 的 resources（cpu/memory/pids/blockIO 字段），核实 2026-08-01。
- Kubernetes 官方文档 v1.36（⚙演进快·锚版本·随时变）：requests/limits→cgroup 映射、cgroup v2 支持；发行版默认 v2 时间表（Fedora31/Debian11/Ubuntu21.10/RHEL9）为发行版公告，核实 2026-08-01。
- 本机实证：`grep cgroup /proc/filesystems`（cgroup 与 cgroup2 并存）、`mount | grep cgroup`（per-controller v1 挂载 + `cgroup2 .../unified`）、`cat /sys/fs/cgroup/unified/cgroup.controllers` → `cpuset hugetlb`，确认 v1 hybrid、v2 仅委派少量控制器。
- 待核项：weight↔shares 完整换算系数、K8s v2 最低内核/发行版硬性要求当前值——均标「待核」，不编造。
- 冲突/分歧：v1（多层级、可禁 OOM、tasks 线程控制、devices 控制器）与 v2（统一层级、不可禁 OOM 但有 oom.group、threaded cgroup、eBPF devices）取向差异，逐条两边并记。
