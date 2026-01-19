package jnu.econovation.isekai.gemini.constant.enums

import mu.KotlinLogging

enum class GeminiEmotion(val text: String) {
    SAD("SAD"),
    SHY("SHY"),
    HAPPY("HAPPY"),
    ANGRY("ANGRY"),
    NEUTRAL("NEUTRAL"),
    SURPRISED("SURPRISED"),
    DESPISE("DESPISE");

    override fun toString(): String {
        return text
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        fun from(input: String): GeminiEmotion {

            entries.find { it.text.equals(input, ignoreCase = true) }?.let {
                return it
            }

            return when (input.uppercase()) {
                "AMUSED", "EXCITED", "JOY", "LAUGHING", "FUNNY", "PLEASED" -> HAPPY

                "DEPRESSED", "GRIEF", "CRYING", "DISAPPOINTED", "UPSET" -> SAD

                "ANNOYED", "FRUSTRATED", "HATE", "IRRITATED" -> ANGRY

                "SHOCKED", "STARTLED", "AMAZED" -> SURPRISED

                "DISGUSTED", "SCORN", "LOATHE" -> DESPISE

                "EMBARRASSED", "ASHAMED", "FLUSTERED" -> SHY

                else -> {
                    logger.warn { "Gemini returned undefined emotion: '$input'. Defaulting to NEUTRAL." }
                    NEUTRAL
                }
            }
        }
    }
}