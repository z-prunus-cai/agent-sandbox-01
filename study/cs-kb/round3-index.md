# Round 3c · 报告 prompt 库 · 主索引

> 40 门课 → **423 条报告 prompt**（每条 = 一个大主题的报告任务，report-format v3）。核实 2026-07-26。
> 每个 prompt 是 ```text 块，首行含 [P?] 课ID·大主题号 名称。粒度默认 1 大主题=1 报告，个别大主题的 prompt 内含"可拆 N 份"判定。

## 清单（按课程序号）
| 文件 | 课程 | 优先级 | prompt 数 |
|------|------|--------|-----------|
| `001-intro-programming.md` | L1-01 程序设计入门 | P0 | 8 |
| `002-discrete-math.md` | L1-02 离散数学 | P0 | 12 |
| `003-linear-algebra.md` | L1-03 线性代数 | P0 | 9 |
| `004-calculus.md` | L1-04 微积分 | P1 | 10 |
| `005-data-structures.md` | L2-01 数据结构 | P0 | 11 |
| `006-digital-logic.md` | L2-02 数字逻辑电路 | P1 | 7 |
| `007-probability-statistics.md` | L2-03 概率论与数理统计 | P1 | 10 |
| `008-algorithms.md` | L3-01 算法设计与分析 | P0 | 15 |
| `009-computer-organization.md` | L3-02 计算机组成原理 | P0 | 8 |
| `010-assembly-machine-level.md` | L3-03 汇编与机器级表示 | P1 | 7 |
| `011-oop-paradigms.md` | L3-04 面向对象与程序设计范式 | P1 | 10 |
| `012-theory-of-computation.md` | L3-05 计算理论/形式语言与自动机 | P1 | 11 |
| `013-operating-systems.md` | L4-01 操作系统 | P0 | 15 |
| `014-computer-networks.md` | L4-02 计算机网络 | P0 | 11 |
| `015-database-systems.md` | L4-03 数据库系统 | P0 | 12 |
| `016-compilers.md` | L4-04 编译原理 | P1 | 10 |
| `017-concurrency-parallelism.md` | L4-05 并发与并行程序设计 | P1 | 15 |
| `018-software-engineering.md` | L4-06 软件工程 | P1 | 13 |
| `019-distributed-systems.md` | L5-01 分布式系统 | P1 | 7 |
| `020-machine-learning.md` | L5-02 机器学习 | P1 | 12 |
| `021-computer-graphics.md` | L5-03 计算机图形学 | P2 | 10 |
| `022-security-cryptography.md` | L5-04 信息安全/密码学 | P1 | 18 |
| `023-advanced-architecture.md` | L5-05 高级计算机体系结构 | P2 | 8 |
| `024-programming-language-theory.md` | L5-06 编程语言理论 | P2 | 10 |
| `025-advanced-algorithms.md` | L6-01 高级算法 | P2 | 8 |
| `026-program-analysis-verification.md` | L6-02 程序分析与形式验证 | P2 | 11 |
| `027-deep-learning.md` | L6-03 深度学习及其分支 | P2 | 12 |
| `028-database-internals.md` | L6-04 数据库内核与存储系统 | P2 | 11 |
| `029-distributed-cloud-systems.md` | L6-05 分布式与云系统专题 | P2 | 6 |
| `030-high-performance-computing.md` | L6-06 高性能计算 | P2 | 10 |
| `031-symbolic-classical-ai.md` | L5-08 符号/经典 AI | P1 | 11 |
| `032-hci.md` | L5-09 人机交互 HCI | P1 | 7 |
| `033-sep-responsible-ai.md` | L5-10 社会伦理职业/负责任 AI | P1 | 7 |
| `034-system-design.md` | L5-11 系统设计 | P1 | 10 |
| `035-observability-sre.md` | L5-12 可观测性/SRE | P1 | 11 |
| `036-containers-cloud-native.md` | L5-13 容器与云原生 | P2 | 12 |
| `037-rust-memory-safety.md` | L5-14 Rust 与内存安全 | P2 | 12 |
| `038-embedded-systems.md` | L5-15 嵌入式系统 | P2 | 12 |
| `039-ai-llm-systems.md` | L6-08 AI/LLM 系统 | P2·⚙演进快 | 12 |
| `040-data-intensive-apps.md` | L6-09 数据密集型应用（DDIA） | P1 | 12 |
| **合计** | **40 门** | — | **423** |

## 执行建议（Round 4 逐条取证，按优先级/依赖分批）
- **P0 优先**（枢纽/必经）：001-005,008,009,013,014,015 等先行。
- 依赖约束见 round1-map §4 拓扑分批；网络与 OS 已解耦、微积分须早于 ML/图形。
- Round 4 按 findings/NNN 落盘，committed ledger 跟踪；粒度判定在各 prompt 内，拆分则 NNN-slug-a/-b。
