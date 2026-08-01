# L4-02·大主题N8 HTTP 与 Web（含 QUIC/HTTP3）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-29 ｜ 先修：N5 传输层（TCP 字节流、连接管理）、N6 拥塞控制、N7 DNS、N9 TLS（HTTP/2、HTTP/3、QUIC 都建在 TLS 之上，本篇只用不深挖）｜ 一手锚点：RFC 9110「HTTP Semantics」、RFC 9111「HTTP Caching」、RFC 9112「HTTP/1.1」、RFC 9113「HTTP/2」、RFC 9114「HTTP/3」（均 Fielding/Nottingham/Reschke 等，2022-06）、RFC 9000「QUIC」(2021-05)、RFC 6265「HTTP State Management」(2011-04)、Kurose & Ross《Computer Networking: A Top-Down Approach》8th ed.(2021) ch2、MDN Web Docs ｜ 成熟度：HTTP/1.1 语义 GA/稳定；HTTP/2、HTTP/3、QUIC、Cookie SameSite ⚙演进快·锚版本（见各章）

本报告的主线是一条贯穿始终的分账：**HTTP 的"语义"（方法、状态码、头部字段的含义，RFC 9110）与 HTTP 的"传输版本"（这些语义在网络上如何编码传输：1.1 的文本行、2 的二进制帧、3 的 QUIC 流）是两层，语义不随版本变，变的只是"怎么把同一份请求/响应搬过去"。** 一个 `GET /index.html` 请求、一个 `200 OK` 响应、一个 `Cache-Control` 头，在 HTTP/1.1、HTTP/2、HTTP/3 里含义完全相同，区别只在于线上字节的组织方式。抓住这条分账，六章的关系就清楚了：8.1 讲语义层；8.2/8.3/8.6 讲三代传输；8.4/8.5 讲两个建在语义之上的应用机制（缓存、会话状态）。

**本篇讲的 Web 缓存（8.4，RFC 9111）是"HTTP 客户端/代理如何缓存并复验单个资源表述"，它不是"分布式缓存的多副本一致性"（那属 L6-05）。** HTTP 缓存关心的是"我手里这份还新鲜吗、要不要回源复验"，不涉及多个缓存节点之间达成一致。

**本大主题是 API 设计（N11）的地基。** REST、幂等性、版本化等都直接建在 RFC 9110 的方法/状态码/头部语义之上，本篇把语义讲清，N11 只做"应用层约定"的展开。

本容器 `curl 8.5.0`（含 nghttp2）可佐证 HTTP/1.1 报文、HTTP/2 的 ALPN `h2` 协商、以及条件请求返回 304；但该 `libcurl` **未编入 HTTP/3**（`curl --http3` 直接报"the installed libcurl version doesn't support this"），故 HTTP/3/QUIC 的实机验证**未取**，8.6 结论以 RFC 9000/9114 与 Kurose 8E、MDN 的多来源比对为承重。出网经代理，部分站点的 `Set-Cookie` 被剥离，Cookie 往返实证部分未取。

---

## 8.1 HTTP 语义（RFC 9110）

### 8.1.1 语义层与传输版本的分离

RFC 9110「HTTP Semantics」(2022-06) 把 HTTP 中**与具体传输版本无关**的那部分抽出来单独成篇：方法、状态码、头部字段、资源与表述、内容协商、条件请求、范围请求、认证框架等。它明确定义了一个"HTTP 消息"的抽象模型——请求由方法 + 目标 + 头部 + 可选主体构成，响应由状态码 + 头部 + 可选主体构成——而这个抽象**如何编码到字节流**，交给 RFC 9112（HTTP/1.1）、9113（HTTP/2）、9114（HTTP/3）三份"传输版本"文档各自规定。

对初学者，最该建立的心智模型是：**RFC 9110 是"词典和语法书"，规定每个词（`GET`、`404`、`Cache-Control`）是什么意思；RFC 9112/9113/9114 是"三种不同的书写系统"，规定这些词在纸上怎么排版。** 换句话说，你在 HTTP/1.1 里学到的 `GET`、`200 OK`、`ETag` 的含义，到了 HTTP/2、HTTP/3 一字不改地成立；这也是为什么升级到 HTTP/2/3 通常不需要改应用代码——语义没变。RFC 9110 出台时一并废弃（obsolete）了旧的 RFC 7230–7235 系列，把散落在多份文档里的语义收拢到一处。

### 8.1.2 请求方法及其安全性/幂等性/可缓存性

HTTP 定义了一组**方法（method）**，表达对目标资源"想做什么"。RFC 9110 §9 正式定义的方法有：GET、HEAD、POST、PUT、DELETE、CONNECT、OPTIONS、TRACE。每个方法带三个正交属性——**安全（safe）**、**幂等（idempotent）**、**可缓存（cacheable）**：

- 安全：语义上"只读"、不改变服务器状态。安全方法：GET、HEAD、OPTIONS、TRACE。
- 幂等：同一请求发一次和发 N 次，对服务器的预期效果相同。幂等方法：GET、HEAD、OPTIONS、TRACE、PUT、DELETE（安全方法都幂等）。
- 可缓存：响应默认可被缓存。默认可缓存的是 GET、HEAD（POST 在满足显式新鲜度信息时也可缓存，但默认不）。

关键对照（RFC 9110 §9.2.1–9.2.3）：

```
方法      安全   幂等   默认可缓存
GET       是     是     是
HEAD      是     是     是
OPTIONS   是     是     否
TRACE     是     是     否
PUT       否     是     否
DELETE    否     是     否
POST      否     否     否（除非显式给新鲜度）
CONNECT   否     否     否
```

初学者最容易混"安全"和"幂等"：**安全是"完全不改状态"，幂等是"改了但重复改结果一样"。** PUT 不安全（它会写数据，改了状态），但幂等——把资源整体替换成同一份内容，替一次和替十次，最终状态一样。POST 既不安全也不幂等——它常表示"新建一条记录"，发两次就建两条。这个区别在 N11 幂等性设计里是承重概念：网络超时后能不能安全重试，取决于方法是否幂等。注意"幂等"是**语义承诺**，不是服务器强制保证——服务器实现得不对也可能违背，规范只是规定了客户端可以据此假设。

### 8.1.3 状态码：五大类与常见码

响应用一个三位**状态码（status code）**告诉客户端结果。RFC 9110 §15 按首位分五大类：

```
1xx  信息性（临时响应，如 100 Continue、101 Switching Protocols）
2xx  成功（200 OK、201 Created、204 No Content、206 Partial Content）
3xx  重定向（301 永久、302 找到、304 未修改、307/308 保持方法的重定向）
4xx  客户端错误（400 请求错、401 未认证、403 禁止、404 未找到、405 方法不允许、409 冲突、410 已删除、412 前置条件失败、416 范围不满足）
5xx  服务器错误（500 内部错误、502 网关错、503 不可用、504 网关超时）
```

对初学者，两条最实用的直觉：**首位数字就够你判断"谁的问题、大方向如何"**——2 开头成功、3 开头"还得再跑一趟"、4 开头"你（客户端）错了"、5 开头"我（服务器）崩了"。第二条：**状态码是可扩展的"注册表"，客户端应按类（class）兜底处理未知码**——遇到没见过的 `418` 也应按 `4xx` 当客户端错误处理，而不是崩溃。这条"按类处理"是 RFC 9110 明确要求的健壮性原则。注意几个易错点：`301` vs `308`——早期 301/302 在重定向时客户端常把 POST 改成 GET，`307`/`308` 是后来引入的"**保持原方法和主体**"的重定向；`401`（未认证，你还没登录）与 `403`（已认证但无权限）常被混用。

### 8.1.4 头部字段、资源与表述、内容协商

HTTP 消息在起始行之后携带若干**头部字段（header field）**，是 `字段名: 字段值` 的键值对，承载元数据。RFC 9110 把资源模型讲得很干净：一个 **URI 标识一个资源（resource）**，服务器针对请求返回该资源在某一时刻、某种格式下的一份**表述（representation）**——同一个资源可以有多份表述（HTML/JSON、中文/英文、gzip/未压缩）。描述表述的元数据头有 `Content-Type`（媒体类型，如 `text/html`）、`Content-Length`、`Content-Encoding`（如 `gzip`）、`Content-Language`。

**内容协商（content negotiation）**就是客户端用 `Accept`、`Accept-Language`、`Accept-Encoding` 等请求头表达偏好，服务器据此挑一份表述返回，并用 `Vary` 头声明"这份响应是按哪些请求头选出来的"（缓存要靠它区分变体，见 8.4）。

对初学者点破"资源 vs 表述"这层：**URL 指向的是"抽象的东西"（比如"今天的首页"），你真正拿到手的永远是它的一份具体表述（此刻、这个语言、这个格式的字节）。** 这解释了为什么同一个 URL 换个 `Accept-Language` 就返回不同语言的页面——资源没变，返回的表述变了。这也是 8.4 缓存和 N11 REST"资源/表述/无状态"的共同地基。

#### 来源与时效
- RFC 9110「HTTP Semantics」(R. Fielding, M. Nottingham, J. Reschke, 2022-06)：方法定义与安全/幂等/可缓存属性（§9）、状态码五类与各码（§15）、头部字段/表述/内容协商（§6/§8/§12）、按类处理未知状态码。承重一手。核实 2026-07-29。
- Kurose & Ross 8E ch2 §2.2「HTTP」：请求/响应报文、方法、常见状态码的教材侧独立复述，与 RFC 9110 交叉核对（教材沿用旧 RFC 7231 术语但语义一致）。核实 2026-07-29。
- MDN Web Docs「HTTP request methods」「HTTP response status codes」：作为第三方一手性文档交叉核对方法属性表与状态码含义。核实 2026-07-29。
- 分账：RFC 9110 是语义层，与传输版本（9112/9113/9114）解耦；本章是 N11 API 设计的直接地基。

## 8.2 HTTP/1.1 消息传输（RFC 9112）

### 8.2.1 报文语法：文本行 + CRLF

RFC 9112「HTTP/1.1」(2022-06) 规定了 HTTP 语义在 HTTP/1.1 里的**具体线上编码**：一种基于文本行、以 CRLF（`\r\n`）分隔的格式。一条请求报文的结构是：

```
request-line = method SP request-target SP HTTP-version CRLF
*( field-name ":" OWS field-value OWS CRLF )
CRLF                        ← 空行，头部结束
[ message-body ]
```

一个最小的具体请求（本机 `curl -v http://example.com` 抓到的真实请求行与头部）：

```
GET / HTTP/1.1
Host: example.com
User-Agent: curl/8.5.0
Accept: */*

```

响应报文对称，起始行换成**状态行**：

```
status-line = HTTP-version SP status-code SP [reason-phrase] CRLF
```

真实响应（同次 `curl -v` 抓到）：

```
HTTP/1.1 200 OK
Content-Type: text/html
Last-Modified: Tue, 21 Jul 2026 07:16:00 GMT
...

<html>...</html>
```

对初学者，HTTP/1.1 最好记的一点是**它就是纯文本，人能直接读、能手敲**——你可以用 `nc`/`telnet` 连到 80 端口，手打 `GET / HTTP/1.1` 加一个 `Host` 头加一个空行，服务器就会回你完整响应。`Host` 头在 HTTP/1.1 里是**必需**的（一台服务器一个 IP 上托管多个域名，靠 `Host` 区分是哪个站点，即"虚拟主机"）。那个"空行"（连续两个 CRLF）是头部与主体的分界，极其关键——少了它服务器就一直在等更多头部。

### 8.2.2 主体定界：Content-Length 与分块传输

收方怎么知道"主体到哪结束"？HTTP/1.1 给两种办法，二选一：

一是 **`Content-Length`**：头里直接声明主体字节数，收方读够这么多字节即完。

二是 **分块传输编码 `Transfer-Encoding: chunked`**：用在"生成时还不知道总长度"（如动态流式输出）的场景。主体被切成若干块，每块前面用十六进制写出本块字节数，最后以一个"0 长块"收尾。格式为：

```
Transfer-Encoding: chunked
CRLF
1a CRLF                     ← 本块 0x1a = 26 字节（十六进制）
<26 字节数据> CRLF
10 CRLF                     ← 下一块 0x10 = 16 字节
<16 字节数据> CRLF
0 CRLF                      ← 0 长块 = 主体结束
CRLF                        ← 可选 trailer 后的终止空行
```

对初学者，这两者的取舍就是"**发之前知不知道总长**"：静态文件知道大小，用 `Content-Length` 最简单；服务器一边算一边发（比如边查数据库边吐结果），事先不知道总长，就用 chunked 边生成边发、发完用 0 块封口。一个易错点/安全点：**同一响应里 `Content-Length` 和 `Transfer-Encoding` 不能矛盾并存**——历史上二者被前后端不一致解析导致了著名的"请求走私（request smuggling）"攻击，RFC 9112 因此对二者共存与优先级有严格规定（存在 `Transfer-Encoding: chunked` 时以它定界，忽略 `Content-Length`）。

### 8.2.3 持久连接、管线化与队头阻塞

HTTP/1.0 默认"一个请求一条 TCP 连接，用完就关"，代价高（每次都要三次握手 + 慢启动）。**HTTP/1.1 默认持久连接（persistent connection / keep-alive）**：一条 TCP 连接上可以顺序发多个请求-响应，省去反复建连。想关连接用 `Connection: close` 头显式声明。

HTTP/1.1 还允许**管线化（pipelining）**：不等前一个响应回来就把后续请求接着发出去。但规范要求响应**必须按请求顺序返回**，于是产生**队头阻塞（head-of-line blocking, HOL）**——排在前面的一个慢响应，会把它后面所有响应都堵住。因为这个毛病，管线化在实践中几乎没被浏览器采用（默认关闭）。

对初学者，这里是理解 HTTP/2 存在意义的钥匙：**HTTP/1.1 的根本瓶颈是"一条连接同一时刻只能有效地处理一个请求-响应"。** 浏览器的历史应对是"对同一域名开 6 条左右并行连接"来变相并发，但连接有限、每条都要各自握手和拥塞窗口预热，很浪费。HTTP/2（8.3）就是来根治这条"应用层队头阻塞"的——它让一条连接上多个请求真正并发。注意区分：这里的 HOL 是**HTTP/1.1 协议层**的（响应必须顺序返回造成的）；到 HTTP/2 还残留一个**TCP 层**的 HOL（见 8.3.4、8.6），二者层次不同，别混。

#### 来源与时效
- RFC 9112「HTTP/1.1」(2022-06)：请求行/状态行/头部/空行的 ABNF（§2–§3）、`Content-Length` 与 `Transfer-Encoding: chunked` 主体定界及二者冲突处理（§6）、持久连接（§9.3）。承重一手。核实 2026-07-29。
- Kurose & Ross 8E ch2 §2.2.2「Non-Persistent and Persistent Connections」：非持久 vs 持久连接、每对象一连接的代价，教材侧交叉核对。核实 2026-07-29。
- MDN「Connection management in HTTP/1.x」「Transfer-Encoding」：持久连接、管线化被弃用、chunked 编码格式的第三方一手复述。核实 2026-07-29。
- 本机实证（已取）：`curl -v http://example.com` 抓到的请求行 `GET / HTTP/1.1` + `Host`/`User-Agent`/`Accept` 头与状态行 `HTTP/1.1 200 OK`，与 RFC 9112 语法一致。
- 分账：这是 8.1 语义在 HTTP/1.1 上的编码；协议层 HOL ≠ 8.6 讲的 TCP 层 HOL。

## 8.3 HTTP/2 分帧与多路复用（RFC 9113）

> ⚙演进快·锚版本 RFC 9113(2022-06，废弃 RFC 7540)；ALPN 标识 `h2`。

### 8.3.1 从文本到二进制分帧层

RFC 9113「HTTP/2」(2022-06，废弃 2015 年的 RFC 7540) 保持 8.1 的全部语义不变，只**换掉线上编码**：不再用文本行，而是在 TCP 之上插入一个**二进制分帧层（binary framing layer）**——所有通信被切成带类型的**帧（frame）**。一个 `GET` 请求变成一个 `HEADERS` 帧（可能跟若干 `DATA` 帧），响应同理。

对初学者，"为什么要变二进制"最直接的答案是：**为了在一条连接上把多个请求-响应交错（interleave）传输而互不干扰。** 文本行的 HTTP/1.1 无法把两个响应的字节在同一连接上你一段我一段地混着发（收方无法拆分归属）；而带"这段属于哪个流"标记的定长二进制帧就能做到。代价是人不能再用肉眼读线上字节、不能再用 `nc` 手敲了——但换来了真正的多路复用。HTTP/2 几乎总在 TLS 之上运行，通过 TLS 的 **ALPN** 扩展协商出 `h2` 标识（本机 `curl -v --http2` 可见 `ALPN: server accepted h2`、`using HTTP/2`）。

### 8.3.2 帧结构与帧类型

每个帧有一个固定 9 字节的帧头，后跟负载：

```
+-----------------------------------------------+
| Length (24)                                   |   帧负载长度
+---------------+---------------+---------------+
| Type (8)      | Flags (8)     |               |   帧类型 + 标志位
+-+-------------+---------------+-------------------------------+
|R| Stream Identifier (31)                                     |   1 位保留 + 31 位流 ID
+=+=============================================================+
| Frame Payload (0...)                                       ...
+---------------------------------------------------------------+
```

RFC 9113 §6 定义的帧类型（十六进制类型码）：

```
0x00 DATA           承载消息主体
0x01 HEADERS        承载头部（开启一个流）
0x02 PRIORITY       流优先级（RFC 9113 已弃用 7540 的优先级方案）
0x03 RST_STREAM     立即终止一个流
0x04 SETTINGS       连接级配置参数
0x05 PUSH_PROMISE   服务器推送预告（实践中已被主流浏览器弃用）
0x06 PING           保活/测 RTT
0x07 GOAWAY         优雅关闭连接
0x08 WINDOW_UPDATE  流量控制窗口更新
0x09 CONTINUATION   接续过长的 HEADERS
```

`HEADERS`（开流、带头部）、`DATA`（带主体）、`SETTINGS`（连接开场时双方交换配置）。那个 **31 位的 Stream Identifier（流 ID）** 是多路复用的核心——它标记"这个帧属于哪个请求-响应流"，收方按流 ID 把交错到达的帧重新归位。规则：客户端发起的流用**奇数** ID，服务器发起的（如推送）用**偶数** ID。

### 8.3.3 流、多路复用与 HPACK 头压缩

一条 HTTP/2 连接上可以并发承载**多个流（stream）**，每个流是一次独立的请求-响应双向字节序列。多个流的帧在同一 TCP 连接上**交错传输**，互不阻塞——这就是**多路复用（multiplexing）**，它根治了 8.2.3 的 HTTP/1.1 应用层队头阻塞：一个慢响应只堵它自己的流，不再堵别的流。因此浏览器对同一域名**只需一条 HTTP/2 连接**，不必再开 6 条并行连接。

头部用 **HPACK（RFC 7541）** 压缩：维护一张双方同步的"索引表"（静态表存常见头名/值，动态表存本连接出现过的头），配合 Huffman 编码。重复出现的头（如每个请求都带的 `Host`、`User-Agent`、`Cookie`）第二次起可以只发一个索引号，大幅省流量。

对初学者，把"流"想成"在同一条水管里并行的多股独立水流，每滴水贴着编号（流 ID）"最直观。HPACK 的动机也好懂：**HTTP 头部高度重复且冗长**（一次页面加载几十个请求，每个都重复带一大堆几乎一样的头），不压缩极浪费。一个易错点：HPACK 的动态表是**连接级**共享状态，且它是为 TCP 的有序可靠交付设计的——到了 HTTP/3 因为 QUIC 流之间无序，必须换成能容忍乱序的 **QPACK**（见 8.6）。

### 8.3.4 残留的 TCP 层队头阻塞

HTTP/2 消除了 HTTP 协议层的队头阻塞，但它跑在**单条 TCP 连接**上，于是暴露出更底层的问题：**TCP 层队头阻塞**。TCP 向上提供"一条严格有序的字节流"，只要中间丢了一个 TCP 段，TCP 就必须等它重传到齐才能把后续字节交给上层——哪怕后续字节属于另一个完全无关的流。结果是：**一个丢包会卡住这条连接上所有 HTTP/2 流**。

对初学者，这是理解 HTTP/3 为什么必须换掉 TCP 的关键：**HTTP/2 把"多路复用"做到了应用层，但 TCP 只认识"一条流"，不知道上面其实跑着很多独立的流，所以它一视同仁地为了保证顺序而全体等待。** 这个矛盾在丢包率高的网络（移动网络）上尤其伤。要真正让"一个流丢包不影响别的流"，必须让传输层本身理解"流"的存在——这正是 QUIC（8.6）做的事。

#### 来源与时效
- RFC 9113「HTTP/2」(2022-06，废弃 RFC 7540)：二进制分帧层、9 字节帧头结构（§4）、帧类型码（§6）、流与多路复用（§5）、流 ID 奇偶规则、优先级方案弃用、推送。承重一手。核实 2026-07-29。⚙演进快·锚 RFC 9113。
- RFC 7541「HPACK」(2015-05)：静态/动态表 + Huffman 的头压缩。承重一手。核实 2026-07-29。
- Kurose & Ross 8E ch2 §2.2.5「HTTP/2」：分帧、多路复用消除 HOL、单连接替代多连接的教材侧交叉核对。核实 2026-07-29。
- MDN「HTTP/2」及 web.dev 相关：ALPN `h2`、server push 实践弃用、TCP 层 HOL 残留的第三方一手复述。核实 2026-07-29。
- 本机实证（已取）：`curl -v --http2 https://www.cloudflare.com` 观察到 `ALPN: curl offers h2,http/1.1` → `server accepted h2` → `using HTTP/2`，坐实 ALPN 协商与 h2 可用。
- 分账/时效：server push 与 RFC 7540 优先级方案已弃用（RFC 9113 明确），若见旧资料仍讲 push/priority 需按 9113 更新；帧格式/类型码取自 RFC 9113，不凭记忆。

## 8.4 Web 缓存与条件请求（RFC 9111）

### 8.4.1 缓存的目标与新鲜度模型

RFC 9111「HTTP Caching」(2022-06) 规定 HTTP 缓存的语义：客户端或中间代理可以**存下一份响应**，之后对同一资源的请求直接用本地副本应答，省一次回源往返。核心是**新鲜度（freshness）**模型——每份缓存副本有一段"新鲜期"，期内直接用（fresh），过期后变**陈旧（stale）**，需回源**复验（revalidate）**。

新鲜期主要由响应头 `Cache-Control: max-age=<秒>` 给出（老式的 `Expires` 给绝对时间，二者并存时 `max-age` 优先）。缓存还用 `Age` 头记录副本已存放多久。判定式（RFC 9111 §4.2）：

```
副本仍新鲜  ⟺  freshness_lifetime > current_age
```

对初学者，把缓存想成"冰箱里的剩菜贴了保质期标签"：`max-age` 是保质期，`Age` 是已经放了多久，没过期直接吃（用缓存，零网络往返），过期了不一定扔——先打个电话问店家"这份还能用吗"（复验，见 8.4.3）。这条"新鲜就直接用、陈旧才复验"是 Web 性能的重要来源。

### 8.4.2 Cache-Control 主要指令

`Cache-Control` 是缓存控制的主头，方向分请求与响应，常见响应指令（RFC 9111 §5.2）：

```
max-age=N          新鲜期 N 秒
s-maxage=N         仅对共享缓存（代理/CDN）的新鲜期，覆盖 max-age
no-cache           可存，但每次用前必须回源复验
no-store           完全不许存（敏感数据）
private            仅允许私有缓存（浏览器）存，共享缓存不得存
public             允许任何缓存存（即使通常不缓存的响应）
must-revalidate    一旦陈旧，必须复验成功才能用，不得擅自返回陈旧副本
immutable          新鲜期内保证不变，浏览器可跳过复验（扩展，RFC 8246）
```

对初学者，两个最易混的是 `no-cache` 和 `no-store`：**`no-store` 是"别存"（隐私/敏感，如银行页面）；`no-cache` 其实是"可以存，但每次用前都要问一下服务器还新鲜吗"**——名字有误导性。另一组是 `private` vs `public`：`private` 指"这份是给某个用户的（如含登录信息），只准浏览器自己存，别放到大家共享的 CDN 上"。`s-maxage` 让你给"共享缓存（CDN）"和"浏览器"设不同新鲜期，是 CDN 场景的常用旋钮。

### 8.4.3 条件请求与 304：验证器 ETag / Last-Modified

副本陈旧后不必盲目重新下载整份——用**条件请求（conditional request，语义定义在 RFC 9110 §13）**问服务器"我这份还是最新的吗"。服务器给每份表述附一个**验证器（validator）**：

- `ETag`：表述内容的一个不透明标签（如内容哈希）。分**强验证器**与**弱验证器**（弱的写作 `W/"..."`，表示"语义等价即可，字节可略有不同"）。
- `Last-Modified`：表述最后修改时间。

复验时客户端把验证器放进条件头回发：`If-None-Match: <ETag>` 或 `If-Modified-Since: <日期>`。若服务器判定没变，回 **`304 Not Modified`**（**不带主体**，只让客户端继续用本地副本）；若变了，回 `200` + 新内容。一次典型复验往返：

```
客户端 →  GET /style.css HTTP/1.1
          If-None-Match: "abc123"
          If-Modified-Since: Tue, 21 Jul 2026 07:16:00 GMT

服务器 ←  HTTP/1.1 304 Not Modified          ← 没变，无主体，省下整份下载
```

对初学者，304 的价值在于**只花一个小往返确认"没变"，就省掉重新下载整个文件的流量**——对大图片/大 JS 尤其划算。强/弱验证器的区别在于"字节级相同"还是"语义相同就算数"：范围请求（断点续传）等场景要求字节级一致，只能用强验证器。注意 `ETag` 优先于 `Last-Modified`（前者更精确，后者只有秒级精度、且时钟可能不准）。

本机实证（已取）：对 `http://example.com` 先取 `Last-Modified`，再带 `If-Modified-Since` 复验，服务器真实返回 `304`——

```
If-Modified-Since: Tue, 21 Jul 2026 07:16:00 GMT
→ HTTP/1.1 304 Not Modified
```

### 8.4.4 共享缓存、代理与 Vary

缓存分**私有缓存（private cache，浏览器自己的）**与**共享缓存（shared cache，多用户共用，如正向代理、CDN 反向代理）**。共享缓存能让一份热门资源服务大量用户、极大减轻源站压力，但必须尊重 `private`/`no-store` 等指令，绝不能把某用户的私有响应发给别人。

`Vary` 响应头解决"同一 URL 有多份变体"的缓存难题（呼应 8.1.4 内容协商）：`Vary: Accept-Encoding` 告诉缓存"这份响应是按 `Accept-Encoding` 选出来的，gzip 版和未压缩版要分开缓存、按请求头匹配"。

对初学者，这里要立起本篇第二条防重红线：**HTTP 缓存讲的是"单个客户端/代理手里这份副本还新不新鲜、要不要回源复验"，它不是"多个缓存节点之间保持数据一致"。** 后者（分布式缓存一致性、写传播、失效广播）属于 L6-05 分布式系统，机制与目标完全不同。HTTP 缓存里根本没有"多副本互相同步"这一步——每个缓存各自独立地按新鲜期和验证器决策，源站是唯一权威。

#### 来源与时效
- RFC 9111「HTTP Caching」(2022-06)：新鲜度/陈旧模型与 `freshness_lifetime > current_age` 判定（§4.2）、`Cache-Control` 指令（§5.2）、`Age`（§5.1）、共享 vs 私有缓存、`Vary`。承重一手。核实 2026-07-29。
- RFC 9110「HTTP Semantics」§13「Conditional Requests」+ §8.8「Validator Fields」：`ETag`/`Last-Modified`、强/弱验证器、`If-None-Match`/`If-Modified-Since`、`304` 语义（条件请求语义在 9110 而非 9111）。承重一手。核实 2026-07-29。
- Kurose & Ross 8E ch2 §2.2.4「Web Caching」+「The Conditional GET」：Web 缓存与条件 GET/304 的教材侧交叉核对。核实 2026-07-29。
- MDN「HTTP caching」「Cache-Control」「ETag」：指令语义与 304 流程的第三方一手复述。核实 2026-07-29。
- 本机实证（已取）：`curl -H "If-Modified-Since: <Last-Modified>" http://example.com` 真实返回 `304 Not Modified`，坐实条件请求/复验。
- 分账（防重红线）：HTTP 缓存（本副本新鲜度/复验）≠ 分布式缓存多副本一致性（→L6-05）。

## 8.5 Cookie 与会话状态

> ⚙演进快·锚版本：承重用 RFC 6265(2011)；`SameSite` 等属性来自 RFC 6265bis（仍 IETF draft，浏览器已广泛部署，随时变）。

### 8.5.1 无状态 HTTP 之上承载会话状态

HTTP 本身是**无状态（stateless）**的——服务器不要求"记住"两次请求之间的关联，每个请求独立处理。但真实应用（登录态、购物车）需要跨多次请求的**会话状态（session state）**。**Cookie（RFC 6265「HTTP State Management Mechanism」, 2011-04）** 就是补上这个能力的机制：服务器用响应头 `Set-Cookie` 发一小段数据给客户端存着，客户端此后对该站点的每个请求都用 `Cookie` 请求头把它带回来。

一次典型往返：

```
服务器 →  HTTP/1.1 200 OK
          Set-Cookie: sessionId=abc123; Path=/; HttpOnly; Secure

客户端 →  GET /account HTTP/1.1
          Cookie: sessionId=abc123        ← 后续每个请求自动带回
```

对初学者，关键心智模型是：**HTTP 无状态是设计选择（服务器不必为每个客户端维护连接状态，易扩展）；Cookie 是在"无状态的协议"上贴一个"客户端替服务器保管的记忆标签"来模拟有状态。** 常见做法是 `Set-Cookie` 里只放一个不透明的 **session id**，真正的会话数据存在服务器端（内存/数据库），id 只是查这份数据的钥匙——这样敏感数据不落到客户端。这与"把数据本身塞进 cookie/令牌"（如 JWT，见 N11）是两条路线，各有取舍。

### 8.5.2 Cookie 属性：作用域、生命周期与安全

`Set-Cookie` 可带若干属性控制其行为（RFC 6265 §4.1 / §5）：

```
Expires=<日期>   绝对过期时间
Max-Age=<秒>     相对过期时间（优先于 Expires）
Domain=<域>      作用域到哪个域（含子域）
Path=<路径>      作用域到哪个路径前缀
Secure           仅在 HTTPS 连接上回发
HttpOnly         禁止 JavaScript（document.cookie）读取
SameSite=Strict/Lax/None   限制跨站请求是否带上（防 CSRF）
```

无 `Expires`/`Max-Age` 的是**会话 cookie**（关浏览器即失效）；带了的是**持久 cookie**。

对初学者，`Secure` 与 `HttpOnly` 是两条基本安全护栏：**`HttpOnly` 让页面里的 JS 读不到 cookie（缓解 XSS 偷 cookie），`Secure` 让 cookie 只在加密连接上发（防明文网络里被嗅探）**。二者常一起用在 session cookie 上。`Domain`/`Path` 决定"这枚 cookie 会被带给哪些请求"，配错会导致 cookie 发不出去或泄漏到不该去的路径。

### 8.5.3 SameSite 与 CSRF（演进项，标注状态）

`SameSite` 属性控制"跨站（third-party）请求要不要带上这枚 cookie"，是防 **CSRF（跨站请求伪造）** 的重要手段：`Strict` 完全不跨站带；`Lax` 允许顶层导航（点链接跳过去）带、但跨站的子资源/表单 POST 不带；`None` 一律带（但要求必须同时置 `Secure`）。

时效标注（硬标）：**`SameSite` 及现代 cookie 的一批细化规则并不在 2011 年的 RFC 6265 正文里，而在其修订草案 `draft-ietf-httpbis-rfc6265bis` 中——该草案截至核实日仍是 IETF draft、未成正式 RFC**，但主流浏览器早已实现，且多数浏览器把**未显式声明 `SameSite` 的 cookie 默认按 `Lax` 处理**。这是一个"规范未定型但事实已成标准"的典型，`⚙演进快·随时变`：具体默认值/边界以当时浏览器实现和最新 6265bis 草案为准，不凭记忆当定论。

对初学者，CSRF 的直觉是：**你登录了银行、cookie 存着登录态；此时一个恶意网站偷偷让你的浏览器向银行发一个转账请求，浏览器会"忠实地"带上你的银行 cookie——请求就以你的身份成立了。** `SameSite=Lax/Strict` 让"从别的站点发起的请求"不带这枚 cookie，从而堵住这条攻击路径。

本机 `curl -c/-b` 可做 cookie 往返，但出网经代理，测试站点（如 github.com）的 `Set-Cookie` 在本次抓取中被剥离/未出现，故 Cookie 端到端往返的实机验证**未取**，本章以 RFC 6265 与 MDN 交叉核对为承重。

#### 来源与时效
- RFC 6265「HTTP State Management Mechanism」(A. Barth, 2011-04)：`Set-Cookie`/`Cookie` 语法、`Expires`/`Max-Age`/`Domain`/`Path`/`Secure`/`HttpOnly` 属性、会话 vs 持久 cookie。承重一手。核实 2026-07-29。
- `draft-ietf-httpbis-rfc6265bis`（IETF HTTP WG，草案）：`SameSite`、默认 Lax、`Secure` 前缀等现代规则。**仍为 draft、未成 RFC**，`⚙演进快·随时变`。核实 2026-07-29。
- Kurose & Ross 8E ch2 §2.2.3「User-Server Interaction: Cookies」：无状态 HTTP + cookie 承载会话状态的教材侧交叉核对。核实 2026-07-29。
- MDN「Using HTTP cookies」「Set-Cookie」「SameSite」：属性语义、浏览器默认 Lax、CSRF 关联的第三方一手复述。核实 2026-07-29。
- 实机 Cookie 往返（`curl -c/-b`）：因代理剥离 `Set-Cookie`，本次**未取**，如实标注。

## 8.6 QUIC 与 HTTP/3（RFC 9000/9114）

> ⚙演进快·锚版本 QUIC=RFC 9000(2021-05)、HTTP/3=RFC 9114(2022-06)；ALPN 标识 `h3`。本环境 `libcurl` 未编入 HTTP/3，实机验证未取。

### 8.6.1 QUIC：UDP 之上的多路复用安全传输

RFC 9000「QUIC: A UDP-Based Multiplexed and Secure Transport」(2021-05) 是一套**运行在 UDP 之上的新传输协议**，用来替代 TCP 承载 HTTP/3。它把过去分散在 TCP + TLS 的功能重新打包：**可靠有序交付、拥塞控制、内建 TLS 1.3 加密（RFC 9001）、以及原生的"多流"能力**，全做在用户态的 QUIC 里。配套还有 RFC 9002（丢包检测与拥塞控制）。

对初学者，最该先破的一个疑惑是"**UDP 不是不可靠吗，怎么拿来做可靠传输**"：QUIC 只是**借 UDP 作为穿过网络的信封**（因为 UDP 简单、且中间设备普遍放行），真正的可靠性（序号、确认、重传、拥塞控制）由 QUIC 自己在 UDP 负载里重新实现了一遍。选 UDP 而不改 TCP，是因为 TCP 深植于操作系统内核和大量中间盒（防火墙/NAT），演进极慢；把新传输做在用户态 UDP 上，浏览器和服务器自己就能快速迭代升级。

### 8.6.2 消解队头阻塞：QUIC 理解"流"

QUIC 原生支持**多个独立的流（stream）**，且**每个流各自保证有序、流之间互相独立**。这正是 8.3.4 遗留问题的解药：因为 QUIC 传输层自己知道有多个流，**一个数据包丢失只影响它所属的那个流，其他流照常交付**——不再有 HTTP/2 那种"一个 TCP 丢包卡住全部流"的 TCP 层队头阻塞。

对初学者，对比着记最清楚：**HTTP/2 = 多路复用做在应用层，但底下的 TCP 只认识"一条流"，所以丢包时 TCP 为保序让所有流陪等；HTTP/3/QUIC = 把"多流"下沉到传输层，传输层自己就能只让丢包的那一条流等，别的流不受连累。** 这是 HTTP/3 相对 HTTP/2 最本质的性能改进，在丢包率高的移动网络上收益最大。

### 8.6.3 更快的握手：1-RTT 与 0-RTT

QUIC 把"建立传输连接"和"TLS 加密握手"**合并**成一次交换：首次连接**只需 1 个 RTT**就能开始发应用数据（对比 TCP 三次握手 1-RTT + TLS 1.3 握手 1-RTT，通常合计 ~2-RTT）。对之前连过的服务器，还可用 **0-RTT** 恢复——在第一个数据包里就捎带应用数据，理论上零往返起步。握手示意：

```
首次（1-RTT）：
  客户端 → Initial(含 TLS ClientHello, ALPN=h3)
  服务器 → Initial/Handshake(含 ServerHello、证书…)
  客户端 → 完成握手 + 首个应用请求        ← 约 1 个 RTT 后即可发数据

恢复（0-RTT）：
  客户端 → Initial + 0-RTT 应用数据(用缓存的密钥直接加密)  ← 0 往返起步
```

对初学者，"少一个 RTT"听起来小，但对跨洲高时延链路（一个 RTT 可能上百毫秒）、以及一次页面动辄几十个连接的 Web 而言，累计节省相当可观。0-RTT 的代价是有**重放攻击**风险（攻击者截获并重发那个 0-RTT 包），所以规范规定 0-RTT 只应用于安全/幂等的请求（呼应 8.1.2），不能用于会改状态的操作。

### 8.6.4 连接迁移与 HTTP/3 的映射

QUIC 用一个**连接标识符（Connection ID）**来认连接，而不是像 TCP 那样用"源 IP+端口/目的 IP+端口"四元组。好处是**连接迁移（connection migration）**：手机从 WiFi 切到蜂窝网络、IP 变了，TCP 连接会断，而 QUIC 凭 Connection ID 仍认得是同一条连接，**无缝续上**、不用重连重握手。

RFC 9114「HTTP/3」(2022-06) 则是把 8.1 的 HTTP 语义**映射到 QUIC 之上**：请求/响应用 HTTP/3 的帧（DATA、HEADERS 等）承载在 QUIC 流上；头压缩换成 **QPACK（RFC 9204）**——它是 HPACK 的"抗乱序"改版，因为 QUIC 流之间无序到达，HPACK 那种严格依赖顺序的动态表会出错。HTTP/3 的 ALPN 标识是 `h3`。

一个部署细节（易错点）：**HTTP/3 不能像 HTTP/2 那样在首个 TCP 连接上用 ALPN 直接协商出来**——因为它压根不用 TCP。客户端通常先用 HTTP/1.1 或 HTTP/2 连上，服务器用 **`Alt-Svc` 响应头**（或 DNS 的 HTTPS 记录）告知"我也支持 h3，在某 UDP 端口"，客户端下次才改走 HTTP/3。

实机说明（未取）：本容器 `curl 8.5.0` 的 `libcurl` **未编入 HTTP/3**，执行 `curl --http3` 直接报 `the installed libcurl version doesn't support this`；`curl --version` 的 Protocols 列表也不含 `h3`。因此 QUIC 握手、连接迁移、HTTP/3 帧等**实机验证均未取**，本章结论以 RFC 9000/9114 与 Kurose 8E、MDN 的多来源比对为承重，如实标注"基线环境上不可用"。

#### 来源与时效
- RFC 9000「QUIC: A UDP-Based Multiplexed and Secure Transport」(2021-05)：UDP 承载、多流独立性、Connection ID 与连接迁移、握手/0-RTT。承重一手。核实 2026-07-29。⚙演进快·锚 RFC 9000。
- RFC 9001「Using TLS to Secure QUIC」/ RFC 9002「QUIC Loss Detection and Congestion Control」(均 2021-05)：QUIC 内建 TLS 1.3 与拥塞控制，交叉印证"TCP+TLS 功能重打包"。核实 2026-07-29。
- RFC 9114「HTTP/3」(2022-06) + RFC 9204「QPACK」：HTTP 语义到 QUIC 的映射、`h3`、`Alt-Svc` 发现、QPACK 抗乱序头压缩。承重一手。核实 2026-07-29。⚙演进快·锚 RFC 9114。
- Kurose & Ross 8E ch2 §2.2.5/§3「HTTP/2, HTTP/3 and QUIC」：QUIC 消除 TCP 层 HOL、合并握手、连接迁移的教材侧交叉核对。核实 2026-07-29。
- MDN「HTTP/3」「Evolution of HTTP」：`h3`、`Alt-Svc`、0-RTT 重放风险的第三方一手复述。核实 2026-07-29。
- 本机实证（未取）：`curl --http3` 报 libcurl 不支持、`curl --version` Protocols 不含 `h3`，坐实"本基线环境无 HTTP/3"，QUIC/HTTP3 实机验证如实留白。
- 分账：QUIC 是传输层新协议（对标 TCP+TLS），HTTP/3 是把 8.1 语义映射其上；TCP 层 HOL（8.3.4）在此被消解。
