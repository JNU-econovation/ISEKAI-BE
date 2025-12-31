package jnu.econovation.isekai

import jnu.econovation.isekai.aiServer.config.AiServerConfig
import jnu.econovation.isekai.common.security.config.UriSecurityConfig
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.prompt.config.PromptConfig
import jnu.econovation.isekai.rtzr.config.RtzrConfig
import jnu.econovation.isekai.storage.config.CloudStorageProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(
    UriSecurityConfig::class,
    GeminiConfig::class,
    PromptConfig::class,
    RtzrConfig::class,
    AiServerConfig::class,
    CloudStorageProperties::class
)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
