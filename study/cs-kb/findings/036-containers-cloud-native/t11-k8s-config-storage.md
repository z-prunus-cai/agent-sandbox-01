# L5-13·大主题11 K8s 配置与存储

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L5-13 大主题2（Linux namespaces，尤其 mount ns）、大主题4（OCI 镜像与不可变镜像）、大主题7（Pod/工作负载）、L4-01 操作系统（文件系统/挂载）、L4-06 配置管理｜一手锚点：Kubernetes 官方文档 Configuration + Storage（kubernetes.io/docs/concepts/，当前稳定 v1.36）、CSI 规范（container-storage-interface/spec）、Kubernetes CSI 文档（kubernetes-csi.github.io）｜成熟度：ConfigMap/Secret/Volume/PV/PVC/StorageClass 均为 GA；CSI 于 K8s v1.13 GA；K8s 对象 API 随版本演进 ⚙演进快·锚版本·随时变

这一份讲"容器里的东西怎么和它需要的配置、密钥、磁盘打交道"。前面大主题4 讲过镜像是不可变的——同一个镜像要能跑在开发、测试、生产各种环境，就不能把环境相关的配置和密码烤进镜像里。这就引出本篇的第一条主线：把配置与密钥从镜像里剥出来，运行时再注入（ConfigMap / Secret）。第二条主线是存储：容器的文件系统默认随容器生死而生死，可一旦应用要存"必须活得比容器久"的数据（数据库文件、上传的图片），就需要一套把存储的生命周期与 Pod 解耦的机制（Volume → PV/PVC/StorageClass），最后再用一个厂商中立的插件标准（CSI）把千奇百怪的真实存储系统统一接进来。

全篇按"配置 → 临时存储 → 持久存储 → 存储插件标准"由浅入深：13-11.1 讲配置与密钥的解耦（ConfigMap/Secret），13-11.2 讲各类 Volume 及其生命周期差异，13-11.3 讲持久卷的供给-申领分离（PV/PVC/StorageClass），13-11.4 讲标准化第三方存储的 CSI 接口。贯穿的机制根是大主题2 的 mount namespace——容器"看到某个目录"本质就是宿主把某个后端挂进了容器的挂载视图；K8s 的 Volume/PV 只是在这之上加了一层"谁来供给、活多久、怎么申领"的编排。

Kubernetes 每年约发三个小版本，存储子系统的字段、默认值、特性成熟度都随版本变化。本篇一律锚定 **v1.36（当前稳定）**，凡涉及"哪个版本 GA"的说法都标注核实日期；具体默认值取自长期稳定的官方文档，个别不确定处标「待核」，不凭记忆编造。

---

## 13-11.1 ConfigMap / Secret

### 13-11.1.1 ConfigMap：把非机密配置与镜像解耦

ConfigMap 是一个 Kubernetes API 对象，用键值对的形式存放**非机密**的配置数据，让"环境相关的配置"与"容器镜像"彼此独立。它有两个存数据的字段：`data` 存 UTF-8 文本键值对，`binaryData` 存 base64 编码的二进制数据；两者的键不能重叠。

这正是对大主题4"不可变镜像"的直接呼应。镜像一旦构建就不该再改，可同一个镜像跑在开发和生产往往需要不同的数据库地址、日志级别、功能开关。如果把这些写死进镜像，就得为每个环境构建一份镜像，既违背不可变原则又难以复用。ConfigMap 让你构建一次镜像、在不同环境挂不同的 ConfigMap。初学者要记住的边界：ConfigMap **不做加密**，任何有读权限的人都能明文看到里面的内容，所以密码、令牌这类敏感数据绝不能放 ConfigMap，要用 Secret（见 13-11.1.2）。一个最小 ConfigMap 长这样：

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: game-demo
data:
  player_initial_lives: "3"
  game.properties: |
    enemy.types=aliens,monsters
    player.maximum-lives=5
```

### 13-11.1.2 Secret：管理敏感数据，且"base64 不是加密"

Secret 与 ConfigMap 结构几乎一样，但专门用来存**敏感数据**——密码、OAuth 令牌、TLS 私钥等。它用 `data` 字段存 base64 编码的值，也提供 `stringData` 字段让你直接写明文（写入时系统自动帮你 base64 编码）。

这里有一条初学者极易误解、也是本主题被点名要辨析的关键：**Secret 里的 base64 只是编码，不是加密**。base64 是可逆的，`echo <值> | base64 -d` 一步就能还原成明文。默认情况下 Secret 在集群的 etcd 里也是以未加密的形式存放的。所以"我把密码放进了 Secret"并不等于"我的密码安全了"——它只是比明文写在 Pod 里更规范、更便于统一管理和授权，而非提供了机密性保护。真正的加固手段见 13-11.1.6。可以这样记：ConfigMap 和 Secret 在数据层面几乎是"孪生对象"，Secret 的额外价值主要在于 Kubernetes 对它做了更谨慎的处置（尽量不落磁盘、按需分发、可单独授权、可开启静态加密），而不在于 base64 本身。

### 13-11.1.3 消费方式：环境变量 vs 卷挂载（更新行为不同）

ConfigMap 和 Secret 主要有两种被 Pod 消费的方式：注入成容器的**环境变量**，或挂载成容器里的**只读文件**（卷挂载）。此外还可以在容器的 command/args 里引用，或让应用直接调 Kubernetes API 读取，但前两种最常用。

一个必须记牢的差异是**更新行为**：以卷方式挂载的 ConfigMap/Secret，当源对象被修改后，容器里对应的文件会被 kubelet **自动同步更新**（有一个同步周期的延迟）；但以环境变量注入的值**不会自动更新**——环境变量在容器启动那一刻就固定了，你必须重建 Pod 才能让新值生效。这是初学者排障时的高频坑：改了 ConfigMap 却发现应用行为没变，往往就是因为用了环境变量注入而没重启 Pod。下面是两种消费方式的对照：

```yaml
# 方式一：环境变量注入（改了 ConfigMap 不会自动生效，需重建 Pod）
env:
  - name: PLAYER_INITIAL_LIVES
    valueFrom:
      configMapKeyRef:
        name: game-demo
        key: player_initial_lives
```

```yaml
# 方式二：卷挂载成只读文件（改了 ConfigMap 文件会自动同步，有延迟）
volumes:
  - name: config-vol
    configMap:
      name: game-demo
containers:
  - name: app
    volumeMounts:
      - name: config-vol
        mountPath: /etc/config
        readOnly: true
```

卷挂载之所以能"注入文件"，机制根就在大主题2 的 mount namespace：kubelet 把 ConfigMap 的内容在宿主上物化成文件，再挂进容器的挂载视图里的 `mountPath`。容器"看到 /etc/config 下有几个文件"，本质就是宿主往它的挂载命名空间里塞了个挂载点——这与后面 Volume（13-11.2）"看到某个目录"是同一套机制。

### 13-11.1.4 immutable 不可变标记与 1MiB 大小上限

ConfigMap 和 Secret 都支持 `immutable: true` 字段（自 v1.19 起可用）。一旦设为 true，该对象就不能再被修改（只能删除重建）。两类对象单个的数据大小上限都是 **1 MiB**。

把配置标为不可变有两个好处，尤其在大规模集群里明显：一是防止意外改动导致应用崩溃（配置一旦定稿就锁死）；二是性能——kubelet 需要持续 watch 它所用到的 ConfigMap/Secret 以便同步更新，如果对象被标记为不可变，kubelet 就可以关闭对它的 watch，显著降低 API server 的负载。1MiB 的上限则提醒初学者：ConfigMap/Secret 是给"配置"用的，不是给"大文件"用的——想塞进一个几十兆的数据文件会直接被拒绝，那种需求应该用 Volume。

### 13-11.1.5 Secret 的内置类型

Secret 有一个 `type` 字段，用来标注这个 Secret 装的是什么，便于 Kubernetes 和相关组件按约定处理。常见内置类型包括：`Opaque`（默认，任意用户自定义数据）、`kubernetes.io/tls`（TLS 证书与私钥）、`kubernetes.io/dockerconfigjson`（镜像仓库拉取凭据，供 imagePullSecrets 用）、`kubernetes.io/service-account-token`（ServiceAccount 令牌）、`kubernetes.io/basic-auth`（基本认证）、`kubernetes.io/ssh-auth`（SSH 凭据）、`bootstrap.kubernetes.io/token`（引导令牌）等。

类型的意义在于"约定即校验"。比如声明成 `kubernetes.io/tls` 的 Secret，Kubernetes 会要求它必须包含 `tls.crt` 和 `tls.key` 两个键，缺了就报错——这样 Ingress 等组件就能放心地按固定键名取证书。对初学者来说，绝大多数场景用默认的 `Opaque` 就够了；`dockerconfigjson` 与 `tls` 是最常打交道的两个专用类型（分别对应"从私有仓库拉镜像"和"给服务配 HTTPS 证书"，后者呼应大主题6 的 imagePullSecrets 语境）。

### 13-11.1.6 Secret 加固：静态加密、RBAC 与外部 Secret

既然 base64 不是加密（13-11.1.2），真正保护 Secret 需要额外手段，官方文档明确列出几条：其一，开启 etcd 的**静态加密（encryption at rest）**，通过 API server 的 EncryptionConfiguration 配置，让 Secret 落到 etcd 时是密文；其二，配好**最小权限的 RBAC**，严格限制谁能读 Secret；其三，考虑使用**外部 Secret 存储**（如专门的密钥管理系统），把真正的密钥托管在集群之外。

这里有一个初学者常忽视的权限陷阱值得点明：在某个命名空间里**有权创建 Pod 的人，实际上就能读到该命名空间里的任意 Secret**——因为他可以创建一个把目标 Secret 挂进去的 Pod 再读出来，这属于"间接读取"。所以 RBAC 授权不能只盯着"谁能直接 get secret"，还要考虑"谁能创建 Pod/Deployment"。整体上，Secret 的定位应理解为"更规范、更可授权、可加密的配置载体"，安全性来自围绕它的这一整套机制（静态加密 + RBAC + 尽量不落盘 + 外部托管），而不是 base64 编码本身。

#### 来源与时效
- 锚点：Kubernetes 官方文档 ConfigMap（kubernetes.io/docs/concepts/configuration/configmap/，v1.36）——`data`/`binaryData`/`immutable` 字段、1MiB 上限、四种消费方式、挂载卷自动更新而环境变量不更新；核实 2026-08-01。
- 锚点：Kubernetes 官方文档 Secret（kubernetes.io/docs/concepts/configuration/secret/，v1.36）——base64 非加密、etcd 存储、EncryptionConfiguration 静态加密、内置类型清单、`data`/`stringData`、`immutable`、1MiB、"能建 Pod 即能读 Secret"的 RBAC 风险；核实 2026-08-01。
- 交叉核对：ConfigMap 与 Secret 两页在"1MiB 上限""immutable 关闭 watch 降负载""挂载卷自动更新 vs env 不更新"上互相印证，无冲突。
- `immutable` 引入版本记为 v1.19（官方 ConfigMap 文档标注）；如需对特定小版本核准可再查该版本 changelog（当前信息取自 v1.36 稳定文档）。
- K8s 存储 API ⚙演进快·锚版本 v1.36·随时变。

---

## 13-11.2 Volume 类型

### 13-11.2.1 Volume 概念：文件系统之外、生命周期与容器解绑的可挂载目录

容器内进程默认只能看到自己镜像里的文件系统，且这套文件系统随容器销毁而消失、容器重启即回到镜像初始态。Volume（卷）就是为解决两个痛点而生：一是让数据"活得比单个容器久"（容器崩溃重启后数据还在），二是让同一个 Pod 里的多个容器**共享**同一份文件。Volume 在 Pod 的 `spec.volumes` 里声明，再由各容器用 `volumeMounts` 挂到自己文件系统的某个路径上。

关键心智是"两级生命周期"。Volume 分两大阵营：**临时卷（ephemeral）**的生命周期绑定在 **Pod** 上——Pod 没了卷就没了；**持久卷**的生命周期**独立于** Pod——Pod 没了数据还在（见 13-11.3）。注意区分"容器"与"Pod"：即使容器崩溃重启（Pod 还在），临时卷里的数据也不会丢，因为它挂在 Pod 而非容器上；只有整个 Pod 被删除，临时卷才随之销毁。这个"容器 vs Pod"的区别是初学者最容易混淆的点。机制上，容器"能看到挂载点"依旧是大主题2 mount namespace 的功劳——kubelet 把卷的后端挂进 Pod 各容器的挂载视图。

### 13-11.2.2 emptyDir：与 Pod 同生命周期的临时卷

`emptyDir` 是最基础的临时卷。当 Pod 被调度到某节点时创建，初始为空，Pod 内所有容器都能读写它；当 Pod 从节点上被移除时，`emptyDir` 里的数据被**永久删除**。

它的典型用途是"暂存盘"：缓存、临时计算中间结果、或作为同 Pod 内两个容器之间交换文件的"中转盘"（正是大主题7 里 writer/reader 两容器通过 `emptyDir` 协作的场景）。要点是它随 Pod 生随 Pod 死，绝不能用来存需要长期保留的数据。一个可选项 `medium: Memory` 能让 emptyDir 用内存（tmpfs）而非磁盘做后端，读写更快但占内存、且节点重启即失；`sizeLimit` 可给它设容量上限：

```yaml
volumes:
  - name: cache-volume
    emptyDir:
      sizeLimit: 500Mi
      medium: Memory   # 可选：用内存做后端
```

### 13-11.2.3 hostPath：挂宿主机路径与安全风险

`hostPath` 把**宿主机节点**上的某个文件或目录直接挂进 Pod。这样容器就能读写它所在那台物理/虚拟机上的真实路径。

官方文档对它有明确的**安全告警**：hostPath 会打破容器隔离，让 Pod 触达宿主机文件系统，可能读到敏感文件或写坏宿主，是容器逃逸和越权的常见入口，**生产环境一般不推荐**。除此之外它还有个实用陷阱：数据绑死在**某台具体节点**上，一旦 Pod 被重新调度到别的节点，就看不到原来的数据了——所以它天然不适合需要跨节点迁移的负载。初学者应把 hostPath 视为"特殊运维/系统级 DaemonSet 才用"的工具（比如节点级日志/监控代理需要读宿主机上的路径），普通应用要持久化数据应走 PV/PVC（13-11.3）。

### 13-11.2.4 投射卷与 configMap/secret/downwardAPI 卷

ConfigMap 和 Secret 除了当环境变量，也能作为**卷**挂进容器，变成一组只读文件（见 13-11.1.3）。与之同类的还有 `downwardAPI` 卷——把 Pod 自身的元信息（如名字、命名空间、标签、资源限额）暴露成文件供容器读取。这几类卷的共同点是"内容来自 Kubernetes 对象、只读、生命周期随 Pod"。

`projected`（投射卷）则把上述多个来源（configMap、secret、downwardAPI，以及 serviceAccountToken）**合并投射到同一个挂载目录**下。它的价值在于"一个挂载点、多个来源"：比如一个应用既要读一份配置文件、又要读一个证书、还要读服务账户令牌，用投射卷可以把它们整齐地聚到同一个目录里，而不必挂三个不同的路径。对初学者，先理解"configMap/secret/downwardAPI 都能以卷形式变成文件"，再把 projected 理解为"它们的组合器"即可。

### 13-11.2.5 persistentVolumeClaim 卷与临时 vs 持久的分界

`persistentVolumeClaim` 是一种特殊的卷来源：它不直接描述后端存储，而是引用一个 PVC（见 13-11.3），从而把真正的持久存储挂进 Pod。这是应用消费持久存储的标准入口——Pod 只说"我要用名为 X 的 PVC"，具体是哪块云盘、哪台 NFS，全被 PV/PVC 抽象掉了。

由此得到本小主题的总纲——那条"临时 vs 持久"的分界线：`emptyDir`、configMap/secret/downwardAPI 卷、projected 卷、以及通用临时卷（generic ephemeral volume）都属于**临时**，生命周期绑 Pod，Pod 一删就没；而通过 `persistentVolumeClaim` 引入的卷属于**持久**，其数据独立于任何单个 Pod 存在，Pod 删了数据还在。初学者选型时先问一句"这份数据需要活得比 Pod 久吗"——不需要就用 emptyDir，需要就走 PVC。至于 hostPath，它虽然数据能留在节点上，但绑死单节点、且有安全风险，属于两者之外的特例，不作为常规持久化手段。

#### 来源与时效
- 锚点：Kubernetes 官方文档 Volumes（kubernetes.io/docs/concepts/storage/volumes/，v1.36）——emptyDir（Pod 生命周期、`medium: Memory`、`sizeLimit`）、hostPath 安全告警、configMap/secret/downwardAPI/projected 卷、persistentVolumeClaim 卷、"临时卷随 Pod 销毁、持久卷独立于 Pod"的分界；核实 2026-08-01。
- 交叉核对：Volumes 页与 Pod/工作负载文档（大主题7）在"emptyDir 跨容器重启保留、Pod 删除才销毁"上一致；与 mount namespace 机制（Linux man page namespaces(7)，大主题2）在"挂载视图注入"上互为佐证，无冲突。
- CoW/tmpfs 具体行为、通用临时卷的细化字段本篇未逐一实证，如需精确字段以 v1.36 Volumes 页与 API 参考为准；标「待核」仅限未在文档明列的默认值。
- K8s 存储 API ⚙演进快·锚版本 v1.36·随时变。

---

## 13-11.3 PV / PVC / StorageClass

### 13-11.3.1 供给-申领分离：PersistentVolume vs PersistentVolumeClaim

持久存储在 Kubernetes 里被拆成两个对象。**PersistentVolume（PV）**是集群里的一块"已经存在的存储资源"，由管理员预先创建或由系统动态创建，它描述真实存储的细节（容量、访问模式、后端类型），生命周期独立于任何使用它的 Pod。**PersistentVolumeClaim（PVC）**是用户提出的"存储申请单"——"我要 5Gi、可读写的存储"，它不关心底层是什么。

这套"供给（PV）与申领（PVC）分离"的设计，本质是把两种角色解耦：管理员/系统负责**供给**存储（关心磁盘、性能、后端），应用开发者负责**申领**存储（只关心要多大、什么访问模式）。这与大主题7 里"Pod 申请 CPU/内存"是完全对称的心智：PVC 之于存储，正如 Pod 的 resources.requests 之于计算。初学者可以这样记：PVC 是一张"提货单"，PV 是"货"，两者匹配后绑定，Pod 再通过引用 PVC 来用这块存储（13-11.2.5）。

### 13-11.3.2 静态供给 vs 动态供给

PV 的来源有两种。**静态供给**：管理员手工预先创建一批 PV，放在那里等着被申领；PVC 来了就从中挑一个匹配的绑上。**动态供给**：当没有现成 PV 匹配某个 PVC 时，集群根据 PVC 引用的 **StorageClass** 自动"现造"一个 PV 出来。

动态供给是现代集群的常态，因为它把管理员从"预判需求、手工囤 PV"里解放出来——应用要多少、什么时候要，系统按 StorageClass 的模板即时创建。触发动态供给需要几个条件：PVC 引用了一个 StorageClass、该 StorageClass 配好了 provisioner、且 API server 启用了 `DefaultStorageClass` 准入控制器。一个初学者易忽略的细节：PVC 若显式把 `storageClassName` 设为空串 `""`，等于**明确禁用**动态供给（只从静态 PV 里找）；而完全不写 `storageClassName` 则会用集群默认 StorageClass。

### 13-11.3.3 绑定与访问模式

PVC 创建后，集群有一个控制循环持续地把它和合适的 PV 配对，绑定是**一对一、排他**的（通过双向的 `claimRef` 锁定）——一个 PV 一旦绑给某 PVC，就不会再被别的 PVC 用。若暂时没有匹配的 PV，PVC 会一直处于未绑定状态等待。

匹配时的一个核心约束是**访问模式（accessModes）**，PV 声明自己支持什么、PVC 请求要什么，必须兼容才能绑。四种模式：

```
ReadWriteOnce (RWO)     单个节点可读写
ReadOnlyMany  (ROX)     多个节点只读
ReadWriteMany (RWX)     多个节点可读写
ReadWriteOncePod (RWOP) 单个 Pod 可读写
```

`ReadWriteOnce` 的粒度是**节点**不是 Pod——它允许同一节点上的多个 Pod 同时读写，只是不允许跨节点。很多人误以为 RWO 等于"只有一个 Pod 能用"，真正"只允许单个 Pod"的是较新的 `ReadWriteOncePod`。另一个现实约束：并非所有后端都支持 RWX（多节点读写），块存储（如云盘）通常只支持 RWO，要 RWX 往往得用文件存储（如 NFS/CephFS），这是选型时的常见坑。

### 13-11.3.4 回收策略：Retain / Delete / Recycle（弃用）

当 PVC 被删除、其绑定的 PV 被"释放"后，PV 里的数据和存储该怎么处理，由**回收策略（reclaim policy）**决定。三种：`Retain`（保留——PV 与底层存储都留着，需管理员手工清理和删除，数据不丢）、`Delete`（删除——PV 与其对应的外部真实存储被自动一并删除）、`Recycle`（旧式的简单擦除 `rm -rf`，已**弃用**，不建议用）。

对初学者最需要记住的是**默认值带来的数据风险**：**动态供给的卷，其回收策略继承自 StorageClass，而 StorageClass 的 reclaimPolicy 默认是 `Delete`**。这意味着——如果你用了默认动态供给、然后删掉了 PVC，那块存储连同里面的数据会被自动删除且不可恢复。生产环境保存重要数据时，常把 StorageClass 的回收策略显式设为 `Retain` 以防误删。这是一个"默认行为咬人"的经典案例，务必核对而非想当然。

### 13-11.3.5 StorageClass 字段：动态供给的模板

**StorageClass** 描述一"类"存储（好比存储的档位/模板），是动态供给的驱动。核心字段：`provisioner`（必填，指定用哪个卷插件来造 PV，如某 CSI 驱动）、`parameters`（传给 provisioner 的厂商特定参数，如磁盘类型、IOPS）、`reclaimPolicy`（动态创建出的 PV 的回收策略，**默认 `Delete`**）、`volumeBindingMode`（绑定时机）、`allowVolumeExpansion`（是否允许后续扩容 PVC，**默认 false**）。默认 StorageClass 通过注解 `storageclass.kubernetes.io/is-default-class: "true"` 标记。

`volumeBindingMode` 值得单独讲，它有两个取值：`Immediate`（**默认**，PVC 一创建就立即绑定/供给卷）和 `WaitForFirstConsumer`（延迟到有 Pod 真正要用这个 PVC 时才绑定/供给）。后者解决了一个真实痛点——拓扑约束：如果存储只能被某些可用区的节点访问，`Immediate` 可能在 Pod 还没调度时就把卷建在了错误的可用区，导致 Pod 永远无法调度到能访问它的节点上；`WaitForFirstConsumer` 让绑定等到调度器已经知道 Pod 该去哪个节点，再在对的地方供给卷。一个动态供给的 StorageClass 示例：

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast
provisioner: csi-driver.example.com
reclaimPolicy: Retain               # 默认为 Delete
allowVolumeExpansion: true          # 默认为 false
volumeBindingMode: WaitForFirstConsumer   # 默认为 Immediate
parameters:
  type: ssd
```

### 13-11.3.6 PV 生命周期阶段与使用中保护

PV 有一个 `status.phase` 字段反映它所处的阶段：`Available`（空闲，尚未被任何 PVC 绑定）、`Bound`（已绑定到某 PVC）、`Released`（绑定的 PVC 已被删除、但 PV 尚未被回收处理）、`Failed`（自动回收过程出错）。理解这几个阶段有助于排障——比如一个 PV 卡在 `Released` 迟迟没释放，往往是回收策略是 Retain 需要人工处理，或回收失败。

还有一层"使用中保护（Storage Object in Use Protection）"值得初学者知道：如果你删除一个仍被 Pod 使用中的 PVC，删除会被推迟，PVC 进入 `Terminating` 但直到没有 Pod 再用它才真正删除（靠 finalizer `kubernetes.io/pvc-protection`）；同理，删除一个仍被 PVC 绑定的 PV 也会被推迟（finalizer `kubernetes.io/pv-protection`）。这是为防止"存储正被用着却被误删导致数据损坏"设的安全网，解释了为什么有时 `kubectl delete pvc` 会看起来"卡住"——其实是保护机制在等使用者先退出。

#### 来源与时效
- 锚点：Kubernetes 官方文档 Persistent Volumes（kubernetes.io/docs/concepts/storage/persistent-volumes/，v1.36）——PV/PVC 供给-申领分离、静态 vs 动态、一对一排他绑定、访问模式 RWO/ROX/RWX/RWOP、回收策略 Retain/Delete/Recycle(弃用) 及"动态卷默认 Delete"、生命周期阶段 Available/Bound/Released/Failed、pvc/pv-protection finalizer；核实 2026-08-01。
- 锚点：Kubernetes 官方文档 Storage Classes（kubernetes.io/docs/concepts/storage/storage-classes/，v1.36）——`provisioner`/`parameters`/`reclaimPolicy`(默认 Delete)/`volumeBindingMode`(默认 Immediate)/`allowVolumeExpansion`(默认 false)、默认类注解 `storageclass.kubernetes.io/is-default-class`；核实 2026-08-01。
- 交叉核对：两页在"动态供给回收策略默认 Delete"上互相印证（PV 页说继承自 StorageClass，SC 页说 reclaimPolicy 默认 Delete），一致无冲突；访问模式定义在 PV 页与 API 参考一致。
- `ReadWriteOncePod` 为较新访问模式，本篇按 v1.36 文档列为 GA；若需其精确 GA 版本可查对应 changelog（当前取 v1.36 稳定文档）。
- K8s 存储 API ⚙演进快·锚版本 v1.36·随时变。

---

## 13-11.4 CSI 存储接口

### 13-11.4.1 CSI 是什么：容器编排系统无关的存储插件标准

**CSI（Container Storage Interface，容器存储接口）**是一个行业标准，用来把任意的块存储和文件存储系统，以统一的方式暴露给运行在容器编排系统（Container Orchestration System，CO，如 Kubernetes）上的工作负载。它的一句话价值是：让存储厂商"**写一次插件，就能跑在多个编排系统上**"，而不必为 Kubernetes、Mesos、Nomad 各写一套。

理解 CSI 要先理解它要解决的历史问题。早期 Kubernetes 把各家存储的对接代码直接编进自己的主干（所谓 in-tree 插件），后果是：加一个新存储得改 Kubernetes 源码、跟着 Kubernetes 发版，存储厂商和 Kubernetes 被死死绑在一起。CSI 把这层接口**标准化并外置**——定义一组厂商去实现的 gRPC 接口，Kubernetes 只面向这组标准接口说话，谁家的存储都能作为"外部插件"接进来，各自独立发版。这就是本主题跨 L4-06"配置管理"的衔接点：CSI 本质是把"存储供给"抽象成一套可插拔、声明式的标准接口，与 K8s 声明式配置管理的思路一脉相承。关键一点：**CSI 规范本身是编排系统中立（CO-agnostic）的**，不属于 Kubernetes——Kubernetes 只是它最主要的消费者之一。

### 13-11.4.2 三个 gRPC 服务与关键 RPC

CSI 规范定义了插件要实现的三个 gRPC 服务。**Identity 服务**：Controller 和 Node 插件都要实现，提供插件的身份信息、能力和健康探测（GetPluginInfo、GetPluginCapabilities、Probe）。**Controller 服务**：负责集群级、与具体节点无关的卷操作——创建/删除卷、把卷挂接（attach）到某节点等（如 CreateVolume、DeleteVolume、ControllerPublishVolume）。**Node 服务**：负责在工作负载真正运行的那个节点上做本地操作——把卷 stage 到节点、再挂载（publish）进容器目标路径（如 NodeStageVolume、NodePublishVolume）。

初学者可以用"两阶段、两层次"来记这套模型。两层次是 Controller（集群大脑，管"这块卷该建在哪、attach 给谁"）和 Node（节点手脚，管"把卷在本机挂起来给容器用"）。Node 侧的两阶段是 Stage（把卷格式化/挂到节点上一个全局位置，一个节点做一次）和 Publish（再把它 bind 挂进具体容器的目标路径，每个使用它的 Pod 各做一次）——这样多个同节点 Pod 共用一块卷时不必重复格式化。这些 RPC 都要求**幂等**：同样的请求重复发来结果一致，不会重复建卷，因为分布式环境里重试是常态。这套抽象的落地又回到了大主题2 的 mount namespace——NodePublishVolume 最终就是把后端挂进 Pod 容器的挂载视图。

### 13-11.4.3 CSI 在 Kubernetes 里怎么落地

在 Kubernetes 侧，CSI 表现为一种 `csi` 卷类型，以及一整套把"CSI 驱动"接入集群的部署组件。CSI 支持在 **Kubernetes v1.13** 达到 GA（核实 2026-08-01；对应可用 CSI 规范 v0.3.0 与 v1.0.0）。存储厂商提供一个"CSI 驱动容器"（实现上面三个服务），Kubernetes 社区则提供一组**边车容器（sidecar）**来把它接进 K8s 的对象模型，让厂商不必了解 K8s 内部细节。

`external-provisioner`（监听 PVC，调 CSI 的 CreateVolume 来动态供给 PV）、`external-attacher`（处理卷的 attach/detach）、`node-driver-registrar`（在每个节点上把 CSI 驱动注册给 kubelet）、`external-resizer`（处理扩容）、`external-snapshotter`（处理快照）、`livenessprobe`（健康探测）。初学者不必记全，只需抓住心智：**K8s 的 PVC/StorageClass（13-11.3）是"前台申领"，CSI 驱动 + 这些边车是"后台执行"**——你写一个引用某 StorageClass 的 PVC，external-provisioner 就替你去调对应 CSI 驱动的 CreateVolume 把真实的卷造出来。这条链把 13-11.3 的声明式申领和 13-11.4 的插件执行接了起来。

### 13-11.4.4 in-tree 迁移到 CSI（CSI migration）

由于历史上许多存储（如各家云盘）是以 in-tree 插件内建在 Kubernetes 里的，社区推行了 **CSI migration**：把这些内建插件的功能逐步迁移到对应的外部 CSI 驱动上，同时保持用户原来的 PV/PVC/StorageClass 写法不变——迁移在底层透明发生，老配置继续能用，只是实际干活的换成了 CSI 驱动。

对初学者，这条的意义主要是"消除困惑"：你可能在文档里看到某些老的 in-tree 卷类型被标记为 deprecated 或"已迁移到 CSI"，不必慌——这不是要你立刻改 YAML，而是底层实现的换血，目标是最终让所有存储都走统一的 CSI 路径、把存储代码彻底移出 Kubernetes 主干。方向明确：新接入的存储一律用 CSI，in-tree 是被逐步淘汰的历史包袱。具体某个 in-tree 插件在哪个版本完成迁移/移除，随版本推进，标「待核」，以对应版本 changelog 为准，不在此凭记忆断言。

#### 来源与时效
- 锚点：CSI 规范（container-storage-interface/spec，spec.md）——"一次开发、跨多 CO 运行"的目标、三个 gRPC 服务（Identity/Controller/Node）职责、CreateVolume/DeleteVolume/ControllerPublishVolume/NodeStageVolume/NodePublishVolume 等 RPC、幂等要求、CO 中立设计；核实 2026-08-01。
- 锚点：Kubernetes CSI 文档（kubernetes-csi.github.io/docs/）——"CSI 是把任意块/文件存储暴露给 CO 的标准"、CSI 在 Kubernetes **v1.13 GA**（兼容 CSI spec v0.3.0/v1.0.0）、边车容器 external-provisioner/external-attacher/node-driver-registrar/external-resizer/external-snapshotter/livenessprobe 分工；核实 2026-08-01。
- 交叉核对：K8s Volumes 页将 CSI 列为 out-of-tree 插件并指出 in-tree 正迁移到 CSI，与 kubernetes-csi 文档、CSI 规范在"标准化外置存储接口""in-tree 迁移"上一致无冲突。
- CSI 规范当前版本号本次未从 spec.md 明确取到具体号（页面结构所限），标「待核」；GA 版本 v1.13 由 kubernetes-csi 官方文档明列。
- CSI migration 中各 in-tree 插件的具体完成/移除版本随版本演进，标「待核」，以对应 K8s 版本 changelog 为准。
- CSI 及 K8s 存储生态 ⚙演进快·锚版本（CSI 规范 / K8s v1.36）·随时变。
