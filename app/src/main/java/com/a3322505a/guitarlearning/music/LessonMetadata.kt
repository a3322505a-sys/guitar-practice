package com.a3322505a.guitarlearning.music

enum class TeachingModule(val title: String) {
    FOUNDATIONS("基础启蒙"), CHORDS("和弦教学"), SCALES("音阶教学"), APPLICATION("综合应用")
}
data class TeachingAction(val eventId: String, val kind: String, val text: String, val voicingId: String? = null, val fromVoicingId: String? = null)
data class LessonMetadata(val module: TeachingModule, val key: KeyContext? = null,
    val warmupIds: List<String> = emptyList(), val transitionIds: List<String> = emptyList(),
    val leadTrackIds: List<String> = emptyList(), val chainStarts: List<String> = emptyList(),
    val actions: List<TeachingAction> = emptyList())
