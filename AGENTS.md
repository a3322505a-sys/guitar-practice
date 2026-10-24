# 当前开发入口：V114

- 仓库 `a3322505a-sys/guitar-practice`，分支 `codex/v114-count-in-cleanup`，基线 v113 `70fb07b9c63d580793ec4f587c98986694a0998f`；版本 `1.4.4 / 114`。
- 当前施工与验收依据：[V114 执行规范](docs/V114-执行规范.md)、[实施与交付](docs/V114-实施与交付.md)、[引用审计](docs/V114-源码引用审计.md)。旧 v107/v108/v112/v113 文档记录当时事实，不再作为“当前入口”。
- 主路径 `MainActivity → ui/PracticeApp → AlphaPlayback / NativeScore`。统一曲包驱动；lesson/piece 从已有 teaching/lesson/practices 推导，不修改 Schema 或曲包生成协议。
- 只保留一个稳定 AlphaTabView 挂载点，弹窗、曲包库、指板折叠、横竖屏切换不得另建播放器/谱面路径。保留光标平滑及全局 ±500 ms 微调、整数 BPM、四键【BPM/播放/循环/节拍】、↺、红黄绿灯、三主题和折叠指板。
- 使用 alphaTab 1.8.4 原生声音和预备拍。准备音量独立于正式 click 开关及其旧偏好；唯一音量出口 `AlphaPlayback.applyMix`。灯只观察原生事件；禁止私有字段反射、MIDI 首拍注入、第二音频输出或人工填拍计时器。
- 缺失曲目返回不可播放的明确占位，`resolve` 抛出 MISSING_PRACTICE；不得替换成不相关教材。保留并发代际、音频焦点、StrictJson、Schema、ScoreValidation、PracticeCompiler、ID/路径/存储保护。
- `LegacySequenceAdapter` 仍服务试听和确定性练习的 Sequence 转换，必须保留；词典、原始 SoundFont 制作素材、main 的 sonivox.sf2 和第三方许可证必须保留。
- 历史 Debug 播放器、旧 TAB、P0 探针和其调查脚本已退役。debug 使用同一原生实现；UiTestActivity 仅为现代 Compose JVM 测试宿主。不得恢复旧实现来绕过回归。
- 内置曲包编写入口 `scripts/build-teaching-v31.py`、`scripts/song_package/daily_practice.py`；音乐事实在 music/score 与现有编译链，教材渐进呈现、练习自由选择。
- 构建/签名见 [变体文档](docs/build-variants.md)。保留 release/debug/compare/standalone，不新增第三方依赖、不变更引擎或 Gradle 依赖。standalone 沿用已交付专用证书，正式 appId/长期证书不变。
- 执行完整 Debug 编译/测试/lint/组装、compare 测试/lint/组装、standalone lint/组装、release 组装，以及 Python 曲包/原生数据与实际 APK DEX/PCM 隔离审计。必须区分 CI、主机 PCM 和真机听感；无真实 Android 设备时明确写“待真机验收”。
- 修改前检查 Git 状态、分支/工作树和其他活动 Agent；只操作独立工作树，不重置用户文件、不强推、不改写远程历史。远程删除与 Tag 另需授权。共享工具链、签名材料及未知用途文件不得清理。
- 历史截图作为验收证据保留，不进入 APK；无关文档整理不拖延已通过核心检查的 APK 交付。
