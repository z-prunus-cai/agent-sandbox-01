# 参数页的两种实现

文中上游源码与文档写成 `<owner>/<name>@<version>` 的 `<path>`，即上游仓库在该版本的路径，代码再写出所在的类与方法，文档再写出所在的章节标题，上游仓库与版本见 [BRIEF.md 第 8 节](../BRIEF.md#8-出处)。

本文说明部署引擎提供的两种参数页：Active Choices 版（Jenkinsfile 中 `form: 'active-choices'`，Active Choices 插件 2.8.11；示例的两个 job `examples/pipelines/readwrite/Jenkinsfile` 和 `examples/pipelines/readonly/Jenkinsfile` 都用这一种）和无插件版（Jenkinsfile 中 `form: 'basic'`，只用 Jenkins 自带功能，构建开始后用 `input` 询问任务参数）：页面上有什么、怎样生成、提交后的值是什么格式、引擎怎样把它转成执行计划。两种方案共用 `examples/platform/jenkins/` 下的共享库（`vars/deployPipeline.groovy` 和 `src/deploy/` 中的类），都由 `ParameterPage.draft` 得到与 `DEPLOY_PLAN` 结构相同的计划草稿，再由 `PlanParser.parse` 统一校验。

## 1. 对比

| 能力 | Active Choices 版 | 无插件版 |
| --- | --- | --- |
| 选择主机组 | 勾选框（`TARGET_GROUPS`） | 每组一个勾选框（`TARGET_web` 等） |
| 选择任务 | 勾选框（`PRE_TASKS` / `POST_TASKS`） | 每个任务一个勾选框（`PRE_task_a` 等） |
| 只显示所选任务的参数 | 同一页上，勾选后立即出现 | 构建开始后由 `input` 步骤弹出 |
| 输入控件 | 整数为数字框，布尔、枚举、失败策略为下拉框，必填参数名后标 “ (required)”，每个任务一个带边框的分组 | 字符串和整数为文本框，布尔为勾选框，枚举、失败策略为下拉框；平铺一列 |
| 填写的值记在哪里 | 构建参数（构建页的 Parameters 中可见） | `input` 的提交记录；执行计划打印在控制台 |
| 等待填写时 | 不涉及 | 构建暂停在 `Plan` 阶段，该阶段不分配执行器，最多等 30 分钟；之后运行的是 `Prepare` 中 `stash` 的文件 |
| API / 上游 job 触发 | `DEPLOY_PLAN` | `DEPLOY_PLAN` |
| 需要的插件 | `uno-choice` | 无 |

## 2. 参数定义只能用 `properties()` 生成

主机组列表来自 `business/inventory/hosts.yml`，任务列表与参数来自各任务目录的 `business/tasks/<任务>/meta/task.yml`、`meta/argument_specs.yml` 和 `defaults/main.yml`，都要在构建运行时读取工作区才知道。所以构建参数由 `deployPipeline` 在 `Prepare` 阶段调用 `properties([disableConcurrentBuilds(), parameters(new ParameterPage(...).definitions())])` 写入 job，而不是写在 `pipeline { parameters { } }` 中。后果：

- 参数定义在构建运行时更新，**从下一次构建起**才出现在参数页上；新建的 job 要先运行一次（结果 NOT_BUILT）。
- 判断“是否第一次运行”必须用 `properties()` 之前读取的 `params`，所以 `deployPipeline` 在调用 `properties()` 之前先把 `params` 复制到 `requested`，再由 `RequestKind.of(requested)` 判断（见 [BRIEF.md 第 4.7 节](../BRIEF.md#47-第一次运行只登记参数params-必须在-properties-之前读取)）。

## 3. Active Choices 版

### 3.1 参数页上的脚本看不到工作区

Active Choices 的 Groovy 脚本在用户打开参数页、改变勾选时，在 Jenkins controller 上运行，没有工作区。所以 `ParameterPage.definitions` 在构建运行时用 `ActiveChoicesForm.literal` 把主机组和任务定义转成 Groovy 字面量，直接写进脚本文本。例如主机组列表的脚本（`ActiveChoicesForm.groupsScript` 生成）：

```groovy
def groups = ['web': ['web-01', 'web-02'], 'api': ['api-01'], 'worker': ['worker-01', 'worker-02']]
return groups.collectEntries { name, hosts -> [(name): name + ' (' + hosts.join(', ') + ')'] }
```

脚本返回 `[值: 显示文字]`：勾选框的值是 `web`，旁边显示 `web (web-01, web-02)`。脚本声明 `sandbox: true`，在 Groovy 沙箱中运行，不需要管理员审批。

### 3.2 沙箱脚本的返回值会经过 HTML 过滤

插件对沙箱脚本的返回值（字符串、列表的每一项、Map 的每个键和值）调用 `SafeHtmlExtendedMarkupFormatter` 过滤（`jenkinsci/active-choices-plugin@2.8.11` 的 `src/main/java/org/biouno/unochoice/model/GroovyScript.java` 的 `eval` 方法和同文件的 `resolveTypeAndSanitize` 方法）。过滤器只放行基本标签及 `input`、`textarea`、`select`、`option`（`jenkinsci/active-choices-plugin@2.8.11` 的 `src/main/java/org/biouno/unochoice/util/SafeHtmlExtendedMarkupFormatter.java` 中 `SafeHtmlExtendedMarkupFormatter` 类的静态字段 `POLICY`），并把部分字符改写成 HTML 实体。在脚本控制台中直接调用该过滤器，输入与输出：

```
web（a）  =>  web&#xff08;a&#xff09;
任务：说明  =>  任务&#xff1a;说明
a — b  =>  a — b
a: b, c (d)  =>  a: b, c (d)
中文、。！？  =>  中文、。&#xff01;&#xff1f;
「」  =>  「」
a=b+c  =>  a&#61;b&#43;c
@@x.y  =>  &#64;&#64;x.y
x"z<b>  =>  x&#34;z<b></b>
ＡＢ１２  =>  &#xff21;&#xff22;&#xff11;&#xff12;
```

两种参数受到的影响不同：

- **勾选框**（`activeChoice`）：显示文字随后又被页面模板转义一次，实体原样显示在页面上，例如 `web&#xff08;web-01, web-02&#xff09;`。所以勾选框文字和各任务 `meta/task.yml` 的 `description` 只用半角符号，`test_tasks.py` 的 `test_description_has_no_characters_the_active_choices_sanitizer_turns_into_entities` 检查 `description` 中没有 U+FF01–U+FF5E 和 `@ = + "`。
- **动态 HTML**（`activeChoiceHtml`）：输出作为 HTML 插入页面，浏览器会还原实体，表单中的全角标点、`@@` 标记都正常。但未放行的标签和属性会被去掉，分组只能用 `div`，必填只能用文字标出。过滤器的输入与输出：

```
IN : <label for="x">a</label><input id="x" name="value">
OUT: a<input id="x" name="value" />
IN : <input id="x" name="value" aria-required="true" required aria-label="msg" title="t">
OUT: <input id="x" name="value" />
IN : <input type="text" name="value" placeholder="必填" class="jenkins-input">
OUT: <input type="text" name="value" placeholder="必填" class="jenkins-input" />
```

`label`、`aria-*`、`title`、`input` 上的 `required` 都被去掉，所以表单在参数名后写 “ (required)”，并给每个文本框和数字框加 `placeholder`（参数名，必填时加 “ (required)”）：浏览器把 `placeholder` 当作没有其他名称时的可访问名称。下拉框的属性只放行 `id`、`class`、`style`、`disabled`、`multiple`、`name`、`required`、`size`，没有能给它命名的。

### 3.3 参数表单

`PRE_TASK_OPTIONS` 是 `activeChoiceHtml`，`referencedParameters: 'PRE_TASKS,PRE_MORE_TASKS'`，`omitValueField: true`。两个列表的勾选变化时，插件重新运行 `ActiveChoicesForm.optionsScript` 生成的脚本，脚本拿到两个列表的值（逗号分隔的任务名），为每个所选任务输出一组输入框；没有勾选任务时只显示 “No task selected”。勾选 `task_c` 后页面上的 HTML：

```html
<div style="border:1px solid #d1d5db;border-radius:6px;padding:8px 14px;margin:0 0 10px 0">
  <div style="margin-bottom:6px"><b>task_c</b> 带枚举参数和布尔参数的示例任务</div>
  <table><tbody>
    <tr><td style="padding:3px 12px 3px 0">Order</td><td>
      <input type="hidden" name="value" value="@@task_c.order">
      <input type="number" name="value" value="30" placeholder="order" class="jenkins-input" style="width:120px">
      smaller numbers run first in this phase</td></tr>
    <tr><td style="padding:3px 12px 3px 0">On failure</td><td>
      <input type="hidden" name="value" value="@@task_c.policy">
      <select name="value"><option value="abort" selected="selected">abort</option><option value="warn">warn</option></select>
      abort: stop the pipeline; warn: mark unstable and continue</td></tr>
    <tr><td style="padding:3px 12px 3px 0">task_c_mode</td><td>
      <input type="hidden" name="value" value="@@task_c.task_c_mode">
      <select name="value"><option value="plan" selected="selected">plan</option><option value="apply">apply</option></select>
      执行模式</td></tr>
    <tr><td style="padding:3px 12px 3px 0">task_c_verbose</td><td>
      <input type="hidden" name="value" value="@@task_c.task_c_verbose">
      <select name="value"><option value="true">true</option><option value="false" selected="selected">false</option></select>
      是否输出详细信息</td></tr>
    <tr><td style="padding:3px 12px 3px 0">task_c_label</td><td>
      <input type="hidden" name="value" value="@@task_c.task_c_label">
      <input type="text" name="value" value="" placeholder="task_c_label" class="jenkins-input" style="width:320px">
      附加标签，可留空</td></tr>
  </tbody></table>
</div>
```

（为便于阅读加了换行和缩进。）

控件由引擎按参数类型决定（`ActiveChoicesForm.input`，对 `ParameterType` 的 `switch`），脚本只按结果生成 HTML：`choices` → 下拉框；`bool` → `true` / `false` 下拉框（保证每个标记后面都恰好跟一个值）；`int` → `type="number"`；其余 → 文本框。标记中的字段键也由引擎生成（`FormField`）。预填值是任务目录的 `defaults/main.yml`。必填参数（如 `task_a_message`）的名称一格为 `task_a_message (required)`，输入框的可访问名称同样是 `task_a_message (required)`（`test_parameter_page.py` 的 `test_integer_parameter_uses_number_input_and_required_parameter_is_marked_in_text`）。

**勾选变化会重新生成整个表单**，已经填写的值丢失：脚本只能看到被引用参数（`PRE_TASKS`）的值，看不到表单自己的旧值。先勾选全部任务，再填写参数（`test_changing_the_selection_regenerates_the_form_and_drops_typed_values`）。

### 3.4 提交的值与解析

提交时，插件把参数中所有 `name="value"` 的输入框的值收集成数组，用 `join(",", true)` 连成一个字符串（`jenkinsci/active-choices-plugin@2.8.11` 的 `src/main/java/org/biouno/unochoice/AbstractUnoChoiceParameter.java` 的 `createValue` 方法）。`join` 把每个值转成 JSON 字符串（`"` 变成 `\"`，`\` 变成 `\\`），第二个参数 `true` 只是去掉每个值两侧的引号。例如把上面表单中的 `task_c_label` 填为 `x, "y"` 后提交，构建参数 `PRE_TASK_OPTIONS` 的值是：

```
@@task_c.order,30,@@task_c.policy,abort,@@task_c.task_c_mode,plan,@@task_c.task_c_verbose,false,@@task_c.task_c_label,x, \"y\"
```

值中的逗号与分隔符相同，所以引擎不按逗号切分，而按隐藏标记切分：`ActiveChoicesForm.parseOptions` 用正则 `(?:^|,)@@` 切开，每段第一个逗号前是 `任务名.字段`（`FormField.split` 在第一个点处切开），之后是值，再由 `ActiveChoicesForm.jsonUnescape` 还原 `\"`、`\\` 等转义。控制台中的执行计划与 Ansible 的输出：

```
    - task_c [abort] [task_c_mode:plan, task_c_verbose:false, task_c_label:x, "y"]
    "msg": "worker-01: task_c mode=plan label=x, \"y\""
```

`Selection.draft` 再用 `Values.fromForm` 按参数类型转换：`int` 能转成 64 位整数时转为数字，否则保留原字符串，由 `PlanParser.parse` 中的 `Values.check` 报 `must be an integer`；`bool` 的 `true` / `false` 转为布尔；非字符串参数的空值表示未填写，使用默认值。同一阶段的任务按表单中填写的执行顺序排列（见 3.5 节），与勾选先后无关。

这种格式本身有歧义：用户在某个值中输入 `,@@` 时，后半段会被当成另一个字段。引擎不去猜测，而是报错：同一字段出现两次、字段不属于所选任务、字段不是该任务声明的参数，都在运行任何任务之前失败（`test_pipeline_validation.py` 的 `test_invalid_form_values_fail_before_any_task_runs`）。

含逗号、引号、反斜杠、`@@`、`=`、`+`、中文的值原样到达 Ansible：在表单中把 `task_a_message` 填为 `a,"b" c\d, @@x 中文=1+2` 时，控制台中 Ansible 的输出为 `"msg": "api-01: a,\"b\" c\\d, @@x 中文=1+2 (2/2)"`（JSON 转义后的显示形式，`test_submitted_form_values_reach_ansible_unchanged`）。

### 3.5 常用任务与“显示更多”、搜索框、执行顺序

任务多时，每个阶段的任务分两个列表。插件只能整体绘制一个勾选框列表，不能折叠其中一部分，所以“收起”的做法是让第二个列表为空：

- `PRE_TASKS`：`menu: main` 的任务，一直显示。
- `PRE_SHOW_MORE`：Jenkins 自带的布尔参数，只在该阶段有 `menu: more` 的任务时登记。
- `PRE_MORE_TASKS`：`reactiveChoice` 勾选框列表，`referencedParameters: 'PRE_SHOW_MORE'`。脚本（`ActiveChoicesForm.moreTasksScript`）在 `PRE_SHOW_MORE` 为空时返回空 Map，否则返回 `menu: more` 的任务。插件把勾选的复选框的值传给脚本，没勾时传空字符串（`jenkinsci/active-choices-plugin@2.8.11` 的 `src/main/js/Util.ts` 中 `Util` 类的 `getElementValue` 方法）。取消勾选会重新生成这个列表，已勾选的不常用任务随之清空（`test_showing_more_lists_the_less_used_tasks_and_hiding_them_drops_their_selection`）。
- `PRE_TASK_OPTIONS` 同时引用两个列表（`referencedParameters: 'PRE_TASKS,PRE_MORE_TASKS'`），为两边所选的任务生成表单；引擎把两个列表的值合在一起（`ParameterPage.draft`）。

两个勾选框列表都设 `filterable: true`：列表下方出现搜索框，按键时只显示文字中含输入内容的任务（`test_filter_box_narrows_a_task_list`）。插件在 `keyup` 事件上过滤，自动化测试要逐字输入，不能直接给输入框赋值。

参数表单中每个任务的第一行是执行顺序：数字框，标记为 `@@<任务>.order`，预填该任务的 `order`。`Selection.draft` 按这些数字给一个阶段的任务排序，留空时用 `order`，数字相同时按 `order`；不是整数时报 `form field <任务>.order must be an integer`（`test_order_typed_on_the_form_decides_the_order_within_a_phase`）。任务之间的先后依赖不做校验，执行计划在运行任何任务之前打印在控制台上。

### 3.6 不经过页面触发

插件说明 Active Choices 参数只供人在页面上填写，通过插件、API 或脚本触发时不保证可用（`jenkinsci/active-choices-plugin@2.8.11` 的 `README.md` 的 “Known Limitations” 一节）。通过 `buildWithParameters` 直接传入的值不经过页面上的 JSON 转义，原样保存，引擎的 `ActiveChoicesForm.jsonUnescape` 却仍会处理其中的反斜杠，例如 `C:\new` 会变成 `C:`、换行、`ew`。所以 API 和上游 job 只传 `DEPLOY_PLAN`。集成测试中直接赋值 `TARGET_GROUPS`、`PRE_TASKS`、`PRE_MORE_TASKS`、`PRE_TASK_OPTIONS` 的用例（`test_pipeline_runs.py` 的 `test_selection_runs_tasks_in_catalog_order_on_the_selected_groups_only`、`test_pipeline_validation.py` 的 `test_invalid_form_values_fail_before_any_task_runs`）只用不含反斜杠的值，用来模拟页面提交。

不传 `PRE_MORE_TASKS` 时，Jenkins 运行它的脚本求默认值，此时被引用的 `PRE_SHOW_MORE` 没有绑定：脚本访问它会抛出 `MissingPropertyException`，插件改用备用脚本，备用脚本返回的文字就成了参数值，计划校验报 `unknown task '(task list failed)'`。所以 `moreTasksScript` 捕获这个异常，按未勾选处理（`test_tasks_from_both_lists_run_in_catalog_order_without_typed_orders` 等经 API 触发的用例）。

## 4. 无插件版：勾选框 + input 步骤

### 4.1 参数页

`form: 'basic'` 时，`ParameterPage.definitions` 为 inventory 中 `deploy_targets` 下的每个主机组生成 `booleanParam(name: 'TARGET_<组>')`，为每个阶段允许的每个任务生成 `booleanParam(name: '<PRE|POST>_<任务名>')`，另有 `SKIP_DEPLOY`（只在读写 job 中）、`EDIT_TASK_OPTIONS`（默认勾选）、`DEPLOY_PLAN`。Jenkins 自带的参数页是静态的，不能随勾选显示或隐藏其他输入框，所以任务参数不放在这一页。

### 4.2 构建开始后的表单

`ParameterPage.draft`（无插件版由其中的 `basicDraft` 处理）在 `EDIT_TASK_OPTIONS` 为真且勾选了任务时，用 `ParameterPage.inputFields` 为每个所选任务生成输入项，交给 `input` 步骤（确认按钮为 “Start”，外面包一层 30 分钟的 `timeout`）。`deployPipeline` 在 stage `Plan` 中调用它，该 stage 不在 `node` 中，等待期间不占执行器和工作区（`test_basic_form.py` 的 `test_waiting_for_the_form_holds_no_executor`）：

| 输入项 | 类型 |
| --- | --- |
| `<阶段>.<任务>.policy` | `choice`，任务的默认策略排在第一个（即默认值） |
| `<阶段>.<任务>.<参数>`，参数有 `choices` | `choice`，默认值排在第一个；没有默认值时保持 `choices` 原顺序 |
| `<阶段>.<任务>.<参数>`，`bool` | `booleanParam` |
| `<阶段>.<任务>.<参数>`，`str` / `int` | `string`，预填默认值 |

勾选 `TARGET_web`、`PRE_task_a`、`POST_check_a` 时，表单只含这两个任务的 6 项：`pre.task_a.policy`、`pre.task_a.task_a_message`、`pre.task_a.task_a_repeat`、`pre.task_a.task_a_delay_seconds`、`post.check_a.policy`、`post.check_a.check_a_min_connections`（`test_form_shows_only_selected_tasks_and_its_values_are_used`）。

`input` 有多个输入项时返回 `[名称: 值]`，**只有一个输入项时直接返回该值**（`jenkinsci/pipeline-input-step-plugin@560.v56198a_642157` 的 `src/main/java/org/jenkinsci/plugins/workflow/support/steps/input/InputStepExecution.java` 的 `proceed` 方法）。没有参数的任务（`check_c`）只有失败策略一项，所以 `basicDraft` 在这种情况下自己组成 Map（`test_form_with_a_single_field_returns_its_value`）。

提交后，每个值转成字符串，按 `<阶段>` 分组成与 Active Choices 版相同的 `[任务.字段: 值]`，交给同一个 `Selection.draft`。点 “Abort” 时构建为 ABORTED，不运行任何任务（`test_abort_in_the_form_aborts_the_build`）。

### 4.3 不弹出表单

- `EDIT_TASK_OPTIONS=false`：所选任务全部使用默认值（`test_without_the_form_every_task_uses_its_defaults`）；有必填参数的任务（如 `task_a`）因此在校验时失败（`test_without_the_form_a_missing_required_parameter_fails`）。
- `DEPLOY_PLAN` 非空：忽略所有勾选框，不弹出表单（`test_deploy_plan_skips_the_form`）。从 API 或上游 job 触发时用这种方式，否则构建会停在 `input` 等人处理。
