package com.forgeprom.studio.domain

enum class SceneTone(val label: String, val guidance: String) {
    BALANCED("متوازن", "balanced, grounded observation"),
    TENSE("متوتر", "build tension through pauses and restrained gestures"),
    MYSTERIOUS("غامض", "use selective detail and withhold explanations"),
    WARM("دافئ", "favor intimate, understated interactions")
}

enum class SceneStyle(val label: String, val guidance: String) {
    CLASSIC("كلاسيكي", "clear staging and economical action"),
    VISUAL("بصري وتأملي", "visual composition, silence and deliberate observation"),
    DIALOGUE("حواري", "distinct voices and subtext carried by dialogue")
}

enum class SceneLighting(val label: String, val guidance: String) {
    NATURAL("طبيعية", "natural light motivated by the setting"),
    LOW_KEY("خافتة", "low-key illumination and deep shadows"),
    HIGH_KEY("ساطعة", "bright, even lighting")
}

data class SceneDirection(
    val pace: Int = 5,
    val tone: SceneTone = SceneTone.BALANCED,
    val camera: Boolean = false,
    val sound: Boolean = false,
    val style: SceneStyle = SceneStyle.CLASSIC,
    val lighting: SceneLighting = SceneLighting.NATURAL,
    val actorCues: Boolean = false
) {
    init { require(pace in 1..10) }

    fun instructions(): String = "Director settings: pace $pace/10 (1 deliberate, 10 brisk); ${tone.guidance}. " +
        (if (camera) "Include concise shot directions where useful. " else "Avoid explicit camera directions. ") +
        (if (sound) "Include purposeful environmental audio cues." else "Avoid separate sound-design cues.") +
        " Style: ${style.guidance}. Lighting: ${lighting.guidance}. " +
        (if (actorCues) "Add brief performance cues in dialogue parentheticals where useful." else "Avoid explicit performance parentheticals.")
}
