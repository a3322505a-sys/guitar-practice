package com.a3322505a.guitarlearning.audio

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import com.a3322505a.guitarlearning.music.Position
import com.a3322505a.guitarlearning.practice.*
import java.util.concurrent.Executors

/** Retained for pre-migration tests; the application entry uses AlphaPlayback. */
class Player(context: Context, private val sinkFactory: (() -> PlaybackSink)? = null) : PracticePlayback {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val bank = SoundBank.NYLON
    private val sampler by lazy { Sampler(bank) { root -> app.assets.open("${bank.folder}/$root.pcm").use { it.readBytes() } } }
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> if(change < 0) pause() }.build()
    @Volatile private var generation = 0
    @Volatile override var guitar = true
    @Volatile override var click = true
    override var progress by mutableStateOf(Progress())
        private set
    override var session: Session? by mutableStateOf(null)
        private set
    override val cursor: Cursor? get() = session?.cursor(progress.tick)
    private var resumeAt = 0.0
    override val active get() = progress.phase == Phase.PLAYING || progress.phase == Phase.PREPARING

    override fun start(sequence: Sequence, bpm: Int, follow: Boolean, repeats: Int, range: BarRange,
        sourceTempo: Boolean, multiplier: Double) {
        generation++
        session = Session(sequence, bpm, follow, if(follow) repeats else 1,range=range,sourceTempo=sourceTempo,tempoMultiplier=multiplier)
        resumeAt = 0.0
        play(session!!, resumeAt)
    }
    override fun resume() { if (progress.phase == Phase.PAUSED) session?.let { play(it, resumeAt) } }
    override fun restart() { session?.let { resumeAt=0.0; play(it, 0.0) } }
    override fun pause() {
        if(!active) return
        generation++
        resumeAt = session!!.resumeTick(progress.tick)
        progress = Progress(Phase.PAUSED, resumeAt, if(resumeAt<session!!.preTicks) "已暂停；继续将从预备小节开始" else "已暂停；继续将从当前小节起点开始")
        audioManager.abandonAudioFocusRequest(focus)
    }
    override fun invalidate() { stop(); progress = Progress(message="设置已改变，下一次播放从头开始") }
    override fun stop() {
        generation++
        session = null
        resumeAt=0.0
        progress = Progress()
        audioManager.abandonAudioFocusRequest(focus)
    }
    override fun audition(positions: List<Position>, strum: Boolean, capoFret: Int, openStringMidi: List<Int>) {
        if(active) return
        session = Session(Sequence(listOf(Event(0,12,positions,strum=strum)),12,"试听",capoFret=capoFret,openStringMidi=openStringMidi), 60, false, 1, preview=true)
        resumeAt=0.0
        play(session!!,0.0)
    }
    override fun release() { stop(); worker.shutdown() }

    private fun play(current: Session, fromTick: Double) {
        val id = ++generation
        if(audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            progress=Progress(Phase.ERROR, message="当前音频正被其他应用占用，请稍后重试")
            return
        }
        progress = Progress(Phase.PREPARING, fromTick, "准备声音…")
        worker.execute {
            var output: PlaybackSink? = null
            try {
                val timing = LegacySessionClock(current)
                val pcm = Pcm(current, sampler)
                if(id != generation) return@execute
                val chunkSize = 512
                output = sinkFactory?.invoke() ?: AndroidSink(attributes)
                val origin = timing.frame(fromTick)
                val end = timing.totalFrames
                var written = origin
                output.play()
                while(id == generation && (end == null || written < end)) {
                    val count = if(end==null) chunkSize else minOf(chunkSize.toLong(),end-written).toInt()
                    val bytes = pcm.block(written, count, guitar, click)
                    var offset=0
                    while(offset<count && id==generation) {
                        val n=output.write(bytes,offset,count-offset)
                        check(n>0) { "音频输出失败 ($n)" }
                        offset+=n
                    }
                    written+=offset
                    val heard=origin+output.heardFrames
                    publish(id, Progress(Phase.PLAYING,timing.tick(heard),""))
                }
                while(id==generation && end!=null) {
                    val heard=origin+output.heardFrames
                    if(heard>=end) break
                    publish(id, Progress(Phase.PLAYING,timing.tick(heard),""))
                    Thread.sleep(12)
                }
                if(id==generation) publish(id,Progress(Phase.ENDED,current.totalExactTicks!!,if(current.preview) "试听结束" else "本次练习结束"))
            } catch(error: Exception) {
                publish(id,Progress(Phase.ERROR,message=error.message ?: "音频输出失败"))
            } finally {
                output?.close()
            }
        }
    }
    private fun publish(id: Int, update: Progress) {
        handler.post {
            if(id==generation) {
                progress=update
                if(update.phase==Phase.ENDED || update.phase==Phase.ERROR) audioManager.abandonAudioFocusRequest(focus)
            }
        }
    }
}
