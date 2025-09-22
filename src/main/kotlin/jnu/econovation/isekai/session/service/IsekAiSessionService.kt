package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.rtzr.service.RtzrSttService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class IsekAiSessionService(
    private val sttService: RtzrSttService
) {

    suspend fun processVoiceChunk(voiceStream: Flow<ByteArray>, scope: CoroutineScope) {
        val result = sttService.stt(voiceStream, scope)
            .filter { it.final }
            .collect { result -> logger.info { "final chunk dto -> $result" } }
    }

}