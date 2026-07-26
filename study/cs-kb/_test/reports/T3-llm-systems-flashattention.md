# L6-08·08S-6 Kernel 与 Triton / FlashAttention
> 基线/核实：2026-07-26 ｜ 先修：L6-03·03-6（注意力/Transformer）、L6-08·08S-5（GPU 内存层次/roofline）、08S-2（FLOP/显存核算） ｜ 一手锚点：CS336 Spring 2026 L6（Kernels, Triton）；FlashAttention v1(2022, arXiv:2205.14135)/v2(2023, arXiv:2307.08691)/v3(2024, arXiv:2407.08608) 论文；Triton 官方文档（triton-lang.org/main） ｜ 成熟度：⚙演进快·锚版本（CS336 Sp26 / FA v1-v3）
>
> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25

**粒度判定：1 份。** 5 个小主题机制同源，全部围绕「IO 感知 kernel」一条主线（访存瓶颈 → 融合 → 分块/在线 softmax → Triton 表达 → 版本演进）；6.5 逐版对齐用一张对照表即可收束，不至过长，无需拆 -a/-b。

**全篇成熟度硬标：`⚙演进快·锚版本 CS336 Sp26 / FA v1-v3`。** 本域半衰期以月/季计，一切结论仅坐实「该锚点、该版本下如此」；框架/方法（Triton vs CUDA、FA 各版）一律不排名、不下优劣定论，只讲机制与版本 delta。凡具体加速比/显存系数/API 名/FP8 精度参数，无一手来源即标「待核」，不凭记忆填。本机无 GPU，涉 kernel 实测处标「实机未验证」。

---

## 08S-6.1 IO 感知与 HBM 读写瓶颈

### 08S-6.1.1 访存主导 vs 算力主导
朴素注意力在现代 GPU 上是访存受限（memory-bound）而非算力受限（compute-bound）——瓶颈在 HBM 与片上 SRAM 之间反复搬运 N×N 中间量所耗的带宽，而不是 matmul 的浮点算力。FlashAttention v1 摘要明确把「未考虑 GPU 各级内存之间的读写代价」列为此前注意力算法的关键缺失。

要理解「受限」是什么意思，可以把 GPU 想成一个食量惊人但住得离仓库很远的大胃王：它每秒能吃（算）的东西极多，但食物（数据）要一趟趟从远处的仓库（HBM）搬过来。如果一道菜「搬的时间」远大于「吃的时间」，那么再增大胃口也没用——真正卡住吞吐的是搬运，也就是带宽。

朴素注意力里，softmax、mask、dropout 这类逐元素算子恰恰是「搬得多、算得少」：每从显存读一个数，只做很少几次运算就又要写回去。这类算子的「算术强度」很低（见 6.1.5），落在 roofline 图的带宽区（承接 08S-5.3）。结论是：想让注意力更快，方向不是堆更多算力，而是想办法少搬数据。这正是后面融合、分块、IO 感知一整套手法的出发点。

### 08S-6.1.2 GPU 内存层次回顾（承接 08S-5）
GPU 的存储是分级的：HBM（显存）容量大但带宽较低、延迟较高；片上 SRAM（shared memory / L1 附近）容量很小但带宽极高、延迟极低。FlashAttention 的整个立论就是「尽量把工作留在 SRAM 里做，尽量少去碰 HBM」。各级具体的容量/带宽数值随 GPU 代际不同（A100 与 H100 不一样），要引用就必须锚定具体型号，此处不落数字（待核·锚型号）。

可以用「书桌 vs 书库」来建立直觉：SRAM 像你面前的书桌，翻页极快但只放得下几本书；HBM 像楼下的大书库，什么都有但每次取书都要跑一趟楼梯。做研究时，如果每查一个数据都跑一趟书库，效率极低；聪明的做法是一次搬一小摞相关的书上桌，在桌上把能做的事都做完再换下一摞。FlashAttention 干的就是这件事——按块把数据搬上「书桌」（SRAM），在桌上算完局部结果，只把最终结果写回「书库」（HBM）。

新手常见的误解是把「快」全归给算得快。实际上这里的快主要来自「少跑楼梯」。记住这条内存层次的落差，是理解后面所有优化的地基。

### 08S-6.1.3 朴素注意力的 IO 复杂度
朴素实现会显式地在 HBM 上物化整张注意力分数矩阵，然后一遍遍读回：先算并写下

S = QKᵀ

（一个 N×N 的大矩阵），再从 HBM 读回它做 softmax、写回，再读回去乘 V。这些中间张量在 HBM 上多次往返，HBM 访问量随序列长度按

O(N²)

量级增长，成为主要成本。

这里的关键不在「算 QKᵀ 要多少 FLOP」，而在「N×N 这张大表要在慢速显存里被写一次、读好几次」。序列越长，这张表越大——长度翻倍，表的面积就是四倍，往返的字节数也随之膨胀。所以长上下文场景下，朴素注意力先被显存和带宽拖垮，而不是被算力拖垮。

一个易错点：不要把 O(N²) 当成「算力复杂度」。注意力的算力本来也是 O(N²) 量级，但朴素实现真正致命的是它把这个 O(N²) 的中间矩阵实实在在地写进了慢速 HBM 又反复读出。FlashAttention 的核心突破正是「同样精确地算完，但那张 N×N 大表根本不落 HBM」（见 6.3）。

### 08S-6.1.4 IO 感知（IO-aware）算法设计取向
IO 感知指把「减少 HBM 访问字节数」当成一等优化目标来设计算法，而不是像传统视角那样只数 FLOP。FlashAttention v1 的标题里就直接写着 "with IO-Awareness"，它把内存读写次数纳入算法的设计与分析——依据 SRAM 的大小来选择分块大小，使得对 HBM 的访问次数最小化。

传统算法分析课上，我们习惯用「做了多少次运算」来衡量快慢（比如排序 O(n log n)）。但在 GPU 上，这个尺子会骗人：两个 FLOP 数完全一样的实现，跑起来可能差好几倍，差别全在「搬了多少数据」。IO 感知就是换一把更贴合硬件现实的尺子——先问「这个算法要在快慢内存之间搬多少字节」，再去优化它。

这是一种思维方式的转变，也是本大主题真正要传达的核心心智模型：在访存受限的世界里，省带宽比省算力更值钱。后面的融合（6.2）和分块（6.3）都只是把这个原则落地的具体手段。

### 08S-6.1.5 算术强度（arithmetic intensity）的桥接
算术强度衡量「每搬一字节数据能摊到多少次运算」，定义为

算术强度 = 计算 FLOP 数 / HBM 访存字节数

它是连接本主题与 08S-5.3 roofline 模型的桥。注意力的逐元素部分算术强度低，落在 roofline 的带宽受限区；因此优化方向是提高有效算术强度（靠融合+分块少往返 HBM），而不是堆更多算力。

直觉上，算术强度就是「性价比」：搬一趟数据（付出带宽成本）能顺带干多少活。强度高（比如稠密大矩阵乘，一次搬入的数据被反复复用做很多乘加）就值得搬，硬件的算力才吃得饱；强度低（比如一次加法就要读写一遍）就亏，硬件大部分时间在等数据。

在 roofline 图上，横轴是算术强度、纵轴是可达吞吐：强度小的时候，吞吐被一条斜线（带宽）压着往上走；强度大到某个「拐点」之后，才被水平线（峰值算力）封顶。注意力的 softmax 段强度低、卡在斜线区，所以优化的着力点是「把强度往拐点方向推」——融合让中间结果不落 HBM、分块让搬上 SRAM 的数据被更充分地复用，二者都在抬高算术强度。这就把「为什么要融合、为什么要分块」统一到了一个量化标准下。

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 一手：FlashAttention v1（Dao et al. 2022，arXiv:2205.14135）摘要——IO-aware、tiling 减少 HBM↔SRAM 读写、fewer HBM accesses。
- 课程：CS336 Spring 2026 L6「Kernels, Triton」；L2 覆盖 FLOP / memory / arithmetic intensity。两源交叉印证「访存受限 + IO 感知」主线。
- 待核：具体各级内存容量/带宽数值随 GPU 型号变，未锚型号 ⇒ 不落数，标待核。

## 08S-6.2 kernel 融合（fusion）

### 08S-6.2.1 kernel 与 launch 开销
一个 kernel 是一次在 GPU 上发射的并行程序。逐算子执行时每个算子各起一个 kernel，每个 kernel 都要从 HBM 把输入读进来、算完再把结果写回 HBM，而且每次发射（launch）本身还有一笔固定的调度开销。算子链越长，往返 HBM 的次数和 launch 的次数就越多。

把它类比成「叫外卖」会更直观：每点一道菜（一个算子）就单独下一单，骑手要专门从餐厅（HBM）取货、送到你手上、你吃完的中间产物又得让他带回餐厅给下一道菜用。下十次单，就有十趟往返加十次「接单派单」的固定手续费。哪怕每道菜本身很快，光是这些往返和手续费就能拖垮整体。

新手容易忽略 launch 开销这一项——它单看很小，但当 kernel 又多又小时，固定开销会累积成可观的浪费。这也是为什么「把很多小算子并成一个大 kernel」（下一节的融合）能同时省两样东西。

### 08S-6.2.2 融合的收益机制
kernel 融合把多个原本各自为政的算子并进同一个 kernel：中间结果直接留在寄存器/SRAM 里，不落回 HBM，从而既省带宽（少往返）又省 launch 次数。这正是 FlashAttention 用来减少 HBM 访问的手段之一。

接着上面的外卖类比：融合相当于把「一整套菜」写成一张单子交给同一个骑手，让他在餐厅里一次做完、只跑一趟送到桌上。中间的半成品（比如切好的配料）留在后厨（SRAM），不用来回搬。省下的既是往返路程（带宽），也是重复的接单手续（launch）。

要点是：融合省下的主要是「搬运」，不是「运算」——运算量基本不变，甚至可能因重算而略增（见 6.2.5），但因为搬运才是瓶颈，总时间反而大降。这再次印证 6.1 的主旨：在访存受限的世界里，少搬比少算更划算。

### 08S-6.2.3 注意力中的融合点
在注意力里，可以把 QKᵀ → scale（乘 1/√d）→ mask → softmax → ×V 这一整条链融成单一 kernel，中间的 S/P 矩阵完全不物化到 HBM。Triton 官方的 fused-attention 教程演示的就是这条融合链（其实现对应的是 FlashAttention v2 算法）。

之所以这一串特别值得融合，是因为链条中间那张 N×N 的分数矩阵 S 又大又只用一次——朴素实现把它写进 HBM 再读回，纯属为一次性中间量付了昂贵的搬运费。融合后它只在 SRAM 里短暂存在、用完即弃，省掉的正是 6.1.3 里那笔 O(N²) 的 HBM 往返。

这里可以先建立一个「串起来看」的直觉：6.1 告诉我们瓶颈在搬运，6.2 告诉我们融合能减少搬运，而注意力这条 QKᵀ→…→×V 链恰好是「中间量巨大、只用一次」的理想融合对象。至于「大矩阵放不进小 SRAM 怎么办」，答案是分块——这就引出了 6.3。

### 08S-6.2.4 融合的代价/边界
融合不是免费午餐，有明确的边界：SRAM 容量有限，所以一次融合处理的块不能太大；可融的算子也受限（跨 kernel 的依赖、需要全局归约的算子难以融进来）；而且手写或编译融合 kernel 的复杂度会上升。融合是一种权衡，不下「总是更好」的定论。

用一句话概括边界：融合靠的是「把中间结果塞进小小的书桌（SRAM）」，可书桌就那么大。要处理的数据一旦超过书桌容量，就塞不下，必须切成小块轮流上桌（分块）。这正是 SRAM 容量约束逼出分块的原因。

一个常见的错觉是「融合越多越好」。实际上融合越激进，对 SRAM 的压力越大、编译器/程序员要照顾的情况越多，反而可能得不偿失。哪些算子值得融、块开多大，都要结合具体硬件与问题规模权衡——这也是为什么本域强调不排名、不下普适定论。

### 08S-6.2.5 与重计算（recomputation）的关系
在反向传播时，FlashAttention 不把整张 S/P 矩阵存下来供反向使用，而是用前向阶段保存的少量归一统计量，在反向时重新算一遍所需的中间量——即用额外算力换显存（recompute-in-backward）。FlashAttention v1 摘要把 recomputation 与 tiling 并列为核心手段。

初学者第一反应往往是「重算不是更慢吗？」——在别的场景确实如此，但这里恰恰划算：因为瓶颈是显存和带宽（6.1），把那张 O(N²) 的大矩阵存下来占显存、还要写读 HBM，代价远高于反向时在 SRAM 里就地重算一遍。所以「多算一点、少存一点」在访存受限的世界里是净赚。

可以类比考试草稿：与其把每一步的庞大草稿都留着（占满桌面），不如只记下几个关键中间结论（归一统计量），需要时凭它们迅速重推。这样桌面（显存）腾出来了，重推的那点力气相比之下微不足道。这个「以算换存」的取舍，是理解 FlashAttention 反向为何省显存的钥匙（细节见 6.3.5）。

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 一手：FA v1 摘要（tiling + recomputation 减少 HBM 读写）；FA v2 摘要（降低非 matmul FLOP、减少经 shared memory 的 warp 间通信）佐证融合边界。
- 官方文档：Triton `getting-started/tutorials/06-fused-attention`——明标「Flash Attention v2 algorithm from Tri Dao」，演示单 kernel 融合。
- 课程：CS336 Sp26 L6 + Assignment 2（自写 Triton FlashAttention2）。
- 待核：launch 开销、SRAM 具体字节阈值——无一手数字，标待核。

## 08S-6.3 FlashAttention 分块（tiling）+ 在线 softmax

### 08S-6.3.1 分块（tiling）
分块指把 Q/K/V 沿序列维切成小块，逐块载入 SRAM，计算局部注意力并把结果增量累积到输出上，全程绝不物化完整的 N×N 分数矩阵 S。块大小依据 SRAM 容量选定（具体默认块大小随实现/硬件而变，待核）。

这就是 6.2.4 里「书桌放不下整本书，就一小摞一小摞上桌」的具体落地。外层遍历 Q 的块，内层遍历 K/V 的块；每对 (Q 块, K/V 块) 在 SRAM 里算出一小片局部注意力贡献，累加进对应的输出块，然后换下一块。整张 N×N 的表从未在任何时刻完整存在过。

难点在于：softmax 需要「一整行」的信息（要对该 query 与所有 key 的分数做归一），可分块时我们一次只看到一行里的一小段。怎么在「还没看全整行」的情况下就正确地做 softmax？答案就是下一节的在线 softmax——它让我们边看边更新，最后得到与看全整行完全一致的结果。

### 08S-6.3.2 在线 / 流式 softmax（online / streaming softmax）
在线 softmax 为每个 query 行维护两个「running」统计量：running max（记作 m）和 running sum（记作 l）。每来一个新的 K/V 块，就先用旧统计量对已累积的输出做一次缩放校正，再并入新块的贡献，边流边归一，最后统一除以 l。这个技法早于 FlashAttention（由 Milakov & Gimelshein 2018 提出 online softmax，⚠仅二手指回原文），FlashAttention 把它嵌进了分块注意力。

对第 j 个新块，更新规则是：

m_new = max(m, rowmax(S_ij))

α = exp(m − m_new)

l = α · l + rowsum(exp(S_ij − m_new))

O = α · O + exp(S_ij − m_new) · V_j

全部块处理完后再做一次归一：

O = O / l

为什么需要「校正因子 α」？因为每来一个新块，可能出现比之前更大的分数，于是全局最大值 m 变大了；而之前累积的 O 和 l 是用旧的、偏小的 m 减出来的，指数基准不一致。α = exp(m_old − m_new) 恰好把旧的累积量「重新缩放」到新基准上，使得无论分块顺序如何，最终结果都和一次性看全整行的 safe softmax 完全一致。

打个比方：你在陆续收到一批考试分数并想算加权平均，但为了防溢出你把所有分数都减去「目前见过的最高分」再取指数。每当来了一个破纪录的新高分，你之前所有的中间累积就得按新高分「统一降档」一次——α 就是这个统一降档的系数。理解了这一点，分块 softmax 就不再神秘：它不是近似，而是把一次性归一拆成了可增量维护的等价形式。

### 08S-6.3.3 数值稳定性（safe softmax）
安全 softmax 指在取指数前先减去该行的最大值，避免 exp 上溢：

softmax(x)_i = exp(x_i − max_j x_j) / Σ_k exp(x_k − max_j x_j)

分块 + 在线归一在浮点下与全量 softmax 数值等价（差异仅为浮点舍入量级）。

为什么要减最大值？因为 exp 增长极快，若某个分数是 89，exp(89) 就已接近单精度浮点的上限、再大就变成 inf，整行归一直接坏掉。减去该行最大值后，最大的那一项变成 exp(0)=1，其余都在 (0,1] 之间，既不溢出、比值又完全不变（分子分母同乘一个常数，softmax 结果不变）。这是所有正经 softmax 实现的标准操作。

关键澄清：在线 softmax 的「running max 减法」正是 safe softmax 在分块场景下的推广——它不是为了近似或提速而牺牲精度的技巧，而是保证「无论怎么切块，算出来和不切块逐位（在舍入意义上）一致」。下面的本机 numpy 实证就是把这句话坐实：分块在线 softmax 的输出与朴素全量 softmax 的输出，逐元素差只在机器精度量级。

`[验✓]` 本机 numpy 数值等价实证（无 GPU，仅验数值等价，不涉 kernel 性能）：分块在线 softmax 输出 vs 朴素全量 softmax 输出，最大逐元素绝对差 ≈ 5.0e-16（machine epsilon 量级），`np.allclose(..., atol=1e-12) → True`。此为补充，不替代多来源比对。

```python
import numpy as np
rng = np.random.default_rng(0); N, d = 512, 64
Q, K, V = (rng.standard_normal((N, d)) for _ in range(3)); scale = 1/np.sqrt(d)
S = (Q@K.T)*scale; S -= S.max(1, keepdims=True)
P = np.exp(S); P /= P.sum(1, keepdims=True); O_naive = P@V
Bk = 64; O = np.zeros((N, d)); m = np.full((N, 1), -np.inf); l = np.zeros((N, 1))
for j in range(0, N, Bk):
    Kj, Vj = K[j:j+Bk], V[j:j+Bk]; Sij = (Q@Kj.T)*scale
    m_new = np.maximum(m, Sij.max(1, keepdims=True)); P_ij = np.exp(Sij - m_new)
    alpha = np.exp(m - m_new); l = alpha*l + P_ij.sum(1, keepdims=True); O = alpha*O + P_ij@Vj; m = m_new
O /= l
print(np.max(np.abs(O_naive - O)), np.allclose(O_naive, O, atol=1e-12))
# → 4.996003610813204e-16 True   (基线 Py3.11.15/np2.4.6 @2026-07-25)
```

### 08S-6.3.4 不物化 S ⇒ 显存降阶
因为整张 N×N 的 S/P 矩阵从不落地，注意力的显存占用从

O(N²)

降到关于序列长度的线性

O(N)

（这是机制级陈述；具体常数系数不承诺，需具体系数标待核）。

这条是 FlashAttention 之所以能撑起长上下文的直接原因。朴素实现里那张随长度平方膨胀的大表是显存杀手：长度到几万，S 单独就要吃掉几十 GB。FlashAttention 让它根本不存在于 HBM，显存需求就退回到只跟 Q/K/V/O 这些「线性大小」的量有关。

注意区分两件事：算力上注意力仍是 O(N²) 量级（每个 query 还是要和每个 key 交互），FlashAttention 并没有、也不声称降低算力复杂度；它降的是显存和 HBM 访存。把「省显存」误当成「省算力」是这里最常见的混淆。

### 08S-6.3.5 反向传播：重计算 + 保存归一统计量
反向传播时同样不存 S/P，而是靠前向保存下来的 softmax 归一统计量（logsumexp，常记作 L）加上重新计算 S，来重建梯度所需的中间量——即 6.2.5 所说的以算力换显存。

具体地说，前向阶段除了输出 O，还会为每个 query 行额外存一个标量 L（把 running max 和 running sum 合并后的对数归一量）。有了 O 和 L，反向时就能在 SRAM 里就地把该块的注意力概率 P 重算出来，而不必在前向时把整张 P 存进 HBM 等着反向读。

初学者的顾虑仍是「重算不亏吗」——答案同 6.2.5：因为瓶颈在显存/带宽，存下 O(N²) 的 P 的代价远高于反向时重算，所以整体是净赚。为什么只存一个标量 L 就够、而不用存整行分数？因为 softmax 的归一只依赖「最大值 + 指数和」这两个汇总量，logsumexp 把它们打包成一个数，反向重建时凭它就能还原归一，无需保留原始的整行分数。

### 08S-6.3.6 「精确注意力（exact）」定位
FlashAttention 是精确（exact，非近似）注意力：它只改变 IO 与计算的调度顺序，不改变数学结果，算出来和朴素注意力在舍入意义下一致——这区别于稀疏/线性注意力（08S-4）那类通过近似来降复杂度的路线。FlashAttention v1 的标题里就写着 "Exact Attention"。

这一点常被误解，务必分清两条完全不同的加速路线。一条是「改数学」：稀疏注意力只算一部分 key、线性注意力用核函数近似 softmax——它们改变了模型实际计算的函数，是以精度换速度/复杂度。另一条是「改工程」：FlashAttention 一个分数都不丢、一次交互都不省，只是重新安排「先算什么、什么留在 SRAM、什么落 HBM」，得到逐位（舍入内）相同的结果。

因此把 FlashAttention 和稀疏/线性注意力并列比较「谁更准」是范畴错误：前者本就精确，谈的是访存效率；后者是近似方法，谈的是精度—效率权衡。6.3.3 的本机 numpy 等价实证，正是「精确」这一定位在数值上的直接佐证。

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 一手：FA v1 摘要（tiling、exact、O(N²)→线性显存、recomputation）；本机 numpy `[验✓]` 数值等价（补充，不替代多来源比对；实机 kernel 性能未验证）。
- 课程：CS336 Sp26 L6 + Assignment 2 以「用 Triton 实现 FlashAttention2」为练习。
- ⚠仅二手：online softmax 溯源 Milakov & Gimelshein 2018（指回一手论文，本轮未直取原文）。
- 待核：默认块大小、显存常数系数——不编造。

## 08S-6.4 Triton 编程模型

### 08S-6.4.1 Triton 是什么
Triton 是「用于并行编程的语言与编译器」，提供一个 Python 环境，让人较高效地写出能在 GPU 上跑满吞吐的自定义 DNN kernel（官方文档原述）。它由 Philippe Tillet 创立（文档版权署 Philippe Tillet），在 OpenAI 开发并开源。文档站锚 triton-lang.org/main（本轮未见页面显式版本号 ⇒ 版本号待核）。

放到语境里看它解决什么痛点：过去要写高性能 GPU kernel 基本只能用 CUDA C++，门槛高、周期长，深度学习研究者很难自己写一个又快又对的融合 kernel。Triton 的定位是「让你用近似 Python 的方式写 kernel，把大量底层优化交给编译器」，从而把手写高性能 kernel 的门槛降下来——FlashAttention 类的融合注意力正是它的典型用武之地。

对时效要保持警惕：Triton 是活跃演进的开源项目，API、支持的硬件、甚至治理归属都在变。本报告只把它当作「块级 kernel 编程模型」的一个代表来讲概念，凡涉具体版本号/API 签名一律标待核，不凭记忆写死。

### 08S-6.4.2 编程抽象层级：块级 vs 线程级
Triton 的抽象是「Blocked Program, Scalar Threads」——程序员写的是「块（program instance）」级的逻辑；而 CUDA 是「Scalar Program, Blocked Threads」，即写标量线程级逻辑、手动管理线程块（SIMT 手写）。二者是不同的抽象取舍，不排名。

用一个类比拆开这两句拗口的话：CUDA 像是你要亲自指挥一整队士兵（线程），得操心每个士兵站哪、怎么协同、怎么对齐取数据——控制力极强，但事无巨细。Triton 则让你只下达「以这一整块数据为单位做什么」的命令（比如「把这一块和那一块做矩阵乘」），至于块内部怎么把活分给一个个线程、怎么向量化、怎么合并访存，交给编译器去排。

对初学者，记住一句话就够：Triton 把「块内线程怎么协作」这层脏活抽走了，你只需按「块」来思考。这既是它上手快的原因，也意味着你放弃了一部分对底层的精细控制——用不用得着那部分控制，取决于具体需求（见 6.4.4），本域不下普适优劣定论。

### 08S-6.4.3 核心原语（概念级）
教程/文档中出现的常见原语（以官方为准）包括：`tl.program_id`（拿到当前 program/块的编号，用来定位自己该处理哪一块）、`tl.load` / `tl.store`（带 mask 的 HBM 读写）、`tl.dot`（块级矩阵乘）、以及用于掩码的手段（如 `tl.where` 实现 causal mask）。较新的内存描述符 `tl.make_tensor_descriptor` 出现在当前 fused-attention 教程中；`tl.make_block_ptr` 未在该页出现（API 随版本变动 ⇒ 具体签名待核）。

把这几个原语串成一个 kernel 的骨架就好懂了：先用 `tl.program_id` 问「我是第几块、负责哪段数据」，用 `tl.load` 把这一块从 HBM 搬进 SRAM（mask 用来处理边界不整除或 causal 遮挡），用 `tl.dot` 在块上做矩阵乘（QKᵀ、×V），中间的 softmax 校正用逐元素运算和 `tl.where` 做，最后用 `tl.store` 把结果写回 HBM。这基本就是一个融合注意力 kernel 的雏形。

务必带着「版本会变」的警惕读这一节：原语的确切名字、签名、乃至是否推荐使用都可能随 Triton 版本迁移（`make_block_ptr` 与 `make_tensor_descriptor` 的此消彼长就是例子）。因此这里只讲「概念上有哪几类原语、各干什么」，任何要写进代码的确切 API 都应回官方文档当版核对，本报告对不确定项标待核。

### 08S-6.4.4 与 CUDA 的分工/取舍
Triton 换取的是开发效率与编译器自动优化；CUDA 提供的是极致的底层控制。什么时候停在 Triton、什么时候下探到 CUDA，取决于具体需求（此点与 L6-06 CUDA 主题重叠，本报告只在 LM kernel 语境下陈述），不下优劣定论。

给初学者一个不绝对化的判断框架：当你要的是「快速写出一个够快的融合 kernel、且能接受编译器替你做大部分调度」时，Triton 往往顺手；当你需要压榨最后一点性能、或要用到编译器还没覆盖的特定硬件指令/调度模式时，才有理由下探到 CUDA 亲自操刀。两者不是替代关系而是不同抽象层，很多真实项目里二者并存。

要避免的思维定式是「Triton 一定比 CUDA 简单所以更好」或反过来「CUDA 才是真功夫」。本域刻意不排名——因为「更好」高度依赖任务、硬件代际和团队，且工具本身在快速演进，今天的结论明天可能就变。

### 08S-6.4.5 本机限制
Triton kernel 需要在 GPU 上运行；本机无 GPU，因此不做任何 Triton kernel 的实机实证，本节只作概念陈述，涉执行处标「实机未验证」。

这条是诚实边界的声明，不是偷懒。它落实了前沿纪律里「不编造 kernel 细节/基准数字」的红线：既然跑不了，就绝不假装跑过、更不臆造加速比或占用率。本报告中唯一做过的本机实证是 6.3.3 的在线 softmax 数值等价（纯 numpy、验数学不验性能），那与「Triton kernel 在 GPU 上的行为」是两回事，不可混为一谈。

读者若要亲验 Triton kernel，需要一台带 NVIDIA GPU 的机器，装好对应版本的 Triton，跑官方 fused-attention 教程——这超出本机（无 GPU）能力范围，故留白。

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 官方文档（一手）：triton-lang.org/main —— index（语言+编译器定义）、programming-guide ch1（Blocked Program/Scalar Threads vs CUDA；编译器自动优化清单；版权 Philippe Tillet）、tutorials/06-fused-attention（FA v2 实现 + 原语）。
- 课程：CS336 Sp26 L6「Kernels, Triton」+ Assignment 2。两源（文档 + 课程）交叉印证块级模型与 Triton 用于 FlashAttention。
- 待核：文档站精确版本号、`tl.*` 各 API 精确签名、Triton 治理归属现状（Tillet/OpenAI 之外的基金会托管情况）。

## 08S-6.5 版本演进：FA v1 → v2 → v3（逐版锚，显式 delta）

### 08S-6.5.1 FA v1（Dao, Fu, Ermon, Rudra, Ré 2022 · arXiv:2205.14135）
v1 确立了整个基本盘：IO-aware + tiling + 在线 softmax + recomputation，实现精确注意力，同时把显存降到线性、减少 HBM 访问。其摘要报告了端到端加速：BERT-large（序列 512）约 15%、GPT-2（序列 1K）约 3×、Long-Range Arena（1K–4K）约 2.4×（这些是论文自报的模型级数字，非本机实测）。

理解 v1 的意义在于：它是前面 6.1–6.3 所有机制的「首次合体落地」。此前 online softmax、分块这些想法零散存在，v1 第一次把它们组织成一个既精确、又显存线性、又真正减少 HBM 往返的完整注意力算法，并证明它在真实模型上端到端更快。可以把 v1 记成「FlashAttention 的奠基版」，后两代都是在它基础上针对硬件利用率继续打磨。

看这些加速数字时要带两条纪律：其一，它们是论文在特定模型/序列长度下自报的，不是通用保证，更不是本机实测（本机无 GPU）；其二，端到端加速依赖具体配置，换个模型/长度/硬件就会变。所以本报告如实转述并注明出处，不把它们当成「FlashAttention 永远快 N 倍」的普适结论。

### 08S-6.5.2 FA v2（Dao 2023 · arXiv:2307.08691）
v2 针对 v1「在 GPU 上只达到理论峰值的约 25–40%」这一低利用率问题，做并行化与工作划分的改进。其摘要给出三点 delta：(1) 调整算法以减少非 matmul 的 FLOP；(2) 在序列长度维上做并行——即使单个注意力头也跨 thread block 并行，以提高 GPU 占用率（occupancy）；(3) 改进 warp 之间的工作划分，减少经由 shared memory 的通信。论文自报约 2× 于 v1，并达到 A100 理论 FLOPs/s 的 50–73%（论文数字，锚 A100）。

为什么要专门降「非 matmul FLOP」？因为 GPU 上有专门的矩阵乘单元（Tensor Core）算 matmul 极快，而 softmax 里的 exp、缩放等非 matmul 运算走的是普通单元、相对更贵。v1 里这类运算占比偏高，拖累了利用率；v2 通过重排算法让宝贵的 Tensor Core 尽量满负荷、把非 matmul 的部分挤掉一些，从而把「峰值利用率」往上抬。这就是它相对 v1 的核心进步方向：不改数学结果（仍是精确注意力），改的是怎么把活更均匀地铺到 GPU 的并行资源上。

「占用率」「warp 间工作划分」对初学者可能陌生，可粗略理解为「让 GPU 上更多的计算单元同时有活干、并且彼此少扯皮（少走慢速的 shared memory 互相传数据）」。同样，2× 和 50–73% 都是论文在 A100 上自报的数字，锚定硬件、非本机实测，转述时保留出处与硬件锚。值得一提：Triton 官方 fused-attention 教程实现的正是 v2 算法，这也交叉印证了 v2 是当前工程上的主流基线。

### 08S-6.5.3 FA v3（Shah, Bikshandi, Zhang, Thakkar, Ramani, Dao 2024 · arXiv:2407.08608）
v3 面向 Hopper（H100）架构（针对 v2 在 H100 上仅约 35% 利用率的问题）。其摘要给出三条机制：(1) 利用 Tensor Core 与 TMA（Tensor Memory Accelerator）的异步能力；(2) warp-specialization，把计算与数据搬运重叠起来（含 block-wise 的交错）；(3) FP8 低精度——用 block quantization + incoherent processing 来提精度。论文自报 FP16 约 1.5–2.0×、最高约 740 TFLOPs/s（约 75% 利用率）；FP8 近 1.2 PFLOPs/s、数值误差比 baseline FP8 低约 2.6×（论文数字，锚 H100；FP8 精度实现细节待核）。

v3 的主题词是「异步」。新一代 GPU（Hopper）提供了让「搬数据」和「做计算」真正并行重叠的硬件能力（TMA 负责异步搬运、warp-specialization 让不同的 warp 分工——有的专门搬、有的专门算），v3 就是把注意力算法改造成能吃满这些新特性的形态。换句话说，v3 之于 v2，很大程度是「把算法对齐到 Hopper 的新硬件特性」，而不是又发明一套全新数学。

FP8 那部分要额外谨慎理解：把数值精度从 FP16 降到 FP8 能进一步提速，但更容易掉精度，于是 v3 用 block quantization（分块量化）和 incoherent processing 等手段来把误差压回去。这些是精度—效率的工程权衡，具体的量化参数/实现细节本报告标待核、不臆造。所有 PFLOPs/TFLOPs 数字同样是论文在 H100 上自报，锚硬件、非本机实测。

### 08S-6.5.4 演进主线：锚硬件代际
把三代连起来看，会浮现一条清晰主线：v3 与具体 GPU 架构（Hopper 的 async / TMA / FP8）强绑定，因此它是一个「锚硬件代际」的对象——一旦出现新架构，它的结论就需要重新锚定。v1/v2 主要在 Ampere（A100）语境下立数。三代之间不排优劣，只是各自针对不同的瓶颈/硬件给出的 delta。

这条主线是本小主题最该带走的判断力：FlashAttention 的每一代都不是抽象意义上的「更好」，而是「针对当时那代硬件的瓶颈做的适配」。v1 打通机制、v2 提升在 A100 上的利用率、v3 吃满 Hopper 的异步与低精度能力。所以读到「v3 更快」时，正确的理解是「在 H100 上、用了它的新特性时更快」，而不是脱离硬件的普适排名。

对时效的含义也很直接：GPU 大约每一两年出新架构，每次都可能催生新一版或新算法。本报告把成熟度硬标为 `⚙演进快·锚版本`，正是提醒读者——今天的对照表锚在 A100/H100，来年出了新架构，就得回到一手论文重新核对，而不是沿用旧结论。

### 08S-6.5.5 版本差异对照表
下表把三代的目标瓶颈、关键改动、绑定硬件、自报数字并列，便于横向对齐。所有加速比/利用率均为各论文自报（非本机实测，实机未验证）；空缺或不确定的格子标「待核」，不编造。⚙演进快·锚版本。

| 版本 | 年/arXiv | 目标瓶颈 | 关键改动（delta） | 绑定硬件锚 | 自报数字（论文） |
|---|---|---|---|---|---|
| v1 | 2022 / 2205.14135 | HBM 访存 / O(N²) 显存 | IO-aware + tiling + 在线 softmax + recomputation；精确、显存线性 | GPU 通用（A100 语境） | BERT-large 15% / GPT-2 3× / LRA 2.4×（端到端） |
| v2 | 2023 / 2307.08691 | v1 利用率仅 25–40% | 降非 matmul FLOP；序列维跨 block 并行；改 warp 间工作划分减 shared-mem 通信 | A100（Ampere） | ~2× vs v1；A100 峰值 50–73% |
| v3 | 2024 / 2407.08608 | v2 在 H100 仅 ~35% | 异步 Tensor Core / TMA；warp-specialization 重叠计算/搬运；FP8（block quant + incoherent processing） | H100（Hopper） | FP16 1.5–2.0×、~740 TFLOPs/s（~75%）；FP8 ~1.2 PFLOPs/s、误差低 ~2.6×（FP8 细节待核） |

怎么读这张表：从左到右是「因为什么慢 → 改了什么 → 在哪代硬件上 → 结果自报多少」。竖着对比「目标瓶颈」一列，能一眼看出三代其实是在追着「利用率」这个指标跑——v1 解决了根本的访存问题，但利用率不高；v2 把 A100 的利用率抬上去；v3 再把 H100 的利用率抬上去。这正是 6.5.4 那条「锚硬件代际」主线的表格化。

再次强调纪律：表里每个数字都带着「哪代硬件、论文自报」的前提，不能剥离前提当普适结论用；标「待核」的格子是诚实留白，等有一手来源再补，绝不用记忆填平。

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 一手（逐版摘要，交叉核对）：FA v1 arXiv:2205.14135；FA v2 arXiv:2307.08691（arXiv id 已核符）；FA v3 arXiv:2407.08608（已核符，Hopper/FP8）。
- 课程：CS336 Sp26 L6 引 FlashAttention；Assignment 2 指定实现 FA2。Triton 官方教程亦标其实现为 FA v2 → 交叉印证 v2 为当前工程主流基线。
- ⚠仅二手（非承重）：Dao-AILab/flash-attention 仓库 README 可佐证各版支持硬件，本轮未直取，指回论文。
- 待核：FP8 量化精度参数、各版默认块大小、除摘要外更细的 kernel 实现数字——无一手即留白，不编造。

---

## 一手引用清单（论文 + 官方文档 + 课程）
1. Dao, Fu, Ermon, Rudra, Ré. *FlashAttention: Fast and Memory-Efficient Exact Attention with IO-Awareness.* 2022. arXiv:2205.14135. https://arxiv.org/abs/2205.14135
2. Dao. *FlashAttention-2: Faster Attention with Better Parallelism and Work Partitioning.* 2023. arXiv:2307.08691. https://arxiv.org/abs/2307.08691
3. Shah, Bikshandi, Zhang, Thakkar, Ramani, Dao. *FlashAttention-3: Fast and Accurate Attention with Asynchrony and Low-precision.* 2024. arXiv:2407.08608. https://arxiv.org/abs/2407.08608
4. Triton 官方文档（triton-lang.org/main）：index；programming-guide ch1 introduction；getting-started/tutorials/06-fused-attention。版权 Philippe Tillet（2020–）。文档站版本号待核。
5. Stanford CS336 *Language Modeling from Scratch*, Spring 2026. Lecture 6「Kernels, Triton」；Assignment 2（用 Triton 实现 FlashAttention2）。https://cs336.stanford.edu/

## 二手（非承重）清单
- Milakov & Gimelshein 2018, *Online normalizer calculation for softmax*（online softmax 溯源；本轮未直取原文，指回一手）。
- Dao-AILab/flash-attention 仓库 README（各版支持硬件的二手佐证，指回论文）。
