package jnu.econovation.isekai.aiServer.service

import jnu.econovation.isekai.aiServer.client.TTSClient
import jnu.econovation.isekai.aiServer.dto.internal.TTSOutput
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
        input: Flow<String>,
        aiServerReadySignal: CompletableDeferred<Unit>
    ): Flow<TTSOutput> {
        return flow {
            emitAll(client.tts(voiceId, input, aiServerReadySignal))
        }
    }
}