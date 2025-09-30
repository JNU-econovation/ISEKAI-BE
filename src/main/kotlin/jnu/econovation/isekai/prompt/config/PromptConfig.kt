package jnu.econovation.isekai.prompt.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.ResourceLoader
import java.nio.charset.StandardCharsets

@ConfigurationProperties(prefix = "prompt")
data class PromptConfig(
    var summarize: String
) {

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @PostConstruct
    fun init() {
        summarize = loadContent(summarize)
    }

    private fun loadContent(path: String): String {
        val resource = resourceLoader.getResource(path)
        return resource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}