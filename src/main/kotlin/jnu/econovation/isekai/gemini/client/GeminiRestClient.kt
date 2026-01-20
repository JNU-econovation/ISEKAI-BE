package jnu.econovation.isekai.gemini.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.Client
import com.google.genai.types.*
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel.*
import jnu.econovation.isekai.gemini.constant.enums.GeminiRestFunctionSignature
import jnu.econovation.isekai.gemini.constant.function.GeminiFunctions
import jnu.econovation.isekai.gemini.dto.client.request.GeminiRestInput
import jnu.econovation.isekai.gemini.dto.client.response.GeminiRestFunctionCall
import jnu.econovation.isekai.prompt.config.PromptConfig
import kotlinx.coroutines.future.await
import mu.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull


@Component
class GeminiRestClient(
    private val mapper: ObjectMapper,
    promptConfig: PromptConfig,
    config: GeminiConfig
) {
    companion object {
        const val IMAGE_RATIO = "16:9"
        const val IMAGE_SIZE = "1K"

        private val SAFETY_SETTINGS = listOf(
            HarmCategory.Known.HARM_CATEGORY_HARASSMENT,
            HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH,
            HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT,
            HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT
        ).map { category ->
            SafetySetting.builder()
                .category(category)
                .threshold(HarmBlockThreshold.Known.BLOCK_ONLY_HIGH)
                .build()
        }

        private val logger = KotlinLogging.logger {}

        private val embeddingConfig = EmbedContentConfig.builder()
            .taskType("SEMANTIC_SIMILARITY")
            .outputDimensionality(768)
            .build()

        private val RAG_SEARCH_TOOL: Tool = Tool.builder()
            .functionDeclarations(listOf(GeminiFunctions.RAG_SEARCH_FUNCTION))
            .build()

        private val FINAL_ANSWER_TOOL: Tool = Tool.builder()
            .functionDeclarations(listOf(GeminiFunctions.FINAL_ANSWER_FUNCTION))
            .build()

        private val GEMINI_3_THINKING_CONFIG = ThinkingConfig
            .builder()
            .thinkingLevel(ThinkingLevel.Known.LOW)
            .build()
    }

    private val nanoBananaConfig = GenerateContentConfig.builder()
        .systemInstruction(Content.fromParts(Part.fromText(promptConfig.backgroundImage)))
        .imageConfig(
            ImageConfig.builder()
                .aspectRatio(IMAGE_RATIO)
                .build()
        )
        .build()

    private val nanoBananaProConfig = GenerateContentConfig.builder()
        .systemInstruction(Content.fromParts(Part.fromText(promptConfig.backgroundImage)))
        .imageConfig(
            ImageConfig.builder()
                .aspectRatio(IMAGE_RATIO)
                .imageSize(IMAGE_SIZE)
                .build()
        )
        .build()

    private val client = Client.builder().apiKey(config.apiKey).build()

    suspend fun getEmbedding(
        text: String,
        model: GeminiModel = GEMINI_EMBEDDING_001
    ): List<ContentEmbedding> {
        val responseFuture =
            client.async.models.embedContent(model.toString(), text, embeddingConfig)
        val response = responseFuture.await()
        val embeddings = response.embeddings()

        return embeddings.orElse(null)
            ?: throw InternalServerException(IllegalStateException("Gemini 응답이 비었습니다."))
    }

    suspend fun getSummaryResponse(
        prompt: String,
        request: Any,
        model: GeminiModel = GEMINI_2_5_FLASH,
        schema: Schema? = null
    ): String {
        val systemInstruction = Content.fromParts(Part.fromText(prompt))
        val finalRequest = request as? String ?: mapper.writeValueAsString(request)
        val userContent = Content.fromParts(Part.fromText(finalRequest))
        val config = buildConfig(systemInstruction, schema)
        val responseFuture = client.async.models.generateContent(
            model.toString(),
            userContent,
            config
        )

        val response = responseFuture.await().text()
            ?: throw InternalServerException(IllegalStateException("Gemini 응답이 비었습니다."))

        return response
    }

    suspend fun getTextDialogResponse(
        context: GeminiRestInput.Context,
        systemPrompt: String,
        userMessage: String,
        functionResponse: GeminiRestInput.ToolResponse? = null,
        model: GeminiModel = GEMINI_3_0_FLASH
    ): GeminiRestFunctionCall {
        logger.info { "rest user message -> $userMessage" }

        val systemInstruction = Content.fromParts(Part.fromText(systemPrompt))
        val tools: List<Tool> =
            if (functionResponse == null) listOf(RAG_SEARCH_TOOL, FINAL_ANSWER_TOOL)
            else listOf(FINAL_ANSWER_TOOL)

        val userRequestPart = Part.fromText(makeUserPrompt(context, userMessage))

        val functionResponsePart: Part? = functionResponse?.let {
            Part.fromFunctionResponse(
                functionResponse.functionName,
                mapOf("result" to functionResponse.result)
            )
        }

        val parts = listOfNotNull(userRequestPart, functionResponsePart)

        val userContent = Content.fromParts(*parts.toTypedArray())

        val config = buildConfig(
            systemInstruction = systemInstruction,
            tools = tools,
            thinkingConfig = GEMINI_3_THINKING_CONFIG
        )

        val response = client.async.models
            .generateContent(model.toString(), userContent, config)
            .await()

        logger.info { "gemini rest response text -> ${response.text()}" }
        logger.info { "gemini rest response functionCalls -> ${response.functionCalls()}" }
        logger.info {
            "gemini rest response finishReason -> ${
                response.finishReason().knownEnum()
            }"
        }

        if (response.text() == null && response.functionCalls() == null) {
            logger.warn { ">>>>> rest gemini 이상한 케이스 : 둘다 null임 -> $response" }
        }

        if (response.finishReason().knownEnum() != FinishReason.Known.STOP) {
            logger.warn { "Gemini가 답변을 완료하지 못했습니다. 이유: ${response.finishReason()}" }
        }

        val functionCalls = response.functionCalls()
        if (!functionCalls.isNullOrEmpty()) {
            val functionCall = functionCalls.first()

            val id = functionCall.id()?.getOrNull()
            val signature = GeminiRestFunctionSignature.fromText(functionCall.name()?.get() ?: "")
                ?: throw InternalServerException(cause = IllegalStateException("Gemini 응답의 함수 이름이 잘못되었습니다."))

            val params = if (functionCall.args()?.getOrNull().isNullOrEmpty()) {
                throw InternalServerException(cause = IllegalStateException("Gemini 응답의 파라미터가 비어있습니다."))
            } else {
                runCatching {
                    mapper.convertValue(
                        functionCall.args()?.getOrNull(),
                        signature.paramsType.java
                    )
                }.getOrElse {
                    throw InternalServerException(cause = IllegalStateException("Gemini params 변환에 실패했습니다: ${it.message}"))
                }
            }

            logger.info { "Function call 감지 -> id: $id, signature: $signature, params: $params" }

            return GeminiRestFunctionCall(id = id, signature = signature, params = params)
        }

        throw InternalServerException(cause = IllegalStateException("Gemini로부터 유효한 응답을 받지 못했습니다."))
    }

    fun getImageResponse(
        prompt: String,
        model: GeminiModel = NANO_BANANA_PRO
    ): ByteArray {
        val userContent = Content.fromParts(Part.fromText(prompt))

        val response = client.models.generateContent(
            model.toString(),
            userContent,
            buildImageConfig(model)
        )

        logger.info { "response -> $response" }

        val parts = response.candidates()
            .get().first()?.content()?.get()?.parts()?.getOrNull()

        if (parts == null)
            throw InternalServerException(IllegalStateException("Gemini 응답이 비었습니다."))

        for (part in parts) {
            if (part.text().isPresent) {
                logger.debug { "part.text -> ${part.text()}" }
                continue
            }
            if (part.inlineData().isPresent)
                return part.inlineData().get().data().get()
        }

        throw InternalServerException(IllegalStateException("Gemini 응답이 비었습니다."))
    }

    private fun buildConfig(
        systemInstruction: Content,
        schema: Schema? = null,
        topP: Float = 1.0f,
        temperature: Float = 1.1f,
        tools: List<Tool>? = null,
        thinkingConfig: ThinkingConfig? = null
    ): GenerateContentConfig {
        val building = GenerateContentConfig.builder()
            .systemInstruction(systemInstruction)
            .candidateCount(1)
            .topP(topP)
            .temperature(temperature)
            .safetySettings(SAFETY_SETTINGS)

        if (thinkingConfig != null) {
            building.thinkingConfig(thinkingConfig)
        }

        if (tools != null) {
            building.tools(tools)
                .toolConfig(
                    ToolConfig.builder()
                        .functionCallingConfig(
                            FunctionCallingConfig.builder()
                                .mode(FunctionCallingConfigMode.Known.ANY)
                                .build()
                        )
                )
        }

        if (schema != null)
            building.responseJsonSchema(schema)
                .responseMimeType("application/json")

        return building.build()
    }

    private fun buildImageConfig(model: GeminiModel): GenerateContentConfig {
        return when (model) {
            NANO_BANANA -> nanoBananaConfig
            NANO_BANANA_PRO -> nanoBananaProConfig
            else -> throw InternalServerException(IllegalStateException("나노 바나나가 아닌 모델일 수 없음 -> ${model.toString()}"))
        }
    }

    private fun makeUserPrompt(
        context: GeminiRestInput.Context,
        userMessage: String
    ): String {
        return "[이전 대화]\n(단기기억) -> ${context.shortTermMemory ?: "없음"}\n(중기기억) -> ${context.midTermMemory ?: "없음"}\n\n현재 사용자 메세지 -> ${userMessage}"
    }
}