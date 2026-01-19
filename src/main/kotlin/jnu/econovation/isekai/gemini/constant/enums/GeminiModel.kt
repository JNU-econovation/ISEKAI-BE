package jnu.econovation.isekai.gemini.constant.enums

enum class GeminiModel(val text: String) {
    GEMINI_EMBEDDING_001("gemini-embedding-001"),
    TEXT_EMBEDDING_004("text-embedding-004"),
    GEMINI_2_5_PRO("gemini-2.5-pro"),
    GEMINI_2_5_FLASH("gemini-2.5-flash"),
    GEMINI_2_5_FLASH_LIVE("gemini-live-2.5-flash-preview"),
    GEMINI_2_5_FLASH_NATIVE_AUDIO("gemini-2.5-flash-native-audio-preview-12-2025"),
    GEMINI_3_0_FLASH("gemini-3-flash-preview"),
    NANO_BANANA("gemini-2.5-flash-image"),
    NANO_BANANA_PRO("gemini-3-pro-image-preview");

    override fun toString(): String {
        return text
    }
}