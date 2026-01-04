package jnu.econovation.isekai.aiServer.client

import jnu.econovation.isekai.aiServer.config.AiServerConfig
import jnu.econovation.isekai.aiServer.dto.request.AIServerCharacterGenerateRequest
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class CharacterGenerateClient(
    config: AiServerConfig,
    builder: RestClient.Builder
) {
    private val client = builder.baseUrl(config.restUrl).build()

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun requestGeneration(request: AIServerCharacterGenerateRequest): Result<Unit> {
        return runCatching {
            logger.info { "request: $request" }

            val responseEntity = client.post()
                .uri("/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String::class.java)

            logger.info { "response: ${responseEntity.body}" }
        }.onFailure { e ->
            logger.error(e) { "AI Server Request Failed" }
        }
    }

}