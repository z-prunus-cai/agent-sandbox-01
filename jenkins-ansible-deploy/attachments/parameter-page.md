# 参数页的两种实现

文中 `.reference-cache/<owner>/<name>@<version>/<path>:<行号>` 指上游仓库在该版本的同一路径与行号，对应关系见 [BRIEF.md 第 8 节](../BRIEF.md#8-出处)。

本文说明 `examples/Jenkinsfile`（Active Choices 版，Active Choices 插件 2.8.11）和 `examples/Jenkinsfile.no-plugin`（无插件版，只用 Jenkins 自带功能）的参数页：页面上有什么、怎样生成、提交后的值是什么格式、引擎怎样把它转成执行计划。两种方案共用 `examples/jenkins/deploy-engine.groovy`，最后都得到与 `DEPLOY_PLAN` 结构相同的计划草稿，再由 `buildPlan` 统一校验。

## 1. 对比

| 能力 | Active Choices 版 | 无插件版 |
| --- | --- | --- |
| 选择主机组 | 勾选框（`TARGET_GROUPS`） | 每组一个勾选框（`TARGET_web` 等） |
| 选择任务 | 勾选框（`PRE_TASKS` / `POST_TASKS`） | 每个任务一个勾选框（`PRE_task_a` 等） |
| 只显示所选任务的参数 | 同一页上，勾选后立即出现 | 构建开始后由 `input` 步骤弹出 |
| 输入控件 | 整数为数字框，布尔、枚举、失败策略为下拉框，必填参数名后标“（必填）”，每个任务一个带边框的分组 | 字符串和整数为文本框，布尔为勾选框，枚举、失败策略为下拉框；平铺一列 |
| 填写的值记在哪里 | 构建参数（构建页的 Parameters 中可见） | `input` 的提交记录；执行计划打印在控制台 |
| 等待填写时 | 不涉及 | 构建暂停在不分配执行器的 `Task options` 阶段，最多 30 分钟；之后运行的是 `Prepare` 中 `stash` 的文件 |
| API / 上游 job 触发 | `DEPLOY_PLAN` | `DEPLOY_PLAN` |
| 需要的插件 | `uno-choice` | 无 |

## 2. 参数定义只能用 `properties()` 生成

主机组列表来自 `inventory/hosts.yml`，任务列表与参数来自 `jenkins/tasks.yml` 和各 role 的文件，都要在构建运行时读取工作区才知道。所以构建参数由 `Prepare` 阶段的 `prepareRequest()` 调用 `properties([disableConcurrentBuilds(), parameters(...)])` 写入 job，而不是写在 `pipeline { parameters { } }` 中。后果：

- 参数定义在构建运行时更新，**从下一次构建起**才出现在参数页上；新建的 job 要先运行一次（结果 NOT_BUILT）。
- 判断“是否第一次运行”必须用 `properties()` 之前读取的 `params`，所以 Jenkinsfile 把 `params` 作为实参传给 `prepareRequest()`（见 [BRIEF.md 第 4.7 节](../BRIEF.md#47-第一次运行只登记参数params-必须在-properties-之前读取)）。

## 3. Active Choices 版

### 3.1 参数页上的脚本看不到工作区

Active Choices 的 Groovy 脚本在用户打开参数页、改变勾选时，在 Jenkins controller 上运行，没有工作区。所以 `activeChoicesParameters()` 在构建运行时把主机组和任务定义用 `groovyLiteral()` 转成 Groovy 字面量，直接写进脚本文本。例如主机组列表的脚本：

```groovy
def groups = ['web': ['web-01', 'web-02'], 'api': ['api-01'], 'worker': ['worker-01', 'worker-02']]
return groups.collectEntries { name, hosts -> [(name): name + ' (' + hosts.join(', ') + ')'] }
```

脚本返回 `[值: 显示文字]`：勾选框的值是 `web`，旁边显示 `web (web-01, web-02)`。脚本声明 `sandbox: true`，在 Groovy 沙箱中运行，不需要管理员审批。

### 3.2 沙箱脚本的返回值会经过 HTML 过滤

插件对沙箱脚本的返回值（字符串、列表的每一项、Map 的每个键和值）调用 `SafeHtmlExtendedMarkupFormatter` 过滤（`.reference-cache/jenkinsci/active-choices-plugin@2.8.11/src/main/java/org/biouno/unochoice/model/GroovyScript.java:191-192`、`:232-250`）。过滤器只放行基本标签及 `input`、`textarea`、`select`、`option`（`.reference-cache/jenkinsci/active-choices-plugin@2.8.11/src/main/java/org/biouno/unochoice/util/SafeHtmlExtendedMarkupFormatter.java:54-59`），并把部分字符改写成 HTML 实体。在 Jenkins 2.580.1 的脚本控制台中直接调用该过滤器的结果：

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

- **勾选框**（`activeChoice`）：显示文字随后又被页面模板转义一次，实体原样显示在页面上，例如 `web&#xff08;web-01, web-02&#xff09;`。所以勾选框文字和 `tasks.yml` 的 `description` 只用半角符号，`test_catalog.py` 检查 `description` 中没有 U+FF01–U+FF5E 和 `@ = + "`。
- **动态 HTML**（`activeChoiceHtml`）：输出作为 HTML 插入页面，浏览器会还原实体，表单中的全角标点、`@@` 标记都正常。但未放行的标签和属性会被去掉，分组只能用 `div`，必填只能用文字标出。同一控制台中的结果：

```
IN : <label for="x">a</label><input id="x" name="value">
OUT: a<input id="x" name="value" />
IN : <input id="x" name="value" aria-required="true" required aria-label="msg" title="t">
OUT: <input id="x" name="value" />
IN : <input type="text" name="value" placeholder="必填" class="jenkins-input">
OUT: <input type="text" name="value" placeholder="必填" class="jenkins-input" />
```

`label`、`aria-*`、`title`、`input` 上的 `required` 都被去掉，所以表单在参数名后写“（必填）”，并给每个文本框和数字框加 `placeholder`（参数名，必填时加“（必填）”）：浏览器把 `placeholder` 当作没有其他名称时的可访问名称。下拉框的属性只放行 `id`、`class`、`style`、`disabled`、`multiple`、`name`、`required`、`size`，没有能给它命名的；需要读屏软件时用无插件版。

### 3.3 参数表单

`PRE_TASK_OPTIONS` 是 `activeChoiceHtml`，`referencedParameters: 'PRE_TASKS'`，`omitValueField: true`。`PRE_TASKS` 的勾选变化时，插件重新运行 `optionsFormScript()` 生成的脚本，脚本拿到 `PRE_TASKS` 的值（逗号分隔的任务名），为每个所选任务输出一组输入框。勾选 `task_c` 后页面上实际的 HTML：

```html
<div style="border:1px solid #d1d5db;border-radius:6px;padding:8px 14px;margin:0 0 10px 0">
  <div style="margin-bottom:6px"><b>task_c</b> 带枚举参数和布尔参数的示例任务</div>
  <table><tbody>
    <tr><td style="padding:3px 12px 3px 0">失败时</td><td>
      <input type="hidden" name="value" value="@@task_c.policy">
      <select name="value"><option value="abort" selected="selected">abort</option><option value="warn">warn</option></select>
      abort：停止流水线；warn：标记不稳定后继续</td></tr>
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

（为便于阅读加了换行和缩进，内容与页面一致。）

控件按参数类型生成：`choices` → 下拉框；`bool` → `true` / `false` 下拉框（保证每个标记后面都恰好跟一个值）；`int` → `type="number"`；其余 → 文本框。预填值是 role 的 `defaults/main.yml`。必填参数（如 `task_a_message`）的名称一格为 `task_a_message（必填）`，输入框的可访问名称同样是 `task_a_message（必填）`（`test_integer_parameter_uses_number_input_and_required_parameter_is_marked_in_text`）。

**勾选变化会重新生成整个表单**，已经填写的值丢失：脚本只能看到被引用参数（`PRE_TASKS`）的值，看不到表单自己的旧值。先勾选全部任务，再填写参数（`test_changing_the_selection_regenerates_the_form_and_drops_typed_values`）。

### 3.4 提交的值与解析

提交时，插件把参数中所有 `name="value"` 的输入框的值收集成数组，用 `join(",", true)` 连成一个字符串（`.reference-cache/jenkinsci/active-choices-plugin@2.8.11/src/main/java/org/biouno/unochoice/AbstractUnoChoiceParameter.java:173-176`）。`join` 把每个值转成 JSON 字符串（`"` 变成 `\"`，`\` 变成 `\\`），第二个参数 `true` 只是去掉每个值两侧的引号。上面的表单中把 `task_c_label` 填为 `x, "y"` 后提交，构建参数 `PRE_TASK_OPTIONS` 的实际值：

```
@@task_c.policy,abort,@@task_c.task_c_mode,plan,@@task_c.task_c_verbose,false,@@task_c.task_c_label,x, \"y\"
```

值中的逗号与分隔符相同，所以引擎不按逗号切分，而按隐藏标记切分：`parseOptions()` 用正则 `(?:^|,)@@` 切开，每段第一个逗号前是 `任务名.字段`，之后是值，再由 `jsonUnescape()` 还原 `\"`、`\\` 等转义。这次构建的控制台中，执行计划与 Ansible 的输出：

```
    - task_c [abort] [task_c_mode:plan, task_c_verbose:false, task_c_label:x, "y"]
    "msg": "worker-01: task_c mode=plan label=x, \"y\""
```

`selectionPlan()` 再按参数类型转换：`int` 能转成 64 位整数时转为数字，否则保留原字符串，由 `buildPlan` 报“应为整数”；`bool` 的 `true` / `false` 转为布尔；非字符串参数的空值表示未填写，使用默认值。同一阶段的任务按 `tasks.yml` 中的顺序排列，与勾选先后无关。

这种格式本身有歧义：用户在某个值中输入 `,@@` 时，后半段会被当成另一个字段。引擎不去猜测，而是报错：同一字段出现两次、字段不属于所选任务、字段不是该任务声明的参数，都在运行 playbook 之前失败（`test_invalid_form_values_fail_before_any_playbook_runs`）。

含逗号、引号、反斜杠、`@@`、`=`、`+`、中文的值原样到达 Ansible：在表单中把 `task_a_message` 填为 `a,"b" c\d, @@x 中文=1+2`，控制台中 Ansible 的输出为 `"msg": "api-01: a,\"b\" c\\d, @@x 中文=1+2 (2/2)"`（JSON 转义后的显示形式，`test_submitted_form_values_reach_ansible_unchanged`）。

### 3.5 不经过页面触发

插件说明 Active Choices 参数只供人在页面上填写，通过插件、API 或脚本触发时不保证可用（`.reference-cache/jenkinsci/active-choices-plugin@2.8.11/README.md:570-573`）。通过 `buildWithParameters` 直接传入的值不经过页面上的 JSON 转义，原样保存，引擎的 `jsonUnescape()` 却仍会处理其中的反斜杠，例如 `C:\new` 会变成 `C:`、换行、`ew`。所以 API 和上游 job 只传 `DEPLOY_PLAN`。集成测试中直接赋值 `TARGET_GROUPS`、`PRE_TASKS`、`PRE_TASK_OPTIONS` 的用例（`test_selection_runs_tasks_in_catalog_order_on_the_selected_groups_only`、`test_invalid_form_values_fail_before_any_playbook_runs`）只用不含反斜杠的值，用来模拟页面提交。

## 4. 无插件版：勾选框 + input 步骤

### 4.1 参数页

`basicParameters()` 为 inventory 中的每个主机组生成 `booleanParam(name: 'TARGET_<组>')`，为每个阶段允许的每个任务生成 `booleanParam(name: '<PRE|POST>_<任务名>')`，另有 `SKIP_DEPLOY`、`EDIT_TASK_OPTIONS`（默认勾选）、`DEPLOY_PLAN`。Jenkins 自带的参数页是静态的，不能随勾选显示或隐藏其他输入框，所以任务参数不放在这一页。

### 4.2 构建开始后的表单

`basicSelection()` 在 `EDIT_TASK_OPTIONS` 为真且勾选了任务时，用 `taskInputs()` 为每个所选任务生成输入项，交给 `input` 步骤。它由 `resolvePlan()` 在 stage `Task options` 中调用，该 stage 不分配 agent，等待期间不占执行器和工作区（`test_no_plugin_waiting_for_input_holds_no_executor`）：

| 输入项 | 类型 |
| --- | --- |
| `<阶段>.<任务>.policy` | `choice`，任务的默认策略排在第一个（即默认值） |
| `<阶段>.<任务>.<参数>`，参数有 `choices` | `choice`，默认值排在第一个；没有默认值时保持 `choices` 原顺序 |
| `<阶段>.<任务>.<参数>`，`bool` | `booleanParam` |
| `<阶段>.<任务>.<参数>`，`str` / `int` | `string`，预填默认值 |

勾选 `TARGET_web`、`PRE_task_a`、`POST_check_a` 时，表单只含这两个任务的 6 项：`pre.task_a.policy`、`pre.task_a.task_a_message`、`pre.task_a.task_a_repeat`、`pre.task_a.task_a_delay_seconds`、`post.check_a.policy`、`post.check_a.check_a_min_connections`（`test_no_plugin_form_shows_only_selected_tasks_and_its_values_are_used`）。

`input` 有多个输入项时返回 `[名称: 值]`，**只有一个输入项时直接返回该值**（`.reference-cache/jenkinsci/pipeline-input-step-plugin@560.v56198a_642157/src/main/java/org/jenkinsci/plugins/workflow/support/steps/input/InputStepExecution.java:256-260`）。没有参数的任务（`check_c`）只有失败策略一项，所以 `basicSelection` 在这种情况下自己组成 Map（`test_no_plugin_form_with_a_single_field_returns_its_value`）。

提交后，每个值转成字符串，按 `<阶段>` 分组成与Active Choices 版相同的 `[任务.字段: 值]`，交给同一个 `selectionPlan()`。点 “Abort” 时构建为 ABORTED，不运行任何 playbook（`test_no_plugin_abort_in_form_aborts_the_build`）。

### 4.3 不弹出表单

- `EDIT_TASK_OPTIONS=false`：所选任务全部使用默认值；有必填参数的任务（如 `task_a`）因此在校验时失败（`test_no_plugin_without_form_rejects_a_missing_required_parameter`）。
- `DEPLOY_PLAN` 非空：忽略所有勾选框，不弹出表单（`test_no_plugin_deploy_plan_skips_the_form`）。从 API 或上游 job 触发时用这种方式，否则构建会停在 `input` 等人处理。
