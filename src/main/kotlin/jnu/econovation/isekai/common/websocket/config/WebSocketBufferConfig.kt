package jnu.econovation.isekai.common.websocket.config

import jnu.econovation.isekai.session.constant.SessionConstant.WEBSOCKET_BUFFER_SIZE
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
class WebSocketBufferConfig {

    @Bean
    fun createWebSocketContainer(): ServletServerContainerFactoryBean {
        val container = ServletServerContainerFactoryBean()
        container.setMaxTextMessageBufferSize(WEBSOCKET_BUFFER_SIZE)
        container.setMaxBinaryMessageBufferSize(WEBSOCKET_BUFFER_SIZE)
        return container
    }

}