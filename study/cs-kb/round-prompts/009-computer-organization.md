# Round3c · L3-02 计算机组成原理 · 报告 prompt（P0）

```text
[P0] L3-02·大主题1 计算机抽象与性能评价 — 报告prompt

任务：为「L3-02·大主题1 计算机抽象与性能评价」产出一份 report-format v3（study/cs-kb/report-format.md）教辅式报告，落盘到 study/cs-kb/findings/009-computer-organization.md（若拆分则按 009-computer-organization-a/-b/… 命名，见下「粒度判定」）。开工前必读：study/cs-kb/brief.md + report-format.md（v3 模板）+ 本 prompt；抬头带可 grep 基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」再动笔。本大主题 5 个小主题、单一主题（性能语言），预期 1 份即可；若展开后过长再拆并说明理由，结论写在报告开头一行。

v3 格式硬性：
- 标题层级 #（报告标题=大主题）→ ##（小主题=章节）→ ###（每个内容项一节）；### 下不写「核心概念：」「辅助说明：」等任何标签，直接正文靠空行分段。
- 每个 ### 内容项：核心说明（必需，讲清是什么/定义/关键结论，正确）+ 充分辅助说明（必需且充分，面向初学者——直觉/为什么/最小例子/易错点/前后关联，写到初学者能懂为止，可多段）。辅助说明不是可省点缀。
- 公式/时序/位模式独占一行（块级），不内联埋进句子。例如 CPU时间 = IC × CPI × 时钟周期 要单独成行。
- 教辅口吻；广度优先，讲到初学者懂但不做专家级纵深（不写长篇机制深挖/完整推导/设计权衡长论）。

小主题清单（## 章节，取自 round3b-subtopics/g04-hardware-arch.md 的 L3-02 大主题 CO-1；下游可直接读该文件 CO-1.1–CO-1.5 取每小主题的一句话范围与实证机会）：
- CO-1.1 抽象层次与设计思想：程序→ISA→硬件的抽象栈、八大伟大思想
- CO-1.2 性能公式：CPU时间 = IC × CPI × 时钟周期，三因子分账（实证机会：perf stat 读 instructions/cycles/CPI）
- CO-1.3 Amdahl 定律：局部加速对整体加速比的上界
- CO-1.4 功耗墙：动态功耗 ∝ CV²f、Dennard 缩放终结
- CO-1.5 多核转向与基准：从单核提频转向多核、SPEC 基准与相对性能

多来源比对（强制）：每个内容项 ≥2 个独立来源交叉核对，优先一手——Patterson & Hennessy《Computer Organization and Design》RISC-V 2nd ed (2020) ch1，可佐以 Berkeley CS61C、Hennessy & Patterson《QA》6th ed ch1；来源打架时两边都记、点明分歧、不和稀泥。具体数值/公式系数/默认值来自比对通过的来源或标「待核」，绝不凭记忆填。
本机实证可选：perf stat 读 IC/cycles/CPI 可加固 CO-1.2，仅便宜且能快速坐实时做，不强制；不能替代多来源比对。实证代码只写仓库外 scratchpad，跑完即清，不入库；报告自足（命令/输出/环境串贴进正文）。
每个 ## 章节末集中列「来源与时效」：锚点（教材§/规范/官方文档+版本+核实日期）、前沿项标「⚙演进快·锚版本」、未证实值标「待核」、冲突项两边定位。不逐项脚注。不编造，标核实日期。

权威锚点清单（取自 round3a-topics/g04-hardware-arch.md）：
- 一手：Patterson & Hennessy《Computer Organization and Design》RISC-V Edition, 2nd ed, 2020, ISBN 9780128203316，ch1 Computer Abstractions and Technology。
- 佐证：Berkeley CS61C；Hennessy & Patterson《Computer Architecture: A Quantitative Approach》6th ed, 2017, ISBN 9780128119051 ch1（量化基础深化）。
- 跨课提示：本主题是全组性能语言的公理，→ L5-05 AA-1 深化，勿在本报告做专家纵深。

卫生红线：报告不得残留工具标签 </…> 或不可见控制字符；不入库任何实证产物。
```

```text
[P0] L3-02·大主题2 指令集体系结构(ISA) — 报告prompt

任务：为「L3-02·大主题2 指令集体系结构(ISA)」产出一份 report-format v3（study/cs-kb/report-format.md）教辅式报告，落盘到 study/cs-kb/findings/009-computer-organization.md（拆分则 009-computer-organization-a/-b/…，见「粒度判定」）。开工前必读：study/cs-kb/brief.md + report-format.md（v3）+ 本 prompt；抬头带可 grep 基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。本大主题 6 个小主题、覆盖编码/寻址/过程调用/RISC-CISC 分账，若某小主题（如指令格式编码）展开过长可考虑拆；否则 1 份。结论写报告开头一行。

v3 格式硬性：#→##→###；### 下不写标签，核心说明（必需，正确）+ 充分辅助说明（必需且充分，面向初学者：直觉/为什么/最小例子/易错点/关联，讲到初学者懂）；指令格式的位段布局/字段位模式独占一行（块级），不内联；教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节，取自 round3b 的 CO-2；下游可直接读 round3b-subtopics/g04-hardware-arch.md 的 L3-02 大主题 CO-2，取 CO-2.1–CO-2.6 一句话范围与实证机会）：
- CO-2.1 寄存器模型与操作数：RISC-V 32 通用寄存器、load-store 操作数约束
- CO-2.2 指令格式编码：R/I/S/B/U/J 六型字段布局与位编码（【实证核】objdump 反汇编 RISC-V 目标码）
- CO-2.3 寻址与内存访问：立即/寄存器/基址+偏移、大小端、对齐
- CO-2.4 过程调用约定：jal/jalr、参数/返回/保存寄存器、栈帧（RISC-V）
- CO-2.5 分支与跳转编码：立即数拼接、PC 相对寻址、条件分支（【实证核】objdump 观察立即数重组）
- CO-2.6 RISC vs CISC、分账：精简/复杂指令权衡、ISA 与微架构分账

多来源比对（强制）：每项 ≥2 独立来源，优先一手——P&H《COD》RISC-V 2nd ed ch2 Instructions: Language of the Computer，可佐以 RISC-V 官方 ISA 规范（The RISC-V Instruction Set Manual, Vol I Unprivileged，记版本）、CS61C；冲突两边都记、点明分歧。编码位段/寄存器编号严禁凭记忆，来自比对通过来源或标「待核」。
本机实证可选：【待核】RISC-V 交叉工具链（riscv64 gcc/objdump）是否本机预装；装了则 objdump 反汇编坐实 CO-2.2/CO-2.5 编码，未装则以 COD 教材图表为准并标「未取」。教学 RISC-V 与本机 x86-64 分账，x86-64 机器级落地归 L3-03，不在此深挖。实证代码只写 scratchpad，报告自足。
每 ## 章节末集中列「来源与时效」：锚点+版本+核实日期、待核项、冲突两边定位；不逐项脚注；不编造。

权威锚点清单（取自 round3a）：
- 一手：P&H《COD》RISC-V 2nd ed, 2020, ISBN 9780128203316, ch2。
- 规范：The RISC-V Instruction Set Manual（riscv.org，记版本/日期）。
- 佐证：Berkeley CS61C。
- 强绑定：CO-2 ⟷ L3-03 AS-1/AS-2（本机 x86-64 机器级落地）；差异指向 L5-05。

卫生红线：不得残留工具标签/控制字符；实证产物不入库。
```

```text
[P0] L3-02·大主题3 计算机算术 — 报告prompt

任务：为「L3-02·大主题3 计算机算术」产出一份 report-format v3（study/cs-kb/report-format.md）教辅式报告，落盘到 study/cs-kb/findings/009-computer-organization.md（拆分则 -a/-b/…）。开工前必读：brief.md + report-format.md（v3）+ 本 prompt；抬头带基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。本大主题 6 个小主题（整数运算 + IEEE-754 浮点 + ALU），整数与浮点跨度较大，若过长可拆整数/浮点两份；否则 1 份。结论写报告开头一行。

v3 格式硬性：#→##→###；### 下不写标签，核心说明（必需）+ 充分辅助说明（必需且充分，初学者向）；公式与位模式独占一行（块级）——如 IEEE-754 单精度字段布局、补码溢出判定式、(-1)^s × 1.f × 2^(e-127) 都要单独成行，不内联；教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节，取自 round3b 的 CO-3；下游可直接读 round3b-subtopics/g04-hardware-arch.md 的 L3-02 大主题 CO-3，取 CO-3.1–CO-3.6）：
- CO-3.1 整数加减与溢出：补码加减、有符号/无符号溢出检测（【实证核】gcc -S 观察 add/溢出标志）
- CO-3.2 乘法：移位相加乘法算法与硬件迭代
- CO-3.3 除法：恢复/不恢复余数除法算法
- CO-3.4 IEEE-754 表示：单/双精度字段、规格化/非规格化/特殊值（实证：Python struct.pack 观察位模式）
- CO-3.5 浮点运算与舍入：浮点加乘流程、舍入模式、精度误差（实证：numpy 数值误差核对）
- CO-3.6 ALU 构造：由算术/逻辑构件组装 ALU、标志输出

多来源比对（强制）：每项 ≥2 独立来源，优先一手——P&H《COD》RISC-V 2nd ed ch3 Arithmetic for Computers，佐以 IEEE 754-2019 标准文本（记条款）、CSAPP ch2（信息表示，2.4 浮点）；冲突两边都记、点明分歧。字段宽度/偏置/舍入模式默认值严禁凭记忆，来自比对通过来源或标「待核」。
本机实证可选：Python struct.pack('>f',x).hex() 观察 IEEE-754 位模式、numpy 核对舍入/精度误差、gcc -S 看溢出标志——便宜时做以加固 CO-3.4/3.5/3.1，不强制、不替代多来源比对。先自检 python3 -c "import numpy"；实证代码只写 scratchpad，报告自足（贴命令+真实输出+环境串）。
每 ## 章节末集中列「来源与时效」：锚点+版本+核实日期、待核项、冲突两边定位；不逐项脚注；不编造。

权威锚点清单（取自 round3a）：
- 一手：P&H《COD》RISC-V 2nd ed, 2020, ISBN 9780128203316, ch3。
- 规范：IEEE 754-2019 Floating-Point Arithmetic。
- 佐证：CSAPP 3rd ed ch2（Bryant & O'Hallaron, 2016, csapp.cs.cmu.edu/3e/）。
- 跨课提示：承 L2-02 DL-1/DL-3；⟳ L3-03 AS-7 浮点机器码是其落地。

卫生红线：不得残留工具标签/控制字符；实证产物不入库。
```

```text
[P0] L3-02·大主题4 处理器数据通路与控制(单周期) — 报告prompt

任务：为「L3-02·大主题4 处理器数据通路与控制(单周期)」产出一份 report-format v3（study/cs-kb/report-format.md）教辅式报告，落盘到 study/cs-kb/findings/009-computer-organization.md（拆分则 -a/-b/…）。开工前必读：brief.md + report-format.md（v3）+ 本 prompt；抬头带基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。本大主题 5 个小主题、单一主题（单周期数据通路+控制），预期 1 份。结论写报告开头一行。

v3 格式硬性：#→##→###；### 下不写标签，核心说明（必需）+ 充分辅助说明（必需且充分，初学者向：数据通路连线的直觉/为什么需要各 MUX/最小例子/易错点）；控制信号真值表、数据流各步序列独占一行（块级），不内联；教辅口吻；广度优先、不做专家纵深（不写完整门级实现推导）。

小主题清单（## 章节，取自 round3b 的 CO-4；下游可直接读 round3b-subtopics/g04-hardware-arch.md 的 L3-02 大主题 CO-4，取 CO-4.1–CO-4.5）：
- CO-4.1 指令执行五步：取指/译码/执行/访存/写回的抽象数据流
- CO-4.2 单周期数据通路：PC/寄存器堆/ALU/存储器/MUX 连线组装
- CO-4.3 主控制单元：从 opcode/funct 位段派生控制信号
- CO-4.4 ALU 控制：两级控制译出 ALU 操作码
- CO-4.5 单周期局限：CPI=1 但周期由最慢指令决定→提出流水化

多来源比对（强制）：每项 ≥2 独立来源，优先一手——P&H《COD》RISC-V 2nd ed ch4 §4.1–4.4 + 在线附录 A（逻辑设计基础），佐以 CS61C、H&H《DDCA》RISC-V ed ch7（微架构，单周期处理器）；冲突两边都记、点明分歧。控制信号取值/opcode 位段严禁凭记忆，来自比对通过来源或标「待核」。
本机实证可选：本主题偏结构原理，本机 x86-64 无教学 RISC-V 单周期硬件可直接观测，实证基本「未取」，以教材图表为准即可；不勉强造实证。前置元件来自 L2-02 DL-3/DL-5；教学 RISC-V 模型 ≠ 本机 x86-64 乱序（指给 L5-05），不在此深挖。
每 ## 章节末集中列「来源与时效」：锚点+版本+核实日期、待核项、冲突两边定位；不逐项脚注；不编造。

权威锚点清单（取自 round3a）：
- 一手：P&H《COD》RISC-V 2nd ed, 2020, ISBN 9780128203316, ch4 §4.1–4.4；在线附录 A（逻辑设计基础）。
- 佐证：Berkeley CS61C；Harris & Harris《DDCA》RISC-V ed, 2021, ISBN 9780128200643, ch7。
- 跨课提示：← L2-02 DL-3/DL-5 提供元件；乱序/超标量指给 L5-05，勿深挖。

卫生红线：不得残留工具标签/控制字符；实证产物不入库。
```

```text
[P0] L3-02·大主题5 流水线 — 报告prompt

任务：为「L3-02·大主题5 流水线（本课高潮）」产出一份 report-format v3（study/cs-kb/report-format.md）教辅式报告，落盘到 study/cs-kb/findings/009-computer-organization.md（拆分则 -a/-b/…）。开工前必读：brief.md + report-format.md（v3）+ 本 prompt；抬头带基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。本大主题 6 个小主题、是本课高潮且含三类冒险/转发/停顿/分支/异常，内容较重，若展开过长可拆「基础+冒险」与「分支预测+异常」两份；否则 1 份。结论写报告开头一行。

v3 格式硬性：#→##→###；### 下不写标签，核心说明（必需）+ 充分辅助说明（必需且充分，初学者向：为什么要段寄存器/冒险直觉/转发路径最小例子/易错点）；流水线时序图（各拍 IF/ID/EX/MEM/WB 排布）、气泡/停顿时序独占一行（块级），不内联；教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节，取自 round3b 的 CO-5；下游可直接读 round3b-subtopics/g04-hardware-arch.md 的 L3-02 大主题 CO-5，取 CO-5.1–CO-5.6）：
- CO-5.1 五级流水与段寄存器：IF/ID/EX/MEM/WB 划分、流水寄存器锁存（实证：Python 逐拍模拟）
- CO-5.2 结构冒险：资源冲突（单口存储器等）与消除
- CO-5.3 数据冒险与转发：RAW 相关、EX/MEM→EX 前递路径
- CO-5.4 load-use 停顿：转发无法覆盖的一拍气泡与冒险检测（实证：Python 逐拍模拟停顿注入）
- CO-5.5 控制冒险与分支预测入门：分支延迟、冲刷、静态/1-bit 预测入门
- CO-5.6 异常与中断：流水线中精确异常的检测与处理

多来源比对（强制）：每项 ≥2 独立来源，优先一手——P&H《COD》RISC-V 2nd ed ch4 §4.5–4.9，佐以 CS61C、H&H《DDCA》RISC-V ed ch7（流水线微架构）、H&P《QA》6th ed 附录 C（Pipelining）；冲突两边都记、点明分歧。转发路径/停顿拍数/预测器状态严禁凭记忆，来自比对通过来源或标「待核」。
本机实证可选：Python 逐拍模拟流水线冒险/停顿注入（纯逻辑模拟，不需 RISC-V 硬件）便宜时做以加固 CO-5.1/5.4；给最小可复现脚本 + 真实输出 + 环境串，只写 scratchpad、不入库。不替代多来源比对。深化（超标量/乱序）归 L5-05 AA-3/AA-4，勿在此深挖。
每 ## 章节末集中列「来源与时效」：锚点+版本+核实日期、待核项、冲突两边定位；不逐项脚注；不编造。

权威锚点清单（取自 round3a）：
- 一手：P&H《COD》RISC-V 2nd ed, 2020, ISBN 9780128203316, ch4 §4.5–4.9。
- 佐证：Berkeley CS61C；H&H《DDCA》RISC-V ed, 2021, ch7；H&P《QA》6th ed, 2017, ISBN 9780128119051, 附录 C。
- 跨课提示：→ L5-05 AA-4（顺序 5 级升级为超标量/乱序），勿重复深挖。

卫生红线：不得残留工具标签/控制字符；实证产物不入库。
```

```text
[P0] L3-02·大主题6 内存层次与缓存 — 报告prompt

任务：为「L3-02·大主题6 内存层次与缓存」产出一份 report-format v3（study/cs-kb/report-format.md）教辅式报告，落盘到 study/cs-kb/findings/009-computer-organization.md（拆分则 -a/-b/…）。开工前必读：brief.md + report-format.md（v3）+ 本 prompt；抬头带基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。本大主题 6 个小主题（cache 映射/替换/写策略/AMAT/虚拟内存+TLB），若过长可拆「cache」与「虚拟内存/TLB」两份；否则 1 份。结论写报告开头一行。

v3 格式硬性：#→##→###；### 下不写标签，核心说明（必需）+ 充分辅助说明（必需且充分，初学者向：局部性直觉/地址如何拆成 tag|index|offset/命中判定最小例子/易错点）；地址位拆分、AMAT 公式独占一行（块级）——如 AMAT = 命中时间 + 失效率 × 失效代价 要单独成行，不内联；教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节，取自 round3b 的 CO-6；下游可直接读 round3b-subtopics/g04-hardware-arch.md 的 L3-02 大主题 CO-6，取 CO-6.1–CO-6.6）：
- CO-6.1 局部性原理：时间/空间局部性与层次存在的理由
- CO-6.2 直接映射 cache：索引/标记/块内偏移、命中判定（实证：sysfs 读真实 cache 参数）
- CO-6.3 相联与替换：组相联/全相联、LRU/随机替换
- CO-6.4 写策略：写直达 vs 写回、写分配/写缓冲
- CO-6.5 AMAT 与多级 cache：平均访存时间、L1/L2/L3 分层优化（实证：步长扫描测有效延迟）
- CO-6.6 虚拟内存与 TLB：分页地址转换、页表、TLB 加速（实证：/proc 观察映射，可选）

多来源比对（强制）：每项 ≥2 独立来源，优先一手——P&H《COD》RISC-V 2nd ed ch5 Large and Fast: Exploiting Memory Hierarchy，佐以 CSAPP ch6（存储器层次）、CS61C、H&P《QA》6th ed ch2/附录 B；冲突两边都记、点明分歧。cache 参数/替换策略默认/页大小严禁凭记忆，本机数值以 sysfs/系统实读为准，教材通式与本机具体分账。
本机实证可选：读 /sys/devices/system/cpu/cpu0/cache/ 取真实 cache 层级/大小/相联度加固 CO-6.2/6.5；步长扫描测有效访存延迟；/proc/self/maps 观察映射——便宜时做，给命令+真实输出+环境串，只写 scratchpad、不入库；不替代多来源比对。虚拟内存深化归 L4-01、高级内存优化归 L5-05 AA-2，勿深挖。
每 ## 章节末集中列「来源与时效」：锚点+版本+核实日期、待核项、冲突两边定位；不逐项脚注；不编造。

权威锚点清单（取自 round3a）：
- 一手：P&H《COD》RISC-V 2nd ed, 2020, ISBN 9780128203316, ch5。
- 佐证：CSAPP 3rd ed ch6（csapp.cs.cmu.edu/3e/）；Berkeley CS61C；H&P《QA》6th ed 附录 B。
- 跨课提示：→ L5-05 AA-2 高级内存优化；→ L4-01 虚拟内存，勿重复深挖。

卫生红线：不得残留工具标签/控制字符；实证产物不入库。
```

```text
[P0] L3-02·大主题7 I/O 与总线 — 报告prompt

任务：为「L3-02·大主题7 I/O 与总线」产出一份 report-format v3（study/cs-kb/report-format.md）教辅式报告，落盘到 study/cs-kb/findings/009-computer-organization.md（拆分则 -a/-b/…）。开工前必读：brief.md + report-format.md（v3）+ 本 prompt；抬头带基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。本大主题 4 个小主题、单一主题，预期 1 份。结论写报告开头一行。

v3 格式硬性：#→##→###；### 下不写标签，核心说明（必需）+ 充分辅助说明（必需且充分，初学者向：轮询/中断/DMA 为什么依次出现的直觉/最小例子/易错点）；带宽/延迟计算式独占一行（块级），不内联；教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节，取自 round3b 的 CO-7；下游可直接读 round3b-subtopics/g04-hardware-arch.md 的 L3-02 大主题 CO-7，取 CO-7.1–CO-7.4）：
- CO-7.1 I/O 编址：内存映射 I/O vs 独立端口 I/O
- CO-7.2 数据传送方式：轮询/中断驱动/DMA 三种及权衡
- CO-7.3 总线特性：带宽/延迟、同步/异步、仲裁
- CO-7.4 存储可靠性：RAID 级别、纠错与冗余

多来源比对（强制）：每项 ≥2 独立来源，优先一手——P&H《COD》RISC-V 2nd ed ch5/ch6（I/O 与可靠性节），佐以 CS61C、Tanenbaum《Structured Computer Organization》或经典 OS 教材（中断/DMA 交叉核对）；冲突两边都记、点明分歧。RAID 级别定义/带宽数值严禁凭记忆，来自比对通过来源或标「待核」。
本机实证可选：本主题偏体系概念，本机可选看 /proc/interrupts 了解中断计数、lsblk/mdadm 了解 RAID（若无则「未取」），不勉强；不替代多来源比对；只写 scratchpad、不入库。中断/驱动深化归 L4-01、系统调用边界归 L3-03，勿深挖。
每 ## 章节末集中列「来源与时效」：锚点+版本+核实日期、待核项、冲突两边定位；不逐项脚注；不编造。

权威锚点清单（取自 round3a）：
- 一手：P&H《COD》RISC-V 2nd ed, 2020, ISBN 9780128203316, ch5/ch6（I/O 与可靠性节）。
- 佐证：Berkeley CS61C；经典 OS/组成教材（中断/DMA/RAID 交叉核对，记版本）。
- 跨课提示：→ L4-01 OS（中断/驱动）；→ L3-03 系统调用边界，勿重复深挖。

卫生红线：不得残留工具标签/控制字符；实证产物不入库。
```

```text
[P0] L3-02·大主题8 并行处理器与多核入门 — 报告prompt

任务：为「L3-02·大主题8 并行处理器与多核入门」产出一份 report-format v3（study/cs-kb/report-format.md）教辅式报告，落盘到 study/cs-kb/findings/009-computer-organization.md（拆分则 -a/-b/…）。开工前必读：brief.md + report-format.md（v3）+ 本 prompt；抬头带基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。本大主题 5 个小主题、均为「入门」定位（深挖归 L5-05），预期 1 份。结论写报告开头一行。

v3 格式硬性：#→##→###；### 下不写标签，核心说明（必需）+ 充分辅助说明（必需且充分，初学者向：Flynn 四类的直觉/一致性问题为什么出现的最小例子/SMT 直觉/易错点）；分类表/位模式若有独占一行（块级），不内联；教辅口吻；广度优先、只讲到入门懂，不做专家纵深（一致性协议状态机/GPU 深挖明确归 L5-05）。

小主题清单（## 章节，取自 round3b 的 CO-8；下游可直接读 round3b-subtopics/g04-hardware-arch.md 的 L3-02 大主题 CO-8，取 CO-8.1–CO-8.5）：
- CO-8.1 Flynn 分类：SISD/SIMD/MISD/MIMD taxonomy
- CO-8.2 多核共享内存：共享地址空间、片上多核组织
- CO-8.3 GPU 概览：众核 SIMT 结构与吞吐导向（入门，深挖归 AA-5）
- CO-8.4 缓存一致性入门：一致性问题提出、嗅探基本思想（深挖归 AA-6）
- CO-8.5 硬件多线程：细粒度/粗粒度/同时多线程 SMT 入门

多来源比对（强制）：每项 ≥2 独立来源，优先一手——P&H《COD》RISC-V 2nd ed ch6 Parallel Processors from Client to Cloud + 在线附录 B（GPU），佐以 CS61C、H&P《QA》6th ed ch5（一致性/TLP 交叉核对，仅取入门层）；冲突两边都记、点明分歧。分类定义/SMT 术语严禁凭记忆，来自比对通过来源或标「待核」。
本机实证可选：可选 lscpu / /proc/cpuinfo 看本机核数与 SMT（超线程）是否开启以佐证 CO-8.2/8.5，便宜时做，给命令+真实输出+环境串，只写 scratchpad、不入库；不替代多来源比对。一致性协议/GPU/WSC 深挖归 L5-05 AA-5/AA-6/AA-8、并发归 L4-05，本报告只做入门，勿与 L5-05 重复深挖。
每 ## 章节末集中列「来源与时效」：锚点+版本+核实日期、待核项、冲突两边定位；不逐项脚注；不编造。

权威锚点清单（取自 round3a）：
- 一手：P&H《COD》RISC-V 2nd ed, 2020, ISBN 9780128203316, ch6；在线附录 B（GPU）。
- 佐证：Berkeley CS61C；H&P《QA》6th ed, 2017, ISBN 9780128119051, ch5/ch6（入门层交叉核对）。
- 跨课提示：→ L5-05 AA-5/AA-6/AA-8（一致性/WSC 深化）；→ L4-05 并发，勿深挖。

卫生红线：不得残留工具标签/控制字符；实证产物不入库。
```

共 8 条 prompt
