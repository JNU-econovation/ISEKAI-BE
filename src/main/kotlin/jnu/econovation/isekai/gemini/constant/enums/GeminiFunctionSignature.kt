package jnu.econovation.isekai.gemini.constant.enums

import jnu.econovation.isekai.gemini.dto.client.response.GeminiFunctionParams
import kotlin.reflect.KClass

enum class GeminiFunctionSignature(
    val paramsType: KClass<out GeminiFunctionParams>
) {
    SEARCH_LONG_TERM_MEMORY_RAG(GeminiFunctionParams.SearchLongTermMemoryRAG::class),
    EMOTION(GeminiFunctionParams.Emotion::class);

    val text: String
        get() = this.name

    companion object {
        fun fromText(text: String):GeminiFunctionSignature? {
            return entries.find { it.text == text }
        }
    }
}