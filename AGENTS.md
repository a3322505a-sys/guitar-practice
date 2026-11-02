# 吉他练习 App：当前开发指引

- 当前开发基线是 `codex/song-package-v2`，App `1.4.5 / 115`；默认 `main` 仍是较早发布代码。开发前以目标分支的最新代码为准，按 `README.md`、`docs/README.md` 和实际实现判断，不按旧施工单回滚。
- 项目是个人工具，优先快速可用、简单易改。合理默认值；不做过度防御、企业级抽象、冗余校验或无目的测试。关键崩溃/数据损坏/谱面事实错误/权限风险保留必要保护。
- 曲包内容读取与存储依赖 `SongReader`、`SongRepository`、Schema、ScoreValidation 与编译链；不因简化需求删除必要音乐校验与 ID/路径安全。
- v2 本地制包按 `docs/song-package-v2.md` 的 manifest/score/practices 三文件执行；现有旧 v1 仅兼容输入，不能把旧的审核模板、哈希表当成新包默认必填项。
- 播放保持一个 AlphaTab 1.8.4 宿主、统一原生时间轴和声音。保留光标微调、完整预备拍、重来、整数 BPM、指板折叠及底部【BPM、播放、循环、节拍】，不恢复退役的双播放器或旧 P0 探针。
- 新交互施工依据 `docs/曲包库与连续练习-整合实施方案.md`，与既有 `PracticeApp.kt`、`SongLibrary.kt`、`PhraseSettingsStore` 直接结合，先实现 Happy Path。
- 构建与签名参考 `docs/build-variants.md`；保持正式包名、长期签名、原生音源与许可证。未真实运行的 CI、构建、APK 或真机结果不得声明已通过。
- 不直接改写 Git 历史；删除历史文档和截图只改变当前分支文件树，不删业务代码、测试夹具、构建脚本或第三方授权文件。不再向 Git 追加入库旧截图和每版独立施工方案。
