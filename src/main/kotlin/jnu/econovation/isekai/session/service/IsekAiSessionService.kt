package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import kotlinx.coroutines.flow.Flow
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class IsekAiSessionService(
    private val liveClient: GeminiLiveClient
) {

    suspend fun processVoiceChunk(voiceStream: Flow<ByteArray>) {
        liveClient.getLiveResponse(voiceStream)
            ?.collect { response -> logger.info { "gemini response -> $response" } }
            ?: logger.info { "gemini response -> null" }
    }
}