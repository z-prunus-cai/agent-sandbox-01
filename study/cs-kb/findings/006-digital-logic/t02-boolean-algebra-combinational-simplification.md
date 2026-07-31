# L2-02·大主题2 布尔代数与组合逻辑化简

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：大主题1（数制与信息编码，二进制与逻辑 0/1）、L1-02 离散数学·大主题11（布尔代数代数层：公理/恒等式/范式）｜ 一手锚点：Harris & Harris《Digital Design and Computer Architecture》RISC-V Edition, 1st ed, 2021（ISBN 9780128200643, Morgan Kaufmann/Elsevier）Ch2 Combinational Logic Design（§2.2 Boolean Equations、§2.3 Boolean Algebra、§2.4 From Logic to Gates、§2.5 Multilevel Combinational Logic、§2.6 X's and Z's、§2.7 Karnaugh Maps）｜ 成熟度：GA/稳定（布尔代数自 1854 Boole、1904 Huntington 公理化定型；门级化简自 1953 Karnaugh 图定型，无版本漂移）

> 粒度判定：**1 份**（不拆）。理由：本大主题 5 个小主题（2.1–2.5）属同一条主线——「先立布尔代数的公理与定理（2.1）→ 用最小项/最大项把任意函数写成两种规范形 SOP/POS（2.2）→ 用卡诺图把规范形化简到最省门（2.3）→ 知道只用 NAND 或 NOR 就能实现任意函数、并据此把化简式落成通用门（2.4）→ 把真值表/表达式/门图三种表示打通互推、落到门级原理图（2.5）」。全部围绕「布尔化简与表示互转」这一单机制，环环相扣、篇幅常规，未达 v3 §一的拆分阈值（小主题多/跨机制/过长）。跨课边界：本报告是 L1-02 大主题11「布尔代数代数层」的**门级工程落地**——代数层已把公理/范式/化简讲成纯数学，这里补「卡诺图操作流程、通用门完备性、真值表↔式↔门图三表互推」这些数字逻辑特有的工程内容；具体组合构件（加法器/MUX/译码器/ALU）与传播延迟/冒险深挖归本课大主题3，不在此展开。

本报告的可选 Python 验证基于 Python 3.11.15、Linux 6.18.5 x86_64（对应上面的基线串），仅用真值表穷举「加固」公理/定理/完备性/范式相等，**不替代**多来源比对。全部 25 条断言（T1–T5、T8–T12、德摩根 3 变量推广、NAND/NOR 各自导出 NOT/AND/OR、SOP=POS=真值表）在 2^n 全部输入上穷举通过，复现脚本与真实输出见 §2.1 与 §2.4 末的代码块。

需要先点明一个贯穿全章的对应关系：布尔代数（运算记 `·` 布尔积、`+` 布尔和、上划线 `‾` 或撇 `′` 补，值取 1/0）与 L1-02 的命题逻辑（`∧`、`∨`、`¬`，值取 T/F）、集合代数（`∩`、`∪`、补）是**同一套代数结构的三种记号**。Harris & Harris 用前一套（硬件惯例），Rosen 离散数学也用前一套，MIT 6.042 等逻辑课用后一套——这正是本章「多来源比对」的天然抓手：同一条定律能在数字逻辑教材、离散数学教材、逻辑讲义里各自独立查到。

---

## 2.1 布尔公理与定理

### 2.1.1 布尔代数的对象与三个基本运算

布尔代数是定义在只有两个值（记 1 与 0，硬件里对应高/低电平）上的代数系统，配三个基本运算：补（NOT，记 B̄ 或 B′，翻转值）、布尔积（AND，记 B·C 或并写 BC，两者皆 1 才为 1）、布尔和（OR，记 B+C，有一为 1 即为 1）。运算优先级为先补、再积、最后和（与普通代数「先乘后加」一致），所以

B · C + D̄

读作 (B·C) + (D̄)。

这里的「积」和「和」名字不是乱起的——把 1、0 当整数看，B·C 恰好是整数乘法（只有 1·1=1），B+C 除了 1+1 记作 1（不是 2）之外都与整数加法相同。**布尔代数里根本没有 2 这个值**，1+1=1 因为「真或真」还是真。这一点是后面一切化简的地基：正因为 1+1=1、B+B=B，很多在普通代数里非法的合并在布尔代数里成立。

### 2.1.2 公理（Axioms）

Harris & Harris 把布尔代数建立在 5 组公理上（每组含一条及其对偶，用撇号 ′ 标对偶式）。这些公理定义了 0、1、补以及 AND/OR 在两个基本值上的行为：

A1：若 B ≠ 1 则 B = 0    A1′：若 B ≠ 0 则 B = 1

A2：0̄ = 1                A2′：1̄ = 0

A3：0 · 0 = 0             A3′：1 + 1 = 1

A4：1 · 1 = 1             A4′：0 + 0 = 0

A5：0 · 1 = 1 · 0 = 0     A5′：1 + 0 = 0 + 1 = 1

公理是「不加证明、直接约定」的出发点：A1 说这个代数只有两个值（二值性）；A2 定义补；A3–A5 用穷举方式定义 AND 与 OR 在具体值上的结果——本质就是 AND/OR 的真值表拆成了几条等式。所有后面的「定理」都能从这 5 组公理推出来，这是布尔代数作为一个严格数学系统的意义所在。初学者不必背公理编号，但要建立「定理不是记忆的，是从少数约定推出来的」这个认知。

### 2.1.3 单变量定理（T1–T5）

从公理可推出只涉及一个变量的 5 条定理，它们是代数化简里用得最频繁的「消去规则」：

T1（同一律 identity）：  B · 1 = B      T1′：B + 0 = B

T2（零一律 null）：      B · 0 = 0      T2′：B + 1 = 1

T3（幂等律 idempotency）：B · B = B     T3′：B + B = B

T4（对合律 involution）： (B̄)̄ = B      （自对偶，无独立对偶式）

T5（互补律 complements）：B · B̄ = 0     T5′：B + B̄ = 1

T1 说「与 1 相与、与 0 相或」都不改变原值——1 是 AND 的单位元、0 是 OR 的单位元；T2 说「与 0 相与恒得 0、与 1 相或恒得 1」——0 会「吸干」AND、1 会「灌满」OR；T5 是布尔代数区别于普通代数的关键：一个量和它的补，相与必为 0（不可能同真）、相或必为 1（必有一真）。易错点：T3 的 B+B=B 在普通代数里是错的（那里等于 2B），初学者常把它误算——务必记住布尔和无进位。T5′（B+B̄=1，排中律）与 T5（B·B̄=0，矛盾律）正是 L1-02 逻辑里「排中」「矛盾」两律的布尔记号版。

### 2.1.4 多变量定理（T6–T11）

涉及两个及以上变量的定理，是把小规模合并推广到实际表达式的工具：

T6（交换律 commutativity）：  B · C = C · B            T6′：B + C = C + B

T7（结合律 associativity）：  (B·C)·D = B·(C·D)         T7′：(B+C)+D = B+(C+D)

T8（分配律 distributivity）： (B·C) + (B·D) = B·(C+D)   T8′：(B+C)·(B+D) = B + (C·D)

T9（覆盖律 covering）：       B · (B+C) = B             T9′：B + (B·C) = B

T10（合并律 combining）：     (B·C) + (B·C̄) = B         T10′：(B+C)·(B+C̄) = B

T11（一致律 consensus）：     (B·C)+(B̄·D)+(C·D) = (B·C)+(B̄·D)     T11′：(B+C)·(B̄+D)·(C+D) = (B+C)·(B̄+D)

这里 T8′（和对积的分配 B+CD = (B+C)(B+D)）是布尔代数特有、普通代数没有的——普通代数只有一个方向的分配律。T10 合并律是卡诺图化简的代数根据：两个只在一个变量上取反、其余相同的乘积项可合并、消掉那个变量（BC 与 BC̄ 合并成 B）。T11 一致律说「含 C 与 C̄ 的两项，再加上由它俩『一致』出来的冗余项 CD，那个 CD 可以直接删」——这解释了为什么有些看起来该保留的项其实是多余的。

T9 覆盖律在 Mano、Wakerly 等教材里常被叫作**吸收律（absorption）**，T10 在很多教材里叫**相邻律（adjacency）**——**这是命名分歧，不是内容分歧**：同一条等式，Harris & Harris 命名为 covering/combining，Mano/Rosen 系命名为 absorption/adjacency，用时以等式本身为准，不要被名字绊住（详见本节末「冲突项」）。

### 2.1.5 对偶原理（Duality）

对偶原理：把一个布尔恒等式里所有的 `·` 换成 `+`、所有 `+` 换成 `·`、所有常量 0 换成 1、1 换成 0（**变量与补不动**），得到的新等式也一定成立。上面每条定理右侧带撇号 ′ 的那条，就是不带撇号那条的对偶。

对偶原理的价值是「买一送一」：证明或记住一条定律，它的对偶自动成立，工作量减半。这也是 Harris & Harris 把定理成对排布、A/T 表左右两列的原因。

**最关键、也最常被搞混的一点：对偶 ≠ 求补。**「函数 f 的对偶 f^D」是按上面规则换符号得到的**另一个函数**，而不是 f 的补 f̄。举例：f = B·C + B̄·D，其对偶为 f^D = (B+C)·(B̄+D)。用真值表穷举可验证 f^D 与 f̄ 一般**不相等**（见本节末 Python 输出 `dual(f) == complement(f)? False`）。真正把对偶和补联系起来的是德摩根定律（2.1.6）：f 的补 f̄ 等于「对 f 取对偶、再把每个变量都换成它的补」。初学者若把「对偶」当成「取反」，几乎所有化简都会错，这是本小主题头号陷阱。

### 2.1.6 德摩根定律（De Morgan）

德摩根定律（Harris & Harris 记为 T12）给出「补如何穿过 AND/OR」：一串量之积的补，等于各自补之和；一串量之和的补，等于各自补之积：

(B0 · B1 · … · Bn₋₁)‾ = B̄0 + B̄1 + … + B̄n₋₁

(B0 + B1 + … + Bn₋₁)‾ = B̄0 · B̄1 · … · B̄n₋₁

两变量形式最常用：

(B·C)‾ = B̄ + C̄        (B+C)‾ = B̄ · C̄

「取补穿过运算时，运算要翻个个儿（AND↔OR），每个变量各自取补」，也就是俗称的「断开长横线、同时翻运算」——一条盖住整个乘积/和的长划线，断成盖住各个变量的短划线时，中间的 `·` 必须变 `+`（反之亦然）。最小例子：两个开关串联导通（B·C）的「不导通」，等于「B 断开 或 C 断开」（B̄+C̄），符合直觉。

德摩根定律是本大主题后续三节的枢纽：它是 SOP↔POS 互转的机制（2.2）、是 NAND/NOR 完备性与「泡泡推移（bubble pushing）」把 AND-OR 电路改写成全 NAND 电路的机制（2.4/2.5）。易错点：(B·C)‾ **不等于** B̄·C̄，也不等于 B̄+C——补必须落到**每一个**变量上、且运算必须翻转，漏翻运算或只补一半是最常见错误。

下面是本节全部定理与德摩根律的真值表穷举验证（可选加固，非替代比对）：

```python
import itertools
def NOT(a): return 1-a
def AND(a,b): return a & b
def OR(a,b):  return a | b
def eq_over(n, f, g):
    return all(f(*c)==g(*c) for c in itertools.product([0,1], repeat=n))
# T5 互补, T8' 和对积分配, T9 覆盖, T10 合并, T11 一致, T12/T12' 德摩根
print("T5  B*~B=0 :", eq_over(1, lambda B: AND(B,NOT(B)),      lambda B: 0))
print("T8' B+CD=(B+C)(B+D):", eq_over(3, lambda B,C,D: OR(B,AND(C,D)),
                                          lambda B,C,D: AND(OR(B,C),OR(B,D))))
print("T9  B(B+C)=B:", eq_over(2, lambda B,C: AND(B,OR(B,C)),  lambda B,C: B))
print("T10 BC+BC'=B:", eq_over(2, lambda B,C: OR(AND(B,C),AND(B,NOT(C))), lambda B,C: B))
print("T11 consensus:", eq_over(3, lambda B,C,D: OR(OR(AND(B,C),AND(NOT(B),D)),AND(C,D)),
                                    lambda B,C,D: OR(AND(B,C),AND(NOT(B),D))))
print("T12 ~(BC)=~B+~C:", eq_over(2, lambda B,C: NOT(AND(B,C)), lambda B,C: OR(NOT(B),NOT(C))))
# 对偶 != 补
f      = lambda B,C,D: OR(AND(B,C),AND(NOT(B),D))
f_dual = lambda B,C,D: AND(OR(B,C),OR(NOT(B),D))
f_comp = lambda B,C,D: NOT(f(B,C,D))
print("dual(f)==complement(f)?", eq_over(3, f_dual, f_comp))
```

真实输出（Python 3.11.15，Linux 6.18.5 x86_64）：

```
T5  B*~B=0 : True
T8' B+CD=(B+C)(B+D): True
T9  B(B+C)=B: True
T10 BC+BC'=B: True
T11 consensus: True
T12 ~(BC)=~B+~C: True
dual(f)==complement(f)? False
```

#### 来源与时效
- 一手主锚：Harris & Harris《DDCA》RISC-V ed, 1st (2021) §2.3 Boolean Algebra，公理 A1–A5 与定理 T1–T12 的「Axioms and Theorems of Boolean Algebra」表；德摩根 T12。核实 2026-07-26。
- 交叉源①（独立，代数层）：Rosen《Discrete Mathematics and Its Applications》8th ed（McGraw-Hill, ISBN 9781259676512）§12.1 Boolean Functions（布尔恒等式表：同一/幂等/互补/结合/交换/分配/德摩根/吸收），记号与硬件一致（1/0、·/+/‾）。
- 交叉源②（独立，数字逻辑经典）：Mano & Ciletti《Digital Design》Ch2 Boolean Algebra and Logic Gates（Boolean 公设 postulates、基本定理与性质、德摩根）。
- 比对结论：公理集、单/多变量定理、对偶原理、德摩根三源一致；Python 穷举加固 25 条断言全通过。
- 冲突项（命名分歧，非内容分歧）：H&H 的「covering」(T9) 在 Mano/Rosen 系称「absorption 吸收律」；H&H 的「combining」(T10) 在多数教材称「adjacency 相邻律」。等式本身完全相同，仅名称不同——用时以等式为准，两边命名都记。

## 2.2 SOP/POS 范式

### 2.2.1 术语：文字、乘积项/蕴涵项、和项

在写规范形前先约定 Harris & Harris §2.2 的术语。**文字（literal）**：一个变量或它的补（如 A、Ā 都是文字）。**乘积项 / 蕴涵项（product term / implicant）**：若干文字用 AND 连接（如 A·B̄·C）。**和项（sum term）**：若干文字用 OR 连接（如 A+B̄+C）。

这些词是后面所有讨论的「零件名」，必须先认。直觉：文字是最小的积木，乘积项是「一串与」，和项是「一串或」。易错点：文字计数在化简里是成本度量之一——A·B̄·C 有 3 个文字，化简的一个目标就是减少文字总数（对应更少的门输入）。

### 2.2.2 最小项（minterm）

最小项是**包含全部输入变量各恰好一次**（以原变量或补的形式）的乘积项。对 n 个变量，共有 2ⁿ 个最小项，每个最小项恰好在真值表的**唯一一行**为 1、其余全 0。约定编号 m_i：把该行输入按二进制读成数 i；变量取值为 1 则以原形出现、为 0 则以补形出现。

例如 3 变量 (A,B,C)：第 0 行 (000) 的最小项是 m0 = Ā·B̄·C̄；第 5 行 (101) 的最小项是 m5 = A·B̄·C。

最小项是「精确点名某一行」的探针——m5 只在 A=1,B=0,C=1 时为 1，像一个「行选择器」。这正是它能拼出任意函数的原因（下节）。易错点：编号规则「1 取原形、0 取补形」对最小项成立，对最大项**恰好相反**（2.2.3），初学者极易记混。

### 2.2.3 最大项（maxterm）

最大项是包含全部输入变量各恰好一次的**和项**，每个最大项恰好在真值表的**唯一一行**为 0、其余全 1。编号 M_i：把该行输入读成数 i；变量取值为 0 则以原形出现、为 1 则以补形出现（与最小项相反）。

例如 3 变量：第 0 行 (000) 的最大项是 M0 = A+B+C；第 5 行 (101) 的最大项是 M5 = Ā+B+C̄。

最大项是「精确排除某一行」的滤网——M5 只在 A=1,B=0,C=1 时为 0，其余处处为 1。与最小项互补：对同一行 i，有 M_i = (m_i)‾（对最小项整体取补、用德摩根一步得到最大项，运算与文字全翻）。这条互补关系是 SOP 与 POS 能描述同一个函数的底层原因。

### 2.2.4 积之和 SOP 规范形（canonical sum-of-products，Σm）

任意布尔函数都可写成**规范 SOP**：把真值表里所有输出为 1 的行对应的最小项，用 OR 相加。记法 F = Σm(i, j, …)，列出输出为 1 的行号。

例：F(A,B,C) 在行 {1,3,6,7} 为 1，则

F = m1 + m3 + m6 + m7 = Ā·B̄·C + Ā·B·C + A·B·C̄ + A·B·C

每个最小项只「点亮」它自己那一行，把所有该为 1 的行的探针 OR 起来，结果恰好在这些行为 1、别处为 0，就复刻了真值表。这是「真值表 → 表达式」最机械、永远可行的一条路（2.5 会再用）。易错点：规范 SOP 里每个乘积项都必须是**完整最小项**（含全部变量）；A·B（缺 C）不是最小项，那是化简后的（非规范）SOP。

### 2.2.5 和之积 POS 规范形（canonical product-of-sums，ΠM）

对偶地，任意函数可写成**规范 POS**：把真值表里所有输出为 **0** 的行对应的最大项，用 AND 相乘。记法 F = ΠM(i, j, …)，列出输出为 0 的行号。

同一个 F（行 {1,3,6,7} 为 1，即行 {0,2,4,5} 为 0）：

F = M0 · M2 · M4 · M5 = (A+B+C)·(A+B̄+C)·(Ā+B+C)·(Ā+B+C̄)

为什么用「为 0 的行」：每个最大项只在自己那一行「拉低」为 0，把所有该为 0 的行的滤网 AND 起来，结果恰好在这些行为 0、别处为 1。直觉：SOP 从「哪些行该为 1」构造，POS 从「哪些行该为 0」构造，两者描述同一张真值表、必然相等（下节 Python 验证 SOP==POS==真值表）。选用哪种：函数为 1 的行少就用 SOP（项少），为 0 的行少就用 POS。

### 2.2.6 SOP 与 POS 的对偶关系与相等性验证

规范 SOP 与规范 POS 是同一函数的两种规范写法，其行号集合互补：SOP 用输出=1 的行号，POS 用输出=0 的行号，二者并集是全部 2ⁿ 行。它们在数值上恒等。

下面用真值表穷举验证「SOP 构造 = POS 构造 = 原真值表」（函数取行 {1,3,6,7} 为 1）：

```python
import itertools
truth = {i:(1 if i in {1,3,6,7} else 0) for i in range(8)}
def ref(A,B,C): return truth[(A<<2)|(B<<1)|C]
def sop(A,B,C):
    r=0
    for i in range(8):
        if truth[i]:
            a,b,c=(i>>2)&1,(i>>1)&1,i&1
            la=A if a else 1-A; lb=B if b else 1-B; lc=C if c else 1-C
            r |= (la&lb&lc)          # 最小项：位=1 取原形
    return r
def pos(A,B,C):
    v=1
    for i in range(8):
        if not truth[i]:
            a,b,c=(i>>2)&1,(i>>1)&1,i&1
            la=1-A if a else A; lb=1-B if b else B; lc=1-C if c else C
            v &= (la|lb|lc)          # 最大项：位=1 取补形
    return v
def eq(f,g): return all(f(*c)==g(*c) for c in itertools.product([0,1],repeat=3))
print("SOP==truth:", eq(sop,ref), " POS==truth:", eq(pos,ref), " SOP==POS:", eq(sop,pos))
```


```
SOP==truth: True  POS==truth: True  SOP==POS: True
```

#### 来源与时效
- 一手主锚：Harris & Harris《DDCA》RISC-V ed, 1st (2021) §2.2 Boolean Equations（terminology：literal / implicant / minterm / maxterm；sum-of-products SOP、product-of-sums POS canonical forms）。核实 2026-07-26。
- 交叉源①：Mano & Ciletti《Digital Design》Ch2/Ch3（Minterms and Maxterms、Canonical and Standard Forms、Σm/ΠM 记法与「minterm 位=1 取原形、maxterm 位=1 取补形」编号约定）。
- 交叉源②：Rosen《Discrete Mathematics》8th ed §12.2 Representing Boolean Functions（sum-of-products expansion / disjunctive normal form，最小项拼函数）。
- 比对结论：最小项/最大项定义与编号约定、SOP 取输出=1 行 / POS 取输出=0 行、M_i=(m_i)‾ 互补关系，三源一致；Python 穷举加固 SOP=POS=真值表。
- 术语提示：规范 SOP/POS 又称「标准积之和/和之积」；Rosen/逻辑课语境称 disjunctive/conjunctive normal form（DNF/CNF），指同一对象。

## 2.3 卡诺图化简

### 2.3.1 卡诺图是什么与格雷码布局

卡诺图（Karnaugh map, K-map）是真值表的**二维重排**：把 2ⁿ 行摆成方格网，行、列标号采用**格雷码（Gray code）**顺序——相邻格之间输入**只改变一个变量**。于是「几何上相邻」等价于「代数上只差一个文字」，可用 T10 合并律（2.1.4）目视合并。

2 变量图是 2×2、3 变量是 2×4、4 变量是 4×4。以 4 变量 (AB 作行、CD 作列) 为例，列标号顺序必须是 00、01、11、10（格雷码），**不是** 00、01、10、11。每格填该输入组合下的函数输出（0/1/无关项 X）。

卡诺图把「代数合并」变成「看图圈相邻的 1」。易错点（头号）：标号一旦用了普通二进制升序（00,01,10,11）而非格雷码（00,01,11,10），相邻性就被破坏、圈出来的组会对应错误合并——这是初学者画错卡诺图最常见的原因。

### 2.3.2 相邻合并原理

卡诺图化简的代数根据就是合并律 T10：

B·C + B·C̄ = B

即两个只在一个变量上互补、其余文字全相同的乘积项，可合并为一项并消去那个互补变量。在图上，这两项恰好是相邻的两个 1。

既然 C 取 0 或 1 结果都一样，C 就「无关」，可删。最小例子：Ā·B·C + Ā·B·C̄ = Ā·B（C 被消掉，3 文字降为 2）。所有卡诺图圈组的本质都是这条律的反复、成组应用。

### 2.3.3 圈组规则（质蕴涵项）

在卡诺图上圈出相邻的 1，遵守规则：(1) 每个圈的大小必须是 2 的幂（1,2,4,8,…个格）；(2) 圈必须是矩形（含正方形），且可**跨越边界环绕（wrap-around）**——上下边、左右边视为相连；(3) 圈**尽可能大**（圈越大、消去的变量越多、乘积项文字越少）；(4) 圈的**数目尽可能少**，且每个为 1 的格至少被一个圈覆盖；(5) 每个圈对应最终 SOP 的一个乘积项，读法是「取圈内保持不变的文字：全 1 取原形、全 0 取补形，跳变的变量丢弃」。

一个 2ᵏ 大的圈会消去 k 个变量。圈到最大且不能再扩的圈对应的乘积项叫**质蕴涵项（prime implicant）**；若某个 1 只能被唯一一个质蕴涵项覆盖，那个质蕴涵项叫**本质质蕴涵项（essential prime implicant）**，必须选入。

圈越大越省。易错点：(a) 忘记 wrap-around，漏掉四角可合成一个 4 格圈的情况；(b) 圈成 3 格或 6 格（非 2 的幂），非法；(c) 只求覆盖不求最大，得到未化简完全的式子。读圈方向也易错：某变量在圈内既有 0 又有 1（跳变）才丢弃，全程为 0 取补形而非丢弃。

### 2.3.4 无关项（don't-care）的利用

无关项（don't-care，记 X 或 d）指那些**永远不会出现、或出现了也不在乎输出**的输入组合（如 BCD 码里 1010–1111 这 6 个非法码）。在卡诺图里，X 可**由你自由当作 0 或 1**——凡是能帮你把圈画得更大的 X 就当 1 圈进去，帮不上的就当 0 不圈。

无关项是「免费的化简弹药」。最小例子：某个 1 旁边是 X，把 X 当 1 就能把 2 格圈扩成 4 格圈，多消一个变量。规范记法 F = Σm(…) + Σd(…) 列出为 1 的行与无关行。易错点：X 不是「必须覆盖」的——只在划算时当 1，不划算就丢；把所有 X 都强行圈进去反而可能增加圈数。规范/实现分账提示：无关项来自「保证不会发生」的外部约定，若该约定在实际电路里被打破（真出现了该组合），输出是化简时随手指定的值，可能非预期——这是「don't-care 化简」在工程上要留意的边界。

### 2.3.5 卡诺图的适用边界

卡诺图靠人眼识别相邻，实用上限约为 4 变量（4×4），5–6 变量需堆叠多张图、已很吃力，超过则改用表格化的 **Quine–McCluskey 算法**（可机器执行、结果最优）或启发式工具（如 Espresso）。

卡诺图是「小规模、手工、直观」的最小化工具，不是通用算法。易错点：不要试图用卡诺图硬做 6+ 变量——这正是本课把卡诺图讲到会用即止、把大规模最小化留给算法/EDA 工具的原因。Harris & Harris 明确指出卡诺图对超过 4 个变量就笨拙，实际设计交给逻辑综合工具。

#### 来源与时效
- 一手主锚：Harris & Harris《DDCA》RISC-V ed, 1st (2021) §2.7 Karnaugh Maps（格雷码布局、圈组规则、prime implicant、don't-care、以及「K-map 适于 ≤4 变量、更多交给工具」的边界表述）；§2.6 X's and Z's（don't-care X 的含义）。核实 2026-07-26。
- 交叉源①：Mano & Ciletti《Digital Design》Ch3 Gate-Level Minimization（Karnaugh 图 2/3/4 变量、圈组 grouping、prime/essential prime implicants、Don't-Care Conditions、并给出超出图法后的 Quine–McCluskey/表格法）。
- 交叉源②：Wakerly《Digital Design: Principles and Practices》K-map 与最小化章节（相邻性=格雷码、最大圈、质蕴涵项术语一致）。
- 比对结论：格雷码相邻、圈大小 2ᵏ、wrap-around、最大圈/最少圈、质蕴涵项与本质质蕴涵项、don't-care 自由取值、≤4 变量适用边界，三源一致，无冲突。
- 术语提示：H&H 的「combining」(T10) 是圈组的代数根据；「essential prime implicant」在中文教材多译「实质/本质质蕴涵项」。

## 2.4 完备集与通用门

### 2.4.1 函数完备集

一个门（或运算）集合若能仅用其成员组合出**任意**布尔函数，就称为**函数完备（functionally complete）**集，也叫**通用（universal）**集。{AND, OR, NOT} 是完备集（因为任意函数都有 SOP 展开，SOP 只用到这三种运算）。

完备性回答「我最少需要哪几种门就够造一切电路」。最小例子：既然 2.2.4 证明任意函数都能写成 SOP（只含 NOT/AND/OR），{AND,OR,NOT} 当然完备。关键洞见（下两节）：其实**单一一种门** NAND 或 NOR 就已完备——这对制造有巨大价值。

### 2.4.2 NAND 的完备性

**NAND 单门即完备**：只用 NAND 就能构造 NOT、AND、OR，从而构造一切函数。构造方式：

NOT：  x̄ = NAND(x, x)

AND：  x·y = NAND( NAND(x,y), NAND(x,y) )     （即对 NAND 输出再取反）

OR：   x+y = NAND( NAND(x,x), NAND(y,y) )      （即 NAND(x̄, ȳ)，由德摩根 = x+y）

OR 的构造正是德摩根律的直接应用：NAND(x̄,ȳ) = (x̄·ȳ)‾ = x+y。因为 {AND,OR,NOT} 完备、而它们都能只用 NAND 造出，所以 NAND 完备。

意义（为什么工业界偏爱 NAND）：CMOS 工艺里 NAND/NOR 比 AND/OR 更「原生」——AND 其实是 NAND 后接一个反相器，多一级。用单一种门还便于版图规整、良率与库单元管理。易错点：别把「NAND 完备」误解成「NAND 等于 AND」——NAND(x,y)=(xy)‾，构造 AND 必须再补一个由 NAND 搭的反相器。

### 2.4.3 NOR 的完备性

对偶地，**NOR 单门也完备**：

NOT：  x̄ = NOR(x, x)

OR：   x+y = NOR( NOR(x,y), NOR(x,y) )

AND：  x·y = NOR( NOR(x,x), NOR(y,y) )      （即 NOR(x̄, ȳ) = (x̄+ȳ)‾ = x·y）

与 NAND 完全对偶（AND/OR 角色互换），根据同样是德摩根律。直觉：NAND 天然实现「与非」、NOR 天然实现「或非」，各自补一层反相就能覆盖另两种基本运算。易错点：单输入接法不同——NAND/NOR 做 NOT 都是「两输入接同一信号」（NAND(x,x)=x̄，NOR(x,x)=x̄），但做 AND/OR 时 NAND 与 NOR 的两级结构不可混用。

下面穷举验证 NAND、NOR 各自都能导出 NOT/AND/OR：

```python
import itertools
def NAND(a,b): return 1-(a&b)
def NOR(a,b):  return 1-(a|b)
def eq(n,f,g): return all(f(*c)==g(*c) for c in itertools.product([0,1],repeat=n))
# NAND 组
print("NAND->NOT:", eq(1, lambda x: NAND(x,x),                      lambda x: 1-x))
print("NAND->AND:", eq(2, lambda x,y: NAND(NAND(x,y),NAND(x,y)),    lambda x,y: x&y))
print("NAND->OR :", eq(2, lambda x,y: NAND(NAND(x,x),NAND(y,y)),    lambda x,y: x|y))
# NOR 组
print("NOR->NOT :", eq(1, lambda x: NOR(x,x),                       lambda x: 1-x))
print("NOR->OR  :", eq(2, lambda x,y: NOR(NOR(x,y),NOR(x,y)),       lambda x,y: x|y))
print("NOR->AND :", eq(2, lambda x,y: NOR(NOR(x,x),NOR(y,y)),       lambda x,y: x&y))
```


```
NAND->NOT: True
NAND->AND: True
NAND->OR : True
NOR->NOT : True
NOR->OR  : True
NOR->AND : True
```

### 2.4.4 任意函数改写为通用门（NAND-NAND / 泡泡推移）

既然任意函数有两级 AND-OR 的 SOP 实现，就能机械地把它改写成**两级全 NAND** 实现（NAND-NAND）：对每个 AND 门与那个 OR 门都各插一对反相（互相抵消，不改变逻辑），再用德摩根律把「OR 后带输入反相」的门等价看成 NAND。这套「插泡泡、抵消、按德摩根重解读」的手法叫**泡泡推移（bubble pushing）**。

SOP 的 AND-OR 结构 ≡ NAND-NAND 结构（两级），POS 的 OR-AND 结构 ≡ NOR-NOR 结构。所以化简得到 SOP 后，落成实际电路时可以「无脑」全换成 NAND，门数不增而工艺更友好。最小例子：F = A·B + C 的 AND-OR 版是 (A AND B) 再 OR C；NAND-NAND 版是 NAND( NAND(A,B), NAND(C,C) )，逻辑等价。易错点：改写时反相必须**成对**出现（每插一个泡泡就要在别处抵消一个），漏抵消会改变逻辑；单文字项（如上例的 C）进入第二级 NAND 前要先经反相处理。

#### 来源与时效
- 一手主锚：Harris & Harris《DDCA》RISC-V ed, 1st (2021) §2.5 Multilevel Combinational Logic（NAND/NOR、bubble pushing、把多级逻辑改写为 NAND/NOR 实现）；完备性经 SOP（§2.2）+ 德摩根（§2.3）导出。核实 2026-07-26。
- 交叉源①：Mano & Ciletti《Digital Design》Ch3（NAND and NOR Implementation、Two-Level Implementation、universal gate 的表述与 AND-OR↔NAND-NAND 等价）。
- 交叉源②：Rosen《Discrete Mathematics》8th ed §12.3 Logic Gates 及习题（NAND/NOR 作为 functionally complete 单门的经典结论）。
- 比对结论：{AND,OR,NOT} 完备、NAND 单门完备、NOR 单门完备、AND-OR↔NAND-NAND 等价，三源一致；Python 穷举加固「NAND/NOR 各自导出 NOT/AND/OR」6 条断言全通过。
- 工艺提示（规范 vs 实现分账）：「NAND/NOR 在 CMOS 中比 AND/OR 更原生（AND=NAND+反相器）」属实现层事实（见本课大主题1 CMOS 门构造），逻辑完备性本身是与工艺无关的数学结论，两者分账。

## 2.5 真值表↔式↔门图互推

### 2.5.1 组合逻辑的三种等价表示

一个组合逻辑函数有三种等价表示，可无损互转：**真值表**（穷举每种输入的输出，语义的「金标准」）、**布尔表达式**（用 ·/+/‾ 写的公式，语法）、**门级原理图**（gate schematic，把表达式画成 AND/OR/NOT/NAND… 门的连线图，工程落地）。

真值表是「函数本身」（一张表就是一个函数），表达式和门图都是描述这张表的写法，同一张表有无穷多等价表达式与电路——这正是 2.2 范式（给每个函数标准写法）和 2.3 化简（在众多写法里挑最省的）之所以有意义。互推能力是数字设计的基本功：需求常以真值表给出，最终要落成门图。

### 2.5.2 真值表 → 表达式

从真值表提取表达式有两条恒可行的机械路径：取输出为 1 的行写规范 **SOP**（Σ最小项，见 2.2.4），或取输出为 0 的行写规范 **POS**（Π最大项，见 2.2.5）。得到规范形后，再用卡诺图（2.3）或代数定理（2.1）化简。

这一步永远有解、且不需要灵感——先机械提规范形、再化简。最小例子：真值表 F 在行 {1,3,6,7} 为 1，则 F = m1+m3+m6+m7（2.2.4 已展开）。易错点：提 SOP 用「为 1 的行」、提 POS 用「为 0 的行」，用反了得到的是补函数 F̄。

### 2.5.3 表达式 → 真值表

给定表达式，代入所有 2ⁿ 种输入组合逐一求值，即得真值表。这是判断两个表达式是否等价的终极办法：两式对所有输入求值相同 ⟺ 等价（本报告所有 Python 验证正是走这条路）。

表达式是「规则」，真值表是「把规则跑遍所有输入的结果」。易错点：求值务必守优先级（先补、再积、最后和）；括号改变结合，A·B+C 与 A·(B+C) 是不同函数。

### 2.5.4 表达式 → 门级原理图

把表达式画成门图：每个 · 换成 AND 门、每个 + 换成 OR 门、每个 ‾ 换成反相器（NOT），按运算嵌套连线。规范 SOP 天然对应**两级 AND-OR** 电路（第一级各 AND 出乘积项，第二级一个 OR 汇总）；规范 POS 对应两级 OR-AND。之后可按 2.4.4 把两级 AND-OR 整体换成 NAND-NAND 以适配工艺。

两级电路延迟浅（信号只穿两层门），是组合逻辑的标准落地形态；多级电路（multilevel）可能省门但延迟深，是面积/速度的权衡（深挖归本课大主题3/6）。最小例子：F = A·B̄ + C 画成——A 与 B̄（B 经反相器）进一个 AND 门，其输出与 C 进一个 OR 门。易错点：文字的补要显式画反相器（B̄ 不是凭空的，需一个 NOT 门驱动）；共享子项可共用门以省面积。

### 2.5.5 门图 → 真值表 / 表达式

反向：从门图的输入侧向输出侧逐门写出中间信号的布尔式，拼成整体表达式；再按 2.5.3 求值得真值表。这样任何一张给定电路都能被「读」回它实现的函数，用于验证或逆向。

门图 → 表达式是「顺着连线抄公式」，门图 → 真值表是「再跑一遍所有输入」。这条闭环让三种表示完全互通：真值表 —(2.5.2)→ 表达式 —(2.5.4)→ 门图 —(2.5.5)→ 真值表，首尾应一致，是设计自检的常用手段。易错点：多级/有扇出（一个信号驱动多门）的电路要仔细标中间节点，别把同名节点算重或漏接。

#### 来源与时效
- 一手主锚：Harris & Harris《DDCA》RISC-V ed, 1st (2021) §2.1 Introduction（真值表/布尔方程/电路三表示）、§2.4 From Logic to Gates（表达式→两级 AND-OR 原理图、schematic 画法）、§2.5（多级与 NAND/NOR 落地）。核实 2026-07-26。
- 交叉源①：Mano & Ciletti《Digital Design》Ch2/Ch3（Truth table ↔ Boolean function ↔ logic diagram 的互转、两级实现 two-level implementation）。
- 交叉源②：Rosen《Discrete Mathematics》8th ed §12.2–12.3（布尔函数的真值表表示、sum-of-products 展开、逻辑门电路图）。
- 比对结论：三表示等价互转、规范 SOP↔两级 AND-OR、规范 POS↔两级 OR-AND、逐输入求值判等价，三源一致，无冲突。
- 关联提示：两级 vs 多级电路的延迟/面积权衡属时序与构件范畴，深挖见本课大主题3（组合构件）、大主题6（时序分析），本报告只点到落图形态、不展开专家级权衡。
