package jnu.econovation.isekai.gemini.constant.enums

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
        fun from(text: String): GeminiEmotion? {
            return entries.find { it.text == text }
        }
    }
}