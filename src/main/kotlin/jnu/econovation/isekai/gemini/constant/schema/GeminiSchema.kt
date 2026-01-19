package jnu.econovation.isekai.gemini.constant.schema

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.genai.types.Schema
import com.google.genai.types.Type
import jnu.econovation.isekai.gemini.constant.enums.GeminiEmotion

object GeminiSchema {
    val REQUEST_REPLY_SCHEMA: Schema = Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(
            ImmutableMap.of(
                "userMessage",
                Schema.builder()
                    .type(Type.Known.STRING)
                    .description("사용자가 Gemini에게 말한 내용의 텍스트")
                    .build()
            )
        )
        .required(listOf("userMessage"))
        .build()

    val LONG_TERM_MEMORY_RAG_PARAMS_SCHEMA: Schema = Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(
            ImmutableMap.of(
                "searchText",
                Schema.builder()
                    .type(Type.Known.STRING)
                    .description("장기기억으로 검색할 키워드이다.")
                    .build()
            )
        )
        .required(ImmutableList.of("searchText"))
        .build()

    val LONG_TERM_MEMORY_RAG_RESPONSE_SCHEMA: Schema = Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(
            ImmutableMap.of(
                "result",
                Schema.builder().type(Type.Known.STRING).build()
            )
        )
        .description("장기기억을 RAG SEARCH 한 결과물이다. 앞에 있는 요약일 수록 유사도가 큰 요약이다.")
        .build()

    val FINAL_ANSWER_SCHEMA: Schema = Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(
            ImmutableMap.of(
                "krTextResponse",
                Schema.builder()
                    .description("한국어 텍스트 응답이다.")
                    .type(Type.Known.STRING)
                    .build(),
                "emotion",
                Schema.builder()
                    .description("Gemini가 느끼고 있는 감정이다. 반드시 ${enumValues<GeminiEmotion>().map { it.text }} 중 하나만 응답해야 한다.")
                    .enum_(enumValues<GeminiEmotion>().map { it.text })
                    .type(Type.Known.STRING)
                    .build()
            )
        )
        .required("krTextResponse", "emotion")
        .build()

    val OK_SCHEMA: Schema = Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(
            ImmutableMap.of(
                "result",
                Schema.builder()
                    .type(Type.Known.STRING)
                    .build()
            )
        )
        .build()
}