# 当前开发入口

- 本轮本地开发工作区：`C:\Users\b3322\Documents\Codex\projects\guitar-practice\work\ui-density-v99`，V3.1 实施分支 `codex/teaching-v3-1`。目录名保留历史名称，不能据此推断源码版本。执行前仍需检查实时 Git 状态与活动任务。
- `guitar-practice` 根工作树保留 v82 未提交修改；`001` 是历史工程并仍提供本机工具链，均不作为当前 App 的修改基线。不要覆盖、重置或删除这些保留内容。
- 本轮从 v103 `e859b47` 增量实施 V3.1，版本 `1.1.0 / 105`。唯一生效需求见 [教学体系 V3.1](docs/教学体系-V3.1.md)，验收事实见 [实施记录](docs/教学体系-V3.1-实施记录.md)；历史教学 UI 的 P1–P8 不是本轮阶段编号。
- 音乐语义入口为 `music/Theory.kt`、`LessonMetadata.kt` 与 `music/score/`；曲包使用 1.1 可选教学能力和单一 `SongCodec`/`PracticeCompiler`。`Tonality/Chord/Sequence` 是当前 UI 和确定性练习生成的适配，不应再注册旧 Kotlin 歌曲表。
- 内置和弦及首批课程的编写入口为 `scripts/build-teaching-v31.py`，生成 `TeachingVoicings.kt` 和 `teaching-v31.gpractice`；修改后重建并用独立审计器校验。旧 SevenBlocks/Kernel 源码只在 debug 回归夹具，正式运行读取迁移曲包。
- UI 协调入口是 `MainActivity` → `ui/PracticeApp.kt`。保留单一 alphaTab 播放/谱面路径、曲包稳定 ID、主题设置、包名、长期签名和用户数据；不为整理而修改业务逻辑。
- 未提交或不包含在当前基线中的工作按 [整理记录](docs/工作区整理-1.0.0.md) 保留。长期 compare 变体、v97 回退与莫扎特 A-r2 不得当作已合入当前实现，也不要自行合并或删除。
- 构建、协议、文档入口见 [开发索引](docs/README.md)，Git 发布规范见 [Git 约定](docs/git-governance.md)。按改动风险做必要验证，区分构建、传输完整性与真机体验。
- 清理前先核实工作树、分支关系、未提交文件和其他活动 Agent；远程删除与 Tag 创建须有单独授权。不重写历史、不强推，不删除共享工具链、签名材料或未知用途文件。
