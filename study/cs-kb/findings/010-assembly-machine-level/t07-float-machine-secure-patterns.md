# L3-03·大主题7 浮点机器码与安全代码模式

> 基线/核实：2026-07-26（基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25）｜ 先修：L3-03 大主题1~6（机器级基础、寄存器与寻址、算术逻辑、控制流、栈帧与调用约定、复合数据）、L3-02 CO-3（IEEE-754 浮点）｜ 一手锚点：CSAPP 3e (2016) §3.10–3.11、Intel SDM Vol.1（AVX/SSE 标量浮点）、Intel CET 规范（endbr64/影子栈）、System V AMD64 ABI（浮点参数走 XMM）、GCC 13 手册、Linux 内核 ASLR 文档｜ 实证环境：Linux 6.18.5 x86-64，gcc 13.3.0 / binutils objdump 2.42 / gdb 15.1｜ 成熟度：浮点/SSE/AVX 为 GA；CET（IBT+影子栈）⚙演进快·硬件与内核支持仍在铺开，见 AS-7.6。

> 粒度判定：AS-7 共 6 个小主题（AS-7.1~AS-7.6），横跨浮点（AVX 标量运算）与安全（溢出/金丝雀/PIE/CET）两个异质簇。评估后判为 **1 份**：两簇虽主题不同，但在本课语境下共享同一条"机器级可观测面"主线——都通过 `gcc -S`/`objdump -d`/多次运行同一套工具坐实，且安全簇（AS-7.3~7.6）本身是一条连贯叙事（溢出攻击面 → 金丝雀 → ASLR → CET 层层加固），拆开反而割裂"攻—防递进"。单份篇幅可控，故不拆 -a/-b。

本报告所有汇编片段、字节编码与运行地址均来自本机实测；命令、源码与真实输出随节给出，脚本仅存 scratchpad 未入库。AT&T 语法为本课主语法（操作数顺序为 `源, 目的`）。安全小节仅作机制教学，任何复现只在隔离环境、不给可武器化步骤。

---

## AS-7.1 XMM/YMM 与标量浮点

### 7.1.1 XMM / YMM 寄存器组

x86-64 的浮点运算不使用早期的 x87 栈式协处理器，而是走 **SSE/AVX 的向量寄存器**。这组寄存器名为 `%xmm0`–`%xmm15`（SSE，各 128 位）；AVX 把它们的宽度扩展到 256 位，改名 `%ymm0`–`%ymm15`，其低 128 位就是对应的 `%xmm`。所谓"标量浮点"是指：虽然寄存器很宽，但一个单精度或双精度浮点数只占用最低的 32 位或 64 位，其余位在标量运算中被忽略。

```
%ymm0 (256 位, AVX)
└─ 低 128 位 = %xmm0 (SSE)
   └─ 低 64 位 = 一个 double
      └─ 低 32 位 = 一个 float
```

初学者要建立的核心直觉是"同一块物理寄存器、多种视角"。整数走 `%rax`/`%eax` 这套通用寄存器，浮点则完全另起一套 `%xmm`；两套互不重叠。为什么浮点要独立一套？因为浮点运算单元和整数单元在硬件上是分开的流水线，用独立寄存器组避免相互抢占，也便于同一条指令并行处理多个打包数据（SIMD）。本大主题只讲"标量"用法（一次一个数），SIMD 打包运算留到 L5-05 AA-5。

### 7.1.2 浮点参数与返回值走 XMM（System V ABI）

按 System V AMD64 ABI，浮点参数不走整型参数寄存器 `%rdi`/`%rsi`…，而是走 `%xmm0`–`%xmm7`（前 8 个浮点参数），浮点返回值放在 `%xmm0`。这与整型的 `%rdi`…6 参数、`%rax` 返回值是两套平行的约定。

下面 `dadd(a,b)` 中 `a` 在 `%xmm0`、`b` 在 `%xmm1`，结果留在 `%xmm0`：

```
double dadd(double a, double b){ return a + b; }
```

```
$ gcc -O1 -S -fno-asynchronous-unwind-tables fp.c
dadd:
	endbr64
	addsd	%xmm1, %xmm0      # xmm0 = a + b（结果即返回值）
	ret
```

理解这一点能解释很多"混合参数"的排布：一个 `f(int n, double x, int m, double y)`，整型 `n`/`m` 依次占 `%rdi`/`%rsi`，浮点 `x`/`y` 依次占 `%xmm0`/`%xmm1`——两条队列**各自独立计数**，不会因为中间夹了浮点就跳号。这也是为什么可变参数函数（如 `printf`）调用前 `%al` 要置成"用到的向量寄存器个数"，好让被调方知道扫描几个 `%xmm`。

### 7.1.3 标量传送指令 movss / movsd

浮点数在寄存器与内存之间搬运用专门的传送指令，而不是整数的 `mov`。`movss`（move scalar single）搬 32 位单精度，`movsd`（move scalar double）搬 64 位双精度；寄存器之间对齐搬运也可用 `movaps`/`movapd`（aligned packed）。

一个浮点常量通常被编译器放进 `.rodata` 只读段，再用 RIP 相对寻址加载：

```
double pi(void){ return 3.14159; }
```

```
$ gcc -O1 -S -fno-asynchronous-unwind-tables mv.c
	movsd	.LC0(%rip), %xmm0      # 从只读段把常量装进 xmm0
	ret
...
.LC0:
	.long	-266631570             # 低 32 位
	.long	1074340345             # 高 32 位（合起来是 3.14159 的 IEEE-754 双精度位模式）
```

初学者两个易错点。其一，`movsd` 这个助记符在整数世界另有同名指令（`movsd` = move string doubleword，字符串搬运），但操作数带 `%xmm` 时汇编器识别为标量双精度浮点传送，二者靠操作数区分，不会真的混淆。其二，常量 `3.14159` 在 `.rodata` 里是以**小端**存的两个 32 位字（低位字在前），这与大主题6 的字节序结论一致——把两个 `.long` 拼起来才是完整的 64 位双精度位模式，不能单独解读任一半。

#### 来源与时效

- CSAPP 3e (2016) §3.11 与 §3.11.1（浮点体系结构、XMM 寄存器、标量传送 movss/movsd）。核实 2026-07-26。
- Intel SDM Vol.1 ch.10–11（SSE/AVX 寄存器 XMM/YMM 组织、标量与打包数据）。核实 2026-07-26。
- System V AMD64 ABI（浮点参数 %xmm0–%xmm7、返回值 %xmm0、%al 记录向量寄存器数的可变参数约定）。核实 2026-07-26。
- 本机实证：gcc 13.3.0 `-O1 -S`（`addsd`/`movsd .LC0(%rip)`）见 7.1.2、7.1.3。
- 冲突项：无。x87 与 SSE/AVX 属不同浮点子系统，本课 x86-64 默认走 SSE/AVX，CSAPP 与 SDM 一致。

## AS-7.2 浮点运算指令

### 7.2.1 算术：加乘等标量运算（addsd/mulss…）

标量浮点算术指令用后缀区分精度：`s` = single（单精度 float），`d` = double（双精度 double）；`ss` = scalar single，`sd` = scalar double。于是常见四则为 `addss/addsd`、`subss/subsd`、`mulss/mulsd`、`divss/divsd`，还有 `sqrtss/sqrtsd`、`maxsd`/`minsd` 等。

```
float  fmul(float a, float b){ return a * b; }
```

```
$ gcc -O1 -S -fno-asynchronous-unwind-tables fp.c
fmul:
	endbr64
	mulss	%xmm1, %xmm0      # 单精度乘：xmm0 = a * b
	ret
```

记忆法：后缀读作"scalar + 精度"。`mulss` = 标量单精度乘，`addsd` = 标量双精度加。去掉那个 `s`（scalar）变成 `mulps`/`addpd` 就是**打包**（packed）版本，一条指令同时算多个数——那是 SIMD，本节不展开。初学者最容易把 `ss`/`sd` 记反：认准第二个字母，`s`ingle 对 float、`d`ouble 对 double。

### 7.2.2 转换：整浮互转与精度互转（cvt 系列）

不同数值格式之间转换用 `cvt`（convert）系列，命名规律是 `cvt{源}2{目的}`，`2` 读作 "to"：

```
cvtsi2sd   有符号整数(signed int) → 双精度(scalar double)
cvtsi2ss   有符号整数            → 单精度
cvttsd2si  双精度 → 整数，t = 截断(truncate)向零舍入
cvtsd2ss   双精度 → 单精度（降精度）
cvtss2sd   单精度 → 双精度（升精度）
```

```
double i2d(long x){ return (double)x; }
long   d2i(double x){ return (long)x; }
```

```
$ gcc -O1 -S -fno-asynchronous-unwind-tables fp.c
i2d:
	pxor	%xmm0, %xmm0          # 先清零目标，避免与旧高位假依赖
	cvtsi2sdq	%rdi, %xmm0   # 整数 x(%rdi) → double
	ret
d2i:
	cvttsd2siq	%xmm0, %rax   # double → long，截断(向零)
	ret
```

关键辨析在那个额外的 `t`：`cvttsd2si` 里的双 `t` 表示 **truncation（截断，向零舍入）**，这正是 C 语言 `(int)3.9 == 3` 的语义——C 强制类型转换浮点转整数总是向零截断，所以编译器选带 `t` 的版本；不带 `t` 的 `cvtsd2si` 则按当前 MXCSR 舍入模式（默认就近舍入）。指令尾部的 `q`（如 `cvtsi2sdq`）是操作数大小后缀，表示整数一侧是 64 位。开头那句 `pxor %xmm0,%xmm0` 先把目标寄存器清零，是为了消除对该寄存器旧值的"假依赖"，属编译器的性能习惯，不影响数值结果。

### 7.2.3 比较：comisd 与 ucomisd，以及 NaN 的处理

浮点比较不用整数的 `cmp`，而用 `comiss`/`comisd`（ordered compare）或 `ucomiss`/`ucomisd`（unordered compare）。它们把比较结果写进整型条件码 ZF/PF/CF（注意不是 SF/OF），随后用 `seta`/`setae`/`setb` 等**无符号**条件的 setCC 读取——因为浮点比较结果被映射到了无符号风格的标志组合。

```
int dcmp(double a, double b){ return a > b; }   // 用 comisd
int deq (double a, double b){ return a == b; }   // 用 ucomisd
```

```
$ gcc -O1 -S -fno-asynchronous-unwind-tables
dcmp:
	comisd	%xmm1, %xmm0      # 有序比较 a ? b
	seta	%al               # a > b 为无符号"above"
	movzbl	%al, %eax
	ret
deq:
	ucomisd	%xmm1, %xmm0      # 无序比较（== 用）
	setnp	%al               # PF=0（有序，非 NaN）才可能相等
	movl	$0, %edx
	cmovne	%edx, %eax        # 若 ZF=0（不等）则清零
	ret
```

必须理解的浮点特性是 **NaN（Not a Number）导致"无序"**：只要参与比较的任一方是 NaN，比较结果既非大于也非小于也非等于，硬件用 **PF（Parity Flag）置 1** 表示这种"无序"结果。这就是 `deq` 里 `setnp`（set if not parity）的由来——`a == b` 必须同时满足"有序（PF=0，无 NaN）"且"相等（ZF=1）"，因为按 IEEE-754，`NaN == NaN` 永远为假。`comisd` 与 `ucomisd` 的唯一区别在于对信号型 NaN（sNaN）是否触发浮点异常：`comisd` 对 sNaN 报异常，`ucomisd` 只对更"安静"的 NaN 不报——但两者对条件码的设置规则相同，普通代码里编译器据 C 表达式（`>` 用 comisd、`==` 用 ucomisd）自动选取。

### 7.2.4 VEX 编码：AVX 的三操作数形式（vaddsd/vcvt…）

在支持 AVX 的机器上（本机有），同一批浮点指令可用 **VEX 前缀编码**，助记符前加 `v`，且从 SSE 的两操作数变成**三操作数**（两个源、一个独立目的），避免破坏源寄存器：

```
$ gcc -O1 -mavx -S -fno-asynchronous-unwind-tables fp.c
	vaddsd	%xmm1, %xmm0, %xmm0    # xmm0 = xmm0 + xmm1（三操作数）
	vmulss	%xmm1, %xmm0, %xmm0
	vcvtsi2sdq	%rdi, %xmm0, %xmm0
	vcvttsd2siq	%xmm0, %rax
	vcomisd	%xmm1, %xmm0
```

初学者对照 7.2.1 会发现：SSE 版 `addsd %xmm1,%xmm0` 是"目的 += 源"（结果覆盖一个源），而 AVX 版 `vaddsd %xmm1,%xmm0,%xmm0` 显式写出目的，即使这里目的恰好还是 `%xmm0`，它也**允许目的不同于两个源**，从而少一次为保存源而做的额外 `mov`。这就是为什么现代编译器在 `-mavx`/`-march=native` 下偏好 VEX 版本。是否默认生成 `v` 前缀取决于目标架构：本机不加 `-mavx` 时 gcc 默认发 SSE 形式（`addsd`），加 `-mavx` 才发 VEX 形式（`vaddsd`）——两者数值语义相同，仅编码与操作数数目不同。

#### 来源与时效

- CSAPP 3e (2016) §3.11.2–§3.11.5（浮点传送/转换、算术运算、比较 comisd/ucomisd、条件码映射、NaN 无序与 PF）。核实 2026-07-26。
- Intel SDM Vol.1（SSE/AVX 标量指令 ADDSD/MULSS/CVTSI2SD/CVTTSD2SI/COMISD/UCOMISD 语义、MXCSR 舍入模式）、Vol.2（指令逐条编码、VEX 三操作数形式）。核实 2026-07-26。
- 本机实证：gcc 13.3.0 `-O1 -S`（SSE 形式）与 `-O1 -mavx -S`（VEX 形式）对照，见 7.2.1–7.2.4。
- 冲突项：无语义冲突。SSE（两操作数）与 AVX（三操作数）为编码差异，数值结果一致；截断/舍入差异由是否带 `t`、由 MXCSR 决定，CSAPP 与 SDM 表述一致。

## AS-7.3 缓冲区溢出

### 7.3.1 攻击面：栈上局部缓冲区与返回地址同处一帧

栈溢出的根源是 x86-64 的一个结构性事实：函数的**局部数组缓冲区**与该函数的**保存返回地址**位于同一个栈帧内，且缓冲区向高地址写入的方向，正指向返回地址所在的位置。当程序把超过缓冲区容量的数据写进去（例如用不做长度检查的 `strcpy`/`gets`），多出的字节就会沿栈"向上"覆盖相邻的栈内容，最终可能盖掉 `call` 压入的返回地址。

一个典型的易受影响帧的布局（概念示意，高地址在上）：

```
高地址  ┌────────────────────┐
        │  调用者压入的返回地址 │  ← ret 会跳到这里存的地址
        ├────────────────────┤
        │  saved %rbp（若有） │
        ├────────────────────┤
        │  char buf[16]       │  ← strcpy 从这里开始，向高地址写
低地址  └────────────────────┘  ← %rsp
```

初学者要抓住的因果链是：`ret` 指令的行为是"从栈顶弹出一个地址并跳转过去"（见大主题5 的 call/ret 机制）。如果攻击输入把返回地址那 8 个字节改写成别的值，函数返回时就会跳到攻击者指定的位置，从而劫持控制流。危险的本质不是"写多了"，而是"被写坏的恰好是决定下一步执行到哪里的返回地址"。C 语言不自带边界检查，是否越界完全由程序员保证，这就是溢出得以存在的土壤。

### 7.3.2 无边界检查的库函数与 FORTIFY 缓解

制造溢出的经典途径是使用**不接收目标长度**的库函数：`gets`（已从 C11 标准移除）、`strcpy`、`strcat`、`sprintf` 等——它们只按源数据的终止符或格式决定写多少，完全不管目标缓冲区多大。对应的安全替代是带长度上限的 `fgets`、`strncpy`/`snprintf` 等。

现代 gcc 在开启优化并启用 `_FORTIFY_SOURCE` 时，会把可推断目标大小的调用替换成带检查的 `__*_chk` 版本。本机 `-O1` 下 `strcpy(buf,src)` 就被换成了 `__strcpy_chk`，第三个参数 `$16` 正是 `buf` 的编译期已知大小：

```
$ gcc -O1 -fstack-protector-strong -S -fno-asynchronous-unwind-tables can.c
	movl	$16, %edx            # 传入目标缓冲区大小 16
	call	__strcpy_chk@PLT      # 运行时若将越界则中止
```

要理解这层缓解的边界：`__strcpy_chk` 只在编译器**能静态推断**出目标对象大小时才有保护作用；若缓冲区大小运行时才定、或通过指针间接传入而无法推断，`__*_chk` 会退化为普通 `strcpy` 从而失去检查。所以 FORTIFY 是"尽力而为"的补充，真正的第一道防线仍是写代码时就用带长度限制的接口。以下小主题（金丝雀、ASLR、CET）都是在"万一还是溢出了"的前提下加固的纵深防御层。

#### 来源与时效

- CSAPP 3e (2016) §3.10.3（越界内存引用与缓冲区溢出、返回地址覆写机制、gets/strcpy 的危险）。核实 2026-07-26。
- GCC 手册 `_FORTIFY_SOURCE` 与 glibc `__strcpy_chk`/`__*_chk` 族文档（可推断大小时的运行时检查、退化条件）。核实 2026-07-26。
- ISO C11（`gets` 已从标准库移除，佐证无界接口的淘汰）。核实 2026-07-26。
- 本机实证：gcc 13.3.0 `-O1 -fstack-protector-strong -S`，观察 `strcpy` 被替换为 `__strcpy_chk@PLT`（三参含大小 16），见 7.3.2。栈溢出机制仅作教学，未做任何可武器化复现。
- 冲突项：无。CSAPP 讲攻击面与原理，GCC/glibc 文档讲缓解实现，二者互补不矛盾。

## AS-7.4 栈金丝雀

### 7.4.1 canary 的布置与校验机制

栈金丝雀（stack canary，源自"矿井里的金丝雀"预警比喻）是编译器在栈帧里**局部缓冲区与返回地址之间**插入的一个哨兵值：进入函数时写入，返回前校验；若被溢出覆盖，说明缓冲区已越界写到了返回地址一侧，程序立即中止，从而在返回地址被利用前拦截。GCC 用 `-fstack-protector`/`-fstack-protector-strong`/`-fstack-protector-all` 控制插入范围。

本机实测 `vuln` 函数的序言与尾声完整展示了这一机制：

```
$ gcc -O1 -fstack-protector-strong -S -fno-asynchronous-unwind-tables can.c
vuln:
	endbr64
	subq	$40, %rsp
	movq	%fs:40, %rax        # 从 TLS 取金丝雀原值
	movq	%rax, 24(%rsp)      # 布置到栈上（缓冲区与返回地址之间）
	xorl	%eax, %eax          # 清掉寄存器里的金丝雀副本，防泄漏
	...                         # 函数主体（含 strcpy）
	movq	24(%rsp), %rax      # 取回栈上的金丝雀
	subq	%fs:40, %rax        # 与原值比较（相等则差为 0）
	jne	.L4                   # 不等 → 被改写 → 跳异常处理
	addq	$40, %rsp
	ret
.L4:
	call	__stack_chk_fail@PLT  # 中止程序
```

初学者要抓住"三步节奏"：序言从线程局部存储取值放上栈，尾声取回并做减法比对，`jne` 决定是正常 `ret` 还是去 `__stack_chk_fail`。关键点在于金丝雀被放在**比缓冲区更靠近返回地址**的位置——顺序写溢出必须先经过金丝雀才能触及返回地址，于是覆盖返回地址的同时必然破坏金丝雀，校验就能发现。

### 7.4.2 金丝雀来自 %fs:40 的线程局部存储

金丝雀值不是编译期写死的常量，而是运行时从 **`%fs:40`** 读取——`%fs` 段寄存器指向线程控制块（TLS），偏移 40（十进制）处存着本线程的金丝雀，由加载器在启动时用随机值初始化。这保证了金丝雀值攻击者无法预知、且每次运行/每线程不同。

理解 `%fs:40` 的意义能澄清两个常见疑问。其一，为什么攻击者不能"连金丝雀一起写对"？因为它是随机且不驻留在可读代码里的秘密值，纯顺序溢出无从得知该填什么。其二，`xorl %eax,%eax` 那句为何紧跟布置之后？那是把刚用过的、还残留在 `%eax`（实为 `%rax`）里的金丝雀副本清零，避免金丝雀通过寄存器意外泄漏。校验时的 `subq %fs:40,%rax` 用减法而非 `cmp`，效果一样（等值相减得 0，设 ZF），随后 `jne` 据此分流。

### 7.4.3 __stack_chk_fail：检测到篡改后的中止

一旦校验发现金丝雀被改写，控制转入 `__stack_chk_fail`（glibc 提供）。该函数不返回，它打印类似 `*** stack smashing detected ***` 的诊断并调用 `abort()` 终止进程，从而把"控制流已可能被劫持"的风险转化为一次可控的崩溃。

要建立的正确预期是：金丝雀是**检测**而非**阻止**溢出——溢出的写动作已经发生（缓冲区已被写坏），金丝雀只是在函数返回、真正跳转到被污染的返回地址**之前**这一刻拦截。因此它对"顺序覆盖返回地址"这类最经典的栈溢出很有效，但对不经过金丝雀的攻击（如直接改写函数指针、或利用格式化字符串任意地址写）就不一定覆盖得到。这也是为什么它要和 ASLR、CET 等其他机制叠加，构成纵深防御。

#### 来源与时效

- CSAPP 3e (2016) §3.10.4"Thwarting Buffer Overflow Attacks"之 Stack Corruption Detection（金丝雀布置/校验、位于缓冲区与返回地址之间）。核实 2026-07-26。
- GCC 13 手册 `-fstack-protector`/`-fstack-protector-strong`/`-fstack-protector-all`（插入策略）与 glibc `__stack_chk_fail`/`__stack_chk_guard` 文档（%fs TLS 金丝雀、检测后 abort）。核实 2026-07-26。
- 本机实证：gcc 13.3.0 `-O1 -fstack-protector-strong -S`，观察 `%fs:40` 取值、`24(%rsp)` 布置、尾声 `subq %fs:40,%rax`+`jne`+`__stack_chk_fail@PLT`，见 7.4.1。
- 冲突项：无。CSAPP 讲机制原理，GCC/glibc 文档讲具体实现（`%fs:40`、选项名），一致互补。

## AS-7.5 PIE / ASLR

### 7.5.1 PIE：位置无关可执行文件

PIE（Position-Independent Executable，位置无关可执行文件）是一种能被加载到任意基地址仍正确运行的可执行文件。它通过 RIP 相对寻址访问自己的代码与数据（见大主题2 的 `sym(%rip)`），因此代码里不含写死的绝对地址，加载器可以把整个映像放到内存任意位置。现代 gcc 默认就生成 PIE。

本机确认默认开启 PIE，产物 ELF 类型是"pie executable"（本质是可执行的共享对象）：

```
$ gcc -v 2>&1 | grep default-pie
... --enable-default-pie ...
$ gcc -O0 -o hi hi.c && file hi
hi: ELF 64-bit LSB pie executable ...
$ gcc -O0 -no-pie -fno-pie -o hi_nopie hi.c && file hi_nopie
hi_nopie: ELF 64-bit LSB executable ...      # 传统固定地址可执行
```

初学者要区分"位置无关"是**能力**，"地址随机化"是**行为**：PIE 只是让映像**可以**被放到任意地址（编译/链接期属性），至于实际是否每次换地方，取决于运行时的 ASLR（见 7.5.2）。二者常一起出现，但概念上分属编译期与运行期。`-no-pie` 会退回传统可执行文件，代码段被固定到约定地址（本机 `0x400000` 起），从而不受随机化保护。

### 7.5.2 ASLR：地址空间布局随机化

ASLR（Address Space Layout Randomization，地址空间布局随机化）是**内核**在每次加载程序时，把栈、堆、共享库、以及 PIE 主映像的基地址随机偏移的机制。目的是让攻击者无法预知"跳到哪个地址能命中想要的代码/数据"，从而削弱依赖固定地址的攻击（如返回到某个已知函数）。

本机 `randomize_va_space=2`（完全随机化），同一 PIE 程序多次运行，`main` 的地址每次不同：

```
$ cat /proc/sys/kernel/randomize_va_space
2
$ ./hi; ./hi; ./hi
main=0x55fa0789b149
main=0x55871f9df149
main=0x560f206d6149
```

对照非 PIE 版本，地址每次完全相同：

```
$ ./hi_nopie; ./hi_nopie
main=0x401136
main=0x401136
```

有两个初学者必须注意的细节。其一，随机化的是**基地址**，映像内部的相对布局不变——观察上面 PIE 三次运行地址的**低 12 位恒为 `149`**（`main` 在页内的偏移），变化的只是高位的页基址，这正因为随机化以页（4 KiB）为粒度。其二，ASLR 是内核开关（`/proc/sys/kernel/randomize_va_space`：0 关、1 部分、2 完全），而 PIE 是文件属性；只有"PIE 文件 + 内核开 ASLR"两者齐备，主程序代码段才真正随机化。`-no-pie` 程序即便内核开 ASLR，其主映像仍固定（只有栈/堆/库会随机），这解释了 `hi_nopie` 地址不变的现象。

#### 来源与时效

- CSAPP 3e (2016) §3.10.4"Thwarting Buffer Overflow Attacks"之 Stack Randomization / Address-Space Layout Randomization（随机化削弱固定地址攻击）。核实 2026-07-26。
- GCC 13 手册 `-pie`/`-fpie`/`-no-pie`、`--enable-default-pie` 配置项；Linux 内核文档 `/proc/sys/kernel/randomize_va_space`（值 0/1/2 语义）。核实 2026-07-26。
- System V AMD64 ABI（PIE 依赖的 RIP 相对寻址、GOT/PLT）。核实 2026-07-26。
- 本机实证：gcc 13.3.0 默认 `--enable-default-pie`；`file` 确认 pie executable；PIE 版 `main` 地址三次运行随机（低 12 位恒为 149），`-no-pie` 版恒为 `0x401136`；`randomize_va_space=2`，见 7.5.1–7.5.2。
- 冲突项：无。PIE（编译/链接期）与 ASLR（运行期内核）职责分账清晰，CSAPP、GCC、内核文档一致。

## AS-7.6 CET / endbr64

### 7.6.1 CET 概览：影子栈与间接分支跟踪（⚙演进快）

CET（Control-flow Enforcement Technology，控制流强制技术）是 Intel 提出的硬件级控制流完整性机制，含两部分：**影子栈（Shadow Stack, SHSTK）**——硬件维护一份只读的返回地址副本，`ret` 时比对普通栈与影子栈的返回地址，不一致即触发异常，专门对抗返回地址被覆写（正是 AS-7.3/7.4 那类攻击）；**间接分支跟踪（Indirect Branch Tracking, IBT）**——要求所有间接跳转/调用（`jmp *`/`call *`）的目标必须是一条 `endbr64` 指令，否则报控制保护异常（#CP），把可被劫持的落点限制到编译器显式标记处。

需要硬标状态：CET 是**演进较快**的前沿安全特性。硬件支持见于较新的 Intel（Tiger Lake 起）与 AMD（Zen 3 起）处理器；软件侧需内核、glibc、编译器协同。是否在某台机器上真正**生效**取决于 CPU、内核与 libc 三方是否都启用——编译器插入 `endbr64` 与在 ELF 打上 CET 标记只是"准备就绪"，不等于运行时强制已激活。本机能观察到编译器侧的标记与指令（见 7.6.3），但运行时是否由硬件强制未在本报告坐实，如实标"未验证运行时强制"。

### 7.6.2 与 CSAPP 的节号关系（待核项已厘清）

本课 prompt 将 CET/endbr64 暂挂在"§3.10.4"并标【待核 具体节号】。核对后厘清：CSAPP 3e 出版于 2016 年，早于 CET 的落地，**§3.10.4 的实际标题是"Thwarting Buffer Overflow Attacks"，讲的是栈随机化（ASLR）、栈破坏检测（金丝雀）与限制可执行代码区域三项，并不包含 CET/endbr64**。因此 CET/endbr64 不属于 CSAPP 3e 的内容，其权威来源应是 Intel CET 规范与 GCC/glibc 文档，而非 CSAPP 某节。

这一厘清对初学者的意义是"分账"：AS-7.4（金丝雀）、AS-7.5（ASLR）确实对应 CSAPP §3.10.4 的两个子节，是教材内容；而 AS-7.6（CET）是教材之后出现的硬件机制，属"规范/实现"来源。把它们混在同一节号下会造成"教材里能查到 endbr64"的错误预期——实际查不到。故本报告对 CET 单独按一手规范锚定，节号问题按此结案。

### 7.6.3 endbr64：间接分支的合法落点标记

`endbr64`（End Branch 64-bit）是 IBT 机制里的"合法落点"标记指令。在开启 CET-IBT 的代码中，每个可能成为间接跳转/调用目标的位置（函数入口、跳转表目标等）都会被编译器插入一条 `endbr64`；若某条间接分支跳到的不是 `endbr64`，硬件即认定控制流被劫持并报 #CP 异常。

本机实测其固定 4 字节编码，且默认已插入到函数入口：

```
$ gcc -O0 -o hi hi.c
$ objdump -d hi | sed -n '/<main>:/,+2p'
0000000000001149 <main>:
    1149:	f3 0f 1e fa          	endbr64
    114d:	55                   	push   %rbp
```

endbr64 的机器编码固定为：

```
f3 0f 1e fa
```

两个关键点帮助理解。其一，`endbr64` 被巧妙设计成在**不支持 CET 的老 CPU 上是一条 NOP（空操作）**——前缀 `f3` 使旧处理器把它当作无害指令，于是同一个二进制既能在新硬件上受 IBT 保护、又能在老硬件上正常跑，向后兼容。这也解释了为何前几个大主题的反汇编里到处可见 `endbr64` 却"什么都不做"：在未激活 IBT 时它确实等价于空操作。其二，本机 gcc 13 默认 `-fcf-protection=full`（同时管 IBT 分支与影子栈返回），`readelf -n` 能看到 ELF 的 CET 属性标记：

```
$ readelf -n hi | grep feature
      Properties: x86 feature: IBT, SHSTK
```

这条 `.note.gnu.property` 告诉加载器"本映像已为 IBT+SHSTK 准备好"。再次强调 7.6.1 的界限：编译器插了 `endbr64`、ELF 打了 IBT/SHSTK 标记，只代表**软件侧就绪**；能否真正被硬件强制，取决于运行 CPU 与内核是否启用 CET，这一层未在本报告实测坐实。

#### 来源与时效

- Intel CET 规范 / Intel SDM Vol.1（CET 章）：影子栈 SHSTK、间接分支跟踪 IBT、ENDBR64 语义与在旧 CPU 上退化为 NOP、控制保护异常 #CP。核实 2026-07-26。⚙演进快·硬件支持 Intel Tiger Lake+ / AMD Zen 3+，运行时强制需 CPU+内核+glibc 协同。
- GCC 13 手册 `-fcf-protection=[full|branch|return|none]`（默认 full）；ELF `.note.gnu.property` 的 x86 feature IBT/SHSTK 标记（binutils/ABI 扩展）。核实 2026-07-26。
- CSAPP 3e (2016) §3.10.4：经核对**不含** CET/endbr64（该节为 ASLR/金丝雀/限制可执行区），据此结清 prompt 的【待核 §3.10.4 节号】——CET 不归此节，改锚 Intel 规范。核实 2026-07-26。
- 本机实证：gcc 13.3.0 默认插入 `endbr64`（编码 `f3 0f 1e fa`），`readelf -n` 显示 `x86 feature: IBT, SHSTK`，`-fcf-protection` 默认为 full；见 7.6.3。运行时硬件强制**未验证**（本机 CPU/内核 CET 激活状态未坐实）。
- 冲突项：节号归属分歧已厘清（CET ∉ CSAPP §3.10.4）；"编译期就绪 vs 运行时强制"须按规范与实现分账，不可由 ELF 标记推断运行时已强制。
