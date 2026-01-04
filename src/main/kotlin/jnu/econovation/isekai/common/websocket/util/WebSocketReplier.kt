package jnu.econovation.isekai.common.websocket.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession

@Deprecated("concurrentWebSocket 세션 데코레이터로 대체로 인한 deprecated")
class WebSocketReplier(
    private val session: WebSocketSession,
    scope: CoroutineScope,
    channelCapacity: Int = 256
) {
    private val logger = KotlinLogging.logger {}
    private val queue =
        Channel<WebSocketMessage<*>>(capacity = channelCapacity)

    init {
        scope.launch {
            try {
                for (message in queue) {
                    if (session.isOpen) {
                        session.sendMessage(message)
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "websocket replier error" }
            } finally {
                queue.close()
            }
        }
    }

    suspend fun send(message: WebSocketMessage<*>): Result<Unit> {
        return runCatching {
            queue.send(message)
        }
    }

    fun close() = queue.close()

}