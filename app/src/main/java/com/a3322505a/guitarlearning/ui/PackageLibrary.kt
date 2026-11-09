package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.assets.song.LibraryPractice
import com.a3322505a.guitarlearning.assets.song.LibrarySnapshot

internal data class PackageCardUiModel(
    val id: String, val title: String, val practices: List<LibraryPractice>, val isTeaching: Boolean,
    val author: String = "",
)

/** Keep variants and source arrangements selectable; only explicitly hidden practices are omitted. */
internal fun packageCards(snapshot: LibrarySnapshot): List<PackageCardUiModel> = snapshot.packages.distinctBy { it.identity.packageId }.map { pkg ->
    val practices = snapshot.packagePractices[pkg.identity.packageId].orEmpty().filter { it.visibility != "hidden" }
    PackageCardUiModel(pkg.identity.packageId, pkg.title, practices, practices.any { p ->
        p.lesson != null || p.teaching?.let { t ->
            listOf(t.category, t.technique, t.use, t.explanation).any(String::isNotBlank)
        } == true
    }, pkg.author)
}

private fun recentPractice(pkg: PackageCardUiModel, recent: List<String>): LibraryPractice? =
    recent.firstNotNullOfOrNull { id -> pkg.practices.firstOrNull { it.key.encoded == id } } ?: pkg.practices.firstOrNull()

private fun orderedPackages(packages: List<PackageCardUiModel>, recent: List<String>): List<PackageCardUiModel> =
    packages.sortedBy { pkg -> recent.indexOfFirst { id -> pkg.practices.any { it.key.encoded == id } }.takeIf { it >= 0 } ?: Int.MAX_VALUE }

@Composable
internal fun PackageLibraryScreen(
    packages: List<PackageCardUiModel>, ready: Boolean, selectedPracticeId: String?,
    onPractice: (LibraryPractice) -> Unit, onManage: () -> Unit,
    recent: List<String> = emptyList(), favorites: Set<String> = emptySet(),
    onFavorite: (String) -> Unit = {}, focusedPackageId: String? = null,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val ordered = orderedPackages(packages, recent)
    val filtered = ordered.filter { pkg ->
        val matches = query.isBlank() || listOf(pkg.title, pkg.author).plus(pkg.practices.map { it.title })
            .any { it.contains(query.trim(), ignoreCase = true) }
        matches && when (category) {
            1 -> pkg.practices.any { it.key.encoded in recent }
            2 -> pkg.id in favorites
            3 -> pkg.isTeaching
            4 -> !pkg.isTeaching
            else -> true
        }
    }
    LaunchedEffect(focusedPackageId) {
        if (focusedPackageId != null) { query = ""; category = 0; expandedId = focusedPackageId }
    }
    LaunchedEffect(focusedPackageId, filtered.map { it.id }) {
        val index = filtered.indexOfFirst { it.id == focusedPackageId }
        if (index >= 0) listState.scrollToItem(index + 3) // search, continuation, filters
    }
    val availablePractices = packages.flatMap { it.practices }
    val continuation = recent.firstNotNullOfOrNull { id -> availablePractices.firstOrNull { it.key.encoded == id } }
        ?: availablePractices.firstOrNull { it.key.encoded == selectedPracticeId }
    val imported = packages.firstOrNull { it.id == focusedPackageId }
    val bannerPractice = imported?.let { recentPractice(it, recent) } ?: continuation
    val bannerPackage = packages.firstOrNull { pkg -> pkg.practices.any { it.key == bannerPractice?.key } }
    val colors = PracticeSkin.colors
    Surface(color = colors.background, modifier = Modifier.fillMaxSize().testTag("package-library")) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            AppTopBar("曲包库") {
                AppButton("管理 · 外观", onManage, Modifier.testTag("library-settings"),
                    size = AppButtonSize.Sm, style = AppButtonStyle.Quiet)
            }
            if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f).widthIn(max = 1080.dp).fillMaxWidth().align(Alignment.CenterHorizontally).testTag("library-list"),
                state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item("search") {
                    OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("搜索曲名、作者、段落") },
                        modifier = Modifier.fillMaxWidth().testTag("library-search"))
                }
                item("continuation") {
                    if (bannerPractice != null) AppCard(variant = AppCardVariant.Banner, modifier = Modifier.testTag("continue-banner")) {
                        Text(if (imported != null) "已导入" else "继续练习", style = MaterialTheme.typography.labelMedium, color = colors.primaryText)
                        Text(bannerPackage?.title.orEmpty(), style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(bannerPractice.title, style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, color = colors.textSecondary)
                        AppButton(if (imported != null) "开始练习" else "继续练习", { onPractice(bannerPractice) },
                            Modifier.fillMaxWidth().testTag("continue-practice"), symbol = AppSymbol.Play)
                    }
                }
                item("filters") { FilterChips(listOf("全部", "最近", "收藏", "教学", "歌曲"), category, { category = it }) }
                if (ready && filtered.isEmpty()) item("empty") {
                    AppCard { Text(if (packages.isEmpty()) "还没有曲包，点击“管理 · 外观”导入。" else "没有匹配曲包") }
                }
                items(filtered, key = { it.id }) { pkg ->
                    val expanded = expandedId == pkg.id
                    AppCard(contentPadding = PaddingValues(0.dp), contentSpacing = 0.dp) {
                        AppListRow(pkg.title, onClick = {
                            if (pkg.practices.size == 1) onPractice(pkg.practices.single())
                            else expandedId = if (expanded) null else pkg.id
                        }, modifier = Modifier.testTag("package-${pkg.id}"), enabled = pkg.practices.isNotEmpty(),
                            subtitle = listOf(pkg.author, "${pkg.practices.size} 段").filter(String::isNotBlank).joinToString(" · "),
                            trailing = {
                                val favorite = pkg.id in favorites
                                AppIconButton(if (favorite) AppSymbol.Star else AppSymbol.StarBorder,
                                    if (favorite) "取消收藏 ${pkg.title}" else "收藏 ${pkg.title}", { onFavorite(pkg.id) },
                                    Modifier.testTag("favorite-${pkg.id}").semantics { selected = favorite })
                            })
                        if (expanded) pkg.practices.forEachIndexed { index, practice ->
                            HorizontalDivider(color = colors.borderSubtle)
                            AppListRow(practice.title, { onPractice(practice) },
                                Modifier.testTag("practice-${practice.key.encoded}"),
                                subtitle = "${practice.suggestedTempo} BPM", selected = selectedPracticeId == practice.key.encoded,
                                leading = { Text("${index + 1}", color = colors.primaryText, style = MaterialTheme.typography.labelLarge) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuickPackageSheet(packages: List<PackageCardUiModel>, recent: List<String>, favorites: Set<String>,
    onPractice: (LibraryPractice) -> Unit, onAll: () -> Unit, onClose: () -> Unit) {
    val choices = orderedPackages(packages, recent).filter { it.id in favorites || it.practices.any { p -> p.key.encoded in recent } }
    AppBottomSheet("换曲包 · 最近与收藏", onClose) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 360.dp)) {
            if (choices.isEmpty()) item { Text("还没有最近或收藏的曲包", modifier = Modifier.padding(16.dp)) }
            items(choices, key = { it.id }) { pkg ->
                recentPractice(pkg, recent)?.let { practice ->
                    AppListRow(pkg.title, { onPractice(practice); onClose() }, Modifier.testTag("quick-${pkg.id}"), subtitle = practice.title)
                }
            }
        }
        AppButton("查看全部曲包", onAll, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = AppButtonStyle.Quiet)
    }
}
