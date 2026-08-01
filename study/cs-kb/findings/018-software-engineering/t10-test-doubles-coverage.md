# L4-06·大主题06-10 测试替身与覆盖率解读

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（本组实证工具链另含 git 2.43.0；本报告的可选实证在 Python 3.11 上真跑，另装 pytest 9.1.1 / coverage.py 7.15.2，二者晚于 2026-07-25 基线快照、为 2026-07-30 当日可装到的版本）｜ 核实日期：2026-07-30 ｜ 先修：本课大主题06-9（测试金字塔与分层：unit/integration/e2e 的速度-稳定性-成本结构，替身主要服务于快而稳的 unit 层）、大主题06-2（规约与契约：一个替身之所以能替真对象，是因为它遵守被替换对象对客户端承诺的那份规约）｜ 一手锚点：G. Meszaros《xUnit Test Patterns: Refactoring Test Code》(2007) 的 Test Double 章（⚠准一手·术语原始出处）；Martin Fowler「Mocks Aren't Stubs」(初版 2004，重大修订 2007-01-02，⚠准一手·普及与澄清)；MIT 6.031 sp22 Testing reading（https://web.mit.edu/6.031/www/sp22/，准一手课程·覆盖率与用例选择侧）；coverage.py 官方文档（Branch coverage 语义，锚 7.15.2）｜ 成熟度：概念稳定（替身谱系术语自 2007 定型多年未变），仅框架实现（unittest.mock / Mockito 等）细节随版本演进

> 一条主线心智模型：单元测试想「只测这一小块代码」，但这块代码往往要依赖别的东西——数据库、网络、时钟、发邮件的服务。这些依赖慢、不稳定、有副作用，会把「测你的逻辑」变成「测整套系统」。**测试替身（test double）** 就是给这些依赖找的**替身演员**：长得像真依赖（接口一样）、但行为是你在测试里说了算的假货，让被测代码在一个受控、可复现的小舞台上跑。替身按「你对它要求多严」分成五类（dummy/stub/spy/mock/fake）。用了替身后，验证有两种姿势：看**结果状态**对不对（状态验证），或看被测代码**怎么调用**了替身（行为验证）。最后，**覆盖率**是量「测试跑到了多少代码」的尺子——它能指出哪里**没测到**，但它量的是「跑过的已有代码」，永远量不出「本该写却没写的逻辑」和「跑了却没断言的空测试」，所以「高覆盖率 ≠ 高质量」。

> ⚠术语承重声明：本报告的五类替身术语是**行业约定**，不是语言规范或标准。原始出处是 Meszaros《xUnit Test Patterns》，经 Fowler「Mocks Aren't Stubs」普及为业界通用词汇（`⚙演进快·锚 2007 定义`）。业界日常把这些词**大量混用**（最常见的是把一切替身都叫「mock」），本报告一律以 Meszaros/Fowler 原定义为准，并在 6.10.1.7 集中点明常见误用与来源间的口径差异。

---

## 6.10.1 替身谱系——dummy/stub/spy/mock/fake 五类区分

### 6.10.1.1 测试替身（Test Double）是所有假依赖的总称

「测试替身」（Test Double）是 Meszaros 造的**总括词**，指在测试里顶替某个真实协作者（collaborator）位置的任何替代对象——名字取自电影里的「特技替身演员」（stunt double）：镜头里看着是主角，实际是替身在完成危险动作。Fowler 在「Mocks Aren't Stubs」里明确采用了 Meszaros 这个词作为所有假对象的上位概念，下面 dummy/stub/spy/mock/fake 是它的五个子类。

对初学者，先建立两个前提。第一，替身能顶替真对象，靠的是它们**实现同一个接口 / 遵守同一份规约**（回顾大主题06-2）：被测代码只按接口调用，分不清背后是真货还是替身。第二，用替身的**动机**是隔离——把被测的这块逻辑（system under test，简称 SUT）与它的依赖切开，好处是测试更快（不连真数据库）、更稳（不受网络抖动影响）、更可控（能构造真实环境里难触发的错误分支，比如「磁盘满了」）、无副作用（不会真发出邮件）。

五类的区别，本质是「你对这个替身**要求到什么程度**」逐级加码：从「根本不用它、只占个位」到「有真实实现的简化版」。下面逐个讲。

### 6.10.1.2 Dummy——只占位、从不真正被用到

Dummy（哑对象）是传进去填满参数列表、但在这次测试里**根本不会被真正使用**的对象。它存在的唯一理由是「这个方法签名要求这里有个参数，不给编译/调用不过去」，而这次测试的路径又碰不到它。

Fowler 原文的定义是：「Dummy objects are passed around but never actually used. Usually they are just used to fill parameter lists.」（哑对象被传来传去但从不真正使用，通常只用来填参数列表。）

一个 `Order` 构造函数要求传一个 `Customer`，但你这次只想测「订单加一件商品后总数是否为 1」，压根用不到 customer 的任何行为。这时传 `Customer(null, null)` 甚至一个 `null`（如果语言允许）就是 dummy——它只是让对象能被造出来。初学者容易把 dummy 和 stub 搞混：区别在于 dummy **不会被调用**，一旦某个替身的方法真的被 SUT 调用并要返回点什么，它就升级成了 stub。

### 6.10.1.3 Stub——提供预设的「罐头答案」

Stub（桩）为测试期间发生的调用提供**预先设定好的固定返回值（canned answers）**，且通常对预设范围之外的任何调用不作反应。它把 SUT 需要的「输入」喂进去，好让 SUT 沿着你想测的那条路走。

Fowler 原文：「Stubs provide canned answers to calls made during the test, usually not responding at all to anything outside what's programmed in for the test.」

SUT 是「根据汇率把美元转成欧元」，它依赖一个 `RateService.getRate()`。真服务要联网、汇率还每秒变，没法写确定性断言。于是给一个 stub，让 `getRate()` **永远返回 0.9**，这样 `convert(100)` 就应恒等于 90，可写死断言。stub 的关键特征是：它只管**给答案**，你不会去核对「stub 被调用了几次、参数是什么」——那是 spy/mock 的事。stub 天然配套**状态验证**（见 6.10.2）：喂好输入，只看 SUT 吐出的结果对不对。

### 6.10.1.4 Spy——会「偷偷记账」的 stub

Spy（间谍）是一种 stub，但它**额外记录**自己「是怎样被调用的」——被调了几次、每次传了什么参数——供测试**事后**去查。它像 stub 一样能给答案，又像监控探头一样留了调用记录。

Fowler 原文：「Spies are stubs that also record some information based on how they were called. One form of this might be an email service that records how many messages it was sent.」（一个能记下「自己被要求发过几封邮件」的邮件服务就是 spy 的一种形态。）

对初学者，spy 与 mock 的差别在**验证的时机与风格**：spy 是「先放它跑、跑完我再去翻它的记账本对账」（事后、命令式断言，如 `assert spy.sendCount == 2`）；mock 则是「事前就把期望写死在它身上、调用不符它当场就让测试失败」（见下条）。⚠口径分歧：这是 Meszaros 的定义（Test Spy = 会记录的 stub）；而有的框架（如 Java 的 Mockito）把 `spy` 一词用作「**包裹一个真实对象、只部分打桩**」的工具，语义不同——见 6.10.1.7。

### 6.10.1.5 Mock——预置「期望」、按交互对错来判定的替身

Mock（模拟对象）是**预先编程好一组「期望」（expectations）** 的对象，这组期望本身就构成「它预期会收到哪些调用」的一份规约。测试结束时（或调用发生时）由 mock 自己去核对「实际收到的调用是否与期望一致」，不一致就让测试失败。

Fowler 原文：「Mocks are objects pre-programmed with expectations which form a specification of the calls they are expected to receive.」

mock 和前面几类的分水岭是：dummy/stub/spy/fake 都用**状态验证**（看结果），只有 mock 天生服务**行为验证**（看交互，见 6.10.2）。最小例子：SUT 是「用户注销时应恰好调用一次 `session.invalidate()`」——这里没有「返回值」可查，正确性完全体现在「有没有、以正确参数、恰当次数地调用了那个方法」。你在 mock 上写死期望「invalidate 应被调用 1 次」，SUT 跑完 mock 自动核对。初学者最该记住的一句：**mock 断言的是「怎么调用的」，不是「返回了什么」。**

### 6.10.1.6 Fake——有真实实现、但走了捷径的简化版

Fake（伪对象）**有能工作的真实实现**，只是为了不适合上生产而走了捷径。最经典的例子是**内存数据库**：它真能存、真能查、真有事务语义，行为像真数据库，但数据只在内存里、进程一停就没，因此快得多、也不需要装真 DB——很适合测试，但你绝不会拿它上线。

Fowler 原文：「Fake objects actually have working implementations, but usually take some shortcut which makes them not suitable for production (an in memory database is a good example).」

fake 与 stub 的区别，初学者要抓住「有没有真逻辑」：stub 是「你问什么我背什么，没有内在逻辑」（`getRate()` 永远吐 0.9，你存了再取它也不会变）；fake 是「我真的照规则算/存」（往内存 DB 里 `insert` 一条，再 `select` 真能查回来，还能测出「重复主键报错」这类真实行为）。fake 因此能覆盖 stub 覆盖不了的多步交互场景，但自己也是一段可能有 bug 的代码，需要有自己的测试。

### 6.10.1.7 术语在业界被大量混用——以 Meszaros/Fowler 原定义为准

这五个词在日常口语和不同工具里被混用得很厉害，这正是 Fowler 写「Mocks Aren't Stubs」的动机。初学者必须知道以下几处常见误用与来源口径差异，才不会被 issue、文档、同事的话绕晕。

最普遍的误用是**把一切替身都叫「mock」**：「mock 掉数据库」十有八九指的是放一个 stub 或 fake，而不是严格意义上「预置期望、验证交互」的 mock。Fowler 的核心论点就是——mock 与 stub 不是一回事，前者做行为验证、后者做状态验证，混着叫会掩盖「你到底在断言结果还是在断言交互」这个重要区别。

第二处是**框架把词借去表别的意思**。Python 标准库的 `unittest.mock.Mock` 是个「万能替身」：同一个 `Mock()` 对象，你用 `.return_value` 给它设返回值时它在当 stub，你用 `.assert_called_with(...)` 查它被怎么调用时它在当 spy/mock——所以 `Mock` 这个类名并不等于 Meszaros 语义的「mock」。Java 的 Mockito 里 `mock()` 造出来的对象默认更偏 stub+spy 风格（不预置严格期望，靠事后 `verify()`），而 `spy()` 指的是**包裹真实对象、只部分打桩**，与 Meszaros「会记账的 stub」不是同一个概念。

第三处是**来源之间的口径**：Meszaros《xUnit Test Patterns》给出五类的原始、较正式的定义（并把 Test Double 作为总称）；Fowler「Mocks Aren't Stubs」采用并普及了这套词汇，两者在五类的划分上**基本一致、不冲突**——真正的分歧不在 Meszaros vs Fowler 之间，而在「学术/书面定义」与「工具/口语用法」之间。实务建议：读别人代码时别只看它自称 mock 还是 stub，要看它**到底在验证结果还是验证交互**，那才是本质。

#### 来源与时效
- G. Meszaros《xUnit Test Patterns: Refactoring Test Code》(Addison-Wesley, 2007)：Test Double 总称与 dummy/stub/spy/mock/fake 五类原始定义（⚠准一手·术语出处，`⚙锚 2007 定义`）。
- Martin Fowler「Mocks Aren't Stubs」(martinfowler.com，初版 2004、重大修订 2007-01-02)：五类的通用化定义（本报告 6.10.1.2~6.10.1.6 的英文原句均引自此文的 "The Difference Between Mocks and Stubs" 一节）+ mock/stub 分水岭（⚠准一手·普及）。
- 交叉印证与误用点：Python 官方文档 `unittest.mock`（`Mock` 兼任 stub/spy/mock 的万能替身，锚 Python 3.11）；Mockito 用 `spy` 表「部分打桩真对象」的口径差异（⚠二手·指路，未逐字取证 Mockito 文档，标「待核具体版本措辞」）。
- 冲突项：`spy` 一词 Meszaros 定义（会记账的 stub）vs Mockito 用法（包裹真对象部分打桩），两边都记；「一切替身皆称 mock」为业界口语误用，以 Fowler 定义纠正。

## 6.10.2 状态验证 vs 行为验证——断言结果 vs 断言交互

### 6.10.2.1 状态验证——喂输入、只看结果状态对不对

状态验证（state verification）是指：让 SUT 跑一遍，然后检查**结束后的状态**——SUT 的返回值、SUT 自身的属性、或它写进某个（可查的）协作者里的数据——是否符合预期。它不关心 SUT 内部**怎么一步步调用**了协作者，只关心「最后结果对不对」。这类测试天然配 stub / fake：用它们把外部输入喂稳，然后断言结果。

Fowler 在「Mocks Aren't Stubs」里把它定义为：exercise（执行 SUT）之后 verify 用的是 SUT（及协作者）的**状态**。初学者可以记成「黑箱视角」：我给你 100 美元、汇率 stub 成 0.9，我只检查你吐出来的是不是 90，至于你内部调了 `getRate()` 一次还是三次、先乘还是先查，我一概不管。

下面这段本机真跑的最小例子里，`test_returns_active_count` 就是状态验证——它只断言 `greet_all` 的**返回值**（活跃用户计数 = 2），不理会 mailer 被怎么调用：

```python
def test_returns_active_count(self):
    stub_mailer = Mock()          # 这里当作被动 stub 用
    g = Greeter(stub_mailer)
    users = [{"email": "a@x", "active": True},
             {"email": "b@x", "active": False},
             {"email": "c@x", "active": True}]
    self.assertEqual(g.greet_all(users), 2)   # 断言的是「结果状态」
```

### 6.10.2.2 行为验证——断言 SUT 与协作者之间「怎么交互」

行为验证（behavior verification）是指：检查 SUT 是否对协作者**发起了正确的调用**——调了哪个方法、参数是什么、调了几次、顺序对不对。它关心的是「交互过程」，而不是最终结果状态。这类测试配 mock（或 spy）：mock 记录/核对这些调用。

为什么需要它？因为有一大类正确性**没有返回值可查**，正确与否完全体现在「有没有对外做对了动作」：注销时应调一次且仅一次 `session.invalidate()`；下单成功应给库存服务发一次扣减；缓存未命中才应去查数据库、命中就不该查。这些场景里「对不对」= 「有没有以正确方式调用了协作者」，只能用行为验证。

下面本机真跑的 `test_sends_to_each_active_user` 就是行为验证——它断言 mailer 的 `send` 被调用了 2 次、且对两个活跃用户各调了一次（参数正确），完全不看返回值：

```python
def test_sends_to_each_active_user(self):
    mock_mailer = Mock()
    g = Greeter(mock_mailer)
    users = [{"email": "a@x", "active": True},
             {"email": "b@x", "active": False},
             {"email": "c@x", "active": True}]
    g.greet_all(users)
    self.assertEqual(mock_mailer.send.call_count, 2)        # 断言「交互」
    mock_mailer.send.assert_any_call("a@x", "Welcome!")     # 断言「交互」
    mock_mailer.send.assert_any_call("c@x", "Welcome!")     # 断言「交互」
```

两个测试都真跑通过（本机 Python 3.11、`unittest`）：

```
$ python3 demo_doubles.py
test_sends_to_each_active_user ... ok
test_returns_active_count ... ok
----------------------------------------------------------------------
Ran 2 tests in 0.001s
OK
```

同一个 `Greeter`，一个从「结果」验、一个从「交互」验——这直观展示了两种验证是**互补的视角**，而不是二选一的对错。

### 6.10.2.3 取舍——行为验证更易「过度指定」而变脆

两种验证各有代价，初学者最该建立的判断是：**行为验证更容易把测试和实现细节绑死，从而变脆（fragile）**。

行为验证断言「怎么调用的」，等于把 SUT 的**内部实现方式**写进了测试。一旦你重构 SUT——比如把「循环里逐个发」改成「批量发一次」，功能完全没变、结果状态一样——那些断言「send 被调 2 次」的行为验证测试会**红**，尽管代码其实是对的。这类「改对了却挂测试」的情况叫过度指定（over-specification）/脆弱测试，是行为验证的主要副作用。状态验证只看结果，对内部怎么实现不敏感，因此对重构更宽容。

反过来，状态验证也有它测不到的东西：没有可观察结果的副作用（发邮件、写日志、调用外部 API）就只能靠行为验证。经验法则是——**能用状态验证就优先用状态验证；只有当「正确性就是那次交互本身」时，才上行为验证**，并且尽量只断言「关键的那几次交互」，别把每个内部调用都锁死。

### 6.10.2.4 经典派 vs mockist 派——同一取舍在 TDD 风格上的投影

Fowler 把「默认用哪种验证」上升为两种 TDD 流派：**经典派（classical / classicist）** 默认用真实对象、必要时用 stub，主要做**状态验证**；**mockist 派（mockist / 「always mock」）** 默认给每个协作者上 mock、主要做**行为验证**。这不是对错之争，是风格取舍，Fowler 本人自陈偏经典派。

对初学者，记住两派的核心权衡即可，别急着站队。mockist 风格的好处是**隔离彻底**（一个类的 bug 只会让它自己的测试红，不会连累依赖它的类的测试，失败定位精准）、且**能驱动设计**（逼你想清楚对象间的协作关系）；代价是测试**与实现耦合更紧、重构时更易碎**，且大量 mock 会让人测「对象间怎么聊天」而非「系统整体行为对不对」。经典风格的好处是测试更贴近真实行为、重构友好；代价是一处 bug 可能让一串测试同时红，定位稍麻烦。TDD 本身与这条线的关系留大主题06-11 展开，本报告只把它作为「状态 vs 行为」这一取舍的自然延伸点到为止。

#### 来源与时效
- Martin Fowler「Mocks Aren't Stubs」(2007-01-02 修订)：状态验证 vs 行为验证的定义（"The Difference Between Mocks and Stubs"）、经典派 vs mockist 派与其权衡（"Classical and Mockist Testing"、"So Should I be a Classicist or a Mockist?"）（⚠准一手承重）。
- G. Meszaros《xUnit Test Patterns》(2007)：State Verification / Behavior Verification 模式，与 Fowler 口径一致（⚠准一手·交叉印证）。
- 本机实证（可选项，已做）：Python 3.11 `unittest.mock`，`demo_doubles.py` 两个测试分别演示状态验证（断言返回值）与行为验证（断言 `call_count` + `assert_any_call`），真实输出见 6.10.2.1/6.10.2.2，`基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`；脚本仅存 scratchpad、未入库。
- 「行为验证更易过度指定/变脆」为 Fowler 与业界共识经验主张（非定理），已标为经验取舍、不作绝对定论。

## 6.10.3 覆盖率类型与陷阱——行/分支/路径覆盖与「高覆盖率 ≠ 高质量」

### 6.10.3.1 覆盖率是量「测试跑到了多少代码」的尺子

代码覆盖率（code coverage）衡量一个测试套件在运行时**执行到了程序的多大比例**。它的直接用途是**指出哪里没被测到**：跑完测试，覆盖率工具把「一次都没执行到」的代码标出来，那些就是测试的盲区。

MIT 6.031 Testing reading 是这样定位它的：覆盖率是「衡量测试套件质量的一种方式——问它把程序执行得多彻底」。初学者要先接受一个前提：覆盖率是一个**必要不充分**的启发式——「没覆盖到的代码肯定没测好」是可信的（所以低覆盖率是明确的坏信号），但反过来「覆盖到了 = 测好了」并不成立（6.10.3.6 会用真跑的反例砸实这一点）。

覆盖率有强弱不同的几种粒度，从弱到强大致是：语句/行 → 分支/判定 → 条件 →（组合）→ 路径。粒度越强，要求测试触及的「代码可能性」越多，也越难达到 100%。

### 6.10.3.2 语句 / 行覆盖——每一条语句是否被执行过

语句覆盖（statement coverage，工具里常按「行」计，line coverage）问：**每一条可执行语句是否至少被执行过一次**。

其比例定义为：

statement coverage = 已执行语句数 / 可执行语句总数

这是最弱、最常见的一档。它容易达到高数字，但盲点也最大。经典盲点：`if cond: do_x()` 这样的单分支语句，只要你用 `cond` 为真的输入跑一次，`if` 行和 `do_x()` 行都算「被执行」，语句覆盖可达 100%——但 `cond` 为假的情况（隐式 else）你根本没测，而 bug 常常就藏在那条没写显式 else 的假分支里。这正是需要更强档「分支覆盖」的原因。

### 6.10.3.3 分支 / 判定覆盖——每个判定的真假两侧是否都走过

分支覆盖（branch coverage，亦称判定覆盖 decision coverage）问：**每个判定点（if / while / for / and-or 短路等）的每一个出口（真侧与假侧）是否都至少走过一次**。

其比例定义为：

branch coverage = 已走过的分支出口数 / 分支出口总数

它严格**强于**语句覆盖：达到 100% 分支覆盖必然达到 100% 语句覆盖（走遍所有分支出口必然执行了所有语句），反之不成立。回到上面 `if cond: do_x()` 的例子——分支覆盖会要求你既测 `cond` 为真、又测 `cond` 为假，从而逼出那条被忽略的假分支。coverage.py 的报告里，`Branch` 列是分支出口总数、`BrPart`（partial branch）列是「只走过一侧」的分支数，正是用来抓这种漏测半边的情况。业界一个务实目标常设在「尽量高的分支覆盖」，而非死磕更贵的路径覆盖。

### 6.10.3.4 条件覆盖与 MC/DC——把复合布尔表达式拆开看

当一个判定里有**复合布尔表达式**（如 `if a and b`），分支覆盖只看整个判定的真假两个出口，可能漏掉「是 a 还是 b 导致了这个结果」。更细的档次专门管这个。

条件覆盖（condition coverage）要求表达式里**每个原子子条件**（`a`、`b`）各自都取到过真和假。MC/DC（Modified Condition/Decision Coverage，修正条件/判定覆盖）更强：要求证明**每个子条件都能独立地改变整个判定的结果**——它是航空等安全攸关软件标准 DO-178C 对最高等级的强制要求。初学者一般课程/工程里接触到「分支覆盖」即足够，MC/DC 属安全关键领域，这里点到为止：知道「复合条件下，分支覆盖仍可能漏掉某个子条件从没起过决定作用」这个盲点即可。

### 6.10.3.5 路径覆盖——所有控制流路径的组合，通常不可行

路径覆盖（path coverage）问：**从入口到出口的每一条可能的执行路径是否都走过**。它是本节最强的一档，也是实践中通常**做不到**的一档。

原因是路径数会**组合爆炸**：n 个顺序排列的独立 if 就有 2ⁿ 条路径，带循环的话路径可以是**无穷多**（循环 0 次、1 次、2 次……各算一条）。MIT 6.031 Testing 明确指出 100% 路径覆盖一般不可行（infeasible），所以现实里没人拿它当门禁目标。初学者理解它的意义在于：它是覆盖率的「理论上限视角」，提醒你「分支全覆盖」离「所有情形都试过」还差得远——分支覆盖只保证每个岔路口的两边各走过一次，不保证各种岔路的**组合**都试过。

### 6.10.3.6 高覆盖率 ≠ 高质量——本机真跑的反例

覆盖率量的是「**跑过的已有代码**」，它有两个原理性盲区：一，量不出「**本该写却没写**」的逻辑（缺失的分支、漏处理的输入类别）——不存在的代码没法被「覆盖」，工具永远看不见；二，量不出「跑了却**没有断言**」的空测试——一个只调用函数、不检查任何结果的测试也能把覆盖率刷满。所以「高覆盖率 ≠ 高质量」不是玄学，是这把尺子的构造决定的。

下面这个本机真跑的反例把第一个盲区砸实。被测函数 `grade` 的规约是「≥90→A，80-89→B，70-79→C，其余→F」，但实现**漏写了 C 这一段**（70-79 会错误地落到 F）：

```python
def grade(score):
    # 规约: >=90 -> A, 80-89 -> B, 70-79 -> C, else F
    if score >= 90:
        return "A"
    if score >= 80:
        return "B"
    return "F"          # BUG: 70-79 本应是 "C"
```

一套「看起来很全」的测试——A、B、F 各测一个：

```python
from grade import grade
def test_a(): assert grade(95) == "A"
def test_b(): assert grade(85) == "B"
def test_f(): assert grade(50) == "F"
```

跑 coverage.py（开分支覆盖），结果是**语句和分支都 100%**：

```
$ python3 -m coverage run --branch -m pytest -q test_grade.py
3 passed
$ python3 -m coverage report -m
Name            Stmts   Miss Branch BrPart  Cover   Missing
-----------------------------------------------------------
grade.py            6      0      4      0   100%
test_grade.py       4      0      6      0   100%
-----------------------------------------------------------
TOTAL              10      0     10      0   100%
```

100% 语句、100% 分支覆盖，`Miss` 和 `BrPart` 全是 0——按覆盖率这把尺子，这套测试「完美」。但 bug 好端端地活着：

```
$ python3 -c "from grade import grade; print('grade(75) =>', grade(75), '(spec says C)')"
grade(75) => F (spec says C)
```

`grade` 里**根本没有** 70-79 对应的分支，覆盖率工具只能对「已存在的分支」要求你都走到，它无从要求你走一条不存在的分支——**缺失的逻辑天生在覆盖率的视野之外**。这就是为什么覆盖率再高也代替不了「按输入空间划分等价类、把边界值（79/80、69/70）都设成用例」的用例设计（回顾 MIT 6.031 的 partition/boundary 策略，见大主题06-9 用例选择）。覆盖率能告诉你「哪里还没测」，但不能告诉你「测得对不对、测全没测全」。

### 6.10.3.7 覆盖率的正确用法——诊断工具，不是考核目标

由 6.10.3.6，覆盖率的健康用法是当**诊断工具**：读它标出的「未覆盖行/分支」去补测、或去删死代码；而**不该**把某个覆盖率数字设成硬考核指标（KPI）。

原因是一旦「达到 X% 覆盖率」变成目标，人会为凑数字写「只执行不断言」的空洞测试，覆盖率上去了、真实保障没变、还多了一堆维护负担——这是 Goodhart 定律（「指标一旦成为目标，就不再是好指标」）在测试上的具体表现，Fowler 的「TestCoverage」一文与 Brian Marick 的早期文章都持此论。务实建议：把覆盖率当作发现盲区的雷达（尤其盯「分支没覆盖到」），关注**趋势和未覆盖清单**而非绝对数字；测试的质量最终由「用例是否覆盖了有意义的输入类别与边界、断言是否真的在检查行为」决定，而这些是覆盖率量不出来的。

#### 来源与时效
- MIT 6.031 sp22 Testing reading（https://web.mit.edu/6.031/www/sp22/，准一手课程）：coverage 定义与 statement/branch/path 三档、100% 路径覆盖一般不可行、以及「用输入空间划分（partition）+ 边界选用例、覆盖率仅作辅助」的策略侧（本节 6.10.3.1/6.10.3.5 据此）。
- coverage.py 官方文档（Branch coverage 页，锚 coverage.py 7.15.2）：分支覆盖语义、报告中 `Branch`/`BrPart`（partial branch）列含义（本节 6.10.3.3/6.10.3.6 报告字段据此）。
- Martin Fowler「TestCoverage」(martinfowler.com，⚠二手指路)+ Brian Marick「How to Misuse Code Coverage」(1997/1999，⚠二手)：「覆盖率作目标会诱发空洞测试、应作诊断而非 KPI」的共识来源（6.10.3.7）——本报告将其记为业界经验共识并给出来源，不作空断言。
- MC/DC 与 DO-178C（6.10.3.4）：航空安全关键软件标准对最高等级要求 MC/DC（⚠二手·指路，未逐字取证 DO-178C 原文，标「待核具体条款措辞」）。
- 本机实证（可选项，已做）：Python 3.11 + coverage.py 7.15.2 + pytest 9.1.1，`grade.py` 反例在 `--branch` 下语句/分支双 100% 而 `grade(75)` 仍返回错误值，真实输出见 6.10.3.6，`基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`；脚本仅存 scratchpad、未入库。
- 冲突/口径：无来源间实质冲突；术语「判定覆盖=分支覆盖」为常见等价用法，「条件覆盖 / MC/DC」是更细档次，已在 6.10.3.3/6.10.3.4 分档说明。
