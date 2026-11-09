package com.a3322505a.guitarlearning.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import com.a3322505a.guitarlearning.practice.Sequence
import com.a3322505a.guitarlearning.practice.Swing
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.assets.song.*
import kotlinx.coroutines.*

@Composable
internal fun SongLibrary(repository: SongRepository,onChanged: (LibrarySnapshot)->Unit,onClose: ()->Unit,displayedSequence: Sequence? = null,themeManager: ThemeManager,
    incomingImport: Pair<Int,Uri>? = null,onImportHandled: ()->Unit = {},onImported: (String)->Unit = {}) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var candidate by remember { mutableStateOf<ImportCandidate?>(null) }
    var message by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }
    var cancellation by remember { mutableStateOf<ImportCancellation?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var snapshot by remember { mutableStateOf(repository.snapshot) }
    var sourceName by remember { mutableStateOf("曲包") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    fun discard(preview: ImportCandidate?) {
        if(preview!=null) scope.launch(NonCancellable+Dispatchers.IO) { repository.cancel(preview) }
    }
    fun close() {
        if(committing || cancellation?.cancel()==false) return
        operation?.cancel();discard(candidate);candidate=null;onClose()
    }
    fun action(block: ()->LibrarySnapshot) {
        busy=true;message="";committing=true
        operation=scope.launch {
            // Short index operations must deliver their actual result after disposal too.
            withContext(NonCancellable) {
                try { snapshot=withContext(Dispatchers.IO) { block() };onChanged(snapshot) }
                catch(e: Exception) { message=e.message ?: "曲包操作失败" }
                finally { busy=false;committing=false }
            }
        }
    }
    fun import(preview: ImportCandidate) {
        val gate=ImportCancellation();cancellation=gate;busy=true;message=""
        operation=scope.launch {
            val job=currentCoroutineContext()
            var imported=false
            // Keep the IO result when returning to a cancelled UI job. Only validation checks cancellation.
            withContext(NonCancellable) {
                try {
                    snapshot=withContext(Dispatchers.IO) {
                        repository.commit(preview,{job.ensureActive()},gate,{committing=true})
                    }
                    onChanged(snapshot);message="导入完成";imported=true
                } catch(e: CancellationException) { message="已取消导入" }
                catch(e: Exception) { message="$sourceName：${e.message?.take(160) ?: "曲包导入失败"}" }
                finally {
                    withContext(Dispatchers.IO) { repository.cancel(preview) }
                    candidate=null;busy=false;committing=false;cancellation=null
                }
            }
            if(imported) onImported(preview.song.identity.packageId)
        }
    }
    fun prepareUri(uri: Uri) {
        if(busy || candidate!=null) return
        sourceName=uri.lastPathSegment?.substringAfterLast('/') ?: "曲包"
        tab=0
        busy=true;message=""
        val gate=ImportCancellation();cancellation=gate
        operation=scope.launch {
            var held: ImportCandidate?=null
            var name=sourceName
            try {
                val job=currentCoroutineContext()
                val preview=withContext(Dispatchers.IO) {
                    name=runCatching {
                        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { rows ->
                            if(rows.moveToFirst()) rows.getString(0) else null
                        }
                    }.getOrNull() ?: name
                    // The granted content URI is the only input, shared by external intents and OpenDocument.
                    val input=context.contentResolver.openInputStream(uri) ?: error("无法读取文件或读取权限已失效")
                    repository.prepare(input) { gate.check();job.ensureActive() }.also { held=it }
                }
                sourceName=name;candidate=preview;held=null
            } catch(e: CancellationException) { message="已取消检查" }
            catch(e: Exception) { message="$name：${e.message?.take(160) ?: "曲包读取失败"}" }
            finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { held?.let { repository.cancel(it) } }
                    busy=false;cancellation=null
                }
            }
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) prepareUri(uri)
    }
    LaunchedEffect(incomingImport,busy,candidate) {
        if(incomingImport!=null && !busy && candidate==null) {
            onImportHandled()
            prepareUri(incomingImport.second)
        }
    }
    DisposableEffect(Unit) { onDispose { cancellation?.cancel();operation?.cancel();discard(candidate) } }
    AppFullScreenDialog({ close() }, Modifier.testTag("management-sheet")) {
        Column(Modifier.fillMaxSize().practiceBackground().displayCutoutPadding().navigationBarsPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            AppTopBar("曲包管理与外观") {
                AppButton("完成", { close() }, Modifier.testTag("management-done"), size = AppButtonSize.Sm,
                    style = AppButtonStyle.Secondary, enabled = !committing)
            }
            SecondaryBar(listOf("曲包管理", "读谱帮助", "三套配色"), tab, { tab = it })
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(tab==2) {
                    ThemePicker(themeManager)
                } else if(tab==1) {
                    DiagramHelp(displayedSequence)
                } else {
                AppCard {
                    Text("导入曲包",style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.primaryText)
                    Text("选择 .gpractice 或 .gpractice.zip；也可从下载文件“打开/分享”导入。",style=MaterialTheme.typography.bodySmall,color=PracticeSkin.colors.textSecondary)
                    AppButton("选择曲包", { picker.launch(arrayOf("*/*")) }, Modifier.testTag("import-package"),
                        symbol = AppSymbol.Import, enabled = !busy && candidate == null)
                if(busy) {
                    Text(if(committing) "正在完成导入，请稍候…" else "正在检查曲包…")
                    if(!committing) AppButton("取消检查", { if(cancellation?.cancel()!=false) operation?.cancel() },
                        size = AppButtonSize.Sm, style = AppButtonStyle.Quiet)
                }
                if(message.isNotBlank()) Text(message,color=PracticeSkin.colors.primaryText,style=MaterialTheme.typography.bodySmall)
                }
                candidate?.let { preview ->
                    val song=preview.song
                    AppCard {
                    Text(sourceName,style=MaterialTheme.typography.bodySmall)
                    Text(song.title+" · 修订 "+song.identity.revision+" · "+song.practices.size+" 项练习",fontWeight=FontWeight.Bold,color=PracticeSkin.colors.textPrimary)
                    Text(listOf(song.author,song.summary).filter(String::isNotBlank).joinToString(" · "));Text(song.sourceDescription)
                    song.warnings.forEach { Text(it) }
                    val current=snapshot.packages.firstOrNull { it.identity.packageId==song.identity.packageId }
                    if(current!=null) Text("当前修订 "+current.identity.revision+"；确认后应用此内容。")
                    AppButton(if(current==null) "导入" else if(song.identity.revision<current.identity.revision)
                        "导入并回退到修订 "+song.identity.revision else "应用更新", { import(preview) },
                        symbol = AppSymbol.Import, enabled = !busy)
                    AppButton("取消导入", { candidate=null;discard(preview) }, size = AppButtonSize.Sm,
                        style = AppButtonStyle.Quiet, enabled = !busy)
                    }
                }
                Text("已启用曲包 · ${snapshot.packages.size}",style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.primaryText)
                snapshot.packages.forEach { pkg ->
                    AppCard {
                        Text(pkg.title,style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.textPrimary)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("已启用 · 修订 "+pkg.identity.revision,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=PracticeSkin.colors.primaryText)
                        AppButton("停用", { action { repository.disable(pkg.identity.packageId) } }, size = AppButtonSize.Sm,
                            style = AppButtonStyle.Secondary, enabled = !busy)
                    }
                        repository.revisions(pkg.identity.packageId).filter { it!=pkg.identity }.firstOrNull()?.let { prior ->
                            AppButton(if(prior.revision==pkg.identity.revision) "恢复上次内容" else "回退到修订 "+prior.revision,
                                { action { repository.rollback(prior) } }, size = AppButtonSize.Sm, style = AppButtonStyle.Quiet, enabled = !busy)
                        }
                    }
                }
                snapshot.diagnostics.forEach { Text(it) }
                }
            }
        }
    }
}

@Composable
private fun DiagramHelp(displayedSequence: Sequence?) {
    AppCard {
        Text("读横向指板",style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.primaryText)
            Text("横向指板上方是 1 弦，下方是 6 弦；左侧数字为弦号，上方数字为品位。",style=MaterialTheme.typography.bodySmall)
            Text("当前音位有外圈高亮，下一音位有预告圈；可切换实际音名与度数。点击右上角箭头收起指板，给乐谱留出更多空间。",style=MaterialTheme.typography.bodySmall)
    }
    AppCard {
        Text("六线谱与演奏提示",style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.primaryText)
            Text("x 为死音，P.M. 为手掌制音；拨弦、滑音、击弦、勾弦和延音按原谱标记演奏。休止继续数拍。",style=MaterialTheme.typography.bodySmall)
            if(displayedSequence?.swing!=Swing.NONE && displayedSequence!=null) Text("${displayedSequence.swing.title}：保留书面时值，按长短2:1演奏；装饰音与推弦使用标准谱面标记。",style=MaterialTheme.typography.bodySmall)
            Text("六线谱上方为 1 弦、下方为 6 弦，数字为品位；0 为空弦，x 为死音。",style=MaterialTheme.typography.bodySmall)
    }
    AppCard {
        Text("开始练琴", style = MaterialTheme.typography.titleMedium)
        Text("从曲包库选择练习；同包有多个练习时，也可点击曲名下方的段落名称，在底部面板选择段落。底部依次为 BPM、播放、循环、节拍。", style = MaterialTheme.typography.bodySmall)
    }
}
