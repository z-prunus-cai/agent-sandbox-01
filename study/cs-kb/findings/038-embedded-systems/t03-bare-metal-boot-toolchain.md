# L5-15·大主题3 裸机执行模型、启动与工具链

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-08-01 ｜ 先修：C 语言（指针、`static`/全局变量、`const`）、大主题2 处理器与内存架构（Cortex-M 寄存器模型、Flash/SRAM/外设的内存映射）、对汇编与链接有大致印象 ｜ 一手锚点：ARM《Cortex-M4 Devices Generic User Guide》(DUI0553) 与《ARMv7-M Architecture Reference Manual》(DDI0403) 复位/向量表/VTOR 节；GNU 官方文档《Using ld》(binutils) 链接脚本、《Using the GNU Compiler Collection (GCC)》Volatiles 与 `-mcpu`/`-mthumb`/`-mfloat-abi` 节；Valvano EE319K/EE445L 裸机开发流程 http://users.ece.utexas.edu/~valvano/arm/outline1.htm ；副锚 Lee & Seshia《Introduction to Embedded Systems》2nd ed Ch.9「Memory Architectures」/Ch.8 ｜ 成熟度：ARMv7-M 复位/向量表语义与 ELF 段模型 GA、长期稳定；`⚙演进快·锚平台版本` = 具体工具链版本（本报告实证锚 arm-none-eabi-gcc 13.2.1 / binutils 2.42）、烧录/调试工具（OpenOCD、厂商烧录器）、newlib 版本、TI EK-TM4C123GXL（Cortex-M4F）具体地址

本关讲的是"一段 C 代码，在没有操作系统的芯片上，从上电那一刻到 `main` 第一条语句之间到底发生了什么，以及是谁、用什么工具、按什么规则把它摆到芯片里正确的位置上"。桌面程序里这些全被操作系统和加载器（loader）替你办了——你从来不用管栈指针谁初始化、全局变量的初值从哪来、代码被放到哪个地址。裸机（bare-metal，无 OS）开发把这层帘子彻底掀开：复位序列、startup 代码、链接脚本、`volatile`、交叉工具链，这五样合起来就是"没有 OS 时，程序如何被正确地放好并跑起来"的完整答案。

芯片上有两种存储器——非易失的 Flash（掉电不丢，放代码和常量）和易失的 SRAM（掉电即失，放可变数据和栈）。程序里几乎所有的"启动魔法"，本质都是在处理"初值存在 Flash 里、但变量要在 SRAM 里跑"这个矛盾。把这条线记住，后面 startup 复制 `.data`、链接脚本里的 VMA/LMA 双地址、`volatile` 为什么必要，都能顺下来。

本报告的具体地址、反汇编与段布局，均在基线机用 GNU Arm 交叉工具链（`arm-none-eabi-gcc 13.2.1`、`binutils 2.42`，目标 Cortex-M4）现场编译得出并贴出真实输出；本机无参考板，故"烧录到真实芯片"一步无法实证，相关内容硬标为文档腿。

---

## 3.1 复位序列、startup 代码与中断向量表布局

### 3.1.1 上电复位后 Cortex-M 硬件自动做的头两件事

Cortex-M（ARMv7-M 架构）在上电或复位后，处理器硬件会自动做两件事、且只做这两件与"取指令"有关的事：先从地址 `0x00000000` 读一个 32 位字，把它装进主栈指针 MSP；再从地址 `0x00000004` 读一个 32 位字，把它当作复位处理函数（Reset_Handler）的入口地址，装进 PC 开始执行。这就是"复位序列"的最内核——之后跑的每一行代码都是软件（startup 代码）的事了。

这是 Cortex-M 和很多老架构（如经典 ARM7/8051、x86）一个关键区别：MSP 的初值不是软件设的，而是硬件在复位时从向量表第一个字直接加载好。为什么这么设计？因为 C 代码一旦运行就可能用到栈（函数调用、局部变量），如果栈指针在第一条 C 语句前还没有效值，程序立刻就崩。ARM 把"给 MSP 一个合法初值"提前到硬件动作里，保证复位处理函数的第一条指令就能安全地压栈。

一个最小固件的向量表被链接到 Flash 起始处，`objdump` 直接读出其原始字节（小端序）：

```text
Contents of section .isr_vector:
 0000 00800020 45000000 41000000 41000000  ... E...A...A...
```

第一个字 `00800020`（小端，实际值 `0x20008000`）就是初始 MSP：它等于 SRAM 起始 `0x20000000` 加上 32 KB 容量，也就是"栈顶"。第二个字 `45000000`（实际值 `0x00000045`）就是复位向量。硬件复位后，MSP=`0x20008000`、PC=`0x00000045`，一条 C 指令都还没跑。

### 3.1.2 中断向量表的布局

向量表（vector table）是一张放在内存里的函数指针数组：每个表项是一个 32 位地址，指向对应异常/中断发生时要跳去执行的处理函数。表项顺序由 ARMv7-M 架构固定：偏移 `0x00` 是初始 MSP（唯一一个不是函数指针、而是数据的表项），`0x04` 是 Reset，`0x08` 是 NMI，`0x0C` 是 HardFault，接着是若干系统异常（MemManage、BusFault、UsageFault、保留、SVCall、DebugMon、保留、PendSV、SysTick），从偏移 `0x40` 起是芯片厂商定义的外部中断 IRQ0、IRQ1…（具体哪个 IRQ 对应哪个外设由 SoC 决定）。

下面是本机固件里向量表的布局对照（前几项）：

```text
偏移    表项            本机实测内容            含义
0x00    Initial MSP     0x20008000             栈顶（SRAM 顶）
0x04    Reset           0x00000045             复位处理函数入口（含 Thumb 位）
0x08    NMI             0x00000041             （本例 weak 别名到 Default_Handler）
0x0C    HardFault       0x00000041             同上
0x2C    SVCall          …                      系统调用异常
0x38    PendSV          …                      常用于 RTOS 上下文切换
0x3C    SysTick         0x00000041             系统节拍定时器中断
0x40+   IRQ0, IRQ1 …    （厂商定义）            外设中断
```

向量表不是"代码"，而是一张"事件→处理函数"的电话簿。芯片一旦发生某个异常（复位、错误、某外设中断），硬件不会去搜索谁来处理，而是直接按固定偏移去表里取那个地址、跳过去执行。所以这张表必须在芯片启动时就已经躺在内存里正确的位置上——它是链接脚本要操心的第一件事（见 3.2）。

向量表默认位于地址 0（即 Flash 起始，因为 Cortex-M 复位时从 0 取 MSP 和 Reset）。但 ARMv7-M 提供了向量表偏移寄存器 VTOR（Vector Table Offset Register，地址 `0xE000ED08`），软件可在运行时把向量表"搬"到别处（比如 SRAM，以支持运行时改写中断处理函数，或支持 bootloader + 应用两段式布局）。ARMv6-M（如 Cortex-M0）早期不一定有 VTOR，M0+ 起可选提供；跨内核细节标 `⚙演进快·锚平台版本`，以具体芯片手册为准。

### 3.1.3 复位向量里的 Thumb 位（地址最低位 = 1）

Cortex-M 只执行 Thumb（Thumb-2）指令，不支持 ARM 32 位定长指令集。ARM 用"分支目标地址的最低位（bit0）"来编码"跳过去后处理器处于 Thumb 状态还是 ARM 状态"：bit0=1 表示 Thumb。因此向量表里每个函数指针的最低位都必须是 1，即使函数真实地址是偶数（Thumb 指令 2 字节对齐，真实入口地址一定是偶数）。硬件取到向量后会清掉这个 bit0 再当地址用，同时据它设置执行状态。

本机实证把这一点摆得很清楚。反汇编显示复位处理函数的真实入口地址是 `0x44`：

```text
00000044 <Reset_Handler>:
  44:	4a11      	ldr	r2, [pc, #68]
```

而向量表第二个字（复位向量）存的却是 `0x00000045` = `0x44 | 1`。差的正是那个 Thumb 位。编译器/链接器在填函数指针时会自动加上它，你手写向量表数组时用 C 函数名取地址也会自动带上——但如果有人手工用裸地址、或用汇编填了一个偶数地址进复位向量，芯片一取到 bit0=0 的地址就会在切换执行状态时触发 HardFault，症状是"程序根本没进 `main` 就死了"，这是裸机新手的经典坑。

### 3.1.4 startup 代码的职责：把 C 运行环境搭起来

硬件复位序列只保证了 MSP 有效、PC 指向 Reset_Handler。可 C 语言的运行还依赖几个前提：已初始化的全局/静态变量必须持有它们源代码里写的初值，未初始化的全局/静态变量必须为 0（C 标准要求静态存储期对象默认零初始化）。这些前提不是硬件给的，得由一段叫 startup（启动/复位处理）的代码在跳进 `main` 之前手工建立。这段代码就是 Reset_Handler 的主体，通常用 C 或少量汇编写。

它的标准动作有三步。第一步，把已初始化数据段 `.data` 的初值从 Flash 复制到 SRAM——因为初值必须存在掉电不丢的 Flash 里，但变量要在可写的 SRAM 里用（这正是全章主线那个矛盾的落点）。第二步，把 `.bss` 段（未初始化的全局/静态变量占的 SRAM 区）整段清零。第三步，（若用 C 库/C++）调用库初始化和全局构造函数，然后调用 `main`。`main` 万一返回，通常落进一个死循环——裸机没有"退回操作系统"这回事。

下面是本机固件里 Reset_Handler 的核心逻辑（简化自实证源码），三步一目了然：

```c
extern uint32_t _sidata, _sdata, _edata, _sbss, _ebss;
void Reset_Handler(void) {
    uint32_t *src = &_sidata, *dst = &_sdata;
    while (dst < &_edata) *dst++ = *src++;      /* 1. 复制 .data: Flash -> SRAM */
    for (dst = &_sbss; dst < &_ebss; ) *dst++ = 0; /* 2. 清零 .bss           */
    main();                                      /* 3. 进入应用             */
    while (1) {}                                 /*    main 不该返回        */
}
```

这里的 `_sidata`、`_sdata`、`_edata`、`_sbss`、`_ebss` 都不是普通变量，而是由链接脚本定义、代表各段边界地址的符号（见 3.2.5）——startup 代码之所以能"知道要复制多少、从哪复制到哪、清零到哪"，全靠这些符号。这体现了裸机开发一个核心观念：startup 代码和链接脚本是一对紧耦合的搭档，必须共用同一套段边界符号名。

### 3.1.5 与通用操作系统启动的分账

初学者常把裸机启动和"电脑开机"混为一谈，要点明区别。桌面/服务器上，程序是一个 ELF/PE 可执行文件，由操作系统的加载器（loader）在你双击时从磁盘读入内存、按段建立虚拟地址映射、把 `.data`/`.bss` 安排好、初始化栈、再跳进入口——`.data` 复制、`.bss` 清零这些活是 OS 和 C 运行时（crt0）替你干的，你写的 `main` 之前有一整层看不见的地基。

裸机没有这层地基：没有 OS、没有加载器、没有文件系统、程序不是"被加载"的，而是烧录时就已经原样躺在 Flash 里，地址在链接时就钉死了。所以"复制 .data、清零 .bss、初始化栈"这些活没人替你干，全落到你（或芯片厂/工具链）提供的 startup 代码头上；向量表必须由你放到复位地址能取到的地方。换句话说，裸机开发者要亲手实现桌面上"加载器 + crt0"那一层的精简版。这也解释了为什么这门课要专门花一章讲启动——在有 OS 的世界里它是隐形的，在裸机世界里它是你的责任。

需要与后续 RTOS 大主题（E-10）分账：即使用了 FreeRTOS/Zephyr 这类实时内核，它们也是"应用的一部分"，同样被烧进 Flash、同样经历本节的硬件复位序列和 startup，然后由 `main` 里的代码启动内核调度器——RTOS 不是像桌面 OS 那样"先于应用存在、再加载应用"，这与通用 OS 的启动模型有本质不同。

#### 来源与时效

- ARM《Cortex-M4 Devices Generic User Guide》(DUI0553) / 《ARMv7-M Architecture Reference Manual》(DDI0403E)：复位行为（从 0x00 取 MSP、0x04 取复位向量）、向量表布局与表项顺序、VTOR（0xE000ED08）、EPSR.T（Thumb 状态位）与地址 bit0=1 的 Thumb 语义、取向量时清 bit0。ARM 官方一手手册。
- Valvano EE319K/EE445L 裸机开发流程：startup 三步（复制 .data、清零 .bss、调 main）、向量表放置、复位处理函数写法。http://users.ece.utexas.edu/~valvano/arm/outline1.htm
- Lee & Seshia《Introduction to Embedded Systems》2nd ed Ch.9「Memory Architectures」：Flash（非易失，放代码/常量）vs SRAM（易失，放数据/栈）分工，与本节 startup 复制动机交叉一致。https://ptolemy.berkeley.edu/books/leeseshia/
- 本机实证（`arm-none-eabi-gcc 13.2.1` / `binutils 2.42`，目标 Cortex-M4，核实 2026-08-01）：向量表原始字节 `00800020 45000000`（初始 MSP=0x20008000、复位向量=0x00000045）、Reset_Handler 真实地址 0x44 与向量值 0x45 的 Thumb 位差、startup 反汇编。工具链版本 `⚙演进快·锚平台版本`。
- 冲突/分歧：VTOR 是否存在与内核版本相关（ARMv6-M 早期无 / M0+ 可选 / ARMv7-M 有），以具体芯片手册为准，不做统一结论；本机无参考板，"烧录后真实上电取向量"一步为文档腿·未实证。

---

## 3.2 链接脚本与段布局（.text / .data / .bss / 栈 / 堆）

### 3.2.1 段（section）是什么：按用途把程序切成块

编译器把源码编译成目标文件时，并不是把所有东西混成一坨，而是按"用途 + 读写属性"把内容分门别类放进不同的段（section）。裸机里最核心的几个段是：`.text`（机器指令，只读）、`.rodata`（只读常量，如字符串字面量和 `const` 全局）、`.data`（有非零初值的全局/静态变量，可读写）、`.bss`（初值为 0 或未初始化的全局/静态变量，可读写），以及运行时才存在的栈（stack）和堆（heap）。

之所以要分段，是因为不同段该放到不同的物理存储器上。只读的 `.text`/`.rodata` 放 Flash（非易失、掉电保留、通常只读）；可读写的 `.data`/`.bss`/栈/堆放 SRAM（可写、掉电即失）。分段是链接脚本能把"什么放哪"讲清楚的前提——链接脚本本质就是一份"把哪些段摆到哪块存储器的哪个地址"的摆放说明书。

本机把一个最小固件编出来，各段布局如下（`objdump -h` 真实输出）：

```text
Idx Name          Size      VMA        LMA        含义
  0 .isr_vector   00000040  00000000   00000000   向量表，放 Flash 头
  1 .text         00000084  00000040   00000040   代码+.rodata，放 Flash
  2 .data         00000004  20000000   000000c4   有初值变量：跑在 SRAM，初值存 Flash
  3 .bss          00000004  20000004   （无 Flash 占用）  零初值变量：只占 SRAM
```

### 3.2.2 VMA 与 LMA：为什么 `.data` 有两个地址

上表里 `.data` 出现了两个不同的地址，这是链接脚本里最容易把初学者绕晕、却又最关键的概念。VMA（Virtual/link Memory Address，也叫运行地址）是"程序运行时这个段应该在的地址"；LMA（Load Memory Address，加载地址）是"这个段的内容在镜像里、被烧录时实际存放的地址"。绝大多数段两者相同，唯独 `.data` 天然不同。

原因还是全章那条主线。`.data` 里的变量要在 SRAM 里被读写，所以它的 VMA 是 SRAM 地址（本机实测 `0x20000000`）；但 SRAM 掉电就没了，变量的初值必须存在掉电不丢的 Flash 里，所以它的初值内容被烧在 Flash（本机实测 LMA `0x000000c4`，紧接在 `.text` 之后）。startup 代码干的第一件事，正是把这块内容从 LMA（Flash）复制到 VMA（SRAM）——3.1.4 那段复制循环里的 `_sidata` 就是这个 LMA，`_sdata` 就是这个 VMA。


```text
.data 的初值：烧录时住在 Flash（LMA），运行时住在 SRAM（VMA），startup 负责搬家。
```

`.bss` 则连 Flash 空间都不占（上表 `.bss` 无 Flash 占用）——因为它全是 0，没必要在镜像里存一堆 0，只要 startup 在 SRAM 里把那段清零即可。这就是"为什么把一个大数组显式初始化成 0 和不初始化，编出来的固件大小可能差很多"的原因：显式非零初值进 `.data`（占 Flash），零/未初始化进 `.bss`（不占 Flash）。

### 3.2.3 链接脚本的两大块：MEMORY 与 SECTIONS

GNU 链接器 `ld` 用一种专门的脚本语言（`.ld` 文件）描述摆放规则，核心是两条命令。`MEMORY` 命令声明芯片上有哪些存储器区域、各自的起始地址、长度和读写属性。`SECTIONS` 命令描述把各个输入段收集、摆放到哪个存储器区域、按什么顺序、边界符号叫什么。

下面是本机实证用的最小链接脚本，五脏俱全、够解释所有概念：

```ld
MEMORY {
  FLASH (rx)  : ORIGIN = 0x00000000, LENGTH = 256K
  SRAM  (rwx) : ORIGIN = 0x20000000, LENGTH = 32K
}
_estack = ORIGIN(SRAM) + LENGTH(SRAM);          /* 栈顶 = SRAM 末尾 */
SECTIONS {
  .isr_vector : { KEEP(*(.isr_vector)) } > FLASH   /* 向量表必须在 0 处 */
  .text : { *(.text*) *(.rodata*) } > FLASH        /* 代码+常量进 Flash */
  _sidata = LOADADDR(.data);                        /* .data 的 LMA */
  .data : { _sdata = .; *(.data*) _edata = .; } > SRAM AT> FLASH
  .bss  : { _sbss = .; *(.bss*) *(COMMON) _ebss = .; } > SRAM
}
```

几个要点。`> FLASH` 指定该段的 VMA 落在 FLASH 区；`AT> FLASH` 单独指定 `.data` 的 LMA 也落在 FLASH（而它的 VMA 由 `> SRAM` 指定在 SRAM）——这一行就是 VMA≠LMA 的来源。`KEEP(...)` 告诉链接器"即使没人引用也别把向量表当垃圾回收删掉"（开了 `--gc-sections` 时尤其重要，否则向量表可能被优化掉，芯片启动直接翻车）。`*(.text*)` 里的 `*` 是通配，收集所有输入文件的所有 `.text` 段。

### 3.2.4 栈与堆的放置

栈（stack）用于函数调用（返回地址、参数、局部变量、被调用者保存的寄存器）。Cortex-M 的栈是满递减（full-descending）：栈指针指向最后压入的有效数据，压栈时地址向下减小。裸机里通常把栈顶设在 SRAM 的最高地址（上面脚本里 `_estack = ORIGIN(SRAM) + LENGTH(SRAM)`），让它向下增长，而 `.data`/`.bss`/堆从 SRAM 低地址向上增长——两头相向生长，中间那块自由 SRAM 被双方共享。硬件复位时装进 MSP 的初值（向量表第 0 项）就是这个 `_estack`。

堆（heap）用于 `malloc`/`free` 动态分配，通常放在 `.bss` 之后、向高地址增长。裸机里堆和栈相向增长，中间没有 MMU 做隔离（见大主题2），一旦栈长得太深、或堆分配太多，两者会在 SRAM 中间相撞（stack-heap collision），互相踩踏、症状诡异且难查——这是裸机 RAM 极其有限（KB 级）时的头号可靠性隐患，也是很多嵌入式规范干脆禁用动态内存、只用静态分配的原因。

一个初学者常见的错误认知是"栈溢出会报错"。裸机上通常不会：没有操作系统的保护页、没有 MMU 的越界陷阱，栈越过边界就是默默地把 `.bss` 或堆的数据覆盖掉，程序继续跑、但数据已经错乱。有 MPU（内存保护单元，见大主题2）的芯片可以设一个保护区把栈溢出变成 fault，但那要显式配置，默认没有。

### 3.2.5 边界符号：链接脚本与 startup 代码的接口

链接脚本里形如 `_sdata = .;` 的写法定义了一个符号，值等于"当前位置计数器 `.`（定位到该处时的地址）"。这些符号会像全局变量的地址一样暴露给 C 代码。3.1.4 的 startup 代码里 `extern uint32_t _sdata, _edata, ...;` 声明的，正是这些由链接脚本定义的边界符号。

要特别小心一个 C 语言层面的坑：这些符号的"值"是地址，但在 C 里要通过"取地址"来用，即写 `&_sdata` 才得到那个边界地址，直接读 `_sdata` 会把该地址处的内存内容当成变量值——这是几乎每个裸机新手都踩过一次的坑。startup 代码遍历时用的是 `&_sdata`、`&_edata` 这种取地址形式。

段的实际大小和地址只有链接时才确定（取决于代码里到底有多少全局变量），startup 代码不可能把地址写死，只能通过这些"链接期才填值"的符号来间接引用边界。改一改代码、变量增减，重新链接后符号值自动更新，startup 代码一个字不用改。这是链接脚本 + 边界符号 + startup 三者协作的精髓。

#### 来源与时效

- GNU《Using ld》(GNU Binutils 官方文档) 链接脚本章：`MEMORY`/`SECTIONS` 命令、VMA vs LMA、`AT>`/`LOADADDR`、定位计数器 `.`、符号定义、`KEEP`、通配符收集。https://sourceware.org/binutils/docs/ld/
- ARM《Cortex-M4 Devices Generic User Guide》：Cortex-M 栈为满递减、MSP 初值来自向量表首项、Flash/SRAM 典型地址布局（0x00000000 Flash、0x20000000 SRAM）。
- Valvano EE319K/EE445L：段模型、.data/.bss/栈/堆在 Flash/SRAM 的分配、栈-堆相向增长与冲突。
- Lee & Seshia 2nd ed Ch.9「Memory Architectures」：存储器层次、易失/非易失分工、静态 vs 动态分配在实时系统里的权衡（副锚，与栈-堆冲突/禁用动态内存交叉）。
- 本机实证（核实 2026-08-01）：`objdump -h` 段表（.isr_vector/.text/.data/.bss 的 VMA/LMA/大小）、上引最小链接脚本可成功链接（`arm-none-eabi-gcc ... -T link.ld` 退出码 0）、`.data` VMA=0x20000000 与 LMA=0x000000c4 分离。
- 冲突/分歧：不同厂商/工具链的边界符号命名不统一（如 `_sdata`/`__data_start__`/`_data`），本报告用 GNU 常见命名，实际以所用 startup+链接脚本配套约定为准，非语义分歧而是命名约定差异。

---

## 3.3 内存映射 I/O 与 volatile 语义

### 3.3.1 内存映射 I/O：外设寄存器就是一些特殊地址

Cortex-M 用内存映射 I/O（memory-mapped I/O）访问外设：每个外设（GPIO、UART、定时器、ADC……）的控制/状态/数据寄存器都被映射到内存地址空间里一段固定地址上（Cortex-M 上外设区通常在 `0x40000000` 起），你读写这些地址，硬件就把它翻译成对相应外设寄存器的读写。没有专门的 I/O 指令（对比 x86 的 `in`/`out`），普通的 load/store 指令就能操作外设——这就是"内存映射"的含义。

对 C 程序员来说，访问一个外设寄存器就是解引用一个指向固定地址的指针。比如把某状态寄存器抽象成：

```c
#define STATUS_REG (*(volatile uint32_t *)0x400253FC)
```

读 `STATUS_REG` 就是读那个地址、进而读到硬件当前状态；写它就是配置硬件。这里 `volatile` 不是可有可无的修饰——它是让这套机制正确工作的关键，下面详述。

### 3.3.2 volatile 的语义：每次访问都要真的发生

`volatile` 告诉编译器：这个对象的值可能在程序控制流之外被改变（被硬件、被中断处理函数、被另一个执行单元），因此对它的每一次读、每一次写，都必须原样生成一次真实的内存访问，不许优化掉、不许缓存进 CPU 寄存器复用、不许把多次访问合并成一次，也不许把它移到别的地方去。C 标准的措辞是：通过 volatile 左值的访问，严格按照抽象机的语义求值，不得被优化省略或重排（相对其它可见副作用）。

为什么外设寄存器必须 `volatile`？因为编译器优化的默认假设是"只要我没写过这个变量，它的值就不会变"。对普通内存变量这没错；但外设状态寄存器会被硬件自己改（比如"数据到齐"标志位由硬件置位）。如果不加 `volatile`，编译器会觉得"这个地址我读过一次了、期间没写过、值肯定没变"，于是把它的值缓存在 CPU 寄存器里循环复用，或者干脆把整个轮询循环优化没——结果就是程序永远看不到硬件的变化，死等在那里。

### 3.3.3 忘记 volatile 的经典 bug（本机反汇编对照）

这个 bug 抽象讲不如看反汇编直观。下面在基线机上用 `arm-none-eabi-gcc -O2`（开优化）编译两个只差 `volatile` 的轮询函数：都在等某状态寄存器的 bit0 变成 1。

```c
#define REG    (*(volatile uint32_t *)0x400253FC)   /* 正确：volatile */
#define REG_NV (*(uint32_t *)0x400253FC)            /* 错误：漏了 volatile */
void wait_volatile(void)    { while ((REG    & 0x01) == 0) { } }
void wait_nonvolatile(void) { while ((REG_NV & 0x01) == 0) { } }
```

本机 `-O2` 反汇编真实输出（Cortex-M4，核实 2026-08-01）：

```text
00000000 <wait_volatile>:
   2:	f8d2 33fc 	ldr.w	r3, [r2, #1020]   @ 循环体内，每轮都重新 ldr 读寄存器
   6:	07db      	lsls	r3, r3, #31
   8:	d5fb      	bpl.n	2 <wait_volatile+0x2>  @ 未满足则跳回 0x2 再读一次
   a:	4770      	bx	lr

00000010 <wait_nonvolatile>:
  12:	f8d3 33fc 	ldr.w	r3, [r3, #1020]   @ 只在进入时读一次（循环体外）
  18:	d400      	bmi.n	1c ...
  1a:	e7fe      	b.n	1a <...>          @ 死循环：只是无限跳自己，再也不读寄存器
```

`volatile` 版本每轮循环都在 `0x2` 处重新 `ldr` 读那个寄存器，所以能看到硬件把 bit0 置 1、随后退出。非 `volatile` 版本被编译器"优化"成——在 `0x12` 处只读一次，之后如果条件没满足，就落进 `1a: b.n 1a` 这个"无限跳转到自己"的死循环，再也不去读寄存器。硬件后来即使把 bit0 置了 1，这段代码也永远看不到，程序彻底卡死。这就是漏 `volatile` 最典型、最难 debug 的症状：低优化（`-O0`）时凑巧能跑，一开 `-O2` 就死。

### 3.3.4 volatile 不保证什么（易错点）

`volatile` 常被初学者过度神话，要把边界讲清。`volatile` 只保证"访问真的发生、不被优化掉/合并/省略"，它不提供三样东西。第一，不保证原子性：对一个 `volatile` 变量的读-改-写（如 `reg |= 0x01`）在指令层面仍是"读、改、写"多步，中途可能被中断打断，导致丢失更新——需要原子操作或临界区（关中断）来保护。第二，不是内存屏障：在弱内存序或多核（如 Cortex-A、双核 MCU）场景，`volatile` 不保证不同地址访问之间的顺序对其它观察者可见，跨核/跨 DMA 的顺序要靠 `DMB`/`DSB` 等屏障指令或专门的同步原语。第三，不替代同步：多线程/中断与主循环共享数据时，`volatile` 只能防止"编译器优化掉访问"，防不住竞态；正确性还得靠关中断、原子类型或锁。


```text
volatile = "这次访问别给我优化没了"，不等于 "原子" 也不等于 "有序" 也不等于 "线程安全"。
```

反过来，也不该滥用 `volatile`：给普通计算变量乱加 `volatile` 会关掉编译器优化、让代码变慢变大，只在"值会被程序控制流之外改变"（外设寄存器、中断与主循环共享的标志、被 DMA 写的缓冲）时才用。这个"什么时候该加、什么时候不该加"的判断，是裸机 C 的一项基本功。

#### 来源与时效

- ISO/IEC C 标准（C11/C17）关于 `volatile` 限定符与"通过 volatile 左值的访问构成可见副作用、按抽象机语义求值"的规定；GCC《Using the GNU Compiler Collection》"Volatiles"/"When is a Volatile Object Accessed?" 节对实现语义的说明。https://gcc.gnu.org/onlinedocs/gcc/Volatiles.html
- ARM《Cortex-M4 Devices Generic User Guide》/《ARMv7-M ARM》：内存映射 I/O、外设区地址、`DMB`/`DSB`/`ISB` 屏障语义（说明 volatile 不等于屏障）。
- Valvano EE319K/EE445L：内存映射 I/O 概念、外设寄存器用 `volatile` 指针访问的惯用法与"漏 volatile 导致轮询失效"的教学案例。
- 本机实证（`arm-none-eabi-gcc 13.2.1`，`-O2 -mcpu=cortex-m4 -mthumb`，核实 2026-08-01）：上引 volatile vs 非 volatile 轮询函数的真实反汇编对照——volatile 版循环内重复 `ldr`，非 volatile 版读一次后落入 `b.n` 自跳死循环。
- 冲突/分歧：无实质来源冲突。需分账的是"规范 vs 实现"——C 标准只规定"volatile 访问是可见副作用、不被省略/重排（相对其它副作用）"，"每次访问对应一条 load/store"是 GCC/主流实现的行为而非标准逐字强制；本报告的指令级结论锚定 GCC 实现并标工具链版本。

---

## 3.4 交叉编译 / 烧录 / 调试流程（gcc / OpenOCD / gdb）

> 本节工具链细节 `⚙演进快·锚平台版本`：实证锚 GNU Arm 工具链 `arm-none-eabi-gcc 13.2.1` + `binutils 2.42`（基线机现装）；烧录/调试工具（OpenOCD、厂商烧录器）本机未安装、无参考板，"连芯片烧录/在线调试"为文档腿·未实证。其它芯片/工具链（LLVM embedded、IAR、Keil MDK、厂商 IDE）流程细节可能不同。

### 3.4.1 交叉编译工具链：在 x86 上编出 ARM 代码

裸机开发几乎总是交叉编译（cross-compile）：你在 x86-64 的开发机上运行编译器，但它产出的是目标芯片（ARM Cortex-M）能执行的机器码。承载这件事的是交叉工具链，GNU 世界里就是 `arm-none-eabi-*` 系列——前缀 `arm-none-eabi` 表示"目标是 ARM 架构、无操作系统（none）、遵循 ARM EABI（嵌入式应用二进制接口）"。它包含交叉版的 `gcc`（编译器）、`ld`（链接器）、`objcopy`/`objdump`（镜像转换/反汇编）、`gdb`（调试器）、以及嵌入式 C 库 newlib。

编译时几个关键选项决定"产物针对哪颗核、用哪套指令、怎么用浮点"，跨核不通用，必须显式给对：

```text
-mcpu=cortex-m4       选目标内核（决定可用指令、调度模型）
-mthumb               生成 Thumb 指令（Cortex-M 只支持 Thumb，必给）
-mfloat-abi=hard      浮点传参走 FPU 寄存器（Cortex-M4F 有硬件 FPU 时）
-mfpu=fpv4-sp-d16     指定 FPU 类型（M4F 为单精度 fpv4-sp-d16）
-ffreestanding        独立环境：不假设有标准托管环境（无 OS）
-nostartfiles         不用默认 crt0，用自己的 startup（裸机常用）
-T link.ld            用自己的链接脚本
```

嵌入式 C 库通常用 newlib，或其精简版 newlib-nano（通过 `--specs=nano.specs` 选用，去掉大部分不常用特性、显著减小体积，适合 KB 级 RAM/Flash）。newlib 把"底层系统调用"（`_write`、`_sbrk`、`_read` 等）留成需要你实现的桩：裸机没有 OS 提供这些，得自己写（比如把 `_write` 接到 UART 实现 `printf` 输出，把 `_sbrk` 接到堆边界符号实现 `malloc`），或用 `--specs=nosys.specs` 链一套空桩。这解释了裸机上"`printf` 默认没输出"的常见困惑——库函数在，但底层出口没接。`⚙演进快`：newlib 版本与 `*.specs` 细节随工具链版本演进，以所用版本文档为准。

### 3.4.2 从 ELF 到可烧录镜像

链接器产出的是 ELF 文件（`fw.elf`），里面除了代码数据，还带符号表、调试信息、段的 VMA/LMA 元数据——这些对调试有用，但烧录器往往只要"纯二进制内容"。所以流程里常有一步用 `objcopy` 把 ELF 转成裸二进制 `.bin` 或 Intel HEX `.hex`：

```text
arm-none-eabi-objcopy -O binary  fw.elf fw.bin   # 纯二进制，需自己指定烧录起始地址
arm-none-eabi-objcopy -O ihex    fw.elf fw.hex   # Intel HEX，自带地址信息
arm-none-eabi-size fw.elf                        # 看 text/data/bss 大小是否装得下
```

`.bin` 是从最低地址起、把镜像内容原样铺出来的字节流，不含地址信息，烧录时要你告诉工具"从 Flash 哪个地址开始写"；`.hex`（Intel HEX）是带地址记录的文本格式，自带"哪段内容烧到哪"，多数烧录工具直接吃。`size` 一步用来核对固件的 `.text+.data` 是否超 Flash、`.data+.bss+栈+堆` 是否超 SRAM——这是裸机每次构建都该看的资源体检。本机 `arm-none-eabi-size` 对最小固件报 `text 196 / data 4 / bss 4`，可用于确认装得进目标存储器。

### 3.4.3 烧录（把镜像写进 Flash）

烧录（flashing/programming）是把镜像写进芯片的非易失 Flash，让它掉电保留、下次上电能被复位序列取到。主流途径有几类。其一，调试探针 + OpenOCD：通过 SWD（Serial Wire Debug，两线）或 JTAG 接口，用一个硬件探针（如 ST-Link、J-Link、CMSIS-DAP，或 TI LaunchPad 板载的调试器）连上芯片，主机端跑 OpenOCD（开源片上调试软件）驱动探针擦写 Flash。其二，厂商烧录工具/IDE（TI UniFlash、STM32CubeProgrammer、Keil/IAR IDE 内置）。其三，芯片内置 bootloader：很多 MCU 出厂带一段 ROM bootloader，可经 UART/USB/CAN 接收固件写 Flash，无需专用探针（常用于产线量产和现场升级）。

一条典型的 OpenOCD 命令行形态（概念示意，本机无探针无板、未实证）：

```text
openocd -f interface/<probe>.cfg -f target/<soc>.cfg \
        -c "program fw.elf verify reset exit"
```

它启动 OpenOCD、加载探针与目标芯片的配置、把 `fw.elf` 写进 Flash 并校验、复位芯片开始运行。SWD 相比老的 JTAG 只用两根信号线（SWDIO/SWCLK），是 Cortex-M 上的主流。`⚙演进快·锚平台版本`：探针型号、OpenOCD 版本、目标 `.cfg` 都随平台/版本变，且本机无参考板，本步全程为文档腿。

### 3.4.4 调试（gdb + OpenOCD gdbserver、半主机、SWO/ITM）

裸机调试的主力仍是 gdb，但和调试桌面程序有个关键不同：桌面上 gdb 直接调试本机进程；裸机上 gdb（用交叉版 `arm-none-eabi-gdb`）运行在开发机，通过网络连到一个 gdbserver——通常就是 OpenOCD 兼职提供的 gdb 服务端口，OpenOCD 再经探针操纵芯片。于是形成一条链：`gdb（主机） ↔ OpenOCD gdbserver ↔ SWD 探针 ↔ 目标芯片`。gdb 发"设断点/单步/读寄存器/读内存"的命令，经这条链落到真实硬件上执行。这样你能在源码级打断点、单步、看变量和外设寄存器的值，跟调试普通程序体验接近，但控制的是真芯片。

典型连接（概念示意，本机未实证）：

```text
arm-none-eabi-gdb fw.elf
(gdb) target extended-remote localhost:3333   # 连 OpenOCD 的 gdb 端口
(gdb) load                                     # 把固件下载进目标
(gdb) break main
(gdb) continue
```

除了这种"停下来单步"的侵入式调试，裸机还常用两类轻量手段。半主机（semihosting）：让目标上的 `printf` 等 I/O 通过调试探针"借"主机的终端/文件系统输出，无需自己实现 UART 出口，方便但很慢、且依赖调试器连着，只适合开发期。SWO/ITM 跟踪：Cortex-M 的调试组件 ITM（Instrumentation Trace Macrocell）可经单线 SWO 输出跟踪/`printf` 数据，比半主机快、对实时性干扰小，是不打断执行看运行时信息的常用方式。这些同样依赖探针与工具链支持，本机无板未实证。

需与通用 OS 的调试分账：桌面调试有操作系统的进程模型、信号、`/proc`、ptrace 等一整套支撑；裸机没有这些，调试能力直接来自 Cortex-M 内核里的硬件调试单元（断点比较器 FPB、数据观察点 DWT、调试访问端口 DAP）——是芯片自带的硬件特性，通过 SWD/JTAG 暴露给探针，而不是软件层提供。这也是为什么裸机调试离不开一个物理探针。

#### 来源与时效

- GNU《Using the GNU Compiler Collection》ARM 选项节（`-mcpu`/`-mthumb`/`-mfloat-abi`/`-mfpu`/`-ffreestanding`/`-nostartfiles`）、GNU `ld`/`objcopy`/`size` 文档；GNU Arm Embedded Toolchain（`arm-none-eabi`）与 newlib/newlib-nano、`*.specs`（nano/nosys）说明。https://gcc.gnu.org/onlinedocs/ ；https://sourceware.org/newlib/
- OpenOCD 官方用户指南：SWD/JTAG、`program ... verify reset exit`、gdbserver 端口（默认 3333）。https://openocd.org/doc/html/index.html
- ARM《Cortex-M4 Devices Generic User Guide》/《ARMv7-M ARM》调试章：FPB（断点）、DWT（观察点）、DAP、ITM/SWO 跟踪、半主机机制；SWD 概览。
- Valvano EE319K/EE445L：完整裸机开发流程（交叉编译 → 烧录 → gdb/调试器在线调试）、TM4C123 + LaunchPad 板载调试器实践。http://users.ece.utexas.edu/~valvano/arm/outline1.htm
- 本机实证（核实 2026-08-01）：交叉编译与链接（`arm-none-eabi-gcc 13.2.1`）成功、`arm-none-eabi-size` 段大小输出；`arm-none-eabi-gdb`/`openocd` 本机未安装、无参考板，烧录与在线调试全程为文档腿·未实证。
- 冲突/分歧：工具/IDE 生态繁杂（GNU vs LLVM vs 商业 IAR/Keil；OpenOCD vs J-Link 专用软件 vs 厂商 CubeProgrammer/UniFlash），流程细节因链而异，本报告以开源 GNU + OpenOCD + gdb 一条链为锚，明写其它链可能不同；`⚙演进快·锚平台版本`。
