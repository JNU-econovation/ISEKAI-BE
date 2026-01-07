package jnu.econovation.isekai.e2e.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.websocket.ContainerProvider
import jnu.econovation.isekai.Application
import jnu.econovation.isekai.aiServer.dto.request.TTSRequest
import jnu.econovation.isekai.aiServer.dto.response.TTSResponse
import jnu.econovation.isekai.aiServer.service.AiServerTTSService
import jnu.econovation.isekai.enums.MessageType
import jnu.econovation.isekai.session.constant.SessionConstant.INCOMING_MESSAGE_SIZE_LIMIT
import jnu.econovation.isekai.session.dto.response.SessionResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.WebSocketClient
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import java.io.BufferedInputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(SpringExtension::class)
class IsekAiSessionHandlerHybridTest(
    @param:LocalServerPort
    private var port: Int,
    private val resourceLoader: ResourceLoader,
    private val mapper: ObjectMapper
) {
    @MockitoBean
    private lateinit var aiServerTTSService: AiServerTTSService

    private companion object {
        const val CHARACTER_ID = 1
        val logger = KotlinLogging.logger {}
        val webSocketHeaders = WebSocketHttpHeaders()
    }

    private val webSocketURI by lazy { URI("ws://localhost:${port}/websocket/voice?characterId=${CHARACTER_ID}") }

    private val client: WebSocketClient by lazy {
        val webSocketContainer = ContainerProvider.getWebSocketContainer()
        webSocketContainer.defaultMaxBinaryMessageBufferSize = INCOMING_MESSAGE_SIZE_LIMIT
        webSocketContainer.defaultMaxTextMessageBufferSize = INCOMING_MESSAGE_SIZE_LIMIT
        StandardWebSocketClient(webSocketContainer)
    }

    private lateinit var readyLatch: CompletableDeferred<Unit>
    private lateinit var subtitleLatch: CompletableDeferred<Unit>

    @BeforeEach
    fun setupMockTTS(): Unit = runBlocking {
        given(aiServerTTSService.tts(any(), any())).willAnswer { invocation ->
            val requestFlow = invocation.getArgument<Flow<TTSRequest>>(0)
            val readySignal = invocation.getArgument<CompletableDeferred<Unit>>(1)

            readySignal.complete(Unit)

            requestFlow.map { request ->
                logger.info { "[Mock TTS] Gemini가 보낸 텍스트 변환 요청 수신: ${request.text}" }
                val dummyAudio = ByteArray(100) { 0xAA.toByte() }
                TTSResponse(payload = dummyAudio, isFinal = true)
            }.onEach {
                delay(50)
            }
        }
    }

    @Test
    @DisplayName("Real Gemini와 Mock TTS를 사용하여 음성 대화가 정상 동작한다 (24k -> 16k 변환 전송)")
    fun e2eWithRealGeminiAndMockTTS() {
        val connectionLatch = CompletableDeferred<Unit>()
        readyLatch = CompletableDeferred()
        subtitleLatch = CompletableDeferred()

        val session = getSession(connectionLatch, webSocketHeaders)

        runBlocking {
            withTimeout(5000) { connectionLatch.await() }
            logger.info { "WebSocket 연결 성공" }

            withTimeout(10000) { readyLatch.await() }
            logger.info { "서버 준비 완료" }

            val resource = resourceLoader.getResource("classpath:test/e2e-test.wav")
            val sourceStream = AudioSystem.getAudioInputStream(BufferedInputStream(resource.inputStream))

            logger.info { "원본 포맷: ${sourceStream.format}" }

            val targetFormat = AudioFormat(
                16000f,
                16,
                1,
                true,
                false
            )

            val convertedStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream)

            logger.info { "오디오 변환 시작 (24kHz -> 16kHz) 및 스트리밍..." }

            val bufferSize = 1600
            val buffer = ByteArray(bufferSize)

            while (true) {
                val bytesRead = convertedStream.read(buffer)
                if (bytesRead <= 0) break

                session.sendMessage(BinaryMessage(buffer.copyOf(bytesRead)))

                delay(50)
            }
            logger.info { "오디오 전송 완료" }

            withTimeout(15000) {
                subtitleLatch.await()
            }
            logger.info { "테스트 성공: 자막 수신 완료" }

            session.close()
            sourceStream.close()
            convertedStream.close()
        }
    }

    private fun getSession(
        latch: CompletableDeferred<Unit>,
        headers: WebSocketHttpHeaders
    ): WebSocketSession = client.execute(object : BinaryWebSocketHandler() {
        override fun afterConnectionEstablished(session: WebSocketSession) {
            latch.complete(Unit)
        }

        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            try {
                val payload = message.payload
                val response = mapper.readValue<SessionResponse>(payload)

                when (response.messageType) {
                    MessageType.SERVER_READY -> readyLatch.complete(Unit)
                    MessageType.SUBTITLE -> {
                        logger.info { "자막 수신: ${response.content}" }
                        subtitleLatch.complete(Unit)
                    }
                    MessageType.ERROR -> logger.error { "에러: ${response.content}" }
                }
            } catch (e: Exception) {
                logger.error("메시지 에러", e)
            }
        }

        override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {}
    }, headers, webSocketURI)
        .get(5, TimeUnit.SECONDS)
}