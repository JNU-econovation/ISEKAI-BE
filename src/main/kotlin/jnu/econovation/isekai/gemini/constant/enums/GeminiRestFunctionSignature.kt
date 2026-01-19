package jnu.econovation.isekai.gemini.constant.enums

import jnu.econovation.isekai.gemini.dto.client.response.GeminiRestFunctionParams
import kotlin.reflect.KClass

enum class GeminiRestFunctionSignature(
        val paramsType: KClass<out GeminiRestFunctionParams>
) {
    SEARCH_LONG_TERM_MEMORY_RAG(GeminiRestFunctionParams.SearchLongTermMemoryRAG::class),
    FINAL_ANSWER(GeminiRestFunctionParams.FinalAnswer::class);

    val text: String
        get() = this.name

    companion object {
        fun fromText(text: String):GeminiRestFunctionSignature? {
            return entries.find { it.text == text }
        }
    }
}