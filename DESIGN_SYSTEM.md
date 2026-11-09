# Guitar Practice · Studio UI 2.0

## 当前执行状态与入口

以 main 已合并的 v120 为基础执行。Studio UI 2.0 的 P1→P4 代码已实施，当前版本 1.4.12 / 122；具体构建和主流程结果以 PR #36 对应提交的 Android core 为准。

- 主题：`ui/Style.kt` 的 `PracticeTheme` / `PracticeSkin`；色彩：`ui/ThemeTokens.kt`、`ui/ThemeRegistry.kt`。使用现有主题切换与持久化。
- 七个组件与图标：`app/src/main/java/com/a3322505a/guitarlearning/ui/AppUiKit.kt`。
- 页面接入：`PackageLibrary.kt` 的搜索/横幅/筛选/列表/快速换曲包；`SongLibrary.kt` 的管理与导入/帮助/外观；`PracticeStage.kt` 的主顶栏、二级段落栏、BottomSheet 和指板外围；`PracticeApp.kt` 的练习结束操作；`PracticeTransport.kt` 的速度面板与播控布局。
- 主顶栏显示曲包曲名，二级栏显示真实练习名与顺序；选段继续走既有 onPractice / selectPractice，保留 ID、顺序及状态处理。NativeScore 位于原来的固定 Box 中，未修改音频、原生谱面、曲包读写或练习编译。
- 窄屏首页内容进入同一滚动列表；短横屏的进度与四主控并排展示，指板默认折叠以保留谱面空间。普通曲包行的收藏有独立触控区，副标题仅显示作者与段数。
- AppChoiceMenu / AppDialog / AppFullScreenDialog / AppIconButton 为共享辅助入口，复用已有 Material 组件；七个基础业务组件的划分保持不变。旧 PracticeAction、TransportMotif、StageIcon 入口已删除。
- Material Symbols Outlined 24px / weight 400，保存在 `app/src/main/res/drawable/ms_*.xml`；默认 20dp、紧凑 16dp、导航可 24dp。图标通过 `AppIcon` 渲染，单独操作通过 `AppIconButton` 提供 48dp 热区。装饰图标描述传 null；图标按钮提供准确的动作描述。
- 上游：https://github.com/google/material-design-icons/tree/master/symbols/android 。Apache 2.0 许可证见 `third_party/material-symbols-LICENSE.txt`。

## Tokens 与组件

普通 UI 使用语义色 `background/surfaceLow/surfaceHigh/surfaceSelected/primary/textPrimary/textSecondary/borderSubtle`，不在业务 Screen 添加硬编码颜色。保留专业音乐配色、Canvas 坐标及谱面尺寸。

| 规则 | 当前值 / 用法 |
| --- | --- |
| 文字 | 大标题 22sp，标题 16sp，正文/按钮 14sp，辅助 12sp；保留系统字体缩放 |
| 按钮 | Sm 36dp、Md 44dp、Lg 52dp 最小视觉高度；所有按钮约 48dp 有效触控区域，允许文字缩放后长高 |
| 圆角 | 内容卡片 16dp、按钮 12dp、筛选胶囊全圆角 |
| 间距 | 常用 4/8/12/16/24dp；保留专业绘制值 |
| 练习视口 | 全宽指板与谱面，外围少层边框，保留原生宿主挂载与滚动行为 |

| 组件 | 使用方式 |
| --- | --- |
| AppTopBar | 返回、页面/曲名标题、右侧操作；不承载上一段 |
| SecondaryBar | 上一段、当前段名与展开矢量、下一段，各自独立热区；onSelect 直接打开 AppBottomSheet；另一重载支持管理 Tab |
| AppButton | Primary / Secondary / Quiet；Sm / Md / Lg；禁用反馈、可选矢量图标 |
| FilterChips | 横向可滚动标签、选中索引、选择回调 |
| AppCard | Normal / Banner 变体；继续练习横幅复用 Banner |
| AppListRow | 标题、副标题、整行点击、独立 trailing 操作；长文本最多两行 |
| AppBottomSheet | 标题、关闭按钮、可组合内容；内容长列表使用 LazyColumn；跳过半展开，处理底部系统栏 |

### 接入示例

```kotlin
AppTopBar(songTitle, onBack = onBack)
SecondaryBar(sectionTitle, onSelect = { sectionSheetOpen = true },
    onPrevious = onPrevious, onNext = onNext,
    previousEnabled = hasPrevious, nextEnabled = hasNext)
if (sectionSheetOpen) AppBottomSheet("选择段落", onDismiss = { sectionSheetOpen = false }) {
    LazyColumn {
        items(practices, key = { it.key.encoded }) { practice ->
            AppListRow(practice.title, onClick = {
                onPractice(practice) // 保留原有 ID 与播放状态处理
                sectionSheetOpen = false
            })
        }
    }
}
AppCard(variant = AppCardVariant.Banner) {
    Text(continuation.title)
    AppButton("继续练习", { onPractice(continuation) }, symbol = AppSymbol.Play)
}
AppListRow(pkg.title, onClick = onPackage, subtitle = pkg.author, trailing = {
    AppIconButton(if (favorite) AppSymbol.Star else AppSymbol.StarBorder, "收藏", onFavorite)
})
```

专业 StageTransport、谱面、指板、BPM 与教学复合组件继续独立封装；普通可复用交互从 UI Kit 取用，不为组件数量强行合并专业操作。

## 1｜四条刚性补充约束（P1 前锁定，写入最终 DESIGN_SYSTEM.md）

### R1｜普通 UI 图标铁律

**需求：** 普通 UI 中彻底消除用字符拼凑的伪图标。

**规则：**

1. 导航、按钮、下拉指示、收藏、开关状态、列表右侧操作等**普通 UI 控件**，禁止使用 `<`、`>`、`‹`、`›`、`▾`、`∨`、`∧`、`⌃`、`⌄`、`▶`、`★`、`☆` 等文本或 Unicode 字符充当图标。
2. 这些图标 **100% 使用 Material Symbols 矢量资源**，经 Compose `Icon` 或共享 `AppIcon` 入口渲染。统一图标大小（默认 20dp，小号 16dp，主要导航允许 24dp）和视觉权重；优先项目内资源复用，不为此迁移框架。
3. 具体语义映射：返回→`ArrowBack`/`ChevronLeft`；上一段/下一段→`ChevronLeft`/`ChevronRight`；段落展开→`KeyboardArrowDown`；播放→`PlayArrow`；收藏→`Star`/`StarBorder`；导入→`FolderOpen`/`UploadFile`；重新开始→`RestartAlt`/`Replay`。名称以实际可用 Material Symbols 资源为准。
4. **例外：** 和弦、调号、音乐表现、谱面记号（如 `♯`、`♭`、`♮`、节奏符号）、真实用户文本和代码示例，属于内容而非普通 UI 图标，保留准确表达。

**验收：** 普通 UI 的图标均为矢量资源；没有以上字符伪图标；支持主题着色和无障碍描述；音乐记号未受破坏。

### R2｜P2 三项必须定点清除的 UI 历史问题

**需求：** 曲包库首页及管理页的三处交互语义必须修正。

**规则与验收：**

| 定点问题 | 必须采用的方式 | 验收 |
| --- | --- | --- |
| 首页搜索框下方“继续练习……”居中纯文本 | 改为带独立背景的 `AppCard` **横幅变体**，展示曲名/段落等必要上下文，提供清晰的“继续练习”行动按钮；点击进入原有练习 | 一眼可辨该区域可操作，按钮触发原有继续流程，手机窄屏文字不乱 | 
| 曲包列表 subtitle 末尾“· 选择 ›” | 从 subtitle 的字符串拼接中**彻底移除**这段伪操作文字；整行通过 `AppListRow` 点击进入/展开原有操作，右侧仅保留收藏/更多操作的 Material Symbols 图标 | subtitle 只显示内容信息，整行热区有效，右侧收藏不误触整行行为 |
| 管理页“选择曲包（导入）”左侧播放图标 | 禁止使用 `PlayArrow`，改为 `FolderOpen` / `UploadFile`；右上角“完成”收敛为统一 `sm` 紧凑按钮样式 | 文件导入入口图标语义准确，完成按钮尺寸统一，仍可完成关闭/退出 |

### R3｜P3 顶栏与段落切换物理隔离

**需求：** 消除两个左箭头挨在一起造成“返回主页”与“上一段”的误触陷阱。

**规则：**

1. `AppTopBar` **只承载返回动作与当前曲名主标题**，允许不干扰主职责的右侧常规操作；严禁把“上一段”紧挨在返回按钮旁，亦严禁二者共用左侧导航区域。
2. 顶栏下方必须使用独立 `SecondaryBar`：**上一段图标按钮 | 当前段落名称 + 下拉指示 | 下一段图标按钮**；两栏分属独立容器，明确视觉层级与触控热区。
3. 点击中间段落名称**直接打开 `AppBottomSheet`** 选择段落；选中后继续调用原有切段回调，保留正确的段落 ID、顺序与播放状态处理。
4. 在小屏和横屏下保持足够的间距及独立点击目标。专业谱面保持稳定挂载与现有滚动/播放行为。

**验收：** 返回与上一段位于不同栏；连续点击无误触；中间段落选择必经 BottomSheet；上一段/下一段/弹窗选段均正确；练习页切换后谱面仍正常。

### R4｜Compose 常规交互实现约束

**需求：** 停止业务 Screen 中反复 `Row/Box + Modifier.clickable` 临时造按钮、顶栏和可点选列表项。

**规则：**

1. 普通业务 Screen 不得继续手搓重复的交互按钮、顶部导航、段落切换栏、筛选胶囊、列表点击行和操作弹窗；必须调用 P1 的共享 UI Kit。
2. `Row` / `Box` / `Column` 仍可用于纯布局，`Modifier.clickable` 可保留在**专业音乐交互、Canvas/谱面、特殊手势或 UI Kit 内部**。新出现的通用可点击控件要抽回共享组件。
3. 业务特殊界面允许复合组件，不要求整款 App 仅由七种组件拼装；反对为了遵守组件数量，把专业操作强塞进 `AppButton`。
4. 若某通用控件已有项目内成熟实现，直接包装或整理为统一入口，优先消除重复，不为“原子化”制造复杂抽象。

**验收：** 业务 Screen 通过共享组件构造普通交互；保留必要布局原语及专业交互；无重复的自制顶栏/通用按钮实现。

## 分阶段验收

- P1：七个组件与现有主题共用，初始 P1 已通过 Android core；后续完整迁移继续检查相同主题持久化主流程。
- P2：已接入继续练习横幅、整行曲包点击、独立收藏、FolderOpen 导入图标与 Sm 完成按钮；导入和曲包读取仍使用原调用链。
- P3：已拆分顶栏和段落栏，BottomSheet 直接选段；既有 UnifiedStageRenderTest 覆盖返回/相邻切段/面板选段/播放/大字体/横竖屏和三套主题。NativeScore 快滚恢复源码保持原样，触控与真机声音待设备验收。
- P4：已清理旧按钮/图标入口与页面伪图标，统一常规菜单和弹窗，保留谱面/指板手势与专业播控复合组件；沿用现有 CI 并运行既有 UI 主流程。

## 后续开发约束

新增或修改页面前，先读取本文件，沿用 PracticeTheme/ThemeTokens 与七个共享 UI 组件。普通导航、按钮、下拉、收藏等图标强制使用 Material Symbols 矢量资源，禁止字符伪图标。普通 Screen 不得手搓重复的可点击按钮/顶栏；专业谱面、指板与乐理符号豁免。练习页主顶栏与段落切换栏必须分离，段落选择使用 BottomSheet。以最小修改范围保持原有音乐业务正常。
