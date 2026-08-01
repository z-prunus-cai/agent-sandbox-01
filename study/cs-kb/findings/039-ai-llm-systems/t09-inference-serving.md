# L6-08·大主题08S-9 推理 / serving

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L6-03·03-6（注意力/Transformer）、L6-08·08S-2（资源核算：参数/FLOP/显存）、08S-5（内存层次/Roofline，memory-bound 判定）｜一手锚点：CS336「Language Modeling from Scratch」Spring 2026 L10（Inference），https://cs336.stanford.edu/ ；PagedAttention/vLLM（Kwon et al., SOSP 2023）https://arxiv.org/abs/2309.06180 ｜成熟度：⚙演进快·锚版本（serving 栈与框架逐季翻新，本篇只写机制"在该锚点下如此"，不排名框架，框架名仅作机制载体举例）

推理（inference）/ serving 讲的是"模型训练好之后，怎么高效地把它跑起来对外服务"。这与训练是两个几乎独立的系统问题：训练一次性吃掉大批数据做梯度更新，追求的是吞吐；而 serving 要同时应付成千上万条实时请求，既要快（低延迟）又要省（高吞吐、低成本），还要处理每条请求长度不一、随时来随时走的动态性。

自回归大模型的生成分成两个性质迥异的阶段——prefill（处理你输入的整段提示）是算力受限的，decode（一个一个吐出新 token）是访存受限的。几乎所有 serving 优化——KV cache、continuous batching、PagedAttention、投机解码、量化——都是在跟"decode 阶段被显存带宽卡住、GPU 算力大量闲置"这件事作斗争。理解了这一点，后面每个机制都能顺下来。

---

## 9.1 KV cache：缓存历史 K/V 避免重算

### 9.1.1 KV cache 是什么

自回归 Transformer 每生成一个新 token，都要让这个新 token 去"看"（注意）前面所有 token。注意力的计算需要每个历史位置的 Key 向量和 Value 向量。如果不做任何缓存，生成第 t 个 token 时就要把前面 t−1 个位置的 K、V 全部重新算一遍；生成第 t+1 个又重算 t 个……总计算量随序列长度呈平方增长，且大量是重复劳动。

KV cache 就是把每个位置一旦算出来的 K、V 向量存进显存，后续步骤直接读缓存、只为当前新 token 计算它自己的一份 K、V 并追加进去。这样每一步的注意力计算量从"对全序列重算"降为"新 token 对已缓存的全序列做一次点积"，把生成过程的计算复杂度从对每步 O(t²) 累积降到每步 O(t)。

缓存的是 K 和 V，不缓存 Q。因为 Query 只属于"当前正在生成的这个 token"，用完即弃；而 K、V 代表"历史内容可被将来查询"，必须留着。缓存 K、V 是标准做法，缓存的名字就叫 KV cache。

### 9.1.2 KV cache 的显存占用公式

KV cache 不是免费的——它是 serving 中除模型权重之外最大的显存消耗项，而且随并发请求数和上下文长度线性膨胀。一条序列占用的 KV cache 字节数为

KV_bytes = 2 × L × n_kv × d_head × s_len × bytes_per_elem

其中 2 是 K 和 V 各一份，L 是层数，n_kv 是每层的 KV 头数，d_head 是每个头的维度，s_len 是序列长度，bytes_per_elem 是数据类型字节数（FP16/BF16 为 2，FP8 为 1）。多条并发请求再乘以 batch 大小。

这个公式说明了两件对 serving 至关重要的事。第一，KV cache 随上下文长度线性增长，长上下文场景下它会迅速吃光显存，成为"这张卡还能同时服务多少条请求"的硬约束。第二，缩小 KV cache 有几条路：减头数（GQA/MQA，见下）、减精度（KV cache 量化，见 9.6）、减冗余存储（PagedAttention，见 9.4）——serving 优化的很大一块都在围绕这个公式的各个因子做文章。（各家实现里公式的写法略有出入，有的把 n_kv×d_head 合写成 hidden 维、有的显式带 batch，本质相同；具体某模型的精确数值应查该模型配置，勿凭记忆填。）

### 9.1.3 GQA / MQA：从源头缩小 KV

标准多头注意力（MHA）里，Query、Key、Value 的头数一样多。Multi-Query Attention（MQA）让所有 Query 头共享同一组 K、V（n_kv = 1）；Grouped-Query Attention（GQA）介于两者之间，把 Query 头分成若干组、每组共享一组 K、V。这样 n_kv 变小，直接按上面公式成比例缩小 KV cache。

注意力里真正数量爆炸的是"要缓存的历史 K、V"，而 Query 每步只有当前一个 token 的。既然瓶颈在 K、V 的存储和读取上（decode 是访存受限的），那就让多个 Query 头共用少量 K、V，用一点点建模能力的让步换取 KV cache 显存和读取带宽的大幅下降。现代大模型广泛采用 GQA 正是出于 serving 效率考量——这是"建模选择"与"系统成本"深度耦合的典型例子（与 08S-3 架构取舍相呼应）。

### 9.1.4 增量更新与全量重算的等价性

KV cache 是一种"用空间换时间"的优化，其正确性前提是：带缓存的增量计算，结果必须与不带缓存、每步从头重算，逐位相等。也就是说 KV cache 是纯粹的性能优化，不改变模型输出的数学定义。

道理在于注意力对历史位置的 K、V 只依赖那个位置本身的输入（在因果掩码下，位置 i 的 K、V 一旦算出就不再改变），所以"提前算好存起来"和"用时现算"必然给出同一组 K、V。初学者可以这样验证心智模型：生成序列时，第 5 步用到的第 2 个位置的 K、V，和第 3 步时那个位置的 K、V 是同一个张量——它不随后续步骤变化，因此缓存它绝对安全。这条不变量也是所有 serving 系统做单元测试时的黄金对拍标准（增量 decode 结果对齐全量 forward）。

#### 来源与时效
- 锚点：CS336 Spring 2026 L10（Inference / KV cache），https://cs336.stanford.edu/ ；核实 2026-08-01。
- KV cache 显存公式与 GQA/MQA 缩减：NVIDIA「Mastering LLM Techniques: Inference Optimization」https://developer.nvidia.com/blog/mastering-llm-techniques-inference-optimization/ ；多篇 KV cache 综述交叉核对公式各因子（K/V 双份、层数、KV 头数、head_dim、精度字节）。核实 2026-08-01。⚙演进快·锚版本。
- 冲突/差异：KV_bytes 公式各来源写法有出入（是否显式带 batch、是否用 hidden 维替代 n_kv×d_head），本质等价；具体模型精确数值需查该模型配置，标「待核」。

## 9.2 prefill vs decode：decode = memory-bound（钥匙洞见）

### 9.2.1 两个阶段是什么

一次生成请求分成两段。prefill（预填充）阶段：把你输入的整段提示（prompt）一次性喂进模型，并行地为提示里每一个 token 算出 K、V 填进 KV cache，最后产出第一个输出 token。decode（解码）阶段：此后每一步只输入上一步刚生成的那一个 token，读取整个 KV cache 做注意力，生成下一个 token，如此逐 token 循环到结束。

关键区别在于"一次处理多少 token"。prefill 一次并行处理成百上千个提示 token；decode 每步只处理 1 个新 token。这个数量差异直接决定了两阶段截然不同的硬件瓶颈。

### 9.2.2 为什么 decode 是 memory-bound（钥匙洞见）

prefill 阶段一次处理大量 token，能把权重矩阵和一大批 token 做成大的矩阵乘法（GEMM），算术强度高、能喂饱 GPU 算力，因此是 compute-bound（算力受限），它决定了首 token 延迟 TTFT。decode 阶段每步只处理 1 个 token，本质是"矩阵 × 向量"（GEMV）：为了生成这一个 token，却要把整个模型的权重、以及整条序列的 KV cache 从显存（HBM）搬进计算单元，算完就搬下一批。搬运的数据量巨大、真正的浮点运算却很少，于是 GPU 大量时间在等数据、算力闲置——这就是 memory-bound（访存/带宽受限）。

用 08S-5 的算术强度（arithmetic intensity = FLOP / 字节）来看：decode 单步的算术强度很低，落在 Roofline 图的带宽墙一侧；prefill 的算术强度高，落在算力墙一侧。这把钥匙解释了 serving 里几乎所有反直觉的现象：为什么加大 batch 几乎不增加 decode 的单步延迟（因为瓶颈是搬权重，多几条请求可以摊薄这次搬运）——continuous batching 之所以有效正源于此；为什么量化（减少要搬的字节）对 decode 提速明显；为什么投机解码（一次验证多个 token，把 GEMV 变回 GEMM）能加速。

decode 慢不是因为算不过来，而是因为搬不过来。

### 9.2.3 两阶段的算术强度对照

可以粗略地把两阶段的"每次前向要搬多少、要算多少"对照起来理解。忽略常数，单次前向的浮点运算量约与"处理的 token 数 × 参数量"成正比，而每步都必须把全部权重从 HBM 读一遍（字节数约与参数量成正比）：

算术强度 ≈ (处理的 token 数 × 参数量 × 常数) / (参数量 × 精度字节) ∝ 处理的 token 数

于是 prefill（token 数大）算术强度高、compute-bound；decode（token 数=1）算术强度低、memory-bound。这个化简是数量级直觉，不是精确模型（真实还要计入 KV cache 读取、注意力项等），具体机器上的拐点需按硬件带宽/算力实测，属「待核」，勿凭记忆填具体 FLOP/字节数字。

### 9.2.4 chunked prefill / 混合批 / prefill-decode 分离

prefill 与 decode 性质相反，把它们放在一起调度就有讲究，主要有两条路线。一条是 chunked prefill（分块预填充）+ 混合批：把很长的 prefill 切成固定 token 预算的小块，与正在进行的 decode 请求拼进同一个批次一起跑，让 compute-bound 的 prefill 片段和 memory-bound 的 decode 片段互补、共同填满 GPU；这能避免"一条超长提示的 prefill 把所有 decode 请求卡住"。另一条是 prefill-decode 分离（disaggregation）：把 prefill 和 decode 放到不同的 GPU 池，各自按自己的瓶颈优化，代价是要在两池之间传输 KV cache 状态。

这两条路线体现了同一个洞见的两种用法——既然两阶段瓶颈互补，要么"混在一起互相填空"，要么"分开各自专精"。哪种更好取决于工作负载（提示长度分布、SLO 要求），无定论。截至锚点，混合批（chunked prefill 共置）在主流框架里已是常见默认，而分离式在大规模、对 TTFT/TPOT 有独立 SLO 的场景中被采用；这些是⚙演进快项，具体默认策略随框架版本变，标「待核」。

#### 来源与时效
- 锚点：CS336 Spring 2026 L10，https://cs336.stanford.edu/ ；核实 2026-08-01。
- decode=memory-bound、prefill=compute-bound：Anyscale「Understand LLM latency and throughput metrics」https://docs.anyscale.com/llm/serving/benchmarking/metrics ；「LLM Inference Unveiled: Survey and Roofline Model Insights」arXiv:2402.16363 —— 两独立源交叉核对"prefill 处理全部提示 token 并行、compute-bound、定 TTFT；decode 每步读全 cache、memory-bandwidth-bound、定 TPOT"。核实 2026-08-01。⚙演进快·锚版本。
- chunked prefill / 混合批 / 分离式：Agrawal et al.「Sarathi / chunked prefill」arXiv:2401.11181 ；vLLM 官方文档（性能/优化章节，https://docs.vllm.ai/ ）交叉核对"混合批 co-locate prefill+decode"。核实 2026-08-01。⚙演进快·锚版本，具体默认策略随框架版本变，标「待核」。

## 9.3 continuous batching：请求级动态拼批提吞吐

### 9.3.1 静态批的问题

最朴素的批处理（static batching）是：攒够一批请求，一起送进模型，等这一整批全部生成完，再收下一批。问题在于同一批里各请求的输出长度差别很大——有的生成 10 个 token 就结束，有的要生成 500 个。短请求早早结束后，它在批里的位置只能空等最长的那条跑完，这段时间 GPU 算力和显存都被浪费。批越大、长度越参差，浪费越严重。

### 9.3.2 iteration-level scheduling（迭代级调度）

continuous batching（连续批处理，也叫 iteration batching）的核心是把调度粒度从"整条请求"细化到"一次迭代（生成一个 token 的一步）"。调度器在每一步之后都重新决定这一步该批哪些请求：某条请求生成完了就立刻移出批、把它腾出的空位让新到达的请求补进来，而不必等整批结束。这一思想由 Orca（OSDI 2022）提出并系统化，称为 iteration-level scheduling。

把 GPU 想成一辆班车。静态批是"坐满一车、开到终点站、所有人一起下车、再发下一班"，先到目的地的乘客只能干等。连续批是"每到一站就让到站的人下车、让在等的人马上上车"，座位几乎永不空置。因为 decode 是 memory-bound、加几条请求几乎不增加单步延迟（9.2.2），这种"随时填空"能把闲置的显存带宽利用率大幅拉高，从而显著提升吞吐。Orca 论文在其设置下相对 FasterTransformer 报告了数量级的吞吐提升（具体倍数依基准与配置而定，属「待核」，不作定论）。

### 9.3.3 selective batching（选择性批处理）

要在"每步动态换人"的批里跑 Transformer 有个技术障碍：不同请求处于不同阶段、序列长度不一，无法简单地拼成一个规整的大张量喂给所有算子。Orca 的解法是 selective batching——只对能批的算子（如各 token 独立的线性层/FFN，把所有 token 摊平成一个大矩阵一起算）做批处理，而对注意力这种"每条请求要在自己的 KV cache 内部做、长度各异"的算子按请求分别处理。

不是所有运算都能"混在一起算"。前馈层对每个 token 的计算是独立的，一千条请求的一千个当前 token 可以摞成一个大矩阵一次算完，效率高；但注意力必须让每个 token 只看自己那条序列的历史，天然不能跨请求混。selective batching 就是"能批的地方使劲批、不能批的地方分开做"。

### 9.3.4 与 PagedAttention 的协同

连续批要频繁地加入/移出请求，每条请求的 KV cache 又在不断增长，这对显存管理提出了苛刻要求——如果 KV cache 必须为每条请求预留一整块连续显存，动态进出就会造成严重碎片。PagedAttention（9.4）的分页 KV 管理正好补上这块：KV cache 按块分配、无需连续，请求可以灵活进出而不产生碎片。因此现代 serving 栈里 continuous batching 与 paged KV 几乎总是搭配出现，前者管"调度谁进批"，后者管"显存怎么放得下"。

#### 来源与时效
- 锚点：CS336 Spring 2026 L10，https://cs336.stanford.edu/ ；核实 2026-08-01。
- iteration-level scheduling / selective batching：Yu et al.「Orca: A Distributed Serving System for Transformer-Based Generative Models」OSDI 2022（一手，机制与"36.9× vs FasterTransformer"数字出处）；Anyscale「Continuous Batching」https://www.anyscale.com/blog/continuous-batching-llm-inference 交叉核对机制。核实 2026-08-01。⚙演进快·锚版本。
- 冲突/边界：吞吐提升倍数高度依赖基准/配置/SLO，各来源数字不可直接横比，标「待核」，不作定论。

## 9.4 PagedAttention：分页管理 KV 显存降碎片（SOSP 2023）

### 9.4.1 显存碎片这个问题

在 PagedAttention 之前，主流做法是给每条请求预留一整块连续显存来放它的 KV cache，且要按"可能的最大长度"预留（因为不知道会生成多长）。这带来两类浪费：内部碎片——预留了最大长度但实际没用满，尾部空着；外部碎片——请求进进出出后，空闲显存被切成许多不连续的小块，明明总量够却拼不出一块连续的大空间给新请求。vLLM 论文观察到当时的引擎实际只用上 20%–40% 的 KV 显存，其余都被碎片吃掉。

### 9.4.2 分页机制：借用操作系统的虚拟内存

PagedAttention 把操作系统虚拟内存 / 分页的思想搬到了 KV cache 管理。它把 KV cache 切成固定大小的物理块（block），每块存放固定数量连续 token 的 K、V（vLLM 默认每块 16 个 token，可用 --block-size 配置）。一条序列的 KV cache 不再要求整段连续，而是分散存在若干不连续的物理块里；每条序列维护一张"块表（block table）"，记录它的逻辑块顺序映射到哪些物理块——完全对应操作系统里"页表把虚拟页映射到物理页帧"。

这就像内存管理里，进程觉得自己有一段连续地址，其实底层被拆成一页页散落在物理内存各处，靠页表串起来。KV cache 也一样：注意力计算时按块表找到各块、逐块参与计算，逻辑上仍是"这条序列完整的历史"，物理上却是碎块拼的。这样按需一块一块地分配，用多少给多少，几乎消灭内部碎片；块大小统一，也不再有外部碎片。

### 9.4.3 块表与按需分配

序列每新增约一个块容量的 token，就向显存池申请一个新物理块并登记进块表；请求结束就把它占的块全部归还池子供他人复用。这种"按需增长、即用即还"的分配，正是让 continuous batching 能高效动态进出的显存基础（9.3.4），也让同一张卡能容纳的并发请求数大幅上升——这直接放大了吞吐。

### 9.4.4 块级共享与 copy-on-write

分页还带来一个额外好处：多条序列若共享同样的前缀（例如相同的系统提示、或并行采样同一 prompt 的多个候选），它们的公共前缀 KV 块可以物理上共享同一份、只在块表里各自指向它，不必各存一份。当某条序列要在共享块上写入分叉内容时，再复制出一份自己的块（copy-on-write，同样借自操作系统）。这进一步省显存，尤其利好"一个 prompt 采样多条输出"和"多请求共享长系统提示"的场景。

这和文件系统/进程 fork 的写时复制是一个套路——能共享就先共享，真要改的时候才复制。前缀共享是 paged KV 顺带解锁的能力，现代框架普遍支持前缀缓存（prefix caching）即源于此。

PagedAttention 是 vLLM（Kwon et al., SOSP 2023）提出的具体机制名；如今几乎所有主流 serving 运行时都实现了 PagedAttention 或与之等价的"块分页 KV"方案，操作性质相同。框架名此处仅作机制载体，不作优劣排名。

#### 来源与时效
- 锚点（一手）：Kwon et al.「Efficient Memory Management for Large Language Model Serving with PagedAttention」SOSP 2023，arXiv:2309.06180，https://arxiv.org/abs/2309.06180 ；核实 2026-08-01。
- 分页/块表/16-token 默认块/CoW/20%–40% 利用率：上述论文为一手；vLLM 官方文档 https://docs.vllm.ai/ 交叉核对块机制与 --block-size 默认值。核实 2026-08-01。⚙演进快·锚版本（默认块大小等参数随版本可变，以运行版本文档为准）。
- 边界：具体显存节省/吞吐提升倍数依模型与负载而定，属「待核」，不作定论；"几乎所有运行时都实现等价方案"为综述性表述，非排名。

## 9.5 投机解码：小模型草稿 + 大模型校验

### 9.5.1 基本原理：draft + verify

投机解码（speculative decoding）针对的正是 decode memory-bound 的痛点。它用一个又小又快的草稿模型（draft model）先自回归地一口气猜出接下来的 K 个 token（草稿便宜，因为模型小），然后把这 K 个候选 token 一次性喂给大模型（目标模型 target model）做一次并行前向来校验。大模型这一次前向就能同时给出对这 K 个位置的判断——这把原本要跑 K 次 GEMV 的串行 decode，变成了草稿的 K 次小前向 + 大模型的 1 次并行前向。

关键在于"验证比生成便宜"：让大模型判断"这 K 个 token 我认不认"，只需一次前向；而让大模型自己逐个生成这 K 个 token，要 K 次前向。既然大模型的一次前向对 1 个 token 和对 K 个 token 的耗时相差不大（memory-bound，瓶颈是搬权重，见 9.2.2），那"一次前向顺带验 K 个"就近乎白赚。

### 9.5.2 拒绝采样保持分布不变

投机解码最重要的性质是：它是无损的——最终输出的 token 分布与"完全用大模型逐个采样"严格一致，不是近似。做到这点靠的是一套修正的拒绝采样（rejection sampling）方案来接受/拒绝草稿 token。

逐个检验草稿 token：设草稿模型给该位置的概率为 q(x)、大模型为 p(x)，草稿采出的 token 以概率

min(1, p(x) / q(x))

被接受；一旦某个 token 被拒绝，就丢弃它及其后的所有草稿 token，并从一个修正后的残差分布重新采一个 token 顶上，然后从下一位置继续。这个接受-拒绝规则经过设计，使得最终每个位置的 token 恰好服从大模型的分布 p。

草稿模型是"抢答的实习生"，大模型是"审核的专家"。专家一次性看完实习生连写的 K 个答案，从头往后逐个核对：认可就留、直到遇到第一个不认可的，就从那里改写并重新接管。因为审核（并行一次前向）远快于专家自己从头写 K 遍，只要实习生猜得还行，整体就更快——而修正采样保证了最终结果与专家亲自写没有分布差异。

### 9.5.3 接受率决定加速幅度

投机解码能省多少，取决于草稿的接受率（token acceptance rate）——大模型认可草稿 token 的比例。接受率高，说明大部分草稿被采纳，一次校验多前进好几个 token，加速明显；接受率低（草稿与大模型能力差距大、或文本多样难猜），大量草稿被拒、白跑草稿，加速有限甚至可能因草稿开销而变慢。所以草稿模型要"既快又准"，与目标模型分布尽量接近，这本身是个权衡。

投机解码在 batch 很大、GPU 本已算力饱和的场景收益会缩水（那时已不那么 memory-bound，多算的验证反而占用算力）；它对小 batch / 低延迟场景最有利。具体加速比高度依赖草稿模型、任务与 batch，属「待核」，不作定论。

### 9.5.4 变体：自草稿（Medusa / EAGLE 类）

原始投机解码要单独准备一个小草稿模型。一类变体去掉了独立草稿模型，改成让目标模型自己"多头预测"——例如 Medusa 在大模型顶部加若干轻量预测头、一次并行猜出未来多个 token；EAGLE 系列在特征层面做自回归草稿并用"草稿树 + 树注意力"同时验证多条候选路径。这类"自草稿 / 多候选树验证"方向是活跃研究，方案逐季演进，具体谁更优不作定论，此处仅登记机制形态。

可以把它们都归为"怎么更便宜地产出高接受率的草稿"这一大问题的不同解法——有的用外部小模型，有的复用大模型自身的中间表示加几个预测头，有的一次猜一棵树（多条候选路径）再一并验证以提高命中。

#### 来源与时效
- 锚点：CS336 Spring 2026 L10，https://cs336.stanford.edu/ ；核实 2026-08-01。
- 原始投机解码 + 修正拒绝采样（无损）：Leviathan et al.「Fast Inference from Transformers via Speculative Decoding」（2023）与 Chen et al.「Accelerating Large Language Model Decoding with Speculative Sampling」（2023）两一手独立提出，交叉核对"验证一次前向、拒绝采样保持目标分布"。核实 2026-08-01。⚙演进快·锚版本。
- 变体：Medusa（Cai et al., 2024）、EAGLE-2「Faster Inference of Language Models with Dynamic Draft Trees」arXiv:2406.16858 —— 交叉核对"自草稿 / 树注意力 / 草稿树"机制。核实 2026-08-01。⚙演进快·活跃研究，具体加速比与优劣标「待核」，不排名。

## 9.6 量化：INT8/FP8/GPTQ/AWQ 精度-显存权衡

### 9.6.1 量化基础：对称/非对称与往返误差

量化（quantization）是把模型里原本的高精度浮点数（如 FP16/BF16）用更少的比特表示（如 INT8、INT4、FP8），从而减小显存占用、减少要从 HBM 搬运的字节数。因为 decode 是 memory-bound（9.2.2），"搬得少"直接等于"跑得快"，同时也让更大的模型/更多并发能塞进同一张卡。

对称量化把浮点值 x 映射到整数 q：

s = max(|x|) / (2^(b-1) − 1),  q = round(x / s),  x̂ = q × s

非对称量化再引入一个零点偏移 z，以更好地覆盖不对称的数值范围：

s = (x_max − x_min) / (2^b − 1),  z = round(−x_min / s),  q = round(x / s) + z,  x̂ = (q − z) × s

这里 b 是比特数，s 是缩放因子（scale），z 是零点（zero-point）。量化再反量化得到 x̂，与原值 x 的差就是量化误差（往返误差）。目标是选好 s、z（以及分组粒度：整张量共享一个 scale，还是每通道/每组各一个）让误差尽量小。

量化就像把连续的音量旋钮换成有限档位的档位开关——档位越少（比特越少）越省，但越可能"卡不到你想要的那个音量"，产生误差。非对称多了个零点，相当于允许档位区间整体平移去贴合数据实际范围（比如激活值大多为正）。分组越细（每通道一个 scale）越准但元数据开销越大。

### 9.6.2 weight-only PTQ：GPTQ

GPTQ 是一种训练后量化（post-training quantization, PTQ）——不重新训练，直接把已训好的权重量化，典型量到 4 比特（权重量化、激活仍用高精度，即 weight-only）。它逐列（按输出通道）量化权重矩阵，每量化一列就用二阶信息（基于 Hessian 的误差补偿）去调整尚未量化的列，把这一列引入的误差尽量"摊派消化"掉，从而在低比特下保住精度。

朴素地把每个权重四舍五入到最近档位，误差会累积。GPTQ 的巧思是"边量化边补救"——每定死一列，就让剩下还没定的列微微调整来抵消刚产生的误差，像下棋时每走一步都顺手修正前面的偏差。它属于 weight-only，只压权重、不动激活。

### 9.6.3 AWQ

AWQ（Activation-aware Weight Quantization，激活感知的权重量化）也是 weight-only PTQ，核心观察是：并非所有权重同等重要，只要保护约 1% 的"显著（salient）权重"就能大幅降低量化误差；而判断哪些权重显著，要看激活的分布（哪些输入通道的激活幅值大），而不是只看权重本身。据此 AWQ 对显著通道施加放缩保护，再做低比特量化。

直觉是"用得多的路要修得好"。有些权重通道对应的输入激活特别大，它们的量化误差会被放大、影响输出最多——这批就是显著权重。AWQ 靠激活统计把它们找出来重点保护，其余照常压缩。AWQ 与 GPTQ 都是广泛使用的 weight-only 方案，出发点不同（AWQ 看激活定显著性、GPTQ 用 Hessian 逐列补偿）；有综述报告 AWQ 在一些设置下精度损失更小，但这依模型与配置而定，不作定论。

### 9.6.4 weight-activation：SmoothQuant 与 W8A8

前面两者只量化权重。要进一步加速，可以把激活也一起量化（weight-activation quantization，如 W8A8：权重 8 比特 + 激活 8 比特）。难点在激活里有少数幅值极大的离群值（outlier），直接量化会让误差爆炸。SmoothQuant 的做法是用一个数学上等价的变换，把量化难度从激活"平移"到权重上——给激活除以一个逐通道的平滑因子、同时给权重乘上它，乘积不变，但让激活变得平滑好量化、权重仍然可控，从而实现 W8A8。

激活里的离群值就像一份成绩单里混进了一个 999 分，逼着你把刻度拉得很粗、其他分数都量不准。SmoothQuant 相当于事先把激活的"尖峰"匀一部分给权重（两边一乘一除、乘积不变），让激活这边不再有极端值、好量化；权重那边本来就比激活规整，多担一点也扛得住。

### 9.6.5 FP8 vs INT8

同样 8 比特，可以用整数格式 INT8，也可以用浮点格式 FP8。区别在数值分布：FP8 有指数位，动态范围更大（能同时表示很小和很大的数）；INT8 是均匀刻度，在其范围内分辨率一致。因此对含离群值、动态范围大的量（权重、激活、KV cache），FP8 往往更"抗离群"、精度影响更小；INT8 在数值集中的场合分辨率利用更充分。FP8 需要硬件支持（较新的 GPU 代际才有原生 FP8 单元），这是⚙锚硬件代际的项。

INT8 像一把等分刻度的尺子，每一格一样宽；FP8 像对数刻度的尺子，靠近 0 的地方刻度密、远处刻度疏，因此能覆盖更宽的范围而不至于对小值完全失真。哪个更好取决于要量化的东西的数值分布和硬件是否支持，不作定论。（例如有厂商文档建议在支持 FP8 的较新 GPU 上，KV cache 量化优先选 FP8 而非 INT8，因其精度影响通常更小——此为具体实现的建议，随硬件/版本变，标「待核」。）

### 9.6.6 KV cache 量化

除了权重和激活，KV cache 本身也可以量化（如 FP8 或 INT8 存 K、V）。由 9.1.2 的公式，KV cache 随上下文和并发线性膨胀，长上下文时它甚至超过权重成为显存大头；把它从 2 字节压到 1 字节，等于把能容纳的上下文/并发直接翻倍，同时减少 decode 每步要搬的字节。代价是 K、V 精度下降可能影响生成质量，需权衡；具体精度损失依方法与模型而定，标「待核」，不编造数字。

量化整体是一组"精度换显存/速度"的权衡，且方案（GPTQ/AWQ/SmoothQuant/各种 FP8·INT4·混合精度）逐季演进（⚙锚方案逐季演进），本篇只讲机制原理，不排名、不给具体精度损失数值。

#### 来源与时效
- 锚点：CS336 Spring 2026 L10，https://cs336.stanford.edu/ ；核实 2026-08-01。
- GPTQ：Frantar et al.「GPTQ: Accurate Post-Training Quantization for Generative Pre-trained Transformers」arXiv:2210.17323（一手，逐列量化+Hessian 误差补偿）。核实 2026-08-01。
- AWQ：Lin et al.「AWQ: Activation-aware Weight Quantization for LLM Compression and Acceleration」arXiv:2306.00978（一手，MLSys 2024；1% 显著权重、看激活分布）。核实 2026-08-01。
- SmoothQuant / W8A8：Xiao et al.「SmoothQuant」（一手，等价变换迁移量化难度）；「LLM Inference Unveiled」arXiv:2402.16363 综述交叉核对 weight-only vs weight-activation 分类。核实 2026-08-01。
- FP8 vs INT8 / KV cache 量化：NVIDIA TensorRT-LLM 量化文档 https://nvidia.github.io/TensorRT-LLM/blogs/quantization-in-TRT-LLM.html （FP8 动态范围更大、Hopper/Ada 上 KV cache 推荐 FP8）；ZeroQuant-FP 等交叉核对。核实 2026-08-01。⚙演进快·锚方案逐季演进/锚硬件代际；具体精度损失/加速比标「待核」，不作定论、不排名。

## 9.7 服务 SLO 指标：TTFT/TPOT/吞吐的权衡

### 9.7.1 TTFT（首 token 延迟）

TTFT（Time To First Token）是从请求到达、到用户看到第一个输出 token 的时间。它主要由排队等待 + prefill 时间构成，反映"系统多快开始响应"。对交互式应用（聊天、流式输出），TTFT 直接决定"点了发送后要盯着空屏幕等多久"，是体感的第一印象。

因为 prefill 是 compute-bound、且要处理整段提示，TTFT 随提示长度增长，也受当前系统排队负载影响。chunked prefill、prefill-decode 分离等（9.2.4）很大程度是在管理 TTFT。

### 9.7.2 TPOT / ITL（逐 token 延迟）

TPOT（Time Per Output Token）是稳态解码阶段平均每个输出 token 的时间；ITL（Inter-Token Latency）是流式输出时相邻 token 之间的实际间隔。两者都刻画"开始吐字之后，字往外冒得多快多顺"。因为 decode 是 memory-bound，TPOT 主要由 decode 单步延迟决定，量化、投机解码、GQA（减少每步要搬的字节）都是在压 TPOT。

TTFT 是"多久开口"，TPOT/ITL 是"开口后说话多流畅"。一个字一个字蹦得很卡（ITL 大）即使 TTFT 很小，体验也差；反之亦然。二者要分开看、分别定指标。

### 9.7.3 吞吐 vs 延迟的根本权衡

吞吐（throughput，单位时间处理的 token 数或请求数）与单请求延迟往往对立。加大 batch（更多请求同时跑）能更充分利用 GPU、提高总吞吐（降低每 token 平均成本），但会让单条请求的 TTFT 和 ITL 变长——因为它要和更多请求分享每一步、排队更久。这正是 serving 调优的中心张力：省钱（高吞吐）还是快（低延迟），鱼与熊掌。

还是班车比喻。班车坐得越满，单位成本越低（吞吐高），但每位乘客上下车、等人凑齐要花更多时间（延迟高）；空车直发最快（延迟低）但极不划算（吞吐低）。continuous batching 之所以珍贵，是因为借着 decode memory-bound 的特性，它能在几乎不牺牲单步延迟的前提下把 batch 填满，部分缓解（但不能消除）这个权衡。

### 9.7.4 goodput：把质量与 SLO 合起来看

单看吞吐会骗人：一个系统可以靠超大 batch 刷出很高的 token/s，却让大多数请求违反了延迟要求。于是引入 goodput（有效吞吐）——只统计那些满足 SLO（例如 TTFT < t₁ 且 TPOT < t₂）的请求所贡献的吞吐。它把"跑得快"和"守住延迟承诺"绑在一起衡量，比裸吞吐更贴近生产目标。

goodput 就像"按时送达的外卖单量"——你一小时接一万单没用，超时的不算数，只有准时送到的才计入有效产能。具体的 SLO 阈值（TTFT/TPOT 该定多少）高度依业务与工作负载，无通用定论；各家基准报告的 TTFT/TPOT/吞吐数字不可跨设置直接横比，标「待核」，不编造具体数值。

#### 来源与时效
- 锚点：CS336 Spring 2026 L10，https://cs336.stanford.edu/ ；核实 2026-08-01。
- TTFT/TPOT/ITL 定义与"TTFT=排队+prefill、TPOT=稳态 decode"：Anyscale「Understand LLM latency and throughput metrics」https://docs.anyscale.com/llm/serving/benchmarking/metrics ；ClickHouse「LLM inference latency: TTFT, tokens per second」https://clickhouse.com/resources/engineering/llm-inference-latency —— 两独立源交叉核对。核实 2026-08-01。⚙演进快·锚版本。
- goodput（满足 SLO 的吞吐）与吞吐-延迟权衡：「On Evaluating Performance of LLM Inference Serving Systems」arXiv:2507.09019 ；Anyscale 文档交叉核对"大 batch 提吞吐但抬 TTFT/ITL"。核实 2026-08-01。
- 边界：所有具体 TTFT/TPOT/吞吐阈值与基准数字依业务/负载/硬件而定，标「待核」，不作定论、不跨设置横比。
