package com.a3322505a.guitarlearning.practice

import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.music.score.*
import com.a3322505a.guitarlearning.practice.compile.PracticeCompiler

/** Native startup content, used while the reviewed library is loading. */
internal object NativeIntro {
    fun phrase(): Phrase {
        val lesson=LessonMetadata(TeachingModule.FOUNDATIONS,Tonality.C_MAJOR.context,
            actions=listOf(TeachingAction("intro-e-0","position","3指五弦三品 C；空弦 D 后用2指四弦二品 E，回 C。")))
        val positions=listOf(Position(5,3),Position(4,0),Position(4,2),Position(5,3))
        val events=positions.mapIndexed { i,p -> ScoreEvent("intro-e-$i","intro-m",Q(i.toLong()),Q.ONE,
            WrittenValue(4),listOf(ScoreNote("intro-n-$i",p.string,p.fret,p.midi)),Articulation(pick=if(i%2==0) "down" else "up")) }
        val score=ScoreDocument("native-startup",listOf(Measure("intro-m","1",Meter(4,4,listOf(1,1,1,1)),Q(4))),
            listOf(Track("guitar","吉他",Music.openMidi)),listOf(Voice("voice","guitar",events)),emptyList(),emptyList(),
            listOf(Tempo(Q.ZERO,Q.ONE,60.0)),emptyList(),emptyList(),Form(),emptyList(),emptyList())
        val spec=PracticeSpec("startup","C 大调入门小句","逐音交替拨弦，句尾回 C。","guitar",listOf("guitar"),
            listOf(PracticeSegment(Coordinate("intro-m"),Coordinate("intro-m",Q(4)))),tempoMode="fixed",
            teaching=PracticeTeaching("基础启蒙","1 小节","原创原生教材","交替拨弦","基础启蒙","标准版","BPM","逐音按标号交替拨弦，句尾回 C。"),
            noteFingerings=listOf(NoteFingering("intro-n-0",3,"原生配指"),NoteFingering("intro-n-2",2,"原生配指"),NoteFingering("intro-n-3",3,"原生配指")),lesson=lesson)
        return Phrase("startup",spec.title,"基础启蒙","1 小节","原创原生教材",60,"入门","交替拨弦","标准调弦 · 无变调夹",
            Sequence(emptyList(),BAR,spec.goal,runtimePlan=PracticeCompiler.compile(score,spec)),goal=spec.goal,use="基础启蒙",version="标准版",lesson=lesson)
    }
}
