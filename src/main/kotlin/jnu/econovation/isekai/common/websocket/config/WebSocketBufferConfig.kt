package jnu.econovation.isekai.common.websocket.config

import jnu.econovation.isekai.session.constant.SessionConstant.INCOMING_MESSAGE_SIZE_LIMIT
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
class WebSocketBufferConfig {

    @Bean
    fun createWebSocketContainer(): ServletServerContainerFactoryBean {
        val container = ServletServerContainerFactoryBean()
        container.setMaxTextMessageBufferSize(INCOMING_MESSAGE_SIZE_LIMIT)
        container.setMaxBinaryMessageBufferSize(INCOMING_MESSAGE_SIZE_LIMIT)
        return container
    }

}