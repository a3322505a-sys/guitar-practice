# 吉他练习 App：开发指引

- 本项目为个人自用离线 Android 工具，优先简单、直接、好用；不做过度防御、企业级抽象或无目的测试，只保留防崩溃、防数据损坏、防谱面/节奏事实错误与路径安全检查。
- **架构红线**：保持单个 AlphaTab 1.8.4 宿主（`NativeScore` 固定挂载）与统一原生时间轴/音频输出。严禁引入第二套 `AudioTrack`/`MediaPlayer`/伪计时器、严禁反射 AlphaTab 私有字段、严禁为预备拍改写源谱显示小节、严禁修改 `app/src/main/assets/sonivox.sf2`。
- **交互基线**：保留上方可折叠指板、主体六线谱、底部四主控【BPM、播放/暂停、循环、节拍】、重新开始、预备拍与光标同步微调；练习速度统一 20～240 BPM（已存值 > 曲包建议值 > 默认 60；超出引擎倍率范围仅临时缩放 MIDI，不改写源谱）。
- **UI 约束**：改动界面前先读根目录 [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)，复用语义主题色、Material Symbols 矢量图标与共享 UI 组件。
- **曲包协议**：统一以三文件 v2（`manifest.json`、`score.json`、`practices.json`）读取；旧 v1 自动抽取三文件忽略旧审核元数据。保留 `SongReader`、`SongRepository`、Schema 与 `ScoreValidation` 校验。
- **曲包制作与预装**：新制曲包用 `scripts/song-package.py build --input <目录> --output <name>.gpractice` 直接输出 `.gpractice`（ZIP_DEFLATED，根目录平放三份 UTF-8 JSON，不套目录）；旧包可用 `scripts/convert-song-packages-v2.py` 批量转换。内置曲包固定放在 `app/src/main/assets/song-packages/`（同 `packageId` 按 `revision` 更新）。
- **包名、签名与版本**：正式包固定 `release` / `com.a3322505a.guitarlearning` / `吉他练习`，复用既有正式证书（见 [docs/build-variants.md](docs/build-variants.md)）；debug 独立 `.debug`。不得自行换包名、证书或版本格式。`app/build.gradle.kts` 是唯一版本来源（`versionName=X.Y.Z`，`versionCode` 严格递增，日常修复升 PATCH）。
- **开发与发布方式**：开始前先同步最新 `main`；改完并在本地跑通单测、Lint 与构建后，可直接提交推送到 `main`，由 GitHub Actions 自动跑全量校验并签发正式 APK（仅做高风险大实验时自行临时切分支）。
- **仓库卫生与汇报**：一次性施工单、临时日志和验收截图不提交进 Git；未实际运行的 CI、构建或真机试弹结果不得声称已通过。
