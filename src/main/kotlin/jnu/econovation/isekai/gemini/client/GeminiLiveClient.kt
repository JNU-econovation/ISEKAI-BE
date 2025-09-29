package jnu.econovation.isekai.gemini.client

import com.google.genai.Client
import com.google.genai.types.*
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.gemini.enums.GeminiModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class GeminiLiveClient(
    private val config: GeminiConfig
) {

    private val logger = KotlinLogging.logger {}

    private val liveConfig = LiveConnectConfig.builder()
        .responseModalities(Modality.Known.TEXT)
        .realtimeInputConfig(buildRealTimeInputConfig())
        .build()

    private val client = Client.builder().apiKey(config.apiKey).build()

    suspend fun getLiveResponse(
        inputVoice: Flow<ByteArray>,
        model: GeminiModel = GeminiModel.GEMINI_2_5_FLASH_LIVE
    ): Flow<String>? {
        return try {
            callbackFlow {
                val session = client.async.live
                    .connect(model.toString(), liveConfig)
                    .await()

                logger.info { "Gemini Live 세션이 연결되었습니다." }

                session.receive { message ->
                    message.serverContent()
                        .flatMap { it.modelTurn() }
                        .flatMap { it.parts() }
                        .ifPresent { parts ->
                            parts.forEach { part ->
                                part.text().ifPresent { text ->
                                    trySend(text).getOrThrow()
                                }
                            }
                        }
                    if (message.serverContent().flatMap { it.turnComplete() }.orElse(false)) {
                        logger.info { "서버가 대답을 완료했습니다." }
                    }
                }

                launch {
                    inputVoice
                        .collect { voiceChunk ->
                            session.sendRealtimeInput(buildAudioContent(voiceChunk))
                                .exceptionally {
                                    logger.error(it) { "audio chunk 보내는 중 에러 발생 -> ${it.message}" }
                                    return@exceptionally null
                                }
                                .await()
                        }
                }

                awaitClose {
                    logger.info { "Flow가 닫힙니다. 세션을 종료합니다." }
                    session.close()
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "텍스트 전송 실패" }
            return null
        }
    }

    private fun buildRealTimeInputConfig(): RealtimeInputConfig {
        return RealtimeInputConfig.builder()
            .automaticActivityDetection(
                AutomaticActivityDetection.builder()
                    .silenceDurationMs(config.silenceDurationMs)
            )
            .build()
    }

    private fun buildAudioContent(voiceChunk: ByteArray): LiveSendRealtimeInputParameters {
        return LiveSendRealtimeInputParameters.builder()
            .media(Blob.builder().mimeType("audio/pcm").data(voiceChunk))
            .build()
    }

}