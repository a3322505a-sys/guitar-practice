package com.a3322505a.guitarlearning

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class SongRepositoryTest {
    private val contract=File("../contracts/song-package/v1")
    private fun reader()=SongReader(SongSchema { File(contract,it).readBytes() })
    private fun fixture(name: String)=File(contract,"fixtures/"+name+".gpractice").readBytes()
    private fun revise(bytes: ByteArray,revision: Int,title: String): ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                while(true) {
                    val entry=input.nextEntry ?: break
                    val data=input.readBytes()
                    val changed=if(entry.name=="manifest.json") String(data).replace("\"revision\": 1","\"revision\": "+revision)
                        .replace("\"title\": \"原创 complex\"","\"title\": \""+title+"\"").toByteArray() else data
                    zip.putNextEntry(ZipEntry(entry.name));zip.write(changed);zip.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }
    @Test fun importUpdateRollbackAndRestartPreserveLegacySettingsAndPinnedSession() {
        val root=Files.createTempDirectory("song-repository").toFile()
        val repo=SongRepository(root,reader());repo.reload()
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("song-transaction-test",0)
        prefs.edit().clear().putInt("hint",2).putInt("phrase.S01.bpm",45).putString("phrase-selected","S01").commit()
        val bytes=fixture("complex")
        val candidate=repo.prepare(ByteArrayInputStream(bytes))
        assertTrue(repo.snapshot.packages.isEmpty())
        val imported=repo.commit(candidate)
        assertEquals(1,imported.packages.size)
        val phrase=repo.resolve(imported.phrases.first { it.assetKey!=null }.id)
        val saved=PhraseSettingsStore(prefs,repo::named)
        saved.save(phrase,85,8,BarRange(1,3))
        val oldSession=Session(phrase.sequence,85,false,1)
        val oldPin=oldSession.plan.revisionPin
        val updated=repo.commit(repo.prepare(ByteArrayInputStream(revise(bytes,2,"修订二"))))
        assertEquals(2,updated.packages.single().identity.revision)
        assertEquals(oldPin,oldSession.plan.revisionPin)
        assertEquals("S01",Phrases.named("S01").id)
        assertEquals(45,prefs.getInt("phrase.S01.bpm",0));assertEquals(2,prefs.getInt("hint",0))
        val restarted=SongRepository(root,reader());restarted.reload()
        restarted.resolve(phrase.id)
        val restored=PhraseSettingsStore(prefs,restarted::named)
        assertEquals(phrase.id,restored.selected().id)
        assertEquals(PhraseSettings(85,8,BarRange(1,3)),restored.load(restored.selected()))
        val prior=restarted.revisions("example.complex").last()
        restarted.rollback(prior)
        restarted.resolve(phrase.id)
        assertEquals(1,restarted.snapshot.packages.single().identity.revision)
        assertEquals(BarRange(1,3),restored.load(restored.selected()).range)
        restarted.disable("example.complex")
        assertEquals("S01",restored.selected().id)
        assertEquals(85,prefs.getInt("asset."+phrase.assetKey!!.encoded+".bpm",0))
    }
    @Test fun idempotenceConflictsCancellationAndDamagedRevisionNeverEraseValidLibrary() {
        val root=Files.createTempDirectory("song-failure").toFile();val repo=SongRepository(root,reader());repo.reload()
        val bytes=fixture("complex")
        repo.commit(repo.prepare(ByteArrayInputStream(bytes)))
        repo.commit(repo.prepare(ByteArrayInputStream(bytes)))
        assertEquals(1,repo.snapshot.packages.size)
        val conflict=repo.prepare(ByteArrayInputStream(revise(bytes,1,"冲突")))
        assertTrue(runCatching { repo.commit(conflict) }.isFailure)
        assertEquals(1,repo.snapshot.packages.size)
        val canceled=repo.prepare(ByteArrayInputStream(fixture("simple")));repo.cancel(canceled)
        assertFalse(canceled.stagingFile.exists());assertEquals(1,repo.snapshot.packages.size)
        assertTrue(runCatching { repo.prepare(ByteArrayInputStream(bytes)) { error("cancel") } }.isFailure)
        val id=repo.snapshot.packages.single().identity
        File(root,id.packageId+"/"+id.revision+"-"+id.digest+"/package.gpractice").writeBytes(byteArrayOf(0))
        repo.reload()
        assertTrue(repo.snapshot.packages.isEmpty());assertFalse(repo.snapshot.diagnostics.isEmpty())
        assertEquals(Phrases.catalogue,repo.snapshot.catalogue)
    }
    @Test fun interruptedIndexWriteRetainsPreviousRevisionAndOrphanFilesStayInactive() {
        val root=Files.createTempDirectory("song-recovery").toFile();val repo=SongRepository(root,reader());repo.reload()
        repo.commit(repo.prepare(ByteArrayInputStream(fixture("simple"))))
        val index=android.util.AtomicFile(File(root,"active-index.json"))
        val interrupted=index.startWrite()
        interrupted.write("{\"active\":[".toByteArray());interrupted.close()
        // A new process opens the last complete index after an unfinished write.
        val restored=SongRepository(root,reader());restored.reload()
        assertEquals("example.simple",restored.snapshot.packages.single().identity.packageId)
        val orphan=restored.prepare(ByteArrayInputStream(fixture("complex")))
        val again=SongRepository(root,reader());again.reload()
        assertTrue(orphan.stagingFile.exists())
        assertEquals(1,again.snapshot.packages.size)
    }
}
