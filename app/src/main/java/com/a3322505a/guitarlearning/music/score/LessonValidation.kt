package com.a3322505a.guitarlearning.music.score

import com.a3322505a.guitarlearning.music.ChordDefinition

/** Validates teaching against playable facts. Missing optional teaching stays missing. */
object LessonValidation {
    fun validate(score: ScoreDocument, practices: List<PracticeSpec>): Boolean {
        var used = false
        fun check(ok: Boolean, path: String, message: String) = musicCheck(ok,"TEACHING",path,message)
        score.voicings.forEach { v ->
            val path = "/score/voicings/" + v.id
            val track = score.tracks.first { it.id == v.trackId }
            if (v.barres.isNotEmpty() || v.quality != null) used = true
            v.barres.forEach { b ->
                check((b.toString..b.fromString).all { string -> v.frets[string-1] >= b.fret &&
                    (v.frets[string-1] != b.fret || v.fingers[string-1] == b.finger) },path,"Barre crosses a muted/open/lower string or conflicting finger")
                check(listOf(b.fromString,b.toString).all { v.frets[it-1] == b.fret && v.fingers[it-1] == b.finger },path,"Barre endpoints must be explicitly fretted")
            }
            v.quality?.let { quality ->
                val root = v.teaching?.root
                check(root in 0..11,path,"Explicit quality needs a sounding root")
                val actual = v.frets.mapIndexedNotNull { index, fret -> if(fret < 0) null else track.midi(index+1,fret)%12 }.toSet()
                check(actual == quality.intervals.map { (root!!+it)%12 }.toSet(),path,"Voicing pitches conflict with the declared chord quality")
                ChordDefinition.parse(v.teaching!!.soundingName)?.let { definition ->
                    check(definition.root.pitchClass==root && definition.quality==quality,path,"Sounding chord name conflicts with its root/quality")
                    definition.bass?.let { bass ->
                        val lowest=v.frets.mapIndexedNotNull { i,f -> if(f<0) null else track.midi(i+1,f) }.min()%12
                        check(lowest==bass.pitchClass,path,"Slash bass conflicts with the lowest sounding note")
                    }
                }
            }
        }
        score.harmonies.forEach { h -> h.validUntil?.let { end ->
            used = true
            val start = score.measureStarts[score.measureIndices.getValue(h.measureId)] + h.onset
            check(end>start && end<=score.length,"/score/harmonies/"+h.id,"Invalid harmony interval")
        } }
        val eventIds = score.voices.flatMap { it.events }.map { it.id }.toSet()
        val practiceIds = practices.map { it.id }.toSet()
        practices.forEach { p -> p.lesson?.let { lesson ->
            used = true
            val path = "/practices/" + p.id + "/lesson"
            check(lesson.transitionIds.all { it in practiceIds && it != p.id },path,"Missing transition practice")
            check(lesson.leadTrackIds.all { it in p.mixTrackIds } &&
                (lesson.leadTrackIds.isEmpty() || p.mixTrackIds.size > 1 && lesson.leadTrackIds.size < p.mixTrackIds.size),path,"Lead fading requires distinct lead and accompaniment tracks")
            check(lesson.chainStarts.distinct().size == lesson.chainStarts.size && lesson.chainStarts.all { it in score.measureIndices },path,"Invalid chain boundaries")
            lesson.key?.spelling // Verify theoretical spelling without guessing a key from the notes.
            lesson.actions.forEach { action ->
                check(action.eventId in eventIds && action.text.isNotBlank(),path,"Action requires a real event and instruction")
                val voicing = action.voicingId?.let { id -> score.voicings.firstOrNull { it.id == id } }
                check(action.voicingId == null || voicing != null,path,"Unknown action voicing")
                if(action.kind == "retained") {
                    val from=score.voicings.firstOrNull { it.id == action.fromVoicingId }
                    check(from!=null && voicing!=null && from.trackId==voicing.trackId && (0..5).any { i ->
                        from.frets[i]>0 && from.fingers[i] in 1..4 && from.frets[i]==voicing.frets[i] && from.fingers[i]==voicing.fingers[i]
                    },path,"Retained-finger action requires two exact compatible voicings with a shared string/fret/finger")
                }
                if(action.kind == "barre") check(voicing?.barres?.isNotEmpty() == true,path,"Barre action requires explicit barre data")
            }
        } }
        return used
    }
}
