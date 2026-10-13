# 吉他练习

离线 Android 吉他练习 App，单 `:app` 模块。当前音乐与功能契约为 **v72 / 3.8.0-local24**，包名 `com.a3322505a.guitarlearning`，沿用长期签名和用户设置。最新既有交付包：[v72 APK](output/guitar-practice-3.8.0-local24-v72.apk)；[Your Trip 来源审核](docs/YourTrip完整练习-v72.md)。

2026-10-13 完成局部UI职责搬迁及三条歌曲资产流程标准化：现有歌曲、目录、设置、播放与图示语义保持；没有增加产品功能、模型层或新发行版本。结果与设备证据见 [施工/兼容记录](docs/v72兼容与施工记录.md)。

## 使用入口

- **音阶**：C大调 / A自然小调五型，往返、三度、相邻指型A→B→A，以及更多练法；一八度/整型音域、每拍1/2/3音。
- **和弦 → 和弦学习**：基础12和弦、《斜阳》16来源指法、《卡夫卡》50来源指法；点音、整和弦、逐弦示范和跟练，候选分析与省略音标记保留。
- **和弦 → 短句应用 → 练习设置**：旧短句、莫扎特三段、原创奏法练习、《斜阳》5条、《卡夫卡》7核心及6个更多版本、《Your Trip》6条。卡夫卡28条来源素材仍可引用，主界面按教学目标显示核心。
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
| `practice/Sequence.kt`、`Expression.kt`、`TabNotation.kt` | 原Event/Sequence、技巧/摇摆/混合拍号、Session/Cursor、制谱；12 tick/四分音符 |
| `audio/Player.kt`、`PlaybackSink.kt`、`Pcm.kt`、`SoundBank.kt` | 唯一会话与设备输出，实际消费PCM帧推进Cursor；既有音源与奏法采样近似 |
| `ui/Diagrams.kt`、`Tablature.kt`、`ChordDeck.kt`、`Style.kt` | 同一弦品/实音的图示、点击、谱行跟随、整卡横滑与样式 |

来源事实 → 教学选材/变体 → 可见目录 → 用户选择恢复保持独立；工具侧JSON不是App运行时曲库。模型/音乐/音频/制谱/和弦与设置文件本轮保持原样，兼容规则见 [契约表](docs/v72兼容与施工记录.md)。

## 构建与资产生产

本机复用原JDK17/SDK35工具链：

```powershell
.\scripts\build-local.ps1 --offline --no-daemon --max-workers=2 testDebugUnitTest lintDebug lintRelease assembleRelease
python scripts/test_song_assets.py
```

其他机器配置JDK17、Android SDK35及local.properties后用 `./gradlew testDebugUnitTest lintDebug assembleRelease`。需要正式交付时才递增版本并用 `scripts/sign-local.ps1` 读取现有签名；不重建密钥，私钥/密码/本地原谱不入仓。

歌曲候选入口是 `scripts/song-assets.py` + `scripts/song-manifests/`，报告包含来源哈希、音乐覆盖/阻断、半开句界、practicePadding、候选输出与v72保护哈希比较。GP/PDF专属适配、真实命令、新歌的数据路径和人工审核点见 [曲谱导入](docs/曲谱导入.md)。原件与许可见 [来源与许可](docs/来源与许可.md)。

## 验证与历史

本地回归、lint、发行构建与候选输出一致性分别记录；真机安装/操作/性能基线和实际试听、实弹、严格音画同步分别陈述。不能用生成成功或CI替代源谱审核/真人验收。本轮未证明性能提升，也没有改热路径。

整理前完整README与v49–v72交付事实已保留在 [版本历史](docs/版本历史-v49-v72.md)，各版本审核文档、原截图和本地APK入口保留。首版背景见 [执行方案](docs/执行.md)；当前事实以本页和对应源码/本轮证据为准。
