package jnu.econovation.isekai.rtzr.client.handler

import mu.KotlinLogging
import kotlinx.coroutines.channels.SendChannel
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import com.fasterxml.jackson.databind.ObjectMapper
import jnu.econovation.isekai.rtzr.dto.client.response.RtzrSttResponse
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import kotlin.jvm.java

class RtzrSttWebSocketHandler(
    private val rtzrChannel: SendChannel<RtzrSttResponse>,
    private val mapper: ObjectMapper
) : TextWebSocketHandler() {

    private val logger = KotlinLogging.logger {}

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info { "RTZR STT 연결 수립 -> ${session.id}" }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val result = mapper.readValue(message.payload, RtzrSttResponse::class.java)
        logger.debug { "STT 결과 수신 -> $result" }
        rtzrChannel.trySend(result)
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus
    ) {
        logger.info { "RTZR STT 연결 종료 -> ${session.id}, Status: $status" }
        rtzrChannel.close()
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error(exception) { "RTZR STT 연결 에러: ${session.id}" }
        rtzrChannel.close(exception)
    }
}