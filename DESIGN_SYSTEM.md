# Guitar Practice · UI 设计速查

## 主题与组件入口

- **主题与色彩**：`ui/Style.kt`（`PracticeTheme` / `PracticeSkin`）、`ui/ThemeTokens.kt`、`ui/ThemeRegistry.kt`。普通 UI 必须使用语义色（`background`、`surfaceLow`、`surfaceHigh`、`surfaceSelected`、`primary`、`textPrimary`、`textSecondary`、`borderSubtle`），禁止在业务页面硬编码颜色；专业谱面、指板 Canvas 与乐理配色豁免。
- **共享组件**：`app/src/main/java/com/a3322505a/guitarlearning/ui/AppUiKit.kt`，提供 `AppTopBar`、`SecondaryBar`、`AppButton`（`Primary`/`Secondary`/`Quiet`；`Sm`/`Md`/`Lg`）、`FilterChips`、`AppCard`（`Normal`/`Banner`）、`AppListRow`、`AppBottomSheet`，以及辅助入口 `AppChoiceMenu`、`AppDialog`、`AppFullScreenDialog`、`AppIcon`、`AppIconButton`。
- **专业复合组件**：`StageTransport`、指板、谱面、`BpmSheet`、`VolumeSheet`、`StageCountInCue` 保持独立封装，不强行塞进通用按钮。

## 四条硬性底线

1. **普通 UI 图标必须用矢量资源**：导航、按钮、下拉、收藏、导入、开关等全部经 `AppIcon` / `AppIconButton` 使用 Material Symbols（`app/src/main/res/drawable/ms_*.xml`，默认 20dp、紧凑 16dp、导航 24dp；独立图标按钮保留 48dp 热区）。严禁用 `<`、`>`、`‹`、`›`、`▾`、`▶`、`★`、`☆` 等字符拼凑伪图标。乐理与谱面符号（如 `♯`、`♭`、`♮`）属于内容，豁免。
2. **顶栏与段落栏物理隔离**：`AppTopBar` 只放返回、曲名主标题与右侧非冲突操作（如音量）；下方独立 `SecondaryBar` 放“上一段 | 当前段落名（点击开 `AppBottomSheet` 选段） | 下一段”。严禁把“上一段”紧挨返回按钮摆放。
3. **禁止在业务页反复手搓通用控件**：常规按钮、顶栏、列表点击行、弹窗一律调用共享组件；首页“继续练习”用 `AppCard(Banner)`，曲包行整行点击且右侧收藏独立热区（不拼伪操作文字），文件导入用文件夹/上传图标而非播放图标。
4. **字号缩放与多屏适配**：遵循系统字体缩放，按钮触控区保持约 48dp；短横屏默认折叠指板并将进度条与四主控并排，优先把空间留给谱面；`NativeScore` 固定挂载不跨分支移动。

> Material Symbols Outlined（24px / weight 400）遵循 Apache 2.0 许可证，见 [third_party/material-symbols-LICENSE.txt](third_party/material-symbols-LICENSE.txt)。
