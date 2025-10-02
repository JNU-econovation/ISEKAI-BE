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

    private companion object {
        const val PERSONA_ID = 1
    }

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
        val session = getSession(latch, headers)

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
            delay(5000)
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

        override fun handleBinaryMessage(
            session: WebSocketSession,
            message: BinaryMessage
        ) {
            val bytes = ByteArray(message.payload.remaining())
            message.payload.get(bytes)
            receivedMessages.offer(bytes)
        }
    }, headers, URI("ws://localhost:${port}/websocket/voice?personaId=${PERSONA_ID}"))
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
