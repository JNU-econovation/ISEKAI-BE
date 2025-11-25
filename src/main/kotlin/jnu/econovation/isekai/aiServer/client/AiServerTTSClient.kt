package jnu.econovation.isekai.aiServer.client

import com.fasterxml.jackson.databind.ObjectMapper
import jnu.econovation.isekai.aiServer.config.AiServerConfig
import jnu.econovation.isekai.aiServer.dto.request.AiServerTTSRequest
import jnu.econovation.isekai.aiServer.dto.response.AiServerTTSResponse
import jnu.econovation.isekai.aiServer.factory.AiServerWebSocketHandlerFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import java.net.URI

@Component
class AiServerTTSClient(
    private val config: AiServerConfig,
    private val handlerFactory: AiServerWebSocketHandlerFactory,
    private val mapper: ObjectMapper
) {
    private companion object {
        const val END_OF_STREAM = "EOS"
        val logger = mu.KotlinLogging.logger {}
        val wsClient = StandardWebSocketClient()
    }

    suspend fun tts(
        requestStream: Flow<AiServerTTSRequest>,
        aiServerReadySignal: CompletableDeferred<Unit>
    ): Flow<AiServerTTSResponse> = channelFlow {
        val handler = handlerFactory.createHandler(channel = this)

        val session = wsClient.execute(
            handler,
            WebSocketHttpHeaders(),
            URI(config.webSocketUrl)
        ).await()

        aiServerReadySignal.complete(Unit)
        logger.info { "AI Server TTS WebSocket 연결 수립 및 신호 전송 완료" }

        launch {
            runCatching {
                requestStream.collect { request ->
                    session.sendMessage(TextMessage(mapper.writeValueAsString(request)))
                }
                session.sendMessage(TextMessage(END_OF_STREAM))
            }.onFailure { exception ->
                logger.error(exception) { "TTS 스트림 전송 중 에러 발생" }
                session.close()
            }
        }

        awaitClose {
            logger.info { "AiServerTTSClient 종료 (awaitClose)" }
            session.close()
        }
    }
}