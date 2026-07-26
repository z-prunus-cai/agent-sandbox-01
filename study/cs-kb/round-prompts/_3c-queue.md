# Round 3c 造 prompt · 队列跟踪（并发 4，逐门推进）

> 顺序即下方列表；保持 ≤4 门在跑。文件已存在=done；已发未落盘=in-flight。
> 恢复规则：next = 最小的既无文件、也不在飞行中的序号；不确定就 `ls round-prompts/` 对照。

✅ **Round 3c 全部完成**：40/40 门落盘，共 **423 条 prompt**（已对账）。主索引见 `round3-index.md`。

| # | 课ID | 文件 | 状态 |
|---|------|------|------|
| 001 | L1-01 | 001-intro-programming | launched |
| 002 | L1-02 | 002-discrete-math | launched |
| 003 | L1-03 | 003-linear-algebra | launched |
| 004 | L1-04 | 004-calculus | launched |
| 005 | L2-01 | 005-data-structures | queued |
| 006 | L2-02 | 006-digital-logic | queued |
| 007 | L2-03 | 007-probability-statistics | queued |
| 008 | L3-01 | 008-algorithms | queued |
| 009 | L3-02 | 009-computer-organization | queued |
| 010 | L3-03 | 010-assembly-machine-level | queued |
| 011 | L3-04 | 011-oop-paradigms | queued |
| 012 | L3-05 | 012-theory-of-computation | queued |
| 013 | L4-01 | 013-operating-systems | queued |
| 014 | L4-02 | 014-computer-networks | queued |
| 015 | L4-03 | 015-database-systems | queued |
| 016 | L4-04 | 016-compilers | queued |
| 017 | L4-05 | 017-concurrency-parallelism | queued |
| 018 | L4-06 | 018-software-engineering | queued |
| 019 | L5-01 | 019-distributed-systems | queued |
| 020 | L5-02 | 020-machine-learning | queued |
| 021 | L5-03 | 021-computer-graphics | queued |
| 022 | L5-04 | 022-security-cryptography | queued |
| 023 | L5-05 | 023-advanced-architecture | queued |
| 024 | L5-06 | 024-programming-language-theory | queued |
| 025 | L6-01 | 025-advanced-algorithms | queued |
| 026 | L6-02 | 026-program-analysis-verification | queued |
| 027 | L6-03 | 027-deep-learning | queued |
| 028 | L6-04 | 028-database-internals | queued |
| 029 | L6-05 | 029-distributed-cloud-systems | queued |
| 030 | L6-06 | 030-high-performance-computing | queued |
| 031 | L5-08 | 031-symbolic-classical-ai | queued |
| 032 | L5-09 | 032-hci | queued |
| 033 | L5-10 | 033-sep-responsible-ai | queued |
| 034 | L5-11 | 034-system-design | queued |
| 035 | L5-12 | 035-observability-sre | queued |
| 036 | L5-13 | 036-containers-cloud-native | queued |
| 037 | L5-14 | 037-rust-memory-safety | queued |
| 038 | L5-15 | 038-embedded-systems | queued |
| 039 | L6-08 | 039-ai-llm-systems | queued |
| 040 | L6-09 | 040-data-intensive-apps | queued |

组文件映射：g01 math-foundations / g02 programming-ds / g03 algorithms / g04 hardware-arch / g05 os-concurrency / g06 networks-distributed / g07 data-management / g08 languages-compilers / g09 ai-track / g10 graphics-embedded-hpc / g11 security-crypto / g12 engineering-delivery / g13 human-society
