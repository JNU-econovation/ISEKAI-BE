package jnu.econovation.isekai.aiServer.service

import jnu.econovation.isekai.aiServer.client.TTSClient
import jnu.econovation.isekai.aiServer.dto.request.TTSRequest
import jnu.econovation.isekai.aiServer.dto.response.TTSResponse
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
        requestStream : Flow<TTSRequest>,
        aiServerReadySignal: CompletableDeferred<Unit>
    ) : Flow<TTSResponse> {
        return flow {
            emitAll(client.tts(requestStream, aiServerReadySignal))
        }
    }
}