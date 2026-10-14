# 吉他练习

离线 Android 吉他练习 App，单 `:app` 模块。当前版本为 **v78 / 4.0.0-local30**，包名 `com.a3322505a.guitarlearning`，沿用长期签名和用户设置。最新交付包：[v78 APK](output/guitar-practice-4.0.0-local30-v78.apk)；[预装曲包](app/src/main/assets/song-packages/)；[本轮迁移记录](docs/剩余曲包迁移与硬编码清理-v78.md)。

2026-10-13 完成局部UI职责搬迁及资产流程标准化：现有歌曲、目录、设置、播放与图示语义保持；没有增加产品功能、模型层或新发行版本。结果与设备证据见 [施工/兼容记录](docs/v72兼容与施工记录.md)。

2026-10-13 v73 修复手机顶部挖孔避让区白条：背景先铺满窗口，再对内容应用安全间距。验证见 [v73记录](docs/v73顶部背景修复.md)。

## 使用入口

- **音阶**：C大调 / A自然小调五型，往返、三度、相邻指型A→B→A，以及更多练法；一八度/整型音域、每拍1/2/3音。
- **和弦 → 和弦学习**：基础12和弦、曲包提供的《嘘月》4张及《斜阳》16张指法，以及外部曲包指法；点音、整和弦、逐弦示范和跟练，候选分析与省略音标记保留。
- **和弦 → 短句应用 → 练习设置**
- 指板/六线谱共享同一音乐事件与播放会话；无和弦分段的多音伴奏仍显示音位指板。速度、次数和小节范围按短句ID保存。声音开关立即生效；换实际内容/速度/选区结束旧播放，切视图保留播放位置。
- 手机手动TAB横屏/指板竖屏；平板按实际窗口与系统方向适配。顶部的大控件与底部原尺寸分开。没有成绩、课程门槛、账号或在线曲库。

## 源码与唯一权威链路

Kotlin目录：`app/src/main/java/com/a3322505a/guitarlearning/`。

| 入口 | 职责 |
| --- | --- |
| `MainActivity.kt` → `ui/PracticeApp.kt` | 状态、设置读写、导航与播放器操作的唯一页面协调入口 |
| `ui/PracticeControls.kt`、`PracticeContent.kt`、`PracticeTransport.kt`、`PracticeSettings.kt` | 原导航/练习区域/固定播放栏/设置展示，只接派生状态与回调 |
| `music/Music.kt` | 调弦、音高、基础音阶/指型和基础和弦；曲包指法由Repository提供 |
| `practice/Phrases.kt`、`PhraseSettings.kt` | 内置通用/七个Block练习；每ID速度/次数/选区恢复，曲包旧ID来自包内教学元数据 |
| `app/src/main/assets/song-packages/` | 四个审核曲包；作为唯一歌曲事件、教学与指法数据源，由现有Repository预装/读取 |
| `music/score/`、`practice/compile/`、`practice/Session.kt` | Q精确乐谱、结构校验、Form/Practice编译与唯一TempoMap；旧12/24 tick由可信适配器转入 |
| `assets/song/`、`contracts/song-package/v1/`、`ui/SongLibrary.kt` | 五文件共同协议、来源审核、动态目录、不可变修订/原子索引及导入管理 |
| `practice/Sequence.kt`、`Expression.kt`、`TabNotation.kt` | 保留内置素材与原谱面投影的兼容；运行时统一消费PerformancePlan与Session |
| `audio/Player.kt`、`PlaybackSink.kt`、`Pcm.kt`、`PlanPcm.kt`、`SoundBank.kt` | 唯一会话、采样器和流式输出；单调Long设备帧推进Cursor，音色保留采样近似 |
| `ui/Diagrams.kt`、`Tablature.kt`、`ChordDeck.kt`、`Style.kt` | 同一弦品/实音的图示、点击、谱行跟随、整卡横滑与样式 |

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
