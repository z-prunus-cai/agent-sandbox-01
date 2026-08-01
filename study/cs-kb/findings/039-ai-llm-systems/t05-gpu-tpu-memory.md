# L6-08·大主题08S-5 GPU/TPU 硬件与内存层次

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01｜先修：L6-08·大主题08S-2 资源核算（FLOP/显存核算）、L6-03·03-6 注意力与 Transformer、L1-03 线性代数（矩阵乘）｜一手锚点：CS336「Language Modeling from Scratch」L5「GPUs, TPUs」(Spring 2026 offering) https://cs336.stanford.edu/ 、Williams/Waterman/Patterson「Roofline: An Insightful Visual Performance Model for Multicore Architectures」CACM 52(4) 2009、Dao et al. 2022「FlashAttention」arXiv:2205.14135（A100 内存层次数字）、NVIDIA Hopper/H100 架构白皮书与 H100/H200/A100 官方 datasheet、NVIDIA Blackwell/B200 官方规格、Google Cloud TPU v5e/v5p/v6e 官方文档｜成熟度：⚙演进快·锚版本 CS336 Spring 2026；硬件具体数值 ⚙锚硬件代际·随代际变

这一大主题只讲一件事的四个侧面：为什么在一块 GPU/TPU 上，"数据从哪层内存搬、搬多快"往往比"能算多少次乘加"更决定实际速度。它和通用 CUDA/HPC 课（L6-06）强重叠，这里只从语言模型的语境切入——目的是给后面的 kernel（08S-6）、推理（08S-9）打一个"内存层次"的直觉底座，不重复通用 GPU 编程立项。

四章的逻辑是一条线：先看硬件把内存分成快而小的 SRAM 和慢而大的 HBM 两级金字塔（5.1）；再用 roofline 模型把"带宽墙"和"算力墙"画在一张图上（5.2）；算术强度（5.3）是把某个具体运算放到这张图上的横坐标；最后 compute-bound 还是 memory-bound 的判定（5.4），就是看这个横坐标落在拐点的左边还是右边。

所有具体型号的带宽/容量/算力数字都随硬件代际快速变化，本篇给出的数值都锚定到具体型号 + 核实日期；机制（金字塔、roofline、算术强度）本身是稳定的，数字才是易变的。

## 08S-5.1 HBM vs SRAM 层次

### 内存金字塔：为什么要分层

现代 GPU/TPU 的内存不是一整块，而是分成好几层：越靠近计算单元的越快、越小、越贵；越远的越慢、越大、越便宜。典型的自上而下是：寄存器（register）→ 片上 SRAM（在 GPU 上表现为 L1/共享内存和 L2 缓存）→ 片外高带宽显存 HBM（就是大家说的"显存 80GB/141GB"那一层）→ 再往外还有主机 CPU 的 DRAM 和跨卡互联。这个"快小 vs 慢大"的排布就叫内存层次（memory hierarchy）或内存金字塔。

之所以要分层，是因为物理上做不到"又大又快又便宜"。SRAM 每比特用 6 个晶体管、贴在计算核旁边，速度极快但一做大芯片面积就爆炸，所以只能是几十 MB 级；HBM 是堆叠的 DRAM 芯片通过硅中介层连到 GPU，能做到上百 GB，但带宽和延迟都远不如片上 SRAM。计算要用的数据必须先从 HBM 搬到片上 SRAM/寄存器才能被算，算完再写回 HBM——这一"搬进搬出"的过程就是访存，很多 LM 算子的真正瓶颈就卡在这里，而不是卡在乘加本身。

对初学者最重要的一句话直觉：GPU 的算力常常是"过剩"的，喂不饱它的是从 HBM 往片上搬数据的带宽。后面 roofline 和 FlashAttention 全建立在这个事实上。

### HBM：大而慢的那一层

HBM（High Bandwidth Memory，高带宽显存）是 GPU 的主显存，用"堆叠 DRAM + 宽总线"换来比普通 GDDR 高得多的带宽。它决定了两件事：一是模型加参数、KV cache、激活能不能放得下（容量墙），二是数据往计算单元搬得多快（带宽墙）。

几个代际的官方规格（⚙锚硬件代际，核实 2026-08-01）：

```
A100 (Ampere)      : 40/80 GB HBM2e   ,  ~1.5–2.0 TB/s
H100 SXM (Hopper)  : 80 GB HBM3       ,  ~3.35 TB/s
H200 (Hopper)      : 141 GB HBM3e     ,  ~4.8 TB/s
B200 (Blackwell)   : 192 GB HBM3e     ,  ~8 TB/s（双 die 合计）
```

要注意"同架构也可能只换显存"：H200 和 H100 都是 Hopper 架构、算力相近，H200 主要是把显存从 80GB/3.35TB/s 升到 141GB/4.8TB/s——这恰恰说明在很多 LM 负载里，厂商认为"加显存和带宽"比"加算力"更值钱，侧面印证了 memory-bound 的普遍性。Blackwell B200 则把两颗 die 用片内高速接口（NV-HBI）连成一颗逻辑 GPU，各接 4 个 HBM3e 堆栈，合起来到约 8 TB/s（这些数字随 SKU 和最终产品有出入，具体以当代 datasheet 为准）。

TB/s 这个单位对初学者很抽象。可以这样感受：H100 每秒能从 HBM 搬约 3.35 万亿字节，但一个 70B 参数模型光权重（bf16, 2 字节）就有 140 GB，把它完整读一遍就要 140/3350 ≈ 0.042 秒——推理每生成一个 token 都要把权重读一遍时，这 40 毫秒级的"读权重"时间就是 decode 慢的根因（见 5.4 与 08S-9）。

### 片上 SRAM：小而快的那一层

片上 SRAM 是集成在 GPU die 上的高速存储，在 NVIDIA GPU 上主要以两种形式出现：每个 SM（流多处理器，Streaming Multiprocessor，GPU 里成百上千个并行计算单元的基本单位）内的 L1/共享内存（shared memory，程序员可显式管理的暂存区），以及所有 SM 共享的 L2 缓存。它容量以 KB～几十 MB 计，但带宽是 HBM 的近十倍、延迟低一个量级。

FlashAttention 论文给出的 A100 数字是本领域最常被引用的一组（arXiv:2205.14135，核实 2026-08-01）：

```
A100 片上 SRAM : 192 KB / SM × 108 SM ≈ 20 MB 总量,  ~19 TB/s
A100 HBM       : 40 GB               ,  ~1.5–2.0 TB/s
```

SRAM 比 HBM 带宽高约 10 倍，但容量小近 4000 倍。这正是 FlashAttention 的立论——把注意力计算分块，让 K/V/Q 的小块尽量待在 SRAM 里反复用，避免把 N×N 的大注意力矩阵落回慢的 HBM。

Hopper 代际（H100）把每个 SM 的可配置 L1/共享内存提到 228 KB，L2 缓存做到约 50 MB（NVIDIA Hopper 白皮书，核实 2026-08-01），比 A100 更大；商用 H100 启用 132 个 SM（完整 GH100 die 有 144 个）。这些数字每代都在变，记机制就好：SRAM 快小、HBM 慢大，中间还有一层 L2 作缓冲。

### 延迟、带宽、容量：三个不同的维度

初学者容易把"慢"混为一谈，其实内存有三个独立指标：延迟（latency，一次访问从发起到拿到数据的等待时间）、带宽（bandwidth，单位时间能搬多少字节）、容量（capacity，总共能存多少）。金字塔越往上，延迟越低、带宽越高、容量越小。

在 GPU 上，训练/推理这类大吞吐负载最关心的是带宽而不是延迟——因为 GPU 靠海量线程并发来"掩盖"单次访问的延迟（一个线程等数据时，硬件切去跑别的线程），所以延迟通常不是主瓶颈，能不能持续把带宽喂满才是。这与 CPU 上"延迟敏感"的直觉正好相反，是 GPU 编程的一个关键心态转变。

容量则是另一条独立的墙：哪怕带宽够，HBM 装不下模型 + KV cache + 激活，就得靠量化、分片（08S-7）、PagedAttention（08S-9）等手段。带宽墙决定"跑多快"，容量墙决定"跑不跑得起来"，两者不要混淆。

### TPU 的内存层次：换个厂商同样的金字塔

Google TPU 是另一条硬件路线，但内存层次的骨架一样：片上有大块 SRAM 暂存（TPU 上叫 VMEM/统一缓冲区一类的片上存储），片外是 HBM 主存，计算核心是一个大的脉动阵列 MXU（Matrix Multiply Unit）。TPU 的设计更"专"，把大量面积压在矩阵乘上。

几个代际的 HBM 规格（Google Cloud TPU 官方文档，⚙锚硬件代际，核实 2026-08-01）：

```
TPU v5e         : 16 GB HBM ,  ~819 GB/s ,  MXU 128×128
TPU v5p         : 95 GB HBM ,  ~2.76 TB/s,  MXU 128×128
TPU v6e (Trillium): 32 GB HBM, ~1.6 TB/s ,  MXU 256×256
```

MXU 是 TPU 的核心：一个 128×128（v6e 起扩到 256×256）的脉动阵列，一个大方阵一次装进去做矩阵乘，数据像水波一样在阵列里流动、边流边乘加。它对初学者的意义是——TPU 把"矩阵乘要尽量大块、复用要尽量高"这件事直接刻进了硬件形状，所以给 TPU 喂小矩阵/低算术强度的算子特别浪费。这和 GPU 张量核（Tensor Core）追求大 GEMM 是同一个道理，只是 TPU 更极端。

具体每代的峰值算力（TFLOPS/BF16 等）随代际和精度而变，本篇不逐一落定，需要时查当代官方规格并标核实日期。

#### 来源与时效
- 一手锚点：CS336 Spring 2026 L5「GPUs, TPUs」https://cs336.stanford.edu/ （lecture_05，核实 2026-08-01）；Dao et al. 2022「FlashAttention」arXiv:2205.14135 §2（A100 SRAM 192KB/SM×108、~19 TB/s；HBM 40GB、1.5–2.0 TB/s），核实 2026-08-01。
- 厂商规格（⚙锚硬件代际，核实 2026-08-01）：NVIDIA H100 SXM datasheet / Hopper 架构白皮书（80GB HBM3、~3.35 TB/s、L2 ~50MB、L1/shared 228KB/SM、132 SM）；NVIDIA H200 datasheet（141GB HBM3e、~4.8 TB/s）；NVIDIA B200/Blackwell 规格（192GB HBM3e、~8 TB/s、双 die NV-HBI）；NVIDIA A100 datasheet；Google Cloud TPU v5e/v5p/v6e 官方文档（HBM 容量/带宽、MXU 128×128→256×256）。
- 冲突与「待核」：H200/B200 的容量与带宽在不同二手来源略有出入（如 B200 HBM 有 180GB/192GB 两种口径、带宽 ~8 TB/s 为双 die 合计），以当代 NVIDIA 官方 datasheet 为准；TPU 具体峰值 TFLOPS 未落定，标「待核」；同型号 SXM/PCIe/不同显存 SKU 数字不同，本表取 SXM/主流口径。厂商公布的带宽多为理论峰值，实测可达率通常低于此，属正常。

## 08S-5.2 Roofline 模型

### Roofline 是什么

Roofline 是一张把"一段程序在某块硬件上最多能跑多快"可视化出来的图（Williams/Waterman/Patterson, CACM 2009）。横轴是算术强度（FLOP/byte，见 5.3），纵轴是可达到的计算吞吐（FLOP/s）。图上有一条像屋顶的折线：左边是一条斜线（受内存带宽限制），右边是一条水平线（受峰值算力限制），两者相交处叫拐点（ridge point）。任何一个 kernel 都是图上一个点，它的高度不会超过这条屋顶。

它解决的问题是：面对一段慢代码，先别急着优化，先判断它到底是"算不过来"（compute-bound，卡在水平屋顶）还是"数据搬不过来"（memory-bound，卡在斜屋顶）——这两种病的药方完全不同，roofline 让你一眼看出该往哪个方向使劲。

### 屋顶的两段与公式

一个 kernel 的可达吞吐由下式的两个上限取小决定：

```
可达吞吐 P = min( 峰值算力 P_peak ,  峰值带宽 B × 算术强度 I )
```

其中 P_peak 单位是 FLOP/s，B 是 HBM 峰值带宽（byte/s），I 是算术强度（FLOP/byte）。

这个 min 的两项就是两段屋顶。当算术强度 I 小时，B×I 这一项小于 P_peak，程序被带宽卡住，吞吐随 I 线性上升——这是左边那条斜线（斜率就是带宽 B，所以叫"带宽屋顶/内存屋顶"）。当 I 大到 B×I 超过 P_peak，程序被算力卡住，吞吐顶到 P_peak 不再涨——这是右边的水平线（"算力屋顶/计算屋顶"）。

初学者可以这样理解那条斜线：带宽固定，每搬一个字节最多能配 I 次浮点运算，所以每秒能完成的浮点运算 = 每秒搬的字节数 × 每字节配的运算数 = B × I。I 越大，同样的带宽就能撑起越高的算力利用——这就是"提高算术强度"为什么能救 memory-bound 程序。

### 拐点 ridge point

两段屋顶相交处的横坐标叫拐点，它是那个恰好从"带宽受限"切换到"算力受限"的临界算术强度：

```
I_ridge = P_peak / B
```

拐点右边（I > I_ridge）的算子是 compute-bound，左边（I < I_ridge）是 memory-bound。所以拐点是判定瓶颈的一把尺子：只要算出某算子的算术强度，和硬件的拐点比一下大小就知道它是哪一类。

用上面 cross-check 过的数字自己代一下（示意值，由公式从峰值算力/带宽推出，非官方发布）：

```
A100 (BF16 ~312 TFLOPS, HBM ~2.0 TB/s) : I_ridge ≈ 312e12 / 2.0e12 ≈ 156 FLOP/byte
H100 (BF16 ~990 TFLOPS, HBM ~3.35 TB/s): I_ridge ≈ 990e12 / 3.35e12 ≈ 295 FLOP/byte
```

算力涨得比带宽快，所以拐点在逐代右移（A100 约 156 → H100 约 295 FLOP/byte）。拐点越靠右，意味着要达到 compute-bound 需要越高的算术强度，越来越多的算子会掉到左边变成 memory-bound——这是"内存墙"越来越重要的量化体现。（其中 H100 的 BF16 峰值不同来源/口径有 dense ~990 与含稀疏翻倍等说法，A100 的 HBM 带宽有 1.5–2.0 TB/s 区间，故上述拐点是数量级示意，精确值随 SKU 与口径变，⚙锚硬件代际。）

### 怎么读一张 roofline 图

拿到一张 roofline 图，先看你的 kernel 那个点落在哪：若贴着左边斜线，它是 memory-bound，被带宽卡住，此时提高算力（换更强的卡的算力部分）没用，得想办法提高算术强度或减少访存；若贴着右边水平线，它是 compute-bound，被算力卡住，此时优化访存收益有限，得靠更强算力或更低精度（如 FP8）来抬高那条水平屋顶。若点远低于屋顶，说明既没打满带宽也没打满算力，通常是并行度不够、延迟没掩盖、或 kernel 启动开销大，有优化空间。

Roofline 的价值就在于"先诊断再开药"：它不告诉你怎么改代码，但告诉你改哪个方向不会白费力气。这是它作为教学/工程工具最实用的地方。

#### 来源与时效
- 一手锚点：Williams, Waterman, Patterson「Roofline: An Insightful Visual Performance Model for Multicore Architectures」Communications of the ACM 52(4), April 2009（roofline 定义、operational/arithmetic intensity、ridge point），核实 2026-08-01；CS336 Spring 2026 L5「GPUs, TPUs」https://cs336.stanford.edu/ （在 LM 语境下讲 roofline / 算术强度），核实 2026-08-01；相关 resource accounting 亦见 CS336 L2（FLOPs, memory, arithmetic intensity）。
- 数值（⚙锚硬件代际）：拐点示意值由 §5.1 已 cross-check 的峰值算力（A100 BF16 ~312 TFLOPS、H100 BF16 ~990 TFLOPS）与 HBM 带宽（~2.0 / ~3.35 TB/s）按 I_ridge = P_peak/B 推出，非厂商直接发布；不同精度（FP8/FP16/BF16）与是否含结构化稀疏会改变 P_peak，进而移动拐点。
- 冲突与「待核」：原论文用 "operational intensity"，ML 圈多用 "arithmetic intensity"，两词此处等义；H100 dense BF16 峰值有 ~990 TFLOPS 与含稀疏 ~1979 TFLOPS 两种口径，取用时须标清 dense/sparse，具体以 NVIDIA datasheet 为准，标「待核」到具体 SKU。

## 08S-5.3 算术强度

### 定义：每搬一个字节配多少次运算

算术强度（arithmetic intensity，也叫 operational intensity）是一个算子完成的浮点运算数除以它在慢内存（HBM/DRAM）上搬运的字节数：

```
算术强度 I = 浮点运算总数 (FLOP) / HBM 访存字节数 (byte)
```

它衡量的是"数据复用率"——同一份从 HBM 搬上来的数据，被拿去做了多少次运算。I 高说明搬一次算很多次，划算；I 低说明搬来只算一两次就丢，带宽全耗在搬运上。它正是 roofline 图的横坐标，是把某个具体运算定位到"带宽墙 vs 算力墙"哪一侧的钥匙。

I 不是硬件属性，而是算法/算子的属性（外加数据类型、是否复用缓存的影响）。同一块 GPU 上，大矩阵乘的 I 很高、逐元素加法的 I 很低。所以"这段代码快不快"一半看硬件（roofline 屋顶），一半看你写的算子的算术强度（横坐标）。

### 怎么算一个算子的算术强度：矩阵乘为例

以最核心的矩阵乘 C = A·B 为例，A 是 m×k、B 是 k×n，结果 C 是 m×n。浮点运算数约为（每个输出元素做 k 次乘加、每次乘加算 2 FLOP）：

```
FLOP = 2 · m · n · k
```

访存字节数（假设每个元素占 s 字节，A、B、C 各读/写一遍，不计缓存复用）：

```
bytes = s · (m·k + k·n + m·n)
```

于是算术强度：

```
I = 2·m·n·k / [ s · (m·k + k·n + m·n) ]
```

取一个方阵极端例子 m=n=k=N、fp16（s=2 字节）：FLOP = 2N³，bytes = 2·3N² = 6N²，于是

```
I = 2N³ / 6N² = N / 3  (FLOP/byte)
```

这说明矩阵越大，算术强度越高——N 一大就轻松越过拐点，进入 compute-bound。这就是"大 GEMM 是 GPU 最爱"的量化原因：它把搬来的每块数据复用了 O(N) 次。反过来，N 很小（比如 batch=1 的向量-矩阵乘，等价于某一维退化）时 I 掉到 O(1)，立刻变 memory-bound。

### 低算术强度的典型：逐元素与归一化

逐元素运算（elementwise，如激活函数、残差相加、缩放）和归一化（LayerNorm/RMSNorm）是低算术强度的代表。对一个 N 元素张量做 y=f(x)：读 N 个元素、写 N 个元素，中间每个元素只做常数次运算，所以

```
I = O(N) / O(N) = O(1)  FLOP/byte
```

它是一个和规模无关的小常数，远在拐点左边，铁定 memory-bound。这解释了两件事：一是为什么这些算子单独看"没算多少"却依然耗时——时间全花在从 HBM 读写那 2N 个元素上；二是 kernel 融合（08S-6）为什么有用——把连续几个逐元素算子融成一个 kernel，中间结果不落 HBM，等于把多次"读+写"合并成一次，直接抬高了整体算术强度。

### LM 里的算术强度地图

把这套尺子套到语言模型上，就能预判各阶段的瓶颈。训练和推理 prefill（一次处理整段 prompt）里，序列长、批量大，主体是大矩阵乘，算术强度高，偏 compute-bound（但注意力那部分因要读写 N×N 矩阵，naive 实现会掉到 memory-bound，正是 FlashAttention 要治的）。推理 decode（逐 token 生成）里，每步只处理一个新 token，权重矩阵乘退化成矩阵-向量乘，每个权重从 HBM 读上来只用一次，算术强度约 O(1)，重度 memory-bound——这就是"decode 是 memory-bound"这句 LM 系统金句的来历（详见 5.4 与 08S-9）。

对初学者，这张地图的用法是：想加速某阶段前，先估它的算术强度落在拐点哪边。落左边就别去堆算力，该减访存/提批量/融 kernel；落右边才考虑更强算力或更低精度。具体某模型某形状的精确 I 值需按实际维度和 dtype 代入上面公式算，本篇给的是量级判断，不落具体基准数字（那些随模型/实现/硬件变，标「待核」）。

#### 来源与时效
- 一手锚点：Williams/Waterman/Patterson CACM 2009（operational/arithmetic intensity 定义与 FLOP/byte 单位）；CS336 Spring 2026 L2「resource accounting: FLOPs, memory, arithmetic intensity」与 L5「GPUs, TPUs」https://cs336.stanford.edu/ ，核实 2026-08-01；Dao et al. 2022 FlashAttention arXiv:2205.14135（注意力为访存受限、需提高数据复用）。
- 推导：矩阵乘 FLOP=2mnk 与逐层 FLOP 核算同 08S-2.3（≈6ND 训练经验估）一脉相承；算术强度公式 I=FLOP/bytes 为定义式，方阵 I=N/3（fp16）由代入得出，属可自证的数量级结论，非编造基准。
- 冲突与「待核」：是否计入缓存/寄存器复用会显著改变实测字节数（上式按"无复用"上界估），故实测算术强度常高于朴素公式值；具体模型各阶段的精确算术强度数值随维度/dtype/实现而变，未落定标「待核」。

## 08S-5.4 compute- vs memory-bound 判定

### 判定准则：算术强度 vs 拐点

一个算子是 compute-bound 还是 memory-bound，就看它的算术强度 I 和硬件拐点 I_ridge = P_peak/B 谁大：

```
I > I_ridge  ⇒ compute-bound（卡算力，贴右边水平屋顶）
I < I_ridge  ⇒ memory-bound（卡带宽，贴左边斜屋顶）
```

这就是把 5.2 的 roofline 和 5.3 的算术强度合起来用：算术强度把算子放到横轴上，拐点是分界线，落在哪边就是哪类瓶颈。判定的意义在于选对优化方向——两类病的药方几乎不重叠。

初学者要记住这是"相对"判定：同一个算子在拐点更靠右的新卡上，可能从 compute-bound 变成 memory-bound（因为拐点右移了，见 5.2）。所以"这个 kernel 是不是 memory-bound"这句话必须带上是哪块硬件。

### LM 中的两个典型对照

训练/prefill vs 推理 decode。prefill 一次吃整段序列，大矩阵乘算术强度高，通常 compute-bound；decode 每步一个 token，权重矩阵乘退化成矩阵-向量乘，每个权重读一次只用一次，算术强度 ~O(1)，memory-bound。所以同一个模型，训练时你可能在拼算力/FP8，推理 decode 时你却在拼显存带宽和批量——这是 serving 优化（连续批处理、投机解码，08S-9）的根本动因：把多条请求的 decode 拼到一起，让同一批权重服务更多 token，从而抬高算术强度、把 decode 从带宽墙下拉出来一点。

大 GEMM vs 逐元素/归一化。大矩阵乘（注意力里的投影、FFN 的两个大矩阵乘）算术强度随规模上升，偏 compute-bound；穿插其间的激活、残差、LayerNorm/RMSNorm 是 O(1) 算术强度，memory-bound。一个 Transformer 前向里，算力屋顶下的大 GEMM 和带宽屋顶下的逐元素算子交替出现，整体性能是两者的混合。

### 从 memory-bound 里逃出来的手段

判定为 memory-bound 后，方向是"减少 HBM 访存 / 提高数据复用 / 提高算术强度"，常见手段：kernel 融合（把连续的逐元素/归一化算子融成一个 kernel，中间结果不落 HBM，08S-6.2）；分块 + 片上复用（FlashAttention 把注意力分块塞进 SRAM 复用，不落 N×N 矩阵，08S-6.3）；提高批量（decode 时把多请求拼批，让权重的一次读服务更多 token）；用更低精度（FP8/INT8）减少每个元素的字节数，等于在分母上省字节、抬高算术强度（08S-9.6 量化）。

判定为 compute-bound 时方向相反：抬高算力屋顶——用张量核/MXU、用更低精度换更高峰值 FLOP、提高并行度把算力打满。要点是别搞反：给 memory-bound 的 decode 换更高算力的卡收益很小，给它加带宽/加批量才对症。

### 易错点

最常见的误区是"看 FLOP 大就以为慢在算力"。一个算子 FLOP 不大却很慢，往往是 memory-bound——时间花在搬数据而非算数据上；反过来 FLOP 很大也可能因为算术强度高而高效。判断慢在哪，要看算术强度和拐点的相对位置，不能只看 FLOP 绝对值。

第二个易错点是把带宽和延迟混为一谈：GPU 上大吞吐负载的 memory-bound 几乎总是指"带宽不够"，而不是"单次访问延迟高"（延迟被大量并发线程掩盖了）。第三个是忘了拐点随硬件/精度移动——同一算子换卡、换 dtype 就可能改变判定，结论必须锚定硬件代际和精度口径。

#### 来源与时效
- 一手锚点：Williams/Waterman/Patterson CACM 2009（memory-bound/compute-bound 与 ridge point 的对应判定）；CS336 Spring 2026 L5「GPUs, TPUs」https://cs336.stanford.edu/ （LM 语境下 decode=memory-bound 的钥匙洞见，与 L10 推理相衔接），核实 2026-08-01；Dao et al. 2022 FlashAttention arXiv:2205.14135（注意力 memory-bound 与 IO 感知优化）。
- 交叉印证：H200 相对 H100 主要升级显存带宽/容量而非算力（NVIDIA H200 datasheet，核实 2026-08-01）这一产品事实，侧面印证 LM 负载普遍偏 memory-bound；decode 为 memory-bound 亦为 vLLM/PagedAttention 系工作（Kwon et al., SOSP 2023, arXiv:2309.06180）的立论前提，与本篇一致。
- 冲突与「待核」：某具体算子在具体硬件上的 compute/memory-bound 归属依赖实测算术强度与该卡拐点，随 dtype、序列长、batch、实现（是否融合/是否用 FlashAttention）而变，本篇只给机制判定框架，不落具体基准数字，需要精确结论时按当时硬件规格 + profiler 实测重定，标「待核」。
