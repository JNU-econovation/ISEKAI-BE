package jnu.econovation.isekai.gemini.constant.function

import com.google.genai.types.Behavior
import com.google.genai.types.FunctionDeclaration
import jnu.econovation.isekai.gemini.constant.enums.GeminiFunctionSignature.*
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema.EMOTION_SCHEMA
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema.LONG_TERM_MEMORY_RAG_PARAMS_SCHEMA
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema.LONG_TERM_MEMORY_RAG_RESPONSE_SCHEMA
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema.TEXT_RESPONSE_SCHEMA

object GeminiFunctions {
    val RAG_SEARCH_FUNCTION: FunctionDeclaration = FunctionDeclaration.builder()
        .description("장기기억 데이터를 얻기 위해 사용하는 RAG search 함수.")
        .name(SEARCH_LONG_TERM_MEMORY_RAG.text)
        .parameters(LONG_TERM_MEMORY_RAG_PARAMS_SCHEMA)
        .response(LONG_TERM_MEMORY_RAG_RESPONSE_SCHEMA)
        .behavior(Behavior.Known.BLOCKING)
        .build()

    val EMOTION_FUNCTION: FunctionDeclaration = FunctionDeclaration.builder()
        .description("Gemini의 현재 감정 상태를 업데이트합니다.")
        .name(EMOTION.text)
        .parameters(EMOTION_SCHEMA)
        .behavior(Behavior.Known.NON_BLOCKING)
        .build()

    val TEXT_RESPONSE_FUNCTION: FunctionDeclaration = FunctionDeclaration.builder()
        .name(RESPONSE_TEXT.text)
        .description("한국어로 대답한다.")
        .parameters(TEXT_RESPONSE_SCHEMA)
        .behavior(Behavior.Known.NON_BLOCKING)
        .build()
}