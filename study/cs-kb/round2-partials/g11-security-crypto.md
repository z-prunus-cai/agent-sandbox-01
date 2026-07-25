# Round 2 · 第 11 组「安全与密码」定方向

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25
> 组构成：**单课组** L5-04 信息安全/密码学（SEC + MSF 数论）**并入 N-15 差分隐私/隐私保护 ML**。
> 依据 round1-map §2-A4：硬先修 = **L1-02（离散/数论）+ L3-02（组成/系统）**；网络（L4-02）、OS（L4-01）降为**子方向软依赖**（网络安全←网络、系统安全←OS）。
> 单课组按纪律「做深一层」：把一门大课拆成 5–8 个 Round 4 可各自成节的方向。

---

## 0 · 一句话定位

安全/密码这门课有两条正交主线，Round 4 拆节必须分账：
- **密码学（构造侧，数学承重）**：数论 → 对称 → 公钥/PKI → 哈希/签名 → 后量子。可 Python 实证，理论课味道。
- **系统/应用安全（攻防侧，系统承重）**：内存安全、Web/注入、认证会话、侧信道。攻防实证**只在仓库外 scratchpad 做教学级最小复现，绝不入库、绝不指向真实攻击目标**。
- **隐私保护（横切）**：差分隐私（DP，理论成熟、可 numpy 实证）+ PPML/机密计算（部分未定型，硬标）。

---

## 1 · 子方向选型 / 对比表

### 1-A 对称 vs 公钥加密（心智模型对比）
| 维度 | 对称密码 | 公钥（非对称）密码 |
|------|----------|--------------------|
| 密钥 | 单一共享密钥 | 公钥/私钥对 |
| 数学承重 | 混淆-扩散、置换网络（无强数论） | 数论难题（大整数分解 RSA / 离散对数 DH-ECC / 格 ML-KEM） |
| 速度 | 快（AES 有 AES-NI 硬件指令） | 慢 2–3 数量级 |
| 典型算法 | AES（FIPS 197）、ChaCha20 | RSA、ECDH/ECDSA、ML-KEM（FIPS 203） |
| 解决的问题 | 批量数据保密 | 密钥分发 + 数字签名（身份/不可否认） |
| 实践 | **混合加密**：公钥交换会话密钥 → 对称加密数据（TLS 即此结构） | 同左 |
| 密钥长度对标 | AES-128/256 | RSA-3072 ≈ AES-128；ECC-256 ≈ AES-128 |

### 1-B 哈希 / 签名算法谱系（现行 NIST 定型对象，核实 2026-07-25）
| 类别 | 算法 | 一手标准 | 状态 | 备注 |
|------|------|----------|------|------|
| 哈希 | SHA-2（SHA-256/512） | FIPS 180-4 | GA·主力 | Merkle–Damgård 结构 |
| 哈希 | SHA-3（Keccak） | FIPS 202 | GA | 海绵结构，抗长度扩展 |
| 哈希 | MD5 / SHA-1 | — | ⚠**已破/弃用** | 仅作反例讲碰撞攻击，勿用于签名 |
| 签名 | RSA-PSS / ECDSA / EdDSA | FIPS 186-5（2023） | GA | 186-5 新增 EdDSA(Ed25519/448) |
| 签名(PQC) | ML-DSA（原 Dilithium） | **FIPS 204**（2024-08-13 定型） | GA | 格基，替代 ECDSA |
| 签名(PQC) | SLH-DSA（SPHINCS+） | **FIPS 205**（2024-08-13 定型） | GA | 纯哈希基，保守备份 |
| KEM(PQC) | ML-KEM（原 Kyber） | **FIPS 203**（2024-08-13 定型） | GA | 格基密钥封装，替代 RSA/ECDH |

### 1-C 漏洞类别谱系（内存安全 / 注入 / 认证 / 侧信道）
| 谱系 | 代表 CWE | 攻击面 | 承重先修 | 防御 |
|------|----------|--------|----------|------|
| 内存安全 | CWE-787 越界写、CWE-125 越界读、CWE-416 UAF | 栈/堆缓冲区、指针 | **L3-02/L3-03**（栈帧、机器级） | 栈金丝雀、ASLR、W^X、边界检查、Rust 所有权 |
| 注入 | CWE-89 SQLi、CWE-79 XSS、CWE-78 命令注入 | 未净化输入拼进解释器 | L4-03(DB)、L4-02(HTTP) | 参数化查询、输出编码、最小权限 |
| 认证/会话 | CWE-287、CWE-384 会话固定 | 口令/令牌/会话 | L4-02（HTTP/cookie） | MFA、加盐哈希口令（bcrypt/argon2）、安全 cookie |
| 侧信道 | CWE-208 计时、CWE-385 | 执行时间/功耗/缓存 | L3-02（缓存/微架构）、L5-05 | 常时比较、掩码、盲化 |

### 1-D 大课内部子方向划分（四象限）
| 子域 | 承重腿 | 实证形态 | Round 4 归节 |
|------|--------|----------|--------------|
| 密码学理论 | 数论（一手：HAC / FIPS） | Python 数值验证 | §数论、§对称、§公钥、§哈希签名 |
| 系统安全 | OS/机器级（L3-02/L4-01） | scratchpad 教学级最小复现 | §内存安全攻防 |
| 网络/应用安全 | HTTP/TLS（L4-02，软依赖） | 靶场概念（不入库） | §Web/注入·认证 |
| 隐私保护 | 概率统计（L2-03） | numpy 加噪验证 | §差分隐私 |

---

## 2 · 重点方向（Round 4 可各自成节，6 节 + 1 横切）

标 `←` 为承重先修依赖。

1. **数论基础** ← L1-02（离散）
   模运算、欧拉定理/费马小定理、扩展欧几里得求逆元、CRT、素性测试（Miller–Rabin）。是 RSA/DH 的数学地基。**P0（组内地基）**。

2. **对称密码** ← 数论(弱)、L3-02(字节/位运算)
   分组密码（AES 的 SPN 结构、轮函数）、工作模式（ECB 的危险 / CBC / CTR / GCM 认证加密）、流密码（ChaCha20）。**P1**。

3. **公钥与 PKI** ← 数论、L4-02(软，证书链走 TLS)
   RSA 加解密、DH/ECDH 密钥交换、椭圆曲线直觉、X.509 证书 / CA 信任链 / 吊销（CRL/OCSP）。**P1**。

4. **哈希与数字签名** ← 数论、对称
   哈希三性质（抗原像/抗第二原像/抗碰撞）、Merkle–Damgård vs 海绵、HMAC、RSA-PSS/ECDSA/EdDSA 签名、PQC 签名硬标（FIPS 204/205）。**P1**。

5. **内存安全攻防** ← **L3-02（组成）+ L3-03（汇编/栈帧）**
   栈缓冲区溢出原理、返回地址覆盖概念、堆/UAF、缓解机制（金丝雀/ASLR/DEP/NX/CFI）、Rust 所有权对照（呼应 N-12）。**P1**。攻防实证仅 scratchpad 教学级。

6. **Web / 注入 · 认证会话** ← L4-02(HTTP,软)、L4-03(DB,软)
   SQL 注入与参数化、XSS 与输出编码、CSRF、认证/会话管理、口令存储（加盐 + argon2/bcrypt）。挂 **OWASP Top 10:2025** 谱系。**P1**。

7. **（横切）差分隐私 / 隐私保护 ML** ← **L2-03（概率统计）** ← L1-04
   (ε,δ)-DP 定义、Laplace / Gaussian 机制、敏感度、组合定理、隐私-效用权衡；PPML（联邦学习/DP-SGD）与机密计算（TEE）**部分未定型硬标**。**P2**。

> 拆节建议：Round 4 出 **6–7 节**（1–4 密码学连续成组，5–6 攻防成组，7 独立横切）。若整体篇幅需压缩，可把 2+4 合并为「对称与哈希」、3 单列。

---

## 3 · 一手源候选 + 本机实证点

### 3-A 一手源候选（承重，核实 2026-07-25）
- **NIST FIPS**（构造侧承重）：FIPS 197（AES）、FIPS 180-4（SHA-2）、FIPS 202（SHA-3）、FIPS 186-5（数字签名，2023）、**FIPS 203/204/205（PQC，2024-08-13 定型）**、SP 800-38 系列（分组模式）、SP 800-90A（DRBG）、SP 800-56A/B（密钥建立）。[^1][^2][^3]
- **RFC**：RFC 8446（TLS 1.3）、RFC 8017（RSA PKCS#1 v2.2）、RFC 2104（HMAC）、RFC 7748（Curve25519/448）、RFC 8032（EdDSA）、RFC 3526（DH 群）。[^4]
- **教材**：*Handbook of Applied Cryptography*（Menezes 等，免费官方 PDF，数论/公钥承重）；Katz-Lindell *Introduction to Modern Cryptography*（可证明安全）。[^5]
- **攻防/应用**：**OWASP Top 10:2025**（2026-01 最终版，官方 owasp.org/Top10/2025）、OWASP ASVS、MITRE **CWE** Top 25。[^6]
- **差分隐私**：Dwork & Roth *The Algorithmic Foundations of Differential Privacy*（2014，DP 定义/机制/组合定理承重）；NIST SP 800-226（DP 保证评估，2025）。[^7]
- **课程锚点**：UC Berkeley CS161（先修 61B/70/61C，佐证 A4 依赖）；Stanford CS255 应用密码学；UCSD DSC291 差分隐私。[^8]

### 3-B 本机实证点（已在 scratchpad 教学级跑通，Round 4 复用；真实输出如下）
- **教科书 RSA（sympy）**：`p,q` 各 16-bit → `n=1211577677`，`e=65537`，`m=42 → c=462822218 → 解回 42 ✓`。用 `sympy.randprime/mod_inverse/pow`。
- **DH 密钥交换（sympy）**：64-bit 素数、g=2，双方 `pow(B,a,p)==pow(A,b,p)` → **共享密钥一致 ✓**。
- **SHA-256 雪崩（hashlib）**：`sha256("hello")=2cf24dba…9824`；改 1 字符（hello→hellp）翻转 **131/256 比特**（≈50%，扩散性演示）。
- **缓冲区溢出原理（gcc -fno-stack-protector）**：8 字节 `buf` 写入 12 字节 `"AAAA…"`，相邻 `canary` 被覆盖为 `0x41414141`（"AAAA"）。gcc 编译期 `-Wstringop-overflow` 已告警。**仅演示"相邻内存被覆盖"这一原理，非返回地址劫持、非攻击目标**。
- **差分隐私 Laplace 机制（numpy）**：计数查询真值 1000，敏感度 1，`rng.laplace(0, 1/ε)`。ε=0.1→std 14.13（理论 √2/ε=14.14）；ε=1→1.41；ε=10→0.14。**实测 std 与理论 √2/ε 吻合 ✓**，直观展示隐私-噪声权衡。

> 纪律：以上攻防/密码实证一律 scratchpad，**不入库**；Round 4 报告贴真实输出 + 可复现命令 + 环境串（本组已备命令）。

---

## 4 · 优先级校准

- **课级**：L5-04 维持 **P1**（round1-map §2-B 未改；SEC 为 CS2023 提级后核心 KA）。
- **组内节级优先级**（供 Round 4 分批）：
  - **P0**：数论基础（组内一切公钥的地基，须最先）。
  - **P1**：对称密码、公钥与 PKI、哈希与签名、内存安全攻防、Web/注入·认证（六层课的安全主干）。
  - **P2**：差分隐私（横切，成熟可立节）；PPML/机密计算作 P2 内**硬标未定型子块**，只登记不下结论。
- **依赖排序提示**：数论 → {对称, 公钥, 哈希签名}；内存安全攻防须在 L3-03（汇编）之后；差分隐私须在 L2-03（概率）之后。网络/OS 为软依赖，不阻塞开工（可与 L4-02/L4-01 并行）。
- **前沿硬标**：PQC（FIPS 203/204/205）已**定型 GA**（2024-08-13），可作结论；但"生产迁移/混合部署"仍演进，标演进。机密计算（TEE/enclave）顶校正式课零散，**硬标未定型**。

---

## 5 · 相对 round1-map 的校正 / 更新（本组据实纠正）

- **OWASP 版本时效更新**：round1 未锁定 OWASP 版本；核实 2026-07-25，**当前官方最终版为 OWASP Top 10:2025**（2025-11 发布、2026-01 最终定稿），已取代 2021 版。新增两类 **A03 软件供应链失败**、**A10 异常条件处理不当**；A07 更名「Authentication Failures」、A09 更名「Security Logging & Alerting Failures」；SSRF 并入 A01 Broken Access Control。Round 4 Web 节须按 **:2025** 谱系写，勿沿用 2021。[^6]
- **PQC 时效坐实**：FIPS 203/204/205 于 **2024-08-13 定型**，基线 2026-07-25 上为 GA，可作结论（round1 view3 曾标 DP✅/机密计算⚙，此处补 PQC 已 GA）。

---

### 一手引用清单（脚注定义）
[^1]: NIST FIPS 197 (AES) / FIPS 180-4 (SHA-2) / FIPS 202 (SHA-3) / FIPS 186-5 (Digital Signature Std, 2023)。NIST CSRC Publications。核实 2026-07-25。
[^2]: NIST FIPS 203 (ML-KEM) / 204 (ML-DSA) / 205 (SLH-DSA)，定型 2024-08-13。https://csrc.nist.gov/news/2024/postquantum-cryptography-fips-approved 。核实 2026-07-25。
[^3]: NIST SP 800-38 系列（分组密码工作模式）、SP 800-90A（DRBG）、SP 800-56A/B（密钥建立）。NIST CSRC。核实 2026-07-25。
[^4]: IETF RFC 8446 (TLS 1.3)、RFC 8017 (PKCS#1 v2.2)、RFC 2104 (HMAC)、RFC 7748 (Curve25519)、RFC 8032 (EdDSA)。https://www.rfc-editor.org/ 。核实 2026-07-25。
[^5]: Menezes, van Oorschot, Vanstone, *Handbook of Applied Cryptography*（官方免费 PDF https://cacr.uwaterloo.ca/hac/ ）；Katz & Lindell, *Introduction to Modern Cryptography*。核实 2026-07-25。
[^6]: OWASP Top 10:2025 官方（最终版 2026-01）https://owasp.org/Top10/2025/0x00_2025-Introduction/ ；OWASP ASVS；MITRE CWE Top 25 https://cwe.mitre.org/ 。核实 2026-07-25。
[^7]: Dwork & Roth, *The Algorithmic Foundations of Differential Privacy*, 2014；NIST SP 800-226（Evaluating Differential Privacy Guarantees, 2025）。核实 2026-07-25。
[^8]: UC Berkeley CS161（先修 61B/70/61C）https://fa25.cs161.org/policies/ ；Stanford CS255；UCSD DSC291 https://cseweb.ucsd.edu/~yuxiangw/classes/DSC291-2024Fall/ 。核实 2026-07-25。

### 二三手（非承重）
- Cloud Security Alliance「NIST FIPS 203/204/205 Finalized」博客（⚠仅佐证 PQC 定型日期，指回 [^2]）。
- OWASP Top 10:2025 各厂商解读（GitLab/Semgrep/Parasoft）（⚠仅佐证版本变更，指回 [^6]）。
