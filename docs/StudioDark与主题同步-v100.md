# v100 Studio Dark 与 PR #19 同步

当前工作台基线为 v99 `5cb6975`。将云端草稿 [PR #19](https://github.com/a3322505a-sys/guitar-practice/pull/19) 的 `f45a892` 配色合入当前分支，保留 v99 两行工具栏与紧凑内容网格；保留草稿 PR 的云端状态，没有合并 main。

新主题为 **黑金暗夜 · Studio Dark**，稳定 ID `studio_dark`，描述为“碳素黑 × 复古金 × 电光青”。

| 用途 | 颜色 |
| --- | --- |
| 外框背景 | `#14171A` |
| 卡片底色 | `#1E2328` |
| 主色 / 根音 / 按钮 | `#D99B26` |
| 辅色 / 节拍 | `#38BDF8` |
| 普通音阶填充 | `#2B323B` |
| 主文字 | `#F3F4F6` |

`PracticeTheme` 同步提供 Material 深/浅配色和默认文字前景色，裸 Text、图标、菜单、内容抽屉、偏好及帮助跟随当前主题；显式指定的辅助文字和图示文字使用同一套 Token。彩色实心按钮和根音保留深色字，保证亮色填充上的文字清晰。谱面通过既有 `applyScoreResources` 和原生换肤桥接刷新，不改 Score、MIDI、播放器或时间线。

主题入口仍为“偏好 → 外观”；窄屏从齿轮进入“练习偏好”。选中即时生效并沿用原主题设置键，重启恢复；森林绿保留默认。PR #19 的浅色主题与退役 ID 回退规则保留。

交付版本 `100 / 4.3.2-local52-dark-review`，继续使用上一轮 `com.a3322505a.guitarlearning.tablet98` 联动验收包身份和长期签名，不覆盖原正式 App。

## 验证与交付

- 18 项针对性检查全部通过：主题配色/文字与音位对比度、主题保存和重启恢复、播放中换肤保留同一会话、alphaTab SVG 资源刷新且 MIDI 事件不变、手机/平板现有工作台布局。
- 验收发行构建成功；lint 0 错误、14 条既有警告。长期证书 SHA-256 `43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`，APK v2/v3 签名与 zipalign 通过；包内 DEX 已读到 Studio Dark 和 PR #19 的四个新主题 ID。
- 主机截图：[手机和弦](screenshots/v100/phone-chord-host.png)、[内容抽屉](screenshots/v100/content-host.png)、[横屏六线谱与偏好](screenshots/v100/landscape-tab-host.png)、[平板](screenshots/v100/tablet-host.png)、[外观选择](screenshots/v100/appearance-host.png)。这些属于实际 Compose 组件主机渲染，谱面为保留的历史夹具；原生 alphaTab 谱面颜色另由 SVG 资源检查确认。
- 主文字与外框背景对比度约 16.35:1；金色文字与深色胶囊约 5.34:1；普通音位文字与填充约 11.76:1。没有以对比度检查代替截图复核。
- 与 v99 逐项比较 34 个资产和原生库文件，仅 AGP 两个 dexopt baseline profile 改变；曲包、SoundFont、原生库字节不变。音乐、音频、练习内核及原始资产无源码差异。
- 本轮未做最终 APK 的设备安装、实际试听或音画同步验收。

交付包：[guitar-practice-4.3.2-local52-dark-review-v100.apk](../output/guitar-practice-4.3.2-local52-dark-review-v100.apk)，40,953,499 字节，SHA-256 `31bfcdd6e1e223832c8cbdf9e58c088484845a6a45a6f4d5d9f058f5bb77d3c6`。机器记录在 `output/v100-verification.json`。


QQ 已发送，消息 ID `844065803`；从历史文件 URL 下载回读，源/收到文件均为 40,953,499 字节，SHA-256 一致。该记录只证明传输完整；验证记录保存在 output，接收方及鉴权信息不提交 Git。
