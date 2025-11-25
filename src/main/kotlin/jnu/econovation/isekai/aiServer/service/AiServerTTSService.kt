package jnu.econovation.isekai.aiServer.service

import jnu.econovation.isekai.aiServer.client.AiServerTTSClient
import jnu.econovation.isekai.aiServer.dto.request.AiServerTTSRequest
import jnu.econovation.isekai.aiServer.dto.response.AiServerTTSResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Service

@Service
class AiServerTTSService(
    private val client: AiServerTTSClient
) {

    suspend fun tts(
        requestStream : Flow<AiServerTTSRequest>,
        aiServerReadySignal: CompletableDeferred<Unit>
    ) : Flow<AiServerTTSResponse> {
        return flow {
            emitAll(client.tts(requestStream, aiServerReadySignal))
        }
    }
}