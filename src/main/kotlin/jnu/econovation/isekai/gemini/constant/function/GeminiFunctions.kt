package jnu.econovation.isekai.gemini.constant.function

import com.google.genai.types.Behavior
import com.google.genai.types.FunctionDeclaration
import jnu.econovation.isekai.gemini.constant.enums.GeminiFunctionSignature
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema

object GeminiFunctions {
    val RAG_SEARCH_FUNCTION: FunctionDeclaration = FunctionDeclaration.builder()
        .description("장기기억 데이터를 얻기 위해 사용하는 RAG search 함수.")
        .name(GeminiFunctionSignature.SEARCH_LONG_TERM_MEMORY_RAG.text)
        .parameters(GeminiSchema.LONG_TERM_MEMORY_RAG_PARAMS_SCHEMA)
        .response(GeminiSchema.LONG_TERM_MEMORY_RAG_RESPONSE_SCHEMA)
        .behavior(Behavior.Known.BLOCKING)
        .build()


    val EMOTION_FUNCTION: FunctionDeclaration = FunctionDeclaration.builder()
        .description("Gemini의 현재 감정 상태를 업데이트합니다. 해당 함수를 적극적으로 사용하세요.")
        .name(GeminiFunctionSignature.EMOTION.text)
        .parameters(GeminiSchema.EMOTION_SCHEMA)
        .behavior(Behavior.Known.BLOCKING)
        .build()
}