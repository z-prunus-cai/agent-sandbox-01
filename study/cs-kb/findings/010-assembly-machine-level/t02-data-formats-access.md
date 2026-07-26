# L3-03·大主题2 数据格式与信息访问

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：L3-03 大主题1（编译流水线、AT&T 语法、机器状态模型）、L3-02 CO-2（ISA 的寄存器与寻址）｜ 一手锚点：CSAPP（Bryant & O'Hallaron）3rd ed, 2016，§3.3–3.4；Intel® 64 and IA-32 Architectures Software Developer's Manual（SDM）Vol.1 ch3（寄存器）、Vol.2 ch2（ModRM/SIB 寻址编码）；System V AMD64 ABI（gitlab.com/x86-psABI/x86-64-ABI，寄存器角色佐证）；GNU as / gcc 官方文档 ｜ 成熟度：GA/稳定（x86-64 基础数据传送与寻址自 AMD64 引入即冻结，无版本漂移）
>
> 粒度判定：**1 份（不拆）**。理由：AS-2 的 5 个小主题是围绕"一条 mov 指令怎样在 x86-64 上搬一个数据"这条主线的连贯展开——先定"搬多大"（2.1 尺寸后缀），再定"搬进哪个寄存器/它有哪些别名"（2.2），再定"内存那头的地址怎么算"（2.3 通用寻址式），然后看真正干搬运和取址的指令族（2.4 mov/movz/movs/lea），最后看现代 PIE 下地址怎么写成位置无关形式（2.5 rip 相对）。条目虽多但全部同源、无跨机制爆炸，合为一份更能体现"尺寸 → 寄存器 → 地址 → 指令 → 位置无关"的递进，不拆。
>
> 本报告所有反汇编片段均在本机 Linux 6.18.5 x86-64 上以 gcc 13.3.0 / objdump 2.42 真跑取得（`-std=c11`），命令与环境串随文给出；寄存器别名与寻址式的规范定义以 CSAPP §3.3–3.4、Intel SDM 交叉核对。汇编默认 **AT&T 语法**（本课主语法），必要处与 Intel 语法差异并记。

---

## 2.1 数据尺寸与后缀

### 2.1.1 b/w/l/q 四个尺寸后缀

x86-64 的一条数据传送/算术指令要处理多大的数据，是靠指令助记符末尾的一个**尺寸后缀**字母表达的。AT&T 语法用四个后缀区分四种操作数宽度：

```
后缀   名称        位数    字节数
 b     byte        8 位    1
 w     word       16 位    2
 l     long       32 位    4
 q     quad       64 位    8
```

于是 `movb` 搬 1 字节、`movw` 搬 2 字节、`movl` 搬 4 字节、`movq` 搬 8 字节。这些后缀不是修饰词，而是选定了指令要动多少位——同一个"搬运"动作，四个后缀就是四条不同宽度的指令。

初学者最容易被 `l` 和 `q` 这两个名字绊住。这里的 "word" 是 x86 的**历史单位**：因为 8086 时代寄存器是 16 位，x86 就把 16 位钉死叫作 "word"，此后即使字长涨到 32、64 位，"word" 仍恒指 16 位。于是 32 位叫 "long word"（后缀 `l`），64 位叫 "quad word"（四个 16 位字，后缀 `q`）。这套命名和"机器字长"无关，只是沿用旧词，记住"w=16 位"这个锚点即可推出其余。

一个易错点：AT&T 的尺寸后缀与 Intel 语法的写法不同。Intel 语法不在助记符上加后缀，而是在内存操作数前写 `byte ptr` / `word ptr` / `dword ptr` / `qword ptr`；当一个操作数是寄存器时，宽度由寄存器名（如 `al`/`ax`/`eax`/`rax`）自动确定，两种语法都可省略显式宽度。这是同一条机器指令的两种文字写法，不是两条指令。

### 2.1.2 后缀与 C 类型的映射

在 x86-64 System V 平台上，C 的整数/指针类型按大小映射到这四个后缀。基本对应关系：

```
C 类型                       宽度     后缀    典型 8 位子寄存器例
char / _Bool                 1 字节    b      %al, %dil
short                        2 字节    w      %ax, %si
int                          4 字节    l      %eax, %edx
long / long long / 指针 / size_t  8 字节 q  %rax, %rcx
```

也就是说，看到汇编里某个变量被 `movl` 搬运，基本能反推它的 C 类型是 4 字节宽（`int`/`unsigned`/`float` 的整数搬运等）；被 `movq` 搬运则是 8 字节宽（`long`、指针等）。

用一段实测坐实这套映射。把四个不同宽度的参数分别存回四个全局变量：

```c
char gc; short gs; int gi; long gl;
void store_all(char c, short s, int i, long l) {
    gc = c; gs = s; gi = i; gl = l;
}
```

```
$ gcc -std=c11 -O1 -S -fno-asynchronous-unwind-tables sizes.c
$ sed -n '/store_all:/,/ret/p' sizes.s     # gcc 13.3.0, 基线 @2026-07-26
store_all:
    endbr64
    movb    %dil, gc(%rip)     # char  -> b, 参数在 %dil（%rdi 低字节）
    movw    %si,  gs(%rip)     # short -> w, 参数在 %si （%rsi 低 16 位）
    movl    %edx, gi(%rip)     # int   -> l, 参数在 %edx（%rdx 低 32 位）
    movq    %rcx, gl(%rip)     # long  -> q, 参数在 %rcx（完整 64 位）
    ret
```

四个后缀 b/w/l/q 与 char/short/int/long 一一对齐，一次全对上。注意此例也顺带展示了前四个整型参数走 `%rdi,%rsi,%rdx,%rcx`（调用约定属大主题5，这里不展开），且每个参数是取对应宽度的子寄存器来搬——这引出下一节的寄存器别名。

### 2.1.3 objdump 省略后缀而 gcc -S 保留后缀（显示差异，非语义差异）

同一条指令，`gcc -S` 生成的汇编写作 `movb/movw/movl/movq`（带后缀），但 `objdump -d` 反汇编回来常常只写 `mov`（不带后缀）。这不是两条不同的指令，只是两个工具的**显示约定不同**。

```
$ objdump -d sizes.o | sed -n '/<store_all>:/,/ret/p'   # objdump 2.42
   4:  40 88 3d ...   mov    %dil,0x0(%rip)      # 对应源里的 movb
   b:  66 89 35 ...   mov    %si, 0x0(%rip)      # 对应源里的 movw
  12:  89 15 ...      mov    %edx,0x0(%rip)      # 对应源里的 movl
  18:  48 89 0d ...   mov    %rcx,0x0(%rip)      # 对应源里的 movq
```

为什么 objdump 敢省后缀？因为当指令的某个操作数是寄存器（如 `%dil`/`%si`/`%edx`/`%rcx`）时，宽度已经由寄存器名唯一确定了，后缀信息冗余，省掉不歧义。反过来，**只有当所有操作数都无法表明宽度时后缀才是必需的**——最典型是"立即数存进内存"，如 `movq $0, (%rax)` 里 `$0` 和 `(%rax)` 都不含宽度，必须靠 `q` 才知道要写 8 字节；此时省掉后缀汇编器会报"操作数大小不明"。

初学者读反汇编不必纠结 objdump 少了后缀：先看操作数里的寄存器名判定宽度，寄存器名前缀 `r`=64 位、`e`=32 位、无前缀的 16 位名=16 位、`l`/`b` 结尾的 8 位名=8 位。

#### 来源与时效
- CSAPP 3rd ed, 2016, §3.3「Data Formats」Figure 3.1（C 类型↔汇编后缀↔大小对照表）；核实 2026-07-26。
- Intel SDM Vol.1, §3.3.1「General-Purpose Registers」与「Fundamental Data Types」（byte/word/doubleword/quadword 定义，与 AT&T b/w/l/q 一一对应）；核实 2026-07-26。
- GNU as / gcc 文档：AT&T 语法的尺寸后缀规则；objdump 在寄存器操作数可定宽时省略后缀属显示约定。核实 2026-07-26。
- 实证：gcc 13.3.0 / objdump 2.42，`sizes.c` 见 §2.1.2/§2.1.3，基线 @2026-07-26。
- 冲突/差异项：`gcc -S` 保留后缀 vs `objdump -d` 省略后缀——同一机器码的两种显示，非语义分歧；两种 AT&T/Intel 宽度写法差异见 §2.1.1。

---

## 2.2 通用寄存器组

### 2.2.1 16 个 64 位通用寄存器

x86-64 有 **16 个 64 位通用寄存器**。它们分成两批命名：一批是 8086/IA-32 沿用来的有名字寄存器，扩展到 64 位后在名字前加 `r`；另一批是 AMD64 新增的、直接用编号命名：

```
%rax  %rbx  %rcx  %rdx        （累加/基址/计数/数据，历史名，现基本通用）
%rsi  %rdi                    （源/目的变址，历史名）
%rbp  %rsp                    （帧指针 / 栈指针，%rsp 专用于栈顶）
%r8   %r9   %r10  %r11        （AMD64 新增，纯编号）
%r12  %r13  %r14  %r15        （AMD64 新增，纯编号）
```

除了 `%rsp` 被硬性约定为栈指针（不能随意挪作它用），其余 15 个在指令层面都是"通用"的——任何算术、传送都能用它们当操作数。名字里的历史含义（"a=累加器"之类）在 x86-64 上大多只剩助记，不再是硬性限制。

初学者可以把这 16 个寄存器理解为"CPU 手边最快的 16 个格子"：内存很大但慢，寄存器只有 16 个但快到几乎不花时间，编译器的核心工作之一就是把最活跃的变量尽量塞进这 16 个格子。为什么恰好 16 个？因为指令编码里寄存器编号字段的宽度有限——AMD64 用一个额外的 REX 前缀位把可寻址寄存器数从 IA-32 的 8 个翻倍到 16 个，编号正好 4 位（2⁴=16）。想再多就要更宽的编码字段。

### 2.2.2 32/16/8 位子寄存器别名

每个 64 位寄存器都可以只访问它的**低位部分**，用不同的名字表示不同宽度。这不是 16 个之外的新寄存器，而是同一个物理寄存器的"低位视图"：

```
64 位   32 位   16 位   8 位(低字节)
%rax    %eax    %ax     %al
%rbx    %ebx    %bx     %bl
%rcx    %ecx    %cx     %cl
%rdx    %edx    %dx     %dl
%rsi    %esi    %si     %sil
%rdi    %edi    %di     %dil
%rbp    %ebp    %bp     %bpl
%rsp    %esp    %sp     %spl
%r8     %r8d    %r8w    %r8b
%r9     %r9d    %r9w    %r9b
%r10    %r10d   %r10w   %r10b
%r11    %r11d   %r11w   %r11b
%r12    %r12d   %r12w   %r12b
%r13    %r13d   %r13w   %r13b
%r14    %r14d   %r14w   %r14b
%r15    %r15d   %r15w   %r15b
```

命名规律很整齐：有名字的 8 个用 `r`(64)/`e`(32)/裸名(16)/`l` 或 `x` 演化名(8)；编号的 8 个用 `r8`(64)/`r8d`(32,d=double word)/`r8w`(16,w=word)/`r8b`(8,b=byte)。选哪个名字，就等于选了对这个物理寄存器操作多少位。

一个必须记住的**易错点/关键规则**：向 32 位子寄存器写入时，CPU 会自动把高 32 位清零；但向 16 位或 8 位子寄存器写入时，高位**保持不变**。

```
写 %eax          -> %rax 高 32 位被清零（隐式零扩展）
写 %ax / %al     -> %rax 其余高位原样保留
```

这条规则在 §2.4 会再次用到（它是"32 位无符号扩展到 64 位不需要专门指令"的原因）。另一个历史遗留：`%rax/%rbx/%rcx/%rdx` 还有传统的"高字节"名 `%ah/%bh/%ch/%dh`（访问 bit 8–15），但它们不能和新的 `%sil/%dil/%r8b` 等一起用在同一条需要 REX 前缀的指令里，初学者一般用 `%al` 这类低字节名即可，遇到 `%ah` 知道是"第 2 个字节"就行。

实测确认低字节名与新编号寄存器的宽度别名确实是这样反汇编出来的：

```
# 参数按宽度取子寄存器（摘自 §2.1.2 的 store_all）
    movb    %dil, ...     # %rdi 的低字节
    movw    %si,  ...     # %rsi 的低 16 位
    movl    %edx, ...     # %rdx 的低 32 位

# 第 5、6 个 int 参数走 %r8d / %r9d（r8/r9 的 32 位视图）
$ gcc -std=c11 -O0 -S regs2.c ; grep -E 'r8|r9' regs2.s
    movl    %r8d, -20(%rbp)
    movl    %r9d, -24(%rbp)
```

### 2.2.3 寄存器的调用角色（ABI 佐证，深挖归大主题5）

硬件层面这 16 个寄存器（除 `%rsp`）大多通用，但**软件层面**的 System V AMD64 ABI 又给它们分派了函数调用中的固定角色：前 6 个整型参数依次走 `%rdi,%rsi,%rdx,%rcx,%r8,%r9`，返回值走 `%rax`，`%rsp` 是栈指针，`%rbx/%rbp/%r12–%r15` 是被调用者保存（callee-saved）等。

这里只作为"为什么反汇编里参数总出现在 `%rdi/%rsi/...`"的背景佐证——上一节实测的 `store_all` 四个参数正好落在 `%rdi,%rsi,%rdx,%rcx`，就是这套约定的体现。完整的调用约定、caller/callee-saved 划分、栈帧布局属大主题5「过程、栈帧与调用约定」，本节不展开，避免重复立项。初学者此处只需知道：寄存器"通用"是指令层面的，"谁负责传参、谁负责保存"是 ABI 这层软件约定叠加上去的。

#### 来源与时效
- CSAPP 3rd ed, 2016, §3.4「Accessing Information」Figure 3.2（16 个整数寄存器的 64/32/16/8 位名对照表）；§3.4.2 提及"写 32 位子寄存器清零高 32 位"规则；核实 2026-07-26。
- Intel SDM Vol.1, §3.4.1「General-Purpose Registers in 64-Bit Mode」（16 个 64 位寄存器 + REX 前缀扩展；写 32 位寄存器零扩展至 64 位的规定）；核实 2026-07-26。
- System V AMD64 ABI（gitlab.com/x86-psABI/x86-64-ABI），§3.2 Figure「Register Usage」（参数/返回值/保存寄存器角色，佐证）；核实 2026-07-26。规范演进由 psABI 维护，基础寄存器角色长期稳定；具体提交哈希待核（本机无 `.reference` 本地副本，按官方规范文本引用）。
- 实证：gcc 13.3.0，`regs2.c` 见 §2.2.2，基线 @2026-07-26。
- 冲突项：无实质分歧；`%ah/%bh/%ch/%dh` 高字节名与 REX 低字节名不可混用属编码约束，两来源一致。

---

## 2.3 操作数寻址

### 2.3.1 三类操作数：立即数 / 寄存器 / 内存

x86-64 指令的每个操作数属于三类之一：

```
立即数 (immediate)   AT&T 写作 $常数        例：$255, $0x1f    —— 指令里直接编码的常量
寄存器 (register)     AT&T 写作 %寄存器名     例：%rax, %edi     —— 值取自寄存器
内存   (memory)       写作 有效地址形式        例：(%rax), 8(%rdi) —— 值取自算出的内存地址
```

AT&T 语法用**前缀符号**区分：`$` 引出立即数，`%` 引出寄存器，裸括号/位移形式表示内存访问。Intel 语法则不用这些前缀（立即数直接写数字、寄存器直接写名字、内存用 `[...]`）。

实测看三类操作数各自的样子：

```
    movl    $255, %eax      # 源=立即数 $255，目的=寄存器 %eax
    movl    12(%rdi), %eax  # 源=内存(有效地址 %rdi+12)，目的=寄存器
$ objdump -d imm.o          # 反汇编里立即数显示为 $0x...
    mov    $0xff,%eax
```

初学者要记牢 AT&T 的**操作数顺序是"源在前、目的在后"**（`mov 源, 目的`），恰好与 Intel 语法相反（Intel 是 `mov 目的, 源`）。看到 `movl $255, %eax` 是"把 255 放进 eax"，不是反过来。少了 `$`（写成 `movl 255, %eax`）语义就变成"从内存地址 255 处取值"，这是初学者最常见的一类错误。

### 2.3.2 内存寻址通式 disp(base, index, scale)

x86-64 内存操作数最一般的形式是一个"比例变址 + 位移"表达式，AT&T 写作：

```
disp(base, index, scale)
```

它计算出的**有效地址**（effective address）是：

```
有效地址 = disp + R[base] + R[index] × scale
```

式中 disp 是常数位移（displacement，可正可负），base 是基址寄存器，index 是变址寄存器，scale 是比例因子，**只能取 1、2、4、8** 之一（对应 1/2/4/8 字节的元素大小）。四个部分都可省略，省略的部分按 0（或 scale 按 1）处理。

这个式子几乎就是为"数组/结构体访问"量身定做的：`base` 放数组首地址，`index` 放下标，`scale` 放元素字节数，`disp` 放字段偏移，一条指令就把 `数组首 + 下标×元素大小 + 字段偏移` 一次算完。这也是为什么它叫"通用"寻址——常见的几种简单形式都是它的特例：

```
(%rax)              base 单独：             地址 = R[rax]
8(%rax)             disp + base：           地址 = R[rax] + 8
(%rax,%rcx)         base + index（scale=1）：地址 = R[rax] + R[rcx]
(%rax,%rcx,4)       base + index×4：        地址 = R[rax] + R[rcx]×4
8(%rax,%rcx,4)      全式：                  地址 = R[rax] + R[rcx]×4 + 8
(,%rcx,4)           只有 index×scale：       地址 = R[rcx]×4
```

用一段实测坐实。数组元素访问 `a[i]`（`a` 是 `int*`，元素 4 字节）编译成一条比例变址寻址：

```c
int arr_index(int *a, long i) { return a[i]; }
```

```
$ gcc -std=c11 -O1 -S arr.c
arr_index:
    movl    (%rdi,%rsi,4), %eax    # 地址 = %rdi(=a) + %rsi(=i)×4，取 4 字节
    ret
```

`%rdi` 是首地址 `a`、`%rsi` 是下标 `i`、`scale=4` 是 `int` 的字节数，一条 `movl (%rdi,%rsi,4),%eax` 就完成"算地址 + 取元素"。若换成带常量偏移的 `a[3]`，则位移字段被用上：

```c
int disp_field(int *a) { return a[3]; }   // 3×4 = 12
```

```
    movl    12(%rdi), %eax        # 地址 = %rdi + 12
```

一个易错点：`scale` 只能是 1/2/4/8，不能是任意数（如 3、5）；元素大小不是这四个值时，编译器会改用乘法或 `lea` 组合来算地址，而不是硬塞进 scale 字段。另一个：`index` 不能用 `%rsp`（栈指针在编码上占了变址寄存器的一个特殊位置），但 `base` 可以是 `%rsp`。

### 2.3.3 有效地址的底层编码 ModRM 与 SIB（浅提）

`disp(base,index,scale)` 这套能力在机器码里由两个字节段实现：**ModRM 字节**表达"寄存器直接 / 内存间接 + 位移"，当出现 index×scale 这种比例变址时再追加一个 **SIB 字节**（Scale-Index-Base），SIB 用 2 位存 scale 的对数（00→×1、01→×2、10→×4、11→×8）、3 位存 index、3 位存 base。

初学者不必背这些位段——只需理解"为什么 scale 只能是 1/2/4/8"从这里就能看清：SIB 只给 scale 留了 2 位，2 位最多编 4 种取值，选定为 1/2/4/8 恰好覆盖常见元素大小。反汇编时 objdump 已经把 ModRM/SIB 翻译回了可读的 `disp(base,index,scale)` 文字，本课停留在读这层文字即可，字节级编码属更底层、不作专家纵深。

#### 来源与时效
- CSAPP 3rd ed, 2016, §3.3「Operand Specifiers」Figure 3.3（立即/寄存器/内存三类操作数与寻址模式表，含 `Imm(r_b,r_i,s)` 通式与有效地址公式）；核实 2026-07-26。
- Intel SDM Vol.2, §2.1「Instruction Format」与「ModR/M and SIB Bytes」（有效地址 = base + index×scale + disp 的编码；scale 2 位取 1/2/4/8；index 不可为 rsp 的编码约定）；核实 2026-07-26。
- 实证：gcc 13.3.0 / objdump 2.42，`arr.c`、`imm.c` 见 §2.3.1/§2.3.2，基线 @2026-07-26。
- 冲突项：无分歧。AT&T `disp(b,i,s)` 与 Intel `[b+i*s+disp]` 是同一寻址式的两种写法；两来源一致。

---

## 2.4 mov 家族与 lea

### 2.4.1 mov：等宽数据传送

`mov` 是最基础的数据传送指令：把源操作数的值复制到目的操作数，**源和目的宽度相同**，由后缀 b/w/l/q 指定。它可以是"寄存器→寄存器""立即数→寄存器""内存→寄存器""寄存器→内存"，但**不能内存→内存**（x86-64 的一条 mov 至多一个内存操作数）。

```
movl    $255, %eax           立即数 -> 寄存器
movq    %rcx, gl(%rip)       寄存器 -> 内存
movl    12(%rdi), %eax       内存   -> 寄存器
```

初学者把 `mov` 理解为"复制"而非"移动"更准确：源不会被清空，只是把值拷一份到目的。内存→内存要分两步（先 mov 进寄存器，再 mov 出去），这也是为什么编译器里到处是"过一道寄存器"的搬运。

`mov` 只搬"够宽的那部分"：`movl` 只碰目的的低 32 位——但如前述 §2.2.2，写 32 位子寄存器会**顺带把高 32 位清零**；而 `movb`/`movw` 写 8/16 位子寄存器则不动高位。这个"等宽传送但 32 位版本隐式清高位"的细节，直接决定了下面零扩展的处理方式。

### 2.4.2 movz：零扩展传送

当要把一个**较窄的无符号值**放进较宽的寄存器时，高位应补 0，这叫零扩展（zero extension）。指令是 `movz`，后缀是两个字母：源宽 + 目的宽，如 `movzbl`（byte→long，8→32）、`movzbq`（8→64）、`movzwl`（16→32）、`movzwq`（16→64）。

```
movzbl  %dil, %eax     8 位 -> 32 位，高位补 0
movzwl  %di,  %eax     16 位 -> 32 位，高位补 0
```

实测（`unsigned char`→`unsigned long`、`unsigned short`→`unsigned long`）：

```c
unsigned long zx(unsigned char c) { return c; }
unsigned long zxw(unsigned short s){ return s; }
```

```
zx:   movzbl  %dil, %eax      # 8->32，配合下一条规则即得 8->64
zxw:  movzwl  %di,  %eax
```

这里有个关键**易错点**：为什么 `unsigned char`→`unsigned long`（8→64）用的是 `movzbl`（目的是 32 位的 `%eax`）而不是 `movzbq`（64 位）？因为写 `%eax` 会自动清零 `%rax` 高 32 位（§2.2.2），所以"8→32 零扩展 + 隐式 32→64 清零" = "8→64 零扩展"，两条效果的一条 `movzbl` 就够了，更短。同理**根本不存在 `movzlq` 指令**（32→64 零扩展）：直接用 `movl %edi,%eax` 写 32 位寄存器就隐式零扩展到 64 位了。

```c
unsigned long zxl(unsigned int i) { return i; }   // 32 -> 64 零扩展
```

```
zxl:  movl    %edi, %eax      # 没有 movzlq；普通 movl 靠隐式清高位完成零扩展
```

### 2.4.3 movs：符号扩展传送

当要把一个**较窄的有符号值**放进较宽的寄存器时，高位要按符号位（最高位）复制填充，这叫符号扩展（sign extension），保证数值不变（-1 仍是 -1）。指令是 `movs`，后缀同样是"源宽+目的宽"：`movsbl`/`movsbq`/`movswl`/`movswq`/`movslq`。

与零扩展不同，**符号扩展的 32→64 版本 `movslq` 是真实存在且必需的**——因为符号扩展要复制符号位而不是补 0，写 32 位寄存器的"清零高位"帮不上忙。实测：

```c
long sx(char c)   { return c; }    // 8  -> 64 符号扩展
long sxw(short s) { return s; }    // 16 -> 64
long sxl(int i)   { return i; }    // 32 -> 64
```

```
sx:   movsbq  %dil, %rax      # 8  -> 64，符号扩展
sxw:  movswq  %di,  %rax      # 16 -> 64
sxl:  movslq  %edi, %rax      # 32 -> 64，真实存在（对比无 movzlq）
```

一句话对照记忆：

```
无符号变宽 -> movz（补 0）；32->64 无专用指令，普通 movl 隐式清高位即可
有符号变宽 -> movs（补符号位）；32->64 有专用 movslq，不可省
```

`movslq` 还有个等价的老写法 `cltq`（无操作数，专门把 `%eax` 符号扩展到 `%rax`），读反汇编时两者都可能遇到，含义一致。

### 2.4.4 lea：取有效地址（而非取值）

`lea`（Load Effective Address，AT&T 常见 `leaq`）长得像 mov——操作数也是 `disp(base,index,scale)` 形式——但它**只把算出来的地址本身放进目的寄存器，并不去内存取值**。即：

```
movq  8(%rdi,%rsi,4), %rax     先算地址 8+rdi+rsi*4，再去该地址取 8 字节到 rax
leaq  8(%rdi,%rsi,4), %rax     只把地址值 8+rdi+rsi*4 本身放进 rax，不访存
```

它的第一个用途是"取某对象的地址"（相当于 C 的 `&`），比如取数组元素地址传给别人。

它的第二个、也是初学者最意外的用途：**被编译器借去算普通整数算术**。因为有效地址公式 `disp + base + index×scale` 恰好能一次算出"若干寄存器的线性组合加常数"，编译器就用 `lea` 来实现乘常数、乘加，而完全不真的访问内存。实测两例：

```c
long lea_calc(long x, long y) { return 4*x + y + 7; }
int  add_const(int x){ return x + 42; }
```

```
lea_calc:  leaq   7(%rsi,%rdi,4), %rax    # 一条 lea 算出 4*x + y + 7
add_const: leal   42(%rdi), %eax          # 用 lea 算 x + 42（比 add 更灵活：目的可换寄存器）
```

`4*x + y + 7` 本来要一次乘法两次加法，`lea` 一条搞定；这是 x86-64 里极常见的优化手法，属大主题3「lea 做算术」的引子，本节只需认得"`lea` 是算地址/算式，不访存"。一个**易错点**：初学者看到 `lea` 里的括号会以为它像 `mov` 那样读内存——它不读；括号在这里只是"地址表达式"的语法，`lea` 取的是这个表达式的**值**（即地址数），不是该地址处的内容。

#### 来源与时效
- CSAPP 3rd ed, 2016, §3.4.2「Data Movement Instructions」（`mov`/`movz`/`movs` 家族与后缀表，Figure 3.4；不存在 `movzlq`、`movl` 隐式零扩展的说明）；§3.5.1「Load Effective Address」（`leaq` 取址与做算术）；核实 2026-07-26。
- Intel SDM Vol.2：`MOV`/`MOVZX`/`MOVSX`/`MOVSXD`/`LEA` 指令语义（`MOVSXD` 即 AT&T `movslq`；写 32 位通用寄存器零扩展至 64 位的通用规则见 Vol.1 §3.4.1.1）；核实 2026-07-26。
- 实证：gcc 13.3.0，`ext.c`（zx/sx 系列）、`addr.c`/`imm.c`（lea）见本节，基线 @2026-07-26。
- 冲突项：无分歧。`movslq`（AT&T）= `MOVSXD`（Intel）= `cltq`（无操作数特例，专用于 %eax→%rax）为同一符号扩展操作的不同写法；两来源一致。

---

## 2.5 RIP 相对寻址

### 2.5.1 sym(%rip)：以指令指针为基址的寻址

`%rip` 是指令指针寄存器（Instruction Pointer，即程序计数器 PC），始终指向"下一条要执行的指令"。x86-64 允许把它当作寻址的基址，写作：

```
sym(%rip)
```

它算出的地址 = **下一条指令的地址 + 一个编码在指令里的 32 位位移**。也就是说，访问一个全局符号 `sym` 时，不写 `sym` 的绝对地址，而写"它相对当前指令的距离"。这就是 RIP 相对寻址（RIP-relative addressing），x86-64 新增、IA-32 没有的寻址方式。

初学者可以这样理解直觉：绝对寻址是"去城市 A 的门牌号 12345"，RIP 相对寻址是"从我现在站的位置往前走 500 米"。后者不关心整段代码被加载到内存的哪个绝对位置——只要代码和数据的**相对距离**不变，走 500 米总能到。这正是位置无关代码所需要的。

### 2.5.2 为什么现代编译默认用它：PIE / ASLR

现代 Linux 上 gcc 默认生成 **PIE**（Position-Independent Executable，位置无关可执行文件），配合内核的 **ASLR**（地址空间布局随机化），程序每次运行被加载到的基址都不同，以增加攻击难度。代码若用写死的绝对地址访问全局变量，一旦加载基址变化就全错了；用 `sym(%rip)` 这种相对寻址，则无论加载到哪，"相对距离"不变，天然位置无关。

实测对比同一函数在 PIE（默认）与 `-no-pie` 下访问全局变量 `counter`：

```c
long counter = 100;
long get(void){ return counter; }
```

```
$ gcc -std=c11 -O1 get.c -o get_pie        # 默认 PIE（file 显示 "pie executable"）
$ objdump -d get_pie | sed -n '/<get>:/,/ret/p'
    1149:  endbr64
    114d:  48 8b 05 bc 2e 00 00   mov 0x2ebc(%rip),%rax   # 4010 <counter>  相对寻址
    1154:  ret

$ gcc -std=c11 -O1 -no-pie get.c -o get_nopie
    40113a:  48 8b 05 d7 2e 00 00 mov 0x2ed7(%rip),%rax   # 404018 <counter>
```

两点值得注意：其一，两种情形反汇编里都是 `...(%rip)` 形式——现代 x86-64 即便非 PIE 也偏好 rip 相对寻址，因为编码更短（32 位位移 vs 64 位绝对地址）。其二，objdump 已经贴心地在注释里把 `0x2ebc(%rip)` 解算成了目标符号 `# 4010 <counter>`：这个注释是 objdump 用"当前指令地址 + 指令长度 + 位移"替我们算好的最终地址，方便阅读，**它不是指令里真实编码的字节**（真实编码的是那个相对位移 `0x2ebc`）。

### 2.5.3 位移相对"下一条指令"而非当前指令（易错点）

RIP 相对的位移是相对**下一条指令的起始地址**计算的，不是相对当前指令。因为 CPU 在执行一条指令时，`%rip` 已经指向了它的下一条。这解释了上面反汇编注释里的算术：

```
当前指令 mov 位于     0x114d，长度 7 字节
下一条指令地址       0x114d + 7 = 0x1154
编码的位移           0x2ebc
目标 counter 地址    0x1154 + 0x2ebc = 0x4010   （= objdump 注释里的 4010）
```

初学者手算 rip 相对目标时最常犯的错，就是拿"当前指令地址 + 位移"去加，结果差一个指令长度。记住口诀"基址是**下一条**指令"即可对上 objdump 的注释。这个偏移量由链接器在最终确定各段布局后填入，源码/反汇编里不必手写。

RIP 相对寻址还有一个编码限制：它占用了内存寻址里 base 的一个特殊编码位置，因此**不能再同时带 index 变址**——`sym(%rip)` 只能是"位移 + rip"，写不出 `sym(%rip,%rax,4)` 这种带下标的形式。访问全局数组的某个动态下标元素时，编译器会先用 `lea sym(%rip),%reg` 把数组首地址取到寄存器，再用普通的 `(%reg,%index,scale)` 去索引。

#### 来源与时效
- CSAPP 3rd ed, 2016, §3.6.6 及 §3.4/§7.x 中关于 PC-relative / PIE 的说明；§3.4.1 寻址模式中的 `Imm(%rip)` 形式；核实 2026-07-26（RIP 相对在 CSAPP 主要结合 PIC/PIE 与跳转介绍）。
- Intel SDM Vol.2, §2.2.1.6「RIP-Relative Addressing」（64 位模式下 ModRM 的 disp32 相对 RIP；相对"下一条指令"计算；不与 SIB index 并用的编码约束）；核实 2026-07-26。
- GNU as / gcc 文档 + `objdump` 行为：默认 PIE、rip 相对注释解算方式；核实 2026-07-26。
- 实证：gcc 13.3.0 / objdump 2.42，`get.c`（PIE vs -no-pie）见 §2.5.2，`file` 报 "pie executable"，基线 @2026-07-26。
- 冲突项：无分歧。objdump 注释里的绝对地址（如 `# 4010 <counter>`）是解算结果、非编码字节，已在 §2.5.2/§2.5.3 显式区分，避免误读为"绝对寻址"。
