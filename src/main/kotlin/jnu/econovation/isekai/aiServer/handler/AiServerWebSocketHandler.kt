package jnu.econovation.isekai.aiServer.handler

import jnu.econovation.isekai.aiServer.dto.response.TTSResponse
import kotlinx.coroutines.channels.SendChannel
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

class AiServerWebSocketHandler(
    private val aiServerChannel: SendChannel<TTSResponse>
) : TextWebSocketHandler() {

    private companion object {
        val logger = mu.KotlinLogging.logger {}
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info { "AI 서버 웹소켓 연결 수립 -> ${session.id}" }
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        logger.debug { "AI 서버 TTS 결과 수신 -> binary message size : ${message.payloadLength}" }

        val buffer = message.payload

        if (message.payloadLength == 0) {
            aiServerChannel.trySend(
                TTSResponse(isFinal = true, payload = ByteArray(0))
            )
            return
        }

        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        aiServerChannel.trySend(
            TTSResponse(isFinal = false, payload = bytes)
        )
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info { "AI 서버 연결 종료 -> ${session.id}, Status: $status" }
        aiServerChannel.close()
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error(exception) { "AI 서버 연결 에러: ${session.id}" }
        aiServerChannel.close(exception)
    }

}