package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.audio.PlaybackSink
import java.util.concurrent.atomic.AtomicLong

class ManualSink : PlaybackSink {
    val head=AtomicLong(0)
    val written=AtomicLong(0)
    @Volatile var nonZero=false
    @Volatile var closed=false
    override val heardFrames get()=head.get()
    override fun play() {}
    override fun write(samples: ShortArray, offset: Int, count: Int): Int { if(samples.any { it!=0.toShort() }) nonZero=true; written.addAndGet(count.toLong()); Thread.sleep(1); return count }
    override fun close() { closed=true }
}
