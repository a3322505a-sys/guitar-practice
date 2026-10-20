# 吉他练习

自用的离线 Android 吉他练习 App，支持音阶、和弦、歌曲与短句练习。采用全宽联动指板/六线谱、底部播控及独立内容库与偏好。

当前开发版本 **1.2.0 / 107**，从 v106 `03431ea` 分支继续实现 [02.md 需求](docs/V3.1-v107-需求.md)。四个并列模块：基础启蒙、和弦教学、音阶教学、综合应用。内置 89 条标准练习记录，另有「每日练习」36 小节套曲及 C/Am、Capo 2、J-Rock 三个原创示范曲包，支持原谱/新手版本、独立领奏/伴奏、明确衔接、Solo 倒序接龙与练前热身。

旧曲包、SevenBlocks/Kernel 和旧 ID 兼容链已移除，当前数据完整编写并由独立审计检查。App 保持单一 alphaTab 播放/谱面时间轴。和弦词典按根音/性质查询多配法，音阶词典覆盖 12 主音 × 5 类及五个封闭位置；教材继续渐进呈现。领奏可手动选 100% / 30% / 0%，伴奏和节拍音量独立控制。内容与验证边界见 [V107 实施记录](docs/V3.1-v107-实施记录.md)；历史基线见 [V106 原生数据记录](docs/原生教学数据-v106.md)，构建入口见 [compare 变体](docs/build-variants.md)。


## 接手导航：V107 源码与练习内容 UI

**定位基线**：`a3322505a-sys/guitar-practice` 的 `codex/native-teaching-v106` 分支，已经包含 V107 词典和每日练习。**`main` 是较早版本，旧 `001` / `002` 不是本工程**。先确认 `git branch --show-current`、`git status --short`、`git log -1 --oneline`，不要覆盖其他工作树的未提交内容。

技术：Kotlin / Jetpack Compose，单个 Android `:app` 模块，minSdk 26（Android 8.0）、JDK 17，alphaTab 1.8.4。以下 Kotlin 文件的共同前缀是：

`app/src/main/java/com/a3322505a/guitarlearning/`

### 截图中「练习内容」具体在哪里

| UI / 行为 | 文件与符号 |
| --- | --- |
| **内容弹窗、四个教学页签、曲包来源 Chip、「管理」、分区标题** | `ui/PracticeControls.kt`：`WorkbenchPanel`、`WorkbenchContentChoices`、`ContentSectionTitle`、`PracticeSegments` |
| **来源识别、内容分组/排序、卡片标题/标签** | `ui/ContentPresentation.kt`：`ContentPresentation`、`phraseSections`、`chordSections`、`phraseText`、`chordText` |
| **两列卡片、选中态、文字换行、响应式列数** | `ui/ContentCards.kt`：`ContentCards` |
| **打开/关闭面板、当前分类/曲包/练习、选择后回传** | `ui/PracticeApp.kt`：`PracticeWorkbench`、`PracticeOverlay.CONTENT` |
| **四模块名称与课程归属** | `music/LessonMetadata.kt`：`TeachingModule`（基础启蒙、和弦教学、音阶教学、综合应用） |
| **两套词典的浏览入口** | `ui/DictionaryBrowser.kt`：`ChordDictionaryEntry`、`ScaleDictionaryEntry` |
| **主题颜色、文本层级、卡片/按钮外观** | `ui/Style.kt`、`ui/ThemeTokens.kt`、`ui/ThemeRegistry.kt`、`ui/ThemeManager.kt` |
| **弹窗状态参数与系统窗口策略** | `ui/PracticeSettings.kt`：`PracticeSettingsState`、`ImmersiveOverlayWindow`；注意 V107 的**弹窗内容布局主要在 PracticeControls** |
| **练习图示、和弦图/指板、状态提示** | `ui/PracticeContent.kt`、`ui/Diagrams.kt`、`ui/TeachingHints.kt` |
| **底部「听示范／开始跟练」、速度、循环** | `ui/PracticeTransport.kt`；不是内容卡片组件 |

**截图对应的两个重要细节：**

1. `WorkbenchContentChoices` 在和弦分支中，对每个 `pairSections` 输出一次固定的「双和弦转换」标题，解释了截图中标题重复、同名分区各只有 1 张卡的现象。UI 整理应先检查这层展示逻辑，**不用动音乐事件**。
2. `ContentPresentation.phraseSections(source, module)` 对基础训练按模块筛选，对指定曲包则允许展示整包卡片。V107 明确要求「选中曲包时四模块卡片一并可见」，所以截图不同页签看到 DP-01～DP-04 不应被直接当成脏数据删除。可重新组织跨模块呈现方式，但必须保留自由访问。

这次 UI 设计的重点是减少重复标题、层层描边和不必要留白，合理处理筛选 Chip、卡片行高、长标题、滚动与底部固定播控。**具体美术方案由设计 Agent 自主决定**；README 只指出入口和不能误伤的功能。

### 项目目录与职责

```text
MainActivity.kt
  └─ ui/PracticeApp.kt（Compose 状态与播放协调）
       ├─ ui/PracticeControls.kt（练习内容浏览/选择）
       │    ├─ ui/ContentPresentation.kt（展示投影）
       │    ├─ ui/ContentCards.kt（卡片与选中态）
       │    └─ ui/DictionaryBrowser.kt（词典）
       ├─ ui/PracticeContent.kt + ui/Diagrams.kt（练习舞台）
       └─ ui/PracticeTransport.kt（播放控制）

scripts/song_package/*.py + scripts/build-teaching-v31.py
  → app/src/main/assets/song-packages/*.gpractice
  → assets/song/SongReader.kt + SongCodec.kt
  → assets/song/SongRepository.kt（LibrarySnapshot）
  → ui/ContentPresentation.kt → ui/PracticeControls.kt
  → 选择 Phrase / Chord
  → practice/compile/PracticeCompiler.kt（PerformancePlan）
  → alphatab/NativeScore.kt + AlphaPlayback.kt（同一谱面/时间轴）
```

| 目录 | 主要入口 |
| --- | --- |
| `ui/` | `PracticeApp.kt`（宿主）、`PracticeControls.kt`（内容）、`ContentCards.kt`（卡片）、`ContentPresentation.kt`（展示投影）、`PracticeContent.kt`（舞台）、`Style.kt`（样式） |
| `assets/song/` | `SongRepository.kt`（曲库与目录）、`SongReader.kt`（读取校验）、`SongCodec.kt`（协议对象）、`StrictJson.kt` |
| `music/` | `Theory.kt`（调式/音高）、`Dictionary.kt`（全量词典）、`TeachingVoicings.kt`、`NativeScalePatterns.kt`、`Music.kt`（和弦/音位）、`ChordSlots.kt`（替代关系）、`score/Score.kt` |
| `practice/` | `Phrases.kt`、`Sequence.kt`、`Session.kt`、`PracticeExtensions.kt`、`TempoControl.kt`；`compile/PracticeCompiler.kt` |
| `alphatab/`、`audio/` | `NativeScore.kt`、`AlphaPlayback.kt`、`AlphaScoreAdapter.kt`、`PlaybackMix.kt`：谱面、原生播放和独立混音 |

**内置曲包**在 `app/src/main/assets/song-packages/`：`teaching-native.gpractice`、`daily-practice.gpractice`、`demo-c.gpractice`、`demo-capo.gpractice`、`demo-rock.gpractice`。协议定义在 `app/src/main/assets/song-contract/`。

**素材生产入口**：`scripts/song_package/daily_practice.py`（DP-01～DP-04）、`scripts/song_package/teaching_data.py`、`scripts/song_package/native_teaching.py`，统一构建为 `scripts/build-teaching-v31.py`；独立核验 `scripts/audit-native-teaching.py`。仅改界面**不需要重新编译曲包**，也不要直接手改已打包的 `.gpractice`。

### UI 修改边界

- 按当前底层事实做展示：**全量词典底层覆盖、教学逐步呈现**。四模块、练习 ID、曲包来源和版本、选卡、衔接、热身与循环入口保持可用；「每日练习」只是可自由选择的曲包，不是签到/强制任务。
- `ContentPresentation.kt` 只负责**展示投影**；重排/合并标题不能改 `PracticeKey`、`Phrase.id`、节拍、音符、弦品、指法、和弦构成与源数据。
- 不因 UI 整理去重写 `PracticeCompiler.kt`、`SongRepository.kt`、`NativeScore.kt`、`AlphaPlayback.kt`。原生 `NativeScore` 要保持固定挂载点，避免 AlphaTabView detach 后损坏实例。
- 保留两类音量（节拍/音乐）的独立调整、试听/跟练及播放位置。借用现有主题 Token；避免顺手引入个人档案、评估关卡、录音或新播放内核。
- 检查 360dp 窄屏、字体放大、半屏弹窗、横屏和平板；尤其注意长名称换行与底部控台覆盖列表。

### 现成验证入口

| 目标 | 测试文件 |
| --- | --- |
| 来源分组/文案 | `app/src/test/java/com/a3322505a/guitarlearning/ui/ContentPresentationTest.kt` |
| 内容弹窗/卡片布局 | `app/src/testDebug/java/com/a3322505a/guitarlearning/ContentPanelRenderTest.kt` |
| V107 每日练习与词典界面 | `app/src/testDebug/java/com/a3322505a/guitarlearning/V107RenderTest.kt` |
| 音乐事实/教学资料 | `app/src/test/java/com/a3322505a/guitarlearning/DailyPracticeTest.kt`、`TeachingV31Test.kt`、`DictionaryTest.kt` |

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:testCompareUnitTest :app:lintCompare :app:assembleCompare
```

按改动范围运行必要项目即可；构建成功不等于真机 UI/听感验收。正式/并装包、长期签名与增量版本见 [构建变体](docs/build-variants.md)。原应用兼容 Android 8，不能擅自改正式包名、签名或清数据。

**资料优先级**：[V107 需求](docs/V3.1-v107-需求.md) → [V107 实施记录](docs/V3.1-v107-实施记录.md) → [V106 原生数据](docs/原生教学数据-v106.md)；纯界面历史可查 [V101 内容面板](docs/练习内容面板-v101.md)、[P1–P8 教学 UI](docs/教学化UI-P1-P8.md)、[V93 主题](docs/v93-UI与主题系统.md)；分支提交遵守 [Git 约定](docs/git-governance.md)。旧 `SevenBlocks` / `KernelPhrases` / GP 导入链及 `001` 工程不是 V107 正式版修改入口。

边学边改，随缘更新。🎸
