package jnu.econovation.isekai.common.s3.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cloud-storage")
data class CloudStorageProperties(
    val user: String,
    val password: String,
    val host: String,
    val port: String,
    val bucket: String,
    val region : String,
    val previewDirectory: String,
    val persistenceDirectory: String
)