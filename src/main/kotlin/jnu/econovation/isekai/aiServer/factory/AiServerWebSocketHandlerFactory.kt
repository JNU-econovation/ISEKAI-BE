package jnu.econovation.isekai.aiServer.factory

import com.fasterxml.jackson.databind.ObjectMapper
import jnu.econovation.isekai.aiServer.dto.internal.TTSResult
import jnu.econovation.isekai.aiServer.handler.AiServerWebSocketHandler
import kotlinx.coroutines.channels.SendChannel
import org.springframework.stereotype.Component

@Component
class AiServerWebSocketHandlerFactory(
    private val mapper: ObjectMapper
) {
    fun createHandler(channel: SendChannel<TTSResult>): AiServerWebSocketHandler {
        return AiServerWebSocketHandler(channel, mapper)
    }
}