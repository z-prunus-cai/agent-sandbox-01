# Round3c · L5-12 可观测性/SRE · 报告 prompt（P1）

```text
[P1] L5-12·大主题1 SRE 原则与 DevOps 关系 — 报告prompt

【任务】
为「L5-12·大主题1 SRE 原则与 DevOps 关系」产出一份符合 report-format v3（广度优先的"教辅"）的报告。先读 study/cs-kb/report-format.md（v3 模板）、study/cs-kb/brief.md，再动笔。抬头带可 grep 基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。先修 L5-11 非功能目标；本主题是运维工程化元框架。

【v3 格式硬性（必须遵守）】
- 标题层级固定 #→##→###：# = 报告标题（本大主题）；## = 小主题（章节）；### = 每个内容项一节。
- ### 下不写「核心概念：」「辅助说明：」等任何标签，直接正文、靠空行分段。
- 每个 ### 内容项：核心说明（必需，是什么/定义/关键结论，正确）＋ 辅助说明（必需且充分——面向初学者的直觉/为什么/最小例子/易错点/前后关联，写到初学者能懂，可多段）。辅助说明不是可省点缀。
- 任何公式/比值/指标定义独占一行（块级），不内联埋进句子。
- 教辅口吻；广度优先——列全内容项、每项讲到初学者懂，但不做专家级纵深（不写长篇机制深挖/推导/设计权衡长论）。

【小主题清单（=## 章节）与应覆盖内容项】
下游可直接读 study/cs-kb/round3b-subtopics/g12-engineering-delivery.md 中 L5-12 大主题 12-1 全部小主题（12-1.1 ~ 12-1.4），逐条成节。关键内容项：
- 12-1.1 SRE 定义 — 以软件工程手段解决运维问题（SRE 书 ch1）。
- 12-1.2 拥抱风险 — 可靠性并非越高越好、100% 是错误目标（ch3）。
- 12-1.3 SRE vs DevOps — "class SRE implements DevOps"的关系（Workbook ch1）。
- 12-1.4 SRE 团队约定 — 运维负载上限（如 ≤50%）等工程化约束。

【多来源比对（强制）】
- 每个内容项 ≥2 个独立来源交叉核对，优先一手：Google《SRE》《SRE Workbook》全文在线。
- 业界二手（DevOps 综述、博客）打 ⚠，仅指路/补留白、不承重。
- 来源打架时两边都记、点明分歧、不和稀泥。
- 不编造：具体数值（如运维负载 50% 上限）来自比对通过的来源或标「待核」，不凭记忆填。
- 标核实日期；SRE 文化/流程为方法学，与后续技术支柱主题分账。
- 章节末（#### 来源与时效）集中列锚点，不逐项脚注。

【权威锚点清单】
- Google《Site Reliability Engineering》 sre.google/sre-book/table-of-contents/（O'Reilly 2016/2017，34 章 5 部，全文在线免费）— 强·一手（ch1/ch3）。
- 《The Site Reliability Workbook》 sre.google/workbook/table-of-contents/（O'Reilly 2018，21 章 3 部，全文在线）— 强·一手（ch1）。

【粒度判定（先判再写）】
先判定"1 份 or 拆 N 份 + 理由"：本主题 4 小主题、概念型、无跨机制，预期 1 份即可，但你须显式给出判定与理由。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（本课多主题共享该 slug；若判定拆分按 035-observability-sre-a/-b 命名）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题2 SLI / SLO / 错误预算 — 报告prompt

【任务】
为「L5-12·大主题2 SLI / SLO / 错误预算」产出一份符合 report-format v3 的报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。先修 L5-11 非功能目标；本主题是本组第二强实证点（错误预算纯算术可硬实证）。

【v3 格式硬性】
- #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需，讲到懂为止）。
- SLI/SLO/错误预算等公式独占一行（块级），例如：
  错误预算 = (1 − SLO) × 窗口时长
  绝不把公式内联埋进句子。
- 教辅口吻；广度优先、讲到初学者懂但不做专家纵深。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b-subtopics/g12-engineering-delivery.md 中 L5-12 大主题 12-2 全部小主题（12-2.1 ~ 12-2.4），逐条成节：
- 12-2.1 SLI 定义 — 好指标属性（用户视角、可聚合、比例式）（SRE 书 ch4）。
- 12-2.2 SLO 目标设定 — 目标与窗口选择，避免过严/过松（Workbook ch2）。
- 12-2.3 错误预算 =(1−SLO)×窗口 — 预算=允许的不可靠量；公式独占行。
- 12-2.4 SLA vs SLO — 对外合约（含罚则）vs 对内目标的区分与后果。

【多来源比对（强制）】
- 每项 ≥2 独立源、优先一手（SRE 书 ch4、Workbook ch2）；二手打 ⚠。
- 冲突两边都记、点明分歧。
- 不编造数值（例：99.9% 对应月度约 43.2 分钟不可用等，须比对或标「待核」）。
- 【SLO 计算 demo（可选）】可用 Python 纯算术做错误预算/允许停机时长演算，贴最小可复现脚本＋真实输出＋环境串；仅作辅助加固，不替代多来源比对。
- 章节末集中列来源与时效。

【权威锚点清单】
- Google《SRE》 sre.google/sre-book/table-of-contents/ — 强·一手（ch4）。
- 《SRE Workbook》 sre.google/workbook/table-of-contents/ — 强·一手（ch2）。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：本主题 4 小主题、算术型、内聚，预期 1 份，须显式给理由。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题3 基于 SLO 的告警（burn-rate） — 报告prompt

【任务】
为「L5-12·大主题3 基于 SLO 的告警（burn-rate）」产出符合 report-format v3 的报告。先读 report-format.md、brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。先修本课大主题2（SLI/SLO/错误预算）。

【v3 格式硬性】
- #→##→###；### 下不写标签，直接正文＋空行分段。
- 每 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需）。
- 燃烧率等公式独占一行（块级），例如：
  burn rate = 预算消耗速度 ／ 恰好在窗口内耗尽预算的速度（1× = 恰好耗尽）
  不内联埋进句子。
- 教辅口吻；广度优先、讲到懂不做专家纵深。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b L5-12 大主题 12-3 全部小主题（12-3.1 ~ 12-3.4），逐条成节：
- 12-3.1 传统阈值告警的问题 — 逐指标阈值导致噪声/漏报（SRE 书 ch10）。
- 12-3.2 燃烧率概念 — burn rate = 预算消耗速度；1×=恰好耗尽（Workbook ch5）。
- 12-3.3 多窗口多燃烧率告警 — 快慢窗口组合兼顾灵敏与稳健。
- 12-3.4 告警质量指标 — precision/recall/detection time/reset time 评估。

【多来源比对（强制）】
- 每项 ≥2 独立源、优先一手（Workbook ch5「Alerting on SLOs」、SRE 书 ch10）；二手打 ⚠。
- 冲突两边都记。
- 不编造多窗口告警的具体窗口/阈值配置（Workbook 给的示例值须比对或标「待核」）。
- 【SLO 计算 demo（可选）】可用 Python 演示"某燃烧率下多久耗尽预算"，贴脚本＋真实输出＋环境串；仅辅助。
- 章节末集中列来源与时效。

【权威锚点清单】
- 《SRE Workbook》 ch5「Alerting on SLOs」 sre.google/workbook/table-of-contents/ — 强·一手。
- Google《SRE》 ch10 sre.google/sre-book/table-of-contents/ — 强·一手。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：4 小主题、内聚，预期 1 份。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题4 metrics 支柱与监控分布式系统 — 报告prompt

【任务】
为「L5-12·大主题4 metrics 支柱与监控分布式系统」产出符合 report-format v3 的报告。先读 report-format.md、brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。跨课提示：基准统计有效性回指 L2-03 概率。

【v3 格式硬性】
- #→##→###；### 下不写标签，正文＋空行分段。
- 每 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需）。
- 任何公式/分位数定义独占一行（块级），不内联。
- 教辅口吻；广度优先、讲到懂不做专家纵深。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b L5-12 大主题 12-4 全部小主题（12-4.1 ~ 12-4.4），逐条成节：
- 12-4.1 时序数据模型与聚合 — 数值时序的采样/聚合/降采样（SRE 书 ch6）。
- 12-4.2 四黄金信号 — 延迟/流量/错误/饱和度。
- 12-4.3 Prometheus 拉模型/OpenMetrics — 抓取模型、PromQL、暴露格式（一手）。
- 12-4.4 分位数/直方图与陷阱 — 平均值误导、分位数不可跨实例平均、直方图桶。

【多来源比对（强制）】
- 每项 ≥2 独立源、优先一手（SRE 书 ch6「Monitoring Distributed Systems」、Prometheus/OpenMetrics 官方文档）；二手打 ⚠。
- "三支柱"是社区框架（⚠）须与规范一手分账；metrics 概念（⚠）vs Prometheus/OpenMetrics 规范（一手）分开标。
- 冲突两边都记；不编造 PromQL 语法/暴露格式细节，须比对或标「待核」。
- 【工具演进】Prometheus/OpenMetrics 工具链演进处标「⚙演进快·锚版本」，给核实日期与所锚版本。
- 章节末集中列来源与时效。

【权威锚点清单】
- Google《SRE》 ch6 sre.google/sre-book/table-of-contents/ — 强·一手。
- Prometheus / OpenMetrics prometheus.io/docs（metrics 事实标准）— 一手（⚙演进快·锚版本）。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：4 小主题，预期 1 份。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题5 logs 支柱 — 报告prompt

【任务】
为「L5-12·大主题5 logs 支柱」产出符合 report-format v3 的报告。先读 report-format.md、brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- #→##→###；### 下不写标签，正文＋空行分段。
- 每 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需）。
- 任何公式独占一行（块级），不内联。
- 教辅口吻；广度优先、讲到懂不做专家纵深。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b L5-12 大主题 12-5 全部小主题（12-5.1 ~ 12-5.4），逐条成节：
- 12-5.1 结构化 vs 非结构化日志 — 键值结构化利于查询与聚合。
- 12-5.2 日志级别与采样/降级 — 高流量下的采样与优先级降级。
- 12-5.3 日志聚合管道 — 采集→传输→索引→查询链路。
- 12-5.4 OTel Logs 规范 vs "支柱"概念 — 规范（GA，一手）与社区"三支柱"框架（⚠）分账。

【多来源比对（强制）】
- 每项 ≥2 独立源；本主题一手承重较弱：logs 无单一标准——OTel Logs 规范（GA）作一手，"三支柱"社区综述打 ⚠ 且显式分账（概念框架 vs 规范）。
- 冲突两边都记、点明分歧。
- 不编造日志级别语义/采样默认值，须比对或标「待核」。
- 【工具演进】OTel Logs 属演进快对象，标「⚙演进快·锚版本」＋核实日期＋所锚 OTel spec 版本。
- 章节末集中列来源与时效。

【权威锚点清单】
- OpenTelemetry Logs 规范 opentelemetry.io/docs/specs（CNCF 已毕业 2026-05；logs 信号 GA/stable，核实 2026-07-25）— 强·一手（⚙演进快·锚版本）。
- "三支柱"社区综述 — ⚠二手，仅指路/补留白、不承重。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：4 小主题，预期 1 份。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题6 traces 支柱与分布式追踪 — 报告prompt

【任务】
为「L5-12·大主题6 traces 支柱与分布式追踪」产出符合 report-format v3 的报告。先读 report-format.md、brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。跨课提示：因果序回指 L5-01，请求链回指 L4-02。

【v3 格式硬性】
- #→##→###；### 下不写标签，正文＋空行分段。
- 每 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需）。
- traceparent 头字段格式若列，须独占一行（块级），不内联。
- 教辅口吻；广度优先、讲到懂不做专家纵深。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b L5-12 大主题 12-6 全部小主题（12-6.1 ~ 12-6.4），逐条成节：
- 12-6.1 span 树与因果关系 — trace 由带父子的 span 组成（跨 L5-01 因果序）。
- 12-6.2 context 传播与 traceparent — W3C Trace Context 头格式（L1 Rec；L2 CR Draft 加随机 trace-id 标志，向后兼容 v00）。
- 12-6.3 头采样 vs 尾采样 — 入口决定 vs 完成后按结果采样的取舍。
- 12-6.4 追踪的用途 — 延迟根因、依赖图、跨服务瓶颈定位（跨 L4-02 请求链）。

【多来源比对（强制）】
- 每项 ≥2 独立源、优先一手（W3C Trace Context 标准、OTel Traces 规范）；二手打 ⚠。
- W3C Trace Context 须硬标状态时效：Level 1 = Recommendation；Level 2 = Candidate Recommendation Draft（加随机 trace-id 标志，向后兼容 v00）——两级都记、点明差异，核实 2026-07-25。
- 不编造 traceparent 字段布局/version 字节，须比对一手或标「待核」。
- 【工具演进】OTel Traces（GA）标「⚙演进快·锚版本」＋核实日期。
- 章节末集中列来源与时效。

【权威锚点清单】
- W3C Trace Context w3.org/TR/trace-context（Level 1 = Recommendation）/ w3.org/TR/trace-context-2（Level 2 = Candidate Recommendation Draft）— 强·一手标准（核实 2026-07-25）。
- OpenTelemetry Traces 规范 opentelemetry.io/docs/specs（CNCF 已毕业 2026-05；traces GA/stable）— 强·一手（⚙演进快·锚版本）。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：4 小主题，预期 1 份。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题7 OpenTelemetry 统一遥测 — 报告prompt

【任务】
为「L5-12·大主题7 OpenTelemetry 统一遥测」产出符合 report-format v3 的报告。先读 report-format.md、brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。本主题是统一"三支柱概念"的规范实体，成熟度须硬标。

【v3 格式硬性】
- #→##→###；### 下不写标签，正文＋空行分段。
- 每 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需）。
- 任何公式/格式定义独占一行（块级），不内联。
- 教辅口吻；广度优先、讲到懂不做专家纵深。抬头成熟度字段标 GA/⚙演进快。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b L5-12 大主题 12-7 全部小主题（12-7.1 ~ 12-7.4），逐条成节：
- 12-7.1 OTel 架构 — API/SDK/Collector/OTLP 分层（opentelemetry.io/docs/specs）。
- 12-7.2 三信号统一与语义约定 — semantic conventions 统一属性命名。
- 12-7.3 信号成熟度硬标 — traces/metrics/logs GA；profiling RC（Q1 2026）→目标 GA Q3 2026（核实 2026-07-25，须坐实基线可用性）。
- 12-7.4 instrumentation — 自动（zero-code）vs 手动埋点。

【多来源比对（强制）】
- 每项 ≥2 独立源、优先一手（opentelemetry.io/docs/specs 官方规范）；二手打 ⚠。
- 冲突两边都记。
- 不编造 GA/RC 状态与时间点：CNCF 已毕业（graduated, 2026-05）；traces/metrics/logs GA/stable；profiling 信号 RC（Q1 2026）→目标 GA Q3 2026——凡未证实的状态/日期标「待核」，不凭记忆填。
- 【工具演进】全主题标「⚙演进快·锚版本」，给核实日期与所锚 spec/信号版本；前沿项（profiling）硬标状态＋目标版本，不作已 GA 结论。
- 章节末集中列来源与时效。

【权威锚点清单】
- OpenTelemetry 规范（CNCF） opentelemetry.io/docs/specs（CNCF 已毕业 2026-05；traces/metrics/logs GA，profiling RC→目标 GA Q3 2026；核实 2026-07-25）— 强·一手（⚙演进快·锚版本）。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：4 小主题、内聚，预期 1 份。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题8 消除 toil 与自动化 — 报告prompt

【任务】
为「L5-12·大主题8 消除 toil 与自动化」产出符合 report-format v3 的报告。先读 report-format.md、brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- #→##→###；### 下不写标签，正文＋空行分段。
- 每 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需）。
- 任何公式（如 toil 占比）独占一行（块级），不内联。
- 教辅口吻；广度优先、讲到懂不做专家纵深。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b L5-12 大主题 12-8 全部小主题（12-8.1 ~ 12-8.4），逐条成节：
- 12-8.1 toil 定义与特征 — 手动/重复/可自动化/无长期价值/随规模线性增长（SRE 书 ch5）。
- 12-8.2 toil 量化与预算 — 度量 toil 占比并设上限。
- 12-8.3 自动化演进阶梯 — 从无自动化到自主系统的层级（ch7）。
- 12-8.4 工程 vs 运维时间平衡 — 用工程时间偿还 toil（Workbook ch6）。

【多来源比对（强制）】
- 每项 ≥2 独立源、优先一手（SRE 书 ch5/ch7、Workbook ch6）；二手打 ⚠。
- 冲突两边都记。
- 不编造 toil 占比上限等具体数值，须比对或标「待核」。
- 章节末集中列来源与时效；本主题属方法学，标核实日期。
- 【工具演进】自动化工具举例处标「⚙演进快·锚版本」（若引具体工具）。
- 章节末集中列来源与时效。

【权威锚点清单】
- Google《SRE》 ch5/ch7 sre.google/sre-book/table-of-contents/ — 强·一手。
- 《SRE Workbook》 ch6 sre.google/workbook/table-of-contents/ — 强·一手。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：4 小主题，预期 1 份。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题9 事件响应与无指责事后复盘 — 报告prompt

【任务】
为「L5-12·大主题9 事件响应与无指责事后复盘」产出符合 report-format v3 的报告。先读 report-format.md、brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。本主题属方法学/流程，与技术支柱主题分账。

【v3 格式硬性】
- #→##→###；### 下不写标签，正文＋空行分段。
- 每 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需）。
- 任何指标定义（如 MTTD/MTTR）独占一行（块级），不内联。
- 教辅口吻；广度优先、讲到懂不做专家纵深。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b L5-12 大主题 12-9 全部小主题（12-9.1 ~ 12-9.4），逐条成节：
- 12-9.1 on-call 与事件管理 — 事件指挥体系（ICS）、角色分工（SRE 书 ch14）。
- 12-9.2 无指责 postmortem 文化 — 对事不对人的复盘（ch15；Workbook ch10）。
- 12-9.3 从失败学习与行动项跟踪 — 复盘产出可执行改进并追踪闭环。
- 12-9.4 响应指标 — MTTD/MTTR 等衡量恢复效率。

【多来源比对（强制）】
- 每项 ≥2 独立源、优先一手（SRE 书 ch14/ch15、Workbook ch9/ch10）；二手打 ⚠。
- 冲突两边都记、点明分歧（如 MTTR 的不同定义口径）。
- 不编造 MTTD/MTTR 等指标口径，须比对或标「待核」。
- 章节末集中列来源与时效，标核实日期。

【权威锚点清单】
- Google《SRE》 ch14/ch15 sre.google/sre-book/table-of-contents/ — 强·一手。
- 《SRE Workbook》 ch9/ch10 sre.google/workbook/table-of-contents/ — 强·一手。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：4 小主题，预期 1 份。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题10 发布工程与金丝雀发布 — 报告prompt

【任务】
为「L5-12·大主题10 发布工程与金丝雀发布」产出符合 report-format v3 的报告。先读 report-format.md、brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。跨课分账：本主题=发布决策；vs L4-06 CD（流水线机制）、vs L5-13 部署（编排落地），三段分别处理，不越界。

【v3 格式硬性】
- #→##→###；### 下不写标签，正文＋空行分段。
- 每 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需）。
- 任何公式独占一行（块级），不内联。
- 教辅口吻；广度优先、讲到懂不做专家纵深。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b L5-12 大主题 12-10 全部小主题（12-10.1 ~ 12-10.4），逐条成节：
- 12-10.1 发布工程原则 — 自服务、快速、可复现、一致（SRE 书 ch8）。
- 12-10.2 金丝雀发布机制 — 小流量灰度＋指标对比（Workbook ch16）。
- 12-10.3 错误预算驱动发布决策 — 预算耗尽则冻结发布（回指本课大主题2）。
- 12-10.4 渐进式交付与自动回滚 — 分阶段放量与异常自动回退（跨 L4-06 CD、L5-13 部署）。

【多来源比对（强制）】
- 每项 ≥2 独立源、优先一手（SRE 书 ch8、Workbook ch16）；二手打 ⚠。
- 冲突两边都记。
- 不编造金丝雀放量比例/阶段等具体数值，须比对或标「待核」。
- 【工具演进】渐进式交付工具（如 flagger/argo rollouts 之类）若引，标「⚙演进快·锚版本」＋核实日期。
- 章节末集中列来源与时效。

【权威锚点清单】
- Google《SRE》 ch8 sre.google/sre-book/table-of-contents/ — 强·一手。
- 《SRE Workbook》 ch16 sre.google/workbook/table-of-contents/ — 强·一手。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：4 小主题，预期 1 份。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

```text
[P1] L5-12·大主题11 过载处理与级联失败 — 报告prompt

【任务】
为「L5-12·大主题11 过载处理与级联失败」产出符合 report-format v3 的报告。先读 report-format.md、brief.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。跨课提示：与 L5-11 容错工程模式重叠，本主题从运维/止血视角讲。

【v3 格式硬性】
- #→##→###；### 下不写标签，正文＋空行分段。
- 每 ### 内容项：核心说明（必需）＋ 充分辅助说明（面向初学者、必需）。
- 排队论相关公式/关系（如利用率与延迟关系）独占一行（块级），不内联。
- 教辅口吻；广度优先、讲到懂不做专家纵深。

【小主题清单（## 章节）与应覆盖内容项】
下游读 round3b L5-12 大主题 12-11 全部小主题（12-11.1 ~ 12-11.4），逐条成节：
- 12-11.1 限流 rate limiting — 客户端/服务端配额保护容量（SRE 书 ch21）。
- 12-11.2 降载 load shedding — 过载时优先丢弃低价值请求。
- 12-11.3 级联失败机理 — 重试放大/资源耗尽/雪崩（ch22）。
- 12-11.4 排队论直觉与容量余量 — 利用率逼近 1 时延迟爆炸，需留余量（Workbook ch11）。

【多来源比对（强制）】
- 每项 ≥2 独立源、优先一手（SRE 书 ch21/ch22、Workbook ch11）；二手打 ⚠。
- 冲突两边都记、点明分歧。
- 不编造排队论数值/利用率阈值，须比对或标「待核」；排队论直觉讲到初学者懂即可，不做专家级推导。
- 【SLO 计算/工具 demo（可选）】可用 Python 演示利用率→排队延迟放大的数量级直觉，贴脚本＋真实输出＋环境串；仅辅助，不替代多来源比对。
- 章节末集中列来源与时效。

【权威锚点清单】
- Google《SRE》 ch21/ch22 sre.google/sre-book/table-of-contents/ — 强·一手。
- 《SRE Workbook》 ch11 sre.google/workbook/table-of-contents/ — 强·一手。

【粒度判定（先判再写）】
先判"1 份 or 拆 N 份 + 理由"：4 小主题，预期 1 份。

【落盘】
Write 到 study/cs-kb/findings/035-observability-sre.md（拆分则 -a/-b）。不残留工具标签/控制字符。
```

共 11 条 prompt
