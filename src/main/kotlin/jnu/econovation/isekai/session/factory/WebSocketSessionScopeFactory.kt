package jnu.econovation.isekai.session.factory

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mu.KotlinLogging

object WebSocketSessionScopeFactory {
    private val logger = KotlinLogging.logger {}

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.error(throwable) { "웹소켓 세션 스코프에서 처리되지 않은 예외 발생" }
    }

    fun create() = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
}