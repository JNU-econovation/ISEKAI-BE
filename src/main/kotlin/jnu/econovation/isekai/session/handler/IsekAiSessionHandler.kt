package jnu.econovation.isekai.session.handler

import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.session.service.IsekAiSessionService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import kotlin.coroutines.cancellation.CancellationException

private val logger = KotlinLogging.logger {}

@Component
class IsekAiSessionHandler(
    private val service: IsekAiSessionService
) : BinaryWebSocketHandler() {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info { "Connection established: ${session.id}" }

        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val clientVoiceStream = MutableSharedFlow<ByteArray>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        val processingJob = sessionScope.launch {
            try {
                val personaId = session.attributes["personaId"] as? Long
                    ?: throw InternalServerException(IllegalStateException("personaId is null"))

                service.processVoiceChunk(clientVoiceStream, personaId, sessionScope)
            } catch (_: CancellationException) {
                logger.info { "세션 ${session.id} 처리가 정상적으로 취소되었습니다." }
            } catch (e: Exception) {
                logger.error(e) { "voice chunk 처리 중 에러 -> ${session.id}" }
            }
        }

        session.attributes["clientVoiceStream"] = clientVoiceStream
        session.attributes["sessionScope"] = sessionScope
        session.attributes["processingJob"] = processingJob
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        val clientVoiceStream =
            session.attributes["clientVoiceStream"] as? MutableSharedFlow<ByteArray>

        val bytes = ByteArray(message.payload.remaining())
        message.payload.get(bytes)

        (session.attributes["sessionScope"] as? CoroutineScope)?.launch {
            clientVoiceStream?.emit(bytes)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info { "Client disconnected: ${session.id}. Cancelling session scope." }
        val sessionScope = session.attributes["sessionScope"] as? CoroutineScope
        sessionScope?.cancel()
    }

}