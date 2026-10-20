# V4 前端 UI/UX 重构

基线：`codex/native-teaching-v106` / `c5e6355`（v107）。实施分支：`codex/v4-studio-ui`。

## 需求与规则

按 V4 设计规范及用户补充的六项工程约束，依次完成 Phase 1–4，各阶段编译成功后独立提交。以平板持琴练习为主要场景，兼顾 360 dp 手机。完整词典与教学内容渐进呈现；不增加档案、评分或强制任务。

- 保留原生曲包、练习 ID、编译器和单一 alphaTab 时间轴。
- 不新增依赖。响应式使用现有 Compose 约束，断点为 600 / 840 dp。
- `forest_daylight` 使用指定的全部固定色值。
- 迁移真实回调；模态界面拦截遮罩和原生谱面触摸。
- 保留测试语义锚点；最终执行单测、Lint 和 Debug APK 构建。

## Phase 1：设计系统

实际类型为 `ThemeTokens` / `ThemeMetrics`。增加背景层级、乐理语义色及尺寸字段，保留旧字段映射。主题收敛为黑金录音室（默认）、暖木工坊、森林日间；废弃 ID 回退并持久化默认值。更新主题与迁移测试。

验证：`./gradlew :app:compileDebugKotlin` 通过（JDK 17，未增加依赖）。

## Phase 2：展示投影与卡片

增加 `ExerciseCardUiModel`，保留真实 PracticeKey，把标题、教学分类、版本、技巧与 BPM/小节信息分开。转换练习投影为单一分区；无边框卡片采用左侧选中条和层级背景。600/840 dp 分别切换双列/三列；标题固定 16 sp，大字体允许完整换行。

验证：`./gradlew :app:clean :app:compileDebugKotlin -Pkotlin.incremental=false` 通过。干净构建排除了当前工具链增量缓存中旧顶层声明引发的重复符号。
