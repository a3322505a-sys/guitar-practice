@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package com.a3322505a.guitarlearning

import alphaTab.Settings
import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.importer.ScoreLoader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/** Independent GPIF reference: source facts, not an Android output or listening claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlphaTabMusicalFactsTest {
    @Test fun complexWrittenRhythmPitchRestsAndTiesMatchSource() {
        val project = File(checkNotNull(System.getProperty("user.dir"))).let { if (it.name == "app") it.parentFile else it }
        val reference = File(project, "work/evidence/complex-gpif-musical-reference.json")
        assumeTrue("Run prepare-alphatab-p0-audit.py for this private source probe", reference.exists())
        val expected = JSONObject(reference.readText())
        val bytes = File(expected.getString("path")).readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("Private source changed since reference extraction", expected.getString("sourceSha256"), sha)
        val score = ScoreLoader.loadScoreFromBytes(Uint8Array(bytes.asUByteArray()), Settings())
        val staff = score.tracks[expected.getInt("track")].staves[0]
        val bars = expected.getJSONArray("bars")
        assertEquals(bars.length(), score.masterBars.length.toInt())
        assertEquals(expected.getInt("capo"), staff.capo.toInt())
        for (index in 0 until bars.length()) {
            val bar = bars.getJSONObject(index)
            val actual = score.masterBars[bar.getInt("bar")]
            assertEquals("Time numerator bar $index", bar.getInt("numerator"), actual.timeSignatureNumerator.toInt())
            assertEquals("Time denominator bar $index", bar.getInt("denominator"), actual.timeSignatureDenominator.toInt())
            assertEquals("Written swing declaration bar $index", bar.getString("tripletFeel"), actual.tripletFeel.toString())
        }
        val beats = expected.getJSONArray("beats")
        var graceBeats = 0
        for (index in 0 until beats.length()) {
            val e = beats.getJSONObject(index)
            val actual = staff.bars[e.getInt("bar")].voices[e.getInt("voice")].beats[e.getInt("beat")]
            val label = "bar ${e.getInt("bar") + 1} voice ${e.getInt("voice") + 1} beat ${e.getInt("beat") + 1}"
            assertEquals("Grace timing $label", e.getString("grace"), actual.graceType.toString())
            // alphaTab 1.8.4 normalizes grace glyph durations in Beat.updateDurations.
            // Check their original timing marker and pitches, without claiming raw-value preservation.
            val grace = e.getString("grace") != "None"
            if (grace) graceBeats++
            else assertEquals("Duration $label", e.getInt("duration"), actual.duration.value)
            assertEquals("Dots $label", e.getInt("dots"), actual.dots.toInt())
            assertEquals("Rest $label", e.getBoolean("rest"), actual.isRest)
            if (e.getInt("tupletNum") != e.getInt("tupletDen")) {
                assertEquals("Tuplet numerator $label", e.getInt("tupletNum"), actual.tupletNumerator.toInt())
                assertEquals("Tuplet denominator $label", e.getInt("tupletDen"), actual.tupletDenominator.toInt())
            } else assertFalse("Unexpected tuplet $label", actual.hasTuplet)
            if (!grace) assertEquals("Written ticks $label", e.getDouble("writtenTicks"), actual.displayDuration, 0.000001)
        }
        val notes = expected.getJSONArray("notes")
        var tieDestinations = 0
        for (index in 0 until notes.length()) {
            val e = notes.getJSONObject(index)
            val beat = staff.bars[e.getInt("bar")].voices[e.getInt("voice")].beats[e.getInt("beat")]
            val note = beat.notes.single { it.string.toInt() == e.getInt("string") }
            val label = "bar ${e.getInt("bar") + 1} voice ${e.getInt("voice") + 1} beat ${e.getInt("beat") + 1} string ${e.getInt("string")}"
            assertEquals("Fret $label", e.getInt("fret"), note.fret.toInt())
            assertEquals("Sounding pitch $label", e.getDouble("pitch"), note.realValueWithoutHarmonic, 0.000001)
            assertEquals("Tie destination $label", e.getBoolean("tieDestination"), note.isTieDestination)
            assertEquals("Let ring $label", e.getBoolean("letRing"), note.isLetRing)
            if (note.isTieDestination) {
                tieDestinations++
                assertNotNull("Tie lost its sounding origin $label", note.tieOrigin)
            }
        }
        assertTrue("Fixture needs tied guitar notes", tieDestinations > 0)
        val report = JSONObject().put("hostOnly", true).put("sourceSha256", sha)
            .put("masterBarsMatched", bars.length()).put("ordinaryWrittenBeatsMatched", beats.length() - graceBeats)
            .put("graceTimingMarkersMatched", graceBeats).put("rawGraceDurationPreserved", false)
            .put("sourceNotesMatched", notes.length()).put("tieDestinationsMatched", tieDestinations)
            .put("deviceAudioVerified", false).put("repeatPlaybackVerified", false)
        File(project, "work/evidence/host/complex-musical-facts.json").writeText(report.toString(2))
    }
}
