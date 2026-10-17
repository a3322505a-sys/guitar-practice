# 吉他练习

仓库协作与 Agent 交付规则见 [Git 分支与交付约定](docs/git-governance.md)。

离线 Android 吉他练习 App，单 `:app` 模块。当前版本为 **v96 / 4.2.0-local48-isolated**，包名 `com.a3322505a.guitarlearning`，沿用长期签名和用户设置。[v96 APK](output/guitar-practice-4.2.0-local48-isolated-v96.apk)；[alphaTab 构建隔离与回归记录](docs/v96-alphaTab-release-isolation.md)；[Gemini Studio Deck 重设计与验证](docs/v95-Gemini-Studio-Deck.md)；[主题系统](docs/v93-UI与主题系统.md)；[v92 音色与验证](docs/v92-音色优化.md)；本地主工作目录未提交的 v82 排版工作独立保留。

v96 从远端 v95 支线独立施工，保留 Studio Deck、11 套主题、原生谱面固定挂载、全部功能及音乐语义。旧 PCM 播放、设备帧时钟和自研 TAB 只编入 debug；release 直接由源集隔离，不依赖压缩器移除。正式 SoundFont 与混音保持，尼龙 PCM 作为生产原料独立保留，见 [音色生产输入](assets/README.md)。

v95 由 Gemini 4 Argon 主导视觉与布局，Codex 本地集成。主界面直接切换音阶、和弦与短句；GP、曲库和管理独立进入，指板/六线谱切换移入练习舞台。手机竖屏保留上下控台，横屏与平板使用左侧 Studio Deck 和右侧全高舞台；窄窗口打开配置时自动退让。根音、普通音与当前音重新分层，保留 11 套主题和外观持久化。音乐、来源、曲包与播放引擎契约保持，具体验证边界见本版记录。

v93 统一工具栏、分段切换、练习主卡片和底部速度/拍号播控台。默认森林绿主题，内置豆沙粉、樱花粉、莓果粉等 11 套主题；右上齿轮 → **主题 / 外观**、练习配置 → **外观**，或 GP 页齿轮可即时切换并在重启后保留。指板、和弦、进度和 alphaTab 谱面/光标共享主题 token；新增配色只需注册主题。保留 v92 音色及既有播放/曲包语义。

v92 将旧版 CC0 nylon 采样转换为 alphaTab SoundFont，替换默认木吉他与节拍器专用短音。节拍与预备拍使用相同音量，示范轨降低以保留多音下的节拍清晰度。声音、谱面和时间轴仍由 alphaTab 统一处理，实际听感由真机验收。

v91 修复曲包和弦选择与逐弦练习中的基础名称反查，卡片、指板、试听与练习共用所选来源身份、指法、调弦及 Capo。工作台和 GP 曲库统一仅显示带节奏的 TAB；初始速度和真实变速保留，同值标记去重。预备拍定位所选段落起始拍，恢复半透明粉色光条，继续由 alphaTab 推进播放与跨行循环。

v90 在原工作台中正式接入 alphaTab：六线谱、声音、变速、选段循环和播放进度使用同一原生引擎；原指板、和弦知识和教学卡片保留。新增 **GP 曲库**：GP5 / GPX / GP 导入一次后保存副本，以后断网重开；搜索、删除确认、最近练习以及每曲选轨、选段、速度、次数、显示和声音偏好分别保存。格式承诺限于已试读的具体样本，不承诺所有 GP8 变体。

现有音阶、和弦、内置短句及四个预装 `.gpractice` 曲包经来源适配进入 alphaTab 模型，原曲库目录和设置键继续使用。正式启动入口只绑定 `AlphaPlayback`，错误时保留原提示；旧实现和采样在 debug 保留对照。自动核对不能替代连续发声、音色、同步、横屏和平板操作验收。

v81 收紧播放栏：窗口宽度和有效高度允许时速度与操作合为一行，窄窗口分两行；速度浮层默认收起，拖动松手才应用。顶部圆角入口只显示一份曲目摘要，TAB 下方只保留播放状态；管理与帮助复用同一配色和卡片。已复现并修复截音后的幽灵音符及新谱继承旧滚动位置，保留同一练习的播放跟随。真机安装、操作及实际试听尚未验证。

v80 将主页面调整为常驻练习工作台：顶部摘要选择内容，配置层分内容/跟练/声音；手机竖屏半屏配置露出有效图示，横屏与平板使用侧面板。底部常驻速度与按状态显示的播放操作，齿轮统一曲包管理和帮助。必要自动检查与构建见记录；实际使用由用户验收。

v79 将音阶、内置练习和曲包统一到同一套六线谱布局与绘制。粉色当前音光条、小节高亮和谱行/密集小节跟随全部使用既有 Session/Cursor；结束时清除高亮。品位数字优先，符干与连梁移到下方，连续延响和 P.M. 使用保留弦号范围的奏法标记。旧渲染分支已在迁移回归后移除；手机/平板模拟渲染与播放状态验证见记录，真机尚待验收。

2026-10-13 完成局部UI职责搬迁及资产流程标准化：现有歌曲、目录、设置、播放与图示语义保持；没有增加产品功能、模型层或新发行版本。结果与设备证据见 [施工/兼容记录](docs/v72兼容与施工记录.md)。

2026-10-13 v73 修复手机顶部挖孔避让区白条：背景先铺满窗口，再对内容应用安全间距。验证见 [v73记录](docs/v73顶部背景修复.md)。

## alphaTab P0 独立验证分支

保留 **v86 / 4.0.0-local38-alphatab-p0** 的历史探针构建，见 [保留 / 迁移清单](docs/alphaTab-P0-preservation.md)、[初次 P0 记录](docs/alphaTab-P0-results.md) 与 [2026-10-15 续作证据](docs/alphaTab-P0-continuation-2026-10-15.md)。当时的软件模拟证据与音频限制仍按原记录保留；v90 起 alphaTab 已进入正式构建，历史探针入口仍只在 `p0`。

## 使用入口

- **音阶 / 和弦 / 短句**：主界面的模式分段直接切换；**当前练习摘要 → 内容** 调整具体素材与参数。
- **音阶**：C大调 / A自然小调五型，往返、三度、相邻指型A→B→A，以及更多练法；一八度/整型音域、每拍1/2/3音。
- **和弦**：基础12和弦、曲包提供的《嘘月》4张及《斜阳》16张指法，以及外部曲包指法；点音、整和弦、逐弦示范和跟练，候选分析与省略音标记保留。
- **短句**：选择曲目/完整乐句；版本、选段、轨道按需展开。
- **GP / 曲库**：独立入口进入原谱练习或选曲，导入 GP5 / GPX / GP，选择六弦吉他轨。配置中的选段按引擎实际演奏顺序列出每次小节经过；跨入延音/连奏的起点需要前移，选段结束截断剩余延音。**‹ 教学** 返回后原教学选择保留。
- **控台速度**：竖屏在底部，横屏/平板在左侧；点带箭头的数值打开滑块或原谱倍率浮层，点收起、空白处或返回关闭。**摘要 → 跟练/声音** 调整次数、速度模式与即时声音开关。**齿轮** 管理曲包、查看帮助和切换主题。
- 指板/六线谱共享同一音乐事件与播放会话；无和弦分段的多音伴奏仍显示音位指板。速度、次数和小节范围按短句ID保存。声音开关立即生效；换实际内容/速度/选区结束旧播放，切视图保留播放位置。
- 手机手动TAB横屏/指板竖屏；平板按实际窗口与系统方向适配。顶部的大控件与底部原尺寸分开。没有成绩、课程门槛、账号或在线曲库。

## 源码与唯一权威链路

Kotlin目录：`app/src/main/java/com/a3322505a/guitarlearning/`。

| 入口 | 职责 |
| --- | --- |
| `MainActivity.kt` → `ui/PracticeApp.kt` | 原教学工作台与设置连接；`PracticeModel` 只创建 `AlphaPlayback` |
| `alphatab/GpLibrary.kt`、`GpPracticeActivity.kt` | 原谱副本、SHA-256 身份、逐曲原子记录、导入与跟练；不写入旧曲库目录 |
| `alphatab/AlphaPlayback.kt`、`NativeScore.kt`、`EngineProjection.kt` | 原生六线谱与单一合成器会话；引擎 MIDI 音符起止/听到的 tick 投影为持续复音指板；切视图保留原生控件 |
| `alphatab/AlphaScoreAdapter.kt` | 现有来源/教学计划到 alphaTab 的有限适配，保留精确时值、弦品、调弦、capo、声部、休止和连线；不支持的转换报错 |
| `alphatab/AlphaNotation.kt`、`PracticeCursorHandler.kt` | 工作台/GP 共用 TAB 与速度标记整理；SDK 光标的粉色呈现和预备拍起点冻结 |
| `ui/PracticeControls.kt`、`PracticeContent.kt`、`PracticeTransport.kt`、`PracticeSettings.kt` | 一级模式与摘要/练习舞台/上下或左侧控台/自适应配置层展示，只接派生状态与回调 |
| `ui/ThemeTokens.kt`、`ThemeRegistry.kt`、`ThemeManager.kt`、`Style.kt`、`ThemePreviewCard.kt` | 视觉 token / 11 套本地主题 / 独立外观偏好 / Compose 主题、卡片骨架和预览；不参与练习数据与播放决策 |
| `music/Music.kt` | 调弦、音高、基础音阶/指型和基础和弦；曲包指法由Repository提供 |
| `practice/Phrases.kt`、`PhraseSettings.kt` | 内置通用/七个Block练习；每ID速度/次数/选区恢复，曲包旧ID来自包内教学元数据 |
| `app/src/main/assets/song-packages/` | 四个审核曲包；作为唯一歌曲事件、教学与指法数据源，由现有Repository预装/读取 |
| `music/score/`、`practice/compile/`、`practice/Session.kt`、`Cursor.kt` | 精确来源、认可路线、旧设置及界面元数据；Session 不含旧秒/设备帧时钟，正式播放由 alphaTab 时间线推进 |
| `assets/song/`、`contracts/song-package/v1/`、`ui/SongLibrary.kt` | 五文件共同协议、来源审核、动态目录、不可变修订/原子索引及导入管理 |
| `practice/Sequence.kt`、`Expression.kt`、`PhraseRhythm.kt` | 内置教学规则、来源兼容和原有节奏校验；保留有效数据能力 |
| `audio/PracticePlayback.kt`、`ui/PlaybackPresentation.kt` | 状态/操作契约与纯显示过滤；各播放器明确提供 Cursor |
| `app/src/debug/java/`、`app/src/testDebug/` | 旧 `Player`、PCM、设备帧/TempoMap 时钟、自研 TAB 与对应对照测试；不进入 release/p0 |
| `assets/soundfont-source/guitar-nylon/`、`scripts/build-practice-soundfont.py` | 既有正式 SoundFont 原料及可复现生产；debug 复用同一份 PCM，release 只打包生成的音色及许可 |
| `ui/Diagrams.kt`、`ChordDeck.kt`、`Style.kt` | 现有音位指板、和弦教学卡片与整卡切换保留 |

来源事实 → 教学选材/变体 → 可见目录 → 用户选择恢复保持独立。通用/七个Block内置素材经可信legacy provider接入；迁移歌曲和新增歌曲使用审核曲包。莫扎特历史6个ID及其他迁移ID保留，旧设置按来源锚点迁移；原键不删除。历史v72契约见 [兼容表](docs/v72兼容与施工记录.md)，当前协议见 [曲包v1](contracts/song-package/v1/README.md)。

## 构建与资产生产

本机复用原JDK17/SDK35工具链：

```powershell
.\scripts\build-local.ps1 --offline --no-daemon --max-workers=2 testDebugUnitTest lintDebug lintRelease assembleRelease
python scripts/verify-bundled-songs.py
python -m pip install -r scripts/song_package/requirements.txt
python scripts/test_song_packages.py
```

其他机器配置JDK17、Android SDK35及local.properties后用 `./gradlew testDebugUnitTest lintDebug assembleRelease`。需要正式交付时才递增版本并用 `scripts/sign-local.ps1` 读取现有签名；不重建密钥，私钥/密码/本地原谱不入仓。

新增歌曲使用 `scripts/song-package.py inspect/normalize/validate/pack`；格式、选轨/分句、审核和打包见 [曲包生产手册](docs/曲包生产手册.md)。旧歌曲Kotlin生成器已移至 `scripts/archive/legacy-v76/`，仅为历史来源参考，不参与App注册、当前生产或CI生成。独立来源CSV和已审核PDF转录保留；`verify-bundled-songs.py`核对预装内容。历史与新运行时流程见 [曲谱导入](docs/曲谱导入.md)，原件与许可见 [来源与许可](docs/来源与许可.md)。

## 验证与历史

迁移来源审核、针对性Repository检查及发行构建分别记录；真机安装/操作/性能基线和实际试听、实弹、严格音画同步分别陈述。不能用生成成功或CI替代源谱审核/真人验收。本轮未证明性能提升，也没有改热路径。

整理前完整README与v49–v72交付事实已保留在 [版本历史](docs/版本历史-v49-v72.md)，各版本审核文档、原截图和本地APK入口保留。首版背景见 [执行方案](docs/执行.md)；当前事实以本页和对应源码/本轮证据为准。
