# 当前开发入口

- V4 练琴参数补丁在 `codex/v4-playback-controls`，基于 `ee6140a`，版本 `1.4.1 / 111`；光标显示微调、整数目标 BPM、主题开关填色见 [v111 补丁记录](docs/V4-练琴界面补丁-v111.md)。单一 AlphaTabView 挂载点及曲包数据/协议保持。

- 当前 UI 施工为 `codex/unified-package-stage`，基于最新 V4 + 音量增强 `9d06743`，版本 `1.4.0 / 110`。当前权威要求见 [统一曲包驱动施工规范](docs/统一曲包驱动施工规范-20261022.md)。用户追加三套配色：青绿晨光（默认）、暖杏木色、靛蓝夜练。入口仅「曲包库 → 练琴舞台」，上方可折叠指板、AlphaTab 主谱面、底部 BPM/播放/循环/节拍；下述旧双工作台与词典布局不再作为当前 UI 要求。

- 当前 V4 UI 重构在 `codex/v4-studio-ui`，基于 `c5e6355`，版本 `1.3.0 / 108`；四阶段与验证见 [V4 实施记录](docs/V4-UI-实施记录.md)。保留原生数据与播放契约，新增模态输入屏蔽；下述 v107 信息作为重构基线查阅。

- 本轮本地开发工作区：`C:\Users\b3322\Documents\Codex\projects\guitar-practice\work\native-teaching-v106`，分支 `codex/native-teaching-v106`。执行前仍需检查实时 Git 状态与活动任务。
- `guitar-practice` 根工作树保留 v82 未提交修改；`001` 是历史工程并仍提供本机工具链，均不作为当前 App 的修改基线。不要覆盖、重置或删除这些保留内容。
- 当前从 v106 `03431ea` 继续落实用户 02.md，版本 `1.2.0 / 107`；需求、实现与当前验证见 [V107 记录](docs/V3.1-v107-实施记录.md)。v106 从 V105 `5afd5f8` 原生重制。v106 曾授权废除此前旧数据兼容；v107 保留 v106 原生 ID、曲库存储根与设置关联，不恢复或再次删除已替换的旧兼容链。v106 范围见 [原生数据记录](docs/原生教学数据-v106.md)，历史需求见 [教学体系 V3.1](docs/教学体系-V3.1.md)，V105 历史验收见 [实施记录](docs/教学体系-V3.1-实施记录.md)；历史教学 UI 的 P1–P8 不是本轮阶段编号。
- 音乐语义入口为 `music/Theory.kt`、`LessonMetadata.kt` 与 `music/score/`；曲包使用 1.1 可选教学能力和单一 `SongCodec`/`PracticeCompiler`。`Tonality/Chord/Sequence` 是当前 UI 和确定性练习生成的适配，不应再注册旧 Kotlin 歌曲表。
- 内置和弦、四模块课程与三个示范曲包的编写入口为 `scripts/build-teaching-v31.py`，生成 `TeachingVoicings.kt`、`NativeScalePatterns.kt` 与五个原生曲包；每日套曲的逐音编写源是 `song_package/daily_practice.py`，完整理论/封闭配法和位置查询在 `music/Dictionary.kt`；修改后重建并用独立审计器校验。旧 SevenBlocks/Kernel、转换器和旧 ID 映射已删除，不再恢复或追加。
- UI 协调入口是 `MainActivity` → `ui/PracticeApp.kt`。保留单一 alphaTab 播放/谱面路径、当前原生曲包 ID、主题设置、包名、长期签名和用户数据；不为整理而修改业务逻辑。
- 未提交或不包含在当前基线中的工作按 [整理记录](docs/工作区整理-1.0.0.md) 保留。长期 compare 配置已按本轮交付要求带入；v97 回退与莫扎特 A-r2 不得当作已合入当前实现，也不要自行合并或删除。
- 构建、协议、文档入口见 [开发索引](docs/README.md)，Git 发布规范见 [Git 约定](docs/git-governance.md)。按改动风险做必要验证，区分构建、传输完整性与真机体验。
- 清理前先核实工作树、分支关系、未提交文件和其他活动 Agent；远程删除与 Tag 创建须有单独授权。不重写历史、不强推，不删除共享工具链、签名材料或未知用途文件。
