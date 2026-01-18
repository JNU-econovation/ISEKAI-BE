package jnu.econovation.isekai.aiServer.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.lettuce.core.internal.Futures.await
import jakarta.websocket.ContainerProvider
import jnu.econovation.isekai.aiServer.config.AiServerConfig
import jnu.econovation.isekai.aiServer.dto.request.TTSRequest
import jnu.econovation.isekai.aiServer.dto.response.TTSResponse
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
class TTSClient(
    private val config: AiServerConfig,
    private val handlerFactory: AiServerWebSocketHandlerFactory,
    private val mapper: ObjectMapper
) {
    private companion object {
        const val END_OF_STREAM = "EOS"
        val logger = mu.KotlinLogging.logger {}
        val wsClient = StandardWebSocketClient(
            ContainerProvider.getWebSocketContainer().apply {
                defaultMaxBinaryMessageBufferSize = 1024 * 1024
                defaultMaxTextMessageBufferSize = 1024 * 1024
                defaultMaxSessionIdleTimeout = 60000

                setAsyncSendTimeout(30000)
            }
        ).apply {
            // 연결/핸드셰이크 타임아웃 설정
            // 기본값이 짧아서 Cold Start 때 터지는 것을 방지합니다. (30초로 설정)
            userProperties["org.apache.tomcat.websocket.IO_TIMEOUT_MS"] = "30000"
        }
    }

    suspend fun tts(
        voiceId: Long,
        requestStream: Flow<TTSRequest>,
        aiServerReadySignal: CompletableDeferred<Unit>
    ): Flow<TTSResponse> = channelFlow {
        val handler = handlerFactory.createHandler(channel = this)

        val session = wsClient.execute(
            handler,
            WebSocketHttpHeaders(),
            URI("${config.webSocketUrl}/${voiceId}")
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