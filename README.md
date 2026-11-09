# 吉他练习

自用离线 Android 吉他练习 App。曲包驱动内容，统一 AlphaTab 练琴舞台；上方可折叠横置指板，主体六线谱，底部单排【BPM、播放、循环、节拍】。教学自由选择，不锁进度。

当前改造版本：App `1.4.12 / 122`，以 main 已合并的 v120 为基线；Studio UI 2.0 的 P1→P4 已实施，包含继续练习横幅、统一曲包列表与管理页、双栏练习导航和底部选段面板。支持打开/分享曲包导入、搜索/最近/收藏、继续练习与同包连续切段。

## 当前入口

- Android：`app/src/main/java/com/a3322505a/guitarlearning/`；主界面 `ui/PracticeApp.kt`，曲包管理 `ui/SongLibrary.kt`。
- 曲包导入与校验：`assets/song/SongReader.kt`、`SongRepository.kt`，曲谱/编译在 `music/score/`、`practice/compile/`。
- 曲包格式：统一读取三 JSON 音乐数据；旧五文件包的审计元数据直接忽略。制包与批量转换见 [本地三文件曲包](docs/song-package-v2.md)，历史样本仍保留在 `contracts/song-package/v1/`。
- 构建：JDK 17、Kotlin、Jetpack Compose、AlphaTab 1.8.4；请看 [构建与变体](docs/build-variants.md)。

## 文档

- [UI 设计系统](DESIGN_SYSTEM.md)、[开发执行规则](AGENTS.md)、[当前文档索引](docs/README.md)
- [曲包导入、曲包库与连续练习整合方案](docs/曲包库与连续练习-整合实施方案.md)
- [曲包 v2 制作](docs/song-package-v2.md)、[来源与许可](docs/来源与许可.md)、[Git 管理约定](docs/git-governance.md)

历史施工记录、旧截图和验收快照不再堆放在当前分支的 `docs/` 中，仍可通过 Git 历史访问。正式源码、许可、素材与构建输入不在清理范围。
