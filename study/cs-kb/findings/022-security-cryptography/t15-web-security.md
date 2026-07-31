# L5-04·大主题15 Web 安全

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：软 L4-02（HTTP：请求/响应、方法、头部、URL 结构）、软 L4-03（数据库：SQL 与查询执行）｜ 一手锚点：UC Berkeley CS161 (Fall 2025) Web Security 系列讲义 https://fa25.cs161.org/ ；Stanford CS155 Web Security 讲义 https://cs155.stanford.edu/syllabus.html ；OWASP Top 10:2025（最终版，2026 年初发布，取代 2021）https://owasp.org/Top10/2025/ ；MDN Web Docs（Same-origin policy / Set-Cookie / CSP / CORS）https://developer.mozilla.org/ ；IETF RFC 6265（HTTP State Management，2011）＋ RFC 6265bis（SameSite 与 Cookie 前缀，IETF 草案，⚙演进中）；MITRE CWE https://cwe.mitre.org/ ｜ 成熟度：多数为 GA/稳定的经典 Web 安全机制；Cookie 的 SameSite 默认行为、CSP 推荐姿势、OWASP 分类硬标 `⚙演进快·锚版本`（见各节）。

> 粒度判定：1 份，不拆。本大主题 6 个小主题（15.1–15.6）共用一条主线——浏览器把不同来源的内容放进同一个用户会话里，攻击面就来自"信任边界被跨越"：SOP（15.1）划边界，Cookie/会话（15.2）承载被冒用的凭据，XSS（15.3）在受害者源内注入脚本、CSRF（15.4）借用受害者凭据发请求、注入（15.5）把数据当代码，OWASP Top 10:2025（15.6）是这些风险在业界的权威谱系。机制同族、篇幅适中，按 v3 默认 1 大主题 = 1 报告。

浏览器是一个同时运行"你信任的网站"和"你不信任的网站"的多租户执行环境，安全几乎全部围绕一个问题——**谁的代码/请求，能以谁的身份，访问谁的数据**。SOP 是隔离的地基，Cookie 是身份的载体，XSS/CSRF/注入是三类最经典的"越界"，OWASP Top 10 则是把这些越界按真实世界频率与影响排序的清单。

15.5 的 SQL 注入在本机用 Python 内置 `sqlite3` 做了教学级实证（仅本地内存库、无真实攻击目标），最小脚本与真实输出贴入 15.5.3。其余攻防仅概念讲解，遵循"实证不替代多来源比对"。

---

## 15.1 同源策略与浏览器模型

### 15.1.1 源（origin）的定义

一个"源"（origin）由三元组唯一确定：协议（scheme）、主机（host）、端口（port）三者全部相同，才算同源。任何一个不同，就是不同的源。

origin = (scheme, host, port)

同源示例与非同源示例：

https://example.com/a   与  https://example.com/b        → 同源（路径不参与判定）

https://example.com     与  http://example.com          → 不同源（scheme 不同）

https://example.com     与  https://api.example.com     → 不同源（host 不同，子域也算）

https://example.com     与  https://example.com:8443    → 不同源（port 不同）

其一，路径（`/a` `/b`）和查询串**不参与**同源判定，只有 scheme/host/port 三样算数；其二，`http` 和 `https` 是两个源，哪怕主机名一模一样；其三，`example.com` 和它的子域 `api.example.com` 是**不同源**——"同一家公司"在浏览器眼里不等于"同一个源"。这个三元组的边界就是浏览器隔离的最小单位。

### 15.1.2 同源策略保护什么

同源策略（Same-Origin Policy, SOP）是浏览器内置的默认安全策略：一个源里的脚本，默认**不能读取**另一个源的响应内容、DOM、Cookie 等敏感数据。它的目的，是防止你打开的恶意网站 A 用脚本去读取你同时登录着的网站 B（如网银）里的数据。

没有 SOP 会发生什么，是理解它的最好方式。设想没有 SOP：你在一个标签页登录了网银 `bank.com`，另一个标签页打开了 `evil.com`；`evil.com` 的 JavaScript 就能发起对 `bank.com` 的请求、并**读取返回的账户页面 HTML**（因为你的浏览器会自动带上 `bank.com` 的登录 Cookie）。SOP 正是切断了"读取跨源响应"这一步：请求也许照发（见 15.1.4），但脚本拿不到响应内容。

要区分两个概念：SOP 限制的是**脚本对跨源资源的编程式读取**（`fetch` 的响应体、`iframe` 里另一个源的 DOM、`canvas` 上跨源图片的像素等），而不是"能不能发请求"或"能不能嵌入"。这个区别是理解 15.1.4 与 15.4（CSRF）的关键。

### 15.1.3 site 与 origin 的区别

除了 origin，还有一个更宽的边界叫 "site"，它按"可注册域名"（eTLD+1，即公共后缀再加一级）划分，忽略 scheme 之外的子域和端口差异。

origin = scheme + host + port（严格，子域算不同）

site（eTLD+1）：可注册域名，如 example.com（a.example.com 与 b.example.com 属同一 site）

举一个例子，`https://a.example.com` 与 `https://b.example.com` 是**不同 origin**，但属于**同一 site**（都归 `example.com`）。而 `https://example.com` 与 `https://example.co.uk` 是不同 site（`co.uk` 是公共后缀，eTLD+1 分别是 `example.com` 与 `example.co.uk`）。

为什么要引入 site 这个更粗的粒度？因为有些安全决策以 site 而非 origin 为界，最重要的就是 Cookie 的 `SameSite` 属性（15.2.5）——它判断的是"同不同 site"，不是"同不同 origin"。初学者常把两者混为一谈，导致误判"子域之间算不算跨站"：跨子域算跨 origin，但**不算**跨 site。

### 15.1.4 SOP 的边界：嵌入允许、读取禁止

SOP 有一条贯穿始终的规则：**跨源"嵌入/使用"资源通常被允许，跨源"读取"内容通常被禁止**。这条规则解释了大量看似矛盾的现象。

默认允许的跨源"写/嵌入"操作：

<img src="跨源图片">          浏览器会加载并显示，但脚本读不到像素

<script src="跨源脚本">      会加载并执行（这也是 CDN 能工作的原因）

<link href="跨源CSS">         会加载并应用

<form action="跨源URL">       表单可以向跨源地址提交（CSRF 的根源，见 15.4）

默认禁止的跨源"读取"操作：

fetch()/XMLHttpRequest 读取跨源响应体   被 SOP 拦（除非 CORS 放行，见 15.1.5）

读取跨源 iframe 的 document / DOM        被拦

读取被 CORS 污染的 <canvas> 像素          被拦

因为"嵌入允许"，一个恶意页面**可以让你的浏览器带着 Cookie 向 `bank.com` 发一个转账 `POST` 请求**（表单提交不受 SOP 拦），这正是 CSRF；但它**读不到** `bank.com` 的响应，这正是 SOP 挡住的那一半。理解这一半开一半关，就理解了 Web 攻防的基本盘。

### 15.1.5 CORS：显式放宽同源策略

跨源资源共享（Cross-Origin Resource Sharing, CORS）是服务器用 HTTP 响应头显式告诉浏览器"我允许哪些源读取我的响应"的机制。它是对 SOP 的**受控放宽**，而不是绕过。

典型的放行响应头：

Access-Control-Allow-Origin: https://app.example.com

Access-Control-Allow-Credentials: true    （允许携带 Cookie 时必需）

Access-Control-Allow-Methods: GET, POST

对于会改变状态或带特殊头的请求，浏览器会先发一个 `OPTIONS` 预检（preflight）请求，问服务器"我接下来这个真实请求你允不允许"，服务器答应了才发真实请求。

初学者常见的三个误解要澄清。其一，CORS 是**放宽读取限制的机制，不是安全防护**——`Access-Control-Allow-Origin: *` 是"允许任何源读我的响应"，用在带凭据的敏感接口上是配置错误。其二，CORS 由**浏览器**执行；命令行 `curl` 不受 CORS 约束（它不是浏览器）。其三，当 `Allow-Credentials: true` 时，`Allow-Origin` **不能**用通配符 `*`，必须回显具体源，这是规范强制的。

### 15.1.6 浏览器的进程隔离与站点隔离

现代浏览器不只靠 SOP 这一道逻辑边界，还引入了进程级隔离。站点隔离（Site Isolation）把不同 site 的页面放进**不同的操作系统进程**，即使某个渲染进程被攻破，也难以直接读取另一个 site 的内存。渲染器还运行在受限沙箱里，不能随意访问文件系统或发起任意系统调用。

对初学者而言，可以把它理解为"纵深防御"：SOP 是软件层的逻辑规则，一旦渲染器本身被内存漏洞攻破（见大主题12/13），逻辑规则可能失效；进程隔离与沙箱则是在硬件/OS 层再加一道墙，把攻破一个源的后果**限制**在那个进程里。这也是为什么 Spectre 类瞬态执行攻击（大主题14）促使浏览器全面推进站点隔离——纯逻辑的 SOP 挡不住跨进程的微架构侧信道读取。

#### 来源与时效
- MDN Web Docs《Same-origin policy》与《Cross-Origin Resource Sharing (CORS)》https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy （origin 三元组、嵌入允许/读取禁止的非对称、CORS 头）。核实 2026-07-30。
- WHATWG HTML/Fetch 标准对 origin 与 site（eTLD+1）的定义；Public Suffix List https://publicsuffix.org/ （site 判定依据）。核实 2026-07-30。
- UC Berkeley CS161 (Fall 2025) Web Security（SOP 讲义）https://fa25.cs161.org/ ；Stanford CS155 Web Security 讲义 https://cs155.stanford.edu/syllabus.html （SOP 保护目标、CSRF 与 SOP 关系）。核实 2026-07-30。
- 站点隔离机制细节属浏览器实现，随版本演进，标 `⚙演进快`；本节仅作概念说明，不给具体进程数/内部默认值。

---

## 15.2 会话与 Cookie

### 15.2.1 HTTP 无状态与 Cookie 会话

HTTP 本身是无状态的：服务器默认不"记得"上一个请求是谁发的。Cookie 是最常用的补救机制——服务器用 `Set-Cookie` 响应头下发一小段数据，浏览器保存后，在后续每个匹配的请求里自动通过 `Cookie` 请求头带回，服务器据此认出"这是同一个会话"。

服务器下发与浏览器回带：

Set-Cookie: sessionid=abc123; ...        （服务器响应头，下发）

Cookie: sessionid=abc123                  （浏览器后续请求头，自动带回）

登录会话的典型做法是"会话标识符"（session ID）：用户登录成功后，服务器生成一个**不可预测的随机字符串**存进 Cookie，同时在服务端保存"这个 ID 对应哪个用户"。之后每个请求带上这个 ID，服务器查表就知道是谁。关键点是 Cookie 里存的通常**只是一个引用（ID）**，不是用户名密码；谁偷到这个 ID，谁就能冒充这个会话——这正是 15.2.3–15.2.6 那些属性要保护的东西。

### 15.2.2 Cookie 的作用域与生命周期属性

`Set-Cookie` 可以携带若干属性，控制这个 Cookie 发给谁、活多久：

Domain=example.com    发给该域及其子域（不设则仅当前主机，更严格）

Path=/app             仅匹配该路径前缀的请求才带

Expires=<日期>        绝对过期时间

Max-Age=<秒>          相对存活秒数（优先于 Expires）

不设 Expires/Max-Age → 会话 Cookie，关浏览器即失效

初学者要注意两个反直觉点。其一，**不设 `Domain` 反而更安全**：不设时 Cookie 只发给下发它的那个确切主机，设了 `Domain=example.com` 则会扩散到所有子域，攻击面更大。其二，`Domain`/`Path` 是**作用域限制，不是安全边界**——它们决定"带不带"，但一个能读到 Cookie 的脚本（无 `HttpOnly` 时）不受 `Path` 阻拦。真正的安全属性是下面几个。

### 15.2.3 Secure 属性

`Secure` 属性要求该 Cookie **只能通过 HTTPS 加密连接发送**（`localhost` 除外），不会在明文 HTTP 请求里带出。

Set-Cookie: sessionid=abc123; Secure

它防的是"网络中间人窃取会话 Cookie"：没有 `Secure` 时，只要攻击者能诱导浏览器发一个 `http://` 请求（例如页面里一个混入的 `http` 资源），会话 Cookie 就会明文暴露在网络上被嗅探。初学者要记住 `Secure` 只管"传输时走不走 HTTPS"，它**不加密 Cookie 内容**、也不阻止 JavaScript 读取（那是 `HttpOnly` 的职责）。

### 15.2.4 HttpOnly 属性

`HttpOnly` 属性禁止 JavaScript 通过 `document.cookie` 读取该 Cookie，它只在 HTTP 请求中被浏览器自动携带。

Set-Cookie: sessionid=abc123; HttpOnly

它的核心价值是**缓解 XSS 窃取会话**：即使页面被注入了恶意脚本（15.3），脚本也读不到带 `HttpOnly` 的 session Cookie，无法把它偷传到攻击者服务器。要澄清两点：`HttpOnly` 不能**阻止** XSS 发生，只是让"偷 Cookie"这一条变现路径失效（攻击者仍可用 XSS 直接以受害者身份发请求）；而且带 `HttpOnly` 的 Cookie 在 `fetch()`/`XMLHttpRequest` 发出的请求里**照样会被浏览器自动带上**——被禁的只是脚本的"读取"，不是"随请求发送"。

### 15.2.5 SameSite 属性

`SameSite` 属性控制 Cookie 在**跨站请求**中是否被携带，是防御 CSRF（15.4）的主力之一。它有三个取值：

SameSite=Strict   仅同站请求带 Cookie；任何跨站请求（包括从外站点击链接跳转）都不带

SameSite=Lax      同站请求带；跨站请求中只有"顶层导航 + 安全方法（GET）"才带，跨站 POST / fetch / img / iframe 不带

SameSite=None      同站与跨站都带；但必须同时加 Secure，否则被拒

这里的"站"按 site（eTLD+1，见 15.1.3）判定，不是 origin。`Strict` 最安全但体验受损（从搜索引擎点进来会显示未登录，因为跨站导航不带 Cookie）；`Lax` 是安全与可用性的折中——它挡住了 CSRF 最常见的跨站表单 `POST`，又允许普通的跨站点击导航保持登录态。

关于默认值必须谨慎，硬标 `⚙演进快·锚版本`：MDN 的表述是"部分浏览器在未指定 `SameSite` 时默认采用 `Lax`"，且这种"默认 Lax"是一个更宽松的变体（对**设置后两分钟内**的 Cookie 仍允许跨站 `POST` 带上，作为兼容缓冲）。不同浏览器/版本的默认行为并不完全一致，因此**不应依赖默认**，安全关键的 Cookie 要显式声明 `SameSite`。SameSite 语义定义在 RFC 6265bis（仍是 IETF 草案），不在 2011 年的 RFC 6265 里。

### 15.2.6 __Host- 与 __Secure- Cookie 名前缀

Cookie 名字以特定前缀开头时，浏览器会强制要求配套属性，作为一种防篡改保证：

__Secure- 前缀   必须由 HTTPS 页面设置且带 Secure

__Host-  前缀    必须带 Secure、必须不设 Domain、且 Path 必须为 /（保证只发回下发它的那台主机）

Set-Cookie: __Host-sessionid=abc123; Secure; Path=/

它解决的问题是"子域/明文页面污染主域 Cookie"：没有前缀时，`http://sub.example.com` 或一个被攻破的子域可能设置一个作用于父域的同名 Cookie 覆盖你的会话（cookie tossing/fixation）。`__Host-` 前缀让浏览器强制这个 Cookie **绑死在单台主机、不带 Domain**，从机制上杜绝这类越权设置。初学者可以把前缀理解为"写进 Cookie 名字里、由浏览器强制执行的完整性约束"。

### 15.2.7 会话管理的最佳实践

安全的会话管理由若干条共同构成，而不是单一开关。会话标识符必须是**足够长的密码学随机数**（不可预测、不可枚举）；登录成功后应**重新生成**会话 ID（防会话固定，见大主题16的 CWE-384）；会话要有**空闲超时与绝对超时**并在登出时**服务端失效**；承载会话的 Cookie 应同时带上 `Secure`、`HttpOnly`、`SameSite`（并酌情用 `__Host-` 前缀）。

综合的会话 Cookie 形态：

Set-Cookie: __Host-sessionid=<高熵随机>; Secure; HttpOnly; SameSite=Lax; Path=/

对初学者，一个统一的直觉是"纵深防御，各挡一路"：`HttpOnly` 挡 XSS 偷 Cookie，`Secure` 挡网络嗅探，`SameSite` 挡 CSRF，随机 ID 挡预测/枚举，登录后轮换挡会话固定。任何一条单独都不够，但叠起来就把冒用会话的各条路径逐一堵死。OWASP 把认证与会话失败归在 A07:2025（见 15.6），可作对照。

#### 来源与时效
- IETF RFC 6265《HTTP State Management Mechanism》(2011) —— Cookie 基本机制、Domain/Path/Secure/HttpOnly。RFC 6265bis（draft-ietf-httpbis-rfc6265bis，IETF 草案，⚙演进中）—— SameSite 与 `__Host-`/`__Secure-` 前缀。核实 2026-07-30。
- MDN Web Docs《Set-Cookie》https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie —— SameSite 三值与语义、"部分浏览器默认 Lax"及两分钟宽限、`Secure` 需配 `SameSite=None`、前缀规则（本节 SameSite/前缀行为直接引自此页，核实 2026-07-30）。
- OWASP Session Management Cheat Sheet / Top 10:2025 A07 认证失败 https://owasp.org/Top10/2025/ —— 会话 ID 随机性、轮换、超时、Cookie 属性组合。核实 2026-07-30。
- Stanford CS155 / UC Berkeley CS161 (Fall 2025) Web Session 讲义 —— 会话 ID 作为凭据引用的模型。核实 2026-07-30。
- 冲突/时效点：SameSite **默认值**跨浏览器与版本不一致，硬标 `⚙演进快·锚版本`，结论采"不依赖默认、显式声明"。

---

## 15.3 XSS（跨站脚本）

### 15.3.1 XSS 是什么

跨站脚本（Cross-Site Scripting, XSS）指攻击者设法让**恶意 JavaScript 在受害者浏览器里、以受害网站的源身份执行**。因为脚本跑在受害站点的源里，它就拥有该源的一切权限：读写 DOM、发起带 Cookie 的同源请求、读取非 `HttpOnly` 的 Cookie 等。根因几乎总是同一句话——**不可信的输入被当作 HTML/脚本插进了页面，而没有正确编码或隔离**。

理解 XSS 的钥匙是回到 SOP（15.1）：SOP 假设"同一个源里的脚本都是这个网站自己的、可信的"。XSS 打破的正是这个假设——攻击者的脚本混进了受害站点的源，于是 SOP 不但挡不住它，反而**为它背书**。这就是为什么 XSS 危害极大：它不是绕过 SOP，而是在 SOP 的信任边界**内部**得手。按内容注入的路径不同，XSS 分三型。

### 15.3.2 存储型 XSS

存储型（Stored/Persistent）XSS 指恶意脚本被**持久保存在服务器**（数据库、留言、评论、用户资料等），之后每个访问该内容的用户都会被注入并执行。

它的危害面最广，因为是"一次注入、多次命中"：攻击者在论坛发一条含 `<script>` 的帖子，此后每个打开帖子的用户浏览器都会执行它，可蠕虫式扩散（历史上的社交网站 XSS 蠕虫即属此类）。初学者要抓住"存储"二字：漏洞点在"存入时未净化 + 输出时未编码"，触发与注入在时间与用户上都是解耦的。

### 15.3.3 反射型 XSS

反射型（Reflected）XSS 指恶意脚本作为请求的一部分（通常在 URL 参数里）被服务器**原样反射**回响应页面并执行，不做持久存储。

典型场景是搜索页把关键词回显：

请求  https://site.com/search?q=<script>steal()</script>

响应  <p>你搜索了：<script>steal()</script></p>   → 脚本执行

因为不落库，攻击者必须**诱导受害者点击**一个精心构造的链接（钓鱼邮件、恶意广告）才能得手，是"一人一次"的定向攻击。初学者常见误区是以为"只在 URL 里、没存进数据库就没事"——只要参数被未编码地回显进 HTML，反射型 XSS 就成立。

### 15.3.4 DOM 型 XSS

DOM 型（DOM-based）XSS 指漏洞完全发生在**客户端 JavaScript**：页面脚本把不可信数据（如 `location.hash`、`location.search`）写入危险的 DOM 汇聚点（sink），触发脚本执行，服务器响应本身可能完全干净。

危险的 sink 举例：

element.innerHTML = 不可信数据

document.write(不可信数据)

eval(不可信数据) / setTimeout("字符串")

location = 不可信数据

注入既不经服务器存储、也不经服务器反射，而是纯粹在浏览器里由前端代码把"数据源（source）"流到"汇聚点（sink）"造成。初学者判断的窍门是看"污染数据有没有经过服务器"——DOM 型的答案是"没有或不必"，因此服务器端的过滤对它无效，必须在前端修（用 `textContent` 而非 `innerHTML`、避免 `eval`）。

### 15.3.5 防御主线：上下文相关的输出编码

XSS 的第一性防御不是"过滤输入里的 `<script>`"，而是**在把数据输出到页面时，按所处上下文做正确的转义/编码**，让数据永远被当作数据、不被解析成标记或代码。

同一份数据，插入不同上下文需要不同编码：

HTML 文本节点     < > & 转成 &lt; &gt; &amp;

HTML 属性值       额外转义引号，且属性要加引号包裹

<script> 内       不能靠 HTML 转义，须避免把不可信数据放进脚本上下文

URL 参数          用 URL 编码（encodeURIComponent）

现代实践里，这层工作主要交给框架：React、Angular、Vue 等模板引擎**默认对插值做 HTML 转义**，所以正常写法天然安全；危险只在你显式绕过时出现（React 的 `dangerouslySetInnerHTML`、Angular 的 `bypassSecurityTrustHtml`、直接 `innerHTML`）。初学者的三条铁律：优先用框架的默认转义；绝不把不可信数据拼进 `innerHTML`/`eval`；DOM 里输出文本用 `textContent`。若确实需要允许富文本，用经过审计的净化库（如 DOMPurify）而非自己写正则过滤。

### 15.3.6 CSP：纵深防御层

内容安全策略（Content Security Policy, CSP）是一个 HTTP 响应头，让服务器声明"这个页面允许从哪些来源加载/执行脚本等资源"，从而在编码万一失手时**限制 XSS 的破坏**。它是纵深防御的第二道墙，不能替代输出编码。

一个偏弱的旧式写法（白名单域名）与推荐的严格写法（基于 nonce）对比：

Content-Security-Policy: script-src 'self' https://cdn.example.com

Content-Security-Policy: script-src 'nonce-<每次随机> ' 'strict-dynamic'; object-src 'none'; base-uri 'none'

严格 CSP 的思路是：只有带上服务器本次随机下发的 `nonce` 的 `<script>` 才会执行，注入进来的行内脚本因为猜不到 nonce 而被浏览器拒绝执行；`'strict-dynamic'` 允许这些受信脚本再动态加载它们需要的脚本，从而摆脱对易错的域名白名单的依赖。OWASP 明确推荐**基于 nonce 或 hash 的严格 CSP**，而非纯域名白名单（后者常因白名单里存在可被滥用的 JSONP 端点而被绕过）。

初学者要牢记两点。其一，CSP 是"减害"不是"根治"：它假设 XSS 可能已经发生，目标是让注入的脚本**跑不起来或传不出数据**（例如禁止连到攻击者域名）。其二，`'unsafe-inline'` 和 `'unsafe-eval'` 会基本抵消 CSP 对 XSS 的防护，是常见的配置错误。更前沿的 Trusted Types（`require-trusted-types-for 'script'`）可从机制上消灭 DOM 型 XSS 的危险 sink，浏览器支持仍在推进，标 `⚙演进快`。

### 15.3.7 其它配套防御

除编码与 CSP 外，几项措施共同收窄 XSS 的战果。Cookie 的 `HttpOnly`（15.2.4）让脚本偷不到会话 Cookie；输入侧的验证/白名单可减少可注入面（但**不能**替代输出编码）；富文本用成熟净化库；框架默认转义是最省力的基线。

一个初学者必须建立的层次感是"预防 → 减害"两层：输出编码 + 框架默认转义 + 净化库属于**预防**（不让脚本注入成功），CSP + HttpOnly 属于**减害**（就算注入成功也限制危害）。真实系统两层都要有，因为任何单层都可能被某个边角遗漏击穿。XSS 在 OWASP 2025 归于 A05:2025-Injection（见 15.6.6）。

#### 来源与时效
- OWASP Cross-Site Scripting (XSS) 与 XSS Prevention / CSP Cheat Sheet；OWASP Top 10:2025 A05 Injection https://owasp.org/Top10/2025/ —— 三型分类、上下文编码、严格 CSP（nonce/hash/strict-dynamic）推荐。核实 2026-07-30。
- MDN Web Docs《Content Security Policy (CSP)》与《Cross-site scripting》https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP —— CSP 指令语义、nonce、`'strict-dynamic'`、Trusted Types。核实 2026-07-30。
- UC Berkeley CS161 (Fall 2025) / Stanford CS155 Web Security 讲义 —— XSS 三型与"脚本以受害源身份执行"模型、CSP 作为纵深防御。核实 2026-07-30。
- Trusted Types 浏览器支持标 `⚙演进快`；不给具体支持版本号（未逐一核）。

---

## 15.4 CSRF（跨站请求伪造）

### 15.4.1 CSRF 是什么与它成立的前提

跨站请求伪造（Cross-Site Request Forgery, CSRF）指攻击者诱导已登录受害者的浏览器，向受害者信任的网站发出一个**受害者本人没有意图的、会改变状态的请求**——而浏览器会**自动带上该站点的会话 Cookie**，服务器因而误以为是本人操作。

它成立依赖三个前提叠加：其一，网站用 **Cookie 自动携带**来认证（浏览器对任何指向该站的请求都会带上 Cookie，见 15.1.4）；其二，请求的效果**仅由参数决定**、服务器不校验"这请求是不是本站页面主动发起的"；其三，攻击者能让受害者的浏览器发出这个请求（一个恶意页面里的自动提交表单、`<img>` 就够）。经典画面是：

evil.com 的页面里放：

<form action="https://bank.com/transfer" method="POST">

  <input name="to" value="attacker"><input name="amount" value="10000">

</form>

<script>document.forms[0].submit()</script>   受害者一打开就自动向 bank.com 转账

注意 CSRF 与 XSS 的对偶：XSS 是"攻击者的脚本跑在受害站点里"，CSRF 是"攻击者借用受害者的凭据发请求、但读不到响应"（SOP 挡住了响应，见 15.1.4）。正因读不到响应，CSRF 只能做"盲发"的状态改变操作（转账、改密码、改邮箱），这也决定了它的防御思路——让服务器能分辨"这个状态改变请求到底是不是本站发起的"。

### 15.4.2 同步器 Token 模式

最经典的 CSRF 防御是同步器令牌（Synchronizer Token / anti-CSRF token）：服务器为每个会话（或每个表单）生成一个**不可预测的随机 token**，嵌进合法页面的表单里；提交时服务器校验请求里的 token 与会话中记录的是否一致，不一致就拒绝。

同步器 token 的流程可以分四步来看。

1. 用户访问表单页 → 服务器生成随机 token，放进隐藏字段 <input type="hidden" name="csrf" value="...">，并记入会话

2. 用户提交 → 请求体带上 csrf=...

3. 服务器比对 请求中的 token == 会话中的 token → 一致才执行

攻击者的 `evil.com` 页面因为 SOP **读不到** `bank.com` 的表单页内容，也就拿不到那个随机 token，伪造的请求缺 token 或 token 错，被服务器拒。关键在 token 必须**不可预测**且**与会话绑定**——放进 Cookie 里让浏览器自动带的 token 是无效的（会被 CSRF 一起带上），必须放进请求体或自定义头这类"跨站请求带不上"的位置。

### 15.4.3 SameSite Cookie 防御

第二条主力是给会话 Cookie 设 `SameSite=Lax`（或 `Strict`）（见 15.2.5）：浏览器在**跨站请求**里就不带会话 Cookie，服务器收到没有有效会话的请求，CSRF 自然失败。

`SameSite=Lax` 恰好挡住 CSRF 最常见的载体——跨站 `POST`、跨站 `fetch`、`<img>`/`<iframe>` 触发的请求都不带 Cookie，而普通的跨站点击导航（GET）仍带、不破坏体验。`Strict` 更严但会让"从外站链接点进来显示未登录"。要提醒初学者：`SameSite` 是很强的基线，但**不宜作为唯一防御**——它的默认值跨浏览器不一致（15.2.5）、老旧浏览器可能不支持、且对"同 site 不同 origin 的子域被攻破"场景保护有限。OWASP 的立场是 `SameSite` 与 token 是**互补的纵深防御**，重要操作两者都上。

### 15.4.4 其它防御手段

几种补充或替代方案各有适用场景。双重提交 Cookie（double-submit）把随机值同时放进 Cookie 和请求参数，服务器比对两者是否相等，适合无服务端会话存储的无状态场景（但需注意子域写 Cookie 的攻击面）。校验 `Origin`/`Referer` 头，判断请求是否来自本站，是轻量的辅助层。对纯 API + 自定义请求头（如 `X-Requested-With`）的调用，天然受 CORS 预检保护——跨站脚本发不出这种带自定义头的请求，除非服务器 CORS 放行。

**不要**用"只接受 POST"来防 CSRF——攻击者用自动提交表单一样能发跨站 `POST`；也不要把 anti-CSRF token 放进 GET 的 URL 里（会随 Referer 泄露）。真正有效的是"攻击者拿不到/带不上的秘密"（token）或"浏览器跨站不带的凭据"（SameSite）。

### 15.4.5 CSRF 与 XSS 的关系及易错点

这里有一件事必须记住，**XSS 能击穿几乎所有 CSRF 防御**。因为 XSS 让攻击者的脚本跑在受害站点源内，它可以直接读到页面里的 anti-CSRF token、也可发起同源请求（Cookie 照带、SameSite 也不拦，因为是同站）。所以有 XSS 时谈 CSRF 防御意义有限——这再次说明 XSS 是更根本的漏洞，必须优先根治。

先问"这个状态改变操作，攻击者能不能在不读取响应的情况下盲发并生效？"——能，就有 CSRF 风险；防御选"token（服务端有会话时首选）+ SameSite（基线）"，并确保站点本身无 XSS。CSRF 在 OWASP 历史上曾是独立条目，2021 起因框架普遍内建防御而并入 A01 Broken Access Control 大类（见 15.6）。

#### 来源与时效
- OWASP Cross-Site Request Forgery (CSRF) Prevention Cheat Sheet；OWASP Top 10:2025 https://owasp.org/Top10/2025/ —— 同步器 token、SameSite、double-submit、Origin/Referer 校验、"XSS 击穿 CSRF 防御"。核实 2026-07-30。
- MDN Web Docs《CSRF》与《Set-Cookie SameSite》https://developer.mozilla.org/ —— SameSite 在跨站请求中的携带规则。核实 2026-07-30。
- UC Berkeley CS161 (Fall 2025) / Stanford CS155 Web Security 讲义 —— CSRF 前提（Cookie 自动携带 + SOP 读不到响应）与 token 有效性原理。核实 2026-07-30。

---

## 15.5 注入类漏洞

### 15.5.1 注入的通用模型：数据被当成了代码

注入（Injection）类漏洞的共同本质是：程序把**不可信的输入拼进了某种会被解释器执行的语句**（SQL、shell 命令、LDAP、模板等），使输入里的一部分越过了"数据"的身份、被解释器当作"代码/结构"执行。防御的共同思路也只有一句——**让数据和代码分离，永远不让输入改变语句的结构**。

理解这个模型后，各种具体注入（SQL、命令、LDAP、XPath、模板注入）都是同一个病在不同解释器上的表现，防御也同构：用"参数化/预编译"或"上下文正确的转义"把数据锁在数据位置。OWASP 2025 把这一族归在 A05:2025-Injection（XSS 也在其中，因为 XSS 是"注入 HTML/JS 到浏览器解释器"）。

### 15.5.2 SQL 注入

SQL 注入（SQLi）指把不可信输入拼接进 SQL 查询字符串，使攻击者能改变查询语义——读取/篡改/删除本无权访问的数据，甚至绕过认证。它是最经典、危害最大的注入类漏洞之一（CWE-89）。

一个用字符串拼接构造的登录/查询语句：

query = "SELECT * FROM users WHERE name = '" + 输入 + "'"

当输入为 `' OR '1'='1` 时，拼出的语句变成：

SELECT * FROM users WHERE name = '' OR '1'='1'

`OR '1'='1'` 恒真，`WHERE` 条件失效，返回**全表**——本该只查一个用户，却泄露了所有行。攻击者进一步可用 `UNION SELECT` 拖出其它表、用注释 `--` 截断后半句、用布尔/时间盲注在无回显时逐位推断数据。初学者抓住一点即可：漏洞不在"用户输入里有引号"，而在"输入被允许改变了 SQL 的语法结构"。

### 15.5.3 参数化查询（预编译语句）

根治 SQL 注入的首选是参数化查询（parameterized query / prepared statement）：SQL 语句的**结构先固定**（用占位符 `?` 或 `:name` 标出数据位置），用户输入作为**参数单独传给数据库**，数据库始终把它当纯数据、绝不当语法解析。

危险写法（拼接）与安全写法（参数化）：

危险  db.execute("SELECT ... WHERE name = '" + 输入 + "'")

安全  db.execute("SELECT ... WHERE name = ?", (输入,))

本机用 Python 内置 `sqlite3` 做了教学级实证（内存库，无真实攻击目标）。同一个攻击输入 `' OR '1'='1`，拼接版返回全表（含管理员行），参数化版返回空——因为它把整个字符串当成了要匹配的"用户名"，库里没有叫这个名字的用户。

复现命令为 `python3 sqli_demo.py`（脚本见下），真实输出如下。

== 拼接（易受注入） 输入: "' OR '1'='1"

  SQL: SELECT id,name,is_admin FROM users WHERE name = '' OR '1'='1'

  结果: [(1, 'alice', 0), (2, 'bob', 0), (3, 'root', 1)]

== 参数化 输入: "' OR '1'='1"

  结果: []

对应最小脚本（关键两函数）：

def vulnerable(name):

    q = "SELECT id,name,is_admin FROM users WHERE name = '" + name + "'"

    return db.execute(q).fetchall()

def safe(name):

    return db.execute("SELECT id,name,is_admin FROM users WHERE name = ?", (name,)).fetchall()

初学者最容易犯的错是"用转义/过滤特殊字符来防 SQLi"——这脆弱且易漏（不同数据库转义规则不同、多字节编码可绕过）。正确做法始终是参数化：让数据库而非字符串拼接来区分代码与数据。这条实证印证了机制，但不替代多来源比对。

### 15.5.4 SQL 注入的其它防御层

参数化是主防线，另有几层配套。ORM/查询构建器在正常用法下内部走参数化，是安全的默认（但其"原始 SQL"逃生口仍可注入）；对**无法参数化的部分**（如表名、列名、`ORDER BY` 方向这类标识符，占位符管不了）必须用**严格白名单**映射，绝不拼接原始输入；数据库账号遵循**最小权限**（应用账号不给 `DROP`/管理权限），把注入得手后的破坏面压到最小；输入验证作为辅助层。

初学者要记住占位符的一个边界：`?`/`:name` 只能替代**值**，不能替代 SQL 的**结构元素**（表名、列名、关键字）。见到"按用户选择的列排序"这种需求，正确姿势是把用户输入映射到一个预定义的合法列名集合，而不是把它拼进 SQL。

### 15.5.5 命令注入

命令注入（OS Command Injection, CWE-78）指程序把不可信输入拼进传给操作系统 shell 执行的命令串，攻击者用 shell 元字符（`;` `|` `&&` `$()` 反引号等）追加或改写命令，从而在服务器上执行任意命令。

一个危险场景如下。

os.system("ping " + 用户输入)      输入 "8.8.8.8; rm -rf /" → 分号后被当成第二条命令执行

根治方法与 SQLi 同构——**不经过 shell、把命令和参数分开传**：用系统调用的"参数数组"形式（如 Python 的 `subprocess.run([...], shell=False)`）直接把可执行文件与各参数作为独立元素传给内核，元字符就失去特殊含义（它们只是某个参数里的普通字符）。

对应的安全写法如下。

subprocess.run(["ping", "-c", "1", 用户输入])   # shell=False，输入只作为一个参数

初学者的关键区分是"走不走 shell"：`shell=True` 或 `os.system` 会让字符串交给 `/bin/sh` 解析，元字符生效——这是漏洞面；用参数数组 + `shell=False` 则绕过 shell 解析。能不调用外部命令就别调（用语言内置库替代），是更彻底的做法。

### 15.5.6 其它注入类型概览

同一模型还衍生出多种注入，防御思路一致（数据/代码分离或上下文转义）。LDAP 注入（拼进目录查询过滤器）、XPath/XML 注入（拼进 XML 查询，并关联 XXE 外部实体攻击）、NoSQL 注入（如把对象结构注入 MongoDB 查询，绕过条件）、以及服务端模板注入 SSTI（不可信输入进入模板引擎被当模板代码执行，常导致 RCE）。

初学者不必记全每种语法，只需内化那句总纲：**只要"不可信输入拼进了某个解释器会执行的串"，就有注入风险；防御就是让输入永远待在数据位置**。参数化、预编译、上下文转义、白名单、最小权限，是这族漏洞反复出现的同一套解法。

#### 来源与时效
- OWASP Top 10:2025 A05 Injection https://owasp.org/Top10/2025/ ；OWASP SQL Injection Prevention / Command Injection Cheat Sheet —— 参数化查询首选、白名单标识符、最小权限、命令注入用参数数组。核实 2026-07-30。
- MITRE CWE-89 (SQL Injection)、CWE-78 (OS Command Injection) https://cwe.mitre.org/ —— 漏洞编号与定义（页面随年度更新，编号稳定）。核实 2026-07-30。
- UC Berkeley CS161 (Fall 2025) / Stanford CS155 Web Security 讲义 —— 注入通用模型（数据被当代码）与参数化原理。核实 2026-07-30。
- 本机教学实证：Python 3.11.15 内置 `sqlite3`（内存库），脚本 `sqli_demo.py`，输出见 15.5.3（实证不替代多来源比对）。

---

## 15.6 OWASP Top 10:2025 谱系

### 15.6.1 OWASP Top 10 是什么

OWASP Top 10 是开放 Web 应用安全项目（OWASP）定期发布的、**最关键的 Web 应用安全风险的共识清单**，是业界最广泛引用的安全基线之一。它按类别（category）而非单个漏洞组织，每类聚合了一批相关的 CWE 弱点编号。2025 版为最终版，2026 年初发布，取代 2021 版。硬标 `⚙演进快·锚版本`：类别名与排序每次改版都会变，引用时必须写明"2025 版"。

2025 版基于对约 589 个 CWE、约 175000 条映射到 CWE 的 CVE 记录（来自国家漏洞库 NVD）的数据分析，最终 10 类里含 248 个 CWE。其中多数类别按贡献数据排名，另保留少量由**社区调查**提升的类别（"数据驱动 + 社区调查"的混合法）——这样既反映真实世界的高频问题，又能纳入数据尚未充分体现但专家公认重要的新兴风险。以上数值引自 OWASP 2025 引言页，核实 2026-07-30。

### 15.6.2 2025 版完整清单

以下是 OWASP Top 10:2025 的十个类别（顺序即排名），逐条列出以避免编造：

A01:2025 - Broken Access Control（访问控制失效）

A02:2025 - Security Misconfiguration（安全配置错误）

A03:2025 - Software Supply Chain Failures（软件供应链失效）

A04:2025 - Cryptographic Failures（加密机制失效）

A05:2025 - Injection（注入）

A06:2025 - Insecure Design（不安全设计）

A07:2025 - Authentication Failures（认证失效）

A08:2025 - Software or Data Integrity Failures（软件与数据完整性失效）

A09:2025 - Security Logging and Alerting Failures（安全日志与告警失效）

A10:2025 - Mishandling of Exceptional Conditions（异常条件处理不当）

这不是"从上到下逐条修"的检查表，而是**风险认知框架**——它告诉你"最容易出问题、影响最大的十个方向在哪"，具体到代码要下沉到对应 CWE 与各类的 Cheat Sheet。本报告 15.1–15.5 讲的 SOP/会话/XSS/CSRF/注入，分别落在 A01、A07、A05 等类里。

### 15.6.3 A01 访问控制失效（含 SSRF 并入）

A01:2025-Broken Access Control 蝉联第 1，因为它在真实应用里出现频率最高。它指用户能执行或访问**其超出授权范围**的操作/数据——越权查看他人记录、篡改 URL 里的对象 ID 越权访问（IDOR）、普通用户触达管理功能等。

2025 的重要变更是：**SSRF（服务器端请求伪造）从 2021 的独立条目并入 A01**。原因是 OWASP 把"诱使服务器去访问它本不该访问的内部资源/地址"本质上视为一种访问控制失效——攻击者借服务器之手越过了网络层的访问边界。初学者可这样理解 SSRF：应用有个"填 URL、由服务器去抓取"的功能，攻击者填入 `http://169.254.169.254/`（云元数据地址）或内网地址，让服务器代替自己访问本无法直达的内部服务。CSRF 在历史上也曾独立，现同样归在这一访问控制大类下（框架内建防御后其独立占比下降）。

### 15.6.4 A03 软件供应链失效（扩展的新面貌）

A03:2025-Software Supply Chain Failures 由 2021 的 A06"脆弱和过时的组件"**扩展而来**，范围大幅拓宽到**整个软件依赖生态**：不只是"用了有漏洞的旧库"，还包括构建系统、CI/CD 管道、分发基础设施、被投毒的开源包与依赖等被攻破的情形。

它的画像很特别：在测试数据里出现频率**最低**，但来自 CVE 的**平均可利用性与影响分数最高**——即"不常见但一旦中招后果极重"。这解释了它被显著提升的原因（部分靠社区调查权重）。对初学者，可联想近年现实事件：一个被广泛依赖的开源包被植入后门、或构建管道被攻破，会**一次性波及成千上万下游用户**。防御涉及依赖来源校验、软件物料清单（SBOM）、锁定与签名验证、最小化并审计依赖等——这些细节属工程实践，标 `⚙演进快`，本节只登记框架定位不深挖。

### 15.6.5 A10 异常条件处理不当（2025 新增）

A10:2025-Mishandling of Exceptional Conditions 是 2025 **全新引入**的类别，聚焦程序在遇到异常/意外情况时的错误处理：不当的错误处理、逻辑缺陷，以及**"失败即放行"（fail-open）**——本应在出错时拒绝，却因异常路径写错反而放行或暴露信息。

一段鉴权代码 `try { 检查权限 } catch { }`，异常被吞掉后代码继续往下执行敏感操作，等于"一出错就等于通过"；又如错误信息把堆栈/内部路径/SQL 语句直接返回给用户，泄露攻击线索。它与"安全设计"的区别在于：A10 强调的是**异常/边界路径**上的处理错误，而正常路径可能完全正确。这类问题隐蔽、测试常覆盖不到，故 2025 单列一类提醒开发者显式设计失败行为（默认拒绝、fail-closed）。

### 15.6.6 2021 → 2025 完整变更映射

下表逐条对照（引自 OWASP 2025 引言页的官方映射，核实 2026-07-30），是本节交叉核对的核心证据：

2021 #1 Broken Access Control        → 2025 A01（保留第1；SSRF 并入）

2021 #2 Cryptographic Failures       → 2025 A04（下降）

2021 #3 Injection                    → 2025 A05（下降；XSS 仍在此类）

2021 #4 Insecure Design              → 2025 A06（下降）

2021 #5 Security Misconfiguration    → 2025 A02（上升到第2）

2021 #6 Vulnerable & Outdated Components → 2025 A03（扩展并更名为 Software Supply Chain Failures）

2021 #7 Identification & Authentication Failures → 2025 A07（更名为 Authentication Failures）

2021 #8 Software or Data Integrity Failures → 2025 A08（保留）

2021 #9 Security Logging & Monitoring Failures → 2025 A09（更名为 Security Logging & Alerting Failures）

2021 #10 Server-Side Request Forgery (SSRF) → 并入 2025 A01

2025 A10 Mishandling of Exceptional Conditions → 全新增加（2021 无对应）

三条最该记住的 2025 变更：其一，**A03 供应链失效**登场（由旧"过时组件"扩展），反映供应链攻击成为主战场；其二，**A10 异常条件处理不当**新增，把 fail-open 类错误单列；其三，**SSRF 并入 A01**、安全配置错误上升到 A02。初学者引用时务必带年份——把 2021 的 "A03 Injection"当成 2025 的说法就会错位（2025 注入是 A05）。冲突/差异已在上表逐条点明，未见 2025 与 2021 官方映射相互矛盾之处。

#### 来源与时效
- OWASP Top 10:2025 官方站点 https://owasp.org/Top10/2025/ 与引言页 `0x00_2025-Introduction`（十类清单、方法论数值 589/248/~175000 CVE、2021→2025 官方映射表）。核实 2026-07-30。硬标 `⚙演进快·锚版本`：类别名/排序随版本变，务必标"2025 版"。
- 变更要点交叉核对：多家安全厂商与社区 2025-12 至 2026-01 的对照分析（Equixly、Parasoft、practical-devsecops、GitLab 博客等，二手，仅用于交叉验证官方映射，不承重）确认 SSRF 并入 A01、A03 供应链、A10 新增、A02 上升。核实 2026-07-30。
- MITRE CWE https://cwe.mitre.org/ —— 各类聚合的 CWE 编号来源。核实 2026-07-30。
- 发布日期：2025 版为最终版、2026 年初发布并取代 2021；确切发布日以 OWASP 官方公告为准，标「待核」，核实 2026-07-30。
