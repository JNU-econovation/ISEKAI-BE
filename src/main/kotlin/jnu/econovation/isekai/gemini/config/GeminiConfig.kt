package jnu.econovation.isekai.gemini.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiConfig(
    val apiKey: String,
    val silenceDurationMs: Int
)
