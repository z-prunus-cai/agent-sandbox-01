# L1-01·大主题8 程序正确性与调试基础

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：大主题3（函数、作用域与调用约定）、大主题4（递归与栈） ｜ 一手锚点：CMU 15-122《Principles of Imperative Computation》（C0 契约：requires/ensures/loop_invariant/assert）；Berkeley CS61A（Efficiency / Orders of Growth 讲次，cs61a.org）；ISO/IEC 9899:2011 (C11) §7.2 Diagnostics `<assert.h>`；Python 3.11 官方文档（Language Reference §7.3 assert 语句；`pdb`/`timeit` 库）；CLRS《Introduction to Algorithms》Ch2（循环不变式三性质） ｜ 成熟度：GA/稳定
>
> 粒度判定：**1 份**（不拆）。理由：本大主题 5 个小主题（8.1–8.5）沿一条「怎么知道程序对不对、错了怎么找、跑得快不快」的入门主线展开——断言 → 契约（前后置条件）→ 循环不变式 → 调试工具 → 效率直觉，环环相扣、无跨机制断层；篇幅属常规单元，不触发拆分。跨课边界：渐近分析（Big-O/Θ/Ω）的正式定义与证明归 L2-01（数据结构）/ L3-01（算法），本报告 8.5 只给「计数操作 + 粗略增长阶 + 规模曲线」的入门直觉，不做形式化。

本报告中的 Python 实测基于 Python 3.11.15、C 实测基于 gcc 13.3.0（`-std=c11`）、gdb 15.1、Linux 6.18.5 x86_64，均对应上面的基线串。所有实证脚本只在仓库外临时目录运行，不入库。

---

## 8.1 测试与断言

### 8.1.1 断言：写进代码里的、可执行的正确性检查

断言（assertion）是一条嵌在程序里的布尔判断：它声称「运行到这里时，某个条件必然为真」。如果条件为真，程序若无其事地继续；如果为假，说明程序已经进入了作者认为「不可能发生」的状态，断言立即让程序停下来、报告出错位置。它是把「我以为这里应该成立的假设」从脑子里搬到代码里、并且让机器替你自动核对的最廉价手段。

断言的核心用途是捕捉程序员自己的逻辑错误（bug），而不是处理正常的、预期内的坏输入。一个好的心智模型是：断言检查的是「本不该发生的事」（内部不变量被破坏），而不是「可能发生的事」（用户输错了、文件不存在）。后者属于错误处理（异常 / 返回码），见 8.1.5。断言是初学者迈向「契约式思维」（8.2）与「循环不变式」（8.3）的第一块踏脚石——它们本质上都是断言，只是放在了更有讲究的位置。

### 8.1.2 C 的 assert：条件为假即调用 abort

在 C 里，`assert` 是 `<assert.h>` 提供的一个宏。按 C11 §7.2 的规定，当它的实参表达式（须为标量类型）为假（即「compares equal to 0」，等于 0）时，`assert` 会把失败信息写到标准错误流，包含实参文本、源文件名、行号与所在函数名，然后调用 `abort` 终止程序。

最小例子与本机实测（gcc 13.3.0，`-std=c11`）：

```c
#include <assert.h>
#include <stdio.h>
int main(void){
    int x = 3;
    printf("before assert\n");
    assert(x == 5);   /* 为假 -> 打印诊断 + abort */
    printf("after assert (unreachable)\n");
    return 0;
}
```

```
$ gcc -std=c11 cassert.c -o cassert
$ ./cassert
before assert
cassert: cassert.c:6: main: Assertion `x == 5' failed.
Aborted
$ echo $?
134
```

诊断行的格式恰好印证了 C11 的描述：文件 `cassert.c`、行号 `6`、函数 `main`、实参文本 `x == 5`。退出码 134 = 128 + 6，其中 6 是 `SIGABRT` 的信号号——`abort()` 通过 `SIGABRT` 杀死进程，这是「断言失败」在 shell 层的典型现场。注意 `after assert` 那行没被打印：一旦断言失败，程序当场就死，后面的代码不再执行。

`assert` 的实参不要写有副作用的表达式，例如 `assert(x = compute())`（笔误把 `==` 写成 `=`，或故意在断言里做事）。因为断言在关闭时会被整体删掉（见 8.1.3），那些副作用也会一并消失，导致「开 debug 能跑、关 debug 就错」。

### 8.1.3 C 的 NDEBUG：断言可被整体关掉

C11 规定：如果在包含 `<assert.h>` 之前定义了宏 `NDEBUG`，那么 `assert` 被定义为展开成 `((void)0)`——即什么都不做。此时断言实参根本不被求值，它可能有的副作用也不会发生。这让你在开发时开着断言、发布时用 `-DNDEBUG` 一键关掉全部断言（消除其运行时开销），而不必逐条删代码。

同一份 `cassert.c`，加 `-DNDEBUG` 重新编译后本机实测：

```
$ gcc -std=c11 -DNDEBUG cassert.c -o cassert_ndebug
$ ./cassert_ndebug
before assert
after assert (unreachable)
$ echo $?
0
```

这次 `x == 5` 明明为假，程序却顺利跑完、正常退出（退出码 0），连 `after assert` 都打印了——因为断言被 `NDEBUG` 整体抹掉了。这正是「断言用来查 bug、不用来做程序逻辑」这条纪律的技术根源：任何你希望在生产环境也一定执行的检查，都不能只靠 `assert`。

### 8.1.4 Python 的 assert 语句与 __debug__ / -O

Python 的 `assert` 不是函数而是语句（Language Reference §7.3）。它有两种形式，官方给出的等价展开是：

```
assert expression
# 等价于：
if __debug__:
    if not expression: raise AssertionError

assert expression1, expression2
# 等价于：
if __debug__:
    if not expression1: raise AssertionError(expression2)
```

也就是说，断言失败时抛出的是 `AssertionError` 异常（可被 `try/except` 捕获），第二个表达式作为错误消息。关键开关是内置变量 `__debug__`：正常运行时为 `True`；当用 `-O`（optimize）启动解释器时，`__debug__` 变为 `False`，且官方明确「当前的代码生成器在请求优化时对 assert 语句不产生任何字节码」——等价于 C 的 `NDEBUG`。

本机实测（Python 3.11.15）：

```python
print("__debug__ =", __debug__)
try:
    assert 2 + 2 == 5, "math is broken"
except AssertionError as e:
    print("caught AssertionError:", e)
```

```
$ python3 passert.py
__debug__ = True
caught AssertionError: math is broken

$ python3 -O passert.py
__debug__ = False
```

普通运行时断言失败、被捕获，打印出消息 `math is broken`；加 `-O` 后 `__debug__` 变 `False`，断言语句被整体跳过（没有再打印 "caught…"，因为它根本没触发）。C 与 Python 在这一点上高度一致：断言是「可关闭的开发期检查」。

C 的 `assert` 失败 → `abort()` → `SIGABRT`（进程被杀，退出码 134），是硬停；Python 的 `assert` 失败 → 抛 `AssertionError`（可捕获），是软停。但「都可被关闭」这一点相同——开发开、发布关。

### 8.1.5 别拿 assert 当输入校验（最高频误用）

一条贯穿两种语言的纪律：断言只用于捕捉程序内部的逻辑错误（「这里本不该发生」），绝不用于校验外部输入或处理可预期的失败。因为断言在优化 / `NDEBUG` 模式下会被整体删除，任何依赖它的「保护」都会在发布版里凭空消失。

Python 里写 `assert user_age >= 0` 来校验用户输入的年龄。开发时它似乎工作，但线上用 `-O` 跑时这行被删掉，恶意或错误输入长驱直入。正确做法是用显式判断 + 抛异常 / 返回错误：

```python
if user_age < 0:
    raise ValueError("age must be non-negative")
```

如果这个检查在「关掉断言」后消失了会导致安全或正确性问题，那它就不该是断言，而应是常规的错误处理逻辑。断言留给「若失败则说明我的代码有 bug」的场合。

#### 来源与时效（本小主题）
- 一手：ISO/IEC 9899:2011 (C11) §7.2 Diagnostics `<assert.h>`——`assert` 实参「compares equal to 0」时把「实参文本 + 文件名 + 行号 + 函数名」写到 stderr 后调用 `abort`；定义 `NDEBUG` 则展开为 `((void)0)`、实参不求值（条文经 cppreference C 版 error/assert 词条转述交叉核对，核实 2026-07-26）。
- 一手：Python 3.11 Language Reference §7.3「The assert statement」——两种形式的等价展开、`AssertionError`、`__debug__` 与 `-O` 语义，https://docs.python.org/3.11/reference/simple_stmts.html （核实 2026-07-26）。
- 实测比对：本机 gcc 13.3.0 跑 C `assert(x==5)` 得诊断行 `cassert.c:6: main: Assertion 'x == 5' failed.` + `Aborted` + 退出码 134（=128+6=SIGABRT）；`-DNDEBUG` 后同代码正常跑完、退出码 0。本机 Python 3.11.15 普通运行断言抛 `AssertionError("math is broken")`；`-O` 下 `__debug__=False`、断言不触发。均与两份规范一致。
- 冲突项：无。C11 与 Python 官方语义各自独立，且实测同向坐实「断言可失败、可关闭、关闭即整体消失」。退出码 134 = 128+信号号 依赖 POSIX/shell 约定，非 C 标准。

---

## 8.2 前置/后置条件（契约式）

### 8.2.1 函数契约：前置条件 requires 与后置条件 ensures

契约式设计（design by contract）把一个函数看成调用方与被调方之间的一份合同，由两类断言表达：

- 前置条件（precondition）：调用这个函数前，调用方必须保证成立的条件（对参数 / 程序状态的要求）。
- 后置条件（postcondition）：只要前置条件被满足，被调方保证在返回时成立的条件（对返回值 / 效果的承诺）。

契约把「一个函数在什么前提下、承诺做到什么」写成可核对的断言，而不是散落在注释里的自然语言。它是 8.1 断言的「制度化」：前置条件是放在函数入口的断言，后置条件是放在函数出口的断言。

一个「整数平方根」函数 `isqrt(x)` 的契约可以是「前置：`x >= 0`；后置：`result*result <= x < (result+1)*(result+1)`」。调用方负责传非负数，被调方负责返回正确的下取整平方根。责任一分，双方都能独立推理自己那半边的正确性——这正是契约的价值：把大程序的正确性论证拆成一个个可局部完成的小论证。

### 8.2.2 以 CMU 15-122 / C0 契约注解为锚

本主题的权威锚是 CMU 15-122 使用的教学语言 C0，它把契约做成一等的注解语法。C0 有四类契约指令，都写成以 `//@` 开头的特殊注释：

```c
//@requires  <bool 表达式>;   // 前置条件
//@ensures   <bool 表达式>;   // 后置条件，可用 \result 指代返回值
//@loop_invariant <bool 表达式>;  // 循环不变式（见 8.3）
//@assert    <bool 表达式>;   // 中途断言
```

其中 `\result` 是后置条件里专用的特殊变量，指代函数的返回值；数组还有 `\length(A)` 之类的特殊算子。一个函数契约的骨架大致长这样：

```c
int pow(int x, int y)
//@requires y >= 0;
//@ensures \result == POW(x, y);
{ ... }
```

这些契约是可动态检查的。用 15-122 的工具（`cc0` 编译器 / `coin` 解释器）编译时加 `-d`（dynamic checking）标志，契约会在运行时被求值，违反即报错并指出是哪条契约、在哪个函数失败；不加 `-d`，注解被忽略、程序照跑。这与 8.1 的「断言可开关」是同一套哲学，只是把它系统化成了语言级注解。

C0 是教学子集，其 `//@` 契约语法不是标准 C / C11 的一部分——在普通 `gcc` 里 `//@requires...` 只是一行普通注释、不会被检查。要在标准 C 里近似契约，就用 8.1 的 `assert`：入口 `assert(前置)`、出口 `assert(后置)`。（因本机未安装 `cc0`，C0 的 `-d` 动态检查行为以 15-122 官方讲义 / C0 参考手册为准，未在本基线实机复现，此点如实标注。）

### 8.2.3 契约把正确性论证「分而治之」

契约最重要的思想收益是责任划分：一旦前后置条件写定，调用方只需保证前置、可直接信任后置；被调方只需在「前置成立」的假设下做到后置，无需操心调用方怎么用。双方都能只看契约、不看对方实现地推理。

从「把整个程序在脑子里跑一遍」转向「逐个函数按契约局部验证」。它也直接为 8.3 铺路——循环不变式就是「把契约式推理用到循环内部」的产物：用不变式这个中间断言，把「循环体每转一圈都维持某性质」局部地论证清楚，最终推出函数的后置条件。

#### 来源与时效（本小主题）
- 一手：CMU 15-122《Principles of Imperative Computation》Lecture 1 Contracts（Fall 2025 版讲义），https://www.cs.cmu.edu/~15122/handouts/lectures/01-contracts.pdf ——四类契约 `//@requires`/`//@ensures`/`//@loop_invariant`/`//@assert`、`\result`、`-d`（`cc0`/`coin`）启用动态检查（核实 2026-07-26；PDF 为二进制流，具体语法经 15-122 课程材料与 C0 Reference/C0 Tutorial 交叉转述核对）。
- 一手：C0 Reference（Frank Pfenning，15-122）与 C0 Tutorial（Contracts / Reasoning with loop invariants），http://c0.typesafety.net/tutorial/Contracts.html ——`\result`、`\length`、`//@` 注解语法、不加 `-d` 则忽略注解（核实 2026-07-26）。
- 待核 / 未验证：C0 的 `-d` 动态契约检查未在本基线实机复现（本机无 `cc0`/`coin`）；契约违反的确切报错文本以官方为准，不臆造。C0 契约语法为教学语言特性，非 ISO C。
- 冲突项：无。15-122 讲义与 C0 Reference 对四类契约与 `\result` 表述一致。

---

## 8.3 循环不变式与终止性

### 8.3.1 循环不变式：初始化 / 保持 / 终止三段论

循环不变式（loop invariant）是一个在循环每一轮迭代开始时都成立的断言，用来论证「这个循环确实算出了想要的东西」。CLRS（Ch2，以插入排序为例）给出了论证一条循环不变式的三个性质：

```
初始化（Initialization）：第一次迭代前，不变式为真。
保持（Maintenance）：若某次迭代前为真，则下次迭代前仍为真。
终止（Termination）：循环结束时，不变式给出一个能推出算法正确的有用性质。
```

这三步和数学归纳法是同构的：初始化 = 基础步，保持 = 归纳步，终止 = 用归纳结论收尾。（15-122 用 INIT / PRES 两个点检查不变式的建立与保持，再结合循环退出条件推出后置条件，是同一套框架的另一种叫法。）

不变式是你对「循环转到一半时，已完成的部分长什么样」的精确描述。比如插入排序，不变式是「子数组 `A[0..i-1]` 已排好序」；循环开始时这段为空（初始化成立），每轮把 `A[i]` 插入到正确位置后这段仍有序（保持），循环结束时 `i` 走到末尾、整个数组有序（终止得到正确性）。找对不变式，循环的正确性论证就水到渠成。

### 8.3.2 用断言插桩把不变式变成可执行检查

不变式不只是纸上论证，它可以直接用断言插桩（instrument）成运行时检查：在循环体顶部放一条 `assert(不变式)`，让机器每一轮都替你核对它是否真的被维持。若某轮断言失败，就精确定位到「不变式在这里被破坏」，是查循环 bug 的利器。

本机实测（Python 3.11.15）——对「求 0 到 n 之和」的循环，取不变式

```
s == i*(i-1)//2      # 「已累加了 0..i-1，其和为 i(i-1)/2」
```

在循环各处插桩：

```python
def sum_to(n):
    i, s = 0, 0
    assert s == i*(i-1)//2          # 初始化：i=0,s=0 时 0==0
    while i <= n:
        assert s == i*(i-1)//2      # 保持：每轮顶部都应成立
        s += i
        i += 1
    assert s == n*(n+1)//2          # 终止 -> 后置条件（高斯求和）
    return s
```

```
$ python3 invariant.py
sum_to(0) = 0    (gauss=0)
sum_to(1) = 1    (gauss=1)
sum_to(5) = 15   (gauss=15)
sum_to(100) = 5050  (gauss=5050)
```

四个规模的断言全部通过、结果与高斯公式 `n(n+1)/2` 逐一吻合，说明这条不变式确实被维持、且终止时化为正确的后置条件。这就是「契约式思维（8.2）+ 断言（8.1）」在循环内部的合体应用。

### 8.3.3 终止性：还需要一个「严格递减的度量」

不变式保证「循环若结束，结果是对的」，但它本身不保证循环一定会结束。终止性（termination）要单独论证：通常的办法是找一个循环变式（loop variant / decreasing measure）——一个取值在良基集合（如非负整数）里、每轮严格减小的量，减无可减时循环必然退出。

以上面的 `sum_to` 为例，度量可取 `n - i`：每轮 `i` 加 1，`n - i` 严格减小，且它有下界（到 0 时循环条件 `i <= n` 不再满足），所以循环必然终止。对初学者的完整心智模型因此是「两条腿」：

- 部分正确性：靠循环不变式（8.3.1 三段论）——「如果停，答案对」。
- 终止性：靠严格递减的度量——「一定会停」。

两者合起来才叫「完全正确」。常见 bug（死循环）几乎都是终止性那条腿断了：度量没有真正减小（比如忘了 `i += 1`，或递减条件写反）。写循环时的自检：每转一圈，我那个「越来越小」的量真的变小了吗？

#### 来源与时效（本小主题）
- 一手：CLRS《Introduction to Algorithms》Ch2「Getting Started」（插入排序）——循环不变式三性质 Initialization / Maintenance / Termination，及其与数学归纳法的类比（核实 2026-07-26；经多份 CLRS 章节解答与课程讲义交叉核对表述一致）。
- 一手：CMU 15-122 / C0 Tutorial「Reasoning with loop invariants」——`//@loop_invariant` 的 INIT（建立）/ PRES（保持）检查 + 结合退出条件推出后置条件，http://c0.typesafety.net/tutorial/Reasoning-with-loop-invariants.html （核实 2026-07-26）。
- 实测比对：本机 Python 3.11.15 对 `sum_to` 插桩不变式 `s == i*(i-1)//2`，四个规模断言全过、结果与高斯公式 `n(n+1)/2` 吻合（0/1/15/5050），独立坐实「初始化—保持—终止」链条。
- 冲突项：无。CLRS 的「Initialization/Maintenance/Termination」与 15-122 的「INIT/PRES + 退出」为同一框架的两种命名，非分歧。终止性需独立度量论证，属通用理论共识。

---

## 8.4 基础调试工具

### 8.4.1 打印调试：最朴素也最常用

打印调试（print debugging）指在可疑代码处插入打印语句（C 的 `printf`、Python 的 `print`），把变量当前值、执行是否到达某处直接打到屏幕上，靠观察输出来定位 bug。它零门槛、无需任何工具，是绝大多数程序员遇到问题时的第一反应。

它的价值在于快、直接、跨环境可用；局限在于要反复改代码、重跑，且信息是「事后一次性」的（打了什么才看得到什么），面对复杂控制流或难复现的 bug 会力不从心。给初学者的实用建议：打印时带上「是谁、在哪」的标签（如 `print("after loop, i =", i)`），别只打一个裸值，否则输出一多就分不清谁是谁。C 侧还要注意：`printf` 到 `stdout` 常有缓冲，程序崩溃前缓冲区可能没刷出，调试崩溃问题时可打到 `stderr`（无缓冲）或加 `fflush(stdout)`，以免「最后一条打印没出来」误导判断。

### 8.4.2 gdb：C 的交互式调试器（断点 / 单步 / 查看）

gdb（GNU Debugger）是 C/C++ 的标准交互式调试器：它能在不改源码的前提下，让程序在指定位置暂停（断点 breakpoint）、一行一行往下走（单步 step/next）、随时查看和修改变量、崩溃后回看调用栈（backtrace）。用它的前提是编译时带调试信息：`gcc -g`（并建议 `-O0` 关优化，否则变量可能被优化掉、行号对不上）。

本机实测（gcc 13.3.0 `-g -O0`，gdb 15.1）：在函数 `add` 处下断点、运行、查看参数、单步：

```c
int add(int a, int b){ int s = a + b; return s; }
int main(void){ int r = add(10, 32); printf("r=%d\n", r); return 0; }
```

```
$ gcc -std=c11 -g -O0 dbg.c -o dbg
$ gdb -q -batch -ex "break add" -ex run -ex "print a" -ex "print b" ./dbg
Breakpoint 1 at 0x1157: file dbg.c, line 2.
Breakpoint 1, add (a=10, b=32) at dbg.c:2
2	int add(int a, int b){ int s = a + b; return s; }
$1 = 10
$2 = 32
```

程序在进入 `add` 时停下，gdb 显示断点位置与实参 `a=10, b=32`，`print a` / `print b` 分别取到 10 与 32。常用命令速查：`break <函数/文件:行>`（下断点）、`run`（启动）、`next`（单步、不进入函数）、`step`（单步、进入函数）、`print <表达式>`（求值查看）、`continue`（继续到下个断点）、`backtrace`（打印调用栈，配合 4.2 的递归栈观察）。

### 8.4.3 pdb：Python 的交互式调试器

pdb 是 Python 标准库自带的交互式调试器，能力与 gdb 类比：断点、单步、查看变量、看栈帧。最快的两种用法是命令行 `python3 -m pdb your.py`（从头进入调试），或在源码里想暂停的地方直接写一行 `breakpoint()`（Python 3.7+ 内置，运行到此自动进入 pdb）。

本机实测（Python 3.11.15）——用 `-m pdb` 启动、在函数 `add` 下断点后运行到断点、进入函数体：

```
$ python3 -m pdb pdbdemo.py
(Pdb) break add
Breakpoint 1 at .../pdbdemo.py:1
(Pdb) run
(Pdb) continue
> .../pdbdemo.py(2)add()
-> s = a + b
```

程序停在 `add` 内的 `s = a + b` 一行，此处即可用 `args`（打印当前函数参数）、`p a+b`（求值表达式）、`n`（单步过）、`s`（单步入）、`c`（继续）、`l`（列出附近源码）、`w`（看调用栈）等。pdb 命令与 gdb 高度相似（`b`/`n`/`s`/`c`/`p`/`w`），学会一个另一个几乎无缝迁移——这是交互式调试器的通用词汇表。

### 8.4.4 断言与消毒器：调试的「自动哨兵」

除了「人盯」的打印和交互式调试，还有一类「让机器自动抓现行」的手段，与本大主题前几节直接相连：把断言（8.1）/ 不变式插桩（8.3）留在代码里，程序一旦进入非法状态就当场喊停，等于在关键路口布下自动哨兵，比事后翻打印高效得多。

C 侧还有编译器消毒器（sanitizer）作为运行时探测器：`gcc -fsanitize=address`（ASan，抓越界 / 释放后使用 / 泄漏）、`-fsanitize=undefined`（UBSan，抓有符号溢出等未定义行为）。它们在大主题2（UB 陷阱）、大主题6（指针与内存陷阱）里有具体演示，此处只作「调试工具箱」的一员点名：定位内存 / UB 类 bug 时，消毒器往往比 gdb 单步更快指到病灶。给初学者的排错顺序直觉：先靠断言 / 消毒器让 bug「自己暴露位置」，再用 gdb/pdb 到现场「看清细节」，打印调试作为随手补充。

#### 来源与时效（本小主题）
- 一手：Python 3.11 官方文档——`pdb`「The Python Debugger」（`python -m pdb`、`breakpoint()`、`break`/`args`/`next`/`step`/`continue` 等命令），https://docs.python.org/3.11/library/pdb.html （核实 2026-07-26）。
- 一手：GNU gdb 官方文档 / gdb 15.1 内置帮助——`break`/`run`/`next`/`step`/`print`/`backtrace` 语义；`gcc -g` 生成调试信息（核实 2026-07-26，本机 gdb 15.1 / gcc 13.3.0）。
- 实测比对：本机 gdb 15.1 在 C 函数 `add` 下断点，观察到 `Breakpoint 1, add (a=10, b=32) at dbg.c:2`、`print a`→10、`print b`→32；本机 Python 3.11.15 `-m pdb` 在 `add` 下断点并停在 `s = a + b`。命令行为与文档一致。
- 冲突项：无。gdb 与 pdb 命令集各自独立，实测均按文档语义工作。`printf` 缓冲行为属实现层常识（stdout 行缓冲 / 全缓冲随是否连终端而变），非规范强约束。

---

## 8.5 效率与增长阶入门直觉

### 8.5.1 先学会「数基本操作」

衡量一段程序快不快，入门的第一步不是掐秒表，而是数它执行了多少次基本操作（比较、赋值、算术等），并看这个次数如何随输入规模 n 变化。计数是机器无关的：它不受 CPU 主频、语言、编译器影响，抓住的是算法本身的「工作量随规模怎么长」。

在长度为 n 的列表里「求和」，循环体执行 n 次加法，操作数 ~ n；而「数出所有数对」用双重循环，操作数 ~ n×n。前者规模翻倍工作量翻倍，后者规模翻倍工作量翻四倍——这种「增长的形状」才是效率的本质，也是 CS61A 讲 orders of growth（增长阶）时反复强调的视角。计数让你在写完（甚至没写完）代码时就能预判「输入变大 10 倍会不会慢到不可接受」。

### 8.5.2 粗略增长阶：只看主导项、忽略常数（正式化归 L2-01/L3-01）

把操作数随 n 的增长「粗粒度」地分类，就得到增长阶的直觉：只保留随 n 增长最快的那一项、扔掉低阶项和常数系数。常见几档由慢到快（此处仅记号直觉，形式化定义归后续课程）：

```
常数 1  <  对数 log n  <  线性 n  <  n log n  <  平方 n^2  <  指数 2^n
```

为什么可以扔掉常数和低阶项？因为当 n 足够大时，主导项完全压过其余部分：`n^2 + 100n + 999` 在 n 很大时几乎就等于 `n^2`，那 100n 和常数无关紧要。对初学者，这一步的意义是「抓大放小」——比较两个算法谁更能扛大输入，看增长阶就够了，不必纠结具体系数。

必须点明的边界：这里给的是入门直觉，不是定义。Big-O / Θ / Ω 的精确定义（存在常数 c 和 n₀ 使得……）、以及如何严格证明一个算法的复杂度，正式化归 L2-01（数据结构）与 L3-01（算法）。本报告只让你「会数操作、会认增长阶的相对快慢」。

### 8.5.3 用 timeit 观察规模曲线

理论上的增长阶可以用实测的规模曲线加以印证：Python 标准库 `timeit` 能较可靠地测一段代码的运行时间（它多次重复、减少噪声）。把输入规模 n 逐步放大、记录耗时，就能「看见」增长阶——线性的时间随 n 成比例，平方的时间随 n 平方式增长。

本机实测（Python 3.11.15，`timeit`）：一个 O(n) 的求和与一个 O(n²) 的双重循环计数：

```
O(n) 线性求和：
  n= 1000       21.3 us
  n= 2000       43.0 us
  n= 4000       85.5 us
  n= 8000      176.2 us
O(n^2) 双重循环：
  n=  100      0.176 ms
  n=  200      0.726 ms
  n=  400      3.111 ms
  n=  800     13.578 ms
```

O(n) 那组，n 每翻一倍时间也约翻一倍（21→43→85→176），是直线增长；O(n²) 那组，n 每翻一倍时间约翻四倍（0.18→0.73→3.1→13.6），是平方增长。这与 8.5.1 数出来的「操作数 ~ n」和「~ n²」精确对上——理论计数与实测曲线互为印证。

其一，实测受机器 / 缓存 / 解释器影响，小 n 时噪声和常数开销会掩盖增长阶，要看大 n 的趋势而非单点绝对值；其二，实测能佐证但不能替代分析——换台机器绝对数就变，唯有增长阶（曲线的形状）是稳定的。所以「先分析增长阶、再用 timeit 印证形状」是正确的次序。

#### 来源与时效（本小主题）
- 一手：Berkeley CS61A（Efficiency / Orders of Growth 讲次；计数操作、增长阶直觉、Θ 记号用于 orders of growth），https://cs61a.org/ （核实 2026-07-26；线上为 Summer 2026 学期，Python 为主）。
- 一手：Python 3.11 官方文档——`timeit`「measure execution time of small code snippets」，https://docs.python.org/3.11/library/timeit.html （核实 2026-07-26）。
- 实测比对：本机 Python 3.11.15 用 `timeit` 测 O(n) 求和与 O(n²) 双重循环，n 翻倍时耗时分别约 ×2 / ×4（线性 21→43→85→176 us；平方 0.18→0.73→3.1→13.6 ms），与「操作数 ~ n / ~ n²」的计数分析吻合。
- 待核 / 边界：Big-O/Θ/Ω 的形式化定义与证明本报告不给，归 L2-01/L3-01；此处仅入门直觉。实测绝对时间依赖本基线机器，仅用于印证增长阶「形状」，非可移植常量。
- 冲突项：无。CS61A 的增长阶直觉与本机 `timeit` 规模曲线同向。
