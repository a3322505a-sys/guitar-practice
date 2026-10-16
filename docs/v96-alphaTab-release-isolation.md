# v96：基于 v95 的 alphaTab 构建隔离

开发基线为远端 `origin/codex/gemini-studio-deck-v95`，提交 `bd9d469f0f5b9d71882156df1fac79b5ff079f5d`。独立工作树为 `work/alphatab-release-isolation-v96`，分支 `codex/alphatab-release-isolation-v96`。版本递增至 `96 / 4.2.0-local48-isolated`。没有合并、重置或改写 v95、main 及昨天的清理分支。

## 原成果保护与迁移取舍

昨天的 v94 工作树仍停留在原 v93 基线，暂存区和工作文件均保留。开始迁移前保存了 618 个源码/资料文件、原始 Git index、二进制补丁、所有未跟踪源码和忽略目录中的产出/测试报告。归档逐文件核对 SHA-256，原状态与 index 在备份前后相同。

- 本地恢复引用：`refs/snapshots/alphatab-cleanup-v94-20261016t003034z`
- 快照提交：`0e79bdee12a5c2a6821013e17914163b4c01bc50`
- 备份目录：项目根 `.local/task-backups/v94-preserved-20261016T003034Z/`
- `working-source.tar` 保留实际工作文件字节和换行；`source-snapshot.tar` 是 Git 规范化源码；`ignored-evidence.tar.gz` 保留基线、日志、图像、音频、测试/分析报告。
- 完整哈希、路径与原状态摘要见同目录 `backup.json`、`source-sha256.json`。

先核对远端 v95 与本地一致，再按文件确认其输入和昨日基线相同，复用 19 项旧运行时搬迁/元数据拆分。两份工作台 UI 文件重新按 v95 做窄改写，完整保留 Studio Deck、模式导航、配置退让、舞台内 ViewSwitch、状态/提示和根容器中固定的 NativeScore 挂载点。v95 的主题/对比度/透明高亮及其更新后的测试保留。昨日修正固定粉色断言的改动已被 v95 覆盖，没有再迁移。

## 正式入口与可达性

| 路径 | 本轮核实与处理 |
| --- | --- |
| `MainActivity → PracticeModel → AlphaPlayback` | 正式工作台入口只接收 AlphaPlayback；移除以类型转换决定新旧绑定的逻辑 |
| `GpPracticeActivity → AlphaPlayback` | 原谱页的原有实现完整保留 |
| `NativeScore → AlphaTabView` | v95 源码不变；排谱、滚动、透明光标及固定挂载仍由同一原生视图负责 |
| `AlphaPlayback → api.play/loadMidiForScore/playBeat` | 示范、跟练、试听、变速、选段、循环、预备拍和音符时间线保持原实现 |
| `playerPositionChanged/fromMidi → EngineProjection → Cursor` | 正式指板状态来自引擎事件；接口不再提供旧 Session.cursor 默认实现 |
| `AlphaPlayback.fail` | 保留错误状态与原文案，不切换旧引擎或渲染器 |
| `PracticeWorkbench` | 共享既有状态/UI；旧 TAB 内容插槽只由 debug 中的显式绑定提供，release 入口只绑定 alphaTab |

所有 `alphatab/*.kt` 文件与 v95 原件逐字节相同。无新播放器、时钟、产品入口、依赖版本、架构框架或 CI gate。

## 源集与必要拆分

| 保留在正式构建 | 从正式构建隔离的对照物 |
| --- | --- |
| Phase、Progress、PracticePlayback 契约 | debug：Player、Sampler、Pcm、AndroidSink、PlaybackSink、FrameCounter、SoundBank |
| Session 的范围/拍号/选段元数据；Cursor 数据 | debug：LegacySessionClock、TempoMap、LegacyPlanTiming、设备帧转换与旧 Cursor 推导 |
| PhraseRhythm 的原节奏接受规则 | debug：TabBar/TabSegment、PlanNotation、TabLayout、PlanTablature、TabSymbols、TabHighlight、Tablature |
| PlaybackPresentation 的纯阶段显示过滤 | debug：旧高亮实际绘制 |
| Score、FormCompiler、PracticeCompiler、LegacySequenceAdapter、IntervalIndex 等活跃数据能力 | testDebug：原 UI/PCM/旧谱面测试；通用 alphaTab/设置/来源事实测试仍可在 release 运行 |

曲目注册原来借 `tabBars()` 验证节奏，现保留相同的校验和拒绝结果，不构造旧谱面对象。新增两项 debug 对照覆盖现有短句、显式三连音、非法细分和跨小节边界。`Sequence.performedTick/writtenTick` 经全项目无调用核实后删除；仍有来源/兼容职责的数据能力保留。

release 明确关闭 `isMinifyEnabled` 与 `isShrinkResources`。隔离依靠物理源集和资产目录，既有依赖版本保持不变。

## v95 声音与可复现生产链

已在迁移前实际运行 v95 的生产脚本 `--check`，并核对 v95 正式 APK 中的 SoundFont 与源码资产相同。

- alphaTab 1.8.4 自有加载流程读取 `sonivox.sf2`，没有接入旧采样播放器。
- 默认吉他 bank 0 / program 24 使用原 22 份 CC0 尼龙采样；GP 的其余吉他 program 25–31 保留原映射。
- 节拍和预备拍均使用 SoundFont bank 128 / program 0 / key 33 的原 45ms 木质短音。
- `MASTER=0.8`、轨道相对系数 `0.15`、`CLICK=0.9` 及声音开关/预备拍策略保持 v95 原码。
- 正式 SoundFont SHA-256 保持 `02ab9f358d1d79906e07ba8eb1d08993addfa3f23a9aaebea51e4589379a2bf8`。

尼龙 PCM 同时是正式音色的生产原料，没有作为废弃素材删除或仅归属旧播放器。它移至 `assets/soundfont-source/guitar-nylon/`，准备/转换脚本改读写这一生产目录；debug 通过额外 assets 源目录复用。旧电吉他 PCM 仅留在 debug。生成命令、固定上游提交、逐 PCM 哈希校验、校准、采样映射与合成算法保持。

生产脚本现在一并生成并核对 release 中 `third-party/nylon/` 的 LICENSE、README、manifest，保证原料搬迁后音色与许可记录仍可完整重建。普通 release 构建只消费已生成音色，不需要下载原录音或安装音频处理依赖。

## 基线与验证证据

v95 的 153 项本地测试（152 通过、1 项原有跳过）及 [远端 CI](https://github.com/a3322505a-sys/guitar-practice-private/actions/runs/38754028582) 已核实；复用其通过的 Python 资产/曲包检查，没有重跑无改动的资产生产审核。源码、APK、7 张 UI 渲染、4 个 alphaTab 合成 WAV、14 个旧 PCM 对照输出均冻结于本工作树 `.local/baseline/`。

迁移后使用全新工作树、关闭构建缓存运行检查，131 个任务全部实际执行，8m51s 完成。

| 检查 | 结果 |
| --- | --- |
| `testDebugUnitTest` | 155 项：154 通过、0 失败、1 项原有本地审计素材测试跳过；保留全部 v95 场景并新增 2 项节奏校验对照 |
| `testReleaseUnitTest` | 31 项全部通过，正式变体不需要旧运行时编译测试 |
| `lintDebug`、`lintRelease` | 各 0 错误、17 警告，与 v95 既有警告数量相同 |
| `assembleDebug`、`assembleRelease`、`:app:compileP0Kotlin` | 全部通过 |
| v95 UI 对照 | 7 张渲染 PNG 逐字节一致，覆盖手机、横屏、平板与主题/配置 |
| 实际音频合成对照 | alphaTab 的 4 个音色/节拍 WAV 和旧 PCM 的 14 个输出逐字节一致 |
| 声音生产链 | `--check` 通过：SoundFont 与三份许可/来源输出全部可复现 |
| release DEX 与资产 | 15,701 个定义类中，无 debug 旧实现定义或类型引用，无原始 PCM；压缩器均关闭 |
| debug 内容 | 93 个旧实现/嵌套类及全部 35 份 PCM 保留 |
| 正式资源 | 20 个保留资产、351 个 Android 资源/原生库条目与 v95 逐字节一致 |
| 引擎代码 | 9 个 alphaTab 接入源文件字节一致，42 个编译类字节一致；3 个 Compose UI 宿主 class 重新编译，按原码与 UI 对照核验 |
| 依赖/流程 | 依赖声明与 v95 完全相同；没有新增 CI gate |
| 签名/对齐 | 原证书 SHA-256 匹配，APK v2/v3 签名及 zipalign 通过 |
| 覆盖安装/启动 | 已在既有 API28 x86 软件模拟器从 v95 以 `install -r` 更新；首次安装时间保留，回读 base.apk SHA-256 与交付包一致；实际主页面、TAB 切换和引擎 4 小节排谱/就绪均确认 |

DEX 结构变化对应的 `assets/dexopt/baseline.prof`、`baseline.profm` 由构建系统重建，不计作音乐或 UI 素材变化。3 个重编译 UI 宿主为 `GpPracticeActivity.class`、`GpPracticeActivityKt.class`、`NativeScoreKt.class`；详细清单和各项核验结果见 `output/isolation-audit.json`，完整执行记录见 `output/v96-verification.json`、`.local/validation-v96.log`。

安装包：`output/guitar-practice-4.2.0-local48-isolated-v96.apk`，40,982,171 字节，较 v95 减少 4,495,879 字节。沿用证书 `43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`。

APK SHA-256：

```text
a8ad107579d700c8337205970095013d9a36cee64aba2f204bb8bea2b6c4ad7d
```

本轮手工审计工具 `scripts/audit-runtime-isolation.py` 直接读取最终 APK 的 DEX 定义/引用及 ZIP 资产，并用 debug APK 作旧类/素材存在的正对照；没有写入 CI。原 v95 和昨日 v94 工作树均保持原状态。

现场已查看 [v96 原生主页面](v96/native-main.png) 与 [v96 横屏原生 TAB](v96/native-tab.png)。启动等待命令曾超时，后续用实际前台 Activity、`rendered bars=4` / `ready visits=4` 和截图确认显示；该软件模拟器耗时不作为真机性能结论。

主机回归与软件模拟器基础安装/启动检查不等同于真机连续音频、主观听感、严格音画同步或长时间操作验收。
