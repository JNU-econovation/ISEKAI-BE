package jnu.econovation.isekai.session.handler

import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.session.constant.SessionConstant.FLOW_BUFFER_SIZE
import jnu.econovation.isekai.session.event.ServerReadyEvent
import jnu.econovation.isekai.session.registry.WebSocketSessionRegistry
import jnu.econovation.isekai.session.service.IsekAiSessionService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import kotlin.coroutines.cancellation.CancellationException


@Component
class IsekAiSessionHandler(
    private val service: IsekAiSessionService,
    private val eventPublisher: ApplicationEventPublisher,
    private val sessionRegistry: WebSocketSessionRegistry
) : BinaryWebSocketHandler() {

    private companion object {
        const val CLIENT_VOICE_STREAM_KEY = "clientVoiceStream"
        const val SESSION_SCOPE_KEY = "sessionScope"
        const val PERSONA_ID_KEY = "personaId"

        val logger = KotlinLogging.logger {}
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info { "Connection established: ${session.id}" }

        sessionRegistry.register(session)

        val webSocketSessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val clientVoiceStream = MutableSharedFlow<ByteArray>(
            extraBufferCapacity = FLOW_BUFFER_SIZE,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        webSocketSessionScope.launch {
            val rtzrReadySignal = CompletableDeferred<Unit>()

            launch {
                try {
                    val personaId = session.attributes[PERSONA_ID_KEY] as? Long
                        ?: throw InternalServerException(IllegalStateException("personaId is null"))

                    service.processVoiceChunk(
                        rtzrReadySignal,
                        clientVoiceStream,
                        personaId,
                        session.id,
                        webSocketSessionScope
                    )
                } catch (_: CancellationException) {
                    logger.info { "세션 ${session.id} 처리가 정상적으로 취소되었습니다." }
                } catch (e: Exception) {
                    logger.error(e) { "voice chunk 처리 중 에러 -> ${session.id}" }
                }
            }

            rtzrReadySignal.await()

            eventPublisher.publishEvent(ServerReadyEvent(source = this, sessionId = session.id))
            logger.info { "클라이언트(${session.id})에게 준비 완료 신호 전송" }
        }

        session.attributes[CLIENT_VOICE_STREAM_KEY] = clientVoiceStream
        session.attributes[SESSION_SCOPE_KEY] = webSocketSessionScope
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        val clientVoiceStream =
            session.attributes[CLIENT_VOICE_STREAM_KEY] as? MutableSharedFlow<ByteArray>

        val bytes = ByteArray(message.payload.remaining())
        message.payload.get(bytes)

        (session.attributes[SESSION_SCOPE_KEY] as? CoroutineScope)?.launch {
            clientVoiceStream?.emit(bytes)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info { "Client disconnected: ${session.id}. Cancelling session scope." }
        val sessionScope = session.attributes[SESSION_SCOPE_KEY] as? CoroutineScope
        sessionScope?.cancel()
        sessionRegistry.unregister(session.id)
    }

}