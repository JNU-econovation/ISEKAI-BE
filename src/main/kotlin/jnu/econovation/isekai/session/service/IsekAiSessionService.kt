package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.rtzr.service.RtzrSttService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class IsekAiSessionService(
    private val sttService: RtzrSttService,
    private val liveClient: GeminiLiveClient
) {

    suspend fun processVoiceChunk(voiceStream: Flow<ByteArray>, scope: CoroutineScope) {
//        val sttResult = sttService.stt(voiceStream, scope)
//            .filter { it.final }
//            .collect { result -> logger.info { "chunk flow dto -> $result" } }
///
        liveClient.getLiveResponse(voiceStream)
            ?.collect { response -> logger.info { "gemini response -> $response" } }
            ?: logger.info { "gemini response -> null" }
    }
}