package jnu.econovation.isekai.session.handler

import com.fasterxml.jackson.databind.ObjectMapper
import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.session.constant.SessionConstant
import jnu.econovation.isekai.session.constant.SessionConstant.FLOW_BUFFER_SIZE
import jnu.econovation.isekai.session.dto.response.SessionResponse
import jnu.econovation.isekai.session.factory.WebSocketSessionScopeFactory
import jnu.econovation.isekai.session.optimizer.SessionOptimizer
import jnu.econovation.isekai.session.registry.WebSocketSessionRegistry
import jnu.econovation.isekai.session.service.IsekAiSessionService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import kotlin.coroutines.cancellation.CancellationException


@Component
class IsekAiSessionHandler(
    private val service: IsekAiSessionService,
    private val mapper: ObjectMapper,
    private val sessionRegistry: WebSocketSessionRegistry
) : BinaryWebSocketHandler() {

    private companion object {
        const val CLIENT_VOICE_STREAM_KEY = "clientVoiceStream"
        const val OPTIMIZER_KEY = "optimizer"
        const val SESSION_SCOPE_KEY = "sessionScope"
        const val PERSONA_ID_KEY = "personaId"

        val logger = KotlinLogging.logger {}
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info { "Connection established: ${session.id}" }

        val concurrentSession = ConcurrentWebSocketSessionDecorator(
            session,
            SessionConstant.SEND_TIME_LIMIT_MS,
            SessionConstant.OUTGOING_BUFFER_SIZE_LIMIT
        )

        sessionRegistry.register(concurrentSession)

        val sessionScope: CoroutineScope = WebSocketSessionScopeFactory.create { throwable ->
            sendErrorMessage(concurrentSession, throwable)
        }

        val clientVoiceStream = MutableSharedFlow<ByteArray>(
            extraBufferCapacity = FLOW_BUFFER_SIZE,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        sessionScope.launch {
            val rtzrReadySignal = CompletableDeferred<Unit>()
            val aiServerReadySignal = CompletableDeferred<Unit>()

            launch {
                handleVoiceChunk(concurrentSession, rtzrReadySignal, aiServerReadySignal, clientVoiceStream)
            }

            launch {
                awaitAndNotifyServerReady(rtzrReadySignal, aiServerReadySignal, concurrentSession)
            }
        }

        val optimizer = SessionOptimizer(concurrentSession, sessionScope)
        concurrentSession.attributes[OPTIMIZER_KEY] = optimizer
        optimizer.start()

        concurrentSession.attributes[CLIENT_VOICE_STREAM_KEY] = clientVoiceStream
        concurrentSession.attributes[SESSION_SCOPE_KEY] = sessionScope
    }

    private suspend fun handleVoiceChunk(
        session: WebSocketSession,
        rtzrReadySignal: CompletableDeferred<Unit>,
        aiServerReadySignal: CompletableDeferred<Unit>,
        clientVoiceStream: MutableSharedFlow<ByteArray>
    ) {
        try {
            val personaId = session.attributes[PERSONA_ID_KEY] as? Long
                ?: throw InternalServerException(IllegalStateException("personaId is null"))

            service.processVoiceChunk(
                sessionId = session.id,
                rtzrReadySignal = rtzrReadySignal,
                aiServerReadySignal = aiServerReadySignal,
                voiceStream = clientVoiceStream,
                personaId = personaId,
                onVoiceChunk = replyVoiceChunk(session),
                onSubtitle = replySubtitle(session)
            )

        } catch (_: CancellationException) {
            logger.info { "세션 ${session.id} 처리가 정상적으로 취소되었습니다." }
        } catch (e: Exception) {
            logger.error(e) { "voice chunk 처리 중 에러 -> ${session.id}" }

            sendErrorMessage(session, e)
        }
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        val clientVoiceStream = session
            .attributes[CLIENT_VOICE_STREAM_KEY] as? MutableSharedFlow<ByteArray>

        val optimizer = session.attributes[OPTIMIZER_KEY] as? SessionOptimizer

        val bytes = ByteArray(message.payload.remaining())

        message.payload.get(bytes)

        optimizer?.onAudioReceived(bytes)

        (session.attributes[SESSION_SCOPE_KEY] as? CoroutineScope)?.launch {
            clientVoiceStream?.emit(bytes)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info { "Client disconnected: ${session.id}. Cancelling session scope." }

        (session.attributes[OPTIMIZER_KEY] as? SessionOptimizer)?.stop()

        (session.attributes[SESSION_SCOPE_KEY] as? CoroutineScope)?.cancel()

        sessionRegistry.unregister(session.id)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error(exception) { "전송 에러 발생: ${session.id}" }

        try {
            session.close(CloseStatus.SERVER_ERROR)
        } catch (e: Exception) {
            logger.error(e) { "session Close 실패: ${session.id}" }
        }

    }

    private suspend fun awaitAndNotifyServerReady(
        rtzrReadySignal: CompletableDeferred<Unit>,
        aiServerReadySignal: CompletableDeferred<Unit>,
        session: WebSocketSession
    ) {
        rtzrReadySignal.await()
        aiServerReadySignal.await()

        val response = SessionResponse.fromServerReady()
        val payload = mapper.writeValueAsString(response)

        session.sendMessage(TextMessage(payload))

        logger.info { "세션 ID ${session.id}로 준비 완료 메시지 전송 완료" }
    }

    private fun replyVoiceChunk(session: WebSocketSession): suspend (ByteArray) -> Unit =
        suspend@{ chunk ->
            val optimizer = session.attributes[OPTIMIZER_KEY] as? SessionOptimizer

            optimizer?.extend()

            session.sendMessage(BinaryMessage(chunk))
        }

    private fun replySubtitle(session: WebSocketSession): suspend (String) -> Unit =
        suspend@{ text ->
            val optimizer = session.attributes[OPTIMIZER_KEY] as? SessionOptimizer

            optimizer?.extend()

            val response = SessionResponse.fromSubtitle(text)

            session.sendMessage(TextMessage(mapper.writeValueAsString(response)))
        }

    private fun sendErrorMessage(session: WebSocketSession, e: Throwable) {
        runCatching {
            val (errorResponse, serverError) = when (e) {
                is ClientException -> {
                    SessionResponse.fromError(e.errorCode) to false
                }

                else -> {
                    SessionResponse.fromError(
                        ErrorCode.INTERNAL_SERVER,
                        "서버에서 예상치 못한 에러가 발생했습니다."
                    ) to true
                }
            }

            session.sendMessage(TextMessage(mapper.writeValueAsString(errorResponse)))

            if (serverError) {
                session.close(CloseStatus.SERVER_ERROR)
            } else {
                session.close(CloseStatus.BAD_DATA)
            }

        }.onFailure { sendEx ->
            logger.error(sendEx) { "에러 메시지 전송 실패 (세션 ID: ${session.id})" }
        }
    }

}