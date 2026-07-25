# Round 3a 大主题分解 · 第 4 组「硬件与体系结构」

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25
> 本组课程：L2-02 数字逻辑电路 · L3-02 计算机组成原理（枢纽）· L3-03 汇编与机器级表示 · L5-05 高级计算机体系结构
> 产物性质：Round 3a「大主题分解」——每门课**锚定权威教材/官方 syllabus 目录**，列全**教学单元级大主题**（覆盖全、少重叠、按教学序）。不做逐条取证（R4 任务）；本文只负责"查全大主题清单 + 跨课重叠提示"。
> 主轴（承 R2 §1 抽象阶梯）：`门/布尔（L2-02）→ 数据通路+流水线（L3-02 枢纽）→ 机器级落字节（L3-03）→ 超标量/乱序/并行（L5-05）`。递进：数字逻辑→组成→高级体系结构；汇编与组成强绑定（同一台机器的"结构视角"vs"编码/运行时视角"）。

---

## 权威目录锚点（URL + 版本 + 核实 2026-07-25）

| 课程 | 锚定权威 | 版本/年份 | ISBN / 定位 | URL（核实 2026-07-25） |
|------|----------|-----------|-------------|------------------------|
| L2-02 数字逻辑 | **Harris & Harris,《Digital Design and Computer Architecture》RISC-V Edition** | 1st ed, 2021 | ISBN 9780128200643（Elsevier/Morgan Kaufmann） | shop.elsevier.com/books/digital-design-and-computer-architecture-risc-v-edition/harris/978-0-12-820064-3 ; books.google.com id=SksiEAAAQBAJ |
| L3-02 组成原理 | **Patterson & Hennessy,《Computer Organization and Design》RISC-V Edition**（+ Berkeley CS61C 佐证） | 2nd ed, 2020 | ISBN 9780128203316（1st ed 9780128122754） | shop.elsevier.com/books/computer-organization-and-design-risc-v-edition/patterson/978-0-12-820331-6 ; O'Reilly TOC 9780128122761 |
| L3-03 汇编 | **Bryant & O'Hallaron,《CSAPP》第 3 章（Machine-Level Representation）**（+ Stanford CS107 syllabus） | 3rd ed, 2016 | 章 3 为核心，支撑章 1/2/7 | csapp.cs.cmu.edu/3e/ ; web.stanford.edu/class/cs107/syllabus + System V AMD64 ABI（gitlab.com/x86-psABI/x86-64-ABI，规范级，R4 记提交哈希） |
| L5-05 高级体系结构 | **Hennessy & Patterson,《Computer Architecture: A Quantitative Approach》** | 6th ed, 2017 | ISBN 9780128119051 | shop.elsevier.com/books/computer-architecture/hennessy/978-0-12-811905-1 ; educate.elsevier.com/book/details/9780128119051 |

> 目录已逐本核实（章号+章名）。H&H RISC-V 版全书 9 章（ch6 架构 / ch7 微架构 / ch8 内存 属组成/体系结构范畴，本组归到 L3-02/L5-05，避免与数字逻辑课重复立项）。COD RISC-V 2nd ed 正文 6 章 + 在线附录 A（逻辑设计基础，与 L2-02 重叠）/B（GPU）/C（控制映射）。QA 6th ed 正文 7 章 + 印刷附录 A（ISA 原理）/B（内存层次复习）/C（流水线基础），另有在线附录 D–M。

---

## L2-02 数字逻辑电路（P1｜文·证｜锚点 Harris & Harris DDCA RISC-V ed ch1–5）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| DL-1 | 数制与信息编码 | 二/十六进制、有符号数(补码)、逻辑电平、CMOS 晶体管到门的物理实现 | H&H ch1 From Zero to One | ⟳与 L3-02 CH-3(算术)、L3-03 数据表示同源；此处讲"位怎么在硬件里存/成门" |
| DL-2 | 布尔代数与组合逻辑化简 | 公理/定理、SOP/POS 范式、卡诺图、NAND/NOR 完备集、真值表↔门三位一体 | H&H ch2 Combinational Logic Design | ⟳L1-02 离散数学(布尔代数/逻辑)是理论前置；本课落到门级 |
| DL-3 | 组合电路构件 | 加法器(半/全加、行波 vs 超前进位 CLA)、MUX/DEMUX、译码/编码器、比较器、ALU 拼装；传播延迟与竞争-冒险(glitch) | H&H ch2/ch5 Digital Building Blocks | →L3-02 CH-4：ALU/MUX 正是数据通路元件 |
| DL-4 | 时序逻辑基础 | 锁存器 vs 边沿触发器、D/JK/T、寄存器、亚稳态、建立/保持时间 | H&H ch3 Sequential Logic Design | →L3-02：触发器→寄存器堆/段寄存器；同步时序模型 |
| DL-5 | 有限状态机(硬件 FSM) | Moore vs Mealy、状态编码(独热/二进制)、状态图→状态表→激励方程→门级 | H&H ch3 | ⟳L3-05 自动机(抽象 FSM)是理论孪生；→L3-02 多周期/微程序控制器；证：Python 逐拍模拟 |
| DL-6 | 时序分析与时钟 | 关键路径/逻辑深度决定 Fmax、时钟偏斜、时序约束、流水线为何能提时钟(埋伏笔) | H&H ch3(时序) | →L3-02 CH-5(流水线)、L5-05：时钟/关键路径是流水化的动机 |
| DL-7 | 数字构建块与存储阵列 | 移位器/乘除法器、SRAM/DRAM/ROM 阵列、可编程逻辑(PLA/FPGA)、（可选）HDL(SystemVerilog)建模 | H&H ch5 Digital Building Blocks / ch4 HDL | →L3-02 CH-6(内存层次)：存储阵列是 cache/主存的物理底座 |

**建议大主题数：K = 7**

---

## L3-02 计算机组成原理（P0 枢纽｜文·码·证｜锚点 COD RISC-V 2nd ed ch1–6 + 附录 A）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| CO-1 | 计算机抽象与性能评价 | 抽象层次、性能公式 `CPU时间=IC×CPI×周期`、Amdahl 定律、功耗墙、多核转向 | COD ch1 Computer Abstractions and Technology | 全组性能语言的公理；→L5-05 CH-1 量化基础深化 |
| CO-2 | 指令集体系结构(ISA) | RISC-V 指令格式/寻址/寄存器模型、过程调用、RISC vs CISC、ISA vs 微架构分账 | COD ch2 Instructions: Language of the Computer | ⟳**与 L3-03 强绑定**：L3-02 定义 ISA(教学 RISC-V)，L3-03 落到本机 x86-64 字节 |
| CO-3 | 计算机算术 | 整数加减乘除、溢出、IEEE-754 浮点表示与运算、ALU 构造 | COD ch3 Arithmetic for Computers | ⟳承 L2-02 DL-1/DL-3；⟳L3-03 CH-7 浮点机器码是其落地 |
| CO-4 | 处理器数据通路与控制(单周期) | PC→取指→译码→读寄存器→ALU→访存→写回；控制信号从指令位段派生；单周期 CPI=1 | COD ch4 The RISC-V Processor（§4.1–4.4）；附录 A 逻辑设计基础 | ←L2-02 DL-3/DL-5 提供元件；教学 RISC-V 模型 ≠ 本机 x86-64 乱序(指给 L5-05) |
| CO-5 | 流水线(本课高潮) | 5 级(IF/ID/EX/MEM/WB)、段寄存器、三类冒险(结构/数据/控制)、转发、停顿、分支预测入门 | COD ch4（§4.5–4.9） | →L5-05 CH-4：把顺序 5 级升级为超标量/乱序；证：Python 逐拍模拟冒险 |
| CO-6 | 内存层次与缓存 | 局部性、直接映射/组相联/全相联、写直达 vs 写回、替换策略、AMAT、虚拟内存/TLB | COD ch5 Large and Fast: Exploiting Memory Hierarchy | →L5-05 CH-2 高级内存优化；→L4-01 虚拟内存；证：sysfs 读真实 cache + 步长扫描 |
| CO-7 | I/O 与总线 | 内存映射 vs 端口 I/O、轮询/中断/DMA、总线带宽/延迟、存储可靠性(RAID) | COD ch5/ch6（I/O 与可靠性节） | →L4-01 OS(中断/驱动)；→L3-03 系统调用边界 |
| CO-8 | 并行处理器与多核入门 | SISD/SIMD/MIMD 分类、多核、GPU 概览、缓存一致性入门 | COD ch6 Parallel Processors from Client to Cloud；附录 B GPU | →L5-05 CH-5/CH-6(一致性/WSC 深化)；→L4-05 并发；勿与 L5-05 重复深挖 |

**建议大主题数：K = 8**

---

## L3-03 汇编与机器级表示（P1｜文·码·证｜本组"可观测面"｜锚点 CSAPP ch3 + SysV ABI + CS107）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| AS-1 | 机器级程序基础与编译产物 | C→`gcc -S`→汇编器→`.o`→链接→可执行；程序编码、AT&T 语法、`-O0` vs `-O2` 汇编 diff | CSAPP §3.1–3.2；CS107(Assembly 概览) | ⟳**与 L3-02 CO-2 强绑定**；→L4-04 编译原理(L3-03 是编译器输出) |
| AS-2 | 数据格式与信息访问 | 数据尺寸/后缀、16 个通用寄存器、操作数寻址 `base+index*scale+disp`、RIP-relative(PIE)、mov/lea | CSAPP §3.3–3.4 | ⟳L3-02 CO-2(寻址方式)在真实 x86-64 上的落地 |
| AS-3 | 算术逻辑与位运算 | add/sub/imul、移位、按位与或异或、lea 做算术、条件码副作用 | CSAPP §3.5 | ⟳L3-02 CO-3(算术)的指令级视角 |
| AS-4 | 控制流 | 条件码、jmp/条件跳转、if/循环编译模式、switch 跳转表、条件传送(cmov) | CSAPP §3.6；CS107(Control Flow) | ⟳L3-02 CO-5 分支；→L4-04 控制流生成 |
| AS-5 | 过程、栈帧与调用约定(SysV ABI) | call/ret、参数寄存器 `rdi,rsi,rdx,rcx,r8,r9`(第7+入栈)、返回值 rax、caller/callee-saved、红区(128B)、栈对齐 16B、序言/尾声 | CSAPP §3.7 + **System V AMD64 ABI**(规范级，R4 记提交哈希) | →L4-01(系统调用约定)、L4-05(栈是并发攻击面)；证：objdump 序言 + gdb 栈帧(R2 §3-A 已确认前 6 参数顺序) |
| AS-6 | 复合数据的机器表示 | 数组寻址、结构体对齐/填充、联合、指针与地址算术、大小端(本机小端) | CSAPP §3.8–3.9 | ⟳L2-01 数据结构的内存布局；→L4-04 目标代码生成 |
| AS-7 | 浮点机器码与安全代码模式 | XMM/YMM 寄存器、AVX 标量浮点(§3.11)；缓冲区溢出、栈金丝雀、PIE/ASLR、CET(endbr64)(§3.10) | CSAPP §3.10–3.11 | ⟳L3-02 CO-3 浮点；→**L5-04 安全**(栈溢出/CET/金丝雀)；→L5-05(SIMD 向量寄存器) |

**建议大主题数：K = 7**

---

## L5-05 高级计算机体系结构（P2｜文·码·证｜锚点 Hennessy-Patterson QA 6th ed ch1–7 + 附录 A/B/C）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| AA-1 | 量化设计基础 | 性能/成本/功耗/可靠性度量、Amdahl、局部性原理、评测方法学(基准与统计有效性) | QA ch1 Fundamentals of Quantitative Design and Analysis；附录 A ISA 原理 | ←L3-02 CO-1 深化；⟳N-10 性能剖析(应用级 vs 微架构分账，见 R1 §3-B) |
| AA-2 | 高级内存层次 | 多级 cache 优化、预取、非阻塞 cache、虚拟内存/地址转换、DRAM/HBM 技术 | QA ch2 Memory Hierarchy Design；附录 B 内存层次复习 | ←L3-02 CO-6 深化；证：rdtscp 步长扫描 + valgrind cachegrind |
| AA-3 | 指令级并行与静态调度 | 流水线深化(附录 C)、循环展开、静态多发射/VLIW、编译器调度、ILP 极限 | QA ch3(静态部分)；附录 C Pipelining | ←L3-02 CO-5(5 级流水线)升级；⟳L4-04 编译器调度 |
| AA-4 | 动态调度·超标量·投机 | Tomasulo(保留站/CDB)、寄存器重命名、ROB 与精确异常、按序退休、分支预测(2-bit→gshare→TAGE)、乱序 MLP | QA ch3(动态部分) | ⟳投机侧信道 Spectre/Meltdown →**L5-04 安全**；证：可预测 vs 随机分支 rdtscp 耗时比 |
| AA-5 | 数据级并行(DLP) | 向量处理器、SIMD/AVX-512(本机有，见 R2 §3-D)、GPU/SIMT、分支发散、占用率、Roofline 模型 | QA ch4 Data-Level Parallelism in Vector, SIMD, and GPU | →**L6-06 HPC / N-16 GPU 编程**(勿重复立项：本课讲硬件机制，L6-06 讲编程落地)；证：AVX-512 vs 标量耗时比 |
| AA-6 | 线程级并行与多处理器 | 共享内存 SMP、缓存一致性(MESI/MOESI、嗅探 vs 目录)、内存一致性模型(x86-TSO vs 弱序)、屏障、伪共享 | QA ch5 Multiprocessors and Thread-Level Parallelism | ⟳**内存序/屏障与 L4-05 并发必分账**(R2 表 1.5)；←L3-02 CO-8；证：伪共享 vs 填充耗时比 |
| AA-7 | 仓库级计算机(WSC) | 数据中心作为计算机、请求级并行、能效/PUE、集群互连、成本模型 | QA ch6 The Warehouse-Scale Computer | →L5-01 分布式 / L6-05 云系统(6th ed 新增视角) |
| AA-8 | 领域专用体系结构(DSA) | 加速器(TPU/GPU 张量核)、专用化收益、软硬协同、Roofline 上的 DSA | QA ch7 Domain-Specific Architectures | →L6-03 深度学习 / L6-06 HPC / N-11 AI 系统(6th ed 新增，硬标演进快) |

**建议大主题数：K = 8**

---

## 组内汇总

- **M = 4 门课**：L2-02(K=7) · L3-02(K=8) · L3-03(K=7) · L5-05(K=8)
- **T = 30 个大主题**
- **递进链核对**：数字逻辑 DL-3/DL-4(元件) → 组成 CO-4/CO-5(数据通路+流水线) → 汇编 AS-*(同一 ISA 落 x86-64 字节) → 高级体系结构 AA-3/AA-4/AA-6(超标量/乱序/并行) —— 每层"用更多硬件换指令吞吐"。
- **强绑定**：L3-02 CO-2(ISA) ⟷ L3-03 AS-1/AS-2(机器级落地)；教学 RISC-V(L3-02) 与本机 x86-64(L3-03) 分账，差异指向 L5-05。
- **跨组接缝**（供 R4 合并点出，勿重复立项）：内存序/屏障 AA-6↔L4-05；向量/GPU AA-5/AA-8↔L6-06/N-16；投机侧信道 AA-4↔L5-04；CET/栈金丝雀 AS-7↔L5-04；中断/虚拟内存 CO-6/CO-7↔L4-01；WSC/DSA AA-7/AA-8↔L5-01/L6-03/N-11。
