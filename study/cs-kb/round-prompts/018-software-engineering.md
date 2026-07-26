# Round3c · L4-06 软件工程 · 报告 prompt（P1）

```text
[P1] L4-06·大主题06-1 软工过程与工程质量观 — 报告prompt
任务：为「L4-06·大主题06-1 软工过程与工程质量观」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（若判定拆分，见文末粒度判定，用 018-software-engineering-<slug>-a/-b）。

【v3 格式硬性（照 report-format.md v3）】
- 标题层级固定：`#` 大主题（报告标题）→ `##` 小主题（章节）→ `###` 每个内容项一节；`###` 之下不写「核心概念：」「辅助说明：」等任何标签，直接正文靠空行分段。
- 每个 `###` 内：先一段核心说明（是什么/定义/关键结论，正确），再充分辅助说明（面向初学者把它讲懂所需：直觉/为什么这样/最小例子/易错点/与前后关联，可多段；这是必需项，不是点缀）。
- 任何命令/配置片段/流程记号一律独占块（块级），不内联埋进句子。
- 教辅口吻；广度优先：小主题下的内容项要查全、宁多列浅讲不遗漏；每项讲到初学者懂即止，不做专家级纵深（不写长篇机制深挖/推导/设计权衡长论）。
- 抬头带可 grep 基线串：`基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（本组实证工具链另含 git 2.43.0），并注明先修/一手锚点/核实日期。
- 本大主题多为软工方法学（⚠行业框架，非规范），凡实践/流程/工具生态处标「⚙演进快·锚版本」。

【小主题清单（=`##` 章节）与每小主题应覆盖内容项】
下游可读 round3b-subtopics/g12-engineering-delivery.md 中 L4-06「06-1」小节取全部内容项（小主题 06-1.1~06-1.4，务必查全）：
- 06-1.1 软件生命周期阶段：需求→设计→实现→演进→维护的活动划分与「维护占主要成本」直觉。
- 06-1.2 瀑布 vs 迭代/敏捷：大批量顺序 vs 小批量迭代的反馈周期取舍（⚠行业框架，非规范，标演进快）。
- 06-1.3 工程质量属性：正确/健壮 vs 可读/可维护/可演进的外部与内部质量分层（MIT 6.031「safe from bugs / easy to understand / ready for change」三目标）。
- 06-1.4 面向变更的设计总纲：把「为演进而设计」作为统摄本组其余单元的元原则。

【多来源比对强制】
- 每个内容项 ≥2 个独立来源交叉核对，优先一手：MIT 6.031 sp22 课程框架（准一手，尤其「三目标」表述）；生命周期/敏捷等行业框架为 ⚠准一手（Cohn/Fowler 等），须打 ⚠、仅指路补留白、不承重。
- 来源打架两边都记、点明分歧、不和稀泥（如「敏捷 vs 瀑布」的效益主张在不同来源口径不一，须标为经验主张而非定论）。
- 具体数值/断言（如「维护占成本比例」）不凭记忆，据来源核实或标「待核」，标核实日期。
- 本机实证不适用（元框架/方法学），无须强跑。
- 每个 `##` 章节末集中列「来源与时效」（锚点+版本+核实日期+冲突项定位），不逐项脚注。

【权威锚点清单】
- MIT 6.031 Software Construction, sp22（https://web.mit.edu/6.031/www/sp22/）— 课程总纲与「三目标」质量观（准一手承重）。
- 行业方法学：Cohn《Succeeding with Agile》(2009)、Martin Fowler 站点（martinfowler.com）—⚠准一手，仅佐证。
- 核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：本大主题 4 个小主题、元框架性质、篇幅偏轻，强烈倾向 1 份（可作本课开篇总纲节）。判定写入报告工作记录/ledger。
```

```text
[P1] L4-06·大主题06-2 规约、契约与抽象数据类型 — 报告prompt
任务：为「L4-06·大主题06-2 规约、契约与抽象数据类型」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文靠空行分段；每项核心说明+充分辅助说明（面向初学者：直觉/为什么/最小例子/易错点/关联，必需且充分）；规约记号（requires/effects）、AF/RI 记号、代码片段一律独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂但不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/一手锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b-subtopics/g12-engineering-delivery.md 中 L4-06「06-2」小节取全部内容项（06-2.1~06-2.5，查全）；跨课提示：与 L3-04 OOP 封装、L5-06 PL 理论契约有重叠，本报告停在软件构造层，点出边界：
- 06-2.1 方法规约：前置/后置条件——用 requires/effects 把实现与客户端解耦为契约（MIT 6.031 Specifications）。
- 06-2.2 规约的强弱与替换——更强前置=更弱规约、更强后置=更强规约；规约比较与安全替换直觉（Designing Specifications）。
- 06-2.3 ADT 操作分类——creator/producer/observer/mutator 四类，以操作而非表示定义类型（Abstract Data Types）。
- 06-2.4 表示不变量 RI 与抽象函数 AF——RI 界定合法表示、AF 把表示映射到抽象值，checkRep 守护（AF & RI）。
- 06-2.5 表示暴露与防御拷贝——rep exposure 破坏不变量的机理与不可变/拷贝防御。

【多来源比对强制】
- 每项 ≥2 独立源交叉核对，优先一手：MIT 6.031 sp22 相应 reading（Specifications / Designing Specifications / ADT / AF&RI）为主承重；契约/替换概念可与经典教材（如 Liskov《Program Development in Java》、LSP 原始表述）交叉印证。
- 规约「强弱」方向、LSP 替换的前后置条件方向易记反，须据一手逐句核对、两边记法冲突则都记并点明。
- 不凭记忆写结论；不确定标「待核」，标核实日期。
- 本机实证可选（非强制）：可用 Python 写一个带 checkRep 的最小 ADT + 触发 rep exposure 的反例演示，做了贴脚本+真实输出+基线串。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- MIT 6.031 sp22（https://web.mit.edu/6.031/www/sp22/）Specifications / Designing Specifications / Abstract Data Types / Abstraction Functions & Rep Invariants（准一手承重）。
- 交叉：Liskov & Guttag《Program Development in Java: Abstraction, Specification, and OO Design》、LSP 原始定义（佐证替换/契约方向，非承重）。
- 核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：5 个小主题、单一「规约+ADT」教学线，默认 1 份；若 ADT/AF-RI 展开过长可拆 -a（规约与替换）/-b（ADT/AF-RI/表示暴露）并说明。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-3 静态检查与类型安全防御 — 报告prompt
任务：为「L4-06·大主题06-3 静态检查与类型安全防御」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；代码片段/断言/编译器命令独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-3」小节取全部内容项（06-3.1~06-3.4，查全）；跨课提示：与 L5-06 类型系统、L4-04 编译前端检查重叠，本报告停在工程纪律层、点出边界：
- 06-3.1 静态 vs 动态检查——编译期可消除的错误类别与「越早发现越省」（MIT 6.031 Static Checking）。
- 06-3.2 类型安全与不可变性作防御——用类型/final/不可变把整类错误设计消除。
- 06-3.3 「避免调试」纪律——fail-fast、断言、局部化错误传播（Avoiding Debugging）。
- 06-3.4 linter 与静态分析工具链——编译器警告/类型检查器/静态分析在门禁中的定位（⚠工具生态，标演进快·锚版本）。

【多来源比对强制】
- 每项 ≥2 独立源，优先一手：MIT 6.031 sp22（Static Checking / Avoiding Debugging）为主承重；工具类（linter/type checker/静态分析）为 ⚠工具生态，打 ⚠、标「⚙演进快·锚版本·随时变」、不承重、仅指路。
- 「静态检查能消除哪类错误」与语言相关，须区分「一般原则」与「某语言/某检查器的具体行为」（规范 vs 实现分账），冲突两边都记。
- 具体工具默认规则/版本不凭记忆，标「待核」或注明所锚版本，标核实日期。
- 本机实证可选：可用 Python（mypy/静态类型）或 gcc `-Wall -Werror` 演示「编译期 vs 运行期」差异，做了贴命令+真实输出+基线串（非强制）。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- MIT 6.031 sp22（https://web.mit.edu/6.031/www/sp22/）Static Checking / Avoiding Debugging（准一手承重）。
- 工具侧（⚠演进快，仅指路）：各语言编译器/类型检查器/linter 官方文档（如 gcc、mypy 等），标所锚版本。
- 核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：4 个小主题、单一「静态防御」教学线、篇幅适中，默认 1 份。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-4 Git 对象模型（内容寻址 DAG） — 报告prompt
任务：为「L4-06·大主题06-4 Git 对象模型（内容寻址 DAG）」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。本大主题为本组最强实证点。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；对象格式串（如 "blob "+len+NUL+内容）、SHA、`git cat-file`/`hash-object` 命令与输出一律独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/一手锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-4」小节取全部内容项（06-4.1~06-4.5，查全）：
- 06-4.1 blob 与内容寻址——文件内容→「blob 」+len+NUL+内容取 SHA；`git hash-object`/`cat-file` 手算可硬实证（Pro Git §10.2）。
- 06-4.2 tree 对象与目录快照——mode+type+sha+name 表示目录；`git cat-file -p <tree>` 实证。
- 06-4.3 commit 对象与父指针 DAG——tree+parent(s)+author/committer+msg 构成不可变提交图；手拼 commit 复算 SHA。
- 06-4.4 annotated tag 对象——指向对象+标注者+消息的第四类对象 vs 轻量 tag（仅引用）。
- 06-4.5 对象存储：loose vs packfile——zlib 压缩、松散对象、pack 与 delta 压缩（§10.4 Packfiles）；`git gc`/`verify-pack` 可观察。SHA-1→SHA-256 过渡状态须据本机 git 2.43.0 核实（round3b 标「待核」）。

【多来源比对强制】
- 每项 ≥2 独立源，优先一手：Pro Git 2nd ed §10.2 Git Objects / §10.4 Packfiles（强·一手承重）；对象格式细节可与 git 官方文档（gitformat-pack、hash-function-transition 等 man/docs）交叉印证。
- SHA-1→SHA-256 过渡为演进项：标「⚙演进快·锚版本」，据本机 `git version`（2.43.0）与官方过渡文档核实当前状态，不确定标「待核」，不编造，标核实日期。
- 冲突两边都记并各自定位。
- 本机实证可选但强烈鼓励（本组最强实证点）：`git hash-object` 手算 blob SHA、`git cat-file -p` 读 tree/commit、手拼对象复算 SHA、`git verify-pack -v` 看 delta；做了贴命令+真实输出+基线串（写仓库外 scratchpad，跑完清）。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- Pro Git 2nd ed（https://git-scm.com/book/en/v2）§10.2 Git Objects、§10.4 Packfiles（强·一手承重）。
- git 官方 man/docs：gitformat-pack、hash-function-transition（SHA-256 过渡，交叉核对，标版本）。
- 本机 git 2.43.0（实证参照系）。核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：5 个小主题、单一对象模型主线、实证密集，默认 1 份；若对象四型+存储层展开过长可拆 -a（blob/tree/commit/tag 四对象）/-b（对象存储 loose/packfile/SHA 过渡）并说明。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-5 Git 引用/分支/HEAD 指针机制 — 报告prompt
任务：为「L4-06·大主题06-5 Git 引用/分支/HEAD 指针机制」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；ref 路径（refs/heads、refs/tags、refs/remotes）、`.git/HEAD` 内容、`git reflog`/`cat` 命令与输出独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-5」小节取全部内容项（06-5.1~06-5.5，查全）：
- 06-5.1 refs/heads 分支=轻量指针——分支只是指向 commit 的文件（约 41 字节：40 hex + 换行）（Pro Git §10.3）；`cat .git/refs/heads/*` 实证。
- 06-5.2 HEAD 与 detached HEAD——符号引用 vs 直接指向 commit 的分离态。
- 06-5.3 tags：轻量 vs 附注——refs/tags 引用 vs 附注 tag 对象的区别（回指 06-4.4）。
- 06-5.4 reflog 与可恢复性——引用移动历史作「后悔药」；`git reflog` 找回悬空 commit。
- 06-5.5 远程跟踪引用——refs/remotes/* 作本地缓存的远端状态。

【多来源比对强制】
- 每项 ≥2 独立源，优先一手：Pro Git §3.1 分支基础 + §10.3 Git References（强·一手承重）；引用规则可与 git 官方 man（git-check-ref-format、gitrevisions）交叉印证。
- 分支指针文件长度「41 字节」等具体数值须据本机实证或官方核实（40 hex + 换行符），不凭记忆，冲突两边都记。
- packed-refs（引用打包后不再是散文件）为易错点，须点明「散文件 vs packed-refs」两种存在形态、据一手核实，标核实日期。
- 本机实证可选但鼓励：`cat .git/HEAD`、`cat .git/refs/heads/*`、`git symbolic-ref HEAD`、`git reflog`、观察 detached HEAD；做了贴命令+真实输出+基线串。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- Pro Git 2nd ed（https://git-scm.com/book/en/v2）§3.1 Branches in a Nutshell、§10.3 Git References（强·一手承重）。
- git 官方 man：git-check-ref-format、gitrevisions、git-reflog（交叉核对）。
- 本机 git 2.43.0。核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：5 个小主题、单一引用机制主线、篇幅适中，默认 1 份。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-6 三向合并与 merge-base（rebase vs merge） — 报告prompt
任务：为「L4-06·大主题06-6 三向合并与 merge-base（rebase vs merge）」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；`git merge-base`/`git merge`/`git rebase` 命令、冲突标记区块（<<<<<<< / ======= / >>>>>>>）独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-6」小节取全部内容项（06-6.1~06-6.5，查全）；边界：冲突的语义解决属人工，讲到机制+标记为止：
- 06-6.1 merge-base 与共同祖先——`git merge-base` 求最近公共祖先作三向基点（可实证）。
- 06-6.2 三向合并算法——base/ours/theirs 三方 diff 合成结果的机制。
- 06-6.3 快进 vs 真合并——fast-forward 移指针 vs 生成合并 commit（两父）。
- 06-6.4 rebase 线性改写与黄金规则——重放提交改写历史；「勿 rebase 已推送公共分支」（Pro Git §3.6）。
- 06-6.5 冲突标记与解决——<<<<<<< / ======= / >>>>>>> 区块含义；语义解决属人工（教学边界）。

【多来源比对强制】
- 每项 ≥2 独立源，优先一手：Pro Git §3.2 Basic Branching and Merging、§3.6 Rebasing、§7.8 Advanced Merging（强·一手承重）；三向合并/merge-base 细节可与 git 官方 man（git-merge-base、git-merge、git-rebase）交叉印证。
- rebase「黄金规则」的边界（何为「公共/已推送分支」）易误解，须据一手措辞核实、点明；merge vs rebase 的取舍是经验主张（标演进/团队相关），不作绝对定论、冲突两边都记。
- 不凭记忆写算法细节；不确定标「待核」，标核实日期。
- 本机实证可选但鼓励：构造分叉历史 `git merge-base`、fast-forward vs `--no-ff`、制造冲突看标记、`git rebase` 观察 SHA 改写；做了贴命令+真实输出+基线串。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- Pro Git 2nd ed（https://git-scm.com/book/en/v2）§3.2、§3.6、§7.8（强·一手承重）。
- git 官方 man：git-merge-base、git-merge、git-rebase（交叉核对）。
- 本机 git 2.43.0。核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：5 个小主题、单一合并机制主线，默认 1 份。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-7 分布式协作、PR 评审与传输协议 — 报告prompt
任务：为「L4-06·大主题06-7 分布式协作、PR 评审与传输协议」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；refspec 语法（+src:dst）、`git fetch`/`push` 命令、协议 URL 形态独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-7」小节取全部内容项（06-7.1~06-7.5，查全）；跨课提示：传输协议建立在 L4-02 网络之上，本报告停在 git 传输层、点边界；PR 评审为 ⚠平台特性/方法学：
- 06-7.1 分布式模型——clone/fetch/push 的完整历史复制语义 vs 集中式。
- 06-7.2 贡献/维护工作流——集成管理者、dictator-lieutenants 等协作拓扑（Pro Git ch5/ch6）。
- 06-7.3 refspec 映射——`+src:dst` 如何决定 fetch/push 的引用对应。
- 06-7.4 传输协议——local/smart-http/ssh 的握手与数据交换（§10.6；跨 L4-02）。
- 06-7.5 代码评审门禁——PR/MR 评审作变更进主干的人工质量关（MIT Code Review；平台侧 ⚠标演进快）。

【多来源比对强制】
- 每项 ≥2 独立源，优先一手：Pro Git ch5（Distributed Git）/ch6（GitHub）/§10.5-10.6（Transfer Protocols、Refspec）为传输与协作机制承重；代码评审原则锚 MIT 6.031 Code Review（准一手）。
- 「PR」是平台（GitHub/GitLab）概念、非 git 内核概念，须显式分账：git 侧机制（refspec/传输）为一手承重；平台评审流程为 ⚠、标「⚙演进快·锚版本·随时变」、不承重。
- 协议握手/refspec 细节不凭记忆，据一手核实、冲突两边都记，标核实日期。
- 本机实证可选：`git ls-remote`、本地 file:// 协议 clone/fetch、看 refspec 默认映射（`git config --get-all remote.origin.fetch`）；做了贴命令+真实输出+基线串。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- Pro Git 2nd ed（https://git-scm.com/book/en/v2）ch5、ch6、§10.5 The Refspec、§10.6 Transfer Protocols（强·一手承重）。
- MIT 6.031 sp22 Code Review（https://web.mit.edu/6.031/www/sp22/）（准一手，评审原则）。
- 平台侧（⚠演进快，仅指路）：GitHub/GitLab PR/MR 与保护分支文档，标所锚版本。
- 本机 git 2.43.0。核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：5 个小主题、跨「协作拓扑+传输机制+评审」，默认 1 份；若传输协议+refspec 展开过长可拆 -a（分布式模型/工作流/评审）/-b（refspec/传输协议）并说明。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-8 分支策略 — 报告prompt
任务：为「L4-06·大主题06-8 分支策略」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。本大主题为团队策略（非 git 机制），实践处普遍标演进快。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；分支模型示意/命令片段独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-8」小节取全部内容项（06-8.1~06-8.4，查全）；全大主题为 ⚠行业方法学、非规范，普遍标「⚙演进快·锚版本」：
- 06-8.1 trunk-based development——短命名分支/直接主干、频繁集成（⚠行业）。
- 06-8.2 GitFlow/长命名分支模型——develop/release/hotfix 多长命名分支及其开销。
- 06-8.3 feature flag 与发布解耦——用开关把「部署」与「发布」解耦，允许主干半成品。
- 06-8.4 策略选型——按团队规模/发布节奏/评审文化取舍（团队策略非机制）。

【多来源比对强制】
- 每项 ≥2 独立源：本大主题**一手最弱**，均为 ⚠行业方法学。承重上限=Pro Git §3.4 Branching Workflows（一手，仅到「工作流大类」）；trunk-based/GitFlow/feature flag 的具体主张来自业界（Fowler、原始 GitFlow 博文、trunkbaseddevelopment.com 等），一律打 ⚠、标「⚙演进快·锚版本·随时变」、不承重、仅指路。
- 各策略优劣是经验主张、来源常互相打架（尤其 GitFlow 已被原作者标注「多数团队不再推荐」），须两边都记、点明分歧年代与来源、不和稀泥、不作绝对定论。
- 不凭记忆断言「哪个更好」；有争议处标「待核/经验主张」，标核实日期。
- 本机实证一般不适用（团队策略）。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- Pro Git 2nd ed（https://git-scm.com/book/en/v2）§3.4 Branching Workflows（一手，承重到工作流大类）。
- ⚠业界（演进快，仅指路，不承重）：Martin Fowler 站点（trunk-based、feature toggles）、原始 GitFlow 文章及其后续注记、trunkbaseddevelopment.com，均标所锚版本/年份。
- 核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：4 个小主题、方法学性质、篇幅偏轻，强烈倾向 1 份。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-9 测试金字塔与测试分层 — 报告prompt
任务：为「L4-06·大主题06-9 测试金字塔与测试分层」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；金字塔示意/测试用例代码/等价类划分表独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-9」小节取全部内容项（06-9.1~06-9.4，查全）；与 06-11（TDD 节奏）分账：本大主题讲测试的结构层，标演进项（金字塔/契约测试为 ⚠行业）：
- 06-9.1 unit/integration/e2e 分层——三层的速度/稳定性/成本结构（Cohn ⚠）。
- 06-9.2 冰淇淋筒反模式——重 e2e 轻 unit 导致慢而脆（Fowler ⚠）。
- 06-9.3 测试用例选择——等价类划分、边界值、glass-box vs black-box（MIT 6.031 Testing，准一手承重）。
- 06-9.4 契约/组件测试——微服务语境下替代重 e2e 的消费者驱动契约（⚠，标演进快）。

【多来源比对强制】
- 每项 ≥2 独立源：测试用例选择（等价类/边界/黑白盒）锚 MIT 6.031 Testing（准一手承重）；「测试金字塔/冰淇淋筒/契约测试」为 ⚠行业框架（Cohn《Succeeding with Agile》2009、Martin Fowler 站点），打 ⚠、标「⚙演进快·锚版本」、不承重、仅指路。
- 金字塔各层比例/命名是经验主张、来源口径不一，两边都记、点明来源与年代、不作绝对定论。
- 不凭记忆写比例数字；不确定标「待核」，标核实日期。
- 本机实证可选：用 Python（如 unittest/pytest）写一个含等价类/边界用例的最小样例，演示黑白盒差异；做了贴命令+真实输出+基线串。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- MIT 6.031 sp22 Testing（https://web.mit.edu/6.031/www/sp22/）（准一手承重，用例选择/黑白盒）。
- ⚠业界（演进快，仅指路）：Mike Cohn《Succeeding with Agile》(2009) 测试金字塔源、Martin Fowler 站点（Test Pyramid、Consumer-Driven Contracts），标年份/版本。
- 核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：4 个小主题、单一「测试结构层」主题，默认 1 份。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-10 测试替身与覆盖率解读 — 报告prompt
任务：为「L4-06·大主题06-10 测试替身与覆盖率解读」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；替身/覆盖率示例代码、覆盖率报告片段独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-10」小节取全部内容项（06-10.1~06-10.3，查全）；替身术语为 ⚠行业（Meszaros/Fowler），标演进/口径分歧：
- 06-10.1 替身谱系——dummy/stub/spy/mock/fake 五类区分（Meszaros/Fowler ⚠）。
- 06-10.2 状态验证 vs 行为验证——断言结果 vs 断言交互的取舍。
- 06-10.3 覆盖率类型与陷阱——行/分支/路径覆盖；「高覆盖率≠高质量」。

【多来源比对强制】
- 每项 ≥2 独立源：五类替身术语源自 Meszaros《xUnit Test Patterns》并经 Martin Fowler「Mocks Aren't Stubs」普及（⚠准一手/业界），打 ⚠、标「⚙演进快·锚版本」；术语在业界常被混用，须以 Meszaros/Fowler 原定义为准并点明常见误用。
- 覆盖率概念（行/分支/路径）可锚 MIT 6.031 Testing + 工具文档交叉；「覆盖率≠质量」是共识但须给来源，不作空断言。
- 不同来源对 mock/stub 边界定义有分歧，两边都记、点明。
- 不凭记忆写定义；不确定标「待核」，标核实日期。
- 本机实证可选：Python 用 unittest.mock 演示 stub vs mock、用 coverage.py 跑行/分支覆盖看「高覆盖仍漏 bug」反例；做了贴命令+真实输出+基线串。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- MIT 6.031 sp22 Testing（https://web.mit.edu/6.031/www/sp22/）（准一手，覆盖率/测试策略侧）。
- ⚠业界（演进快，仅指路）：G. Meszaros《xUnit Test Patterns》(2007) 替身谱系、Martin Fowler「Mocks Aren't Stubs」，标版本/年份；覆盖率工具文档（如 coverage.py）标所锚版本。
- 核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：3 个小主题、篇幅轻，强烈倾向 1 份（可与 06-9 或 06-11 邻接但按大主题独立成节）。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-11 TDD 与测试作为设计压力 — 报告prompt
任务：为「L4-06·大主题06-11 TDD 与测试作为设计压力」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；红-绿-重构循环示意/测试代码/依赖注入示例独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-11」小节取全部内容项（06-11.1~06-11.3，查全）；与 06-9（测试结构）分账：本大主题讲节奏/流程，TDD 为 ⚠行业方法学、标演进：
- 06-11.1 红-绿-重构循环——先失败测试→最小实现→重构的节奏（Beck ⚠）。
- 06-11.2 测试先行作设计压力——可测性倒逼解耦/依赖注入。
- 06-11.3 重构安全网——回归测试保障重构不改行为。

【多来源比对强制】
- 每项 ≥2 独立源：TDD 循环锚 Kent Beck《Test-Driven Development: By Example》(2002)（⚠准一手，打 ⚠、标「⚙演进快·锚版本」）；「测试驱动设计」的效益主张可与 MIT 6.031（测试与规约先行）交叉印证方向。
- TDD 的效益/适用范围有长期争议（如「TDD is dead」论战），须记为经验主张、点明来源与分歧、不作绝对定论、两边都记。
- 不凭记忆写效益断言；不确定标「待核」，标核实日期。
- 本机实证可选：用 Python（pytest）走一遍红-绿-重构最小实例、演示可测性倒逼依赖注入；做了贴命令+真实输出+基线串。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- Kent Beck《Test-Driven Development: By Example》(2002)（⚠准一手，红绿重构源）。
- MIT 6.031 sp22（https://web.mit.edu/6.031/www/sp22/）Testing / Specifications（交叉印证「先想规约/测试」方向）。
- ⚠业界（仅指路）：Martin Fowler 站点相关文章（TDD 争论），标年份。
- 核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：3 个小主题、篇幅轻、方法学性质，强烈倾向 1 份。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-12 CI 持续集成与门禁 — 报告prompt
任务：为「L4-06·大主题06-12 CI 持续集成与门禁」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。本大主题 CI 工具生态演进极快，普遍标演进快·锚版本；git hook 可作本机最小 CI 演示。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；流水线 YAML 片段、`.git/hooks` 脚本、lockfile 片段一律独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-12」小节取全部内容项（06-12.1~06-12.5，查全）；跨课提示：CI 交付全库产出；工具侧全部 ⚠、标「⚙演进快·锚版本」：
- 06-12.1 CI 核心实践——频繁集成、主干始终可构建可发布（⚠工具无关概念）。
- 06-12.2 流水线门禁阶段——构建→单测→静态检查→合并的自动关卡。
- 06-12.3 制品与构建缓存——artifact 产出/版本化与缓存加速。
- 06-12.4 git hooks 本地门禁——pre-commit/pre-push 拦截；本机 `.git/hooks` 可写脚本硬实证最小 CI。
- 06-12.5 构建可复现与依赖锁定——lockfile/固定版本保证可重复构建。

【多来源比对强制】
- 每项 ≥2 独立源：CI「工具无关概念」（频繁集成/主干可发布）可锚 Martin Fowler「Continuous Integration」文章（⚠准一手，标年份）+ 与 git hooks 机制（Pro Git §8.3 Git Hooks，一手）交叉；具体流水线/制品/缓存实现锚 GitHub Actions / GitLab CI 官方文档（⚠二手），一律打 ⚠、标「⚙演进快·锚版本·随时变」、不承重、仅指路。
- git hooks 为一手承重项：pre-commit/pre-push 触发时机据 Pro Git §8.3 + git 官方 githooks(5) man 核实。
- 概念（工具无关）vs 某平台实现须分账、冲突两边都记；具体默认值/语法不凭记忆，标「待核」或注明所锚版本，标核实日期。
- 本机实证可选但鼓励：在临时 repo 写 `.git/hooks/pre-commit` 脚本（跑 lint/测试、失败即拦截）演示「最小 CI 门禁」；做了贴命令+真实输出+基线串（写仓库外 scratchpad）。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- Pro Git 2nd ed（https://git-scm.com/book/en/v2）§8.3 Git Hooks + git 官方 githooks(5) man（一手承重，本地门禁机制）。
- ⚠业界（演进快，仅指路）：Martin Fowler「Continuous Integration」文章（标年份）、GitHub Actions（docs.github.com/actions）/ GitLab CI（docs.gitlab.com）官方文档，标所锚版本/核实日期。
- 本机 git 2.43.0。核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：5 个小主题、单一 CI 主题、含 git hook 实证，默认 1 份。判定写入 ledger。
```

```text
[P1] L4-06·大主题06-13 CD：持续交付 vs 持续部署 — 报告prompt
任务：为「L4-06·大主题06-13 CD：持续交付 vs 持续部署」产出一份 report-format v3（广度优先·教辅式）报告，落盘到 findings/018-software-engineering.md（拆分则 018-software-engineering-<slug>-a/-b）。本大主题工具/实践演进快，普遍标演进快·锚版本。

【v3 格式硬性】`#`→`##`→`###`；`###` 下不写标签、直接正文分段；每项核心说明+充分辅助说明（初学者向，必需且充分）；环境晋级流水线示意/部署策略示意/回滚命令片段独占块，不内联；教辅口吻；广度优先查全、讲到初学者懂不做专家纵深；抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`（含 git 2.43.0）+先修/锚点/核实日期。

【小主题清单（=`##` 章节）与内容项】
下游读 round3b 中 L4-06「06-13」小节取全部内容项（06-13.1~06-13.4，查全）；交付链三段分账须显式点出：本报告=流水线机制；金丝雀发布决策留 L5-12 §12-10、编排落地留 L5-13：
- 06-13.1 delivery vs deployment 分账——「随时可发布」vs「自动发布到生产」的界线。
- 06-13.2 环境晋级流水线——dev→staging→prod 逐级门禁晋级。
- 06-13.3 部署策略——蓝绿/滚动/金丝雀（机制细节跨 L5-12 发布工程、L5-13 编排，本报告点到即止、指边界）。
- 06-13.4 回滚与可回退性——快速回退作发布安全网。

【多来源比对强制】
- 每项 ≥2 独立源：delivery vs deployment 的界定可锚 Jez Humble/Martin Fowler「Continuous Delivery」表述（⚠准一手，标年份）+ 与本组 L5-12 发布工程口径交叉；具体部署策略/环境晋级实现来自工具文档（GitHub/GitLab CD、云平台，⚠二手），一律打 ⚠、标「⚙演进快·锚版本·随时变」、不承重、仅指路。
- 「continuous delivery vs continuous deployment」定义在业界常被混用，须以 Fowler/Humble 原定义为准、点明混用、两边都记。
- 蓝绿/滚动/金丝雀机制在本报告只做入门直觉，纵深与「错误预算驱动发布决策」明确指向 L5-12 §12-10、编排指向 L5-13，报告须显式点出下沉边界。
- 不凭记忆写工具默认值；不确定标「待核」，标核实日期。
- 本机实证一般不适用（需生产环境/编排），如做概念级脚本演示可选并注明。
- 每章末集中列「来源与时效」。

【权威锚点清单】
- ⚠准一手（演进快，指路为主）：Jez Humble & David Farley《Continuous Delivery》(2010)、Martin Fowler 站点（ContinuousDelivery、BlueGreenDeployment、CanaryRelease），标年份。
- 工具侧（⚠二手，仅指路）：GitHub / GitLab CD、云平台部署文档，标所锚版本/核实日期。
- 交叉/下沉：发布决策见 L5-12 §12-10、编排落地见 L5-13（仅指引，不在本报告展开）。
- 核实基线日期 2026-07-25。

【粒度判定（下游先做）】先判「1 份 or 拆 N 份 + 理由」：4 个小主题、交付链收尾章、点到为止，强烈倾向 1 份（可作本课收尾节）。判定写入 ledger。
```

共 13 条 prompt
