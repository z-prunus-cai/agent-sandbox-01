# L5-13·大主题12 容器安全边界

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01｜先修：大主题1 容器 vs VM 与隔离模型 / 大主题2 Linux namespaces / 大主题3 cgroups；L4-01 操作系统（进程、特权、系统调用）｜一手锚点：Linux man page capabilities(7)/seccomp(2)/user_namespaces(7)/namespaces(7)、Kubernetes 官方文档 Security（⚙演进快·锚 v1.36）、gVisor / Kata Containers 官方文档、NIST SP 800-190｜成熟度：capabilities/seccomp/LSM/user ns 均 GA；gVisor/Kata 沙箱 ⚙演进快·前沿

容器的"安全边界"不是一堵墙，而是"共享同一个宿主内核"这个前提下、靠 namespaces（视图隔离）、cgroups（资源隔离）、再加 capabilities/seccomp/LSM（削权与过滤）、user namespace（降权）层层收窄出来的一条**软边界**；当基础隔离不够时，才升级到 gVisor / Kata 这类"给容器再套一层内核或微 VM"的强隔离方案。

本报告与大主题1/2/3 一脉相承：大主题1 讲"容器共享内核 ≠ 轻量 VM"，大主题2/3 讲 namespaces/cgroups 这两块隔离原语；本主题讲的正是"这条隔离边界在安全上有多硬、怎么加硬"。

---

## 13-12.1 共享内核攻击面

### 13-12.1.1 共享内核意味着什么

容器与宿主机、以及同一台机器上的所有其他容器，运行在**同一个 Linux 内核实例**之上。容器只是被 namespaces 换了"视图"、被 cgroups 限了"份额"的普通宿主进程，它发起的每一次系统调用最终都进入这唯一的内核来执行。

容器里 `uname -r` 看到的内核版本，一定和宿主机完全一样，因为根本就是同一个内核。容器镜像里打包的只是用户态文件（libc、二进制、配置），从来不含内核。所以"容器里装个别的内核版本"这件事在普通容器里是做不到的。这一点是后面所有安全讨论的地基：既然内核只有一个且被大家共享，那么这个内核就是所有容器共同的**信任根**，也是共同的**单点风险**。

本机可直接看到"容器进程视角切换但内核不变"的机制根——`unshare` 创建新 namespace 后仍是同一内核在服务：

```
unshare --user --map-root-user sh -c 'echo "inside uid=$(id -u)"; uname -r'
```

在本沙箱（kernel 6.18.5）里，无论进不进 namespace，`uname -r` 都是 6.18.5。

### 13-12.1.2 攻击面：系统调用接口与容器逃逸

因为内核共享，容器的核心攻击面就是**内核对用户态暴露的系统调用接口**（Linux 有 300 多个 syscall）。容器里的进程只要能触发内核里某个可利用的漏洞（syscall 实现 bug、驱动 bug、竞态条件等），就有可能突破 namespace/cgroup 的隔离，在宿主内核上下文里执行代码——这就是**容器逃逸（container escape）**。逃逸成功意味着攻击者从"一个容器内"变成"控制宿主机 / 其他容器"。

VM 的攻客要攻破的是很窄的虚拟硬件接口（hypervisor），而容器的攻客面对的是整个 syscall 表——面积大得多。历史上有代表性的两类逃逸：一类是**内核漏洞**（如 Dirty COW，CVE-2016-5195，一个 copy-on-write 竞态可提权），容器里也能利用，因为用的是同一个有漏洞的内核；另一类是**运行时/配置漏洞**（如 runc 的 CVE-2019-5736，通过覆写宿主机上的 runc 二进制实现逃逸）。这些 CVE 编号仅作历史示例说明"逃逸真实存在"，具体细节归安全专题；这里要记住的结论是：**攻击面大 = 需要主动收窄**，这正是 13-12.2 之后所有手段存在的理由。

很多人以为"跑在容器里就天然安全，恶意程序出不来"，这是不对的。默认配置下容器只是"隔离"，不等于"沙箱"；隔离能防止误操作互相干扰，但面对蓄意攻击，共享内核这条边界是可以被攻破的。

### 13-12.1.3 与 VM 的边界对比

虚拟机给每个 guest 一个**独立的内核**，guest 与 host 之间隔着 hypervisor（和硬件虚拟化支持）。攻击者要从 guest 打到 host，必须攻破 hypervisor 这层——它暴露的接口比整个 syscall 表窄得多，因此"逃逸难度"通常显著高于容器。

这解释了业界的一句常识：**VM 的隔离边界比容器"硬"，但代价是每个 VM 要跑一整个内核、启动慢、内存占用大**；容器边界"软"但轻快。所以选择不是"谁更好"，而是"这份工作负载值得多硬的边界、能付多少开销"。这条"隔离强度 vs 开销"的谱系正是大主题1 讲过的隔离谱系，本主题在它两端之间又补上了 gVisor/Kata 这样的"中间档"（见 13-12.4）。

说 VM 边界"更硬"是相对的，不是绝对安全——hypervisor 逃逸漏洞同样存在，只是更罕见、攻击面更小。安全是概率与成本问题，不是非黑即白。

### 13-12.1.4 隔离面即安全边界（回指 namespaces / cgroups）

容器的安全边界在机制上就等于它的**隔离面**：namespaces 决定"容器能看见什么"（进程、网络、挂载、用户等视图），cgroups 决定"容器能用多少资源"（CPU、内存、PID 数等）。逃逸的本质，就是突破这些 namespace/cgroup 的约束、把手伸到边界之外的宿主资源上。

因此安全加固的思路自然分两条腿：一条是**加固隔离面本身**（比如用 user namespace 让"容器内 root"不再等于"宿主 root"，见 13-12.3）；另一条是**在隔离面之上再削权**（capabilities 砍掉容器进程本不需要的特权、seccomp 挡住它本不该调的 syscall、LSM 施加强制访问控制，见 13-12.2）。这两条腿都建立在大主题2/3 的原语之上——理解 namespaces/cgroups，就理解了这条边界"是什么"；本主题讲的是这条边界"怎么被攻、怎么加硬"。

cgroups 主要防的是"吵闹邻居"（一个容器把 CPU/内存吃光拖垮别人，属可用性/DoS 面），namespaces + 削权手段主要防的是"越权与逃逸"（机密性/完整性面）。两者共同构成完整的边界，缺一不可。

#### 来源与时效
- Linux man page namespaces(7)（视图隔离原语的权威定义，随内核演进但核心稳定）；核实 2026-08-01。
- NIST SP 800-190《Application Container Security Guide》（共享内核攻击面、逃逸风险的权威系统性论述，一手政府指南）；核实 2026-08-01。
- Liz Rice《Container Security》（O'Reilly，容器逃逸与内核共享的教材级论述，准一手）；核实 2026-08-01。
- 本机实证：kernel 6.18.5，`unshare` 内外 `uname -r` 一致，佐证"内核共享"；核实 2026-08-01。
- 冲突/分歧：无实质分歧。CVE-2016-5195 / CVE-2019-5736 仅作历史示例，非本报告承重结论；细节以对应 CVE 官方通告为准（此处不逐一核对编号外的技术细节，标「示例·细节待核」）。

---

## 13-12.2 能力 / seccomp / LSM 收窄

### 13-12.2.1 capabilities：把 root 的全能拆成细粒度权限

传统 Unix 里 "root（uid 0）" 是一个全能账户——要么全有、要么全无。Linux capabilities（capabilities(7)）把 root 的超级权限切成了几十个独立的"能力单元"，每个单元管一类特权操作。例如：

```
CAP_NET_ADMIN     配置网络接口/路由/防火墙
CAP_NET_BIND_SERVICE  绑定 <1024 的特权端口
CAP_SYS_ADMIN     一大批管理操作（挂载、setns 等），权限极广
CAP_CHOWN         改变文件属主
CAP_SYS_TIME      修改系统时钟
```

一个进程不必是"全权 root"才能干活，它只需要它真正需要的那几个 capability。把其余的全部去掉，即使这个进程被攻陷，攻击者能利用的特权也被限制在很小的范围里。这就是"最小权限原则"在容器里的落地。

辅助理解：每个进程有几组 capability 集合——Permitted（允许拥有的）、Effective（当前生效的）、Inheritable/Ambient（跨 exec 传递用）、Bounding（上界，能力天花板）。初学者只需抓住"Effective = 此刻真正能用的权限；Bounding = 无论如何都拿不到超过它的权限"。本机可直接读出当前进程的能力位掩码：

```
grep Cap /proc/self/status
```

本沙箱输出 `CapEff: 000001fffeffffff`（一个十六进制位掩码，每一位对应一个 capability）。容器运行时做的事，就是在启动进程前把这个掩码收窄。

### 13-12.2.2 默认丢弃与危险 capability（CAP_SYS_ADMIN）

容器运行时并不会让容器里的 root 拿到宿主 root 的全部 capability。以 Docker 为例，它**默认只保留一组少量 capability、丢弃其余全部**。这组默认保留集合大约 14 个，代表性的有：

```
CHOWN, DAC_OVERRIDE, FOWNER, FSETID, KILL,
SETGID, SETUID, SETPCAP, NET_BIND_SERVICE, NET_RAW,
SYS_CHROOT, MKNOD, AUDIT_WRITE, SETFCAP
```

这里必须分账：**"默认保留哪些"是容器运行时（Docker/containerd 等）的策略，不是 Linux 内核语义**；具体名单与数量会随运行时版本微调，上面这组是 Docker 长期默认（精确成员/数量随版本变，标「⚙演进快·锚 Docker 默认·待核精确名单」）。Kubernetes 则不预设这套 Docker 默认，而是让你在 Pod 的 `securityContext.capabilities` 里显式 `drop`/`add`。

其中最需要警惕的是 **CAP_SYS_ADMIN**，它被戏称为"新的 root"——因为它涵盖的操作极多（挂载文件系统、`setns` 进入别的 namespace 等），一旦容器持有它，逃逸难度大幅下降。安全基线的通用建议是：**默认 drop 掉 ALL，再按需 add 回最少的几个**，且尽量永不授予 CAP_SYS_ADMIN。K8s 里的写法：

```
securityContext:
  capabilities:
    drop: ["ALL"]
    add: ["NET_BIND_SERVICE"]
```

`--privileged`（Docker）/ `privileged: true`（K8s）会把几乎所有 capability、并关闭 seccomp/AppArmor 一起放开，等于拆掉大半安全边界——生产中应视为高危、能不用就不用。

### 13-12.2.3 seccomp：过滤系统调用

seccomp（secure computing，seccomp(2)）让进程给内核装一个**系统调用过滤器**：进程声明"我只允许调用这批 syscall，其余的一律拒绝（返回错误 / 直接杀进程）"。现代用法是 seccomp-BPF 模式（`SECCOMP_SET_MODE_FILTER`），用一段 BPF 程序按 syscall 号和参数决定放行还是拦截。

13-12.1 说过攻击面是整张 syscall 表，seccomp 就是直接**缩小这张表**。绝大多数应用一辈子用不到 `mount`、`reboot`、`kexec_load`、`ptrace` 等危险 syscall，把它们挡在门外，就等于提前堵死了很多利用内核漏洞的路径。

Docker 自带一个**默认 seccomp profile**，它以"白名单大多数、屏蔽危险少数"为策略，屏蔽掉数十个高危/罕用 syscall（Docker 文档常引用"约屏蔽 44 个 / 300 多个中"，具体数字随版本变，标「待核」）。Kubernetes 侧对应的是 seccomp profile 类型：

```
securityContext:
  seccompProfile:
    type: RuntimeDefault   # 用容器运行时的默认 profile
```

且 K8s 提供 `SeccompDefault` 特性，可让节点上所有 Pod 默认套用 `RuntimeDefault`（该特性约自 v1.27 起 GA，标「⚙演进快·锚 v1.36·精确起始版本待核」）。本沙箱当前进程未装过滤器（`grep Seccomp /proc/self/status` 显示 `Seccomp: 0`），正好说明"seccomp 是要显式开启的加固，默认裸进程没有"。

seccomp 只管"能不能调这个 syscall"，不管"调用后访问哪个具体对象"——那是 LSM 的活。二者互补，不是替代。

### 13-12.2.4 LSM：AppArmor 与 SELinux（强制访问控制）

LSM（Linux Security Modules）是内核里的一套**安全钩子框架**，在关键操作（打开文件、执行程序、网络访问等）处插入检查点，让具体的安全模块决定"允许还是拒绝"。容器领域最常用的两个 LSM 是 **AppArmor** 和 **SELinux**，它们实现的是**强制访问控制（MAC）**——由系统策略统一强制，进程自己（即便是 root）也不能绕过或放宽。

两者风格不同：

```
AppArmor  基于「路径」的 profile：规定某程序能读/写哪些路径、用哪些能力
SELinux   基于「标签/类型」的强制：给进程和资源打 type 标签，按策略矩阵放行
```

capabilities 和 seccomp 控制"能力"和"能调哪些 syscall"，而 LSM 进一步约束"能对哪些具体资源做什么"。比如 AppArmor 可以规定容器进程即使有写权限也不能写宿主的某些敏感路径；SELinux（在 RHEL 系常见）给每个容器进程打上如 `container_t` 的类型标签，让它默认碰不到宿主上打着别的标签的文件。Docker 默认会套一个 `docker-default` 的 AppArmor profile；启用 SELinux 的发行版上运行时会自动给容器打标签。

**LSM 是否可用、用哪个，取决于宿主内核配置与发行版**——Ubuntu 系默认 AppArmor，RHEL/Fedora 系默认 SELinux，同一时刻通常只主用其一。本沙箱内核未暴露 `/sys/kernel/security/lsm`、也无 AppArmor 模块、无 `getenforce`（SELinux）——即"本机未启用可观测的 LSM"，本项因此以 man page/官方文档为据说明，未做本机 LSM 实证（如实标注，不臆造本机行为）。

### 13-12.2.5 K8s 落地：securityContext 与 Pod Security Standards

在 Kubernetes 里，上面这些收窄手段的入口统一是 Pod / 容器的 `securityContext`（设置 `runAsNonRoot`、`capabilities`、`seccompProfile`、`readOnlyRootFilesystem`、`allowPrivilegeEscalation` 等），以及集群级的 **Pod Security Standards（PSS）** 三档基线：

```
Privileged   不限制，最宽松（等于不设防）
Baseline     阻止已知的明显提权（禁 privileged、禁危险 hostPath 等）
Restricted   最严：强制非 root、drop ALL capability、RuntimeDefault seccomp 等
```

这三档由 **Pod Security Admission（PSA）** 这个内置准入控制器在名字空间级别强制执行（用 label 给 namespace 标 enforce/audit/warn 模式）。它取代了早期的 PodSecurityPolicy（PSP，已在 v1.25 移除）。这是把前四项"内核级收窄手段"包装成"运维可声明、可批量强制的策略"的一层。

securityContext 是"单个 Pod 怎么设防"，PSS/PSA 是"整个 namespace 强制所有 Pod 至少达到某档设防"。二者配合，才能保证一个团队里没人偷偷跑 `privileged` 容器。这一切都随 K8s 版本演进（标「⚙演进快·锚 v1.36」），具体字段默认值以对应版本官方文档为准，未证实处标「待核」。

#### 来源与时效
- Linux man page capabilities(7)（capability 集合模型与各 CAP 语义，一手）；seccomp(2)（seccomp-BPF 过滤模型，一手）；核实 2026-08-01。
- Kubernetes 官方文档 Security / Security Contexts / Pod Security Standards / Pod Security Admission（⚙演进快·锚 v1.36，一手）；PSP 于 v1.25 移除、PSA 取代之；核实 2026-08-01。
- Docker 官方文档（默认 capability 集合、默认 seccomp profile、docker-default AppArmor profile；属"运行时策略"非内核语义，随版本变）；核实 2026-08-01。
- 本机实证：`grep Cap /proc/self/status` → `CapEff 000001fffeffffff`；`grep Seccomp /proc/self/status` → `Seccomp 0`（裸进程无过滤器）；本机未启用可观测 LSM（无 `/sys/kernel/security/lsm`、无 AppArmor 模块、无 getenforce）；核实 2026-08-01。
- 待核：Docker 默认保留 capability 的精确名单/数量、默认 seccomp profile 屏蔽的确切 syscall 数（"约 44"随版本变）、K8s `SeccompDefault` GA 的精确起始版本（约 v1.27）。
- 冲突/分歧：无实质规范冲突；"默认保留/屏蔽哪些"是运行时策略差异（Docker vs containerd vs K8s RuntimeDefault 各有默认），已按"运行时策略 ≠ 内核语义"分账。

---

## 13-12.3 rootless / user ns 强化

### 13-12.3.1 user namespace 与 uid/gid 映射

user namespace（user_namespaces(7)）是八种 namespace 里专门隔离**用户与组 ID**的一种。它的关键能力是**uid/gid 映射**：一个进程可以在 namespace 内部是 uid 0（root），而这个"内部 root"被映射成宿主机上的一个**普通非特权 uid**。映射关系写在 `/proc/<pid>/uid_map` 和 `gid_map` 里，格式为三列：

```
容器内起始ID   宿主机起始ID   映射长度
```

13-12.1 说逃逸的最坏后果是"变成宿主 root"。有了 user ns，"容器内 root" ≠ "宿主 root"——即使攻击者在容器里是 root、甚至逃逸出去，他在宿主上也只是那个被映射到的普通用户，能造成的破坏被大幅压缩。这是把"逃逸后的爆炸半径"从核弹级降到手枪级的手段。

本机实证（本沙箱可用 unshare）：

```
unshare --user --map-root-user sh -c 'echo "inside uid=$(id -u):$(id -g)"; cat /proc/self/uid_map'
```

输出为 `inside uid=0:0` 且 uid_map 为 `0 0 1`。解读：进程在新 user ns 里看到自己是 uid 0，`--map-root-user` 把"容器内 0"映射到"调用者的宿主 uid"。本沙箱调用者本身是 root（宿主 uid 0），所以显示映射到 0；换成一个普通用户运行同一条命令，第二列就会是那个普通用户的真实 uid——那才是 user ns 降权的典型样子。这个细节如实说明，不美化本机现象。

### 13-12.3.2 命名空间化的 capabilities

user namespace 的一个精妙点：进入一个新的 user ns 后，进程会在**这个 ns 内**获得一整套 capability（成为该 ns 的"完全 root"），但这些 capability **只对该 ns 所拥有/管辖的资源有效**，对 ns 外的宿主资源无效。也就是说"root 是被命名空间化的"。

本机可直接看到这个现象——对比进入 user ns 前后的有效能力位：

```
grep CapEff /proc/self/status                                   # ns 外
unshare --user --map-root-user sh -c 'grep CapEff /proc/self/status'   # ns 内
```

本沙箱 ns 外为 `000001fffeffffff`，进入 user ns 后变成 `000001ffffffffff`（位更满，成了该 ns 的"满能力 root"）。关键理解：**位掩码更满不代表更危险**——这些能力被局限在新 user ns 内，比如它可以在这个 ns 里 `mount`、可以创建子 namespace，但不能拿这套能力去动宿主上不属于该 ns 的文件或设备。这正是 rootless 容器"让非特权用户也能创建看似完整的容器环境"的底层原理：非特权用户先造一个 user ns，在里面他是 root，就能继续 unshare 出 mnt/net/pid 等其他 namespace 来搭容器。

初学者看到"容器里我是 root、还能 mount"就以为拿到了宿主特权——其实那是 user ns 内的局部 root，出了这个 ns 什么都不是。

### 13-12.3.3 rootless 容器

**rootless 容器**指的是**容器运行时本身（以及它启动的容器）都以宿主机上的普通非特权用户身份运行**，全程不需要宿主 root。它正是建立在 13-12.3.1/2 的 user ns 之上：普通用户借 user ns 在容器内获得 root 体验，同时宿主侧不暴露真正的 root 权限。Podman 以 rootless 为主打，Docker 也支持 rootless 模式。

要跑 rootless，宿主要给用户分配一段可映射的"子 uid/gid"范围，配置在 `/etc/subuid`、`/etc/subgid`：

```
cat /etc/subuid    # 形如 ubuntu:100000:65536，表示 ubuntu 可映射 100000 起的 65536 个 uid
```

本沙箱这两个文件存在且含 `ubuntu:100000:65536`、`pgtest:165536:65536` 等条目，说明 subuid/subgid 机制在位。容器内的 uid 就被映射进这段范围，从而"容器内的一堆用户"对应到"宿主上一段本就属于该普通用户的无害 uid"。

安全收益：即便容器被攻破，攻击者拿到的是一个普通用户在其子 uid 段内的权限，动不了宿主系统文件、装不了内核模块。此外 rootless 还顺带减小了"运行时守护进程以 root 常驻"这一历史攻击面。代价是若干功能受限（见下）。

### 13-12.3.4 局限与残余风险

rootless / user ns 不是万能盾。要点：

第一，**它降低的是"逃逸后的权限"，没有消除逃逸本身**——共享内核这条边界还在（13-12.1），一个足够强的内核漏洞仍可能被利用；user ns 只是让得手后的收益变小。

第二，**user namespace 自身也扩大过内核攻击面**：允许非特权用户创建 user ns，等于让更多内核代码路径对非特权用户开放，历史上出过若干与之相关的提权 CVE；因此一些发行版会限制非特权 user ns 的创建（相关 sysctl 开关随内核/发行版不同而不同，本沙箱未见 `unprivileged_userns_clone` 该项，具体开关标「待核·因发行版而异」）。这是一把双刃剑：它是加固手段，本身也需被谨慎管理。

第三，**功能受限**：rootless 下无法直接做需要真 root 的操作（如绑定 <1024 端口需额外配置、某些网络/存储驱动要用 fuse-overlayfs、slirp4netns 等用户态替代方案，性能与兼容性略有折损）。

user ns / rootless 是"纵深防御"的重要一层，应尽量启用，但要和 13-12.2 的 capabilities/seccomp/LSM 叠加使用，而不是指望单靠它一层就安全。

#### 来源与时效
- Linux man page user_namespaces(7)（uid/gid 映射、能力命名空间化语义，一手）；namespaces(7)（总览，一手）；核实 2026-08-01。
- Podman / Docker rootless 官方文档（subuid/subgid、fuse-overlayfs、slirp4netns 等用户态方案；⚙演进快，随版本变）；核实 2026-08-01。
- 本机实证：`unshare --user --map-root-user` 观察到 inside uid=0、uid_map `0 0 1`（本机以 root 运行故映射到 0，已注明非典型降权样例）；ns 内 CapEff `000001ffffffffff` vs ns 外 `000001fffeffffff`；`/etc/subuid` 含 `ubuntu:100000:65536`；核实 2026-08-01（util-linux 2.39.3，kernel 6.18.5）。
- 待核：限制非特权 user ns 创建的具体 sysctl 开关名（随内核版本/发行版而异，本沙箱未见旧式 `unprivileged_userns_clone`）；rootless 各驱动的性能折损量级。
- 冲突/分歧：无规范冲突。安全立场上存在张力（user ns 既加固又扩内核攻击面），本报告两面都记，不和稀泥。

---

## 13-12.4 gVisor / Kata 沙箱

> 本小主题为 ⚠前沿·快速演进领域；性能开销、成熟度等具体数值随版本大幅变动，凡未逐一核到官方最新数据处一律标「待核」，不作定论。

### 13-12.4.1 为何需要更强隔离

前三节的手段（capabilities/seccomp/LSM/user ns）都在"共享内核"这个前提下收窄边界——它们让攻击更难、逃逸后收益更小，但**没有改变"大家共用一个内核"这个根本事实**。对于多租户、跑不受信任代码（如公有云函数、CI 里跑用户提交的代码）等高风险场景，人们希望把边界做得更接近 VM 那么硬，同时尽量保留容器的轻快。

由此出现两条"加内核层"的思路：一是**在容器和宿主内核之间插一个用户态的"应用内核"来接管 syscall**（gVisor）；二是**给每个容器/Pod 套一个自带内核的轻量虚拟机**（Kata）。二者都对上兼容 OCI/容器生态（可当作一种容器运行时接入），对下大幅收窄或替换了宿主内核这条攻击面。

### 13-12.4.2 gVisor：用户态应用内核

gVisor（Google 开源，runtime 名 `runsc`）的核心是一个**用 Go 写的用户态"应用内核" Sentry**：容器发出的系统调用被拦截并重定向到 Sentry，由 Sentry 在用户态实现大部分内核语义，只在必要时以极受限的方式回调宿主内核。文件访问走一个叫 Gofer 的独立代理进程。拦截 syscall 的方式有多种平台（如基于 ptrace、KVM、systrap 等，随版本演进）。

宿主内核不再直接面对容器里几百个 syscall，而是只面对 Sentry 发出的一小撮、且经过 Sentry 检查的调用——**攻击面被大幅收窄**，即使 Sentry 有 bug，它自己也跑在 seccomp 等限制下。代价是：syscall 要多绕一层用户态实现，**syscall 密集 / I/O 密集型负载会有明显性能开销**，且 gVisor 的应用内核不是 100% 覆盖 Linux 全部 syscall/特性，个别程序会不兼容。具体开销与兼容性覆盖率随版本变化很大，标「待核·⚙演进快」。

gVisor = "在用户态给容器仿一个内核，让真内核少挨刀"。

### 13-12.4.3 Kata：微 VM 强隔离

Kata Containers 走的是另一条路：**每个容器（或每个 Pod）跑在一个轻量级虚拟机（microVM）里，拥有自己的 guest 内核**，借助硬件虚拟化（底层 VMM 可用 QEMU、Cloud Hypervisor、Firecracker 等）。对 K8s/OCI 而言它表现得像一个普通容器运行时（通过 CRI 接入），但隔离强度接近 VM。

因为每个容器有独立 guest 内核、并隔着 hypervisor，容器逃逸要先攻破 guest 内核、再攻破 hypervisor 才能碰到宿主——回到了 13-12.1.3 讲的"VM 式硬边界"。代价是每个微 VM 要跑一个内核，**内存/启动开销高于普通容器**（虽然 Firecracker 等专为快启动优化过），且需要宿主支持硬件虚拟化（嵌套虚拟化环境下可能不可用）。具体启动时延/内存占用随 VMM 与版本差异很大，标「待核·⚙演进快」。

gVisor 是"软件仿内核，省内核不省 CPU"；Kata 是"真给一个内核，隔离最硬但最重"。

### 13-12.4.4 权衡与成熟度

把四类隔离放到大主题1 的隔离谱系上，从软到硬、从轻到重大致是：

```
普通进程  <  普通容器(ns+cgroup+削权)  <  gVisor(用户态应用内核)  <  Kata(微 VM)  <  传统 VM
```

跑自己可信的代码，普通容器 + 13-12.2/3 的加固通常够用；跑不受信任的多租户代码或强合规场景，才值得为 gVisor/Kata 付性能与运维成本。没有"最好"，只有"这份负载值得多硬的边界、能付多少开销"。

成熟度与时效（务必谨慎）：gVisor、Kata 均为活跃开源项目、在生产有真实使用（如公有云的沙箱化函数/容器），但两者都处于**快速演进**中，版本、平台支持、性能特性变化频繁。本报告不给具体版本号与性能百分比——这些属「待核·随时变·锚版本」，需以查询时的官方文档/发布说明为准，凡见到二手来源给出的"开销 X%""快 Y 倍"之类数字，未经官方核对一律视为 ⚠不承重。K8s 侧通过 RuntimeClass 选择这类运行时（`runtimeClassName` 指向 runsc/kata），该机制本身已 GA，但底层沙箱项目成熟度独立演进。

#### 来源与时效
- gVisor 官方文档（gvisor.dev，Sentry/Gofer 架构、平台、兼容性；⚠前沿·⚙演进快，一手但快速变化）；核实 2026-08-01。
- Kata Containers 官方文档（katacontainers.io，microVM/guest kernel/VMM 选项；⚠前沿·⚙演进快，一手但快速变化）；核实 2026-08-01。
- Kubernetes 官方文档 RuntimeClass（选择 runsc/kata 等运行时的机制，⚙演进快·锚 v1.36，一手）；核实 2026-08-01。
- 交叉印证：NIST SP 800-190 / Liz Rice《Container Security》对"沙箱化容器运行时作为强隔离选项"的定位（准一手，佐证方向不佐证具体数值）；核实 2026-08-01。
- 待核（明确不编造）：gVisor 各平台性能开销、syscall/特性兼容覆盖率；Kata 启动时延与内存开销、各 VMM 差异；两项目当前稳定版本号。凡具体数值均需查询时以官方发布说明为准。
- 冲突/分歧：二手来源对 gVisor/Kata 的性能数字分歧大且时效性强，本报告不选边、不引用具体数字，统一标「待核」。
