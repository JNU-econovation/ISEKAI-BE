package jnu.econovation.isekai.gemini.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.Client
import com.google.genai.types.*
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel.*
import kotlinx.coroutines.future.await
import mu.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull


@Component
class GeminiClient(
    private val mapper: ObjectMapper,
    config: GeminiConfig
) {
    companion object {
        const val IMAGE_RATIO = "16:9"
        const val IMAGE_SIZE = "4K"

        private val nanoBananaConfig = GenerateContentConfig.builder()
            .imageConfig(
                ImageConfig.builder()
                    .aspectRatio(IMAGE_RATIO)
                    .build()
            )
            .build()

        private val nanoBananaProConfig = GenerateContentConfig.builder()
            .imageConfig(
                ImageConfig.builder()
                    .aspectRatio(IMAGE_RATIO)
                    .imageSize(IMAGE_SIZE)
                    .build()
            )
            .build()

        private val logger = KotlinLogging.logger {}


        private val embeddingConfig = EmbedContentConfig.builder()
            .taskType("SEMANTIC_SIMILARITY")
            .outputDimensionality(768)
            .build()
    }

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

    suspend fun getTextResponse(
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

    fun getImageResponse(
        prompt: String,
        model: GeminiModel = NANO_BANANA
    ): ByteArray {
        val userContent = Content.fromParts(Part.fromText(prompt))

        val response = client.models.generateContent(
            model.toString(),
            userContent,
            buildImageConfig(model)
        )

        logger.debug { "image response -> $response" }
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

    private fun buildImageConfig(model: GeminiModel): GenerateContentConfig {
        return when (model) {
            NANO_BANANA -> nanoBananaConfig
            NANO_BANANA_PRO -> nanoBananaProConfig
            else -> throw InternalServerException(IllegalStateException("나노 바나나가 아닌 모델일 수 없음 -> ${model.toString()}"))
        }
    }
}