package jnu.econovation.isekai.aiServer.client

import jnu.econovation.isekai.aiServer.config.AiServerConfig
import jnu.econovation.isekai.aiServer.dto.request.AIServerCharacterGenerateRequest
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class CharacterGenerateClient(
    config: AiServerConfig,
    builder: RestClient.Builder
) {
    private val client = builder.baseUrl(config.restUrl).build()

    fun requestGeneration(request: AIServerCharacterGenerateRequest) {
        //가상 스레드
        client.post()
            .uri("/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
    }

}