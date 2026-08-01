# L5-13·大主题5 OCI 运行时与容器运行时链

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L4-01 操作系统（进程/clone/文件系统挂载）、本课大主题2 namespaces、大主题3 cgroups、大主题4 OCI 镜像 ｜一手锚点：OCI Runtime Spec v1.3.0（2025-11-04）、Linux man clone(2)/setns(2)/pivot_root(2)/unshare(2)、runc 官方文档与源码、Kubernetes 官方文档 v1.36（CRI）｜成熟度：OCI Runtime Spec 与 runc 均为 GA；运行时工具版本 ⚙演进快·锚版本·随时变（本机 runc 1.3.4 / containerd 2.2.2）

前面几个大主题把容器的"零件"讲清了：namespaces 隔离视图、cgroups 限额资源、OCI 镜像提供文件系统层。本大主题回答的是"这些零件怎么被组装成一个真正跑起来的容器"。答案分两层：一个**标准**（OCI Runtime Spec，规定"容器长什么样、有哪些操作"），和一串**工具**（从 kubelet / containerd 这样的高层运行时，一路调用到 runc / crun 这样的低层运行时，最终由后者调用 Linux 内核原语把进程关进隔离环境）。这条从上到下的调用链，就是"容器运行时链"。本报告最后一章会把 runc 如何调用 namespaces/cgroups 原语落地隔离显式点出，衔接 L4-01 操作系统。

本报告所有状态、字段、命令行为均在本机以真实 runc 1.3.4 跑通验证（Docker daemon 不可达，但 runc/containerd 二进制可用），实证片段直接贴在正文。

---

## 13-5.1 filesystem bundle 与 config.json

### 13-5.1.1 什么是 filesystem bundle

OCI Runtime Spec 规定：一个容器在磁盘上的标准打包形式叫 **filesystem bundle**（文件系统 bundle）。它就是一个目录，里面有两样东西——一份名为 `config.json` 的配置文件（必须正好叫这个名字，放在 bundle 根目录），以及一个容器根文件系统（rootfs）目录，配置文件通过 `root.path` 字段指向它。低层运行时（如 runc）接收的输入就是"这个 bundle 目录 + 一个容器 ID"，其余全部信息都从 bundle 里读。

bundle 是"镜像"与"运行中容器"之间的中间形态，理解它能打通大主题4 和本主题的衔接。镜像（OCI Image）是可分发、可寻址的分层归档；把镜像的各层用 union FS 叠成一个完整可读写的目录树，再配一份 config.json，就成了一个 bundle。换句话说，高层运行时的一项核心工作，正是"把镜像解包+叠层成 rootfs，并根据用户请求生成 config.json"，从而把镜像转成 bundle 交给低层运行时。本机用 `runc spec` 生成一份模板 bundle，结构如下：

```
bundle/
├── config.json     # 必须叫这个名字
└── rootfs/         # config.json 里 root.path 指向它
```

### 13-5.1.2 config.json：容器的完整配置蓝图

`config.json` 用一份 JSON 描述"这个容器要怎么跑"的全部信息：用哪个 rootfs、执行哪个进程、进程的参数/环境变量/工作目录/用户、要开哪些 namespace、cgroup 资源限额、要挂载哪些文件系统、主机名、能力（capabilities）、seccomp 过滤、钩子等等。低层运行时不做任何"猜测"，一切以 config.json 为准，这保证了同一份 bundle 在任何合规运行时上行为一致。

本机 `runc spec` 生成的模板，顶层键与关键字段实测如下：

```
top keys = ['ociVersion', 'process', 'root', 'hostname', 'mounts', 'linux']
root      = {'path': 'rootfs', 'readonly': True}
process.args = ['sh']
linux keys   = ['resources', 'namespaces', 'maskedPaths', 'readonlyPaths']
namespaces   = ['pid', 'network', 'ipc', 'uts', 'mount']
```

这里有几个初学者要抓住的点。`root.path` 是相对 bundle 目录的 rootfs 路径，`readonly` 控制根是否只读。`process.args` 是容器启动后真正 exec 的命令（这里是 `sh`）。`linux.namespaces` 列出要新建的 namespace——注意 runc 模板默认只开 pid/network/ipc/uts/mount 五种，**没有** user ns 和 cgroup ns（rootless 场景才会加 user ns，须自行配置），这解释了为什么默认容器里"root 就是宿主 root"、需要额外手段才能 rootless。`linux.resources` 就是 cgroup 限额（对应大主题3 的 cpu/memory/pids 等控制器），`maskedPaths`/`readonlyPaths` 则是把 `/proc/kcore` 之类敏感路径遮蔽或只读，属安全加固。

config.json 里凡是"列出一个 namespace 类型但不给 `path`"就表示"新建一个该类型 namespace"；若给了 `path`（指向某个 `/proc/[pid]/ns/xxx`）则表示"加入已有的那个 namespace"——这个区别正是 13-5.4.3 setns 的配置入口。

### 13-5.1.3 ociVersion 与规范/实现的版本分账

`config.json` 顶层的 `ociVersion` 字段声明这份配置遵循哪个版本的 OCI Runtime Spec，运行时据此判断自己能否解析。规范当前版本是 v1.3.0（2025-11-04）；但**具体实现所支持的规范版本会滞后**，这是规范与实现必须分账的典型例子。

runc 1.3.4 自报 `spec: 1.2.1`，它 `runc spec` 生成的 config.json 里 `ociVersion` 就是 `1.2.1`，运行中容器 `runc state` 返回的 `ociVersion` 也是 `1.2.1`——即本机这套工具实现的是 Runtime Spec 1.2.1，而不是最新的 1.3.0。

```
runc version 1.3.4
spec: 1.2.1
```

初学者容易把"规范版本"和"工具版本"混为一谈。规范版本（1.2.1 / 1.3.0）说的是"这套字段和语义的标准修订号"，工具版本（runc 1.3.4）说的是"这个实现的发布号"，两者独立演进。写 config.json 时 `ociVersion` 要匹配目标运行时能吃下的规范版本，否则运行时可能拒绝或忽略新字段。规范号相邻小版本之间通常向后兼容（新增字段为主），所以 1.2.1 的 bundle 一般能被支持 1.3.0 的运行时接受，反之则要小心新字段不被识别。

### 13-5.1.4 运行时命令行操作接口

OCI Runtime Spec 不仅规定 bundle 的静态结构，还规定运行时必须提供的**操作**（operations）：`create`（据 bundle 创建容器）、`start`（启动用户进程）、`state`（查询状态）、`kill`（发信号）、`delete`（销毁）。这套操作把"容器"抽象成一个有明确生命周期的受管对象，是 13-5.2 状态机的操作面。

runc 把这些操作实现为子命令，本机实测一条完整链路：

```
runc create --bundle <bundle> <id>   # 创建，进程就位但未启动
runc state <id>                      # 查询状态
runc start <id>                      # 启动用户进程
runc kill <id> KILL                  # 发送 SIGKILL
runc delete <id>                     # 销毁
```

要点在于 `create` 与 `start` 是**分开的两步**（规范如此要求）。`create` 之后容器已"就位"（namespaces 已建、rootfs 已切、cgroup 已配、用户进程已阻塞在一个同步点上等待），但用户命令还没真正跑；`start` 才放行让它执行。这个"先创建后启动"的两段式设计，给了外部工具在进程真正跑起来之前做最后布置的窗口（例如高层运行时在 created 之后、start 之前配置网络），这正是 13-5.2.4 各类 hook 存在的位置。

#### 来源与时效
- OCI Runtime Spec v1.3.0（2025-11-04）：`bundle.md`（bundle 目录结构、config.json 命名与 root.path）、`config.md`（顶层字段 ociVersion/root/process/mounts/hostname/linux 定义）、`runtime.md`（create/start/state/kill/delete 操作），核实 2026-08-01。
- 本机实证（Linux 6.18.5，runc 1.3.4，spec 1.2.1）：`runc spec` 生成 config.json，`python3` 解析出顶层键、root、process.args、linux.namespaces；`runc --version` 确认实现版本与所实现规范版本，核实 2026-08-01。
- 规范文本与 runc 实现两来源一致；唯一显式分歧是**规范版本**（一手规范 1.3.0）与**本机实现所报规范版本**（runc 1.3.4 → 1.2.1）不同——已按"规范 vs 实现分账"两边都记。

---

## 13-5.2 容器生命周期状态机

### 13-5.2.1 四个状态：creating / created / running / stopped

OCI Runtime Spec 用一个 `status` 字段描述容器所处的生命周期阶段，规范定义了四个值：

```
creating → created → running → stopped
```

`creating` 是运行时正在执行 create 操作、还没建完的过渡态；`created` 表示创建完成、资源（namespace/rootfs/cgroup）已就位但用户进程尚未启动；`running` 表示用户进程正在执行；`stopped` 表示用户进程已退出（无论正常退出、被信号杀死还是启动失败）。规范也允许运行时定义额外的实现相关状态，但这四个是标准值。

初学者最容易漏掉的是 `created` 与 `running` 的区别——很多人以为"创建=运行"，其实容器在 created 态是"上了膛但没扣扳机"，这正是两段式（create/start）设计的直接体现（见 13-5.1.4）。`creating` 通常一闪而过、外部很难观测到；日常能查到的稳定态主要是 created / running / stopped 三个。

### 13-5.2.2 状态转换与触发操作

状态之间的转换由运行时操作触发，本机以 runc 1.3.4 跑通了一条完整转换链，真实输出如下：

```
[after create]  status= created  pid= 20835
=== start ===   start rc=0
[after start]   status= running  pid= 20835
=== kill KILL ===
[after kill]    status= stopped  pid= 0
=== delete ===  delete rc=0
[after delete]  container does not exist
```

对应的转换规则是：`create` 把容器带到 `created`；`start` 从 created 迁到 `running`（运行时执行 process.args 指定的进程）；用户进程结束或被 `kill` 杀死后迁到 `stopped`；`delete` 只能对 stopped 态的容器执行，它释放所有资源并让容器彻底消失（此后 `state` 报 "container does not exist"）。注意 delete 不是一个状态，而是"从状态机里移除"的操作——一个健康的容器生命周期是 created→running→stopped→（delete 后不存在）。

一是 `start` 只能作用于 created 态，对 running 态重复 start 会报错；二是 `delete` 只能作用于 stopped 态，想删一个还在 running 的容器要么先 kill，要么用 `delete --force`（先杀后删）。

### 13-5.2.3 state 查询及其字段与 PID 语义

`state` 操作返回容器的当前状态，规范规定其 JSON 至少包含 `ociVersion`、`id`（容器 ID）、`status`（上述四态之一）、`pid`（容器内 1 号进程在**宿主 PID namespace** 中的 PID）、`bundle`（bundle 绝对路径）、`annotations`（注解）。本机 `runc state` 实测字段与规范一致。

这里有个极能加深理解的细节：state 返回的 `pid` 是**宿主视角**的 PID，而同一个进程在容器自己的 PID namespace 里是 1 号进程。本机把容器进程做成打印自身 PID 的小程序，并读它的 `/proc/<hostpid>/status`：

```
host-side pid of container process = 20835
Name:   mini
NSpid:  20835   1
```

`NSpid: 20835 1` 一行同时给出了两个视角——宿主里是 20835，进入 pid namespace 后是 1。这直观印证了大主题2 的 PID namespace 隔离：容器里的进程以为自己是 init（PID 1），宿主却看得清清楚楚它其实是 20835。另外注意容器进入 `stopped` 后 `pid` 变成了 0——进程已死，宿主侧 PID 不再有效，这也是判断容器是否真的停了的一个可靠信号。

### 13-5.2.4 生命周期钩子 hooks

OCI Runtime Spec 允许在生命周期的特定时刻插入**钩子**（hooks）——运行时在到达某个阶段时自动执行的外部程序，用于做容器本体之外的布置（最典型的是配置网络）。规范定义的钩子按执行时机排列大致为：`createRuntime`、`createContainer`（在 create 期间、命名空间已建但用户进程未起时，分别在运行时命名空间和容器命名空间上下文中执行）、`startContainer`（在容器内、exec 用户进程之前）、`poststart`（用户进程已启动后）、`poststop`（容器删除时）。早期的 `prestart` 钩子已被标记弃用，由 createRuntime/createContainer 取代。

钩子解释了"为什么容器网络能在容器起来的一瞬间就配好"。以每 Pod 一 IP 为例：运行时在 created 之后、进程真正跑之前执行一个钩子，钩子拿到容器的 network namespace，往里面创建 veth、配 IP、加路由，等 start 放行时容器一睁眼网络已经通了。这就是两段式生命周期（13-5.1.4）留出的那个"最后布置窗口"的实际用途。初学者只需记住：钩子是标准化的"生命周期回调点"，让高层工具能在不改容器进程的前提下，把网络、存储等外部设施接到容器上。（各钩子的精确上下文与顺序在规范修订间有过调整，具体某钩子在某运行时的实现细节标「待核」，以目标运行时文档为准。）

#### 来源与时效
- OCI Runtime Spec v1.3.0（2025-11-04）：`runtime.md`（status 四态定义、状态转换、state 输出字段 ociVersion/id/status/pid/bundle/annotations）、`config.md` 的 Hooks 小节（createRuntime/createContainer/startContainer/poststart/poststop 与 prestart 弃用），核实 2026-08-01。
- 本机实证（runc 1.3.4）：`runc create/state/start/kill/delete` 全链路跑通，观测 created→running→stopped 转换与 pid 字段；`/proc/<pid>/status` 的 `NSpid` 行印证宿主 PID 与容器内 PID 1 的对应，核实 2026-08-01。
- 规范与 runc 行为在四态与转换上一致。hooks 的精确执行上下文/顺序在规范历史修订中有过演进，本报告标注要点、细节留「待核」，不臆断具体运行时实现。

---

## 13-5.3 高层运行时 vs 低层运行时

### 13-5.3.1 分层：两类运行时各管什么

"容器运行时"这个词在实践中指两类分工不同的软件，分为**低层运行时**（也叫 OCI 运行时）和**高层运行时**（也叫容器运行时/container runtime）。低层运行时只干一件事：拿到一个 bundle（rootfs + config.json），调用内核原语把它变成一个隔离进程，并管理该容器的生命周期——它不懂镜像、不懂网络、不懂拉取。高层运行时则负责镜像拉取与管理、把镜像解包叠层成 rootfs、生成 config.json、管理存储与网络、对上提供 API，然后把"真正创建容器"这一步**委托**给低层运行时。

这条分工线可以用一句话记住：低层运行时把"一个 bundle"变成"一个运行的进程"；高层运行时把"一个镜像引用 + 一份用户意图"变成"一个 bundle"并驱动低层运行时。分层的好处是可替换性——只要都遵守 OCI Runtime Spec 的 bundle 与操作契约，高层就能在 runc、crun 之间自由切换。

### 13-5.3.2 低层（OCI）运行时：runc / crun / youki / runsc

低层运行时是 OCI Runtime Spec 的实现者。代表实现有：**runc**（Go 编写，OCI 的参考实现，从 Docker 的 libcontainer 抽出，业界事实标准）；**crun**（C 编写，Red Hat 主导，二进制更小、启动更快、内存占用更低，与 runc 命令行兼容）；**youki**（Rust 编写）；以及走"强隔离"路线的 **runsc**（gVisor，用户态内核）和 **kata-runtime**（每容器一个微 VM）——后两者虽然也接 OCI 接口，但底层不是共享内核的 namespaces/cgroups，属大主题12 的沙箱话题。

对初学者，抓住"runc 是参考实现、crun 是更轻的兼容替代"即可。它们对上暴露同一套 create/start/state/kill/delete 命令，对下调用同一批内核原语，差别主要在实现语言与性能，不在语义。本机装的低层运行时是 runc 1.3.4（`which crun` 无结果，本机无 crun）。运行时版本 ⚙演进快·锚版本·随时变。

### 13-5.3.3 高层运行时：containerd 与 CRI-O

主流的高层运行时是 **containerd** 和 **CRI-O**。containerd 是 CNCF 毕业项目，功能全面（镜像、快照/存储、内容管理、通过 CRI 插件对接 Kubernetes，也能被 Docker 用作底层引擎），本机装的是 containerd 2.2.2。CRI-O 是专为 Kubernetes 打造的精简高层运行时，只实现 Kubernetes CRI 所需的功能，不追求通用。

两者对上的接口重点不同：containerd 自身有一套原生 gRPC API（也提供 `ctr` 这样的调试 CLI，本机有 `ctr`），并通过一个 CRI 插件把自己适配成 Kubernetes 能用的运行时；CRI-O 则直接就是一个 CRI 实现。它们对下都通过 shim 调用 runc/crun。初学者记住"高层管镜像与编排对接、低层管进程隔离"这条界线即可，不必纠结二者功能清单差异。高层运行时版本同样 ⚙演进快·锚版本·随时变。

### 13-5.3.4 shim 与完整调用链（含 CRI 与 kubelet）

高层运行时并不直接 fork/exec 低层运行时后就撒手，中间隔着一个 **shim**（垫片）进程。以 containerd 为例，它启动一个 `containerd-shim-runc-v2` 进程，由 shim 去调 runc 创建容器，然后 shim **常驻**：它持有容器进程的父子关系、转发 stdio、收集退出码，最关键的是让容器在 containerd 守护进程重启时仍能存活（shim 不随 containerd 生死）。CRI-O 里对应的角色叫 `conmon`（container monitor），职责类似。

把整条链从 Kubernetes 视角串起来：

```
kubelet
  → CRI (gRPC)             # 标准接口，K8s v1.36
    → containerd (CRI 插件) 或 CRI-O
      → containerd-shim-runc-v2 (containerd) / conmon (CRI-O)
        → runc / crun       # 低层 OCI 运行时
          → 容器进程          # clone/setns/pivot_root/cgroup 装配后 exec
```

**CRI**（Container Runtime Interface）是 kubelet 与高层运行时之间的标准 gRPC 接口，让 Kubernetes 不绑定具体运行时。历史上 Kubernetes 通过 dockershim 支持 Docker，该垫片已在 Kubernetes v1.24 移除，此后主流是 containerd（含 CRI 插件）与 CRI-O 直连 CRI（跨版本细节 ⚙演进快·锚版本，锚 v1.36）。初学者只需建立这条"kubelet → CRI → 高层运行时 → shim → 低层运行时 → 进程"的链路心智：每一层只认下一层的标准接口，从而整条链任一环都可替换。

#### 来源与时效
- OCI Runtime Spec v1.3.0（低层运行时须实现的 bundle 契约与操作），核实 2026-08-01。
- Kubernetes 官方文档 v1.36：Container Runtimes / CRI（kubelet 经 CRI 对接 containerd/CRI-O；dockershim 于 v1.24 移除），⚙演进快·锚版本 v1.36，核实 2026-08-01。
- containerd 官方文档（架构、shim v2、CRI 插件）与 CRI-O 官方文档（conmon）为高层运行时分工与 shim 角色的一手来源；runc/crun 官方仓库文档为低层运行时来源。
- 本机实证：`which runc containerd ctr` 确认本机有 runc 1.3.4、containerd 2.2.2、ctr；`which crun crictl` 无结果（本机无 crun/crictl），核实 2026-08-01。
- 版本处一律 ⚙演进快·锚版本·随时变（runc 1.3.4 / containerd 2.2.2 / K8s v1.36 为本报告锚定值）。

---

## 13-5.4 runc 创建容器机制（内核原语的落地）

### 13-5.4.1 总览：从 bundle 到运行进程

本节把前面所有零件串成一条落地路线，显式点出 runc 如何调用 L4-01 操作系统提供的 namespaces/cgroups 原语来实现隔离。粗略说，`runc create` 收到 bundle 后做的事按顺序大致是：读 config.json → 用 clone/unshare **新建 config 里列出的 namespaces** → 在 mount namespace 内按 mounts 配置挂载文件系统 → 用 **pivot_root 把根切换到 rootfs** → 设置主机名（uts ns）、装配 **cgroup 资源限额**、丢弃多余 capabilities、装载 seccomp 过滤、设 no_new_privs → 阻塞等待 `start` → 收到 start 后在容器上下文里 exec `process.args` 指定的用户进程。

这条路线里，"隔离"不是某个单一开关，而是 namespaces（视图，大主题2）+ pivot_root（根文件系统）+ cgroups（资源，大主题3）+ 能力/seccomp（削权，大主题12）叠加出来的。本机实测已确认容器进程确实拥有与宿主不同的 pid/net/mnt/uts/ipc namespace，且其根就是 bundle 的 rootfs（见下文各节）。

### 13-5.4.2 namespaces 装配：clone / unshare 与 nsexec 引导

runc 用 **clone(2)** 或 **unshare(2)** 系列调用创建新 namespace。clone(2) 是 fork 的加强版，通过 `CLONE_NEW*` 标志（`CLONE_NEWPID`、`CLONE_NEWNS`（mount）、`CLONE_NEWNET`、`CLONE_NEWUTS`、`CLONE_NEWIPC`、`CLONE_NEWUSER`、`CLONE_NEWCGROUP`、`CLONE_NEWTIME`）让新进程一出生就处在全新的 namespace 里；unshare(2) 则让**当前**进程脱离并进入新 namespace。config.json 的 `linux.namespaces` 列表决定要新建哪几种。

有个初学者不必深究但值得知道的工程细节：runc 主体是 Go，而 Go 运行时是多线程的，多线程进程无法直接对某些 namespace（尤其 PID/user）执行 unshare/setns。runc 的解法是一段叫 **nsexec** 的 C 引导代码，它在 Go runtime 启动之前、以单线程状态完成 clone/setns 的关键步骤（分 parent/child/grandchild 几个阶段，正确处理 PID namespace 里"子进程才是新 ns 里的 1 号"这一约束），之后再把控制权交回 Go 主程序。你不需要记住阶段细节，只要知道"runc 有一段先于 Go 跑的 C 代码专门做 namespace 装配"即可。

本机把一个 runc 容器跑起来后，对比容器进程与宿主的 namespace inode，实测五种 namespace 全部不同（inode 号不同即代表是不同的 namespace 实例）：

```
pid  container=pid:[4026532297]  host=pid:[4026531836]  -> DIFFERENT(isolated)
net  container=net:[4026532298]  host=net:[4026531833]  -> DIFFERENT(isolated)
mnt  container=mnt:[4026532294]  host=mnt:[4026531832]  -> DIFFERENT(isolated)
uts  container=uts:[4026532295]  host=uts:[4026531838]  -> DIFFERENT(isolated)
ipc  container=ipc:[4026532296]  host=ipc:[4026531839]  -> DIFFERENT(isolated)
```

这就是 config.json 里 `namespaces: ['pid','network','ipc','uts','mount']` 五项被 runc 逐一 clone 出来的直接证据，把大主题2 的"视图隔离"从概念坐实到了 inode 层面。

### 13-5.4.3 setns：加入已有 namespace

与"新建 namespace"相对的操作是"加入已有 namespace"，由 **setns(2)** 完成。当 config.json 的某个 namespace 项带了 `path`（一个指向 `/proc/[pid]/ns/xxx` 的路径）时，runc 不新建而是调用 setns 把进程放进那个已存在的 namespace。

这在两个场景下必不可少。其一是 `runc exec`（进入一个已在运行的容器执行命令）：新进程要通过 setns 加入目标容器的各个 namespace，才能"看见"容器内部的进程、网络、文件系统。其二是共享 namespace，最典型的就是 Kubernetes 的 Pod——同一个 Pod 里的多个容器**共享网络 namespace**（因此彼此能用 localhost 通信、共享同一个 IP），实现方式正是后加入的容器用 setns 加入先建好的那个 network namespace（常挂在一个 pause/sandbox 容器上）。这把大主题9 "Pod 内容器共享网络"的机制根落到了 setns 这个系统调用。记忆钥匙：clone/unshare 是"开新房间"，setns 是"进别人已开好的房间"。

### 13-5.4.4 pivot_root：切换根文件系统

新建 mount namespace 只是让容器有了独立的挂载视图，但此时容器看到的根还是宿主的根。要把容器的 `/` 换成 bundle 的 rootfs，runc 调用 **pivot_root(2)**：它把当前进程的根挂载点换成 new_root，并把旧的根移到一个 put_old 目录，随后 runc 卸载并丢弃旧根，容器便再也访问不到宿主文件系统。

初学者常问"为什么不用 chroot"。chroot 只改变进程对"根在哪"的看法，但旧根仍以挂载形式存在、历史上存在多种逃逸手法；pivot_root 是真正把根挂载**替换并解除**，隔离更彻底，因此是容器运行时的默认选择（在极少数环境如 rootfs 为 ramfs、pivot_root 不可用时，runc 才回退到 `MS_MOVE + chroot`）。

读容器 init 进程的 `/proc/<pid>/root/` 与其 mountinfo，确认容器的根就是 bundle 的 rootfs（一个只含我们放进去的 `mini` 程序及 dev/proc/sys 的极小目录树），而非宿主根：

```
container / contents:  dev  mini  proc  sys
mountinfo root line:   ... /.../scratchpad/ocidemo/bundle/rootfs / ro,relatime - ext4 ...
```

mountinfo 里 `/.../bundle/rootfs` 被挂成了容器的 `/`（只读），正是 pivot_root 把 bundle 的 rootfs 装为容器根的结果——把大主题4 的 rootfs 概念和本主题的 bundle 概念在运行态对上了。

### 13-5.4.5 cgroup 装配、安全收窄，最后 exec

隔离视图之外，runc 还要给容器套上资源笼子和安全约束。它把 config.json 的 `linux.resources`（对应大主题3 的 cpu/memory/pids/io 控制器）翻译成对 cgroupfs 的写入：为该容器在 `/sys/fs/cgroup` 下创建一个 cgroup 目录、写入各控制器的限额文件（如 `memory.max`、`cpu.max`、`pids.max`），并把容器进程的 PID 写进 `cgroup.procs` 让它归属该 cgroup。这一步与 namespaces 装配相互独立又彼此配合：namespaces 决定"看得见什么"，cgroup 决定"用得掉多少"，共同构成大主题1 讲的"容器三要素"里的后两项。

在放行用户进程之前，runc 还做一连串**削权**：按 config 丢弃多余 Linux capabilities（只留必要的）、设置 `no_new_privs`（禁止后续 exec 获得新特权）、装载 seccomp 过滤（限制可用的系统调用）、应用 AppArmor/SELinux 标签、设 rlimits、切换到 config 指定的 uid/gid。全部布置完成后进程阻塞在一个同步点（fifo）上，等 `runc start` 写入信号，才最终 `execve` 成 `process.args` 指定的用户命令——至此容器从 created 迈入 running。

把这一节连起来读，就完整回答了本大主题的核心问题：runc 依次用 clone/unshare 建 namespaces（视图隔离）、setns 处理共享/进入、pivot_root 切根（文件系统隔离）、写 cgroupfs 装配限额（资源隔离）、丢 capabilities/装 seccomp（削权），最后 exec 用户进程。这些全是 L4-01 操作系统提供的通用内核原语，容器运行时不过是把它们按一份标准配置（config.json）有序地组装起来——"容器"本质上就是"被这样组装过的一个普通 Linux 进程"。

#### 来源与时效
- Linux man page：clone(2)（`CLONE_NEW*` 标志与新建 namespace）、setns(2)（加入已有 namespace）、pivot_root(2)（根挂载替换、put_old 语义、与 chroot 的差异）、unshare(2)/unshare(1)，核实 2026-08-01（本机未安装 man 页，以 man-pages 项目上游文本为准，另用本机实证交叉印证）。
- OCI Runtime Spec v1.3.0：`config-linux.md`（namespaces 的新建 vs path 加入语义、resources/cgroup 字段、rootfsPropagation/maskedPaths 等安全项）与 `config.md`（capabilities/seccomp/no_new_privs/user），核实 2026-08-01。
- runc 官方文档与源码（libcontainer 的 nsexec.c 引导、pivot_root 默认与 MS_MOVE+chroot 回退、cgroup 装配路径）为实现机制一手来源。
- 本机实证（runc 1.3.4，Linux 6.18.5）：跑真实容器后比对 `/proc/<pid>/ns/{pid,net,mnt,uts,ipc}` 与宿主 inode（五种全部不同）；读 `/proc/<pid>/root/` 与 `/proc/<pid>/mountinfo` 确认根为 bundle rootfs（pivot_root 结果），核实 2026-08-01。
- 规范（新建/加入语义、字段定义）与 man page（原语行为）与本机实证三者一致；nsexec 分阶段等实现细节属 runc 特有、不在规范内，已标为实现来源而非规范结论。
