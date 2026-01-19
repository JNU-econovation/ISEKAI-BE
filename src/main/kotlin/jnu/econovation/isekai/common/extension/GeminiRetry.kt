package jnu.econovation.isekai.common.extension

import com.google.genai.errors.ServerException
import jnu.econovation.isekai.common.exception.server.InternalServerException
import kotlinx.coroutines.delay
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

suspend fun <T> geminiRetry(
    times: Int = 3,
    initialDelay: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            if (e is InternalServerException || e is ServerException) {
                logger.warn { "Retrying... error: ${e.message}" }
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            } else {
                throw e
            }
        }
    }
    return block()
}
