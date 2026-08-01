# L5-13·大主题4 OCI 镜像格式与分层/union FS

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L5-13 大主题1（容器 vs VM）、L4-01 操作系统（文件系统/挂载）、L4-06 Git 对象模型（内容寻址）｜一手锚点：OCI Image Spec v1.1.0（2024-02-15，manifest/config/layer/media-types）、OCI Distribution Spec v1.1.0（2024-02-15，digest 引用）、Linux overlayfs 内核文档 Documentation/filesystems/overlayfs.rst（内核 6.x）｜成熟度：OCI 镜像格式与 OverlayFS 为 GA（长期稳定）；OCI 规范版本号 ⚙演进快·锚版本·随时变

这一份讲"一个容器镜像到底是什么文件、长什么样、为什么能层层复用还能秒级启动"。心智模型一句话：容器镜像不是一个大压缩包，而是一叠"只读补丁层"加上一份"怎么把它们叠起来、叠好后怎么运行"的说明书；运行时再用一种叫联合文件系统（union FS，本机是 OverlayFS）的技术把这叠只读层加一个可写层"透明地摞成一个根目录"给进程用。全篇有两条贯穿线索：一是内容寻址（用内容的哈希 digest 当地址，和 L4-06 Git 对象、L4-01 某些文件系统同源），它带来天然去重与不可篡改；二是分层 + 写时复制（CoW），它带来复用与轻量。规范（OCI 定义镜像文件长什么样）与实现（OverlayFS 定义运行时怎么把层摞起来）要分开看——两者用不同的方式表达"删除一个文件"，本篇会专门点出这处分歧。

---

## 13-4.1 镜像 manifest 与 config

### 13-4.1.1 镜像的顶层结构：index → manifest → config + layers

一个符合 OCI 规范的镜像，在概念上是一棵靠哈希指针连起来的树。最顶上（可选）是 image index（镜像索引，俗称"胖清单"fat manifest），它按平台（如 linux/amd64、linux/arm64）分别指向若干 image manifest（镜像清单）。每一份 image manifest 描述某一个具体平台的镜像，它指向两样东西：一份 image config（镜像配置，一个 JSON，记录"怎么运行"以及"层的组装顺序"），以及一个 layers 数组（若干层，每层是一个 tar 包，记录文件系统的增量改动）。所有这些指向都不是用文件路径，而是用被指对象内容的 digest（内容哈希）。

初学者可以按"目录树"来记：index 像"这张镜像针对各 CPU 架构分别有哪些版本"的总目录；manifest 像某一版本的"装箱单"，列清楚用哪份配置、摞哪几层；config 像"说明书"，写明入口命令、环境变量、以及这几层按什么顺序摞；layers 才是真正装东西的"箱子"。为什么要分这么多级？因为这样每一级都能被独立寻址、独立缓存、独立复用——两个镜像若共用同一个基础层，那层的 tar 只需存一份、传一次（这正是 13-4.2 的去重）。注意 index 是可选的：单平台镜像可以只有 manifest，没有 index。

### 13-4.1.2 image manifest：一张镜像的装箱单

image manifest 是一个 JSON 对象，规范要求的核心字段有三个：`schemaVersion`（模式版本，必须为整数 `2`，用于与老 Docker 兼容）、`config`（一个描述符 descriptor，按 digest 指向那份 image config）、`layers`（一个描述符数组，按顺序、按 digest 指向每一层）。此外强烈建议带上 `mediaType` 字段标明自己是 `application/vnd.oci.image.manifest.v1+json`。

这里的"描述符"（descriptor）是 OCI 里到处复用的小结构，它至少含三项：`mediaType`（这个被指对象是什么类型）、`digest`（它的内容哈希，即地址）、`size`（字节大小）。一份最小 manifest 长这样：

```json
{
  "schemaVersion": 2,
  "mediaType": "application/vnd.oci.image.manifest.v1+json",
  "config": {
    "mediaType": "application/vnd.oci.image.config.v1+json",
    "digest": "sha256:a1b2c3...",
    "size": 1470
  },
  "layers": [
    {
      "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
      "digest": "sha256:d4e5f6...",
      "size": 32654
    },
    {
      "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
      "digest": "sha256:7890ab...",
      "size": 16724
    }
  ]
}
```

初学者最容易搞混的是"manifest 里的 config 是一份 JSON，不是某一层文件系统"——它记录的是运行参数和层顺序，本身不含任何用户文件。另一个易错点：manifest 里 `layers` 数组的顺序是有意义的（从底到顶，越靠后越"上面"），不能乱序。还要分清两个 `size`/`digest`：manifest 里每个 layer 描述符的 digest 指的是"可能被压缩过的那个 blob"（见 13-4.2.3），而不是解压后的内容——这处区别后面会专门讲。

### 13-4.1.3 image config：记录如何运行与 rootfs 组装顺序

image config 是 manifest 的 `config` 字段指向的那份 JSON，它承担两大职责：一是记录容器"怎么运行"，二是记录根文件系统由哪些层按什么顺序组装。规范要求的顶层字段包括 `architecture`（CPU 架构，如 `amd64`，必需）、`os`（操作系统，如 `linux`，必需）、`rootfs`（必需，见下）；常见的可选字段有 `created`（创建时间戳）、`config`（执行参数对象）、`history`（各层的构建历史）。

其中 `config` 子对象放的就是运行时默认参数，字段名注意大小写（首字母大写）：`Env`（环境变量数组）、`Entrypoint`（入口命令）、`Cmd`（默认参数）、`WorkingDir`（工作目录）、`User`（运行用户/UID）、`ExposedPorts`（声明暴露端口）、`Volumes`（声明数据卷）、`Labels`（元数据）、`StopSignal`（停止信号）等。`rootfs` 子对象的 `type` 必须为 `layers`，其 `diff_ids` 是"从底到顶排列的各层内容哈希"的数组。一段节选：

```json
{
  "architecture": "amd64",
  "os": "linux",
  "config": {
    "Env": ["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],
    "Entrypoint": ["/bin/myapp"],
    "Cmd": ["--serve"],
    "WorkingDir": "/app"
  },
  "rootfs": {
    "type": "layers",
    "diff_ids": [
      "sha256:aaaa...",
      "sha256:bbbb..."
    ]
  }
}
```

为什么"层顺序"既出现在 manifest 的 `layers` 又出现在 config 的 `rootfs.diff_ids`？因为它们记的是同一叠层的两种哈希——manifest 记的是"分发时那个（可能压缩的）blob 的 digest"，config 记的是"解压后内容的 diff_id"（见 13-4.2.3），运行时正是靠 config 的 `rootfs.diff_ids` 顺序把层解开、按底到顶摞成根文件系统。易错点：`architecture`/`os` 是必需的，这也是同一个 `Dockerfile` 在不同架构机器上要构建出不同 config 的原因；`diff_ids` 的顺序错了，摞出来的文件系统就是错的。

### 13-4.1.4 image index：多平台"胖清单"

image index 是可选的最顶层对象，媒体类型为 `application/vnd.oci.image.index.v1+json`，它让"同一个镜像名"能同时服务多种 CPU 架构/操作系统。它内部是一个 `manifests` 数组，每个元素是一个描述符，指向某个具体平台的 image manifest，并带一个 `platform` 子字段说明该 manifest 适用的平台（含 `architecture`、`os`，可选 `os.version`、`variant` 等）。

直觉上，index 就是"多架构分流表"：当你 `docker pull` 某个镜像而不指定架构时，客户端先取 index，读你机器的架构（比如 arm64），再从数组里挑出 `platform.architecture` 匹配的那一项，去拉对应的 manifest 和层。这就是为什么同一个 `python:3.11` 标签在 Intel 笔记本和 Apple Silicon 上都能直接跑——它们拉到的是 index 里不同的分支。易错点：index 本身不含任何层，它只是"指向多份 manifest 的指针表"；很多单平台镜像根本没有 index，直接就是一份 manifest。注意历史包袱：Docker 早期用的是"manifest list"（媒体类型 `application/vnd.docker.distribution.manifest.list.v2+json`），OCI index 是它的标准化对应物，二者语义相近、现代工具通常都能识别。

### 13-4.1.5 media type 一览（内容类型标签）

OCI 用 media type（媒体类型字符串）标注每个对象是什么，这样解析方不用猜。核实到的 v1.1.0 定义如下（独占列出，避免记错）：

```
application/vnd.oci.image.index.v1+json      —— image index（多平台索引）
application/vnd.oci.image.manifest.v1+json   —— image manifest（单平台清单）
application/vnd.oci.image.config.v1+json     —— image config（运行配置 JSON）
application/vnd.oci.image.layer.v1.tar       —— 层：未压缩 tar
application/vnd.oci.image.layer.v1.tar+gzip  —— 层：gzip 压缩 tar（最常见）
application/vnd.oci.image.layer.v1.tar+zstd  —— 层：zstd 压缩 tar（较新，压缩更快/更高）
```

为什么要记这些？因为分发协议（大主题6）和运行时都靠 media type 分流：拉到一个 blob，看它的 media type 是 `...manifest.v1+json` 就当清单解析，是 `...layer.v1.tar+gzip` 就当 gzip 层解压。易错点是不要凭记忆改写这些字符串（少一个点、大小写错都会导致工具拒收）；tar+zstd 是相对较新的层压缩格式，老工具未必支持，属"能力待协商"的部分。这里也要做规范与实现分账：Docker 自家历史上有一套 `application/vnd.docker.*` 的媒体类型（如 `...image.rootfs.diff.tar.gzip`），与 OCI 的 `application/vnd.oci.*` 并存，现代仓库和运行时大多两套都认。

#### 来源与时效
- 锚点：OCI Image Spec v1.1.0（2024-02-15）——`manifest.md`（schemaVersion=2、config/layers 必需）、`config.md`（architecture/os/rootfs 必需，rootfs.type=layers、diff_ids）、`media-types.md`（上列媒体类型字符串，核实 2026-08-01 逐条比对官方仓库 v1.1.0 标签文本）。
- 交叉核对：媒体类型字符串同时在 OCI `media-types.md` 与 `manifest.md`/`config.md` 的示例中出现，两处一致；index 的多平台语义由 Image Spec `index.md` 与 Distribution Spec v1.1.0 的拉取流程共同印证。
- 冲突/分账：Docker 历史媒体类型（`application/vnd.docker.*`、manifest list）与 OCI 媒体类型（`application/vnd.oci.*`、image index）并存，语义对应但字符串不同，工具层多做兼容——规范文本明确二者为不同命名空间，不可混写。
- 版本：OCI Image Spec 锚定 v1.1.0（2024-02-15）（⚙演进快·锚版本·随时变）；是否已有 v1.1.x 补丁修订标「待核」，本报告结论基于 v1.1.0 文本。

## 13-4.2 层与内容寻址

### 13-4.2.1 层是文件系统 changeset（tar 增量）

每一层不是一个完整的文件系统快照，而是一个"相对下面各层的改动集合"（filesystem changeset），物理上就是一个 tar 归档。一层里记录三类东西：新增的文件/目录、被修改的文件/目录（整份收录改动后的版本），以及被删除文件的删除标记（whiteout，见 13-4.2.4）。把最底层当作起点，从底到顶依次"应用"（apply，不是简单解压覆盖，而是要处理删除标记）每一层，最终重建出完整的根文件系统。

建立直觉最好的类比是"叠透明胶片"：最底层画了一整套基础文件（比如一个精简 Linux 发行版的 `/bin`、`/lib`），第二层是一张只画了"我又装了 python"的胶片，第三层是"我把某个配置文件改了、又删了一个默认文件"的胶片。把这几张胶片按顺序叠在灯箱上，从上往下看到的合成图，就是最终容器看到的文件系统。为什么要做成增量而不是每层存全量？因为增量层小、可复用：无数镜像都基于同一个"基础层"，那层只画一次、存一份。易错点：修改一个大文件的一个字节，这一层也会把整份文件收进来（tar 层的粒度是文件，不是块），所以镜像分层设计上要把"常变的东西"放上层、"稳定的东西"放下层，才能最大化复用与缓存命中。

### 13-4.2.2 内容寻址 digest 与去重

OCI 用 digest（内容摘要）来标识和引用每一个对象——层、config、manifest 都有自己的 digest。digest 的格式是"算法名 + 冒号 + 十六进制编码"，当前默认且最广泛支持的算法是 sha256：

```
sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

这叫内容寻址（content-addressable）：一个对象的"地址"就是它内容的哈希，而不是某个文件名或 URL。带来的直接好处有两条。其一是天然去重：两个不同镜像若含完全相同的一层，那层内容相同 ⇒ digest 相同 ⇒ 在存储和传输里只需保留/传输一份（大主题6 的分发去重正建立于此）。其二是防篡改与可验证：拿到一个 blob，本地重新算一遍哈希，若和引用它的 digest 一致，就能确认"内容没被动过手脚、也没传坏"。

初学者要抓住的关键点是"地址即指纹"：只要内容变一个比特，digest 就完全不同，于是它就成了一个"新对象"。这也解释了不可变性（13-4.4）的根：你无法"就地改一层还保持同一个 digest"。易错点：digest 是对"字节内容"求哈希，所以同样的文件集合若打包方式不同（tar 里文件顺序、时间戳、权限位不同），digest 也会不同——这正是"可复现构建"要费力消除的非确定性来源（13-4.4.2）。

### 13-4.2.3 digest vs diff_id：压缩前后的两个哈希

同一层其实牵涉两个哈希，初学者极易混淆，必须掰开：diff_id 是"对该层未压缩 tar 归档求的哈希"，而 layer digest 是"对分发时实际存放的那个 blob（可能是 gzip/zstd 压缩后的 tar）求的哈希"。config 的 `rootfs.diff_ids` 用的是前者，manifest 的 `layers[].digest` 用的是后者。

```
未压缩 tar  --(sha256)-->  diff_id       （写进 config.rootfs.diff_ids）
   |
   | gzip 压缩
   v
压缩后 blob --(sha256)-->  layer digest  （写进 manifest.layers[].digest）
```

为什么要两个？因为同一份文件内容，用 gzip 压和用 zstd 压会得到不同的压缩字节，于是 layer digest 不同；但解压后内容相同，diff_id 相同。用 diff_id 来描述"文件系统内容身份"（谁和谁其实是同一层内容）就与压缩方式无关，运行时据此判断"这层我本地已经解压展开过了、不必重来"。用 layer digest 来做分发和存储寻址（拉取/去重的是那个压缩 blob）。易错点：把 config 里的 diff_id 拿去 registry 按 digest 拉 blob 会拉不到，因为 registry 存的是压缩 blob，按 layer digest 编址。规范里对未压缩层，diff_id 与 layer digest 可能相等；一旦压缩，二者必然不同。

### 13-4.2.4 whiteout：在 tar 层里表示"删除"

分层是"往上叠"，那怎么表示"删掉下层已有的一个文件"？OCI 层格式规定用 whiteout（涂白）文件：在上层 tar 里放一个特殊的空文件，名字是 `.wh.` 前缀加上要删除目标的 basename。例如要在镜像里删掉 `/etc/my-app-config`，就在这一层放一个：

```
./etc/.wh.my-app-config
```

当运行时从底到顶应用各层、遇到这个 `.wh.` 标记时，就把下层同名的 `my-app-config` 从合成视图里隐藏掉。还有一种更省的"不透明涂白"（opaque whiteout），用固定名字 `.wh..wh..opq` 放在某目录里，表示"把该目录在所有下层里的内容整体隐藏"，比逐个文件打 whiteout 高效。规范明确 whiteout 只作用于更下层/父层的资源，不能隐藏同层里的文件。

直觉上，whiteout 就是"叠加胶片"里那张写着"把下面那个东西盖掉"的贴纸——它本身不是内容，而是一条删除指令。易错点：初学者常以为"上层直接不含某文件就等于删了它"，其实不然——上层不提就是"不改动"，下层的文件仍会透出来；只有显式放 whiteout 才是删除。这处非常关键，因为它正是"OCI 规范如何表示删除"，而运行时用的 OverlayFS 表示删除的方式并不一样（见 13-4.3.4 的规范/实现分账）。

### 13-4.2.5 与 Git 对象模型、文件系统的内容寻址关联

镜像层的"内容即地址"和 L4-06 的 Git 对象模型是同一种思想：Git 里 blob/tree/commit 都用其内容的哈希（SHA-1，正过渡到 SHA-256）作为对象名，改一个字节就是一个新对象；OCI 用 sha256 digest 给 layer/config/manifest 编址，道理完全一样。两者都因此获得了去重（相同内容只存一份）、完整性校验（重算哈希比对）、以及"历史不可原地篡改"的性质。

再往下沉，某些文件系统/存储系统（如内容寻址存储 CAS、部分快照/去重文件系统）也用同一招按内容块哈希编址来去重，这与 L4-01 文件系统里的思路相通。给初学者的收束：一旦某个系统追求"去重 + 防篡改 + 可缓存共享"，用内容哈希当地址几乎是必然选择，容器镜像只是这一通用范式在"软件分发"场景的落地。易错点/分账：Git 与 OCI 虽同为内容寻址，但对象类型、哈希算法、组织方式各不相同，不能互换——"同源思想"不等于"同一实现"。

#### 来源与时效
- 锚点：OCI Image Spec v1.1.0（2024-02-15）——`layer.md`（层为 changeset、whiteout `.wh.`、opaque `.wh..wh..opq`、层按序应用）、`config.md`（diff_id = 未压缩 tar 的 digest）、`descriptor.md`（digest 格式 `algorithm:encoded`、sha256 为注册算法）；核实 2026-08-01。
- 交叉核对：diff_id 与 layer digest 的区分在 `config.md`（diff_id 定义）与 `manifest.md`（layers 描述符 digest）两处独立印证；内容寻址去重语义与 OCI Distribution Spec v1.1.0 的 blob 共享一致。
- 关联锚点：Git 内容寻址见 Pro Git 2nd ed §10（对象模型），与本处为跨主题思想关联，非同一实现。
- 版本：sha256 为当前默认注册摘要算法；是否有镜像层默认改用其他算法的动向标「待核」。OCI Image Spec 锚定 v1.1.0（⚙演进快·锚版本）。

## 13-4.3 union FS / OverlayFS CoW

### 13-4.3.1 联合挂载：把多层叠成一个视图

union filesystem（联合文件系统）是一类能"把多个目录（层）叠加挂载成单一目录视图"的文件系统技术，历史上有 AUFS、OverlayFS、devicemapper 等实现；现代 Linux 与容器运行时的默认是 OverlayFS（内核内置）。它解决的正是 13-4.2 的运行时落地问题：镜像层都是只读的 tar 展开物，容器运行时需要把它们叠成一个可读可写的根目录 `/` 交给进程，而且多个容器要能共享同一批只读层、各自的写入互不干扰。

直觉上，联合挂载就是把 13-4.2.1 那叠"透明胶片"真正摞到灯箱上、并在最上面再铺一张空白可写胶片：进程看到的是合成后的完整文件系统，读到的内容来自某一层（上层遮下层），而它写下的任何改动都只落在最上面那张可写胶片上，底下的只读层一个字节都不动。这就是容器能"从同一个只读镜像秒起一堆实例、各自随便改文件还互不影响"的机制根。易错点：union FS 是运行时的"组装术"，不是镜像格式本身——镜像格式（OCI）定义层怎么存、怎么表示删除，union FS 定义运行时怎么把层摞起来给进程看，两者是接力关系。

### 13-4.3.2 OverlayFS 的 lower / upper / work / merged

OverlayFS 挂载时有四类目录，理解它们就理解了整个机制：`lowerdir`（一个或多个只读下层，对应镜像的各只读层，可用冒号分隔叠多层，最左的优先级最高即"更上面"）、`upperdir`（唯一的可写上层，容器运行期的所有写入都落这里）、`workdir`（OverlayFS 内部用于原子操作的工作目录，必须和 upperdir 在同一文件系统，用户不直接用）、以及挂载点 `merged`（进程实际看到的合并视图）。一条典型挂载命令：

```
mount -t overlay overlay \
  -o lowerdir=layer2:layer1,upperdir=writable,workdir=work \
  merged
```

读操作的规则是"上层遮下层"：找一个文件时从 upperdir 往 lowerdir、从最左 lower 往最右 lower 依次找，找到的第一个生效。所以在上面命令里 `layer2` 比 `layer1` 优先（对应镜像里更上面的层覆盖更下面的层）。给初学者的落点：这恰好和 13-4.1 里"layers 数组从底到顶"对应起来——运行时把镜像最底层放 lowerdir 最右、最顶层放最左，再叠一个空 upperdir 作容器可写层。易错点：lowerdir 全是只读，绝不能指望往里写；workdir 必须与 upperdir 同一个文件系统，否则挂载失败；lowerdir 的冒号顺序（谁在左=谁在上）搞反会导致文件覆盖关系全错。

### 13-4.3.3 写时复制（Copy-on-Write, CoW）

CoW 是分层能"共享只读、各自可写"的关键。规则是：读一个只在下层存在的文件，直接读下层（零复制、零额外空间）；一旦要修改它，OverlayFS 先把整份文件从下层复制到 upperdir（copy-up），之后所有读写都作用在 upper 的这份副本上，下层原件保持不变。新建文件直接建在 upper；删除下层文件则在 upper 记一个删除标记（见 13-4.3.4）。

CoW 让"起一个容器"几乎不占额外磁盘——多个容器共享同一批只读 lower，各自只有一个空 upper，写多少才占多少。代价是"首次修改一个大文件"要付一次整份 copy-up 的开销（延迟与空间），因为 OverlayFS 的 copy-up 粒度是整个文件而非块。这解释了容器实践里的两条经验：一是"容器可写层里放大量写入或大文件是反模式"，应改用 volume（大主题11）把数据写到 union FS 之外；二是镜像构建时应尽量避免在上层去修改下层的大文件（会触发 copy-up 把整份大文件抄进新层，镜像反而变大）。

### 13-4.3.4 OverlayFS 的删除表示：字符设备 whiteout（规范 vs 实现分账）

这是本篇最需要点破的"规范与实现分账"。OCI 镜像格式（13-4.2.4）规定层里删除用 `.wh.` 前缀的普通文件来表示；而运行中的 OverlayFS 表示"删除一个下层文件"用的是另一套：在 upperdir 里放一个 device number 为 0/0 的字符设备文件（character device）作为 whiteout；"隐藏整个目录的下层内容"则通过给该目录设置 `trusted.overlay.opaque="y"` 扩展属性（xattr）来表达。二者语义相同（都是"盖掉下层"），但物理表示完全不同。

为什么会有两套？因为它们服务于不同阶段：`.wh.` 文件是"归档/分发格式"里对删除的可移植表示（tar 里塞不进真正的字符设备，也不该依赖特定内核特性），而字符设备 whiteout 是"运行时内核里"对删除的高效表示。容器运行时的存储驱动（graph driver，如 containerd 的 overlayfs snapshotter）负责在两者间翻译：拉取/展开层时把 tar 里的 `.wh.xxx` 翻译成 overlay 的字符设备 whiteout，反向打包层时再翻回 `.wh.` 文件。给初学者的收束：看到"删除"在两个地方长得不一样，不是矛盾，而是"存储格式"与"运行时格式"各自最优表示、由驱动层做转换。易错点：直接在 OverlayFS 的 upperdir 里手放 `.wh.` 普通文件不会被内核当删除处理（内核只认字符设备 whiteout）；反过来把 overlay 的字符设备直接塞进 OCI tar 也不合规范。

### 13-4.3.5 本机实证：挂 overlayfs 观察 CoW（真实输出）

本机（Linux 6.18.5，`/proc/filesystems` 含 `nodev overlay`，overlayfs 内核内置）成功挂载 overlay 并观察到上层遮下层与 CoW。可复现要点与真实输出如下：

```
# 准备：lower1 有 a.txt(=from-lower1)/b.txt(=base-b)；lower2 有 a.txt(=覆盖)/c.txt
mkdir lower1 lower2 upper work merged
mount -t overlay overlay \
  -o lowerdir=lower2:lower1,upperdir=upper,workdir=work merged
```

合并视图 `ls merged` 输出 `a.txt  b.txt  c.txt`（三层文件并集）；`cat merged/a.txt` 输出：

```
from-lower2-overrides
```

即 lowerdir 最左的 `lower2` 覆盖了 `lower1` 的同名 `a.txt`，印证 13-4.3.2 的"最左 lower 优先/上层遮下层"。接着触发 CoW（`echo appended >> merged/b.txt`，b.txt 原只在 lower1）：改动后 `ls upper` 出现了 `b.txt`（发生了 copy-up），`cat upper/b.txt` 为：

```
base-b
appended
```

而底层原件 `cat lower1/b.txt` 仍是：

```
base-b
```

下层一个字节未动，改动只落 upper——这正是写时复制。基线锚定：本机 cgroup 为 v1 hybrid（见大主题3），但 OverlayFS 与 cgroup 版本无关，overlay 挂载在本机可用（大主题4 之前 round-prompt 标注的"overlayfs 挂载权限待核"，本机实测为可挂，权限充足）。

#### 来源与时效
- 锚点：Linux 内核文档 `Documentation/filesystems/overlayfs.rst`（内核 6.x）——lowerdir/upperdir/workdir/merged 语义、最左 lower 优先、copy-up 行为、字符设备 whiteout（0/0）与 `trusted.overlay.opaque` xattr；核实 2026-08-01。
- 本机实证：Linux 6.18.5，`mount -t overlay` 成功，观察到"上层遮下层"与 copy-up（上列真实输出，scratchpad 内运行、未入库）。
- 交叉核对：CoW/联合挂载定性由内核 overlayfs 文档与 OCI Image Spec `layer.md`（层按序应用重建 rootfs）两侧印证；删除表示差异为二者显式分账点（OCI `.wh.` 文件 vs overlay 字符设备），非冲突而是分层职责不同。
- 版本/分账：union FS 有多种实现（AUFS/OverlayFS/devicemapper 等），行为细节因实现而异（⚙实现相关）；本篇 CoW/whiteout 表示锚定 OverlayFS，其余实现的对应表示标「待核」。

## 13-4.4 不可变镜像与可复现构建

### 13-4.4.1 不可变镜像作可靠部署基座

不可变镜像（immutable image）指：一个由 digest 唯一确定的镜像，其内容一经构建就不再改变；要"改"只能构建出一个新镜像、得到新的 digest。这个性质直接来自 13-4.2 的内容寻址——digest 是内容指纹，改内容必然换 digest，所以"同一个 digest 永远对应同一堆字节"是数学上成立的，而非靠约定。

它为什么是可靠部署的基座？因为它消除了"环境漂移"：如果部署时按 digest（而非可变 tag）引用镜像，那么开发、测试、生产三处跑的字节完全一致，"在我机器上是好的、上线就挂"这类问题里"镜像不一致"这一维被彻底排除。回滚也变得简单可靠——回滚就是把引用指回上一个已知good 的 digest，那堆字节还在、还是原样。给初学者的关联：这与 13-4.1.3 里 config 记录运行参数、与大主题11"配置/密钥与镜像解耦"配套使用——镜像不可变，把"随环境而变的配置"放到镜像外（ConfigMap/Secret/环境变量），才能做到"一个镜像跑遍所有环境"。易错点：tag（如 `:latest`）是可变的、可被重新指向另一堆字节，用 tag 部署不等于不可变部署；真正的不可变引用是 `name@sha256:...`（tag vs digest 的可重复性属大主题6）。

### 13-4.4.2 可复现构建（reproducible build）

可复现构建指：同样的源代码 + 同样的构建环境/指令，能构建出逐字节相同（因而 digest 相同）的镜像。它比"不可变"更进一步——不可变只保证"这堆字节不变"，可复现保证"任何人重跑构建都能得到同一堆字节"，从而可独立验证"这个镜像确实是由这份源码构建的"，是供应链安全（大主题6 的签名/attestation）的基础。

难点在于构建过程里有大量非确定性来源会让 digest 变动：文件时间戳（mtime）、tar 打包时的文件顺序、文件属主/权限、构建中嵌入的当前时间或随机数、依赖版本未锁定（今天装到的库和明天不同）等等——回顾 13-4.2.2，digest 对字节敏感，任何一处抖动都会让层的 digest 变。所以可复现构建的实践就是逐条消除这些非确定性：锁定依赖版本（lockfile）、把时间戳规范化为固定值（如 `SOURCE_DATE_EPOCH`）、固定文件排序与权限、避免嵌入构建时刻。给初学者的直觉：可复现构建就是"把构建函数变成纯函数"——同样的输入永远给同样的输出。易错点：可复现是"尽力消除非确定性"的工程目标，很多现实构建并不完全可复现；宣称"可复现"必须指明"在什么构建环境/工具版本下"，脱离环境谈可复现没有意义（本项属工程实践，具体工具链达成度标「待核」）。

### 13-4.4.3 层缓存与构建效率

分层不仅省存储/传输，也让"构建"变快：构建工具（如基于 Dockerfile 的构建器）会把每条指令产出的层按"输入是否变化"做缓存，输入没变就直接复用上次那层（同一 digest），只从第一处变化开始重建后续层。这就是"把稳定的步骤放前面、常变的步骤放后面"能大幅加速构建的原因。

一个应用镜像若先 `COPY` 依赖清单并安装依赖、再 `COPY` 频繁改动的源码，那么改源码时依赖层命中缓存、不必重装依赖；若顺序反过来（先 COPY 全部源码再装依赖），改任何一行源码都会让后面的依赖安装层缓存失效、每次都重装，慢得多。给初学者的收束：这条实践把 13-4.2.1"常变放上层、稳定放下层"从"省空间"延伸到"省构建时间"，两者同因同源——都源于"层按内容寻址、内容不变则整层复用"。易错点：缓存命中依据的是"该层及其所有下层的输入是否都没变"，只要某个靠下的层变了，其上所有层的缓存都会失效（缓存是链式的）。

#### 来源与时效
- 锚点：OCI Image Spec v1.1.0（2024-02-15）——不可变性/内容寻址根据 `descriptor.md`（digest 唯一标识内容）与 `config.md`/`manifest.md`（镜像由 digest 引用组成）；核实 2026-08-01。
- 交叉核对：digest 引用不可变 + tag 可变的对比由 OCI Distribution Spec v1.1.0（tag/digest 引用）与 Image Spec 共同印证（tag vs digest 详见大主题6）。
- 二手/待核：可复现构建的具体达成手段（`SOURCE_DATE_EPOCH`、lockfile、时间戳规范化）与层缓存机制属构建工具工程实践，随工具（BuildKit/buildah 等）演进——⚙演进快·随时变；具体工具默认行为与"完全可复现"达成度标「待核」，本篇只述原理不锚定某工具版本。
- 关联：不可变镜像 + 配置外置见大主题11；供应链签名/可复现的验真见大主题6（⚠前沿，规范化程度待核）。
