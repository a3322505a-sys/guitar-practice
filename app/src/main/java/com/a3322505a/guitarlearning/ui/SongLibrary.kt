package com.a3322505a.guitarlearning.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.assets.song.*
import kotlinx.coroutines.*

@Composable
internal fun SongLibrary(repository: SongRepository,onChanged: (LibrarySnapshot)->Unit,onClose: ()->Unit) {
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
    AlertDialog(onDismissRequest={close()},title={Text("本地曲库")},
        text={
            Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("选择经过审核的 .gpractice 文件；导入后可离线使用。")
                Button(onClick={picker.launch(arrayOf("*/*"))},enabled=!busy && candidate==null) { Text("选择曲包") }
                if(busy) {
                    Text(if(committing) "正在完成导入，请稍候…" else "正在检查曲包…")
                    if(!committing) TextButton(onClick={if(cancellation?.cancel()!=false) operation?.cancel()}) { Text("取消检查") }
                }
                if(message.isNotBlank()) Text(message)
                candidate?.let { preview ->
                    val song=preview.song
                    Text(song.title+" · 修订 "+song.identity.revision+" · "+song.practices.size+" 项练习")
                    Text(song.summary);Text(song.sourceDescription)
                    song.warnings.forEach { Text(it) }
                    val current=snapshot.packages.firstOrNull { it.identity.packageId==song.identity.packageId }
                    if(current!=null) Text("当前修订 "+current.identity.revision+"；正在播放的会话继续使用原修订。")
                    Button(enabled=!busy,onClick={import(preview,current!=null && song.identity.revision<current.identity.revision)}) {
                        Text(if(current==null) "导入" else if(song.identity.revision<current.identity.revision) "导入并回退到修订 "+song.identity.revision else "应用更新")
                    }
                    TextButton(enabled=!busy,onClick={candidate=null;discard(preview)}) { Text("取消导入") }
                }
                snapshot.packages.forEach { pkg ->
                    HorizontalDivider();Text(pkg.title+" · 修订 "+pkg.identity.revision)
                    Row {
                        TextButton(enabled=!busy,onClick={action { repository.disable(pkg.identity.packageId) }}) { Text("停用") }
                        repository.revisions(pkg.identity.packageId).filter { it.revision<pkg.identity.revision }.firstOrNull()?.let { prior ->
                            TextButton(enabled=!busy,onClick={action { repository.rollback(prior) }}) { Text("回退到修订 "+prior.revision) }
                        }
                    }
                }
                snapshot.diagnostics.forEach { Text(it) }
            }
        },confirmButton={TextButton(enabled=!committing,onClick={close()}) { Text("完成") }})
}
