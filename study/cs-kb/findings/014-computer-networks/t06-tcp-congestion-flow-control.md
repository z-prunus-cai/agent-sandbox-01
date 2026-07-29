# L4-02·大主题N6 TCP 拥塞控制与流控

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-29 ｜ 先修：N5 传输层与可靠数据传输（TCP 段结构、字节流序号与累积确认、超时重传与 RTT 估计）｜ 一手锚点：Kurose & Ross《Computer Networking: A Top-Down Approach》8th ed.(2021) Ch3；RFC 5681（TCP Congestion Control，2009，承重）；RFC 9438（CUBIC，2023，承重）；RFC 9293（Transmission Control Protocol，2022，流控/SWS/Nagle）；RFC 3168（ECN，2001）＋RFC 9768（AccECN，2025）＋RFC 9330/9331（L4S，2023）；BBR = IETF draft-ietf-ccwg-bbr-04（2025-10-20，Experimental，**非 RFC**）；Stanford CS144 ｜ 成熟度：Reno/CUBIC/流控/ECN 均 GA/稳定；BBR ⚙演进快·锚 draft-ietf-ccwg-bbr-04（BBRv3）·随时变

> 粒度判定：**1 份，不拆**。本大主题 6 个小主题（N6.1–N6.6）都围绕同一根主线——「发送方到底一次能往网络里塞多少字节」。这个「多少」由两把闸门共同决定：一把是接收方开的（流控，rwnd），一把是网络自己"逼"出来的（拥塞控制，cwnd）。N6.1 讲第一把闸，N6.2–N6.6 讲第二把闸从原理（AIMD）到经典实现（Reno）、到 Linux 主流（CUBIC）、到显式信号（ECN）、到新范式（BBR）的演进。六节篇幅适中、共享同一套术语（cwnd/ssthresh/RTT/BDP），拆开反而割裂主线。按 report-format v3 §一「默认 1 大主题 = 1 报告」，不产 `-a/-b`。

本报告从头到尾只回答一个问题，**任一时刻，TCP 发送方允许有多少"已发出但还没被确认"的字节在路上飞（即 in-flight / FlightSize）。** 这个上限是两个窗口取小：

允许发送的字节数上限 = min(rwnd, cwnd)

rwnd（receive window，接收窗口）由接收方在每个报文段里回告，保护的是**接收方的缓冲区别被塞爆**——这是流量控制（flow control）。cwnd（congestion window，拥塞窗口）由发送方自己维护、网络里没有任何字段直接携带它，保护的是**中间路由器的队列别被塞爆导致丢包**——这是拥塞控制（congestion control）。初学者最容易混的就是这两者：流控是"你（接收端）来不来得及收"，拥塞控制是"网络中间堵不堵"。两者独立计算、最后取最小值，谁更紧谁说了算。

---

## N6.1 滑动窗口流量控制

### N6.1.1 接收窗口 rwnd 与"取小"规则

流量控制（flow control）解决的是**速度匹配**问题，防止一个快的发送方淹没一个慢的接收方。接收方维护一个接收缓冲区，把收到但应用还没读走的数据暂存其中；它在**每个**发回的报文段的 TCP 头部「Window」字段里，回告当前还剩多少空闲空间，这个数就是 rwnd。发送方据此把"在途未确认字节数"约束在 rwnd 之内。RFC 9293 把最终发送窗口 SND.WND 表述为受接收方回告窗口与拥塞窗口共同约束：

SND.WND = min(rwnd, cwnd)

要抓住的直觉是"rwnd 是接收方主动开的一扇门，门开多大发送方就最多塞多少"。接收方每读走一批数据、缓冲区腾出空间，就在下一个 ACK 里回告一个更大的 rwnd（开门）；如果应用读得慢、缓冲区快满，rwnd 就变小（关门）。因为 ACK 是双向捎带的，这个"门的大小"几乎实时地反馈给发送方。注意 rwnd 是**接收方**告诉**发送方**的，方向别搞反；一条全双工连接的两个方向各有独立的 rwnd。

一个常见误区是把 rwnd 和 cwnd 当成一回事。它们的信息来源根本不同：rwnd 是接收方**明写**在报文里的一个数，发送方直接读取；cwnd 是发送方**根据丢包/RTT 自己推算**出来的，网络和对端都看不见它。所以流控是"显式协作"，拥塞控制是"隐式猜测"。

### N6.1.2 窗口缩放（Window Scale）：16 位字段不够用

TCP 头部的 Window 字段只有 16 位，最大表示 65535 字节。在高带宽·长时延（大 BDP）链路上，65535 字节远不足以填满管道，会白白限制吞吐。RFC 7323（TCP Extensions for High Performance，承接早期 RFC 1323）定义了**窗口缩放选项（Window Scale option）**：连接建立时（仅在 SYN / SYN-ACK 里）双方各协商一个移位因子 shift.cnt（0–14），此后回告的窗口值都要左移这么多位来解释。

实际生效窗口 = Window 字段值 × 2^(shift.cnt)

初学者需要知道的关键点是：窗口缩放**只在三次握手的 SYN 报文里协商一次**，之后不能改；且必须双方都支持才生效。这是为什么现代 Linux 默认打开窗口缩放——否则千兆网上的单条 TCP 连接会被 64KB 窗口卡死在很低的吞吐上。BDP（带宽时延积）= 带宽 × RTT，就是"填满管道所需的在途字节数"，窗口必须 ≥ BDP 才能跑满带宽。

### N6.1.3 零窗口与零窗口探测（persist 定时器）

当接收方缓冲区被填满、应用又迟迟不读，接收方会回告 **rwnd = 0**（零窗口），等于告诉发送方"先别发了"。问题来了：发送方停发后，接收方后来腾出了空间、想回告一个非零窗口——但这个"窗口更新"是靠一个纯 ACK 承载的，**ACK 本身不可靠、不会被重传**。万一这个更新 ACK 丢了，双方就会永久互相等待（死锁）：发送方等窗口开，接收方以为已经通知过了。

TCP 用**零窗口探测（zero-window probe）** 打破这个死锁。发送方看到零窗口后启动 **persist 定时器**，定时器到期就发一个 1 字节的探测报文段（Zero Window Probe），逼接收方回一个 ACK，这个 ACK 里就带着最新的窗口值。只要窗口还是 0，发送方就按（通常指数退避的）间隔一直探测下去，直到窗口重新打开。RFC 9293 明确要求接收方即使在零窗口下也必须处理到来报文段的 RST 与 URG 字段。

理解要点是"探测的责任在发送方，不在接收方"。为什么？因为丢的是"窗口打开"的好消息，吃亏的是想发数据的发送方，所以由它主动来问最合理。这也是可靠协议设计的一个通用套路：不可靠的通知 + 主动轮询兜底。

### N6.1.4 糊涂窗口综合征（SWS）与 Nagle 算法

**糊涂窗口综合征（Silly Window Syndrome, SWS）** 指连接陷入"每次只传很少字节"的低效状态，头部开销（IP 20B + TCP 20B = 40B）相对载荷大到离谱。它可能由两端引发。接收方一侧：应用每次只读走 1 字节，接收方就每次只回告 1 字节的窗口，发送方就每次只发 1 字节。发送方一侧：应用每次只写 1 字节（如 telnet 逐字符），就每次发一个 41 字节的小包。

RFC 9293 给出两端各自的抑制办法。接收方侧 SWS 避免：不要一有一点空间就回告，而是**攒到能增加 min(缓冲区的一部分, 一个 MSS) 时才回告更大的窗口**，否则继续回告当前（可能为 0 的）窗口。发送方侧靠 **Nagle 算法（Nagle's algorithm）**：

若已有未确认数据在途（SND.NXT > SND.UNA），则新的小块数据先缓冲，直到收到对已发数据的 ACK，或攒够一个整段（full-sized segment）才发出。

Nagle 的直觉是"一次连接里最多只允许有一个未被确认的小包在飞"，把大量小写攒成大包，极大降低小包比例。代价是可能引入最多一个 RTT 的延迟，对"小请求—立即响应"的交互式/低延迟应用（如某些 RPC）不利，所以 RFC 9293 强制要求实现必须提供**关闭 Nagle 的开关**，即套接字选项 `TCP_NODELAY`。初学者要记住：Nagle 默认开、提升吞吐、可能加延迟；对延迟敏感就用 `TCP_NODELAY` 关掉——这是流控相关最常见的一道调优题。

#### 来源与时效
- RFC 9293（Transmission Control Protocol，2022-08，核实 2026-07-29）§3.8.6「Managing the Window」「SWS Avoidance」「Nagle Algorithm」、§3.8.6.1 零窗口探测与 persist：SND.WND = min(rwnd, cwnd)、零窗口探测责任在发送方、接收方/发送方两侧 SWS 避免、Nagle 与 `TCP_NODELAY`（MUST-17）。
- Kurose-Ross 8E Ch3 §3.5.5「Flow Control」（核实 2026-07-29）：rwnd 语义、接收缓冲区速度匹配、零窗口下发送方持续发 1 字节探测以获知窗口重开——与 RFC 9293 一致。
- RFC 7323（TCP Extensions for High Performance，2014，核实 2026-07-29）：窗口缩放选项、16 位窗口在大 BDP 下不足、shift.cnt 仅 SYN 协商——补 Kurose 未展开处。
- 冲突项：无实质冲突。SWS 接收方阈值「fraction of buffer」的具体取值实现相关，RFC 只给原则不给死数。

---

## N6.2 拥塞控制原理与 AIMD

### N6.2.1 什么是拥塞、拥塞的代价与拥塞信号

**拥塞（congestion）** 指过多的源以过快的速率向网络注入数据，超过了中间路由器的转发能力，导致路由器队列堆积。它的代价有两重：一是排队时延随负载升高而急剧上升（接近容量时趋于无穷）；二是队列溢出时**丢包**，而丢掉的包往往是上游已经耗费带宽转发过的，形成"做无用功"的浪费，重传又进一步加剧拥塞，可能一路滑向**拥塞崩溃（congestion collapse）**——吞吐坍塌到接近零。这正是 1980 年代末互联网真实发生过、催生 Jacobson 拥塞控制的历史背景。

问题的核心难点在于：网络内部**不会显式告诉端主机"我堵了"**（这是 TCP/IP 的端到端设计选择）。所以经典 TCP 只能靠**推断**。它用的拥塞信号主要是**丢包**（loss-based）：一次超时重传（RTO）或收到 **3 个重复 ACK**，都被当作"网络堵了"的证据。后来 ECN（N6.5）提供了不丢包的显式信号，BBR（N6.6）则改用带宽/RTT 建模而非丢包。

初学者要建立的心智是"没有仪表盘的驾驶"。发送方看不到网络内部的拥塞程度，只能像蒙眼开车一样，靠"有没有撞墙（丢包）"来反推该踩多深油门。这个"看不见"是理解 TCP 一切拥塞控制机制为什么长这样的根源。

### N6.2.2 AIMD：加性增、乘性减

TCP 拥塞控制的骨架是 **AIMD（Additive Increase, Multiplicative Decrease，加性增乘性减）**，作用在拥塞窗口 cwnd 上。规则朴素得惊人。一个 RTT 顺利收到确认、没丢包时，窗口做加性增（每 RTT 只多加一个 MSS）：

cwnd ← cwnd + 1 个 MSS

一旦检测到丢包，窗口做乘性减（经典 Reno 取 β = 0.5，即直接减半）：

cwnd ← cwnd × β

加性增是"温柔试探"：每个 RTT 只多发一个 MSS，缓慢逼近可用带宽的上限。乘性减是"猛踩刹车"：一旦丢包立刻把窗口砍掉一大半，快速让出拥塞的网络。这一慢一快是刻意的不对称——探测容量要小心（怕引发拥塞），退让要果断（怕加剧崩溃）。

为什么偏偏是"加性增 + 乘性减"，而不是别的组合（如 AIAD 加性增加性减、MIMD）？下一节的收敛性分析会说明：只有 AIMD 这个组合能让多条竞争的流**同时收敛到公平且高效**。这是 AIMD 成为 TCP 基石的数学理由，不是拍脑袋的选择。

### N6.2.3 AIMD 的收敛性与公平性（收敛示意）

考虑两条共享同一瓶颈的 TCP 流，把它们的窗口画成二维平面上的一个点（横轴 = 流1 窗口，纵轴 = 流2 窗口）。平面上有两条参考线：**效率线**（两流之和 = 瓶颈容量，越靠右上越充分利用带宽）和**公平线**（两流相等的 45° 对角线）。AIMD 的动态过程会把任意起点**收敛到两线交点**（既充分利用、又完全公平）。加性增让两流各 +1，代表点沿 45°方向平移（斜向右上）——不改变两者之差，靠近效率线；乘性减让两流各 ×β，代表点沿指向原点的射线收缩——按比例缩小、**主动缩小两者的绝对差距**，靠近公平线。整体轨迹是：

斜上冲顶（加性增） → 过载丢包 → 向原点回缩（乘性减） → 再斜上冲顶……

代表点于是在效率线附近来回锯齿，同时被一步步推向公平线交点。

关键洞察在乘性减那一步：因为是**按比例**缩（乘法），窗口大的那条流被砍掉的绝对量更多，于是每次减都在缩小两流的差距——这就是 AIMD 收敛到公平的数学根源。反过来若用"减性减"（各减固定常数），差距不变，永远无法收敛到公平。初学者可以记："加法保持差、乘法缩小差；正是那一记乘性刹车把大家拉平。"（本机 Python 模拟见 N6.2 来源，多流 cwnd 确实收敛到公平线附近的锯齿。）

这里要点明一个前提，这套公平性结论假设各流 RTT 相近。实际中 **RTT 越小的流每个 RTT 加得越勤，会抢到更多带宽**（RTT 不公平），这是经典 AIMD 的已知局限，也是 CUBIC 等改进要处理的问题之一。

#### 来源与时效
- RFC 5681（TCP Congestion Control，2009-09，核实 2026-07-29）§1「Introduction」拥塞崩溃背景、§3 拥塞窗口与 AIMD 精神：拥塞避免阶段每 RTT 增约一个 SMSS、丢包乘性减。
- Kurose-Ross 8E Ch3 §3.7「TCP Congestion Control」（核实 2026-07-29）：AIMD 锯齿、丢包=拥塞信号（超时/三重复 ACK）、拥塞崩溃、AIMD 收敛到公平线的图示与"RTT 不公平"局限——与 RFC 一致。
- 本机实证（可选，已取，2026-07-29）：Python 数值模拟 3 条同 RTT AIMD 流（加 1 / 乘 0.5），窗口序列在约数十个 RTT 内从悬殊初值收敛到彼此相差 <10% 的公平锯齿，佐证收敛性；脚本为一次性数值迭代，不入库。
- 冲突项：无。β 取值属实现选择（Reno 0.5、CUBIC 0.7），非分歧。

---

## N6.3 TCP Reno（慢启动 / 拥塞避免 / 快恢复）

### N6.3.1 三个状态变量：cwnd、ssthresh、FlightSize

Reno（RFC 5681 描述的标准算法）的状态由三个量刻画。**cwnd**（拥塞窗口）是发送方自我限制的在途字节上限。**ssthresh**（slow start threshold，慢启动阈值）是一条分界线：cwnd 在它之下走"慢启动"（指数增），越过它走"拥塞避免"（线性增）。**FlightSize** 是当前实际在途（已发未确认）的字节数。RFC 5681 用 **SMSS**（Sender Maximum Segment Size，发送方最大段大小）作为增减的单位。

初始窗口 IW 不是 1 个段，RFC 5681 按 SMSS 分档：

若 SMSS > 2190 字节：IW = 2 × SMSS，且不超过 2 段；

若 1095 < SMSS ≤ 2190 字节：IW = 3 × SMSS，且不超过 3 段；

若 SMSS ≤ 1095 字节：IW = 4 × SMSS，且不超过 4 段。

要记住的是"ssthresh 是慢启动和拥塞避免的分水岭"。cwnd < ssthresh 时激进（指数），cwnd > ssthresh 时保守（线性）；等于时两者皆可。ssthresh 初值可以设得很大（如 rwnd 或无穷），让连接一开始尽情慢启动，直到第一次丢包才被"校准"到一个接近真实容量的值。（注：更晚的 RFC 6928 把常见初始窗口提升到约 10 段=IW10，属演进，Linux 已采用；RFC 5681 本身仍是上面的 2–4 段规则。）

### N6.3.2 慢启动（Slow Start）：名字骗人的指数增长

**慢启动（slow start）** 在 cwnd < ssthresh 时运行。它的规则是每收到一个确认新数据的 ACK，就把 cwnd 增加至多一个 SMSS：

cwnd += min(N, SMSS)   （N 为该 ACK 新确认的字节数）

效果是**每个 RTT，cwnd 翻倍**（指数增长）：第 1 个 RTT 发 1 段、收到 1 个 ACK 后能发 2 段；第 2 个 RTT 发 2 段、收 2 个 ACK 后能发 4 段……1、2、4、8 地涨。

"慢启动"这名字极具误导性——它其实是 TCP **最快**的增长阶段。"慢"是相对于历史上"连接一开始就直接猛发一整个窗口"而言的：慢启动改成从 1 个（或几个）段小心起步，故称"慢"。为什么用 min(N, SMSS)（Appropriate Byte Counting）而不是简单"每 ACK 加一个 SMSS"？为了防"ACK 分割攻击"——恶意接收方把一个段拆成很多小 ACK 来骗发送方超速增窗，用"按实际确认字节数"封住这个漏洞。慢启动在丢包或 cwnd 达到 ssthresh 时结束。

### N6.3.3 拥塞避免（Congestion Avoidance）：线性爬升

cwnd > ssthresh 后进入**拥塞避免（congestion avoidance）**，改为每个 RTT 只增加约一个 SMSS 的线性增长。RFC 5681 给出的每 ACK 增量公式是：

cwnd += SMSS × SMSS / cwnd   （每个确认新数据的 ACK）

一个 RTT 里大约有 cwnd/SMSS 个 ACK 回来，每个加 SMSS²/cwnd，累加正好约等于一个 SMSS——这就实现了 AIMD 的"加性增"（每 RTT +1 段）。

理解要点是"这条公式是把'每 RTT 加一个段'摊到每个 ACK 上的等价写法"。为什么不直接每 RTT 加一次？因为 TCP 是事件驱动（收到 ACK 才动作），没有"一个 RTT 到了"的显式时钟，只能把增量分摊到每个 ACK。cwnd 越大，每个 ACK 的增量越小，正好保证无论窗口多大，每个 RTT 的总增量恒为约一个段——这就是"加性"的精确含义。

### N6.3.4 快重传与快恢复（Reno 的招牌）：cwnd 状态机

丢包有两种检测方式，Reno 对它们的反应截然不同。**超时（RTO）** 是重罚：ssthresh 降到 FlightSize 的一半、cwnd 直接跌回 1 个段（LW，loss window）重新慢启动。**3 个重复 ACK** 是轻罚：触发**快重传（fast retransmit）**——不等超时立即重传那个疑似丢失的段——随后进入 **快恢复（fast recovery）**，cwnd 只减半而不跌回 1。区别对待的逻辑是：还能收到（重复）ACK，说明网络还在送包、只是漏了一个，没堵死，不必回到起点。

Reno 完整的 cwnd 状态机与转换如下：

```
                    连接建立, cwnd=IW, ssthresh=大值
                              │
                              ▼
          ┌───────────────────────────────────────┐
          │  慢启动 SLOW START (cwnd < ssthresh)    │
          │  每 ACK: cwnd += min(N,SMSS) 〔每RTT×2〕│
          └───────────────────────────────────────┘
             │ cwnd ≥ ssthresh           │ 3 个重复 ACK
             ▼                            │  → 快重传
   ┌────────────────────────────┐        │  ssthresh=max(FlightSize/2,2·SMSS)
   │ 拥塞避免 CONG. AVOIDANCE     │        │  cwnd=ssthresh+3·SMSS
   │ 每 ACK: cwnd += SMSS²/cwnd  │        ▼
   │        〔每 RTT +1 段〕      │  ┌──────────────────────────────┐
   └────────────────────────────┘  │ 快恢复 FAST RECOVERY          │
       │      │                     │ 每多收 1 个重复 ACK: cwnd+=SMSS│
       │      │ 3 个重复 ACK        │ （窗口膨胀，撑住在途数据）      │
       │      └────────────────────▶│ 收到新数据 ACK:               │
       │                            │   cwnd=ssthresh（放气/deflate）│
       │                            │   → 回拥塞避免                 │
       │  超时 RTO                   └──────────────────────────────┘
       ▼
   ssthresh=max(FlightSize/2,2·SMSS)
   cwnd = 1·SMSS (LW)  →  重回慢启动
       （超时可从任意状态触发）
```

三个关键等式（RFC 5681，均用 SMSS 为单位）：

ssthresh = max(FlightSize / 2, 2 × SMSS)   （丢包时，无论超时还是三重复 ACK）

cwnd = ssthresh + 3 × SMSS   （进入快恢复的瞬间，"+3"补偿已离开网络、正被那 3 个重复 ACK 确认的 3 个段）

cwnd = ssthresh   （快恢复结束、收到确认新数据的 ACK 时，放气回到拥塞避免）

快恢复期间每再收到一个重复 ACK，cwnd 还要临时 **+SMSS**（窗口膨胀，inflation），因为每个重复 ACK 都意味着"又有一个段离开了网络、被接收方收下了"，可以再放一个新段进去，撑住数据流不干涸。收到确认新数据的 ACK 后再把这些临时膨胀"放气（deflate）"掉、回到 ssthresh。初学者记住整体图景即可：**超时=回到 1 重新慢启动（重罚）；三重复 ACK=减半后走快恢复（轻罚）**。这一"轻重有别"正是 Reno 相对更早的 Tahoe（一律回 1）的核心改进，锯齿因此不再每次都探底、平均吞吐更高。

#### 来源与时效
- RFC 5681（TCP Congestion Control，2009-09，承重，核实 2026-07-29）§2 术语（SMSS/cwnd/ssthresh/FlightSize/IW/LW）、§3.1 慢启动与拥塞避免（IW 的 2/3/4×SMSS 分档、cwnd+=min(N,SMSS)、cwnd+=SMSS²/cwnd）、§3.2 快重传/快恢复（ssthresh=max(FlightSize/2,2·SMSS)、cwnd=ssthresh+3·SMSS、每重复 ACK +SMSS、退出时 cwnd=ssthresh）、§3.1 超时后 cwnd≤LW=1 段。所有等式逐字取自 RFC，未凭记忆。
- Kurose-Ross 8E Ch3 §3.7（核实 2026-07-29）：慢启动指数、拥塞避免线性、超时 vs 三重复 ACK 的重罚/轻罚区分、Tahoe→Reno 演进、cwnd 锯齿图——与 RFC 5681 一致。
- 本机实证（可选，已取，2026-07-29）：Python 按 RFC 5681 规则模拟单流 cwnd 演化（慢启动指数段 → 越过 ssthresh 转线性 → 注入丢包触发减半），输出呈典型锯齿，佐证状态机；一次性脚本不入库。
- 演进标注：初始窗口 IW10（RFC 6928，2013）已被 Linux 采用，比 RFC 5681 的 2–4 段更大，属 delta；本节承重规则以 RFC 5681 为准。
- 冲突项：无。RFC 5681 与 Kurose 在数值与流程上一致。

---

## N6.4 CUBIC（Linux 默认）

### N6.4.1 为什么需要 CUBIC：Reno 在大 BDP 下涨得太慢

CUBIC 是为**高带宽·长时延（大 BDP）** 网络设计的拥塞控制，自 Linux 2.6.19（2006）起成为**主线内核默认算法**，并由 RFC 9438（2023，取代早期 RFC 8312）标准化。动机是 Reno 的线性增长在大管道上太慢：假设一条 10 Gbps、100 ms RTT 的链路，Reno 每 RTT 才加一个段，从半窗爬回满窗要花很长时间（数量级上可达上千秒），一次丢包就要极久才能恢复，带宽被长期浪费。

CUBIC 的核心思路是：让窗口的增长**只取决于"距离上次丢包过了多久"（实时间 t），而与 RTT 无关**。这样 RTT 长的连接不再吃亏（缓解了 Reno 的 RTT 不公平），且窗口曲线能设计成"丢包后先快速逼近上次的窗口高度、在高度附近放慢、越过后再加速探测新容量"，兼顾恢复速度与稳定性。

初学者先建立一个对比即可：Reno 的窗口是"每 RTT +1 的直线锯齿"，CUBIC 的窗口是"以上次丢包点为拐点的三次曲线"。曲线形状让它在接近上次出事的窗口值附近走得很稳，远离时走得很猛——这正是"三次函数"被选中的原因。

### N6.4.2 三次窗口增长函数（公式）

CUBIC 用一个关于时间 t 的三次函数决定目标窗口。RFC 9438 的核心公式：

W_cubic(t) = C × (t − K)³ + W_max

其中 t 是自进入本轮拥塞避免以来经过的**实际秒数**；W_max 是上一次发生拥塞（窗口被降）时的窗口大小；C 是决定激进程度的常数；K 是"从当前窗口涨回 W_max 所需的时间"，由下式确定（RFC 9438 形式）：

K = ∛( (W_max − cwnd_epoch) / C )

（cwnd_epoch 为进入本轮拥塞避免时的窗口值。注：更早的 RFC 8312 与 2008 年原始论文写作 K = ∛(W_max × (1 − β_cubic) / C)，两式在"刚减窗、cwnd_epoch = β·W_max"的常见情形下一致；此处为 ⚙演进点，承重锚 RFC 9438。）

两个默认常数（RFC 9438 明确给出，未凭记忆）：

C = 0.4

β_cubic = 0.7

理解这条曲线的最好方式是看它的拐点在 t = K 处、函数值等于 W_max。t < K（**凹区 / concave**，cwnd < W_max）时，曲线从下方快速上升、逼近 W_max 时逐渐放缓——即"丢包后先猛追回上次的高度，快到时减速、稳住"。t > K（**凸区 / convex**，cwnd > W_max）时，曲线又开始加速上升——即"既然过了上次的高度还没丢包，说明可能有更多带宽，大胆往上探"。这个"先急后缓再急"的 S 形，就是 CUBIC 兼顾"快恢复"与"稳定探测"的关键。

### N6.4.3 丢包时的乘性减与 Reno 友好区

发生丢包时，CUBIC 记住当前窗口作为新的 W_max，并按 β_cubic 乘性减（β_cubic = 0.7）：

W_max ← cwnd

ssthresh ← cwnd × β_cubic

注意 CUBIC 减到 0.7（只砍 30%），比 Reno 的 0.5（砍一半）**温和**，因此丢包后损失的吞吐更少、恢复更快。为保证在小 BDP、短 RTT 环境下 CUBIC **不会比 Reno 更弱**（否则部署 CUBIC 反而吃亏），RFC 9438 定义了 **Reno 友好区（Reno-friendly region，旧称 TCP-friendly region）**：并行维护一个"如果是 Reno 会涨到多少"的估计窗口 W_est，当三次函数算出的 W_cubic 比 W_est 还小时，就改用 W_est（即退化到 Reno 的行为）。W_est 递推：

W_est = W_est + α_cubic × (segments_acked / cwnd)，其中 α_cubic = 3 × (1 − β_cubic) / (1 + β_cubic)

代入 β_cubic = 0.7 得 α_cubic ≈ 0.53。初学者只需抓住三点：**减窗更温和（×0.7）、增长按实时间三次曲线（与 RTT 解耦）、并且托底不弱于 Reno（友好区）**。这三条合起来解释了为什么 CUBIC 能成为高速网络时代的默认选择。

### N6.4.4 本机默认算法实证

本机 `sysctl` 实测（Linux 6.18.5，2026-07-29）：

```
net.ipv4.tcp_congestion_control = bbr
net.ipv4.tcp_available_congestion_control = reno bbr cubic
```

这里要做**规范与实现分账**：**Linux 主线内核自 2.6.19 起的默认算法是 CUBIC**（这是"教科书事实"），但**本机镜像被运维显式改成了 bbr**（`tcp_congestion_control = bbr`），并非 CUBIC。可用列表 `reno bbr cubic` 表明三种算法模块都已编入/加载。这说明"默认算法"是一个可被系统管理员用 `sysctl`/`net.ipv4.tcp_congestion_control` 覆盖的运行时配置，不是写死的——不能因为"Linux 默认 CUBIC"就断定任意一台 Linux 机器当前跑的就是 CUBIC。本例正好反证了这一点，也顺带坐实了 N6.6 要用的事实：本内核确实带了 bbr 模块。

#### 来源与时效
- RFC 9438（CUBIC for Fast and Long-Distance Networks，2023-08，承重，核实 2026-07-29）§4「Core Algorithm」：W_cubic(t)=C(t−K)³+W_max、K=∛((W_max−cwnd_epoch)/C)、§4.5 C SHOULD=0.4、§4.6 β_cubic=0.7、§4.3 凹/凸区、§4.2 Reno 友好区与 W_est、α_cubic=3(1−β)/(1+β)。常数逐字取自 RFC。
- Kurose-Ross 8E Ch3 §3.7.1「Classic TCP Congestion Control」及 CUBIC 小节（核实 2026-07-29）：CUBIC 为 Linux/主流默认、三次曲线以 W_max 为拐点、凹/凸探测直觉、减窗 0.7 比 Reno 0.5 温和——与 RFC 9438 一致。
- 本机实证（可选，已取，2026-07-29）：`sysctl net.ipv4.tcp_congestion_control`→`bbr`；`tcp_available_congestion_control`→`reno bbr cubic`。用于规范/实现分账（主线默认 CUBIC vs 本机改 bbr）。
- 演进/冲突标注：K 的定义 RFC 9438（用 cwnd_epoch）vs RFC 8312/原论文（用 W_max·(1−β)），两边都记，承重取 9438。RFC 8312 已被 9438 取代（obsoleted），引用一律锚 9438。

---

## N6.5 ECN 显式拥塞通知

### N6.5.1 ECN 的核心思想：标记代替丢包

**ECN（Explicit Congestion Notification，显式拥塞通知，RFC 3168，2001）** 让网络在拥塞时**给包打个标记**、而不是直接丢掉它，从而把"拥塞信号"从"丢包"这种破坏性事件里解耦出来。当路由器队列开始堆积（还没溢出）时，支持 ECN 的路由器不丢包，而是在 IP 头部把该包标记为「经历过拥塞」（CE, Congestion Experienced）；接收方看到 CE，就在回给发送方的 ACK 里回显这一信号；发送方收到后**像遇到丢包一样降窗**，但**没有真的丢任何数据**、无需重传。

要抓的直觉是"ECN 是一种不流血的拥塞信号"。传统 TCP 必须靠丢包才知道网络堵了，代价是丢的包要重传、还平白增加时延；ECN 让路由器提前、无损地打招呼，端主机就能在丢包发生**之前**主动退让。这对时延敏感和高吞吐场景都有利。前提是**路径上路由器 + 两端主机都要支持并协商开启**，任一环节不支持就退回传统丢包模式。

### N6.5.2 两处比特：IP 头 2 位 + TCP 头 2 个标志

ECN 用到 IP 和 TCP 两层各自的字段。IP 头部里，原 ToS/流量类别字段的低 2 位被定义为 **ECN 字段**，共 4 个码点：

00 = Not-ECT（不支持 ECN 的传输）
10 = ECT(0)、01 = ECT(1)（ECN-Capable Transport，发送方声明"本包支持 ECN，可被标记"）
11 = CE（Congestion Experienced，路由器把 ECT 包改成此值表示"这里堵了"）

TCP 头部用两个此前保留的标志位承载反馈：**ECE（ECN-Echo）** 由接收方置位，回显"我收到了被标 CE 的包"；**CWR（Congestion Window Reduced）** 由发送方置位，告诉接收方"我已经因你的 ECE 降过窗了，可以停止回显"。连接建立时双方在 SYN / SYN-ACK 里用 ECE+CWR 组合协商是否启用 ECN（ECN-setup）。

初学者记住这条链路即可：**路由器在 IP 层把 ECT 改成 CE（一次标记）→ 接收方在 TCP 层用 ECE 回显（告知发送方）→ 发送方降窗并用 CWR 确认（闭环）**。IP 层负责"打标记"，TCP 层负责"把标记传回源头并闭环"，两层配合。

### N6.5.3 本机 ECN 配置与演进（AccECN / L4S）

本机 `sysctl net.ipv4.tcp_ecn` 实测（2026-07-29）为 **2**。Linux 该参数的语义是：0 = 关闭；1 = 主动请求也被动接受 ECN；**2 = 不主动发起、但对端请求时接受**（当前本机即此保守默认）。

ECN 正处于活跃演进，涉及处硬标 ⚙演进快。经典 RFC 3168 有个瓶颈：**每个 RTT 只能反馈一个拥塞信号**（ECE 是个开关状态），信息量太粗。**AccECN（Accurate ECN，RFC 9768，2025 发布）** 更新 RFC 3168，用 TCP 头部（含一个原 ECN-nonce 保留位）承载**每 RTT 多个标记的计数**，反馈精度大幅提升。这正是 **L4S（Low Latency, Low Loss, Scalable throughput，RFC 9330 架构 / RFC 9331 ECN 协议，2023）** 所依赖的前提：L4S 用 ECT(1) 作为"我要走低时延队列"的标识，配合可扩展拥塞控制在毫秒级排队下运行，AccECN 的精细反馈是其必要条件。初学者只需知道：**经典 ECN（3168）= 丢包信号的无损替代；AccECN（9768）+ L4S（9330/9331）= 让 ECN 反馈更精细、支撑下一代低时延网络的演进方向**，这些较新、部署仍在铺开中。

#### 来源与时效
- RFC 3168（The Addition of Explicit Congestion Notification to IP，2001-09，核实 2026-07-29）：IP 头 2 位 ECN 字段四码点（Not-ECT/ECT(0)/ECT(1)/CE）、TCP 头 ECE 与 CWR 标志、SYN 阶段 ECN-setup 协商、CE 标记代替丢包。码点逐字取自 RFC。
- Kurose-Ross 8E Ch3 §3.7.2「Network-Assisted Explicit Congestion Notification」（核实 2026-07-29）：ECN 让路由器标记而非丢包、接收方 ECE 回显、发送方降窗并置 CWR、需路径与两端共同支持——与 RFC 3168 一致。
- RFC 9768（More Accurate ECN Feedback in TCP，2025，核实 2026-07-29 经 IETF datatracker）：经典 ECN 每 RTT 仅一个信号的局限、AccECN 提供多标记反馈、复用 ECN-nonce 保留位、为 L4S 前提。⚙演进快。
- RFC 9330/9331（L4S 架构 / L4S 的 ECN 协议，2023，核实 2026-07-29）：ECT(1) 作 L4S 标识、依赖 AccECN。⚙演进快·锚 2023–2025 版·随时变。
- 本机实证（可选，已取，2026-07-29）：`sysctl net.ipv4.tcp_ecn` = 2（不主动发起、被动接受）。
- 冲突项：无。3168 为基线，9768/9330/9331 为其更新/扩展，分账清楚。

---

## N6.6 BBR 基于模型的拥塞控制（⚠draft）

### N6.6.1 成熟度硬标：BBR 至今仍是 IETF draft，不是 RFC

先把状态钉死（⚙演进快·锚版本·随时变）：截至核实日 **2026-07-29，BBR 仍是 IETF Internet-Draft，尚未成为 RFC**。当前承重版本为 **draft-ietf-ccwg-bbr-04**（2025-10-20 更新，描述 **BBRv3**），意向状态为 **Experimental（实验性）**。BBR 于 2024-10 被 IETF 拥塞控制工作组（CCWG）**采纳为工作组文档**、目标是发一个 Experimental RFC，但在基线核实日**尚未定稿发布**。历史沿革：早期 draft-cardwell-iccrg-bbr-congestion-control 描述 BBRv1/v2，后转入 CCWG 演进到 BBRv3。因此本节所有 BBR 结论都以"draft/实验"定性，不作为定型标准陈述。

初学者要建立的判断是"BBR 已被 Google 大规模部署（YouTube、其 CDN、gRPC/QUIC）且进入 Linux 内核多年，但'工业界广泛使用'不等于'IETF 标准化完成'"。规范上它仍是草案、还在改（v1→v2→v3 差异不小），所以引用任何 BBR 的具体数值/行为都必须锚到具体 draft 版本。

### N6.6.2 核心思想：用带宽×RTT 建模，而非等丢包

经典 TCP（Reno/CUBIC）是**丢包驱动（loss-based）**：不断加窗直到把某个路由器队列填满、丢包，才退让。这在"深缓冲"路由器上会造成 **bufferbloat（缓冲膨胀）**——队列长期被塞满，时延奇高却还没丢包；在"浅缓冲 + 随机丢包"链路上又会把非拥塞丢包误判为拥塞而过度退让。

BBR（Bottleneck Bandwidth and Round-trip propagation time）换了范式：它**持续测量两个物理量**——瓶颈带宽 BtlBw（近期交付速率的最大值）和最小往返传播时延 RTprop（近期 RTT 的最小值，代表空队列时的纯传播时延）——并据此把发送速率/在途数据量对准这条链路的"最优工作点"。理论最优在于经典的管道模型：

BDP = BtlBw × RTprop

BBR 力图让在途数据量维持在约一个 BDP（足以跑满带宽），既不制造持久排队（低时延）、又充分利用带宽（高吞吐）。它主要用**速率（pacing）** 而非单纯窗口来控制发送节奏，并周期性地"探带宽"（短暂提速看交付率能否更高）和"探 RTT"（短暂减量看 RTT 能否更低）来更新这两个估计值。

两者的关键区别可以一句话概括，**丢包驱动的 CUBIC 问"我能不能再多发一点直到出事"，BBR 问"这条管道到底有多粗多长，我照着灌刚好填满"**。所以 BBR 对随机丢包不敏感（不把非拥塞丢包当拥塞），在长肥管道和有丢包的无线/跨洋链路上常有更好表现；代价是与丢包型算法共存时的公平性、以及不同版本的行为差异仍是活跃研究/争议点（BBRv1 曾被指在某些场景抢占 CUBIC 带宽，v2/v3 持续修正）。

### N6.6.3 本机可用性实证

本机 `sysctl net.ipv4.tcp_available_congestion_control`（2026-07-29）返回 `reno bbr cubic`，且 `tcp_congestion_control = bbr`，说明**本内核（Linux 6.18.5）确实编入了 bbr 拥塞控制模块，并已被设为当前默认**。这坐实了"BBR 虽在 IETF 仍是 draft，但在 Linux 实现层面早已 GA 可用、甚至被本机选作默认"这一"规范 vs 实现"的落差——标准进度与工程部署并不同步。

初学者从这里应带走两点：其一，能在 `sysctl` 里选用某算法，只代表"内核实现了它"，与"它是不是 IETF 定型标准"是两码事；其二，本机默认恰是 BBR 而非教科书说的 CUBIC，再次提醒"默认值以实测为准，不凭记忆"。

#### 来源与时效
- draft-ietf-ccwg-bbr-04（BBR Congestion Control，2025-10-20，Experimental，**非 RFC**，核实 2026-07-29 经 IETF datatracker）：BBRv3、BtlBw×RTprop 建模、pacing、ProbeBW/ProbeRTT、CCWG 于 2024-10 采纳、目标 Experimental RFC。⚙演进快·锚 draft-04·随时变。
- Kurose-Ross 8E Ch3 §3.7.2「Delay-based Congestion Control」及 BBR 讨论（核实 2026-07-29）：BBR 属基于时延/带宽（非丢包）的拥塞控制、Google 部署、以带宽×RTT 定工作点、对比丢包型算法——与 draft 一致（教材定性、不给最新 v3 细节）。
- Cardwell 等《BBR: Congestion-Based Congestion Control》ACM Queue 2016（原始文献，二手承重补充，核实 2026-07-29）：BtlBw/RTprop 双测量、bufferbloat 动机、对随机丢包不敏感。
- 本机实证（可选，已取，2026-07-29）：`tcp_available_congestion_control` = `reno bbr cubic`、`tcp_congestion_control` = `bbr`，坐实本内核含 bbr 模块且设为默认。
- 冲突/演进项：BBR 版本间行为差异显著（v1 公平性争议 → v2/v3 修正），且规范未定稿；一切数值须锚具体 draft 版本，本节不引任何"定型标准值"。
