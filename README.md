# 吉他练习

自用离线 Android 吉他练习 App。曲包就是内容入口：选择曲包与练习后进入同一个练琴舞台，上方为可折叠指板，主体为 AlphaTab 六线谱，底部单排【BPM、播放、循环、节拍】。教学内容由曲包元数据驱动，不锁进度。

当前开发：`codex/v114-count-in-cleanup`，**1.4.4 / 114**，基于 v113 `70fb07b`。V114 修复旧偏好导致原生预备拍跳过的问题，清理历史 Debug/P0 实现和误导性曲目回退；真实设备验收与交付状态见 [V114 记录](docs/V114-实施与交付.md)。

## 代码入口

共同路径：`app/src/main/java/com/a3322505a/guitarlearning/`。

| 功能 | 入口 |
| --- | --- |
| 曲包库、练琴舞台与弹窗协调 | ui/PracticeApp.kt、SongLibrary.kt、StageTransport.kt |
| 唯一谱面宿主、播放、平滑光标 | alphatab/NativeScore.kt、AlphaPlayback.kt、CursorDisplayClock.kt |
| 曲包事务、校验、按需准备 | assets/song/SongRepository.kt、SongReader.kt、music/score/、practice/compile/ |
| 指板与教学图示 | ui/Diagrams.kt、LinkedBoardIndex.kt |
| 主题与持久化偏好 | ui/ThemeManager.kt、ThemeRegistry.kt、practice/PhraseSettings.kt |
| 词典与教材数据 | music/Dictionary.kt、TeachingVoicings.kt、NativeScalePatterns.kt |

Kotlin / Jetpack Compose，JDK 17，alphaTab 1.8.4，compileSdk 36 / targetSdk 35。当前 minSdk 26 是工程配置，不表示承诺旧设备适配。单一音乐引擎负责谱面、播放、原生预备拍和事件时间轴；光标微调只影响显示。

## 构建与交付

完整命令、四种变体和签名约定见 [构建文档](docs/build-variants.md)。standalone 包名固定为 `com.a3322505a.guitarlearning.standalone`，版本 114，沿用 v111–v113 standalone 专用签名；正式版包名与长期签名保持。

CI 执行 Android 构建/单测/lint、Python 曲包校验及实际 APK 隔离审计。任何主机测试都不替代真实设备听到完整预备拍和正式第 1 拍。

## 文档

- [当前执行规则](AGENTS.md)、[V114 施工规范](docs/V114-执行规范.md)、[源码引用与退役审计](docs/V114-源码引用审计.md)
- [统一曲包驱动 UI 规范](docs/统一曲包驱动施工规范-20261022.md)、[曲包协议](contracts/song-package/v1/README.md)、[生产手册](docs/曲包生产手册.md)
- [文档索引与历史证据](docs/README.md)、[来源与许可](docs/来源与许可.md)、[Git 约定](docs/git-governance.md)

历史需求、验收截图和 SoundFont 原始素材保留用于追溯；旧 Debug 播放器、旧 TAB 与一次性 P0 探针不参与任何 App 变体。
