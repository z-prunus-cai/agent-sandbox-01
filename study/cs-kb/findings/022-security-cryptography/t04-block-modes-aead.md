# L5-04·大主题04 分组工作模式与认证加密(AEAD)

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：G11-03 对称加密与分组密码（PRP/分组密码、CPA 安全、AES）；软 ← L3-02（字节/位运算）｜ 一手锚点：Katz & Lindell《Introduction to Modern Cryptography》3rd ed，Ch5（CCA & Authenticated Encryption）、Ch7（实用对称原语），https://www.cs.umd.edu/~jkatz/imc.html ；NIST SP 800-38A（2001，分组工作模式）、SP 800-38C（2004，CCM）、SP 800-38D（2007，GCM/GMAC），https://csrc.nist.gov/ ；IETF RFC 8439（2018，ChaCha20-Poly1305），https://www.rfc-editor.org/rfc/rfc8439.html ；UC Berkeley CS161（Fall 2025）L8，https://fa25.cs161.org/ ｜ 成熟度：SP 800-38A/38C、RFC 8439、EtM 组合 GA/稳定；SP 800-38D 现行但 NIST 于 2024 宣布修订（⚙ 修订中，见 4.4/4.5 末）

> 一条主线心智模型：**分组密码（AES）本身只会加密"一块"固定长度的数据，它是零件不是整机；"工作模式"就是把这个零件安全地拼成能加密任意长消息的整机——而拼错（ECB、复用 IV/nonce、只加密不认证）就是本大主题里一连串灾难的根源。** 现代共识是：不要单独用加密模式，直接用认证加密（AEAD），它一次给你机密性+完整性+来源认证。

> 本主题以构造与攻击原理辨析为主。已在仓库外 scratchpad 用纯 Python 做两处**可选**加固（因 brief 载明 pycryptodome 不随容器持久，实证用 SHA-256 作教学级 keystream 生成器代替 AES，仅演示模式结构、不代表真实 AES 输出）：4.2 CTR 模式加解密自洽往返、4.5 nonce 复用导致 c1⊕c2=m1⊕m2 的两次一密泄露（真跑验证等式成立且可由已知 m1 完全恢复 m2）。脚本与真实输出见文末。实证仅为补充；多来源比对（Katz-Lindell 3rd ed × NIST SP 800-38 系列 × RFC 8439 × CS161）才是正确性主承重。未做实证的条目如实标注。

---

## 4.1 ECB 的危险

### 4.1.1 分组工作模式是什么，ECB 作为最朴素模式

分组密码（如 AES）是一个带密钥的定长置换：给定密钥 K，它只能把恰好一个分组（AES 是 128 位 = 16 字节）的明文映射成同样长度的密文。真实消息几乎总比一个分组长，于是需要"工作模式（mode of operation）"规定如何把消息切成多个分组、逐块调用分组密码、并把结果拼起来。电子密码本模式（ECB，Electronic Codebook）是最直接的做法：把明文切成分组，每块**独立**加密。

第 i 块的加解密写作

C_i = E_K(P_i)

P_i = D_K(C_i)

初学者要抓住的关键词是"独立"和"确定性"：每块加密只看自己、不看别块，且相同明文块在相同密钥下永远得到相同密文块。名字里的"码本"正是这个意思——它像一本查得到的字典，同一个明文块查出来永远是同一个密文块。这两点直接埋下了下一节的祸根。

### 4.1.2 确定性导致的结构泄露（"企鹅图"）

ECB 的致命问题是：明文中的重复模式会原封不动地暴露到密文里。因为相同明文块 → 相同密文块，攻击者即使解不出内容，也能看出"哪些块彼此相等"，从而读出数据的结构、重复、边界。

最著名的直观演示就是所谓"ECB 企鹅（Tux）图"：把一张有大片纯色区域的位图用 ECB 加密后，纯色区域的重复像素块变成重复密文块，肉眼仍能辨认出企鹅的轮廓；而用 CBC/CTR 等模式加密后图像变成看不出结构的噪声。这个例子几乎出现在每一本教材里，用来说明"看起来是乱码，不等于没泄露信息"。

对初学者，容易忽视的一点是：泄露"哪些块相等"本身就可能是严重信息。例如一个数据库字段用 ECB 加密，攻击者能统计出哪些记录字段值相同（同一病种、同一薪资档），或在已知格式的协议里定位重复的头部/字段。机密性的现代标准（CPA 安全 / 语义安全）要求"密文不泄露关于明文的任何可计算信息"，而 ECB 连"两条明文是否相等"都藏不住，因此从定义上就不达标。

### 4.1.3 为何标准与实践一致禁用/不推荐 ECB

结论是明确的：ECB **不提供**语义安全 / CPA 安全，除非在极特殊场景（如加密单个不重复的随机分组），否则不应用于加密数据。NIST SP 800-38A 把 ECB 列为一种模式，但学界与工程界的共识（Katz-Lindell、CS161 及主流库文档）是把它当反面教材，实际加密一律改用带随机化的模式或直接用 AEAD。

任何"确定性"的加密都无法达到 CPA 安全，因为 CPA 安全要求同一明文加密两次得到看起来无关的两条密文（这样攻击者无法通过"重放-比对"判断你加密了什么）。ECB 的病根就是确定性 + 分块独立。修正方向有两条——让加密带上每次都不同的随机量（IV/nonce），或者让每块依赖前面的块，这正是下节 CBC/CTR 的思路。

ECB 的问题不是 AES 弱，而是"用法"错。同一个 AES 放进 CBC/CTR/GCM 里就安全，放进 ECB 里就泄露。密码学里"原语正确 + 用法错误 = 不安全"是最常见的翻车方式，本大主题反复出现。

#### 来源与时效
- 锚点：NIST SP 800-38A（2001）§6.1 ECB 模式定义（C_j = CIPH_K(P_j)），https://csrc.nist.gov/pubs/sp/800/38/a/final ，核实 2026-07-30。
- 锚点：Katz & Lindell 3rd ed，Ch3/Ch5 关于确定性加密不满足 CPA 安全、ECB 泄露明文重复结构的论述；核实 2026-07-30。
- 交叉锚：UC Berkeley CS161（Fall 2025）L8（block cipher modes，ECB penguin 示例与禁用理由），https://fa25.cs161.org/ ，核实 2026-07-30。
- 冲突项：三源一致——SP 800-38A 收录 ECB 作为"模式定义"但不背书其安全性，教材/课程明确将其判为不安全用法，非实质分歧。

## 4.2 CBC 与 CTR 模式

### 4.2.1 CBC（密码分组链接）模式

CBC（Cipher Block Chaining）通过"链接"消除 ECB 的确定性：加密每一块前，先把它与前一块的密文异或，第一块则与一个初始向量 IV 异或。设 C_0 = IV，则

C_i = E_K(P_i ⊕ C_{i-1})

解密反过来：

P_i = D_K(C_i) ⊕ C_{i-1}

由于每块都被前面所有块（经由链接）影响，且 IV 每次不同，相同明文加密两次会得到完全不同的密文，重复结构被打散。

初学者要记住三点。其一，CBC 解密用的是分组密码的**解密**函数 D_K，所以 CBC 要求底层是可逆置换（PRP），这与 CTR 不同。其二，IV 必须随每条消息变化——SP 800-38A 明确要求 CBC 的 IV **不可预测**（unpredictable，实践中取随机）；若 IV 可被攻击者预测，会招致选择明文下的区分攻击（著名的 TLS 1.0 BEAST 攻击即源于可预测 IV）。其三，CBC 是**串行**的：加密第 i 块要等第 i-1 块的密文算完，无法并行加密（但解密可并行，因为每个 P_i 只依赖 C_i 和 C_{i-1}）。

### 4.2.2 CTR（计数器）模式

CTR（Counter）模式换了个思路：不直接加密明文，而是用分组密码加密一串"计数器块"生成一段伪随机的密钥流（keystream），再把密钥流与明文异或。每个计数器块由一个 nonce（一次性数）拼接一个递增计数器构成。第 i 块为

C_i = P_i ⊕ E_K(nonce ‖ counter_i)

P_i = C_i ⊕ E_K(nonce ‖ counter_i)

注意加密和解密用的是**同一个**运算（都是"异或密钥流"），因为异或自反。

对初学者，CTR 最反直觉又最重要的一点是：它**只用到分组密码的加密方向 E_K，从不调用 D_K**。这意味着底层只需是伪随机函数（PRF）即可，无需可逆。CTR 天然**可并行**（每个计数器块相互独立，可同时算、可随机访问任意块），且明文无需填充到整块——这两点让它在高性能场景很受欢迎。代价是：nonce/counter 组合绝不能在同一密钥下重复，否则密钥流复用，酿成 4.5 讲的两次一密灾难。

### 4.2.3 IV 与 nonce 的语义区别（易错核心）

CBC 的 IV 和 CTR 的 nonce 都是"每条消息一个的公开随机量"，但安全要求不同，混用会出事。

CBC 要求 IV **不可预测**（通常直接取随机）；仅仅"唯一"不够，因为可预测的 IV 会被选择明文攻击利用。CTR 要求 nonce 在同一密钥下**唯一**（不重复即可，甚至可用递增计数器，不必随机），但绝不能复用。

CBC-IV：要"随机/不可预测"

CTR-nonce：要"唯一/不重复"

初学者常见错误是"反正都叫 IV，随便给个 0 或固定值"。对 CBC，固定 IV 退化成确定性加密（重蹈 ECB 覆辙）；对 CTR，复用 nonce 直接让两条消息共享密钥流。IV/nonce 一般随密文明文传输（它是公开的，不是密钥），但它的正确生成方式是安全的一部分。

### 4.2.4 其他机密性模式与并行性概览（CFB/OFB）

SP 800-38A 共定义五种机密性（confidentiality-only）模式：ECB、CBC、CFB（密码反馈）、OFB（输出反馈）、CTR。CFB 和 OFB 与 CTR 类似，也是"生成密钥流再异或"的自同步/同步流式模式，同样只用加密方向、无需填充；区别在密钥流的产生方式（CFB 反馈密文、OFB 反馈自身输出、CTR 用独立计数器）。实践中 CTR 因可并行、可随机访问而胜出，CFB/OFB 已较少新用。

ECB/CBC 是"分组式"（明文进分组密码，需要填充、CBC 串行加密），CFB/OFB/CTR 是"流式"（生成密钥流异或明文、无需填充）。这一层认识足以理解为什么现代 AEAD（GCM、ChaCha20-Poly1305）内部都建立在 CTR 类的密钥流之上——流式模式的并行性与无填充特性更适合做高性能认证加密的底座。

### 4.2.5 无认证模式的可锻性——引出完整性缺口

CBC/CTR/CFB/OFB 都只提供**机密性**，不提供完整性；它们全部是"可锻的（malleable）"——攻击者不需要密钥就能对密文做可预测的篡改。

以 CTR 为例，因为 C_i = P_i ⊕ KS_i，攻击者翻转密文某一位，解出的明文对应位就随之翻转（KS 不变）。所以要把"转账 $10"改成"转账 $90"，攻击者只需在密文相应字节上异或一个已知差值——完全不必解密。CBC 也有类似的位翻转与块重排效应（改 C_{i-1} 的某位，会以可预测方式改 P_i 的对应位，同时把 P_{i-1} 打成乱码）。

这就是本大主题从 4.3 走向 4.4 的动机：只有机密性远远不够，必须叠加完整性/认证。历史上无数漏洞（padding oracle、比特翻转攻击）都源于"以为加密了就安全"。现代结论是：**永远不要裸用机密性模式，要用认证加密（AEAD）**。

#### 来源与时效
- 锚点：NIST SP 800-38A（2001）§6.2 CBC（C_1 = CIPH_K(P_1 ⊕ IV)…）、§6.5 CTR、附录 C 关于 IV 生成（CBC 的 IV 需 unpredictable、CTR 的 counter 需 unique）；https://csrc.nist.gov/pubs/sp/800/38/a/final ，核实 2026-07-30。
- 锚点：Katz & Lindell 3rd ed，Ch3（CPA-secure 加密的 CBC/CTR 构造与随机 IV/nonce 要求、可锻性讨论）；核实 2026-07-30。
- 交叉锚：UC Berkeley CS161（Fall 2025）L8（CBC/CTR 结构、IV vs nonce、并行性、malleability），https://fa25.cs161.org/ ，核实 2026-07-30。
- 冲突项：三源一致；对"IV 不可预测 vs 唯一"的措辞 SP 800-38A 最精确（CBC=unpredictable、CTR=unique），教材/课程与之一致，无分歧。

## 4.3 填充与填充预言攻击

### 4.3.1 分组填充与 PKCS#7

CBC、ECB 这类分组式模式要求明文长度是分组长度的整数倍，而真实消息通常不是，因此加密前要"填充（padding）"补齐。最常用的是 PKCS#7 填充：若还差 n 个字节补满一块，就补 n 个值均为 n 的字节；若明文恰好整块，则**额外再补满一整块**（每字节都是 blocklen），以便解密方能无歧义地判断并去掉填充。

举例（分组 8 字节）：明文剩 5 字节，则补 3 个 `0x03`；明文剩满 8 字节，则补 8 个 `0x08`。解密后读最后一个字节 n，检查末尾 n 个字节是否都等于 n，是则去掉、否则报"填充错误"。

初学者要理解填充的目的仅是"对齐 + 可无歧义还原"，它本身不提供任何安全性。真正的隐患不在填充规则，而在"解密方如何反馈填充是否合法"——这正是填充预言攻击的入口。

### 4.3.2 填充预言（padding oracle）攻击原理

填充预言攻击由 Serge Vaudenay 于 2002 年（Eurocrypt'02，《Security Flaws Induced by CBC Padding》）提出。前提是：系统作为一个"预言机"，对攻击者提交的任意密文，会（哪怕间接地，通过错误消息、响应时间、连接行为）泄露"解密后的填充是否合法"这一位信息。攻击者利用它，在**完全不知道密钥**的情况下逐字节恢复明文。

原理建立在 CBC 解密式上。记中间值

I_i = D_K(C_i)

则

P_i = I_i ⊕ C_{i-1}

攻击者控制并篡改前一块密文 C_{i-1}（记为构造出的 C'），提交 (C', C_i) 给预言机。解密方算出

P' = D_K(C_i) ⊕ C' = I_i ⊕ C'

攻击者逐字节爆破 C' 的最后一字节，直到预言机回报"填充合法"（此时 P' 末字节多半是 `0x01`）。由此解出 I_i 的该字节，再异或真实的 C_{i-1} 就得到真实明文 P_i 的该字节。然后把目标填充值推进到 `0x02 0x02`、`0x03 0x03 0x03`……逐字节向左恢复整块。

1. 目标：恢复密文块 C_i 对应的明文 P_i。
2. 构造伪造前块 C'，从最后一字节开始，256 种取值逐一试。
3. 预言机回报"填充合法" ⇒ 推出 I_i 该字节 = C'该字节 ⊕ 目标填充值。
4. 由 P_i 字节 = I_i 字节 ⊕ 真实 C_{i-1} 字节，得该明文字节。
5. 目标填充值加一、左移一位，重复直到整块恢复。

每字节平均约 128 次、最多 256 次询问即可，效率远高于暴力破解——Vaudenay 指出它比 RSA 上的 Bleichenbacher 攻击还高效。历史上 ASP.NET、Ruby on Rails、OpenSSL、以及 TLS 的 Lucky13 变体都栽在这类问题上。

### 4.3.3 缓解与教训

正确的缓解不是"改填充算法"，而是消除那一位泄露与从根上加认证：

- 用认证加密（AEAD）或 Encrypt-then-MAC：先验证 MAC，MAC 不过就根本不解密、不看填充，攻击者拿不到任何填充信息（见 4.4）。
- 不向外区分"填充错误"与"MAC/其他错误"：对所有解密失败返回同一个不可区分的错误。
- 做成常数时间（constant-time）：避免用响应时间泄露填充是否合法（Lucky13 正是利用了 MAC-then-Encrypt 下的时间差）。

初学者要带走的核心教训有两条。其一，"一位泄露"就能崩盘：系统只不过区分了两种错误，攻击者就能逐字节解出全部明文，这说明侧信道（连错误消息、时间都算）是真实威胁。其二，这再次印证"机密性模式必须配认证"——若先认证再解密，被篡改的密文在验 MAC 阶段就被拒，根本走不到解填充那一步。这直接引出 4.4 的 AEAD。

#### 来源与时效
- 锚点：Serge Vaudenay,《Security Flaws Induced by CBC Padding — Applications to SSL, IPSEC, WTLS...》，Eurocrypt 2002，https://www.iacr.org/archive/eurocrypt2002/23320530/cbc02_e02d.pdf ，核实 2026-07-30。
- 锚点：PKCS#7 填充定义见 IETF RFC 5652（CMS）§6.3；核实 2026-07-30。
- 交叉锚：Katz & Lindell 3rd ed，Ch5（CCA 安全、padding oracle 作为 CCA 攻击的经典范例）；UC Berkeley CS161（Fall 2025）L8（padding oracle 原理与缓解），https://fa25.cs161.org/ ，核实 2026-07-30。
- 冲突项：无实质分歧；各源一致地把 padding oracle 归为 CCA 类攻击，缓解共识为"AEAD/EtM + 不可区分错误 + 常数时间"。

## 4.4 认证加密(AEAD)

### 4.4.1 为什么需要 AEAD（机密性 ≠ 完整性）

认证加密（Authenticated Encryption, AE）是同时提供机密性与完整性/来源认证的加密方案；带关联数据的版本称 AEAD（AE with Associated Data），是当代加密的默认选择。它的安全目标记为 CCA 安全 + 密文完整性（ciphertext integrity）：攻击者既问不出明文信息，也无法伪造出任何一条会被接收方接受的新密文。

前面 4.2.5、4.3 已反复说明动机：CBC/CTR 只保证"读不懂"，却挡不住"被改"。攻击者可以翻转位、重放、拼接密文，接收方毫不知情地解出被篡改的明文。AEAD 用一个消息认证码（MAC / 认证标签 tag）把密文钉死——任何改动都会让标签验证失败。

对初学者，一句话记忆：**加密防偷看，认证防篡改，AEAD 两者一起给你，还顺带认证一段不加密但要防改的"关联数据"。**

### 4.4.2 通用组合三法与 Encrypt-then-MAC

把一个 CPA 安全的加密方案和一个安全 MAC 拼成 AE，有三种"通用组合（generic composition）"方式（Bellare & Namprempre 2000/2008 的经典分析）：

- Encrypt-and-MAC（E&M）：c = Enc(m)，t = MAC(m)，发送 (c, t)。
- MAC-then-Encrypt（MtE）：t = MAC(m)，c = Enc(m‖t)，发送 c。
- Encrypt-then-MAC（EtM）：c = Enc(m)，t = MAC(c)，发送 (c, t)。

结论（现代共识）：只有 **Encrypt-then-MAC 在通用意义上可靠**——先加密、再对**密文**算 MAC，接收方先验 MAC 再解密。

EtM 收发步骤：

发送：c = Enc_{K1}(m)；t = MAC_{K2}(c ‖ AD)；发送 (c, t, AD)

接收：先验 t 是否等于 MAC_{K2}(c ‖ AD)，不等则立即拒绝（绝不解密）；相等才 m = Dec_{K1}(c)

初学者要抓住两点。其一，MAC 要算在**密文**上而不是明文上，且验证要在解密**之前**——这样被篡改的密文在验 MAC 阶段就被拦下，padding oracle、Lucky13 这类"解密过程泄露信息"的攻击面被直接切断。其二，加密和 MAC 要用**独立的密钥**（K1≠K2），不可复用同一把钥匙。E&M（如老式 SSH）和 MtE（如 TLS 1.0 的 CBC 套件）历史上都出过问题，正是它们让攻击者能触发解密再观察行为。

### 4.4.3 关联数据（AD）的含义

AEAD 的"AD"指关联数据（Associated Data）：一段**需要认证但不需要加密**的数据。典型例子是网络包的头部（版本、序号、地址）——它必须公开可读（路由要用），但绝不能被篡改。AEAD 把 AD 一并纳入标签计算，改了 AD 也会验证失败，却不加密 AD。

一条 AEAD 密文实际绑定了三样东西——被加密的明文、明文之外要防改的 AD、以及 nonce。接收方只有在密文、AD、nonce 三者都与发送时一致时才通过验证。这让 AEAD 能防"把合法密文搬到另一个上下文重放"的攻击。

### 4.4.4 GCM 构造（NIST SP 800-38D）

GCM（Galois/Counter Mode）是最主流的 AEAD，NIST SP 800-38D 标准化。它把 CTR 模式的机密性和一个在伽罗瓦域 GF(2^128) 上的通用哈希 GHASH 组合起来，本质是 Encrypt-then-MAC 的高效专用实例，且加密与认证都可并行。

其骨架为：

用 CTR 模式加密明文得到密文 C（计数器块从 J_0 的后继开始）

哈希子密钥 H = E_K(0^128)

GHASH 在 GF(2^128) 上对（关联数据 A ‖ 密文 C ‖ 长度块）做多项式求值，约化多项式为

x^128 + x^7 + x^2 + x + 1

认证标签 T = GHASH_H(A, C) ⊕ E_K(J_0)

其中 J_0 由 nonce（IV）导出：当 IV 恰为 96 位时 J_0 = IV ‖ 0^31 ‖ 1，否则 J_0 也经 GHASH 折叠得到。

初学者不必掌握 GF(2^128) 的代数细节，只需把握三件事。其一，GCM = "CTR 加密 + GHASH 认证"，所以它继承了 CTR 的高性能与并行性。其二，SP 800-38D 推荐 IV 长度为 96 位（此时 J_0 构造最简、效率最高）；标签长度可取 128/120/112/104/96 位（更短的 64/32 位仅限特定受限应用）。标签长度与 IV 长度是**独立**选择的——SP 800-38D 并不存在"IV 非 96 位就必须用 128 位标签"这类耦合规则（此前版本此处曾误加该约束，已订正）。其三，GHASH 是"以 H 为密钥的通用哈希"，不是普通哈希——它只在配合每消息不同的 E_K(J_0) 掩码时才安全，因此 nonce 绝不能复用（见 4.5.5，GCM 的 nonce 复用尤其致命，会同时泄露 H 从而伪造标签）。

### 4.4.5 CCM 构造（NIST SP 800-38C）

CCM（Counter with CBC-MAC）是另一种标准 AEAD，NIST SP 800-38C 标准化（注意：CCM 是 800-38**C**，GCM 是 800-38**D**；本大主题原始清单把两者并列在 38D 下，实为分属两份规范）。CCM 用 CTR 模式做加密、用 CBC-MAC 做认证，采用 MAC-then-Encrypt 风格但经过专门证明是安全的（它不是"裸"的通用组合）。CCM 在无线（如 WPA2 的 AES-CCMP）、受限设备中常见。

对初学者，CCM 与 GCM 的实用对比：GCM 认证可并行、性能通常更高（尤其有硬件 GHASH/PCLMULQDQ 指令时），是 TLS 1.3 的主力；CCM 结构更简单、只依赖分组密码本身（不需额外的域乘法器），适合硬件受限场景，但需两遍处理数据。两者都要求 nonce 唯一。

### 4.4.6 AEAD 安全定义与 nonce 要求

AEAD 的安全性通常表述为 nonce-based：只要对每条消息使用**唯一的 nonce**，方案就同时满足 CPA 机密性与密文完整性（攻击者无法伪造/篡改）。这把"安全"的责任明确压到"nonce 不重复"这一条工程约束上。

SP 800-38D 对 GCM 给出量化限制：在给定密钥下，认证加密调用总数不得超过 2^32 次，除非仅使用由确定性构造生成的 96 位 IV（此时限制可放宽）；这是为控制 nonce 碰撞概率与 GHASH 的伪造界。这类"用量上限"是 AEAD 部署里常被忽视却真实存在的边界。

AEAD 不是"银弹到可以乱用"。它把大量安全性换成了一条清晰的纪律——**每个 (key, nonce) 只用一次**。违反它（4.5.5）会瞬间摧毁机密性甚至认证。选择 AEAD 是对的，但仍要正确管理 nonce（随机 96 位 + 限量，或严格递增计数器）。

NIST 于 2024 宣布将修订 SP 800-38D（拟移除长度小于 96 位的标签、明确 TLS 1.3 的 IV 构造为批准用法、澄清 IV 构造指南）。截至核实日 2026-07-30，现行有效版本仍为 2007 年 11 月版；修订稿状态属 ⚙ 演进中，具体条款以最终发布为准（待核）。

#### 来源与时效
- 锚点：NIST SP 800-38D（2007-11，GCM/GMAC）——H=E_K(0^128)、GHASH 约化多项式 x^128+x^7+x^2+x+1、96 位 IV 推荐、标签 96–128 位、2^32 调用上限；https://csrc.nist.gov/pubs/sp/800/38/d/final ，核实 2026-07-30。⚙ NIST 2024 修订公告：https://csrc.nist.gov/news/2024/nist-to-revise-sp-80038d-gcm-and-gmac-modes 。
- 锚点：NIST SP 800-38C（2004，CCM = CTR + CBC-MAC）；https://csrc.nist.gov/pubs/sp/800/38/c/final ，核实 2026-07-30。
- 锚点：M. Bellare & C. Namprempre,《Authenticated Encryption: Relations among Notions and Analysis of the Generic Composition Paradigm》（2000；J. Cryptology 2008）——E&M/MtE/EtM 三法，EtM 通用可靠，https://eprint.iacr.org/2000/025 ，核实 2026-07-30。
- 交叉锚：Katz & Lindell 3rd ed，Ch5（AE/AEAD 定义、密文完整性、EtM、GCM）；UC Berkeley CS161（Fall 2025）L8。
- 冲突项：原始小主题清单把"GCM/CCM 构造"统标 SP 800-38D，经比对 CCM 实属 SP 800-38C、GCM 属 SP 800-38D，本报告按规范分账更正，不构成来源冲突而是清单笔误。

## 4.5 流密码与 ChaCha20-Poly1305

### 4.5.1 流密码原理及其与 CTR 的关系

流密码（stream cipher）不把明文切块过置换，而是用密钥（和 nonce）生成一段伪随机的密钥流（keystream），再逐字节/逐位与明文异或：

C = P ⊕ KS

P = C ⊕ KS

加密解密是同一个异或操作。CTR 模式其实就是"用分组密码构造出的流密码"——它把 AES 当成密钥流发生器。因此流密码与 CTR 共享同样的性质：无需填充、可并行/随机访问、以及同一个致命弱点——密钥流绝不能复用。

流密码的全部安全性都压在"密钥流看起来随机且不重复"上。密钥流一旦对两条消息相同，异或的自反性立刻把秘密暴露（下见 4.5.5）。历史上 RC4 曾是最广泛的流密码，但因密钥流存在统计偏差等缺陷已被淘汰（RFC 7465 于 2015 年禁止在 TLS 中使用 RC4），现代首选是 ChaCha20。

### 4.5.2 ChaCha20 流密码结构（RFC 8439）

ChaCha20 是 Daniel Bernstein 设计、由 RFC 8439 标准化的流密码（RFC 8439 于 2018 年发布，废止并取代 RFC 7539，主要把 nonce 从 64 位统一为 96 位）。它维护一个 16 个 32 位字（512 位）的状态，布局为：4 个常量字 + 8 个密钥字（256 位密钥）+ 1 个块计数器字 + 3 个 nonce 字（96 位 nonce）。四个常量字固定为

0x61707865, 0x3320646e, 0x79622d32, 0x6b206574

核心是"quarter round" QR(a,b,c,d)，只用 32 位模加、异或、循环左移三种运算（一种 ARX 设计，天然抗时序侧信道）：

a += b; d ^= a; d <<<= 16

c += d; b ^= c; b <<<= 12

a += b; d ^= a; d <<<= 8

c += d; b ^= c; b <<<= 7

一次对状态施加 4 个"列"quarter round + 4 个"对角"quarter round 记为 2 轮，重复 10 次即 20 轮（这就是名字里的"20"）；最后把结果与初始状态逐字相加，得到 64 字节密钥流块，再与明文异或。

ChaCha20 用 256 位密钥、96 位 nonce、32 位块计数器；块计数器 32 位意味着单个 (key, nonce) 最多产 2^32 个 64 字节块 ≈ 256 GB 密钥流（超出须换 nonce）。它全程只用加/异或/移位、没有查表，因此实现天然常数时间，在无 AES 硬件加速的设备上通常比 AES 快，是移动端与 TLS 1.3 的主力之一。

### 4.5.3 Poly1305 消息认证码

Poly1305 是与 ChaCha20 配套的一次性（one-time）MAC：给定一把每消息唯一的一次性密钥（记为 (r, s)，由 ChaCha20 用该消息的 (key, nonce) 现场派生），把消息切成若干块当作多项式系数，在素数域上求值再加掩码，得到 16 字节标签。其核心在模

p = 2^130 − 5

上进行（这也是名字 1305 的由来），标签 ≈ ((∑ 消息块·r^i) mod p + s) mod 2^128。

初学者只需把握两点。其一，Poly1305 是"一次性 MAC"——每条消息必须用不同的 (r, s)，而 (r, s) 又由 (key, nonce) 派生，所以 nonce 复用不仅泄露明文（4.5.5），还会泄露认证密钥使攻击者能伪造标签，后果比机密性丢失更严重。其二，它属于"通用哈希 + 一次性掩码"这一族（与 GCM 的 GHASH 同源思路），速度极快，是其被广泛采用的原因。

### 4.5.4 ChaCha20-Poly1305 AEAD（RFC 8439）

RFC 8439 把 ChaCha20 与 Poly1305 组合成一个 AEAD 算法，是 GCM 之外 TLS 1.3 的另一主力套件。其编排为 Encrypt-then-MAC 风格：

1. 用 ChaCha20 以块计数器 0 生成一块，取其前 32 字节作为本消息的 Poly1305 一次性密钥 (r, s)。

2. 用 ChaCha20 从块计数器 1 起生成密钥流，加密明文得密文 C。

3. 用 Poly1305 对（关联数据 A ‖ 零填充 ‖ 密文 C ‖ 零填充 ‖ len(A) ‖ len(C)）算 16 字节标签 T。

4. 输出 (C, T)；解密方先验 T 再解密。

初学者对照 GCM 记忆即可：两者都是"CTR 类流式加密 + 通用哈希类一次性 MAC + EtM"的同构范式，区别只在底层原语（AES-CTR/GHASH vs ChaCha20/Poly1305）。选 ChaCha20-Poly1305 的典型理由是没有 AES 硬件加速时更快、且天然常数时间抗侧信道。

### 4.5.5 nonce 复用灾难

流密码（含 CTR、ChaCha20）与所有 nonce-based AEAD 的头号死穴是：**同一密钥下复用 nonce**。一旦复用，两条消息得到相同密钥流 KS：

C1 = P1 ⊕ KS

C2 = P2 ⊕ KS

C1 ⊕ C2 = P1 ⊕ P2

密钥流被消掉，攻击者拿到两条明文的异或。这就是经典的"两次一密（two-time pad）"：借助语言冗余（拖字、频率）或只要已知其中一条明文，另一条即可被完全恢复。本报告在 scratchpad 真跑验证了该等式成立、且由已知 m1 完整恢复出 m2（见文末）。

对认证型 AEAD，nonce 复用更致命：GCM 复用 nonce 会泄露哈希子密钥 H，使攻击者不仅读出明文异或、还能**伪造任意消息的合法标签**（著名的"forbidden attack"）；ChaCha20-Poly1305 复用 nonce 会泄露 Poly1305 的一次性密钥，同样导致伪造。

- 每个 (key, nonce) 只用一次；nonce 用严格递增计数器，或足够长（96 位）的随机数并限制单密钥消息数。

- 换密钥就可以重置 nonce 计数（安全边界是 (key, nonce) 对，不是 nonce 本身全局唯一）。

- 若无法保证 nonce 唯一，改用"抗 nonce 误用"的方案（如 AES-GCM-SIV，RFC 8452），它在 nonce 意外复用时只泄露"两条消息是否相同"而不崩盘。

### 4.5.6 RC4 与历史流密码（反例）

RC4 是历史上部署最广的流密码（曾用于 WEP、早期 TLS、WPA-TKIP）。它简单快，但密钥流存在可检测的统计偏差（尤其前若干字节），加上 WEP 里错误的 IV 拼接用法，导致一系列实用攻击。IETF 于 2015 年以 RFC 7465 明令禁止在 TLS 中使用 RC4。

初学者从 RC4 应带走的教训与本大主题主线一致：其一，流密码的密钥流必须"看起来真随机且不重用"，任何偏差或复用都致命；其二，原语弱（RC4 的偏差）与用法错（WEP 的 IV 复用）会叠加放大。现代选择是 ChaCha20（+Poly1305）或 AES-GCM，不再用裸流密码、更不用 RC4。

#### 来源与时效
- 锚点：IETF RFC 8439（2018-06，ChaCha20 and Poly1305 for IETF Protocols，废止 RFC 7539）——16 字状态、常量 0x61707865/0x3320646e/0x79622d32/0x6b206574、quarter round、20 轮、96 位 nonce、Poly1305（p=2^130−5）与 AEAD 组合；https://www.rfc-editor.org/rfc/rfc8439.html ，核实 2026-07-30。
- 锚点：Katz & Lindell 3rd ed，Ch3（流密码 / 同步流密码与 nonce）、Ch7（ChaCha20、实用 AEAD）；核实 2026-07-30。
- 交叉锚：NIST SP 800-38D（2007）关于 GCM nonce 复用泄露 H（配合独立文献 J. Böck 等"forbidden attack"）；IETF RFC 7465（2015，Prohibiting RC4 Cipher Suites）；IETF RFC 8452（2019，AES-GCM-SIV，抗 nonce 误用）；均核实 2026-07-30。
- 冲突项：无实质分歧。RFC 8439 vs 旧 RFC 7539 的差异（nonce 64→96 位、块计数 64→32 位）已在正文按 8439 现行版为准标注；Poly1305 标签计算的精确系数属实现细节，本报告按 RFC 8439 教学级概述，未逐系数展开（如需精确公式以 RFC 8439 §2.5 为准）。

---

## 附：可选实证（仓库外 scratchpad，教学级，不代表真实 AES/ChaCha 输出）

因 brief 载明 pycryptodome/加密库不随容器持久，下列演示用 Python 标准库 `hashlib.sha256` 作教学级密钥流发生器代替真实分组密码，仅演示 CTR/流密码的**结构**与 nonce 复用的**代数后果**，不作为 AES 或 ChaCha20 的实现或测试向量。多来源比对（见各章"来源与时效"）才是正确性主承重。

复现脚本（`aead_demo.py`）：

```python
import hashlib, os

def keystream_block(key, nonce, ctr):
    return hashlib.sha256(key + nonce + ctr.to_bytes(8, 'big')).digest()  # 32-byte block

def ctr_crypt(key, nonce, data):
    out = bytearray()
    for i in range(0, len(data), 32):
        ks = keystream_block(key, nonce, i // 32)
        blk = data[i:i+32]
        out += bytes(a ^ b for a, b in zip(blk, ks))
    return bytes(out)

key = os.urandom(16); nonce = os.urandom(8)
msg = b"Attack at dawn. Bring the whole army immediately, no delay!"
ct = ctr_crypt(key, nonce, msg)
pt = ctr_crypt(key, nonce, ct)   # CTR: same op decrypts
print("CTR round-trip OK:", pt == msg)

# Nonce reuse: same (key,nonce) on two messages => two-time pad
m1 = b"HELLO ALICE, transfer $10 to BOB pls-----------------------"
m2 = b"SECRET: launch codes are 0000 and the vault pin is 1337!!!!"
c1 = ctr_crypt(key, nonce, m1)   # REUSING the same nonce -- the fatal error
c2 = ctr_crypt(key, nonce, m2)
xor_ct = bytes(a ^ b for a, b in zip(c1, c2))
xor_pt = bytes(a ^ b for a, b in zip(m1, m2))
print("c1 XOR c2 == m1 XOR m2 :", xor_ct == xor_pt)
recovered_m2 = bytes(a ^ b for a, b in zip(xor_ct, m1))
print("recovered m2 given m1 :", recovered_m2 == m2)
```

真实输出（Python 3.11.15 @2026-07-30）：

```
CTR round-trip OK: True
c1 XOR c2 == m1 XOR m2 : True
recovered m2 given m1 : True
```

第一行印证 4.2.2：CTR 加解密是同一异或操作。后两行印证 4.5.5：nonce 复用使密钥流相消，c1⊕c2 恰等于 m1⊕m2，且已知 m1 即可完整恢复 m2——两次一密灾难。
