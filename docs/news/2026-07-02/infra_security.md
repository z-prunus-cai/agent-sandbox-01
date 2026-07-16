# 基础设施与网络安全深度情报简报(2026-07-02)

> 情报窗口:核心事件以过去48小时(2026-06-30~07-02)为主;因两条主线(基建/安全)在紧窗口内的高价值增量情报数量有限,已按弹性时间窗口原则扩展至过去约8-10天(2026-06-22~07-02),个别背景性但仍在持续影响生产判断的事件(如内核治理演进、CI/CD供应链漏洞类披露)一并收录并标注具体发生日期。

---

## 🔴 Tier 1:核心突破、重大变革与范式巨震

### 1. Oracle PeopleSoft 漏洞链遭 ShinyHunters 规模化在野利用,Nissan、NAIC 相继沦陷 `[0-day 在野利用]` `[供应链攻击]`

**事件全景**:Google/Mandiant 确认,黑产团伙 ShinyHunters(UNC6240)在 2026-05-27 至 06-09 期间已对 PeopleSoft Enterprise PeopleTools 8.61/8.62 发起在野利用,较 Oracle 正式补丁提前逾两周。攻击者部署伪装成合法云端点的定制版 MeshCentral 代理执行管理命令与横向移动,并声称已从约100家机构的300余套 PeopleSoft 实例窃取数据,教育行业首当其冲。**Nissan** 美洲分部员工数据(SSN、银行、税务信息)、**NAIC**(美国保险监管协会)自称3.1TB 数据(NAIC 否认规模,称仅涉及公开报告与过期日志)相继披露失守,构成6月最具破坏性的企业级数据泄露事件簇。

**漏洞成因分析**:核心漏洞 CVE-2026-35273 为 CWE-918 服务端请求伪造(SSRF),CVSS 9.8,无需认证、无需交互即可触发,攻击者借此在 PeopleTools 内部管理端点(PSEMHUB)获得立足点。Oracle 定于 07-21 发布的7月关键补丁更新(CPU)确认该漏洞与另一枚 PeopleSoft 缺陷 **CVE-2026-35278** 存在链式利用关系,共同构成完整 RCE 链条——这是典型的"内部管理接口暴露于不可信网络边界"架构缺陷,一旦被自动化扫描发现即可规模化复用。

**生产架构影响与加固指南**:受影响版本为 PeopleTools 8.61/8.62,企业应立即核查资产是否已修补,复查 PSEMHUB 等管理端点的网络可达性(理想情况下不应暴露于公网或不可信内网分区),并对可能已失守的环境执行凭证轮换、MeshCentral/RMM 类工具异常安装排查与横向移动溯源。鉴于漏洞在补丁发布前已被利用超两周,建议对遗留 ERP 系统类资产建立"补丁发布即视为已泄露"的应急响应默认假设。

### 2. Linux 内核治理黑天鹅——AI 生成补丁洪流迫使 ARM64/KVM 交出"零新功能"合并窗口 `[内核重大重构]`

**事件全景**:Linux 7.2-rc1 于 06-28 发布,合并窗口正式关闭,内核树代码总量突破4300万行。但本轮周期最具警示意义的并非新特性,而是治理层面的结构性异变:KVM/arm64 维护者 Marc Zyngier 罕见地提交了一份**不含任何新功能、纯粹是修复**的拉取请求,直言"当前邮件列表中充斥太多 AI 驱动的修复补丁,已经难以抽出精力评审新特性";ARM64 架构维护者 Will Deacon 同期证实"新一代 AI 工具客观上拖慢了我们在功能侧的进度"。与此同时,Linus Torvalds 公开表示 AI 生成的重复漏洞报告已让内核私有安全邮件列表"几乎完全失控、对所有人都是浪费时间",推动切换至新的公开报告机制。

**底层机制分析**:这标志着内核开发流程正经历自 Git 诞生以来最深刻的范式冲击——评审瓶颈已从"人力评审 vs. 补丁数量"演变为"人力评审 vs. AI 辅助生成的补丁与漏洞报告的双重洪流"。对比之下,RISC-V(批量 G-stage TLB 刷新)、s390(2G 大页)、Intel(嵌套 MBEC)、AMD(嵌套 GMET)本轮周期均斩获 KVM 新特性,唯独 ARM64 因评审资源被 AI 噪声挤占而交了白卷,直接量化证明了"评审瓶颈"已从假设变为可测量的现实生产力损耗。

**生产架构影响与加固指南**:对依赖 ARM64/KVM 虚拟化底座(如 AWS Graviton、Azure Cobalt、Google Axion 云实例)的团队而言,新特性交付周期可能出现结构性delay,需要在容量规划中纳入"上游特性滞后"风险;安全团队应关注内核漏洞报告公开化机制切换后,厂商下游(RHEL/Ubuntu/SUSE)CVE 披露节奏可能随之调整;同时建议 AI 辅助代码贡献团队建立内部预审门槛(如 Kubernetes 已引入的 CodeRabbit 模式),避免向上游倾泻低质量补丁反噬整个开源生态的评审产能。

### 3. CI/CD 与开源供应链信任链系统性沦陷——Cordyceps 漏洞类叠加 Shai-Hulud 蠕虫进化 `[供应链攻击]`

**事件全景**:渗透测试机构 Novee Security 于 06-23 披露代号 "Cordyceps" 的系统性 GitHub Actions CI/CD 漏洞类,扫描约3万个高影响力仓库后验证出数百条完全可利用的攻击链,微软 Azure Sentinel、Google AI Agent Development Kit、Apache、Cloudflare、Python 软件基金会均在受影响之列。与此同时,自2025年9月持续至今的 **Shai-Hulud** npm/PyPI 自我传播蠕虫在06-24掀起新一轮攻势("Mini Shai-Hulud"),波及 LeoPlatform、RStreams 生态,并演化出名为"Hades"的 Python 变种(代号 Miasma)。

**漏洞成因分析**:Cordyceps 的根源在于工作流普遍将 PR/评论输入视为可信的维护者输入——攻击者仅需一个免费 GitHub 账号发起低权限工作流,其输出若被高权限工作流(持有 `roles/owner` 级 GCP 令牌)消费,单个步骤看似无害,链式组合即可实现完全接管;GitHub 06-18 发布的 `actions/checkout v7` 仅拦截了最常见的 fork-PR checkout 模式,对手动 `run:` 块拉取不可信代码、`issue_comment` 触发型工作流等攻击面并未覆盖。Shai-Hulud 新一代技术"Phantom Gyp"则利用恶意 `binding.gyp` 触发 `node-gyp` 在安装阶段执行任意命令,更关键的是,部分投毒包甚至携带**合法有效的 SLSA Build Level 3 溯源证明**——这是首个被证实能产出"合法签名"恶意包的蠕虫,彻底击穿了"溯源证明=可信"这一行业默认假设。

**生产架构影响与加固指南**:技术团队须审计 CI/CD 工作流中的跨工作流数据流(尤其是低权限工作流输出流入高权限令牌作用域的路径),对 `issue_comment`/`pull_request_target` 触发器施加最小权限原则;不能再将 SLSA 等溯源证明作为唯一信任锚点,需叠加 postinstall 脚本行为基线检测与依赖变更异常告警;对已使用相关生态包的组织,应立即核查 CI 日志中是否存在非常规 OIDC 令牌交换记录。

### 4. 容器隔离层集体沦陷——containerd/runc/Kata Containers 关键逃逸漏洞集中爆发 `[高危 CVE]` `[虚拟化安全]`

**事件全景**:06-18 前后,containerd 项目同时向 1.7~2.3 全部5条受支持分支(2.3.2/2.2.5/2.1.9/2.0.10/1.7.33)发布安全更新,修复5枚 CRI 插件漏洞,其中3枚评级"严重";同期 runc 发布 1.5.0 GA 并修复 CVE-2026-41579;更严重的是,专为对抗容器逃逸而设计的 VM 级隔离方案 **Kata Containers** 被曝出 CVE-2026-47243——攻击者可从虚拟机内部突破 virtiofsd 直达宿主机 root,恰好命中其新一代 `runtime-rs` 运行时(即将成为 Kata 4.0 默认运行时)。Azure AKS 同期修复的 CVE-2026-32193(CVSS 8.8)则是容器逃逸至宿主节点的又一实例。

**底层机制分析**:containerd 的核心问题集中在 CRI 检查点/恢复(checkpoint/restore)功能对不可信元数据的信任边界失守——**CVE-2026-53488**(镜像 LABEL 指令未经净化即可致宿主机命令执行)、**CVE-2026-53492**(CDI 注解在检查点恢复过程中被伪造注入)、**CVE-2026-50195**(未校验的检查点镜像引用导致共享节点间镜像缓存投毒)均源于"恢复阶段直接信任镜像自带元数据,而非重新对照实时 Pod 规格校验"这一设计缺陷。Kata 的 CVE-2026-47243 则是虚拟机内 root 用户可向宿主 virtiofsd 发送原始 FUSE `FUSE_SYMLINK` 请求,在特定运行时配置下,该请求被在共享目录**之外**执行,使得客户机 root 能在宿主机敏感路径(如 `/etc/cron.d`)创建符号链接,间接以宿主 root 身份执行载荷。

**生产架构影响与加固指南**:所有使用 checkpoint/restore 特性的多租户 Kubernetes 环境须立即升级至 containerd ≥2.3.2/2.2.5/2.1.9,并审计是否曾启用该功能;runc 应统一升级至 1.5.0/1.4.3/1.3.6;依赖 Kata 作为"沙箱级隔离"防线的团队(如 AKS 上的 AI Agent 工作负载隔离)需确认 Kata 版本已应用 `ffa59ce3aa78` 之后的补丁,并重新评估 `runtime-rs` 在生产环境的信任假设——"VM 级隔离天然免疫容器逃逸"的行业默认认知需要修正。

### 5. Cloudflare 全球光纤中断叠加印度-Telegram BGP 劫持——互联网单点脆弱性再敲警钟 `[云厂商/CDN区域性中断]`

**事件全景**:06-22,Cloudflare 于13:35 UTC监测到错误率与延迟异常飙升,14:37 UTC 确认根因为北美东部一条第三方光纤线路physical层被切断,由于 Cloudflare 承载全球约20%的Web流量,故障沿依赖链级联扩散,X(逾3.5万条 DownDetector 报告)、Reddit、Microsoft Teams、Zoom、AWS 前端服务、Robinhood、Discord 均出现明显中断,流量工程重路由后约20分钟内多数服务恢复。同期(06-16),印度 Reliance(AS18101)为执行国内 Telegram 封禁令,发起针对 Telegram IP 段的 BGP 更具体路由劫持,却意外泄露至境外,一路影响到阿联酋地区的 Telegram 连通性,Telegram 随即反制发布更具体前缀,双方陷入"前缀长度军备竞赛"。

**底层机制分析**:两起事件共同指向同一个基建脆弱点——互联网骨干层的物理与逻辑单点故障均可在无软件缺陷、无攻击意图的情况下引发跨品牌、跨地域的级联失效。Cloudflare 事件纯属物理层光纤中断,却因其在全球互联架构中的枢纽地位,直接放大为"AWS/X/Teams 同时故障"的表象;BGP 劫持事件则暴露出多数运营商对上游缺乏 RPKI ROV/IRR 过滤兜底,导致原本旨在境内生效的封锁路由径直外溢至全球路由表。

**生产架构影响与加固指南**:技术团队应认识到"多可用区容灾"并不能抵御 CDN/骨干网层面的单点物理故障,对关键业务应评估多 CDN/多线路冗余方案;安全与网络团队需推动上游 ISP 部署 RPKI 路由来源验证(ROV)并对同行运营商的路由公告设置更严格的路径长度/前缀过滤规则,以防区域性封锁指令外溢为全球性路由劫持;监控团队应将"第三方光纤/骨干网状态"纳入服务健康度仪表盘的上游依赖清单。

---

## 🟡 Tier 2:关键演进、生产工程实践与高危漏洞

### 6. Linux 内核本地提权"三连镜像"——page-cache/网络子系统提权漏洞家族持续扩张 `[高危 CVE]`

06-08至06-25 期间三枚 Linux 内核本地提权漏洞相继被发布完整利用链:**CVE-2026-43503("DirtyClone", CVSS 8.8)** 由 JFrog 披露,根因是 `__pskb_copy_fclone()` 在克隆已克隆的 fclone skb 时丢失 `SKBFL_SHARED_FRAG` 标志,导致内核误判 page-cache 共享内存的归属,可借 XFRM/IPsec 原地加密变换篡改缓存中的 setuid 二进制文件(如 `/bin/su`)提权至 root,且不留磁盘痕迹;**CVE-2026-46331("pedit COW")** 是 `tcf_pedit_act()` 中写范围计算早于运行时偏移解析完成,导致越界写入命中共享页缓存,PoC 命名 "packet_edit_meme" 在CVE分配后一天内即公开;**CVE-2026-23111** 是 nf_tables 一处因误删一个 `!` 符号导致的 use-after-free,Exodus Intelligence 06-08 发布的完整利用链在空闲系统上对 Debian/Ubuntu LTS 命中率超99%。三者均要求非特权用户命名空间可用,行动指南:立即核查发行版补丁状态,评估是否可关闭非必要的 `unprivileged_userns_clone`,并对 `act_pedit` 等非必需内核模块加载施加限制。

### 7. 微软 Defender 0-day 家族持续武器化,已被勒索团伙采纳 `[0-day 在野利用]`

微软 Defender 权限提升漏洞家族(研究者 "Nightmare Eclipse" 因不满 MSRC 披露处置流程而连续公开 PoC)持续升级:04月披露、04-14修复的 **CVE-2026-33825("BlueHammer")** 于07-01被 CISA 确认已遭勒索团伙武装化利用;另一枚 **CVE-2026-50656("RoguePlanet", CVSS 7.8)** 滥用 NTFS 目录联接、机会锁、卷影副本与 WER 计划任务的组合竞态条件获取 SYSTEM 权限,截至07月初**仍无官方补丁**,微软仅表示"补丁开发中"(注:该 CVE 编号在不同信源间存在 CVE-2026-50656 与 CVE-2026-47281 两种记录,发布前建议以 MSRC 官方公告为准)。两者共同揭示:安全研究披露纠纷驱动的"抗议式" PoC 公开正在缩短漏洞从"研究发现"到"勒索软件武器化"的窗口期。企业应对未修补的 RoguePlanet 加强本机 EDR 行为检测规则覆盖,并密切关注 MSRC 补丁发布动态。

### 8. 网络边界设备预认证 RCE 集中引爆,多起遭勒索团伙关联利用 `[高危 CVE]` `[已出补丁]`

三起独立的边界设备漏洞在同一窗口期集中曝光并遭在野利用:**Ivanti Sentry**(CVE-2026-10520,CVSS **10.0**,`ConfigServiceController` 未认证命令注入;CVE-2026-10523,CVSS 9.9,认证绕过)被 Shadowserver 确认存在大规模扫描,19个可见暴露实例中已有2个被植入后门,修复版本 10.5.2/10.6.2/10.7.1;**Progress Kemp LoadMaster**(CVE-2026-8037,CVSS 9.6)根因是 `escape_quotes()` 清洗函数使用未清零、未加空终止符的堆内存,watchTowr 06-29发布完整利用链,修复版本 GA v7.2.63.2/LTSF v7.2.54.18;**Check Point Remote Access VPN**(CVE-2026-50751,CVSS 9.3)因 IKEv1 证书校验逻辑缺陷允许无密码建立完整 VPN 会话,Check Point 以中等置信度将其归因于 **Qilin 勒索团伙**关联团伙。三者共性是均已出补丁但被在野利用抢跑,行动指南:立即核查上述三类设备版本并优先修补,同时评估是否可临时收紧管理接口公网暴露面。

### 9. 企业级软件6月高危 CVE 速递——SAP/Atlassian/Adobe/Cisco/Fortinet/Palo Alto/Chrome/Exchange 集中放量 `[高危 CVE]`

06月末至07月初集中爆发多枚 CVSS ≥9.0 高危漏洞:**Adobe ColdFusion**(APSB26-68,06-30)一次性发布**6枚 CVSS 10.0** 缺陷,含不受限文件上传(CVE-2026-48276);**SAP** 6月补丁日修复 SAML XML签名包装认证绕过(CVE-2026-44748,CVSS 9.9)与 RFC 协议未认证 RCE(CVE-2026-27671,CVSS 9.8);**Atlassian** Jira/Confluence 因 Axios 依赖原型污染与 SSRF(CVE-2026-40175/CVE-2026-42043,均 CVSS 10.0)及 Tomcat 依赖注入需紧急升级;**Cisco Secure Workload**(CVE-2026-20223,CVSS 10.0)未认证 API 可致跨租户 Site Admin 级接管,**无缓解方案**仅能升级;**Fortinet FortiSandbox**(CVE-2026-25089,CVSS 9.8)未认证命令注入;**Palo Alto PAN-OS** 多枚缺陷(CVE-2026-0300 CVSS 9.3、CVE-2026-0257)已确认存在有限在野利用;**Chrome V8** 年内第5个0-day(CVE-2026-11645)已入 CISA KEV;**Exchange** OWA XSS 0-day(CVE-2026-42897)已在披露时被在野利用。建议企业按 CVSS 与暴露面双维度排序修补优先级,重点关注无缓解方案的 Cisco Secure Workload 与已遭利用的 PAN-OS/Chrome/Exchange。

### 10. 三大云厂商机密计算硬件隔离竞赛白热化 `[云服务 GA]` `[架构演进]`

AWS 于 Graviton5 平台推出 **"Nitro Isolation Engine"**,宣称是首个经过**形式化数学验证**(而非仅测试覆盖)的云虚拟化隔离组件,搭配 C9g/C9gd(192核、DDR5-8800、PCIe Gen6)及 Lambda MicroVMs——基于 Firecracker 的有状态沙箱原语,单会话可持续运行8小时,专为运行 AI Agent 生成的不可信代码设计;Azure 同期将 **Confidential VM(Azure Linux)** 推至 GA 并预览 Intel TDX 机密虚拟机的**在线活迁移**(此前必须重启才能完成主机维护);Google Cloud 则将机密计算扩展至 **Blackwell GPU**(Confidential G4 预览)与 H100(Confidential Space GA),三者共同将"硬件级隔离"从 CPU 阶段扩展至 GPU 训练/推理阶段。技术团队评估敏感 AI 工作负载云选型时,应重点比较三家在 GPU 机密计算成熟度与活迁移能力上的差异。

### 11. Kubernetes 生态运维能力集中跃迁——EKS 回滚、AKS LTS、GKE 待命缓冲区 `[云服务 GA]`

三大云厂商同期在 Kubernetes 托管服务层面推出重要运维能力:**AWS EKS** 新增控制面7天窗口内逐版本回滚能力(此前控制面升级为不可逆操作),回滚耗时约20分钟;**Azure AKS** 将 Kubernetes 1.36 推至 **GA 且赋予 LTS(长期支持)** 状态,同批次修复 Istio 插件 CVE-2026-47774 并升级多个 CSI 驱动;**Google GKE** 待命缓冲区(Standby Buffers)GA,挂起节点约30秒即可恢复(较全新节点置备快2-3倍),仅按持久盘+IP计费,GKE Agent Sandbox 场景下调度延迟降至亚秒级、成本降低最高90%。三项能力共同指向一个趋势:托管 Kubernetes 正从"能跑起来"转向"能安全试错、能快速弹性"的生产成熟度阶段,建议运维团队评估现有集群升级策略是否可享受上述新能力降低变更风险。

### 12. Linux 7.2 合并窗口全景——Rust 内存安全深化与调度器重构 `[内核子系统更新]`

Linux 7.2-rc1 于06-28发布,收官本轮合并窗口,内核树增至逾4300万行。安全相关最大看点是 **Rust 内存安全阵地进一步扩张**:内核 vendored 进约4万行 `zerocopy` crate 代码,为更多 Rust 驱动消除 `unsafe` 块;Greg Kroah-Hartman 与 Benno Lossin 在 RustWeek 2026 提出的 **`Untrusted<T>`** 类型设计仍在推进中,声称可通过编译期标记强制所有用户态/硬件输入数据经过唯一可审计的校验路径,理论上可消除高达80%的历史内核 CVE。调度器方面,**Cache Aware Scheduling(CAS)** 正式合并,将通信密集型任务钉死在同一末级缓存(LLC)域内以减少缓存抖动,在 hackbench/schbench 基准上有显著收益;sched_ext 因文件布局问题被 Torvalds 斥为"令人厌恶"后完成向 `kernel/sched/ext/` 的目录重构。运维团队可评估新调度特性在高并发微服务/游戏工作负载上的收益,同时关注 Rust 生态扩张带来的驱动开发范式转变。

### 13. 文件系统与内存管理工程突破——MGLRU、btrfs、XFS、exFAT 多点开花 `[内核子系统更新]`

06月内核工程侧多项量化收益显著的优化落地:**MGLRU**(多代最近最少使用页面回收)改进在 MongoDB+YCSB 基准上 NVMe 场景吞吐提升最高30%,慢速 I/O(机械硬盘)场景提升最高100%;**btrfs** 修复了自2023年新挂载 API 转换以来一直存在的直接 I/O 意外串行化缺陷(`SB_NOSEC` 标志从未被设置),单行修复带来 **59%的直接 I/O 吞吐提升**(826→1311 MB/s);同时 btrfs 修复了新披露的 **CVE-2026-53284**(元数据回写状态跟踪错误导致事务"半提交"、文件系统被强制置为只读,且无需底层硬盘故障即可复现);**XFS** 的分区(zoned)分配器正式脱离实验状态,面向 SMR/ZNS 顺序写约束存储设备;**exFAT** 完成向 iomap 框架的完整迁移,移除私有页缓存代码。技术团队应关注这些底层 I/O 路径优化对现有存储性能基线评测的影响,并优先验证 btrfs CVE 补丁是否已应用到生产内核。

---

## 🟢 Tier 3:日常风向与情报速递

- **Kubernetes v1.37 开发进入 alpha.2**(06-25),同期官方博客披露已在部分 SIG 仓库引入 **CodeRabbit** 作为 AI 预评审工具,作为对"AI 时代开源维护"评审产能危机的直接回应。
- **QEMU 三条稳定分支(11.0.2/10.2.4/10.0.11)同步发布补丁**(06-26);**Docker Engine 29.6.1** 修复 grpcfuse 内核模块递归导致的虚拟机崩溃(CVE-2026-8936)及 Model Runner 推理后端容器逃逸(CVE-2026-5817)。
- **Ceph v20.2.2 "Tentacle"** 发布,修复 LingerOp 模块的 use-after-free 与内存泄漏,新增 Rocky Linux 10 支持;**NetApp StorageGRID 12.1** 引入跨地域联邦命名空间(可扩展至10EB),AI负载场景聚合吞吐最高达12TB/s。
- **PowerDNS 同日发布双安全公告**(06-25):Authoritative Server 与 Recursor 均存在内部 Web 服务资源无限分配导致的 DoS 缺陷,Recursor 另修复一处 ECS 客户端子网信息泄露(CVE-2026-40012)。
- **Google Cloud 印度节点网络降级持续近3周**:因新德里第三方数据中心机房失火导致网络设备紧急下线,影响 Hybrid Connectivity/VPC/Media CDN,直至06-29左右才完全恢复。
- **ThousandEyes周报**显示06-22至06-28当周全球网络中断事件环比激增26%(516起,美国境内344起),同期 Cox、Akamai、Fastly 均出现多起边缘/ISP级短时不稳定,反映基础设施层压力具有跨厂商相关性而非孤立事件。
- **Cloudflare 同期密集发布多项平台能力**:OAuth 客户端创建向全体开发者开放(涉及 Ory Hydra 1.x→2.x 零停机迁移逾1.3亿行数据)、Workflows 引入 Saga 补偿回滚模式、Durable Objects 新增美国专属司法管辖区、AI 爬虫流量分类控制(Search/Agent/Training)向全体客户开放,并计划09-15起对新接入域名的广告页面默认拦截 Training/Agent 类爬虫。
- **虚拟化生态动态**:IBM Cloud 上的 Red Hat OpenShift Virtualization 服务(99.99% SLA)GA;Vercel Services 推出前后端统一部署与私有服务间网络;Oracle Duality 隐私增强计算平台(全同态加密+可信执行环境)登陆 OCI 服务国防情报数据共享;DigitalOcean 推理引擎新增 H100/H200/B300/MI300/MI325 全代际 GPU 选项。
- **Aflac 日本子公司数据泄露**:438万保单持有人信息(含约23万人银行/保费扣款账户信息)于06-15至06-25期间遭未授权访问,因流量异常才被发现,同期日本 Sapporo、Nidec、KDDI 等企业相继披露入侵事件。
- **Medtronic** 遭 ShinyHunters 勒索,声称窃取约900万条记录(公司否认涉及医疗设备与患者安全系统),泄露站点信息其后被下架但赎金支付情况未确认。
- **约240亿条凭证记录经 Elasticsearch 错误配置暴露**(8.3TB,由 Cybernews 于06-12发现并于06-15责令下线):数据来自36个信息窃取木马日志与Telegram泄露渠道聚合,大部分为凭证+会话令牌,凸显会话令牌盗取可同时绕过密码与MFA的风险。
- **ScreenConnect 被滥用投递 AsyncRAT**:攻击者通过90余个仿冒 OBS Studio、Bandicam 等软件的 SEO 投毒站点(覆盖10种语言),利用签名安装程序 DLL 侧加载部署远控木马,配合进程镂空技术规避检测。
- **执法动态**:Scattered Spider 成员(19岁美/爱沙尼亚双重国籍)经芬兰引渡至美国受审,涉及包括2025年5月800万美元加密货币敲诈在内的多起入侵案;**CISA 新增8项工业控制系统安全公告**(06-30),涵盖施耐德电气、三菱电机等厂商,其中一项涉及历史 XZ Utils 后门残留影响 B&R 工业产品线。
- **Azure 存储架构调整**:Azure Files 迁移至以"共享"为独立顶级资源的新管理模型(GA,脱离存储账户依赖);Blob 存储自07-01起对新建存储账户的 Cool/Cold/Archive 层级实施128KiB最小计费对象大小新规,小对象海量存储场景成本可能激增最高32倍,需提前评估对象打包策略。
- **GCP 基建工程深度动态**:机密计算扩展至 Blackwell/H100 GPU 并开源 Prompt 加密 SDK;发布"万亿参数 TPU 训练集群可靠性模型"架构详解,首次以"拓扑可用性"(而非单实例SLA)重新定义超大规模训练集群的容量保障口径(9216颗 Ironwood TPU 编组的超级舱目标为95%时间内144个立方体中130个可用)。
