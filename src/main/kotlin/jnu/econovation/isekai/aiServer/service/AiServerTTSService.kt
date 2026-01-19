package jnu.econovation.isekai.aiServer.service

import jnu.econovation.isekai.aiServer.client.TTSClient
import jnu.econovation.isekai.aiServer.dto.internal.TTSResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Service

@Service
class AiServerTTSService(
    private val client: TTSClient
) {

    suspend fun tts(
        voiceId: Long,
        requestStream: Flow<String>,
        aiServerReadySignal: CompletableDeferred<Unit>
    ): Flow<TTSResult> {
        return flow {
            emitAll(client.tts(voiceId, requestStream, aiServerReadySignal))
        }
    }
}