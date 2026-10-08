package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/** Exports both banks through the App's real renderer, with identical musical events. */
class AudioComparisonTest {
    @Test fun renderSameExercisesWithBothBanksAndMatchAuditionLevel() {
        val range=listOf(40,45,48,52,55,59,64,69,74,77).map { midi ->
            (6 downTo 1).map { Position(it,midi-Music.openMidi[it-1]) }.first { it.fret in 0..13 }
        }
        val examples=linkedMapOf(
            "single-notes" to Sequence(range.mapIndexed { i,p -> Event(i*6,6,listOf(p)) }+Event(60,36,emptyList()),96,""),
            "open-chords" to chordSequence(Chords.named("C"),Chords.named("Am"),ChordExercise.CHANGE,4),
            "arpeggio" to chordSequence(Chords.named("D"),Chords.named("Am"),ChordExercise.ARPEGGIO,4))
        val output=File("../output/audio-comparison").apply { mkdirs() }
        val report=StringBuilder("example,bank,frames,rms_dbfs,peak_dbfs,normalization_db\n")
        for((name,sequence) in examples) for((label,bank) in listOf("old-electric" to SoundBank.ELECTRIC,"new-nylon" to SoundBank.NYLON)) {
            val sampler=Sampler(bank) { root -> File("src/main/assets/${bank.folder}/$root.pcm").readBytes() }
            val session=Session(sequence,90,false,1)
            val renderer=Pcm(session,sampler)
            val count=session.frame(sequence.length.toDouble()).toInt()
            val wave=renderer.block(0,count,true,false)
            val rms=sqrt(wave.sumOf { it.toDouble()*it }/count)/32768
            assertTrue(rms>.01)
            val peak=wave.maxOf { abs(it.toInt()) }/32768.0
            assertTrue(peak<.97)
            // Whole-clip RMS is matched for reproducible development audition. A-weighted
            // and subjective loudness are separately checked by the analysis script.
            val gain=min(10.0.pow(-20.0/20)/rms,.93/peak)
            val matched=wave.map { (it*gain).roundToInt().toShort() }.toShortArray()
            val measured=sqrt(matched.sumOf { it.toDouble()*it }/count)/32768
            wav(File(output,"$name-$label.wav"),matched)
            report.append("$name,$label,$count,${20*log10(measured)},${20*log10(peak*gain)},${20*log10(gain)}\n")
        }
        File(output,"renderer-levels.csv").writeText(report.toString())
    }
    private fun wav(file: File, wave: ShortArray) {
        FileOutputStream(file).use { out ->
            fun int(v:Int) { repeat(4) { out.write((v ushr (it*8)) and 255) } }
            fun short(v:Int) { repeat(2) { out.write((v ushr (it*8)) and 255) } }
            out.write("RIFF".toByteArray());int(36+wave.size*2);out.write("WAVEfmt ".toByteArray())
            int(16);short(1);short(1);int(SAMPLE_RATE);int(SAMPLE_RATE*2);short(2);short(16)
            out.write("data".toByteArray());int(wave.size*2);wave.forEach { short(it.toInt()) }
        }
    }
}
