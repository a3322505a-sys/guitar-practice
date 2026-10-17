package com.a3322505a.guitarlearning.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import com.a3322505a.guitarlearning.practice.Sequence
import com.a3322505a.guitarlearning.practice.Swing
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.assets.song.*
import kotlinx.coroutines.*

@Composable
internal fun SongLibrary(repository: SongRepository,onChanged: (LibrarySnapshot)->Unit,onClose: ()->Unit,displayedSequence: Sequence? = null,themeManager: ThemeManager) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var candidate by remember { mutableStateOf<ImportCandidate?>(null) }
    var message by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }
    var cancellation by remember { mutableStateOf<ImportCancellation?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var snapshot by remember { mutableStateOf(repository.snapshot) }
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
    fun import(preview: ImportCandidate,rollback: Boolean) {
        val gate=ImportCancellation();cancellation=gate;busy=true;message=""
        operation=scope.launch {
            val job=currentCoroutineContext()
            // Keep the IO result when returning to a cancelled UI job. Only validation checks cancellation.
            withContext(NonCancellable) {
                try {
                    snapshot=withContext(Dispatchers.IO) {
                        repository.commit(preview,rollback,{job.ensureActive()},gate,{committing=true})
                    }
                    onChanged(snapshot);message="导入完成"
                } catch(e: CancellationException) { message="已取消导入" }
                catch(e: Exception) { message=e.message ?: "曲包导入失败" }
                finally {
                    withContext(Dispatchers.IO) { repository.cancel(preview) }
                    candidate=null;busy=false;committing=false;cancellation=null
                }
            }
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            busy=true;message=""
            val gate=ImportCancellation();cancellation=gate
            operation=scope.launch {
                var held: ImportCandidate?=null
                try {
                    val job=currentCoroutineContext()
                    val preview=withContext(Dispatchers.IO) {
                        val input=context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
                        repository.prepare(input) { gate.check();job.ensureActive() }.also { held=it }
                    }
                    candidate=preview;held=null
                } catch(e: CancellationException) { message="已取消检查" }
                catch(e: Exception) { message=e.message ?: "曲包读取失败" }
                finally {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) { held?.let { repository.cancel(it) } }
                        busy=false;cancellation=null
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { cancellation?.cancel();operation?.cancel();discard(candidate) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Dialog(onDismissRequest={close()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        ImmersiveOverlayWindow()
        BackHandler { close() }
        Surface(color=Color.Transparent,modifier=Modifier.fillMaxSize().testTag("management-sheet")) {
        Column(Modifier.fillMaxSize().practiceBackground().displayCutoutPadding().navigationBarsPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("设置与帮助",style=MaterialTheme.typography.titleLarge,color=PracticeSkin.colors.primary,modifier=Modifier.weight(1f))
                OutlinedButton(enabled=!committing,onClick={close()},modifier=Modifier.heightIn(min=48.dp), shape=MaterialTheme.shapes.small) { Text("完成") }
            }
            PracticeSegments(listOf("曲包管理","帮助说明","主题 / 外观"),tab,{tab=it},Modifier.fillMaxWidth())
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(tab==2) {
                    ThemePicker(themeManager)
                } else if(tab==1) {
                    DiagramHelp(displayedSequence)
                } else {
                PracticeContentCard(modifier=Modifier.fillMaxWidth()) {
                    Text("导入曲包",style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.primary)
                    Text("选择经过审核的 .gpractice 文件；导入后可离线使用。",style=MaterialTheme.typography.bodySmall,color=PracticeSkin.colors.textSecondary)
                    PracticeAction("选择曲包",enabled=!busy && candidate==null,onClick={picker.launch(arrayOf("*/*"))})
                if(busy) {
                    Text(if(committing) "正在完成导入，请稍候…" else "正在检查曲包…")
                    if(!committing) TextButton(onClick={if(cancellation?.cancel()!=false) operation?.cancel()}, shape=MaterialTheme.shapes.small) { Text("取消检查") }
                }
                if(message.isNotBlank()) Text(message,color=PracticeSkin.colors.primary,style=MaterialTheme.typography.bodySmall)
                }
                candidate?.let { preview ->
                    val song=preview.song
                    PracticeContentCard(modifier=Modifier.fillMaxWidth()) {
                    Text(song.title+" · 修订 "+song.identity.revision+" · "+song.practices.size+" 项练习",fontWeight=FontWeight.Bold,color=PracticeSkin.colors.textPrimary)
                    Text(song.summary);Text(song.sourceDescription)
                    song.warnings.forEach { Text(it) }
                    val current=snapshot.packages.firstOrNull { it.identity.packageId==song.identity.packageId }
                    if(current!=null) Text("当前修订 "+current.identity.revision+"；正在播放的会话继续使用原修订。")
                    Button(enabled=!busy,onClick={import(preview,current!=null && song.identity.revision<current.identity.revision)}, shape=MaterialTheme.shapes.small) {
                        Text(if(current==null) "导入" else if(song.identity.revision<current.identity.revision) "导入并回退到修订 "+song.identity.revision else "应用更新")
                    }
                    TextButton(enabled=!busy,onClick={candidate=null;discard(preview)}, shape=MaterialTheme.shapes.small) { Text("取消导入") }
                    }
                }
                Text("已启用曲包 · ${snapshot.packages.size}",style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.primary)
                snapshot.packages.forEach { pkg ->
                    Surface(shape=MaterialTheme.shapes.large,color=PracticeSkin.colors.surface,border=BorderStroke(PracticeSkin.metrics.borderWidth,PracticeSkin.colors.border),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(pkg.title,style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.textPrimary)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("已启用 · 修订 "+pkg.identity.revision,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=PracticeSkin.colors.primary)
                        OutlinedButton(enabled=!busy,onClick={action { repository.disable(pkg.identity.packageId) }}, shape=MaterialTheme.shapes.small) { Text("停用") }
                    }
                        repository.revisions(pkg.identity.packageId).filter { it.revision<pkg.identity.revision }.firstOrNull()?.let { prior ->
                            TextButton(enabled=!busy,onClick={action { repository.rollback(prior) }}, shape=MaterialTheme.shapes.small) { Text("回退到修订 "+prior.revision) }
                        }
                    }
                    }
                }
                snapshot.diagnostics.forEach { Text(it) }
                }
            }
        }
        }
    }
}

@Composable
private fun DiagramHelp(displayedSequence: Sequence?) {
    PracticeContentCard(modifier=Modifier.fillMaxWidth()) {
        Text("读指板与和弦图",style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.primary)
            Text("横向指板上方是 1 弦，下方是 6 弦；左侧数字为弦号，上方数字为品位。",style=MaterialTheme.typography.bodySmall)
            Text("绿色为路线音位，粉色为当前音，蓝色圈为下一音。同一音位可同时是当前和下一音。",style=MaterialTheme.typography.bodySmall)
            Text("和弦图左至右为 6→1 弦，金色为根音；O 为空弦，X 不弹。",style=MaterialTheme.typography.bodySmall)
    }
    PracticeContentCard(modifier=Modifier.fillMaxWidth()) {
        Text("六线谱与演奏提示",style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.primary)
            Text("x 为死音，P.M. 为手掌制音；拨弦、滑音、击弦、勾弦和延音按原谱标记演奏。休止继续数拍。",style=MaterialTheme.typography.bodySmall)
            if(displayedSequence?.swing!=Swing.NONE && displayedSequence!=null) Text("${displayedSequence.swing.title}：保留书面时值，按长短2:1演奏；装饰音与推弦使用标准谱面标记。",style=MaterialTheme.typography.bodySmall)
            Text("在指板标注菜单选择“隐藏 / 盲弹”可隐藏音位与演奏提示；六线谱始终保留乐谱音符。",style=MaterialTheme.typography.bodySmall)
    }
    PracticeContentCard(modifier=Modifier.fillMaxWidth()) {
        Text("跟练与指法",style=MaterialTheme.typography.titleMedium,color=PracticeSkin.colors.textPrimary)
    Text("听示范和跟练按有效弦逐根弹，每弦一拍；数字 1—4 表示手指，O 为空弦，X 弦不弹。",style=MaterialTheme.typography.bodySmall)
    Text("no5 表示省略五音；斜杠后的音是最低音；5 表示根音与五音构成的力量和弦，双音 riff 按两根有效弦演奏。斜阳指法用变调夹3品，数字相对变调夹；基础和弦无夹。",style=MaterialTheme.typography.bodySmall)
    }
}
