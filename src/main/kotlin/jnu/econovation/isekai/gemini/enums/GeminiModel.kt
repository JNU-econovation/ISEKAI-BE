package jnu.econovation.isekai.gemini.enums

enum class GeminiModel(name: String) {
    GEMINI_EMBEDDING_001("gemini-embedding-001"),
    TEXT_EMBEDDING_004("text-embedding-004"),
    GEMINI_2_5_FLASH_NATIVE_AUDIO_DIALOG("gemini-2.5-flash-preview-native-audio-dialog");

    override fun toString(): String {
        return name
    }
}