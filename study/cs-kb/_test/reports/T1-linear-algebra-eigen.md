# L1-03·大主题5 特征值·特征向量·对角化
> 基线/核实：2026-07-25 ｜ 先修：L1-03 大主题2/3/4（消元与秩·向量空间·正交性） ｜ 一手锚点：MIT 18.06 Unit II · Strang《Intro to Linear Algebra》第6章 · NumPy/SymPy/SciPy 官方文档 ｜ 成熟度：GA（经典数学，不演进）
> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25
> 粒度判定：**1 份**。4 个小主题、内容密度中等、机制同源（都围绕特征分解 A=SΛS⁻¹），未超载，故不拆。
> 多来源比对：逐项 ≥2 独立源交叉核对（MIT 18.06 lecture 标题 + NumPy/SymPy/SciPy 官方文档 + Perron–Frobenius/Stochastic matrix 交叉，非承重）。实机 Python 抽验全通过（见各章末与文末环境串）。

---

## 5.1 特征值与特征向量

### 5.1.1 特征值/特征向量定义（Av=λv）
核心概念：
对方阵 A，若存在非零向量 v 与标量 λ 使 Av=λv，则 λ 为特征值、v 为对应特征向量；几何上 A 作用在 v 上只改变长度（按 λ 缩放）、不改变所在直线的方向（λ<0 时反向，λ 复数时含旋转）。

要求 v≠0（否则任何 λ 都平凡满足）；λ 可为 0（对应 A 奇异，v 在零空间内）。

### 5.1.2 特征方程 det(A−λI)=0 与特征多项式
核心概念：
Av=λv ⇔ (A−λI)v=0 有非零解 ⇔ A−λI 奇异 ⇔ **det(A−λI)=0**；把它展开为 λ 的 n 次多项式即特征多项式 p(λ)，其根就是全部特征值。

n×n 矩阵的特征多项式次数为 n，故（含重数、含复数）恰有 n 个特征值（代数基本定理）。

### 5.1.3 求特征值与求特征向量
核心概念：
先解 det(A−λI)=0 得各 λ；再对每个 λ 解齐次方程 (A−λI)v=0，其解空间（即 A−λI 的**零空间/nullspace**）中的非零向量就是该 λ 的特征向量。

最小例子：A=[[2,1],[1,2]]，det(A−λI)=(2−λ)²−1=0 ⇒ λ=1,3；λ=3 得 v=[1,1]、λ=1 得 v=[1,−1]（Python 实测特征值 {1,3} 一致）。

### 5.1.4 代数重数 vs 几何重数
核心概念：
**代数重数**=该 λ 作为特征多项式根的重数；**几何重数**=对应特征空间维数=dim null(A−λI)。恒有 1 ≤ 几何重数 ≤ 代数重数。

易错点：两者相等（对每个 λ）是可对角化的充要条件；几何<代数即"亏损"。例：[[2,1],[0,2]] 的 λ=2 代数重数 2、几何重数 1（SymPy eigenvects 实测只给出 1 个特征向量）。

### 5.1.5 迹=特征值之和、行列式=特征值之积
核心概念：
tr(A)=Σλᵢ、det(A)=Πλᵢ（均按重数计）。这是不解出特征向量也能做的快速一致性校验/速算。

最小例子：A=[[2,1],[1,2]]，tr=4=1+3、det=3=1×3（Python 实测 trace 4.0=Σλ、det 3.0=Πλ）。

### 5.1.6 特殊情形：三角/对角阵、重特征值、复特征值
核心概念：
三角阵（含对角阵）的特征值就是其对角元（因 det(A−λI) 直接是对角元之差的乘积）；实矩阵的复特征值成共轭对出现；旋转矩阵 [[cosθ,−sinθ],[sinθ,cosθ]] 无实特征值，特征值为 e^{±iθ}（纯旋转、无实方向不变）。

易错点：对角元相同不等于可对角化——[[2,1],[0,2]] 对角元都是 2 但亏损、不可对角化。

#### 来源与时效
- 锚点：MIT 18.06 Unit II lecture "Eigenvalues and Eigenvectors"（OCW 18.06SC Fall 2011 syllabus，核实 2026-07-25）；Strang《Intro to Linear Algebra》第6章（§6.1–6.2）。
- 交叉·实现：SymPy `Matrix.eigenvects()` 返回 `(eigenvalue, algebraic_multiplicity, [eigenvectors])` 三元组列表（docs.sympy.org, sympy 1.14.0）——用于坐实"几何重数=返回的特征向量个数、可 < 代数重数"。
- 交叉·非承重 ⚠仅二手：复/旋转矩阵谱表述与 Wikipedia "Eigenvalues and eigenvectors" 一致，仅补留白。
- 实机：np.linalg.eig / SymPy eigenvects 抽验（λ、tr=Σλ、det=Πλ、亏损阵）全部符合，见文末环境串。

---

## 5.2 对角化与矩阵的幂

### 5.2.1 对角化 A=SΛS⁻¹
核心概念：
若 A 有 n 个线性无关特征向量，把它们作列拼成 S、把对应特征值放上对角得 Λ，则 A=SΛS⁻¹（等价 S⁻¹AS=Λ）。这把 A 的作用"解耦"成各特征方向上的独立缩放。

命名差异：MIT/Strang 记 A=SΛS⁻¹（S=特征向量矩阵）；SymPy `diagonalize()` 返回 `(P, D)` 使 **M=P·D·P⁻¹**（P=S、D=Λ），符号不同、内容相同。

### 5.2.2 可对角化的充要条件
核心概念：
A（n×n）可对角化 ⇔ 有 n 个线性无关特征向量 ⇔ 对每个特征值几何重数=代数重数。特征值两两不同是充分（非必要）条件。

SymPy `Matrix.is_diagonalizable()` 即检验此条件（实对称/更一般正规矩阵总可对角化——谱定理，承接大主题3正交性，此处点到即止）。

### 5.2.3 不可对角化（亏损/defective）
核心概念：
当某特征值几何重数<代数重数（特征向量不够 n 个）时，A 不可对角化，称亏损矩阵；此时退而用 Jordan 标准形（承接大主题8，此处不展开）。

最小例子：[[2,1],[0,2]] 亏损（SymPy `is_diagonalizable()` 实测返回 False）。

### 5.2.4 矩阵的幂 A^k=SΛ^kS⁻¹
核心概念：
由 A=SΛS⁻¹ 得 A^k=SΛ^kS⁻¹，而 Λ^k 只是把各对角元（特征值）各自 k 次方——把矩阵幂降成标量幂，是求高次幂/长期行为的主力工具。

最小例子：斐波那契 [[1,1],[1,0]]^k 的 (0,1) 元给出 F_k；Python 实测 F^10 的 [0,1]=55、A^6 用 SΛ⁶S⁻¹ 与 matrix_power 误差 0。NumPy 对应 `np.linalg.matrix_power`。

### 5.2.5 相似性与对角化关系
核心概念：
A、B 相似指存在可逆 M 使 B=M⁻¹AM；相似矩阵有相同特征值（及特征多项式、迹、行列式）。对角化就是"A 相似于对角阵 Λ"这一特例。

（相似矩阵完整讨论见大主题8，此处点到即止。）

#### 来源与时效
- 锚点：MIT 18.06 Unit II lecture "Diagonalization and Powers of A"（OCW 18.06SC，核实 2026-07-25）；Strang 第6章（§6.2）。
- 交叉·实现：SymPy `Matrix.diagonalize()` → `(P, D)` 且 `M = P*D*P**-1`；`Matrix.is_diagonalizable()`（docs.sympy.org / GeeksforGeeks 交叉，sympy 1.14.0）。NumPy `numpy.linalg.matrix_power`（numpy 2.4.6 官方文档）。
- 冲突/命名分歧（两边都记）：符号 A=SΛS⁻¹（MIT/Strang）vs A=PDP⁻¹（SymPy）——同一分解、仅记号不同，非实质冲突。
- 实机：A=SΛS⁻¹ 复原误差 0；A^6、Fibonacci、亏损阵判定全部符合。

---

## 5.3 微分方程与矩阵指数

### 5.3.1 线性方程组 du/dt=Au 的解
核心概念：
一阶线性常系数方程组 du/dt=Au（u∈ℝⁿ）的解为 u(t)=e^{At}u(0)。若 A 可对角化，按特征向量展开 u(0)=Σcᵢvᵢ，则 u(t)=Σcᵢe^{λᵢt}vᵢ——每个特征方向独立地以 e^{λt} 演化。

### 5.3.2 矩阵指数 e^{At} 的级数定义与特征分解计算
核心概念：
e^{At} := Σ_{k≥0} (At)^k/k!（此级数对任意方阵收敛）。若 A=SΛS⁻¹，则 e^{At}=S·e^{Λt}·S⁻¹，而 e^{Λt} 只是对角上各 e^{λᵢt}——把矩阵指数降成标量指数。

实现分账：SciPy `scipy.linalg.expm` **不**走特征分解，而用 Padé 逼近 + scaling-and-squaring（Al-Mohy & Higham 2009 算法；scipy 1.17.1 官方文档），数值上更稳。Python 实测：谱方法 S·e^{Λt}·S⁻¹ 与 expm 结果最大误差 ~2.2e-16。

### 5.3.3 稳定性判据（特征值实部符号）
核心概念：
du/dt=Au 的解由 e^{λt} 主导：所有 Re(λ)<0 ⇒ u(t)→0（渐近稳定/收敛）；任一 Re(λ)>0 ⇒ 发散；最大 Re(λ)=0（其余<0）⇒ 临界（有界不衰减）。

最小例子：B=[[−2,1],[0,−3]] 特征值 {−2,−3} 全负实部 ⇒ 收敛（Python 实测特征值 [−2,−3]）。

### 5.3.4 与 A^k（离散）的类比
核心概念：
离散动态 u_{k+1}=Au_k 的长期行为看 λ^k（|λ|<1 收敛、|λ|>1 发散、|λ|=1 临界）；连续动态 du/dt=Au 看 e^{λt}（Re λ 符号）。两者是同一特征分解在离散/连续下的对应：λ^k ↔ e^{λt}，稳定阈值 |λ|=1 ↔ Re(λ)=0。

#### 来源与时效
- 锚点：MIT 18.06 Unit II lecture "Differential Equations and exp(At)"（OCW 18.06SC，核实 2026-07-25）；Strang 第6章（§6.3 微分方程与 e^{At}）。
- 交叉·实现：`scipy.linalg.expm`——Padé + scaling/squaring（Al-Mohy & Higham 2009），scipy 1.17.1 官方文档；SymPy `Matrix.exp()` 亦可符号求矩阵指数。
- 交叉·非承重 ⚠仅二手：稳定性 Re(λ) 判据与 Wikipedia "Matrix exponential" 一致，仅补留白、指回一手。
- 实机：expm(At) 谱方法 vs scipy 误差 ~2.2e-16；稳定阵特征值符号符合。

---

## 5.4 马尔可夫矩阵与 Fourier 级数（离散↔连续视角）

> 分账说明（一句）：本节走**线代视角**（马尔可夫矩阵的特征值/特征向量、稳态=λ=1 特征向量）；与 L2-03「马氏链/平稳分布」的**随机过程视角**互补，不重复立项。

### 5.4.1 马尔可夫（随机）矩阵定义
核心概念：
（列）随机矩阵：所有元非负，且**每列和=1**（MIT 18.06/Strang 惯用列随机；行随机则每行和=1，转置关系）。它把概率分布向量映到概率分布向量。

命名分歧（两边都记）：MIT/Strang 用**列随机**（稳态是右特征向量、列和为1）；许多概率教材/Wikipedia 用**行随机**（稳态是左特征向量/行向量）——仅约定不同。本报告采列随机。

### 5.4.2 λ=1 必存在、其余 |λ|≤1、稳态=λ=1 的特征向量
核心概念：
列随机矩阵必有特征值 λ=1（因全1行向量是左特征向量：列和为1 ⇒ 𝟙ᵀA=𝟙ᵀ），且所有特征值满足 |λ|≤1（谱半径=1）；对应 λ=1 的（右）特征向量归一化后即**稳态分布**。

最小例子：M=[[0.9,0.2],[0.1,0.8]]（列和均1），特征值 {1, 0.7}，λ=1 特征向量归一化=[2/3,1/3]（Python 实测一致）。

### 5.4.3 稳态收敛条件（正矩阵 / Perron–Frobenius 要点，浅提）
核心概念：
若马尔可夫矩阵为**正**（所有元>0，或某次幂为正/不可约非周期），则 λ=1 是唯一的模最大特征值（简单根），其余 |λ|<1，故任意初始分布下 M^n 收敛到唯一稳态（Perron–Frobenius）。存在 |λ|=1 的其它特征值（周期/可约）时不收敛到单一稳态。

最小例子：上例 M 正，M^200 两列都收敛到 [2/3,1/3]（Python 实测）。

### 5.4.4 Fourier 级数作为「正交基下投影」的特征视角（浅提）
核心概念：
Fourier 级数把函数按正交基 {e^{ikx}}（或 sin/cos）展开，系数=函数在各基向量上的投影/内积——与"用正交特征向量基展开向量、每个分量独立演化"是同一"正交基投影"思想的无限维版本（承接大主题3正交性）。

（复指数 e^{ikx} 是微分/平移算子的特征函数，是 5.1–5.3 特征思想在函数空间的对应；此处仅点到即止。）

#### 来源与时效
- 锚点：MIT 18.06 Unit II lecture "Markov Matrices; Fourier Series"（OCW 18.06SC，核实 2026-07-25）；Strang 第6章（马尔可夫矩阵与 Fourier 级数节）。
- 交叉·非承重 ⚠仅二手：Perron–Frobenius"唯一模最大正特征值+正特征向量+收敛唯一稳态"取自 Wikipedia "Perron–Frobenius theorem"（核实 2026-07-25，Wikipedia "Stochastic matrix" 页当次抓取 503 未取，λ=1/|λ|≤1 由 MIT lecture + Python 实证承重，不单靠二手）。
- 交界提醒：⚠与 L2-03 马氏链/平稳分布互补——本节线代视角，勿重复立项。
- 实机：列随机 M 特征值 {1,0.7}、稳态 [2/3,1/3]、M^200 收敛，全部符合。

---

## 附：本机实证环境与最小复现脚本（自足）

环境串：`基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（实测 python 3.11.15 / numpy 2.4.6 / sympy 1.14.0 / scipy 1.17.1）。

```python
import numpy as np, sympy as sp, scipy.linalg as sla
A = np.array([[2.,1.],[1.,2.]])
w,S = np.linalg.eig(A)                       # 5.1: 特征值 {1,3}
assert abs(np.trace(A)-w.sum())<1e-9         # tr=Σλ=4
assert abs(np.linalg.det(A)-np.prod(w))<1e-9 # det=Πλ=3
assert np.allclose(S@np.diag(w)@np.linalg.inv(S), A)          # 5.2: A=SΛS⁻¹
assert np.allclose(np.linalg.matrix_power(A,6),
                   (S@np.diag(w**6)@np.linalg.inv(S)).real)   # 5.2: A^6
assert np.linalg.matrix_power(np.array([[1,1],[1,0]]),10)[0,1]==55  # Fibonacci F_10
J = sp.Matrix([[2,1],[0,2]]); assert not J.is_diagonalizable()      # 5.1/5.2: 亏损
t=0.5
assert np.allclose(sla.expm(A*t),
                   (S@np.diag(np.exp(w*t))@np.linalg.inv(S)).real)  # 5.3: e^{At}
M = np.array([[0.9,0.2],[0.1,0.8]])          # 列随机
wm,Sm = np.linalg.eig(M); assert np.isclose(max(wm),1.0)           # 5.4: λ=1
```
真实输出（关键行）：特征值 [1. 3.]；tr 4.0=Σλ、det 3.0=Πλ；A=SΛS⁻¹ 误差 0；A^6 误差 0；F_10=55；亏损阵 is_diagonalizable=False；expm 误差 ~2.2e-16；Markov 特征值 [0.7 1.]、稳态 [0.667 0.333]、M^200 两列→[0.667 0.333]。（实证代码仅存 scratchpad，不入库。）
