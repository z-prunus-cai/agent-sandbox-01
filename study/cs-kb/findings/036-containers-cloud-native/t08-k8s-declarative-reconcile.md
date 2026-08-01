# L5-13·大主题8 K8s 声明式模型与 reconcile 循环

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01｜先修：L5-13 大主题7 K8s 工作负载对象、L4-01 OS 进程模型与 L4-02 网络（理解 API 前端/存储的分工）｜一手锚点：Kubernetes 官方文档 Cluster Architecture / Controllers / Working with Objects / Custom Resources / Operator（kubernetes.io，当前稳定 v1.36）｜成熟度：GA（⚙演进快·锚版本 v1.36·随时变）

声明式模型与 reconcile 循环是理解 Kubernetes（及整个云原生生态）最核心的一条心智线：你只描述「想要什么」，一群永不停机的控制器持续把「实际是什么」拉向它。本报告把这条主线拆成四段——声明式 API、reconcile 控制循环、承载它的控制平面、以及用 CRD/operator 把这套模型延伸到自定义对象。它与大主题7 的工作负载对象（那些对象正是被 reconcile 的目标）和大主题9 的服务发现（Endpoint 也由控制器收敛）连成一体。

Kubernetes 约每 3–4 个月发一个次要版本，字段、API 组、默认行为逐版演进。本报告锚定 v1.36（2026-04 发布，2026-08-01 为当前稳定；下一版 v1.37 计划 2026-08-26），凡涉及具体版本行为处均按此锚点，读者跨版本使用时须核对当版文档。

---

## 13-8.1 声明式 API

### 13-8.1.1 声明式 vs 命令式

声明式 API 指你向系统提交一份「期望的最终状态」的描述，而不是一串「怎么做」的操作步骤；系统自己负责算出并执行从当前到期望的差异。Kubernetes 的整套对象模型就建立在这个范式上：你写一份 YAML 说「我要 3 个副本的这个应用」，而不是「启动进程 A、再启动进程 B、再启动进程 C」。

命令式（imperative）像是你亲自下达每一步指令：`docker run` 起一个容器、挂了你再手动重起。声明式（declarative）像是你把目标交给一个管家——「始终保持 3 个在跑」——之后不管谁挂了、节点宕了，管家都会自动补齐。二者的分水岭是「谁负责弥合差异」：命令式是你，声明式是系统。

初学者最容易混的是 `kubectl` 里两种用法：`kubectl create`/`kubectl run`/`kubectl scale` 是命令式（一次性动作），而 `kubectl apply -f` 是声明式（提交期望态、可反复提交）。同一个工具兼具两种风格，但云原生的正道是声明式：它天然可版本化、可 diff、可回滚，是后文自愈与 GitOps 的地基。

### 13-8.1.2 spec 是期望态，status 是实际态

几乎每个 Kubernetes 对象都有两个关键字段：`spec` 由你填写，描述你想要的状态（desired state）；`status` 由系统填写并持续更新，描述对象当前的真实状态（current/observed state）。官方对此的表述是：`spec` 是「你希望资源具备的特征……它的期望态」，`status` 则「描述对象的当前状态，由 Kubernetes 系统及其组件提供并更新」。

用一个 Deployment 的最小例子看这对字段：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
spec:
  replicas: 3          # 期望态：我要 3 个副本
  selector:
    matchLabels: { app: web }
  template:
    metadata:
      labels: { app: web }
    spec:
      containers:
        - name: web
          image: nginx:1.27
```

你只写了 `spec.replicas: 3`。系统读到后启动 3 个实例，并把观察到的结果写回 `status`（如 `status.readyReplicas`）。任何一个实例挂掉都会先反映为 status 变化，系统随即补一个新的把 status 拉回等于 spec。


```
期望态(spec)  ——你写——>  Kubernetes  <——系统写——  实际态(status)
```

初学者常犯的错是去手动改 `status` 字段——那是系统的领地，改了也会被覆盖；你能控制的只有 `spec`。另一个易错点是把 `spec` 当成「命令执行一次」，其实它是「持续目标」：只要对象还在，系统就一直拿它当靶子。

### 13-8.1.3 kubectl apply 与幂等的声明式提交

声明式提交的核心工具是 `kubectl apply -f <file>`：你把完整的期望态清单交给它，它负责算出与集群现状的差异并只改需要改的部分。反复 apply 同一份不变的清单不会产生副作用——这就是幂等：

```
kubectl apply -f web.yaml    # 第一次：创建
kubectl apply -f web.yaml    # 再一次：无变化，不重建
```

它幂等的原因是 apply 比对的是「你声明的期望态」与「集群里已有的对象」，相同就什么都不做。这与命令式的 `kubectl create` 不同：对已存在对象再 `create` 会报「已存在」错误，而 `apply` 只是确认一致。

理解幂等对初学者很重要，因为它是把清单放进 Git、由流水线反复应用（GitOps）的前提：无论应用多少次，集群都收敛到清单描述的那个状态，不会因为「多跑了一次」而出乱子。易错点是混用命令式与声明式——比如先 `kubectl scale` 手动改成 5 个副本，下次 `apply` 一份写着 3 的清单又被拉回 3；两种风格对同一对象拉锯会让人困惑，实践中应二选一、以声明式为准。

### 13-8.1.4 声明式带来的自愈、可审计与 GitOps

因为期望态被持久保存、且系统持续朝它收敛，声明式模型天然带来三个工程红利：自愈（故障后自动回到期望态，无需人工介入）、可审计与可回滚（期望态就是一份文本，谁改了什么、回退到哪一版都清清楚楚）、以及 GitOps（把期望态清单当作唯一事实源放进 Git，用自动化持续 apply）。

直觉上，命令式世界里「系统当前长什么样」只存在于运行中的进程里，一旦漂移你很难知道「本该长什么样」；声明式世界里「本该长什么样」是一份独立于运行时的显式文档，系统随时可拿它纠偏。这也是「基础设施即代码」在编排层的体现。

声明式不等于「瞬间生效」或「永远成功」。系统是尽力持续收敛，收敛需要时间，且可能因为镜像拉不到、资源不足而卡住——这时 status/事件会告诉你卡在哪，但 spec 依然是那个不变的靶子。初学者别把「apply 成功返回」误解为「应用已经跑起来」，前者只是「期望态已被接受并存储」，实际收敛要看 status。

#### 来源与时效

- Kubernetes 官方文档《Objects In Kubernetes / Working with Objects》——spec 为期望态、status 为当前状态且由系统更新、控制平面持续把实际态匹配到期望态、Deployment 三副本自愈例子（kubernetes.io/docs/concepts/overview/working-with-objects/，v1.36，核实 2026-08-01）。
- Kubernetes 官方文档《Declarative Management of Kubernetes Objects Using Configuration Files》与《Kubernetes Object Management》——`kubectl apply` 声明式 vs `create`/`scale` 命令式的分工（kubernetes.io/docs/concepts/overview/working-with-objects/object-management/，v1.36，核实 2026-08-01）。
- 交叉核对：官方 Custom Resources 文档亦复述「你声明资源的期望态、控制器保持当前态与之同步」，与上面 spec/status 表述一致，无冲突。
- ⚙演进快·锚版本 v1.36·随时变：apiVersion（如 `apps/v1`）、字段随版本演进；GitOps 为社区实践（Argo CD/Flux 等工具，⚠二手指路，不承重），声明式/spec/status 机制本身为一手规范。

---

## 13-8.2 控制器 reconcile 循环

### 13-8.2.1 控制循环与 reconcile 的概念

控制器（controller）是一段持续运行的控制循环：它观察集群某类资源的状态，然后做出或请求改变，让当前状态更接近期望状态。官方原话是「控制器是监视集群状态的控制循环，在需要时做出或请求改变；每个控制器都试图让当前集群状态更接近期望状态」。这个「观察—比对—纠偏」的动作，就叫 reconcile（调谐/协调）。

你设定期望温度（期望态），恒温器测量当前温度（实际态），并开关制热/制冷来缩小差距。它不会问「现在几度、该加热几分钟」这种一次性指令，而是循环不断地测量与纠偏。Kubernetes 控制器就是集群版的恒温器，只不过它调的不是温度，而是「在跑的 Pod 数」「Service 后端列表」等。

初学者要建立的第一个直觉是：控制器不是「执行一次就结束的脚本」，而是「永不退出的循环」。你 apply 之后它不是跑一遍就走，而是持续盯着，一有偏差就再收敛。

### 13-8.2.2 期望态收敛：把 current 持续拉向 desired

reconcile 的本质是一个不断求解并弥合差距的过程：

```
loop forever:
    desired = 读取 spec（期望态）
    current = 观测实际态（status / 真实资源）
    if current != desired:
        采取动作缩小差距
```

控制器「跟踪至少一种资源类型，这些对象的 spec 字段代表期望态，该资源的控制器负责让当前态更接近期望态」。关键在于它是持续的：并不追求「到达某个稳定态后停机」。官方明确指出「你的集群可能永远达不到稳定态……只要控制器在运行且能做出有用的改变，整体是否稳定都不要紧」。

为什么设计成「永不停」而非「达标即停」？因为集群本就在持续变化：节点会宕、进程会崩、流量会变。一个只跑一次的调度不可能应对这些；只有持续 reconcile 才能实现自愈。初学者容易期待「reconcile 一次就大功告成」，实际上它是常态化的后台纠偏，收敛与新扰动此消彼长是正常的。

### 13-8.2.3 幂等与 level-triggered（面向状态而非面向事件）

一个健壮的 reconcile 必须是幂等的：拿同一份期望态反复执行，结果一致、不会重复制造副作用（比如不会因为被触发两次就起两个多余的 Pod）。实现幂等的关键设计是 level-triggered（电平触发/面向状态）而非 edge-triggered（边沿触发/面向事件）：控制器每次都读取「当前完整状态」并与期望态比对来决定动作，而不是依赖「捕捉到某个变更事件」来驱动。

edge-triggered 像门铃，只在「有人按」那一刻响，漏掉一次按铃就永远错过；level-triggered 像看水位，任何时候看一眼水位就知道该不该加水，即使中间漏看了几次，下次看仍能纠正。Kubernetes 控制器以状态为准（辅以事件作为「该看一眼了」的提醒），所以即便丢了事件、控制器重启、或同一变更被通知多次，reconcile 依然能收敛到正确结果。

这解释了初学者的一个常见困惑：为什么控制器重启后不会「乱套」？因为它不靠记忆「之前发生过什么事件」，重启后重新读一遍当前状态与期望态即可继续正确工作。写自定义控制器时，「reconcile 必须幂等、不假设只被调用一次」是头号纪律。

### 13-8.2.4 watch/informer 事件驱动而非死循环轮询

虽然概念模型是「循环」，但控制器并不真的忙等轮询（那会把 API server 压垮）。实际实现依靠 API server 提供的 watch 机制：控制器订阅它关心的资源，资源一有变化，API server 就把变更推送过来，触发一次 reconcile；平时则安静等待。客户端库通常用 informer（本地缓存 + watch）来承载这套机制，兼顾实时性与低负载。

不是每秒去问一遍「变了没、变了没」（轮询），而是「订阅通知，变了叫我」（watch）。同时为了防止漏事件或缓存陈旧，控制器还会周期性做全量 resync（重新对全部对象跑一遍 reconcile）——这正好也印证了上一节的 level-triggered 幂等设计：resync 反复触发也不会出错。

概念层是「持续控制循环」，实现层是「watch 事件驱动 + 周期 resync」。它不是低效的死循环轮询，也不是纯事件驱动（纯事件会漏），而是两者结合、以状态为最终依据。

### 13-8.2.5 控制器经 API server 间接生效，用标签/属主区分资源

控制器执行纠偏时，大多数情况下不亲自创建容器，而是向 API server 发请求、由请求的副作用达成目标。官方以 Job 控制器为例：「Job 控制器看到新任务时，会确保集群某处的 kubelet 在一组节点上运行正确数量的 Pod……Job 控制器自己不运行任何 Pod 或容器，而是告诉 API server 去创建或删除 Pod」。也有少数控制器直接与外部系统（云厂商 API）交互。

这带来一个问题：集群里有多个控制器（Deployment 控制器、Job 控制器……）都在创建 Pod，谁的 Pod 归谁管？答案是靠元数据区分——标签（labels）与属主引用（ownerReferences）。官方说「你可以同时有 Deployment 和 Job，它们都创建 Pod；Job 控制器不会删除你的 Deployment 创建的 Pod，因为有信息（标签）让控制器区分它们」。这套「一个控制器管一类关注点、靠标签认领自己的资源」的分工，是 Kubernetes 可组合、可扩展的根基（呼应「关注点分离」的设计原则）。

手动改动一个由控制器管理的对象（比如手删一个 Deployment 管的 Pod），控制器会立刻按期望态把它补回来——这不是 bug，正是 reconcile 在工作。要真正减少副本，得改 Deployment 的 `spec.replicas`（期望态），而不是去删 Pod（实际态）。

#### 来源与时效

- Kubernetes 官方文档《Controllers》——控制器即控制循环、恒温器类比、当前态趋近期望态、「集群可能永不稳定亦无妨」、经 API server 间接生效、Job 控制器不自己跑 Pod、标签区分资源、内建控制器在 kube-controller-manager 内（kubernetes.io/docs/concepts/architecture/controller/，v1.36，核实 2026-08-01）。
- level-triggered vs edge-triggered、幂等、watch/informer 与 resync：由 Kubernetes API 概念文档《Efficient detection of changes / Watch》与社区 client-go informer 机制佐证，与《Controllers》「watch 集群状态」表述一致（kubernetes.io/docs/reference/using-api/api-concepts/，v1.36，核实 2026-08-01）。level-triggered 作为 Kubernetes 设计哲学的表述广见于官方设计文档与社区，机制正确性以「watch + 幂等 reconcile」一手描述承重；「level/edge」术语本身为设计习语。
- 交叉核对：训练所得的 Kubernetes 控制器通识与上述官方文档一致，无冲突。
- ⚙演进快·锚版本 v1.36·随时变：具体控制器集合、informer 库细节随版本/客户端库演进；控制循环与幂等收敛的机制心智稳定。

---

## 13-8.3 控制平面架构

### 13-8.3.1 kube-apiserver：集群的唯一读写前端

kube-apiserver 是 Kubernetes 控制平面的前端，对外暴露 Kubernetes API，处理所有 API 请求，是集群的主要管理入口。所有对集群状态的读与写——无论来自 `kubectl`、控制器还是 kubelet——都经过它。它被设计为可水平扩展：部署多个实例并做负载均衡即可。

可以把 API server 想成集群的「总服务台」：任何人想知道或改变集群状态，都得到这个窗口来办，不能绕过它直接动数据库或节点。这种「单一入口」设计带来统一的认证、鉴权、准入校验（admission）与审计——安全与一致性都收口在这里。

几乎没有组件直接读写 etcd，大家都通过 API server。控制器、scheduler、kubelet 之间也不直接互相调用，而是各自与 API server 交互、以 API 对象为中介松耦合——这正是前一章 watch 机制能统一工作的前提。

### 13-8.3.2 etcd：一致且高可用的唯一事实存储

etcd 是一个一致性、高可用的键值存储，作为 Kubernetes 所有集群数据的后端存储（backing store）。集群里每一个对象的期望态与状态，最终都持久化在 etcd 里。官方特别提醒：要为 etcd 数据制定备份计划——它丢了，集群状态就丢了。

etcd 用 Raft 共识协议在多个副本间保持强一致，这就是它「即使部分节点故障也能保证数据不乱」的底气（协议正确性属分布式共识范畴，此处只需知道它提供强一致的可靠存储）。它是集群的「唯一事实来源」（single source of truth）：所有组件对「集群现在到底是什么状态」的认知，归根结底都来自 etcd（经 API server 读出）。

把 etcd 当成「可以直接连上去改」的数据库。生产中严禁绕过 API server 直接写 etcd——那会跳过所有校验与 watch 通知，让集群状态与各组件认知脱节。etcd 只由 API server 访问，这是纪律也是设计。

### 13-8.3.3 kube-scheduler：为未调度的 Pod 挑节点

kube-scheduler 是控制平面组件，监视新创建、尚未分配节点的 Pod，为每个这样的 Pod 选择一个合适的节点去运行。它做决策时考虑的因素包括：单个与整体的资源需求、硬件/软件/策略约束、亲和与反亲和规则、数据局部性、工作负载间干扰、截止期限等。

scheduler 本身也是一个控制器/控制循环：它的「期望态」是「所有 Pod 都被分配到节点」，它持续把「有 Pod 还没节点」这个差距收敛掉。要注意它只负责「决定放哪」（把选中的节点写进 Pod 的绑定），真正把容器跑起来的是目标节点上的 kubelet——这体现了「决策与执行分离」。

初学者常问「Pod 卡在 Pending 是不是 scheduler 坏了」：更常见的是没有节点满足这个 Pod 的资源/约束（比如 requests 太大、污点不容忍），scheduler 找不到可行节点就只能让它 Pending，并在事件里说明原因。调度的过滤+打分细节属大主题10，本章只需知道它是控制平面里专管「放置」的那个控制器。

### 13-8.3.4 kube-controller-manager：内建控制器的集合体

kube-controller-manager 运行 Kubernetes 内建的一批控制器进程。逻辑上它们是相互独立的控制器，但为了降低复杂度，被编译进同一个二进制、在同一个进程里运行。官方举出的内建控制器例子包括：节点控制器（Node controller，发现并响应节点宕机）、Job 控制器（管理一次性任务的 Pod）、EndpointSlice 控制器（把 Service 关联到 Pod）、ServiceAccount 控制器（为新命名空间建默认 ServiceAccount）等。

把它理解成「一屋子恒温器住在一起」：每个控制器管一类关注点，各自跑各自的 reconcile 循环，但打包在一个进程里方便部署与运维。前一章说的「控制器」在集群里的物理落脚点，大部分就在这个组件内。

初学者要区分「控制器」（概念/逻辑单元，有很多个）与「controller-manager」（把内建控制器装在一起的那个进程组件）。第三方或自定义控制器（operator）通常不住在这里，而是作为独立 Pod 部署——见 13-8.4。

### 13-8.3.5 cloud-controller-manager：云集成（可选）

cloud-controller-manager 内嵌与具体云厂商相关的控制逻辑，把你的集群与云厂商 API 对接。它只在云环境里运行；自建（on-premises）或本地学习环境里没有它。它把「与云相关的控制器」从 kube-controller-manager 里拆出来，好处是云厂商可以独立于 Kubernetes 主干迭代自己的集成代码。

它承载的典型控制器有：节点控制器（判断云上被删除的节点）、路由控制器（配置云网络路由）、服务控制器（管理云负载均衡器，例如你建一个 `type: LoadBalancer` 的 Service，它去云上开一个 LB）。它逻辑上是单进程，也可水平扩展。

它是「可选的、云专属的」控制平面组件，本地 minikube/kind 里看不到它是正常的；理解它体现了 Kubernetes「把厂商相关逻辑与核心解耦」的一贯思路即可。

### 13-8.3.6 节点侧执行末端：kubelet 与 kube-proxy

控制平面决定「应该是什么」，真正在每台工作节点上落地的是节点组件。kubelet 是每个节点上的代理，确保容器按 PodSpec 在 Pod 中运行——它只管由 Kubernetes 创建的容器。kube-proxy 是（可选的）节点网络代理，维护节点上的网络规则、实现 Service 概念（若网络插件已自带等价转发则可不需要它）。容器运行时（containerd/CRI-O 等，经 CRI 接口）负责真正执行容器。

把这一层理解为 reconcile 的「最后一公里」：scheduler 把 Pod 绑到节点后，该节点的 kubelet 看到「有个 Pod 该我跑」，就调用容器运行时把它拉起来，并把观察到的状态写回 API server（更新 status）。kubelet 自身也是一个面向状态的循环：持续让本节点实际运行的容器匹配分配给它的 PodSpec。

这把整条声明式链路闭合了：你写 spec → 存进 etcd（经 API server）→ scheduler/控制器决策 → kubelet 在节点执行 → status 写回。初学者由此能看清「一个 `kubectl apply` 之后到底发生了什么」的全貌。kube-proxy/网络与存储的细节分别归大主题9、大主题11。

#### 来源与时效

- Kubernetes 官方文档《Cluster Architecture》——kube-apiserver（API 前端、可水平扩展）、etcd（一致高可用 KV、需备份）、kube-scheduler（为未分配节点的新 Pod 选节点及其考量因素）、kube-controller-manager（内建控制器编入单一进程，含 Node/Job/EndpointSlice/ServiceAccount 控制器）、cloud-controller-manager（云集成、仅云环境、含 Node/Route/Service 控制器）、kubelet/kube-proxy/容器运行时职责（kubernetes.io/docs/concepts/architecture/，v1.36，核实 2026-08-01）。
- etcd 强一致（Raft）与「唯一事实源」：etcd 官方文档佐证（etcd.io/docs/，一手），与 K8s 文档「一致高可用 KV 后端存储」一致，无冲突。
- 交叉核对：官方《Controllers》文档亦述内建控制器运行于 kube-controller-manager，与《Cluster Architecture》一致。
- ⚙演进快·锚版本 v1.36·随时变：具体内建控制器清单、组件默认参数随版本演进（如 kube-proxy 在部分网络方案下可省、部分职责随特性门控变动）；控制平面五组件的核心分工心智稳定。待核：跨云厂商 cloud-controller-manager 承载的控制器集合各有差异，以各厂商文档为准。

---

## 13-8.4 CRD / operator 扩展

### 13-8.4.1 自定义资源（CR）与 CustomResourceDefinition（CRD）

自定义资源（custom resource, CR）是对 Kubernetes API 的扩展，代表某个具体安装的定制，默认安装里不一定有。CustomResourceDefinition（CRD）是加入自定义资源的两种方式中较简单的一种：你定义一个 CRD 对象，就创建出一个带有你指定名称与 schema 的新资源类型，之后由 Kubernetes API server 负责为它提供服务与存储。装好后，你就能像操作内建的 Pod 一样，用 `kubectl` 创建和访问这些自定义对象。

直觉上，CRD 让你「教会」集群认识一个新名词。比如你想让集群理解「数据库」这个概念，可以定义一个 `Database` CRD，之后就能：

```yaml
apiVersion: example.com/v1
kind: Database
metadata:
  name: my-db
spec:
  engine: postgres
  version: "16"
  storageGB: 20
```

它遵循 Kubernetes 的一切惯例（`.spec`/`.status`/`.metadata`、`kubectl get/apply`、RBAC、watch）。CRD 的最大优点是简单、无需编程即可创建，也无需自己写 API server。

初学者要建立的关键认识（下一节展开）：光有 CRD，自定义对象只是「能存能取的结构化数据」——把上面的 `Database` 建出来，集群并不会真去装一个 Postgres。要让它「活起来」，还需要一个控制器。

### 13-8.4.2 CRD vs 聚合 API（两种扩展方式）

加自定义资源有两条路：CRD 与 API 聚合（aggregated API / aggregation layer）。CRD 简单、声明式、无需编程，复用现有的 kube-apiserver 存储与服务；聚合 API 则要你自己实现并运行一个独立的 API server，接入聚合层，换来对 API 行为更精细的控制（自定义存储、特殊校验/子资源逻辑等）。

绝大多数场景用 CRD 就够了；只有当你需要 CRD 给不了的灵活性（例如非 etcd 存储、特殊的一致性或协议行为、或要包装一个已有的 REST API）时，才上聚合 API。官方的对比表也是这个结论——CRD 易用但通用性带来灵活度受限，聚合 API 更灵活但要写代码、要运维额外的 API server。

初学者阶段几乎只会遇到 CRD（生态里的 operator 全都基于 CRD）。知道聚合 API 存在、是「更重但更灵活」的另一条路即可，不必深究。

### 13-8.4.3 CR + 自定义控制器 = 真正的声明式 API

自定义资源单独存在时只能存取数据；当你为它配一个自定义控制器（custom controller），它才提供「真正的声明式 API」。官方原话：「自定义资源本身只让你存取结构化数据；当你把自定义资源与自定义控制器结合，它们才提供真正的声明式 API……你声明资源的期望态，Kubernetes 控制器让 Kubernetes 对象的当前态与你声明的期望态保持同步」。

也就是说，自定义控制器把大主题前两节的 reconcile 循环用到你的新资源上：它 watch 你的 `Database` 对象，读它的 `spec`，然后去创建 StatefulSet、Service、Secret、真正拉起 Postgres，并把结果写回 `Database` 的 `status`——current 持续趋近 desired，和内建控制器管 Deployment 是同一套机制，只是管的对象换成了你自定义的类型。

这解释了为什么说 Kubernetes 是「可扩展的控制平面」而不仅是「容器编排器」：同一套声明式 + reconcile 模型可以套用到任何领域对象上。初学者的易错认知是「建了 CRD 对象就该自动生效」——不，没有对应控制器的 CR 只是一条静静躺着的记录。

### 13-8.4.4 operator 模式：把运维知识编码成控制器

operator 是使用自定义资源来管理应用及其组件的软件扩展，遵循 Kubernetes 原则、尤其是控制循环。它本质上是「自定义资源 + 自定义控制器」的组合，作为 Kubernetes API 的客户端、充当某个 CR 的控制器，在不改 Kubernetes 自身代码的前提下扩展集群行为。

operator 的立意是把「一个资深运维人员的领域知识」编码进软件：一个懂 Postgres 的人知道怎么部署它、怎么备份恢复、怎么带 schema 迁移地升级、故障怎么处置——operator 就把这些操作自动化，让你只需声明 `Database` 的期望态，剩下的运维动作由 operator 持续 reconcile 完成。官方举的典型能力包括：按需部署、备份与恢复、带数据迁移的升级、为非云原生应用发布服务、模拟故障做韧性测试等。


```
Operator = 自定义资源(CR/CRD) + 自定义控制器(reconcile 循环)
```

它把本报告四段串成一个圆环：声明式 API（你写期望态）→ reconcile 循环（控制器持续收敛）→ 控制平面（API server/etcd 承载）→ CRD/operator（把这套模型延伸到任意自定义领域对象）。初学者只要抓住「operator 就是给你自己的应用装一个专属的、永不停机的自动运维管家」，就抓住了云原生扩展性的精髓。需注意 operator 是应用层扩展、由第三方或你自己编写与部署（通常作为集群里的一个 Pod 运行），质量与成熟度参差，选用生态 operator 时要看其社区活跃度与稳定性（⚠此为工具生态判断，非规范）。

#### 来源与时效

- Kubernetes 官方文档《Custom Resources》——CR 是 API 扩展、CRD 定义新资源类型且由 API server 存储服务、无需编程、CR 单独只存数据、CR+自定义控制器=真正声明式 API、CRD vs 聚合 API 对比、声明式 API 特征（kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/，v1.36，核实 2026-08-01）。
- Kubernetes 官方文档《Operator pattern》——operator 是用自定义资源管理应用的软件扩展、遵循控制循环、作为 CR 的控制器、编码人类运维知识、典型能力清单（kubernetes.io/docs/concepts/extend-kubernetes/operator/，v1.36，核实 2026-08-01）。
- 交叉核对：两篇官方文档相互印证「operator = CR + 控制器、遵循 reconcile」，与《Controllers》的控制循环表述一致，无冲突。
- ⚙演进快·锚版本 v1.36·随时变：CRD schema 能力（结构化 schema、版本转换 webhook、子资源等）逐版增强；聚合层/apiserver-aggregation 细节随版本演进。⚠二手非承重：具体 operator 框架（Operator SDK/Kubebuilder/controller-runtime 等）与生态 operator 成熟度属工具生态，指路用，成熟度「随项目而定，选用前核对各项目当前状态」。
