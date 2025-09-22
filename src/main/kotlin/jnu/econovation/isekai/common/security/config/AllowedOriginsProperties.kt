package jnu.econovation.isekai.common.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security")
data class AllowedOriginsProperties(val allowedFrontEndOrigins: List<String>)