package jnu.econovation.isekai.aiServer.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai-server")
data class AiServerConfig(
    val webSocketUrl: String,
    val restUrl: String
)