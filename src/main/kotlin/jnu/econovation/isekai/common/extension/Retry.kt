package jnu.econovation.isekai.common.extension

import kotlinx.coroutines.delay
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

suspend fun <T> retry(
    times: Int = 3,
    initialDelay: Long = 1000,
    factor: Double = 2.0,
    retryCondition: (Exception) -> Boolean = { true },
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            if (retryCondition(e)) {
                logger.warn { "Retrying... (${it + 1}/${times - 1}) error: ${e.message}" }
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            } else {
                throw e
            }
        }
    }
    return block()
}