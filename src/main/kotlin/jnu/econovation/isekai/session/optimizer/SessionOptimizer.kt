package jnu.econovation.isekai.session.optimizer

import kotlinx.coroutines.*
import mu.KotlinLogging
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.atomic.AtomicLong

class SessionOptimizer(
    private val session: WebSocketSession,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 120_000L
) {
    private val logger = KotlinLogging.logger {}
    private val lastActiveTime = AtomicLong(System.currentTimeMillis()) // 마지막 활동 시간
    private var monitorJob: Job? = null

    fun start() {
        if (monitorJob?.isActive == true) return

        monitorJob = scope.launch {
            while (isActive && session.isOpen) {
                delay(5000)
                checkTimeout()
            }
        }
    }

    fun refresh() {
        lastActiveTime.set(System.currentTimeMillis())
    }

    fun stop() {
        monitorJob?.cancel()
    }

    private fun checkTimeout() {
        if (System.currentTimeMillis() - lastActiveTime.get() > timeoutMs) {
            logger.info { "[Timeout] ${timeoutMs}ms 비활성. 세션 종료: ${session.id}" }
            runCatching {
                session.close(CloseStatus(CloseStatus.NORMAL.code, "Idle Timeout"))
            }
            stop()
        }
    }
}