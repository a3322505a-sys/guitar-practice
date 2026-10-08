package com.a3322505a.guitarlearning.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.a3322505a.guitarlearning.practice.SAMPLE_RATE

/** Device output boundary; musical time always comes from frames actually consumed. */
interface PlaybackSink : AutoCloseable {
    val heardFrames: Long
    fun play()
    fun write(samples: ShortArray, offset: Int, count: Int): Int
}

internal class AndroidSink(attributes: AudioAttributes) : PlaybackSink {
    private val track = AudioTrack.Builder().setAudioAttributes(attributes)
        .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(maxOf(2048, AudioTrack.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT))).build()
    override val heardFrames get() = track.playbackHeadPosition.toLong() and 0xffffffffL
    override fun play() = track.play()
    override fun write(samples: ShortArray, offset: Int, count: Int) = track.write(samples,offset,count,AudioTrack.WRITE_BLOCKING)
    override fun close() { try { track.pause(); track.flush() } finally { track.release() } }
}
