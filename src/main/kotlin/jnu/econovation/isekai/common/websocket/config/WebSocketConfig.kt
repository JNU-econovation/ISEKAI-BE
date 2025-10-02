package jnu.econovation.isekai.common.websocket.config

import jnu.econovation.isekai.common.security.config.AllowedOriginsProperties
import jnu.econovation.isekai.common.websocket.interceptor.WebSocketHandshakeInterceptor
import jnu.econovation.isekai.session.handler.IsekAiSessionHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val originsProperties: AllowedOriginsProperties,
    private val handler: IsekAiSessionHandler,
    private val webSocketHandshakeInterceptor: WebSocketHandshakeInterceptor
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/websocket/voice")
            .addInterceptors(webSocketHandshakeInterceptor)
            .setAllowedOriginPatterns(*originsProperties.allowedFrontEndOrigins.toTypedArray())
    }

}