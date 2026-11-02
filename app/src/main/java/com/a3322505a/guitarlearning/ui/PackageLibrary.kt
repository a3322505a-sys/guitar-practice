package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
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
        val matches = query.isBlank() || listOf(pkg.title, pkg.author).plus(pkg.practices.map { it.title }).any { it.contains(query.trim(), ignoreCase = true) }
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
        if (index >= 0) listState.scrollToItem(index)
    }
    val continuation = recent.firstNotNullOfOrNull { id -> packages.flatMap { it.practices }.firstOrNull { it.key.encoded == id } }
    val imported = packages.firstOrNull { it.id == focusedPackageId }
    val colors = PracticeSkin.colors
    Surface(color = colors.background, modifier = Modifier.fillMaxSize().testTag("package-library")) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("曲包库", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onManage, modifier = Modifier.heightIn(min = 48.dp).testTag("library-settings")) { Text("管理 · 外观") }
            }
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("搜索曲名、作者、段落") },
                modifier = Modifier.fillMaxWidth().testTag("library-search"))
            if (imported != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("已导入 · ${imported.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = colors.primaryText)
                    recentPractice(imported, recent)?.let { practice -> TextButton(onClick = { onPractice(practice) }) { Text("开始练习 ›") } }
                }
            } else if (continuation != null) {
                TextButton(onClick = { onPractice(continuation) }, modifier = Modifier.fillMaxWidth().testTag("continue-practice"),
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    Text("继续练习 · ${continuation.title} ›", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "最近", "收藏", "教学", "歌曲").forEachIndexed { index, title ->
                    FilterChip(category == index, { category = index }, label = { Text(title) })
                }
            }
            if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f).widthIn(max = 1080.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                if (ready && filtered.isEmpty()) item {
                    Text(if (packages.isEmpty()) "还没有曲包，点击“管理 · 外观”导入。" else "没有匹配曲包", modifier = Modifier.padding(16.dp))
                }
                items(filtered, key = { it.id }) { pkg ->
                    val expanded = expandedId == pkg.id
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().testTag("package-${pkg.id}")
                            .clickable(enabled = pkg.practices.isNotEmpty()) {
                                if (pkg.practices.size == 1) onPractice(pkg.practices.single())
                                else expandedId = if (expanded) null else pkg.id
                            }.heightIn(min = 64.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pkg.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(listOf(pkg.author, "${pkg.practices.size} 段", if (expanded) "收起 ⌃" else "选择 ›").filter(String::isNotBlank).joinToString(" · "),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                            TextButton(onClick = { onFavorite(pkg.id) }, modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)) {
                                Text(if (pkg.id in favorites) "★" else "☆", modifier = Modifier.testTag("favorite-${pkg.id}"))
                            }
                        }
                        if (expanded) pkg.practices.forEachIndexed { index, practice ->
                            Row(Modifier.fillMaxWidth().clickable { onPractice(practice) }
                                .testTag("practice-${practice.key.encoded}").heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${index + 1}", color = colors.primaryText, style = MaterialTheme.typography.labelLarge)
                                Text(practice.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(if (selectedPracticeId == practice.key.encoded) "继续 ›" else "${practice.suggestedTempo} BPM ›",
                                    style = MaterialTheme.typography.labelMedium, color = colors.primaryText)
                            }
                        }
                        HorizontalDivider(color = colors.borderSubtle)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickPackageSheet(packages: List<PackageCardUiModel>, recent: List<String>, favorites: Set<String>,
    onPractice: (LibraryPractice) -> Unit, onAll: () -> Unit, onClose: () -> Unit) {
    val choices = orderedPackages(packages, recent).filter { it.id in favorites || it.practices.any { p -> p.key.encoded in recent } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("换曲包 · 最近与收藏", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            if (choices.isEmpty()) item { Text("还没有最近或收藏的曲包", modifier = Modifier.padding(16.dp)) }
            items(choices, key = { it.id }) { pkg ->
                recentPractice(pkg, recent)?.let { practice ->
                    ListItem(headlineContent = { Text(pkg.title) }, supportingContent = { Text(practice.title) },
                        modifier = Modifier.clickable { onPractice(practice); onClose() }.testTag("quick-${pkg.id}"))
                }
            }
        }
        TextButton(onClick = onAll, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("查看全部曲包 ›") }
        Spacer(Modifier.navigationBarsPadding())
    }
}
