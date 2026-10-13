package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*
import com.a3322505a.guitarlearning.ui.ChordGeometry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class KafkaTest {
    private fun rows(name: String)=javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readLines() }.drop(1).map { it.split(',') }
    private fun sampler()=Sampler(SoundBank.NYLON) { File("src/main/assets/guitar-nylon/$it.pcm").readBytes() }
    @Test fun fullSourceSectionsAndDistinctEndingsAreKeptAcrossThreeGuitars() {
        validatePhrases(Phrases.all)
        assertEquals(28,Phrases.kafka.size)
        assertEquals(('A'..'M').map { it.toString() }.toSet(),Phrases.kafka.map { it.category.substringBefore("类") }.toSet())
        Phrases.kafka.forEach { phrase ->
            val seq=phrase.sequence
            assertTrue(seq.barCount>=4)
            assertEquals(0,seq.capoFret)
            assertEquals(seq.barCount,seq.tabBars().size)
            assertEquals(BarRange(0,seq.barCount-1),Session(seq,phrase.sourceBpm,true,2).range)
        }
        assertNotEquals(Phrases.named("KAF-E-29-G1").sequence.events,Phrases.named("KAF-E-61-G1").sequence.events)
        assertTrue(Phrases.named("KAF-A-1-G1").passage.contains("9–16"))
        assertTrue(Phrases.named("KAF-C-17-G1").passage.contains("49–56"))
        assertTrue(Phrases.named("KAF-I-82-G3").sequence.events.any { it.stringExpression.values.any { x->x.bend.isNotEmpty() } })
    }
    @Test fun everyGpOnsetRetainsFretsActualMidiRestsDurationAndTieWithoutReattack() {
        val source=rows("kafka-gp.csv")
        source.groupBy { Triple(it[0],it[1].toInt(),it[2].toInt()) }.forEach { (key,notes) ->
            val seq=Phrases.named(key.first).sequence
            val bar=key.second-seq.originalBar-1
            val tick=seq.barStart(bar)+key.third
            val event=seq.eventAt(tick.toDouble())!!
            val where="${key.first} bar ${key.second} tick ${key.third}"
            assertTrue(where,event.tick<=tick && event.tick+event.duration>=tick+notes.first()[3].toInt())
            assertEquals(where,notes.filter { it[4]!="0" && it[7]=="0" }.map { Position(it[4].toInt(),it[5].toInt()) }.toSet(),event.positions.toSet())
            assertEquals(where,notes.filter { it[7]=="1" }.map { it[4].toInt() }.toSet(),event.deadStrings)
            notes.filter { it[4]!="0" && it[7]=="0" }.forEach { n ->
                assertEquals(where,n[6].toInt(),seq.midi(Position(n[4].toInt(),n[5].toInt())))
                assertEquals(where,n[8]=="1",event.tick<tick)
            }
            if(notes.all { it[4]=="0" }) assertTrue(where,event.rest)
        }
        Phrases.kafka.forEach { p ->
            assertEquals(source.filter { it[0]==p.id && it[8]=="0" }.map { it[1] to it[2] }.distinct().size,p.sequence.events.size)
        }
    }
    @Test fun mixedMeterCountInSelectionLoopCursorResumeAndEndAgree() {
        val seq=Phrases.named("KAF-H-77-G2").sequence
        assertEquals(listOf(4,4,4,4,2),seq.barMeters)
        assertEquals(216,seq.length)
        val s=Session(seq,80,true,2)
        assertEquals(48,s.preTicks)
        assertEquals(480L,s.totalTicks)
        assertEquals(0,s.loopAt(263.999))
        assertEquals(1,s.loopAt(264.0))
        assertEquals(4,s.cursor(252.0).bar)
        assertEquals(1,s.cursor(252.0).beat)
        assertEquals(240.0,s.resumeTick(252.0),0.0)
        val selected=Session(seq,80,true,2,range=BarRange(4,4))
        assertEquals(24,selected.preTicks)
        assertEquals(24,selected.practiceLength)
        assertEquals(72L,selected.totalTicks)
        assertEquals(1,selected.cursor(12.0).beat)
        assertEquals(4,selected.cursor(24.0).bar)
        assertEquals(0,selected.cursor(24.0).beat)
        val pcm=Pcm(selected,sampler())
        assertTrue(pcm.block(selected.frame(72.0),512,true,true).all { it==0.toShort() })
    }
    @Test fun sourceSwingChangesRealFramesAndCursorButKeepsWrittenValuesAndExplicitTriplets() {
        val seq=Phrases.named("KAF-D-25-G2").sequence
        val s=Session(seq,80,false,2)
        assertEquals(Swing.SIXTEENTH,seq.swing)
        assertEquals(4.0,seq.performedTick(3.0),0.0)
        assertEquals(6.0,seq.performedTick(6.0),0.0)
        assertEquals(10.0,seq.performedTick(9.0),0.0)
        assertEquals(3,seq.events[1].tick)
        for(tick in listOf(0.0,3.0,6.0,9.0,384.0,387.0)) assertEquals(tick,s.tick(s.frame(tick)),.001)
        val eighth=Phrases.named("KAF-E-29-G2").sequence
        assertEquals(8.0,eighth.performedTick(6.0),0.0)
        val triplet=Sequence(listOf(Event(0,4,listOf(Position(1,0))),Event(4,4,emptyList()),Event(8,4,listOf(Position(1,1))),Event(12,36,emptyList())),48,"triplet",tuplets=listOf(Tuplet(0,4)),swing=Swing.EIGHTH)
        assertEquals(4.0,triplet.performedTick(4.0),0.0)
        assertEquals(8.0,triplet.writtenTick(8.0),0.0)
    }
    @Test fun gpGraceBendPalmMuteAndSlideFlagsHaveBothNotationAndSound() {
        val grace=Phrases.named("KAF-B-9-G3").sequence.events.single { it.stringExpression.values.any { x->x.grace!=null } }
        assertEquals(15,grace.stringExpression.getValue(3).grace!!.fret)
        assertEquals(1.5,grace.stringExpression.getValue(3).grace!!.writtenTicks,0.0)
        assertEquals(17,grace.positions.single().fret)
        assertTrue(grace.expressionLabel().contains("装饰15/"))
        val samples=sampler()
        val bend=Event(0,12,listOf(Position(3,10)),stringExpression=mapOf(3 to StringExpression(bend=listOf(PitchPoint(0.0,0.0),PitchPoint(3.0,2.0)))))
        fun wave(e: Event)=Pcm(Session(Sequence(listOf(e),12,"probe"),80,false,1,preview=true),samples).block(0,30000,true,false)
        val plain=wave(bend.copy(stringExpression=emptyMap()))
        assertFalse(plain.contentEquals(wave(bend)))
        for(flag in listOf(4,8,16)) assertFalse(plain.contentEquals(wave(bend.copy(stringExpression=mapOf(3 to StringExpression(slide=flag))))))
        assertFalse(plain.contentEquals(wave(bend.copy(stringExpression=mapOf(3 to StringExpression(grace=Grace(8)))))))
        assertEquals(setOf(4,8,16),Phrases.kafka.flatMap { it.sequence.events }.flatMap { it.stringExpression.values }.map { it.slide }.filter { it!=0 }.toSet())
        assertTrue(Phrases.named("KAF-B-9-G2").sequence.events.all { it.positions.isEmpty() || it.stringExpression.values.all { x->x.ring==true } })
    }
    @Test fun everyPerStringGpExpressionSurvivesIncludingBendHoldOnTies() {
        rows("kafka-expressions.csv").forEach { row ->
            val seq=Phrases.named(row[0]).sequence
            val tick=seq.barStart(row[1].toInt()-seq.originalBar-1)+row[2].toInt()
            val event=seq.eventAt(tick.toDouble())!!
            val x=event.stringExpression.getValue(row[3].toInt())
            when(row[4]) {
                "ring" -> assertEquals(true,x.ring)
                "palm" -> assertEquals(true,x.palmMute)
                "accent" -> assertTrue(x.accent)
                "short" -> { assertTrue(x.short);assertFalse(x.accent) }
                "slide" -> assertEquals(row[5].toInt(),x.slide)
                "grace" -> { val want=row[5].split('/');assertEquals(want[0].toInt(),x.grace!!.fret);assertEquals(want[1].toDouble(),x.grace.writtenTicks,0.0) }
                "bend" -> row[5].split('|').forEach { point ->
                    val want=point.split('/')
                    assertTrue("${row.take(5)} bend $point",x.bend.any { kotlin.math.abs(it.tick-(tick-event.tick+want[0].toDouble()))<.0001 && it.semitones==want[1].toDouble() })
                }
                else -> error("unverified GP technique ${row[4]}")
            }
        }
    }
    @Test fun sourceStaccatoUsesHalfDurationWithoutChangingTheWrittenBeatOrAccentingIt() {
        val source=Phrases.kafka.flatMap { it.sequence.events }.flatMap { it.stringExpression.values }.filter { it.short }
        assertEquals(45,source.size)
        assertTrue(source.none { it.accent })
        val e=Event(0,12,listOf(Position(3,13)),stringExpression=mapOf(3 to StringExpression(short=true)))
        val seq=Sequence(listOf(e),12,"staccato")
        assertEquals(12,seq.events.single().duration)
        assertTrue(e.expressionLabel().contains("短促"))
        val s=Session(seq,60,false,1,preview=true)
        val pcm=Pcm(s,sampler())
        assertTrue(pcm.block(s.frame(6.0),1024,true,false).all { it==0.toShort() })
        val plain=Pcm(s.copy(sequence=seq.copy(events=listOf(e.copy(stringExpression=emptyMap())))),sampler())
        assertTrue(plain.block(s.frame(6.0),1024,true,false).any { it!=0.toShort() })
    }
    @Test fun chordsCoverEverySimultaneousSourceGripAndStableArpeggioWithoutInventingNotes() {
        val expected=rows("kafka-gp.csv").groupBy { Triple(it[0],it[1],it[2]) }.values.map { b ->
            b.filter { it[4]!="0" && it[7]=="0" }.map { Position(it[4].toInt(),it[5].toInt()) }.toSet()
        }.filter { it.size>1 }.toSet()
        val actual=KafkaChords.all.map { it.positions.toSet() }.toSet()
        assertTrue(actual.containsAll(expected))
        assertEquals(50,actual.size)
        assertEquals(50,KafkaChords.all.size)
        assertEquals(mapOf("卡夫卡 · 伴奏指法" to 38,"卡夫卡 · 分解伴奏指法" to 6,"卡夫卡 · 双音 riff" to 6),KafkaChords.all.groupingBy { it.group }.eachCount())
        KafkaChords.all.forEach { chord ->
            assertEquals(0,chord.capoFret)
            assertTrue(chord.source.contains("源谱无和弦标记"))
            assertTrue(chord.positions.any(chord::isRoot))
            assertEquals(chord.positions.map(chord::midi),chordSequence(chord,chord,ChordExercise.CHECK,4).events.flatMap { it.positions }.map { it.midi })
            chord.positions.forEach { assertNotEquals("非和弦音",chord.role(it)) }
            chord.frets.forEachIndexed { i,f -> assertEquals(f!=null && f>0,chord.fingers[i]>0) }
            val geometry=ChordGeometry(390f,300f,chord.baseFret)
            chord.positions.forEach { p ->
                val y=geometry.position(p).y
                assertTrue(y in 0f..300f)
                if(p.fret>0) assertTrue(y>geometry.top && y<geometry.top+4*geometry.fretStep)
                else assertEquals(geometry.openY,y,0f)
            }
            val rootName=Regex("^[A-G][♭♯]?").find(chord.soundingName)!!.value
            val pitchNames=mapOf("C" to 0,"C♯" to 1,"D♭" to 1,"D" to 2,"D♯" to 3,"E♭" to 3,"E" to 4,"F" to 5,"F♯" to 6,"G♭" to 6,"G" to 7,"G♯" to 8,"A♭" to 8,"A" to 9,"A♯" to 10,"B♭" to 10,"B" to 11)
            assertEquals(pitchNames.getValue(rootName),chord.root)
            val suffix=chord.soundingName.substring(rootName.length).substringBefore('/')
            val expectedIntervals=when(suffix) {
                "dim7" -> setOf(0,3,6,9);"dim" -> setOf(0,3,6)
                "m7" -> setOf(0,3,7,10);"maj7" -> setOf(0,4,7,11)
                "7" -> setOf(0,4,7,10);"7(no3,no5)" -> setOf(0,10)
                "m" -> setOf(0,3,7);"sus4" -> setOf(0,5,7)
                "5" -> setOf(0,7);"八度双音" -> setOf(0)
                "" -> setOf(0,4,7);else -> error("unverified chord name ${chord.soundingName}")
            }
            assertEquals(chord.name,expectedIntervals,chord.positions.map { (chord.midi(it)-chord.root)%12 }.toSet())
            if('/' in chord.soundingName) assertEquals(pitchNames.getValue(chord.soundingName.substringAfter('/')),chord.midi(chord.positions.first())%12)
        }
        val inversion=KafkaChords.all.first { it.soundingName=="B♭dim7/E" }
        assertEquals(10,inversion.root)
        assertEquals(4,inversion.midi(inversion.positions.first())%12)
        val high=KafkaChords.all.first { it.soundingName=="C7(no3,no5)/B♭" }
        assertEquals(13,high.baseFret)
        assertEquals(setOf(0,10),high.positions.map { high.midi(it)%12 }.toSet())
    }
}
