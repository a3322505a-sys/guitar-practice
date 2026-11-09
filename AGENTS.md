# 吉他练习 App：当前开发指引

- `main` 是唯一正式发布基线。保留 Studio UI 2.0 和已预装的 13 个曲包，以最新代码为准；不按旧施工单回滚。
- 项目是个人工具，优先快速可用、简单易改。合理默认值；不做过度防御、企业级抽象、冗余校验或无目的测试。关键崩溃/数据损坏/谱面事实错误/权限风险保留必要保护。
- 曲包内容读取与存储依赖 `SongReader`、`SongRepository`、Schema、ScoreValidation 与编译链；不因简化需求删除必要音乐校验与 ID/路径安全。
- 新版曲包统一以 v2 manifest/score/practices 三文件为准。旧 v1 文件在读取时抽取核心三份数据走同一路径，旧 review、provenance、hash 和能力声明不参与导入；旧包用 `scripts/convert-song-packages-v2.py` 批量转换。
- 播放保持一个 AlphaTab 1.8.4 宿主、统一原生时间轴和声音。保留光标微调、完整预备拍、重来、整数 BPM、指板折叠及底部【BPM、播放、循环、节拍】，不恢复退役的双播放器或旧 P0 探针。
- 新交互施工依据 `docs/曲包库与连续练习-整合实施方案.md`，与既有 `PracticeApp.kt`、`SongLibrary.kt`、`PhraseSettingsStore` 直接结合，先实现 Happy Path。
- 构建与签名参考 `docs/build-variants.md`；保持正式包名、长期签名、原生音源与许可证。未真实运行的 CI、构建、APK 或真机结果不得声明已通过。
- 不直接改写 Git 历史；删除历史文档和截图只改变当前分支文件树，不删业务代码、测试夹具、构建脚本或第三方授权文件。不再向 Git 追加入库旧截图和每版独立施工方案。

- UI 新增/修改先读根目录 `DESIGN_SYSTEM.md`：复用现有主题和七个共享组件；普通图标必须用 Material Symbols；专业谱面/指板/乐理豁免。已接入曲包库/管理/练习页，保持顶栏与段落栏分离、BottomSheet 直接选段，并复用共享菜单/弹窗辅助入口。

- 练习速度统一 20～240 BPM；保存值优先，其次曲包/练习初始值，默认 60。原谱速度曲线按起点固定倍率播放；引擎范围外仅临时缩放 MIDI，不重绘或改写源谱。
- 正式发布统一 `release` / `com.a3322505a.guitarlearning` / `吉他练习`，只复用既有正式证书；debug 独立 `.debug`。不得自行换证书、包名、版本格式。
- `app/build.gradle.kts` 是唯一版本值来源：`versionName=X.Y.Z`，`versionCode` 严格递增；日常修复升 PATCH。正式文件名由 Gradle 生成 `GuitarPractice-X.Y.Z.apk`；无签名检查包带 `-unsigned`。
- 流程：任务分支修改 → 核心 CI 通过 → 合并 main → main CI 用 Secrets 签署 → 正式 APK 上传 Google Drive【APK】。只推荐一份正式包。首次从 standalone 迁移可卸载旧包，此后固定覆盖更新。
- 内置曲包打包来源固定 `app/src/main/assets/song-packages/`；同 packageId 按 revision 更新，不在 CI 抓取整个网盘。具体构建/签名与一次性 Secrets 设置见 `docs/build-variants.md`。
