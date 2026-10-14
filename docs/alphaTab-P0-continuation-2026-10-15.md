# alphaTab P0 续作记录：v86

接续任务 `01a13aee-9949-7bc1-b87d-d99b2b518199`。本轮使用已有 `codex/alphatab-p0` worktree；远端 main 仍为 `09995e6` / v81。用户明确选择暂不安装 AEHD，继续其他可行验证。

**本轮已取得 Android 软件模拟器中的安装、原生排谱、离线重开与播放事件证据；P0 的连续听谱、音色、延迟和严格音画同步仍未验收。没有进入 P1–P5。**

## Android 中实际观察的结果

成功环境为官方 Android Emulator **32.1.15 / build 10696886**、Android 9 / API 28 AOSP **x86** 镜像，`-accel off`、单核、软件 GPU。宿主未安装驱动，未修改宿主代理或网络设置。API 35 x86_64 的 35 / 37 版，以及 33.1.18 + API 28 软件模式均未启动成功；降低模拟 CPU 型号也未解决其访问异常。

| 检查 | 已取得的证据 | 边界 |
| --- | --- | --- |
| 安装与启动 | v83、v84、v85、v86 均以 `install -r` 成功；v86 已安装包回读 SHA-256 与交付包一致 | 只有模拟器 x86；没有实机 / arm64 运行证明 |
| GP5 | 原始《ウミユリ海底譚 ver.2 guitar 2.gp5》显示标准谱与 TAB；实际观察到播放高亮、自动跟随、位置 / beat 回调和 Playing → Paused → Playing | 不把回调推进当作连续音频合格 |
| GPX | 原始同名 GPX 在关闭无线 / 移动数据后重新打开；117 小节、吉他轨、音色就绪与实际谱面已观察 | 未逐项核对全部奏法的实际发声 |
| GP 8.0.2 小样本 | 原始同名 `.gp`，26,202 字节，独立 GPIF 确认第 54 小节两个含音符的声部；Android 中定位该小节并核对原谱的旋律及第二声部节奏斜线 | 这是一个具体 GP8 文件；第二声部原文含 `Slashed`，不是两个全显品位声部重叠的覆盖证据 |
| 离线资源 | Wi-Fi disabled、移动数据关闭、飞行模式广播完成、路由表为空；离线重开后仍读谱、排谱与加载音色。APK 未声明 INTERNET | 不是 GP 持久曲库验收；P0 使用随包样本 |
| 横屏与滚动 | 1280×720 原生显示；修复前滚动后谱面变空，修复后可看到第 54 小节及后续谱面 | 较大逻辑视口截图出现兼容缩放 / 黑边，调试控件与标题占空间；不能当产品横屏 / 平板验收 |

《盗作》28.9 MB 原文件仍在主机侧成功解析并核对。在本轮软件模拟的时间边界内未完成 Android 解析，已停止该实例；没有把加载状态当作成功，也没有裁改原谱。轻量样本是桌面的另一份原始 GP，不是删声部或改写后的《盗作》。

现场截图、原始日志与安装回读位于 [Android 证据](../work/evidence/android-api28/)。其中 `v86-polyphonic-scroll.png` 为正常视口的滚动修复证据，`v86-polyphonic-large-bars.png` 覆盖第 54 小节及原文斜线标记，`v86-gpx-offline.png` 为 GPX 离线显示；较大逻辑视口截图有黑边。首次启动还出现过 Android System UI 无响应弹窗。

## 本轮代码与独立核对

- P0 入口新增受限的 `p0Fixture` / `p0Preview` 启动参数，六小节窗口只控制显示。素材、alphaTab 音乐模型和完整播放时间线没有被改写。
- 补充原始轻量多声部 GP，样本准备脚本可重复选择体积最小的已确认多声部吉他文件；原大文件保留。
- 避免 Spinner 初始化再次渲染已选轨。
- Android 预览保持 `core.enableLazyLoading=true`。SDK 的 `AlphaTabRenderSurface` 回收离屏 Bitmap，并在滚回时请求重新绘制；关闭惰性渲染不会保留 `ScoreLayout` 的绘制回调，导致回收后空白。已用实际 Android 滚动复验。
- 新增独立 GPIF 音乐事实提取与探针。《盗作》所选吉他轨的 99 小节拍号 / swing 声明、943 个普通拍点的时值 / 附点 / 连音 / 休止、1,393 个弦品及实音音高、66 个延音连接匹配。6 处装饰音核对类型和音高；alphaTab 会规范化装饰音符形，未声称保留其原始符值或证明抢拍时长。
- 四份实际样本的 SVG 与 44.1 kHz PCM 主机探针通过，样本有限且峰值 / RMS 非零。它们是主机合成，不是 Android 录音或真人音色验收。
- 两项针对性主机探针分别通过；最终 v86 assemble / lint 通过，lint 0 errors / 15 warnings。复用先前 53 份解析、27 份独立 GPIF 弦品检查与 27 个旧功能测试记录，没有重新声称全项目设备回归。

本轮重新核对 53 份桌面原件 SHA-256 全部未变；主工作目录原有 20 份 v82 未提交文件 SHA-256 全部未变。P0 不访问正式曲库与练习设置；真实旧数据升级保留仍是 P5 的设备验收事项。没有推送本轮分支，没有本轮远端 CI 结果。

## 未解决的验收边界

软件模拟下 GP5 的原生 AudioTrack 为 44.1 kHz 双声道浮点流，观察到写入及播放回调；同一观察窗口出现 **4,273,383** 的 AudioTrack underrun 计数。声音连续性、听感、延迟与严格同步不能据此判为通过。尝试预编译后包管理器曾确认 v83 为 `speed`，其耗时和这个环境都不构成真机性能结论。

尚缺可实时运行的目标设备 / 加速环境中的音色与连续发声、复杂大 GP 的内存 / 性能、双显品位多声部、持续复音指板、反复选段与两轮循环、变速 / 预备拍 / 节拍器、完整生命周期和升级保真实数据核验。先完成这些必要的 P0 / 相关阶段边界，再推进 GP 持久曲库与正式统一会话；不要把本测试入口直接当作迁移后的 App。

## 测试包与复现

**v86 / 4.0.0-local38-alphatab-p0**，包名 `com.a3322505a.guitarlearning`，75,072,754 字节。

APK SHA-256：`d897df696343da4c8fa9643d9a61cc023c8a372b44ab53756caf79456e7e0f31`。

长期证书 SHA-256：`43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`。签名 / 对齐通过，安装回读一致。仅供 P0 本地验证，包含用户 GP 样本。

最终入口：[本地交付目录](../../../output/alphaTab-P0-v86/README.md)。旧 v83–v85 本地包和证据保留。

```powershell
python scripts/prepare-alphatab-p0.py
python scripts/prepare-alphatab-p0-audit.py
.\scripts\build-local.ps1 --offline --no-daemon --max-workers=2 :app:testP0UnitTest --tests com.a3322505a.guitarlearning.AlphaTabMusicalFactsTest --tests com.a3322505a.guitarlearning.AlphaTabProbeTest.simpleAndPolyphonicFixturesRenderAndSynthesizeOffline :app:assembleP0 :app:lintP0
.\scripts\sign-local.ps1 -BuildType p0
# 已启动的独立模拟器：
adb -s emulator-5558 install -r output/guitar-practice-4.0.0-local38-alphatab-p0-v86.apk
adb -s emulator-5558 shell cmd activity start-activity -n com.a3322505a.guitarlearning/.alphatab.AlphaTabProbeActivity --es p0Fixture polyphonic.gp --ez p0Preview true
```

模拟器文件在 `D:\Temp\guitar-practice-alphatab-p0`；使用 `sdk3215\emulator\emulator.exe` 和 `avd\AlphaTabP0Api28.avd`，不要把含 33.1.18 的 `sdk32\emulator` 目录误当作成功版本。官方归档 32.1.15 SHA-256 为 `8b16f7e802031c45285339fcfd3b2cebc4bf9d935cf46c6b6c5db8e7b8fe22e8`，API 28 x86 镜像官方 SHA-1 为 `ce03c42d80c0fc6dc47f6455dbee7aa275d02780`，下载均校验通过。参考：[Android 加速说明](https://developer.android.com/studio/run/emulator-acceleration)、[官方模拟器归档](https://developer.android.com/studio/emulator_archive)。

```powershell
$env:ANDROID_AVD_HOME = 'D:/Temp/guitar-practice-alphatab-p0/avd'
$env:ANDROID_HOME = 'C:/Users/b3322/Documents/Codex/projects/001/work/toolchain/android-sdk'
& 'D:/Temp/guitar-practice-alphatab-p0/sdk3215/emulator/emulator.exe' -avd AlphaTabP0Api28 -no-window -no-boot-anim -no-snapshot -accel off -gpu swiftshader_indirect -feature -Vulkan -audio wav -cores 1 -memory 2048 -port 5558
```
