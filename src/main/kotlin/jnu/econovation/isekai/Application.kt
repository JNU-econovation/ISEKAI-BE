package jnu.econovation.isekai

import jnu.econovation.isekai.common.security.config.AllowedOriginsProperties
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.rtzr.config.RtzrConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(
    RtzrConfig::class,
    AllowedOriginsProperties::class,
    GeminiConfig::class
)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
