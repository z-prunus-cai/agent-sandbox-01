# Round3c · L5-05 高级计算机体系结构 · 报告 prompt（P2）

```text
[P2] L5-05·大主题1 量化设计基础 — 报告prompt
任务：为「L5-05·大主题1 量化设计基础」产出一份 report-format v3（广度优先教辅式）报告。

【格式硬性（v3，必守）】
- 标题层级严格 #→##→###：# 为报告标题（本大主题），## 为小主题（章节），### 为每个内容项一节。
- ### 之下不写「核心概念：」「辅助说明：」等任何标签，直接正文分段：先一段核心说明（是什么/定义/关键结论，正确），再充分的辅助说明（面向初学者把它讲懂所需的直觉/为什么这样/最小例子/易错点/与前后关联，可多段）。辅助说明是必需项，不是点缀，要写到初学者能懂为止。
- 任何公式/加速比表达式独占一行（块级），不内联埋进句子；流水线/时序/数据流叙述同样独占行或块，不塞进段内。
- 教辅口吻；广度优先——小主题下的内容项要查全、宁多列浅讲不遗漏；每项讲到初学者懂即止，不做专家级纵深（不写长篇机制深挖/完整推导/设计权衡长论）。
- 抬头带可 grep 基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；并标核实日期。

【小主题清单（每个 ## 一节；内容项做 ###）】覆盖下列 5 个小主题，内容项可读 round3b-subtopics/g04-hardware-arch.md 中 L5-05 大主题 AA-1（小主题 AA-1.1~AA-1.5）取全：
- 1.1 设计度量（AA-1.1）：性能/成本/功耗/能效/可靠性/可用性的量化定义。
- 1.2 量化原理（AA-1.2）：Amdahl 定律、常见情形加速（common case fast）、大数定律式经验法则。
- 1.3 局部性与并行（AA-1.3）：局部性原理与三类并行（ILP/DLP/TLP）总览。
- 1.4 评测方法学（AA-1.4）：基准套件、几何平均、结果统计有效性。
- 1.5 ISA 原理（AA-1.5，附录 A）：ISA 分类、寻址、操作数与编码的设计权衡。

【多来源比对（强制）】每个内容项须用 ≥2 个独立来源交叉核对，优先一手：Hennessy & Patterson《Computer Architecture: A Quantitative Approach》6th ed（ch1 + 印刷附录 A）。来源打架时两边都记、点明分歧、不和稀泥。具体数值/默认值/公式系数来自比对通过的来源，或标「待核」，绝不凭记忆填。本机 perf 应用级实证为可选补充（如 AA-1.4 可用 perf 读 instructions/cycles，但须与微架构分账；不能替代多来源比对）。

【章节末来源与时效】每个小主题末集中列锚点（教材§/附录 + 版本 + 核实日期），不逐项脚注；冲突项定位两边来源；未证实项标「待核」，不编造。

【权威锚点清单（取自 round3a）】
- Hennessy & Patterson,《Computer Architecture: A Quantitative Approach》6th ed, 2017, ISBN 9780128119051（正文 7 章 + 印刷附录 A ISA 原理/B 内存层次复习/C 流水线基础；在线附录 D–M）。
- URL（核实 2026-07-25）：shop.elsevier.com/books/computer-architecture/hennessy/978-0-12-811905-1 ; educate.elsevier.com/book/details/9780128119051
- 先修：L3-02 CO-1（计算机抽象与性能评价，本大主题为其深化）。

【粒度判定（先做）】开工先判「1 份 or 拆 N 份 + 理由」：本大主题 5 个小主题偏综述性、篇幅可控，默认倾向 1 份；若判拆分需给理由并用 023-advanced-architecture-<后缀>.md 命名。

【落盘目标】findings/023-advanced-architecture.md（本大主题为课程报告的组成部分；若粒度判定为拆分，用 023-advanced-architecture-<后缀>.md）。报告须自足，不残留工具标签/控制字符。
```

```text
[P2] L5-05·大主题2 高级内存层次 — 报告prompt
任务：为「L5-05·大主题2 高级内存层次」产出一份 report-format v3（广度优先教辅式）报告。

【格式硬性（v3，必守）】
- 标题层级严格 #→##→###：# 报告标题，## 小主题（章节），### 每个内容项一节。
- ### 之下不写任何标签，直接正文分段：先核心说明（是什么/定义/关键结论，正确），再充分辅助说明（初学者读懂所需的直觉/为什么/最小例子/易错点/关联，可多段，必需且充分，讲到初学者懂）。
- 任何公式（如 AMAT 表达式）独占一行（块级），不内联；cache 访问流程/时序独占行或块。
- 教辅口吻；广度优先、查全内容项、宁多列浅讲不遗漏；每项讲到初学者懂即止，不做专家纵深。
- 抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；标核实日期。

【小主题清单（每个 ## 一节；内容项做 ###）】覆盖下列 5 个小主题，内容项可读 round3b-subtopics/g04-hardware-arch.md 中 L5-05 大主题 AA-2（小主题 AA-2.1~AA-2.5）取全：
- 2.1 Cache 优化技术（AA-2.1）：六类优化（降命中时间/降失效率/降失效代价）。
- 2.2 预取（AA-2.2）：硬件流预取、编译器软件预取。
- 2.3 非阻塞 cache（AA-2.3）：hit-under-miss、MSHR 与内存级并行。
- 2.4 虚拟内存加速（AA-2.4）：多级页表、大页、TLB 与地址转换优化。
- 2.5 主存技术（AA-2.5）：DDR DRAM/HBM/闪存的带宽与延迟特性。

【多来源比对（强制）】每个内容项 ≥2 独立来源交叉核对，优先一手：Hennessy & Patterson《QA》6th ed（ch2 + 附录 B）；补充可参 L3-02 COD RISC-V 2nd ed ch5（内存层次基础，本大主题为其深化）。冲突两边都记、点明分歧。数值/默认值/带宽延迟数据须比对通过或标「待核」，不凭记忆填；主存技术代际数据前沿易变，标「⚙演进快·锚版本」。本机实证可选（rdtscp 步长扫描 / valgrind cachegrind），不能替代多来源比对。

【章节末来源与时效】每小主题末集中列锚点（教材§/附录 + 版本 + 核实日期）；冲突项两边定位；待核项标注不编造。

【权威锚点清单（取自 round3a）】
- Hennessy & Patterson,《Computer Architecture: A Quantitative Approach》6th ed, 2017, ISBN 9780128119051（ch2 Memory Hierarchy Design + 印刷附录 B 内存层次复习）。
- URL（核实 2026-07-25）：shop.elsevier.com/books/computer-architecture/hennessy/978-0-12-811905-1 ; educate.elsevier.com/book/details/9780128119051
- 先修：L3-02 CO-6（内存层次与缓存，本大主题为其深化）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」：5 个小主题、含较多技术分支，若某小主题内容项显著膨胀可考虑拆分并给理由（拆则用 023-advanced-architecture-<后缀>.md）。

【落盘目标】findings/023-advanced-architecture.md（若拆分用 023-advanced-architecture-<后缀>.md）。报告自足，不残留工具标签/控制字符。
```

```text
[P2] L5-05·大主题3 指令级并行与静态调度 — 报告prompt
任务：为「L5-05·大主题3 指令级并行与静态调度」产出一份 report-format v3（广度优先教辅式）报告。

【格式硬性（v3，必守）】
- 标题层级严格 #→##→###；### 下不写任何标签，直接正文分段：核心说明（是什么/定义/结论）+ 充分辅助说明（初学者读懂所需直觉/为什么/最小例子/易错点/关联，必需且充分）。
- 任何公式独占一行（块级）；流水线阶段/冒险/旁路时序图示以独占行或块呈现，不内联埋进句子。
- 教辅口吻；广度优先、查全、宁多列浅讲不遗漏；讲到初学者懂即止，不做专家纵深。
- 抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；标核实日期。

【小主题清单（每个 ## 一节；内容项做 ###）】覆盖下列 5 个小主题，内容项可读 round3b-subtopics/g04-hardware-arch.md 中 L5-05 大主题 AA-3（小主题 AA-3.1~AA-3.5）取全：
- 3.1 流水线复习（AA-3.1，附录 C）：深化 CO-5，附录 C 的冒险与旁路。
- 3.2 循环变换（AA-3.2）：循环展开、软件流水暴露 ILP。
- 3.3 静态多发射/VLIW（AA-3.3）：编译期打包多操作、超长指令字。
- 3.4 编译器指令调度（AA-3.4）：静态重排隐藏延迟、寄存器压力权衡。
- 3.5 ILP 极限（AA-3.5）：数据/名字/控制相关对可挖并行度的约束。

【多来源比对（强制）】每个内容项 ≥2 独立来源交叉核对，优先一手：Hennessy & Patterson《QA》6th ed（ch3 静态部分 + 附录 C Pipelining）；补充可参 L3-02 COD ch4（5 级流水线基础，本大主题为其升级）、L4-04 编译器调度视角。冲突两边都记、点明分歧。数值/系数须比对通过或标「待核」。本机实证可选（gcc -O3 -S 看循环展开），不能替代多来源比对。

【章节末来源与时效】每小主题末集中列锚点（教材§/附录 + 版本 + 核实日期）；冲突两边定位；待核项标注不编造。

【权威锚点清单（取自 round3a）】
- Hennessy & Patterson,《Computer Architecture: A Quantitative Approach》6th ed, 2017, ISBN 9780128119051（ch3 静态部分 + 印刷附录 C Pipelining）。
- URL（核实 2026-07-25）：shop.elsevier.com/books/computer-architecture/hennessy/978-0-12-811905-1 ; educate.elsevier.com/book/details/9780128119051
- 先修：L3-02 CO-5（5 级流水线，本大主题为其升级）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」：本大主题为静态 ILP，与大主题4（动态调度）分账，5 小主题篇幅可控，默认倾向 1 份；拆则给理由并用 023-advanced-architecture-<后缀>.md。

【落盘目标】findings/023-advanced-architecture.md（若拆分用 023-advanced-architecture-<后缀>.md）。报告自足，不残留工具标签/控制字符。
```

```text
[P2] L5-05·大主题4 动态调度·超标量·投机 — 报告prompt
任务：为「L5-05·大主题4 动态调度·超标量·投机」产出一份 report-format v3（广度优先教辅式）报告。

【格式硬性（v3，必守）】
- 标题层级严格 #→##→###；### 下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者读懂所需直觉/为什么/最小例子/易错点/关联，必需且充分，讲到初学者懂）。
- 任何公式独占一行（块级）；Tomasulo 数据流/保留站/CDB/ROB 退休时序以独占行或块呈现，不内联埋进句子。
- 教辅口吻；广度优先、查全、宁多列浅讲不遗漏；讲到初学者懂即止，不做专家纵深。
- 抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；标核实日期。

【小主题清单（每个 ## 一节；内容项做 ###）】覆盖下列 6 个小主题，内容项可读 round3b-subtopics/g04-hardware-arch.md 中 L5-05 大主题 AA-4（小主题 AA-4.1~AA-4.6）取全：
- 4.1 动态调度动机（AA-4.1）：记分板、乱序发射解相关。
- 4.2 Tomasulo（AA-4.2）：保留站、公共数据总线 CDB、隐式重命名。
- 4.3 寄存器重命名（AA-4.3）：物理寄存器堆消除 WAR/WAW。
- 4.4 ROB 与精确异常（AA-4.4）：重排序缓冲、按序退休、精确异常。
- 4.5 分支预测（AA-4.5）：2-bit → gshare → TAGE 演进、BTB/RAS。
- 4.6 投机与 MLP（AA-4.6）：投机执行、乱序内存级并行；点出投机侧信道 Spectre/Meltdown 接缝（→L5-04 安全，勿深挖）。

【多来源比对（强制）】每个内容项 ≥2 独立来源交叉核对，优先一手：Hennessy & Patterson《QA》6th ed（ch3 动态部分）。冲突两边都记、点明分歧。分支预测器演进/命名易变，前沿项标「⚙演进快·锚版本」；数值须比对通过或标「待核」，不凭记忆填。本机实证可选（AA-4.5 可预测 vs 随机分支 rdtscp 耗时比），不能替代多来源比对。

【章节末来源与时效】每小主题末集中列锚点（教材§ + 版本 + 核实日期）；冲突两边定位；待核项标注不编造。

【权威锚点清单（取自 round3a）】
- Hennessy & Patterson,《Computer Architecture: A Quantitative Approach》6th ed, 2017, ISBN 9780128119051（ch3 动态部分）。
- URL（核实 2026-07-25）：shop.elsevier.com/books/computer-architecture/hennessy/978-0-12-811905-1 ; educate.elsevier.com/book/details/9780128119051
- 先修：本大主题3（静态 ILP）；接缝 L5-04 安全（投机侧信道）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」：本大主题机制密集（Tomasulo/重命名/ROB/分支预测），若讲解体量大可考虑拆分并给理由（拆则用 023-advanced-architecture-<后缀>.md）；否则 1 份。

【落盘目标】findings/023-advanced-architecture.md（若拆分用 023-advanced-architecture-<后缀>.md）。报告自足，不残留工具标签/控制字符。
```

```text
[P2] L5-05·大主题5 数据级并行 DLP — 报告prompt
任务：为「L5-05·大主题5 数据级并行（DLP）」产出一份 report-format v3（广度优先教辅式）报告。

【格式硬性（v3，必守）】
- 标题层级严格 #→##→###；### 下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者读懂所需直觉/为什么/最小例子/易错点/关联，必需且充分，讲到初学者懂）。
- 任何公式（如 Roofline 的算术强度/性能上界表达式）独占一行（块级）；向量执行/SIMT warp 数据流以独占行或块呈现，不内联。
- 教辅口吻；广度优先、查全、宁多列浅讲不遗漏；讲到初学者懂即止，不做专家纵深。
- 抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；标核实日期。

【小主题清单（每个 ## 一节；内容项做 ###）】覆盖下列 5 个小主题，内容项可读 round3b-subtopics/g04-hardware-arch.md 中 L5-05 大主题 AA-5（小主题 AA-5.1~AA-5.5）取全：
- 5.1 向量处理器（AA-5.1）：向量寄存器/lane/链接、条带挖掘。
- 5.2 SIMD 扩展（AA-5.2）：AVX-512（本机有）、掩码与打包运算。
- 5.3 GPU / SIMT（AA-5.3）：warp/线程块、SIMT 执行模型。
- 5.4 分支发散与占用率（AA-5.4）：发散惩罚、occupancy 与延迟隐藏。
- 5.5 Roofline 模型（AA-5.5）：算术强度 vs 峰值带宽/算力的性能上界。
（讲硬件机制即可；编程落地接缝 →L6-06 HPC / N-16 GPU 编程，勿重复深挖。）

【多来源比对（强制）】每个内容项 ≥2 独立来源交叉核对，优先一手：Hennessy & Patterson《QA》6th ed（ch4 Vector, SIMD, GPU）。冲突两边都记、点明分歧。GPU/张量硬件代际数据前沿易变，标「⚙演进快·锚版本」；数值/带宽须比对通过或标「待核」，不凭记忆填。本机实证可选（AA-5.2 AVX-512 vs 标量耗时比、AA-5.5 算术强度点定位），不能替代多来源比对。

【章节末来源与时效】每小主题末集中列锚点（教材§ + 版本 + 核实日期）；冲突两边定位；待核项标注不编造。

【权威锚点清单（取自 round3a）】
- Hennessy & Patterson,《Computer Architecture: A Quantitative Approach》6th ed, 2017, ISBN 9780128119051（ch4 Data-Level Parallelism in Vector, SIMD, and GPU）。
- URL（核实 2026-07-25）：shop.elsevier.com/books/computer-architecture/hennessy/978-0-12-811905-1 ; educate.elsevier.com/book/details/9780128119051
- 接缝：L6-06 HPC / N-16 GPU 编程（编程落地，本大主题只讲硬件机制）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」：5 小主题跨向量/SIMD/GPU/Roofline，默认倾向 1 份；若 GPU 与向量两块体量都大可考虑拆分并给理由（拆则用 023-advanced-architecture-<后缀>.md）。

【落盘目标】findings/023-advanced-architecture.md（若拆分用 023-advanced-architecture-<后缀>.md）。报告自足，不残留工具标签/控制字符。
```

```text
[P2] L5-05·大主题6 线程级并行与多处理器 — 报告prompt
任务：为「L5-05·大主题6 线程级并行与多处理器」产出一份 report-format v3（广度优先教辅式）报告。

【格式硬性（v3，必守）】
- 标题层级严格 #→##→###；### 下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者读懂所需直觉/为什么/最小例子/易错点/关联，必需且充分，讲到初学者懂）。
- 任何公式独占一行（块级）；MESI/MOESI 状态机、嗅探/目录协议消息流、内存序读写重排示例以独占行或块呈现，不内联埋进句子。
- 教辅口吻；广度优先、查全、宁多列浅讲不遗漏；讲到初学者懂即止，不做专家纵深。
- 抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；标核实日期。

【小主题清单（每个 ## 一节；内容项做 ###）】覆盖下列 6 个小主题，内容项可读 round3b-subtopics/g04-hardware-arch.md 中 L5-05 大主题 AA-6（小主题 AA-6.1~AA-6.6）取全：
- 6.1 共享内存结构（AA-6.1）：集中式 SMP vs 分布式共享内存（NUMA）。
- 6.2 嗅探一致性（AA-6.2）：MESI/MOESI 状态机、总线嗅探。
- 6.3 目录一致性（AA-6.3）：目录协议对大规模系统的扩展。
- 6.4 内存一致性模型（AA-6.4）：x86-TSO vs 弱序；与 L4-05 并发分账（硬件内存序机制 vs 语言级并发原语，须点明分账）。
- 6.5 同步与屏障（AA-6.5）：原子指令、锁、内存屏障 fence 语义。
- 6.6 伪共享（AA-6.6）：同缓存行争用导致的隐形串行化。

【多来源比对（强制）】每个内容项 ≥2 独立来源交叉核对，优先一手：Hennessy & Patterson《QA》6th ed（ch5 Multiprocessors and Thread-Level Parallelism）。冲突两边都记、点明分歧（尤其一致性 vs 一致性模型术语易混，须辨析）。数值须比对通过或标「待核」，不凭记忆填。本机实证可选（AA-6.1 numactl 观察拓扑、AA-6.5 反汇编看 lock/mfence、AA-6.6 伪共享 vs 填充耗时比），不能替代多来源比对。

【章节末来源与时效】每小主题末集中列锚点（教材§ + 版本 + 核实日期）；冲突两边定位；待核项标注不编造。

【权威锚点清单（取自 round3a）】
- Hennessy & Patterson,《Computer Architecture: A Quantitative Approach》6th ed, 2017, ISBN 9780128119051（ch5 Multiprocessors and Thread-Level Parallelism）。
- URL（核实 2026-07-25）：shop.elsevier.com/books/computer-architecture/hennessy/978-0-12-811905-1 ; educate.elsevier.com/book/details/9780128119051
- 先修：L3-02 CO-8（并行处理器与多核入门）；接缝 L4-05 并发（内存序/屏障须与语言级并发分账）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」：6 小主题含一致性协议与内存模型两大块，若体量大可考虑拆分并给理由（拆则用 023-advanced-architecture-<后缀>.md）；否则 1 份。

【落盘目标】findings/023-advanced-architecture.md（若拆分用 023-advanced-architecture-<后缀>.md）。报告自足，不残留工具标签/控制字符。
```

```text
[P2] L5-05·大主题7 仓库级计算机 WSC — 报告prompt
任务：为「L5-05·大主题7 仓库级计算机（WSC）」产出一份 report-format v3（广度优先教辅式）报告。

【格式硬性（v3，必守）】
- 标题层级严格 #→##→###；### 下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者读懂所需直觉/为什么/最小例子/易错点/关联，必需且充分，讲到初学者懂）。
- 任何公式（如 PUE、TCO/CapEx-OpEx 表达式）独占一行（块级），不内联埋进句子。
- 教辅口吻；广度优先、查全、宁多列浅讲不遗漏；讲到初学者懂即止，不做专家纵深。
- 抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；标核实日期。

【小主题清单（每个 ## 一节；内容项做 ###）】覆盖下列 5 个小主题，内容项可读 round3b-subtopics/g04-hardware-arch.md 中 L5-05 大主题 AA-7（小主题 AA-7.1~AA-7.5）取全：
- 7.1 数据中心作为计算机（AA-7.1）：WSC 抽象、请求级/数据级并行。
- 7.2 能效与 PUE（AA-7.2）：供电/散热效率、能耗比例性。
- 7.3 集群互连（AA-7.3）：网络拓扑、带宽/延迟层级。
- 7.4 存储与可靠性（AA-7.4）：大规模故障率、冗余与恢复。
- 7.5 成本模型（AA-7.5）：CapEx/OpEx、TCO 权衡。

【多来源比对（强制）】每个内容项 ≥2 独立来源交叉核对，优先一手：Hennessy & Patterson《QA》6th ed（ch6 The Warehouse-Scale Computer）。冲突两边都记、点明分歧。PUE/成本/故障率等数据随年份与厂商变动大，前沿项标「⚙演进快·锚版本·随时变」；数值须比对通过或标「待核」，不凭记忆填现状结论。本机实证不适用（数据中心尺度），标「未取/不适用」即可。

【章节末来源与时效】每小主题末集中列锚点（教材§ + 版本 + 核实日期）；冲突两边定位；待核项标注不编造。

【权威锚点清单（取自 round3a）】
- Hennessy & Patterson,《Computer Architecture: A Quantitative Approach》6th ed, 2017, ISBN 9780128119051（ch6 The Warehouse-Scale Computer，6th ed 新增视角）。
- URL（核实 2026-07-25）：shop.elsevier.com/books/computer-architecture/hennessy/978-0-12-811905-1 ; educate.elsevier.com/book/details/9780128119051
- 接缝：L5-01 分布式 / L6-05 云系统。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」：5 小主题偏综述、篇幅可控，默认倾向 1 份；拆则给理由并用 023-advanced-architecture-<后缀>.md。

【落盘目标】findings/023-advanced-architecture.md（若拆分用 023-advanced-architecture-<后缀>.md）。报告自足，不残留工具标签/控制字符。
```

```text
[P2] L5-05·大主题8 领域专用体系结构 DSA — 报告prompt
任务：为「L5-05·大主题8 领域专用体系结构（DSA）」产出一份 report-format v3（广度优先教辅式）报告。

【格式硬性（v3，必守）】
- 标题层级严格 #→##→###；### 下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者读懂所需直觉/为什么/最小例子/易错点/关联，必需且充分，讲到初学者懂）。
- 任何公式（如 Roofline、脉动阵列吞吐表达式）独占一行（块级）；脉动阵列数据流以独占行或块呈现，不内联埋进句子。
- 教辅口吻；广度优先、查全、宁多列浅讲不遗漏；讲到初学者懂即止，不做专家纵深。
- 抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；标核实日期。

【小主题清单（每个 ## 一节；内容项做 ###）】覆盖下列 5 个小主题，内容项可读 round3b-subtopics/g04-hardware-arch.md 中 L5-05 大主题 AA-8（小主题 AA-8.1~AA-8.5）取全：
- 8.1 DSA 设计原则（AA-8.1）：专用化收益、去通用开销的指导原则。
- 8.2 TPU 与脉动阵列（AA-8.2）：矩阵乘加速器、脉动数据流。
- 8.3 GPU 张量核（AA-8.3）：张量核/矩阵单元与混合精度。
- 8.4 软硬协同（AA-8.4）：领域语言、编译器与硬件协同设计。
- 8.5 DSA 上的 Roofline（AA-8.5）：以 Roofline 评估加速器效率。
（讲硬件机制与设计原则；接缝 →L6-03 深度学习 / L6-06 HPC / N-11 AI 系统，勿重复深挖。）

【多来源比对（强制）】每个内容项 ≥2 独立来源交叉核对，优先一手：Hennessy & Patterson《QA》6th ed（ch7 Domain-Specific Architectures，6th ed 新增）。冲突两边都记、点明分歧。⚠前沿硬标：QA 6th ed 为 2017 出版，DSA（TPU/张量核）硬件代际演进极快，具体产品/代际须标「⚙演进快·版本成熟度·随时变」，不以教材数字作现状结论（brief §3）；数值须比对通过或标「待核」，不凭记忆填。本机实证多不适用（无 TPU/张量核），标「未取/不适用」；如涉 GPU 相关可选。

【章节末来源与时效】每小主题末集中列锚点（教材§ + 版本 + 核实日期）；冲突两边定位；前沿代际统一标演进快；待核项标注不编造。

【权威锚点清单（取自 round3a）】
- Hennessy & Patterson,《Computer Architecture: A Quantitative Approach》6th ed, 2017, ISBN 9780128119051（ch7 Domain-Specific Architectures，6th ed 新增，硬标演进快）。
- URL（核实 2026-07-25）：shop.elsevier.com/books/computer-architecture/hennessy/978-0-12-811905-1 ; educate.elsevier.com/book/details/9780128119051
- 接缝：L6-03 深度学习 / L6-06 HPC / N-11 AI 系统。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」：5 小主题偏综述、篇幅可控，默认倾向 1 份；拆则给理由并用 023-advanced-architecture-<后缀>.md。

【落盘目标】findings/023-advanced-architecture.md（若拆分用 023-advanced-architecture-<后缀>.md）。报告自足，不残留工具标签/控制字符。
```

共 8 条 prompt
