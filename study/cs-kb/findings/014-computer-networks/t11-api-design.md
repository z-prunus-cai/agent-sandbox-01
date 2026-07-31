# L4-02·大主题N11 API 设计（并入 N-09）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-29 ｜ 先修：本课大主题N8（HTTP 与 Web，尤其 RFC 9110 HTTP 语义——本报告全部建于其上）、大主题N9（TLS，API 鉴权令牌几乎总跑在 TLS 之上）、大主题N5（传输层，理解无状态/连接的地基）｜ 一手锚点：RFC 9110（HTTP Semantics，2022-06，方法/幂等/安全/状态码/条件请求/Retry-After 承重）、RFC 6749（The OAuth 2.0 Authorization Framework，2012-10）、RFC 6750（OAuth 2.0 Bearer Token Usage，2012-10）、RFC 7519（JSON Web Token，2015-05）、RFC 7515（JWS，2015-05）、RFC 7518（JWA，签名算法）、RFC 7636（PKCE，2015-09）、RFC 6585（Additional HTTP Status Codes，含 429，2012-04）、RFC 8288（Web Linking / Link 头，2017-10）、RFC 5789（PATCH，2010-03）、Roy T. Fielding 博士论文《Architectural Styles and the Design of Network-based Software Architectures》(2000) ch.5（REST 原始定义）、OpenAPI Specification 3.1.1（OpenAPI Initiative / Linux Foundation，2024-10）、Kurose & Ross《Computer Networking: A Top-Down Approach》8th ed(2021) §2.2（HTTP 语义地基参照）｜ 成熟度：GA/稳定（REST、RFC 9110、OAuth 2.0、JWT、OpenAPI 3.1 均已 GA 且广泛部署）；**⚙演进快·锚版本**：OAuth 2.1（仍 IETF draft，见 N11.5.9）、HTTP RateLimit 头（仍 IETF draft，见 N11.6.5）、OpenAPI 4.0 "Moonwalk"（开发中）。

> 粒度判定（开工第一步）：**1 份，不拆**。本大主题 6 个小主题（N11.1–N11.6）共用同一根地基（RFC 9110 HTTP 语义）并沿一条设计主线递进——"先把资源怎么建模、方法怎么映射讲清（N11.1）→ 由此引出方法的安全/幂等属性与重试语义（N11.2）→ 接口一旦上线怎么演进不砸客户端（N11.3）→ 怎么用机器可读契约把这套约定固化下来（N11.4）→ 谁能调用、以什么身份调用（N11.5 鉴权）→ 集合资源怎么翻页、过滤与限流这些收尾约定（N11.6）"。六节篇幅适中、互为因果，按 report-format v3 §一默认 1 大主题 = 1 报告；任务亦指定只产这一个文件，故不拆 `-a/-b`。

> 本报告一条主线心智模型：**Web API 设计不是一个新的协议层，而是"建在 HTTP 9110 语义之上的一套应用层约定"**。HTTP 已经给了你一套现成的通用词汇——一组方法（GET/PUT/POST/DELETE…）、一组状态码（200/201/404/409…）、一套头部（ETag/Retry-After/Link…）；API 设计做的事，是把你的业务概念映射成"资源 + 表述"，再复用 HTTP 这套词汇去操作它们，从而让任何懂 HTTP 的客户端都能"望文生义"地使用你的接口，而不用为每个接口重新发明一套语义。REST 是这套映射最有影响力的架构风格，OpenAPI 是把这套约定写成机器可读契约的语言，OAuth 2.0/JWT 是往上加"谁在调用"的身份层。

> 上下游边界（本课不外扩，只在交界处一句指路）：**契约测试、CI 流水线、消费者驱动契约（consumer-driven contract）、API 网关的运维编排等属 L4-06 软件工程，不在本课**——本报告只讲 OpenAPI 契约"是什么、描述什么"，不讲怎么把它接进 CI 做回归。**鉴权部分承重用 OAuth 2.0 = RFC 6749**；OAuth 2.1 目前仍是 IETF draft（`draft-ietf-oauth-v2-1`，尚未成为 RFC），本报告仅在 N11.5.9 登记其演进方向、硬标状态，不作为结论承重。密码学原语本身（HMAC/RSA/ECDSA "为什么安全"、TLS 握手细节）归 N9 与 g11（安全密码课），本报告只把 JWT 签名当作**机制**讲其结构与陷阱，不深挖算法。

> 证据说明：本报告协议事实以 RFC 一手文本（9110/6749/7519/6750/6585/8288/5789）、Fielding 2000 论文 ch.5、OpenAPI 3.1.1 规范、Kurose-Ross 8E §2.2 多路独立交叉核对；方法的安全/幂等属性、状态码含义、JWT 注册声明、OAuth 授权流参数均取自规范原文、非凭记忆。核对基于作者训练内一手文本，未做本次会话内的 RFC/OpenAPI 在线抓取。本机实证为可选补充、不替代多来源比对：本容器有 Python 3.11.15、`curl 8.x`、`jsonschema 4.26.0`（本次 `pip install`），据此就地跑了三段真实实证——最小 `http.server` API + `curl` 观察方法映射与幂等（N11.2）、`jsonschema` 按 OpenAPI 3.1 组件 schema 校验样例（N11.4）、Python `base64`/`hmac` 构造并解 JWT 看声明结构（N11.5）——真实输出贴入对应节（@2026-07-29，Linux 6.18.5 x86_64）。

---

## N11.1 REST 资源模型与方法映射

### N11.1.1 REST 是一组架构约束，不是一个协议

REST（REpresentational State Transfer）是 Roy Fielding 在其 2000 年博士论文第 5 章定义的一种**网络应用架构风格**。它不是标准、不是协议、也不绑定 HTTP，而是一组约束的集合：满足这些约束的系统被称为 RESTful。Fielding 列出的约束是：客户端-服务器分离、**无状态（stateless）**、可缓存（cache）、**统一接口（uniform interface）**、分层系统（layered system），以及一个可选约束"按需代码（code-on-demand，如浏览器下载 JS）"。

初学者最需要纠正的一个误解是"REST = 用 JSON 的 HTTP 接口"。不是。REST 是一套设计准则，HTTP 只是当今最常用来落地它的载体；反过来，一个用 HTTP+JSON 的接口如果全靠 `POST /doEverything` 塞一个动作名进 body（即把 HTTP 当纯传输管道用），那它其实不 RESTful。REST 的价值在于：一旦你的接口遵守这套通用约束，任何懂 HTTP 的客户端、缓存、代理都能对它做出正确假设（GET 可缓存、可重试；带 `ETag` 可条件请求……），无需为你的接口定制。

### N11.1.2 资源、标识符与表述

REST 的核心名词有三个，务必分清。**资源（resource）**是你想暴露的任何"可命名的东西"——一个用户、一笔订单、"今天的天气"，甚至一个集合（"所有订单"）。**标识符（identifier）**是资源的名字，在 Web 上就是 URI，如 `/orders/42`。**表述（representation）**是资源在某一刻的一份具体字节序列——比如订单 42 的 JSON 快照，或它的 XML 版本、它的 HTML 版本。

这里要建立的关键直觉是，**你操作的永远是"表述"，不是资源本身**。资源是抽象概念（"订单 42"），它随时间变化；你 `GET /orders/42` 拿到的是它此刻的一份表述（一段 JSON），你 `PUT` 上去的也是一份新表述。同一资源可有多份表述，客户端通过**内容协商**（`Accept: application/json` vs `Accept: application/xml`）选要哪种。由此得出 URI 设计的两条经验准则：URI 标识"名词/资源"而非"动词/动作"（用 `/orders/42` 而非 `/getOrder?id=42`），集合用复数名词（`/orders`），层级用路径表达从属（`/orders/42/items`）。

### N11.1.3 无状态约束

无状态指**每个请求都必须自带服务器处理它所需的全部信息，服务器不在请求之间保存客户端的会话状态**。也就是说，服务器处理第 N 个请求时，不依赖"上一个请求发生过什么"这种存在服务器内存里的会话上下文；任何需要的上下文（身份、分页游标等）都由客户端在本次请求里带上。

这与"应用有没有状态"是两回事，初学者极易混淆。资源本身当然有状态（订单 42 的数据存在数据库里），这是**资源状态**，允许且必须持久化。无状态约束禁止的是**会话状态**——不要在服务器内存里放"这个客户端登录后正处在第 3 步"。它带来的直接好处是水平扩展：因为任何一台服务器都能独立处理任何一个请求，你可以把请求随意负载均衡到任意实例、任意实例宕了重试到别处都不影响正确性。代价是每个请求要重复携带身份等信息（这正是 N11.5 的令牌要在每个请求里带的原因）。

### N11.1.4 统一接口与 HATEOAS

统一接口是 REST 区别于其它风格的核心约束，Fielding 把它拆成四条子约束：资源的标识（用 URI）、通过表述操纵资源、**自描述消息**（每条消息自带足够元数据说明怎么处理它，如 `Content-Type`）、以及 **HATEOAS**（Hypermedia As The Engine Of Application State，超媒体作为应用状态引擎）。

HATEOAS 是其中最少被完整实现、也最需要解释的一条。它要求：服务器在返回的表述里**内嵌指向下一步可用操作的链接**，客户端靠跟随这些链接来驱动流程，而不是把 URI 规则硬编码在客户端里。举个最小例子——一笔订单的响应里带：

```json
{
  "id": "42", "status": "pending",
  "_links": {
    "self":   { "href": "/orders/42" },
    "pay":    { "href": "/orders/42/payment", "method": "POST" },
    "cancel": { "href": "/orders/42", "method": "DELETE" }
  }
}
```

客户端看到 `pay` 链接就知道"现在可以支付"，看不到就知道不能——业务规则由服务器通过链接的出现/消失来驱动，客户端不必自己判断"pending 状态才能付款"。现实中完整实现 HATEOAS 的 API 很少（多数停在 N11.1.7 的 Level 2），但理解它能帮你看懂为什么 REST 强调"超媒体驱动"以及 GraphQL/gRPC 这些非 REST 风格在权衡什么。

### N11.1.5 方法映射到 RFC 9110 语义

RESTful API 的"动词"直接复用 HTTP 方法，其语义由 RFC 9110 §9.3 逐个规范定义（不是由 API 作者自定义）。资源模型到方法的典型映射如下：

```
操作           方法      RFC 9110    典型语义（作用于 /orders 或 /orders/42）
─────────────  ────────  ─────────   ──────────────────────────────────────────
读取集合       GET       §9.3.1      GET /orders        → 返回订单列表表述（安全）
读取单个       GET       §9.3.1      GET /orders/42     → 返回订单 42 表述（安全）
创建（服务器定 ID）POST   §9.3.3      POST /orders       → 新建订单，201 + Location
创建/整体替换（客户端定 ID）PUT §9.3.4 PUT /orders/42    → 有则整体替换，无则创建
部分更新       PATCH     RFC 5789    PATCH /orders/42   → 局部修改（9110 未定义，见下）
删除           DELETE    §9.3.5      DELETE /orders/42  → 删除订单 42
探测能力       OPTIONS   §9.3.7      OPTIONS /orders    → 返回 Allow 头列出可用方法
```

有一点必须点明，**PATCH 不是 RFC 9110 定义的方法**，它由 RFC 5789 单独定义，用于"对资源应用一组部分修改"。POST 是"通用的、语义最宽松的方法"——RFC 9110 §9.3.3 把它定义为"请求目标资源按其自身语义处理请求内含的表述"，所以创建、触发计算、提交表单都常用它，但也正因如此它最不 RESTful（容易被滥用成 RPC）。选 POST 还是 PUT 建资源，取决于"谁决定新资源的 URI"：服务器决定用 POST（返回 `Location`），客户端已知目标 URI 用 PUT（见 N11.2.5）。

### N11.1.6 状态码映射

响应的"结果类别"复用 HTTP 状态码（RFC 9110 §15），API 应挑语义最贴切的那个，而不是一律 200 再在 body 里塞 `{"error": ...}`。常用映射：

```
201 Created          §15.3.2   资源创建成功（POST/PUT 建新），配 Location 头
200 OK               §15.3.1   请求成功且有响应体
204 No Content       §15.3.5   成功但无响应体（典型 DELETE、无返回的 PUT）
400 Bad Request      §15.5.1   请求语法/框架层面有误
401 Unauthorized     §15.5.2   未认证（其实是"未鉴权/身份缺失"，命名有历史误差）
403 Forbidden        §15.5.4   已认证但无权限
404 Not Found        §15.5.5   资源不存在
405 Method Not Allowed §15.5.6 该资源不支持此方法，必须带 Allow 头列出支持的方法
409 Conflict         §15.5.10  与资源当前状态冲突（如乐观锁版本不符、重复创建）
422 Unprocessable Content §15.5.21 语法正确但语义校验失败（RFC 9110 已纳入核心）
429 Too Many Requests RFC 6585 §4  触发限流（见 N11.6）
503 Service Unavailable §15.6.4   暂时不可用，可配 Retry-After
```

401 与 403。401 表示"我不知道你是谁（缺或坏的凭证）"，语义上还要求响应带 `WWW-Authenticate` 头说明该怎么认证；403 表示"我知道你是谁，但你不许做这事"。另一个坑：405 **必须**带 `Allow` 响应头（RFC 9110 §15.5.6 用 MUST），告诉客户端这个 URI 到底支持哪些方法。还要注意 422 在 RFC 9110 里已经从 WebDAV "转正"进核心 HTTP 语义（§15.5.21），可放心用于表达"字段校验没过"，无需再引 WebDAV。

### N11.1.7 Richardson 成熟度模型（教学辅助，二手）

Richardson 成熟度模型是一个衡量"一个 HTTP 接口有多 RESTful"的分级教学工具，由 Leonard Richardson 提出、经 Martin Fowler 的博客广为流传（`⚠二手`：非 RFC/论文，作为理解脚手架用，不作承重结论）。四级：Level 0，只有一个 URI、只用 POST 传动作名（纯 RPC over HTTP）；Level 1，引入"资源"多个 URI 但仍单一方法；Level 2，正确使用 HTTP 方法与状态码（绝大多数"REST API"停在这里）；Level 3，引入 HATEOAS 超媒体控制。

这个模型对初学者的价值在于：它把"RESTful 程度"从一个模糊的褒义词变成可定位的台阶，让你看清自己的接口卡在哪一级。要提醒的是，Fielding 本人曾公开强调"只有到 Level 3（超媒体驱动）才配叫 REST"，而工业界口语里的"REST API"通常只指 Level 2。这不是矛盾，是"学术严格定义"与"行业约定俗成"的用词差异，知道这层差异即可，不必纠结。

#### 来源与时效
- 锚点：Fielding 2000 论文 ch.5（§5.1 约束、§5.2 资源/表述/统一接口/HATEOAS 定义）；RFC 9110 §9.3（方法定义）、§15（状态码，含 §15.5.21 的 422）；RFC 5789（PATCH）；Kurose-Ross 8E §2.2（HTTP 方法与状态码地基）。@核实 2026-07-29。
- `⚠二手`：Richardson 成熟度模型（Fowler 博客/Richardson 提出）作教学脚手架，非承重来源。
- 无规范级冲突项。用词分歧已在 N11.1.7 点明（Fielding "REST 须 Level 3" vs 工业界 "REST API ≈ Level 2"）。

## N11.2 幂等性与安全方法

### N11.2.1 安全方法（safe methods）

RFC 9110 §9.2.1 定义：一个请求方法是**安全（safe）**的，当且仅当其定义的语义"本质上是只读的"——即客户端不指望、也不为服务器上的任何状态改变负责。规范列出的安全方法是 **GET、HEAD、OPTIONS、TRACE**。

安全不等于"服务器绝对不发生任何变化"——GET 一个页面服务器可能记一条访问日志或递增计数器，那是服务器自己的副作用，不是客户端"请求"的效果，客户端不为之负责。安全的实际意义在于：安全方法可以被爬虫、预取、浏览器自动重发而不担心"改坏东西"，缓存和代理也据此对 GET 大胆缓存。由此得一条设计红线——**绝不要用 GET 去触发有副作用的操作**（如 `GET /orders/42/delete`），否则任何一个预取爬虫都可能替你删数据。

### N11.2.2 幂等方法（idempotent methods）

RFC 9110 §9.2.2 定义：一个方法是**幂等（idempotent）**的，当且仅当"用它发出多个相同请求对服务器的预期效果，与只发一个相同请求的效果相同"。规范明确列出的幂等方法是 **PUT、DELETE，以及全部安全方法（GET/HEAD/OPTIONS/TRACE）**。**POST 不是幂等的**；PATCH 一般也不是幂等的（RFC 5789 明确 PATCH 既不安全也不保证幂等）。

理解幂等的关键是抠"效果相同"而非"响应相同"。`DELETE /orders/42` 第一次删掉资源返回 204，第二次资源已不存在——服务器"被删掉订单 42"这个**状态效果是一样的**（订单 42 处于"不存在"），所以 DELETE 幂等；至于第二次返回 204 还是 404，那是响应码不同，不影响幂等判定。同理 `PUT /orders/42`（整体替换成同一份表述）重复几次，资源终态一致，故幂等。POST `/orders`（每次新建一个）重复 3 次会造出 3 笔订单，效果随次数累积，故不幂等。幂等的实用价值：**网络超时后客户端能不能安全重试**——幂等方法可放心重发，非幂等方法（POST）重发有重复副作用风险，需靠 N11.2.4 的幂等键兜底。

### N11.2.3 各方法的安全/幂等/可缓存属性对照

把 RFC 9110 §9.2–§9.3 的属性汇总成一张表（这些属性来自规范定义，非约定俗成）：

```
方法      安全    幂等    可缓存             有请求体      RFC
────────  ──────  ──────  ────────────────  ──────────    ─────────
GET       是      是      是                否（不建议）  9110 §9.3.1
HEAD      是      是      是                否            9110 §9.3.2
OPTIONS   是      是      否                可            9110 §9.3.7
TRACE     是      是      否                否            9110 §9.3.8
PUT       否      是      否                是            9110 §9.3.4
DELETE    否      是      否                可            9110 §9.3.5
POST      否      否      仅在显式给出新鲜度时  是          9110 §9.3.3
PATCH     否      否*     否                是            RFC 5789
```

表中 POST 的可缓存性是一个特例——RFC 9110 §9.3.3 说 POST 响应默认不可缓存，只有当响应显式带出新鲜度信息（如 `Cache-Control`/`Expires`）且带 `Content-Location` 时才可缓存，实务中极少用。PATCH 的 `否*` 表示"规范不保证幂等"，但一个具体 PATCH 接口可以被设计成幂等（如整字段赋值），这时由 API 作者承诺、宜显式声明。初学者记忆钩子：安全 ⊂ 幂等（安全方法必幂等，反之不然——PUT/DELETE 幂等但不安全）。

### N11.2.4 幂等键设计（Idempotency-Key）

POST 不幂等带来一个现实痛点：客户端发了 `POST /payments` 扣款请求，网络超时没收到响应——它不知道服务器到底扣没扣，重发怕重复扣款，不重发怕漏扣。工业界的解法是**幂等键（idempotency key）**：客户端为"同一个逻辑操作"生成一个唯一键（通常是 UUID），随请求头带上（常见头名 `Idempotency-Key`，由 Stripe 等推广，`⚠二手/事实标准`：目前是 IETF draft `draft-ietf-httpapi-idempotency-key-header`，尚未成 RFC）；服务器记录"这个键处理过、结果是什么"，收到重复键就直接返回首次的结果而不重复执行。

```
POST /payments HTTP/1.1
Idempotency-Key: 3f2a9c7e-1b4d-4e8a-9f01-6c2d5b7a8e90
Content-Type: application/json

{ "amount": 4200, "currency": "usd" }
```

设计上，键要能唯一标识"一次业务意图"（同一次结账用同一个键，用户重试结账也是同一个键），服务器侧要把"键→结果"存一段时间并保证并发下的原子性（防两个同键请求同时进来都执行）。这本质上是**用应用层的键给一个天然非幂等的 POST 补上幂等语义**，是分布式系统里"至少一次投递 + 去重 = 恰好一次效果"思路在 API 层的具体落地（与 L5-01 的 exactly-once 语义同源）。

本机实证（@2026-07-29，Linux 6.18.5，Python `http.server` + `curl`）验证了 PUT/DELETE 的幂等状态效果：

```
-- PUT /items/9 两次（整体写入同一份表述）：
   1st PUT status=201     # 资源原不存在 → 创建
   2nd PUT status=200     # 已存在 → 整体替换，资源终态与第一次后完全一致
-- DELETE /items/9 两次：
   1st DELETE status=204
   2nd DELETE status=204  # 效果不变：订单 9 仍处于"不存在"
-- GET /items/9（删后）：status=404
```

这段真实输出印证了 N11.2.2 的关键点：多次 PUT/DELETE 后资源的**状态效果一致**（终态相同），响应码 201→200 的差异不影响幂等判定。

### N11.2.5 POST 建资源 vs PUT 建资源

创建资源既能用 POST 也能用 PUT，区别在"谁定新资源的 URI 以及幂等性"。POST 到集合 `POST /orders`：服务器决定新资源 URI，返回 201 + `Location: /orders/42`；因每次 POST 造一个新资源，**不幂等**（重发造两笔）。PUT 到具体 URI `PUT /orders/42`：客户端已知/指定目标 URI，"有则整体替换、无则创建"；**幂等**（重发终态一样）。

选择准则很直接：ID 由服务器生成（自增、雪花 ID）用 POST；ID 由客户端提供（如用 UUID 或业务自然键）可用 PUT 从而白拿幂等。这也解释了为什么"用 PUT 创建"在需要重试安全的场景（如移动端弱网）更受青睐——它天生幂等，不必再叠 N11.2.4 的幂等键。

#### 来源与时效
- 锚点：RFC 9110 §9.2.1（安全）、§9.2.2（幂等，明列 PUT/DELETE+安全方法幂等、POST 不幂等）、§9.3.1–§9.3.8（各方法定义与可缓存性）；RFC 5789（PATCH 非安全非幂等）。@核实 2026-07-29。
- 本机实证：Python 3.11.15 `http.server` 最小 API + `curl 8.x`，PUT/DELETE 重复观察终态不变（真实输出见 N11.2.4）。
- `⚠二手/draft`：`Idempotency-Key` 头为事实标准，对应 IETF draft `draft-ietf-httpapi-idempotency-key-header`，**尚未成 RFC**，标「⚙演进快·锚版本」。
- 无冲突项。

## N11.3 版本化与演进

### N11.3.1 破坏性变更 vs 兼容变更

API 一旦有客户端在用，就不能随意改——改动分两类。**兼容（非破坏性）变更**：老客户端不改代码仍能正常工作，典型如"新增一个可选字段""新增一个端点""在枚举里加一个老客户端会忽略的值（若客户端容忍）"。**破坏性（不兼容）变更**：会让老客户端出错，典型如"删字段/改字段名""改字段类型""把可选参数改成必填""改 URL 结构""改错误码语义"。版本化要解决的核心问题就是：**当你不得不做破坏性变更时，如何让新老客户端并存、给老客户端迁移的时间窗，而不是某天直接砸掉**。

初学者要建立的判断力是"哪些改动其实不破坏兼容、可以直接上"。加一个可选响应字段通常安全（老客户端解析 JSON 时会忽略不认识的字段）；但把一个字段从"总是出现"改成"有时不出现"就可能破坏那些没做空值判断的老客户端——所以兼容性判断要站在**最挑剔的老客户端**角度想。能不加版本就不加：多数演进应尽量做成兼容变更，版本号是留给真正躲不掉的破坏性变更的最后手段。

### N11.3.2 URL 路径版本

最常见、最直白的做法：把版本号放进 URL 路径，如 `/v1/orders`、`/v2/orders`。优点是极其直观——URL 里一眼看到版本，浏览器/curl/日志里都清清楚楚，路由和缓存也好按前缀分流。这也是 GitHub、Stripe 早期、绝大多数公开 API 的选择。

代价与争议：从 REST 纯粹主义看，`/v1/orders` 和 `/v2/orders` 指向的其实是"同一个订单资源"的两份表述，用不同 URI 标识同一资源违背了"一个资源一个标识符"的理想（这正是 N11.3.3 媒体类型版本想解决的）。但实务中路径版本的可运维性压倒了这点纯洁性，仍是主流。通常版本粒度取到大版本（`v1`/`v2`），不放次版本号进 URL——次版本的兼容演进应通过 N11.3.4 的兼容变更消化掉。

### N11.3.3 头版本与媒体类型版本

第二类做法把版本从 URL 挪到 HTTP 头，URL 保持"一个资源一个 URI"的纯净。两种常见形态：自定义头（如 `X-API-Version: 2`），或复用 HTTP 内容协商机制、把版本编进**媒体类型**（`Accept: application/vnd.example.order+json; version=2`，或 `application/vnd.example.v2+json`）。后者被认为"最 REST"，因为它把"要哪个版本"当成"要哪种表述"来协商，完全走 HTTP 既有的 `Accept`/`Content-Type` 语义。

```
GET /orders/42 HTTP/1.1
Accept: application/vnd.example.order+json; version=2
```

代价是可用性差：URL 里看不到版本，`curl`/浏览器直接访问拿到的是默认版本，调试和分享链接都不如路径版本直观，且很多缓存/网关对按 `Accept` 分版本支持不好。所以这是"理论最优、实践门槛高"的一档，采用者比路径版本少。三种方式（路径/自定义头/媒体类型）没有 RFC 强制，属工程约定，各有取舍，团队按可运维性 vs 纯洁性权衡即可。

### N11.3.4 向后兼容策略与容错读取

与其频繁升大版本，成熟 API 更依赖一组让"演进不破坏兼容"的策略。**加法式演进**：只增不减不改——新功能加新字段/新端点，老字段保留。**容错读取（tolerant reader）**：客户端解析响应时忽略不认识的字段、对缺失的可选字段有默认值，这样服务端加字段不会撑爆客户端。这正是**鲁棒性原则（Postel 定律）**"发送时严格、接收时宽容"在 API 层的体现（源出 RFC 761/1122 对 TCP 的表述，被广泛借用到 API 设计）。

配套的运维约定还有：**废弃（deprecation）而非删除**——想去掉一个字段/端点时先标记废弃、发 `Deprecation`/`Sunset` 响应头（`⚠`：`Sunset` 头是 RFC 8594，`Deprecation` 头为 IETF draft）、公告一个下线日期，给客户端迁移窗口，到期再删。核心心法一句话：**破坏性变更要么避免（用兼容演进消化），要么显式版本化 + 给迁移期**，绝不无声无息地改语义——无声的破坏性变更是线上事故的经典来源。

#### 来源与时效
- 锚点：RFC 9110 §12（内容协商，媒体类型版本的机制地基）；鲁棒性原则见 RFC 761 §2.10 / RFC 1122 §1.2.2；`Sunset` 头 RFC 8594（2019-05）。Fielding 2000 ch.5（一资源一标识的 REST 理想，作为路径版本"不纯"的判据）。@核实 2026-07-29。
- `⚠draft/约定`：三种版本化方式无 RFC 强制、属工程约定；`Deprecation` 头为 IETF draft，`Idempotency-Key`/`RateLimit` 同类。语义版本 SemVer 2.0.0 为社区规范（semver.org），非 RFC。
- 无冲突项；"路径 vs 媒体类型"之争已两边记（可运维性 vs REST 纯洁性）。

## N11.4 OpenAPI 契约与文档

### N11.4.1 契约先行与 OpenAPI 是什么

OpenAPI Specification（OAS）是一种**描述 HTTP API 的机器可读契约语言**，用 YAML 或 JSON 写，由 OpenAPI Initiative（隶属 Linux Foundation）维护；它的前身是 Swagger 规范。一份 OpenAPI 文档描述：有哪些路径（paths）、每个路径支持哪些方法、每个操作的参数/请求体/各种响应的**结构（schema）**、以及鉴权方式等——它不描述实现，只描述"这个 API 长什么样、收什么、吐什么"。

"契约先行（contract-first / design-first）"指先写 OpenAPI 契约、团队评审定稿后，前后端各自照契约独立开发；对立面是"代码先行（code-first）"——先写服务端代码再从注解生成契约。契约先行的价值在于：契约成为前后端、跨团队的**单一事实源**，前端可据它先造 mock 并行开发，后端据它生成骨架，双方都以契约为准绳而非口头约定。（注意边界：**用契约做契约测试、接进 CI 做回归属 L4-06 软工**，本节只讲契约本身描述什么。）

### N11.4.2 OpenAPI 文档结构

一份 OpenAPI 3.1 文档的顶层骨架固定为几个字段，理解它就能读懂任何 OpenAPI 文件：

```yaml
openapi: 3.1.1                 # 规范版本
info:                          # 元信息：标题、版本、描述
  title: Orders API
  version: 1.0.0
servers:                       # 基础 URL 列表
  - url: https://api.example.com/v1
paths:                         # 核心：每个路径 → 每个方法 → 一个 operation
  /orders/{id}:
    get:
      parameters:
        - name: id
          in: path
          required: true
          schema: { type: string, format: uuid }
      responses:
        '200':
          description: 订单表述
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Order' }
        '404': { description: 未找到 }
components:                    # 可复用部件：schema、参数、响应、securityScheme
  schemas:
    Order: { ... }             # 见 N11.4.3
```

`paths` 是"路径 → 方法 → 操作"的三层嵌套，每个操作声明它的 `parameters`（路径/查询/头参数）、`requestBody`、和按状态码分列的 `responses`；重复用到的结构提到 `components` 里用 `$ref` 引用（DRY）。`info.version` 是这份 API 文档自身的版本号，与 N11.3 的 API 大版本是两回事，别混。

### N11.4.3 组件 schema 与 JSON Schema 2020-12

OpenAPI 用 **schema** 描述数据结构（字段名、类型、必填、取值约束）。一个关键的版本事实：**OpenAPI 3.1 的 schema 方言就是 JSON Schema 2020-12**——3.1 版做的一件大事就是让 OpenAPI 的 schema 与 JSON Schema 标准完全对齐（3.0.x 时还是"JSON Schema 的一个改造子集"，有细微不兼容）。这意味着 3.1 的 schema 可以直接用标准 JSON Schema 校验器验证。一个订单 schema 片段：

```yaml
components:
  schemas:
    Order:
      type: object
      required: [id, status, total]
      additionalProperties: false
      properties:
        id:     { type: string, format: uuid }
        status: { type: string, enum: [pending, paid, shipped] }
        total:  { type: number, minimum: 0 }
        items:  { type: array, items: { type: string } }
```

本机实证（@2026-07-29，Python 3.11.15 + `jsonschema 4.26.0`，把上面这份 schema 当作 JSON Schema 2020-12 校验样例）：

```
valid 样例 {"id":"3f..","status":"paid","total":42.5,"items":["sku-1"]}
   → 错误: []                              # 通过

bad 样例   {"id":"3f..","status":"frozen","total":-1}
   → status : 'frozen' is not one of ['pending', 'paid', 'shipped']
   → total  : -1 is less than the minimum of 0
```

一是 OpenAPI 3.1 的组件 schema 确实能被标准 JSON Schema 2020-12 校验器（`Draft202012Validator`）直接吃下并校验；二是 `enum`/`minimum`/`required`/`additionalProperties` 这些约束是**可执行的**——契约不只是文档，它能真正在运行时校验请求/响应是否合规（很多网关/框架据此自动做输入校验，非法请求直接回 422，见 N11.1.6）。

### N11.4.4 从契约派生代码、文档与 mock

OpenAPI 的实用价值大半来自它的机器可读性带来的**工具生态**：文档站（Swagger UI / Redoc 把 YAML 渲染成可交互 API 文档）、客户端/服务端代码生成（openapi-generator 按契约生成多语言 SDK 骨架与服务端 stub）、mock 服务器（据契约的 example/schema 造假数据供前端并行开发）、请求校验中间件（运行时按 schema 校验入参出参）。

对初学者，记住一条价值主线即可：**因为契约是机器可读的，"文档、SDK、mock、校验"都能从同一份契约自动派生、且保证彼此一致**——这消除了"文档与实现不同步"这个 API 维护里最常见的烂账。至于把这些工具接进 CI、做消费者驱动契约测试，是 L4-06 软件工程的范畴，不在本课展开。OpenAPI 的当前锚版本是 3.1.1（2024-10），4.0（代号 "Moonwalk"）仍在开发，标「⚙演进快·锚版本」。

#### 来源与时效
- 锚点：OpenAPI Specification 3.1.1（OpenAPI Initiative / Linux Foundation，2024-10），尤其"OpenAPI 3.1 的 schema 方言 = JSON Schema 2020-12"这一对齐事实；JSON Schema 2020-12 core/validation。@核实 2026-07-29。
- 本机实证：`jsonschema 4.26.0` 的 `Draft202012Validator` 校验上文 Order schema，valid 样例过、bad 样例报出 enum/minimum 违规（真实输出见 N11.4.3）。
- 版本注记：OpenAPI 3.0.x（如 3.0.4）与 3.1.x 并存；3.0 schema 非严格 JSON Schema，3.1 才完全对齐。OAS 4.0 "Moonwalk" 开发中，标「⚙演进快」。
- 无冲突项。

## N11.5 认证与授权（OAuth 2.0 / JWT）

### N11.5.1 认证 vs 授权

两个常被混用的词必须先分清。**认证（authentication, authn）**回答"你是谁"——核验身份（密码、令牌、证书）。**授权（authorization, authz）**回答"你被允许做什么"——核验权限（这个用户能不能删这笔订单）。两者对应 N11.1.6 里的两个状态码：认证失败是 401，授权失败（身份已知但无权）是 403。

在 API 里，因为无状态约束（N11.1.3），**每个请求都要自带身份凭证**——服务器不记"你上一个请求登录过"。于是问题变成"用什么当凭证、凭证怎么安全地发放"。OAuth 2.0 是发放/委托授权的框架（解决"第三方 App 如何在不拿你密码的前提下代你访问资源"），JWT 是一种承载身份/权限声明的令牌格式。二者常配合但不是一回事：OAuth 是"流程/框架"，JWT 是"令牌的一种数据格式"，OAuth 的访问令牌**可以**是 JWT，也可以是不透明随机串。

### N11.5.2 OAuth 2.0 角色与端点

RFC 6749 §1.1 定义四个角色。**资源所有者（resource owner）**：能授权访问受保护资源的实体，通常是用户。**客户端（client）**：想代表资源所有者访问资源的应用（如一个第三方 App）。**授权服务器（authorization server）**：认证资源所有者、发放访问令牌的服务器。**资源服务器（resource server）**：持有受保护资源、接受访问令牌的 API 服务器。

授权服务器暴露两个核心端点（§3.1、§3.2）：**授权端点（authorization endpoint）**——用户在这里登录并同意授权；**令牌端点（token endpoint）**——客户端在这里用授权凭据换访问令牌。产出两种令牌（§1.4、§1.5）：**访问令牌（access token）**，短期、每次调 API 都带上；**刷新令牌（refresh token）**，长期、用来在访问令牌过期后换新的访问令牌而不必让用户重新登录。

### N11.5.3 授权码流（Authorization Code Grant）

授权码流是 RFC 6749 §4.1 定义的、面向"有后端的 Web/移动应用"的主力流程，也是唯一被 OAuth 2.1 推荐保留的用户授权流程。它的精髓是"先拿一个一次性的短命授权码，再在后端安全通道用它换令牌"，避免访问令牌暴露在浏览器地址栏/前端。时序：

```
用户(资源所有者)      客户端App            授权服务器           资源服务器(API)
     |                  |                     |                    |
     |   访问App，触发登录 |                     |                    |
     |----------------->|                     |                    |
     |  (A) 重定向到授权端点 response_type=code  |                    |
     |      client_id, redirect_uri, scope, state, code_challenge   |
     |<---------------- 302 --------------------|                    |
     |  (B) 用户登录并同意授权                    |                    |
     |============ 直接与授权服务器交互 =========>|                    |
     |  (C) 重定向回 redirect_uri，带 code + state                    |
     |<-------------------------------------- 302                    |
     |                  |  (D) 后端用 code 换令牌（token 端点）        |
     |                  |  grant_type=authorization_code, code,       |
     |                  |  redirect_uri, client 认证, code_verifier   |
     |                  |------------------->|                        |
     |                  |  (E) 返回 access_token (+ refresh_token)    |
     |                  |<-------------------|                        |
     |                  |  (F) 带 Authorization: Bearer <token> 调 API |
     |                  |------------------------------------------->|
     |                  |  (G) 校验令牌后返回受保护资源                 |
     |                  |<-------------------------------------------|
```

**`state`** 是客户端生成的随机值，原样回传用来防 CSRF（校验回来的 state 与发出去的一致）。**`code_challenge`/`code_verifier`** 是 **PKCE**（RFC 7636）——客户端先发 `code_challenge`（verifier 的哈希），换令牌时再出示 `code_verifier`，防止授权码在重定向中被截获后被他人拿去换令牌。PKCE 原本为无后端的移动/SPA 客户端设计，OAuth 2.1 把它扩成对**所有**客户端强制（见 N11.5.9）。

### N11.5.4 其他授权类型

RFC 6749 还定义了另外几种授权类型，各配不同场景。**客户端凭据（client credentials，§4.4）**：没有用户参与、服务对服务（machine-to-machine），客户端直接用自己的 id/secret 换令牌，代表"应用自己"而非某个用户。**资源所有者密码凭据（password，§4.3）**：客户端直接收用户名密码去换令牌——因为让客户端碰到用户密码、违背 OAuth 初衷，已被 OAuth 2.1 弃用。**隐式流（implicit，§4.2）**：早期为浏览器 SPA 设计、直接在重定向里回令牌，因安全性差也被 OAuth 2.1 弃用（改用授权码流 + PKCE）。**刷新令牌（§6）**：用 `grant_type=refresh_token` 换新访问令牌。

落到选型，有用户、有后端 → 授权码流 + PKCE；有用户、纯前端 SPA/移动 → 同样授权码流 + PKCE（不再用隐式流）；无用户、服务间调用 → 客户端凭据。password 与 implicit 认作"历史遗留、新系统别用"。

### N11.5.5 Bearer 令牌

RFC 6750 定义了访问令牌最常用的呈现方式——**Bearer 令牌**。客户端把令牌放进 `Authorization` 请求头：

```
GET /orders/42 HTTP/1.1
Host: api.example.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

"Bearer（持票人）"的含义是**谁持有这个令牌谁就能用**——令牌本身不绑定持有者身份，就像不记名车票，捡到就能坐车。这直接推出两条铁律：**Bearer 令牌必须只在 TLS 上传输**（否则被中间人截获即被冒用），且**令牌应短期有效**（缩小泄漏后的危害窗口，过期后靠刷新令牌续）。这也是 N11.5.2 把访问令牌设计成短期的原因。

### N11.5.6 JWT 结构

JWT（JSON Web Token，RFC 7519）是一种紧凑、URL 安全的令牌格式。最常见的形态是**签名的 JWT**（用 JWS，RFC 7515 封装），由三段用 `.` 连接的 **base64url**（无填充）编码串组成：

```
Header.Payload.Signature

eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9   ← Header：{"alg":"HS256","typ":"JWT"}
.eyJpc3MiOiJodHRwczovL2F1dGguZXhhbXBsZS5jb20iLCJzdWIiOiJ1c2VyLTQyIiwuLi59
                                        ← Payload：一组声明（claims）
.t6Yii0SK3X4sSykzSzwD-RTeqfBJ15Z-j13ft_e2ChQ
                                        ← Signature：对前两段的签名
```

本机实证（@2026-07-29，Python 3.11.15 `hmac`+`base64` 构造一个 HS256 JWT 再解码）：

```
HEADER : {'alg': 'HS256', 'typ': 'JWT'}
PAYLOAD: {'iss': 'https://auth.example.com', 'sub': 'user-42',
          'aud': 'api://orders', 'exp': 1924992000, 'iat': 1893456000,
          'scope': 'orders:read orders:write', 'jti': 'a1b2c3'}
SIG len (bytes): 32
```

关键直觉，也是最大初学者坑：**JWT 的前两段只是 base64url 编码，不是加密**——任何拿到令牌的人都能直接解出 header 和 payload 看到里面的声明（上面的解码就是明证）。签名（第三段，HS256 用 HMAC-SHA256 产生 32 字节 = 256 位，与实证的 `SIG len 32` 吻合）保证的是**完整性与真实性**（内容没被篡改、确由持密钥的授权服务器签发），**不是机密性**。所以绝不要把密码、密钥等敏感数据放进 JWT payload；要保密就得靠 TLS 传输或改用加密的 JWE（RFC 7516）。

### N11.5.7 JWT 注册声明

RFC 7519 §4.1 定义了一组**注册声明（registered claims）**，都是可选但语义标准化的，资源服务器据此校验令牌：

```
iss  (Issuer)     签发者——谁签发的这个令牌（授权服务器标识）
sub  (Subject)    主体——令牌代表谁（通常是用户 ID）
aud  (Audience)   受众——这个令牌打算给谁用（哪个 API/资源服务器）
exp  (Expiration) 过期时间——此刻之后令牌失效（NumericDate，秒级 Unix 时间）
nbf  (Not Before) 生效时间——此刻之前令牌无效
iat  (Issued At)  签发时间
jti  (JWT ID)     令牌唯一 ID——可用于防重放/吊销黑名单
```

资源服务器验令牌时的标准动作至少包括：验签名、查 `exp` 没过期、查 `aud` 确实是发给自己的（防"发给 A 服务的令牌被拿去调 B 服务"）、按需查 `iss` 是信任的签发者。`exp` 用的 `NumericDate` 是自 1970-01-01 UTC 起的秒数（上面实证里 `exp: 1924992000` 就是这种格式）。除注册声明外，可加**私有声明**（如上面的 `scope` 表示权限范围）承载业务信息。

### N11.5.8 JWT 安全陷阱：alg:none 与算法混淆

JWT 有几个经典、务必知道的实现陷阱。**`alg: none`**：JWS 允许一个"不签名"的算法 `none`，历史上不少库校验时会信任令牌 header 里自报的 `alg`——攻击者把 `alg` 改成 `none`、去掉签名，若库照单全收就等于绕过验签伪造任意令牌。**算法混淆（RS256↔HS256）**：把非对称算法 RS256（用私钥签、公钥验）的 header 篡改成对称 HS256，诱导服务器用"公开的 RSA 公钥"当 HMAC 密钥来验，而公钥是公开的，攻击者就能伪造签名。

防御的心法很简单，**验签时算法必须由服务器端固定或白名单化，绝不信任令牌自报的 `alg`**；`exp`/`aud` 必须校验；密钥要足够强。这些坑的根源都是"令牌内容对持有者可见可改，唯一的防线是签名验证"——所以验签环节任何松懈都是致命的。RFC 7519 与 RFC 8725（JWT 最佳实践）对此有专门告诫。

### N11.5.9 OAuth 2.1 演进（硬标 draft）

**⚙演进快·锚版本·随时变**：OAuth 2.1 是把 OAuth 2.0 十年来的安全最佳实践收敛进一份文档的努力，目前是 **IETF draft（`draft-ietf-oauth-v2-1`），截至核实日期 2026-07-29 尚未成为正式 RFC**——因此本报告一切承重结论仍以 OAuth 2.0 = RFC 6749 为准，OAuth 2.1 仅登记方向。

OAuth 2.1 相对 2.0 的主要变化（草案方向，非最终）：对**所有**客户端强制 PKCE（不再只限公共客户端）；**移除隐式流（implicit）与密码流（password grant）**；强制精确匹配 `redirect_uri`；细化刷新令牌的安全要求。对初学者，记住这句即可——OAuth 2.1 不是推翻 2.0，而是"把 2.0 里那些'官方推荐但可选'的安全做法变成强制、把已知不安全的老流程删掉"的一次整理；学习时按 2.0 打底、按 2.1 的方向去实践（授权码 + PKCE、不碰 implicit/password）最稳妥。

#### 来源与时效
- 锚点：RFC 6749 §1.1（角色）、§1.3–§1.5（授权类型/令牌）、§3.1–§3.2（端点）、§4.1（授权码流）、§4.2–§4.4/§6（其他授权类型）；RFC 6750（Bearer 令牌用法）；RFC 7519 §4.1（JWT 注册声明）、§3（结构）；RFC 7515（JWS）、RFC 7518（算法）、RFC 7636（PKCE）、RFC 8725（JWT BCP）。@核实 2026-07-29。
- 本机实证：Python 3.11.15 `hmac`/`base64` 构造 HS256 JWT 并解码，坐实"前两段仅 base64url 可明文解、HS256 签名 32 字节"（真实输出见 N11.5.6）。
- `⚙演进快·锚版本·随时变`：**OAuth 2.1 = IETF draft `draft-ietf-oauth-v2-1`，截至 2026-07-29 未成 RFC**；变更内容为草案方向、可能再变；承重仍用 RFC 6749。
- 无规范级冲突项；"OAuth 是框架 vs JWT 是格式""access token 可为 JWT 或不透明串"已在 N11.5.1 澄清。

## N11.6 分页/过滤/限流约定

### N11.6.1 偏移分页 vs 游标分页

集合资源（`GET /orders` 可能有百万条）不能一次全返，要分页。两大流派。**偏移分页（offset/limit 或 page/size）**：`GET /orders?offset=40&limit=20` 或 `?page=3&size=20`，直白、支持跳页。缺点是深翻页慢（数据库要跳过前 N 行），且翻页过程中若有新增/删除会导致"漏读或重复读"（页边界漂移）。**游标分页（cursor / keyset）**：`GET /orders?limit=20&cursor=<opaque>`，服务器返回一个不透明游标标记"上次读到哪"，下次带上它继续。优点是深翻页恒定快、对并发增删稳定；缺点是不能随机跳到第 N 页、只能顺序前进（有时也支持后退）。

落到选型，需要"跳到第 100 页"这种随机访问且数据量不大 → 偏移分页；数据量大、要稳定顺序遍历（信息流、导出）→ 游标分页。这两种都不是 RFC 规定的，属工程约定，参数名（`offset`/`page`/`cursor`/`limit`/`size`）各家不一，设计时保持团队内一致即可。

### N11.6.2 Link 头与 RFC 8288

分页的"下一页在哪"除了放进响应体，还可用标准的 **`Link` 响应头**（RFC 8288，Web Linking）表达，用 `rel` 关系标注每个链接的用途：

```
HTTP/1.1 200 OK
Link: <https://api.example.com/orders?cursor=abc&limit=20>; rel="next",
      <https://api.example.com/orders?limit=20>;            rel="first",
      <https://api.example.com/orders?cursor=xyz&limit=20>; rel="prev"
```

`rel="next"/"prev"/"first"/"last"` 是标准化的链接关系，客户端跟着 `next` 走即可翻页而无需自己拼 URL——这其实是 N11.1.4 HATEOAS 思想在分页上的落地（服务器给出下一步链接、客户端跟随）。GitHub API 就是用 `Link` 头做分页的著名例子。相比把分页链接塞进响应体的私有字段（如 `{"next": "..."}`），`Link` 头的好处是标准、且不污染业务数据结构；两种做法都常见。

### N11.6.3 过滤、排序与字段选择约定

集合资源的裁剪普遍用查询参数表达，无 RFC 强制、纯约定，常见形态：**过滤** `GET /orders?status=paid&min_total=100`（按字段值筛）；**排序** `GET /orders?sort=-created_at`（`-` 前缀表降序，或 `sort=created_at&order=desc`）；**字段选择/稀疏字段集** `GET /orders?fields=id,total`（只返回指定字段，省带宽）；**搜索** `?q=keyword`（全文/模糊）。

约定虽自由，几条经验值得守：过滤/排序参数应作用在**资源自身的字段**上、语义直观；复杂查询别硬塞进 URL（过长、难编码），可考虑专门的搜索端点或（争议地）用 POST 带查询体。要提醒初学者的坑——用 POST 传"查询条件"虽绕开了 URL 长度限制，但让"读操作"变成了不安全、不可缓存的 POST（违背 N11.2 的安全方法约定），是无奈的取舍而非首选。GraphQL 正是为解决"客户端精确指定要哪些字段/关联"这个诉求而生的另一条技术路线（非 REST，本课不展开）。

### N11.6.4 限流：429 与 Retry-After

为防滥用与保护后端，API 普遍**限流（rate limiting）**。当客户端超过配额，服务器回 **429 Too Many Requests**（RFC 6585 §4），并宜带 **`Retry-After`** 响应头告诉客户端多久后再试（RFC 9110 §10.2.3 定义，值可以是秒数或一个 HTTP 日期）：

```
HTTP/1.1 429 Too Many Requests
Retry-After: 30
Content-Type: application/json

{ "error": "rate_limit_exceeded", "message": "Try again in 30 seconds." }
```

`Retry-After` 不止用于 429——RFC 9110 §10.2.3 说它也可配 **503 Service Unavailable**（服务临时不可用时告知恢复时间）和 3xx 重定向。客户端拿到 429 + `Retry-After` 的正确行为是**按提示退避后重试**，而不是立刻猛重试（那会雪上加霜）；配合指数退避 + 抖动是工业界标准做法。要分清 429（你被限流了，是客户端触发的临时拒绝）和 503（服务端自己不可用），两者语义不同但都可带 `Retry-After`。

### N11.6.5 RateLimit 响应头（draft 硬标）

除 `Retry-After` 外，很多 API 还返回"当前配额剩多少、何时重置"的信息，让客户端主动避免撞限流。长期以来这是一组事实标准的 `X-RateLimit-*` 头（`X-RateLimit-Limit`/`X-RateLimit-Remaining`/`X-RateLimit-Reset`），各家不完全一致。

**⚙演进快·锚版本·随时变**：IETF 正在把它标准化为 `RateLimit` 与 `RateLimit-Policy` 头（`draft-ietf-httpapi-ratelimit-headers`），**截至核实日期 2026-07-29 仍是 draft、尚未成为 RFC**——所以承重的限流机制仍是"429 + `Retry-After`"这两个已成规范的部件，`RateLimit-*` 头目前按事实约定用、且字段名/格式可能随草案再变，不作承重结论。初学者只需记住：**限流的"硬"部分（429、Retry-After）有规范背书可靠用；"软"部分（剩余配额提示头）尚在标准化中、按约定用并留意演进**。

#### 来源与时效
- 锚点：RFC 6585 §4（429 Too Many Requests）；RFC 9110 §10.2.3（Retry-After，适用于 429/503/3xx）、§15.6.4（503）；RFC 8288（Web Linking / Link 头与 rel 关系）。@核实 2026-07-29。
- `⚙演进快·锚版本·随时变`：`RateLimit`/`RateLimit-Policy` 头 = IETF draft `draft-ietf-httpapi-ratelimit-headers`，**截至 2026-07-29 未成 RFC**；`X-RateLimit-*` 为事实约定。分页/过滤/排序/字段选择的参数命名均无 RFC 强制、属工程约定。
- 无规范级冲突项；"偏移 vs 游标分页""Link 头 vs 响应体分页链接""POST 传查询的取舍"均两边记明取舍。
