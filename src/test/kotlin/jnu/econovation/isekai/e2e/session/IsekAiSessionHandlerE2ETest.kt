package jnu.econovation.isekai.e2e.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.websocket.ContainerProvider
import jnu.econovation.isekai.Application
import jnu.econovation.isekai.enums.MessageType
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.session.constant.SessionConstant.WEBSOCKET_BUFFER_SIZE
import jnu.econovation.isekai.session.dto.response.SessionResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.junit.jupiter.SpringExtension
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
class KioskAiSessionHandlerE2ETest(
    @param:LocalServerPort
    private var port: Int,
    private val resourceLoader: ResourceLoader,
    private val geminiConfig: GeminiConfig,
    private val mapper: ObjectMapper
) {

    private companion object {
        const val PERSONA_ID = 1
        val logger = KotlinLogging.logger {}
        val webSocketHeaders = WebSocketHttpHeaders()
    }

    private val webSocketURI = URI("ws://localhost:${port}/websocket/voice?personaId=${PERSONA_ID}")

    private val client: WebSocketClient by lazy {
        val webSocketContainer = ContainerProvider.getWebSocketContainer()
        webSocketContainer.defaultMaxBinaryMessageBufferSize = WEBSOCKET_BUFFER_SIZE
        webSocketContainer.defaultMaxTextMessageBufferSize = WEBSOCKET_BUFFER_SIZE
        StandardWebSocketClient(webSocketContainer)
    }

    private lateinit var readyLatch: CompletableDeferred<Unit>

    @Test
    @DisplayName("클라이언트가 연결 후 바이너리 메시지를 전송하면 핸들러가 처리한다")
    fun e2e() {
        val connectionLatch = CompletableDeferred<Unit>()
        readyLatch = CompletableDeferred()
        val session = getSession(connectionLatch, webSocketHeaders)

        runBlocking {
            withTimeout(5000) { connectionLatch.await() }

            withTimeout(5000) { readyLatch.await() }

            val resource = resourceLoader.getResource("classpath:test/e2e-test.wav")
            val inputStream = resource.inputStream
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
            delay(10000)
            session.close()
        }
        logger.info { "음성 스트리밍 e2e 끝" }
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
                logger.debug { "테스트 클라이언트 수신 (Text): $payload" }

                val response = mapper.readValue<SessionResponse>(payload)

                when (response.messageType) {
                    MessageType.SERVER_READY -> {
                        logger.info { ">>> 서버 준비 완료 신호 수신 <<<" }
                        readyLatch.complete(Unit)
                    }

                    MessageType.GEMINI_OUTPUT -> {
                        logger.info { "Gemini output 수신 -> ${response.content}" }
                    }
                }
            } catch (e: Exception) {
                logger.error("텍스트 메시지 처리 중 에러", e)
            }
        }

        override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {}
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
