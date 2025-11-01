package jnu.econovation.isekai.session.event.listener

import com.fasterxml.jackson.databind.ObjectMapper
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.common.config.ApplicationCoroutineScope
import jnu.econovation.isekai.session.dto.response.SessionResponse
import jnu.econovation.isekai.session.event.GeminiResponseEvent
import jnu.econovation.isekai.session.event.ServerReadyEvent
import jnu.econovation.isekai.session.registry.WebSocketSessionRegistry
import kotlinx.coroutines.launch
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

@Component
class SessionEventListener(
    private val scope: ApplicationCoroutineScope,
    private val sessionRegistry: WebSocketSessionRegistry,
    private val mapper: ObjectMapper,
    private val memoryService: ChatMemoryService
) {
    companion object {
        private val logger = mu.KotlinLogging.logger {}
    }

    @EventListener
    fun handleServerReady(event: ServerReadyEvent) = scope.launch {
        val session = getSession(event.sessionId) ?: return@launch

        val response = SessionResponse.fromServerReady()

        val payload = mapper.writeValueAsString(response)

        session.sendMessage(TextMessage(payload))

        logger.info { "세션 ID ${event.sessionId}로 준비 완료 메시지 전송 완료" }
    }

    @EventListener
    fun handleGeminiResponse(event: GeminiResponseEvent) {
        val response = event.content.response
        val personaId = event.content.personaId

        scope.launch { saveChat(personaId, response.inputSTT, response.output) }

        scope.launch {
            val session = getSession(event.sessionId) ?: return@launch

            val response = SessionResponse.fromGeminiOutput(response.output)

            val payload = mapper.writeValueAsString(response)

            session.sendMessage(TextMessage(payload))

            logger.info { "세션 ID ${event.sessionId}로 gemini response 메시지 전송 완료" }
        }
    }

    private fun getSession(id: String): WebSocketSession? {
        val session = sessionRegistry.getSession(id) ?: run {
            logger.warn { ">>> session not found. sessionId: $id" }
            return null
        }

        if (!session.isOpen) {
            logger.warn { ">>> session is not open. sessionId: $id" }
            return null
        }

        return session
    }

    private suspend fun saveChat(personaId: Long, input: String, output: String) {
        try {
            //todo: 추후 MASTER MEMBER -> 사용자화
            memoryService.save(personaId, ChatDTO(input, output))
        } catch (e: Exception) {
            logger.error(e) { "채팅 저장 중 예외 발생" }
        }
    }

}