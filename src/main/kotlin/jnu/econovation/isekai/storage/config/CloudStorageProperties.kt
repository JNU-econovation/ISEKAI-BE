package jnu.econovation.isekai.storage.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cloud-storage")
data class CloudStorageProperties(
    val user: String,
    val password: String,
    val host: String,
    val port: String,
    val bucket: String,
    val region : String
)