package jnu.econovation.isekai.gemini.client

import com.google.genai.AsyncSession
import com.google.genai.Client
import com.google.genai.types.*
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveResponse
import jnu.econovation.isekai.gemini.enums.GeminiModel
import kotlinx.coroutines.channels.ProducerScope
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
        .inputAudioTranscription(AudioTranscriptionConfig.builder().build())
        .realtimeInputConfig(buildRealTimeInputConfig())
        .build()

    private val client = Client.builder().apiKey(config.apiKey).build()

    suspend fun getLiveResponse(
        inputVoice: Flow<ByteArray>,
        model: GeminiModel = GeminiModel.GEMINI_2_5_FLASH_LIVE
    ): Flow<GeminiLiveResponse>? {
        return try {
            callbackFlow {
                val session = client.async.live
                    .connect(model.toString(), liveConfig)
                    .await()

                logger.debug { "Gemini Live 세션이 연결되었습니다." }

                val inputSTTBuffer = StringBuilder()
                val outputBuffer = StringBuilder()

                session.receive { message -> onMessageReceived(message, inputSTTBuffer, outputBuffer) }
                launch { send(inputVoice, session) }
                awaitClose { onClosed(session) }
            }
        } catch (e: Exception) {
            throw InternalServerException(e)
        }
    }

    private fun ProducerScope<GeminiLiveResponse>.onMessageReceived(
        message: LiveServerMessage,
        inputSTTBuffer: StringBuilder,
        outputBuffer: StringBuilder
    ) {
        logger.debug { "gemini received -> $message" }

        message.serverContent().flatMap { it.inputTranscription() }
            .ifPresent { inputTranscription ->
                inputTranscription.text()
                    .ifPresent { text -> inputSTTBuffer.append(text) }
            }

        message.serverContent()
            .flatMap { it.modelTurn() }
            .flatMap { it.parts() }
            .ifPresent { parts ->
                parts.forEach { part ->
                    part.text()
                        .ifPresent { text -> outputBuffer.append(text) }
                }
            }

        if (message.serverContent().flatMap { it.turnComplete() }.orElse(false)) {
            val finalInput = inputSTTBuffer.toString()
            val finalOutput = outputBuffer.toString()

            logger.debug { "서버가 대답을 완료했습니다. STT: [$finalInput], Response: [$finalOutput]" }

            if (finalInput.isNotEmpty() || finalOutput.isNotEmpty()) {
                trySend(GeminiLiveResponse(finalInput, finalOutput))
            }

            inputSTTBuffer.clear()
            outputBuffer.clear()
        }
    }

    private suspend fun send(inputVoice: Flow<ByteArray>, session: AsyncSession) {
        inputVoice.collect { voiceChunk ->
            session.sendRealtimeInput(buildAudioContent(voiceChunk))
                .exceptionally {
                    logger.error(it) { "audio chunk 보내는 중 에러 발생 -> ${it.message}" }
                    return@exceptionally null
                }
                .await()
        }
    }


    private fun onClosed(session: AsyncSession) {
        logger.info { "Flow가 닫힙니다. 세션을 종료합니다." }
        session.close()
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
            .audio(Blob.builder().mimeType("audio/pcm").data(voiceChunk))
            .build()
    }

}