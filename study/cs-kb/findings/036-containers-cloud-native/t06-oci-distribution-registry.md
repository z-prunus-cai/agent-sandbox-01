# L5-13·大主题6 OCI 分发与镜像仓库

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：本课大主题4 OCI 镜像格式与分层、L4-02 计算机网络（HTTP 请求/响应、状态码、缓存）、密码学哈希（内容摘要）｜一手锚点：OCI Distribution Spec v1.1.0（2024-02-15）、OCI Image Spec v1.1.0（2024-02-15）、HTTP 相关 RFC、Sigstore/cosign 官方文档 ｜成熟度：分发协议 GA；13-6.4 供应链签名为快速演进领域（⚙演进快·锚版本·随时变）

镜像做好了要放到哪里、别人怎么把它取下来跑——这就是"分发"（distribution）要解决的问题。存放镜像的服务器叫**镜像仓库**（registry），你熟悉的 Docker Hub、GitHub Container Registry、Harbor、各云厂商的容器镜像服务都是它的实例。它们之间之所以能互通（用 docker、podman、containerd、skopeo 任一客户端推拉任一家仓库），靠的是一份公共契约：**OCI Distribution Spec**（分发规范）。这份规范只规定"客户端与仓库之间的 HTTP 交互长什么样"，不规定仓库内部怎么存储。本报告先讲这套 pull/push 协议，再讲支撑它的内容寻址与去重，接着讲 tag 与 digest 两种引用方式对"可重复性"的影响，最后触及正在快速成型的镜像签名与供应链安全。整个协议建在 HTTP 之上（衔接 L4-02 网络），下面很多机制其实就是把 HTTP 的既有能力（状态码、Location 重定向、Range 分块、条件请求）用在了镜像传输上。

---

## 13-6.1 registry pull/push API：blob 与 manifest 的分发协议

### 13-6.1.1 registry 存的两类东西：blob 与 manifest

一个仓库在协议层面只区分两类对象：**blob** 和 **manifest**。blob（binary large object，二进制大对象）是按内容摘要（digest）寻址的不可变字节块——镜像的每一层压缩 tar、以及镜像的 config JSON，都是 blob。manifest 则是描述"一个镜像由哪些 blob 组成"的清单文件（大主题4 讲过它的结构：一个 config 描述符 + 一组 layer 描述符），也可以是一个 index（多架构镜像的清单的清单）。

理解这一点，整套 API 就有了骨架：所有端点无非是"上传/下载 blob""上传/下载 manifest""按名字列出/删除"这几件事。仓库里的内容按**仓库名**（repository name，如 `library/nginx`）组织，规范里用 `<name>` 表示；每个 name 下有若干 manifest（用 tag 或 digest 引用）和它们所依赖的 blob。初学者常把"镜像"当成一个单一文件，其实在仓库里它是"一个 manifest + 若干 blob"的松散集合，pull 一个镜像 = 先拿 manifest、再按清单逐个拿缺的 blob。

### 13-6.1.2 所有端点都在 `/v2/` 命名空间下

OCI Distribution 的所有 API 路径都以 `/v2/` 开头（这个 `v2` 是历史沿革——它继承自 Docker Registry HTTP API V2，OCI 把它标准化后沿用了前缀）。客户端与仓库建立联系的第一步，是探测这个根端点确认对方"会说 v2 协议"：

```
GET /v2/
```

仓库若支持该协议，返回 `200 OK`（若需要认证则返回 `401 Unauthorized` 并在 `WWW-Authenticate` 头里指明去哪拿令牌）。这一步相当于握手：客户端据此判断能否继续、以及要不要先走认证流程。

之所以要有这么一个"空"端点专门做版本探测，是因为客户端面对的是形形色色的仓库实现，需要一个统一的、副作用最小的方式先确认协议版本和认证要求，再决定后续请求怎么发。

### 13-6.1.3 pull：先取 manifest，再取 blob

拉取一个镜像分两步。第一步取 manifest，用 tag 或 digest 作为引用（规范里统称 `<reference>`）：

```
GET /v2/<name>/manifests/<reference>
```

客户端应在 `Accept` 头里列出自己能处理的 manifest media type（如 `application/vnd.oci.image.manifest.v1+json` 或 index 的 `application/vnd.oci.image.index.v1+json`），仓库据此返回合适的清单。第二步，客户端解析 manifest 拿到各层和 config 的 digest，再逐个下载对应 blob：

```
GET /v2/<name>/blobs/<digest>
```

这里 `<digest>` 形如 `sha256:<64位十六进制>`。blob 内容是不可变的，所以可以放心缓存、也可以从任意镜像的同一层复用（见 13-6.2）。

为什么先 manifest 后 blob？因为 manifest 很小（几 KB 的 JSON），先拿到它就能知道"这个镜像总共要下哪些层、每层多大、哪些本地已有"，从而只下载缺失的部分——这正是分层镜像省流量的前提。一个易错点是：`GET blob` 返回的往往不是 `200` 直接带数据，而可能是 `307`/`302` 重定向到一个真正存数据的地方（比如对象存储的临时 URL），客户端要跟随 `Location`。

### 13-6.1.4 用 HEAD 先探存在性

在真正下载或上传前，客户端常先发 HEAD 请求探一探"这个东西仓库里有没有"：

```
HEAD /v2/<name>/manifests/<reference>
HEAD /v2/<name>/blobs/<digest>
```

存在则返回 `200`（并在响应头给出 `Content-Length`、`Docker-Content-Digest` 等元信息），不存在返回 `404`。HEAD 与 GET 语义相同但不返回响应体，这是 HTTP 本身的机制（衔接 L4-02）。

推一层之前先 HEAD 一下该 blob 的 digest，如果仓库已经有了（因为别的镜像共享同一层），就完全跳过上传——这是去重省带宽的关键一招（见 13-6.2）。拉取时同理，HEAD manifest 可以在不下载整份清单的情况下拿到它的规范 digest，用于校验或判断本地缓存是否最新。

### 13-6.1.5 push blob：会话式上传（POST → PATCH/PUT）

上传一个 blob 不是一个请求搞定，而是一个**会话**。先向仓库申请开一个上传会话：

```
POST /v2/<name>/blobs/uploads/
```

仓库返回 `202 Accepted`，并在 `Location` 头里给出这次上传专属的会话 URL（形如 `/v2/<name>/blobs/uploads/<reference>`，这里的 `<reference>` 是仓库分配的会话标识，不要与镜像引用混淆）。之后有两种传法。

分块上传（chunked）：把 blob 切成若干块，逐块 PATCH 到会话 URL，每块用 `Content-Range` 头标明字节区间，最后一次 PUT 关闭会话并带上整体 digest 供校验：

```
PATCH /v2/<name>/blobs/uploads/<reference>
PUT   /v2/<name>/blobs/uploads/<reference>?digest=<digest>
```

单体上传（monolithic）：一次 POST 直接带 `?digest=<digest>` 和全部数据传完：

```
POST /v2/<name>/blobs/uploads/?digest=<digest>
```

分块的好处是大层可以断点续传、失败只重传坏块；`Range` 头用于查询"已经收到哪些字节"（格式如 `0-<末字节>`），便于续传。收尾的 PUT 之所以要再带一次 `digest`，是让仓库用它去校验"收全的字节算出来的哈希是否和你声称的一致"，一致才落盘——这既防传输损坏，也保证 blob 名副其实地"内容寻址"（13-6.2）。

### 13-6.1.6 跨仓库挂载（cross-repo mount）省一次上传

如果要推的这一层，在同一仓库服务器的**另一个** repository 里已经存在，客户端可以请求"挂载"而不是重新上传：

```
POST /v2/<name>/blobs/uploads/?mount=<digest>&from=<other_name>
```

仓库若认可（`from` 指定的源仓库里确有该 digest，且客户端有权访问），直接返回 `201 Created` 把该 blob 关联到目标仓库，一个字节都不用传。

比如你的应用镜像和别人的镜像都基于同一个 `ubuntu` 基础层，推你的镜像时那几层基础层大概率服务器上已有，挂载让推送几乎只上传你新增的那一层。它把 13-6.2 的"内容寻址去重"从"同仓库"扩展到了"同服务器跨仓库"。

### 13-6.1.7 push manifest：最后一步"发布"

所有 blob 都在仓库里之后，最后 PUT manifest 把它们组装成一个可被引用的镜像：

```
PUT /v2/<name>/manifests/<reference>
```

请求体是 manifest JSON，`Content-Type` 标明其 media type，`<reference>` 通常是要打的 tag（如 `v1.2.3`）。仓库成功后返回 `201 Created`，并在 `Docker-Content-Digest` 响应头里回给你这份 manifest 的规范 digest。

顺序很重要：**必须先传完所有被引用的 blob，再 PUT manifest**。因为规范要求仓库在接收 manifest 时校验它引用的 blob 都已存在，否则应拒绝（返回 `BLOB_UNKNOWN` 类错误）。这保证了"仓库里存在的 manifest 一定是自洽的、能拉全的"——不会出现"清单在、层却缺"的半成品镜像。这也是为什么 push 的自然流程是自底向上：先 blob 后 manifest。

### 13-6.1.8 列 tag 与删除

列出一个仓库下的所有 tag：

```
GET /v2/<name>/tags/list
GET /v2/<name>/tags/list?n=<数量>&last=<上一页最后一个tag>
```

结果可能很多，故支持用 `n` 和 `last` 做分页（拿一批、带着"上次最后一个"再要下一批）。删除 manifest（或 tag）与删除 blob：

```
DELETE /v2/<name>/manifests/<reference>
DELETE /v2/<name>/blobs/<digest>
```

删除是否被支持、以及删 tag 与删底层 manifest/blob 的确切语义，因仓库实现而异（有的只解引用不立即回收空间，靠后台 GC）。规范定义了这些端点，但把很多回收细节留给实现——这是"规范与实现分账"的一个典型点：端点形状是标准的，删除后的空间回收行为要看具体仓库。

#### 来源与时效
- OCI Distribution Spec v1.1.0（2024-02-15，opencontainers/distribution-spec），核实 2026-08-01：`/v2/` 版本检查、manifest/blob 的 GET/HEAD/PUT/DELETE、blob 上传会话（POST→PATCH/PUT）、单体与分块上传、`?mount=&from=` 跨仓库挂载、`/tags/list` 分页参数 `n`/`last`，均逐路径对照规范原文抄录。
- 头字段 `Location`、`Docker-Content-Digest`、`Content-Range`、`Range`、`Content-Length` 的用途以 Distribution Spec 的头字段表交叉确认。
- HTTP 语义（HEAD 无体、状态码、重定向 `Location`、Range 分块）回指 HTTP 相关 RFC（L4-02），与规范用法一致。
- 分账：端点形状由规范强制；删除后的空间回收、blob 存储后端（是否重定向到对象存储）属实现细节，两份来源均将其留给实现，正文已标注。

---

## 13-6.2 内容寻址与去重：按 digest 共享层减少传输

### 13-6.2.1 digest 是什么：内容的密码学指纹

digest（摘要）是对一个 blob 的字节内容做密码学哈希得到的唯一标识，格式是 `算法:十六进制哈希值`，最常见的是：

```
sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

规范把它定义为"由 blob 内容的密码学哈希创建的唯一标识符"，算法前缀（`sha256`、也可 `sha512`）决定用哪种哈希。关键性质是**内容决定名字**：同样的字节 → 同样的 digest；改一个 bit → digest 完全不同。这就是"内容寻址"（content-addressable）——你不是用"路径/名字"找东西，而是用"内容的指纹"找东西。

这一心智模型在计算机科学里到处出现：Git 用 SHA 给每个对象命名（衔接 L4-06 Git 对象模型），本课大主题4 讲镜像层时也用它。初学者要建立的直觉是：digest 既是"地址"又是"校验和"——你拿它去仓库要数据，拿回来再自己算一遍哈希，对得上就证明"没被篡改、没传坏、正是我要的那份"。

### 13-6.2.2 去重：相同 digest 只存一份、只传一次

因为 blob 用内容命名，两个不同镜像若含有字节完全相同的一层（比如都基于同一个基础镜像），这层的 digest 就一样，在仓库里**只需存一份**，在网络上**只需传一次**。

去重在推拉两端都省：拉取时，客户端解析 manifest 拿到各层 digest，先看本地缓存有没有——本地已有的层（比如上个版本就下过的基础层）直接跳过，只下真正新增/变化的层。推送时，先 HEAD 每个 blob 的 digest（13-6.1.4），仓库已有的就不传，甚至可以用跨仓库挂载（13-6.1.6）连关联都省了。结果是：一个日常迭代的应用镜像，每次发版往往只有最上面那一薄层是新的，推拉都极快——这正是分层 + 内容寻址组合的红利。

去重的粒度是**整层**，不是"层内的文件"。哪怕两层只差一个文件，它们也是两个不同 digest、两个独立 blob，不会互相复用。所以镜像构建时"把易变的东西放在靠上的层、把稳定的依赖放在靠下的层"能最大化复用——这是大主题4 分层构建的实践经验在分发侧的直接回报。

### 13-6.2.3 完整性校验：数据自证清白

内容寻址让传输天然具备完整性保护。客户端下载完一个 blob 后，对收到的字节重新计算哈希，与请求时用的 digest 比对，不一致就说明数据在途中损坏或被篡改，直接丢弃重试。仓库在接收上传时也做同样的校验（收尾 PUT 带 digest，见 13-6.1.5），算不对就拒绝落盘。响应里的 `Docker-Content-Digest` 头则让客户端能拿到"仓库认定的规范 digest"用于比对。

这带来一个重要保证：**只要你按 digest 拉取，你拿到的内容就一定是那个 digest 对应的字节，任何中间环节（CDN、代理、镜像站）都无法在不被发现的情况下改动它**。这也是 13-6.3 说"digest 引用不可变、可重复"的底层原因，以及 13-6.4 供应链签名能成立的地基——签名签的其实就是这些 digest。注意：内容寻址保证的是"内容没被改"（完整性），不等于"内容可信/是好人做的"（真实性与来源）；后者要靠签名解决，别把两者混为一谈。

#### 来源与时效
- OCI Distribution Spec v1.1.0（digest 定义、上传/下载的 digest 校验、`Docker-Content-Digest` 头）与 OCI Image Spec v1.1.0（descriptor 的 `digest` 字段、层与 config 均按 digest 引用），核实 2026-08-01，两份规范一致。
- 内容寻址与去重的机制根同 L4-06 Git 对象模型（SHA 命名、内容即地址），作为跨课交叉印证，非承重。
- 完整性 vs 真实性的区分：完整性由内容寻址保证，真实性/来源需签名（13-6.4），两来源（Distribution Spec + Sigstore 文档）分别支撑，正文已点明界线。

---

## 13-6.3 tag vs digest 引用：可变 tag vs 不可变 digest 的可重复性

### 13-6.3.1 reference 的两种形态

前面端点里的 `<reference>` 可以是两种东西：**tag**（人给的可读名字，如 `nginx:1.27`、`myapp:latest`）或 **digest**（内容摘要，如 `nginx@sha256:...`）。两者都能定位到一个 manifest，但性质截然不同。

tag 是仓库维护的一个"可变指针"：`myapp:latest` 今天指向这份 manifest，明天你重新 push 同名 tag，它就指向另一份了——tag → manifest 的映射随时可被覆盖。digest 则是 manifest 内容本身的哈希，**不可变**：`myapp@sha256:abc...` 永远指向那一份确切的 manifest，因为一旦内容变了 digest 也就变了，不可能"还叫这个 digest 但内容不同"。

日常拉取写 `docker pull nginx:1.27` 用的是 tag，图的是好记；但要"钉死一个确切版本"就得用 digest：`docker pull nginx@sha256:...`。

### 13-6.3.2 可变 tag 的便利与陷阱

tag 可变很方便：发布者把 `latest` 或 `1.27` 滚动指向最新构建，使用者不改引用就能拿到更新。但这份便利有代价——**同一个 tag 在不同时间可能拉到不同镜像**。

最经典的坑是 `latest`：你今天 CI 里 `FROM node:latest` 构建通过，一周后同样的 Dockerfile 却因为 `latest` 悄悄指向了新大版本而构建失败或行为变化；两台机器"拉的都是 `myapp:latest`"却跑着不同代码，排障时极其困惑。tag 还可能被重新打（retag）、被删除后重建指向别处。所以 tag 适合"我要最新的"，不适合"我要那个确切的、和上次一模一样的"。这也是为什么很多团队约定"生产部署禁用 `latest`"。

### 13-6.3.3 用 digest 钉死可重复性

要"这次和上次拉到的字节完全一样"，就用 digest 引用（业界叫 pin by digest）。因为 digest 由内容决定（13-6.2），只要 digest 不变，拉到的 manifest 及其引用的所有层就一定逐字节相同——**可重复、可审计、防偷换**。

实践中的常见模式是"用 tag 找、用 digest 钉"：开发时用 tag 方便，一旦确定要发布的版本，就把它解析成 digest 记录进部署清单（Kubernetes 的 Pod spec、锁文件、IaC 配置里写 `image: myapp@sha256:...`）。这样无论中途 tag 怎么被人重打、无论从哪个镜像站/CDN 拉，落地的都是同一份镜像——呼应大主题4"不可变镜像作可靠部署基座"。取舍也很清楚：digest 可重复但不可读、且不会自动获得更新（要更新就得显式换 digest）；tag 可读、能自动跟随更新，但牺牲了确定性。成熟做法是二者配合，而非二选一。

### 13-6.3.4 建在 HTTP 之上：引用如何变成一次请求

无论 tag 还是 digest，最终都被填进 `/v2/<name>/manifests/<reference>` 这个 URL 里发一个 HTTP 请求（衔接 L4-02）。也就是说"引用"在协议层就是 URL 路径的一段，仓库拿到后去查"这个 reference 对应哪份 manifest"。

正因为跑在 HTTP 上，分发能白捡 HTTP 的整套基础设施：用 CDN/缓存代理加速 blob 下载（blob 不可变，是完美的可缓存对象）、用 `Docker-Content-Digest` 响应头让客户端校验缓存命中的对象是否正确、用标准状态码（`200/307/404/401`）表达结果、用 Bearer Token 走标准认证。理解"分发本质是一组带约定的 HTTP 交互"，能帮你在排障时直接用 `curl` 手动打这些端点观察，也解释了为什么一个普通的静态文件服务器加点逻辑就能当只读仓库用。

#### 来源与时效
- OCI Distribution Spec v1.1.0（`<reference>` 可为 tag 或 digest、manifest 端点、`Docker-Content-Digest`）与 OCI Image Spec v1.1.0（digest 由内容决定、不可变），核实 2026-08-01，两来源一致。
- `latest` 可变性导致的不可重复性：属规范允许的 tag 语义（tag 是可覆盖指针）的直接推论，规范未规定 `latest` 有特殊含义——"latest = 最新"仅是客户端/生态约定，此点两来源一致，正文已按"约定而非规范"表述。
- HTTP 之上的缓存/认证/状态码回指 HTTP 相关 RFC（L4-02），作机制根印证。

---

## 13-6.4 镜像签名与供应链：cosign / attestation 等验真（⚙演进快·锚版本·随时变）

> 本小主题为快速演进领域：工具版本、存储约定、规范化程度都在变动。以下按核实 2026-08-01 的状态写，凡二手承重处打 ⚠，规范化程度不确定处标「待核」，不作定论。

### 13-6.4.1 为什么需要：内容寻址保完整、保不了来源

前面说过，按 digest 拉取能保证"内容没被改"（完整性），但保证不了"这镜像是不是可信方构建的、有没有在你信任链的某个环节被人换成投毒版本"（真实性与来源）。软件供应链攻击正是钻这个空子：往公共基础镜像里塞后门、劫持某个环节把恶意镜像推到你信任的 tag 上。**镜像签名**要解决的就是"我怎么确信这个镜像确实由我信任的实体产出、且自那以后没被动过"。

直觉上，签名就是让发布者用私钥对镜像的 digest 做数字签名，使用者用对应公钥验证。因为签的是 digest（13-6.2）、而 digest 又锁死了全部内容，所以"验签通过"就等于"这份确切内容确实经过签名者背书"。这是把密码学签名（衔接 L5-04 安全）用到了容器分发上。

### 13-6.4.2 cosign：把签名当作仓库里的另一个工件

cosign（Sigstore 项目的容器签名工具，⚠工具文档承重）的核心思路是：**签名本身也是一个 OCI 工件，就存在同一个仓库里**，不需要额外的签名服务器。这样签名跟着镜像走、用同一套 pull/push 协议分发。

签名与镜像的关联方式有两种，正处在从旧到新的过渡中（⚙演进快）。传统方式是**约定 tag**：对 digest 为 `sha256:abc...` 的镜像，把它的签名推到一个特殊 tag `sha256-abc....sig`（把 `:` 换成 `-`、加 `.sig` 后缀）下（⚠此 `.sig` 命名为 cosign 约定、非 OCI 规范，具体后缀细节以 cosign 当前文档为准，待核锚定版本）。较新的方式是用 OCI 1.1 的 **referrers API**（见 13-6.4.5）通过 manifest 的 `subject` 字段把签名"挂"到镜像上，这是规范层面的关联机制。两种方式并存，选哪种取决于仓库是否支持 referrers、以及 cosign 版本与配置——具体默认行为随版本变化，待核。

### 13-6.4.3 keyless 签名：Fulcio 短期证书 + OIDC 身份

传统签名要求发布者妥善保管一把长期私钥——一旦泄露，攻击者就能冒签。Sigstore 提出的 **keyless（无密钥）签名**换了个思路：不长期持有私钥，而是签名那一刻临时生成一对密钥，由证书颁发机构 **Fulcio** 校验你的 OIDC 身份（如你的 GitHub/Google 账号、或 CI 的工作负载身份）后，签发一张**短期证书**把这把临时公钥绑定到你的身份上；签完私钥即销毁、证书很快过期。

Sigstore 官方文档原话是 Fulcio "签发短期证书，把临时密钥绑定到一个 OpenID Connect 身份"，且"私钥在签名后不久即被销毁、短期身份证书随之过期"。这样做的好处是把"保管长期私钥"这个最脆弱的环节干脆去掉——没有长期密钥可泄露。验证方转而依赖下面的透明日志来确认"某身份在某时刻确实签了某内容"。这是快速演进的机制，具体证书有效期、支持的 OIDC 提供方等数值以官方文档当前版本为准（待核）。

### 13-6.4.4 Rekor 透明日志：让签名事件公开可审计

keyless 用短期证书，那证书过期后怎么还能验证当初的签名有效？答案是 **Rekor 透明日志**：每次签名事件都被记入一个不可篡改的公开日志，条目里含"工件的哈希、公钥、签名"（Sigstore 文档原话），并带可信时间戳。

透明日志借鉴了证书透明度（Certificate Transparency）的思路：把签名行为公开、可追加、不可篡改地记录下来。它带来两重价值——验证方可以凭日志条目确认"签名发生在证书有效期内"，即使证书已过期；发布者可以监控日志，一旦发现有人用自己的身份签了自己没签过的东西，就能及早发现身份被滥用。这类"公开可审计的信任"是 Sigstore 供应链安全模型的支柱之一（⚠机制细节以官方文档为准，快速演进）。

### 13-6.4.5 attestation / in-toto / SLSA：不只签"是谁"，还证"怎么来的"

签名回答"这镜像是谁背书的"，**attestation（证明/证言）**进一步回答"这镜像是怎么造出来的"——它是一份关于工件的、可验证的元数据声明，比如"这镜像由某条 CI 流水线、用某个 commit 的源码、在某时间构建"。业界常用 **in-toto** 的 attestation 格式来承载这类声明，用 **SLSA**（Supply-chain Levels for Software Artifacts，供应链软件工件安全等级）框架来定义"构建来源（provenance）应该包含什么、达到什么等级"（⚠in-toto/SLSA 为社区规范，成熟度与采纳度待核）。

直觉上，签名 + attestation 合起来支撑一个"可验证的供应链"：使用者在部署前不仅验"签名有效、来源可信"，还能验"这镜像确实来自受信的构建系统、用的是审阅过的源码"。SBOM（软件物料清单，列出镜像里有哪些组件/依赖）也常以 attestation 形式附在镜像上，供漏洞扫描与合规审计。这些声明和签名一样，都可以作为工件存进仓库、通过下面的 referrers 机制挂到镜像上。这是当前供应链安全最活跃的方向，标准与工具都在快速成型，规范化程度整体「待核」。

### 13-6.4.6 referrers API：把签名/SBOM/attestation 挂到镜像上（规范层机制）

OCI Distribution/Image Spec v1.1.0 引入了 **referrers（引用者）API**，为"把附属工件关联到某个镜像"提供了规范化机制。做法是：附属工件（签名、SBOM、attestation……）的 manifest 里带一个 `subject` 字段，指向它所描述的目标镜像的 descriptor；仓库处理后在响应里回 `OCI-Subject` 头确认。之后任何人都能查某镜像挂了哪些附属工件：

```
GET /v2/<name>/referrers/<digest>
GET /v2/<name>/referrers/<digest>?artifactType=<mediaType>
```

返回一个 image index，里面列出所有以该 digest 为 `subject` 的 manifest（可用 `artifactType` 过滤只看签名或只看 SBOM）。这把 13-6.4.2 里 cosign 的"约定 tag"土办法升级成了规范支持的一等机制。

考虑到许多老仓库还不支持这个新端点，规范定义了**回退**：客户端查 referrers 得到 `404` 时，MUST 回退到读一个按"referrers tag schema"约定的 tag——格式为 `<alg>-<ref>`，即"摘要算法 - 目标 digest 的前 64 位十六进制"（如 `sha256-aaaa...`），该 tag 下存着一个与 referrers 响应等价的 image index。这个设计让新机制能在存量仓库上平滑落地：支持就用新端点、不支持就退回约定 tag，客户端行为一致。referrers API 本身是 v1.1.0 规范内容（GA），但围绕它的签名/attestation 生态（cosign、sigstore、SLSA 采纳）仍快速演进，整体成熟度与默认实践「待核」。

#### 来源与时效
- OCI Distribution Spec v1.1.0（2024-02-15）：referrers API 路径 `/v2/<name>/referrers/<digest>`、`artifactType` 过滤、`subject` 字段与 `OCI-Subject` 头、404 回退到 referrers tag schema、回退 tag 格式 `<alg>-<ref>`（前 64 位），核实 2026-08-01，逐项对照规范原文。
- OCI Image Spec v1.1.0：manifest 的 `subject` 字段与 descriptor 定义，与 Distribution Spec 交叉一致。
- Sigstore/cosign 官方文档（docs.sigstore.dev）：keyless 签名（Fulcio 短期证书绑定 OIDC 身份、私钥即时销毁）、Rekor 透明日志（记录工件哈希/公钥/签名的不可篡改日志）——⚠工具文档承重，快速演进。
- ⚠仅二手/社区规范：cosign 传统 `.sig` 约定 tag 命名、in-toto attestation 格式、SLSA provenance 等级——为工具约定与社区框架，非 OCI 规范；具体后缀/字段/等级定义以各自当前文档为准，标「待核」，本报告不作定论。
- 前沿标注：本小主题整体 ⚙演进快·锚版本·随时变，核实日期 2026-08-01；数值（证书有效期、默认关联方式、SLSA 采纳度）未逐一坐实者均标「待核」，未凭记忆填充。
- 冲突/分歧记录：签名与镜像的关联方式存在"cosign 约定 tag"（工具约定、历史做法）与"OCI referrers API/`subject`"（规范机制、较新）两条并行路径，二者并存且处于过渡中，具体客户端默认走哪条随工具版本变化，本报告两条都记、不和稀泥。
