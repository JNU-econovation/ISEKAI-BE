package jnu.econovation.isekai.e2e.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.websocket.ContainerProvider
import jnu.econovation.isekai.Application
import jnu.econovation.isekai.common.security.util.JwtUtil
import jnu.econovation.isekai.common.websocket.dto.response.TicketResponse
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_EMAIL
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.member.repository.MemberRepository
import jnu.econovation.isekai.session.constant.SessionConstant.INCOMING_MESSAGE_SIZE_LIMIT
import jnu.econovation.isekai.session.dto.request.SessionTextRequest
import jnu.econovation.isekai.session.dto.response.MessageType.*
import jnu.econovation.isekai.session.dto.response.SessionTextResponse
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ResourceLoader
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.client.RestClient
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.WebSocketClient
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import java.net.URI
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(SpringExtension::class)
class IsekAiSessionHandlerE2ETest(
    @param:LocalServerPort
    private var port: Int,
    private val resourceLoader: ResourceLoader,
    private val geminiConfig: GeminiConfig,
    private val mapper: ObjectMapper,
    private val memberRepository: MemberRepository,
    private val jwtUtil: JwtUtil
) {
    private companion object {
        const val CHARACTER_ID = 1
        val logger = KotlinLogging.logger {}
        val webSocketHeaders = WebSocketHttpHeaders()
    }

    val restClient = RestClient.builder()
        .baseUrl("http://localhost:${port}")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    private lateinit var ticket: String

    private lateinit var webSocketURI: URI

    private val webSocketClient: WebSocketClient by lazy {
        val webSocketContainer = ContainerProvider.getWebSocketContainer()
        webSocketContainer.defaultMaxBinaryMessageBufferSize = INCOMING_MESSAGE_SIZE_LIMIT
        webSocketContainer.defaultMaxTextMessageBufferSize = INCOMING_MESSAGE_SIZE_LIMIT
        StandardWebSocketClient(webSocketContainer)
    }

    private lateinit var readyLatch: CompletableDeferred<Unit>
    private lateinit var subtitleLatch: CompletableDeferred<Unit>

    @BeforeEach
    fun setUp() {
        val member = memberRepository.findByEmail(MASTER_EMAIL)
            ?: throw IllegalStateException("테스트 유저가 DB에 존재하지 않습니다: $MASTER_EMAIL")
        val accessToken = jwtUtil.generateToken(MemberInfoDTO.from(member))

        ticket = restClient.post()
            .uri("/websocket/ticket")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body(TicketResponse::class.java)
            ?.ticket
            ?: throw IllegalStateException("티켓 발급 실패")

        webSocketURI = URI("ws://localhost:${port}/characters/$CHARACTER_ID/voice?ticket=$ticket")
    }

    @Test
    @DisplayName("클라이언트가 연결 후 바이너리 메시지를 전송하면 핸들러가 처리한다")
    fun e2e() {
        val connectionLatch = CompletableDeferred<Unit>()
        readyLatch = CompletableDeferred()
        val session = getSession(connectionLatch, webSocketHeaders)

        runBlocking {
            withTimeout(5000) { connectionLatch.await() }

            withTimeout(15000) { readyLatch.await() }

            val resource = resourceLoader.getResource("classpath:test/e2e-test.wav")
            val inputStream = resource.inputStream
            inputStream.skip(44)
            val totalBytes = inputStream.available()
            val halfwayPoint = totalBytes / 2
            val buffer = ByteArray(3200)

            var bytesSent = 0
            // 1. 파일의 절반까지만 전송
            while (bytesSent < halfwayPoint) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead <= 0) break

                val chunkToSend = buffer.copyOf(bytesRead)
                session.sendMessage(BinaryMessage(chunkToSend))
                bytesSent += bytesRead
                delay(100)
            }

            // 2. 중간 침묵
            val silenceMs = geminiConfig.silenceDurationMs.toLong() + 1000L
            val silentChunk = ByteArray(buffer.size)
            val silenceIterations = silenceMs / 100

            logger.info { "음성 스트리밍 중간 지점 도달" }
//            sendSilence(silenceIterations, session, silentChunk, silenceMs)


            // 3. 나머지 파일 전송
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead <= 0) break

                val chunkToSend = buffer.copyOf(bytesRead)
                session.sendMessage(BinaryMessage(chunkToSend))
                delay(100)
            }

            // 4. 끝 침묵
            logger.info { "음성 스트리밍 끝 지점 도달" }
            sendSilence(silenceIterations, session, silentChunk, silenceMs)

            // 5. 마무리
            logger.info { "음성 스트리밍 끝!" }
            delay(20000)
            session.close()
        }
        logger.info { "음성 스트리밍 e2e 끝" }
    }

    @Test
    @DisplayName("텍스트 메세지를 보낼 수 있다.")
    fun e2eWithTextMessage() {
        val connectionLatch = CompletableDeferred<Unit>()
        readyLatch = CompletableDeferred()
        subtitleLatch = CompletableDeferred()

        val session = getSession(
            connectionLatch,
            webSocketHeaders
        )

        runBlocking {
            withTimeout(5000) { connectionLatch.await() }
            logger.info { "WebSocket 연결 성공" }

            withTimeout(10000) { readyLatch.await() }
            logger.info { "서버 준비 완료" }

            val request = SessionTextRequest.from("안녕하세요 자기소개 해주세요")

            launch {
                session.sendMessage(TextMessage(mapper.writeValueAsString(request)))
                logger.info { "텍스트 메세지 전송 완료" }
            }

            async {
                val bufferSize = 1600
                logger.info { "VAD 트리거를 위한 침묵(Silence) 패킷 전송 시작..." }

                val silenceDurationMs = 20000L
                val delayMs = 50L
                val silenceLoopCount = silenceDurationMs / delayMs
                val silenceBuffer = ByteArray(bufferSize) { 0 }

                for (i in 0 until silenceLoopCount) {
                    session.sendMessage(BinaryMessage(silenceBuffer))
                    delay(delayMs)
                }
                logger.info { "침묵 패킷 전송 완료 ($silenceDurationMs ms)" }
            }.await()



            withTimeout(15000) {
                subtitleLatch.await()
            }
            logger.info { "테스트 성공: 자막 수신 완료" }

            session.close()
        }
    }

    private fun getSession(
        latch: CompletableDeferred<Unit>,
        headers: WebSocketHttpHeaders
    ): WebSocketSession = webSocketClient.execute(object : BinaryWebSocketHandler() {
        override fun afterConnectionEstablished(session: WebSocketSession) {
            latch.complete(Unit)
        }

        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            try {
                val payload = message.payload
                logger.debug { "테스트 클라이언트 수신 (Text): $payload" }

                val response = mapper.readValue<SessionTextResponse>(payload)

                when (response.messageType) {
                    SERVER_READY -> {
                        logger.info { ">>> 서버 준비 완료 신호 수신 <<<" }
                        readyLatch.complete(Unit)
                    }


                    USER_SUBTITLE_CHUNK -> {
                        logger.info { "input stt chunk 자막 수신 -> ${response.content}" }
                    }

                    USER_ONE_SENTENCE_SUBTITLE -> {
                        logger.info { "input stt 한 문장 자막 수신 -> ${response.content}" }
                    }

                    BOT_SUBTITLE -> {
                        logger.info { "output stt 자막 수신 -> ${response.content}" }
                    }

                    INTERRUPTED -> {
                        logger.info { "Gemini 응답 중 사용자가 끼어 듦" }
                    }

                    TURN_COMPLETE -> {
                        logger.info { "Turn Complete 수신 -> ${response.content}" }
                    }

                    ERROR -> {
                        logger.info { "예외 수신 -> ${response.content}" }
                    }

                    EMOTION -> {
                        logger.info { "감정 수신 -> ${response.content}" }
                    }
                }
            } catch (e: Exception) {
                logger.error("텍스트 메시지 처리 중 에러", e)
            }
        }

        override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
            logger.info { "테스트 클라이언트 수신 (Binary): ${message.payloadLength} bytes" }
        }
    }, headers, webSocketURI)
        .get(3, TimeUnit.SECONDS)

    private suspend fun sendSilence(
        silenceIterations: Long,
        session: WebSocketSession,
        silentChunk: ByteArray,
        silenceMs: Long
    ) {
        logger.info { "${silenceMs / 1000}초간 침묵 스트림 전송 시작" }

        repeat(silenceIterations.toInt()) {
            session.sendMessage(BinaryMessage(silentChunk))
            delay(100)
        }

        logger.info { "${silenceMs / 1000}초 침묵 스트림 전송 종료" }
    }
}
