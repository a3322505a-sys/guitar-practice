package com.a3322505a.guitarlearning


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a3322505a.guitarlearning.assets.song.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.audio.*
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
    @Test fun twoActivityRepositoriesSeedOneStorageRootWithoutRacingPublication() {
        val root=Files.createTempDirectory("song-concurrent-seed").toFile()
        val bytes=fixture("complex")
        val seed=mapOf("complex" to { ByteArrayInputStream(bytes) })
        val repositories=listOf(SongRepository(root,reader(),seed),SongRepository(root,reader(),seed))
        val start=java.util.concurrent.CountDownLatch(1)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val jobs=repositories.map { repo -> pool.submit<LibrarySnapshot> { start.await();repo.reload() } }
            start.countDown()
            jobs.forEach { future ->
                val state=future.get(45,java.util.concurrent.TimeUnit.SECONDS)
                assertTrue(state.diagnostics.toString(),state.diagnostics.isEmpty())
                assertEquals(1,state.packages.size)
            }
            repositories[0].disable("example.complex")
            assertTrue(repositories[1].reload().packages.isEmpty())
            assertEquals(1,repositories[1].revisions("example.complex").size)
        } finally { pool.shutdownNow() }
    }
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
        assertEquals("S01",repo.named("S01").id)
        assertTrue(repo.named("S01").unprepared)
        assertTrue(runCatching { repo.resolve("S01") }.exceptionOrNull() is com.a3322505a.guitarlearning.music.score.MusicError)
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
        assertEquals(phrase.id,restored.selected().id)
        assertTrue(restored.selected().unprepared)
        assertEquals(85,prefs.getInt("asset."+phrase.assetKey!!.encoded+".bpm",0))
    }
    @Test fun idempotenceConflictsCancellationAndDamagedRevisionNeverEraseValidLibrary() {
        val root=Files.createTempDirectory("song-failure").toFile();val repo=SongRepository(root,reader());repo.reload()
        val bytes=fixture("complex")
        repo.commit(repo.prepare(ByteArrayInputStream(bytes)))
        // Simulate an existing v75 catalogue, whose teaching fields are derived on reload.
        val identity=repo.snapshot.packages.single().identity
        val catalogue=File(root,identity.packageId+"/"+identity.revision+"-"+identity.digest+"/catalogue.json")
        val priorHash=sha256(catalogue.readBytes())
        val oldMetadata=StrictJson.parse(catalogue.readBytes()).objectValue().toMutableMap()
        listOf("voicings","tracks","summary").forEach { oldMetadata.remove(it) }
        catalogue.writeBytes(StrictJson.encode(oldMetadata))
        val indexFile=File(root,"active-index.json")
        indexFile.writeText(indexFile.readText().replace(priorHash,sha256(catalogue.readBytes())))
        repo.reload()
        repo.commit(repo.prepare(ByteArrayInputStream(bytes)))
        assertEquals(1,repo.snapshot.packages.size)
        val conflict=repo.prepare(ByteArrayInputStream(revise(bytes,1,"冲突")))
        assertTrue(runCatching { repo.commit(conflict) }.isFailure)
        assertFalse(conflict.stagingFile.exists())
        assertEquals(1,repo.snapshot.packages.size)
        val canceled=repo.prepare(ByteArrayInputStream(fixture("simple")));repo.cancel(canceled)
        assertFalse(canceled.stagingFile.exists());assertEquals(1,repo.snapshot.packages.size)
        assertTrue(runCatching { repo.prepare(ByteArrayInputStream(bytes)) { error("cancel") } }.isFailure)
        val id=repo.snapshot.packages.single().identity
        File(root,id.packageId+"/"+id.revision+"-"+id.digest+"/package.gpractice").writeBytes(byteArrayOf(0))
        repo.reload()
        assertTrue(repo.snapshot.packages.isEmpty());assertFalse(repo.snapshot.diagnostics.isEmpty())
        assertTrue(repo.snapshot.catalogue.isEmpty())
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
        val abandoned=File(root,"staging/00000000-0000-0000-0000-000000000000.gpractice").apply { writeBytes(fixture("simple")) }
        val unrelated=File(root,"staging/keep.txt").apply { writeText("valid unrelated data") }
        again.reload()
        assertFalse(abandoned.exists());assertTrue(orphan.stagingFile.exists());assertTrue(unrelated.exists())
        restored.cancel(orphan)
    }
    @Test fun cancellationBeforePublicationCleansStageAndCancellationAfterGateFinishesCommit() {
        val root=Files.createTempDirectory("song-cancel-gate").toFile();val repo=SongRepository(root,reader());repo.reload()
        val before=repo.prepare(ByteArrayInputStream(fixture("simple")))
        val gate=ImportCancellation()
        var checks=0
        assertTrue(runCatching { repo.commit(before,cancel={if(++checks==3) gate.cancel()},cancellation=gate) }.isFailure)
        assertFalse(before.stagingFile.exists());assertTrue(repo.snapshot.packages.isEmpty())
        val after=repo.prepare(ByteArrayInputStream(fixture("simple")))
        val committed=ImportCancellation()
        repo.commit(after,cancellation=committed,onCommitStarted={assertFalse(committed.cancel())})
        assertTrue(committed.committing);assertFalse(after.stagingFile.exists())
        assertEquals("example.simple",SongRepository(root,reader()).reload().packages.single().identity.packageId)
        // Exercise the checkpoint after staging bytes have actually been written.
        assertTrue(runCatching { repo.prepare(ByteArrayInputStream(fixture("complex"))) {
            if(File(root,"staging").listFiles().orEmpty().any { it.extension=="gpractice" }) error("cancel after write")
        } }.isFailure)
        assertTrue(File(root,"staging").listFiles().orEmpty().none { it.extension=="gpractice" })
    }
    @Test fun nativeImportKeepsMusicAndLeavesRetiredSettingsUntouched() {
        val root=Files.createTempDirectory("native-import").toFile()
        val repo=SongRepository(root,reader());repo.reload()
        val prefs=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("native-import",0)
        prefs.edit().clear().putString("phrase-selected","S01").putInt("phrase.S01.bpm",73).commit()
        val store=PhraseSettingsStore(prefs,repo::named)
        assertEquals("S01",store.selected().id)
        assertTrue(store.selected().unprepared)
        val file=File("src/main/assets/song-packages/demo-c.gpractice")
        val source=reader().read(file.readBytes())
        repo.commit(repo.prepare(file.inputStream()))
        assertEquals("S01",store.selected().id)
        assertTrue(store.selected().unprepared)
        val phrase=repo.resolve(com.a3322505a.guitarlearning.music.score.PracticeKey(source.identity.packageId,"A-original").encoded)
        assertEquals(source.score,phrase.sequence.performance.score)
        store.select(phrase);store.save(phrase,85,8,BarRange(1,1))
        val restarted=SongRepository(root,reader()).also { it.reload() }
        val restored=PhraseSettingsStore(prefs,restarted::named)
        assertEquals(phrase.id,restored.selected().id)
        assertEquals(PhraseSettings(85,8,BarRange(1,1)),restored.load(restarted.resolve(phrase.id)))
        assertEquals(73,prefs.getInt("phrase.S01.bpm",0))
    }
}
