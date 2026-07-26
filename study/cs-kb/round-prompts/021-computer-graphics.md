# Round3c · L5-03 计算机图形学 · 报告 prompt（P2）

```text
[P2] L5-03·大主题G-01 图形管线概览与数学预备 — 报告prompt
任务：为「L5-03·大主题 G-01 图形管线概览与数学预备」产出一份 report-format v3（广度优先的"教辅"）报告，自己 Write 落盘到 study/cs-kb/findings/021-computer-graphics.md（若已存在则把本大主题作为 `#` 一节追加，勿覆盖他人成果；保持大主题按 G-01…G-10 顺序）。抬头带可 grep 基线串：`基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，并注明核实日期。

【粒度判定（先做）】开工先判"1 份 or 拆 N 份 + 理由"：本大主题偏概念+数学复习、小主题 5 个、不跨机制，默认判 1 份即可；若判拆分需写明理由与 `021-computer-graphics-a/-b` 切分点。把判定结论写进报告开头一行。

【v3 格式硬性】
- 标题层级严格 `#`（大主题=报告标题）→ `##`（小主题=章节）→ `###`（每个内容项一节）；`###` 下【不写「核心概念：」「辅助说明：」等标签】，直接正文，靠空行分段。
- 每个 `###` 内容项 = 核心说明（必需：是什么/定义/关键结论，正确）+ 充分辅助说明（必需：面向初学者的直觉/为什么/最小例子/易错点/前后关联，写到初学者能懂为止，可多段；不是可省点缀）。
- 任何矩阵/变换/光照/公式【独占一行】（块级），不内联埋进句子。
- 教辅口吻；广度优先——讲到初学者懂但不做专家级纵深（不写长篇机制深挖/完整推导/设计权衡长论）。

【小主题清单（`##` 章节，覆盖全 G-01.1…G-01.5；下游可读 study/cs-kb/round3b-subtopics/g10-graphics-embedded-hpc.md 中 L5-03 大主题 G-01 表核对内容项）】
- G-01.1 渲染任务定义、实时 vs 离线渲染的取舍谱系。
- G-01.2 图形管线阶段总览：应用 → 几何 → 光栅化 → 片元 → 输出合并（可用文字画流程）。
- G-01.3 点/向量/仿射空间与坐标系约定（列 vs 行向量、左手 vs 右手系）。
- G-01.4 线性映射、矩阵表示、内积/范数与正交基（含 Gram-Schmidt 直觉）。
- G-01.5 叉积、法线、重心坐标的几何直觉。

【多来源比对（强制）】每个内容项 ≥2 个独立来源交叉核对，优先一手：Real-Time Rendering 4th ed / CMU 15-462（Spring 2024）> Fundamentals of CG。冲突两边都记、点明分歧、不和稀泥。numpy 验证【可选】（如构造正交基、叉积求面法线、点在三角形内测试）——是补充不是替代，不能替代多来源比对。不编造数值/系数/约定，未证实标「待核」，标核实日期。

【章节末来源】每个 `##` 小主题末集中列来源与时效（教材§/规范/官方文档+版本+核实日期），不逐项脚注；前沿项标「⚙演进快·锚版本」；冲突项两边定位。

【权威锚点清单】
- 主锚：CMU 15-462/662 Computer Graphics（Spring 2024，Nancy Pollard），Lectures & Readings：http://15462.courses.cs.cmu.edu/spring2024/lectures（Intro + Linear Algebra/Vector Calculus Review lecture）。核实 2026-07-25。
- 副锚教材：Fundamentals of Computer Graphics（Marschner & Shirley）变换/数学章；Real-Time Rendering, 4th ed（管线概览章）。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

```text
[P2] L5-03·大主题G-02 几何变换与投影管线 — 报告prompt
任务：为「L5-03·大主题 G-02 几何变换与投影管线」产出一份 report-format v3 报告，自己 Write 追加到 study/cs-kb/findings/021-computer-graphics.md（作为 `#` 一节，按 G-01…G-10 顺序，勿覆盖）。抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，注明核实日期。

【粒度判定（先做）】判"1 份 or 拆 N 份 + 理由"：本大主题 6 个小主题、全在"变换→投影"一条主线上、机制连贯，默认 1 份；如判拆分写明理由与切分点，结论写进报告开头。

【v3 格式硬性】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文分段。
- 每个 `###` = 核心说明（必需）+ 充分辅助说明（必需，面向初学者：直觉/为什么/最小例子/易错点/关联，讲到懂为止）。
- 矩阵/变换/投影/公式【独占一行】（如 4×4 变换矩阵、透视投影矩阵、法线变换式），不内联。
- 教辅口吻；广度优先、讲到初学者懂、不做专家纵深。

【小主题清单（`##`，覆盖全 G-02.1…G-02.6；下游可读 round3b-subtopics 中 L5-03 大主题 G-02 表）】
- G-02.1 齐次坐标与基本仿射变换（平移/旋转/缩放）。
- G-02.2 复合变换、变换栈与坐标系链 model→world→view（矩阵乘非交换性）。
- G-02.3 相机/视图变换（lookAt 构造与正交性）。
- G-02.4 透视与正交投影矩阵、clip 空间与视锥（w 分量含义）。
- G-02.5 透视除法 → NDC → 视口变换到屏幕像素（完整 MVP + 视口映射）。
- G-02.6 法线变换用逆转置矩阵的推导直觉与陷阱（M vs (M⁻¹)ᵀ）。

【多来源比对（强制）】每项 ≥2 独立源，优先一手：CMU 15-462 Perspective Projection lecture / Real-Time Rendering 4th > Fundamentals of CG 变换/视图章。注意 clip 空间深度约定（OpenGL [-1,1] vs Vulkan/D3D [0,1]）、列主 vs 行主、左右手系差异——冲突两边都记、点明来源约定。numpy 变换管线验证【可选但推荐】（构造 4×4 矩阵作用于点、lookAt 正交性、投影后 w、MVP+视口全链路、法线用 (M⁻¹)ᵀ 对比）——补充不替代比对。不编造矩阵元素/约定，未证实标「待核」。

【章节末来源】每个 `##` 末集中列来源+版本+核实日期；深度/坐标约定冲突项两边定位。

【权威锚点清单】
- 主锚：CMU 15-462/662（Spring 2024）Perspective Projection / Transforms lecture：http://15462.courses.cs.cmu.edu/spring2024/lectures 。核实 2026-07-25。
- 副锚：Fundamentals of CG（Marschner & Shirley）变换与视图章；Real-Time Rendering 4th 变换章。
- 规范锚（深度约定佐证）：OpenGL 4.6 Core（NDC z∈[-1,1]，冻结 2022-05）、Vulkan 1.4（z∈[0,1]，2024-12）。核实 2026-07-25。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

```text
[P2] L5-03·大主题G-03 光栅化与可见性 — 报告prompt
任务：为「L5-03·大主题 G-03 光栅化与可见性」产出 report-format v3 报告，自己 Write 追加到 study/cs-kb/findings/021-computer-graphics.md（`#` 一节，按序，勿覆盖）。抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，注明核实日期。

【粒度判定（先做）】判"1 份 or 拆 N 份 + 理由"：本大主题 5 个小主题、集中于三角形光栅化与深度可见性，默认 1 份；如判拆分写明理由，结论写进报告开头。

【v3 格式硬性】层级 `#`→`##`→`###`；`###` 下不写标签直接正文；每项核心说明+充分辅助说明（初学者向）；公式（边函数、重心坐标、透视校正插值 1/w 式）【独占一行】；教辅口吻、广度优先、讲到懂不做专家纵深。

【小主题清单（`##`，覆盖全 G-03.1…G-03.5；下游可读 round3b-subtopics 中 L5-03 大主题 G-03 表）】
- G-03.1 三角形 setup 与遍历（edge function 边函数 / 包围盒扫描）。
- G-03.2 重心坐标与顶点属性线性插值。
- G-03.3 透视校正插值（1/w 插值）与错误对照（仿射 vs 透视校正）。
- G-03.4 深度缓冲 Z-buffer 与逐像素可见性。
- G-03.5 裁剪、背面剔除与覆盖/多重采样覆盖。

【多来源比对（强制）】每项 ≥2 独立源，优先一手：CMU 15-462 Rasterization / Visibility lecture / Real-Time Rendering 4th > Fundamentals of CG。注意 top-left 填充规则、z-buffer 精度/[0,1] 约定等差异——冲突两边都记。numpy 软光栅验证【可选】（边函数覆盖、重心插值颜色、仿射 vs 透视校正对照、z-buffer 解遮挡、背面剔除法线·视向符号）——补充不替代比对。不编造，未证实标「待核」。

【章节末来源】每个 `##` 末集中列来源+版本+核实日期；冲突项两边定位。

【权威锚点清单】
- 主锚：CMU 15-462/662（Spring 2024）Rasterization / Visibility lecture：http://15462.courses.cs.cmu.edu/spring2024/lectures 。核实 2026-07-25。
- 副锚：Real-Time Rendering 4th 光栅化/可见性章；Fundamentals of CG 光栅化章。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

```text
[P2] L5-03·大主题G-04 采样、走样与信号处理 — 报告prompt
任务：为「L5-03·大主题 G-04 采样、走样与信号处理」产出 report-format v3 报告，自己 Write 追加到 study/cs-kb/findings/021-computer-graphics.md（`#` 一节，按序，勿覆盖）。抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，注明核实日期。

【粒度判定（先做）】判"1 份 or 拆 N 份 + 理由"：5 个小主题、围绕采样/滤波/抗锯齿一条线，默认 1 份；如判拆分写理由，结论写进报告开头。

【v3 格式硬性】层级 `#`→`##`→`###`；不写标签直接正文；每项核心说明+充分辅助说明（初学者向）；公式（奈奎斯特频率、卷积式、over 合成式）【独占一行】；教辅口吻、广度优先、讲到懂不做专家纵深。

【小主题清单（`##`，覆盖全 G-04.1…G-04.5；下游可读 round3b-subtopics 中 L5-03 大主题 G-04 表）】
- G-04.1 采样定理与奈奎斯特频率。
- G-04.2 混叠成因（几何边缘/纹理/时间闪烁）。
- G-04.3 滤波与卷积、重建滤波器。
- G-04.4 抗锯齿：超采样 SSAA、MSAA、预滤波。
- G-04.5 Alpha 合成与 over 运算符、预乘 alpha。

【多来源比对（强制）】每项 ≥2 独立源，优先一手：CMU 15-462 Sampling & Aliasing / Filtering, Convolution / Compositing lecture / Real-Time Rendering 4th（抗锯齿章）> Fundamentals of CG 采样章。注意 MSAA 与 SSAA 定义/覆盖-着色采样区分、预乘 vs 非预乘 alpha 约定——冲突两边都记。numpy/scipy 验证【可选】（欠采样棋盘显混叠、盒式/高斯核卷积、超采样降采样对比边缘、over 合成两图层）——补充不替代比对。不编造，未证实标「待核」。

【章节末来源】每个 `##` 末集中列来源+版本+核实日期；冲突项两边定位。

【权威锚点清单】
- 主锚：CMU 15-462/662（Spring 2024）Sampling & Aliasing / Filtering, Convolution / Compositing lecture：http://15462.courses.cs.cmu.edu/spring2024/lectures 。核实 2026-07-25。
- 副锚：Real-Time Rendering 4th 抗锯齿/合成章；Fundamentals of CG 采样与信号处理章。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

```text
[P2] L5-03·大主题G-05 纹理映射与着色/光照模型 — 报告prompt
任务：为「L5-03·大主题 G-05 纹理映射与着色/光照模型」产出 report-format v3 报告，自己 Write 追加到 study/cs-kb/findings/021-computer-graphics.md（`#` 一节，按序，勿覆盖）。抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，注明核实日期。

【粒度判定（先做）】判"1 份 or 拆 N 份 + 理由"：5 个小主题、纹理+经验光照一条线，默认 1 份；如判拆分写理由，结论写进报告开头。

【v3 格式硬性】层级 `#`→`##`→`###`；不写标签直接正文；每项核心说明+充分辅助说明（初学者向）；光照公式（Lambert 漫反射、Phong/Blinn-Phong 高光式）【独占一行】；教辅口吻、广度优先、讲到懂不做专家纵深。

【小主题清单（`##`，覆盖全 G-05.1…G-05.5；下游可读 round3b-subtopics 中 L5-03 大主题 G-05 表）】
- G-05.1 纹理坐标 (u,v) 与参数化、寻址模式（wrap/clamp）。
- G-05.2 纹理过滤（最近/双线性/三线性）与 mipmap 链。
- G-05.3 漫反射 Lambert 与镜面 Phong / Blinn-Phong。
- G-05.4 法线贴图/凹凸贴图与环境（反射）贴图。
- G-05.5 材质参数与多光源着色累加。

【多来源比对（强制）】每项 ≥2 独立源，优先一手：Real-Time Rendering 4th（着色章）/ CMU 15-462 Texture Mapping lecture > Fundamentals of CG。注意 Phong vs Blinn-Phong 半程向量差异、高光指数与能量归一化约定——冲突两边都记，并点明这些是经验模型（G-09 讲物理版）。numpy 光照管线验证【可选】（uv 采样查表、双线性插值、逐像素 Blinn-Phong、扰动法线重算高光、多光源求和）——补充不替代比对。不编造系数，未证实标「待核」。

【章节末来源】每个 `##` 末集中列来源+版本+核实日期；经验 vs 物理模型的辨析与冲突项两边定位。

【权威锚点清单】
- 主锚：CMU 15-462/662（Spring 2024）Texture Mapping lecture：http://15462.courses.cs.cmu.edu/spring2024/lectures 。核实 2026-07-25。
- 副锚：Real-Time Rendering 4th 着色/纹理章；Fundamentals of CG 纹理与着色章。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

```text
[P2] L5-03·大主题G-06 可编程管线与实时渲染 API — 报告prompt
任务：为「L5-03·大主题 G-06 可编程管线与实时渲染 API」产出 report-format v3 报告，自己 Write 追加到 study/cs-kb/findings/021-computer-graphics.md（`#` 一节，按序，勿覆盖）。抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，注明核实日期。

【粒度判定（先做）】判"1 份 or 拆 N 份 + 理由"：5 个小主题、含两套 API 规范（GL/Vulkan），默认 1 份；若判 Vulkan 显式模型内容过重可考虑拆分，写明理由与切分点，结论写进报告开头。

【重要分账约束】本大主题是"同一 GPU 硬件的图形视角"，只讲可编程渲染管线与 API；【不要】展开通用计算/性能优化（那是 L6-06 的 H-05/H-06），仅在辨析处一句点到。

【v3 格式硬性】层级 `#`→`##`→`###`；不写标签直接正文；每项核心说明+充分辅助说明（初学者向）；如有公式独占一行；教辅口吻、广度优先、讲到懂不做专家纵深。

【小主题清单（`##`，覆盖全 G-06.1…G-06.5；下游可读 round3b-subtopics 中 L5-03 大主题 G-06 表）】
- G-06.1 GPU 固定功能 vs 可编程阶段的分工演进。
- G-06.2 顶点着色器阶段职责（变换/属性输出，复用 G-02 直觉）。
- G-06.3 光栅化插值 → 片元着色器阶段职责（复用 G-03/G-05 直觉）。
- G-06.4 OpenGL 4.6 Core 状态机与绘制提交模型 🔴（4.6 已冻结，末次 spec 2022-05-05）。
- G-06.5 Vulkan 1.4 显式模型（队列/命令缓冲/管线对象/同步）🔴（1.4，2024-12 活跃线）。

【多来源比对（强制）】每项 ≥2 独立源，优先一手规范：OpenGL 4.6 Core Profile spec / Vulkan 1.4 spec（Khronos 官方）+ Real-Time Rendering 4th。GL（隐式状态机）与 Vulkan（显式、程序员管同步/内存）模型差异要显式对照。本大主题【无 GL 上下文/无 GPU → 文档腿·未实证】，硬标不实证、不编造 API 调用输出。numpy 仅可模拟顶点/片元阶段逻辑（可选，复用 G-02/03/05）。版本号硬标「⚙演进快·锚版本」，未证实标「待核」。

【章节末来源】每个 `##` 末集中列来源+版本+核实日期；GL vs Vulkan 模型差异作辨析并两边定位。

【权威锚点清单】
- 规范锚：OpenGL 4.6 Core Profile（Khronos，末次 spec 更新 2022-05-05，已冻结/最终版）；Vulkan 1.4（Khronos，2024-12，当前活跃线）。核实 2026-07-25。
- 副锚：Real-Time Rendering 4th 实时渲染管线/API 章；CMU 15-462（Spring 2024）管线相关 lecture。
- 分账提示：GPU 通用计算/性能属 L6-06（H-05/H-06），本报告不覆盖。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

```text
[P2] L5-03·大主题G-07 几何表示：曲线曲面/网格/细分/空间数据结构 — 报告prompt
任务：为「L5-03·大主题 G-07 几何表示：曲线曲面/网格/细分/空间数据结构」产出 report-format v3 报告，自己 Write 追加到 study/cs-kb/findings/021-computer-graphics.md（`#` 一节，按序，勿覆盖）。抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，注明核实日期。

【粒度判定（先做）】判"1 份 or 拆 N 份 + 理由"：本大主题 6 个小主题、跨"曲线曲面 / 网格与细分 / 空间数据结构"三块机制，内容偏多；请认真判是否拆 2 份（如 `-a` 曲线曲面+网格细分、`-b` 空间数据结构），给理由与切分点，结论写进报告开头。

【v3 格式硬性】层级 `#`→`##`→`###`；不写标签直接正文；每项核心说明+充分辅助说明（初学者向）；公式（de Casteljau 递推、Bézier/B 样条基、细分权重）【独占一行】；教辅口吻、广度优先、讲到懂不做专家纵深。

【小主题清单（`##`，覆盖全 G-07.1…G-07.6；下游可读 round3b-subtopics 中 L5-03 大主题 G-07 表）】
- G-07.1 参数曲线：Bézier 与 B 样条、de Casteljau 算法。
- G-07.2 参数曲面与张量积曲面。
- G-07.3 多边形网格与半边（half-edge）数据结构。
- G-07.4 细分曲面（Loop / Catmull-Clark 规则）。
- G-07.5 网格化与重网格化概念。
- G-07.6 空间层次：BVH / kd-tree / 八叉树 / 均匀网格。

【多来源比对（强制）】每项 ≥2 独立源，优先一手：CMU 15-462 Curves & Surfaces / Meshing / Subdivision / Spatial Hierarchies lecture / Real-Time Rendering 4th > Fundamentals of CG。注意 Loop（三角网格）与 Catmull-Clark（四边网格）适用面、半边数据结构字段约定差异——冲突两边都记。numpy/Python 验证【可选】（de Casteljau 求值、张量积 Bézier 曲面、半边邻接遍历、一步 Loop 细分、2D 网格/BVH 分桶）——补充不替代比对。不编造细分权重/规则，未证实标「待核」。

【章节末来源】每个 `##` 末集中列来源+版本+核实日期；冲突项两边定位。

【权威锚点清单】
- 主锚：CMU 15-462/662（Spring 2024）Curves & Surfaces / Meshing / Subdivision / Spatial Hierarchies lecture：http://15462.courses.cs.cmu.edu/spring2024/lectures 。核实 2026-07-25。
- 副锚：Fundamentals of CG（曲线曲面/网格章）；Real-Time Rendering 4th（加速结构章）；PBRT（几何/加速结构在线章）。
- 跨课提示：空间层次也是光追加速结构（↔ G-08）；本报告讲几何数据结构视角，光追遍历放 G-08。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

```text
[P2] L5-03·大主题G-08 光线追踪与加速结构 — 报告prompt
任务：为「L5-03·大主题 G-08 光线追踪与加速结构」产出 report-format v3 报告，自己 Write 追加到 study/cs-kb/findings/021-computer-graphics.md（`#` 一节，按序，勿覆盖）。抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，注明核实日期。

【粒度判定（先做）】判"1 份 or 拆 N 份 + 理由"：5 个小主题、集中在求交+BVH+光追硬件直觉，默认 1 份；如判拆分写理由，结论写进报告开头。

【v3 格式硬性】层级 `#`→`##`→`###`；不写标签直接正文；每项核心说明+充分辅助说明（初学者向）；公式（光线参数式、球/平面/三角求交、Möller–Trumbore、AABB slab 测试）【独占一行】；教辅口吻、广度优先、讲到懂不做专家纵深。

【小主题清单（`##`，覆盖全 G-08.1…G-08.5；下游可读 round3b-subtopics 中 L5-03 大主题 G-08 表）】
- G-08.1 光线生成与光线-图元求交（球/平面/三角形，Möller–Trumbore）。
- G-08.2 BVH 构建（中点 / SAH 表面积启发式分裂）。
- G-08.3 BVH 遍历、AABB slab 测试与求交剔除。
- G-08.4 高性能光追：光线打包/SIMD、RT 硬件直觉 🔴（RT Core 演进快，本机无 GPU）。
- G-08.5 几何光学与光场（light field）直觉。

【多来源比对（强制）】每项 ≥2 独立源，优先一手：PBRT（求交/加速结构在线章）/ CMU 15-462 Ray Tracing / High-Performance Ray Tracing lecture > Real-Time Rendering 4th（实时光追章）。注意 SAH 代价模型细节差异——冲突两边都记。numpy 验证【可选】（Möller–Trumbore 光线-三角、光线-球、递归建小 BVH、slab 测试遍历）——补充不替代比对。G-08.4 的 RT 硬件【本机无 GPU → 文档腿·未实证】硬标，不编造硬件性能数字，版本/硬件标「⚙演进快·锚版本」。未证实标「待核」。

【章节末来源】每个 `##` 末集中列来源+版本+核实日期；冲突项两边定位。

【权威锚点清单】
- 主锚：CMU 15-462/662（Spring 2024）Ray Tracing / High-Performance Ray Tracing lecture：http://15462.courses.cs.cmu.edu/spring2024/lectures ；PBRT（在线开放，求交与加速结构章）。核实 2026-07-25。
- 副锚：Real-Time Rendering 4th 实时光追/加速结构章；Fundamentals of CG 光线追踪章。
- 跨课提示：BVH/空间层次与 G-07 共享（G-07 讲数据结构，此处讲光追遍历）；RT 并行/RT Core 与 L6-06 邻接。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

```text
[P2] L5-03·大主题G-09 辐射度量、反射与蒙特卡洛渲染 — 报告prompt
任务：为「L5-03·大主题 G-09 辐射度量、反射与蒙特卡洛渲染」产出 report-format v3 报告，自己 Write 追加到 study/cs-kb/findings/021-computer-graphics.md（`#` 一节，按序，勿覆盖）。抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，注明核实日期。

【粒度判定（先做）】判"1 份 or 拆 N 份 + 理由"：本大主题 7 个小主题、跨"辐射度量与反射 / 蒙特卡洛与路径追踪 / 相机"，内容偏多；请认真判是否拆 2 份（如 `-a` 辐射度量+BRDF+渲染方程、`-b` 蒙卡+重要性采样+路径追踪+相机），给理由与切分点，结论写进报告开头。

【v3 格式硬性】层级 `#`→`##`→`###`；不写标签直接正文；每项核心说明+充分辅助说明（初学者向）；公式（辐射率定义、渲染方程反射积分形式、MC 估计量、重要性采样 pdf 权重）【独占一行】；教辅口吻、广度优先、讲到懂不做专家纵深。

【小主题清单（`##`，覆盖全 G-09.1…G-09.7；下游可读 round3b-subtopics 中 L5-03 大主题 G-09 表）】
- G-09.1 辐射度量量纲：辐射通量/辐照度/辐射率（radiance）。
- G-09.2 BRDF 与反射模型（理想漫反/镜面/微表面），能量守恒。
- G-09.3 渲染方程（反射积分形式）。
- G-09.4 蒙特卡洛积分与估计量方差。
- G-09.5 重要性采样与方差缩减。
- G-09.6 路径追踪与俄罗斯轮盘赌终止。
- G-09.7 相机模型（针孔 / 薄透镜景深）。

【多来源比对（强制）】每项 ≥2 独立源，优先一手：PBRT（辐射度量/反射/蒙卡/相机在线章）/ CMU 15-462 Radiometry / Reflectance / Monte Carlo Rendering / Importance Sampling / Camera Models lecture > Real-Time Rendering 4th。注意 radiance 定义（每单位投影面积每单位立体角）与量纲、渲染方程符号约定差异——冲突两边都记。numpy 验证【可选】（BRDF 能量守恒检查、MC 估计积分误差随 N 收敛、均匀 vs 重要性采样方差对比、小路径追踪玩具、针孔投影/薄透镜采样）——补充不替代比对。不编造量纲/系数，未证实标「待核」。

【章节末来源】每个 `##` 末集中列来源+版本+核实日期；符号/量纲约定冲突项两边定位。

【权威锚点清单】
- 主锚：CMU 15-462/662（Spring 2024）Radiometry / Reflectance / Monte Carlo Rendering / Importance Sampling / Camera Models lecture：http://15462.courses.cs.cmu.edu/spring2024/lectures ；PBRT（在线开放，物理渲染章）。核实 2026-07-25。
- 副锚：Real-Time Rendering 4th 基于物理着色章；Fundamentals of CG 光传输章。
- 跨课提示：蒙卡估计/方差/重要性采样 ↔ L2-03 概率；积分 ↔ L1-04；本报告讲图形语境。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

```text
[P2] L5-03·大主题G-10 物理动画与数值方法 — 报告prompt
任务：为「L5-03·大主题 G-10 物理动画与数值方法」产出 report-format v3 报告，自己 Write 追加到 study/cs-kb/findings/021-computer-graphics.md（`#` 一节，按序，勿覆盖）。抬头带 `基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，注明核实日期。

【粒度判定（先做）】判"1 份 or 拆 N 份 + 理由"：本大主题 7 个小主题、跨"时间积分与物理动画 / 优化与数值线代 / IK 与数据拟合"，内容偏多且机制分散；请认真判是否拆 2 份（如 `-a` ODE/时间积分/物理动画/数值微分、`-b` 优化/数值线代/IK/数据拟合），给理由与切分点，结论写进报告开头。

【v3 格式硬性】层级 `#`→`##`→`###`；不写标签直接正文；每项核心说明+充分辅助说明（初学者向）；公式（显式/隐式 Euler、RK4、辛积分更新式、前向/中心差分、梯度下降/牛顿更新、雅可比伪逆、最小二乘正规方程）【独占一行】；教辅口吻、广度优先、讲到懂不做专家纵深。

【小主题清单（`##`，覆盖全 G-10.1…G-10.7；下游可读 round3b-subtopics 中 L5-03 大主题 G-10 表）】
- G-10.1 ODE 与时间积分器（显式/隐式 Euler、RK4、辛积分）。
- G-10.2 数值微分与差分近似误差（阶）。
- G-10.3 基于物理的动画（质点-弹簧、刚体基础）。
- G-10.4 优化基础（梯度下降 / 牛顿法）。
- G-10.5 数值线代（直接解 vs 迭代解）。
- G-10.6 逆运动学（雅可比转置/伪逆）。
- G-10.7 数据拟合（最小二乘）。

【多来源比对（强制）】每项 ≥2 独立源，优先一手：CMU 15-462 Physically-Based Animation / Optimization / Numerical Linear Algebra / Inverse Kinematics lecture / Real-Time Rendering 4th（动画相关章）> 数值方法权威教材（如 Fundamentals of CG 相关章、标准数值分析教材）。注意显式 Euler 能量漂移 vs 辛积分能量守恒、差分误差阶、雅可比转置 vs 伪逆 IK 差异——冲突两边都记。numpy/scipy/sympy 验证【可选但推荐】（各积分器能量漂移对比、前向/中心差分误差阶、质点-弹簧仿真、scipy.optimize 最小化、直接 vs 迭代解线性系统、2/3 连杆 IK 雅可比迭代、lstsq 拟合）——补充不替代比对。不编造误差阶/系数，未证实标「待核」。

【章节末来源】每个 `##` 末集中列来源+版本+核实日期；冲突项两边定位。

【权威锚点清单】
- 主锚：CMU 15-462/662（Spring 2024）Physically-Based Animation / Optimization / Numerical Linear Algebra / Inverse Kinematics lecture：http://15462.courses.cs.cmu.edu/spring2024/lectures 。核实 2026-07-25。
- 副锚：Fundamentals of CG 动画/数值章；Real-Time Rendering 4th 动画章；标准数值分析教材佐证误差阶与积分器性质。
- 跨课提示：ODE/积分器 ↔ L1-04 微积分；数值线代 ↔ L1-03；大规模仿真并行 ↔ L6-06；本报告讲图形动画语境。
- 卫生红线：实证代码只写 scratchpad，报告不残留工具标签/控制字符。
```

共 10 条 prompt
