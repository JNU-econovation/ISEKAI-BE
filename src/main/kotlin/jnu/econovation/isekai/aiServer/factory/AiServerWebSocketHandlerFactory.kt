package jnu.econovation.isekai.aiServer.factory

import com.fasterxml.jackson.databind.ObjectMapper
import jnu.econovation.isekai.aiServer.dto.response.AiServerTTSResponse
import jnu.econovation.isekai.aiServer.handler.AiServerWebSocketHandler
import kotlinx.coroutines.channels.SendChannel
import org.springframework.stereotype.Component

@Component
class AiServerWebSocketHandlerFactory(
    private val mapper: ObjectMapper
) {
    fun createHandler(channel: SendChannel<AiServerTTSResponse>): AiServerWebSocketHandler {
        return AiServerWebSocketHandler(channel)
    }
}