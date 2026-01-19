package jnu.econovation.isekai.gemini.constant.function

import com.google.genai.types.Behavior
import com.google.genai.types.FunctionDeclaration
import jnu.econovation.isekai.gemini.constant.enums.GeminiRestFunctionSignature
import jnu.econovation.isekai.gemini.constant.enums.GeminiLiveFunctionSignature
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema.FINAL_ANSWER_SCHEMA
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema.LONG_TERM_MEMORY_RAG_PARAMS_SCHEMA
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema.LONG_TERM_MEMORY_RAG_RESPONSE_SCHEMA
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema.OK_SCHEMA
import jnu.econovation.isekai.gemini.constant.schema.GeminiSchema.REQUEST_REPLY_SCHEMA

object GeminiFunctions {
    val RAG_SEARCH_FUNCTION: FunctionDeclaration = FunctionDeclaration.builder()
        .description("장기기억 데이터를 얻기 위해 사용하는 RAG search 함수.")
        .name(GeminiRestFunctionSignature.SEARCH_LONG_TERM_MEMORY_RAG.text)
        .parameters(LONG_TERM_MEMORY_RAG_PARAMS_SCHEMA)
        .response(LONG_TERM_MEMORY_RAG_RESPONSE_SCHEMA)
        .build()

    val FINAL_ANSWER_FUNCTION: FunctionDeclaration = FunctionDeclaration.builder()
        .description("최종 응답 함수.")
        .name(GeminiRestFunctionSignature.FINAL_ANSWER.text)
        .parameters(FINAL_ANSWER_SCHEMA)
        .build()

    val REQUEST_REPLY_FUNCTION: FunctionDeclaration = FunctionDeclaration.builder()
        .name(GeminiLiveFunctionSignature.REQUEST_REPLY.text)
        .description("사용자가 Gemini에게 말을 했고, 사용자의 말에 대해 구체적인 대답이나 처리가 필요할 때 호출합니다. 사용자의 발화 내용을 인자로 전달합니다.")
        .parameters(REQUEST_REPLY_SCHEMA)
        .behavior(Behavior.Known.BLOCKING)
        .response(OK_SCHEMA)
        .build()
}