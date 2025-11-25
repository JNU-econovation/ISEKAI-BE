package jnu.econovation.isekai.gemini.constant.function

import com.google.common.collect.ImmutableMap
import com.google.genai.types.*

object GeminiFunctionTools {
    const val RESPONSE_TEXT = "responseText"

    val TEXT_RESPONSE_TOOL: Tool = Tool.builder()
        .functionDeclarations(
            listOf(
                FunctionDeclaration.builder()
                    .name(RESPONSE_TEXT)
                    .description("2개의 언어 (한국어와 일본어)로 대답한다.")
                    .behavior(Behavior.Known.NON_BLOCKING)
                    .parameters(
                        Schema.builder()
                            .type(Type.Known.OBJECT)
                            .properties(
                                ImmutableMap.of(
                                    "krText",
                                    Schema.builder()
                                        .description("한국어 버전의 텍스트 응답")
                                        .type(Type.Known.STRING)
                                        .build(),
                                    "jpText",
                                    Schema.builder()
                                        .description("일본어 버전의 텍스트 응답")
                                        .type(Type.Known.STRING)
                                        .build()
                                )
                            )
                            .required("krText", "jpText")
                            .build()
                    )
                    .build()
            )
        )
        .build()

    val TEXT_RESPONSE_TOOL_CONFIG: ToolConfig = ToolConfig.builder()
        .functionCallingConfig(
            FunctionCallingConfig.builder()
                .mode(FunctionCallingConfigMode.Known.ANY)
                .allowedFunctionNames(listOf(RESPONSE_TEXT))
                .build()
        )
        .build()
}