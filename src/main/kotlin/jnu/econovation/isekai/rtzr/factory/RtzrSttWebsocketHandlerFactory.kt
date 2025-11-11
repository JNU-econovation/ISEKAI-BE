package jnu.econovation.isekai.rtzr.factory

import com.fasterxml.jackson.databind.ObjectMapper
import jnu.econovation.isekai.rtzr.client.handler.RtzrSttWebSocketHandler
import jnu.econovation.isekai.rtzr.dto.client.response.RtzrSttResponse
import kotlinx.coroutines.channels.SendChannel
import org.springframework.stereotype.Component

@Component
class RtzrSttWebsocketHandlerFactory(
    private val mapper: ObjectMapper
) {
    fun createHandler(channel: SendChannel<RtzrSttResponse>) : RtzrSttWebSocketHandler {
        return RtzrSttWebSocketHandler(channel, mapper)
    }
}