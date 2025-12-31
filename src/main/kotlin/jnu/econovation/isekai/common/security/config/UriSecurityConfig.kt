package jnu.econovation.isekai.common.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "uri")
data class UriSecurityConfig(
    val allowedFrontEndOrigins: List<String>,
    val successPath : String,
    val defaultRedirectOrigin : String
)