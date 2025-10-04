package jnu.econovation.isekai

import jnu.econovation.isekai.common.security.config.AllowedOriginsProperties
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.prompt.config.PromptConfig
import jnu.econovation.isekai.rtzr.config.RtzrConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(
    AllowedOriginsProperties::class,
    GeminiConfig::class,
    PromptConfig::class,
    RtzrConfig::class
)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
