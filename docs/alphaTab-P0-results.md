# alphaTab P0 验证记录

2026-10-14，分支 `codex/alphatab-p0`，远端基线 `09995e6`（v81）。

**当前结论：主机侧解析、SVG 排谱、44.1 kHz 离线音频合成及 Android APK 构建通过；Android 模拟器未成功启动，尚未安装运行本测试包。P0 运行验收未完成，不进入 P1–P5。**

## 已交付内容

- [功能保留 / 替换 / 迁移清单](alphaTab-P0-preservation.md)。原有工作台、指板、音阶、和弦教学、短句、曲包、设置和签名均保留。
- Android 原生 alphaTab 验证入口：`app/src/p0/`，只进入 `p0` 构建，原 `MainActivity` 入口仍可用。
- P0 测试包：`output/guitar-practice-4.0.0-local35-alphatab-p0-v83.apk`，75,048,109 字节；SHA-256：`27e73cdc8ecfc13581cb7e3fb4fa806d1ba76da24618a37346f93d3b0c5c0e83`。
- 包名仍为 `com.a3322505a.guitarlearning`，versionCode 83；已有长期证书 SHA-256 `43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`，签名 / 对齐校验通过。此 APK 只用于 P0；**未证明可在 Android 上启动**。
- 本地证据：[格式试读](../work/evidence/host/desktop-formats.json)、[合成与排谱数据](../work/evidence/host/fixtures.json)、[主机谱面预览](../work/evidence/host/notation-preview.html)、[预览 PNG](../work/evidence/host/notation-preview.png)。这些是主机输出，不能当作 Android 截图。
- 本地合成试听片段：[GP5](../work/evidence/host/simple.gp5.wav)、[GPX](../work/evidence/host/sample.gpx.wav)、[GP](../work/evidence/host/complex.gp.wav)。均为 alphaTab 自身合成器输出的双声道 44.1 kHz WAV，并非从 GP 内嵌音轨播放；只导出所选吉他通道。尚无真人音色听感验收。

原谱和私有测试素材只放在忽略的 `work/` 目录，不提交 Git。测试 APK 含三个用户本地样本，不作为公开发行包。

## 本地 GP 优先验证

从 `C:\Users\b3322\Desktop\谱子` 找到 53 个文件，均使用实际 Android AAR 内的 `ScoreLoader` 解析并生成 MIDI：

| 输入 | 样本数 | 已证实 | 尚未证实 |
| --- | ---: | --- | --- |
| GP5 | 12 | 全部解析 / MIDI 生成成功；选定样本 SVG 排谱和离线吉他 PCM 通过 | 全部节奏、奏法逐项原谱审核；Android 显示 / 发声 |
| GPX | 14 | 全部解析 / MIDI 生成成功；选定样本 SVG 排谱和离线吉他 PCM 通过 | 同上 |
| `.gp` ZIP / GPIF | 27 | 全部解析 / MIDI 生成成功；吉他轨的书面轨 / 谱表 / 小节 / 声部 / 拍内索引 / 弦 / 品事件与 Python 独立读取 GPIF 的签名全部一致 | 反复实际播放顺序、所有奏法、实际音高 / 严格音画同步 |

27 份 `.gp` 的元数据含 1 份 GP7，以及 GP8 的 8.0.0 / 8.0.2 / 8.1.0 / 8.1.3 / 8.1.4 输出。这是这些具体文件的证据，**不承诺所有 GP8 文件、音源和功能兼容**。

选定素材：

| 样本 | 原始本地文件 | 覆盖 |
| --- | --- | --- |
| GP5 / GPX | 海底谭 / 花水电车版主音吉他 / ウミユリ海底譚 ver.2 guitar 2 | 单吉他入口、休止、死音、P.M.；谱中第 54 小节也有两个发声声部，不能把整个文件称为纯单声部 |
| GP | 盗作 / 盗作.gp，Electric Guitar 1（源轨索引 3） | 第 23 小节两个发声声部；调弦 `[39,44,49,54,58,63]`，比标准低半音；复杂谱面、连线及奏法标记 |

首轮《思想犯》有 12 个计数差异，定位为**核对脚本过滤负品位造成的误报**：原 GPIF 在吉他轨中重复引用了 `fret=-8` 的音符。修正检查器后，alphaTab 保留的原始事件全部对应。P0 打开入口对非死音的负品位报错，要求核对源谱，不自动改成其他音位。原文件没有被修改。

## 原生接入与离线资源

- 使用 Maven Central 的 `net.alphatab:alphaTab:1.8.4`，未经修改；当前稳定发布见 [上游 v1.8.4](https://github.com/CoderLine/alphaTab/releases/tag/v1.8.4)。接入方式为官方 `AlphaTabView`，见 [Android 安装文档](https://www.alphatab.net/docs/getting-started/installation-android)。
- P0 worktree 采用 Kotlin / Compose 编译插件 2.2.20、AGP 8.10.1、Gradle 8.11.1、compileSdk 36，复用 JDK 17 / Build Tools 35.0.0，minSdk 26 / targetSdk 35 保留。工具链兼容范围见 [AGP 8.10 官方说明](https://developer.android.com/build/releases/agp-8-10-0-release-notes)。主工作目录的工具配置没有被改写。
- alphaTab 依赖的 alphaSkia 3.4.135 包含 arm64-v8a / armeabi-v7a / x86 / x86_64 本地库；尚未验证任何 ABI 的设备运行。
- 已独立检查签名 APK：包含 Bravura.otf（507,976 字节）、sonivox.sf2（1,351,896 字节）及样本；不声明 INTERNET 权限。音色 SHA-256 为 `4978cf37e7164206f2d22dc4fdf4020ffe040787210bf781e31bdc79961f59ef`。
- alphaTab 为 MPL-2.0；alphaSkia 为 BSD-3-Clause（并含上游第三方声明）；Sonivox 为 Apache-2.0；Bravura 为 SIL OFL。许可证 / 声明 / 原始源码链接随 P0 APK 放在 `assets/third-party/`。
- 强制使用 `EnabledSynthesizer`，防止 GP 内嵌的音轨替代示范合成。播放、速度、循环、预备拍、节拍器均调用 alphaTab API；P0 没有新增自建节拍 / 音频时钟。
- 仅打印 `playedBeatChanged` 的诊断音位，不把单个 beat 回调当作完整持续复音状态。后续指板接入还需结合引擎的播放 / MIDI 事件核对各声部、持续音与释放。
- alphaTab 的弦号 1 表示最低音弦；原教学指板的 1 表示最高音弦，映射必须为 `stringCount + 1 - note.string`。调弦 / capo 的完整设备验收留在 P2。

## 已完成检查与实际边界

- P0 两项主机探针通过：53 文件解析 / MIDI / 独立 GPIF 核对，以及三份实际素材的 SVG / 离线合成。SVG 已用同包 Bravura 字体在浏览器实际渲染并查看。
- 三段吉他 PCM 全部样本为有限数值，峰值和 RMS 非零；WAV 为 2 声道 / 44,100 Hz，约 8 / 8 / 9.6 秒。没有亲耳听感、实际音高或弹奏验收。
- 早期主机试验使用 22.05 kHz 导出时出现非有限 PCM；最终按 Android SDK 原生输出的 44.1 kHz 验证通过。其他采样率不在已验证能力内。
- `lintP0` 和 `assembleP0` 成功；lint 无错误，保留既有依赖 / API 等提示。新版 lint 指出的本 worktree `local.properties` 冒号转义问题已修正。
- 复用原有 CoreTest / KernelTest / PhraseSettingsTest / SongRepositoryTest，27 个测试通过；覆盖原教学与时间线基础、每曲设置及曲库行为。没有扩大成全项目设备 / 性能验收。
- 主目录原有 20 份未提交文件已记录 SHA-256 并再次核对，内容全部保留；本轮未写入用户 App 私有数据，未修改桌面原谱。
- 远端 main / PR #11 的历史检查已核实成功；本 P0 分支尚未推送，不声称已有本轮远端 CI 结果。

## 模拟器阻碍

用户改为要求自行下载 Android 模拟器验证后，已在 `D:\Temp\guitar-practice-alphatab-p0` 下载并校验：

- Android Emulator 37.2.12（官方仓库 SHA-1 校验）及 35.5.10（官方归档 SHA-256 校验）。
- Android 15 / API 35 AOSP x86_64 系统镜像（官方 SHA-1 校验），建立独立 AlphaTabP0 AVD。

本机固件虚拟化开启，但 HypervisorPresent=false，AEHD / GVM 服务均未安装，当前进程无管理员权限。两版 emulator 的软件模式启动出现 `0xC0000005`；另一次仅停在 ADB offline，未进入 Android。调整单核、软件 GPU、关闭 Vulkan、音频等仍未获得可用运行实例。**没有把 ADB offline 或进程存在当成模拟器已启动。**

Google 官方 AEHD 2.2（175,928 字节）安装包已备好，目录 `work/vendor/aehd-2.2`；目录签名核验为 Microsoft Windows Hardware Compatibility Publisher。已向用户请求允许安装并处理可能出现的 Windows 管理员确认；批准前不安装驱动。没有修改宿主代理、DNS、路由、防火墙、Windows 虚拟化功能或重启机器。

因此尚缺：APK 安装 / 启动、Android 原生 Skia 排谱、离线重启、AudioTrack 发声与进度回调、暂停 / 继续 / 循环边界、横竖屏和性能。物理设备及真人音色听感也未验证。

## 复现与后续入口

```powershell
python scripts/prepare-alphatab-p0.py
.\scripts\build-local.ps1 --no-daemon --max-workers=2 :app:testP0UnitTest --tests com.a3322505a.guitarlearning.AlphaTabProbeTest :app:lintP0 :app:assembleP0
.\scripts\sign-local.ps1 -BuildType p0
# 有已启动 Android 后：
adb -s <serial> install -r output/guitar-practice-4.0.0-local35-alphatab-p0-v83.apk
adb -s <serial> shell am start -n com.a3322505a.guitarlearning/.alphatab.AlphaTabProbeActivity
adb -s <serial> logcat -s AlphaTabP0
```

本机首次获取依赖时，Gradle 使用当前系统代理的进程参数；没有将代理写进仓库。Gradle 发行包取镜像时也核对了官方 SHA-256。私有 GP 素材不在 CI：CI 只编译 P0 入口，原有生产功能检查仍走原流程。

下一步优先解决模拟器加速并完成 P0 运行验收，再决定 P1 的 GP 持久曲库。原 `.gpractice` 与内置练习适配、指板持续复音同步、反复选段、教学迁移及旧内核清理均未施工。
