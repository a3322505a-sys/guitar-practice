# v99 UI 密度收口

基于上一轮 v98 工作台提交 `51eccb3`；仅改 Compose 布局、入口文案、相关 UI 测试及交付版本号。播放引擎、音乐数据、曲包、焦点索引与谱面挂载方式保持原实现。

- 顶部内容胶囊和视图操作始终同一行；窄窗口将偏好及更多入口合为齿轮菜单。
- 联动指板顶部将上一步、听当前、下一步合为浅色胶囊，右侧同一行保留小节号和标注菜单。删除音位/虚线图例；隐藏提示移至“隐藏 / 盲弹”菜单项。普通音阶和和弦指板也保留提示菜单，避免底部入口移除后无法恢复提示。
- 底部常态只保留听示范和开始跟练。暂停、继续、结束及重来沿用原状态逻辑。
- 和弦与五指型使用 48dp 起的紧凑按钮、6dp 间隔，按可用宽度安排 3–5 列；歌曲列表不改。音阶的调性和把位指型分组显示。
- 标注偏好沿用原键，不重置用户选择；标注模式与隐藏提示状态继续分别保存，恢复提示使用菜单内的“显示提示”。

交付版本为 `99 / 4.3.1-local51-density`。沿用上一轮并存验收包身份 `com.a3322505a.guitarlearning.tablet98`（版本名带 `-review`），可覆盖上一轮联动验收包；默认正式构建仍保留原应用 ID。沿用长期签名。

## 验证与交付

- 复用现有回归：160 项，159 通过、1 项原有跳过。最后一次网格列数修订后，重跑内容抽屉与平板侧栏的两项 UI 检查；主机截图人工复核。没有新增音乐或播放测试门槛。
- 验收发行构建成功；最终验收包 lint 为 0 错误、14 条既有警告。长期证书 SHA-256 `43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`，APK v2/v3 签名和 zipalign 检查通过。
- 与 v98 验收包逐项比较 34 个资产/原生库文件；仅 AGP 生成的两个 dexopt baseline profile 改变，曲包、SoundFont 和原生库字节不变。音乐/音频/练习内核与源资产无源码差异。
- 主机渲染：[手机主界面](screenshots/v99/phone-workbench-host.png)、[调性及五指型](screenshots/v99/phone-scale-content-host.png)、[三列和弦](screenshots/v99/phone-chord-content-host.png)、[联动工具栏](screenshots/v99/phone-linked-toolbar-host.png)、[标注菜单](screenshots/v99/phone-label-menu-host.png)、[宽窗口四/五列网格](screenshots/v99/tablet-chord-grids-host.png)。联动组件截图直接使用生产 Compose 组件和既有短句数据；主机环境未加载原生谱面引擎，不代表 native 控件操作证明。
- API-28 软件模拟器持续出现 System UI ANR，重启后仍复现；候选安装和无障碍查询超时，不能据此确认最终包已安装或界面运行通过。没有将候选截图作为最终交付证据。真机安装、交互、音频和同步验收待用户确认。

交付包：[`guitar-practice-4.3.1-local51-density-review-v99.apk`](../output/guitar-practice-4.3.1-local51-density-review-v99.apk)，40,953,499 字节，SHA-256 `b023168c5463ce8f2a240789c60442cb3168bff7670eec5510553f011eee1b41`。机器记录在 `output/v99-verification.json`。

QQ 回读核对只证明传输完整。GitHub 以 v98 分支为比较基线，仅同步本次 UI 补丁，不自动合并 main。


QQ 已发送并从历史消息取得文件 URL 下载回读；消息 ID `575779270`，源/收到文件均为 40,953,499 字节，SHA-256 一致。交付源码提交为 `0f7532956f07bd79f38316d241afed779e6c5999`。本地传输记录在 `work/delivery-v99/`，不提交接收方信息和鉴权数据。
