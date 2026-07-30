# L5-04·大主题10 TLS 与安全信道协议

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：G11-04（分组工作模式与 AEAD：GCM、ChaCha20-Poly1305、nonce 语义）、G11-06（公钥加密/RSA）、G11-07（DH 密钥交换与 ECDH/X25519）、G11-09（PKI 与 X.509 证书、信任链、吊销）；软 ← L4-02（网络分层、TCP）｜ 一手锚点：IETF RFC 8446《The Transport Layer Security (TLS) Protocol Version 1.3》（2018-08），https://www.rfc-editor.org/rfc/rfc8446.txt ；IETF RFC 5869《HKDF》（2010-05），https://www.rfc-editor.org/rfc/rfc5869.txt ；Katz & Lindell《Introduction to Modern Cryptography》3rd ed §13.7（TLS 案例研究）；UC Berkeley CS161 (Fall 2025) L20；Stanford CS155 HTTPS ｜ 成熟度：TLS 1.3（RFC 8446）GA/稳定，但部署面（版本占比、0-RTT 采用、PQ 混合密钥交换迁移）⚙演进快·锚 2026-07-30

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（10.1 握手流程 → 10.2 密钥派生调度 → 10.3 前向保密 → 10.4 认证与信道绑定 → 10.5 降级与中间人）是围绕"同一场 TLS 1.3 握手"的五个切面：先走一遍消息流（10.1），再放大握手内部的密钥调度机器（10.2），抽出其中"临时密钥换来前向保密"这一性质（10.3），抽出"证书签名 + Finished MAC 换来认证与信道绑定"这一性质（10.4），最后看攻击者如何试图撬开它以及现实部署的坑（10.5）。机制同族、篇幅适中，符合 report-format v3「1 大主题 = 1 报告」，不拆 `-a/-b`。

> 一条主线心智模型：**TLS 把"两个原本互不认识、只有一条被监听的 TCP 连接"变成"经过认证、加密、且带完整性的安全信道"。它做三件事——用（临时）DH 协商出双方共享的秘密（机密性 + 前向保密），用证书签名让客户端确认对面确实是它想访问的服务器（认证），用一条贯穿整场握手的 transcript 哈希把上面两件事和最终密钥死死绑在一起（信道绑定 / 防篡改防降级）。TLS 1.3 相较 1.2 的最大变化是：砍掉所有不提供前向保密的密钥交换（只留 (EC)DHE），把握手压到一轮（1-RTT），并从 ServerHello 之后就开始加密。**

> 本报告只讲**协议编排**：分组模式（GCM/ChaCha20-Poly1305，见大主题 04）、公钥/RSA（06）、DH/ECDH/X25519（07）、数字签名（08）、证书/PKI（09）等**原语**已在各自大主题成节，这里只在用到处指回、不重讲。TLS 1.3 握手步骤、HKDF 密钥派生式、Finished MAC 与信道绑定步骤均按 v3 要求独占行/块，不内联。本报告在仓库外 scratchpad 用纯 Python（hmac/hashlib）自实现 HKDF 并对 RFC 5869 Test Case 1 交叉验证通过（PRK/OKM 逐字节相符），脚本与真实输出贴入 10.2 末。实证仅为补充；多来源比对（RFC 8446 × RFC 5869 × Katz §13.7 × CS161）才是正确性主承重。openssl s_client 抓包解析记录**未取**（如实标注）。

---

## 10.1 TLS 1.3 握手：一轮完成协商 + 认证 + 前向保密

### 10.1.1 安全信道要解决的问题与 TLS 的定位

TLS（Transport Layer Security）是运行在可靠传输（通常是 TCP）之上、应用层之下的一层协议，目标是把一条任何人都能窃听和篡改的字节流，变成一条具有**机密性**（旁人读不到内容）、**完整性**（内容被改会被发现）、**认证**（至少能确认服务器身份）的安全信道。HTTPS 就是 "HTTP 跑在 TLS 上"。TLS 由两层组成：底下的**记录层（record layer）**负责把数据切块、用 AEAD 加密成一条条记录；上面的**握手协议（handshake protocol）**负责在通信开始时协商密钥、认证身份。本大主题讲的就是握手这层的编排。

对初学者，先建立一个分工图：握手协议像"两个人见面时先握手、验身份、约定暗号"，一旦约定完成，之后所有真正的对话（应用数据）都用这个暗号（对称密钥 + AEAD）加密。握手本身不传业务数据，它只负责"把密钥安全地在两端立起来，并确认对面是谁"。TLS 1.3（RFC 8446，2018 年 8 月发布）是当前主流版本，它把之前 TLS 1.2 里冗长、可选项繁多、还残留不安全套件的握手做了大清理。

### 10.1.2 1-RTT 完整握手的消息流

TLS 1.3 的核心卖点是**一轮往返（1-RTT）**就能完成密钥协商 + 服务器认证，随后立刻发应用数据。完整握手（无 PSK、服务器认证、可选客户端认证）的消息流如下，`{}` 表示"用握手流量密钥加密"，`[]` 表示"用应用流量密钥加密"，`+` 表示扩展（extension）：

Client → Server：ClientHello + key_share + signature_algorithms (+ psk 相关扩展)

Server → Client：ServerHello + key_share

Server → Client：{EncryptedExtensions}

Server → Client：{CertificateRequest*}（可选，要求客户端认证时）

Server → Client：{Certificate}

Server → Client：{CertificateVerify}

Server → Client：{Finished}

Server → Client：[Application Data*]（可选，服务器可提前发数据）

Client → Server：{Certificate*} {CertificateVerify*}（可选，被要求时）

Client → Server：{Finished}

Client → Server：[Application Data]

带 `*` 的消息是条件性的（例如只有需要客户端认证时才出现 CertificateRequest/客户端 Certificate/CertificateVerify）。

要点是理解这里"一轮"是怎么数的：客户端在**第一个** ClientHello 里就已经把自己的临时 DH 公钥（放在 `key_share` 扩展）猜着发过去了——它按最可能被接受的曲线（如 X25519）预先生成好一份。服务器收到后，自己也生成临时 DH 公钥放进 ServerHello，此刻**双方已各自算得共享秘密**，服务器于是能立刻用派生出的握手密钥加密后续所有消息（EncryptedExtensions 起都是密文），并把证书、签名、Finished 一次性发回。客户端验完就能发应用数据。整个过程客户端只等了一个 RTT。

这一点是相对 TLS 1.2 的关键进步：TLS 1.2 需要 2-RTT（先 Hello 协商套件、再单独一轮 ClientKeyExchange 交换密钥）。TLS 1.3 把"猜测密钥交换参数"提前塞进第一个 Hello，省掉一轮。若服务器不接受客户端猜的那条曲线，会回一个 **HelloRetryRequest** 要求客户端换参数重发，此时退化为 2-RTT——这是唯一的额外往返来源。

### 10.1.3 从 ServerHello 之后就开始加密

TLS 1.3 与 1.2 的另一个结构性差异：**ServerHello 之后的所有握手消息都是加密的**。ClientHello 和 ServerHello 本身是明文（它们要协商出密钥，还没密钥可用），但一旦双方交换完 `key_share`、算出共享秘密并派生出"握手流量密钥"，从 EncryptedExtensions 开始（含证书、签名、Finished）全部用握手密钥经 AEAD 加密。

这带来的直接好处是**隐私**——服务器证书（会暴露访问的是哪个网站/组织）在 1.2 里是明文可见的，1.3 里被加密隐藏。初学者容易困惑"证书还没验、密钥怎么就有了"——注意 DH 密钥交换本身不需要认证就能算出共享秘密（见 10.3），认证是**之后**用证书签名补上的；所以"先加密、后认证"在同一轮内不矛盾：加密保护的是隐私，认证由 CertificateVerify + Finished 事后坐实。

### 10.1.4 会话恢复、PSK 与 0-RTT（概览，细节见 10.2）

除了上面的完整握手，TLS 1.3 支持基于**预共享密钥（PSK）**的握手：要么是带外配置的 PSK，要么是上次连接后服务器用 NewSessionTicket 发给客户端的**恢复票据（resumption ticket）**。重连时客户端在 ClientHello 里带上 PSK 标识，跳过证书交换，用 PSK 直接派生密钥，握手更轻。

PSK 模式还解锁了 **0-RTT**：客户端可以在第一个 ClientHello 里就附带用 PSK 派生的"早期流量密钥"加密的应用数据（`early_data`），服务器还没回应就能开始处理，实现"零往返"发数据。代价是 0-RTT 数据**不具前向保密、且可被重放**，属于安全性上的重要折衷，细节与风险见 10.2.5。

#### 来源与时效（本小主题末集中列）
- 一手锚点：RFC 8446 §2「Protocol Overview」（1-RTT 消息流图、`{}`/`[]`/`+`/`*` 记号、HelloRetryRequest、加密从 EncryptedExtensions 起）、§2.2（会话恢复与 PSK）、§2.3（0-RTT）。消息流经本报告向 RFC 原文 §2 逐条比对核对，核实 2026-07-30。
- 交叉源：Katz-Lindell 3rd ed §13.7（把 TLS 1.3 作为"认证密钥交换协议"的案例，强调 1-RTT 与握手加密）；UC Berkeley CS161 Fall 2025 L20（TLS/HTTPS 讲义，握手流程与 1.2 对比）；Stanford CS155 HTTPS 讲。三源在"1-RTT、只留 (EC)DHE、ServerHello 后加密"三点上一致。核实 2026-07-30。
- 冲突项：无实质分歧。教材/课程为教学简化省略了扩展细节（如 HelloRetryRequest），以 RFC 8446 §2 为准。
- 时效：TLS 1.3 协议文本自 2018 冻结，稳定 GA；但"1-RTT/0-RTT 在真实流量中的采用比例"⚙演进快，本报告不给具体占比数值（待核，不编造）。

---

## 10.2 密钥派生调度：HKDF 与握手/应用密钥的层层下沉

### 10.2.1 HKDF：extract-then-expand 的密钥派生框架

TLS 1.3 所有密钥都由 **HKDF（HMAC-based Key Derivation Function，RFC 5869）**派生。HKDF 遵循 "extract-then-expand"（先萃取、再扩展）范式，由两个函数组成。萃取步骤把一段可能不均匀（有结构、非满熵）的输入密钥材料 IKM 与一个 salt，压成一段固定长度、伪随机均匀的**伪随机密钥 PRK**：

PRK = HKDF-Extract(salt, IKM) = HMAC-Hash(salt, IKM)

扩展步骤再把 PRK 拉伸成任意长度 L 的输出密钥材料 OKM，其中 info 是绑定用途的上下文标签：

OKM = HKDF-Expand(PRK, info, L)

HKDF-Expand 的内部构造是把 HMAC 迭代拼接（`|` 表示拼接，`0x01`/`0x02`… 是单字节计数器）：

N = ceil(L / HashLen)

T(0) = 空串

T(1) = HMAC-Hash(PRK, T(0) | info | 0x01)

T(2) = HMAC-Hash(PRK, T(1) | info | 0x02)

T(i) = HMAC-Hash(PRK, T(i−1) | info | i)

OKM = (T(1) | T(2) | … | T(N)) 的前 L 个字节

初学者要抓两点直觉。第一，为什么分两步：Extract 负责"提纯"——从一段可能被部分预测的秘密（如 DH 结果，它是群元素、并非均匀比特串）里榨出满熵的 PRK；Expand 负责"分发"——从一把 PRK 派生出多把用途不同、互相独立的子密钥。第二，为什么 Expand 里要带 info 和计数器：info 把"这把密钥是干什么用的"绑进派生，保证"握手密钥"和"应用密钥"即使来自同一 PRK 也彼此不同、不可互推；计数器则让输出能安全地拉伸到超过一个哈希块的长度。

### 10.2.2 TLS 1.3 对 HKDF-Expand 的封装：HKDF-Expand-Label 与 Derive-Secret

TLS 1.3 不直接调用 HKDF-Expand，而是套了两层封装，把 info 结构化。第一层 HKDF-Expand-Label 规定 info 的编码格式，并给所有标签统一加 `"tls13 "` 前缀（注意 tls13 后有一个空格），使 TLS 1.3 派生出的密钥与任何其他协议隔离：

HKDF-Expand-Label(Secret, Label, Context, Length) = HKDF-Expand(Secret, HkdfLabel, Length)

其中 HkdfLabel 是这样一个结构体（长度前缀 + 带前缀标签 + 上下文）：

struct {

    uint16 length = Length;

    opaque label<7..255> = "tls13 " + Label;

    opaque context<0..255> = Context;

} HkdfLabel;

第二层 Derive-Secret 是专门用来派生"秘密"的便捷式，它把当前握手到此刻为止的所有消息的**转录哈希（Transcript-Hash，即握手消息串接后的哈希）**作为 Context 传入，输出长度取哈希输出长（Hash.length）：

Derive-Secret(Secret, Label, Messages) = HKDF-Expand-Label(Secret, Label, Transcript-Hash(Messages), Hash.length)

这里最关键、也最能体现 TLS 1.3 设计精髓的一点是：Derive-Secret 把 **transcript 哈希喂进了密钥派生**。这意味着最终密钥不仅取决于 DH 秘密，还取决于"到目前为止双方看到的每一条握手消息"。任何对握手消息的篡改（哪怕改一个扩展字段）都会让两端算出不同的转录哈希、从而派生出不同的密钥，握手立刻失败——这是信道绑定与防降级的密码学根。

### 10.2.3 密钥调度：三次 Extract 串起来的密钥树

TLS 1.3 的完整密钥调度是把 HKDF-Extract 和 Derive-Secret 交替串成一棵"密钥树"，分三级 Extract。用 `0` 表示一串全零字节（长度等于哈希输出长），PSK 为预共享密钥（无 PSK 时也填 0），(EC)DHE 为 DH 密钥交换算出的共享秘密：

Early Secret = HKDF-Extract(salt=0, IKM=PSK)

Handshake Secret = HKDF-Extract(salt=Derive-Secret(Early Secret, "derived", ""), IKM=(EC)DHE)

Master Secret = HKDF-Extract(salt=Derive-Secret(Handshake Secret, "derived", ""), IKM=0)

从这三个"主干秘密"上，用 Derive-Secret 挂出各阶段的**流量秘密（traffic secret）**，各流量秘密对应的逐字标签如下（左侧为其派生所依据的主干秘密）：

Early Secret → "ext binder" / "res binder"（PSK binder）、"c e traffic"（客户端早期/0-RTT 流量）、"e exp master"（早期导出）

Handshake Secret → "c hs traffic"（客户端握手流量）、"s hs traffic"（服务器握手流量）

Master Secret → "c ap traffic"（客户端应用流量）、"s ap traffic"（服务器应用流量）、"exp master"（导出主秘密）、"res master"（恢复主秘密）

最后，每个流量秘密再用 HKDF-Expand-Label 派生出真正喂给 AEAD 的对称写密钥和 nonce（IV）：

[sender]_write_key = HKDF-Expand-Label(Secret, "key", "", key_length)

[sender]_write_iv = HKDF-Expand-Label(Secret, "iv", "", iv_length)

初学者不必背下每个标签，但要抓住这棵树的三个层次含义。第一层 Early（早期）只有用 PSK 时才有实质内容，服务 0-RTT。第二层 Handshake（握手）密钥用来加密握手中段那些消息（EncryptedExtensions、证书、签名、Finished）。第三层 Application（应用）密钥才是握手完成后加密真正业务数据用的。两级 "derived" 步骤（在每次 Extract 之前对上一级秘密做一次 Derive-Secret(., "derived", "")）的作用是"把上一级秘密先经哈希搅一下再当作下一级的 salt"，保证各级 Extract 的输入在密码学上彼此隔离、不能反推。

### 10.2.4 前向保密与密钥独立性的调度含义

因为握手密钥来自 Handshake Secret（含 (EC)DHE），应用密钥来自 Master Secret（也在 (EC)DHE 之下），只要那把临时 DH 私钥握手后被丢弃，事后任何人（哪怕拿到服务器的长期证书私钥）都无法重算这些流量密钥——这就是前向保密的调度层原因（性质本身见 10.3）。此外握手密钥和应用密钥挂在不同主干秘密下、用不同标签派生，彼此独立：即便握手密钥泄露也推不出应用密钥。TLS 1.3 还支持握手后用 "traffic secret" 沿 "traffic upd" 标签做**密钥更新（KeyUpdate）**，长连接可周期性滚动流量密钥，限制单把密钥的暴露面。

### 10.2.5 0-RTT 早期数据与重放风险

PSK 模式下，客户端可用 Early Secret 派生的 "c e traffic" 早期流量密钥，在第一个 ClientHello 里就加密并发送应用数据（early_data），达成 0-RTT。它的两条安全折衷必须记牢。其一，**无前向保密**：0-RTT 数据只由 PSK（一个相对长寿的共享秘密）保护，没有新鲜的临时 DH 参与，PSK 若日后泄露，历史 0-RTT 数据可被解密。其二，**可重放（replay）**：0-RTT 数据在服务器完成握手、确立新鲜性之前就被处理，网络上的攻击者可以把捕获到的 0-RTT 记录原样重发，服务器可能重复执行同一请求。RFC 8446 明确要求应用只在**幂等**、可容忍重放的操作上使用 0-RTT（如只读 GET），并建议服务器侧做单次票据 / 反重放窗口等缓解，但标准坦承无法完全消除重放。初学者的正确心智是：0-RTT 是"用一部分安全性换一次往返延迟"的可选特性，不是免费午餐。

### 10.2.6 本机实证：自实现 HKDF 对齐 RFC 5869 测试向量

为坐实上面的 HKDF 公式（10.2.1），在仓库外 scratchpad 用 Python 标准库 hmac/hashlib 自实现 HKDF-Extract / HKDF-Expand（SHA-256），跑 RFC 5869 附录 A Test Case 1，PRK 与 OKM 与 RFC 给定值逐字节相符。

下面是复现脚本（基线 Py3.11.15 @2026-07-30）。

```python
import hmac, hashlib, math
def extract(salt, ikm): return hmac.new(salt, ikm, hashlib.sha256).digest()
def expand(prk, info, L):
    n = math.ceil(L/32); t = b""; okm = b""
    for i in range(1, n+1):
        t = hmac.new(prk, t+info+bytes([i]), hashlib.sha256).digest(); okm += t
    return okm[:L]
ikm  = bytes.fromhex("0b"*22)
salt = bytes.fromhex("000102030405060708090a0b0c")
info = bytes.fromhex("f0f1f2f3f4f5f6f7f8f9"); L = 42
prk = extract(salt, ikm); okm = expand(prk, info, L)
print(prk.hex()); print(okm.hex())
```

它在本机跑出的真实输出为两行十六进制。

PRK = 077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5

OKM = 3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865

两者与 RFC 5869 Test Case 1 的 PRK/OKM 完全一致（`PRK_ok=True, OKM_ok=True`），印证 HKDF-Extract=HMAC(salt,IKM) 与 HKDF-Expand 的计数器迭代拼接构造无误。此实证只验 HKDF 原语，不代表完整 TLS 密钥调度的实机抓取（后者未取）。

#### 来源与时效（本小主题末集中列）
- 一手锚点：RFC 5869 §2.2（HKDF-Extract）、§2.3（HKDF-Expand 的 T(i) 迭代）、附录 A.1 Test Case 1（IKM/salt/info/L/PRK/OKM 十六进制值）；RFC 8446 §7.1（HkdfLabel 结构、HKDF-Expand-Label、Derive-Secret、含 "derived" 的三级 Extract 密钥调度、各流量秘密标签）、§7.3（"key"/"iv" 派生）、§2.3 与 §8（0-RTT 与反重放）。HKDF-Expand-Label/Derive-Secret/HkdfLabel/Finished 相关公式经本报告向 RFC 8446 §7.1 原文逐条比对核实 2026-07-30。
- 交叉源：本机自实现 HKDF 对 RFC 5869 Test Case 1 交叉验证通过（见 10.2.6，PRK/OKM 逐字节相符）；Katz-Lindell 3rd ed §13.7 与 §7（HKDF 作为 extract-then-expand KDF 的定义）；CS161 Fall 2025 L20（密钥调度教学简图）。多源在 "extract-then-expand、transcript 入密钥派生、0-RTT 无前向保密且可重放" 三点上一致。核实 2026-07-30。
- 冲突项：无。教学材料常只画"Early/Handshake/Master 三级 + hs/ap 流量"简化树，省略 binder/exporter 等分支；以 RFC 8446 §7.1 完整标签清单为准，非分歧。
- 时效：HKDF（RFC 5869）与 TLS 密钥调度（RFC 8446）文本稳定；0-RTT 的部署与反重放策略属实现层，⚙演进快，本报告不给具体实现默认值。

---

## 10.3 前向保密：临时 ECDHE 与会话密钥独立性

### 10.3.1 前向保密的定义

前向保密（Forward Secrecy，也叫 Perfect Forward Secrecy / PFS）指：即使某一方的**长期私钥**在未来被泄露，攻击者也无法解密此前已经录下的历史会话。换句话说，长期密钥的失窃不会"追溯性"地摧毁过去通信的机密性。TLS 1.3 把前向保密作为**强制**属性——它只保留提供前向保密的密钥交换方式，即临时 (EC)DHE。

对初学者，先分清两种密钥。**长期密钥**是服务器证书里那把、长期不变、用来证明身份（签名）的私钥。**临时密钥**是每次握手现场生成、握手一结束就丢弃的一次性 DH 密钥对。前向保密的关键正是：真正决定会话对称密钥的是那把**临时** DH 密钥，而长期私钥只用来**签名**（证明"这把临时公钥确实是我发的"），并不参与推导会话密钥。所以长期私钥即便日后泄露，也只能让攻击者冒充服务器发起**新**握手，却算不出**旧**握手里那把已被丢弃的临时密钥，历史密文依旧解不开。

### 10.3.2 ECDHE：临时椭圆曲线 DH

TLS 1.3 的密钥交换用 **(EC)DHE**——椭圆曲线 Diffie–Hellman，Ephemeral（临时）。DH/ECDH 的数学原语见大主题 07，这里只讲编排：双方在 Hello 的 `key_share` 扩展里各放一份**本次握手新生成**的临时 DH 公钥，各自用"自己的临时私钥 × 对方的临时公钥"算出同一个共享秘密 (EC)DHE，这个秘密作为 IKM 喂进 10.2.3 的 Handshake Secret 那级 Extract。

以抽象群记法，客户端私钥 a、服务器私钥 b，生成元/基点 g：

客户端发送 g^a，服务器发送 g^b

共享秘密 = g^(ab)（客户端算 (g^b)^a，服务器算 (g^a)^b，相等）

字母里的 "E"（Ephemeral）是前向保密的命门：a、b 是一次性的，握手一结束就从内存丢弃，之后世界上再无人持有它们，g^(ab) 也就无法重算。TLS 1.3 支持的曲线组包括 secp256r1/384r1/521r1 与 x25519/x448（`supported_groups` 扩展），以及有限域 DH 的 ffdhe 组；实践中 X25519 最常用。TLS 1.2 时代还允许"静态 RSA 密钥交换"（客户端用服务器长期 RSA 公钥加密一个 pre-master secret），那种方式**没有**前向保密——录下密文的攻击者只要日后拿到服务器 RSA 私钥就能解密全部历史会话；TLS 1.3 已彻底删除这种模式。

### 10.3.3 会话密钥独立性

每次握手都用全新的临时 DH 对，得到全新的 (EC)DHE，于是每条会话的流量密钥彼此**独立**：破解或泄露一次会话的密钥，不会波及其它会话；同理，一次握手内握手密钥与应用密钥也独立（10.2.4）。这把"单点泄露"的爆炸半径压到最小——一次会话一把火，烧不到别处。

初学者常有一个疑问，就是既然每次都新生成临时密钥、还要签名，那长期证书私钥到底还有什么用。答案是它专管**认证**——它不参与算密钥，只用来对握手转录做签名（CertificateVerify，见 10.4），向客户端证明"跟你做这次 ECDHE 的对面，确实是证书上那个身份"。把机密性（靠临时 DH）与认证（靠长期签名）解耦，正是前向保密得以成立的结构前提。

#### 来源与时效（本小主题末集中列）
- 一手锚点：RFC 8446 §1.2（相对 1.2 的变更：移除静态 RSA 与非前向保密的 DH，强制 (EC)DHE）、§4.2.8（key_share）、§4.2.7（supported_groups：secp256r1/384r1/521r1、x25519/x448、ffdhe 系列）、§7.1（(EC)DHE 作为 Handshake Secret 的 IKM）。核实 2026-07-30。
- 交叉源：Katz-Lindell 3rd ed §10（DH 与前向保密定义）+ §13.7（TLS 强制临时 DH）；CS161 Fall 2025 L20（forward secrecy = 长期密钥泄露不追溯泄露历史会话）；Stanford CS155 HTTPS（静态 RSA 无 PFS 的对比）。三源一致。核实 2026-07-30。
- 冲突项：无。术语 PFS 与 FS 混用，含义相同，已在 10.3.1 说明。
- 时效：机制稳定。曲线偏好（如 X25519 占比、抗量子的混合密钥交换如 X25519+ML-KEM 逐步铺开）⚙演进快，属部署面，本报告不给占比数值（待核，不编造）。

---

## 10.4 身份认证与信道绑定：证书、CertificateVerify、Finished 与下调保护

### 10.4.1 服务器认证的两步：证书 + 对握手的签名

DH 密钥交换本身是**不认证的**——它能让双方算出共享秘密，却不能保证对面是谁（中间人也能跟你各做一次 DH，见 10.5.2）。TLS 用两步补上服务器认证。第一步 **Certificate** 消息：服务器发来它的 X.509 证书链（结构、CA 信任链、验证见大主题 09），证书把服务器的**长期签名公钥**绑定到它的域名身份，由客户端信任的 CA 签发。第二步 **CertificateVerify** 消息：服务器用与证书对应的**长期私钥**，对"到此刻为止的整个握手转录哈希"做一个数字签名。

关键在于第二步签的**内容**。CertificateVerify 签名的输入不是随便一段数据，而是一个精心构造的串：64 个值为 0x20 的字节（空格），接一个上下文字符串（服务器方是 `"TLS 1.3, server CertificateVerify"`，客户端方是 `"TLS 1.3, client CertificateVerify"`），再接一个分隔用的 0x00 字节，最后接握手转录哈希。示意为：

签名内容 = (0x20 × 64) ‖ "TLS 1.3, server CertificateVerify" ‖ 0x00 ‖ Transcript-Hash(到 Certificate 为止的所有握手消息)

这个签名的意义在于，只有真正持有证书私钥的一方，才能对"这一场具体握手"产出有效签名。它同时完成了两件事——证明身份（持私钥），以及把"证书身份"绑定到"本次握手所交换的临时 DH 公钥"（因为转录里包含了 key_share）。那 64 字节 0x20 前缀 + 上下文串是为了域分隔，防止这个签名被挪作他用（如跨版本、跨角色的签名重用攻击）。

初学者要记住这里的分工——证书**说**"我是谁"，CertificateVerify **证明**"我确实持有这个身份对应的私钥，而且我就是刚跟你做 DH 的那一方"。少了第二步，任何人拿到你的公开证书都能冒充你——因为证书本身是公开可复制的。客户端认证（若服务器发了 CertificateRequest）是完全对称的一套：客户端也发 Certificate + CertificateVerify。

### 10.4.2 Finished 消息与密钥确认

握手的收尾是双方各发一条 **Finished** 消息，它是对整个握手转录的一个 **MAC（HMAC）**，用于**密钥确认**（证明双方确实派生出了相同密钥）和**握手完整性**（证明握手全程没被篡改）。Finished 用的 MAC 密钥 finished_key，是从该端的握手流量秘密（BaseKey，服务器用 "s hs traffic"、客户端用 "c hs traffic"）派生的：

finished_key = HKDF-Expand-Label(BaseKey, "finished", "", Hash.length)

verify_data = HMAC(finished_key, Transcript-Hash(Handshake Context, Certificate*, CertificateVerify*))

即 verify_data 是用 finished_key 对"到 CertificateVerify（若有）为止的握手转录哈希"求 HMAC。接收方用同样方式算一遍，比对相等才接受。

为什么已经有了 CertificateVerify 签名还需要 Finished？因为它们保护的东西不同。CertificateVerify 用**长期私钥**证明身份，Finished 用**从共享 DH 秘密派生的 MAC 密钥**证明"我确实算出了和你一样的握手密钥"——这是签名给不了的密钥确认。Finished 的 MAC 密钥源自 (EC)DHE，中间人若没算出正确的共享秘密，就发不出能通过校验的 Finished。它是握手能否成立的最后一道闸，也是下面信道绑定与防降级的落点。

### 10.4.3 信道绑定：transcript 哈希把一切钉死

TLS 1.3 防篡改、防降级的密码学根，是一条贯穿始终的**握手转录哈希（transcript hash）**：从 ClientHello 起，每加入一条握手消息就更新一次这条累积哈希。它出现在三个承重位置——被喂进密钥派生（Derive-Secret 的 Context，见 10.2.2）、被 CertificateVerify 签名、被 Finished 求 MAC。

它带来的后果是，攻击者对任何一条握手消息的任何篡改（改扩展、删消息、改版本号、替换 key_share……），都会让双方看到的转录序列不一致，从而算出不同的转录哈希，进而（其一）派生出不同的密钥使 AEAD 解密失败，（其二）使 CertificateVerify 签名验不过，（其三）使 Finished 的 MAC 对不上。三重冗余保证任何中途篡改都会被检出、握手中止。这就是"信道绑定"（channel binding）——最终会话密钥被密码学地绑定到"双方实际看到的这一整场握手"，而不只是绑定到 DH 结果。

初学者可以这样理解转录哈希的威力：它像一份两端各自记录的"会议纪要摘要"，握手结束时双方通过 Finished 互相核对摘要是否一致。只要中途有人偷改了任何一句话，两份摘要就对不上，会议当场作废。

### 10.4.4 下调保护（downgrade protection）

TLS 1.3 还在协议里内置了针对"版本降级"的显式检测。当一个支持 TLS 1.3 的服务器因某种原因（或被攻击者操纵）最终协商到 TLS 1.2 或更低版本时，它必须在 ServerHello.random（32 字节随机数）的**最后 8 个字节**填入一个约定的哨兵值，向对面示警。这两个哨兵值前 7 字节是 ASCII "DOWNGRD"（0x44 0x4F 0x57 0x4E 0x47 0x52 0x44），最后一字节区分降到哪一档，两个完整哨兵值如下（左侧为被降到的版本）。

TLS 1.2 → 44 4F 57 4E 47 52 44 01

TLS 1.1 或更低 → 44 4F 57 4E 47 52 44 00

一个同时支持 1.3 的客户端，若发现自己最终协商到较低版本、却在 ServerHello.random 尾部看到这个哨兵，就知道"对面其实支持更高版本，这次降级可疑"，于是中止连接。因为 ServerHello.random 受后续 Finished/签名的转录保护，攻击者无法在不被发现的情况下抹掉这个哨兵。初学者要理解它解决的正是 10.5.1 的降级攻击：没有它，一个 on-path 攻击者可以偷偷把双方都支持 1.3 的连接压到有已知弱点的旧版本。

#### 来源与时效（本小主题末集中列）
- 一手锚点：RFC 8446 §4.4.2（Certificate）、§4.4.3（CertificateVerify：64×0x20 前缀 + 上下文串 "TLS 1.3, server/client CertificateVerify" + 0x00 + 转录哈希）、§4.4.4（Finished：finished_key = HKDF-Expand-Label(BaseKey,"finished","",Hash.length)，verify_data = HMAC(finished_key, Transcript-Hash(...))）、§4.4.1（Transcript-Hash 定义）、§4.1.3（downgrade 哨兵 44 4F 57 4E 47 52 44 01/00 置于 ServerHello.random 末 8 字节）。CertificateVerify/Finished/哨兵值经本报告向 RFC 8446 原文逐条比对核实 2026-07-30。
- 交叉源：Katz-Lindell 3rd ed §13.7（认证密钥交换：签名做认证、MAC 做密钥确认、transcript 绑定）；CS161 Fall 2025 L20（证书 + 签名 + Finished 的认证三件套、防降级）；Stanford CS155 HTTPS。多源一致。核实 2026-07-30。
- 冲突项：无。CertificateVerify 前缀字节数（64）、哨兵字节序列均以 RFC 8446 原文为准。
- 时效：机制稳定 GA，无演进快项。

---

## 10.5 降级与中间人：攻击面与 HTTPS 部署陷阱

### 10.5.1 版本/套件降级攻击

降级攻击（downgrade attack）指 on-path 攻击者篡改握手协商，把双方本可用的强协议/强算法，压到有已知弱点的旧版本或弱套件，再攻击那个弱点。历史上著名案例有 FREAK / Logjam（诱导用弱出口级 RSA/DH 参数）、POODLE（逼回 SSL 3.0 的 CBC 填充漏洞）等，它们都利用了旧 TLS "协商过程本身不受完整性保护、攻击者可静默改动 Hello 里的版本/套件列表"这一缺陷。

TLS 1.3 用两道机制封堵。其一是 10.4.4 的**下调保护哨兵**：降级到 1.2/1.1 会在 ServerHello.random 留下可被检出的痕迹。其二是**转录完整性**（10.4.3）：Finished 与 CertificateVerify 覆盖了整个握手（含版本、支持的组、密码套件列表），攻击者改动 Hello 内容就会导致 MAC/签名校验失败。此外 TLS 1.3 本身砍掉了几乎所有历史弱套件（无 RC4、无 CBC-mode 的老 MAC-then-encrypt、无静态 RSA、无出口级弱参数），把可被降级到的目标大幅收窄。初学者要理解降级攻击的本质是"攻击最弱环节"——安全性取决于双方**愿意接受的最低配置**，所以既要在协议里保护协商完整性，也要在部署上直接禁用弱版本/弱套件。

### 10.5.2 中间人攻击与认证的必要性

中间人（MITM，man-in-the-middle）攻击里，攻击者夹在客户端与服务器之间，对客户端假装自己是服务器、对服务器假装自己是客户端，分别与两端各做一次密钥交换，从而能解密、读取、篡改双向流量。纯粹的 DH 密钥交换**无法**阻止 MITM——因为 DH 不认证对方身份，攻击者完全可以跟你正常做一次 DH。

TLS 阻止 MITM 靠的正是 10.4 的**服务器认证**：攻击者要冒充服务器，就得出示一张"域名匹配、且由客户端信任的 CA 签发"的证书，并用对应私钥对握手转录签名（CertificateVerify）。攻击者没有目标域名的合法私钥，也搞不到 CA 为该域名签发的证书，于是签名过不了、握手失败。所以 TLS 的安全性最终**锚定在 PKI**（大主题 09）：只要 CA 体系可信、客户端正确校验证书（域名匹配、信任链、有效期、未吊销），MITM 就被挡住。反过来，若 CA 被攻破、误签发，或客户端不校验证书，认证就失守——这把 TLS 的信任根引到了 CT（证书透明度）、吊销机制等 PKI 话题。

初学者要建立的因果链，是机密性（临时 DH）、认证（证书签名）、完整性与绑定（transcript + Finished）三者缺一不可。只有 DH 而无认证 = 挡窃听但挡不住 MITM；只有认证而无绑定 = 可能被人拼接/降级握手。TLS 1.3 把三者编排在一轮握手里同时达成。

### 10.5.3 HTTPS 部署陷阱

即使 TLS 协议本身无懈可击，HTTPS 的现实安全仍高度依赖**部署与配置**，常见陷阱可归为下面几类。

第一类是明文入口与降级入口。用户往往先访问 `http://`，若服务器不强制跳转 HTTPS，首个明文请求就可能被 SSL-Stripping（把 https 链接改写成 http）攻击截获。缓解手段是 **HSTS（HTTP Strict Transport Security）**响应头，它指示浏览器今后对该域强制走 HTTPS，并可通过 HSTS preload 列表让连第一次访问也被强制。

第二类是证书校验缺陷。客户端（尤其自研 App、脚本、内部服务）若不校验证书链、不校验域名（hostname mismatch）、或图省事关闭校验（"信任所有证书"），就等于把认证这条腿废掉，MITM 门户大开。这是移动/后端开发中反复出现的高危错误。

第三类是混合内容（mixed content）。HTTPS 页面里若引用了 http:// 的脚本/资源，这些明文子资源可被篡改，进而污染整个页面。

第四类是弱配置遗留。服务器同时开着老旧协议版本（TLS 1.0/1.1、SSL 3.0）或弱套件，会给降级攻击留下目标；私钥保管不当、证书过期、SNI 与证书不匹配等运维问题也常见。

第五类涉及 Cookie 作用域。会话 Cookie 未设 `Secure` 标志会随明文请求泄露，未设 `HttpOnly`/`SameSite` 则与 XSS/CSRF 交织（属 Web 安全大主题）。

初学者应建立的正确心智是把整链看成木桶——TLS 1.3 把"协议层"做到了很强，但安全信道的实战强度等于"协议 + PKI + 客户端校验 + 服务器配置 + 应用层用法"这条链上的最弱环节。协议正确不等于系统安全。

#### 来源与时效（本小主题末集中列）
- 一手锚点：RFC 8446 §4.1.3（下调保护）、§1.2 与 §C（相对旧版删除弱套件/弱密钥交换、实现注意事项）、§E（安全性分析：MITM 依赖认证、转录完整性防降级）。核实 2026-07-30。
- 交叉源：Stanford CS155 HTTPS（SSL-Stripping、HSTS、混合内容、证书校验陷阱）；UC Berkeley CS161 Fall 2025 L20（MITM 与认证必要性、降级攻击）；Katz-Lindell 3rd ed §13.7（认证缺失下 DH 不防 MITM）。历史降级案例（FREAK/Logjam/POODLE）为业界公认，用于说明动机、非承重定量结论。多源在"降级靠协商完整性+删弱套件封堵、MITM 靠证书认证封堵、部署陷阱在协议之外"上一致。核实 2026-07-30。
- 冲突项：无实质分歧。具体历史 CVE 编号与影响面本报告不逐一给数值（非本大主题承重，避免凭记忆编造）。
- 时效：协议层机制稳定 GA。HSTS preload、浏览器对旧版本/弱套件的弃用时间线、以及各站 HTTPS 部署占比 ⚙演进快，属部署面，本报告不锚定具体日期/占比数值（待核，不编造）。
