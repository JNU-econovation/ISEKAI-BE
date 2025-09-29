package jnu.econovation.isekai.e2e.session

import jnu.econovation.isekai.Application
import jnu.econovation.isekai.gemini.config.GeminiConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.collections.copyOf

private val logger = KotlinLogging.logger {}

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
    private val geminiConfig: GeminiConfig
) {
    private val client = StandardWebSocketClient()
    private val receivedMessages = ArrayBlockingQueue<ByteArray>(10)

    @AfterEach
    fun tearDown() {
        receivedMessages.clear()
    }

    @Test
    @DisplayName("클라이언트가 연결 후 바이너리 메시지를 전송하면 핸들러가 처리한다")
    fun e2e() {
        val latch = CompletableDeferred<Unit>()
        val headers = WebSocketHttpHeaders()
        val session = client.execute(object : BinaryWebSocketHandler() {
            override fun afterConnectionEstablished(session: WebSocketSession) {
                latch.complete(Unit)
            }

            override fun handleBinaryMessage(
                session: WebSocketSession,
                message: BinaryMessage
            ) {
                val bytes = ByteArray(message.payload.remaining())
                message.payload.get(bytes)
                receivedMessages.offer(bytes)
            }
        }, headers, URI("ws://localhost:${port}/websocket/voice"))
            .get(3, TimeUnit.SECONDS)

        runBlocking {
            latch.await()

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

            // 2. 의도적으로 침묵 (데이터 전송 중단)
            val silenceMs = geminiConfig.silenceDurationMs.toLong() + 1000L
            logger.info { "음성 스트리밍 중간 지점 도달, ${silenceMs / 1000}초간 침묵 시작" }
            delay(silenceMs)
            logger.info { "${silenceMs / 1000}초 침묵 종료, 나머지 스트리밍 재개" }

            // 3. 나머지 파일 전송
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead <= 0) break
                val chunkToSend = buffer.copyOf(bytesRead)
                session.sendMessage(BinaryMessage(chunkToSend))
                delay(100)
            }

            logger.info { "음성 스트리밍 끝!" }
            delay(5000) // Gemini의 추가 응답을 기다리는 시간
            session.close()
        }
        logger.info { "stt 끝" }
    }
}
