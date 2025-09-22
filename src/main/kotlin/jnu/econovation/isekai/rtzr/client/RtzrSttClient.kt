package jnu.econovation.isekai.rtzr.client


import jnu.econovation.isekai.rtzr.client.handler.RtzrSttWebSocketHandler
import jnu.econovation.isekai.rtzr.config.RtzrConfig
import jnu.econovation.isekai.rtzr.dto.client.response.RtzrAuthResponse
import jnu.econovation.isekai.rtzr.dto.client.response.RtzrSttResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import kotlin.jvm.java

@Component
class RtzrSttClient(
    private val config: RtzrConfig,
    private val handler: RtzrSttWebSocketHandler,
    private val rtzrChannel: ReceiveChannel<RtzrSttResponse>
) {
    private companion object {
        val END_OF_STREAM = TextMessage("EOS")
    }

    private val logger = KotlinLogging.logger {}
    private val restClient = WebClient.builder().build()
    private val wsClient = StandardWebSocketClient()

    suspend fun auth(): RtzrAuthResponse {
        return restClient.post()
            .uri(config.authUrl)
            .bodyValue(getAuthBody())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .retrieve()
            .bodyToMono(RtzrAuthResponse::class.java)
            .awaitSingle()
    }

    suspend fun stt(
        voiceStream: Flow<ByteArray>,
        accessToken: String,
        scope: CoroutineScope
    ): Flow<RtzrSttResponse> {

        val session = wsClient.execute(
            handler,
            getWebSocketHeader(config, accessToken),
            getUriWithQueryParams(config).toUri()
        ).await()

        scope.launch {
            try {
                voiceStream.collect { chunk ->
                    session.sendMessage(BinaryMessage(chunk))
                }
                session.sendMessage(END_OF_STREAM)
            } catch (e: Exception) {
                logger.error(e) { "음성 스트림 전송 중 에러 발생" }
                session.close()
            }
        }
        return rtzrChannel.consumeAsFlow()
    }

    private fun getAuthBody(): LinkedMultiValueMap<String, String> {
        val formData = LinkedMultiValueMap<String, String>()
        formData.add("client_id", config.clientId)
        formData.add("client_secret", config.clientSecret)
        return formData
    }

    private fun getUriWithQueryParams(config: RtzrConfig): UriComponents {
        return UriComponentsBuilder.fromUri(URI(config.sttUrl))
            .queryParam("sample_rate", config.sampleRate)
            .queryParam("encoding", config.encoding)
            .queryParam("model_name", config.modelName)
            .queryParam("domain", config.domain)
            .queryParam("language", config.language)
            .build()
    }

    private fun getWebSocketHeader(config: RtzrConfig, accessToken: String): WebSocketHttpHeaders {
        val headers = WebSocketHttpHeaders()
        headers.add("Authorization", "Bearer $accessToken")
        return headers
    }
}
