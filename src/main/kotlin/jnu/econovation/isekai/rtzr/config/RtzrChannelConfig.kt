package jnu.econovation.isekai.rtzr.config

import jnu.econovation.isekai.rtzr.dto.client.response.RtzrSttResponse
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RtzrChannelConfig {
    private companion object {
        const val BUFFER_SIZE = 512
    }

    @Bean
    fun rtzrChannel() = Channel<RtzrSttResponse>(
        capacity = BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

}