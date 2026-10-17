# 教学化 UI · P0–P8 实施与验收

2026-10-17。在当前本地工作区 `work/ui-density-v99`、`e3a4ba5` 的现有代码上增量实施，分支 `codex/teaching-ui-p1-p8`。没有切换远端基线或合入其他保留分支。版本递增为 `1.0.1 / 103`，正式包名与长期签名沿用。

## 实际改动

| 文件 | 内容 |
| --- | --- |
| `ui/ContentPresentation.kt`（原有） | 按来源和稳定 ID 派生基础分组、顺序、教学 Tag；音阶文本与真实品位范围；曲包和弦性质标签 |
| `ui/ContentCards.kt` | Tag 在上、粗体 Primary 在下，左对齐；按宽度/字体降列，音阶标题单行；完整无障碍名称与独立单选组 |
| `ui/PracticeControls.kt` | 移除音阶外围套卡，分成五个轻量小节；音域与节奏各自单选，继续回传原 `ScaleOptions.copy` |
| `ui/Diagrams.kt` | 保守横按派生、圆角半透明短带、紧凑图例/横按说明；普通下一音细环，显式技巧连接与符号；沿用现有坐标和命中 |
| `ContentPresentationTest.kt`、新增 `TeachingDiagramTest.kt` | 映射、来源隔离、真实数据跨度、横按正反例及显式技巧 |
| `ContentPanelRenderTest.kt`、新增 `TeachingDiagramRenderTest.kt`、`ChordSourceRenderTest.kt` | 尺寸/字体、提示隔离、点按与导入和弦全链路 |
| `WorkbenchRenderTest.kt`、`RenderTest.kt` | 更新原有测试的音阶显示名称查找；前者纳入本轮回归，后者只同步两处文字并编译，未整体复跑 |
| `app/build.gradle.kts`、README/开发索引 | 版本和成果入口 |

未修改 `Music.kt`、练习/曲包数据、音阶生成、关系解析、音频、时间轴、谱面、Repository 或导入协议。`PracticeContent.kt` 不需要改动：它已经将真实来源和弦传给 `ChordDiagram`；在共享绘图入口增强即可覆盖该路径。

## P0–P8

| 阶段 | 状态 | 实际依据 |
| --- | --- | --- |
| P0 | 完成 | 核对本地基线与已有映射/双行卡；确认 `Chord.frets/fingers` 为 6→1 弦，曲包来源使用编码 ID；定位普通箭头来源 |
| P1 | 完成 | 基础和弦 5/3/4，共 12 个；顺序与对象完整性断言；实际 ID `D/F♯` 显示 `D/F#`，固定 Tag「第一转位 · G→Em 常用桥梁」 |
| P2 | 完成 | 标准 F、Bm、迷你 F、高把位 F 与反例检查；FULL/ROOTS/HIDDEN 渲染；点按坐标/试听回归 |
| P3 | 完成 | 基础短句 7/5/4/2，共 18 项；按稳定 ID 重排，不修改原列表；三连音与五类技巧保留指定标签 |
| P4 | 完成 | 五个轻量小节，360dp/411dp/1024dp 实际组件检查；宽屏五指型五列，窄屏降列 |
| P5 | 完成 | 调性/指型/模式/音域/节奏 2/5/6/2/3；真实跨度动态读取；音域、节奏双选中状态独立 |
| P6（可选） | 完成 | 锚定完整和弦名称识别强力/减七/减三/斜杠性质；品位后缀优先；`no5` 不误判 |
| P7 | 完成 | 删除本地 `LinkedFretboardDock` 从普通 active/upcoming 两点派生的通用虚线箭头；普通音仅高亮/细环；显式 slide/hammer/pull/tap 关系继续专用连接；隐藏模式无泄漏 |
| P8 | 完成（主机范围） | 40 项针对性 debug 检查、50 项 release 测试全部通过；构建/签名通过；手机/平板/大字体渲染和新导入曲包链路通过；真机体验待验 |

## 数据核对与保守处理

- 音阶本地实际范围为 mi 0–3、sol 2–6、la 4–8、si 7–10、re 9–13 品。C 大调与 A 自然小调共享当前指型坐标，没有按调性平移；标签直接读取各 `Pattern.positions` 极值。
- `T01` 有 `Tuplet(0,4)` 与 `Tuplet(12,2)`，分别为八分、十六分三连音；每拍 3 音的音阶以 `QUARTER / 3` 等分并有显式连音组。因此使用「时值转换 · 三连音」及「三连音」。
- 莫扎特 K265-B 原名「2 发展」，只展示为「2 变奏」，原 ID、曲包与音符保持；该曲包含密集十六分，标签按指定映射。没有合入另一个工作区的 A-r2 数据。
- 基础 12 和弦与《嘘月》4 个按法均不满足横按推导条件，继续显示原单点指法。`Bm7(no5)` 确实使用两个不同手指且中间保留空弦，不能画成横按。
- 《斜阳》五个真实局部横按由相同指号和同品数据证明：D5/A 高音10品、C♯5/G♯ 9品、E5/B 12品、F♯5/C♯ 14品均为 2～1 弦；D5/A 低音7品为 4～3 弦。保留原品位 Tag。
- 跨空弦、跨 X、同指不同品、同品不同指、指号缺失/0、异常列表等均不连线。F/Bm 只在测试夹具出现，没有新增基础练习卡。
- 指型连接虽然生成 A→B→A 路线，当前运行时没有独立、稳定的 Box 阶段/切换标记。本轮不从品位差或下一音推断换把，保守不显示方向箭头。

## 空间与提示边界

指法图保留原 `height` 参数、`ChordGeometry`、弦距与点按范围。紧凑说明叠放在原有上方留白，不参与高度分配：默认「指法：1食 · 2中 · 3无 · 4小」，有足够留白才显示第二行横按说明。空间不足省略视觉说明，完整手指/横按信息保留在原图无障碍描述。ROOTS/HIDDEN 不提供指法或横按信息。底部 BPM 与「开始跟练」沿用原布局。

## 验证与交付

40 项针对性 debug 检查与 50 项 release 测试全部通过，0 失败、0 跳过。覆盖 `ContentPresentationTest`、`TeachingDiagramTest`、`TeachingDiagramRenderTest`、`ContentPanelRenderTest`、`ChordSourceTest`、`ChordSourceRenderTest`、`WorkbenchRenderTest`、`PhraseSettingsTest`，以及 release 的全部现有测试。`lintRelease` 为 0 错误、14 条既有警告，`assembleRelease` 成功。没有整体运行历史 debug 测试集。

主机截图输出在 `.local/teaching-ui/`，采用现有 debug 练习活动与真实 Compose 组件。新增曲包测试走实际 `.gpractice` 校验、prepare/commit、来源筛选、和弦选择、试听、开始以及歌曲和声段切换；仅为测试创建新包身份，音乐文件不变，生产曲包没有改写。测试等待了曲库就绪和原子索引更新，并沿用选择卡片自动关闭内容层的现有交互。

本地正式签名包：[guitar-practice-1.0.1-v103.apk](../output/guitar-practice-1.0.1-v103.apk)，40,986,267 字节。SHA-256：`8136abcb8789ba5661d13f242193ff3fe957dca1bd7f1a28165efdb24b29b69b`。独立读取 APK 确认包名 `com.a3322505a.guitarlearning`、版本 `1.0.1 / 103`；原长期证书 SHA-256 为 `43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`，APK v2/v3 与 zipalign 验证通过。与本地 v102 比较，32 个音乐资产/原生库条目字节完全一致（排除 dexopt profile）。机器记录：[teaching-ui-verification.json](../output/teaching-ui-verification.json)。

| 场景 | 已检查的组件截图 |
| --- | --- |
| 411dp 手机 | [调性/指型](screenshots/teaching-ui/phone-scale-patterns.png)、[音域/节奏双选中](screenshots/teaching-ui/phone-scale-range-rhythm.png) |
| 360dp 手机、1.5 倍字体 | [标题与降列](screenshots/teaching-ui/narrow-large-scale-patterns.png)、[独立小节](screenshots/teaching-ui/narrow-large-scale-range-rhythm.png) |
| 1024dp 平板 | [五指型五列与底部控件](screenshots/teaching-ui/tablet-scale-patterns.png) |
| 完整/局部/高把位横按 | [四种夹具](screenshots/teaching-ui/tablet-full-barres.png)、[隐藏提示](screenshots/teaching-ui/tablet-hidden-barres.png)、[窄区大字体](screenshots/teaching-ui/narrow-large-barre.png) |
| 普通音与技巧 | [普通下一音细环](screenshots/teaching-ui/plain-next-note-ring.png)、[滑音](screenshots/teaching-ui/technique-SL01.png)、[击弦](screenshots/teaching-ui/technique-H01.png)、[勾弦](screenshots/teaching-ui/technique-P01.png) |
| 曲包筛选 | [斜阳原品位 Tag](screenshots/teaching-ui/phone-shayou-chords-dark.png) |

主机测试不证明真机音色、音频连续性或严格音画同步。初次本地交付时没有 QQ 发送、GitHub 推送或合并。

后续交付：用户要求同步 GitHub 并发包后，代码提交 `99d1232600344df8bb1b1759b8d230e42a6c4a96` 已推送至 [codex/teaching-ui-p1-p8](https://github.com/a3322505a-sys/guitar-practice/tree/codex/teaching-ui-p1-p8)，独立读取远端分支确认 SHA 一致。GitHub 返回仓库已迁移到 `a3322505a-sys/guitar-practice`。上述 APK 已于 2026-10-17 通过 QQ 发送，消息 ID `2015418011`；从该消息的私聊文件 URL 下载回读，字节数与 SHA-256 均和本地一致。记录在 `output/qq-v103-delivery.json`。本次仅同步任务分支，没有合并 main；传输完整性不替代真机体验验收。
