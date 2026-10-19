package com.a3322505a.guitarlearning.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.music.*
import com.a3322505a.guitarlearning.practice.*

/** Derived display values; this object stores no preferences or playback state. */
internal data class PracticeContentState(
    val page: Int,
    val chordPhrases: Boolean,
    val tab: Boolean,
    val tabletControls: Boolean,
    val scale: ScaleOptions,
    val first: Chord,
    val phrase: Phrase,
    val melody: Boolean,
    val sequence: Sequence?,
    val displayedSequence: Sequence?,
    val scaleResult: RouteResult,
    val hint: Hint,
    val renderHint: Hint,
    val active: Boolean,
    val phase: Phase,
    val renderCursor: Cursor?,
    val cursor: Cursor?,
    val showingSession: Boolean,
    val event: Event?,
    val phraseNow: Event?,
    val nextEvent: Event?,
    val moving: MovingPosition?,
    val current: List<Position>,
    val shownChord: Chord?,
    val nextChord: String,
    val status: String,
    val note: String,
    val beatsPerBar: Int,
    val chords: List<Chord> = Chords.all,
    val configuring: Boolean = false,
    val previewPositions: List<Position> = emptyList(),
    val upcomingPositions: List<Position> = emptyList(),
    val previewAt: Double = 0.0,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PracticeContent(
    state: PracticeContentState,
    modifier: Modifier,
    onChord: (Chord) -> Unit,
    onView: (Int) -> Unit,
    onTap: (Position) -> Unit,
    onAudition: (Chord) -> Unit,
    legacyTab: (@Composable (PracticeContentState, Modifier, (Int) -> Unit) -> Unit)? = null,
    onHint: () -> Unit = {},
) = with(state) {
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    if (displayedSequence == null && (page != 1 || chordPhrases)) {
        PracticeContentCard(
            title = "练习舞台",
            actions = { ViewSwitch(tab, onView) },
            modifier = modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (note.isNotBlank()) note else scaleResult.reason.ifBlank { "正在准备曲目…" }, color = colors.textSecondary)
            }
        }
        return@with
    }
    if (tab) {
        checkNotNull(legacyTab)(state, modifier, onView)
        return@with
    }
    fun eventLabel(value: Event?) = if (value == null || value.rest) "留拍" else listOf(
        value.positions.joinToString { "${it.string} 弦 ${it.fret} 品 ${Music.name(displayedSequence!!.midi(it), true)}" },
        value.expressionLabel(),
    ).filter { it.isNotBlank() }.joinToString(" ")

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableHeight = maxHeight
        val compact = configuring || maxHeight < (if (tabletControls) 340.dp else 480.dp)
        val fontScale = LocalDensity.current.fontScale
        // Decide from the complete route, so rests/dead notes never resize the card.
        val openStringsOnly = melody && displayedSequence!!.route.isNotEmpty() && displayedSequence.route.all { it.fret == 0 }
        // A short window scrolls the whole chord page, never labels inside its diagram.
        val chordPageScroll = !configuring && maxHeight < (if(page==0) 280.dp else 560.dp) * fontScale
        val chordScrollState = remember(page) { ScrollState(0) }

        val cardContent: @Composable (Chord, Boolean) -> Unit = card@{ cardChord, selected ->
            val cardModifier = (if (openStringsOnly) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .then(if (selected) Modifier.testTag("practice-card") else Modifier.clearAndSetSemantics {})
            val unavailable = cardChord.unavailableReason
            if (unavailable != null) {
                PracticeContentCard(
                    title = cardChord.name,
                    actions = { if (selected) ViewSwitch(false, onView) },
                    modifier = cardModifier,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ChordSteps(first, onChord, chords)
                        Text("左右滑动切换和弦", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                    Text(unavailable, color = colors.danger)
                }
                return@card
            }

            val phraseCard = page == 1 && chordPhrases
            val chordTones = cardChord.positions.distinctBy { cardChord.midi(it) % 12 }
                .sortedBy { (cardChord.midi(it) - cardChord.root) % 12 }
            val liveHeadline = when {
                phraseCard -> phrase.passage.ifBlank { phrase.title }
                page == 1 -> "${cardChord.name} · 逐弦检查"
                melody -> phrase.title
                hint == Hint.FULL && event != null -> if (event.rest) "现在：留拍" else "现在：${eventLabel(event)}"
                else -> "${Patterns.forKey(scale.key,scale.pentatonic)[scale.pattern].title} · 每拍${scale.notesPerBeat}音"
            }

            PracticeContentCard(
                modifier = cardModifier,
                footer = {
                    val progressRatio = if (selected && showingSession && cursor?.pre == false && displayedSequence != null && displayedSequence.length > 0) {
                        (cursor.localTick.toFloat() / displayedSequence.length.toFloat()).coerceIn(0f, 1f)
                    } else null
                    PracticeStatus(
                        if (selected) status else "准备好，就开始一轮",
                        phase,
                        beatsPerBar,
                        if (selected) cursor else null,
                        selected && showingSession,
                        compact,
                        note = if (selected && hint == Hint.FULL) note else "",
                        progress = progressRatio,
                    )
                },
            ) {
                // L3 Stage Live HUD Header: left side shows non-redundant live performance cues; right side embeds [指板 | 六线谱].
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            liveHeadline,
                            modifier = Modifier.weight(1f),
                            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!phraseCard && page == 0 && hint == Hint.FULL && nextEvent != null && !configuring) {
                            val nextText = "下一：" + if (nextEvent.positions.isEmpty()) "留拍" else nextEvent.positions.joinToString { Music.name(it.midi, true) }
                            Surface(
                                color = colors.primaryContainer,
                                shape = RoundedCornerShape(metrics.cornerSmall),
                                border = BorderStroke(metrics.borderWidth, colors.primary.copy(alpha = 0.4f)),
                            ) {
                                Text(
                                    nextText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primaryText,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (selected) {
                            BoardLabelMenu(hint, onHint, title = if (page == 0) "音名" else "指法")
                            ViewSwitch(false, onView)
                        }
                    }

                    if(page==1 && !chordPhrases && selected) {
                        val slot=ChordSlots.slot(cardChord)
                        if(slot!=null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            ChordSlots.choices(cardChord,chords).forEach { choice ->
                                FilterChip(selected=choice.id==cardChord.id,onClick={ onChord(choice) },label={
                                    Text(if(choice.name.substringAfterLast(" · ")==slot.original) "原配 ${slot.original}" else "简化替代 ${slot.beginner}")
                                })
                            }
                        }
                    }
                    if (phraseCard) {
                        val cue = if (hint == Hint.HIDDEN) "已撤提示" else displayedSequence?.runtimePlan?.let {
                            planCue(it, if (showingSession) cursor else null,previewAt)
                        } ?: if (!melody) "${cardChord.name} · ${phraseCue(phraseNow, displayedSequence!!)}"
                        else phraseCue(phraseNow, displayedSequence!!)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                color = colors.primaryContainer,
                                shape = RoundedCornerShape(metrics.cornerSmall),
                                modifier = Modifier.weight(1f, fill = false).height(30.dp * fontScale),
                            ) {
                                Text(
                                    cue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).testTag("phrase-cue"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!melody && nextChord.isNotBlank()) {
                                Text(
                                    "下一：$nextChord",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    } else if (page == 1) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ChordSteps(first, onChord, chords)
                            Column(Modifier.weight(1f)) {
                                if (cardChord.capoFret > 0 || cardChord.openStringMidi != Music.openMidi) {
                                    Text(
                                        cardChord.conditions,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("chord-conditions"),
                                    )
                                }
                                if (hint != Hint.HIDDEN && !configuring) {
                                    Text("按法 ${cardChord.name} · 实音 ${cardChord.soundingName}",style=MaterialTheme.typography.bodySmall)
                                    Text(cardChord.toneSummary + " · 从${cardChord.positions.maxOf { it.string }}弦拨起",style=MaterialTheme.typography.bodySmall)
                                    Text(cardChord.definition?.quality?.description ?: "${cardChord.soundingName} · 按实际构成音发声",style=MaterialTheme.typography.bodySmall)
                                    Text("逐弦自查：指尖靠品丝，避开邻弦；哑音先检查触弦与按压。",style=MaterialTheme.typography.bodySmall)
                                    Text(
                                        chordTones.filter { hint != Hint.ROOTS || cardChord.isRoot(it) }
                                            .joinToString(" · ") { "${cardChord.role(it)} ${cardChord.noteName(it)}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else {
                                    Text("左右滑动整卡切换", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = { onAudition(cardChord) },
                                enabled = selected && !active && phase != Phase.PAUSED,
                                modifier = Modifier.heightIn(min = 36.dp),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(metrics.borderWidth, colors.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.primaryContainer, contentColor = colors.primaryText),
                            ) {
                                Text("听和弦", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (!compact && !active) {
                        Text(
                            sequence?.explanation ?: scaleResult.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (hint == Hint.HIDDEN && !phraseCard) {
                        Text("已撤提示", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // L3 Instrument Canvas Well
                Box(
                    (if (openStringsOnly) Modifier.height(128.dp * fontScale) else Modifier.weight(1f))
                        .fillMaxWidth()
                        .testTag("diagram-viewport"),
                    contentAlignment = Alignment.Center,
                ) {
                    DiagramContainer(Modifier.fillMaxSize()) {
                        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val viewportHeight = maxHeight
                            if (page == 0) {
                                val h = maxOf(if (configuring) 80.dp else 176.dp * fontScale, viewportHeight).coerceAtMost(310.dp * fontScale)
                                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                                    val positions = if (scale.exercise == ScaleExercise.CONNECTION && scale.pattern < Patterns.forKey(scale.key,scale.pentatonic).lastIndex) {
                                        (Patterns.forKey(scale.key,scale.pentatonic)[scale.pattern].positions + Patterns.forKey(scale.key,scale.pentatonic)[scale.pattern + 1].positions).distinct()
                                    } else {
                                        Patterns.forKey(scale.key,scale.pentatonic)[scale.pattern].positions
                                    }
                                    Fretboard(
                                        positions,
                                        scale.key,
                                        renderHint,
                                        false,
                                        current,
                                        !active && phase != Phase.PAUSED,
                                        h,
                                        onTap,
                                        showLegend = !configuring && !compact,
                                    )
                                }
                            } else if (melody && displayedSequence!!.route.isNotEmpty()) {
                                Fretboard(
                                    displayedSequence.route,
                                    null,
                                    renderHint,
                                    false,
                                    if (showingSession) current else if(displayedSequence.runtimePlan!=null) previewPositions else phraseNow?.positions.orEmpty(),
                                    !active && phase != Phase.PAUSED,
                                    viewportHeight.coerceAtMost(310.dp * fontScale),
                                    onTap,
                                    moving,
                                    if(displayedSequence.runtimePlan!=null) upcomingPositions else nextEvent?.positions.orEmpty(),
                                    showLegend = false,
                                    capoFret = displayedSequence.capoFret,
                                    openStringMidi = displayedSequence.runtimePlan?.mainTrack?.openStringMidi ?: Music.openMidi.toList(),
                                    movingPositions = cursor?.movingPositions ?: moving?.let { listOf(it) }.orEmpty(),
                                )
                            } else if (melody) {
                                Text(if (hint == Hint.HIDDEN) "按节拍演奏" else "死音节奏", color = colors.textSecondary)
                            } else {
                                ChordDiagram(
                                    cardChord,
                                    if (cardChord.root < 0 && hint == Hint.ROOTS) Hint.FULL else hint,
                                    if (selected) current else emptyList(),
                                    selected && !active && phase != Phase.PAUSED,
                                    viewportHeight.coerceAtMost(390.dp * fontScale).coerceAtMost(availableHeight),
                                    onTap,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full-width stage without the legacy 240.dp empty side hint card.
        Box(
            Modifier.fillMaxSize()
                .testTag("chord-page")
                .then(if (chordPageScroll) Modifier.verticalScroll(chordScrollState) else Modifier),
        ) {
            Box(Modifier.fillMaxWidth().height(if (chordPageScroll) (if(page==0) 300.dp else 580.dp) * fontScale else availableHeight)) {
                if (page == 1 && !chordPhrases) {
                    ChordDeck(
                        chords.indexOf(first),
                        chords.size,
                        { onChord(chords[it]) },
                        Modifier.fillMaxSize(),
                    ) { index, selected -> cardContent(chords[index], selected) }
                } else {
                    Box(Modifier.fillMaxSize()) { cardContent(shownChord ?: first, true) }
                }
            }
        }
    }
}

/** Integrated L4 Telemetry & Beat Matrix strip shared by fretboard/chord diagrams and native score stage. */
@Composable
internal fun PracticeStatus(
    status: String,
    phase: Phase,
    beatsPerBar: Int,
    cursor: Cursor?,
    showingSession: Boolean,
    compact: Boolean,
    note: String = "",
    progress: Float? = null,
) {
    val colors = PracticeSkin.colors
    val metrics = PracticeSkin.metrics
    val fontScale = LocalDensity.current.fontScale
    Surface(
        color = colors.surfaceSecondary,
        shape = RoundedCornerShape(metrics.cornerSmall),
        border = BorderStroke(metrics.borderWidth, colors.border.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth().testTag("playback-status"),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = (if (compact) 36.dp else 42.dp) * fontScale)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (phase == Phase.ERROR) colors.danger else colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (note.isNotBlank() && !compact) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(beatsPerBar) { i ->
                        val activeBeat = i == cursor?.beat && showingSession
                        val isDownbeat = i == 0
                        Box(
                            Modifier.size(
                                width = if (activeBeat) 12.dp else if (isDownbeat) 10.dp else 8.dp,
                                height = if (activeBeat) 12.dp else if (isDownbeat) 10.dp else 8.dp,
                            ).background(
                                if (activeBeat) colors.progressComplete else if (isDownbeat) colors.primary.copy(alpha = 0.3f) else colors.progressPending,
                                RoundedCornerShape(metrics.cornerSmall),
                            ),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = colors.progressComplete,
                        trackColor = colors.progressPending,
                    )
                }
            }
        }
    }
}
