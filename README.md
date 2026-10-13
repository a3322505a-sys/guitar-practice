# 吉他练习

离线 Android 吉他练习 App，单 `:app` 模块。当前版本为 **v75 / 4.0.0-local27**，包名 `com.a3322505a.guitarlearning`，沿用长期签名和用户设置。最新交付包：[v75 APK](output/guitar-practice-4.0.0-local27-v75.apk)；[音乐内核与曲包记录](docs/音乐内核与曲包施工记录.md)。

v75 新增严格的 .gpractice 曲包导入：练习设置 → 本地曲库 / 导入歌曲。外部审核歌曲无需增加源码或重打包；导入后复制到私有存储，可离线使用。支持精确节奏、多声部、变拍、曲序、变速、调弦/整夹和通用奏法；原谱速度倍率与固定四分音符速度分开。源码验证和 QQ 文件回读已完成，本次无连接设备，安装启动、系统文件选择和严格音画/试听/实弹仍待验。

2026-10-13 完成局部UI职责搬迁及三条歌曲资产流程标准化：现有歌曲、目录、设置、播放与图示语义保持；没有增加产品功能、模型层或新发行版本。结果与设备证据见 [施工/兼容记录](docs/v72兼容与施工记录.md)。

2026-10-13 v73 修复手机顶部挖孔避让区白条：背景先铺满窗口，再对内容应用安全间距。验证见 [v73记录](docs/v73顶部背景修复.md)。

2026-10-13 v74 完整录入桌面《七个Block》GP的38小节，七个入口保留实际弦品、上下拨、死音、混合拍号与三十二分/三连音；反复按GP展开，三组原次数待核保留说明。来源与验证见 [v74记录](docs/七个Block完整录入-v74.md)。

## 使用入口

- **音阶**：C大调 / A自然小调五型，往返、三度、相邻指型A→B→A，以及更多练法；一八度/整型音域、每拍1/2/3音。
- **和弦 → 和弦学习**：基础12和弦、《斜阳》16来源指法、《卡夫卡》50来源指法；点音、整和弦、逐弦示范和跟练，候选分析与省略音标记保留。
- **和弦 → 短句应用 → 练习设置**：旧短句、莫扎特三段、原创奏法练习、《斜阳》5条、《卡夫卡》7核心及6个更多版本、《Your Trip》6条、《七个Block》7条完整技巧练习。卡夫卡28条来源素材仍可引用，主界面按教学目标显示核心。
- 指板/六线谱共享同一音乐事件与播放会话；无和弦分段的多音伴奏仍显示音位指板。速度、次数和小节范围按短句ID保存。声音开关立即生效；换实际内容/速度/选区结束旧播放，切视图保留播放位置。
- 手机手动TAB横屏/指板竖屏；平板按实际窗口与系统方向适配。顶部的大控件与底部原尺寸分开。没有成绩、课程门槛、账号或在线曲库。

## 源码与唯一权威链路

Kotlin目录：`app/src/main/java/com/a3322505a/guitarlearning/`。

| 入口 | 职责 |
| --- | --- |
| `MainActivity.kt` → `ui/PracticeApp.kt` | 状态、设置读写、导航与播放器操作的唯一页面协调入口 |
| `ui/PracticeControls.kt`、`PracticeContent.kt`、`PracticeTransport.kt`、`PracticeSettings.kt` | 原导航/练习区域/固定播放栏/设置展示，只接派生状态与回调 |
| `music/Music.kt`、`ShayouChords.kt`、`KafkaChords.kt` | 调弦、音高、音阶/指型、实际和弦指法与分析信息 |
| `practice/Phrases.kt`、`KafkaPractice.kt`、`PhraseSettings.kt` | 完整来源集合、可见目录、教学版本/旧ID映射、每ID速度/次数/选区恢复与回退 |
| `practice/Shayou.kt`、`Kafka.kt`、`YourTrip.kt` | 三首歌的编译期事件；由来源工具生成，审核后纳入 |
| `music/score/`、`practice/compile/`、`practice/Session.kt` | Q精确乐谱、结构校验、Form/Practice编译与唯一TempoMap；旧12/24 tick由可信适配器转入 |
| `assets/song/`、`contracts/song-package/v1/`、`ui/SongLibrary.kt` | 五文件共同协议、来源审核、动态目录、不可变修订/原子索引及导入管理 |
| `practice/Sequence.kt`、`Expression.kt`、`TabNotation.kt` | 保留内置素材与原谱面投影的兼容；运行时统一消费PerformancePlan与Session |
| `audio/Player.kt`、`PlaybackSink.kt`、`Pcm.kt`、`PlanPcm.kt`、`SoundBank.kt` | 唯一会话、采样器和流式输出；单调Long设备帧推进Cursor，音色保留采样近似 |
| `ui/Diagrams.kt`、`Tablature.kt`、`ChordDeck.kt`、`Style.kt` | 同一弦品/实音的图示、点击、谱行跟随、整卡横滑与样式 |

来源事实 → 教学选材/变体 → 可见目录 → 用户选择恢复保持独立。内置素材经可信legacy provider保留目录、旧ID及设置；新歌使用审核JSON包。历史v72契约见 [兼容表](docs/v72兼容与施工记录.md)，当前协议见 [曲包v1](contracts/song-package/v1/README.md)。

## 构建与资产生产

本机复用原JDK17/SDK35工具链：

```powershell
.\scripts\build-local.ps1 --offline --no-daemon --max-workers=2 testDebugUnitTest lintDebug lintRelease assembleRelease
python scripts/test_song_assets.py
python -m pip install -r scripts/song_package/requirements.txt
python scripts/test_song_packages.py
```

其他机器配置JDK17、Android SDK35及local.properties后用 `./gradlew testDebugUnitTest lintDebug assembleRelease`。需要正式交付时才递增版本并用 `scripts/sign-local.ps1` 读取现有签名；不重建密钥，私钥/密码/本地原谱不入仓。

新增歌曲使用 `scripts/song-package.py inspect/normalize/validate/pack`；格式、选轨/分句、审核和打包见 [曲包生产手册](docs/曲包生产手册.md)。旧 `song-assets.py` 与保护CSV保留作历史来源/兼容回归；不再作为新歌注册入口。历史与新运行时流程见 [曲谱导入](docs/曲谱导入.md)，原件与许可见 [来源与许可](docs/来源与许可.md)。

## 验证与历史

本地回归、lint、发行构建与候选输出一致性分别记录；真机安装/操作/性能基线和实际试听、实弹、严格音画同步分别陈述。不能用生成成功或CI替代源谱审核/真人验收。本轮未证明性能提升，也没有改热路径。

整理前完整README与v49–v72交付事实已保留在 [版本历史](docs/版本历史-v49-v72.md)，各版本审核文档、原截图和本地APK入口保留。首版背景见 [执行方案](docs/执行.md)；当前事实以本页和对应源码/本轮证据为准。
