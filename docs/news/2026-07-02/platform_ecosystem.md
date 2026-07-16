# 云原生平台 · 数据工程 · 应用生态与开源治理情报简报

**统计窗口**:2026-06-24 至 2026-07-02(核心聚焦过去48小时:6月30日–7月2日;因云原生/跨端/开源治理子领域48小时内一手发布密度有限,弹性扩展至8天窗口,窗口外背景信息均已标注具体日期)

---

## 🔴 Tier 1:核心突破、重大变革与范式巨震

### 1. `[移动分发合规黑天鹅]` 最高法院受理苹果诉Epic上诉 vs Google Play"松绑支付权"与Android"锁死安装权"同步生效:三线交织的移动分发权博弈

**事件全景**:美国最高法院于2026年6月30日同意受理苹果对"藐视法庭"裁决的上诉(Apple Inc. v. Epic Games, 25-1311),预计10月开庭期审理。案件源头是2021年加州法官Rogers裁定苹果须放松反引导规则,2025年4月该法官进一步认定苹果虽未违反禁令文字,但通过对外链交易仍收取高额费用(此前报道达27%)违反禁令"精神",构成藐视法庭,第九巡回法院维持原判;苹果此次上诉聚焦"能否仅因违反精神认定藐视"及"禁令效力是否不当扩大至全体开发者"两点核心问题,7月1日苹果已计划请求法院在终审前暂停执行下级法院要求的费率调整程序。与此同时,Google Play已于6月30日在美/英/EEA三地落地Epic v. Google案和解条款:终结十五年30%统一抽成,年收入前100万美元统一10%服务费、超出部分分级抽成20%/25%,继续用Google计费系统者额外加收5%,改用外部支付则免除;依法院强制令,美区应用列表将于7月22日起默认开放给第三方安卓商店(除非开发者主动opt-out)。但Google同步推进的Android Developer Verification已明确强制时间表:9月30日起巴西/印尼/新加坡/泰国的认证设备将阻止安装未验证开发者的应用,2027年扩展至全球所有GMS设备。苹果侧同期已与巴西CADE达成协议(6月18日生效),复制欧盟CTC模式:第三方商店分发免App Store佣金但需缴5%"核心佣金",这已是苹果DMA式合规模板首次系统性复制到欧盟以外司法辖区。

**底层机制/演进逻辑分析**:三条战线共享同一底层逻辑冲突——司法/监管力量正在从"支付权"(苹果反引导条款、Google计费拆分)、"目录权"(Google Play Catalog Access强制开放)两个维度拆解平台方的垄断闭环,但平台方同时在"身份准入权"维度(Android Developer Verification)重建新的控制层。最高法院受理上诉意味着"藐视法庭"这一强执行手段的合法性边界仍未定型,苹果与巴西CADE的协议则揭示平台方正主动将欧盟监管压力下形成的CTC费率模型,作为应对全球类DMA监管浪潮的标准化模板提前部署,以避免陷入逐国单独博弈的被动局面。

**生产架构影响与迁移指南**:订阅制/游戏类App厂商需重建LTV模型下的真实佣金成本,并在7月22日前(Google)完成Catalog Settings的opt-out决策;需密切关注最高法院10月开庭期动向,若裁决推翻藐视法庭认定,苹果外链收费政策可能出现反复,建议保留双路径(内购+外链)技术实现以应对政策不确定性;巴西CADE协议应作为进入其他非欧盟司法辖区(如印度、韩国)的费率谈判参照系提前纳入合规预案。同时须规划"开发者身份验证"流程,7月起可申请面向学生/爱好者的"有限分发账户"(20台设备上限、免政府ID),避免9月30日大限后在四国市场批量安装失败。

---

### 2. `[开源协议武器化清算]` Relicensing浪潮清算年:Valkey碾压Redis、OpenTofu反超Terraform、MinIO因AGPL诉讼自我了断、LibreOffice/OnlyOffice双线爆发治理危机

**事件全景**:RedMonk年度综述给出2023-2025relicensing浪潮的最终判决——云厂商全面获胜,fork项目健康存活且协议"回撤"未能逆转迁移。实证数据:Valkey(Redis的BSD-3-Clause分支)Docker拉取量破1亿次(同比增17倍),已成为Fedora/Ubuntu/Debian/Arch默认内存数据库,AWS ElastiCache默认引擎(较Redis OSS快8%、P99延迟低22%、内存少20%、价格低20%),企业采用率达83%;Redis虽已回撤至AGPLv3,但主要Linux发行版已停止分发。IaC领域,OpenTofu执行次数占比63%、新建workspace占比72%双双反超Terraform,已凭MPL-2.0特批例外进入CNCF沙箱;但HashiCorp/IBM(法务规模堪比中型律所)指控持续升级,已致部分企业因惧怕诉讼风险而悄悄停止贡献。更激烈的案例是MinIO——为向Nutanix/Weka施压将协议改为AGPL,最终纠纷未平反致自身2026年2月12日归档GitHub仓库"自杀",社区随即依AGPL不可撤销条款自主fork"复活"续命。开源办公套件双线爆发危机:LibreOffice主管机构The Document Foundation依"存在法律纠纷"条款清除Collabora(贡献43%代码、史上前十贡献者中7人在内)全部会员资格,被称为"LibreOffice内战";Nextcloud等九家欧洲公司fork的"主权版"Euro-Office与OnlyOffice就AGPLv3第7条款(logo署名义务)爆发跨国法律战,6月9日发布1.0版后争议部分软化。AI模型开源定义之争同步升温:MiniMax M2.7将协议由标准MIT悄然改为自命名"Modified-MIT"(商业使用需书面授权),被批"挂羊头卖狗肉";字符编码库chardet新维护者称"基于Claude大模型从零重写"并将LGPL改为MIT,原作者提出"无权relicense"抗议,引发"AI能否被用作洗白协议工具"的行业级合规担忧。

**底层机制/演进逻辑分析**:七个案例共享同一因果链——商业公司/基金会试图把协议或治理条款当作对抗竞争对手的商业杠杆,但协议/治理决定一旦被下游生态大规模采用即产生不可逆路径依赖(fork不会因原项目"回撤"而回流),而武器化本身又会反噬发起者的社区信任。MiniMax与chardet案例进一步揭示了AI辅助编码时代的新型风险敞口:LLM训练语料的Copyleft代码溯源问题,以及"完全重写"能否规避原始许可证义务,尚无司法先例可循,任何依赖AI进行大规模代码重写/relicense的团队都面临未定性的法律风险。

**生产架构影响与迁移指南**:仍用Redis而非Valkey的团队应评估迁移窗口;IaC选型应默认将OpenTofu纳入候选;任何计划以"协议武器化"或"AI重写relicense"应对竞争/合规压力的团队,都应以MinIO与chardet为负面参照;依赖LibreOffice/Collabora Online的团队需关注两条产品线是否代码分道扬镳;GitHub已上线"开源许可证合规检查"公开预览(6月30日,基于ruleset的依赖许可证策略拦截),企业应尽快接入建立依赖引入前的许可证门禁。

---

### 3. `[AI驱动开源供应链安全治理三重奏]` IBM/Red Hat/Deloitte联手投入50亿美元Project Lightwell + Linux Foundation Akrites计划 + Apache Magpie晋升顶级项目:开源维护模式的AI化重组

**事件全景**:三项独立但高度呼应的治理动作在过去两周密集落地。其一,IBM与Red Hat于5月启动、Deloitte于6月26日以"集成协作方"身份加入的Project Lightwell,投入50亿美元资金及逾2万名工程师打造"企业级开源安全清算所",通过AI驱动漏洞分析覆盖持续可见性发现、上下文优先级排序、机器速度修复、生态信任合规四大板块,早期采用者含美国银行、纽约梅隆银行、花旗、高盛、摩根大通等金融机构,并与Palo Alto Networks的Prisma虚拟补丁技术整合。其二,Linux Foundation旗下Alpha-Omega计划于6月25日发起Akrites,联合AWS、Anthropic、Chainguard、Google、IBM、Microsoft/GitHub、NVIDIA、OpenAI、Red Hat、Rust Foundation等20余家横跨云厂商/AI实验室/金融机构/安全公司的组织,建立共享安全事件响应团队(SIRT)与统一协调漏洞披露流程,承诺为无人维护的软件包充当"最后维护者"。其三,Apache Software Foundation于6月30日宣布Apache Magpie正式毕业为顶级项目——这是一套面向开源维护者的"智能体辅助仓库维护"基础设施,覆盖安全问题端到端处理、issue/PR分诊、贡献者对话式指导、committer技能训练四大领域,设计原则明确要求隐私安全优先、LLM供应商中立、拒绝昂贵订阅绑定,已在Apache Airflow试点。

**底层机制/演进逻辑分析**:三者共同指向同一结构性判断——AI辅助漏洞挖掘工具正指数级降低攻击者发现0-day的门槛(此前需数周才能定位的漏洞现在AI几分钟即可发现),而传统依赖分散志愿维护者响应的开源安全模型已结构性滞后于这一威胁增速。三条路径呈现出鲜明的治理光谱分化:Lightwell代表大型科技/咨询巨头以商业订阅模式介入,资金规模空前(50亿美元、2万工程师);Akrites代表非营利基金会牵头、行业协同共享响应基础设施的路径;Magpie则代表基金会将"AI智能体参与开源维护"标准化、制度化为项目治理的常规组成部分,三者构成了同一威胁应对逻辑下的商业化清算所、协同防御网络、制度化工具三层解法,可能重塑未来开源漏洞响应的权力结构与资金流向。

**生产架构影响与迁移指南**:使用关键OSS组件(尤其C/C++内存不安全的基础设施软件)的企业应评估接入Lightwell(商业订阅路径)或关注Akrites后续公布的SIRT披露协调窗口机制(免费协同路径)两条选项;安全团队需重新评估"AI辅助代码审计"引入内部供应链安全流程的路径与信任边界;依赖Apache生态项目的团队应关注Magpie在更多顶级项目(继Airflow后)的试点进展,评估AI智能体参与issue分诊/PR审查对项目响应速度与代码质量的实际影响,并提前建立对AI辅助维护决策的人工复核机制。

---

### 4. `[存储引擎断代/云数据库存算分离三国杀]` Snowflake Postgres/Databricks Lakebase/Azure HorizonDB三强下注"Postgres化"存算分离,叠加PostgreSQL 19图查询原生化与Iceberg 1.11加密模型重构

**事件全景**:过去一个季度内,三大数据平台巨头同步完成"Postgres引擎+自定义存储层+存算分离"产品布局:Snowflake以约2.5亿美元收购Crunchy Data推出Snowflake Postgres(GA,被评价为三者中"最像原生Postgres");Databricks基于收购的Neon引擎+Mooncake推出Lakebase(AWS上GA,Azure公测);Azure推出邀请制预览的HorizonDB(号称亚毫秒级多可用区提交延迟)。三者共同锚点是"计算存储分离+协议兼容PostgreSQL+面向Agentic AI工作负载"。与此同时PostgreSQL 19 Beta 1(6月4日,GA计划9月)是近年功能密度最高的大版本:原生SQL/PGQ图查询标准支持(无需扩展即具备图遍历能力)、`REPACK`命令实现非阻塞表膨胀整理(终结"表膨胀只能停机整理"痛点)、并行autovacuum、`pg_plan_advice`执行计划固化扩展。湖仓层面,Apache Iceberg 1.11.0(5月19日GA)完成安全模型重构——引入信封加密(每元数据文件独立DEK,再由KMS托管主密钥加密),REST Catalog新增服务端扫描规划(元数据计算从查询引擎下沉至目录服务)。向量检索侧,Amazon S3 Vectors正式GA后单索引容量提至20亿向量(较预览期提升40倍),宣称较专用向量数据库降低总成本最高90%。

**底层机制/演进逻辑分析**:云厂商自建OLTP引擎的战略转向,本质是把"Postgres协议兼容性"作为新的分发杠杆——不再要求企业重写应用代码即可获得云原生存算分离带来的弹性伸缩收益,直接冲击CockroachDB/YugabyteDB/RDS在企业级Postgres兼容层的市场定位。而Iceberg加密模型下沉到规范层,叠加S3 Vectors把向量能力做成对象存储的原生数据类型而非独立数据库品类,共同印证同一判断:2026年数据基础设施竞争的战场已从"能不能做"转移到"元数据引擎效率"与"存储层原生集成深度",专用产品的差异化窗口正被压缩到"超低延迟+复杂过滤+多租户隔离"等狭窄场景。

**生产架构影响与迁移指南**:重度使用Iceberg表格式的团队需评估1.11加密模型升级对现有Catalog/查询引擎(Snowflake/Databricks/ClickHouse/Doris)兼容性的影响,提前规划双写或灰度升级;向量数据库选型应重新计算"专用产品 vs 对象存储原生方案"的TCO分界点,长尾低频查询场景优先考虑S3 Vectors等原生集成;正在做OLTP云迁移评估的团队,应将Snowflake Postgres/Lakebase/HorizonDB的"Postgres兼容+存算分离"新范式纳入选型对比,但需注意HorizonDB此前公测期即曝出CVSS 10.0认证绕过漏洞(已修复),新兴产品建议延后至少一个补丁周期再考虑生产接入。

---

### 5. `[云原生底座性能范式收敛]` 从"功能竞赛"转向"资源效率"竞赛:Kubernetes调度器增量计数实现15-30倍吞吐提升,Linkerd控制面内存降低85%

**事件全景**:Kubernetes v1.37.0-alpha.2(6月25日)中,SIG-Scheduling修复了kube-scheduler处理带PVC挂载Pod时的核心性能瓶颈——原实现每个调度周期都对所有节点重算PVC引用计数,导致吞吐随已调度Pod数量增长"逐渐衰减";修复后改为仅对两次调度快照间的变化量做增量更新,PR基准数据显示10万Pod、预置PVC挂载场景下吞吐从10-20 pods/s提升至300+ pods/s,约15-30倍提升。同期Linkerd 2.20(6月23日GA)重构了destination controller的内部集群状态结构,在Pod高频churn场景下将控制面内存占用降低最多85%,原生Kubernetes sidecar容器编排正式GA并成为默认方式,同时引入限速感知负载均衡。Prometheus同步发布v3.13.0(7月1日)并首次标记为长期支持(LTS)版本,带来大小写不敏感正则匹配2倍提速、chunk填充优化带来12-15%查询提速、直方图WAL解码器内存分配降低最多50%,但同时包含PromQL的`min()`/`max()`时长函数更名为`min_of()`/`max_of()`等破坏性变更。CRI-O v1.36.2/v1.34.10(7月2日)同步修正GOMAXPROCS注入逻辑,确保容器实际获得的GOMAXPROCS至少为CPU request的两倍,避免节点空闲时Go运行时因goroutine数量受限而节流。

**底层机制/演进逻辑分析**:这四项发布看似分属调度、服务网格、可观测性、容器运行时四个独立子系统,但共享同一条底层演进逻辑——在K8s生态核心控制面组件功能已趋饱和的背景下,2026年中期的竞争焦点全面转向"同等资源下承载更大规模"的效率工程:调度器从O(节点数×Pod数)的全量重算降至增量更新,服务网格从O(Pod数)的全量状态维护降至内存精简结构。这一趋势与AI/批处理工作负载对集群规模、Pod churn频率提出的更高要求直接相关(Kubernetes Gang Scheduling KEP-4671在v1.36进入Beta后v1.37持续打磨minCount弹性伸缩能力)。

**生产架构影响与迁移指南**:大规模K8s集群(万级以上Pod)运维团队应关注v1.37 GA(预计8月26日)后PVC密集型工作负载的实测收益,并规划升级窗口;已部署Linkerd的团队可评估2.20版本能否让服务网格下沉到此前因控制面内存开销过高而放弃mesh化的边缘/资源受限集群;Prometheus用户需在LTS版本升级前逐条核对PromQL函数改名等破坏性变更对现有告警规则/Dashboard的影响。

---

## 🟡 Tier 2:关键演进、生产工程实践与生态风向

**`[GA正式版]` Flux v2.9.0(6/30)——CLI插件系统、后量子加密、Workload Identity认证**:新增CLI插件系统(首批Mirror/Schema插件)、SOPS解密支持Age后量子密码算法、基于Kubernetes的Workload Identity认证(对接OpenBao/Vault)、自定义Sigstore信任根(面向气隙环境)。**Breaking Changes**:`image.toolkit.fluxcd.io/v1beta2`与`notification.toolkit.fluxcd.io/v1beta2`两API已EOL并从CRD移除。**行动指南**:升级前必须执行`flux migrate`完成API迁移,气隙环境团队可评估自定义Sigstore信任根方案替代传统密钥验证。

**`[Breaking Changes]` Istio 1.30.2/1.29.5/1.28.9三条支持分支同批安全补丁(6/24)**:修复源自Envoy的多个高危漏洞——OAuth2 filter的AES-256-CBC cookie解密padding oracle攻击(CVSS 6.8,修复方案改用AES-256-GCM)、HTTP/3 QPACK blocked decoding拒绝服务(CVSS 7.5)、证书SAN中NUL字节未校验(CVSS 4.4)。**行动指南**:生产环境应在窗口期内升级,尤其关注OAuth2场景的padding oracle风险,建议对已知使用OAuth2 filter的网关优先升级。

**`[数据基础设施Agentic化]` YugabyteDB 2026.1.0.0 + Databricks Genie按量计费 + MongoDB自动化向量嵌入:数据平台计费模型集体转向AI推理成本绑定**:YugabyteDB 2026.1(6/29)通过MCP暴露完整数据库生命周期管理接口,使AI Agent可直接执行建索引、扩缩容等运维动作;Databricks Genie将于7月6日从固定额度切换为按量付费(每用户每月免费150 DBU);MongoDB"Automated Voyage AI Embeddings"公测,数据写入时自动生成向量嵌入。此转向与6月1日GitHub Copilot订阅制瓦解(agentic会话成本较固定订阅暴涨10-50倍)是同一逻辑在数据基础设施层的延伸。**行动指南**:企业数据平台团队需重新建模AI原生工作负载的真实资源消耗,安全团队需为"AI Agent通过MCP直接执行运维动作"设计权限护栏与审计日志。

**`[平台工程实践]` Security Profiles Operator v1.0.0(6/26)——K8s安全策略API全面稳定化**:项目历史上首个稳定版,8个CRD API从Beta全部升级为v1,提供零停机迁移路径,新增校验型Admission Webhook在非法策略进入reconcile前直接拒绝。**行动指南**:使用seccomp/SELinux/AppArmor策略管理的安全团队应规划API迁移窗口。

**`[生产工程实践]` ClickHouse 26.5/26.6发布,MPP分布式执行叠加AI Agent化产品线**:26.5系列在TPC-H SF100基准上,`ORDER BY...LIMIT`下推穿透JOIN实现20.4倍加速、内存降低175倍;26.6(pre-release)新增跨worker节点多阶段scatter/gather分布式执行、`EXPLAIN WHATIF`低成本验证跳数索引效果。商业层面ClickHouse Cloud客户数破2000家、ARR同比增长超4倍并明确指向IPO路径,官方证实开源版本许可证维持Apache 2.0不变。**行动指南**:评估跳数索引优化效果时优先用`EXPLAIN WHATIF`做低成本试验。

**`[生态政策调整]` 向量数据库赛道遭对象存储原生化与价格战双重挤压**:Pinecone批量导入费率从$1/GB降至$0.25/GB(降幅75%);Milvus 2.6.19(7/1)延续分层存储(降本最高50%)与RaBitQ 1-bit量化(内存降低最高72%)路线;Weaviate v1.37.11(7/1)优化异步复制与BM25评分性能。**行动指南**:向量存储选型需重新计算"专用产品vs对象存储原生集成(S3 Vectors)"的TCO分界点。

**`[生态政策调整]` Kotlin Toolchain(原Amper)正式更名并升级Alpha,反映JetBrains统一Kotlin工具链入口战略**:JetBrains实验性构建工具Amper于6月24日在KotlinConf'26上宣布正式更名为"Kotlin Toolchain"并发布0.11版(升级Alpha阶段,意味着长期支持承诺),原`amper`包装脚本需替换为`kotlin`命令,原仓库已归档。同日Kotlin 2.4.20-Beta1(EAP)发布,Kotlin/Native默认启用klib增量编译缩短debug构建时间。**行动指南**:Alpha阶段暂无需生产迁移,但应持续跟踪该工具链定位调整对现有Gradle构建体系的潜在冲击。

**`[平台工程实践]` Swift/Xcode生态持续打磨,CocoaPods退场时间表明确化**:Xcode 26.6(6/25)正式将Google Gemini纳入可选编码代理,与既有Anthropic/OpenAI模型并列;SE-0526"withDeadline"提案历经三轮评审(第三轮6/28启动)仍在推敲语义正确性。Firebase已明确10月起停止向CocoaPods推送新版本,CocoaPods中央注册表将于12月2日转为只读。**行动指南**:仍依赖CocoaPods管理Firebase/Sentry等核心依赖的项目需在10-12月截止日期前完成向SPM迁移。

**`[生态政策调整]` GitHub"开源许可证合规检查"公开预览(6/30)+ Apache Magpie试点外溢**:GitHub允许企业基于ruleset制定集中式许可证策略,PR新增依赖时自动核对合规性并拦截不合规组件;这是SSPL/BSL/AGPL协议泛滥后平台层对企业依赖可见性诉求的直接回应,与Apache Magpie(见Tier1#3)共同勾勒出"开源治理工具化+AI化"的年中趋势。**行动指南**:中大型企业应尽快接入此类工具建立依赖引入前的许可证门禁,与内部SBOM流程打通。

---

## 🟢 Tier 3:日常风向与情报速递

- Kubernetes 1.33于6月28日正式EOL停止一切补丁支持;1.34将于10月27日EOL;v1.37 Alpha周期持续推进,预计8月26日GA。
- Prometheus v3.13.0同步修复CVE-2026-44990(UI层XSS)及跨主机重定向凭据泄露问题;Crossplane v2.3.3(6/22)修复包签名验证TOCTOU漏洞(GHSA-mf7q-r4rv-jv94)。
- Argo CD v3.5.0-rc2(7/1)进入候选发布阶段;Grafana v13.1.0(7/1)引入告警Rules API v2;Loki v3.7.3(6/24)引入布隆过滤器加速stream查询。
- Terraform v1.15.7(6/24)纯维护补丁,1.16.0-alpha系列持续迭代;Pulumi连续发布v3.248-250(6/24至7/2),Node.js SDK切换要求Node.js 22+。
- CNCF毕业项目Dragonfly v2.5.0(6/25)新增dfget原生支持直接拉取Hugging Face/ModelScope模型仓库;OpenTelemetry已于5月21日毕业为CNCF Graduated项目,巩固可观测性事实标准地位。
- etcd-operator正式捐赠并入Cozystack项目发布v1alpha2 API;CNCF Kepler项目(Sandbox)完成再架构提升K8s Pod功耗测量精度;OpenEverest获CNCF Sandbox接纳。
- DuckDB v1.5.4与v1.4.5 LTS双线发布(6/17同日);Apache Doris 4.1.2(6/17)新增向量检索与AI Functions;Apache Livy(6/4)历经9年孵化毕业为ASF顶级项目。
- PostgreSQL 19 Beta 1(6/4)新增逻辑复制序列同步支持;MongoDB 8.3 GA(5/5)扩展查询表达式原生类型强制转换;pgvector 0.8.4(6/30)修复HNSW并发vacuum图修复错误。
- Flutter 3.44.5热修复(6/30)延续"零用户可见breaking change"稳定优先周期;React Native 0.83起已实现跨版本零破坏性变更升级;Expo SDK 56起New Architecture强制启用。
- Google Play于4月批量更新开发者政策:新增联系人权限最小化要求、强制账户官方过户流程、真实货币"预测市场"功能须6月1日前加入试点;苹果Core Technology Commission已于1月1日全面取代按安装量计费的CTF,统一为5%数字商品收入抽成。
- OpenSSF 6月月报披露"Miasma"与"Mini Shai-Hulud"两波npm供应链攻击持续发酵(Miasma已入侵Red Hat员工账号污染32个@redhat-cloud-services命名空间包);《CRA认知度报告》显示全球66%开源开发者对欧盟网络韧性法案"完全不了解",9月11日漏洞报告义务生效日期临近。
- Linux Foundation宣布拟推出Agent Name Service(ANS),基于DNS基础设施为AI智能体提供可信身份与发现能力;OSI在联合国开源周宣布两年期"开源AI奖学金"项目。
- ScyllaDB放弃AGPL开源社区版转向source-available;Liquibase Community转向Functional Source License(FSL,两年后自动转开源);Soda Core由Apache 2.0改为Elastic License 2.0——三者均延续"基础设施类开源项目增长后收紧协议以防御云厂商竞争"模式。
- Bambu Lab AGPLv3违规持续升级,SFC发起"Baltobu"反制项目众筹25万美元(截止7/17),PrusaSlicer创始人公开声援;终端工具Warp开源数日内即遭社区fork(OpenWarp)以打破云依赖。
- Python Software Foundation启动首届"打包理事会"(Packaging Council)选举,基于PEP 772设立5席专司打包规范协调;OSI董事会选举合法性危机持续发酵,工作组预计9月前提交改革建议。
