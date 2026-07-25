# Round 3a · 第 11 组「安全与密码」· 大主题分解

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25
> 课程：**L5-04 信息安全/密码学**（SEC + MSF 数论）**并入 N-15 差分隐私/隐私保护 ML**（round1-map §3-C，横切）。
> 硬先修（round1-map §2-A4）：**L1-02（离散/数论）+ L3-02（组成/系统）**；网络 L4-02、OS L4-01 为子方向软依赖。
> 目标：把这门大课拆成**查全的教学单元级大主题清单**，覆盖全、少重叠、按教学序。核实日期 2026-07-25。

---

## 0 · 权威锚点（一手优先，全部核实 2026-07-25）

这门课有两条正交主线，锚点按主线分账：

### A. 密码学构造侧（数学承重）
| 锚点 | 定位 | 版本 | URL | 核实 |
|------|------|------|-----|------|
| **Katz & Lindell,《Introduction to Modern Cryptography》** | 全书 TOC（本地抽取 PDF 目录/前言坐实结构） | **第 3 版** | https://www.cs.umd.edu/~jkatz/imc.html （TOC PDF: imc/toc-preface-3rd.pdf） | ✓ TOC 已抽取 |
| **Handbook of Applied Cryptography (HAC)** | 数论/公钥承重（免费官方 PDF） | Menezes 等，1996 | https://cacr.uwaterloo.ca/hac/ | ✓ |
| **NIST FIPS 197 (AES)** | 分组密码标准 | 现行 | https://csrc.nist.gov/pubs/fips/197/final | ✓ |
| **NIST FIPS 180-4 (SHA-2) / 202 (SHA-3)** | 哈希标准 | 现行 | https://csrc.nist.gov/ | ✓ |
| **NIST FIPS 186-5 (Digital Signature Std)** | 签名（含 EdDSA） | 2023 | https://csrc.nist.gov/pubs/fips/186-5/final | ✓ |
| **NIST FIPS 203/204/205 (ML-KEM/ML-DSA/SLH-DSA)** | 后量子，**定型 2024-08-13，基线 GA** | 现行 | https://csrc.nist.gov/news/2024/postquantum-cryptography-fips-approved | ✓ |
| **NIST SP 800-38A/38D** | 分组工作模式（CBC/CTR）/ GCM | 现行 | https://csrc.nist.gov/ | ✓ |
| **IETF RFC 8446 (TLS 1.3) / 8017 (PKCS#1 v2.2) / 2104 (HMAC) / 7748 (Curve25519) / 8032 (EdDSA) / 5280 (X.509)** | 协议/构造 | 现行 | https://www.rfc-editor.org/ | ✓ |

### B. 系统/网络/应用安全侧（系统承重）+ 隐私横切
| 锚点 | 定位 | 版本 | URL | 核实 |
|------|------|------|-----|------|
| **UC Berkeley CS161 Computer Security** | 全学期 27 讲 schedule（内存安全→密码→Web→网络）——本组主干教学序锚点 | Fall 2025 | https://fa25.cs161.org/ | ✓ 全 schedule 抽取 |
| **Stanford CS155 Computer & Network Security** | 19 讲 syllabus（控制劫持→隔离→微架构→Web→密码→网络→隐私/AI） | 现行（2025/26 学年） | https://cs155.stanford.edu/syllabus.html | ✓ |
| **OWASP Top 10:2025** | Web 漏洞谱系（最终版 2026-01；已取代 2021） | 2025 | https://owasp.org/Top10/2025/ | ✓ |
| **MITRE CWE Top 25** | 漏洞分类 | 现行 | https://cwe.mitre.org/ | ✓ |
| **Dwork & Roth,《The Algorithmic Foundations of Differential Privacy》** | DP 定义/机制/组合定理承重 | 2014 | 官方免费 PDF | ✓ |
| **NIST SP 800-226** | 评估 DP 保证 | 2025 | https://csrc.nist.gov/ | ✓ |

> **Katz-Lindell 3rd ed 结构（抽取坐实）**：Part I 引论与古典（Ch1 Introduction / Ch2 Perfectly Secret Encryption）｜Part II 对称（Ch3 Private-Key Enc(CPA) / Ch4 MAC / Ch5 CCA & Authenticated Enc / Ch6 Hash Functions / Ch7 实用对称原语=AES/ChaCha20/SHA-3）｜Part III 公钥（Ch8* 理论构造 / Ch9 数论与困难假设 / Ch10* 分解与离散对数算法 / Ch11 密钥管理与公钥革命=DH / Ch12 公钥加密=RSA/El Gamal/KEM-DEM / Ch13 数字签名 / Ch14* 后量子 / Ch15* 高级专题）。3rd ed 新增：TLS 1.3（§13.7）、Ch14 后量子、ChaCha20/SHA-3/海绵、GCM/CCM/Poly1305、KEM/DEM。

---

## 1 · 大主题清单（编号 | 大主题 | 一句话范围 | 来源锚点 | 先修提示）

> 教学序：**古典地基 → 数论 → 对称 → 哈希/MAC → 公钥/DH → 签名/PKI → TLS → 后量子**（密码构造块，Katz-Lindell 序）；再 **内存 → 缓解/隔离 → 侧信道 → Web → 认证 → 网络**（系统/网络块，CS161/CS155 序）；末 **隐私/DP 横切**。

### 密码学构造块（数学承重，可 Python 实证）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 先修提示 |
|------|--------|-----------|----------|----------|
| G11-01 | **安全基本原理与古典密码** | CIA 安全属性、威胁/攻击模型、Kerckhoffs 原则、可证明安全三支柱（定义/假设/证明）、古典密码及其破译、完美保密与一次性密码本(OTP)、Shannon 定理 | Katz Part I (Ch1–2)；CS161 L1；CS155 L1 | — |
| G11-02 | **数论基础** | 模运算、群/环、欧拉定理与费马小定理、扩展欧几里得求逆元、中国剩余定理(CRT)、素性测试(Miller–Rabin)、困难假设(分解/离散对数) | Katz Ch9–10；HAC Ch2–4 | ← **L1-02（离散/数论）** |
| G11-03 | **对称加密与分组密码** | 伪随机性(PRG/PRF)、计算安全与 CPA 定义、规约证明、分组密码结构(AES 的 SPN/轮函数、DES/Feistel 史)、密钥长度对标 | Katz Ch3,7；FIPS 197 | ← G11-02(弱)、L3-02(字节/位运算) |
| G11-04 | **分组工作模式与认证加密(AEAD)** | ECB 的危险/CBC/CTR、填充与填充预言攻击、认证加密(GCM/CCM/ChaCha20-Poly1305)、nonce/IV 语义、流密码(ChaCha20) | Katz Ch5,7；NIST SP 800-38A/38D；CS161 L8 | ← G11-03 |
| G11-05 | **哈希函数与 MAC** | 哈希三性质(抗原像/抗第二原像/抗碰撞)、生日攻击、Merkle–Damgård vs 海绵(SHA-3)、长度扩展、随机预言模型、HMAC/GMAC/Poly1305、MD5/SHA-1 已破（反例） | Katz Ch4,6,7；FIPS 180-4/202；RFC 2104；CS161 L9 | ← G11-02、G11-03 |
| G11-06 | **公钥加密与 RSA** | 公钥革命动机(密钥分发问题)、RSA 加解密与陷门置换、El Gamal、CCA 安全、KEM/DEM 与混合加密范式、PKCS#1 OAEP | Katz Ch11–12；RFC 8017(PKCS#1 v2.2) | ← G11-02、G11-05 |
| G11-07 | **DH 密钥交换与椭圆曲线密码** | Diffie–Hellman 密钥交换、离散对数问题(CDH/DDH)、DH 群、椭圆曲线直觉与 ECDH、Curve25519/X25519、ECC vs RSA 密钥长度 | Katz Ch11,§9.3.4；RFC 7748；RFC 3526 | ← G11-02 |
| G11-08 | **数字签名** | 签名安全定义(EUF-CMA)、RSA-PSS、DSA/ECDSA、EdDSA(Ed25519/448)、Fiat–Shamir 变换与 Schnorr、签名 vs MAC(不可否认) | Katz Ch13；FIPS 186-5(2023)；RFC 8032 | ← G11-05、G11-06、G11-07 |
| G11-09 | **PKI 与证书** | X.509 证书结构、CA 信任链与信任模型、证书签发/验证、吊销(CRL/OCSP)、Web PKI 与 CT 日志、案例分析 | RFC 5280；CS161 L12；Katz §12–13 应用 | ← G11-08 |
| G11-10 | **TLS 与安全信道协议** | TLS 1.3 握手(密钥协商+身份认证+前向保密)、混合加密工程落地、会话密钥派生、HTTPS 目标与陷阱、降级/中间人 | RFC 8446；Katz §13.7；CS161 L20；CS155 HTTPS | ← G11-04、G11-06/07、G11-09；软 ← L4-02(网络) |
| G11-11 | **后量子密码(PQC)** | 量子对密码的冲击(Shor/Grover)、格基与哈希基构造、ML-KEM(封装)/ML-DSA/SLH-DSA(签名)、混合部署与迁移。**硬标：FIPS 203/204/205 已 2024-08-13 定型 GA；生产迁移仍演进** | Katz Ch14；FIPS 203/204/205 | ← G11-06/07、G11-08 |

### 系统 / 网络 / 应用安全块（系统承重；攻防实证仅 scratchpad 教学级，不入库）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 先修提示 |
|------|--------|-----------|----------|----------|
| G11-12 | **内存安全漏洞与控制流劫持** | 栈缓冲区溢出与返回地址覆盖(概念)、堆溢出、释放后使用(UAF)、格式化串、整数溢出、控制劫持原理(CWE-787/125/416) | CS161 L2–4；CS155 控制劫持攻击；MITRE CWE | ← **L3-02(组成)+L3-03(汇编/栈帧)** |
| G11-13 | **内存安全缓解与隔离** | 栈金丝雀、ASLR、DEP/NX(W^X)、CFI、边界检查、沙箱/隔离(seccomp/进程隔离)、Rust 所有权对照(呼应 N-12) | CS161 L5；CS155 隔离与沙箱 | ← G11-12 |
| G11-14 | **侧信道与微架构安全** | 计时侧信道(CWE-208)、缓存攻击、Spectre/Meltdown 类瞬态执行、功耗/电磁、常时(constant-time)实现、掩码/盲化 | CS155 硬件与微架构安全；CWE-208 | ← L3-02(缓存/微架构)、软 ← L5-05 |
| G11-15 | **Web 安全** | 浏览器同源模型、Cookies/会话、CSRF、XSS(存储/反射/DOM)、SQL 注入与参数化、命令注入、CAPTCHA/UI 攻击。挂 **OWASP Top 10:2025** 谱系（A03 供应链、A10 异常处理新增） | CS161 L13–16；CS155 Web；OWASP Top 10:2025 | ← 软 L4-02(HTTP)、L4-03(DB) |
| G11-16 | **认证、访问控制与口令安全** | 认证(authN) vs 授权(authZ)、访问控制模型、口令存储(加盐 + bcrypt/argon2)、会话管理与固定(CWE-384)、MFA、OAuth/JWT/令牌 | CS161(Passwords)；CS155；OWASP A07 认证失败 | ← G11-05；软 L4-02(HTTP/cookie) |
| G11-17 | **网络安全** | 低层网络攻击(嗅探/欺骗)、TCP/BGP 攻击、DNS 与 DNSSEC、拒绝服务(DoS/DDoS)与防火墙、入侵检测(IDS)、AI/ML 安全 | CS161 L17–25；CS155 网络安全/DoS | ← 软 L4-02(网络) |

### 隐私保护块（横切；概率承重，可 numpy 实证）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 先修提示 |
|------|--------|-----------|----------|----------|
| G11-18 | **隐私、匿名与差分隐私(DP)** | 匿名/抗审查(Tor)；(ε,δ)-DP 定义、敏感度、Laplace/Gaussian 机制、组合定理、隐私-效用权衡；PPML(联邦学习/DP-SGD)与机密计算(TEE)**硬标未定型子块，只登记** | CS161 L26 匿名与 Tor；Dwork & Roth 2014；NIST SP 800-226(2025) | ← **L2-03(概率统计)** ← L1-04 |

---

## 2 · 覆盖 / 重叠 / 缺口自检

- **覆盖全**：密码构造(01–11)对齐 Katz-Lindell 3rd ed 全 TOC（古典/对称/哈希-MAC/公钥/DH-ECC/签名/PKI/TLS/PQC）；系统安全(12–17)对齐 CS161 27 讲 + CS155 19 讲全序（内存/隔离/微架构/Web/认证/网络）；隐私(18)对齐 N-15 并入 + CS161 匿名讲。三主线无遗漏。
- **少重叠**：MAC 归 G11-05（与哈希同章族，Katz Ch4/6 相邻）不重复入签名；侧信道单列 G11-14（既非纯内存亦非纯密码，Round 2 §1-C 独立谱系）；TLS(10)只讲协议编排，其原语在 03/06/07/08 已成节，指回不重讲。
- **教学序依赖链**：02 数论 → {03 对称, 06 公钥, 07 DH}；05 哈希 → 08 签名 → 09 PKI → 10 TLS → 11 PQC；12 内存 须在 L3-03(汇编)后 → 13 缓解 → 14 侧信道；18 DP 须在 L2-03(概率)后。网络/OS 软依赖不阻塞开工。
- **前沿硬标**：G11-11 PQC 已 GA(2024-08-13)可作结论，迁移演进硬标；G11-18 机密计算(TEE)硬标未定型只登记；G11-15 须按 **OWASP Top 10:2025** 而非 2021 版写（SSRF 并入 A01、A03 供应链/A10 异常处理为 2025 新增）。

---

## 建议大主题数：K = 18

（密码构造 11 + 系统/网络安全 6 + 隐私横切 1。此为「查全」教学单元级清单；Round 4 出报告时可按 Round 2 建议压缩合并为 6–7 节，如 01+02 合「密码学预备」、05 内含 MAC、12+13 合「内存安全攻防」、15+16 合「Web 与认证」。）
