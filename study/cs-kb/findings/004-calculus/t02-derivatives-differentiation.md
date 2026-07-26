# L1-04·大主题2 导数与微分法

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：极限与连续（大主题1，尤其 ε-δ 极限、连续定义）、函数与三角/指数/对数基础 ｜ 一手锚点：MIT 18.01 Single Variable Calculus (Fall 2006) 官方 syllabus，Unit 1"Differentiation"（https://ocw.mit.edu/courses/18-01-single-variable-calculus-fall-2006/pages/syllabus/ ）；James Stewart《Calculus》第 2–3 章（导数与求导法则）作工程标准教材交叉锚；Mathematics for Machine Learning (Deisenroth, Faisal, Ong, 2020) 第 5 章"Vector Calculus"作 ML 承重佐证 ｜ 成熟度：GA/稳定（经典数学内容，无演进快项）

> 粒度判定：**1 份，不拆**。本大主题 4 个小主题（2.1 导数定义 → 2.2 求导法则 → 2.3 链式法则 → 2.4 隐函数与对数微分），全部落在单变量"如何求一个函数的导数"这一条主线上：先把导数定义成差商极限、立住切线与"可导蕴含连续"的地基，再把常见函数的求导法则批量列全，接着用链式法则处理复合，最后用隐微分/对数微分/反函数导数处理"不能直接解出 y 或幂里含变量"的情形。四块不跨机制、篇幅适中，符合 v3 默认"1 大主题 = 1 报告"，故不拆 `-a/-b`。

本报告的主线心智模型：**导数就是"瞬时变化率 = 切线斜率"，它由一个极限（差商的极限）定义**；一旦把这个极限对基本函数各算一次，得到一张"求导法则表"，此后就不再回头算极限，而是像查字典一样组合这些法则去微分任何复杂表达式。四个小主题正是这条路：2.1 立定义与切线、并讲清"可导 ⇒ 连续、但反过来不成立"；2.2 把基本函数的导数与四则运算法则列全；2.3 补上组合复杂函数所必需的链式法则（这也是 ML 反向传播的地基）；2.4 处理"y 被方程隐式定义"或"幂/底数都含变量"的高级求导技巧。

---

## 2.1 导数定义

### 2.1.1 差商与导数：瞬时变化率的极限

函数 f 在点 x 处的**导数（derivative）** f′(x) 定义为**差商（difference quotient）当自变量增量趋于 0 时的极限**：

    f′(x) = lim(h→0) [ f(x + h) − f(x) ] / h

只要这个极限存在（是一个有限的数），就说 f 在 x 处**可导（可微，differentiable）**。等价地，令 Δx = h、Δy = f(x+h)−f(x)，导数就是 Δy/Δx 在 Δx→0 时的极限，记作 dy/dx。它衡量的是"y 随 x 变化的瞬时速率"。

差商 [f(x+h)−f(x)]/h 本身是一个很朴素的东西：它就是函数图像上两点 (x, f(x)) 与 (x+h, f(x+h)) 连线（割线）的斜率。让 h→0，第二个点沿曲线滑向第一个点，割线就"收敛"成切线——所以导数的几何含义是**切线斜率**（见 2.1.2）。物理含义是瞬时速度：位置对时间的导数就是速度。

最小例子：对 f(x) = x²，按定义算

    f′(x) = lim(h→0) [ (x+h)² − x² ] / h = lim(h→0) [ 2xh + h² ] / h = lim(h→0) (2x + h) = 2x

注意关键一步：在取极限**之前**先把分子展开、约掉分母里的 h（因为 h≠0 时可以约），最后才令 h→0，得到 2x。这也解释了为什么不能"直接把 h=0 代进差商"——那会得到 0/0 的不定型，必须先化简。

易错点：差商里的 h 可正可负，导数要求**左右两侧的极限都存在且相等**（呼应大主题1的单侧极限）。若左右极限不等，导数就不存在。dy/dx 是一个**整体记号**（Leibniz 记号），不是 dy 除以 dx 两个量相除；初学阶段把它当作 f′(x) 的另一种写法即可。

sympy 印证：按定义对 x² 取差商极限得 2x，与 `diff(x**2)` 完全一致（脚本见文末）。

### 2.1.2 切线：导数的几何身份

f 在点 (a, f(a)) 处的**切线（tangent line）**是过该点、斜率等于 f′(a) 的直线：

    y = f(a) + f′(a)·(x − a)

导数 f′(a) 就是这条切线的斜率。曲线在某点"看起来是什么方向、有多陡"，答案就是那一点的导数。

为什么切线是割线的极限？取曲线上另一点 (a+h, f(a+h))，它与 (a, f(a)) 连成的割线斜率正是差商 [f(a+h)−f(a)]/h；当 h→0，这条割线绕着定点旋转、逼近成切线，其斜率的极限就是 f′(a)。这就是 2.1.1 那个极限的几何来历——**导数 = 切线斜率 = 割线斜率的极限**，三句话说的是同一件事。

用途/关联：切线是"用直线局部近似曲线"的最简单方式，即线性近似 f(x) ≈ f(a) + f′(a)(x−a)。这条思想往后会长成微分、牛顿法、以及大主题6的泰勒展开（一阶泰勒就是切线）。易错点：切线在切点附近才是好的近似，离切点越远误差越大；另外"切线"未必只与曲线交于一点（它可以在别处再穿过曲线），"只碰一点"不是切线的定义，斜率等于导数才是。

### 2.1.3 可导蕴含连续，但连续不蕴含可导

**若 f 在 x 处可导，则 f 在 x 处必连续**；但反过来不成立——**连续的函数未必可导**。这是初学者最容易记反的一条关系，务必记牢方向：

    可导 ⇒ 连续　（成立）
    连续 ⇒ 可导　（不成立，有反例）

为什么"可导 ⇒ 连续"：可导意味着 lim(h→0) [f(x+h)−f(x)]/h 存在且有限，于是分子 f(x+h)−f(x) 必须随 h→0 一起趋于 0（否则比值会爆掉），这正是 f 在 x 连续的定义。直觉上，图像若有"断裂/跳跃"（不连续），根本谈不上光滑的切线，自然不可导。

反方向的经典反例是绝对值函数 f(x) = |x| 在 x = 0 处：它处处连续，但在 0 点不可导——因为左侧割线斜率趋于 −1、右侧趋于 +1，左右极限不相等，差商极限不存在。几何上就是图像在原点有一个"尖角（corner）"，没有唯一的切线方向。

    d/dx |x|：x<0 时为 −1，x>0 时为 +1，x=0 处不存在

易错点：不可导的常见成因有三类——尖角（如 |x| 在 0）、竖直切线（切线斜率为无穷，如 x^(1/3) 在 0）、以及不连续点（跳跃/断裂处）。凡遇"处处连续所以处处可导"的说法都要警惕，这是错的。ML 关联：ReLU 激活 max(0,x) 就是这类"连续但在 0 不可导"的函数，实际实现里靠"次梯度/约定 0 处取 0 或 1"绕过——这条数学事实在 L5-02 会再遇到。

#### 来源与时效（本小主题末集中列）
- 锚点：MIT 18.01 (Fall 2006) syllabus, Unit 1"Differentiation"，第一部分"Derivatives"含"Definition of derivative / rate of change / limit of difference quotient / geometric interpretation as slope of tangent"（官方 syllabus 已列出该单元，https://ocw.mit.edu/courses/18-01-single-variable-calculus-fall-2006/pages/syllabus/ ，核实 2026-07-26）；Stewart《Calculus》§2.1（切线与变化率）、§2.7–2.8（导数定义与"可导 ⇒ 连续"定理）。
- 交叉核对（独立第二源）：Wikipedia "Derivative" 条目（核实 2026-07-26）确认差商极限定义 f′(x)=lim(h→0)[f(x+h)−f(x)]/h、切线斜率解释、以及"differentiable ⇒ continuous 但反之不真（|x| 在 0 为反例）"。与 MIT/Stewart 完全一致，无分歧。
- 实证：sympy 按定义对 x² 取差商极限 = 2x = diff(x²)；|x| 在 x<0、x>0 两侧导数为 −1、+1（脚本见文末）。
- 成熟度：GA/稳定，经典内容无演进；无「待核」项。

## 2.2 求导法则

### 2.2.1 幂法则与四则线性

**幂法则（power rule）**：对任意实数指数 n，

    d/dx (xⁿ) = n·x^(n−1)

配合导数对加减与常数倍的**线性性质**（求和法则与常数倍法则）：

    d/dx [ c·f(x) ] = c·f′(x)
    d/dx [ f(x) ± g(x) ] = f′(x) ± g′(x)

以及常数的导数为 0（d/dx c = 0），就足以微分任何多项式。

直觉：幂法则可由 2.1 的定义 + 二项式展开推出（对 (x+h)ⁿ 展开，领头项 nx^(n−1)h 除以 h 后留下，其余含 h² 以上的项趋于 0）。它对 n 是负数、分数一样成立：例如

    d/dx (1/x) = d/dx x^(−1) = −x^(−2) = −1/x²
    d/dx √x = d/dx x^(1/2) = (1/2)·x^(−1/2) = 1/(2√x)

最小例子：d/dx (3x⁴ − 5x + 7) = 12x³ − 5。

易错点：指数是**常数**时才用幂法则；若底数是常数、指数才是变量（如 2^x），那属于指数函数，用的是 2.2.3 的公式，不能套幂法则写成"x·2^(x−1)"（这是典型错误）。既是变量底又是变量指数（x^x）要用对数微分（见 2.4.2）。

### 2.2.2 积法则与商法则

**积法则（product rule）**：两个函数乘积的导数不是"导数相乘"，而是

    (u·v)′ = u′·v + u·v′

**商法则（quotient rule）**：

    (u/v)′ = ( u′·v − u·v′ ) / v²　（要求 v ≠ 0）

直觉/为什么不是简单相乘：当 x 变一点点，乘积 uv 的变化由两部分叠加——"u 变化引起的"（v 保持）加上"v 变化引起的"（u 保持），故是 u′v + uv′。这是初学者第一大易错点：(uv)′ ≠ u′v′。

商法则可由积法则 + 链式法则推出，记忆口诀是"**下乘上导 减 上乘下导，再除以下的平方**"——注意分子里的**减号**和顺序不能颠倒（u′v 在前、uv′ 在后），写反符号是第二大易错点。分母是 v²。

最小例子：

    d/dx (x²·sin x) = 2x·sin x + x²·cos x
    d/dx (x / (x+1)) = [ 1·(x+1) − x·1 ] / (x+1)² = 1/(x+1)²

sympy 印证：(u·v)′ 给出 u′v+uv′，(u/v)′ 给出 (u′v−uv′)/v²，与上式逐字一致（脚本见文末）。

### 2.2.3 三角、指数、对数函数的导数

常用基本函数的导数（应当直接记住，它们各自由 2.1 定义 + 相应极限推出）：

三角函数：

    d/dx sin x = cos x
    d/dx cos x = −sin x
    d/dx tan x = sec²x = 1 + tan²x

指数函数：

    d/dx eˣ = eˣ
    d/dx aˣ = aˣ·ln a　（a > 0）

对数函数：

    d/dx ln x = 1/x　（x > 0）
    d/dx log_a x = 1 / (x·ln a)

直觉与为什么：eˣ 的导数是它自己，这正是选 e 作为"自然底数"的原因——它让指数函数的变化率恰好等于函数值本身，是所有指数曲线里唯一斜率=高度的那条。一般 aˣ 的导数多出一个因子 ln a，正来自 aˣ = e^(x ln a) 配合链式法则。sin/cos 的导数关系可由 sin 的差商极限 + 大主题1的特殊极限 lim(x→0) sin x / x = 1 推出——这就是那条特殊极限"值钱"的地方之一。

易错点：
- cos 的导数带**负号**（−sin x），sin 的不带；这一对符号最常记混。
- d/dx aˣ = aˣ ln a，不要漏掉 ln a；只有 a=e 时 ln a=1 才退化成 eˣ。
- 这些三角导数公式默认自变量以**弧度（radian）**计；若用角度制，会平白多出一个 π/180 的因子，公式就不再是这个干净形式。这是极易被忽略的前提。

sympy 印证：sin→cos、cos→−sin、tan→tan²+1（即 sec²）、eˣ→eˣ、aˣ→aˣln a、ln x→1/x、log_a x→1/(x ln a) 全部逐条通过（脚本见文末）。

#### 来源与时效（本小主题末集中列）
- 锚点：MIT 18.01 (Fall 2006) syllabus, Unit 1"Differentiation"，含"Derivatives of products, quotients, sine, cosine / Chain rule / exponential and log"（官方 syllabus 已列出，同上 URL，核实 2026-07-26）；Stewart《Calculus》§3.1–3.6（幂/积/商法则、三角函数导数、指数与对数函数导数）。
- 交叉核对（独立第二源）：Wikipedia "Differentiation rules" 与 "Derivative" 条目（核实 2026-07-26）确认幂法则 nx^(n−1)、线性性、积法则 u′v+uv′、商法则 (u′v−uv′)/v²、以及 sin′=cos、cos′=−sin、tan′=sec²、(eˣ)′=eˣ、(aˣ)′=aˣln a、(ln x)′=1/x、(log_a x)′=1/(x ln a)。与 MIT/Stewart 一致，无分歧。
- 实证：sympy `diff` 对以上每条法则/基本导数逐一验证通过（脚本见文末）。
- 成熟度：GA/稳定；无「待核」项。冪法则中 tan′ 记法 sec²x 与 1+tan²x 等价（三角恒等），非分歧。

## 2.3 链式法则

### 2.3.1 链式法则：复合函数的求导

**链式法则（chain rule）**给出复合函数 (f∘g)(x) = f(g(x)) 的导数：**外层导数（在内层处取值）乘以内层导数**。设 y = f(u)、u = g(x)，则

    d/dx f(g(x)) = f′(g(x)) · g′(x)

用 Leibniz 记号更好记：

    dy/dx = (dy/du) · (du/dx)

即"把中间变量 u 的 du 当作可以形式上约掉的东西"，dy/dx = dy/du · du/dx。

核心直觉是**变化率相乘**：如果 u 随 x 变化的速率是 du/dx，而 y 随 u 变化的速率是 dy/du，那么 y 随 x 变化的总速率就是两者相乘——就像齿轮传动，外轮转速 = 外轮对中轮的传动比 × 中轮对内轮的传动比。方向很关键：**从最外层开始，一层层剥到最里层，每剥一层乘上该层的导数**。

最小例子（务必看清"外×内"的顺序）：

    d/dx sin(x²) = cos(x²) · 2x　（外层 sin 的导 cos，在 x² 处取值；再乘内层 x² 的导 2x）
    d/dx (3x + 1)⁵ = 5·(3x + 1)⁴ · 3 = 15·(3x + 1)⁴
    d/dx e^(x²) = e^(x²) · 2x

易错点：最常见错误是"忘记乘内层导数"，比如把 d/dx sin(x²) 错写成 cos(x²)（漏了 ·2x）。链可以有很多层，逐层相乘即可，如 d/dx sin(cos(x²)) = cos(cos(x²))·(−sin(x²))·2x。另一个易错点是分不清"内外"：先做的运算是内层、后做的是外层。

sympy 印证：d/dx sin(x²) = 2x·cos(x²)、d/dx (3x+1)⁵ = 15(3x+1)⁴、d/dx e^(x²) = 2x·e^(x²)，均与手算一致（脚本见文末）。

### 2.3.2 链式法则为何是反向传播（backprop）的地基

链式法则是把"损失对参数的导数"沿着计算图**从输出端一层层往输入端传**的唯一依据——这正是神经网络训练里**反向传播（backpropagation）**的数学内核，本内容项显式喂给 L5-02。

一个神经网络可以看成很多简单函数的深层复合：

    L = f_N( f_{N−1}( … f_1(x; θ) … ) )

要知道最终损失 L 对某个很靠前的参数 θ 有多敏感（即 ∂L/∂θ），就要把从 L 到 θ 这条路径上每一层的局部导数**连乘**起来——这就是链式法则 dy/dx = dy/du·du/dx 在多层上的推广。反向传播之所以"反向"，是因为按链式法则从最外层（损失）的导数出发、逐层向内乘上各层局部导数最省计算（复用已算好的后段乘积），而不是从输入端正向重算。

为初学者点明方向：本课（单变量）只需牢牢建立"复合 ⇒ 导数相乘、从外到内逐层剥"这一条直觉；到了多变量（本课大主题7 的多元链式法则与雅可比矩阵）会把"相乘"升级成"矩阵相乘"，再到 L5-02 就落成 backprop 的具体算法。这里不展开梯度与矩阵形式，只需记住：**链式法则 = 深度学习能训练的根本原因**。

易错点/关联：初学时不必纠结"为什么反向比正向省"（那是 L5-02 的计算复杂度问题）；此处只要能对多层复合正确地逐层相乘即可。

#### 来源与时效（本小主题末集中列）
- 锚点：MIT 18.01 (Fall 2006) syllabus, Unit 1"Differentiation"含"Chain rule"（官方 syllabus，同上 URL，核实 2026-07-26）；Stewart《Calculus》§3.4"The Chain Rule"。
- 交叉核对（独立第二源）：Wikipedia "Chain rule" 条目（核实 2026-07-26）确认 (f∘g)′(x)=f′(g(x))·g′(x) 与 Leibniz 形式 dy/dx=dy/du·du/dx。ML 承重佐证：Mathematics for Machine Learning (Deisenroth et al., 2020) §5.2.2 与第 5 章将链式法则明列为反向传播/自动微分的基础。三源一致，无分歧。
- 实证：sympy 对 sin(x²)、(3x+1)⁵、e^(x²) 的链式求导结果与手算逐条一致（脚本见文末）。
- 成熟度：GA/稳定；无「待核」项。

## 2.4 隐函数与对数微分

### 2.4.1 隐函数微分（隐微分）

当 y 由一个方程 F(x, y) = 0 **隐式地**定义、无法（或不便）解出显式的 y = f(x) 时，用**隐函数微分（implicit differentiation）**求 dy/dx：**把方程两边同时对 x 求导，其间把 y 视为 x 的函数（故对含 y 的项要用链式法则乘上 dy/dx），再解出 dy/dx**。

以单位圆 x² + y² = 1 为例，两边对 x 求导：

    2x + 2y·(dy/dx) = 0　⟹　dy/dx = −x/y

关键在于第二项：y² 对 x 求导时，y 是 x 的隐函数，按链式法则得 2y·(dy/dx)，那个 dy/dx **不能漏**——这是隐微分的头号易错点。求导后方程里同时出现 x、y 和 dy/dx，把 dy/dx 当未知量解出来即可，所以结果通常同时含 x 和 y（如 −x/y），这很正常。

直觉：隐微分省去了"先解出 y"这一步（很多方程根本解不出，或解出后有 ±分支很麻烦），直接对整条曲线求切线斜率。上圆例中 dy/dx = −x/y 也符合几何：圆上一点的切线垂直于该点的半径向量 (x, y)，斜率恰为 −x/y。

易错点：对每一个含 y 的项都要记得乘 dy/dx；对 xy 这类乘积项还要先用积法则再对 y 项配链式（d/dx(xy) = y + x·dy/dx）。sympy 印证：对 x²+y²=1 隐式求导解得 dy/dx = −x/y（脚本见文末）。

### 2.4.2 对数微分

**对数微分（logarithmic differentiation）**是处理"**幂里含变量**"（如 y = x^x）或"**大量因子连乘/连除**"的技巧：**先对等式两边取自然对数，用对数把乘除化成加减、把指数搬下来当系数，再隐式求导**。

对 y = x^x（x > 0），取对数得 ln y = x·ln x，两边对 x 求导（左边用链式法则得 (1/y)·y′）：

    (1/y)·(dy/dx) = ln x + 1　⟹　dy/dx = y·(ln x + 1) = x^x·(ln x + 1)

为什么需要它：x^x 既不是幂函数（指数不是常数，不能用幂法则 nx^(n−1)）也不是指数函数（底数不是常数，不能用 aˣ ln a），两条法则都套不上；取对数后 ln(x^x) = x ln x 就变成了会求导的形式。这也顺带解释了 2.2.1 的告诫——遇到变量底、变量指数同时出现，标准出路就是对数微分。

对连乘/连除，如 y = (x²·√(x+1)) / (x−3)³，取对数后变成 ln y = 2ln x + ½ln(x+1) − 3ln(x−3)，求导只需逐项相加，比直接用积法则/商法则硬算省力得多。易错点：取对数要求真数为正，严格来说应对 |y| 取对数或分区间讨论；初学阶段只要记住"取对数把乘除幂降为加减乘"的用途即可。sympy 印证：d/dx x^x = x^x(ln x + 1)（脚本见文末）。

### 2.4.3 反函数的导数

若 f 可导且在某点 f′ ≠ 0，则其**反函数（inverse function）** f⁻¹ 也可导，导数是原函数导数的倒数（在对应点取值）：

    (f⁻¹)′(y) = 1 / f′( f⁻¹(y) )

用 Leibniz 记号最直观：

    dx/dy = 1 / (dy/dx)

由此可推出反三角函数的导数（常用，建议记住）：

    d/dx arcsin x = 1 / √(1 − x²)　（|x| < 1）
    d/dx arccos x = −1 / √(1 − x²)
    d/dx arctan x = 1 / (1 + x²)

直觉：反函数的图像是原函数图像沿 y=x 的镜像，镜像会把"斜率"变成"倒数"（一条斜率为 m 的直线镜像后斜率为 1/m），所以 dx/dy = 1/(dy/dx)。这也解释了为什么要求 f′≠0——原斜率为 0 处（水平切线）镜像后成竖直切线，反函数在那里导数为无穷、不可导。

推导示例（arcsin）：设 y = arcsin x，即 x = sin y，两边对 x 隐式求导得 1 = cos y·(dy/dx)，故 dy/dx = 1/cos y = 1/√(1−sin²y) = 1/√(1−x²)——正是"反函数导数 = 隐微分 + 三角恒等"的组合。易错点：arccos 的导数带负号，与 arcsin 只差一个符号，容易记混；arctan 的分母是 1+x²（不带根号），定义域是全体实数。sympy 印证：arcsin′ = 1/√(1−x²)、arccos′ = −1/√(1−x²)、arctan′ = 1/(1+x²) 全部通过（脚本见文末）。

#### 来源与时效（本小主题末集中列）
- 锚点：MIT 18.01 (Fall 2006) syllabus, Unit 1"Differentiation"含"Implicit differentiation, inverses / Derivatives of inverse trig, logarithms"（官方 syllabus，同上 URL，核实 2026-07-26）；Stewart《Calculus》§3.5（隐微分与反三角导数）、§3.6（对数微分与反函数导数）。
- 交叉核对（独立第二源）：Wikipedia "Implicit function"（隐微分）、"Logarithmic derivative"/"Differentiation rules"（对数微分、x^x 例）、"Inverse functions and differentiation"（(f⁻¹)′=1/f′∘f⁻¹）与 "Inverse trigonometric functions" 条目（核实 2026-07-26）确认 dy/dx=−x/y（圆）、d/dx x^x=x^x(ln x+1)、arcsin′=1/√(1−x²)、arccos′=−1/√(1−x²)、arctan′=1/(1+x²)。与 MIT/Stewart 一致，无分歧。
- 实证：sympy `idiff`/隐式求导给 −x/y；`diff(x**x)`=x^x(ln x+1)；`diff(asin/acos/atan)` 与上式逐条一致（脚本见文末）。
- 成熟度：GA/稳定；无「待核」项。

---

## 附：本报告实证脚本与真实输出（自足、可复现）

环境串：基线 Py3.11.15/np2.4.6/sympy1.14.0 @2026-07-26。下述脚本在基线环境实跑，输出即下方所示。

```python
import sympy as sp
x, a, n = sp.symbols('x a n', positive=True)
X, h = sp.symbols('x h')

# 2.1 导数定义（差商极限）与 |x| 不可导
f = lambda t: t**2
print("2.1 diff-quotient x^2:", sp.limit((f(X+h)-f(X))/h, h, 0), "| diff:", sp.diff(X**2, X))
print("2.1 |x| one-sided deriv at 0 (left,right):", sp.limit(abs(0+h)/h,h,0,'-'), sp.limit(abs(0+h)/h,h,0,'+'))

# 2.2 求导法则
print("2.2 power x^n:", sp.simplify(sp.diff(x**n, x)))       # n*x**(n-1)
u = sp.Function('u')(X); v = sp.Function('v')(X)
print("2.2 product:", sp.diff(u*v, X))
print("2.2 quotient:", sp.simplify(sp.diff(u/v, X)))
print("2.2 sin,cos,tan:", sp.diff(sp.sin(X),X), sp.diff(sp.cos(X),X), sp.diff(sp.tan(X),X))
print("2.2 e^x, a^x:", sp.diff(sp.exp(X),X), sp.diff(a**X,X))
print("2.2 ln x, log_a x:", sp.diff(sp.log(X),X), sp.diff(sp.log(X,a),X))

# 2.3 链式法则
print("2.3 sin(x^2):", sp.diff(sp.sin(X**2),X))
print("2.3 (3x+1)^5:", sp.diff((3*X+1)**5,X))
print("2.3 e^(x^2):", sp.diff(sp.exp(X**2),X))

# 2.4 隐微分 / 对数微分 / 反函数
yx = sp.Function('y')(X)
print("2.4 implicit circle dy/dx:", sp.solve(sp.diff(X**2 + yx**2 - 1, X), sp.diff(yx, X)))
print("2.4 d/dx x^x:", sp.diff(X**X, X))
print("2.4 arcsin',arccos',arctan':", sp.diff(sp.asin(X),X), sp.diff(sp.acos(X),X), sp.diff(sp.atan(X),X))
```

真实输出：

    2.1 diff-quotient x^2: 2*x | diff: 2*x
    2.1 |x| one-sided deriv at 0 (left,right): -1 1
    2.2 power x^n: n*x**(n - 1)
    2.2 product: u(x)*Derivative(v(x), x) + v(x)*Derivative(u(x), x)
    2.2 quotient: (-u(x)*Derivative(v(x), x) + v(x)*Derivative(u(x), x))/v(x)**2
    2.2 sin,cos,tan: cos(x) -sin(x) tan(x)**2 + 1
    2.2 e^x, a^x: exp(x) a**x*log(a)
    2.2 ln x, log_a x: 1/x 1/(x*log(a))
    2.3 sin(x^2): 2*x*cos(x**2)
    2.3 (3x+1)^5: 15*(3*x + 1)**4
    2.3 e^(x^2): 2*x*exp(x**2)
    2.4 implicit circle dy/dx: [-x/y(x)]
    2.4 d/dx x^x: x**x*(log(x) + 1)
    2.4 arcsin',arccos',arctan': 1/sqrt(1 - x**2) -1/sqrt(1 - x**2) 1/(x**2 + 1)

（说明：sympy 中 log 即自然对数 ln；tan′ 输出 tan²x+1 与 sec²x 等价；|x| 在 0 处左、右单侧差商极限分别为 −1、+1 不相等，故 0 处不可导。）实证仅作公式加固，不替代上文各小主题末的多来源比对。
