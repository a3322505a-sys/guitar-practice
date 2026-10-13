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
    var operation by remember { mutableStateOf<Job?>(null) }
    var snapshot by remember { mutableStateOf(repository.snapshot) }
    fun action(block: suspend ()->LibrarySnapshot) {
        operation=scope.launch {
            busy=true;message=""
            try { snapshot=withContext(Dispatchers.IO) { block() };onChanged(snapshot) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { message=e.message ?: "曲包操作失败" }
            finally { busy=false }
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) operation=scope.launch {
            busy=true;message=""
            try {
                val job=currentCoroutineContext()
                candidate=withContext(Dispatchers.IO) {
                    val input=context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
                    repository.prepare(input) { job.ensureActive() }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { message=e.message ?: "曲包读取失败" }
            finally { busy=false }
        }
    }
    DisposableEffect(Unit) { onDispose { operation?.cancel() } }
    AlertDialog(onDismissRequest={operation?.cancel();onClose()},title={Text("本地曲库")},
        text={
            Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("选择经过审核的 .gpractice 文件；导入后可离线使用。")
                Button(onClick={picker.launch(arrayOf("*/*"))},enabled=!busy && candidate==null) { Text("选择曲包") }
                if(busy) { Text("正在检查曲包…");TextButton(onClick={operation?.cancel()}) { Text("取消检查") } }
                if(message.isNotBlank()) Text(message)
                candidate?.let { preview ->
                    val song=preview.song
                    Text(song.title+" · 修订 "+song.identity.revision+" · "+song.practices.size+" 项练习")
                    Text(song.summary);Text(song.sourceDescription)
                    song.warnings.forEach { Text(it) }
                    val current=snapshot.packages.firstOrNull { it.identity.packageId==song.identity.packageId }
                    if(current!=null) Text("当前修订 "+current.identity.revision+"；正在播放的会话继续使用原修订。")
                    Button(enabled=!busy,onClick={
                        action { repository.commit(preview,current!=null && song.identity.revision<current.identity.revision).also { candidate=null } }
                    }) { Text(if(current==null) "导入" else if(song.identity.revision<current.identity.revision) "导入并回退到修订 "+song.identity.revision else "应用更新") }
                    TextButton(enabled=!busy,onClick={action { repository.cancel(preview);candidate=null;repository.snapshot }}) { Text("取消导入") }
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
        },confirmButton={TextButton(onClick={operation?.cancel();onClose()}) { Text("完成") }})
}
