# v95：Gemini Studio Deck

基线为 `codex/ui-skins-v93` / `c8b7809c0d167071368b2d57e06fbbcc65f306d0`，本版独立分支 `codex/gemini-studio-deck-v95`。
版本为 `95 / 4.2.0-local47-studio`，沿用原包名、长期签名与用户数据。

## 设计与实现

Gemini 4 Argon 网页任务模式主导视觉与布局，收到 v93 的 14 个重点源码文件及六张现状截图后，交付 Luthier Stage & Studio Deck 方案与 11 文件完整补丁。Codex 负责本地集成、工程修复与验证。本轮仅完成这一版。

- 音阶、和弦、短句作为一级模式直接进入；GP、曲库与管理独立于指板/六线谱视图切换。
- 手机竖屏使用上部模式与摘要、中央舞台、底部播控；横屏与平板使用左侧 Studio Deck、右侧全高舞台，移除旧 240dp 静态提示侧卡。
- 配置占用宽度后重新计算主区布局；窄主区退回上下结构，避免左控台与右配置面板挤掉指板。
- 根音使用深主色，普通音使用柔和底色，当前音使用强调色；增加实际显示品位范围内的辅助品标。保留全部 11 套主题、原主题 ID、未知值回退与跨 Activity 持久化。
- NativeScore 在主工作台与 GP 页面均固定挂载于根容器，只将舞台占位矩形映射为位置和尺寸；切视图和布局不切换它的父容器。原引擎、lazy loading、来源适配、音乐数据和偏好键继续沿用。

Codex 的工程收尾包括辅助文字对比度、窄导航换行、窄控台大字体下的速度布局与完整主按钮，以及播放进度和短句提示变化时的舞台高度稳定。短横屏左侧播控区固定可见，上方导航独立滚动；大字体和弦页滚动整个卡片，保留完整和弦。原生 Android 小节光标绘制在谱线上方，因此将原本不透明的主题高亮改为透明覆盖，避免新窄舞台中的首小节音符被遮盖。

旧测试中固定顶底布局、重复标题和粉色常量的断言按新导航与主题语义调整，保留功能、播放和数据检查。短横屏控台与大字体和弦页的测试明确滚动实际纵向容器，避免误操作内层横向分页器。另修复了测试替换真实页面时与初始曲包导入并发写入的等待时序。

## 验证与交付

2026-10-15 本地执行结果：

| 检查 | 结果 |
| --- | --- |
| `testDebugUnitTest` | 153 项，152 通过，0 失败，1 项原有跳过（独立本地 GP/PDF 审计素材测试） |
| `lintDebug` | 通过；0 错误，17 条警告（DataExtractionRules、ModifierParameter、OldTargetApi、UseKtx） |
| `assembleRelease`、`:app:compileP0Kotlin` | 通过；与单测/Lint 同轮构建共 4m |
| `scripts/test_song_assets.py` | 4 项通过 |
| `scripts/test_song_packages.py` | 13 项通过；jsonschema 仅安装至本地忽略目录 |
| UI 回归覆盖 | 直接模式导航、音阶/和弦/短句、指板/TAB、播放阶段与固定画布、来源和弦/调弦/Capo、速度浮层、配置退让、11 套主题与重建持久化 |
| 签名与对齐 | 原长期证书 SHA-256 匹配，APK v2/v3 签名和 zipalign 校验通过 |

首轮整套回归曾出现 14 项失败：包含旧布局/固定颜色断言，以及实际的对比度、窄控件触摸尺寸和播放时画布偏移问题。修复实现并更新布局断言后，上表完整重跑通过，保留对既有功能和数据的回归检查。

已人工查看下列真实 Robolectric 渲染结果；它们用于检查布局和视觉层级，不代表原生 alphaTab、实际音频或真机操作验收：

- [手机竖屏](v95/phone.png)、[横屏 TAB](v95/landscape-tab.png)、[平板工作台](v95/tablet.png)
- [平板主题配置](v95/tablet-settings.png)、[320dp / 1.3 倍字体完整和弦与主按钮](v95/small-large-text.png)

安装包：`output/guitar-practice-4.2.0-local47-studio-v95.apk`，SHA-256：

```text
b47fc9c32cab56ab021cf67bb6bffc14e1a0aff0f6f9869181c3b78a9f2e2584
```

沿用证书 SHA-256：`43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`。已在既有 API28 x86 软件模拟器使用 `adb install -r` 从 v93 更新到 v95，保留首次安装时间与设置；没有卸载或清空数据。最终包再次覆盖安装后回读 `base.apk`，SHA-256 与交付文件一致。

正式入口实际启动、音阶/和弦/短句直接切换、指板与六线谱互切并自动旋转、alphaTab 原生排谱完成、透明高亮下六根谱线和品位数字显示、短横屏主按钮固定可见已通过现场截图核对：[原生手机页](v95/native-phone.png)、[原生和弦](v95/native-chord.png)、[原生短句](v95/native-phrase.png)、[原生横屏 TAB](v95/native-tab.png)。小屏和弦/短句图卡沿用整卡纵向滚动查看下半部。软件模拟器冷启动、原生库加载与首次排谱较慢，启动等待命令曾超时，但后续已实际显示并就绪；不能据此衡量真机性能。

[原生 GP 曲库](v95/native-gp-library.png) 入口已实际打开；更新前保存的 GP 文件、最近曲目、75% 速度与第 53–56 次经过选段均保留，页面可继续练习。

代码提交 `2853b2897a04009b9e29c354a5d17b32d353e17c` 的 [GitHub Android core CI](https://github.com/a3322505a-sys/guitar-practice-private/actions/runs/38754028582) 已全部通过。CI 执行相同的单测、Lint、Release/P0 编译和 Python 资产/曲包检查，远端仅上传 unsigned APK；正式交付使用上述本地长期签名包。后续交付文档提交不改变 App 源码，使用 `[skip ci]` 避免重复构建。

仍需人工在真机确认：整体视觉偏好、横竖屏和不同系统字体的手感、连续音频/节拍器听感、谱面光标严格同步，以及实际常用 GP 文件的导入与长时间跟练。软件模拟器和自动化回归不能替代这些验收。

原始 Gemini 归档、请求与本地过程日志仅保存在忽略目录 `.local/gemini-redesign-20261015/`，不提交私人对话和签名材料。
