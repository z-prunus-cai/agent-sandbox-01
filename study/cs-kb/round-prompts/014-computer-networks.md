# Round3c · L4-02 计算机网络 · 报告 prompt（P0）

```text
[P0] L4-02·大主题N1 分层与封装参照系 — 报告prompt

任务：为「L4-02·大主题N1 分层与封装参照系」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N1」小节，据其 5 条小主题逐条覆盖。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N1 概念性、体量适中，默认 1 份即可；若判拆分按 014-computer-networks-a/-b 命名。

【v3 格式硬性】标题层级严格 #（报告标题=大主题）→ ##（小主题=章节）→ ###（内容项，一项一节）；### 下不写「核心概念/辅助说明」等标签，直接正文分段：先一段核心说明（是什么/定义/结论，正确），再充分辅助说明（面向初学者的直觉/为什么/最小例子/易错点/关联，写到初学者懂为止，可多段）。任何报文格式/分层示意/封装嵌套图独占行或独占代码块，不内联埋进句子。全程教辅口吻；广度优先、每项讲到初学者懂但不做专家级纵深。抬头带基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」与核实日期。

【小主题清单（## 章节，逐条覆盖，内容项来自 round3b N1）】
- N1.1 OSI 七层参考模型：七层各自职责与抽象边界，作为对齐术语的参照系。
- N1.2 TCP/IP 四层模型与栈映射：现实互联网四层协议栈及与 OSI 的对应/塌缩关系。
- N1.3 封装与解封装：PDU 逐层加头/剥头，段→包→帧的封装嵌套（封装示意独占块）。
- N1.4 端到端原则与分层哲学：智能置边缘、网络保持简单（Saltzer-Reed-Clark 1984，待核是否作承重锚，Kurose ch1 已含论述可降级为教材锚）。
- N1.5 分层的代价与跨层考量：分层带来的复制/开销与跨层优化张力。

【多来源比对（强制）】每个内容项须 ≥2 个独立来源交叉核对，优先一手：Kurose-Ross 8E ch1、RFC 1122（主机分层要求）。来源打架时两边都记、点明分歧、不和稀泥。具体层名/字段/年份不凭记忆，取自比对通过的来源或标「待核」。本机 tcpdump 实证可选（如 `tcpdump -X` 看同一包以太/IP/TCP 逐层头部佐证 N1.3），不强制。每个 ## 章节末集中列来源与时效（锚点+版本+核实日期；前沿/待核项显式标；冲突项两边定位），不逐项脚注。

【权威锚点清单（取自 round3a）】
- Kurose & Ross《Computer Networking: A Top-Down Approach》8th ed(2021) 官方 TOC：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- Stanford CS144：https://cs144.github.io/
- IETF RFC 1122（主机需求，分层承重）：https://www.rfc-editor.org/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（若判拆分用 -a/-b 后缀）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N2 链路层与局域网 — 报告prompt

任务：为「L4-02·大主题N2 链路层与局域网」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N2」小节，据其 6 条小主题逐条覆盖（N2.6 VLAN 为查全补位/可选，可裁剪但需交代）。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N2 默认 1 份；拆分按 014-computer-networks-a/-b 命名。

【v3 格式硬性】层级 #→##→###；### 下不写标签，先核心说明后充分辅助说明（面向初学者，必需且充分）；以太帧格式/CRC 多项式除法/校验和示意独占行或独占块。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N2）】
- N2.1 链路层服务与成帧：组帧、链路接入、（可选）可靠交付等职责。
- N2.2 差错检测：奇偶/校验和/CRC：检错码原理、CRC 多项式除法、Internet 校验和（公式/示意独占块）。
- N2.3 多路访问协议：信道划分、随机接入（CSMA/CD、CSMA/CA）、轮流协议（无线避让详见 N10）。
- N2.4 MAC 编址与以太网帧：MAC 地址语义、以太帧格式与以太网演进（帧格式独占块）。
- N2.5 交换机自学习与 ARP：交换机 MAC 学习/转发、ARP 从 IP 解析 MAC。
- N2.6 VLAN 与数据中心链路层（可选）：VLAN 隔离与数据中心多路径拓扑。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：Kurose-Ross 8E ch6、CS144（datagrams/链路）、以太网/CRC 相关规范（如 IEEE 802.3 概念、Internet 校验和 RFC 1071，待核引用层级）。冲突两边都记。字段长度/CRC 多项式系数不凭记忆，取自来源或标「待核」。本机实证可选（`tcpdump -e` 看源/目的 MAC 与帧类型；`ip neigh`/`tcpdump arp` 看 ARP 请求-应答；Python 实现 CRC-32/16-bit 校验和对拍 `zlib.crc32`），不强制。章节末集中列来源与时效。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E ch6：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- Stanford CS144（含增量搭 TCP/IP 栈 lab）：https://cs144.github.io/
- IETF RFC（如 1071 校验和，待核）：https://www.rfc-editor.org/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N3 网络层·数据平面（IP 编址与转发） — 报告prompt

任务：为「L4-02·大主题N3 网络层·数据平面（IP 编址与转发）」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N3」小节，据其 7 条小主题逐条覆盖。注意分账：本大主题=数据面转发，路由（控制面）在 N4，勿越界。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N3 小主题 7 条、含 IPv4/IPv6/分片/NAT 多机制，若过长可判拆分（如编址+转发 / 分片+NAT+ICMP+IPv6），按 014-computer-networks-a/-b 命名并给理由。

【v3 格式硬性】层级 #→##→###；### 下不写标签，核心说明+充分辅助说明（面向初学者，必需）；IPv4/IPv6 数据报头部格式、子网掩码位运算、最长前缀匹配示意独占行或独占块。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N3）】
- N3.1 IPv4 数据报格式与编址：头部字段语义与点分十进制地址（头部格式独占块）。
- N3.2 子网划分与 CIDR：子网掩码、CIDR 无类前缀与地址聚合（位运算示意独占块）。
- N3.3 转发与最长前缀匹配：路由器数据面转发表查找与 LPM 决策。
- N3.4 分片、MTU 与 PMTUD：IP 分片重组、MTU 限制与路径 MTU 发现。
- N3.5 NAT 地址转换：私有地址复用与 NAT 端口映射/穿透。
- N3.6 IPv6 编址与过渡：128 位编址、地址类型与双栈/隧道过渡。
- N3.7 ICMP 差错与诊断：ICMP 报文类型、ping/traceroute 探测原理。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：RFC 791(IPv4)、RFC 8200(IPv6)、RFC 1122(主机需求)、Kurose-Ross 8E ch4。冲突两边都记。头部字段位宽/默认值不凭记忆，取自来源或标「待核」。本机实证可选（`tcpdump -v` 看 TTL/ID/协议号；Python `ipaddress` 算网络/广播/可用主机数与建前缀表模拟 LPM；`ping -M do -s <n>` 二分探路径 MTU；`traceroute`+`tcpdump icmp`；`ip -6 addr`/`tcpdump ip6`；`iptables -t nat -L` 待核容器权限），不强制。章节末集中列来源与时效。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E ch4：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- IETF RFC 791 / 8200 / 1122：https://www.rfc-editor.org/
- Stanford CS144：https://cs144.github.io/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N4 网络层·控制平面（路由） — 报告prompt

任务：为「L4-02·大主题N4 网络层·控制平面（路由）」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N4」小节，据其 6 条小主题逐条覆盖（N4.6 广播/多播为查全补位/可选）。防重红线：路由（求可达性/最短路）≠ 分布式共识（求单值一致，属 L5-01），不得混谈。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N4 默认 1 份；拆分用 -a/-b。

【v3 格式硬性】层级 #→##→###；### 下不写标签，核心说明+充分辅助说明（面向初学者，必需）；最短路/距离向量迭代/无穷计数示意与状态更新独占行或独占块。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N4）】
- N4.1 路由问题抽象与最短路：图模型 + 最小代价路径作为路由目标。
- N4.2 链路状态路由（OSPF）：拓扑泛洪 + 各节点本地 Dijkstra。
- N4.3 距离向量路由（RIP）与无穷计数：Bellman-Ford 迭代、无穷计数与毒性逆转/水平分裂。
- N4.4 域间路由 BGP：自治系统、策略路由、AS-PATH 与 eBGP/iBGP。
- N4.5 SDN 与控制/数据面分离：集中式控制器、OpenFlow 下发流表。
- N4.6 广播与多播路由（可选）：组播分发树与 IGMP 组管理。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：Kurose-Ross 8E ch5、相关 RFC（OSPF/BGP/RIP 概念定位，待核具体号）。冲突两边都记。算法收敛细节/字段不凭记忆，取自来源或标「待核」。本机实证可选（Python 建带权图跑 Dijkstra/Bellman-Ford、模拟 LS 泛洪与 DV 迭代收敛；`ip route` 看本机路由表），不强制。章节末集中列来源与时效。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E ch5：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- IETF RFC（OSPF/BGP/RIP，待核号）：https://www.rfc-editor.org/
- Stanford CS144：https://cs144.github.io/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N5 传输层与可靠数据传输 — 报告prompt

任务：为「L4-02·大主题N5 传输层与可靠数据传输」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N5」小节，据其 6 条小主题逐条覆盖。本大主题为本课重心之一。分账：socket 系统调用接口属 OS(L4-01)，本课讲协议语义。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N5 含 UDP/rdt 原理/TCP 段/连接状态机/RTT，机制密集，若过长可判拆分（如 复用+UDP+rdt 原理 / TCP 段+连接管理+RTT），按 -a/-b 命名并给理由。

【v3 格式硬性】层级 #→##→###；### 下不写标签，核心说明+充分辅助说明（面向初学者，必需）；TCP 头部格式、三次握手/四次挥手时序、TCP 状态机、RTT/RTO 递推公式均独占行或独占块，不内联。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N5）】
- N5.1 复用/分用与端口：按端口多路复用、socket 四元组标识连接。
- N5.2 UDP 无连接传输：UDP 数据报格式、校验和与无连接语义（报文格式独占块）。
- N5.3 可靠传输原理（rdt/GBN/SR）：序号/ACK/重传/定时器，停等→流水线（回退 N/选择重传）。
- N5.4 TCP 段结构与字节流序号：TCP 头部、字节流序号与累积确认（头部格式独占块）。
- N5.5 TCP 连接管理与状态机：三次握手/四次挥手与 TCP 状态转换（时序与状态机独占块）。
- N5.6 超时重传与 RTT 估计：RTT 采样、EWMA 平滑与 RTO（Jacobson/Karn）计算（公式独占行）。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：RFC 9293(TCP，承重)、RFC 768(UDP)、Kurose-Ross 8E ch3、CS144（lab 增量搭 TCP）。冲突两边都记。头部字段/状态名/RTO 系数（如 α/β、G）不凭记忆，取自 RFC 9293 或标「待核」。本机实证可选（Python socket bind + `ss -tunap` 看四元组；UDP 收发+`tcpdump udp`；Python 模拟 GBN/SR 丢包行为；`tcpdump -S` 看绝对 seq/ack；`tcpdump 'tcp[tcpflags]'` 抓 SYN/FIN、`ss -to` 看 TIME_WAIT；Python 递推 SRTT/RTTVAR 画 RTO），不强制。章节末集中列来源与时效。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E ch3：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- IETF RFC 9293(TCP) / 768(UDP)：https://www.rfc-editor.org/
- Stanford CS144：https://cs144.github.io/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N6 TCP 拥塞控制与流控 — 报告prompt

任务：为「L4-02·大主题N6 TCP 拥塞控制与流控」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N6」小节，据其 6 条小主题逐条覆盖。本大主题为本课重心之一，纯传输层机制。硬标：BBR 仍为 IETF draft（非 RFC），承重用 Reno(5681)/CUBIC(9438)。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N6 默认 1 份；拆分用 -a/-b。

【v3 格式硬性】层级 #→##→###；### 下不写标签，核心说明+充分辅助说明（面向初学者，必需）；AIMD 收敛/cwnd 锯齿状态机、慢启动→拥塞避免→快恢复的状态转换、CUBIC 三次函数公式均独占行或独占块。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N6）】
- N6.1 滑动窗口流量控制：接收窗口 rwnd、零窗口探测与糊涂窗口综合征。
- N6.2 拥塞控制原理与 AIMD：拥塞信号、加性增/乘性减及其收敛与公平性（收敛示意独占块）。
- N6.3 TCP Reno（慢启动/拥塞避免/快恢复）：Reno 的 cwnd 状态机与快重传触发（状态机独占块）。
- N6.4 CUBIC（Linux 默认）：三次函数窗口增长、高带宽时延积友好（公式独占行）。
- N6.5 ECN 显式拥塞通知：路由器标记代替丢包作为拥塞信号。
- N6.6 BBR 基于模型的拥塞控制（⚠draft）：估计瓶颈带宽×RTT、非丢包驱动；硬标仍为 IETF draft，坐实基线成熟度。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：RFC 5681(Reno 拥塞控制)、RFC 9438(CUBIC)、Kurose-Ross 8E ch3；BBR 用 IETF draft 并硬标状态。冲突两边都记。cwnd 阈值/CUBIC 常数/默认算法不凭记忆，取自 RFC 或本机 `sysctl` 实证或标「待核」。本机实证可选（`sysctl net.ipv4.tcp_congestion_control` 看默认与可用算法、确认是否含 bbr 模块以坐实成熟度；Python 模拟多流 AIMD 收敛到公平线、cwnd 锯齿；`tcpdump` 看 window 字段与 IP ECN 位/TCP ECE/CWR），不强制。章节末集中列来源与时效，前沿项（BBR）标「⚙演进快·锚版本·随时变」。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E ch3：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- IETF RFC 5681(Reno) / 9438(CUBIC)；⚠BBR=IETF draft（非 RFC）：https://www.rfc-editor.org/
- Stanford CS144：https://cs144.github.io/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N7 DNS 名字解析 — 报告prompt

任务：为「L4-02·大主题N7 DNS 名字解析」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N7」小节，据其 7 条小主题逐条覆盖（N7.7 DNSSEC/DoH/DoT 为可选）。防重：分布式命名 ≠ 分布式共识；不深入一致性模型（→L5-01）。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N7 默认 1 份；拆分用 -a/-b。

【v3 格式硬性】层级 #→##→###；### 下不写标签，核心说明+充分辅助说明（面向初学者，必需）；DNS 报文格式、名字空间树、递归/迭代解析时序均独占行或独占块。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N7）】
- N7.1 层级名字空间与区域授权：域名树、区（zone）划分与授权委派。
- N7.2 服务器层级（根/TLD/权威）：根、顶级域、权威服务器的分工。
- N7.3 递归 vs 迭代解析：解析器代客递归 vs 服务器逐级迭代（解析时序独占块）。
- N7.4 缓存与 TTL：递归/本地缓存与 TTL 生存期控制。
- N7.5 资源记录类型：A/AAAA/CNAME/MX/NS/TXT/SOA 等 RR 语义。
- N7.6 报文格式与传输（UDP/53、TCP 回退、EDNS0）：DNS 报文结构、大响应回退 TCP 与 EDNS 扩展（报文格式独占块）。
- N7.7 DNS 安全与加密（DNSSEC/DoH/DoT）（可选）：应答完整性签名与解析通道加密。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：RFC 1034/1035(DNS 概念与实现)、Kurose-Ross 8E ch2；EDNS0/DNSSEC 用对应 RFC（待核号）。冲突两边都记。报文字段/RR 类型码/默认端口不凭记忆，取自 RFC 或标「待核」。本机实证可选（`dig NS .`/`dig NS com` 看层级；`dig +trace` vs `dig +norecurse` 对比迭代/递归；连续 `dig` 同名看 TTL 递减；`dig A/MX/+short NS` 取记录；`tcpdump -n port 53` 看查询/应答；`dig +dnssec` 看 RRSIG 待核解析器支持），不强制。章节末集中列来源与时效。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E ch2：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- IETF RFC 1034 / 1035：https://www.rfc-editor.org/
- Stanford CS144：https://cs144.github.io/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N8 HTTP 与 Web（含 QUIC/HTTP3） — 报告prompt

任务：为「L4-02·大主题N8 HTTP 与 Web（含 QUIC/HTTP3）」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N8」小节，据其 6 条小主题逐条覆盖。分账要点：HTTP 语义(9110) 与传输版本(1.1/2/3) 分账；Web 缓存 ≠ 分布式缓存一致性（→L6-05）。本大主题是 API 设计(N11) 的地基。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N8 默认 1 份；拆分用 -a/-b。

【v3 格式硬性】层级 #→##→###；### 下不写标签，核心说明+充分辅助说明（面向初学者，必需）；HTTP 请求/响应报文格式、HTTP/2 分帧、条件请求往返、QUIC 握手示意均独占行或独占块。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N8）】
- N8.1 HTTP 语义（RFC 9110）：与传输版本无关的方法/状态码/头部语义层。
- N8.2 HTTP/1.1 消息传输（RFC 9112）：报文语法、持久连接与分块传输（报文格式独占块）。
- N8.3 HTTP/2 分帧与多路复用（RFC 9113）：二进制帧、单连接多流与 HPACK 头压缩。
- N8.4 Web 缓存与条件请求（RFC 9111）：Cache-Control、ETag/If-Modified-Since、代理缓存。
- N8.5 Cookie 与会话状态：无状态 HTTP 之上的会话状态承载。
- N8.6 QUIC 与 HTTP/3（RFC 9000/9114）：UDP 之上的多路复用、连接迁移与消解队头阻塞。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：RFC 9110(HTTP 语义)/9111(缓存)/9112(HTTP1.1)/9113(HTTP2)/9114(HTTP3)/9000(QUIC)、Kurose-Ross 8E ch2。冲突两边都记。状态码/头部字段/帧类型不凭记忆，取自 RFC 或标「待核」。本机实证可选（`curl -v` 看方法与状态行/头部；`nc`/`printf` 手敲 HTTP/1.1+`tcpdump port 80`；`curl --http2 -v` 看 ALPN 待核；`curl -v` 观察 Cache-Control 与 304；`curl -c/-b` 看 Set-Cookie/Cookie 往返；`curl --http3` 待核环境是否编入 HTTP/3），不强制。章节末集中列来源与时效。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E ch2：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- IETF RFC 9110-9114 / 9000：https://www.rfc-editor.org/
- Stanford CS144：https://cs144.github.io/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N9 TLS 安全信道与网络安全基础 — 报告prompt

任务：为「L4-02·大主题N9 TLS 安全信道与网络安全基础」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N9」小节，据其 5 条小主题逐条覆盖。防重红线：本课讲 TLS/网络安全**协议机制**；密码学原语（AES/RSA/哈希/签名**为何安全**）属 L5-04（g11），只引用不深挖。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N9 默认 1 份；拆分用 -a/-b。

【v3 格式硬性】层级 #→##→###；### 下不写标签，核心说明+充分辅助说明（面向初学者，必需）；TLS 记录层/握手分层、TLS 1.3 握手时序（1-RTT/0-RTT）、证书链验证示意均独占行或独占块。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N9）】
- N9.1 安全目标与威胁模型：机密/完整/认证/可用及被动/主动攻击面。
- N9.2 TLS 记录层与握手协议分层：记录协议承载、握手协议协商密钥的分工。
- N9.3 TLS 1.3 握手（1-RTT/0-RTT/前向保密）：精简握手流程、临时密钥与前向保密（握手时序独占块）。
- N9.4 证书链与 PKI 信任：X.509 证书、CA 信任链与验证/吊销。
- N9.5 防火墙/IDS/IPsec 概念：边界过滤、入侵检测与网络层安全通道（机制在本课，密码原语→L5-04）。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：RFC 8446(TLS 1.3)、Kurose-Ross 8E ch8；X.509/IPsec 用对应 RFC（待核号）。冲突两边都记。握手消息/密码套件/证书字段不凭记忆，取自 RFC 8446 或标「待核」。本机实证可选（`tcpdump port 443` 看握手明文头与后续密文记录；`openssl s_client -connect host:443 -tls1_3` 看协商与证书；`openssl x509 -text` 解证书、`openssl verify` 验链），不强制。章节末集中列来源与时效。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E ch8：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- IETF RFC 8446(TLS 1.3)：https://www.rfc-editor.org/
- Stanford CS144：https://cs144.github.io/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N10 无线与移动网络（可选/低优先） — 报告prompt

任务：为「L4-02·大主题N10 无线与移动网络」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N10」小节，据其 4 条小主题逐条覆盖。本大主题为查全补位/低优先，教学可裁剪；广度覆盖即可，不追纵深。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N10 体量小，默认 1 份；如整门课合卷则作为章节并入亦可，按下游判定与 -a/-b 命名。

【v3 格式硬性】层级 #→##→###；### 下不写标签，核心说明+充分辅助说明（面向初学者，必需）；802.11 帧格式/CSMA/CA 避让/切换流程示意独占行或独占块。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N10）】
- N10.1 无线链路特性与 CSMA/CA：无线信道损耗、隐藏/暴露终端与避让接入。
- N10.2 802.11 WiFi 架构与帧：基础设施模式、关联/认证与 802.11 帧（帧格式独占块）。
- N10.3 蜂窝网络与 4G/5G：蜂窝架构、无线接入网与核心网。
- N10.4 移动管理与切换：移动 IP、切换与漫游的地址/会话延续。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：Kurose-Ross 8E ch7；802.11 概念用 IEEE 标准/移动 IP RFC（待核号）。冲突两边都记。帧字段/制式参数不凭记忆，取自来源或标「待核」。本机实证可选（`iw dev`，待核容器通常无无线接口），基本以概念为主、不强制。章节末集中列来源与时效。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E ch7：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- IEEE 802.11 / 移动 IP RFC（待核号）：https://www.rfc-editor.org/
- Stanford CS144：https://cs144.github.io/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

```text
[P0] L4-02·大主题N11 API 设计（并入 N-09） — 报告prompt

任务：为「L4-02·大主题N11 API 设计」产出一份符合 report-format.md（v3 教辅模板）的报告。先读 report-format.md（v3）+ brief.md + 本 prompt；再读 round3b-subtopics/g06-networks-distributed.md 中「N11」小节，据其 6 条小主题逐条覆盖。定位：API 设计是「建于 HTTP 9110 语义之上的应用层约定」，非新协议层，与 N8(HTTP) 上下承接。防重：契约测试/CI/协作流程属 L4-06 软工，不在本课；OAuth 2.1 仅登记为演进方向（仍 draft），鉴权承重用 OAuth 2.0=RFC 6749。

【粒度判定（开工第一步）】先判「1 份 or 拆 N 份 + 理由」并写入交付。N11 默认 1 份；拆分用 -a/-b。

【v3 格式硬性】层级 #→##→###；### 下不写标签，核心说明+充分辅助说明（面向初学者，必需）；方法→HTTP 9110 语义映射表、OpenAPI schema 片段、JWT 结构、OAuth 授权流时序均独占行或独占块。教辅口吻；广度优先、讲到初学者懂不做专家纵深。抬头带基线串与核实日期。

【小主题清单（## 章节，内容项来自 round3b N11）】
- N11.1 REST 资源模型与方法映射：资源/表述/无状态、方法映射到 RFC 9110 语义。
- N11.2 幂等性与安全方法：安全/幂等方法定义与幂等键设计。
- N11.3 版本化与演进：URL/头/媒体类型版本与向后兼容策略。
- N11.4 OpenAPI 契约与文档：契约先行、schema 描述请求/响应结构（schema 片段独占块）。
- N11.5 认证与授权（OAuth 2.0 / JWT）：RFC 6749 授权流与 RFC 7519 无状态令牌（⚠OAuth 2.1 仍 draft）（JWT 结构/授权流独占块）。
- N11.6 分页/过滤/限流约定：集合资源分页、过滤与速率限制的实用约定。

【多来源比对（强制）】每项 ≥2 独立来源交叉核对，优先一手：RFC 9110(HTTP 语义，方法/幂等/状态码承重)、RFC 6749(OAuth 2.0)、RFC 7519(JWT)、OpenAPI 规范(Linux Foundation)。冲突两边都记。方法幂等/安全属性、JWT 声明、授权流参数不凭记忆，取自 RFC 或标「待核」；OAuth 2.1 硬标「仍 IETF draft，未成 RFC」。本机实证可选（Python 起最小 HTTP API（`http.server`/Flask）+`curl` 试方法映射；`curl` 重复 PUT/DELETE 观察结果不变；手写 OpenAPI 片段并 `jsonschema` 校验样例；Python `base64` 解 JWT header/payload 看声明结构；`curl` 观察 Retry-After/分页链接头待核目标 API），不强制。章节末集中列来源与时效，前沿项（OAuth 2.1）标「⚙演进快·随时变」。

【权威锚点清单（取自 round3a）】
- Kurose-Ross 8E（HTTP 语义地基参照）：https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf
- IETF RFC 9110(HTTP 语义) / 6749(OAuth 2.0) / 7519(JWT)；⚠OAuth 2.1=IETF draft：https://www.rfc-editor.org/
- OpenAPI 规范（Linux Foundation）：https://spec.openapis.org/

【落盘】自己 Write 到 study/cs-kb/findings/014-computer-networks.md（拆分用 -a/-b）。不残留工具标签/控制字符。
```

共 11 条 prompt
