package jnu.econovation.isekai.aiServer.handler

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import jnu.econovation.isekai.aiServer.dto.response.AiServerErrorResponse
import jnu.econovation.isekai.aiServer.dto.response.TTSResponse
import jnu.econovation.isekai.aiServer.exception.NoSuchVoiceException
import jnu.econovation.isekai.common.exception.server.InternalServerException
import kotlinx.coroutines.channels.SendChannel
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

class AiServerWebSocketHandler(
    private val aiServerChannel: SendChannel<TTSResponse>,
    private val mapper: ObjectMapper
) : TextWebSocketHandler() {

    private companion object {
        val logger = mu.KotlinLogging.logger {}
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info { "AI 서버 웹소켓 연결 수립 -> ${session.id}" }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = message.payload

        logger.debug { "AI 서버 텍스트 메세지 수신 -> $payload" }

        runCatching {
            val rootNode = mapper.readTree(payload)

            if (rootNode.has("status") && rootNode["status"].asText() == "error") {

                val errorResponse = mapper.treeToValue(rootNode, AiServerErrorResponse::class.java)

                logger.error { "AI 서버 에러 발생: Code=${errorResponse.code}, Msg=${errorResponse.message}" }

                val exception = when (errorResponse.code) {
                    "400_001", "500_001", "500_002", "500_999" -> {
                        InternalServerException(cause = IllegalStateException(errorResponse.message))
                    }
                    "400_002" -> {
                        NoSuchVoiceException()
                    }
                    else -> {
                        InternalServerException(cause = IllegalStateException("Unknown Error: ${errorResponse.message}"))
                    }
                }

                aiServerChannel.close(exception)
                session.close(CloseStatus.SERVER_ERROR)
                return@runCatching
            }

            logger.info { "AI 서버 상태/일반 메시지: $payload" }

        }.onFailure {
            when (it) {
                is JsonProcessingException -> {
                    logger.warn { "JSON 형식이 아닌 메시지 수신: $payload" }
                }
                else -> {
                    logger.error(it) { "텍스트 메시지 처리 중 알 수 없는 에러" }
                }
            }
        }
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