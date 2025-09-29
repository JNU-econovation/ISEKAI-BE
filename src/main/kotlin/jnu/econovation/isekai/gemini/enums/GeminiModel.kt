package jnu.econovation.isekai.gemini.enums

enum class GeminiModel(val text: String) {
    GEMINI_EMBEDDING_001("gemini-embedding-001"),
    TEXT_EMBEDDING_004("text-embedding-004"),
    GEMINI_2_5_FLASH("gemini-2.5-flash"),
    GEMINI_2_5_FLASH_LIVE("gemini-live-2.5-flash-preview");

    override fun toString(): String {
        return text
    }
}