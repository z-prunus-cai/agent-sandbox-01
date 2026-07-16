# 云原生·数据工程·应用生态与开源治理 全景情报简报

**情报周期**：2026年6月下旬 — 2026年7月7日（以近48小时动态为核心，叠加仍在发酵的关键事件全景）

---

## 🔴 Tier 1：核心突破、重大变革与范式巨震

### 1. GitOps控制面信任崩塌：Argo CD repo-server未授权RCE十八个月未修复，供应链信任链路整体失守
`[Breaking Changes]` `[开源协议变更]`

**事件/架构全景**：法国安全公司Synacktiv于7月1日公开披露一条Argo CD完整攻击链——repo-server组件对外暴露的内部gRPC服务`GenerateManifest`完全无鉴权，攻击者只需能触达内部网络端口，即可构造请求操纵Kustomize/Helm选项，令攻击者控制的仓库代码在repo-server主机上执行。该漏洞早于2025年1月即已上报给Argo CD维护团队，十八个月过去仍未获得正式补丁，且至披露时尚未分配CVE编号。Synacktiv已构建名为`argo-cdown`的自动化利用工具验证全链路可行性，出于给防御方留出窗口暂缓公开发布。

**底层机制/演进逻辑分析**：完整攻击链条为"repo-server RCE→读取环境变量中的Redis密码→连接Argo CD Redis缓存投毒部署数据→下一次自动同步时把攻击者指定的工作负载下发到集群"，本质是把GitOps"声明式配置即信任源"的核心假设武器化：repo-server与Redis之间的内部流量此前被隐式视为可信区域，未纳入零信任边界。这与Tier2中Argo CD v3.5引入内部mTLS的动作形成鲜明互文——安全社区与项目维护方事实上已默认GitOps控制面组件间流量长期缺乏纵深防御。

**生产架构影响与迁移指南**：由于官方补丁尚未发布，唯一有效缓解手段是启用Argo CD自带但默认关闭的NetworkPolicy清单，将repo-server与Redis端口访问严格限制在Argo CD自身组件范围内；所有生产集群应立即审计Helm Chart部署是否已启用该策略。技术团队应将GitOps控制面（Argo CD/Flux等）正式纳入与IAM、密钥管理系统同等级别的"零层级"（tier-zero）资产保护范围，在下一轮安全评审中加入repo-server网络隔离项，而非仅关注RBAC与Git签名校验等传统防线。

---

### 2. Redis/Valkey许可证内战延烧：AGPLv3回归叠加云厂商站队，数据基础设施选型进入断代分裂期
`[开源协议变更]` `[存储引擎重构]`

**事件/架构全景**：Redis Ltd.在此前SSPL→BSL→（2025年5月）回归AGPLv3的反复后，目前为Redis开源版同时提供RSALv2、SSPLv1、AGPLv3三种许可证选项，试图以"重新拥抱OSI批准协议"挽回社区信任；与此同时Linux基金会治理的Valkey（AWS/Google主导）已成为AWS ElastiCache默认内存存储引擎，定价较Redis OSS低约20%（较MemoryDB低约30%），企业采用率与PR活跃度已显著超越Redis本身，Valkey 9.1于5月发布，Docker镜像两年内拉取量突破1亿次（同比增长17倍）。ScyllaDB同期在2026.2版本中把Alternator（DynamoDB兼容API）的语义向量搜索能力升级至GA，为DynamoDB工作负载用户提供无需额外接入向量数据库的原生路径，进一步分流内存/NoSQL选型格局。

**底层机制/演进逻辑分析**：这是一次典型的"协议反复不能挽回生态位"案例——许可证文本层面的多次回摆（BSL→AGPL）未能逆转开发者与云厂商already完成的路径迁移，Valkey借助Linux基金会中立治理与云厂商深度绑定定价，已把"许可证信任"转化为事实上的"生态位锁定"。这一断层直接投射到数据架构选型：企业级缓存/内存数据层的选型决策权重已从"技术特性对比"让渡为"许可证血统与云厂商定价捆绑"的组合博弈，且分裂后的两条技术分支（Redis AGPL版 vs. Valkey Linux基金会版）在协议兼容性与商业支持体系上正持续拉大差距。

**生产架构影响与迁移指南**：仍在使用Redis 7.2（2026年2月28日已EOL）的团队应尽快在Redis新许可证矩阵与Valkey之间做出明确技术选型决策，而非依赖历史惯性；已上云且使用AWS/GCP托管服务的团队，应重新核算ElastiCache Valkey相对于自建Redis集群的长期成本优势（已有企业案例报告年缓存成本降低60%）；技术选型委员会应将"许可证治理主体是否为中立基金会"正式纳入数据基础设施供应商尽调清单的量化评分项。

---

### 3. Kubernetes调度层与入口层双重范式迁移：DRA设备级调度迈向Beta，Ingress-NGINX强制退役倒逼Gateway API成为唯一入口标准
`[云原生大版本]` `[Breaking Changes]`

**事件/架构全景**：Kubernetes v1.36"Haru"已交付70项增强（18项Stable/25项Beta/25项新Alpha），User Namespaces、Mutating Admission Policies、细粒度Kubelet API鉴权同步GA；面向AI/GPU工作负载，Resource Health Status与Device Taints and Tolerations双双进入Beta——后者首次允许DRA驱动通过ResourceSlice将"设备级"（而非节点级）故障标记为污点，集群管理员可创建DeviceTaintRule对象实现设备粒度的调度隔离。与此同时，Ingress-NGINX已由SIG Network/安全响应委员会于3月24日正式宣布退役，此后不再提供任何维护、Bug修复或CVE补丁；Gateway API被确立为官方指定的唯一后继标准，官方迁移工具Ingress2Gateway 1.0已于3月发布，社区自发的替代方案"InGate"未能获得足够牵引力，同步进入退役流程。v1.37特性冻结定于7月9-10日，GA目标8月26日。

**底层机制/演进逻辑分析**：两条主线共同指向调度与流量入口两个关键面的"精细化收编"——DRA的设备级污点/容忍模型，是把GPU/加速器资源从静态device-plugin二元分配模型升级为可声明、可分区、故障可隔离的动态模型，直接回应AI训练/推理场景下硬件故障隔离粒度不足的痛点；Ingress-NGINX的强制退役则是Kubernetes首次以"停止一个长期事实标准项目"的方式，用行政力量而非纯技术优势推动生态迁移，标志着入口层标准化进程从"多方案并存竞争"进入"官方指定单一路径"阶段，直接影响上层应用的分发与流量接入架构。

**生产架构影响与迁移指南**：所有仍在生产环境运行Ingress-NGINX的团队应将迁移列为高优先级——该项目已零安全响应能力，任何新发现的CVE都不会再获得修复，应立即使用Ingress2Gateway 1.0评估迁移路径并制定切换排期；使用GPU/加速器资源池的AI基础设施团队应评估从legacy device-plugin迁移至DRA驱动的路径，尤其是多租户GPU共享与MIG切分场景可显著受益于设备级污点隔离；计划升级至v1.37的集群应在GA前完成特性冻结清单核对，避免踩中新一轮Beta特性引入的行为变更。

---

### 4. AI资本重塑开源基金会治理版图：Rust基金会接纳OpenAI铂金会员引发治理俘获质疑，Linux基金会/Apache同步构建AI原生治理框架
`[基金会治理]` `[开源协议变更]`

**事件/架构全景**：Rust基金会6月17日宣布OpenAI以60万美元总投入（会员费+额外维护者资助）加入成为铂金会员，资金定向投入Rust Project Goals、Rust Innovation Lab及核心依赖库维护者直接资助，`cargo-semver-checks`维护者Predrag Gruevski同步以OpenAI代表身份加入基金会理事会——社区评论者Bryan Lunduke公开质疑此举构成"花钱买理事会席位"的治理俘获先例。同期，Apache基金会将Magpie（"厂商中立AI Agent辅助维护"框架，覆盖安全问题处理、PR/Issue分诊、贡献者指导）升级为顶级项目；Linux基金会牵头联合AWS、Anthropic、Cisco、Google、IBM、Microsoft/GitHub、NVIDIA、OpenAI、Red Hat等近20家机构发起Akrites项目，建立跨项目共享安全事件响应团队（SIRT）与标准化协调漏洞披露（CVD）流程；Linux基金会牵头的Agentic AI Foundation（AAIF，锚定MCP协议）成立不足四个月即吸纳170家成员机构，增速"超过CNCF同期两倍"，MCP协议"2026-07-28"候选版本已明确协议层将转为无状态设计，取消此前Streamable HTTP强制的会话粘滞要求。

**底层机制/演进逻辑分析**：四条线索共同勾勒出同一场结构性转移——AI实验室（OpenAI、Anthropic）正从"开源生态的消费者"转变为"开源基础设施治理体系的直接出资方与决策参与者"，其资金规模与渗透速度（AAIF四个月即超越CNCF同期体量）远超传统基金会治理演化节奏。Apache Magpie与Akrites代表基金会侧对这一趋势的两种制度化应对：前者用"六轴厂商中立"设计（LLM后端/Agent运行时/代码托管等互不绑定）主动约束单一AI厂商的路径依赖风险，后者用跨基金会共享安全响应机制应对AI辅助漏洞挖掘导致的"发现效率提升、修复效率未同步提升"的结构性风险积压。

**生产架构影响与迁移指南**：技术选型委员会评估任何基金会主导项目时，应将"核心资金来源是否集中于单一或少数AI厂商"纳入供应链治理风险评估维度，避免因资金结构过度集中而形成隐性厂商锁定；已经或计划引入AI Agent参与内部开源维护流程的团队，可直接参考Apache Magpie的六轴厂商中立设计模板；采用或评估MCP协议的团队应关注7月28日候选规范定稿后的无状态化改造，评估现有基于会话粘滞的负载均衡架构是否需要相应简化。

---

### 5. AI对开源可持续性的双重反噬：curl停摆漏洞报告受理一个月抗议AI垃圾情报，TrapDoor投毒行动首次系统性攻击AI编程助手信任链
`[Breaking Changes]` `[开源协议变更]`

**事件/架构全景**：curl核心维护者Daniel Stenberg宣布7月1日至8月3日期间暂停通过HackerOne受理任何漏洞报告（"Summer of Bliss"），直接原因是curl目前平均每18小时即收到一份AI生成的虚假/垃圾漏洞报告（AI普及前约为每周一份），而每份报告仍需团队仅有的7名维护者人工复现、分诊、修复与协调披露，此前curl原有漏洞悬赏计划已于1月因同类问题被迫关停，改用GitHub报告渠道试验后仍以失败告终；XML解析库libexpat维护者同期宣布跟进类似暂停。与此同时，Socket.dev披露"TrapDoor"跨生态供应链投毒行动——覆盖npm（21个包）、PyPI（7个）、crates.io（6个）共34个包、384个恶意版本，其核心载荷首次采用在仓库中植入含零宽度Unicode隐藏指令的`.cursorrules`与`CLAUDE.md`文件，专门诱导AI编程助手解析并执行伪装成"安全扫描"的操作，进而窃取SSH密钥、云凭据、浏览器数据与加密钱包。

**底层机制/演进逻辑分析**：两起事件构成AI对开源生态可持续性冲击的一体两面——curl事件揭示"AI生成内容的生产成本趋近于零"与"人工验证成本保持不变"之间的结构性剪刀差，正在系统性拖垮开源核心基础设施维护者的可持续参与能力（据行业调研，60%开源维护者无偿工作，44%将倦怠列为离开首要原因）；TrapDoor行动则标志着攻击面正式从"人类审阅代码"扩展至"AI Agent解析仓库配置文件"，利用AI编程助手对`.cursorrules`/`CLAUDE.md`等约定俗成配置文件的高信任度，把提示注入武器化为供应链攻击的新入口——这对依赖AI Agent自动化处理PR、Issue、依赖升级的团队构成此前未被建模的新型威胁面。

**生产架构影响与迁移指南**：任何在CI/CD或本地开发环境中允许AI编程助手自动读取并执行仓库内配置文件（`.cursorrules`、`CLAUDE.md`、`AGENTS.md`等）指令的团队，必须将这些文件纳入代码评审与静态扫描范围，对隐藏字符（零宽度Unicode等）进行强制检测；依赖curl/libexpat等核心库的团队应对7月至8月的漏洞响应窗口空档期提高自身依赖扫描与外部情报订阅优先级；企业安全团队应评估当前是否已对AI生成的安全报告/PR贡献建立独立于人工报告的分级验证流程，避免用同一套人工产能吞吐AI规模化产出的信息噪声。

---

## 🟡 Tier 2：关键演进、生产工程实践与生态风向

### 6. 云/AI巨头并购潮重塑Postgres与前端工具链底座：Snowflake收购Crunchy Data、Databricks深化Neon整合、Cloudflare收购VoidZero
`[生态政策调整]` `[存储引擎重构]`

**核心增量**：Snowflake以约2.5亿美元收购Crunchy Data打造"Snowflake Postgres"，明确对标Databricks对Neon（约10亿美元收购）的布局，Neon数据显示超80%新建数据库实例由AI Agent自动创建而非人工，实例创建延迟低于500毫秒；Cloudflare6月4日收购VoidZero（Vite/Vitest/Rolldown/Oxc团队，创始人为Vue.js/Vite作者Evan You），承诺全线工具保持开源与厂商中立，并出资100万美元设立Vite生态基金，Vite周下载量已破1.3亿。**核心工程思想**：三笔交易共同标志着"数据平台层"与"前端工具链层"正被云/AI巨头同步并购收编为战略基础设施，Agent自动化创建数据库实例的行为模式（Neon）反过来倒逼底层Postgres架构必须原生适配毫秒级实例创建与销毁节奏。**落地行动指南**：使用Neon/Crunchy的团队应关注收购后定价与SLA条款变化窗口期；重度依赖Vite工具链的前端团队应跟踪生态基金分配透明度，评估长期厂商中立承诺的实际履约情况。

### 7. OpenTelemetry晋级CNCF最高治理成熟度：可观测性标准范式在整条技术栈完成事实标准化
`[基金会治理]` `[GA正式版]`

**核心增量**：OpenTelemetry于5月正式毕业为CNCF Graduated项目并在7月持续获得行业分析关注，JS API包过去12个月下载量超36亿次，Python API包超13亿次，项目活跃度在240余个CNCF项目中仅次于Kubernetes本身，毕业前提是核心组件（含Collector）通过第三方独立安全审计与正式治理评审；opentelemetry-collector-releases已迭代至v0.155.0（7月4日）。**核心工程思想**：治理毕业不仅是荣誉认证，更是采购决策中的隐性筛选条件——它意味着埋点标准具备长期厂商中立的安全响应保障，直接影响Envoy AI Gateway、eBPF可观测性栈（Cilium+Hubble、Tetragon、Grafana Beyla）等下游项目的选型信任基础。**落地行动指南**：仍使用厂商私有埋点SDK的团队应将迁移至OTel原生埋点纳入年度技术债务清单；评估可观测性平台的团队应将"是否原生支持OTel协议而非仅做协议转换"作为长期选型硬指标。

### 8. 查询引擎存储层持续竞速：ClickHouse 26.6引入假设性索引与级联物化视图，ScyllaDB 2026.2默认Trie索引实现3倍吞吐提升
`[存储引擎重构]` `[GA正式版]`

**核心增量**：ClickHouse 26.6（7月3日）新增假设性跳数索引（可零成本评估索引收益后再决定是否真实创建）、级联可刷新物化视图，并将插入去重的判定粒度由"分区/Part级"改为"整个插入批次级"（行为破坏性变更）；ScyllaDB 2026.2（6月29日）将基于Trie的SSTable索引格式设为默认，官方基准显示吞吐提升最高达3倍且延迟下降，同时DynamoDB兼容API（Alternator）新增原生语义向量搜索扩展。**核心工程思想**：两者均体现"降低试错成本"与"存算路径原生融合AI检索能力"两条并行工程主线——假设性索引让DBA无需真实建索引即可评估收益，Trie索引格式重构则是存储引擎层面为应对海量小对象随机读而做的结构性优化。**落地行动指南**：ClickHouse升级前需重新评估依赖旧去重粒度的写入幂等逻辑是否受影响；DynamoDB工作负载团队可评估ScyllaDB Alternator语义向量搜索作为免于引入独立向量数据库的替代路径。

### 9. IaC工具链份额易位：OpenTofu新建工作区占比升至76%，Terraform 1.15与Crossplane v2.3同步补齐组合式基础设施能力
`[平台工程实践]` `[生态政策调整]`

**核心增量**：Scalr数据显示OpenTofu在2026年上半年运行占比已达约63%、新建工作区占比从56%升至76%，Linux基金会治理与CNCF项目身份进一步巩固其中立地位；HashiCorp同期发布Terraform 1.15，新增动态模块源、变量/输出正式弃用机制、原生类型转换函数及Windows ARM64支持，被视为对OpenTofu既有领先特性的补课；Crossplane v2.3引入`function-kro`组合函数，允许直接复用kro的YAML+CEL编排语法。**核心工程思想**：IaC工具链的竞争焦点已从"基础功能对齐"转向"组合式基础设施编排语法的互操作性"，`function-kro`降低了跨工具编排逻辑迁移的学习成本。**落地行动指南**：新建IaC项目团队应优先评估OpenTofu作为默认选型，尤其在关注长期厂商中立治理的场景；存量Terraform用户可借Terraform 1.15的变量弃用机制系统清理历史技术债。

### 10. 服务网格入口层出现强制性破坏性变更：Cilium 1.20预览版TLSRoute API迁移不可回退，Envoy AI Gateway同期转正GA
`[Breaking Changes]` `[GA正式版]`

**核心增量**：Cilium 1.20（预计7月底GA，当前为1.20.0-pre.4）文档确认Gateway API升级至v1.5.1，其中TLSRoute资源由v1alpha2迁移至v1，需要手工迁移且无自动兼容路径，同时新增基于本地网卡的BGP接口宣告能力；Envoy AI Gateway已于6月23日转正GA，v1beta1控制面API纳入稳定性保障范围，原生支持16家LLM提供商路由、MCP网关及多模态/音频端点。**核心工程思想**：服务网格层正同步承接两类新增职责——网络协议层持续向Gateway API标准收敛，同时新增LLM流量路由这一全新的"AI原生数据面"能力，二者共同扩大了服务网格在AI基础设施中的入口地位。**落地行动指南**：使用Cilium管理TLSRoute的团队须在升级前规划手工迁移排期并在预发环境充分验证；已大规模接入多家LLM Provider的团队可评估用Envoy AI Gateway统一收敛现有分散的LLM网关脚本方案。

### 11. 移动应用分发监管持续收紧与放宽并行：Apple App Store审核准则加严 × 日本MSCA驱动iOS Notarization × Google Play目录默认开放开发者需主动opt-out
`[生态政策调整]` `[Breaking Changes]`

**核心增量**：Apple 6月8日更新App Store审核准则与开发者协议，收紧4.3(b)条款打击"平庸、低质量或克隆应用"，并因日本《移动软件竞争法》（MSCA）要求为第三方应用商店分发引入iOS Notarization机制；Google Play执行Epic诉讼和解条款，6月30日起佣金上限降至20%（原30%），并已于6月22日通知开发者，除非在7月22日前主动opt-out，第三方Android应用商店将默认可获取其应用列表元数据。**核心工程思想**：分发生态呈现"支付与商店层持续开放、内容与身份准入层同步收紧"的分裂路径，两大平台的合规动作均由区域性监管（欧盟DMA、日本MSCA、美国反垄断和解）驱动而非自主战略调整。**落地行动指南**：面向日本市场分发的iOS应用需评估Notarization合规成本；所有Google Play开发者应在7月22日前登录Play Console核实Catalog Settings，避免应用元数据被动默认开放给未经审核的第三方渠道。

### 12. AI编码助手计费与安全治理同步升级：GitHub Copilot按量计费全面落地，Actions安全路线图进入强制执行窗口
`[生态政策调整]` `[Breaking Changes]`

**核心增量**：GitHub Copilot全线套餐已于6月1日转为"订阅费不变+AI Credits按Token消耗"的混合计费模式，额度耗尽后不再自动降级至低阶模型；此前因基础设施原因暂停的新用户注册已于6月16日起分批重开。GitHub Actions侧同步推进安全路线图：`actions/checkout` v7+已在所有受支持主版本回填对`pull_request_target`/`workflow_run`上下文中fork PR代码拉取的阻断，自托管Runner最低版本强制时间表已确认7月与9月两个执行节点，工作流执行策略（Workflow Execution Policies）进入组织级公测。**核心工程思想**：AI编码助手的资源计量与CI/CD供应链安全强化正同步推进，反映出"AI消耗需要精细计量、AI触发的自动化需要更严格执行策略"已成为同一治理逻辑的两面。**落地行动指南**：企业应提前为Copilot Token消耗设置预算告警，避免额度耗尽阻断日常研发；使用自托管Runner的团队须核对当前版本是否满足7/9月强制时间表要求，避免届时批量失效。

### 13. 跨端框架集中迭代：Swift官方登陆Android、Kotlin 2.4并发GC与Swift Export、Compose First战略与React Native旧架构彻底移除
`[GA正式版]` `[平台工程实践]`

**核心增量**：Swift 6.3随官方Swift SDK for Android发布，首次提供官方渠道跨iOS/Android共享Swift代码，Swift-Java互操作包同步支持异步/抛出函数跨语言调用；Kotlin 2.4.0（6月3日GA）默认GC切换为并发标记清除，去虚拟化场景内存占用最高降低50%；Google正式宣布Android UI开发进入"Compose First"战略阶段；React Native自0.83起（Expo SDK 55同步）彻底移除Legacy桥接架构，新架构下冷启动提速43%、渲染提速39%、内存降低26%。**核心工程思想**：四大主流跨端/原生框架在同一窗口期集中完成"架构收敛"——旧架构/旧运行时的彻底移除标志着各生态均已完成"新架构从可选到唯一"的临界点跨越。**落地行动指南**：仍停留在React Native Legacy架构或Kotlin旧GC默认值的团队应将迁移列为2026下半年技术债务清单首位，避免与后续版本形成断层式不兼容。

---

## 🟢 Tier 3：日常风向与情报速递

- **Kubernetes v1.37**特性冻结定于7月9-10日，代码/测试冻结7月22-23日，GA目标8月26日；已知CVE包括CSI NFS/SMB驱动路径穿越（CVE-2026-3864/3865）、Go HTTP/2传输层无限循环（CVE-2026-33814）及SPDY帧解析内存放大（CVE-2026-35469）。
- **etcd v3.7.0-rc.0**发布，带来长期呼吁的RangeStream特性、彻底移除遗留v2store组件及大规模读性能优化。
- **HashiCorp Vault 2.0**成为IBM并购后首个大版本，新增工作负载身份联邦替代静态凭据；HCP Vault Secrets SaaS已于7月1日彻底停服，强制用户迁移至社区版或HCP Vault Dedicated。
- **Percona Operator for MySQL 1.2.0**（7月3日）新增`PerconaServerMySQLClusterSet` CRD实现声明式跨区域复制拓扑管理，及基于PVC用量阈值的存储自动扩容。
- **PostgreSQL 18.4/17.10/16.14/15.18/14.23**安全维护版本合计修复11项安全漏洞与60余项缺陷；PostgreSQL 19 Beta 1已于6月4日发布，GA预计9月。
- **Kyverno**由CNCF沙箱晋升为孵化项目；**Karmada**（多集群编排）被TOC接纳为孵化项目；**Fluid**（AI/ML数据编排）3月晋升孵化项目。
- **Envoy Gateway v1.8.2**（7月1日）为1.8系列补丁发布，同步修复Gateway API安全升级相关ValidatingAdmissionPolicy资源打包方式，此前变更曾导致Flux误将其识别为CRD而产生管理冲突。
- **Istio 1.28.10**（7月1日）修复多项Envoy上游CVE及Istio自身安全问题；最新稳定小版本为1.30.2。
- **Apache Flink 2.2.1**（5月）修复44项缺陷；**Apache Pulsar**同期以约30个PIP提案的活跃节奏推进5.0.0-M1"可扩展Topics API"开发。
- **向量数据库**：Milvus持续以自研Woodpecker WAL替代Kafka/Pulsar依赖，并预览基于"Milvus 3.0内核"的Vector Lakebase；Qdrant 1.17/1.18新增相关性反馈与TurboQuant量化；Pinecone于7月2日发布面向AI Agent知识管理的"Nexus"。
- **Node.js**宣布release节奏改革，自Node 27起由每年两个大版本改为一个，取消奇偶LTS区分；Node 20已于4月30日EOL。
- **Python Packaging Council**首届选举提名将于7月28日启动（依据PEP 772批准的治理流程），与PSF理事会选举同期举行。
- **EU Cyber Resilience Act**首批强制条款（24小时内向ENISA上报被主动利用漏洞）将于9月11日生效，仅商业化运营的开源项目在管辖范围内。
- **VS Code 1.127**（7月1日）Browser Tools for Agents转正GA，Agent可自主打开页面、截图并验证自身产出。
- **crates.io**新增基于RustSec的Security标签页，收紧CI令牌明文存储与部分高风险Trigger类型的Trusted Publishing支持。
- **Docker Hub**订阅体系重构持续发酵：免费个人层不再包含Build Cloud分钟数，付费层统一打包Scout与Testcontainers额度。
- **Apache Livy**（Spark REST服务）历时多年孵化正式晋级顶级项目。
- **WordPress/Automattic与WP Engine诉讼**推进至motions-to-dismiss听证阶段，W3Techs数据显示WordPress市场份额过去六个月由43.2%降至41.9%（尚无确证因果关联）。
