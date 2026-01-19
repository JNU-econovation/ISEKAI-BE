package jnu.econovation.isekai.aiServer.client

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.websocket.ContainerProvider
import jnu.econovation.isekai.aiServer.config.AiServerConfig
import jnu.econovation.isekai.aiServer.dto.internal.TTSResult
import jnu.econovation.isekai.aiServer.dto.request.TTSRequest
import jnu.econovation.isekai.aiServer.factory.AiServerWebSocketHandlerFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
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
                defaultMaxSessionIdleTimeout = 1800000

                setAsyncSendTimeout(30000)
            }
        ).apply {
            // 연결/핸드셰이크 타임아웃 설정
            // 기본값이 짧아서 Cold Start 때 터지는 것을 방지합니다. (60초로 설정)
            userProperties["org.apache.tomcat.websocket.IO_TIMEOUT_MS"] = "60000"
        }
    }

    suspend fun tts(
        voiceId: Long,
        requestStream: Flow<String>,
        aiServerReadySignal: CompletableDeferred<Unit>
    ): Flow<TTSResult> = channelFlow {
        val handler = handlerFactory.createHandler(channel = this)

        logger.info { "TTS 서버 웹소켓 연결 중" }

        val session = wsClient.execute(
            handler,
            WebSocketHttpHeaders(),
            URI("${config.webSocketUrl}/${voiceId}")
        ).await()

        logger.info { "TTS 서버 웹소켓 연결 성공!" }

        aiServerReadySignal.complete(Unit)
        logger.info { "AI Server TTS WebSocket 연결 수립 및 신호 전송 완료" }

        launch {
            runCatching {
                requestStream
                    .map { TTSRequest(prompt = it) }
                    .collect { request ->
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