package jnu.econovation.isekai.aiServer.handler

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jnu.econovation.isekai.aiServer.dto.internal.TTSOutput
import jnu.econovation.isekai.aiServer.dto.response.TTSTextResponse
import jnu.econovation.isekai.aiServer.exception.NoSuchVoiceException
import jnu.econovation.isekai.common.exception.BusinessException
import jnu.econovation.isekai.common.exception.server.InternalServerException
import kotlinx.coroutines.channels.SendChannel
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

class AiServerWebSocketHandler(
    private val aiServerChannel: SendChannel<TTSOutput>,
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
        logger.info { "AI 서버 메시지 수신: $payload" }

        runCatching {
            when (val response = mapper.readValue<TTSTextResponse>(payload)) {
                is TTSTextResponse.ErrorResponse -> {
                    logger.error { "AI 서버 에러 발생: Code=${response.code}, Msg=${response.message}" }

                    val exception = handleTTSResponseError(response)

                    aiServerChannel.close(exception)
                    session.close(CloseStatus.SERVER_ERROR)
                }

                is TTSTextResponse.GeneralResponse -> {
                    logger.info { "AI 서버 상태 메시지 수신: ${response.status}" }

                    if (response.status == "streaming") {
                        aiServerChannel.trySend(TTSOutput.StartStreaming())
                    }
                }
            }

        }.onFailure {
            when (it) {
                is JsonProcessingException -> logger.warn { "잘못된 JSON 형식: $payload" }
                else -> logger.error(it) { "메시지 처리 중 예외 발생" }
            }
        }
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        logger.info { "AI 서버 TTS 결과 수신 -> binary message size : ${message.payloadLength}" }

        val buffer = message.payload

        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        aiServerChannel.trySend(TTSOutput.Voice(bytes))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info { "AI 서버 연결 종료 -> ${session.id}, Status: $status" }
        aiServerChannel.close()
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error(exception) { "AI 서버 연결 에러: ${session.id}" }
        aiServerChannel.close(exception)
    }

    private fun handleTTSResponseError(response: TTSTextResponse.ErrorResponse): BusinessException {
        val exception = when (response.code) {
            "400_001", "500_001", "500_002", "500_999" -> {
                InternalServerException(cause = IllegalStateException(response.message))
            }

            "400_002" -> {
                NoSuchVoiceException()
            }

            else -> {
                InternalServerException(cause = IllegalStateException("Unknown Error: ${response.message}"))
            }
        }
        return exception
    }
}
