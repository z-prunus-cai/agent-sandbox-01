# L5-13·大主题7 K8s 工作负载对象

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L5-13 大主题1（容器 vs VM）、大主题2（Linux namespaces）、大主题3（cgroups）、L4-01 操作系统（进程/命名空间）｜一手锚点：Kubernetes 官方文档 Workloads（kubernetes.io/docs/concepts/workloads/，当前稳定 v1.36）、Pod 生命周期文档、apps/v1 与 batch/v1 API 参考｜成熟度：Pod / Deployment / ReplicaSet / StatefulSet / DaemonSet（apps/v1）与 Job / CronJob（batch/v1）均为 GA；K8s 对象 API 随版本演进 ⚙演进快·锚版本·随时变

这一份讲"在 Kubernetes 里，你到底提交什么东西来跑容器"。心智模型一句话：你几乎从不直接跑容器，而是向集群提交一份 YAML 声明"我想要什么样、多少个、以什么方式运行的工作负载"，K8s 的控制器再不停地把现实拉向你声明的期望态。前面大主题1/2/3 讲的是"一台机器上一个容器怎么被隔离出来"，本篇往上跳一层，讲"一群容器在一个集群里怎么被组织、复制、更新、编排"。全篇按抽象从低到高铺开：先是最小单元 Pod（13-7.1），再是管无状态副本的 ReplicaSet/Deployment（13-7.2），然后是管有状态与每节点守护的 StatefulSet/DaemonSet（13-7.3），最后是管一次性/定时批处理的 Job/CronJob（13-7.4）。贯穿线索有两条：一是"Pod 是短暂的、会被随时替换的"，所以几乎所有对象都是"帮你管理 Pod 的控制器"；二是"选择器 + 模板"（selector + template）这套"用标签认领 Pod、用模板批量造 Pod"的模式在几乎每个控制器里重复出现。K8s 深度专题（调度、网络、存储内幕）分别归后续大主题与 L6-05，本篇只建立"编排解决什么 + 核心对象心智"。

Kubernetes 每年约发三个小版本，工作负载对象的字段、默认值、乃至新特性成熟度都随版本变化。本篇一律锚定 **v1.36（当前稳定）**，凡涉及"哪个版本 GA"的说法都标注核实日期；具体默认数值取自长期稳定的官方文档，个别不确定处标「待核」，不凭记忆编造。

---

## 13-7.1 Pod 最小调度单元

### 13-7.1.1 Pod 是什么：K8s 里最小的可部署与可调度单元

Pod 是 Kubernetes 中你能创建和管理的最小可部署计算单元。一个 Pod 封装了一个或多个紧密协作的容器、这些容器共享的存储卷、一个共享的网络身份，以及一份"这些容器该怎么跑"的规约。调度器调度的粒度是 Pod 而不是单个容器——一个 Pod 里的所有容器总是被作为一个整体调度到同一个节点上，同生共死地被放置。

初学者常问"为什么不直接以容器为单位"。因为现实里常有几个容器必须待在一起、能通过本机通信、能看到同一块盘——比如一个主应用容器加一个负责收集日志的辅助容器。如果它们被分到不同机器上就无法这样紧耦合。Pod 就是"应逻辑上归为一体、必须同机同网的一组容器"的封装。绝大多数场景下一个 Pod 只有一个容器（"一个 Pod 一个应用进程"是最常见形态），多容器 Pod 是为需要辅助进程的特殊场景准备的（见 13-7.1.4）。要记住的关键直觉：Pod 是"一次性用品"——它没有自愈能力，节点挂了或被驱逐，这个 Pod 就永远消失了（不会在别处复活），取而代之的是由控制器新建一个全新的 Pod（新名字、新 IP）。所以生产中你几乎不会裸建 Pod，而是让 Deployment 等控制器替你管理它们（见 13-7.2）。

### 13-7.1.2 Pod 内容器共享什么：网络与 IPC 共享，文件系统默认不共享

一个 Pod 内的多个容器共享同一个网络命名空间（network namespace）：它们有同一个 Pod IP、同一套端口空间，彼此之间可以直接用 `localhost` 通信。它们默认还共享 IPC 命名空间（可通过 System V IPC / POSIX 消息队列互通），以及 UTS 命名空间（同一主机名）。但它们默认各自拥有独立的 mount 命名空间——也就是说每个容器有自己的根文件系统（来自各自的镜像），彼此看不到对方的文件；要在容器间共享文件，得显式挂载同一个 Volume（数据卷）到各容器。PID 命名空间默认不共享（每个容器看到自己是 PID 1），但可以通过 Pod 的 `shareProcessNamespace: true` 开启共享。

这一点正是回指大主题2/3 的机制根：Pod 之所以能让内部容器"同一个 IP、localhost 互通、共享某些视图又隔离另一些视图"，靠的就是 Linux namespaces 的选择性共享——把这几个容器放进同一个 network/IPC/UTS 命名空间，却各自保留独立的 mount 命名空间；资源上再用 cgroups 施加限额。初学者最容易踩的坑：以为同 Pod 的两个容器能直接读到对方镜像里的文件——不能，文件系统默认是隔离的，必须靠共享 Volume（比如一个 `emptyDir`）当"中转盘"。另一个易错点：同 Pod 内两个容器不能监听同一个端口，因为它们共享端口空间，会冲突。下面用一段最小 Pod 清单展示两个容器通过共享卷协作：

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: shared-data-demo
spec:
  volumes:
    - name: shared
      emptyDir: {}
  containers:
    - name: writer
      image: busybox
      command: ["sh", "-c", "echo hello > /data/msg; sleep 3600"]
      volumeMounts:
        - name: shared
          mountPath: /data
    - name: reader
      image: busybox
      command: ["sh", "-c", "cat /data/msg; sleep 3600"]
      volumeMounts:
        - name: shared
          mountPath: /data
```

### 13-7.1.3 pause 容器与 Pod 沙箱

在实现层面，Pod 的共享网络/IPC 命名空间需要一个"占位者"来持有。运行时会为每个 Pod 先起一个极小的基础容器（社区惯称 pause 容器，或 sandbox/infra 容器），它几乎什么都不做（就是睡眠、回收僵尸进程），唯一职责是率先创建并长期持有 Pod 的那套共享命名空间；随后业务容器再以"加入这套已有命名空间"（setns）的方式启动。这样即使某个业务容器崩溃重启，命名空间（连同 Pod IP）也不会丢失，因为持有者 pause 容器还活着。

对初学者这是一个"知其然"的实现细节，不必深究，但它解释了两个常见困惑。其一：为什么一个 Pod 崩掉重启单个容器时 IP 不变？因为 IP 挂在 pause 容器持有的 network 命名空间上，业务容器只是加入进来。其二：为什么 `kubectl get pods` 有时能看到 Pod 里"多出一个你没定义的容器"的资源占用？那通常就是这个基础容器。这部分属于运行时（containerd/CRI-O 经由 CRI 接口）的落地机制，回指大主题5 的运行时链，本篇点到为止。

### 13-7.1.4 多容器模式：init 容器、sidecar 与原生 sidecar

Pod 支持两类"非主"容器。init 容器（初始化容器）在主容器启动前按顺序逐个运行到成功退出，常用于"跑数据库迁移、等待依赖就绪、准备配置文件"这类一次性前置任务——它们跑完就结束，主容器才开始。sidecar（边车）容器则是与主容器长期并肩运行的辅助容器，典型用途是日志收集、指标代理、服务网格的流量代理等。经典写法里 sidecar 就是普通的并列容器；从较新版本起，Kubernetes 提供了"原生 sidecar"：把容器写在 `initContainers` 列表里但给它设 `restartPolicy: Always`，于是它像 init 容器一样先于主容器启动、按序就位，又像普通容器一样长期存活，并且在 Pod 关闭时后于主容器停止。

初学者可用一句话记住三者的时间线：init 容器"跑完就走、主容器才来"；普通 sidecar"和主容器一起来、一起被管"；原生 sidecar"早于主容器就位、晚于主容器退场"。为什么要引入原生 sidecar？因为老式并列 sidecar 有几个痛点：Job 里 sidecar 不退出会导致 Job 永远不"完成"；启动顺序无法保证（代理可能晚于主应用起来，导致早期请求失败）。原生 sidecar 通过"归到 initContainers 且常驻"解决了顺序与生命周期问题。

原生 sidecar（SidecarContainers 特性）成熟度需按版本看：其在 v1.28 引入为 alpha、v1.29 转 beta、约 v1.33 达到 GA（此 GA 具体版本以官方特性门控文档为准，核实 2026-08-01；⚙演进快·锚版本·随时变）。在锚定的 v1.36 上该能力可用。一段原生 sidecar 写法：

```yaml
spec:
  initContainers:
    - name: log-shipper
      image: fluent/fluent-bit
      restartPolicy: Always      # 使这个 init 容器变为常驻 sidecar
  containers:
    - name: app
      image: my-app:1.0
```

### 13-7.1.5 Pod 生命周期：phase、容器状态与 restartPolicy

Pod 有一个高层的 `status.phase` 字段，取值为固定的五个：`Pending`（已被接受但还有容器未就绪，比如镜像在拉取或还没调度成功）、`Running`（已绑定节点、至少一个容器在运行）、`Succeeded`（所有容器成功终止且不再重启）、`Failed`（所有容器已终止且至少一个是失败退出）、`Unknown`（无法获取 Pod 状态，通常是与节点失联）。phase 是粗粒度的汇总；更细的信息在每个容器的状态里，容器状态有三种：`Waiting`、`Running`、`Terminated`，且带原因（如 `CrashLoopBackOff`、`ImagePullBackOff`）。Pod 级的 `restartPolicy` 控制容器退出后是否重启，取值 `Always`（默认）、`OnFailure`、`Never`——注意它作用于同一节点上"重启容器"，而不是"在别处重建 Pod"。

初学者要建立的关键区分是"重启容器" vs "重建 Pod"。`restartPolicy` 管的是前者：容器进程挂了，kubelet 在原节点原 Pod 里把它拉起来（并带指数退避，反复失败即 `CrashLoopBackOff`）。而"节点没了、Pod 没了、要造个新 Pod"是后者，那不归 `restartPolicy` 管，归上层控制器（Deployment/ReplicaSet 等）管。另一个易错点：`Always` 这个默认值对 Deployment 里的长驻服务是对的，但对 Job 这种"跑完就该结束"的工作负载就不合适——所以 Job 的 Pod 模板只能用 `OnFailure` 或 `Never`。terminationGracePeriodSeconds（优雅终止宽限期）默认 30 秒：删除 Pod 时先发 `SIGTERM`，等这段时间让进程收尾，超时才 `SIGKILL`。

### 13-7.1.6 探针：liveness、readiness、startup

Kubernetes 用探针（probe）周期性检查容器健康，共三种。liveness（存活）探针失败会导致 kubelet 重启该容器——用于"进程还在但已卡死"的自愈。readiness（就绪）探针失败会把该 Pod 从 Service 的可用端点里摘除（不再给它转发流量），但不重启它——用于"暂时不能服务，但别杀"的场景，比如正在加载大缓存。startup（启动）探针用于慢启动应用：在它成功之前，liveness/readiness 探针都不生效，从而给慢启动进程一个不被误杀的宽限窗口。每种探针可用 HTTP GET、TCP 连接或执行命令（exec）三种方式实现。

初学者最常混淆 liveness 和 readiness："服务临时过载/依赖不可用"该用 readiness（摘流量、别杀），"进程死锁/内存泄漏卡住"才用 liveness（重启才能救）。一个常见事故：给慢启动应用配了过紧的 liveness 探针，结果应用还没起来就被反复重启，陷入 `CrashLoopBackOff`——正确做法是加 startup 探针兜住启动期。startup 探针在 v1.20 达到 GA（核实 2026-08-01）。一段配置示例：

```yaml
containers:
  - name: web
    image: my-web:1.0
    readinessProbe:
      httpGet:
        path: /healthz
        port: 8080
      periodSeconds: 5
    livenessProbe:
      httpGet:
        path: /livez
        port: 8080
      periodSeconds: 10
    startupProbe:
      httpGet:
        path: /livez
        port: 8080
      failureThreshold: 30
      periodSeconds: 10
```

### 13-7.1.7 Pod 是短暂的：为什么需要控制器

Pod 被设计成短暂（ephemeral）、可抛弃的对象。它不会自愈：宿主节点故障、被驱逐、或 Pod 本身被删除后，这个 Pod 实例就彻底没了，K8s 不会"把它搬到别处"。取而代之的机制是——由更高层的工作负载控制器（ReplicaSet/Deployment/StatefulSet/DaemonSet/Job 等）持有一个 Pod 模板和期望副本数，持续观察"现在活着几个符合条件的 Pod"，少了就照模板新建、多了就删除。

这解释了本篇后续所有对象存在的理由：它们本质上都是"帮你把短暂的 Pod 维持成你想要的样子"的管理器。初学者应据此养成习惯——生产里几乎从不手写裸 Pod（`kind: Pod`），因为裸 Pod 一旦挂掉就不会回来；应写 Deployment 等控制器，由它保证副本数。裸 Pod 只适合调试、一次性任务或学习。这也顺带解释了"为什么 Pod 名字后面常带一串随机后缀"——那是控制器批量造出来的、随时会被替换的实例，名字本就不该被依赖（有状态场景对稳定名字的需求，正是 StatefulSet 存在的动机，见 13-7.3）。

#### 来源与时效
- 锚点：Kubernetes 官方文档 Concepts/Workloads/Pods（Pod 概念、共享命名空间、多容器模式）、Pods/Pod Lifecycle（phase 五值、容器状态、restartPolicy、probes、terminationGracePeriodSeconds=30）、Pods/Init Containers 与 Sidecar Containers；均锚定 v1.36，核实 2026-08-01。
- 交叉核对：Pod 五个 phase 值与 restartPolicy 三值在 Pod Lifecycle 文档与 core/v1 API 参考（PodStatus.phase、PodSpec.restartPolicy）两处一致；共享 network/IPC 命名空间语义与大主题2 namespaces(7) 一手文档相互印证（机制根）。
- 版本/待核：原生 sidecar（`initContainers` 内 `restartPolicy: Always`）成熟度按版本演进——alpha v1.28、beta v1.29、GA 约 v1.33，确切 GA 版本以官方 Feature Gates / SidecarContainers 文档为准，标「待核」（⚙演进快·锚版本·随时变）；startup 探针 v1.20 GA。terminationGracePeriodSeconds 默认 30 秒为长期稳定默认值。
- 分账：`restartPolicy`（同节点重启容器）与"控制器重建 Pod"是两个不同机制，文档明确区分，本篇按此分账。

## 13-7.2 ReplicaSet / Deployment

### 13-7.2.1 ReplicaSet：维持指定数量的相同 Pod 副本

ReplicaSet 的唯一职责是：在任意时刻维持一组"相同的" Pod 恰好为指定的副本数（`replicas`）。它靠三样东西工作：`replicas`（想要几个）、`selector`（用标签选择器认领哪些 Pod 算"我的"）、`template`（Pod 模板，副本不足时照它创建新 Pod）。控制器不停地数"当前有几个匹配 selector 且存活的 Pod"，少了就按 template 新建，多了就删除，从而把实际副本数收敛到期望值。

初学者要抓住"selector 认领"这个核心机制：ReplicaSet 并不"记住"自己造过哪些 Pod，而是靠标签选择器动态认领——任何标签匹配 selector 的 Pod 都会被它算作副本之一。这带来一个易错点：如果你手动创建一个标签恰好匹配某 ReplicaSet 的裸 Pod，它会被"收编"进去，甚至可能触发 ReplicaSet 删掉多余的以维持副本数。规范要求 `spec.template` 的标签必须匹配 `spec.selector`，否则 API 拒绝。还有一点：ReplicaSet 只保证"数量对"，它不做滚动更新——改了 template 里的镜像，已存在的旧 Pod 不会被它自动替换。正因如此，实践中你几乎从不直接写 ReplicaSet，而是写 Deployment，让 Deployment 去管 ReplicaSet（见下）。

### 13-7.2.2 Deployment：声明式管理 ReplicaSet 与无状态应用

Deployment 是管理无状态应用最常用的对象。你向它声明期望态（用哪个镜像、跑几个副本），它在背后创建并管理 ReplicaSet 来达成这个期望；当你更新 Deployment（比如换镜像版本），它会编排一次受控的发布——新建一个新版 ReplicaSet、逐步把副本从旧 RS 挪到新 RS，并保留旧 RS 以便回滚。可以理解为分工：ReplicaSet 负责"维持某一版本的 N 个副本"，Deployment 负责"在多个版本的 ReplicaSet 之间做有序切换"。

对初学者，Deployment 是入门首选，因为它把"保持副本数（自愈）+ 平滑升级 + 回滚"打包好了。一个常见误解是"Deployment 直接管 Pod"——不是，它管的是 ReplicaSet，由 ReplicaSet 再管 Pod，所以 `kubectl get rs` 你会看到 Deployment 名字加一串哈希后缀的 ReplicaSet（哈希来自 Pod 模板内容，模板变则新 RS）。适用边界要记清：Deployment 面向无状态、副本彼此等价、可随意替换、不需要稳定名字或独占存储的应用；有状态应用要用 StatefulSet（13-7.3）。一段最小 Deployment：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
spec:
  replicas: 3
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
        - name: web
          image: my-web:1.0
```

### 13-7.2.3 滚动更新：maxUnavailable 与 maxSurge

Deployment 的默认更新策略是 `RollingUpdate`（滚动更新）：升级时不是一次性替换所有 Pod，而是新旧 Pod 交替、逐步替换，保证升级期间服务不中断。它的节奏由两个参数控制：`maxUnavailable`（升级过程中最多允许多少副本不可用）和 `maxSurge`（最多允许临时超出期望副本数多少个新 Pod）。二者都可写成绝对数或百分比，默认均为 25%。

初学者可这样理解这两个旋钮：`maxSurge` 决定"能不能先多起几个新的再撤旧的"（更快、更占资源），`maxUnavailable` 决定"能不能先撤几个旧的腾出空间"（更省资源、但短时容量下降）。举例：3 副本、maxSurge=1、maxUnavailable=0，则升级时会先起第 4 个（新版）Pod，等它就绪再撤 1 个旧的，全程可用副本不低于 3——这是"零容量损失"的稳妥打法。易错点：滚动更新是否顺利依赖 readiness 探针（13-7.1.6）——如果没配就绪探针，K8s 会以为新 Pod 一起来就"就绪"，可能在新版其实还没准备好时就撤掉旧版，造成短暂错误。配置示例：

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
```

### 13-7.2.4 Recreate 策略

除了滚动更新，Deployment 还有 `Recreate`（重建）策略：升级时先把所有旧 Pod 全部杀掉，再创建新 Pod。这会造成一段明确的服务中断窗口（旧的没了、新的还没起），但保证"任一时刻只存在一个版本"。

当新旧版本无法同时运行时——比如两版应用都要独占同一个数据库锁、或共享一块只能被单实例挂载的存储卷、或新版做了不兼容的数据格式变更。此时滚动更新会让新旧版本短暂共存而冲突，反而必须用 Recreate 强制"先全下、再全上"。初学者要记住这是"用可用性换一致性"的取舍：绝大多数无状态服务应选默认的滚动更新（不停机），只有确有"版本不能共存"约束时才选 Recreate。

### 13-7.2.5 回滚、修订历史与 rollout 控制

因为 Deployment 会保留旧版本的 ReplicaSet（数量由 `revisionHistoryLimit` 控制，默认保留 10 个），它天然支持回滚：一条命令即可把 Deployment 退回到上一个（或指定的）修订版本，本质是把副本从当前 RS 挪回某个旧 RS。相关的常用运维动作围绕 `kubectl rollout` 展开。

对初学者，这套"发布可回退"能力是把 Deployment 当生产标准的关键理由之一：升级出问题时不必手忙脚乱重新部署老镜像，一条 `rollout undo` 就回去了。几条常用命令：

```bash
kubectl rollout status deployment/web       # 观察本次发布是否完成
kubectl rollout history deployment/web      # 查看修订历史
kubectl rollout undo deployment/web         # 回滚到上一版
kubectl rollout pause deployment/web        # 暂停发布（可攒多个改动一起放量）
kubectl rollout resume deployment/web       # 恢复发布
```

一处易错点在于，回滚只回滚 Deployment 管辖的 Pod 模板（镜像、配置等），它不会帮你回滚外部状态（数据库 schema、已写入的数据）——所以"发布可回退"是对无状态部分而言，有状态变更的回退要另行设计。`revisionHistoryLimit` 默认值 10 为长期稳定默认（核实 2026-08-01）。

#### 来源与时效
- 锚点：Kubernetes 官方文档 Concepts/Workloads/Workload Management——ReplicaSet、Deployments（RollingUpdate/Recreate、maxUnavailable/maxSurge 默认 25%、revisionHistoryLimit 默认 10、rollout/undo）；apps/v1 API 参考（DeploymentSpec.strategy、ReplicaSetSpec）；均锚定 v1.36，核实 2026-08-01。
- 交叉核对：maxUnavailable/maxSurge 默认 25% 在 Deployment 概念文档与 apps/v1 `RollingUpdateDeployment` API 字段说明两处一致；"Deployment 管 ReplicaSet、ReplicaSet 管 Pod"的层级由 Deployment 与 ReplicaSet 两篇文档相互印证。
- 版本：apps/v1 Deployment/ReplicaSet 自 v1.9 起 GA，字段长期稳定；具体默认值随极少数版本微调的可能性存在，本篇取 v1.36 文档文本（⚙演进快·锚版本·随时变）。
- 分账：ReplicaSet 只维持副本数、不做滚动更新；滚动更新是 Deployment 层能力——文档明确区分，本篇据此分账。

## 13-7.3 StatefulSet / DaemonSet

### 13-7.3.1 StatefulSet：为有状态应用提供稳定标识与稳定存储

StatefulSet 管理一组需要"稳定身份"的 Pod，专为有状态应用（数据库、消息队列、分布式存储等）设计。它相比 Deployment 提供四项额外保证：稳定且唯一的网络标识（Pod 名字带有序序号，形如 `web-0`、`web-1`、`web-2`，删除重建后名字不变）；稳定的持久存储（通过 `volumeClaimTemplates` 为每个序号的 Pod 绑定各自专属的 PVC，重建后仍挂回同一块盘）；有序的部署与伸缩（按序号 0、1、2… 依次创建，缩容时逆序 …2、1、0 依次删除，默认前一个就绪才动下一个）；有序的滚动更新（按逆序逐个更新）。它通常搭配一个 headless Service（无 ClusterIP 的服务）来给每个 Pod 一个稳定的 DNS 名。

初学者要抓住 Deployment 与 StatefulSet 的根本区别：Deployment 的副本是"可互换的一群等价体"，名字随机、无所谓谁是谁、共享或无所谓存储；StatefulSet 的副本是"有编号、有身份的个体"，`web-0` 永远是 `web-0`、永远挂它自己那块盘。为什么数据库需要这个？因为集群型数据库里各成员角色不同（主/从）、各自持有不同数据分片，必须能被稳定寻址、重启后接回自己的数据，不能像无状态服务那样随便换个新身份。默认的有序（`OrderedReady`）行为可改为 `Parallel`（`podManagementPolicy: Parallel`，并行起停，不等待）。一段带专属存储的 StatefulSet 骨架：

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: web
spec:
  serviceName: web-headless
  replicas: 3
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
        - name: web
          image: my-db:1.0
          volumeMounts:
            - name: data
              mountPath: /var/lib/data
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 10Gi
```

### 13-7.3.2 何时用 StatefulSet vs Deployment

判断标准很直接：如果你的应用副本彼此等价、可随意替换、不需要固定名字、不需要每副本独占一块持久盘，就用 Deployment；如果任一副本需要稳定的网络身份、需要在重建后接回自己的持久数据、或需要按序启停，就用 StatefulSet。

对初学者，一个实用的反问是"我能不能随便杀掉任一副本、由一个全新随机名字的副本顶替，而应用毫无影响？"——能，就是无状态，用 Deployment；不能（比如顶替者拿不到原来那份数据、或别人找不到它了），就是有状态，用 StatefulSet。常见误用：给数据库用 Deployment + 一个共享 PVC，结果多副本同时读写同一块盘导致数据损坏（多数块存储只支持单点读写 `ReadWriteOnce`）。要提醒的是 StatefulSet 只提供"稳定身份与存储绑定"这套编排机制，它本身不懂你的数据怎么复制、怎么选主——这些数据层逻辑仍需应用自己（或配套 Operator）实现。

### 13-7.3.3 DaemonSet：每个（符合条件的）节点上恰好跑一个 Pod

DaemonSet 保证在集群中每个（满足条件的）节点上都运行一个该 Pod 的副本。当有新节点加入集群，DaemonSet 会自动在其上创建 Pod；节点被移除时，其上的 Pod 也随之回收。它的副本数不是你指定的固定值，而是"跟着节点数走"。

这套语义天生契合"每台机器上都得有一份"的基础设施型工作负载：节点级日志收集器（如 Fluent Bit/Fluentd）、节点级监控代理（如 node-exporter）、集群网络插件（CNI 的每节点组件）、存储守护进程等。初学者对比记忆：Deployment/ReplicaSet 回答"我要 N 个副本（不管落在哪些节点）"，DaemonSet 回答"每个节点各来一个"。可以用 `nodeSelector` 或节点亲和性把 DaemonSet 限定到部分节点（如只在有 GPU 的节点上跑）。一段最小 DaemonSet：

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: node-agent
spec:
  selector:
    matchLabels:
      app: node-agent
  template:
    metadata:
      labels:
        app: node-agent
    spec:
      containers:
        - name: agent
          image: my-node-agent:1.0
```

### 13-7.3.4 DaemonSet 的调度、污点容忍与更新

DaemonSet 的 Pod 由默认调度器调度（较早版本曾由 DaemonSet 控制器自行放置，现代版本改为控制器创建带节点亲和的 Pod、交由默认调度器绑定，核实 2026-08-01），因此像亲和性、抢占等调度机制对它同样适用。因为很多 DaemonSet（网络、监控）需要跑在包括被打了污点（taint）的节点在内的所有节点上，它们通常配置相应的容忍（toleration）。DaemonSet 也支持滚动更新（`updateStrategy` 为 `RollingUpdate` 或 `OnDelete`）。

初学者要理解"为什么 DaemonSet 常需要容忍污点"：控制平面节点或专用节点常被打上污点以拒绝普通工作负载，但日志/监控这类基础组件恰恰需要覆盖到这些节点，所以要显式声明容忍来"获准入驻"。污点/容忍、亲和性的机制细节归大主题10（调度），本篇只点出 DaemonSet 与它们的关系。`OnDelete` 更新策略的含义：改了模板后不自动替换，只有当你手动删掉某节点上的旧 Pod 时，才用新模板重建——适合需要人工把控每台机器更新节奏的敏感组件。

#### 来源与时效
- 锚点：Kubernetes 官方文档 Concepts/Workloads/Workload Management——StatefulSets（稳定标识/序号/volumeClaimTemplates/有序保证/podManagementPolicy、headless Service）、DaemonSet（每节点一 Pod、调度、taint/toleration、updateStrategy）；apps/v1 API 参考；均锚定 v1.36，核实 2026-08-01。
- 交叉核对：StatefulSet 的"稳定网络标识 + 稳定存储 + 有序"三保证在 StatefulSet 概念文档与 apps/v1 `StatefulSetSpec`（serviceName、volumeClaimTemplates、podManagementPolicy、updateStrategy）两处一致；DaemonSet "跟随节点数"的语义由 DaemonSet 概念文档与 API 参考印证。
- 版本/待核：DaemonSet 由默认调度器调度（`ScheduleDaemonSetPods`）自较早版本（约 v1.17）起为默认行为，本篇按现代默认表述，早期版本由 DaemonSet 控制器直接放置的历史差异标记为版本演进（⚙演进快·锚版本）。apps/v1 StatefulSet/DaemonSet 自 v1.9 GA。
- 分账：StatefulSet 提供编排层的稳定身份/存储绑定，但不实现数据复制/选主——文档强调数据层逻辑属应用责任，本篇据此分账，不夸大其保证范围。

## 13-7.4 Job / CronJob

### 13-7.4.1 Job：把 Pod 跑到成功完成的一次性任务

Job 用于运行"跑到完成就结束"的任务，而非长驻服务。它创建一个或多个 Pod，并持续重试直到指定数量的 Pod 成功终止；达到成功目标后，Job 标记为完成，不再创建新 Pod（已完成的 Pod 通常保留以便查日志，直到被清理）。这与 Deployment 形成鲜明对比：Deployment 的 Pod 应当永远运行（`restartPolicy: Always`），而 Job 的 Pod 应当有明确的成功退出（模板的 `restartPolicy` 只能是 `OnFailure` 或 `Never`）。

初学者要抓住"完成"这个核心概念：服务是"永远在线"，任务是"做完就好"。典型 Job 用例：数据库迁移、批量数据处理、生成报表、发送一批邮件、一次性计算。因为要有"成功"这个终点，Job 的 Pod 绝不能用默认的 `restartPolicy: Always`（那意味着容器一退出就重启、永远完不成），必须用 `OnFailure`（失败才重启同一 Pod 里的容器）或 `Never`（失败就换个新 Pod 重试，由 Job 控制）。一段最小 Job：

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: pi
spec:
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: pi
          image: perl:5.34
          command: ["perl", "-Mbignum=bpi", "-wle", "print bpi(2000)"]
  backoffLimit: 4
```

### 13-7.4.2 Job 的并行度、完成数、重试与完成模式

Job 用两个字段刻画规模：`completions`（需要多少个 Pod 成功才算整个 Job 完成）与 `parallelism`（同时最多跑几个 Pod）。三种常见形态：单次任务（不设或都为 1，跑一个成功即完）；固定完成计数（设 `completions=N`，跑到 N 个成功，`parallelism` 控制并发）；工作队列型（设 `parallelism`、不设 `completions`，各 Pod 从共享队列取活，任一成功且队列空即结束）。重试由 `backoffLimit` 控制（失败重试次数上限，默认 6），超过则 Job 标记为失败，且重试间隔按指数退避拉长。完成模式 `completionMode` 有 `NonIndexed`（默认，只数成功个数）与 `Indexed`（每个 Pod 拿到一个从 0 到 `completions-1` 的唯一索引，适合把工作按索引静态分片）。

对初学者，`completions` 与 `parallelism` 的关系可类比"总共要做 N 件事，同时最多 P 个人做"。工作队列型是常见的"多 worker 抢任务"模式：不预设总数，起若干 worker 并行从队列消费，队列空且都成功即收工。易错点：`backoffLimit` 计的是"失败次数"，不是"耗时"——若想限制总运行时长要用 `activeDeadlineSeconds`（超时则 Job 失败并终止其 Pod）。`Indexed` 模式在 v1.24 达到 GA（核实 2026-08-01），它让每个 Pod 知道"我负责第几片"，配合稳定的主机名后缀便于静态分工。另有更细的失败处理策略（如 Pod failure policy、成功策略 JobSuccessPolicy）属较新特性，成熟度随版本演进，具体版本标「待核」，本篇不展开。

### 13-7.4.3 CronJob：按时间表定期创建 Job

CronJob 在时间维度上管理 Job：它按一个 cron 表达式的时间表，周期性地创建 Job（每次触发就新建一个 Job，由该 Job 再去跑 Pod）。它就是"K8s 版的 crontab"，用于定时备份、周期性报表、定时清理等。`schedule` 字段用标准 5 段 cron 语法（分 时 日 月 周）。

CronJob 造 Job，Job 造 Pod。所以排查定时任务时要顺着这条链看（CronJob 有没有按点触发 → 触发出的 Job 成没成功 → Job 的 Pod 干了啥）。cron 语法示例与最小 CronJob：

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: nightly-backup
spec:
  schedule: "0 2 * * *"        # 每天 02:00
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
            - name: backup
              image: my-backup:1.0
```

CronJob 的时区。默认按 kube-controller-manager 所在环境的时区解释 schedule；较新版本支持在 spec 里显式写 `timeZone` 字段（此字段 GA 版本以官方文档为准，核实 2026-08-01，⚙演进快·锚版本），不指定时行为与部署环境相关，跨时区团队易在此踩坑。

### 13-7.4.4 CronJob 的并发策略、错过处理与历史保留

CronJob 有几个控制运行行为的关键字段。`concurrencyPolicy`（并发策略）决定上一次触发的 Job 还没结束、下一次触发点又到了时怎么办，取值：`Allow`（默认，允许并发运行）、`Forbid`（禁止并发，跳过本次）、`Replace`（用新的替换掉还在跑的旧 Job）。`startingDeadlineSeconds`（启动截止秒数）规定：若因故错过了预定触发时刻超过这么多秒，就干脆放弃这一次，不再补跑。历史保留由 `successfulJobsHistoryLimit`（默认 3）与 `failedJobsHistoryLimit`（默认 1）控制，即分别保留最近多少个成功/失败的 Job 供排查。CronJob 还可用 `suspend: true` 临时挂起（不再触发新 Job）。

初学者要理解 `concurrencyPolicy` 解决的真实问题：假设备份任务偶尔跑得比周期还久，如果放任并发（`Allow`），两个备份同时读写会打架——此时该用 `Forbid`（宁可跳过也别撞车）或 `Replace`（新的顶掉旧的）。`startingDeadlineSeconds` 的意义在于控制器重启/短暂宕机后不要"报复性补跑"一堆错过的任务：设了它，只补最近这一次（且在截止期内）。易错点：如果 `startingDeadlineSeconds` 设得过小、而控制器检查间隔又赶不上，可能导致本该触发的任务被当成"错过太久"而跳过——官方文档对此有专门提醒。这些字段的默认值（成功保留 3、失败保留 1）为长期稳定默认（核实 2026-08-01）。CronJob 深度（如精确的错过语义、时区细节）归 L6-05，本篇建立心智即止。

#### 来源与时效
- 锚点：Kubernetes 官方文档 Concepts/Workloads/Workload Management——Jobs（completions/parallelism/backoffLimit 默认 6/completionMode/activeDeadlineSeconds）、CronJob（schedule cron 语法/concurrencyPolicy 三值/startingDeadlineSeconds/successfulJobsHistoryLimit 默认 3、failedJobsHistoryLimit 默认 1/timeZone/suspend）；batch/v1 API 参考；均锚定 v1.36，核实 2026-08-01。
- 交叉核对：backoffLimit 默认 6、CronJob 历史保留默认 3/1 在概念文档与 batch/v1 API 字段说明两处一致；concurrencyPolicy 的 Allow/Forbid/Replace 三值由 CronJob 文档与 `CronJobSpec.concurrencyPolicy` API 印证；Job 的 `restartPolicy` 仅 OnFailure/Never 与 Pod Lifecycle（13-7.1.5）一致。
- 版本/待核：`completionMode: Indexed` v1.24 GA；CronJob `timeZone` 字段 GA 版本以官方文档为准，标「待核」（⚙演进快·锚版本·随时变）；Pod failure policy、JobSuccessPolicy 等较新失败/成功处理策略成熟度随版本演进，具体 GA 版本标「待核」，本篇不作定论。
- 分账：CronJob→Job→Pod 三层从属关系由 CronJob 与 Job 两篇文档共同确立；错过触发的补跑语义（startingDeadlineSeconds）文档有显式告警，本篇按文档表述，未证实的边界行为不编造，深度归 L6-05。
