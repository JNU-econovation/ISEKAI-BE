package jnu.econovation.isekai.session.factory

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mu.KotlinLogging

object WebSocketSessionScopeFactory {
    private val logger = KotlinLogging.logger {}

    fun create(onError: (Throwable) -> Unit): CoroutineScope {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            logger.error(throwable) { "웹소켓 세션 스코프(Global)에서 예외 포착" }

            onError(throwable)
        }

        return CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    }
}