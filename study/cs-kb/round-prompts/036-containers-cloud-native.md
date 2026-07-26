# Round3c · L5-13 容器与云原生 · 报告 prompt（P2）

```text
[P2] L5-13·大主题1 容器 vs VM 与隔离模型 — 报告prompt
任务：为「L5-13·大主题1 容器 vs VM 与隔离模型」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 标题层级固定：# 大主题（=报告标题）→ ## 小主题（=章节）→ ### 内容项（每项一节）。
- ### 下不写「核心概念：」「辅助说明：」等任何标签，直接正文、靠空行分段。每个 ### 必须给出：①核心说明（是什么/定义/关键结论，正确）；②充分的辅助说明（面向初学者把它讲懂所需——直觉/为什么/最小例子/易错点/与前后关联；必需，不是点缀，可多段）。
- 命令 / 配置片段 / 公式独占块级行，不内联埋进句子。
- 教辅口吻；广度优先——把每个内容项讲到初学者能懂即止，不做专家级纵深（不写长篇机制深挖/推导/设计权衡长论）。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可直接读 round3b-subtopics/g12-engineering-delivery.md 中 L5-13 大主题 13-1（小主题 13-1.1~13-1.4）取完整条目。本 prompt 已自足点名如下：
- ## 13-1.1 hypervisor 整机虚拟化 vs 共享内核：独立内核/整机 vs 共享宿主内核的进程隔离（跨 L5-05 虚拟化）。
- ## 13-1.2 容器三要素：进程 + namespaces（视图）+ cgroups（资源）的组合。
- ## 13-1.3 “容器不是轻量 VM”辨析：无独立内核，隔离强度与攻击面差异。
- ## 13-1.4 隔离谱系与用例：进程/容器/微 VM/VM 的隔离-开销权衡。
- 机制根衔接 L4-01 OS：namespaces/cgroups 本是 OS 资源隔离原语，本课是其工程落地，务必在正文点出这一衔接。

【验证纪律（强制）】
- 多来源比对：每个内容项 ≥2 个独立来源交叉核对，优先一手（OCI 运行时/镜像规范、Kubernetes 官方文档、Linux man page namespaces(7)/cgroups(7)）；来源冲突时两边都记、点明分歧、不和稀泥。
- 本机 demo 可选：如便宜可用 unshare / 读 /sys/fs/cgroup 加固某结论（本沙箱 Docker daemon 不可达；cgroup 本机为 v1 hybrid），不逐项强制。
- 章节末集中列来源与时效（不逐项脚注）：锚点 + 版本 + 核实日期；不编造数值/默认值/版本号，未证实标「待核」。
- K8s 与各工具版本处一律标「⚙演进快·锚版本」并写明锚定版本。

【权威锚点清单】
- Linux man page：namespaces(7) / cgroups(7) / unshare(1) / user_namespaces(7)（强·一手）。
- OCI Runtime Spec v1.3.0（2025-11-04）；OCI Image/Distribution Spec v1.1.0（2024-02-15）（强·一手）。
- Kubernetes 官方文档 kubernetes.io/docs/concepts/，当前稳定 v1.36（⚙演进快·锚版本）。

【粒度判定】先判「本大主题 1 份报告 or 拆 N 份 + 理由」，再动笔。

【落盘】自己 Write 到 findings/036-containers-cloud-native.md（报告抬头带可 grep 基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题2 Linux namespaces（视图隔离） — 报告prompt
任务：为「L5-13·大主题2 Linux namespaces（视图隔离）」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 标题层级固定：# 大主题 → ## 小主题 → ### 内容项。
- ### 下不写任何标签，直接正文分段。每个 ### 必须给：①核心说明（正确的是什么/定义/结论）；②充分辅助说明（初学者读懂所需的直觉/为什么/最小例子/易错点/关联；必需，可多段）。
- 命令 / 配置片段 / 公式独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics/g12-engineering-delivery.md 中 L5-13 大主题 13-2（小主题 13-2.1~13-2.5）。本 prompt 自足点名：
- ## 13-2.1 namespaces 总览：pid/net/mnt/uts/ipc/user/cgroup/time 八种（namespaces(7)）。
- ## 13-2.2 PID / mount ns：进程视图与挂载视图隔离；可 unshare --pid --mount --fork 实证。
- ## 13-2.3 network ns：独立网络栈/接口；unshare --net 或 ip netns 实证。
- ## 13-2.4 user ns 与 rootless：uid/gid 映射使非特权用户获容器内 root（user_namespaces(7)）；unshare --user --map-root-user 实证。
- ## 13-2.5 UTS/IPC/cgroup/time ns：主机名/域名、SysV IPC、cgroup 视图、时钟偏移隔离。
- 机制根衔接 L4-01 OS：namespaces 是 OS 提供的视图隔离原语，本课讲其容器工程用途。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（Linux man page namespaces(7)/user_namespaces(7)/unshare(1)、OCI 运行时规范）；冲突两边都记、点明分歧。
- unshare / cgroup 本机 demo 可选：便宜可用则跑（本沙箱 Docker daemon 不可达，unshare 可用），贴真实命令与输出；不强制。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；不编造，未证实标「待核」。
- K8s 与工具版本处标「⚙演进快·锚版本」。

【权威锚点清单】
- Linux man page：namespaces(7) / unshare(1) / user_namespaces(7) / cgroups(7)（强·一手）。
- OCI Runtime Spec v1.3.0（2025-11-04）（强·一手，容器化落地佐证）。
- Kubernetes 官方文档 v1.36（⚙演进快·锚版本，仅在需要时回指）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题3 cgroups（资源限额，v1 vs v2） — 报告prompt
任务：为「L5-13·大主题3 cgroups（资源限额，v1 vs v2）」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / 配置片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-3（小主题 13-3.1~13-3.5）。自足点名：
- ## 13-3.1 cgroup 概念：控制器/层级/任务模型（cgroups(7)）。
- ## 13-3.2 cpu 控制器：份额(shares/weight) vs 配额(quota/period) 限流；读 /sys/fs/cgroup 实证。
- ## 13-3.3 memory 控制器与 OOM：内存上限触发 OOM kill/回收。
- ## 13-3.4 pids / io 控制器：进程数上限与块设备 I/O 权重/上限。
- ## 13-3.5 v1 hybrid vs v2 统一层级：本机 v1 hybrid（多层级）vs v2 单一统一层级 + cgroup.controllers；须做 delta 分账——明写「本机基线 v1、v2 为现代默认」；cat /proc/filesystems + 挂载点可实证。
- 机制根衔接 L4-01 OS：cgroups 是 OS 资源管理原语，本课讲其对容器资源限额的落地。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（Linux man page cgroups(7)、内核 cgroup-v1/v2 文档、OCI 运行时规范）；v1/v2 差异冲突两边都记、点明分歧。
- cgroup 本机 demo 可选：读 /sys/fs/cgroup、cat /proc/filesystems 加固结论（本机 cgroup v1 hybrid），贴真实输出；不强制。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；具体默认值/文件名不编造，未证实标「待核」。
- K8s 与工具版本处标「⚙演进快·锚版本」。

【权威锚点清单】
- Linux man page：cgroups(7)（强·一手）；内核 Documentation/admin-guide/cgroup-v1、cgroup-v2。
- OCI Runtime Spec v1.3.0（2025-11-04）（资源限额字段映射佐证）。
- Kubernetes 官方文档 v1.36（⚙演进快·锚版本，requests/limits 回指时用）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题4 OCI 镜像格式与分层/union FS — 报告prompt
任务：为「L5-13·大主题4 OCI 镜像格式与分层/union FS」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / 配置片段 / manifest 片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-4（小主题 13-4.1~13-4.4）。自足点名：
- ## 13-4.1 镜像 manifest 与 config：manifest 索引层、config 记录运行参数（OCI Image Spec v1.1.0）。
- ## 13-4.2 层与内容寻址：每层为 tar，按 digest 内容寻址去重。
- ## 13-4.3 union FS / OverlayFS CoW：只读层叠加 + 可写层写时复制；本机可尝试挂 overlayfs 观察（待核权限）。
- ## 13-4.4 不可变镜像与可复现构建：镜像不可变作可靠部署基座。
- 衔接：内容寻址与 L4-01 文件系统、L4-06 Git 对象模型（同为内容寻址）可点一句关联。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（OCI Image Spec v1.1.0、Linux overlayfs 文档/man）；冲突两边都记、点明分歧。
- 本机 demo 可选：挂 overlayfs 观察 CoW（overlayfs 挂载权限待核），不强制。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；manifest 字段/media type 不编造，未证实标「待核」。
- 工具/规范版本处标「⚙演进快·锚版本」（OCI 规范版本、overlayfs 行为）。

【权威锚点清单】
- OCI Image Spec v1.1.0（2024-02-15）（强·一手，manifest/config/layer 定义）。
- OCI Distribution Spec v1.1.0（2024-02-15）（digest 引用佐证）。
- Linux overlayfs 内核文档 / man（CoW 机制一手）。
- Kubernetes 官方文档 v1.36（⚙演进快·锚版本，镜像拉取语境）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题5 OCI 运行时与容器运行时链 — 报告prompt
任务：为「L5-13·大主题5 OCI 运行时与容器运行时链」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / config.json 片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-5（小主题 13-5.1~13-5.4）。自足点名：
- ## 13-5.1 filesystem bundle + config.json：rootfs + 配置构成运行时 bundle（OCI Runtime Spec v1.3.0）。
- ## 13-5.2 容器生命周期状态机：creating/created/running/stopped 状态转换。
- ## 13-5.3 高层 vs 低层运行时：containerd/CRI-O（镜像/生命周期）→ runc/crun（OCI 运行时）分工。
- ## 13-5.4 runc 创建容器机制：clone/unshare + setns + pivot_root + cgroup 装配的落地（回指大主题2 namespaces / 大主题3 cgroups）。
- 机制衔接 L4-01 OS：显式点出 runc 如何调用 namespaces/cgroups 原语落地隔离。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（OCI Runtime Spec v1.3.0、Linux man page namespaces(7)/clone(2)/pivot_root(2)、runc 官方文档）；冲突两边都记、点明分歧。
- unshare 本机 demo 可选：便宜可用则演示 setns/pivot_root 相关原语（本沙箱 Docker daemon 不可达），不强制。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；状态名/字段不编造，未证实标「待核」。
- runc/containerd/CRI-O 版本处标「⚙演进快·锚版本」。

【权威锚点清单】
- OCI Runtime Spec v1.3.0（2025-11-04）（强·一手，bundle/config.json/生命周期）。
- Linux man page：namespaces(7) / clone(2) / pivot_root(2)（强·一手，落地机制）。
- Kubernetes 官方文档 v1.36 CRI 相关（⚙演进快·锚版本）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题6 OCI 分发与镜像仓库 — 报告prompt
任务：为「L5-13·大主题6 OCI 分发与镜像仓库」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / HTTP 请求片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-6（小主题 13-6.1~13-6.4）。自足点名：
- ## 13-6.1 registry pull/push API：blob 与 manifest 的分发协议（OCI Distribution Spec v1.1.0）。
- ## 13-6.2 内容寻址与去重：按 digest 共享层减少传输。
- ## 13-6.3 tag vs digest 引用：可变 tag vs 不可变 digest 的可重复性（跨 L4-02 HTTP）。
- ## 13-6.4 镜像签名与供应链：cosign/attestation 等验真（⚠前沿，规范化程度待核）。
- 衔接：分发协议建于 HTTP 之上，可点一句回指 L4-02 网络。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（OCI Distribution Spec v1.1.0、OCI Image Spec v1.1.0、HTTP 相关 RFC）；冲突两边都记、点明分歧。
- 13-6.4 供应链/签名为前沿，凡二手承重打 ⚠，规范化程度标「待核」，不作定论。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；端点路径/media type 不编造，未证实标「待核」。
- 工具/规范版本处标「⚙演进快·锚版本」（cosign、sigstore、OCI 规范版本）。

【权威锚点清单】
- OCI Distribution Spec v1.1.0（2024-02-15）（强·一手，pull/push 协议）。
- OCI Image Spec v1.1.0（2024-02-15）（digest/manifest 引用一手）。
- Kubernetes 官方文档 v1.36（⚙演进快·锚版本，imagePullSecrets/镜像策略语境）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题7 K8s 工作负载对象 — 报告prompt
任务：为「L5-13·大主题7 K8s 工作负载对象」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / YAML 清单片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。K8s 深度专题归 L6-05，本课只做「编排解决什么 + 核心对象心智」。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-7（小主题 13-7.1~13-7.4）。自足点名：
- ## 13-7.1 Pod 最小调度单元：共享网络/存储的容器组（K8s docs Workloads v1.36）。
- ## 13-7.2 ReplicaSet / Deployment：副本管理与滚动更新/回滚。
- ## 13-7.3 StatefulSet / DaemonSet：有稳定标识的有状态 vs 每节点一实例。
- ## 13-7.4 Job / CronJob：一次性与定时批处理（深度归 L6-05）。
- 衔接：Pod 内容器共享 namespaces，可回指大主题2/3 的隔离机制。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（Kubernetes 官方文档 Workloads v1.36）；不同版本行为差异或文档与实现冲突时两边都记、点明分歧。
- 本机 demo 可选：本沙箱无 K8s 集群，实证以文档为主，不强制跑集群。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；字段名/默认值不编造，未证实标「待核」。
- K8s 版本处一律标「⚙演进快·锚版本」，锚定 v1.36（当前稳定），说明对象 API 随版本演进。

【权威锚点清单】
- Kubernetes 官方文档 Workloads，kubernetes.io/docs/concepts/，当前稳定 v1.36（⚙演进快·锚版本，强·一手）。
- OCI Runtime/Image Spec（容器执行/镜像语境回指，一手）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题8 K8s 声明式模型与 reconcile 循环 — 报告prompt
任务：为「L5-13·大主题8 K8s 声明式模型与 reconcile 循环」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / YAML 清单片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。此为云原生核心心智，讲透直觉但不深挖源码。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-8（小主题 13-8.1~13-8.4）。自足点名：
- ## 13-8.1 声明式 API：声明期望态而非命令步骤（Cluster Architecture v1.36）。
- ## 13-8.2 控制器 reconcile 循环：持续把实际态收敛到期望态。
- ## 13-8.3 控制平面架构：API server / etcd / scheduler / controller-manager 分工。
- ## 13-8.4 CRD / operator 扩展：自定义资源 + 控制器扩展声明式模型。
- 衔接：期望态收敛心智可与大主题7 工作负载对象、大主题9 服务发现串起来讲。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（Kubernetes 官方文档 Cluster Architecture / Controllers v1.36）；文档与实现或版本差异冲突时两边都记、点明分歧。
- 本机 demo 可选：本沙箱无集群，以文档实证为主，不强制。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；组件职责/字段不编造，未证实标「待核」。
- K8s 版本处标「⚙演进快·锚版本」，锚定 v1.36。

【权威锚点清单】
- Kubernetes 官方文档 Cluster Architecture / Controllers，kubernetes.io/docs/concepts/，当前稳定 v1.36（⚙演进快·锚版本，强·一手）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题9 K8s 服务发现/负载均衡/网络 — 报告prompt
任务：为「L5-13·大主题9 K8s 服务发现/负载均衡/网络」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / YAML 清单片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-9（小主题 13-9.1~13-9.4）。自足点名：
- ## 13-9.1 Service 与 ClusterIP：为动态 Pod 提供稳定虚拟 IP（Services & Networking v1.36）。
- ## 13-9.2 kube-proxy / EndpointSlice / DNS：端点跟踪与集群内服务发现。
- ## 13-9.3 Ingress 与外部入口：L7 路由暴露服务到集群外（跨 L5-11 负载均衡）。
- ## 13-9.4 网络模型与 CNI / NetworkPolicy：每 Pod 一 IP、扁平网络与策略隔离（跨 L4-02 网络）。
- 衔接：network ns（大主题2）是每 Pod 一 IP 的机制根，可回指。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（Kubernetes 官方文档 Services & Networking v1.36、CNI 规范）；文档与实现或版本差异冲突时两边都记、点明分歧。
- 本机 demo 可选：network ns 可用 unshare --net / ip netns 演示底层机制（本沙箱无集群），不强制。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；字段/默认行为不编造，未证实标「待核」。
- K8s 版本处标「⚙演进快·锚版本」，锚定 v1.36；Ingress 已趋向 Gateway API，须点明演进/时效。

【权威锚点清单】
- Kubernetes 官方文档 Services & Networking，v1.36（⚙演进快·锚版本，强·一手）。
- CNI 规范（containernetworking/cni，一手）。
- Linux man page network_namespaces(7)（机制根回指，一手）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题10 K8s 调度/抢占/驱逐 — 报告prompt
任务：为「L5-13·大主题10 K8s 调度/抢占/驱逐」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / YAML 清单片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-10（小主题 13-10.1~13-10.4）。自足点名：
- ## 13-10.1 调度器过滤 + 打分：filter 可行节点、score 择优（Scheduling v1.36）。
- ## 13-10.2 亲和/反亲和/污点容忍：约束 Pod 放置的软硬规则。
- ## 13-10.3 requests/limits 与 QoS：资源声明决定 Guaranteed/Burstable/BestEffort。
- ## 13-10.4 抢占与驱逐：高优先级抢占、节点压力驱逐。
- 衔接：requests/limits 最终落到 cgroups 限额（大主题3），可回指。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（Kubernetes 官方文档 Scheduling/Preemption/Eviction v1.36）；文档与实现或版本差异冲突时两边都记、点明分歧。
- 本机 demo 可选：cgroup 侧可读 /sys/fs/cgroup 佐证 limits 如何落地（本沙箱无集群），不强制。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；QoS 判定规则/字段不编造，未证实标「待核」。
- K8s 版本处标「⚙演进快·锚版本」，锚定 v1.36。

【权威锚点清单】
- Kubernetes 官方文档 Scheduling, Preemption and Eviction，v1.36（⚙演进快·锚版本，强·一手）。
- Linux man page cgroups(7)（limits 落地机制回指，一手）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题11 K8s 配置与存储 — 报告prompt
任务：为「L5-13·大主题11 K8s 配置与存储」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / YAML 清单片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-11（小主题 13-11.1~13-11.4）。自足点名：
- ## 13-11.1 ConfigMap / Secret：配置与密钥同镜像解耦（Configuration & Storage v1.36）。
- ## 13-11.2 Volume 类型：emptyDir/hostPath/投射卷等生命周期差异。
- ## 13-11.3 PV / PVC / StorageClass：供给-申领分离与动态供给。
- ## 13-11.4 CSI 存储接口：标准化第三方存储插件（跨 L4-06 配置管理）。
- 衔接：Secret 非加密仅 base64、配置与镜像解耦呼应大主题4 不可变镜像，可点明。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（Kubernetes 官方文档 Configuration + Storage v1.36、CSI 规范）；文档与实现或版本差异冲突时两边都记、点明分歧。
- 本机 demo 可选：mount ns（大主题2）可演示挂载视图隔离佐证 Volume 概念（本沙箱无集群），不强制。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；字段/默认回收策略不编造，未证实标「待核」。
- K8s 版本处标「⚙演进快·锚版本」，锚定 v1.36。

【权威锚点清单】
- Kubernetes 官方文档 Configuration + Storage，v1.36（⚙演进快·锚版本，强·一手）。
- CSI 规范（container-storage-interface/spec，一手）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

```text
[P2] L5-13·大主题12 容器安全边界 — 报告prompt
任务：为「L5-13·大主题12 容器安全边界」产出一份符合 report-format v3 模板的教辅式报告。

【v3 格式硬性要求】
- 层级：# → ## → ###；### 下不写标签，直接正文分段。
- 每个 ### 给：①核心说明（正确）；②充分辅助说明（初学者所需直觉/为什么/最小例子/易错点/关联；必需）。
- 命令 / 配置片段独占块级行。
- 教辅口吻；广度优先、讲到初学者懂即止，不做专家级纵深。

【本报告小主题清单（##章节）与应覆盖内容项】
下游可读 round3b-subtopics 中 L5-13 大主题 13-12（小主题 13-12.1~13-12.4）。自足点名：
- ## 13-12.1 共享内核攻击面：单一内核 = 容器逃逸风险面（跨 L5-04 安全）。
- ## 13-12.2 能力/seccomp/LSM 收窄：capabilities 削权、seccomp 过滤 syscall、AppArmor/SELinux。
- ## 13-12.3 rootless / user ns 强化：用 user ns 降低逃逸后权限。
- ## 13-12.4 gVisor / Kata 沙箱：用户态内核 vs 微 VM 强隔离（⚠前沿）。
- 机制衔接 L4-01 OS：共享内核=共享 namespaces/cgroups 隔离面，逃逸即突破隔离，回指大主题1/2/3。

【验证纪律（强制）】
- 多来源比对：每内容项 ≥2 独立来源，优先一手（Linux man page capabilities(7)/seccomp(2)/user_namespaces(7)、Kubernetes 官方文档 Security、gVisor/Kata 官方文档）；冲突两边都记、点明分歧。
- 13-12.4 gVisor/Kata 为前沿，凡二手承重打 ⚠，成熟度/性能开销标「待核」，不作定论。
- unshare 本机 demo 可选：user ns 削权可用 unshare --user --map-root-user 演示（本沙箱可用），不强制。
- 章节末集中列来源与时效：锚点 + 版本 + 核实日期；capability 名/seccomp 默认 profile 不编造，未证实标「待核」。
- K8s 与工具版本处标「⚙演进快·锚版本」（Pod Security Standards、gVisor/Kata 版本）。

【权威锚点清单】
- Linux man page：capabilities(7) / seccomp(2) / user_namespaces(7)（强·一手）。
- Kubernetes 官方文档 Security，v1.36（⚙演进快·锚版本，强·一手）。
- gVisor / Kata Containers 官方文档（⚠前沿，一手但快速演进）。

【粒度判定】先判「1 份 or 拆 N 份 + 理由」，再动笔。

【落盘】Write 到 findings/036-containers-cloud-native.md（抬头带基线串与核实日期；不残留工具标签/控制字符）。
```

共 12 条 prompt
