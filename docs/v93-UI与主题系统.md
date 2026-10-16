# v93：统一 UI 骨架与主题换肤

基于 v92 `8602b94`；本次工作目录为 `guitar-practice/work/ui-skins-v93`，分支 `codex/ui-skins-v93`。
主目录未提交的 v82 排版实验与其他工作树保留。版本为 v93 / `4.1.0-local45-skins`，沿用包名和长期签名。

## 使用

- 右上齿轮 → **主题 / 外观**；练习配置 → **外观**；GP 页面齿轮也可直接进入。
- 点击预览卡立即应用；当前主题有文字与勾选状态，重启后恢复。列表末尾可恢复默认。
- 默认森林绿：背景 `#F4F6F8`、白色卡片、主色 `#057A55`、点缀 `#F59E0B`。
- 另有豆沙粉、樱花粉、莓果粉、暖米白、极简灰、鼠尾草绿、深海蓝、墨绿、雾紫、珊瑚粉。

## 统一结构

顶部曲目摘要、模式分段、设置使用统一的 44dp 工具列；空间足够时合为一行，窄窗口分行。
指板、六线谱、和弦和 GP 内容共享白色主卡片。和弦导航、试听与切换说明收进卡片 Header，
状态和拍点固定在卡片内。底部统一速度、拍号、次级操作与主练习按钮，保留原有播放状态行为。
圆角分为 20 / 14 / 12dp，结构边框为 1dp，阴影为 1dp；颜色、形状与层级均从主题读取。
移除糖果色、背景渐变与旧 Candy 组件分支。

## Theme / Skin API

| 文件 | 职责 |
| --- | --- |
| `ui/ThemeTokens.kt` | `ThemeTokens` 颜色语义、`ThemeMetrics` 圆角/边框/阴影/按钮/分段规则、`AppThemeSpec` |
| `ui/ThemeRegistry.kt` | 本地内置主题注册表及未知 ID 的默认回退；新增配色只在此注册 |
| `ui/ThemeManager.kt` | 当前主题、选择/恢复/读取/通知；独立 `practice_appearance` 偏好，不触碰练习设置 |
| `ui/Style.kt` | `PracticeTheme`、`PracticeSkin`、`PracticeContentCard`、`DiagramContainer`、统一主按钮 |
| `ui/ThemePreviewCard.kt` | 主题色板、关键组件预览、已选状态与恢复默认 |
| `alphatab/NativeScore.kt` | 将主题颜色投影至固定版本 SDK 的谱面资源、背景及播放/选择光标 |

所有主题共享相同的布局和交互；主题切换不进入内容/速度修改分支，不调用播放停止或重新载入曲目。
主题管理器用 SharedPreferences 监听同步教学和 GP 两个 Activity；离开时注销监听。
自绘 Canvas 在组合期间取得 token 后传入绘制函数，避免静态颜色和无法刷新的缓存。
alphaTab 只更新视觉资源并重绘，保留原始 Score/MIDI、当前播放器与时间轴；指板/TAB 切换保留同一原生视图。

## 验证

43 项针对性检查通过（按过滤批次运行，最终去重结果为 43/43）：主题注册及管理 6 项、主题界面 4 项、原生谱面主题 2 项、工作台 6 项、来源和弦 2 项、原生光标 2 项、原生谱面兼容 5 项、alphaTab 迁移 7 项、小屏/大字体/播放/平板布局 9 项。

覆盖主题即时切换、重启恢复、跨 Activity 同步、默认回退、11 套主题的文字/音点对比度；同一 ScoreRenderer 和 Score 连续换肤后 SVG 颜色更新，MIDI 的所有事件字段保持一致。播放中的外观切换保留原 Session；速度拖动仍仅在松手提交，和弦来源/逐弦/试听、提示、整卡切换、TAB 滚动与小屏布局保持原行为。

配置半屏露出指板时发现的 12dp 裁切已修复：改为在 DiagramContainer 内部取得可用高度。平板顶栏检查按新要求核对 44dp；模式文字按实际字体测量，必要时在分段槽内横向滚动。原生谱面视图保持固定父容器和调用位置，避免 alphaTab 因 detach 销毁接口。

`lintDebug`：0 错误 / 16 条建议（目标 API、备份配置、KTX 写法）；`assembleRelease` 与 `compileP0Kotlin` 通过。
只改固定 SDK 的视觉资源，并调用公开设置刷新：已核对 `1.8.4` 在播放模式不变时复用播放器、光标和处理器，不重新加载 MIDI。生产环境的 lazy loading 保持开启。

交付：[v93 签名 APK](../../../output/guitar-practice-4.1.0-local45-skins-v93.apk)。包名与 v92 一致，版本号升至 93，长期证书 SHA-256 为 `43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`；v2/v3 签名和 zipalign 检查通过。APK SHA-256：`80c7a3f3beaf851bf901bd3fa97f1ab74ee3963ec1b1c758f3bbcad578b1485f`。
打包后的 SoundFont 仍为 v92 的 `02ab9f358d1d79906e07ba8eb1d08993addfa3f23a9aaebea51e4589379a2bf8`，没有更换音色或播放器。构建、签名及按测试名去重的明细保存在本地 `.local/` 与 `output/v93-verification.json`。

预览：[默认手机](screenshots/v93/forest-phone.png)、[横屏谱面](screenshots/v93/forest-landscape-tab.png)、[豆沙粉主题选择](screenshots/v93/appearance-dusty.png)、[半屏配置](screenshots/v93/phone-partial-content.png)、[平板大字体](screenshots/v93/tablet-large-text-side.png)。截图来自 Robolectric 的保留 UI 测试夹具；原生 alphaTab 另做 SVG/数据检查。没有连接 adb 真机，覆盖安装、原生 GP 页面实际操作、连续音频和音画同步仍待真机验收。

实现依据：[Compose 自定义设计系统](https://developer.android.com/develop/ui/compose/designsystems/custom)，
alphaTab 资源接口另以本地固定 `1.8.4` AAR 检查。
