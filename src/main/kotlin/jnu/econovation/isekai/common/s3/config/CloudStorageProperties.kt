package jnu.econovation.isekai.common.s3.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cloud-storage")
data class CloudStorageProperties(
    val host: String,
    val port: String,
    val publicUrl: String,
    val bucket: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val previewDirectory: String,
    val persistenceDirectory: String
)