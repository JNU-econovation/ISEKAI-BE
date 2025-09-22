package jnu.econovation.isekai

import jnu.econovation.isekai.common.security.config.AllowedOriginsProperties
import jnu.econovation.isekai.rtzr.config.RtzrConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(RtzrConfig::class, AllowedOriginsProperties::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
