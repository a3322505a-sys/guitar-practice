# 开发与文档索引

当前开发目录为 `guitar-practice/work/native-teaching-v106`，分支 `codex/native-teaching-v106`，版本 **1.2.0 / 107**，从 v106 `03431ea` 继续作业。当前需求及验证入口见 [V107 实施记录](V3.1-v107-实施记录.md)。v106 授权、原生课程及曲包、实际验收边界见 [V106 记录](原生教学数据-v106.md)；V105 及此前的迁移要求仅作历史参考。根工作树 v82 未提交内容继续保留。

## 构建

单个 `:app` 模块，Kotlin/Compose，JDK 17，compileSdk 36 / targetSdk 35 / minSdk 26。本机脚本继续复用 `001/work/toolchain` 中的 JDK/SDK；该目录仍有效，不属于可清理的旧工程废料。其他机器按 `local.properties` 或 `ANDROID_HOME` 配置 SDK。

```powershell
.\scripts\build-local.ps1 --offline --no-daemon --max-workers=2 assembleRelease assembleDebug assembleP0
.\scripts\sign-local.ps1
```

`--offline` 要求依赖已缓存；新环境需先解析依赖。正式包继续使用 `com.a3322505a.guitarlearning` 和既有长期证书。`-PtabletReview=true` 仅保留独立验收包名/标签，版本与正式构建一致。debug、release、p0 共享当前版本，已移除 p0 的旧 `86` 覆盖。历史 v95 对照包和待合入的长期 compare 构建是不同保留工作，不在本次版本改动内。

## 当前有效资料

- [Git 分支与交付约定](git-governance.md)、[执行规则](../AGENTS.md)
- [曲包协议与编译链](../contracts/song-package/v1/README.md)、[曲包生产手册](曲包生产手册.md)、[来源与许可](来源与许可.md)
- [alphaTab 正式接入](alphaTab-正式接入-v90.md)、[release/debug 隔离记录](v96-alphaTab-release-isolation.md)
- [平板联动工作台](平板联动工作台-v98.md)、[主题同步](StudioDark与主题同步-v100.md)、[内容面板](练习内容面板-v101.md)

## 历史材料

版本验收、资产来源、截图和音乐事实记录继续保留，反映其当时的证据边界，不作为当前待办。四份已被实现替代的施工/任务方案移至 [历史方案目录](archive/plans/)，原需求及验收条款未删除；对应链接已调整。音乐内核设计和曲包协议仍有效，保留原位置。

旧版本记录中的 `output/` 路径属于其交付时的工作树，不保证当前工作树包含同一份旧 APK；旧发布包及证据仍在根 `output/` 或整理记录所列的归档中，没有为了修复历史下载链接再复制一套。

旧版本需按提交、PR 或保留记录追溯，不按文件名中的版本大小推断完整性。本轮没有创建 Git Tag；建议后续合入 main 并验证后再评估 `v1.0.0`。
