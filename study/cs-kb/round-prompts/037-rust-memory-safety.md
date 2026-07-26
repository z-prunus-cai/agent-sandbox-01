# Round3c · L5-14 Rust 与内存安全 · 报告 prompt（P2）

> 说明：本课 K=12 个大主题（R1–R12，取自 round3a/g08 与 round3b/g08）。以下依次 12 条报告 prompt，每条自足，供下游 Round 4 直接产出 report-format v3 教辅报告。
> 全课共享版本锚（每条内均复述）：**⚙演进快·锚版本**——The Rust Programming Language book（"the book"）页面声明对齐 **Rust 1.90.0（2025-09-18）+ edition 2024**，本机 **rustc/cargo 1.94.1**；二者 delta 需现查不凭记忆。基线串（报告抬头必带、可 grep）：`基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

```text
[P2] L5-14·大主题R1 Rust 定位、工具链与工程骨架 — 报告prompt

任务：为「L5-14·大主题R1 Rust 定位、工具链与工程骨架」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #（大主题=报告标题）→ ##（小主题=章节）→ ###（每个内容项一节）。
- ### 之下不写「核心概念：」「辅助说明：」等任何标签，直接正文分段：先一段核心说明（是什么/定义/关键结论，正确），再充分的辅助说明（面向初学者的直觉/为什么/最小例子/易错点/前后关联，写到初学者懂为止，可多段）。辅助说明是必需项，不是点缀。
- 任何 Rust 代码片段独占块级代码块（```rust），不内联埋进句子；公式同样独占行。
- 教辅口吻；广度完备，宁可多列浅讲，不遗漏。
- 报告抬头带 v3 模板行（基线/核实日期 ｜ 先修 ｜ 一手锚点@版本 ｜ 成熟度）与可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单（每个 ## 一章）与应覆盖内容项——细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R1（小主题 R1.1–R1.5）：
- R1.1 安装与 cargo/rustc：构建/运行/依赖、edition 2024。
- R1.2 猜数游戏综合（the book Ch.2）：输入/循环/match/crate 的 feature 速览。
- R1.3 包/crate/模块系统（Ch.7）：路径、可见性、use、模块树。
- R1.4 自动化测试（Ch.11）：#[test]、单元/集成测试、cargo test。
- R1.5 cargo 进阶与文档（Ch.14）：workspace、发布、rustdoc、profile。

多来源比对（强制）：每个内容项 ≥2 个独立来源交叉核对，优先一手，一手优先级 The Rust Programming Language book > The Rust Reference > The Rustonomicon；来源打架时两边都记、点明分歧、不和稀泥。rustc 实机复现为可选补充（如 cargo new / cargo test / cargo doc 真跑），不能替代多来源比对。不编造版本号/默认值/命令输出，来源不足标「待核」并标核实日期。章节末集中列来源与时效，不逐项脚注。
版本处标「⚙演进快·锚版本」：the book 对齐 Rust 1.90.0 + edition 2024，本机 rustc/cargo 1.94.1，二者 delta 现查不凭记忆；edition 2024 相关默认项如实标版本。

权威锚点清单（取自 round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book（Klabnik/Nichols/Krycho），活文档，声明 Rust 1.90.0 + edition 2024，Ch.1–2,7,11,14 https://doc.rust-lang.org/book/
- The Rust Reference https://doc.rust-lang.org/reference/
- 本机 rustc/cargo 1.94.1（cargo --version / rustc --version 实测）

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认本大主题作为 findings/037 内的 R1 章群（1 份）；若与其余大主题合并后过长，按 v3 规则拆 037-rust-memory-safety-<slug>-a/-b 并写明理由。

落盘：下游自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（报告须自足，复现命令/环境串贴进正文；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R2 所有权与 move 语义 — 报告prompt

任务：为「L5-14·大主题R2 所有权与 move 语义」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明（是什么/定义/关键结论，正确）+ 充分辅助说明（初学者的直觉/为什么/最小例子/易错点/关联，写到懂为止，可多段，必需）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R2（小主题 R2.1–R2.4）：
- R2.1 栈/堆与所有权三规则：每值一 owner、离开作用域即释放。
- R2.2 move 与 Copy：移动语义、Copy trait、clone；仿射类型的工程化（理论根→L5-06，此处只做一句交叉引用，不展开）。
- R2.3 参数/返回的所有权流转：传入=移动、返回=移出。
- R2.4 Drop 与 RAII：析构顺序、作用域结束释放，对比 C++ RAII。

多来源比对（强制）：每内容项 ≥2 独立来源交叉核对，优先一手（the book Ch.4 > Reference > Nomicon）；冲突两边都记、点明分歧。rustc 实机复现可选（如 use-after-move 报错 E0382、impl Drop 观察析构打印顺序），以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」：the book 对齐 1.90.0，本机 rustc 1.94.1，delta 现查。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book Ch.4 https://doc.rust-lang.org/book/
- The Rust Reference（destructors/ownership 语义）https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R2 章群（1 份）；过长按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R3 借用、引用与借用检查器 — 报告prompt

任务：为「L5-14·大主题R3 借用、引用与借用检查器」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R3（小主题 R3.1–R3.5）：
- R3.1 共享借用与可变借用：& / &mut 语义。
- R3.2 别名异或可变：aliasing XOR mutability 核心不变式（可一句交叉引用「这是编译期别名分析→L6-02 的工程内建」，不展开）。
- R3.3 借用检查器与 NLL：非词法生命周期、区域推断（基线借用检查器）。
- R3.4 Polonius 状态（待核）：基于 datalog 的下一代借用检查器是否已成默认——如实标「待核」，基线仍以 NLL 为主。
- R3.5 悬垂引用与切片：防悬垂、切片借用。

多来源比对（强制）：每内容项 ≥2 独立来源，优先一手（the book Ch.4 > Reference > Nomicon）；冲突两边都记、点明分歧。rustc 报错复现可选：E0502（可变借用与共享借用冲突）、E0499（两个 &mut）、E0106/返回悬垂引用——以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效、点明冲突项。
版本处标「⚙演进快·锚版本」：the book 对齐 1.90.0、本机 rustc 1.94.1，delta 现查；**R3.4 Polonius 是否成默认借用检查器待核**（基线 NLL；-Zpolonius 为 nightly，本机稳定版可用性待核，如实标不下结论）。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book Ch.4 https://doc.rust-lang.org/book/
- The Rust Reference https://doc.rust-lang.org/reference/
- The Rustonomicon（别名/借用）https://doc.rust-lang.org/nomicon/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R3 章群（1 份）；本大主题机制密集（含待核 Polonius），过长可按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R4 生命周期与区域推断 — 报告prompt

任务：为「L5-14·大主题R4 生命周期与区域推断」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R4（小主题 R4.1–R4.4）：
- R4.1 生命周期标注语法：'a、函数签名中的约束（区域理论根→L5-06，一句交叉引用不展开）。
- R4.2 省略规则（elision）：三条输入/输出省略规则。
- R4.3 结构体中的生命周期：持引用的 struct 标注。
- R4.4 'static 与生命周期界：'static、T: 'a 约束。

多来源比对（强制）：每内容项 ≥2 独立来源，优先一手（the book Ch.10 > Reference）；冲突两边都记、点明分歧。rustc 报错复现可选：E0106（缺生命周期标注）、E0621/E0597（生命周期界/借用活得不够久）——以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」：the book 对齐 1.90.0、本机 rustc 1.94.1，省略规则条数与 edition 相关默认现查、delta 不凭记忆。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book Ch.10 https://doc.rust-lang.org/book/
- The Rust Reference（lifetime elision）https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R4 章群（1 份）；过长按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R5 代数数据类型与模式匹配 — 报告prompt

任务：为「L5-14·大主题R5 代数数据类型与模式匹配」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R5（小主题 R5.1–R5.4）：
- R5.1 struct 建模（Ch.5）：具名/元组/单元 struct、方法。
- R5.2 enum 与 Option（Ch.6）：和类型、Option（积/和类型工程化→L5-06 T4，一句交叉引用不展开）。
- R5.3 match 与穷尽性检查：match、编译期穷尽性。
- R5.4 模式语法全谱（Ch.18）：解构/守卫/绑定/范围/if let。

多来源比对（强制）：每内容项 ≥2 独立来源，优先一手（the book Ch.5–6,18 > Reference 的 patterns 章）；冲突两边都记、点明分歧。rustc 报错复现可选：E0004（非穷尽 match）——以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」：the book 对齐 1.90.0、本机 rustc 1.94.1，delta 现查。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book Ch.5–6,18 https://doc.rust-lang.org/book/
- The Rust Reference（patterns / match）https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R5 章群（1 份）；过长按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R6 泛型、trait 与类型推导 — 报告prompt

任务：为「L5-14·大主题R6 泛型、trait 与类型推导」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R6（小主题 R6.1–R6.5）：
- R6.1 泛型与单态化：泛型函数/结构/枚举、monomorphization。
- R6.2 trait 定义与实现：trait、默认方法、孤儿规则。
- R6.3 trait bound 与 where：约束式多态（受限多态/类型类根→L5-06 T7–T9，一句交叉引用不展开）。
- R6.4 trait 对象与动态分发：dyn、对象安全、vtable。
- R6.5 局部类型推导：表达式级推断（非全程 HM）。

多来源比对（强制）：每内容项 ≥2 独立来源，优先一手（the book Ch.10 > Reference）；冲突两边都记、点明分歧。rustc 复现可选：E0277（未满足 trait bound）、--emit=llvm-ir 看单态化产物——以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」：the book 对齐 1.90.0、本机 rustc 1.94.1，delta 现查。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book Ch.10 https://doc.rust-lang.org/book/
- The Rust Reference（traits / type inference）https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R6 章群（1 份）；过长按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R7 错误处理 — 报告prompt

任务：为「L5-14·大主题R7 错误处理」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R7（小主题 R7.1–R7.4）：
- R7.1 panic! 与不可恢复错误：栈展开 vs abort、panic= 设置。
- R7.2 Result 与可恢复错误：Result<T,E> 建模。
- R7.3 ? 运算符与传播：错误传播、From 转换。
- R7.4 何时 panic 的准则：契约违反 vs 可预期失败。

多来源比对（强制）：每内容项 ≥2 独立来源，优先一手（the book Ch.9 > Reference）；冲突两边都记、点明分歧。rustc 复现可选：触发 panic 看 backtrace、? 链式示例编译——以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」：the book 对齐 1.90.0、本机 rustc 1.94.1，panic 运行时/abort 相关默认现查、delta 不凭记忆。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book Ch.9 https://doc.rust-lang.org/book/
- The Rust Reference（panic / unwinding）https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R7 章群（1 份）；过长按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R8 智能指针与内部可变性 — 报告prompt

任务：为「L5-14·大主题R8 智能指针与内部可变性」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R8（小主题 R8.1–R8.5）：
- R8.1 Box<T>：堆分配、递归类型定长化。
- R8.2 Deref 与 Drop：解引用强制、自定义析构。
- R8.3 Rc<T> 共享所有权：单线程引用计数。
- R8.4 RefCell/Cell 内部可变性：运行期借用检查（把编译期规则换成运行期逃生阀，一句点题）。
- R8.5 循环引用与 Weak：Rc 环导致泄漏、Weak 破环。

多来源比对（强制）：每内容项 ≥2 独立来源，优先一手（the book Ch.15 > 标准库 std 文档 / Reference）；冲突两边都记、点明分歧。rustc 复现可选：RefCell 运行期 panic "already borrowed"、构造 Rc 环观察泄漏——以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」：the book 对齐 1.90.0、本机 rustc 1.94.1，delta 现查。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book Ch.15 https://doc.rust-lang.org/book/
- The Rust Reference / std 标准库文档（Box/Rc/RefCell/Weak）https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R8 章群（1 份）；过长按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R9 无畏并发 — 报告prompt

任务：为「L5-14·大主题R9 无畏并发」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R9（小主题 R9.1–R9.4）：
- R9.1 线程与 move 闭包：thread::spawn、闭包捕获。
- R9.2 消息传递（channel）：mpsc、所有权随消息转移。
- R9.3 共享状态 Mutex/Arc：Mutex<T> + Arc。
- R9.4 Send/Sync：标记 trait、编译期消除数据竞争（交 L4-05 并发；此处=类型系统层拦截，一句交叉引用不展开）。

多来源比对（强制）：每内容项 ≥2 独立来源，优先一手（the book Ch.16 > Reference / std 文档）；冲突两边都记、点明分歧。rustc 复现可选：跑多线程示例、跨线程传非 Send 类型的报错——以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」：the book 对齐 1.90.0、本机 rustc 1.94.1，delta 现查。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book Ch.16 https://doc.rust-lang.org/book/
- The Rust Reference（Send/Sync 语义）https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R9 章群（1 份）；过长按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R10 异步编程 — 报告prompt

任务：为「L5-14·大主题R10 异步编程」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R10（小主题 R10.1–R10.4）：
- R10.1 async/await 与 Future：async fn 编译为状态机、Future trait。
- R10.2 执行器/运行时：标准库不含运行时，需第三方（Ch.17 是否引入具体运行时——待核）。
- R10.3 流（Stream）与并发原语：异步流、join/select。
- R10.4 与线程模型的取舍：async vs 线程的权衡。

多来源比对（强制）：每内容项 ≥2 独立来源，优先一手（the book Ch.17 > Reference / std::future 文档）；冲突两边都记、点明分歧。rustc 复现可选：--emit=llvm-ir 看 async 状态机——以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」（本大主题演进最快，硬标）：the book 对齐 1.90.0、本机 rustc 1.94.1，delta 现查；**R10.2 the book Ch.17 是否引入具体 async 运行时、以及运行时是否随版本变化——待核，对齐本机 1.94.1 现查不凭记忆**。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rust Programming Language book Ch.17 https://doc.rust-lang.org/book/
- The Rust Reference / std::future 文档 https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R10 章群（1 份）；过长按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R11 unsafe 与安全边界 — 报告prompt

任务：为「L5-14·大主题R11 unsafe 与安全边界」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R11（小主题 R11.1–R11.4）：
- R11.1 unsafe 的五种超能力：裸指针解引用 / 调 unsafe fn / 访问 static mut / impl unsafe trait / union。
- R11.2 裸指针与安全抽象封装：不变式义务、把 unsafe 封在安全 API 内。
- R11.3 FFI 与 extern：extern "C"、ABI 边界（承 L3-03 ABI，一句交叉引用不展开）。
- R11.4 UB 与 Nomicon 不变式：未定义行为、别名/对齐/初始化义务。

多来源比对（强制）：每内容项 ≥2 独立来源，本大主题**优先一手 The Rustonomicon > The Rust Reference > the book Ch.20**（unsafe 的一手在 Nomicon/Reference）；冲突两边都记、点明分歧。rustc 复现可选：写 *const/*mut 示例、链接一个 C 函数——以本机 rustc 1.94.1 现跑输出为准、不凭记忆；不能替代多来源比对。不编造，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」：the book 对齐 1.90.0、本机 rustc 1.94.1，delta 现查；**R11.4 Miri 检测 UB 的本机可用性待核**（如实标，工具不可用则降为概念+只读 Nomicon，不下结论）。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- The Rustonomicon（unsafe 一手）https://doc.rust-lang.org/nomicon/
- The Rust Reference（unsafe/ABI 语义规范）https://doc.rust-lang.org/reference/
- The Rust Programming Language book Ch.20 https://doc.rust-lang.org/book/
- 本机 rustc 1.94.1

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。默认作为 findings/037 内 R11 章群（1 份）；过长按 v3 拆 037-...-<slug>-a/-b 并写理由。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

```text
[P2] L5-14·大主题R12 形式基础与保证 — 报告prompt

任务：为「L5-14·大主题R12 形式基础与保证」产出一份 report-format v3（study/cs-kb/report-format.md）教辅报告。面向初学者、广度优先，讲到初学者能懂但不做专家纵深（RustBelt 只讲到初学者懂「它证明了什么、为什么重要」，不做证明纵深）。

v3 格式硬性（逐条遵守）：
- 标题层级严格 #→##→###。
- ### 之下不写任何标签，直接正文分段：核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，必需，可多段）。
- Rust 代码片段独占块级（```rust）；公式独占行。
- 教辅口吻；广度完备。
- 报告抬头带 v3 模板行 + 可 grep 基线串 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）`。

小主题清单与应覆盖内容项——细目见 round3b-subtopics/g08-languages-compilers.md 中 L5-14 大主题 R12（小主题 R12.1–R12.4）：
- R12.1 RustBelt 目标：为含 unsafe 的标准库建立机器化可靠性。
- R12.2 语义类型与 Iris 分离逻辑：语义可定型、Iris 基础。
- R12.3 λRust 演算与借用模型：借用/生命周期的形式模型。
- R12.4 四门汇合点：回接 L5-06 T3 类型安全（Progress+Preservation）+ L6-02 验证半场（各作一句交叉引用点题，不展开）。

多来源比对（强制）：每内容项 ≥2 独立来源，优先一手 RustBelt（Jung 等 POPL'18 论文）> 相关官方/作者材料；冲突两边都记、点明分歧。本大主题为形式基础，无本机实证（rustc 复现不适用），如实标「无本机实证」。不编造论文结论/术语，缺据标「待核」+核实日期。章节末集中列来源与时效。
版本处标「⚙演进快·锚版本」：RustBelt 针对当时的 Rust 语义，与本机 rustc 1.94.1 的 delta 现查、不凭记忆；论文结论按发表版本记，不外推。

权威锚点清单（round3a/g08，核实 2026-07-25）：
- RustBelt（Jung, Jourdan, Krebbers, Dreyer, POPL'18；形式基础）
- The Rustonomicon（unsafe 库合理性背景）https://doc.rust-lang.org/nomicon/
- 交叉：L5-06 T3（Safety=Progress+Preservation）、L6-02 验证半场
- 本机 rustc 1.94.1（仅作版本锚，无实证）

粒度判定：下游先判「1 份 or 拆 N 份 + 理由」。本大主题小主题少（4）且偏概念，默认作为 findings/037 内 R12 章群（1 份），一般不拆。

落盘：自行 Write 到 study/cs-kb/findings/037-rust-memory-safety.md（自足；不残留工具标签/控制字符）。
```

共 12 条 prompt
