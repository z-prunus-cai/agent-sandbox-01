# Round3c · L5-01 分布式系统 · 报告 prompt（P1）

```text
[P1] L5-01·大主题1 系统模型、RPC 与失败模型 — 报告prompt

【任务】为「L5-01·大主题1 系统模型、RPC 与失败模型」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）与 study/cs-kb/brief.md 定调，再动笔。研究对象边界：本课=分布式的「算法与不可能性定理」（论文承重），止于「为什么这样才对」；系统级吞吐/分片/调度落地属 L6-05，勿越界。

【v3 格式硬性】
- 标题层级固定：#（报告标题=本大主题）→ ##（小主题=章节）→ ###（每个内容项一节）。### 之下不写「核心概念：」「辅助说明：」等任何标签，直接正文、靠空行分段。
- 每个 ### 内容项都要有：核心说明（必需，讲清是什么/定义/关键结论，正确）+ 充分辅助说明（必需，面向初学者：直觉/为什么这样/最小例子/易错点/与前后关联，写到初学者能懂为止，可多段；不是可省点缀）。
- 任何协议消息序列、不变式、时序图独占一行或独占代码块，不内联埋进句子。
- 教辅口吻；广度优先，讲到初学者懂即止，不做专家级纵深（不写长篇机制深挖/完整推导/设计权衡长论）。

【小主题清单（对应 ## 章节；覆盖内容项）】下游可读 study/cs-kb/round3b-subtopics/g06-networks-distributed.md 中 L5-01 大主题 D1（小主题 1.1–1.5）取全部内容项，本 prompt 已点名，自足：
- 1.1 同步 vs 异步系统模型：时序假设（有界/无界延迟）如何决定问题可解性
- 1.2 失败模型谱系：崩溃-停止/遗漏/拜占庭失败的假设强度递增
- 1.3 消息传递语义：不可靠信道的丢失/乱序/重复抽象
- 1.4 RPC 与并发原语：远程调用抽象与 at-least-once / at-most-once / exactly-once 语义
- 1.5 故障检测器：心跳/超时的完备性（completeness）与准确性（accuracy）权衡

【验证纪律（强制）】
- 多来源比对强制：每个内容项 ≥2 个独立来源交叉核对，优先一手（MIT 6.5840 前 6.824 讲义/lab、经典论文原文）；来源打架时两边都记、点明分歧、不和稀泥。
- Python 模拟（lossy/reorder channel、最小 RPC、心跳+超时误判）可选，仅在便宜且能加固某结论时做，不能替代多来源比对。
- 章节末集中列「来源与时效」（不逐项脚注）：锚点+版本+核实日期；前沿/演进项标「⚙演进快·锚版本」；未证实数值/默认值标「待核」，绝不凭记忆编造。给出核实日期。

【权威锚点清单（取自 round3a，核实 2026-07-25）】
- MIT 6.5840（前 6.824）Distributed Systems，Spring 2026 schedule（L1-2 MapReduce/RPC&Threads）：https://pdos.csail.mit.edu/6.824/schedule.html
- 经典论文（承重）：分布式系统失败模型/故障检测器相关（Chandra-Toueg 故障检测器为可选延伸锚，待核是否引为承重）

【粒度判定】动笔前先判「1 份 or 拆 N 份 + 理由」：D1 含 5 个小主题、机制同源（都在刻画系统模型与失败假设），预判 1 份即可；若判需拆，给理由并按 019-distributed-systems-<后缀>.md 命名。

【落盘】自己 Write 到 study/cs-kb/findings/019-distributed-systems.md（若粒度判定为拆分，用 019-distributed-systems-a/-b.md 等）。报告抬头带可 grep 基线串与核实日期。成品不得残留工具标签或控制字符。
```

```text
[P1] L5-01·大主题2 时间、时钟与因果序 — 报告prompt

【任务】为「L5-01·大主题2 时间、时钟与因果序」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）与 study/cs-kb/brief.md 定调，再动笔。边界：本课讲逻辑序（为什么物理时钟不能定序、逻辑/向量时钟如何捕获因果），把「物理时间做成系统能力」（Spanner TrueTime）留给 L6-05，勿越界。

【v3 格式硬性】
- 层级 #→##→###；### 之下不写任何标签，直接正文、空行分段。
- 每个 ### 内容项：核心说明（必需，正确）+ 充分辅助说明（必需，面向初学者：直觉/为什么/最小例子/易错点/关联，可多段）。
- 任何时钟递增规则、happens-before 偏序关系、时序图独占一行或独占代码块，不内联。
- 教辅口吻；广度优先，讲到初学者懂即止，不做专家级纵深。

【小主题清单（对应 ## 章节；覆盖内容项）】下游可读 round3b-subtopics/g06-networks-distributed.md 中 L5-01 大主题 D2（小主题 2.1–2.4）取全部内容项，本 prompt 已点名，自足：
- 2.1 物理时钟与时钟漂移：石英漂移、NTP 同步为何仍不足以给事件定序
- 2.2 happens-before 与 Lamport 时钟：事件偏序定义与标量逻辑时钟递增规则
- 2.3 向量时钟：捕获因果，判定 happens-before / 并发（concurrent）
- 2.4 全序广播与时间戳定序：用逻辑时钟 + 全序广播构造一致事件序

【验证纪律（强制）】
- 多来源比对强制：每个内容项 ≥2 个独立来源交叉核对，优先一手（Lamport CACM 1978 原文、RFC 5905 NTP、MIT 6.5840 讲义）；冲突两边都记、点明分歧。
- Python 模拟（Lamport 时钟单调性、向量时钟并发检出、基于时间戳的全序队列）可选，不替代多来源比对。
- 章节末集中列「来源与时效」：锚点+版本+核实日期；演进项标「⚙演进快」；未证实项标「待核」，不编造；给核实日期。

【权威锚点清单（取自 round3a，核实 2026-07-25）】
- Lamport, "Time, Clocks, and the Ordering of Events in a Distributed System"，CACM 1978（承重一手）
- RFC 5905（NTPv4）：https://www.rfc-editor.org/
- MIT 6.5840 Spring 2026 schedule：https://pdos.csail.mit.edu/6.824/schedule.html

【粒度判定】动笔前先判「1 份 or 拆 N 份 + 理由」：D2 含 4 个小主题、篇幅适中且同源（时间/因果一条主线），预判 1 份；若判需拆，给理由并按 019-distributed-systems-<后缀>.md 命名。

【落盘】自己 Write 到 study/cs-kb/findings/019-distributed-systems.md（拆分则 019-distributed-systems-a/-b.md 等）。抬头带基线串与核实日期。成品不得残留工具标签或控制字符。
```

```text
[P1] L5-01·大主题3 复制与复制状态机（RSM） — 报告prompt

【任务】为「L5-01·大主题3 复制与复制状态机（RSM）」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）与 study/cs-kb/brief.md 定调，再动笔。边界：复制≠共识（复制是目标、共识是达成手段，共识本身在大主题4）；工程化（快照/成员变更/分片）属 L6-05，本课只讲复制模型与法定人数原理，勿越界。

【v3 格式硬性】
- 层级 #→##→###；### 之下不写任何标签，直接正文、空行分段。
- 每个 ### 内容项：核心说明（必需，正确）+ 充分辅助说明（必需，面向初学者：直觉/为什么/最小例子/易错点/关联，可多段）。
- 任何 quorum 不变式（如 W+R>N）、复制状态机不变式、时序图独占一行或独占代码块，不内联。
- 教辅口吻；广度优先，讲到初学者懂即止，不做专家级纵深。

【小主题清单（对应 ## 章节；覆盖内容项）】下游可读 round3b-subtopics/g06-networks-distributed.md 中 L5-01 大主题 D3（小主题 3.1–3.4）取全部内容项，本 prompt 已点名，自足：
- 3.1 复制目标与架构：复制动机与主从/多主/无主三类写入拓扑对比
- 3.2 复制状态机模型：确定性状态机 + 有序日志 ⇒ 各副本收敛同态
- 3.3 读写 Quorum 与 W+R>N：法定人数交集保证读到最新写
- 3.4 同步 vs 异步复制的张力：复制时机在丢数据风险与写延迟间的取舍

【验证纪律（强制）】
- 多来源比对强制：每个内容项 ≥2 个独立来源交叉核对，优先一手（MIT 6.5840 Raft/一致性系列讲义、Raft/Paxos 论文中关于 RSM 的论述）；冲突两边都记、点明分歧。
- Python 模拟（同一日志喂两副本验证状态一致、N/R/W 配置下读写交集）可选，不替代多来源比对。
- 章节末集中列「来源与时效」：锚点+版本+核实日期；演进项标「⚙演进快」；未证实项标「待核」，不编造；给核实日期。

【权威锚点清单（取自 round3a，核实 2026-07-25）】
- MIT 6.5840（前 6.824）Spring 2026 schedule（Raft/一致性系列）：https://pdos.csail.mit.edu/6.824/schedule.html
- 经典论文（承重）：Raft（ATC 2014）、Paxos（1998/2001）中 RSM 与 quorum 相关论述

【粒度判定】动笔前先判「1 份 or 拆 N 份 + 理由」：D3 含 4 个小主题、同源，预判 1 份；若判需拆，给理由并按 019-distributed-systems-<后缀>.md 命名。

【落盘】自己 Write 到 study/cs-kb/findings/019-distributed-systems.md（拆分则 019-distributed-systems-a/-b.md 等）。抬头带基线串与核实日期。成品不得残留工具标签或控制字符。
```

```text
[P1] L5-01·大主题4 共识：FLP / Paxos / Raft — 报告prompt

【任务】为「L5-01·大主题4 共识：FLP / Paxos / Raft」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）与 study/cs-kb/brief.md 定调，再动笔。边界：本课停在「选举/日志复制/安全性论证/不可能性」（为什么对）；快照/成员变更/分片 KV/线性一致读的工程实现属 L6-05，勿越界。

【v3 格式硬性】
- 层级 #→##→###；### 之下不写任何标签，直接正文、空行分段。
- 每个 ### 内容项：核心说明（必需，正确）+ 充分辅助说明（必需，面向初学者：直觉/为什么/最小例子/易错点/关联，可多段）。
- 任何协议消息序列（Prepare/Accept、AppendEntries）、安全性不变式、时序图独占一行或独占代码块，不内联埋进句子。
- 教辅口吻；广度优先，讲到初学者懂即止（safety/liveness 讲直觉与结论，不做完整形式化证明纵深）。

【小主题清单（对应 ## 章节；覆盖内容项）】下游可读 round3b-subtopics/g06-networks-distributed.md 中 L5-01 大主题 D4（小主题 4.1–4.6）取全部内容项，本 prompt 已点名，自足：
- 4.1 共识问题定义：agreement/validity/termination 与 safety/liveness 目标
- 4.2 FLP 不可能性：异步 + 单崩溃下确定性共识无法保证终止的边界
- 4.3 Paxos 单值共识：Prepare/Accept 两阶段与多数派 quorum 交集安全性
- 4.4 Raft leader 选举：任期、投票、随机化超时避免选票分裂
- 4.5 Raft 日志复制与提交：AppendEntries、日志匹配性质与提交规则
- 4.6 规避 FLP 以保活性：随机化/故障检测/稳定领导者在实践中恢复 liveness

【验证纪律（强制）】
- 多来源比对强制：每个内容项 ≥2 个独立来源交叉核对，优先一手 Paxos 论文（Lamport 1998/2001）、Raft 论文（Ongaro & Ousterhout, ATC 2014）、FLP 论文（JACM 1985）、MIT 6.5840（前 6.824）讲义；冲突两边都记、点明分歧、不和稀泥。
- Python 模拟（单值 Paxos 竞争提案收敛、Raft 选举 split-vote 重试、日志复制崩溃后一致性校验、超时随机化恢复活性）可选，不替代多来源比对。
- 章节末集中列「来源与时效」：锚点+版本+核实日期；演进项标「⚙演进快」；未证实项标「待核」，不编造；给核实日期。

【权威锚点清单（取自 round3a，核实 2026-07-25）】
- FLP：Fischer, Lynch & Paterson，JACM 1985（承重一手）
- Paxos：Lamport 1998「The Part-Time Parliament」/ 2001「Paxos Made Simple」（承重一手）
- Raft：Ongaro & Ousterhout，USENIX ATC 2014（承重一手）
- MIT 6.5840（前 6.824）Spring 2026 schedule（L4/L6-7）：https://pdos.csail.mit.edu/6.824/schedule.html

【粒度判定】动笔前先判「1 份 or 拆 N 份 + 理由」：D4 含 6 个小主题、跨 FLP/Paxos/Raft 三套机制且篇幅偏重，可能需拆（如 4.1-4.2 定义与不可能性 / 4.3-4.6 Paxos+Raft 两份）；下游据实给「1 份 or 拆 N 份 + 理由」，拆分按 019-distributed-systems-<后缀>.md 命名。

【落盘】自己 Write 到 study/cs-kb/findings/019-distributed-systems.md（拆分则 019-distributed-systems-a/-b.md 等）。抬头带基线串与核实日期。成品不得残留工具标签或控制字符。
```

```text
[P1] L5-01·大主题5 一致性模型谱系 — 报告prompt

【任务】为「L5-01·大主题5 一致性模型谱系」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）与 study/cs-kb/brief.md 定调，再动笔。边界：本课讲分布式对象一致性语义（线性→顺序→因果→最终）；硬件内存模型/缓存一致在 L5-05/L4-05，勿混。

【v3 格式硬性】
- 层级 #→##→###；### 之下不写任何标签，直接正文、空行分段。
- 每个 ### 内容项：核心说明（必需，正确）+ 充分辅助说明（必需，面向初学者：直觉/为什么/最小例子/易错点/关联，可多段）。
- 任何一致性判定的不变式/历史（history）合法性条件、时序图独占一行或独占代码块，不内联。
- 教辅口吻；广度优先，讲到初学者懂即止，不做专家级纵深。

【小主题清单（对应 ## 章节；覆盖内容项）】下游可读 round3b-subtopics/g06-networks-distributed.md 中 L5-01 大主题 D5（小主题 5.1–5.5）取全部内容项，本 prompt 已点名，自足：
- 5.1 线性一致性：实时序 + 原子生效的最强单对象一致性
- 5.2 顺序一致性：存在保程序序的全序，但不绑实时
- 5.3 因果一致性：尊重因果依赖、并发写允许分歧
- 5.4 最终一致性与会话保证：收敛性与读己写/单调读等会话保证
- 5.5 一致性代价谱系：越强一致越需协调的性能/可用性权衡轴（对接大主题6 CAP）

【验证纪律（强制）】
- 多来源比对强制：每个内容项 ≥2 个独立来源交叉核对，优先一手（Herlihy & Wing TOPLAS 1990 线性一致、Lamport 1979 顺序一致、MIT 6.5840 L8 讲义）；冲突两边都记、点明分歧。
- Python 模拟（暴搜检验读写历史是否可线性化/存在合法顺序序、向量时钟判因果可见性、异步收敛与会话读语义）可选，不替代多来源比对。
- 章节末集中列「来源与时效」：锚点+版本+核实日期；演进项标「⚙演进快」；未证实项标「待核」，不编造；给核实日期。

【权威锚点清单（取自 round3a，核实 2026-07-25）】
- Herlihy & Wing，"Linearizability"，TOPLAS 1990（承重一手）
- Lamport 1979（顺序一致性，"How to Make a Multiprocessor Computer That Correctly Executes Multiprocess Programs"）
- MIT 6.5840（前 6.824）Spring 2026 schedule（L8 Linearizability）：https://pdos.csail.mit.edu/6.824/schedule.html

【粒度判定】动笔前先判「1 份 or 拆 N 份 + 理由」：D5 含 5 个小主题、同一谱系一条轴，预判 1 份；若判需拆，给理由并按 019-distributed-systems-<后缀>.md 命名。

【落盘】自己 Write 到 study/cs-kb/findings/019-distributed-systems.md（拆分则 019-distributed-systems-a/-b.md 等）。抬头带基线串与核实日期。成品不得残留工具标签或控制字符。
```

```text
[P1] L5-01·大主题6 CAP 与 PACELC 分区权衡 — 报告prompt

【任务】为「L5-01·大主题6 CAP 与 PACELC 分区权衡」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）与 study/cs-kb/brief.md 定调，再动笔。边界：本课讲两框架的精确表述、误读辨析与归类方法（谱系的「选择理由」）；具体系统怎么选（Dynamo/Spanner 实例）属 L6-05，本课只给分类方法、实例落 L6-05，勿越界。

【v3 格式硬性】
- 层级 #→##→###；### 之下不写任何标签，直接正文、空行分段。
- 每个 ### 内容项：核心说明（必需，正确）+ 充分辅助说明（必需，面向初学者：直觉/为什么/最小例子/易错点/关联，可多段）。
- 任何定理的精确表述/不变式、分区期间的取舍关系、时序图独占一行或独占代码块，不内联。
- 教辅口吻；广度优先，讲到初学者懂即止，不做专家级纵深。

【小主题清单（对应 ## 章节；覆盖内容项）】下游可读 round3b-subtopics/g06-networks-distributed.md 中 L5-01 大主题 D6（小主题 6.1–6.4）取全部内容项，本 prompt 已点名，自足：
- 6.1 CAP 定理精确表述：分区期间线性一致 C 与可用 A 不可兼得
- 6.2 CAP 常见误读辨析：「牺牲 P」不成立、C 特指线性一致、非「三选二」等澄清
- 6.3 PACELC 补充维度：无分区时延迟 L 与一致 C 也需权衡
- 6.4 系统按 CAP/PACELC 归类：用两框架给具体系统（AP/CP、EL/EC）归类的方法

【验证纪律（强制）】
- 多来源比对强制：每个内容项 ≥2 个独立来源交叉核对，优先一手（Gilbert & Lynch SIGACT News 2002 CAP 证明、Abadi 2012 PACELC、MIT 6.5840 讲义）；冲突两边都记、点明分歧、不和稀泥（尤其 CAP 通俗「三选二」表述 vs 学术精确表述的分歧要显式记）。
- Python 模拟（分区时强制 C/A 二选一的玩具复现）可选，不替代多来源比对。
- 章节末集中列「来源与时效」：锚点+版本+核实日期；演进项标「⚙演进快」；未证实项标「待核」，不编造；给核实日期。

【权威锚点清单（取自 round3a，核实 2026-07-25）】
- Gilbert & Lynch，CAP 定理证明，ACM SIGACT News 2002（承重一手）
- Abadi，"Consistency Tradeoffs in Modern Distributed Database System Design"（PACELC），IEEE Computer 2012（承重一手）
- MIT 6.5840（前 6.824）Spring 2026 schedule：https://pdos.csail.mit.edu/6.824/schedule.html

【粒度判定】动笔前先判「1 份 or 拆 N 份 + 理由」：D6 含 4 个小主题、篇幅较小且高度同源，预判 1 份；若判需拆，给理由并按 019-distributed-systems-<后缀>.md 命名。

【落盘】自己 Write 到 study/cs-kb/findings/019-distributed-systems.md（拆分则 019-distributed-systems-a/-b.md 等）。抬头带基线串与核实日期。成品不得残留工具标签或控制字符。
```

```text
[P1] L5-01·大主题7 分布式事务与原子提交 — 报告prompt

【任务】为「L5-01·大主题7 分布式事务与原子提交」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）与 study/cs-kb/brief.md 定调，再动笔。边界：本课讲 2PC 的理论机制/阻塞性、快照隔离等并发控制的理论机制；Spanner 的分布式事务系统实现属 L6-05 C3，勿越界。

【v3 格式硬性】
- 层级 #→##→###；### 之下不写任何标签，直接正文、空行分段。
- 每个 ### 内容项：核心说明（必需，正确）+ 充分辅助说明（必需，面向初学者：直觉/为什么/最小例子/易错点/关联，可多段）。
- 任何协议消息序列（prepare→commit）、原子提交不变式、时序图独占一行或独占代码块，不内联埋进句子。
- 教辅口吻；广度优先，讲到初学者懂即止，不做专家级纵深。

【小主题清单（对应 ## 章节；覆盖内容项）】下游可读 round3b-subtopics/g06-networks-distributed.md 中 L5-01 大主题 D7（小主题 7.1–7.5）取全部内容项，本 prompt 已点名，自足：
- 7.1 分布式事务与 ACID 挑战：跨节点原子性与隔离为何比单机难
- 7.2 两阶段提交（2PC）：协调者/参与者 prepare→commit 的原子提交协议
- 7.3 2PC 阻塞问题与 3PC：协调者故障导致阻塞、3PC 非阻塞尝试与局限
- 7.4 并发控制与快照隔离（理论）：MVCC/快照隔离机制与写偏斜异常
- 7.5 分布式死锁与提交时序：跨节点死锁的检测/避免与提交定序

【验证纪律（强制）】
- 多来源比对强制：每个内容项 ≥2 个独立来源交叉核对，优先一手（MIT 6.5840 L11 Distributed Transactions 讲义、2PC/快照隔离经典文献）；冲突两边都记、点明分歧、不和稀泥。
- Python 模拟（2PC 正常提交/中止、杀协调者复现阻塞、MVCC 版本读构造写偏斜）可选，不替代多来源比对。
- 章节末集中列「来源与时效」：锚点+版本+核实日期；演进项标「⚙演进快」；未证实项标「待核」，不编造；给核实日期。

【权威锚点清单（取自 round3a，核实 2026-07-25）】
- MIT 6.5840（前 6.824）Spring 2026 schedule（L11 Distributed Transactions）：https://pdos.csail.mit.edu/6.824/schedule.html
- 经典文献（承重）：2PC/3PC 原子提交与快照隔离（Berenson et al. 隔离级别、Gray & Reuter 事务处理等，择一手交叉核对）

【粒度判定】动笔前先判「1 份 or 拆 N 份 + 理由」：D7 含 5 个小主题（原子提交 7.1-7.3 + 并发控制 7.4-7.5 两簇），预判 1 份可容纳，若篇幅偏重可拆为提交协议/并发控制两份；下游据实给「1 份 or 拆 N 份 + 理由」，拆分按 019-distributed-systems-<后缀>.md 命名。

【落盘】自己 Write 到 study/cs-kb/findings/019-distributed-systems.md（拆分则 019-distributed-systems-a/-b.md 等）。抬头带基线串与核实日期。成品不得残留工具标签或控制字符。
```

共 7 条 prompt
