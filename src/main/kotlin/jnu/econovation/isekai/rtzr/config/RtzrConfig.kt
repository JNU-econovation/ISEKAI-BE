package jnu.econovation.isekai.rtzr.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rtzr")
data class RtzrConfig(
    val clientSecret: String,
    val clientId: String,
    val authUrl: String,
    val sttUrl: String,
    val sampleRate: Int,
    val encoding: String,
    val modelName: String,
    val domain: String,
    val language: String,
    val useDisfluencyFilter: Boolean
)