package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
)

/** Classification uses reviewed lesson/teaching metadata, never package IDs or title keywords. */
internal fun packageCards(snapshot: LibrarySnapshot): List<PackageCardUiModel> = snapshot.packages.map { pkg ->
    val practices = snapshot.packagePractices[pkg.identity.packageId].orEmpty()
    PackageCardUiModel(pkg.identity.packageId, pkg.title, practices, practices.any { p ->
        p.lesson != null || p.teaching?.let { t ->
            listOf(t.category, t.technique, t.use, t.explanation).any(String::isNotBlank)
        } == true
    })
}

@Composable
internal fun PackageLibraryScreen(
    packages: List<PackageCardUiModel>, ready: Boolean, selectedPracticeId: String?,
    onPractice: (LibraryPractice) -> Unit, onManage: () -> Unit,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = PracticeSkin.colors
    Surface(color = colors.background, modifier = Modifier.fillMaxSize().testTag("package-library")) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("曲包库", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onManage, modifier = Modifier.heightIn(min = 48.dp).testTag("library-settings")) {
                    Text("管理 · 外观")
                }
            }
            Text("选一段，开始练琴", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 16.dp))
            if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f).widthIn(max = 1080.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (ready && packages.isEmpty()) item {
                    Text("还没有曲包，点击“管理 · 外观”导入 .gpractice 文件。")
                }
                items(packages, key = { it.id }) { pkg ->
                    val expanded = expandedId == pkg.id
                    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceHigh,
                        modifier = Modifier.fillMaxWidth().testTag("package-${pkg.id}")) {
                        Column {
                            Row(Modifier.fillMaxWidth().clickable(enabled = pkg.practices.isNotEmpty()) {
                                if (pkg.practices.size == 1) onPractice(pkg.practices.single())
                                else expandedId = if (expanded) null else pkg.id
                            }.padding(16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(pkg.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${if (pkg.isTeaching) "教学包" else "曲包"} · ${pkg.practices.size} 项练习",
                                        color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                    if (pkg.practices.size == 1) pkg.practices.single().goal.takeIf(String::isNotBlank)?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Text(if (pkg.practices.size == 1) "开始 ›" else if (expanded) "收起 ⌃" else "选择 ⌄",
                                    color = colors.primaryText, style = MaterialTheme.typography.labelLarge)
                            }
                            if (expanded) pkg.practices.forEachIndexed { index, practice ->
                                HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 16.dp))
                                Row(Modifier.fillMaxWidth().clickable { onPractice(practice) }
                                    .testTag("practice-${practice.key.encoded}").padding(16.dp).heightIn(min = 48.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${index + 1}".padStart(2, '0'), color = colors.primaryText,
                                        style = MaterialTheme.typography.labelLarge)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(practice.title, fontWeight = FontWeight.Medium)
                                        if (practice.goal.isNotBlank()) Text(practice.goal, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis, color = colors.textSecondary,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(if (selectedPracticeId == practice.key.encoded) "继续 ›" else "${practice.suggestedTempo} BPM ›",
                                        style = MaterialTheme.typography.labelMedium, color = colors.primaryText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
