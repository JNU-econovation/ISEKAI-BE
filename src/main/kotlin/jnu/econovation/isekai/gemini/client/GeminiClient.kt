package jnu.econovation.isekai.gemini.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.Client
import com.google.genai.types.*
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel
import kotlinx.coroutines.future.await
import org.springframework.stereotype.Component


@Component
class GeminiClient(
    private val mapper: ObjectMapper,
    config: GeminiConfig
) {
    private val client = Client.builder().apiKey(config.apiKey).build()

    suspend fun getEmbedding(
        text: String,
        model: GeminiModel = GeminiModel.GEMINI_EMBEDDING_001
    ): List<ContentEmbedding> {
        val config = EmbedContentConfig.builder()
            .taskType("SEMANTIC_SIMILARITY")
            .outputDimensionality(768)
            .build()

        val responseFuture = client.async.models.embedContent(model.toString(), text, config)
        val response = responseFuture.await()
        val embeddings = response.embeddings()

        return embeddings.orElse(null)
            ?: throw InternalServerException(IllegalStateException("Gemini 응답이 비었습니다."))
    }

    suspend fun getTextResponse(
        prompt: String,
        request: Any,
        model: GeminiModel = GeminiModel.GEMINI_2_5_FLASH,
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

    private fun buildConfig(
        systemInstruction: Content,
        schema: Schema? = null,
        thinkingBudget: Int = -1
    ): GenerateContentConfig {

        val building = GenerateContentConfig.builder()
            .systemInstruction(systemInstruction)
            .candidateCount(1)
            .thinkingConfig(ThinkingConfig.builder().thinkingBudget(thinkingBudget).build())

        if (schema != null)
            building.responseJsonSchema(schema)
                .responseMimeType("application/json")

        return building.build()
    }

}