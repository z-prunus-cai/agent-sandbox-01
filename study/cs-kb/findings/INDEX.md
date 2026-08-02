# 计算机科学知识库 · 主索引（Master Index）

> 广度优先「教辅」知识库：40 门课程、423 个大主题报告。每份报告遵循 report-format v3（`#`大主题→`##`小主题→`###`内容项；每项核心说明＋充分辅助说明、讲到初学者能懂；公式独占行；每小主题末集中列来源；前沿项硬标「⚙演进快·锚版本·随时变」）。
> 取向：**时效性 > 正确性 > 广度 >（放弃）专家级纵深**。正确性主承重＝每个内容项 ≥2 个独立来源交叉核对，冲突两边都记。

- 课程数：**40** ｜ 报告数：**423** ｜ 组织：`findings/<课程目录>/tNN-<slug>.md`
- 质检：已过 Round 5 两层对抗质检（机械扫描 + 逐课联网对抗核查），见 [`ROUND5-QA-SUMMARY.md`](../ROUND5-QA-SUMMARY.md)。

## 课程总览

| 层 | # | 课程 | 报告数 |
|----|----|------|--------|
| L1 | 001 | [程序设计入门](#001-程序设计入门) | 8 |
| L1 | 002 | [离散数学](#002-离散数学) | 12 |
| L1 | 003 | [线性代数](#003-线性代数) | 9 |
| L1 | 004 | [微积分](#004-微积分) | 10 |
| L2 | 005 | [数据结构](#005-数据结构) | 11 |
| L2 | 006 | [数字逻辑电路](#006-数字逻辑电路) | 7 |
| L2 | 007 | [概率论与数理统计](#007-概率论与数理统计) | 10 |
| L3 | 008 | [算法设计与分析](#008-算法设计与分析) | 15 |
| L3 | 009 | [计算机组成原理](#009-计算机组成原理) | 8 |
| L3 | 010 | [汇编与机器级表示](#010-汇编与机器级表示) | 7 |
| L3 | 011 | [面向对象与程序设计范式](#011-面向对象与程序设计范式) | 10 |
| L3 | 012 | [计算理论与自动机](#012-计算理论与自动机) | 11 |
| L4 | 013 | [操作系统](#013-操作系统) | 15 |
| L4 | 014 | [计算机网络](#014-计算机网络) | 11 |
| L4 | 015 | [数据库系统](#015-数据库系统) | 12 |
| L4 | 016 | [编译原理](#016-编译原理) | 10 |
| L4 | 017 | [并发与并行程序设计](#017-并发与并行程序设计) | 15 |
| L4 | 018 | [软件工程](#018-软件工程) | 13 |
| L5 | 019 | [分布式系统](#019-分布式系统) | 7 |
| L5 | 020 | [机器学习](#020-机器学习) | 12 |
| L5 | 021 | [计算机图形学](#021-计算机图形学) | 10 |
| L5 | 022 | [信息安全与密码学](#022-信息安全与密码学) | 18 |
| L5 | 023 | [高级计算机体系结构](#023-高级计算机体系结构) | 8 |
| L5 | 024 | [编程语言理论](#024-编程语言理论) | 10 |
| L6 | 025 | [高级算法](#025-高级算法) | 8 |
| L6 | 026 | [程序分析与形式验证](#026-程序分析与形式验证) | 11 |
| L6 | 027 | [深度学习及其分支](#027-深度学习及其分支) | 12 |
| L6 | 028 | [数据库内核与存储系统](#028-数据库内核与存储系统) | 11 |
| L6 | 029 | [分布式与云系统专题](#029-分布式与云系统专题) | 6 |
| L6 | 030 | [高性能计算](#030-高性能计算) | 10 |
| L5 | 031 | [符号与经典 AI](#031-符号与经典-ai) | 11 |
| L5 | 032 | [人机交互 HCI](#032-人机交互-hci) | 7 |
| 跨层 | 033 | [社会伦理职业与负责任 AI](#033-社会伦理职业与负责任-ai) | 7 |
| L5 | 034 | [系统设计](#034-系统设计) | 10 |
| L5 | 035 | [可观测性与 SRE](#035-可观测性与-sre) | 11 |
| L5 | 036 | [容器与云原生](#036-容器与云原生) | 12 |
| L5 | 037 | [Rust 与内存安全](#037-rust-与内存安全) | 12 |
| L5 | 038 | [嵌入式系统](#038-嵌入式系统) | 12 |
| L6 | 039 | [AI/LLM 系统](#039-aillm-系统) | 12 |
| L6 | 040 | [数据密集型应用 DDIA](#040-数据密集型应用-ddia) | 12 |

## 各课程报告清单

### L1 数理与编程基础

#### 001 程序设计入门

- [L1-01·大主题1 值、变量与两套心智模型](001-intro-programming/t01-values-variables-models.md)
- [L1-01·大主题2 控制流与结构化编程](001-intro-programming/t02-control-flow.md)
- [L1-01·大主题3 函数、作用域与调用约定](001-intro-programming/t03-functions-scope-calling.md)
- [L1-01·大主题4 递归与栈](001-intro-programming/t04-recursion-and-stack.md)
- [L1-01·大主题5 复合数据与基础数据结构入门](001-intro-programming/t05-compound-data-structures-intro.md)
- [L1-01·大主题6 指针与内存（C 专属地基）](001-intro-programming/t06-pointers-and-memory.md)
- [L1-01·大主题7 简单 I/O 与类型表示](001-intro-programming/t07-io-and-type-representation.md)
- [L1-01·大主题8 程序正确性与调试基础](001-intro-programming/t08-correctness-debugging.md)

#### 002 离散数学

- [L1-02·大主题1 逻辑与证明](002-discrete-math/t01-logic-and-proof.md)
- [L1-02·大主题2 基本结构：集合·函数·序列·求和](002-discrete-math/t02-sets-functions-sequences-sums.md)
- [L1-02·大主题3 算法与函数增长](002-discrete-math/t03-algorithms-and-growth-of-functions.md)
- [L1-02·大主题4 数论与密码学](002-discrete-math/t04-number-theory-and-cryptography.md)
- [L1-02·大主题5 归纳与递归](002-discrete-math/t05-induction-and-recursion.md)
- [L1-02·大主题6 计数（组合学）](002-discrete-math/t06-counting-combinatorics.md)
- [L1-02·大主题7 离散概率](002-discrete-math/t07-discrete-probability.md)
- [L1-02·大主题8 高级计数技术（递推与容斥）](002-discrete-math/t08-advanced-counting-recurrences-inclusion-exclusion.md)
- [L1-02·大主题9 关系](002-discrete-math/t09-relations.md)
- [L1-02·大主题10 图与树](002-discrete-math/t10-graphs-and-trees.md)
- [L1-02·大主题11 布尔代数（代数层）](002-discrete-math/t11-boolean-algebra.md)
- [L1-02·大主题12 计算模型（导论）](002-discrete-math/t12-models-of-computation.md)

#### 003 线性代数

- [L1-03·大主题1 线性方程组与消元](003-linear-algebra/t01-linear-systems-and-elimination.md)
- [L1-03·大主题2 向量空间与四大子空间](003-linear-algebra/t02-vector-spaces-four-subspaces.md)
- [L1-03·大主题3 正交性、投影与最小二乘](003-linear-algebra/t03-orthogonality-projection-least-squares.md)
- [L1-03·大主题4 行列式](003-linear-algebra/t04-determinants.md)
- [L1-03·大主题5 特征值·特征向量·对角化](003-linear-algebra/t05-eigenvalues-diagonalization.md)
- [L1-03·大主题6 对称矩阵与正定性（谱定理/二次型）](003-linear-algebra/t06-symmetric-matrices-positive-definiteness.md)
- [L1-03·大主题7 奇异值分解与伪逆](003-linear-algebra/t07-svd-and-pseudoinverse.md)
- [L1-03·大主题8 线性变换与基变换](003-linear-algebra/t08-linear-transformations-change-of-basis.md)
- [L1-03·大主题9 复矩阵与 FFT](003-linear-algebra/t09-complex-matrices-fft.md)

#### 004 微积分

- [L1-04·大主题1 极限与连续](004-calculus/t01-limits-continuity.md)
- [L1-04·大主题2 导数与微分法](004-calculus/t02-derivatives-differentiation.md)
- [L1-04·大主题3 导数的应用](004-calculus/t03-derivative-applications.md)
- [L1-04·大主题4 积分与微积分基本定理](004-calculus/t04-integration-ftc.md)
- [L1-04·大主题5 积分技巧与应用](004-calculus/t05-integration-techniques-applications.md)
- [L1-04·大主题6 级数与泰勒展开](004-calculus/t06-series-taylor.md)
- [L1-04·大主题7 多元函数·偏导·梯度](004-calculus/t07-multivariable-partial-gradient.md)
- [L1-04·大主题8 多重积分](004-calculus/t08-multiple-integrals.md)
- [L1-04·大主题9 向量微积分](004-calculus/t09-vector-calculus.md)
- [L1-04·大主题10 微分方程与凸性接口](004-calculus/t10-differential-equations-convexity.md)

### L2 核心基础

#### 005 数据结构

- [L2-01·大主题1 抽象数据类型与渐近分析](005-data-structures/t01-adt-and-asymptotics.md)
- [L2-01·大主题2 线性表：数组与链表](005-data-structures/t02-linear-lists-arrays-linked.md)
- [L2-01·大主题3 受限接口结构：栈/队列/双端队列](005-data-structures/t03-stack-queue-deque.md)
- [L2-01·大主题4 哈希表](005-data-structures/t04-hash-tables.md)
- [L2-01·大主题5 树与二叉搜索树](005-data-structures/t05-trees-and-bst.md)
- [L2-01·大主题6 平衡搜索树](005-data-structures/t06-balanced-search-trees.md)
- [L2-01·大主题7 堆与优先队列](005-data-structures/t07-heap-priority-queue.md)
- [L2-01·大主题8 不相交集合（并查集）](005-data-structures/t08-disjoint-sets-union-find.md)
- [L2-01·大主题9 图的表示与基础遍历](005-data-structures/t09-graph-representation-traversal.md)
- [L2-01·大主题10 字符串/前缀结构（Trie）](005-data-structures/t10-trie-prefix-structures.md)
- [L2-01·大主题11 排序（作为数据结构应用）](005-data-structures/t11-sorting.md)

#### 006 数字逻辑电路

- [L2-02·大主题DL-1 数制与信息编码](006-digital-logic/t01-number-systems-encoding.md)
- [L2-02·大主题2 布尔代数与组合逻辑化简](006-digital-logic/t02-boolean-algebra-combinational-simplification.md)
- [L2-02·大主题DL-3 组合电路构件](006-digital-logic/t03-combinational-building-blocks.md)
- [L2-02·DL-4 时序逻辑基础](006-digital-logic/t04-sequential-logic-fundamentals.md)
- [L2-02·大主题DL-5 有限状态机（硬件 FSM）](006-digital-logic/t05-finite-state-machines.md)
- [L2-02·大主题DL-6 时序分析与时钟](006-digital-logic/t06-timing-analysis-clock.md)
- [L2-02·大主题DL-7 数字构建块与存储阵列](006-digital-logic/t07-building-blocks-memory-arrays.md)

#### 007 概率论与数理统计

- [L2-03·大主题1 概率基础与计数](007-probability-statistics/t01-probability-basics-counting.md)
- [L2-03·大主题2 条件概率、独立性与贝叶斯](007-probability-statistics/t02-conditional-probability-independence-bayes.md)
- [L2-03·大主题3 随机变量与常见分布族](007-probability-statistics/t03-random-variables-distributions.md)
- [L2-03·大主题4 期望、方差与矩](007-probability-statistics/t04-expectation-variance-moments.md)
- [L2-03·大主题5 联合分布、协方差与变换](007-probability-statistics/t05-joint-distributions-covariance-transformations.md)
- [L2-03·大主题6 条件期望](007-probability-statistics/t06-conditional-expectation.md)
- [L2-03·大主题7 不等式与极限定理（LLN/CLT）](007-probability-statistics/t07-inequalities-limit-theorems.md)
- [L2-03·大主题8 随机过程导论（马氏链/泊松）](007-probability-statistics/t08-stochastic-processes-markov-poisson.md)
- [L2-03·大主题9 参数估计（点估计·MLE/MAP·区间）](007-probability-statistics/t09-parameter-estimation-mle-map-intervals.md)
- [L2-03·大主题10 假设检验](007-probability-statistics/t10-hypothesis-testing.md)

### L3 系统与理论支柱

#### 008 算法设计与分析

- [L3-01·大主题1 算法分析与渐进记号](008-algorithms/t01-analysis-asymptotic-notation.md)
- [L3-01·大主题2 分治与递归式求解](008-algorithms/t02-divide-conquer.md)
- [L3-01·大主题3 随机化算法与概率分析](008-algorithms/t03-randomized.md)
- [L3-01·大主题4 排序与比较模型下界](008-algorithms/t04-sorting.md)
- [L3-01·大主题5 中位数与顺序统计量](008-algorithms/t05-selection-order-statistics.md)
- [L3-01·大主题6 高级数据结构](008-algorithms/t06-adv-datastruct.md)
- [L3-01·大主题7 动态规划](008-algorithms/t07-dynamic-programming.md)
- [L3-01·大主题8 贪心算法与拟阵](008-algorithms/t08-greedy.md)
- [L3-01·大主题9 摊还分析](008-algorithms/t09-amortized.md)
- [L3-01·大主题10 图的遍历与连通性](008-algorithms/t10-graph-traversal.md)
- [L3-01·大主题11 最小生成树与最短路](008-algorithms/t11-mst-shortestpath.md)
- [L3-01·大主题12 网络流与二部图匹配](008-algorithms/t12-flow-matching.md)
- [L3-01·大主题13 NP完全性与多项式归约](008-algorithms/t13-np-completeness.md)
- [L3-01·大主题14 近似算法入门](008-algorithms/t14-approximation.md)
- [L3-01·大主题15 专题选讲（Selected Topics）](008-algorithms/t15-selected-topics.md)

#### 009 计算机组成原理

- [L3-02·大主题1 计算机抽象与性能评价](009-computer-organization/t01-abstraction-performance.md)
- [L3-02·大主题2 指令集体系结构（ISA）](009-computer-organization/t02-isa.md)
- [L3-02·大主题3 计算机算术](009-computer-organization/t03-arithmetic.md)
- [L3-02·大主题4 处理器数据通路与控制（单周期）](009-computer-organization/t04-datapath-control.md)
- [L3-02·大主题5 流水线](009-computer-organization/t05-pipelining.md)
- [L3-02·大主题6 内存层次与缓存](009-computer-organization/t06-memory-hierarchy-cache.md)
- [L3-02·大主题7 I/O 与总线](009-computer-organization/t07-io-bus.md)
- [L3-02·大主题8 并行处理器与多核入门](009-computer-organization/t08-parallel-multicore.md)

#### 010 汇编与机器级表示

- [L3-03·大主题1 机器级程序基础与编译产物](010-assembly-machine-level/t01-machine-program-basics.md)
- [L3-03·大主题2 数据格式与信息访问](010-assembly-machine-level/t02-data-formats-access.md)
- [L3-03·大主题3 算术逻辑与位运算](010-assembly-machine-level/t03-arithmetic-logic-bitops.md)
- [L3-03·大主题4 控制流](010-assembly-machine-level/t04-control-flow.md)
- [L3-03·大主题5 过程、栈帧与调用约定 SysV ABI](010-assembly-machine-level/t05-procedures-stack-abi.md)
- [L3-03·大主题6 复合数据的机器表示](010-assembly-machine-level/t06-composite-data-representation.md)
- [L3-03·大主题7 浮点机器码与安全代码模式](010-assembly-machine-level/t07-float-machine-secure-patterns.md)

#### 011 面向对象与程序设计范式

- [L3-04·大主题1 编程范式总览与抽象层次](011-oop-paradigms/t01-paradigms-overview-abstraction.md)
- [L3-04·大主题2 数据抽象与封装](011-oop-paradigms/t02-data-abstraction-encapsulation.md)
- [L3-04·大主题3 OO 三支柱：封装/继承/多态](011-oop-paradigms/t03-oo-three-pillars.md)
- [L3-04·大主题4 动态派发机制](011-oop-paradigms/t04-dynamic-dispatch.md)
- [L3-04·大主题5 接口、抽象与设计原则](011-oop-paradigms/t05-interfaces-design-principles.md)
- [L3-04·大主题6 泛型与参数多态](011-oop-paradigms/t06-generics-parametric-polymorphism.md)
- [L3-04·大主题7 函数式范式](011-oop-paradigms/t07-functional-paradigm.md)
- [L3-04·大主题8 状态、可变性与求值模型](011-oop-paradigms/t08-state-mutability-evaluation.md)
- [L3-04·大主题9 声明式与元语言抽象（选修广度）](011-oop-paradigms/t09-declarative-metalinguistic.md)
- [L3-04·大主题10 设计模式作为范式产物（可选）](011-oop-paradigms/t10-design-patterns-as-paradigm.md)

#### 012 计算理论与自动机

- [L3-05·大主题01 数学预备与证明方法](012-theory-of-computation/t01-math-preliminaries-proofs.md)
- [L3-05·大主题02 有限自动机与正则语言](012-theory-of-computation/t02-finite-automata-regular.md)
- [L3-05·大主题03 上下文无关语言](012-theory-of-computation/t03-context-free-languages.md)
- [L3-05·大主题04 图灵机与丘奇–图灵论题](012-theory-of-computation/t04-turing-machines-church-turing.md)
- [L3-05·大主题05 可判定性](012-theory-of-computation/t05-decidability.md)
- [L3-05·大主题06 归约](012-theory-of-computation/t06-reducibility.md)
- [L3-05·大主题07 可计算性高级专题（可选延伸章）](012-theory-of-computation/t07-computability-advanced.md)
- [L3-05·大主题08 时间复杂度：P/NP/NP 完全](012-theory-of-computation/t08-time-complexity-p-np.md)
- [L3-05·大主题09 空间复杂度](012-theory-of-computation/t09-space-complexity.md)
- [L3-05·大主题10 难解性：层级定理与电路复杂度](012-theory-of-computation/t10-intractability-hierarchy-circuits.md)
- [L3-05·大主题11 复杂度高级专题（可选延伸章）](012-theory-of-computation/t11-complexity-advanced.md)

### L4 系统方向主干

#### 013 操作系统

- [L4-01·大主题01 操作系统概念、双模式与内核结构](013-operating-systems/t01-os-concepts-dual-mode-kernel.md)
- [L4-01·大主题02 进程抽象与进程 API](013-operating-systems/t02-process-abstraction-api.md)
- [L4-01·大主题03 受限直接执行：中断、陷入与系统调用](013-operating-systems/t03-limited-direct-execution-syscalls.md)
- [L4-01·大主题04 线程抽象与线程 API](013-operating-systems/t04-thread-abstraction-api.md)
- [L4-01·大主题05 CPU 调度](013-operating-systems/t05-cpu-scheduling.md)
- [L4-01·大主题06 同步原语与锁实现](013-operating-systems/t06-synchronization-locks.md)
- [L4-01·大主题07 并发 bug 与死锁](013-operating-systems/t07-concurrency-bugs-deadlock.md)
- [L4-01·大主题08 地址空间与内存 API](013-operating-systems/t08-address-space-memory-api.md)
- [L4-01·大主题09 地址翻译与分页机制](013-operating-systems/t09-address-translation-paging.md)
- [L4-01·大主题10 缺页、页替换与交换](013-operating-systems/t10-page-faults-replacement-swapping.md)
- [L4-01·大主题11 I/O 子系统与设备驱动](013-operating-systems/t11-io-subsystem-drivers.md)
- [L4-01·大主题12 持久存储介质与 RAID](013-operating-systems/t12-persistent-storage-raid.md)
- [L4-01·大主题13 文件与目录接口](013-operating-systems/t13-file-directory-interface.md)
- [L4-01·大主题14 文件系统实现与崩溃一致性](013-operating-systems/t14-filesystem-impl-crash-consistency.md)
- [L4-01·大主题15 虚拟化与容器隔离](013-operating-systems/t15-virtualization-container-isolation.md)

#### 014 计算机网络

- [L4-02·大主题N1 分层与封装参照系](014-computer-networks/t01-layering-encapsulation.md)
- [L4-02·大主题N2 链路层与局域网](014-computer-networks/t02-link-layer-lan.md)
- [L4-02·大主题N3 网络层·数据平面（IP 编址与转发）](014-computer-networks/t03-network-layer-data-plane.md)
- [L4-02·大主题N4 网络层·控制平面（路由）](014-computer-networks/t04-network-layer-control-plane.md)
- [L4-02·大主题N5 传输层与可靠数据传输](014-computer-networks/t05-transport-reliable-data.md)
- [L4-02·大主题N6 TCP 拥塞控制与流控](014-computer-networks/t06-tcp-congestion-flow-control.md)
- [L4-02·大主题N7 DNS 名字解析](014-computer-networks/t07-dns.md)
- [L4-02·大主题N8 HTTP 与 Web（含 QUIC/HTTP3）](014-computer-networks/t08-http-web-quic-http3.md)
- [L4-02·大主题N9 TLS 安全信道与网络安全基础](014-computer-networks/t09-tls-network-security.md)
- [L4-02·大主题N10 无线与移动网络](014-computer-networks/t10-wireless-mobile.md)
- [L4-02·大主题N11 API 设计（并入 N-09）](014-computer-networks/t11-api-design.md)

#### 015 数据库系统

- [L4-03·大主题03-1 关系模型与关系代数](015-database-systems/t01-relational-model-algebra.md)
- [L4-03·大主题03-2 SQL 语言（基础→高级）](015-database-systems/t02-sql-language.md)
- [L4-03·大主题03-3 数据库设计与 E-R 模型](015-database-systems/t03-db-design-er-model.md)
- [L4-03·大主题03-4 范式与模式设计](015-database-systems/t04-normalization-schema-design.md)
- [L4-03·大主题03-5 物理存储与文件组织](015-database-systems/t05-physical-storage-file-org.md)
- [L4-03·大主题03-6 索引与 B+树](015-database-systems/t06-indexes-bplus-tree.md)
- [L4-03·大主题03-7 查询处理与执行](015-database-systems/t07-query-processing-execution.md)
- [L4-03·大主题03-8 查询优化](015-database-systems/t08-query-optimization.md)
- [L4-03·大主题03-9 事务与 ACID](015-database-systems/t09-transactions-acid.md)
- [L4-03·大主题03-10 并发控制](015-database-systems/t10-concurrency-control.md)
- [L4-03·大主题03-11 日志与恢复](015-database-systems/t11-logging-recovery.md)
- [L4-03·大主题03-12 （边界）分布式/并行数据库概览](015-database-systems/t12-distributed-parallel-db-overview.md)

#### 016 编译原理

- [L4-04·大主题C1 编译器整体结构与流水线](016-compilers/t01-compiler-structure-pipeline.md)
- [L4-04·大主题C2 词法分析](016-compilers/t02-lexical-analysis.md)
- [L4-04·大主题C3 语法分析](016-compilers/t03-syntax-analysis.md)
- [L4-04·大主题C4 语法制导翻译与 AST](016-compilers/t04-sdt-ast.md)
- [L4-04·大主题C5 语义分析与类型检查](016-compilers/t05-semantic-analysis-typechecking.md)
- [L4-04·大主题C6 中间代码生成](016-compilers/t06-intermediate-code-generation.md)
- [L4-04·大主题C7 运行时环境](016-compilers/t07-runtime-environment.md)
- [L4-04·大主题C8 目标代码生成](016-compilers/t08-target-code-generation.md)
- [L4-04·大主题C9 机器无关优化与数据流分析](016-compilers/t09-machine-independent-opt-dataflow.md)
- [L4-04·大主题C10 高级优化专题](016-compilers/t10-advanced-optimization.md)

#### 017 并发与并行程序设计

- [L4-05·大主题01 互斥问题与理论](017-concurrency-parallelism/t01-mutual-exclusion-theory.md)
- [L4-05·大主题02 并发对象与线性一致性](017-concurrency-parallelism/t02-concurrent-objects-linearizability.md)
- [L4-05·大主题03 共享内存基础与寄存器构造](017-concurrency-parallelism/t03-shared-memory-registers.md)
- [L4-05·大主题04 同步原语的相对能力与共识](017-concurrency-parallelism/t04-primitive-power-consensus.md)
- [L4-05·大主题05 内存一致性模型与重排序](017-concurrency-parallelism/t05-memory-consistency-reordering.md)
- [L4-05·大主题06 原子操作与 memory_order](017-concurrency-parallelism/t06-atomics-memory-order.md)
- [L4-05·大主题07 数据竞争定义与 UB](017-concurrency-parallelism/t07-data-race-ub.md)
- [L4-05·大主题08 自旋锁与竞争](017-concurrency-parallelism/t08-spinlocks-contention.md)
- [L4-05·大主题09 阻塞同步与 monitor](017-concurrency-parallelism/t09-blocking-sync-monitor.md)
- [L4-05·大主题10 无锁并发数据结构](017-concurrency-parallelism/t10-lockfree-data-structures.md)
- [L4-05·大主题11 可扩展并发结构与分布式协调](017-concurrency-parallelism/t11-scalable-structures-coordination.md)
- [L4-05·大主题12 任务并行与调度](017-concurrency-parallelism/t12-task-parallelism-scheduling.md)
- [L4-05·大主题13 事务内存](017-concurrency-parallelism/t13-transactional-memory.md)
- [L4-05·大主题14 消息传递与并行范式](017-concurrency-parallelism/t14-message-passing-paradigms.md)
- [L4-05·大主题15 并行性能模型](017-concurrency-parallelism/t15-parallel-performance-models.md)

#### 018 软件工程

- [L4-06·大主题06-1 软工过程与工程质量观](018-software-engineering/t01-process-quality.md)
- [L4-06·大主题06-2 规约、契约与抽象数据类型](018-software-engineering/t02-specifications-contracts-adt.md)
- [L4-06·大主题06-3 静态检查与类型安全防御](018-software-engineering/t03-static-checking-type-safety.md)
- [L4-06·大主题06-4 Git 对象模型（内容寻址 DAG）](018-software-engineering/t04-git-object-model.md)
- [L4-06·大主题06-5 Git 引用/分支/HEAD 指针机制](018-software-engineering/t05-git-refs-branches-head.md)
- [L4-06·大主题06-6 三向合并与 merge-base（rebase vs merge）](018-software-engineering/t06-three-way-merge-rebase.md)
- [L4-06·大主题06-7 分布式协作、PR 评审与传输协议](018-software-engineering/t07-distributed-collab-pr-protocols.md)
- [L4-06·大主题06-8 分支策略](018-software-engineering/t08-branching-strategies.md)
- [L4-06·大主题06-9 测试金字塔与测试分层](018-software-engineering/t09-test-pyramid-layers.md)
- [L4-06·大主题06-10 测试替身与覆盖率解读](018-software-engineering/t10-test-doubles-coverage.md)
- [L4-06·大主题06-11 TDD 与测试作为设计压力](018-software-engineering/t11-tdd-design-pressure.md)
- [L4-06·大主题06-12 CI 持续集成与门禁](018-software-engineering/t12-ci-gates.md)
- [L4-06·大主题06-13 CD：持续交付 vs 持续部署](018-software-engineering/t13-cd-delivery-deployment.md)

### L5 进阶与应用方向

#### 019 分布式系统

- [L5-01·大主题1 系统模型、RPC 与失败模型](019-distributed-systems/t01-system-models-rpc-failure.md)
- [L5-01·大主题2 时间、时钟与因果序](019-distributed-systems/t02-time-clocks-causality.md)
- [L5-01·大主题3 复制与复制状态机（RSM）](019-distributed-systems/t03-replication-rsm.md)
- [L5-01·大主题4 共识：FLP / Paxos / Raft](019-distributed-systems/t04-consensus-flp-paxos-raft.md)
- [L5-01·大主题5 一致性模型谱系](019-distributed-systems/t05-consistency-models.md)
- [L5-01·大主题6 CAP 与 PACELC 分区权衡](019-distributed-systems/t06-cap-pacelc.md)
- [L5-01·大主题7 分布式事务与原子提交](019-distributed-systems/t07-distributed-transactions-commit.md)

#### 020 机器学习

- [L5-02·大主题1 监督学习框架与经验风险最小化](020-machine-learning/t01-supervised-framework-erm.md)
- [L5-02·大主题2 线性回归与优化基础](020-machine-learning/t02-linear-regression-optimization.md)
- [L5-02·大主题3 分类：逻辑回归与广义线性模型](020-machine-learning/t03-logistic-regression-glm.md)
- [L5-02·大主题4 生成式学习](020-machine-learning/t04-generative-learning.md)
- [L5-02·大主题5 支持向量机与核方法](020-machine-learning/t05-svm-kernel-methods.md)
- [L5-02·大主题6 偏差-方差·正则化·模型选择](020-machine-learning/t06-bias-variance-regularization.md)
- [L5-02·大主题7 树与集成方法](020-machine-learning/t07-trees-ensembles.md)
- [L5-02·大主题8 神经网络基础与反向传播（边界主题·入门止）](020-machine-learning/t08-neural-nets-backprop-intro.md)
- [L5-02·大主题9 无监督学习：聚类与密度估计](020-machine-learning/t09-clustering-density.md)
- [L5-02·大主题10 降维与流形学习](020-machine-learning/t10-dimensionality-reduction-manifold.md)
- [L5-02·大主题11 学习理论](020-machine-learning/t11-learning-theory.md)
- [L5-02·大主题12 评估与度量纪律](020-machine-learning/t12-evaluation-metrics.md)

#### 021 计算机图形学

- [L5-03·大主题G-01 图形管线概览与数学预备](021-computer-graphics/t01-pipeline-math-prelim.md)
- [L5-03·大主题G-02 几何变换与投影管线](021-computer-graphics/t02-transforms-projection.md)
- [L5-03·大主题G-03 光栅化与可见性](021-computer-graphics/t03-rasterization-visibility.md)
- [L5-03·大主题G-04 采样、走样与信号处理](021-computer-graphics/t04-sampling-aliasing.md)
- [L5-03·大主题G-05 纹理映射与着色/光照模型](021-computer-graphics/t05-texture-shading-lighting.md)
- [L5-03·大主题G-06 可编程管线与实时渲染 API](021-computer-graphics/t06-programmable-pipeline-api.md)
- [L5-03·大主题G-07 几何表示：曲线曲面/网格/细分/空间数据结构](021-computer-graphics/t07-geometry-representation.md)
- [L5-03·大主题G-08 光线追踪与加速结构](021-computer-graphics/t08-ray-tracing-acceleration.md)
- [L5-03·大主题G-09 辐射度量、反射与蒙特卡洛渲染](021-computer-graphics/t09-radiometry-brdf-montecarlo.md)
- [L5-03·大主题G-10 物理动画与数值方法](021-computer-graphics/t10-physics-animation-numerics.md)

#### 022 信息安全与密码学

- [L5-04·大主题01 安全基本原理与古典密码](022-security-cryptography/t01-security-principles-classical.md)
- [L5-04·大主题02 数论基础](022-security-cryptography/t02-number-theory.md)
- [L5-04·大主题03 对称加密与分组密码](022-security-cryptography/t03-symmetric-block-ciphers.md)
- [L5-04·大主题04 分组工作模式与认证加密(AEAD)](022-security-cryptography/t04-block-modes-aead.md)
- [L5-04·大主题05 哈希函数与 MAC](022-security-cryptography/t05-hash-mac.md)
- [L5-04·大主题06 公钥加密与 RSA](022-security-cryptography/t06-public-key-rsa.md)
- [L5-04·大主题07 DH 密钥交换与椭圆曲线密码](022-security-cryptography/t07-dh-ecc.md)
- [L5-04·大主题08 数字签名](022-security-cryptography/t08-digital-signatures.md)
- [L5-04·大主题09 PKI 与证书](022-security-cryptography/t09-pki-certificates.md)
- [L5-04·大主题10 TLS 与安全信道协议](022-security-cryptography/t10-tls-secure-channel.md)
- [L5-04·大主题11 后量子密码(PQC)](022-security-cryptography/t11-post-quantum-crypto.md)
- [L5-04·大主题12 内存安全漏洞与控制流劫持](022-security-cryptography/t12-memory-safety-vulns.md)
- [L5-04·大主题13 内存安全缓解与隔离](022-security-cryptography/t13-memory-safety-mitigations.md)
- [L5-04·大主题14 侧信道与微架构安全](022-security-cryptography/t14-side-channel-uarch.md)
- [L5-04·大主题15 Web 安全](022-security-cryptography/t15-web-security.md)
- [L5-04·大主题16 认证、访问控制与口令安全](022-security-cryptography/t16-authn-access-control-passwords.md)
- [L5-04·大主题17 网络安全](022-security-cryptography/t17-network-security.md)
- [L5-04·大主题18 隐私、匿名与差分隐私(DP)](022-security-cryptography/t18-privacy-anonymity-dp.md)

#### 023 高级计算机体系结构

- [L5-05·大主题1 量化设计基础](023-advanced-architecture/t01-quantitative-design.md)
- [L5-05·大主题2 高级内存层次](023-advanced-architecture/t02-advanced-memory-hierarchy.md)
- [L5-05·大主题3 指令级并行与静态调度](023-advanced-architecture/t03-ilp-static-scheduling.md)
- [L5-05·大主题4 动态调度·超标量·投机](023-advanced-architecture/t04-dynamic-scheduling-superscalar.md)
- [L5-05·大主题5 数据级并行 DLP](023-advanced-architecture/t05-data-level-parallelism.md)
- [L5-05·大主题6 线程级并行与多处理器](023-advanced-architecture/t06-tlp-multiprocessors.md)
- [L5-05·大主题7 仓库级计算机 WSC](023-advanced-architecture/t07-warehouse-scale-computers.md)
- [L5-05·大主题8 领域专用体系结构 DSA](023-advanced-architecture/t08-domain-specific-architectures.md)

#### 024 编程语言理论

- [L5-06·大主题T1 预备与语义风格](024-programming-language-theory/t01-preliminaries-semantics-styles.md)
- [L5-06·大主题T2 无类型 λ 演算](024-programming-language-theory/t02-untyped-lambda-calculus.md)
- [L5-06·大主题T3 简单类型 λ 演算与类型安全](024-programming-language-theory/t03-stlc-type-safety.md)
- [L5-06·大主题T4 简单类型系统的扩展](024-programming-language-theory/t04-stlc-extensions.md)
- [L5-06·大主题T5 子类型](024-programming-language-theory/t05-subtyping.md)
- [L5-06·大主题T6 递归类型](024-programming-language-theory/t06-recursive-types.md)
- [L5-06·大主题T7 类型重建与 let-多态](024-programming-language-theory/t07-type-reconstruction-let-polymorphism.md)
- [L5-06·大主题T8 全称/存在多态](024-programming-language-theory/t08-universal-existential-polymorphism.md)
- [L5-06·大主题T9 有界量化](024-programming-language-theory/t09-bounded-quantification.md)
- [L5-06·大主题T10 高阶系统](024-programming-language-theory/t10-higher-order-systems.md)

#### 031 符号与经典 AI

- [L5-08·大主题1 智能体范式与问题形式化](031-symbolic-classical-ai/t01-agent-paradigms-problem-formulation.md)
- [L5-08·大主题2 无信息搜索](031-symbolic-classical-ai/t02-uninformed-search.md)
- [L5-08·大主题3 启发式（有信息）搜索](031-symbolic-classical-ai/t03-informed-heuristic-search.md)
- [L5-08·大主题4 复杂环境中的搜索](031-symbolic-classical-ai/t04-search-complex-environments.md)
- [L5-08·大主题5 对抗搜索与博弈](031-symbolic-classical-ai/t05-adversarial-search-games.md)
- [L5-08·大主题6 约束满足问题 CSP](031-symbolic-classical-ai/t06-constraint-satisfaction.md)
- [L5-08·大主题7 命题逻辑与逻辑智能体](031-symbolic-classical-ai/t07-propositional-logic-agents.md)
- [L5-08·大主题8 一阶逻辑与推理](031-symbolic-classical-ai/t08-first-order-logic-inference.md)
- [L5-08·大主题9 知识表示](031-symbolic-classical-ai/t09-knowledge-representation.md)
- [L5-08·大主题10 自动规划](031-symbolic-classical-ai/t10-automated-planning.md)
- [L5-08·大主题11 不确定性下的推理与序贯决策](031-symbolic-classical-ai/t11-uncertainty-sequential-decision.md)

#### 032 人机交互 HCI

- [L5-09·大主题1 人的认知基础与人因](032-hci/t01-cognition-human-factors.md)
- [L5-09·大主题2 可用性的可测量定义](032-hci/t02-usability-measurable.md)
- [L5-09·大主题3 交互设计原则](032-hci/t03-interaction-design-principles.md)
- [L5-09·大主题4 评估方法体系（analytic vs empirical）](032-hci/t04-evaluation-methods.md)
- [L5-09·大主题5 以用户为中心的设计流程 + 原型迭代（UCD）](032-hci/t05-ucd-prototyping.md)
- [L5-09·大主题6 无障碍与包容性设计](032-hci/t06-accessibility-inclusive-design.md)
- [L5-09·大主题7 交互范式与新型模态](032-hci/t07-interaction-paradigms-modalities.md)

#### 034 系统设计

- [L5-11·大主题1 需求与非功能目标定义](034-system-design/t01-requirements-nfr.md)
- [L5-11·大主题2 容量估算方法学（back-of-envelope）](034-system-design/t02-capacity-estimation.md)
- [L5-11·大主题3 负载均衡与流量分发](034-system-design/t03-load-balancing.md)
- [L5-11·大主题4 缓存策略](034-system-design/t04-caching-strategies.md)
- [L5-11·大主题5 数据分片 sharding](034-system-design/t05-sharding.md)
- [L5-11·大主题6 复制 replication](034-system-design/t06-replication.md)
- [L5-11·大主题7 一致性-可用性-延迟折衷](034-system-design/t07-consistency-availability-latency.md)
- [L5-11·大主题8 消息队列与异步/事件驱动](034-system-design/t08-message-queues-async.md)
- [L5-11·大主题9 容错工程模式](034-system-design/t09-fault-tolerance-patterns.md)
- [L5-11·大主题10 读写路径与数据模型选型](034-system-design/t10-read-write-path-data-model.md)

#### 035 可观测性与 SRE

- [L5-12·大主题1 SRE 原则与 DevOps 关系](035-observability-sre/t01-sre-principles-devops.md)
- [L5-12·大主题2 SLI / SLO / 错误预算](035-observability-sre/t02-sli-slo-error-budget.md)
- [L5-12·大主题3 基于 SLO 的告警（burn-rate）](035-observability-sre/t03-slo-alerting-burn-rate.md)
- [L5-12·大主题4 metrics 支柱与监控分布式系统](035-observability-sre/t04-metrics-pillar.md)
- [L5-12·大主题5 logs 支柱](035-observability-sre/t05-logs-pillar.md)
- [L5-12·大主题6 traces 支柱与分布式追踪](035-observability-sre/t06-traces-distributed-tracing.md)
- [L5-12·大主题7 OpenTelemetry 统一遥测](035-observability-sre/t07-opentelemetry.md)
- [L5-12·大主题8 消除 toil 与自动化](035-observability-sre/t08-toil-automation.md)
- [L5-12·大主题9 事件响应与无指责事后复盘](035-observability-sre/t09-incident-response-postmortem.md)
- [L5-12·大主题10 发布工程与金丝雀发布](035-observability-sre/t10-release-engineering-canary.md)
- [L5-12·大主题11 过载处理与级联失败](035-observability-sre/t11-overload-cascading-failure.md)

#### 036 容器与云原生

- [L5-13·大主题1 容器 vs VM 与隔离模型](036-containers-cloud-native/t01-containers-vs-vm.md)
- [L5-13·大主题2 Linux namespaces（视图隔离）](036-containers-cloud-native/t02-linux-namespaces.md)
- [L5-13·大主题3 cgroups（资源限额，v1 vs v2）](036-containers-cloud-native/t03-cgroups.md)
- [L5-13·大主题4 OCI 镜像格式与分层/union FS](036-containers-cloud-native/t04-oci-image-format.md)
- [L5-13·大主题5 OCI 运行时与容器运行时链](036-containers-cloud-native/t05-oci-runtime-chain.md)
- [L5-13·大主题6 OCI 分发与镜像仓库](036-containers-cloud-native/t06-oci-distribution-registry.md)
- [L5-13·大主题7 K8s 工作负载对象](036-containers-cloud-native/t07-k8s-workloads.md)
- [L5-13·大主题8 K8s 声明式模型与 reconcile 循环](036-containers-cloud-native/t08-k8s-declarative-reconcile.md)
- [L5-13·大主题9 K8s 服务发现/负载均衡/网络](036-containers-cloud-native/t09-k8s-networking.md)
- [L5-13·大主题10 K8s 调度/抢占/驱逐](036-containers-cloud-native/t10-k8s-scheduling.md)
- [L5-13·大主题11 K8s 配置与存储](036-containers-cloud-native/t11-k8s-config-storage.md)
- [L5-13·大主题12 容器安全边界](036-containers-cloud-native/t12-container-security.md)

#### 037 Rust 与内存安全

- [L5-14·大主题R1 Rust 定位、工具链与工程骨架](037-rust-memory-safety/t01-rust-toolchain-project.md)
- [L5-14·大主题R2 所有权与 move 语义](037-rust-memory-safety/t02-ownership-move.md)
- [L5-14·大主题R3 借用、引用与借用检查器](037-rust-memory-safety/t03-borrowing-borrow-checker.md)
- [L5-14·大主题R4 生命周期与区域推断](037-rust-memory-safety/t04-lifetimes-regions.md)
- [L5-14·大主题R5 代数数据类型与模式匹配](037-rust-memory-safety/t05-adt-pattern-matching.md)
- [L5-14·大主题R6 泛型、trait 与类型推导](037-rust-memory-safety/t06-generics-traits.md)
- [L5-14·大主题R7 错误处理](037-rust-memory-safety/t07-error-handling.md)
- [L5-14·大主题R8 智能指针与内部可变性](037-rust-memory-safety/t08-smart-pointers-interior-mutability.md)
- [L5-14·大主题R9 无畏并发](037-rust-memory-safety/t09-fearless-concurrency.md)
- [L5-14·大主题R10 异步编程](037-rust-memory-safety/t10-async-programming.md)
- [L5-14·大主题R11 unsafe 与安全边界](037-rust-memory-safety/t11-unsafe-safety-boundary.md)
- [L5-14·大主题R12 形式基础与保证](037-rust-memory-safety/t12-formal-foundations.md)

#### 038 嵌入式系统

- [L5-15·大主题1 嵌入式/CPS 概念与设计约束](038-embedded-systems/t01-embedded-cps-constraints.md)
- [L5-15·大主题2 处理器与内存架构](038-embedded-systems/t02-processor-memory-architecture.md)
- [L5-15·大主题3 裸机执行模型、启动与工具链](038-embedded-systems/t03-bare-metal-boot-toolchain.md)
- [L5-15·大主题4 GPIO 与数字 I/O](038-embedded-systems/t04-gpio-digital-io.md)
- [L5-15·大主题5 中断与实时响应](038-embedded-systems/t05-interrupts-realtime.md)
- [L5-15·大主题6 定时器、PWM 与时钟系统](038-embedded-systems/t06-timers-pwm-clocks.md)
- [L5-15·大主题7 模拟接口：ADC / DAC](038-embedded-systems/t07-adc-dac.md)
- [L5-15·大主题8 串行通信总线](038-embedded-systems/t08-serial-buses.md)
- [L5-15·大主题9 DMA 与高效数据搬运](038-embedded-systems/t09-dma.md)
- [L5-15·大主题10 RTOS、多任务与实时调度](038-embedded-systems/t10-rtos-scheduling.md)
- [L5-15·大主题11 建模、分析与形式验证（CPS）](038-embedded-systems/t11-modeling-formal-verification.md)
- [L5-15·大主题12 低功耗与可靠性设计权衡](038-embedded-systems/t12-low-power-reliability.md)

### L6 前沿与专题

#### 025 高级算法

- [L6-01·大主题01 高级数据结构与摊还加速](025-advanced-algorithms/t01-advanced-ds-amortization.md)
- [L6-01·大主题02 网络流进阶](025-advanced-algorithms/t02-advanced-network-flow.md)
- [L6-01·大主题03 线性规划与对偶](025-advanced-algorithms/t03-linear-programming-duality.md)
- [L6-01·大主题04 近似算法](025-advanced-algorithms/t04-approximation-algorithms.md)
- [L6-01·大主题05 随机化算法](025-advanced-algorithms/t05-randomized-algorithms.md)
- [L6-01·大主题06 在线算法与竞争分析](025-advanced-algorithms/t06-online-algorithms-competitive.md)
- [L6-01·大主题07 亚线性与流式算法](025-advanced-algorithms/t07-sublinear-streaming.md)
- [L6-01·大主题08 高维几何与度量嵌入](025-advanced-algorithms/t08-high-dim-geometry-embeddings.md)

#### 026 程序分析与形式验证

- [L6-02·大主题V1 不可判定性与近似框架](026-program-analysis-verification/t01-undecidability-approximation.md)
- [L6-02·大主题V2 数据流分析](026-program-analysis-verification/t02-dataflow-analysis.md)
- [L6-02·大主题V3 约束式分析](026-program-analysis-verification/t03-constraint-based-analysis.md)
- [L6-02·大主题V4 抽象解释](026-program-analysis-verification/t04-abstract-interpretation.md)
- [L6-02·大主题V5 类型与效果系统](026-program-analysis-verification/t05-type-effect-systems.md)
- [L6-02·大主题V6 分析求解算法](026-program-analysis-verification/t06-analysis-solving-algorithms.md)
- [L6-02·大主题V7 符号执行](026-program-analysis-verification/t07-symbolic-execution.md)
- [L6-02·大主题V8 模型检验](026-program-analysis-verification/t08-model-checking.md)
- [L6-02·大主题V9 演绎验证：Hoare 逻辑与最弱前条件](026-program-analysis-verification/t09-hoare-logic-wp.md)
- [L6-02·大主题V10 SMT 求解引擎](026-program-analysis-verification/t10-smt-solving.md)
- [L6-02·大主题V11 交互式定理证明](026-program-analysis-verification/t11-interactive-theorem-proving.md)

#### 027 深度学习及其分支

- [L6-03·大主题1 前馈网络与反向传播 / 自动微分](027-deep-learning/t01-feedforward-backprop-autodiff.md)
- [L6-03·大主题2 深度学习的正则化](027-deep-learning/t02-regularization.md)
- [L6-03·大主题3 深度模型优化](027-deep-learning/t03-optimization.md)
- [L6-03·大主题4 卷积网络](027-deep-learning/t04-convolutional-networks.md)
- [L6-03·大主题5 序列建模：RNN/LSTM/GRU](027-deep-learning/t05-sequence-models-rnn.md)
- [L6-03·大主题6 注意力与 Transformer](027-deep-learning/t06-attention-transformer.md)
- [L6-03·大主题7 表示学习与嵌入](027-deep-learning/t07-representation-learning-embeddings.md)
- [L6-03·大主题8 预训练-微调范式](027-deep-learning/t08-pretrain-finetune.md)
- [L6-03·大主题9 深度生成模型](027-deep-learning/t09-generative-models.md)
- [L6-03·大主题10 深度强化学习](027-deep-learning/t10-deep-reinforcement-learning.md)
- [L6-03·大主题11 分支落地：视觉 / NLP / 多模态](027-deep-learning/t11-vision-nlp-multimodal.md)
- [L6-03·大主题12 实践方法论](027-deep-learning/t12-practical-methodology.md)

#### 028 数据库内核与存储系统

- [L6-04·大主题04-1 现代分析型 DBMS 架构总览](028-database-internals/t01-analytical-dbms-architecture.md)
- [L6-04·大主题04-2 列存布局与压缩编码](028-database-internals/t02-columnar-storage-compression.md)
- [L6-04·大主题04-3 LSM 树存储引擎](028-database-internals/t03-lsm-tree-storage.md)
- [L6-04·大主题04-4 向量化查询执行](028-database-internals/t04-vectorized-execution.md)
- [L6-04·大主题04-5 查询编译与代码生成](028-database-internals/t05-query-compilation-codegen.md)
- [L6-04·大主题04-6 连接与聚合算法的内核实现](028-database-internals/t06-join-aggregation-internals.md)
- [L6-04·大主题04-7 查询优化器实现](028-database-internals/t07-query-optimizer.md)
- [L6-04·大主题04-8 调度与多核并行执行](028-database-internals/t08-scheduling-parallel-execution.md)
- [L6-04·大主题04-9 MVCC 实现内幕](028-database-internals/t09-mvcc-internals.md)
- [L6-04·大主题04-10 分布式事务](028-database-internals/t10-distributed-transactions.md)
- [L6-04·大主题04-11 HTAP 与新硬件](028-database-internals/t11-htap-new-hardware.md)

#### 029 分布式与云系统专题

- [L6-05·大主题1 分布式文件/对象存储](029-distributed-cloud-systems/t01-distributed-file-object-storage.md)
- [L6-05·大主题2 复制状态机与协调服务落地](029-distributed-cloud-systems/t02-replicated-state-machine-coordination.md)
- [L6-05·大主题3 全球分布式事务与外部一致性](029-distributed-cloud-systems/t03-global-transactions-external-consistency.md)
- [L6-05·大主题4 批处理数据处理框架](029-distributed-cloud-systems/t04-batch-processing-frameworks.md)
- [L6-05·大主题5 流处理与数据密集型架构](029-distributed-cloud-systems/t05-stream-processing.md)
- [L6-05·大主题6 集群资源调度与编排](029-distributed-cloud-systems/t06-cluster-scheduling-orchestration.md)

#### 030 高性能计算

- [L6-06·大主题1 并行计算动机与架构分类](030-high-performance-computing/t01-parallel-motivation-taxonomy.md)
- [L6-06·大主题2 性能模型与扩展性](030-high-performance-computing/t02-performance-models-scalability.md)
- [L6-06·大主题3 共享内存并行 / OpenMP](030-high-performance-computing/t03-shared-memory-openmp.md)
- [L6-06·大主题4 分布式内存并行 / MPI](030-high-performance-computing/t04-distributed-memory-mpi.md)
- [L6-06·大主题5 GPU 架构与 CUDA 编程模型](030-high-performance-computing/t05-gpu-cuda-programming.md)
- [L6-06·大主题6 GPU 内存层级与性能优化](030-high-performance-computing/t06-gpu-memory-optimization.md)
- [L6-06·大主题7 并行算法模式](030-high-performance-computing/t07-parallel-algorithm-patterns.md)
- [L6-06·大主题8 数据局部性与通信优化](030-high-performance-computing/t08-locality-communication-optimization.md)
- [L6-06·大主题9 数值库生态](030-high-performance-computing/t09-numerical-libraries.md)
- [L6-06·大主题10 应用与领域案例](030-high-performance-computing/t10-applications-case-studies.md)

#### 039 AI/LLM 系统

- [L6-08·大主题08S-1 从零实现 LM 骨架 + tokenization](039-ai-llm-systems/t01-lm-skeleton-tokenization.md)
- [L6-08·大主题08S-2 资源核算与工程](039-ai-llm-systems/t02-resource-accounting.md)
- [L6-08·大主题08S-3 架构与超参的系统取舍](039-ai-llm-systems/t03-architecture-hyperparams.md)
- [L6-08·大主题08S-4 注意力变体与 MoE](039-ai-llm-systems/t04-attention-variants-moe.md)
- [L6-08·大主题08S-5 GPU/TPU 硬件与内存层次](039-ai-llm-systems/t05-gpu-tpu-memory.md)
- [L6-08·大主题08S-6 Kernel 与 Triton / FlashAttention](039-ai-llm-systems/t06-kernels-triton-flashattention.md)
- [L6-08·大主题08S-7 并行策略](039-ai-llm-systems/t07-parallelism-strategies.md)
- [L6-08·大主题08S-8 缩放定律](039-ai-llm-systems/t08-scaling-laws.md)
- [L6-08·大主题08S-9 推理 / serving](039-ai-llm-systems/t09-inference-serving.md)
- [L6-08·大主题08S-10 评估（系统视角）](039-ai-llm-systems/t10-evaluation-systems.md)
- [L6-08·大主题08S-11 数据工程](039-ai-llm-systems/t11-data-engineering.md)
- [L6-08·大主题08S-12 训练后与对齐](039-ai-llm-systems/t12-post-training-alignment.md)

#### 040 数据密集型应用 DDIA

- [L6-09·大主题09-1 数据系统架构权衡与非功能需求](040-data-intensive-apps/t01-architecture-tradeoffs-nfr.md)
- [L6-09·大主题09-2 数据模型与查询语言](040-data-intensive-apps/t02-data-models-query-languages.md)
- [L6-09·大主题09-3 存储与检索引擎（选型视角）](040-data-intensive-apps/t03-storage-retrieval-engines.md)
- [L6-09·大主题09-4 编码与演化](040-data-intensive-apps/t04-encoding-evolution.md)
- [L6-09·大主题09-5 复制](040-data-intensive-apps/t05-replication.md)
- [L6-09·大主题09-6 分区 / 分片](040-data-intensive-apps/t06-partitioning-sharding.md)
- [L6-09·大主题09-7 事务的分布式视角](040-data-intensive-apps/t07-transactions-distributed.md)
- [L6-09·大主题09-8 分布式系统的麻烦](040-data-intensive-apps/t08-distributed-systems-troubles.md)
- [L6-09·大主题09-9 一致性与共识](040-data-intensive-apps/t09-consistency-consensus.md)
- [L6-09·大主题09-10 批处理](040-data-intensive-apps/t10-batch-processing.md)
- [L6-09·大主题09-11 流处理](040-data-intensive-apps/t11-stream-processing.md)
- [L6-09·大主题09-12 数据伦理与"做正确的事"](040-data-intensive-apps/t12-data-ethics.md)

### 跨层 · 伦理与责任

#### 033 社会伦理职业与负责任 AI

- [L5-10·大主题1 规范性伦理框架](033-sep-responsible-ai/t01-normative-ethics-frameworks.md)
- [L5-10·大主题2 专业操守与责任（ACM Code 2018）](033-sep-responsible-ai/t02-professional-ethics-acm-code.md)
- [L5-10·大主题3 负责任 AI 多维张力](033-sep-responsible-ai/t03-responsible-ai-tensions.md)
- [L5-10·大主题4 AI 治理框架](033-sep-responsible-ai/t04-ai-governance.md)
- [L5-10·大主题5 隐私、数据保护与知识产权](033-sep-responsible-ai/t05-privacy-data-ip.md)
- [L5-10·大主题6 计算的社会情境与影响](033-sep-responsible-ai/t06-social-context-impact.md)
- [L5-10·大主题7 可持续性与算力伦理](033-sep-responsible-ai/t07-sustainability-compute-ethics.md)
