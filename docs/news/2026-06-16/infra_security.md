# IT 基础设施与网络安全综合情报简报

**情报周期**：2026-06-14 ~ 2026-06-16  
**发布日期**：2026-06-16  
**分类级别**：内部技术参考

---

## 🔴 Tier 1：核心突破、重大变革与范式巨震

---

### 1. `[19年潜伏漏洞 · 在野利用]` CIFSwitch CVE-2026-46243：Linux 内核 CIFS 子系统本地提权，可单命令获 root

**事件/架构全景**

CVE-2026-46243（研究界命名 "CIFSwitch"）是一枚埋伏于 Linux 内核 CIFS/SMB 客户端子系统整整 19 年的本地特权提升漏洞，已被多个发行版（Red Hat、Rocky Linux、Ubuntu、CloudLinux）确认可在完全打补丁的系统上以普通用户权限单命令获取 root shell。该漏洞已被 Greg Kroah-Hartman 纳入 2026 年 6 月首批 stable 批次进行上游修复，并触发全系列稳定内核（7.0.11、6.18.34、6.12.92、6.6.142、6.1.175、5.15.209、5.10.258）的同步回植（backport）。

**底层机制/漏洞成因分析**

漏洞根因位于 `fs/smb/client/cifs_spnego.c`，该文件注册了 `cifs.spnego` key 类型但**未对 key 创建请求的来源进行内核态校验**。`cifs.spnego` 的 key 描述字段（`pid`、`uid`、`creduid`、`upcall_target`）被 `cifs.upcall` 用户空间助手当作可信的内核原始输入处理，而实际上任何低权限用户均可通过标准系统调用 `add_key(2)` 或 `request_key(2)` 伪造这些字段。攻击者构造一个携带恶意字段的 `cifs.spnego` key 请求，诱使内核以 root 身份启动 `cifs.upcall`；后者在切换回攻击者命名空间并进行 NSS 查询之前，会以 root 权限加载攻击者预置的恶意 NSS 共享库，从而完成本地 root 完整提权。攻击链需要三个条件同时成立：加载了 `cifs` 内核模块、安装了 `cifs-utils` 用户空间包、且 `/etc/request-key.conf` 中存在 `cifs.spnego` 规则。三者缺一则攻击链断裂。

**生产架构影响与加固指南**

任何运行共享租户工作负载（CI/CD Runner、容器宿主机、多用户 HPC 集群）的 Linux 系统均面临即时威胁，本地代码执行即意味着全系统沦陷。上游修复提交为 `3da1fdf4efbc`（"smb: client: reject userspace cifs.spnego descriptions"）。**立即行动**：
- 优先将内核升级至上述修复版本；若内核无法立即替换，执行以下任一操作可阻断攻击链：`rmmod cifs` / `apt remove --purge cifs-utils` / 删除 `/etc/request-key.conf` 中的 `cifs.spnego` 规则。
- 在容器环境中通过 `seccomp`/`AppArmor` 限制 `add_key(2)` 和 `request_key(2)` 的调用，可作为纵深防御层。
- 将 KEV 加固截止期限（Red Hat RHSB-2026-005 要求联邦机构 72 小时内完成缓解）纳入应急变更流程。

---

### 2. `[供应链攻击]` Miasma / Mini Shai-Hulud：npm 生态系统双轨定向污染，117K 周下载量遭植入凭证窃取蠕虫

**事件/架构全景**

2026 年 5 月至 6 月，威胁行为体 TeamPCP 实施了两阶段精确供应链攻击，覆盖 npm 与 PyPI 双生态，最终形成持续扩散的凭证窃取蠕虫态势。第一波（5 月 11 日）以 TanStack 的 GitHub Actions CI 流水线为突破口，在 6 分钟内向 42 个 `@tanstack/*` 包推送 84 个恶意制品，攻击工具命名为 "Mini Shai-Hulud"。5 月 12 日，TeamPCP 在 GitHub 和 BreachForums 公开 Mini Shai-Hulud 源码，激活了第三方仿制攻击浪潮。6 月 1 日，Wiz Research 识别到以 Mini Shai-Hulud 为基底的新变种 "Miasma" 已植入 `@redhat-cloud-services` npm 命名空间下 32 个包的 32 个版本，该命名空间周下载量约 117,000 次，GitHub 随即临时禁用了受影响的 Microsoft 仓库。

**底层机制/漏洞成因分析**

攻击向量：TanStack 第一波利用了 CI/CD 流水线对第三方 Action 的过度信任与 npm token 的宽泛 publish 权限；Miasma 第二波则绕过了 Red Hat 官方的代码审查机制，直接以维护者身份令牌（推测为窃取或泄露）向已信任命名空间推送恶意版本，而不修改对应的 GitHub 源代码仓库——这造成了"制品哈希"与"源码仓库"的根本性脱节，使常规 SCM 审计完全失效。Miasma payload 在安装时（`postinstall` 钩子）静默执行，从开发者环境中收割：GitHub token、Azure/GCP 服务账号凭证、SSH 私钥、CI/CD 令牌（GitHub Actions、GitLab、CircleCI）、`~/.aws/credentials`、`~/.kube/config`，并通过 HTTPS beacon 外传，随后在宿主机上实现蠕虫自繁殖传播。

**生产架构影响与加固指南**

所有在 2026-06-01 之后安装过 `@redhat-cloud-services/*` 任意包的 CI/CD 环境、开发者工作站及生产构建系统均应视为已失陷并完整轮换凭证。**立即行动**：
- 审计 `package-lock.json` / `yarn.lock`，隔离对应版本的 `@redhat-cloud-services` 包；在构建系统清洁前，不得使用相关凭证访问生产环境。
- 强制推行 **npm provenance attestation**（SLSA Level 3），要求所有受信包在发布时附带可验证的构建来源。
- 为 CI/CD token 实施最小权限与短期有效期（TTL ≤ 1 小时），并在 SIEM 中对 `postinstall` 脚本执行网络连接行为设立告警规则。
- 对 `@redhat-cloud-services` 命名空间下全部有效版本执行哈希与来源交叉核验，确认制品与源码仓库对应关系。

---

### 3. `[0-day 在野利用]` Check Point VPN CVE-2026-50751（CVSS 9.3）：IKEv1 认证绕过被 Qilin 勒索软件团伙武器化

**事件/架构全景**

2026 年 6 月 8 日，Check Point 发布 CVE-2026-50751 安全公告，该漏洞为其远程访问 VPN（Remote Access VPN）、Mobile Access 及 Spark 防火墙产品中的**严重认证绕过漏洞**（CWE-287），CVSS 评分 9.3。活跃利用最早追溯至 2026 年 5 月 7 日，6 月初呈现快速升温态势。6 月 8 日，Qilin 勒索软件附属团伙被确认正以此漏洞为初始访问向量发动定向攻击。6 月 12 日，watchTowr Labs 发布技术深度分析并附带概念验证（PoC）利用代码，进一步降低了攻击门槛。

**底层机制/漏洞成因分析**

漏洞根因在于 IKEv1 握手协议的**证书校验逻辑缺陷**：攻击者通过在 IKEv1 协商阶段发送携带伪造 Vendor ID payload 的定制化数据包，可操控认证标志位（authentication flags）。受影响代码未能正确验证客户端证书与预期身份的绑定关系，导致攻击者在**无需有效用户密码**的情况下即可建立完整的 VPN 会话，等效于获得合法的远程网络接入权限。该漏洞仅影响启用了**已废弃的 IKEv1 密钥交换协议**的配置，IKEv2 部署不受影响。伴生漏洞 CVE-2026-50752 进一步涉及证书校验旁路，两者组合可实现更隐蔽的攻击链。

**生产架构影响与加固指南**

VPN 网关的认证绕过直接等同于企业边界防御失守——攻击者获取 VPN 接入后即可横向移动至内网，勒索软件部署窗口随之开启。全球已有数十家组织确认遭受攻击，PoC 公开后攻击面已大幅扩大。**立即行动**：
- **立即应用** Check Point 发布的 hotfix（修复 CVE-2026-50751 及 CVE-2026-50752），不接受任何延期。
- 若无法立即打补丁，**禁用 IKEv1 支持**并强制迁移至 IKEv2。
- 检查 Check Point 提供的 IoC（Indicator of Compromise）列表，对 VPN 日志进行全量回溯分析（时间窗口覆盖 5 月 7 日以来）。
- 对所有 VPN 接入账号强制执行多因素认证（MFA），并在 EDR 侧对 VPN 网关发起的异常横向连接设立即时告警。

---

### 4. `[多零日在野利用]` Microsoft 六月补丁星期二：记录在册 208 枚 CVE，YellowKey / GreenPlasma / MiniPlasma 三零日集群

**事件/架构全景**

2026 年 6 月 9 日，微软发布其历史上规模最大的单次补丁批次，涵盖 **208 枚 CVE**，其中 33 个评级为严重（Critical），28 个为远程代码执行（RCE）类型。本批次最具关注价值的是三个被研究人员 "Nightmare Eclipse" 集中披露的本地权限提升/安全特性绕过零日漏洞组合——YellowKey、GreenPlasma、MiniPlasma——三者均已在 6 月 9 日补丁发布前于真实环境中被验证可利用。

**底层机制/漏洞成因分析**

- **CVE-2026-45585（YellowKey）**：Windows 恢复环境（WinRE）中内置的 BitLocker 绕过后门。攻击者持有物理接触权限时，可通过预制 USB 设备在未打补丁的 Windows 11 / Server 2022/2025 系统上完全绕过 BitLocker 全盘加密，直接访问受保护磁盘内容。
- **CVE-2026-45586（GreenPlasma）**：Windows 协作翻译框架（CTFMON）中的本地权限提升漏洞（CVSS 7.8），允许标准用户在已完全更新的系统上提权至 SYSTEM，无需任何特殊前置条件。
- **CVE-2020-17103（MiniPlasma）**：Windows Cloud Filter 驱动（`cldflt.sys`）中已知漏洞的新型利用变体，ThreatLocker 实验室证实其可在 Windows 11 最新 2026 年 5 月补丁级别系统上实现标准用户到 SYSTEM 的完整提权。

此外，本批次还覆盖了 55 个 RCE 漏洞和 CVE-2026-50507（BitLocker 安全特性绕过）等高危项目。

**生产架构影响与加固指南**

MiniPlasma 远程可利用性意味着任何允许低权限代码执行的系统（如 RDP 多用户环境、VDI 基础设施、共享 Windows 工作站）都面临即时提权风险；YellowKey 对于部署 BitLocker 作为唯一数据保护层的端点安全架构构成根本性威胁。**立即行动**：
- 将 Windows 11 / Server 2022/2025 环境的六月补丁部署列为 P0 应急任务，72 小时内完成全覆盖。
- 对 MiniPlasma 额外部署应用白名单（如 ThreatLocker 或 AppLocker），阻断 `cldflt.sys` 的异常调用路径。
- 审查 BitLocker 部署是否配合 TPM + PIN 双因素绑定，单纯依赖 TPM 自动解锁的配置在 YellowKey 场景下形同虚设。
- 在 SIEM 中对 CTFMON 进程的异常子进程生成行为设立即时告警。

---

### 5. `[RaaS 生态巨震]` The Gentlemen 勒索软件即服务集团：478 名受害者、跨平台打击能力、内部泄露曝光完整作战手册

**事件/架构全景**

The Gentlemen RaaS（勒索软件即服务）集团在 2026 年以史上最快速度完成规模扩张，仅前五个月即声称攻击超过 328 家组织、覆盖 66 个国家，占同期全球勒索软件受害总量的约 10%，累计受害者数量已达 478 家。2026 年 5 月，该集团自身遭到入侵，内部聊天记录和作战数据库外泄，研究人员得以对其运营模式实施前所未有的深度分析——Check Point Research 在泄露数据中识别出超过 1,570 个关联受害实体。

**底层机制/漏洞成因分析**

The Gentlemen 部署了专门针对 **Windows、Linux、ESXi 宿主机、NAS 设备及 BSD 系统**的独立加密器，具备一次攻击完整覆盖企业全栈基础设施的能力——这使其区别于仅针对单一平台的传统勒索软件。初始访问向量优先锁定暴露的边界防御设施（VPN 网关、远程访问入口、防火墙管理门户），并持续跟踪新鲜漏洞（内部通讯显示其主动评估 CVE-2025-32433、CVE-2025-33073），同时叠加 Cisco 边界设备漏洞利用、NTLM 中继攻击及 OWA/M365 凭证撞库。该集团以 **90% 附属分成比例**吸引高技术能力攻击者，形成强力的招募飞轮效应，规模扩张速度远超历史上任何 RaaS 集团。

**生产架构影响与加固指南**

ESXi 加密器的存在意味着单一 ESXi 宿主机沦陷即可导致整个虚拟化集群的批量加密，传统的"在虚拟机内部安装 EDR"防御策略在此场景下完全失效。**立即行动**：
- 在 ESXi 宿主机层面部署专用安全监控（如 VMware vSphere Lifecycle Manager 配合 CIS Benchmark 加固），限制 API 对外暴露。
- 对所有边界 VPN/防火墙管理接口实施强制 MFA 和基于 IP 白名单的访问控制，遵循"零暴露面"原则。
- 实施 3-2-1-1-0 备份策略（3份备份、2种介质、1份异地、1份离线不可变、0次恢复失败），离线备份必须与生产网络实施物理隔离。
- 参照 Check Point Research 发布的 The Gentlemen IoC 列表，对过去 6 个月的边界设备日志进行威胁追踪（Threat Hunting）。

---

## 🟡 Tier 2：关键演进、生产工程实践与高危漏洞

---

### 1. `[内核重大发布]` Linux Kernel 7.1 GA：FRED 默认启用、NTFS 四年重写落地、Landlock 覆盖 UNIX 域套接字

**核心增量/漏洞成因**

2026 年 6 月 14 日，Linus Torvalds 正式发布 Linux Kernel 7.1。三项核心技术变动值得重点关注：①**Intel FRED（Flexible Return and Event Delivery）默认启用**，专为 Panther Lake 及后续 Diamond Rapids 设计，从硬件层面重构 x86 的中断、异常和系统调用的进出内核路径，消除大量历史遗留的 x86 特例代码，早期 Phoronix 微基准测试显示上下文切换密集型负载提升 5~12%。②**NTFS 驱动完整重写**（基于 Paragon NTFS3，历时 4 年迭代），写入性能已接近 Windows 原生速度，消除了 5.15 版本引入后长期存在的边缘场景 Bug，发行版采用率有望大幅提升。③**Landlock LSM 扩展至 UNIX 域套接字**，通过新的 LSM hook 引入路径名 UNIX 套接字访问权限控制，沙箱化工作负载和容器环境现可细粒度限制进程间 IPC 通道，显著缩小同主机进程横向通信攻击面。

**核心工程思想/预期差**

FRED 最关键的安全意义在于其**减少了内核入口/出口路径的代码量与复杂度**——历史上大量内核漏洞（如 Spectre/Meltdown 缓解中的 SWAPGS 错误）正是源于这些边界处的处理逻辑错误，FRED 的简化降低了类似问题出现的概率。Landlock UNIX 套接字支持填补了进程沙箱化的关键缺口，但需注意新权限模型要求应用程序主动适配（opt-in），存量容器应用不会自动获益。

**落地行动指南**

- 评估并计划 Kernel 7.1 升级路径，优先部署于 Panther Lake 硬件和高上下文切换负载（微服务、高频 syscall 应用）环境。
- 在支持 NTFS 工作负载的 Linux 节点上切换至 7.1 内置 NTFS3 驱动，停用 `ntfs-3g` FUSE 层以降低攻击面并提升性能。
- 为新部署的沙箱化服务（容器、安全关键守护进程）配置 Landlock 域套接字访问策略（参考 `landlock(7)` man page 新增的 socket 权限位）。

---

### 2. `[高危 CVE · 链式利用 · 部分无补丁]` Cisco SD-WAN 双零日链式攻击：CVE-2026-20182 + CVE-2026-20245，完整夺取 SD-WAN 控制面

**核心增量/漏洞成因**

两个漏洞形成完整攻击链：**CVE-2026-20182**（Catalyst SD-WAN Controller 认证绕过）利用对等认证机制的缺陷，允许远程攻击者免认证登录为高权限内部账户；**CVE-2026-20245**（SD-WAN Manager CLI 命令注入，CISA 于 6 月 9 日纳入 KEV）允许已具备 netadmin 权限的远程攻击者通过上传精心构造的文件执行任意命令并提权至 root。两者组合构成"无需凭证→完整 SD-WAN fabric root 控制"的完整链条，已被安全机构定性为"高度复杂威胁行为体"所为。CVE-2026-20245 **目前无可用补丁，亦无官方 workaround**。这是 2026 年内 Cisco SD-WAN 被利用的第 **7 个** zero-day。

**核心工程思想/预期差**

SD-WAN Controller/Manager 作为整个广域网控制面的单点，一旦沦陷即意味着攻击者可向全量 WAN Edge 设备推送恶意配置变更——这是比传统防火墙旁路更具破坏力的基础设施级控制权转移。联邦机构已收到 CISA 的两周整改通知（CISA KEV 截止 6 月 23 日）。

**落地行动指南**

- 立即将 Catalyst SD-WAN Controller 升级至 CVE-2026-20182 已修复版本；CVE-2026-20245 尚无补丁，应立即将 SD-WAN Manager CLI 访问严格限制至跳板机（bastion host），并实施细粒度 ACL 控制。
- 对 SD-WAN Manager 的所有配置推送操作启用带外审计（out-of-band audit log），实时监控异常配置变更。
- 将 SD-WAN 管理接口从公网隔离（不得暴露于互联网），仅允许来自明确 IP 范围的管理连接。

---

### 3. `[高危 CVE · 已出补丁 · CISA KEV]` Chrome V8 CVE-2026-11645（CVSS 8.8）：第五次 2026 年 Chrome 零日，浏览器内任意代码执行

**核心增量/漏洞成因**

CVE-2026-11645 是 Chrome V8 引擎（JavaScript/WebAssembly 执行器）中的越界读写（Out-of-Bounds Read and Write）漏洞，CVSS 8.8。攻击者通过诱使用户访问特制 HTML 页面即可远程触发，获得在浏览器进程上下文内的任意代码执行能力。沙箱逃逸需链接附加利用，但已有相关活跃利用报告。该漏洞于 6 月 9 日被 CISA 纳入 KEV 目录，是 2026 年内第五个被在野利用的 Chrome zero-day，也是 2026 年内第二个直接针对 V8 的 zero-day。修复版本为 **Chrome 149 稳定版**（6 月 8 日发布，持续滚动推送）。Chromium 内核同样影响 Microsoft Edge 和 Opera。

**核心工程思想/预期差**

V8 的 JIT 编译路径（JavaScript 即时编译）历来是浏览器漏洞的高发区，2026 年内双 V8 zero-day 表明该引擎在 Wasm 特性扩展过程中引入了新的内存安全隐患。企业中大量老版本 Chrome/Edge 通过托管锁定的情况（管理员未开启自动更新）是主要暴露面。

**落地行动指南**

- 强制将企业内所有 Chrome/Edge/Chromium 内核浏览器更新至当前稳定版（Chrome 149+）；通过 GPO 或 SCCM 推送紧急更新策略，不得等待标准更新窗口。
- 在 Web Proxy 层对已知恶意域名实施封锁，并启用 DNS 安全过滤作为纵深防御。
- 将"浏览器版本合规检查"纳入端点安全基线，不满足版本要求的设备拒绝接入企业网络。

---

### 4. `[高危 CVE · 永久无补丁 · CISA KEV]` Arista EOS CVE-2026-7473：网络隧道协议盲解封装，CISA 要求两周整改但厂商拒绝发布补丁

**核心增量/漏洞成因**

CVE-2026-7473（CVSS 6.9）影响 Arista 7020R、7280R/R2、7500R/R2 系列交换机，漏洞成因为：当设备配置了隧道解封装功能（VXLAN、decap-groups 或 GRE 隧道接口）时，**交换机不验证接收到的隧道报文的协议类型**，导致其会错误解封装并转发目的 IP 匹配解封装 IP 的任意隧道协议报文，攻击者可借此绕过网络分段控制注入流量或实施流量劫持。该漏洞已被 CISA 纳入 KEV 目录（6 月 9 日），联邦机构须于 6 月 23 日前完成整改。**Arista 明确声明不计划发布软件修复**，原因是修复可能破坏现有部署配置。

**核心工程思想/预期差**

厂商"永久不修复"的立场在关键网络基础设施领域极为罕见，意味着运营者必须完全依赖手动缓解措施（ACL）承担长期运营风险，且随着设备版本迭代漏洞可能持续存在数年。

**落地行动指南**

- 立即在 Arista 受影响设备的上游设备（路由器/防火墙）或设备本身部署严格的 ACL，过滤非预期隧道协议报文（仅允许明确配置的隧道类型的报文到达解封装接口）。
- 将受影响的 Arista 设备型号纳入资产风险台账，制定长期替换或配置隔离计划。
- 向上游供应商施压，要求在下一代设备中解决此类协议验证设计缺陷。

---

### 5. `[内核子系统更新]` Linux Stable 系列全量同步：CIFSwitch 补丁回植、Fragnesia 漏洞修复、多系列并行维护

**核心增量**

2026 年 6 月初，Greg Kroah-Hartman 完成了覆盖 7 个活跃维护分支的同步发布：7.0.11、6.18.34、6.12.92、6.6.142、6.1.175、5.15.209、5.10.258。本批次的两个核心修复为：① **CIFSwitch（CVE-2026-46243）**回植到全部受支持分支；② **Fragnesia 漏洞**（6.1、5.15、5.10 的小型定向更新），涉及特定网络分片处理路径的安全修正。当前 Linux 内核官方维护的 LTS 系列横跨 5.10~7.0，企业采用周期长达 5 年的 LTS 版本（5.10 EOL 为 2026 年底）须在补丁到来后立即应用。

**落地行动指南**

- 将 CIFSwitch 补丁列为 P0，优先于下一个维护窗口前推送至全部生产内核；对于无法立即升级的节点，执行前述 CIFS 模块级缓解措施。
- 核实 5.10 系列的终止支持计划，制定 LTS 迁移路线图（优先向 6.6 LTS 或 6.12 LTS 迁移）。

---

### 6. `[性能基建演进]` Intel Cache-Aware Scheduling for Linux 2026：CFS 调度器感知 L2/L3 缓存拓扑，多线程负载提升 10~30%

**核心增量**

Intel 工程师向 Linux CFS（Completely Fair Scheduler）提交的 Cache-Aware Scheduling（CAS）更新已完成内核集成评审，核心变动是：调度器能够精确区分共享 L2 缓存的核心组与仅共享 L3 缓存的核心组，并在任务调度时优先将互相高频通信的线程迁移至共享低级别缓存的兄弟核心，减少跨 NUMA 节点的缓存失效（cache miss）开销。早期基准测试显示多线程密集型应用（HPC、数据库 OLAP、容器微服务集群）性能提升幅度为 **10%~30%**，Intel 与 AMD CPU 架构均受益。调度器还引入了实时工作负载监控能力，可动态调整任务放置策略。

**落地行动指南**

- 关注该特性进入主线版本的时间节点，并优先在高核密度服务器（32C+ 以上）和多 NUMA 节点架构的计算集群上进行 A/B 性能测试。
- 注意 CAS 引入后，对安全敏感的任务隔离策略（如 L1TF/MDS 缓解中的 CPU 亲和性绑定）可能需要重新评估与配置。

---

### 7. `[威胁情报]` CISA KEV 六月批次完整梳理：涵盖网络设备、浏览器、Linux 内核与移动端的全栈多维利用态势

**核心增量**

2026 年 6 月 CISA 已分三批向 KEV 目录新增 6 枚漏洞，全部具有已知在野利用证据，联邦机构须遵循两周内完成整改的强制要求：

| 日期 | CVE | 产品 | 类型 |
|------|-----|------|------|
| 06-02 | CVE-2022-0492 | Linux 内核 | 错误认证 |
| 06-02 | CVE-2025-48595 | Android Framework | 整数溢出 |
| 06-03 | CVE-2026-45247 | Mirasvit Magento 插件 | 不受信数据反序列化 |
| 06-09 | CVE-2026-7473 | Arista EOS | 隧道协议验证缺失 |
| 06-09 | CVE-2026-11645 | Google Chromium V8 | 越界读写 |
| 06-09 | CVE-2026-20245 | Cisco SD-WAN Manager | 命令注入 |

**落地行动指南**

- 将上述 CVE 清单导入漏洞管理平台，设定 P0 整改优先级，跨端点、网络设备、移动终端、Web 应用层统一推进修复。
- CVE-2022-0492 的持续在野利用表明攻击者正在针对存量未升级内核（尤其是容器逃逸场景）发动长尾攻击，需核查所有容器宿主机的内核版本。

---

## 🟢 Tier 3：日常风向与情报速递

- **Linux 7.2 合并窗口**：Linus Torvalds 计划 6 月 28 日发布 Linux 7.2-rc1，因其出行行程，合并窗口调度可能存在小幅延迟。

- **Linux 6.18.34 稳定维护版**：作为本月 stable 主力版本之一正式发布，含 CIFSwitch 修复及多项驱动稳定性补丁，生产环境应优先采用。

- **GreenPlasma CVE-2026-45586（CTFMON EoP）**：CTFMON 本地提权已由微软六月补丁修复，CVSS 7.8，本地利用，已有 PoC 流出，应验证补丁覆盖情况。

- **YellowKey CVE-2026-45585（BitLocker/WinRE）**：BitLocker 绕过漏洞补丁已随六月 PT 分发，物理接触场景下的数据中心入侵（"邪恶女佣"攻击）现有可行缓解。

- **Microsoft Defender for Open-Source Databases GA on AWS RDS**：微软 Defender 已从 6 月 1 日起对 AWS RDS（Aurora PostgreSQL/MySQL、PostgreSQL、MySQL、MariaDB）开始计费，多云 CSPM 用户应核查许可证配置。

- **Europol 捣毁 AudiA6 加密货币洗钱服务**：Europol 成功打掉专为勒索软件团伙及网络犯罪组织提供加密洗钱服务的 AudiA6 地下平台，对 The Gentlemen 等 RaaS 集团的资金流转形成一定打击。

- **TeamPCP TanStack CI 投毒事件回顾（5 月 11 日）**：GitHub Actions CI 流水线权限配置不当是此次攻击的根本性入口，建议全面审计第三方 Action 的权限范围，实施 Actions 的 SHA 锁定（`uses: owner/repo@sha` 而非 `@latest`）。

- **Android CVE-2025-48595 整数溢出**：CISA 6 月 2 日纳入 KEV，影响 Android Framework 层，攻击者可实现本地代码执行，应推送 Android 最新安全补丁（2026 年 5 月或更新安全级别）。

- **Magento/Adobe Commerce CVE-2026-45247 反序列化**：CISA 6 月 3 日纳入 KEV，电商平台运营者需立即升级 Mirasvit Full Page Cache Warmer 插件或卸载，避免服务器端 RCE。

- **The Gentlemen 内部聊天记录泄露**：泄露内容证实该集团正系统性跟踪最新公开漏洞并将其纳入武器库，建议将"新披露高危漏洞 → 72 小时内加固"设为运营标准 SLA。

- **2026 年 Cisco SD-WAN 第七个 zero-day**：连续七个被利用的 zero-day 暴露了 SD-WAN 产品线长期存在的代码质量与安全开发流程问题，企业应将 Cisco SD-WAN 纳入高风险供应商名单并启动多厂商冗余评估。

- **FBI 网络犯罪损失突破 210 亿美元（2025 年度）**：FBI IC3 报告显示 2025 年美国网络犯罪经济损失达创纪录 210 亿美元，关键基础设施攻击频次显著上升，政策层面已推动 CIRCIA 强制事件报告机制加速落地。

- **GKE on AWS/Azure 进入维护模式**：谷歌宣布 GKE on AWS 与 GKE on Azure 将于 2027 年 3 月 17 日正式终止服务，受影响客户应制定迁移至 Anthos 或原生 EKS/AKS 的过渡计划。

- **Chrome 149 稳定版全球推送**：包含 V8 CVE-2026-11645 修复及其他安全更新，Chromium 内核浏览器（Edge、Opera 等）厂商已跟进发布对应补丁，务必确认企业内全品类浏览器已完成更新。

- **Intel FRED 安全意涵**：FRED 对 x86 内核入口路径的简化不仅是性能优化，从防御层面也降低了类似 Spectre-v2（IBRS bypass）等依赖内核入口复杂性的侧信道攻击面，应在安全架构评审中予以积极评分。
