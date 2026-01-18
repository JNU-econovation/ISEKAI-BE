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
        .description("Gemini의 현재 감정 상태를 업데이트합니다. 대화를 시작하기 전에 이 함수를 가장 먼저 호출하여 표정을 설정해야 합니다. 반드시 텍스트 발화를 시작하기 **전(Before)**에 호출하세요.")
        .name(GeminiFunctionSignature.EMOTION.text)
        .parameters(GeminiSchema.EMOTION_SCHEMA)
        .behavior(Behavior.Known.BLOCKING)
        .build()
}