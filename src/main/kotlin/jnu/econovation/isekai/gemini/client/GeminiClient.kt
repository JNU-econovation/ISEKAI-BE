package jnu.econovation.isekai.gemini.client

import com.google.genai.Client
import com.google.genai.types.ContentEmbedding
import com.google.genai.types.EmbedContentConfig
import com.google.genai.types.LiveConnectConfig
import com.google.genai.types.ProactivityConfig
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.gemini.enums.GeminiModel
import kotlinx.coroutines.future.await
import org.springframework.stereotype.Component

@Component
class GeminiClient(
    private val config: GeminiConfig
) {
    private val liveConfig = LiveConnectConfig.builder()
        .responseModalities("AUDIO", "TEXT")
        .proactivity(ProactivityConfig.builder().proactiveAudio(true))
        .build()

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
        val extractedList: List<ContentEmbedding>? = embeddings.orElse(null)

        if (extractedList == null || extractedList.isEmpty())
            throw InternalServerException(IllegalStateException("Gemini 응답이 비었습니다."))

        return extractedList
    }

    suspend fun getLiveResponse(
        model: GeminiModel = GeminiModel.GEMINI_2_5_FLASH_NATIVE_AUDIO_DIALOG
    ) {
        client.async.live.connect(
            model.toString(),
            liveConfig
        )
    }

}