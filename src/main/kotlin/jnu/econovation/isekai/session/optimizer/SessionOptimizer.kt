package jnu.econovation.isekai.session.optimizer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import kotlin.math.sqrt

class SessionOptimizer(
    private val session: WebSocketSession,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 60_000L,
    private val noiseThreshold: Int = 500
) {
    private var timeoutJob: Job? = null
    private val logger = KotlinLogging.logger {}

    fun start() {
        resetTimer()
    }

    fun onAudioReceived(chunk: ByteArray) {
        if (!isNoise(chunk)) {
            resetTimer()
        }
    }

    fun extend() {
        resetTimer()
    }

    fun stop() {
        timeoutJob?.cancel()
    }

    private fun resetTimer() {
        timeoutJob?.cancel()

        timeoutJob = scope.launch {
            delay(timeoutMs)

            logger.info { "[Timeout] ${timeoutMs}ms 동안 활동 없음. 세션 종료. ID: ${session.id}" }
            if (session.isOpen) {
                session.close(CloseStatus(CloseStatus.NORMAL.code, "Idle Timeout"))
            }
        }
    }

    private fun isNoise(chunk: ByteArray): Boolean {
        var sum = 0.0
        for (i in chunk.indices step 2) {
            if (i + 1 >= chunk.size) break
            val sample = (chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = sqrt(sum / (chunk.size / 2))
        return rms < noiseThreshold
    }
}