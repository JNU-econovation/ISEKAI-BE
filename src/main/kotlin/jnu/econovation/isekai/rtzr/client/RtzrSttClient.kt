package jnu.econovation.isekai.rtzr.client

import jnu.econovation.isekai.rtzr.config.RtzrConfig
import jnu.econovation.isekai.rtzr.dto.client.response.RtzrAuthResponse
import jnu.econovation.isekai.rtzr.dto.client.response.RtzrSttResponse
import jnu.econovation.isekai.rtzr.factory.RtzrSttWebsocketHandlerFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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

@Component
class RtzrSttClient(
    private val config: RtzrConfig,
    private val handlerFactory: RtzrSttWebsocketHandlerFactory
) {
    private companion object {
        const val BUFFER_SIZE = 512
        val END_OF_STREAM = TextMessage("EOS")
        val logger = KotlinLogging.logger {}
        val restClient = WebClient.builder().build()
        val wsClient = StandardWebSocketClient()
    }

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
        scope: CoroutineScope,
        rtzrReadySignal: CompletableDeferred<Unit>
    ): Flow<RtzrSttResponse> {
        val channel = getChannel()

        val handler = handlerFactory.createHandler(channel)

        val session = wsClient.execute(
            handler,
            getWebSocketHeader(accessToken),
            getUriWithQueryParams(config).toUri()
        ).await()

        rtzrReadySignal.complete(Unit)
        logger.info { "RTZR STT WebSocket 연결 수립 및 신호 전송 완료" }

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
        return channel.consumeAsFlow()
    }

    private fun getChannel(): Channel<RtzrSttResponse> {
        return Channel(capacity = BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
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
            .queryParam("use_disfluency_filter", config.useDisfluencyFilter)
            .build()
    }

    private fun getWebSocketHeader(accessToken: String): WebSocketHttpHeaders {
        val headers = WebSocketHttpHeaders()
        headers.add("Authorization", "Bearer $accessToken")
        return headers
    }
}
