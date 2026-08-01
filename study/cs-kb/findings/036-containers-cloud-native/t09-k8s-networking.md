# L5-13·大主题9 K8s 服务发现/负载均衡/网络

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：大主题2 Linux namespaces（network ns）、大主题7 工作负载对象（Pod）、大主题8 声明式模型；跨 L4-02 网络 ｜一手锚点：Kubernetes 官方文档 Services & Networking @v1.36（当前稳定，2026-04-22 发布）、CNI 规范（containernetworking/cni）、Gateway API（sigs.k8s.io，v1 GA）、Linux man page network_namespaces(7) ｜成熟度：Service/DNS/NetworkPolicy 为 GA；Ingress GA 但事实冻结、正向 Gateway API 迁移，⚙演进快

Kubernetes 里 Pod 是「用完即弃」的：它随时被销毁重建，每次重建 IP 都变。可如果服务 A 要调用服务 B，总不能每次都去查 B 现在的 IP。这一大主题讲的就是 K8s 如何在「一堆会漂移的 Pod」之上，搭出稳定的寻址、发现、负载均衡与对外暴露，以及底下那套「每 Pod 一 IP」的扁平网络是怎么回事。

---

## 13-9.1 Service 与 ClusterIP

### 13-9.1.1 Service 抽象：为一组 Pod 提供稳定入口

Service 是 K8s 的一种 API 对象，它把「一组功能相同、随时增减的 Pod」抽象成一个稳定的访问端点：一个固定的虚拟 IP（ClusterIP）加一个固定的 DNS 名字。客户端只认这个固定入口，Service 负责把请求分发到当前健康的后端 Pod 上，Pod 增删对客户端透明。

初学者最容易卡在「为什么需要它」。设想后端是一个有 3 个副本的 Deployment，这 3 个 Pod 的 IP 是 10.244.1.7、10.244.2.3、10.244.1.9 之类，而且滚动更新后全变了。前端如果把这些 IP 写死就会立刻失效。Service 给了一个永不变的地址（比如 ClusterIP 10.96.0.50、DNS 名 `backend.default.svc.cluster.local`），前端只连它，K8s 在背后维护「这个名字当前对应哪些 Pod IP」的映射。这就是「服务发现」：用稳定的名字找到漂移的实例。

要点是 Service 本身不是一个进程、也不是一个代理容器，它是一条「规则」——一个存在 etcd 里的对象，由集群里每个节点上的数据面（kube-proxy 等，见 13-9.2）翻译成实际的转发规则。所以 ClusterIP 是个「虚拟」IP：没有任何一张网卡真的持有它，它只在转发规则里存在。

### 13-9.1.2 selector：用标签选后端

大多数 Service 通过 `selector`（标签选择器）声明「哪些 Pod 是我的后端」。控制面持续监视集群里带匹配标签的 Pod，把它们的地址收集成端点集合（EndpointSlice，见 13-9.2.1）。


```yaml
apiVersion: v1
kind: Service
metadata:
  name: backend
spec:
  selector:
    app: backend        # 选中所有带 app=backend 标签的 Pod
  ports:
    - port: 80          # Service 对外暴露的端口
      targetPort: 8080  # 转发到 Pod 上的端口
```

这里体现了 K8s 的声明式与松耦合：Service 不点名具体 Pod，只描述「标签长这样的都算」。于是无论 Pod 怎么扩缩、重建，只要新 Pod 带上 `app=backend`，就自动进入后端集合；这与大主题8 的期望态收敛心智一脉相承。

`selector` 是按标签匹配，不是按名字或 Deployment 匹配。如果 Pod 标签写错、或 Service 的 selector 与 Pod 标签不一致，Service 会「有 IP 但没有后端」，请求全部失败且报错不直观。排查时先看这个 Service 对应的 EndpointSlice 是否为空。还有一种 Service 干脆不写 selector（用于手工指向外部地址或跨命名空间端点），此时端点要自己维护。

### 13-9.1.3 ClusterIP：默认类型，仅集群内可达

ClusterIP 是 Service 的默认类型。它从集群预留的「Service CIDR」网段里分配一个虚拟 IP，这个 IP 只在集群内部路由——集群外的机器无法直接访问它。它是集群内「东西向」流量（服务间调用）的标准做法。

直觉上可以把 ClusterIP 段理解成一个「只在集群内有意义的电话区号」。它和 Pod 用的网段是两套不同的地址空间：Pod IP 是真实分配到 Pod 网络命名空间网卡上的、可被路由的地址；ClusterIP 是纯虚拟的、靠每个节点的转发规则「拦截并改写」才生效的地址（详见 13-9.4.5）。

新手常疑惑「ping 不通 ClusterIP」。很多实现里 ClusterIP 只对声明过的端口/协议装转发规则，ICMP（ping）未必被处理，ping 不通不代表服务不可用；应当用 `curl` 访问声明的端口来验证。另外 ClusterIP 默认不能从集群外访问，想对外暴露要换类型或叠加 Ingress（13-9.3）。

### 13-9.1.4 Service 类型谱系：ClusterIP / NodePort / LoadBalancer / ExternalName

Service 有四种 `type`，构成一条「从纯内部到逐步对外」的谱系。ClusterIP 只在集群内可达。NodePort 在每个节点上开一个高位端口（默认范围 30000–32767，⚙可配），集群外通过「任一节点 IP:该端口」访问，请求再被转到 Service。LoadBalancer 在 NodePort 基础上，请云厂商/负载均衡器控制器分配一个外部负载均衡器，把公网流量导进来（依赖具体环境实现）。ExternalName 特殊：它不选 Pod、不分配 ClusterIP，只是让集群 DNS 把该 Service 名解析成一个外部 DNS 名（返回 CNAME），用于把外部服务「伪装」成集群内名字。

这几类是层层包含的：LoadBalancer 通常内部实现为「LoadBalancer → NodePort → ClusterIP → 端点」的叠加。理解这一点能帮你搞清一个请求进来后经过几跳。

NodePort 端口范围默认 30000–32767，写成 80 会被拒（除非改集群配置），初学者常想当然。ExternalName 只做 DNS 层重定向，不做端口转发、不做 TLS 处理，也无法用于「name 不是合法 DNS 主机名」的场景。生产里直接把每个服务都设成 LoadBalancer 会开销巨大（每个都要一个云 LB），更常见是用一个 Ingress/Gateway 统一入口（13-9.3）。

### 13-9.1.5 Headless Service：不要虚拟 IP，直接给 Pod 地址

把 `clusterIP` 显式设为 `None` 就得到 headless（无头）Service。它不分配虚拟 IP、不做负载均衡，而是让集群 DNS 直接返回后端所有 Pod 的 IP（一组 A/AAAA 记录）。客户端自己决定连哪个。

```yaml
spec:
  clusterIP: None
  selector:
    app: db
```

有些场景客户端要「点名」某个具体实例，而不是被随机分发。典型是有状态应用（StatefulSet，见大主题7）——每个副本身份不同（db-0、db-1），客户端要能稳定连到 db-0。headless Service 配合 StatefulSet 会给每个 Pod 一个稳定的 DNS 子域名（如 `db-0.db.default.svc.cluster.local`），让「按身份寻址」成为可能。

headless 不做负载均衡，负载分发的责任落回客户端；DNS 返回的是一组 IP，客户端库若只取第一条就会全打到同一个 Pod。另外 DNS 有缓存，Pod 变动到客户端看到更新之间存在时间窗，不适合要求极快端点收敛的场景。

### 13-9.1.6 端口命名、多端口与会话亲和

一个 Service 可以暴露多个端口，此时每个端口必须命名（`name`），供 DNS SRV 记录和 Ingress 引用。`port` 是 Service 侧端口，`targetPort` 是 Pod 侧端口，两者可以不同，`targetPort` 还能写成 Pod 里定义的端口名（字符串），解耦「对外端口」与「容器实际监听端口」。

默认 Service 把请求近似均匀地分到各端点。设置

```yaml
spec:
  sessionAffinity: ClientIP
```

后，来自同一客户端 IP 的请求会尽量固定打到同一个 Pod（基于源 IP 的粘性），有会话状态时有用。

`sessionAffinity: ClientIP` 的粘性基于客户端 IP，若客户端都在同一个 NAT/网关后面（源 IP 相同），会退化成「全打到一个 Pod」的伪均衡。真正需要按用户会话粘的场景，通常应在 L7（Ingress/Gateway，13-9.3）用 cookie 做亲和，而不是靠这个字段。

#### 来源与时效

- 一手：Kubernetes 官方文档 Concepts / Services, Load Balancing, and Networking / Service（kubernetes.io/docs/concepts/services-networking/service/），锚定 v1.36（当前稳定，2026-04-22 发布）。核实 2026-08-01。⚙演进快·锚版本：Service 的类型与字段跨版本有增量（如 traffic distribution、dual-stack 相关字段），行为以所用集群版本为准。
- 一手：NodePort 默认端口范围 30000–32767 见 Service 文档 type: NodePort 小节（该范围可由 API server 的 `--service-node-port-range` 调整，标「可配」）。
- 交叉核对：headless Service 与 StatefulSet 稳定网络标识，见官方文档 Workloads / StatefulSet 与 DNS for Services and Pods 两处相互印证。
- 冲突/待核：无实质冲突。ExternalName 是否被特定 Ingress/网格正确处理、`sessionAffinity` 超时默认值等实现细节随环境不同，未逐一核到具体默认值处标「待核」。

## 13-9.2 kube-proxy / EndpointSlice / DNS

### 13-9.2.1 EndpointSlice：Service 背后的端点清单

EndpointSlice 是记录「某个 Service 当前有哪些可用后端地址（IP + 端口 + 就绪状态 + 拓扑）」的 API 对象。控制面的 EndpointSlice 控制器监视 Service 和 Pod，实时把匹配 selector 且就绪的 Pod 地址写进一批 EndpointSlice；数据面（kube-proxy、DNS、Ingress 控制器等）读它来知道该往哪儿转发。它是「稳定名字 → 当前实例列表」这层映射的载体。

为什么不是一个大列表而是「切片」（Slice）：早期的 Endpoints 对象把一个 Service 的所有端点塞进单个对象，一个几千 Pod 的 Service 每次 Pod 变动就要重传整个巨型对象，给 etcd 和所有 watcher 造成风暴。EndpointSlice 把端点分成多个小片（每片默认上限 100 个端点，⚙可配），一个 Pod 变了只需更新它所在的那一小片，大幅降低更新开销。

时效要点（⚙演进快）：老的 `core/v1` Endpoints API 自 Kubernetes v1.33（2025-04）起被标记为「已弃用」，读写它会收到 API server 警告；EndpointSlice（`discovery.k8s.io/v1`）自 v1.21 起 GA，是唯一支持双栈、拓扑感知/流量分发等新特性的端点 API。新代码应一律面向 EndpointSlice。Endpoints 对象目前仍会被兼容性地生成，但不应再作为开发目标。核实 2026-08-01。

### 13-9.2.2 kube-proxy：把 Service 规则铺到每个节点

kube-proxy 是运行在每个节点上的组件，负责把「Service 的虚拟 IP → 后端端点」这条抽象规则落成本节点真实的数据面转发规则。它监视 Service 和 EndpointSlice，当有变化时更新本节点的内核转发表，使得任何发往某 ClusterIP:port 的包被负载均衡地改写目的地址（DNAT）到某个后端 Pod IP。

ClusterIP 之所以「虚拟」，正是因为没有真正的进程在那个 IP 上监听——是 kube-proxy 在每个节点装的规则「拦截」发往 ClusterIP 的包并改写目的地。所以负载均衡是分布式发生在每个节点内核里的，不存在一个中心代理成为瓶颈或单点。

名字里有「proxy」，但现代默认模式下 kube-proxy 并不逐包地在用户态代理流量（那是最早已淘汰的 userspace 模式），它只是「配置规则的控制器」，真正转发在内核完成。另外，用 eBPF 数据面的 CNI（如 Cilium）可以完全替代 kube-proxy，此时集群里根本没有 kube-proxy，这在新集群里越来越常见。

### 13-9.2.3 kube-proxy 的数据面模式：iptables / ipvs / nftables

kube-proxy 有多种后端实现。iptables 模式用 iptables 规则做 DNAT，长期是默认，但规则数随 Service 数线性膨胀、更新是 O(n)，大集群下更新慢。ipvs 模式用内核 IPVS（基于哈希表），查找接近 O(1)、支持更多均衡算法（rr、lc、sh 等），适合大规模。nftables 模式用较新的 nftables 子系统，解决 iptables 的性能与扩展性问题，是官方推荐的现代方向。

时效要点（⚙演进快·锚版本）：nftables 模式在 v1.29 alpha、v1.31 beta、v1.33（2025）GA。但即便 nftables GA，官方文档明确「iptables 仍是默认模式」——想用 nftables 要显式配置：

```
--proxy-mode nftables
```

核实 2026-08-01。是否切换要看内核版本与 CNI 兼容性。

模式选择通常是集群级配置，普通使用者改不了也一般不需要改；只有在超大规模或遇到 iptables 更新延迟问题时才需要关心。别把「GA」误读成「已成默认」——这是两回事，nftables 至锚定版本仍非默认。

### 13-9.2.4 集群 DNS：用名字发现服务

集群内每个 Service 都自动获得一个 DNS 名，格式为

```
<service>.<namespace>.svc.<cluster-domain>
```

默认 cluster-domain 是 `cluster.local`（⚙可配）。同命名空间内可直接用 `<service>` 短名访问，跨命名空间要带 `.<namespace>`。DNS 把 Service 名解析到它的 ClusterIP（普通 Service），或直接解析到各 Pod IP（headless）。这是服务发现最常用的入口——代码里写域名，不写 IP。

承担这项工作的组件是 CoreDNS（现代集群的默认集群 DNS，取代了早期的 kube-dns）。它作为集群内的一个 Deployment 运行，kubelet 把每个 Pod 的 `/etc/resolv.conf` 指向集群 DNS 的 ClusterIP，并通过 `search` 域让短名可用。多端口 Service 还会生成 SRV 记录，Pod 也有对应 DNS 记录。

DNS 有缓存与 TTL，端点变化到解析更新之间有窗口，别指望 DNS 毫秒级反映 Pod 变动（这也是普通 Service 用 ClusterIP 而非直接 DNS 轮询 Pod 的原因之一：转发层收敛比 DNS 快）。另外 `search` 域会让 `curl backend` 这类短名在本命名空间「碰巧能通」，但换命名空间就失效，写代码时用全限定名（FQDN）更稳。cluster-domain 是否为 `cluster.local` 取决于集群安装配置，不要硬编码假设。

### 13-9.2.5 流量策略：externalTrafficPolicy 与 internalTrafficPolicy

Service 上有两个字段控制「流量倾向本地节点还是全集群」。`externalTrafficPolicy` 针对外部进入的流量（NodePort/LoadBalancer）：设为 `Cluster`（默认）时，任一节点收到的外部流量都可被转发到任意节点上的后端，均衡最好但会多一跳且丢失客户端源 IP（因二次 NAT）；设为 `Local` 时只转发给本节点上的后端，保留客户端源 IP、少一跳，但若本节点没有后端就会丢弃。`internalTrafficPolicy` 类似，针对集群内部流量，`Local` 表示优先/仅用本节点端点。

这是「就近 vs 均衡」的取舍。要拿到真实客户端 IP（比如做限流、审计），或想省一跳延迟，就用 `Local`；但要自己保证每个入口节点都有后端（通常配合 DaemonSet），否则会出现「有的节点能通、有的不通」。

`externalTrafficPolicy: Local` 下没有本地端点的节点会直接拒绝该流量，这常被误诊为「服务挂了」；其实是策略叠加拓扑不匹配。默认 `Cluster` 更省心但会做 SNAT 掩盖真实源 IP——若上游看到的全是节点 IP 而非用户 IP，多半就是这个原因。

#### 来源与时效

- 一手：Kubernetes 官方文档 EndpointSlices（kubernetes.io/docs/concepts/services-networking/endpoint-slices/）、Service / kube-proxy 代理模式、DNS for Services and Pods，锚定 v1.36。核实 2026-08-01。
- 一手/时效：Endpoints 弃用（v1.33+ 读写有警告、EndpointSlice v1.21 GA）见官方博客「Kubernetes v1.33: Continuing the transition from Endpoints to EndpointSlices」（2025-04-24）与 EndpointSlices 概念页，两处交叉印证。⚙演进快·锚版本。
- 一手/时效：kube-proxy nftables 模式 v1.29 alpha→v1.31 beta→v1.33 GA、且「iptables 仍为默认」，见官方博客「NFTables mode for kube-proxy」（2025-02-28）与 KEP-3866，交叉核对一致。⚙演进快·锚版本，核实 2026-08-01。
- 交叉核对：CoreDNS 为默认集群 DNS，见 DNS for Services and Pods 文档；kube-proxy 可被 eBPF CNI 替代属实现选择，标为实现层信息。
- 冲突/待核：EndpointSlice 每片默认端点上限（文档述约 100，可由 `--max-endpoints-per-slice` 调整）具体默认值以所用版本文档为准，未逐版本核到处标「待核」。NodePort/cluster-domain 等可配项默认值同理。

## 13-9.3 Ingress 与外部入口

### 13-9.3.1 Ingress 与 Ingress 控制器：L7 对外路由

Ingress 是一种 API 对象，用一套规则描述「如何把集群外的 HTTP/HTTPS 请求，按主机名和路径，路由到集群内不同的 Service」。它工作在应用层（L7），能看懂 URL 和 Host 头，做基于内容的路由和 TLS 终止。但 Ingress 对象本身只是「规则声明」，真正干活的是 Ingress 控制器——一个运行在集群里的反向代理（如 ingress-nginx、Envoy、Traefik、云厂商实现），它读取 Ingress 对象并把规则配置进自己的代理。

光创建 Ingress 对象不会有任何效果，集群里必须先装了某个 Ingress 控制器。这是初学者最常见的坑——「Ingress 建好了但访问不通」，多半是没装控制器，或没指定 `ingressClassName` 让对象与控制器配对。

如果每个服务都用 LoadBalancer 类型，就要 N 个云负载均衡器，贵且难管。Ingress 让你用一个入口（一个外部 LB/IP）承载多个服务，靠 Host/Path 分流，是「一入口多后端」的 L7 网关。这与 L4 的 Service 负载均衡互补——Service 做 L3/L4 的端点分发，Ingress 做 L7 的内容路由。

### 13-9.3.2 Ingress 规则：host / path 路由与 TLS

一条 Ingress 规则按 Host 头和 URL 路径把请求映射到某个后端 Service 的端口。一个最小例子：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: web
spec:
  ingressClassName: nginx
  rules:
    - host: shop.example.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: api-svc
                port:
                  number: 80
  tls:
    - hosts: ["shop.example.com"]
      secretName: shop-tls
```

`pathType` 有 `Exact` / `Prefix` / `ImplementationSpecific` 三种，决定路径怎么匹配。`tls` 段引用一个存 TLS 证书的 Secret，让控制器做 HTTPS 终止（在入口解密，内部走明文或再加密）。

`pathType` 语义务必写清，`Prefix` 的「路径段前缀」匹配和字符串前缀不完全一样（按 `/` 分段比较），漏写或写错会导致路由不生效。不同 Ingress 控制器对高级功能（重写、限流、金丝雀）用各自的 annotation 实现，互不通用、不可移植——这正是催生 Gateway API 的原因之一（见 13-9.3.4）。

### 13-9.3.3 NodePort 与 LoadBalancer：不经 Ingress 的对外方式

除了 Ingress，把服务暴露到集群外还有两条更底层的路（其实是 Service 的类型，13-9.1.4）。NodePort 在每个节点开一个高位端口，外部用「节点 IP:端口」访问；它是 L4、无 TLS/路由能力，常作为其他方案的底座。LoadBalancer 请环境提供一个外部负载均衡器指向这些 NodePort，给一个稳定外部 IP；它也是 L4。

NodePort/LoadBalancer 是 L4「把某个 Service 整个端口捅到外面」，一个 LB 通常对一个 Service。Ingress/Gateway 是 L7「一个入口按 Host/Path 分发到多个 Service」。生产里常见组合是：一个 LoadBalancer 类型的 Service 指向 Ingress 控制器，Ingress 控制器再按 L7 规则分发到各业务 Service——即「云 LB → Ingress 控制器 → 各 Service → Pod」。

LoadBalancer 依赖具体环境提供外部 LB（云平台或自建的 MetalLB 等），裸机集群若没装相应组件，LoadBalancer Service 会一直停在 `Pending`（拿不到 EXTERNAL-IP）。这不是 bug，是缺少 LB 实现。

### 13-9.3.4 Gateway API：Ingress 的继任者（⚙演进快·随时变）

Gateway API 是一组更新、更有表达力的网关 API，被社区定位为 Ingress 的继任者。它把职责拆成多种角色化资源：`GatewayClass`（由实现提供，类比「LB 型号」）、`Gateway`（一个实际入口/监听器，平台团队管）、`HTTPRoute`/`GRPCRoute`/`TCPRoute` 等（应用团队管的路由规则）。相比 Ingress 靠一堆非标准 annotation 拼功能，Gateway API 把常见需求（多协议、流量拆分、header 操作、跨命名空间引用等）做进了标准字段，可移植性更好。

时效要点（⚙演进快·锚版本·随时变）：Gateway API 是独立于 K8s 主版本发布的项目。其核心资源（GatewayClass/Gateway/HTTPRoute）自 v1.0（2023-11）起有 GA 的 `v1` API 版本；v1.5（2026-02-27）继续把一批实验特性转正到 Standard。截至核实日（2026-08-01）最新版本约在 v1.5/v1.6 一线（精确最新小版本标「待核」，以项目 Releases 页为准）。与此同时，传统 Ingress API 事实上已「冻结」（GA 但不再加新特性），且广泛使用的 ingress-nginx 控制器已宣布退役、按公告其部署将于 2026-11 后停止——这标志迁移方向明确。核实 2026-08-01。

Ingress 目前仍是 GA、仍可用，不会立刻消失；但新项目若需要 L7 高级能力，应评估直接上 Gateway API，避免被某控制器的私有 annotation 锁定。Gateway API 同样需要一个支持它的实现（Istio、Envoy Gateway、Cilium、云厂商等）在集群里运行，光有 CRD 不干活——这点与 Ingress 需要控制器一致。因为演进快，任何具体版本/GA 状态使用前都应回官方 Releases 与 versioning 文档确认。

#### 来源与时效

- 一手：Kubernetes 官方文档 Ingress、Ingress Controllers（kubernetes.io/docs/concepts/services-networking/ingress/），锚定 v1.36。核实 2026-08-01。Ingress API 为 `networking.k8s.io/v1`，自 v1.19 GA。
- 一手/时效：Gateway API 官方站点（gateway-api.sigs.k8s.io）versioning 与 Releases；官方博客「Gateway API v1.5: Moving features to Stable」（2026-04-21 发布页）。v1.0（2023-11）核心资源达 GA `v1`。⚙演进快·锚版本·随时变，核实 2026-08-01；最新精确小版本标「待核」。
- 时效：ingress-nginx 退役与「部署 2026-11 后停止」见 Gateway API 迁移相关公告（⚠部分为项目/生态公告，作方向指引；精确停止时间以官方公告为准，标「待核」）。
- 交叉核对：NodePort/LoadBalancer 对外语义与 Ingress 分账，见 Service 文档与 Ingress 文档两处一致；LoadBalancer 依赖外部实现、裸机需 MetalLB 等属实现层。
- 冲突/待核：Gateway API 最新小版本号、各实现对 GA 资源的支持完成度随时变，标「待核」。

## 13-9.4 网络模型与 CNI / NetworkPolicy

### 13-9.4.1 K8s 网络模型：每 Pod 一 IP 的扁平网络

Kubernetes 规定了一个基础网络模型，任何合规实现都必须满足几条要求：每个 Pod 有自己独立的 IP；同一集群内所有 Pod 之间可以不经 NAT 直接互通；节点上的代理（如 kubelet）可以和该节点上的 Pod 互通；且 Pod 看到的自身 IP 与别的 Pod 看到它的 IP 是同一个。结果是一张「扁平网络」——从 Pod 视角，整个集群像一个大局域网，每个 Pod 是网里一台有独立 IP 的主机。

它极大简化了心智模型。没有 Pod 间 NAT，意味着应用不用关心「我在容器里、端口被映射」这类传统 Docker 单机的端口冲突/映射问题——每个 Pod 有整个端口空间，容器里监听 8080，别的 Pod 就用 `<podIP>:8080` 访问，所见即所得。这也是 Service/DNS 能干净工作的前提。

注意区分两套地址。Pod IP 是真实、可路由、写在 Pod 网卡上的；Service 的 ClusterIP 是虚拟、不可路由、靠转发规则生效的（见 13-9.4.5）。另外「每 Pod 一 IP」是模型要求，具体怎么实现（overlay 隧道、BGP 路由、云 VPC 原生等）由 CNI 插件决定，模型只管结果不管手段。

### 13-9.4.2 network namespace：每 Pod 一 IP 的机制根

「每个 Pod 有独立 IP 与独立网络栈」这件事，底层靠的是 Linux 的 network namespace（网络命名空间，见大主题2）。一个 network namespace 拥有自己独立的网络接口、路由表、iptables 规则、端口空间。K8s 给每个 Pod 建一个 network namespace，Pod 内的所有容器共享它——所以同一 Pod 内的容器彼此可用 `localhost` 通信、看到同一套网卡和同一个 Pod IP。

把它和 Service 串起来：Service/ClusterIP 是「上层抽象」，而 Pod 拥有真实 IP 这件「下层事实」正是 network namespace 提供的原语。可以本机直观感受这个原语（本沙箱无集群，但 unshare 可用）：

```
unshare --net --fork bash    # 进入一个新的 network namespace
ip addr                       # 只看到孤零零的 lo（且默认 DOWN），与主机网络隔离
```

这演示了「新网络栈里视图是干净、隔离的」——K8s 正是在这样一个新命名空间里，由 CNI 插件插上网卡、配好 IP 和路由，才让 Pod「拥有自己的 IP」。

同一 Pod 内多容器共享 network namespace（所以能 localhost 互通、且不能各自绑同一端口），但不同 Pod 各有独立命名空间、互相隔离——跨 Pod 通信必须走 Pod IP。把「Pod 内共享」错当成「Pod 间也共享」是常见误解。

### 13-9.4.3 CNI：把 Pod 接入网络的标准接口

CNI（Container Network Interface）是 CNCF 维护的一套规范，定义了「容器运行时如何调用网络插件来给容器配置网络」的契约：运行时在 Pod 的 network namespace 建好后，按 CNI 规范调用插件（一个可执行程序 + JSON 配置），插件负责分配 IP、创建虚拟网卡对（veth）、设置路由，把这个命名空间接入集群网络；Pod 删除时再调用插件回收。K8s 本身不实现具体网络，而是通过 CNI 把「怎么组网」外包给可插拔的插件。

这就是为什么不同集群网络实现差别很大却都能跑 K8s：Calico、Cilium、Flannel、云厂商 VPC CNI 等都实现同一套 CNI 契约，但底层手段不同（Flannel 常用 VXLAN overlay 隧道；Calico 可用 BGP 做无隧道路由；Cilium 用 eBPF）。选哪个影响性能、可观测性、以及是否支持 NetworkPolicy 等能力，但对上层 Pod/Service 抽象透明。

时效要点（⚙演进快·锚版本）：CNI 规范由 containernetworking/cni 维护，当前主线为 `1.x`（如 v1.0.0/v1.1.0 一线；精确当前版本标「待核」，以项目仓库为准）。核实 2026-08-01。

CNI 是「容器网络」的通用规范，不是 K8s 专属，也不负责 Service 那层虚拟 IP（那是 kube-proxy/CNI 数据面另一部分的事）。选 CNI 时要看它是否支持你需要的功能——比如 Flannel 传统上不带 NetworkPolicy 执行能力，装了它写的 NetworkPolicy 不会生效（见 13-9.4.4）。

### 13-9.4.4 NetworkPolicy：Pod 间的 L3/L4 防火墙

NetworkPolicy 是一种命名空间级 API 对象，用来声明「哪些 Pod 允许和哪些来源/目的通信」，工作在 L3/L4（IP、端口、协议），按 Pod 标签、命名空间标签或 IP 段选择对端。它给扁平网络加上分段隔离——默认状态下集群内所有 Pod 可互通，NetworkPolicy 让你收紧成「最小必要连通」。

一个 Pod 若没有任何 NetworkPolicy 选中它，则「全放行」（可被任何来源访问、可访问任何目的）。一旦有至少一条 policy 通过 `podSelector` 选中了它的某个方向（Ingress 或 Egress），该 Pod 在那个方向就变成「默认拒绝」，只有被 policy 明确允许的流量才通。一个「拒绝本命名空间所有入站」的经典写法：

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
spec:
  podSelector: {}        # 选中本命名空间所有 Pod
  policyTypes: ["Ingress"]
  # 不写 ingress 规则 = 不允许任何入站
```

把它想成「白名单防火墙，但只在你开始写规则后才对被选中的 Pod 生效」。这种「一旦被选中就翻转为默认拒绝」的语义是最大易错点——新手常以为「加一条 allow 规则只是加白名单、不影响其他流量」，实际上加了第一条 Ingress 规则后，没被列进白名单的入站就全被挡了。

NetworkPolicy 的执行依赖 CNI 插件支持。K8s 只定义了这个对象，执行交给数据面。如果所用 CNI 不支持 NetworkPolicy（如基础版 Flannel），你写的 policy 会被 API 接受但根本不生效——安全上是「静默失败」，非常危险。上生产前务必验证所选 CNI（Calico、Cilium 等）确实执行策略。NetworkPolicy 也不做 L7（看不懂 HTTP 路径/方法），要按内容做策略得靠服务网格或 CNI 的 L7 扩展。

### 13-9.4.5 Service 网络 vs Pod 网络：两个虚拟 IP 段

集群里存在两套不同的地址空间，初学者极易混淆。Pod CIDR 是给 Pod 分配真实 IP 的网段，这些 IP 由 CNI 分配、写在 Pod 网卡上、可在集群内路由。Service CIDR（ClusterIP 段）是给 Service 虚拟 IP 用的网段，这些 IP 不属于任何网卡、不可路由，只在每个节点的转发规则（kube-proxy）里被拦截改写成某个 Pod IP。

Pod A 访问 `backend`（一个 ClusterIP，属 Service CIDR）→ DNS 把名字解析成 ClusterIP → 包发出后被本节点内核规则拦截 → DNAT 改写目的地址为某个后端 Pod IP（属 Pod CIDR）→ 包经扁平网络送达该 Pod。可见 ClusterIP 只是「转发规则里的一个把手」，真正承载数据的始终是 Pod IP。

两个 CIDR 必须互不重叠、也不能和节点网络/外部网络冲突，否则路由歧义会引发诡异故障，这是集群规划时的硬约束。还有——ClusterIP ping 不通、抓包在 Pod 网卡上看不到「以 ClusterIP 为目的」的包（因为已在内核被改写），都源于「Service 网络是虚拟的」这一事实，理解了这两个地址段就不奇怪了。

#### 来源与时效

- 一手：Kubernetes 官方文档 Cluster Networking（网络模型四要求）、Network Policies（kubernetes.io/docs/concepts/services-networking/network-policies/），锚定 v1.36。核实 2026-08-01。
- 一手：CNI 规范，containernetworking/cni（github.com/containernetworking/cni，SPEC.md）。⚙演进快·锚版本：当前规范主线 1.x，精确当前小版本标「待核」，核实 2026-08-01。
- 一手：Linux man page network_namespaces(7) / namespaces(7)（每 Pod 一 IP 的机制根），与大主题2 一致；本机 `unshare --net` 可佐证网络栈隔离（util-linux 2.39.3，本沙箱可用）。
- 交叉核对：NetworkPolicy「未被选中=全放行、被选中方向=默认拒绝」语义，以及「执行依赖 CNI 支持、不支持则不生效」，见官方 Network Policies 文档明确说明，与 CNI 实现文档（Calico/Cilium）交叉印证。
- 冲突/待核：CNI 规范精确当前版本号、各 CNI 对 NetworkPolicy/L7 的支持范围随实现与版本变化，标「待核」；Pod CIDR/Service CIDR 具体默认值由集群安装工具决定，不编造。
