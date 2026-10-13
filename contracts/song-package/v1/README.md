# 歌曲数据包 v1

首次协议冻结：2026-10-13；major=1、minor=0。方案文档是设计与历史审查依据，本目录是实际协议；App 能力及限制以 capabilities.json 和施工记录为准。首次冻结前补入经过条件与弱起 leadIn，既有原创样本字节未改；尚未发布过其他 v1 封装。

容器后缀 .gpractice，ZIP 恰好包含 manifest.json、score.json、practices.json、provenance.json、review.json。五份 *.schema.json 是双方共同结构规则，build-schemas.py 同步 App 内的同字节副本。只含数据，拒绝附件、脚本、HTML、音源、APK、嵌套 ZIP、路径变形、重复条目、加密和符号链接。

## 字段与引用

- ID 是稳定 ASCII 标识，包内不同实体 ID 不重名，引用闭合。显示名允许重复。ID 不承担音高、时间、顺序或类名语义；包身份和本地 ID 构成全局身份。旧 ID 只由受控内置兼容 provider 映射。包内 alias 不抢占别的包或内置 ID。
- Q 的单位是四分音符=1，n/d 必须约分、d>0、0={0,1}，数值预算 10^12。运算保持精确，超限拒绝；绘图、积分和最终帧舍入才用浮点。最终帧绝对舍入，避免每音/每轮累加舍入。
- Meter 分母为 1/2/4/8/16/32/64，beatGroups 的和等于 numerator。actualDuration 表示真正书面长度；不完整小节要求 incompleteReason。leadIn 是缺省前置拍位，默认 0，和 actualDuration 不超过 nominalMeter；它只用于拍位与预备对齐，不冒充原谱休止。
- 每声部按绝对书面位置无缝覆盖 Score；休止只属于本声部。measureRest 表示明确整小节休止。practicePadding 有理由，和原谱 rest 分开。相同时间可以有不同声部事件和音头；同轨同弦冲突拒绝，显式 sharedSoundId 才允许共同发声。
- 轨道弦序 1 为最高音弦方向，openStringMidi 六项、整夹 capo 和 fretCount 都是本轨事实。普通音校验调弦+capo+品位。泛音保留基音品位、partial、touchFret 与最近 MIDI 音名；实际采样频率用基音频率×partial，触点必须是相应节点。
- written 的 baseDenominator/dots 与逐层 tuplet 比率必须等于实际时长。组成员可以含休止、跨小节和不等值成员；嵌套须指向直接 parent，禁止交叉、循环、成员缺失。
- 核心字段未知即拒绝，extensions 只允许有命名空间的非音乐纯文本；不会执行或加载其内容。未知 major/minor 不做默认降级。

## 曲序、速度与表达

- Repeat.times 包含第一次；ending.passes 针对指定反复。Jump dc/ds 使用一次，to-coda 在回跳后生效，Fine 在回跳后结束。repeatsAfterJump 默认 false。occurrence 与 repeatContext 是原谱经过，Session 的练习循环是另一身份；Form 最多 50000 次经过。
- condition 可限定经过 occurrence 与反复遍次 repeatPasses。共同有界 Form 决定条件，不使用表达式语言；默认状态在来源坐标重新计算，符合条件的后置事件覆盖同位置默认事件。反复上下文只保存当前反复和房子相关项，最多受反复嵌套预算限制。
- Tempo 的 beatUnit 为 Q；例如附点四分=60，即四分=90，6/8 一小节 2 秒。线性 BPM 渐变以公共演奏拍位为自变量，后来的速度事件覆盖此前曲线。秒采用积分，帧采用绝对 round；逆解用于实际消费帧光标。
- 默认 source 模式保持原谱变速×倍率；fixed 模式用显式四分音符 BPM，忽略旧倍率。旧设置继续用 fixed；改速度/轨道/选区会结束旧会话。预备是完整有效拍号小节，leadIn 另行对齐并提示。
- Swing 具有范围与轨/声部作用域；比例与 unit 明确。显式 tuplets 保持内部精确比例，组端点按公共映射定位。不同声部的发声区间各自编译，但只有一条设备帧时钟；高亮查询这些区间。
- 音头覆盖事件默认值，显式 false/空曲线会覆盖默认值。采样支持力度/重音、扫弦时差、P.M./短促/死音/延响/截音，tie、slur、h/p、连奏与重拨滑音、滑入/滑出、tapping、bend/vibrato/摇把、装饰组、trill/tremolo、泛音。
- Tie 不另起音，继承未显式重定义的连续表达/相位；Slur 保留各自拨奏。重拨滑音在到达时另起音。装饰组 before/on 时间明确，pick 关系另起音，其他关系连续。曲线点经同一时间映射到帧，点间按帧线性插值；bend+vibrato+摇把相加。无法正确表达的冲突组合、不可分细分与不足一帧/扫弦延迟越界明确拒绝。
- Sampler 音色为近似，不宣称原录音/RSE 复刻。capabilities.json 分别列模型、TAB、时序音高和音色；设备、试听与实弹是否完成另见施工记录。

## 练习与修订

PracticeSpec 是来源坐标半开窗口，written/performed 明确；经过重复时必须给 occurrence。多个窗口有序拼接，mainTrackId 与 mixTrackIds 分开。默认 entry/exit=reject；切断事件、关系、连音必须列出受影响音头并明示 carryIn/reattack/truncate。源谱保留，教学处理是派生计划。用户运行时小节范围恢复持续状态，末尾淡出但不增拍。

相同 ID/revision/digest 幂等；同修订不同内容拒绝，高修订更新，低修订经显式回退。包和审核哈希基于原始字节，不证明作者或音乐正确。活动索引原子提交，不把多次设置写入算作一个导入事务；导入不修改设置，使用稳定键/来源锚点恢复，失效范围回整句且保留速度、次数。正在播放的不可变计划钉住修订；停用不会删除外部原件或运行中的数据。

## 来源与审核

provenance 的源文件 SHA-256 与 noteId 定位覆盖全部源音头；GP 保留对象引用链，PDF 保留页码/区域或既有可定位转录证据。原件、私钥、机器日志、临时曲包不入仓。review 绑定 score/practices/provenance 字节哈希，并分别统计书面和展开单位。

diagnostics 分类 supported / approximation / out-of-scope / review-needed / unsupported-blocker。conditional 必须给具体假设和受影响练习；只允许已披露的来源反复次数/覆盖假设，不能绕过未知音乐、结构、音高、时值或预算错误。normalize 生成 review.template.json，不能自动审核；reviewer/method 未完成时 pack 拒绝。

统一错误含 code、pointer、reason、sourceRef（可提供时）。JSON/ZIP、SCHEMA、MUSIC/EXPRESSION/BOUNDARY、FORM、HASH/REVIEW_HASH、CAPABILITY_DECLARATION/CAPABILITY、REVIEW_NEEDED、COVERAGE、NAMESPACE、REVISION_CONFLICT/ROLLBACK_REQUIRED、LIMIT/PLAN_LIMIT/TIMING_RESOLUTION 分开。

资源数值见 capabilities.json。首次 limits 是实现边界，不是所有手机已通过的性能保证。cases.json 的有效与无效包共用两端；手列曲序与解析时间期望在测试中，源曲独立 CSV 不由新生成器重写。
