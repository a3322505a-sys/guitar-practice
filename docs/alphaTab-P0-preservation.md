# alphaTab P0：现有能力保留、替换与迁移清单

核查日期：2026-10-14。实施分支 `codex/alphatab-p0`，基于已 fetch 的远端 `main`：`09995e6f2f0f33f0cbbb52341abd06dc339a559c`（v81 / 4.0.0-local33，PR #11 已合并、两项 verify 成功，当前无开放 PR）。

主工作目录另有未提交的 v82 排版工作。本 P0 使用 `guitar-practice/work/alphatab-p0` 独立 Git worktree；不把这批未提交工作当作远端基线，也不覆盖它。`001` 仅提供既有构建工具链，不在该历史工程实施功能。

## 保留、替换与迁移

| 当前实际能力 / 存储 | 现有入口 | 决定 | 后续迁移条件 |
| --- | --- | --- | --- |
| Compose 常驻工作台、内容/跟练/声音配置、速度控制 | `ui/PracticeApp.kt`、`PracticeControls.kt`、`PracticeSettings.kt`、`PracticeTransport.kt` | 保留；P0 另设验证入口 | 新引擎达到同等交互后再接入，不为 P0 重设计主页面 |
| C 大调 / A 自然小调、五指型、往返/三度/连接、每拍 1/2/3 音 | `music/Music.kt`、`practice/Sequence.kt` 的 `Routes.scale` | 保留教学规则，未来适配 alphaTab 模型 | 逐项核对弦品、音高、顺序、节奏，禁止静默量化 |
| 基础 12 和弦、曲包和弦、手指编号、根音/构成音、开放/禁弹弦 | `music/Music.kt`、`ui/ChordDeck.kt`、`ui/Diagrams.kt`、`assets/song/SongRepository.kt` | 完整保留教学模型和交互 | 点音、逐弦、整和弦试听均需通过新引擎验证；不得移除分析卡片 |
| 七个 Block、通用短句、四个预装曲包及外部曲包 | `practice/Phrases.kt`、`SevenBlocks.kt`、`app/src/main/assets/song-packages/` | 保留源数据与可用入口 | 清点全部谱面语义后适配；不能假定用户有原 GP，也不能只保留 GP 曲库 |
| 现有动态曲库、修订、教学目录和指法 | `assets/song/SongRepository.kt`、`ui/SongLibrary.kt` | 保留，后续精简管理 UI | 新 GP 曲库复用本地持久化经验；旧 `.gpractice` 原件/索引可恢复后才做一次性迁移 |
| 已安装用户曲库 | App 私有目录 `files/songs`（索引、修订及目录元数据） | P0 不写入或迁移 | P5 覆盖安装与失败恢复独立验收；不清空、不破坏性迁移 |
| 学习/声音偏好、选中短句、每曲速度/次数/选段/选轨 | `practice-settings` SharedPreferences、`practice/PhraseSettings.kt` | 保留全部旧键 | 按现有 ID/来源锚点转换；失败保留原值，升级前后对比 |
| 指板、和弦图、点击试听与提示 | `ui/Diagrams.kt`、`ui/ChordDeck.kt`、`PracticeApp.kt` | 保留，未来只做引擎状态适配 | 弦号、调弦、capo、复音与持续音一起映射；不能自己计时模拟高亮 |
| 自研 TAB 排版 / 符号 / 高亮 | `ui/TabLayout.kt`、`Tablature.kt`、`PlanTablature.kt`、`TabSymbols.kt` | P2 达标后由 alphaTab 替换 | P0 保留原路径；不继续投资专业排谱，也不提前删除 |
| 自研播放、流式 PCM、Session/Cursor/TempoMap | `audio/Player.kt`、`PlanPcm.kt`、`practice/Session.kt`、`practice/compile/` | 新链路成立后替换运行时权威 | alphaTab 统一谱面、播放与进度；不建设第二个正式节拍器/播放器 |
| 应用身份、长期签名、离线能力 | `app/build.gradle.kts`、`scripts/sign-local.ps1`、Manifest | 保留 | 不换 applicationId 或密钥；新资源需随 APK 安装且分别核查许可 |

## P0 实施及停止边界

1. 只接入独立技术验证入口，优先使用桌面「谱子」真实 GP 文件；个人谱件不提交 Git。
2. 验证 native Android SDK 的实际可构建版本、真实格式解析、六线谱、离线音色、播放与位置事件，记录依赖和许可。
3. 简单单声部与复杂多声部分开选样；对照原 GP 结构检查，不能只证明文件能打开。
4. 主机解析、自动渲染、构建、实机显示、实机音频输出和真人听感分别记录。
5. 当前 ADB 无设备；设备接入前不声称 P0 实机验收通过。若核心能力不可行，停止迁移，保留证据和阻碍。
6. 本轮不连续推进 P1–P5，不删除旧系统、不改用户曲库或设置。

P0 实验细节与最终结论另见同目录的 `alphaTab-P0-results.md`（实际验证后填写）。
