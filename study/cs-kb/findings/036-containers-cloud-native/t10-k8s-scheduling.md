# L5-13·大主题10 K8s 调度/抢占/驱逐

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L5-13 大主题3（cgroups）、大主题7（K8s 工作负载对象）、大主题8（声明式模型与 reconcile 循环）、L4-01 操作系统（进程/资源限额）｜一手锚点：Kubernetes 官方文档 Scheduling, Preemption and Eviction（kubernetes.io/docs/concepts/scheduling-eviction/，当前稳定 v1.36）、kube-scheduler、Assigning Pods to Nodes、Taints and Tolerations、Pod Priority and Preemption、Node-pressure Eviction、Pod QoS Classes 各页；Linux man page cgroups(7)｜成熟度：调度框架 / 亲和反亲和 / 污点容忍 / QoS / PriorityClass / 节点压力驱逐均 GA；K8s 每约三个月发一小版本，字段与默认值随版本演进 ⚙演进快·锚版本·随时变

这一份回答一个问题：当你把一个 Pod 提交给集群，Kubernetes 到底怎么决定"让它跑在哪台机器上"，机器不够时又"牺牲谁、赶走谁"。前面大主题7/8 讲了你声明什么工作负载、控制器怎么把期望态收敛成一堆待运行的 Pod；这些 Pod 一出生都是"未调度"（没有 `nodeName`）的，接力棒就交到本篇的三个环节：调度（scheduling，给 Pod 挑一台合适的节点）、抢占（preemption，高优先级 Pod 挤走低优先级 Pod 腾位）、驱逐（eviction，节点资源告急时把 Pod 赶下去）。

贯穿全篇的心智模型有两条。第一条是"调度是一次预算匹配 + 择优"：调度器先按 Pod 的资源请求和各种放置约束筛掉不合格的节点（过滤），再在合格节点里打分挑最优（打分）。第二条是"资源声明是一切的锚"：你在 Pod 里写的 `requests`/`limits` 既决定调度器怎么算"装不装得下"，又决定运行时怎么用 cgroups 限住容器，还决定这个 Pod 的服务质量等级（QoS），而 QoS 又决定它在节点缺资源时"排第几个被赶走"。所以本篇最后会把这条线一路接回大主题3 的 cgroups。

Kubernetes 版本演进快，本篇一律锚定 **v1.36（当前稳定）**。凡涉及默认阈值、特性 GA 版本的说法都标核实日期；具体数值取自长期稳定的官方文档，个别不确定处标「待核」，绝不凭记忆编造。

---

## 13-10.1 调度器过滤 + 打分

### 13-10.1.1 kube-scheduler 是什么：给未调度 Pod 挑节点的控制平面组件

kube-scheduler 是控制平面里专门负责"给 Pod 选节点"的组件。它持续监听 API server，找出所有已经创建、但还没有被分配节点（`spec.nodeName` 为空）的 Pod，为每一个挑出一台最合适的节点，然后把这个决定写回 API server（这一步叫 binding，绑定）。绑定之后，那台节点上的 kubelet 才会真正把 Pod 里的容器拉起来。

对初学者最关键的一点是理清分工：调度器只做"决策"，不做"运行"。它决定"这个 Pod 该去 node-3"，但把 Pod 的容器真正启动起来是 node-3 上 kubelet 的活。调度器每次只处理一个 Pod（逐个决策），而不是一次性求解整盘棋的全局最优——这是一个务实的近似，换来的是简单和可扩展。还要区分调度器和 reconcile 循环（大主题8）：Deployment 控制器负责"应该有几个 Pod"，把新 Pod 造出来；调度器负责"这些新 Pod 各自去哪台机器"。两者是接力，不是同一件事。

### 13-10.1.2 过滤阶段：筛出所有"装得下"的可行节点

调度一个 Pod 分两大步，第一步是过滤（filtering，也叫 predicates）。调度器逐台检查集群里的节点，把"根本不满足这个 Pod 硬性要求"的节点全部剔除，剩下的叫可行节点（feasible nodes）。典型的过滤检查包括：这台节点剩余的可分配资源够不够装下 Pod 声明的 `requests`；节点上要求的端口有没有冲突；Pod 的 `nodeSelector`/节点亲和的硬性条件、污点容忍是否满足；卷的拓扑约束是否成立等等。

过滤看的是 `requests`（请求量），不是节点当前的真实使用量。调度器算"这台机器还剩多少可分配"，用的是"节点可分配总量减去已调度到它上面的所有 Pod 的 requests 之和"，而不是去看 CPU/内存此刻真正用了多少。这解释了一个常见困惑——为什么一台看起来 CPU 只用了 10% 的机器，却被调度器判为"装不下"新 Pod？因为上面已有的 Pod 把 requests 都占满了，哪怕它们实际很闲。过滤的结果是一份可行节点清单；如果清单为空，Pod 就调度失败、停在 Pending 状态，事件里会给出"哪些节点因什么原因被过滤掉"的原因（这也是排障的第一手线索）。

### 13-10.1.3 打分阶段：在可行节点里择优，平分时随机

过滤给出一批"都能装"的节点后，第二步是打分（scoring，也叫 priorities）。调度器让一组打分插件给每个可行节点打分并加权求和，得到每台节点的总分，然后把 Pod 分给分数最高的那台。打分考虑的是"哪台更划算"，比如：把 Pod 放上去后节点资源是否更均衡、镜像是否已经在该节点本地缓存（省去拉取）、Pod 间亲和/反亲和的软偏好是否被满足、跨拓扑域是否分布得更均匀等。

当多台节点拿到相同的最高分，调度器在它们之间随机挑一台。

这样设计是为了在同样优秀的节点间自然地分散负载，避免总是压向同一台。初学者容易把"过滤"和"打分"混为一谈，一句话区分：过滤是"能不能"（硬性、一票否决、结果是能/不能的布尔判断），打分是"好不好"（软性、比较优劣、结果是个分数）。硬约束不满足的节点在过滤阶段就没了，根本进不到打分；软偏好只在打分阶段影响排名，不满足顶多扣分、不会把节点直接淘汰。

### 13-10.1.4 调度框架与调度配置：可插拔的扩展点

现代 kube-scheduler 的内部结构叫调度框架（scheduling framework）。它把一次调度过程切成一串有序的扩展点（extension points），每个扩展点上可以挂若干插件，调度器内建的过滤、打分逻辑本身也是以插件形式实现的。主要扩展点按调度一个 Pod 的时间顺序大致包括：QueueSort（决定等待队列里 Pod 的先后）、Filter（过滤，即前述可行性筛选）、Score（打分）、Reserve（在内存里为 Pod 预留资源）、Permit（允许/延迟/拒绝进入绑定）、Bind（真正把 Pod 绑到节点）等，此外还有 PreFilter/PostFilter、PreScore、PreBind/PostBind 等前后置钩子。

对初学者，记住"Filter 和 Score 是两个核心扩展点，其他是围绕它们的前后处理"就够了。这套框架的价值在于可扩展：你可以通过调度配置（Scheduler Configuration，用 KubeSchedulerConfiguration 定义 profiles，即调度画像）启用/禁用某些插件、调整打分权重，甚至跑多个画像让不同 Pod 走不同调度策略；需要更定制时还能写自己的插件，或干脆部署第二个自定义调度器、用 Pod 的 `schedulerName` 指定由谁来调度它。这里点到为止——本课只建立"调度器是可插拔的、过滤+打分是其骨架"这个心智，深挖归 L6-05。

### 13-10.1.5 nodeName 直接指派：绕过调度器的最原始方式

除了交给调度器，你还能在 Pod 里直接写死 `spec.nodeName`，把它钉死到某一台具体节点上。

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: pinned
spec:
  nodeName: node-3
  containers:
    - name: app
      image: nginx
```

这是最原始、最"硬"的放置手段：一旦写了 `nodeName`，调度器根本不参与——kubelet 直接尝试在那台节点上运行它。正因为绕过了调度器，它也绕过了过滤阶段的所有检查：如果 node-3 其实资源不够、或者不存在、或者有不容忍的污点，这个 Pod 不会被重新调度到别处，而是直接失败或卡住。所以生产里几乎不用 `nodeName`，它主要用于测试或极特殊场景。初学者应把它和下一节的 `nodeSelector`/亲和区分开：`nodeName` 是"我自己指定唯一一台、不要你调度"，而 `nodeSelector`/亲和是"我给出约束、仍由调度器在满足约束的节点里挑"。

#### 来源与时效

- 锚点：Kubernetes 官方文档《kube-scheduler》与《Scheduling Framework》，kubernetes.io/docs/concepts/scheduling-eviction/，当前稳定 v1.36，核实 2026-08-01。过滤/打分两阶段、平分随机选节点、扩展点列表（QueueSort/Filter/Score/Reserve/Permit/Bind 等）均据此。
- 交叉核对：官方《Assigning Pods to Nodes》页对 `nodeName`、`nodeSelector` 的定位；调度器"逐个 Pod 决策、只做绑定不做运行"的分工在 kube-scheduler 概述与 Pod 生命周期文档中一致。
- ⚙演进快·锚版本·随时变：调度框架的插件集合、默认启用项与 KubeSchedulerConfiguration 的 API 版本随 K8s 版本演进；本篇锚 v1.36。具体某扩展点上默认挂哪些插件的完整清单以对应版本官方文档为准，个别默认权重「待核」。

---

## 13-10.2 亲和/反亲和/污点容忍

### 13-10.2.1 nodeSelector：最简单的节点约束

`nodeSelector` 是给 Pod 加节点约束的最简单方式：你给节点打上标签（label），再在 Pod 里写"只调度到带某标签的节点"。

```yaml
spec:
  nodeSelector:
    disktype: ssd
```

上面这段表示：只有带 `disktype=ssd` 标签的节点才是可行节点，调度器只在这些节点里挑。它本质是一条硬性过滤条件——所有列出的标签都必须精确匹配（是"与"关系），一个不满足该节点就被过滤掉。

`nodeSelector` 简单够用，但表达力很弱，只能做"标签精确相等"的与匹配，没法表达"或""不等于""这些值之一""满足不了就退而求其次"等复杂意图。这些更丰富的需求正是下一节节点亲和要解决的。可以把 `nodeSelector` 看成节点亲和的一个极简特例。

### 13-10.2.2 节点亲和：required（硬）与 preferred（软）

节点亲和（node affinity）是 `nodeSelector` 的加强版，用更丰富的表达式约束"Pod 想去什么样的节点"，并且区分硬约束和软偏好两档：

```
requiredDuringSchedulingIgnoredDuringExecution   （硬：不满足就不调度）
preferredDuringSchedulingIgnoredDuringExecution  （软：尽量满足，满足不了也照样调度）
```

`required...` 是硬约束，作用在过滤阶段：不满足的节点直接被淘汰，若没有任何节点满足，Pod 就一直 Pending。`preferred...` 是软偏好，作用在打分阶段：满足偏好的节点加分（每条偏好带一个 `weight` 权重），但即便全都不满足，Pod 仍会被调度到某个可行节点上——它只影响"更想去哪"，不影响"能不能去"。表达式用 `matchExpressions`，支持 `In`/`NotIn`/`Exists`/`DoesNotExist`/`Gt`/`Lt` 等操作符，比 `nodeSelector` 灵活得多。

那串又长又拗口的名字值得拆开记：`requiredDuringScheduling` 指"调度时必须满足"，`IgnoredDuringExecution` 指"运行期间若节点标签变了，不满足也不会把已经跑起来的 Pod 赶走"。也就是说这些规则只在调度那一刻生效，事后节点标签变化不会触发重新调度——这是与下面"污点 NoExecute"的一大区别（那个会驱逐已运行的 Pod）。名字里还预留了 `...IgnoredDuringExecution` 对应的"RequiredDuringExecution"变体，但那属于尚未落地的能力，本篇不展开。

### 13-10.2.3 Pod 间亲和/反亲和与 topologyKey

节点亲和约束的是"Pod 与节点的关系"，Pod 间亲和/反亲和（inter-pod affinity/anti-affinity）约束的是"Pod 与 Pod 的相对位置"：让某些 Pod 倾向于（亲和）或避免（反亲和）和另一些 Pod 待在同一个拓扑域里。它同样分 `required`（硬）与 `preferred`（软）两档，核心在于 `topologyKey`——用节点上的某个标签键来定义"什么算同一个域"。

```yaml
spec:
  affinity:
    podAntiAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        - labelSelector:
            matchLabels:
              app: web
          topologyKey: kubernetes.io/hostname
```

上面这段的意思是：带 `app=web` 标签的 Pod 之间互斥，`topologyKey: kubernetes.io/hostname` 表示"以主机名为域"，于是同一台节点上最多只放一个这样的 Pod——这是"把同一服务的多个副本打散到不同机器、避免单机故障全灭"的经典写法。若把 `topologyKey` 换成 `topology.kubernetes.io/zone`，域就变成"可用区"，实现跨机房打散。亲和（`podAffinity`）则相反，用于"让互相频繁通信的 Pod 尽量同域以降延迟"。

初学者需要注意两个坑。其一，Pod 间亲和/反亲和的计算成本较高（调度器要两两比对已有 Pod 的分布），在大规模集群里可能明显拖慢调度，官方也提示在数百节点以上规模需谨慎使用。其二，`topologyKey` 依赖节点确实打了对应标签，若节点没有该标签，规则可能无法按预期生效。对"打散副本"这个最常见诉求，往往更推荐用下一节的拓扑分布约束，它更省算力也更直观。

### 13-10.2.4 拓扑分布约束：把副本均匀铺开

拓扑分布约束（topology spread constraints）专门解决"让一组 Pod 在各个拓扑域之间尽量均匀分布"的需求，比 Pod 反亲和更适合表达"均衡"而非"互斥"。

```yaml
spec:
  topologySpreadConstraints:
    - maxSkew: 1
      topologyKey: topology.kubernetes.io/zone
      whenUnsatisfiable: DoNotSchedule
      labelSelector:
        matchLabels:
          app: web
```

`maxSkew` 是允许的最大不均衡度（任意两个域里匹配 Pod 数量之差的上限），`topologyKey` 定义域（这里是可用区），`whenUnsatisfiable` 决定约束满足不了时怎么办：`DoNotSchedule` 是硬约束（不满足就 Pending），`ScheduleAnyway` 是软偏好（尽量均衡、实在不行也照调）。

它和 Pod 反亲和的区别值得一句话点明：反亲和表达的是"绝对不要在一起"（每个域至多一个），而拓扑分布约束表达的是"数量要接近"（各域相差不超过 `maxSkew`）。想要"三副本尽量分到三个可用区、每区一个"这类均衡诉求，用拓扑分布约束比堆一串反亲和规则更清晰、也更省调度开销。初学者只需记住：需要"均匀铺开"时优先想到它。

### 13-10.2.5 污点与容忍：从节点一侧拒绝 Pod

前面几种约束都是"从 Pod 一侧说我想去哪"，污点（taint）与容忍（toleration）反过来——"从节点一侧说我排斥谁"。给节点打一个污点，默认就会排斥所有 Pod；只有在 Pod 上显式声明了对应的容忍（toleration），这个 Pod 才被允许调度到该节点。

```
kubectl taint nodes node-1 key1=value1:NoSchedule
```

污点有三种效果（effect）：

```
NoSchedule        不容忍者不调度到此节点（已在运行的不动）
PreferNoSchedule  尽量别调度过来（软性，实在没别处也可以）
NoExecute         不容忍者不但不调度，连已在运行的也会被驱逐
```

Pod 侧的容忍与之配对：

```yaml
spec:
  tolerations:
    - key: "key1"
      operator: "Equal"
      value: "value1"
      effect: "NoSchedule"
```

理解要点是"污点/容忍是一对锁与钥匙"：污点是节点上的锁，容忍是 Pod 带的钥匙，钥匙对上锁才放行。它和亲和是互补的两个方向——亲和是 Pod 主动挑节点（吸引），污点是节点主动挡 Pod（排斥）；实践中常配合使用，比如给一批 GPU 节点打污点把普通 Pod 挡在外面，再给 GPU 作业加容忍并配节点亲和，实现"专机专用"。

`NoExecute` 会驱逐已经在运行的不容忍 Pod，这正是 K8s 处理节点故障的手段——节点变 NotReady 或失联时，控制平面会自动给它打上 `node.kubernetes.io/not-ready` 或 `unreachable` 的 `NoExecute` 污点，于是上面的 Pod 被驱逐、由控制器在别处重建。容忍里的 `tolerationSeconds` 可以设置"容忍这个污点多久后才被驱逐"（默认注入的这两个容忍通常给 300 秒），用来避免网络短暂抖动就立刻搬迁全部 Pod。这一条把污点和后面 13-10.4 的"驱逐"连了起来：污点驱逐是驱逐的一种触发来源。

#### 来源与时效

- 锚点：Kubernetes 官方文档《Assigning Pods to Nodes》（node affinity / nodeSelector / inter-pod affinity）、《Pod Topology Spread Constraints》、《Taints and Tolerations》，kubernetes.io/docs/concepts/scheduling-eviction/，v1.36，核实 2026-08-01。`required/preferred` 两档、`matchExpressions` 操作符、`topologyKey`、污点三效果（NoSchedule/PreferNoSchedule/NoExecute）、`tolerationSeconds` 均据此。
- 交叉核对：`node.kubernetes.io/not-ready`、`node.kubernetes.io/unreachable` 的自动 NoExecute 污点及默认 300s 容忍见《Taints and Tolerations》"基于污点的驱逐"一节，与《Node-pressure Eviction》《Nodes》页对节点故障处理的描述一致。
- ⚙演进快·锚版本·随时变：默认注入容忍的具体 `tolerationSeconds`（约 300s）与内建污点键名随版本可能微调，本篇锚 v1.36；`RequiredDuringExecution` 变体尚未落地，不作结论。个别默认值「待核」。

---

## 13-10.3 requests/limits 与 QoS

### 13-10.3.1 requests 与 limits：一个给调度看，一个给运行时限

每个容器可以为 CPU、内存等资源声明两个数：`requests`（请求量）和 `limits`（上限）。

```yaml
resources:
  requests:
    cpu: "250m"
    memory: "256Mi"
  limits:
    cpu: "500m"
    memory: "512Mi"
```

两者角色完全不同。`requests` 是"保底预留"，是调度器算账的依据：调度器保证把 Pod 放到"可分配资源减去已有 requests 之和"仍够容纳这个 Pod 的 requests 的节点上（见 13-10.1.2）。`limits` 是"运行时天花板"，由节点上的 kubelet/运行时通过 cgroups 施加，限制容器实际最多能用多少（见 13-10.3.4）。

调度看 requests，不看 limits，更不看真实用量。调度器只关心"你请求了多少"来决定塞哪台机器；`limits` 在调度时基本不参与节点容量计算，它是跑起来之后才发挥作用的运行时约束。CPU 的单位 `m` 是毫核（millicore），`250m` = 0.25 个 CPU 核；内存用字节，`Mi` 是 2 的幂的兆（1Mi=1048576 字节），`M` 才是十进制的兆——两者不同，写错会导致实际额度和预期有出入，是常见坑。另一个坑：如果只写 `limits` 不写 `requests`，Kubernetes 会把 `requests` 默认设成等于 `limits`。

### 13-10.3.2 可压缩 vs 不可压缩资源：CPU 被节流，内存会 OOM

CPU 和内存对超限的处理方式根本不同，因为一个可压缩、一个不可压缩。CPU 是可压缩资源：容器用量顶到 `limits` 时不会被杀，只会被节流（throttling）——内核 cgroups 的 CPU 配额机制按周期给它分片，超了就让它等下个周期，表现为变慢但不死。内存是不可压缩资源：内存一旦分配出去就没法"温和地收回一点让它慢下来"，所以容器用量超过内存 `limits` 时，内核会直接触发 OOM（Out Of Memory）把容器里的进程杀掉，容器随后按重启策略重启，`kubectl describe` 里会看到 `OOMKilled`。

这个区别对初学者极其重要，因为它解释了两类完全不同的线上现象。CPU 打满 limits 的服务表现为"变卡、延迟升高、吞吐下降"，但进程还活着；内存打满 limits 的服务表现为"进程被突然杀掉、容器重启、请求中断"。所以 CPU limits 设小一点最多是慢，内存 limits 设小了则会反复 OOM 崩溃。一个常见的调优取向由此而来：CPU 通常只设 requests、不设或宽设 limits（避免无谓节流），而内存的 requests 与 limits 要更贴近真实峰值并留足余量（避免 OOM）。

### 13-10.3.3 QoS 三类：Guaranteed / Burstable / BestEffort

Kubernetes 依据 Pod 里各容器 requests/limits 的设置情况，自动给 Pod 打一个服务质量等级（QoS class），共三档，它决定资源紧张时的"生存优先级"。判定规则如下：

Guaranteed（最高保障）：Pod 里每个容器都同时设置了 CPU 和内存的 requests 与 limits，且对每个容器、每种资源，limits 都等于 requests。

Burstable（可突发）：不满足 Guaranteed，但 Pod 中至少有一个容器设置了 CPU 或内存的 request 或 limit。

BestEffort（尽力而为）：Pod 里所有容器都完全没有设置任何 CPU/内存的 requests 或 limits。

全都设满且 limits==requests → Guaranteed；设了一部分 → Burstable；啥都没设 → BestEffort。这三档的意义在下一节和驱逐里兑现——节点缺资源时，BestEffort 最先被牺牲，Burstable 次之，Guaranteed 最后。初学者常见误解是以为"QoS 是我在 YAML 里直接选的字段"——不是，它是 Kubernetes 根据你怎么写 requests/limits 自动推导出来的结果，你只能通过调整资源声明来间接控制它。要拿到 Guaranteed，必须给每个容器的 CPU 和内存都写上 requests 与 limits 且两两相等，漏一个容器或漏一种资源就掉到 Burstable。

（补充时效：K8s v1.34 起引入 Pod 级别的资源声明，Guaranteed 的判定相应扩展到"Pod 级 requests/limits 也需相等"；本篇锚 v1.36，容器级判定规则如上，Pod 级细节 ⚙演进快·锚版本·随时变。）

### 13-10.3.4 requests/limits 如何落到 cgroups（回指大主题3）

Pod 被调度、kubelet 拉起容器时，`requests`/`limits` 最终会被翻译成 Linux cgroups 的具体参数，由内核强制执行——这正是大主题3 讲的资源限额机制在 K8s 里的落地。CPU 的 `requests` 大致映射到 cgroups 的 CPU 份额/权重（`cpu.shares` 或 v2 的 `cpu.weight`，决定竞争时按比例分配 CPU），CPU 的 `limits` 映射到配额/周期（`cpu.cfs_quota_us`/`cpu.cfs_period_us` 或 v2 的 `cpu.max`，即前述节流的实现）。内存的 `limits` 映射到内存上限（`memory.limit_in_bytes` 或 v2 的 `memory.max`），越过它就由内核 OOM 机制处置。

把这条链打通，前面几节就串成了一个完整故事：你在 YAML 里写的抽象数字（`250m`、`512Mi`）→ 调度器用 requests 决定放哪台机器 → kubelet 把 requests/limits 写进该 Pod/容器对应的 cgroup 文件 → 内核按 cgroups 参数真正限住 CPU 分片和内存上限。所谓"CPU 超限被节流、内存超限被 OOM"（13-10.3.2）本质就是内核 cgroups 控制器的行为，K8s 只是把它包装成了声明式字段。具体映射到 cgroup v1 还是 v2、字段名细节取决于节点内核与容器运行时（本知识库基线机器为 cgroup v1 hybrid，v2 为现代默认），精确的换算公式随版本演进，个别系数「待核」，不在此深挖——机制根在大主题3 与 cgroups(7)。

#### 来源与时效

- 锚点：Kubernetes 官方文档《Resource Management for Pods and Containers》（requests/limits、CPU 毫核/内存单位、只写 limits 则 requests 取等值）、《Pod Quality of Service Classes》（Guaranteed/Burstable/BestEffort 判定规则），v1.36，核实 2026-08-01。
- 交叉核对：QoS 三类判定与"BestEffort→Burstable→Guaranteed"的驱逐次序在《Pod QoS Classes》与《Node-pressure Eviction》两页互证一致；CPU 可压缩被节流、内存不可压缩触发 OOM 的行为与 Linux man page cgroups(7)（CPU/memory 控制器语义）一致，requests/limits→cgroups 映射据此回指大主题3。
- ⚙演进快·锚版本·随时变：Pod 级资源声明（约 v1.34 引入）对 Guaranteed 判定的扩展、requests→cgroup 具体系数与 v1/v2 字段名随版本/内核演进，本篇锚 v1.36 + 基线机器 cgroup v1 hybrid；精确换算「待核」，不编造。

---

## 13-10.4 抢占与驱逐

### 13-10.4.1 PriorityClass 与 Pod 优先级

Pod 优先级（priority）用一个整数表示这个 Pod 相对其他 Pod 有多重要，数字越大越重要。你不直接在 Pod 上写数字，而是先创建一个集群级的 PriorityClass 对象把名字映射到整数值，再在 Pod 里用 `priorityClassName` 引用它：

```yaml
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: high-priority
value: 1000000
globalDefault: false
description: "关键业务专用"
```

```yaml
spec:
  priorityClassName: high-priority
```

优先级的取值范围是 32 位整数（约 -2147483648 到 1000000000）；大于 10 亿的区间为系统保留，内建的 `system-cluster-critical`、`system-node-critical` 两个 PriorityClass 用它来保证关键系统组件不被挤掉，用户自建的 PriorityClass 名字不能以 `system-` 打头。`globalDefault: true` 的 PriorityClass 给所有没写 `priorityClassName` 的 Pod 当默认值，全集群最多只能有一个；若不存在这样的默认，没指定优先级的 Pod 优先级为 0。

优先级有两层作用要分清。其一是排队：调度队列里高优先级 Pod 排在前面，先被调度。其二是抢占：当高优先级 Pod 找不到可行节点时，可能触发抢占去挤掉低优先级 Pod（下一节）。初学者要注意"优先级"和"QoS"是两个正交的概念——优先级由你通过 PriorityClass 显式指定、影响排队和抢占；QoS 由 requests/limits 自动推导、影响节点压力驱逐的次序。两者都关乎"谁先被牺牲"，但触发场景和判定来源不同，别混为一谈。

### 13-10.4.2 抢占：高优先级 Pod 挤掉低优先级 Pod 腾位

当一个 Pod 因为资源不足而无法调度（所有节点都过滤失败），若它的优先级高于某些正在运行的 Pod，调度器会启动抢占（preemption）：在某个节点上驱逐（删除）一个或多个优先级更低的 Pod，腾出资源让这个高优先级 Pod 能被调度上去。被牺牲的低优先级 Pod 叫受害者（victims）。

机制上有几个要点。调度器选定一台节点并决定牺牲哪些受害者后，会在待调度 Pod 的 `status.nominatedNodeName` 记下"提名节点"，表示资源正为它保留；但这不保证它最终一定落在这台节点——受害者优雅终止需要时间，其间可能有更高优先级的 Pod 半路杀出抢走位置，届时 `nominatedNodeName` 会和最终的 `nodeName` 不一致。受害者被删除时会得到它自己配置的 `terminationGracePeriodSeconds` 优雅终止时间来收尾。抢占默认策略是 `PreemptLowerPriority`（可抢占更低优先级者）；把 PriorityClass 的 `preemptionPolicy` 设为 `Never` 则得到"非抢占"Pod——它享受高优先级带来的排队靠前，但不会去挤掉别人，适合"想优先调度、但不愿打断在跑的作业"的批处理/数据科学场景。

初学者要理解抢占和普通驱逐的区别：抢占是调度器为了"塞进一个装不下的高优先级 Pod"而主动腾位，触发点在"调度失败"；而下一节的节点压力驱逐是 kubelet 为了"救一台快撑爆的节点"而赶人，触发点在"节点资源告急"。两者都会终止 Pod，但动机、执行者（调度器 vs kubelet）、选谁的规则都不同。另一个坑：抢占对 PodDisruptionBudget（PDB，见 13-10.4.4）是尽力而为——调度器会尽量不违反 PDB，但当没有别的受害者可选时，仍会照抢不误。

### 13-10.4.3 节点压力驱逐：节点资源告急时 kubelet 主动赶人

节点压力驱逐（node-pressure eviction）是节点上的 kubelet 在检测到节点自身资源（内存、磁盘、inode、PID 等）低于阈值时，主动终止该节点上一些 Pod 来回收资源、保住节点稳定的机制。kubelet 监控一组驱逐信号（eviction signals），常见的有：

```
memory.available     可用内存
nodefs.available     节点文件系统可用空间
nodefs.inodesFree    节点文件系统可用 inode
imagefs.available    镜像文件系统可用空间
imagefs.inodesFree   镜像文件系统可用 inode
pid.available        可用进程 ID
```

kubelet 的默认硬驱逐阈值（hard eviction thresholds，触发即立刻驱逐、0 秒宽限）为：

```
memory.available  < 100Mi
nodefs.available  < 10%
nodefs.inodesFree < 5%
imagefs.available < 15%
imagefs.inodesFree< 5%
```

选谁被驱逐的排序规则是先看 QoS、再看用量超出请求的程度：先驱逐 BestEffort，其次 Burstable，最后才轮到 Guaranteed；同一 QoS 档内，用量超出自己 `requests` 越多的 Pod 越先被赶走。驱逐前 kubelet 还会先做节点级回收（删无用镜像、清死容器）尝试自救。

这一节把前面所有线索收束成一个因果闭环。为什么设了 requests/limits 就更"安全"？因为它决定 QoS，而 QoS 决定被驱逐的次序：一个 BestEffort Pod（啥都没设）在节点内存吃紧时头一个被赶走，一个 Guaranteed Pod（设满且相等）几乎最后才动。初学者要记牢两点。其一，硬阈值是"越过就立刻杀、没有宽限"，所以内存类信号触发时驱逐来得很突然；软阈值（soft eviction）才有可配置的宽限期。其二，节点压力驱逐不遵守 PodDisruptionBudget——它是救火，顾不上你设的中断预算；这点和"API 发起的驱逐"（下一节）恰好相反。上面那些默认阈值是 kubelet 的可配置项，此处数值为官方文档所列默认值（核实 2026-08-01），实际集群可能被运维改过，具体以节点 kubelet 配置为准。

### 13-10.4.4 API 发起的驱逐与 drain：与压力驱逐区分

除了 kubelet 自发的节点压力驱逐，还有一类"API 发起的驱逐"（API-initiated eviction）：外部通过调用 Eviction API 主动请求删除某个 Pod，最典型的入口是 `kubectl drain`（排空节点，为维护/升级把 Pod 挪走）。

```
kubectl drain node-1 --ignore-daemonsets
```

它和节点压力驱逐有一个关键差异：API 发起的驱逐尊重 PodDisruptionBudget（PDB）。PDB 是你为一个应用声明的"最少要保留几个可用副本 / 最多同时中断几个"的约束；当 drain 想赶走的 Pod 会违反 PDB 时，驱逐请求会被拒绝或阻塞，直到有足够副本可用为止——这保护了服务在滚动维护中不至于瞬间掉到可用副本以下。

对初学者，把三种"让 Pod 消失"的机制并排记清很有价值：抢占（调度器为塞高优先级 Pod 而腾位，PDB 尽力而为）、节点压力驱逐（kubelet 为救节点而赶人，不管 PDB）、API 发起的驱逐/drain（人或控制器主动请求，严格尊重 PDB）。它们触发者不同、是否尊重 PDB 不同，排障时先分清"Pod 是被谁、因什么赶走的"，才能对症。此外还有前面 13-10.2.5 提过的基于污点的驱逐（节点 NotReady 时自动打 NoExecute 污点驱逐不容忍的 Pod），也属于广义驱逐的一条来源，机制上由节点生命周期控制器触发。

#### 来源与时效

- 锚点：Kubernetes 官方文档《Pod Priority and Preemption》（PriorityClass、value 范围与 system- 保留、globalDefault、nominatedNodeName、preemptionPolicy Never、优雅终止、PDB 尽力而为）、《Node-pressure Eviction》（驱逐信号、默认硬阈值、QoS+超请求排序、不遵守 PDB）、《API-initiated Eviction》与《Safely Drain a Node》（drain 尊重 PDB），v1.36，核实 2026-08-01。
- 交叉核对：默认硬驱逐阈值 `memory.available<100Mi / nodefs.available<10% / nodefs.inodesFree<5% / imagefs.available<15% / imagefs.inodesFree<5%` 取自《Node-pressure Eviction》默认值表，2026-08-01 核实；驱逐排序（BestEffort→Burstable→Guaranteed，档内按超请求用量）与《Pod QoS Classes》一致。抢占"不保证落在提名节点"及 PDB 尽力而为与《Pod Priority and Preemption》限制一节互证。
- ⚙演进快·锚版本·随时变：默认驱逐阈值、`preemptionPolicy: Never`（v1.24 起）等特性的可用性与默认值随版本演进，且阈值为 kubelet 可配置项，实际以节点配置为准；本篇锚 v1.36。任何被运维覆盖的阈值「待核」，不凭记忆断言。冲突项：本篇未发现文档与实现的实质冲突；若某集群实测驱逐次序异常，多因自定义 kubelet 配置或自定义调度器所致，需回到该集群实际配置核对。
