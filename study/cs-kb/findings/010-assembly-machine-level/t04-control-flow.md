# L3-03·大主题4 控制流

> 基线/核实：2026-07-26（基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25）｜ 先修：L3-03 大主题1~3（机器级基础、寄存器与寻址、算术逻辑指令）、L3-02 CO-5（分支与流水线）｜ 一手锚点：CSAPP 3e (2016) §3.6、Intel SDM Vol.2（CMP/TEST/SETcc/Jcc/JMP/CMOVcc）、Stanford CS107（Control Flow）｜ 实证环境：Linux 6.18.5 x86-64，gcc 13.3.0 / binutils objdump 2.42 / gdb 15.1｜ 成熟度：GA（x86-64 控制流指令集稳定；注 endbr64/notrack 为 CET 相关前缀，见下文说明）

> 粒度判定：AS-4 共 6 个小主题（AS-4.1~AS-4.6），含 if/循环/switch/cmov 多种编译模式与若干反汇编片段。评估后判为 **1 份**：六个小主题共享同一条主线（条件码→读条件码→用条件码选择控制流），拆分会割裂"设标志—读标志—跳转/传送"这一连续叙事，且单份篇幅可控，故不拆 -a/-b。

本报告所有汇编片段来自本机实测。为便于阅读，示例主要用 `gcc -Og`（轻优化、结构清晰）与 `gcc -O0/-O1/-O2` 对照；命令、源码与真实输出随每节给出，脚本仅存 scratchpad 未入库。AT&T 语法为本课主语法（操作数顺序为 `源, 目的`）。

---

## AS-4.1 条件码读写

### 4.1.1 条件码寄存器 CF / ZF / SF / OF

CPU 在 RFLAGS 寄存器里维护一组单比特的"条件码"（condition codes / status flags），记录最近一条算术或逻辑指令产生的副效应。控制流指令不直接比较数值，而是先由某条指令设置这些标志，再由跳转/传送/置位指令读取它们。四个与整数控制流最相关的标志是：

```
CF (Carry Flag)    进位标志：无符号运算最高位向外产生进位/借位
ZF (Zero Flag)     零标志：  运算结果为 0
SF (Sign Flag)     符号标志：运算结果最高位为 1（按补码视为负）
OF (Overflow Flag) 溢出标志：有符号补码运算发生溢出
```

初学者最容易混淆的一点是"进位 CF"与"溢出 OF"的分工：CF 服务于**无符号**解释，OF 服务于**有符号**解释。同一条 `add` 指令会同时算好这两个标志，具体用哪个由后续读标志的指令（也就是编译器根据 C 类型是否有符号来选）决定。比如判断 `unsigned a > b` 会用到 CF，判断 `int a > b` 会用到 SF 与 OF 的组合，硬件把两种可能都提前备好，取用时零成本。

另一个关键直觉：条件码是"隐式的、易失的"。它没有名字不能像 `%rax` 那样直接读，只能通过 `setCC`/`jCC`/`cmovCC` 间接使用；而且几乎每条算术逻辑指令都会覆盖它，所以"设标志"和"用标志"之间通常紧挨着，中间不能插入会破坏标志的指令。注意 `lea` 与 `mov` **不改条件码**（这也是编译器爱用 `lea` 做算术的原因之一，见大主题3）。

### 4.1.2 cmp 设标志（比较）

`cmp` 指令执行"目的 − 源"的减法，但**只设置条件码、丢弃差值**，专门用于比较两个数而不破坏任何寄存器。AT&T 语法下 `cmpq %rsi, %rdi` 计算的是 `%rdi − %rsi`（源在前、目的在后），这一操作数顺序是初学者读汇编的头号陷阱。

下面的 C 函数 `a > b` 编译后清楚展示 cmp 的用法：

```
long gt(long a, long b){ return a > b; }
```

```
$ gcc -Og -S -fno-asynchronous-unwind-tables cc.c
gt:
	endbr64
	cmpq	%rsi, %rdi      # 计算 a - b，设 ZF/SF/OF/CF，不保存差
	setg	%al            # 若 (SF^OF)==0 且 ZF==0，即 a>b（有符号），置 al=1
	movzbl	%al, %eax
	ret
```

要牢记 `cmpq %rsi, %rdi` 语义是 `a − b`：参数 `a` 在 `%rdi`、`b` 在 `%rsi`（System V 前两个整型参数），源操作数 `%rsi` 是被减数还是减数很容易记反。一个可靠的记忆法是把它读成"拿目的去减源"，即 `dest − src`；后面 `jg`/`setg` 判断的"大于"永远是指 `dest > src`。cmp 相当于"做一次减法给 CPU 看，但不告诉任何人结果是多少"。

### 4.1.3 test 设标志（按位与测试）

`test` 指令执行"目的 AND 源"的按位与，同样**只设条件码、丢弃结果**。它最常见的用法是把一个寄存器和自己相与来判断是否为零或为负，因为 `x & x == x`，于是 ZF 反映 `x==0`、SF 反映 `x<0`。

```
long iszero(long a){ return a == 0; }
```

```
iszero:
	endbr64
	testq	%rdi, %rdi      # a & a，结果影响 ZF：a==0 时 ZF=1
	sete	%al            # ZF==1 则置 1
	movzbl	%al, %eax
	ret
```

为什么判零用 `testq %rdi,%rdi` 而不是 `cmpq $0,%rdi`？两者效果等价，但 `test` 版本编码更短、也不需要一个立即数，是编译器和手写汇编的惯用法。逻辑与运算永远使 CF 与 OF 清零（因为按位与不可能产生进位或补码溢出），所以 `test` 后只有 ZF/SF/PF 有意义——这是它与 `cmp` 的一个实质区别：`cmp` 之后 CF/OF 有效，`test` 之后 CF/OF 恒为 0，因此 `test` 只能用于"等于零/非零/符号"这类判断，不能用于无符号大小比较。

### 4.1.4 setCC 读标志（按条件置字节）

`setCC` 系列（`sete`、`setg`、`setl`、`setb`……）读取条件码，把某个条件是否成立写成一个 0/1 字节，存进一个**8 位寄存器**（如 `%al`）。它是把"比较结果"落地成布尔值的标准手段，因此上面两例末尾都跟着 `movzbl %al, %eax` 把这个字节零扩展到 32/64 位返回。

`setCC` 只写 1 字节这一点是初学者的高频坑：`sete %al` 只改 `%al`，`%eax` 的高位不会自动清零，所以编译器几乎总是配一条 `movzbl %al,%eax`（零扩展）把整个返回寄存器补齐。后缀（`e/ne/g/ge/l/le/a/ae/b/be/s/z`…）对应不同条件，且**有符号与无符号用不同的后缀家族**：

```
有符号比较：g(>) ge(>=) l(<) le(<=)   ← 读 SF、OF、ZF 组合
无符号比较：a(>) ae(>=) b(<) be(<=)   ← 读 CF、ZF
相等/为零： e/z(==0)  ne/nz(!=0)       ← 读 ZF
```

同一族条件由 `jCC`（跳转）与 `cmovCC`（条件传送）共用后缀（见 4.2、4.6），学会一次即可通吃三类指令。为什么 `>` 有 `g` 和 `a` 两个版本？因为"大于"对有符号数看的是 `(SF^OF)==0 && ZF==0`，对无符号数看的是 `CF==0 && ZF==0`，硬件条件不同，编译器按 C 操作数的类型选后缀——这正是 4.1.1 中"CF 管无符号、OF 管有符号"的落地。

#### 来源与时效

- CSAPP 3e (2016) §3.6.1 条件码、§3.6.2 访问条件码（cmp/test/setCC，setg 的 `(SF^OF)&~ZF` 条件、`test x,x` 惯用法）。核实 2026-07-26。
- Intel SDM Vol.2：CMP、TEST（"CF and OF flags are set to 0"）、SETcc（"sets the destination byte"）指令条目。核实 2026-07-26。
- 本机实证：gcc 13.3.0 `-Og -S`，见 4.1.2/4.1.3 真实输出。
- 冲突项：无实质冲突。CSAPP 与 Intel SDM 对 cmp（`dest−src` 顺序为 AT&T 惯例，Intel 语法操作数顺序相反但语义同）一致；AT&T vs Intel 仅记法差异，非语义分歧。

---

## AS-4.2 跳转指令

### 4.2.1 jmp 直接跳转

`jmp` 无条件把控制转移到目标地址。"直接跳转"（direct jump）的目标是一个在汇编中写成标号、在机器码里编码成**相对当前位置的偏移量**的常量地址，链接后固定不变。它是构造循环回边、跳过 else 分支等结构的基本件。

```
	jmp	.L4        # 直接跳转，目标 .L4 是编译期已知标号
```

初学者常以为 `jmp` 里存的是目标的绝对地址，其实 x86-64 的常规 `jmp`/`jCC` 用的是**PC 相对**编码：机器码里存的是"目标 − 下一条指令地址"的差（rel8 或 rel32），CPU 执行时用 `rip + 偏移` 算出目标。好处是代码整体搬到内存任意位置（PIE、动态库）都不用改这些跳转字节，天然位置无关。

### 4.2.2 jmp 间接跳转

"间接跳转"（indirect jump）的目标在运行期才确定，来自寄存器或内存。语法上以 `*` 前缀标记，如 `jmp *%rax`（跳到 `%rax` 里的地址）或 `jmp *(%rdx,%rdi,8)`（跳到内存表项里的地址）。它是实现 switch 跳转表、函数指针尾调用、虚函数派发的底层机制。

```
void tail_via_ptr(void (*fp)(void)){ fp(); }
```

```
$ gcc -O2 -S -fno-asynchronous-unwind-tables jmps.c
tail_via_ptr:
	endbr64
	jmp	*%rdi          # 间接跳转到函数指针 fp（尾调用优化）
```

`*` 是区分直接/间接的关键标记，不要漏读：`jmp .L4` 是跳到标号，`jmp *%rax` 是跳到"寄存器里存的那个地址"。间接跳转因为目标不定，对流水线分支预测更不友好（CPU 难猜落点），也是控制流劫持攻击的目标——这正是 CET/`endbr64` 与反汇编里出现的 `notrack` 前缀存在的原因（合法的间接跳转落点须是 `endbr64`，除非该跳转带 `notrack` 前缀显式豁免，如编译器可信的 switch 表跳转）。

### 4.2.3 jCC 条件跳转

`jCC`（`je`、`jne`、`jg`、`jle`、`ja`、`jb`……）根据条件码决定是否跳转：条件成立则转移到目标，否则顺序执行下一条。它与 `setCC` 共用整套条件后缀，是编译 if/循环/短路逻辑的核心。

以带分支的绝对差为例：

```
long absdiff(long x, long y){ if (x > y) return x-y; else return y-x; }
```

```
absdiff:
	endbr64
	cmpq	%rsi, %rdi      # x - y
	jle	.L2            # 若 x <= y（有符号），跳到 else 分支
	movq	%rdi, %rax     # then: x - y
	subq	%rsi, %rax
	ret
.L2:
	movq	%rsi, %rax     # else: y - x
	subq	%rdi, %rax
	ret
```

注意"C 里的条件"与"汇编里的跳转条件"常是**相反**的：源码判 `x > y` 走 then，编译器却生成 `jle`（`x <= y` 时跳走）——因为它把 else 分支放到别处，用一条"条件不满足就跳过 then"的跳转实现，顺序落下来的才是 then。这种"取反跳过"是 4.3 jump-over 模式的核心，先在这里建立直觉：读 `jCC` 时要问"什么条件下会跳走"，而不是"什么条件下继续"。后缀家族（`g/l` 有符号、`a/b` 无符号）与 setCC 完全一致。

### 4.2.4 跳转的编码：rel8 与 rel32

直接跳转在机器码里以 PC 相对偏移编码，短跳转用 1 字节偏移（rel8，范围 −128~+127），远跳转用 4 字节偏移（rel32）。汇编器/链接器根据目标距离自动选长短形式，这解释了为什么同样一条 `jmp` 在反汇编里可能是 2 字节或 5 字节。

```
   8:	77 29                	ja     33 <sw+0x33>   # 短条件跳转：操作码 77 + rel8=0x29
```

上面 `ja` 编码为 `77 29`：`77` 是 `ja` 的操作码，`29` 是相对下一条指令的偏移（`0x0a + 0x29 = 0x33`，正好是目标）。理解 PC 相对编码能解开两个初学疑问：其一，为什么反汇编里跳转目标显示成绝对地址而机器码里看不到那个地址——因为地址是 `rip+偏移` 现算的；其二，为什么代码可以整体重定位而无需逐条改跳转——偏移是相对的，跟绝对基址无关。间接跳转 `jmp *%rax` 则不是相对偏移，而是直接取寄存器/内存里的绝对地址。

#### 来源与时效

- CSAPP 3e (2016) §3.6.3 跳转指令、§3.6.4 跳转指令编码（PC 相对 rel8/rel32、直接 vs 间接 `*` 记法）。核实 2026-07-26。
- Intel SDM Vol.2：JMP、Jcc 指令条目（rel8/rel32/r/m64 操作数形式、条件码判定表）。核实 2026-07-26。
- Stanford CS107 Control Flow 讲义（jCC 与 C 条件取反、间接跳转用于函数指针）。核实 2026-07-26（二手佐证，不承重）。
- 本机实证：gcc 13.3.0 `-O2 -S`（`jmp *%rdi`）、`-Og -S`（`jle`）、objdump 2.42 `-d`（`77 29` 编码，见 AS-4.5 输出）。
- 冲突项：无。`endbr64`/`notrack` 属 CET（Intel Control-flow Enforcement Technology）机制，gcc 13.3 默认插入 `endbr64`，与 CSAPP 2016 版成书时未覆盖属"版本 delta"而非冲突，标为演进项。

---

## AS-4.3 条件分支编译

### 4.3.1 if-else 的 jump-over（跳过）模式

编译器把 `if-else` 翻译成一种固定套路：先用 `cmp`/`test` 设标志，再用一条"当条件**不**成立时跳过 then 块"的 `jCC` 跳到 else（或结尾），then 块顺序落下、末尾用 `jmp`/`ret` 跨过 else。这就是 jump-over（也叫 conditional control transfer）模式，是 4.2.3 `absdiff` 例子的推广。

用 C 伪码表示这套模板：

```
    if (!cond) goto Else;      # jCC 取反条件，跳过 then
      <then 语句>
      goto Done;               # 无条件跳，跨过 else（若 then 以 return 结尾则用 ret）
    Else:
      <else 语句>
    Done:
```

对照 `absdiff` 的真实汇编（见 4.2.3）：`cmpq %rsi,%rdi` 设标志，`jle .L2` 在 `x<=y` 时跳过 then，then 块 `x-y` 顺序执行后直接 `ret`，`.L2` 标号处是 else 块 `y-x`。两个分支都以 `ret` 收尾，所以这里省掉了跨越用的 `jmp`。

理解这套模式的关键是抓住"取反"：源码写 `if (x>y)`，汇编却是 `jle`（`x<=y` 跳走），因为要"守卫" then 块——只有条件不满足才需要绕开它。初学者读汇编时应把 `jCC` 的目标标号当作"绕行出口"来看。这种"用跳转选择走哪条路"的做法对现代 CPU 有个隐患：如果分支难以预测（比如条件随机），预测错误会冲刷流水线、代价可达十几个周期——这正是编译器在某些场景改用无分支 `cmov`（见 4.6）的动机。只包含赋值、无副作用、两侧都便宜的短 if，才是 `cmov` 的候选；带函数调用、可能触发异常（如解引用、除法）或分支高度可预测的 if，仍走 jump-over。

#### 来源与时效

- CSAPP 3e (2016) §3.6.5 用条件控制实现条件分支（jump-over 模板、C goto 等价形式）。核实 2026-07-26。
- Intel SDM Vol.2：Jcc（条件转移语义）。核实 2026-07-26。
- 本机实证：gcc 13.3.0 `-Og -S`，`absdiff` 输出见 4.2.3。
- 冲突项：无。CSAPP 的 goto 等价写法与 gcc 实际生成结构一致（gcc 在两分支均 return 时省略跨越 jmp，属实现细节，非分歧）。

---

## AS-4.4 循环编译

### 4.4.1 do-while：最基础的循环模板

`do-while` 是最贴近汇编的循环形态：循环体先无条件执行一次，末尾用一条 `jCC` 判断是否回跳到循环体开头。它只有**一条**回边跳转、无预判，是编译器构造其他循环的目标形态。

```
long fact_dowhile(long n){ long r=1; do{ r*=n; n--; }while(n>1); return r; }
```

```
fact_dowhile:
	endbr64
	movl	$1, %eax
.L2:
	imulq	%rdi, %rax      # 循环体：r *= n
	subq	$1, %rdi        # n--
	cmpq	$1, %rdi        # 比较 n 与 1
	jg	.L2            # n > 1 则回跳，继续循环
	ret
```

它的 goto 等价形式最直观：

```
    loop:
      <body>
      if (cond) goto loop;   # 条件成立就回跳
```

do-while 只有循环体入口 `.L2` 一个标号和一条回跳 `jg`，没有任何"进入循环前的检查"，因为语义保证至少执行一次。初学者可把它当作循环的"母版"：读懂它，再看 while/for 只是在它前面加一道"要不要进循环"的门。

### 4.4.2 while：jump-to-middle 与 guarded-do 两种模板

`while` 循环需要"先判断再执行"，编译器有两种实现。低优化常用 **jump-to-middle**（跳到中间）：先无条件 `jmp` 到位于循环体之后的判断处，判断成立再跳回体首。高优化常用 **guarded-do**（守卫式 do-while）：先做一次初始判断守卫，条件成立才落入一个 do-while。

本机 `-Og` 下 gcc 生成的是 jump-to-middle：

```
long fact_while(long n){ long r=1; while(n>1){ r*=n; n--; } return r; }
```

```
fact_while:
	endbr64
	movl	$1, %eax
	jmp	.L4            # 先跳到"中间"的判断处
.L5:
	imulq	%rdi, %rax      # 循环体
	subq	$1, %rdi
.L4:
	cmpq	$1, %rdi        # 判断（第一次也在这里判）
	jg	.L5            # 成立则跳到体首 .L5
	ret
```

两种模板对应的 goto 形式如下：

```
jump-to-middle:                 guarded-do:
      goto Test;                    if (!cond) goto Done;   # 守卫：一次都不满足就直接结束
    Loop:                         Loop:
      <body>                        <body>
    Test:                           if (cond) goto Loop;
      if (cond) goto Loop;        Done:
```

为什么要两种？jump-to-middle 结构简单、代码短，代价是每轮循环入口都要执行那条 `jmp`（或者说第一次要跳过去）。guarded-do 把"是否进入循环"的判断只做一次，之后循环体内就是纯粹的 do-while，回边更紧凑、对分支预测更友好，所以 `-O1` 及以上编译器更偏好它。两者语义完全等价（都保证 `while(0次)` 时循环体一次不执行），差别纯在代码形态与性能，读汇编时看到"先 jmp 到后面判断"或"先一道守卫判断再落入循环"都应识别为 while/for。

### 4.4.3 for：改写成 while 再套模板

C 的 `for(init; test; update) body` 被编译器机械改写为 `init; while(test){ body; update; }`，因此汇编模板与 while 完全相同——同样是 jump-to-middle 或 guarded-do，只是多了初始化和每轮末尾的 update。

```
long fact_for(long n){ long r=1; for(long i=2;i<=n;i++) r*=i; return r; }
```

```
fact_for:
	endbr64
	movl	$2, %eax        # init: i = 2
	movl	$1, %edx        # r = 1
	jmp	.L7            # jump-to-middle
.L8:
	imulq	%rax, %rdx      # body: r *= i
	addq	$1, %rax        # update: i++
.L7:
	cmpq	%rdi, %rax      # test: i <= n
	jle	.L8
	movq	%rdx, %rax
	ret
```

可以看到 for 与 4.4.2 的 while 骨架一模一样，只是初始化 `i=2` 提到循环前、`i++` 放在体尾。理解"for 就是 while 的语法糖"能解释很多现象：为什么 `for(;;)` 是死循环（test 恒真被优化掉）、为什么 `continue` 会跳到 update 而不是直接回体首（因为 update 属于每轮末尾）。一个易错点：C11 起 `for` 里声明的 `i` 作用域仅限循环，汇编里它就是个普通局部量（此处放在 `%rax`），出循环即失效。

#### 来源与时效

- CSAPP 3e (2016) §3.6.6 循环（do-while / jump-to-middle / guarded-do 三模板、for→while 改写规则）。核实 2026-07-26。注：CSAPP §3.6.6 将 while 的两实现分别称 "jump to middle" 与 "guarded do"。
- Intel SDM Vol.2：Jcc、JMP（回边与前向跳转语义）。核实 2026-07-26。
- 本机实证：gcc 13.3.0 `-Og -S`，三个函数真实输出见上。
- 冲突项：无实质分歧。哪种 while 模板被选用随优化级/编译器版本变化（gcc 13.3 `-Og` 用 jump-to-middle，`-O1`+ 倾向 guarded-do），属"实现随版本演进"而非来源冲突；教材给出两种模板均在本机可复现。

---

## AS-4.5 switch 跳转表

### 4.5.1 跳转表机制：O(1) 多路分支

当 `switch` 的 case 标签**稠密**（取值集中在一段小区间）且数量较多时，编译器不会生成一长串 `cmp`/`jCC`，而是构造一张**跳转表**（jump table）：一个按 case 值索引的地址数组。运行时先做一次范围检查，再用 case 值直接索引表、取出目标地址、间接跳转过去，无论多少 case 都是 O(1)。

```
long sw(long x){ switch(x){ case 0:...case 5:...; default:...; } return r; }
```

```
$ gcc -O1 -c -fno-asynchronous-unwind-tables sw.c && objdump -d -Matt sw.o
0000000000000000 <sw>:
   0:	f3 0f 1e fa          	endbr64
   4:	48 83 ff 05          	cmp    $0x5,%rdi              # 范围检查：x 与 5 比
   8:	77 29                	ja     33 <sw+0x33>           # x>5（无符号）→ default
   a:	48 8d 15 00 00 00 00 	lea    0x0(%rip),%rdx         # rdx = 跳转表基址（重定位填入）
  11:	48 63 04 ba          	movslq (%rdx,%rdi,4),%rax     # rax = 表[x]（4 字节有符号偏移，符号扩展）
  15:	48 01 d0             	add    %rdx,%rax              # rax = 基址 + 偏移 = 目标绝对地址
  18:	3e ff e0             	notrack jmp *%rax            # 间接跳转到该 case
```

范围检查用**无符号** `ja`（而非 `jg`）是一个精巧点：`x` 即便是负数，转成无符号后也是个巨大的值，一定 `> 5`，于是一条 `ja` 同时挡住了"太大"和"负数"两种越界，省掉一次单独的下界检查。这就是为什么明明源码是有符号 `long`，反汇编却用无符号跳转的原因。

### 4.5.2 跳转表布局：.rodata 中的相对偏移

现代 gcc（PIE 默认）不把绝对地址存进表，而是存**相对表基址的 32 位有符号偏移**，放在只读节 `.rodata`。取目标时用 `movslq` 把 4 字节偏移符号扩展进 `%rax`，再 `add` 上表基址得到绝对地址——上面 `movslq (%rdx,%rdi,4)` + `add %rdx,%rax` 两条正是干这个。表用相对偏移是为了位置无关（PIE 下代码基址运行期才定），比存绝对地址更省重定位、更安全。

本机把该目标文件链接成可执行后，跳转表（表基址 0x2004）实测内容与解码如下：

```
$ objdump -s -j .rodata swexe        # 表基址 0x2004 处的 6 个 4 字节小端偏移
 2000 01000200 60f1ffff 7ef1ffff 66f1ffff
 2010 6cf1ffff 6cf1ffff 72f1ffff ...

解码（target = 0x2004 + 符号扩展偏移）：
 case0: 0xfffff160 (-3744) -> 0x1164  (sw+0x1b, mov $0xa=10)
 case1: 0xfffff17e (-3714) -> 0x1182  (sw+0x39, mov $0x14=20)
 case2: 0xfffff166 (-3738) -> 0x116a  (sw+0x21, mov $0x1e=30)
 case3: 0xfffff16c (-3732) -> 0x1170  (sw+0x27, mov $0x28=40)
 case4: 0xfffff16c (-3732) -> 0x1170  (sw+0x27, mov $0x28=40)  ← 与 case3 共用
 case5: 0xfffff172 (-3726) -> 0x1176  (sw+0x2d, mov $0x32=50)
```

这张实测表把几个教学要点一次讲透。其一，表项是"偏移"不是"地址"，必须 `基址+偏移` 才得到真正的跳转目标，这与旧式"表里存绝对地址、`jmp *table(,%rdi,8)`"不同（后者是 CSAPP 2016 图示的经典形态，仍可在非 PIE `-fno-pie -no-pie` 下见到）。其二，`case 3` 与 `case 4` 落到同一个偏移 `0x1170`，正是源码里 `case 3:` 直落（fall-through）到 `case 4:` 合并处理的机器级体现——跳转表天然支持 case 合并，两个索引指向同一处理块即可。其三，`default` 不在表内，由前面的范围检查 `ja` 直接兜住。

### 4.5.3 稠密 vs 稀疏：编译器何时选跳转表

跳转表只在 case 值**稠密**时划算：表长度正比于 case 值的极差（max−min），若 case 稀疏（如 1、100、10000），建一张巨大且大半为 default 的表既费空间又不值，编译器改用二分比较树或线性 `cmp` 链。是否用表、阈值多少由编译器启发式和优化级决定，不是语言规定。

初学者常有的疑问：为什么有的 switch 反汇编看到跳转表、有的却是一串 `cmp`/`je`？答案就在稠密度与 case 数量。少数几个 case（如 2~3 个）即便稠密，编译器也可能直接用比较更快；case 多且连续才触发跳转表。另外 case 值有偏移时（如从 100 开始），编译器会先减去下界再索引，等价于把区间平移到 0 起点。这些都是启发式，具体阈值随 gcc 版本变化，读汇编时以"看到 `jmp *` 加 `.rodata` 表就是跳转表实现"为准，不必记死阈值。

#### 来源与时效

- CSAPP 3e (2016) §3.6.7 switch 语句（跳转表结构、范围检查、间接跳转 `jmp *`；教材示例为非 PIE、表存绝对地址形态）。核实 2026-07-26。
- Intel SDM Vol.2：JMP r/m64（间接跳转）、MOVSXD（`movslq` 符号扩展）。核实 2026-07-26。
- Stanford CS107 Control Flow（jump table 与稠密/稀疏权衡）。核实 2026-07-26（二手佐证，不承重）。
- 本机实证：gcc 13.3.0 `-O1 -c` + objdump 2.42 `-d`/`-s`/`-r`；表项偏移经手工解码并与各 case 处理块地址逐一核对一致（case3/case4 共用、default 走范围检查），见 4.5.2。
- 冲突项：表项存"绝对地址"（CSAPP 2016 经典图示，非 PIE）vs 存"相对表基址的偏移"（本机 gcc 13.3 PIE 默认）——两边都记：前者需 `jmp *table(,%rdi,8)` 一步到位，后者需 `movslq`+`add`+`jmp *%rax` 三步。差异源于 PIE 位置无关要求，属编译配置演进，非语义冲突。

---

## AS-4.6 条件传送 cmov

### 4.6.1 cmov 机制：把分支变成数据流

`cmovCC`（`cmovg`、`cmovle`、`cmove`……）根据条件码决定"是否把源拷进目的"：条件成立就传送，否则目的保持不变。它读同一套条件码、用同一套后缀，但**不改变控制流**——CPU 顺序执行，不跳转。编译器用它把简单的 `a>b ? x : y` 编译成**无分支**代码。

```
long maxl(long a, long b){ return a > b ? a : b; }
```

```
$ gcc -O2 -S -fno-asynchronous-unwind-tables cmov.c
maxl:
	endbr64
	cmpq	%rdi, %rsi      # 比较（注意此处顺序，见下）
	movq	%rdi, %rax      # 先把 a 放进结果（默认值）
	cmovge	%rsi, %rax      # 若 b>=a 成立，则用 b 覆盖 → 得到 max
	ret
```

理解 cmov 的关键：它**同时算好了两条路的候选值**，再按条件挑一个，全程不跳转。上例先无条件把 `a` 放进 `%rax` 作默认，再 `cmovge` 在条件成立时用 `b` 覆盖。对比 jump-over 版本（`-O0` 下 `maxl` 用 `cmpq`+`cmovge` 亦可，纯分支写法则会有 `jCC`），cmov 版没有任何 `jCC`，因此不会有分支预测失败的风险。

初学者两个易错点：一是 `cmov` 的目的**必须是寄存器**（不能是内存目的），且不支持 8 位操作数（16/32/64 位可）；二是它"无条件先算好两边"意味着两个候选表达式都会被求值，所以只有当两边都便宜且**无副作用**时编译器才敢用它。

### 4.6.2 适用条件：为什么 cmov 不总是更好

cmov 用"总是算两边"换掉"分支预测失败的风险"。当分支**难预测**（条件近乎随机）时，cmov 免去了预测失败的十几周期冲刷，通常更快；但当分支**高度可预测**（几乎总走同一边）时，预测器几乎不失误，反而是 cmov 白算了另一边、还引入了对 `cmp` 结果的数据依赖，可能更慢。所以编译器只在特定条件下才生成 cmov。

编译器不用 cmov（改回 jump-over）的典型情形有三类，都必须记住。第一，两侧表达式**有副作用或可能出错**：`p ? *p : 0` 里若用 cmov 会无条件解引用 `*p`，在 `p` 为空时崩溃——控制流分支能避免执行不该执行的那侧，cmov 不能。第二，两侧**计算昂贵**：无条件把两边都算出来的浪费超过分支预测失败的期望代价。第三，涉及函数调用等无法投机执行的操作。这解释了为什么同样是三元表达式，`a>b?a:b`（两边都是现成寄存器）出 cmov，而 `a>b?f():g()` 必然出分支。一个实用判据：能被 cmov 化的，几乎都是"两侧都是已算好的纯值、二选一"的场景。

#### 来源与时效

- CSAPP 3e (2016) §3.6.6 用条件传送实现条件分支（cmov 无分支机制、"两边都求值"的适用性限制、副作用/昂贵/易错三类不可用情形）。核实 2026-07-26。
- Intel SDM Vol.2：CMOVcc（条件传送语义，"操作数为 16/32/64 位、目的须为通用寄存器"）。核实 2026-07-26。
- Stanford CS107 Control Flow（cmov 与分支预测、可预测性对性能取舍）。核实 2026-07-26（二手佐证，不承重）。
- 本机实证：gcc 13.3.0 `-O2 -S`（`cmovge`）与 `-O0 -S`（同样含 cmovge，此小函数两级均无分支）对照，见 4.6.1。
- 冲突项：无语义冲突。"cmov 是否更快"依赖分支可预测性与微架构，属性能权衡而非对错分歧；CSAPP 与 SDM 对指令语义（不改控制流、目的须寄存器、不支持 8 位）一致。
