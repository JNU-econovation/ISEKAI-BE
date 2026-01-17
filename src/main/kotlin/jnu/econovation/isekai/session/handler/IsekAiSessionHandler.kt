package jnu.econovation.isekai.session.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.util.AudioUtil
import jnu.econovation.isekai.common.websocket.constant.WebSocketConstant.MEMBER_ID_KEY
import jnu.econovation.isekai.session.constant.SessionConstant
import jnu.econovation.isekai.session.constant.SessionConstant.STREAM_BUFFER_SIZE
import jnu.econovation.isekai.session.dto.request.SessionBinaryRequest
import jnu.econovation.isekai.session.dto.request.SessionRequest
import jnu.econovation.isekai.session.dto.request.SessionTextRequest
import jnu.econovation.isekai.session.dto.response.SessionBinaryResponse
import jnu.econovation.isekai.session.dto.response.SessionResponse
import jnu.econovation.isekai.session.dto.response.SessionTextResponse
import jnu.econovation.isekai.session.factory.WebSocketSessionScopeFactory
import jnu.econovation.isekai.session.optimizer.SessionOptimizer
import jnu.econovation.isekai.session.service.IsekAiSessionService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
) : BinaryWebSocketHandler() {

    private companion object {
        const val CLIENT_STREAM_KEY = "clientVoiceStream"
        const val OPTIMIZER_KEY = "optimizer"
        const val SESSION_SCOPE_KEY = "sessionScope"
        const val CHARACTER_ID_KEY = "characterId"

        var WebSocketSession.clientStream: Channel<SessionRequest>
            get() = attributes[CLIENT_STREAM_KEY] as Channel<SessionRequest>
            set(value) {
                attributes[CLIENT_STREAM_KEY] = value
            }

        var WebSocketSession.optimizer: SessionOptimizer
            get() = attributes[OPTIMIZER_KEY] as SessionOptimizer
            set(value) {
                attributes[OPTIMIZER_KEY] = value
            }

        var WebSocketSession.scope: CoroutineScope
            get() = attributes[SESSION_SCOPE_KEY] as CoroutineScope
            set(value) {
                attributes[SESSION_SCOPE_KEY] = value
            }

        var WebSocketSession.characterId: Long?
            get() = attributes[CHARACTER_ID_KEY] as? Long
            set(value) {
                attributes[CHARACTER_ID_KEY] = value
            }

        var WebSocketSession.memberId: Long?
            get() = attributes[MEMBER_ID_KEY] as? Long
            set(value) {
                attributes[MEMBER_ID_KEY] = value
            }

        val logger = KotlinLogging.logger {}
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info { "Connection established: ${session.id}" }

        val concurrentSession = ConcurrentWebSocketSessionDecorator(
            session,
            SessionConstant.SEND_TIME_LIMIT_MS,
            SessionConstant.OUTGOING_BUFFER_SIZE_LIMIT
        )

        val sessionScope: CoroutineScope = WebSocketSessionScopeFactory.create { throwable ->
            sendErrorMessage(concurrentSession, throwable)
        }

        val hostMemberId = session.memberId
            ?: run {
                logger.error { "memberId가 null 입니다. (인증 X 의심)" }
                session.close(CloseStatus.POLICY_VIOLATION)
                return
            }

        val characterId = session.characterId
            ?: throw InternalServerException(IllegalStateException("캐릭터 id가 null 입니다."))

        val clientStream: Channel<SessionRequest> = Channel(
            capacity = STREAM_BUFFER_SIZE, // 0.1초 마다 청크 보낼 시 12.8초 정도 저장 가능
            onBufferOverflow = BufferOverflow.SUSPEND
        )

        sessionScope.launch {
            val aiServerReadySignal = CompletableDeferred<Unit>()
            val geminiReadySignal = CompletableDeferred<Unit>()

            launch {
                runCatching {
                    service.processInputStream(
                        sessionId = session.id,
                        geminiReadySignal = geminiReadySignal,
                        aiServerReadySignal = aiServerReadySignal,
                        inputStream = clientStream.receiveAsFlow(),
                        characterId = characterId,
                        hostMemberId = hostMemberId,
                        onReply = reply(session)
                    )

                }.onFailure { exception ->
                    when (exception) {
                        is CancellationException -> {
                            logger.info { "세션 ${session.id} 처리가 정상적으로 취소되었습니다." }
                        }

                        else -> {
                            logger.error(exception) { "voice chunk 처리 중 에러 -> ${session.id}" }
                            sendErrorMessage(session, exception)
                        }
                    }
                }
            }

            launch {
                awaitAndNotifyServerReady(aiServerReadySignal, geminiReadySignal, concurrentSession)
            }
        }

        val optimizer = SessionOptimizer(concurrentSession, sessionScope)
        concurrentSession.optimizer = optimizer
        optimizer.start()

        concurrentSession.clientStream = clientStream
        concurrentSession.scope = sessionScope
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        val clientInputStream = session.clientStream
        val payloadSize = message.payload.remaining()
        val bytes = ByteArray(payloadSize)

        message.payload.get(bytes)

        if (AudioUtil.isSilence(bytes)) {
            val silenceBytes = ByteArray(payloadSize)

            clientInputStream.trySend(SessionBinaryRequest(silenceBytes))
            return
        }

        session.optimizer.refresh()

        clientInputStream.trySend(SessionBinaryRequest(bytes))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val request: SessionTextRequest = mapper.readValue<SessionTextRequest>(message.payload)

        val clientInputStream = session.clientStream

        val optimizer = session.optimizer

        optimizer.refresh()

        clientInputStream.trySend(request)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info { "Client disconnected: ${session.id}. Cancelling session scope." }

        session.optimizer.stop()

        session.scope.cancel()
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
        aiServerReadySignal: CompletableDeferred<Unit>,
        geminiReadySignal: CompletableDeferred<Unit>,
        session: WebSocketSession
    ) {
        aiServerReadySignal.await()
        geminiReadySignal.await()

        val response = SessionTextResponse.fromServerReady()
        val payload = mapper.writeValueAsString(response)

        session.sendMessage(TextMessage(payload))

        logger.info { "세션 ID ${session.id}로 준비 완료 메시지 전송 완료" }
    }

    private fun reply(session: WebSocketSession): suspend (SessionResponse) -> Unit =
        suspend@{ response ->
            val optimizer = session.optimizer

            optimizer.refresh()

            when (response) {
                is SessionBinaryResponse -> {
                    session.sendMessage(BinaryMessage(response.content))
                }

                is SessionTextResponse -> {
                    session.sendMessage(TextMessage(mapper.writeValueAsString(response)))
                }
            }
        }

    private fun sendErrorMessage(session: WebSocketSession, e: Throwable) {
        runCatching {
            val (errorResponse, serverError) = when (e) {
                is ClientException -> {
                    SessionTextResponse.fromError(e.errorCode) to false
                }

                else -> {
                    SessionTextResponse.fromError(
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