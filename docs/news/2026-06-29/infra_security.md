# IT 基础设施与网络安全情报简报

**日期：2026-06-29 | 情报时窗：过去 48–96 小时（扩展至近两周以确保双轨覆盖充足）**
**方向：Linux 内核 · 公有云基础设施 · 网络安全攻防**

---

## 🔴 Tier 1：核心突破、重大变革与范式巨震

### 1. `[0-day 在野利用]` CVE-2026-46331 "pedit COW"：Linux 内核 tc 子系统页缓存污染 0-day，PoC 公开 24 小时内武器化

**事件/架构全景**

2026 年 6 月 16 日，kernel.org CNA 在 Linux 内核 `net/sched` 子系统的 `tcf_pedit_act()` 函数中正式分配 CVE-2026-46331，命名为"pedit COW"（Copy-on-Write）。**漏洞的根本性危险在于：任意无权限本地用户可在数秒内将系统提权至 root，且利用方式隐蔽至极——不写入磁盘，不触发内核日志，完全在内存页缓存层面完成攻击**。仅 24 小时后，GitHub 上便出现了武器化 PoC `packet_edit_meme`，使得该漏洞从理论风险直接演化为实战级威胁。Ubuntu 官方已确认 18.04 至 26.04 所有受支持版本均受影响（截至 2026-06-25），上游修复版本为 v7.1-rc7，受影响内核范围为 v5.18 至 v7.1-rc6。

**底层机制/漏洞成因分析**

`tc`（traffic control）的 `pedit` 动作允许对经过内核网络包的数据进行就地编辑（in-place edit），其核心函数 `tcf_pedit_act()` 设计上遵循 Copy-on-Write 策略——在对包数据写入前，必须先取得独占副本。然而该函数存在严重的时序错误（race condition）：**可写内存范围的校验发生在运行时偏移量被完全计算出之前**，某些包编辑键（`pedit key`）的偏移量是在运行时动态解析的，导致写操作越过了已校验的边界，直接污染了 `setuid` root 二进制文件（如 `/bin/su`）在内存中的页缓存副本。攻击者随后执行该"已中毒"的内存镜像，注入的 shellcode 以 root 权限运行。利用条件为：`act_pedit` 内核模块可加载 + 非特权用户命名空间（unprivileged user namespaces）已启用——这是绝大多数现代 Linux 发行版的默认配置，使得攻击面极为广泛。

**生产架构影响与加固指南**

- **立即行动**：升级内核至 v7.1-rc7 或各发行版已发布的带外安全补丁版（Ubuntu/RHEL/Debian 均已推送）；
- **临时缓解（若无法立即重启）**：执行 `modprobe -r act_pedit` 卸载模块；同时将 `kernel.unprivileged_userns_clone=0` 写入 `/etc/sysctl.d/` 以禁用非特权用户命名空间；
- **高风险场景排查**：多租户环境（KVM/容器宿主机）、Kubernetes worker 节点、共享开发服务器——这些场景中本地用户可直接执行该 PoC；
- **检测信号**：监控 `ip link`/`tc` 相关 netlink 事件异常、`/bin/su` 等 setuid 二进制在内存中的哈希漂移（借助 `auditd` 或 eBPF 探针实现页缓存完整性监控）。

---

### 2. `[供应链攻击]` Miasma/Mini Shai-Hulud 战役：20+ LeoPlatform/RStreams npm 包同时中毒，CI/CD 流水线系统性沦陷

**事件/架构全景**

2026 年 6 月 24 日 23:04:55 UTC，Microsoft 威胁情报（MSTIC）捕获到一起高度自动化的供应链攻击事件：攻击者通过窃取的 npm 维护者账户凭证（账户 `czirker`），在 **不足 6 秒** 的窗口内同步发布了 LeoPlatform 和 RStreams 生态下 **20 余个 npm 包** 的恶意版本，涵盖 SDK、CLI、AWS 连接器、Cron 调度、日志、无服务器等核心组件——这些包构成了大量企业数据管道与云集成工作流的基础。此攻击被研究人员归入持续活跃的 **Miasma 战役**（又名 Mini Shai-Hulud / Hades 恶意家族变体），该家族此前已波及 GitHub Actions 工作流与 Go 语言生态系统（Verana Blockchain 项目被证实牵连其中）。

**底层机制/漏洞成因分析**

攻击者采用"Phantom Gyp"绕过技术：**恶意包中不含任何可见的 `preinstall`/`postinstall` npm 脚本**（这些是常规扫描器的主要检测目标），而是植入 `binding.gyp` 构建描述文件；当 `npm install` 触发 `node-gyp` 编译时，`binding.gyp` 中的 shell 命令展开实现任意代码执行——从而完全规避了基于 `scripts` 字段的静态检测。恶意载荷将工具包写入 `/tmp/p.js` 后，调用 **Bun 运行时**（自动下载 v1.3.13）而非 Node.js 执行，原因在于大多数 Node.js EDR 模块加载钩子和 CI/CD 沙箱监控工具均基于 Node 运行时，Bun 的使用直接盲掉了这些检测探针。最终目标是系统性窃取：**GitHub Actions runner 上的环境变量与 secret、云厂商凭证（AWS/GCP/Azure）、npm/PyPI 等包注册表 token、密码管理器数据**，并通过受害者自身的 GitHub token 进行外泄，形成"借刀杀人"的横向传播链。

**生产架构影响与加固指南**

- **紧急排查**：凡在 2026-06-24 23:00 至 06-25 06:00 UTC 期间执行过含 `@leo-platform/*` 或 `@rstreams/*` 的 `npm install` 的 CI/CD 流水线，均应视为疑似感染，立即轮换所有相关凭证；
- **锁定依赖版本**：所有生产 `package-lock.json` 和 `yarn.lock` 应校验包完整性哈希（`integrity` 字段），与 npm 审计日志比对；
- **供应链纵深防御**：在 CI 中强制启用 `npm audit` + Socket.dev 或 Snyk 扫描；对 `binding.gyp` 存在的包执行手动审查或沙箱预执行；
- **运行时隔离**：CI/CD runner 建议以最小权限运行，禁止对外网络访问（仅白名单），并对 `/tmp`、外部进程启动等行为实施 eBPF 审计。

---

### 3. `[供应链攻击]` OptinMonster/TrustPulse/PushEngage CDN 供应链投毒：1.2M WordPress 站点被植入后门管理员账户

**事件/架构全景**

2026 年 6 月 12 日 22:17 UTC，安全研究机构 Sansec 发现 WordPress 插件巨头 Awesome Motive 旗下的 CDN 分发基础设施遭到入侵，攻击者篡改了被数百万网站引用的 `OptinMonster`、`TrustPulse`、`PushEngage` 三款插件的 SDK JavaScript 文件。截至事件响应完成，**超过 120 万个 WordPress 站点在 CDN 缓存刷新之前均处于主动感染状态**，每个被感染站点均可能成为攻击者的持久性后门跳板——这是 WordPress 生态史上规模最大的 CDN 层供应链投毒事件之一。

**底层机制/漏洞成因分析**

攻击链起点是 Awesome Motive 自身营销官网服务器上运行的 **UpdraftPlus 备份插件中的已知漏洞**，攻击者借此获得服务器访问权，进而在服务器上找到了明文存储的 **CDN API 密钥**（一个典型的 Secret 横向泄露场景）。拿到 CDN 密钥后，攻击者修改了被全球用户网站实时引用的正式 SDK 文件，注入恶意 JavaScript：该脚本具备 **无限期驻留（persistence）** 机制——它监听页面中登录管理员的 Cookie，在检测到 WordPress 后台管理员身份后，自动在目标站点创建一个隐藏的后门管理员账户，安装自隐藏的后门插件，并将新账户凭证外泄至攻击者控制的 `tidio.cc`（仿冒正规工具 `tidio.com`）。值得注意的是，攻击者将 CDN 缓存刷新的边界利用到极致——在 OptinMonster 和 TrustPulse 路径被清理后（06-13 19:02 UTC），PushEngage SDK 在部分 CDN 边缘节点上仍持续投毒超过 24 小时，表明攻击者对 CDN 缓存失效机制有深入研究。

**生产架构影响与加固指南**

- **受影响站点排查**：检查 WordPress 用户表（`wp_users`）中是否存在陌生的 `administrator` 角色账户；检查已安装插件列表中的隐藏/低名知度插件；审查近期 SSH/FTP 登录日志；
- **CDN 安全架构反思**：插件厂商不应将 CDN API 密钥与 Web 应用共置同一服务器；CDN 分发文件应实施**子资源完整性（SRI, Subresource Integrity）哈希校验**，阻止运行时内容替换；
- **WordPress 运维加固**：所有引用第三方 CDN 资源的插件须定期执行 SRI 审计；部署 WAF 并监控后台 `/wp-admin` 异常账户创建行为；限制 `wp_users` 的直接数据库写权限。

---

## 🟡 Tier 2：关键演进、生产工程实践与高危漏洞

### 1. `[高危 CVE]` `[已出补丁]` CIFSwitch（CVE-2026-46243）：潜伏 19 年的内核 CIFS Upcall 缺陷，单条命令即可获 root

**核心增量/漏洞成因**

研究员 Asim Manizada 于 2026 年 5 月 28 日披露 CVE-2026-46243，漏洞根源在于 `cifs.spnego` 密钥类型的 **请求来源校验完全缺失**——代码可追溯至 2007 年引入，在内核中潜伏 19 年。攻击者通过 `request_key(2)` 系统调用伪造 `cifs.spnego` 密钥描述，触发特权 Kerberos 辅助进程 `cifs.upcall` 以攻击者控制的参数运行；结合用户命名空间与挂载命名空间操作，诱导 `cifs.upcall` 加载恶意 NSS 共享库，完成本地提权。补丁上游 commit `3da1fdf` 已在 2026-06-02 合并并推送至各发行版。默认启用 SELinux/AppArmor 的系统（Ubuntu 26.04、Fedora 40+、RHEL/CentOS Stream 10、Rocky Linux 10、SLES 16）不受实际利用影响，但未硬化的 LTS 发行版（如 Ubuntu 20.04/22.04 + cifs-utils ≥ 6.14）暴露面极大。

**核心工程思想/预期差**

本漏洞揭示了内核"trusted helper"模式的深层风险：`cifs.upcall` 作为 setuid root 辅助进程，其调用参数来源从未被严格校验。在没有 LSM 沙箱的系统上，它构成了一条绕过所有用户态权限控制的 root 直通路径。

**落地行动指南**

升级内核或 cifs-utils 至含补丁版本；若系统不需要 CIFS/SMB 挂载，立即执行 `modprobe -r cifs` 并将 `cifs` 加入内核模块黑名单；检查 `/etc/modules-load.d/` 是否有强制加载 cifs 的配置；对共享主机环境执行非特权用户命名空间限制。

---

### 2. `[高危 CVE]` `[已出补丁]` Dirty Frag 家族全景：CVE-2026-43284 / CVE-2026-43500 / CVE-2026-43503（DirtyClone）——网络栈页缓存写原语集群式爆发

**核心增量/漏洞成因**

2026 年 5-6 月，以 Dirty Frag 命名的 Linux 内核 LPE 漏洞家族集中披露，揭示了**内核网络栈 skb（socket buffer）与文件系统页缓存边界管理**上的系统性设计缺陷：

- **CVE-2026-43284**（CVSS 8.8）：IPsec ESP（esp4/esp6）就地解密路径中，当接收路径对通过 `splice(2)/sendfile(2)` 进入 socket 的管道页缓存进行解密时，写操作直接命中非内核私有的文件背衬页，形成页缓存写原语；
- **CVE-2026-43500**（CVSS 7.8）：RxRPC（AFS 协议）中存在相同模式的缺陷；
- **CVE-2026-43503 DirtyClone**（CVSS 8.8，JFrog Security Research，2026-06-25 披露完整利用细节）：`__pskb_copy_fclone()` 在克隆 skb 时错误地删除了 `SKBFL_SHARED_FRAG` 安全标志，该标志是内核标记"此内存由文件背衬——禁止就地写入"的唯一保护机制，被清除后相当于无声撤销了所有下游的 COW 检查。三个 CVE 均在 v7.1-rc5（2026-05-21 合并）中修复。

**核心工程思想/预期差**

Dirty Frag 家族的危险性在于：利用链仅依赖标准系统调用（`socket/setsockopt/vmsplice/splice/sendmsg`）和默认加载的内核模块（`esp4/esp6/rxrpc`），**无需任何特殊权限、无需第三方工具、无内核日志痕迹**，在所有主流企业发行版默认配置下均可直接利用。这对多租户 KVM 宿主机与 Kubernetes 集群构成容器逃逸级风险：Kubernetes 中 `CAP_NET_ADMIN` 可通过用户命名空间获取，使容器内攻击者可直接操纵宿主内核页缓存。

**落地行动指南**

升级至含 v7.1-rc5 backport 的发行版内核（RHEL/Ubuntu/CloudLinux 均已推送）；临时缓解：`modprobe -r esp4 esp6 rxrpc`；Kubernetes 集群需在 PSS（Pod Security Standards）中明确禁止 `CAP_NET_ADMIN` 的非受控使用；部署 Falco/Sysdig 规则监控 `splice` + `sendmsg` 的异常组合调用。

---

### 3. `[高危 CVE]` `[已出补丁]` 微软六月 Patch Tuesday：史上最大规模补丁日，206 个 CVE 含 6 个 0-day，Hyper-V 与 RDP 均告危

**核心增量/漏洞成因**

2026 年 6 月 9 日微软推送 Patch Tuesday，共修复 **206 个漏洞**（史上最高单次记录，前记录约 150+），其中 33 个评级 Critical，28 个为远程代码执行，包含 6 个零日漏洞。关键条目：

- **CVE-2026-45586**（CTFMON EoP）：Windows Collaborative Translation Framework 本地提权至 SYSTEM，已被公开讨论；
- **CVE-2026-50507**（BitLocker Bypass，CVSS 6.8）：物理访问者可绕过 BitLocker 设备加密，影响所有依赖全盘加密作为唯一防护的笔记本设备策略；
- **CVE-2026-49160**（HTTP.sys DoS，CVSS 7.5）：HTTP/2 协议处理缺陷，无需认证即可远程触发 IIS 服务 DoS；
- Hyper-V 与 Remote Desktop Client 中的 Critical RCE 漏洞为数字化办公与云桌面（AVD）基础设施引入高优先级补丁义务。

**落地行动指南**

优先在 72 小时内修复所有 Critical 及 CISA KEV 涉及的 CVE；Hyper-V 主机与 RDS 服务器需在维护窗口内完成热补丁或重启更新；对 BitLocker 策略执行物理安全补充控制（BIOS 密码 + TPM 2.0 封存）；IIS 服务器建议开启 HTTP.sys 的 HTTP/2 流量速率限制。

---

### 4. `[高危 CVE]` `[CISA KEV]` CVE-2026-20262：Cisco Catalyst SD-WAN Manager 认证后文件写入 0-day，CISA 联邦修复 DDL 已截止

**核心增量/漏洞成因**

CVE-2026-20262（CVSS 6.5，但实际危害远超评分）是 Cisco Catalyst SD-WAN Manager 中的**认证后任意文件写入漏洞**：低权限认证用户可向特定 API 端点发送精心构造的 HTTP 请求，在底层操作系统上创建或覆盖任意文件，进而利用覆盖的配置文件或二进制文件实现 root 提权。Cisco 在 2026 年 6 月初内部发现后确认已有有限范围的在野利用，CISA 于 6 月 15 日将其加入 KEV 目录，联邦 FCEB 机构修复截止日期为 **2026-06-29**（即今日）。这已是 **2026 年第六个被确认在野利用的 Cisco SD-WAN 零日漏洞**，表明攻击者已将 SD-WAN 控制平面列为高价值持续攻击目标。

**落地行动指南**

立即将 SD-WAN Manager 升级至 Cisco 补丁版本；在补丁前，限制管理 API 仅允许受信任 IP 段访问，启用 MFA；审计过去 30 天内 SD-WAN Manager API 的 HTTP 日志，重点关注非常规 PUT/POST 请求（含文件路径参数）；审查 SD-WAN 基础设施管理员账户权限最小化合规状态。

---

### 5. `[高危 CVE]` `[CISA KEV]` CVE-2026-12569：PTC Windchill/FlexPLM PLM 系统 RCE 漏洞在野利用，JSP WebShell 活跃植入

**核心增量/漏洞成因**

CVE-2026-12569（CVSS 9.3）是 PTC Windchill PDMLink 和 FlexPLM 产品中的**反序列化不可信数据**漏洞，攻击者通过网络发送恶意请求即可触发任意代码执行，无需认证。PTC 于 2026-06-25 确认接收到"持续升温的威胁活动报告"——攻击者正在将 JSP WebShell 植入 `/Windchill/login/` 目录，实现持久化命令执行与数据窃取。CISA 于 2026-06-25 将其列入 KEV，联邦修复截止为 **2026-06-28**（已截止），这也是 **PTC 产品首次进入 CISA KEV 目录**。受影响版本覆盖 Windchill 11.0–13.1.x 的多个分支，补丁已覆盖 13.1.1、13.0.2、12.1.2、12.0.2、11.2.1、11.1 M020 和 11.0 M030。

**落地行动指南**

立即部署 PTC 官方补丁；扫描 `/Windchill/login/` 等 Web 根路径下的异常 `.jsp` 文件；检查 Web 服务器进程的子进程异常（WebShell 命令执行特征）；断网隔离不能立即打补丁的实例，制造商与工程设计企业需特别关注，Windchill 通常存储高价值 IP 与产品蓝图数据。

---

### 6. `[云服务 GA]` `[安全架构演进]` Azure 机密虚拟机热迁移（Confidential Live Migration for Intel TDX VMs）：加密边界不中断的跨主机迁移

**核心增量/漏洞成因**

微软于 Build 2026 上宣布 **Azure Confidential Live Migration** 进入技术预览，核心能力是：Intel TDX（Trust Domain Extensions）加密虚拟机在跨物理主机迁移期间，**虚拟机内存内容全程处于 TDX 硬件加密保护下**，宿主机操作系统和 Hypervisor 层均无法读取迁移中的内存明文——这直接解决了传统 KVM 热迁移的"迁移窗口明文暴露"问题。其技术路径依赖 Intel 定义的 **MigTD**（Migration Trust Domain）小型可信模块：MigTD 以独立受信任域运行，验证迁移目标主机是否满足迁移策略（基于远程认证），且 MigTD 的度量值被纳入 TDX VM 的整体认证证据链，可通过 Microsoft Azure Attestation 或第三方 OIDC 认证进行远程审计。计划目标实例系列为 DC/ECesv6 和 DC/ECedsv6，GA 时间表待定。

**落地行动指南**

金融、医疗、政府等合规密集型工作负载应将此能力纳入平台更新路线图；在 TDX 环境中部署的受监管数据处理流水线需更新认证验证逻辑以包含 MigTD 度量值；评估现有 AMD SEV-SNP 虚拟机的迁移策略与 Intel TDX 路线的协同。

---

### 7. `[内核重大更新]` Linux Kernel 7.1 正式发布（2026-06-14）：NTFS 四年重写落地、Intel FRED 默认启用、sched_ext 迈向 per-cgroup

**核心增量/漏洞成因**

Linus Torvalds 于 2026 年 6 月 14 日正式 tag Linux 7.1。关键变更：

- **NTFS 子系统四年重写**（`ntfs3` 全面替代旧驱动）：解决了内核原有 NTFS 驱动在大型文件、压缩分区、日志恢复等场景的正确性与性能历史顽疾；新驱动针对企业双引导与 Windows 共享存储场景影响显著，但同时引入了更广泛的 NTFS 解析攻击面（需关注后续针对 `ntfs3` 的模糊测试披露）；
- **Intel FRED（Flexible Return and Event Delivery）默认启用**：替代传统 IDT 异常/中断分发机制，降低了 ring 切换延迟，并通过硬件强制区分特权级别上下文，从体系结构层面收窄了多种与中断处理相关的侧信道攻击面；
- **sched_ext 子调度器支持（per-cgroup）**：可扩展调度器类开始向 per control group 子调度器演进，允许对容器/Kubernetes Pod 应用差异化调度策略，是解决混部集群延迟与吞吐资源争抢的架构基础；
- **Landlock LSM UNIX 域套接字访问控制扩展**：新增对路径命名 UNIX socket 的访问权控制，增强应用沙箱隔离粒度；移除 i486 架构支持，清理约 14 万行遗留代码。

**落地行动指南**

企业内核升级团队应评估 `ntfs3` 兼容性测试矩阵；容器平台团队关注 sched_ext per-cgroup 能力的早期接入，为 Kubernetes QoS 调度增强做技术预研；所有启用 Intel FRED 的系统应重新评估 BIOS/UEFI 固件兼容性。

---

## 🟢 Tier 3：日常风向与情报速递

- **Linux 7.1-rc6 Linus 指出"本周补丁量大于预期"**：rc6 阶段出现较大规模 late-fix 合并，主要集中在驱动与网络子系统，最终正式版推迟 1 周至 6 月 14 日，释放 v7.1 后 v7.2 合并窗口随即开放。

- **AWS G7 实例 GA（NVIDIA RTX PRO 4500 Blackwell Server GPU）**：相较 G6 系列，AI 推理性能提升 4.6x，图形处理性能提升 2.1x，面向媒体渲染、3D 可视化、VDI 与小规模 AI 推理工作负载，同期推出 Spot 定价。

- **Amazon Bedrock Managed Knowledge Base 正式发布**：提供原生多格式数据解析（Smart Parsing）、多步复杂查询（Agentic Retriever）和企业级 RAG 管道编排，数据不出 AWS VPC，面向合规密集型行业的 RAG 落地消除了数据主权顾虑。

- **AWS 2026 年资本开支承诺达 2000 亿美元**：史上最高单年 Capex 承诺，主要流向数据中心新建扩容、Trainium/Inferentia 自研芯片与 AI 基础设施，反映全球算力军备竞赛持续升温。

- **Azure Intel TDX 机密 VM GA 动量加速**：DC/ECesv5 实例系列已在多区域正式开放，本季度 TDX 工作负载部署量环比翻番，主要客户集中在欧盟金融监管（GDPR/DORA）与医疗数据处理场景。

- **CVE-2026-32193（AKS 路径遍历 RCE，CVSS 8.8）**：Azure Kubernetes Service worker 节点上，低权限容器通过 `hostNetwork` 模式可向宿主机级别服务发送特制请求，实现容器逃逸，Azure 已推送自动修复至受管节点，自管 Kubernetes 需手动更新。

- **Cisco 2026 年第六个 SD-WAN 零日确认**：CVE-2026-20262 是 2026 年内第六个在野利用的 Cisco SD-WAN 漏洞，安全研究人员警告攻击者已将 SD-WAN 控制平面作为高价值定向攻击对象，企业应考虑对 vManage 管理面实施零信任隔离。

- **Qilin 勒索软件组织攻陷 Covenant Health**：约 47.8 万名患者记录泄露（姓名、SSN、医疗记录），事件根源追溯至未受 MFA 保护的 VPN 入口，2026 年医疗行业勒索攻击频率持续高位。

- **ShinyHunters 攻击 American Tower Corporation**：宣称窃取超过 520 万条记录，含客户与地主 PII、资产 GPS 坐标和访问码，电信基础设施数据泄露对无线基站物理安全构成二次风险。

- **Unimed 德国医疗账单服务商遭 APT 攻击**：波及德国多所大学附属医院的账单数据系统，2026 年 4 月攻击但影响持续至今，欧洲医疗 KRITIS 合规压力进一步上升。

- **Google Cloud 2025 年全球宕机 RCA 技术分析持续发酵**：Service Control 代码中的自动化配额更新策略变更缺乏 Feature Flag 保护与错误处理，导致全球 54 项服务 7 小时中断，是云厂商"渐进式发布失守"的经典案例，被多家云工程团队引入 SRE 训练材料。

- **WordPress 6.8.x 安全维护更新推送**：修复若干低危 XSS 与权限绕过问题，与 OptinMonster CDN 供应链事件叠加，建议所有 WordPress 站点同期更新核心版本与插件，并审计 `wp_options` 中的外部 CDN 引用白名单。

- **CISA 6 月 KEV 目录单月新增 8 个条目**（截至 2026-06-29），涵盖 Cisco SD-WAN、PTC Windchill、CVE-2026-46331（Linux 内核）、以及若干历史遗留 ICS/SCADA 漏洞，联邦机构合规压力指数级上升。

- **OpenSSF 发布《软件供应链安全成熟度模型 v2.0》**：回应 Miasma 等 CI/CD 供应链攻击浪潮，新增 "Package Registry Token Rotation" 与 "Build Provenance Attestation" 两个核心成熟度域，SLSA Level 3 认证要求更新正式生效。

- **eBPF 安全工具链持续演进**：Cilium 1.17 与 Tetragon 1.3 同期更新，新增对 Dirty Frag 家族利用行为（异常 `splice` + `sendmsg` 组合）的预制检测规则，建议 Kubernetes 安全团队立即导入规则集并验证覆盖率。

---

*情报来源涵盖：kernel.org、CISA KEV、Red Hat PSIRT、Ubuntu Security、CloudLinux 安全博客、JFrog Security Research、Sansec、Microsoft Threat Intelligence、BleepingComputer、The Hacker News、SecurityWeek、Wiz Blog、Tenable、Socket.dev、PTC Trust Center、Azure Tech Community 等。*
