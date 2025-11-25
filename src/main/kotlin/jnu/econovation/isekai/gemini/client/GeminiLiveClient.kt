package jnu.econovation.isekai.gemini.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableList
import com.google.genai.AsyncSession
import com.google.genai.Client
import com.google.genai.types.*
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveResponse
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel
import jnu.econovation.isekai.gemini.constant.function.GeminiFunctionTools
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveTextResponse
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveTextResponseChunk
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class GeminiLiveClient(
    private val config: GeminiConfig,
    private val mapper: ObjectMapper
) {

    companion object {
        private val logger = KotlinLogging.logger {}
        private val googleSearchTool = Tool.builder()
            .googleSearch(GoogleSearch.builder().build())
            .build()
    }

    private val client = Client.builder().apiKey(config.apiKey).build()

    suspend fun getLiveResponse(
        inputData: Flow<GeminiInput>,
        prompt: String,
        model: GeminiModel = GeminiModel.GEMINI_2_5_FLASH_NATIVE_AUDIO
    ): Flow<GeminiLiveResponse> {
        return try {
            callbackFlow {
                val session = client.async.live
                    .connect(model.toString(), buildConfig(prompt))
                    .await()

                logger.debug { "Gemini Live 세션이 연결되었습니다." }

                val inputSTTBuffer = StringBuilder()
                val outputKrBuffer = StringBuilder()
                val outputJpBuffer = StringBuilder()

                session.receive {
                    onMessageReceived(message = it, inputSTTBuffer, outputKrBuffer, outputJpBuffer)
                }
                launch { send(inputData, session) }
                awaitClose { onClosed(session) }
            }
        } catch (e: Exception) {
            throw InternalServerException(e)
        }
    }

    private fun buildConfig(prompt: String): LiveConnectConfig {
        return LiveConnectConfig.builder()
            .responseModalities(Modality.Known.AUDIO)
            .inputAudioTranscription(AudioTranscriptionConfig.builder().build())
            .realtimeInputConfig(buildRealTimeInputConfig())
            .systemInstruction(Content.fromParts(Part.fromText(prompt)))
            .tools(ImmutableList.of(googleSearchTool, GeminiFunctionTools.TEXT_RESPONSE_TOOL))
            .build()
    }

    private fun ProducerScope<GeminiLiveResponse>.onMessageReceived(
        message: LiveServerMessage,
        inputSTTBuffer: StringBuilder,
        outputKrBuffer: StringBuilder,
        outputJpBuffer: StringBuilder
    ) {
        logger.debug { "gemini received -> $message" }

        processInputSTT(message, inputSTTBuffer)

        processTurnComplete(message, inputSTTBuffer, outputKrBuffer, outputJpBuffer)

        processFunctionCall(message, outputKrBuffer, outputJpBuffer)
    }


    private fun processInputSTT(
        message: LiveServerMessage,
        inputSTTBuffer: StringBuilder
    ) {
        message.serverContent().flatMap { it.inputTranscription() }
            .ifPresent {
                it.text().ifPresent { inputSTTChunk -> inputSTTBuffer.append(inputSTTChunk) }
            }
    }


    private fun ProducerScope<GeminiLiveResponse>.processTurnComplete(
        message: LiveServerMessage,
        inputSTTBuffer: StringBuilder,
        outputKrBuffer: StringBuilder,
        outputJpBuffer: StringBuilder
    ) {
        if (message.serverContent().flatMap { it.turnComplete() }.orElse(false)) {
            val finalInput = inputSTTBuffer.toString()
            val finalKrOutput = outputKrBuffer.toString()
            val finalJpOutput = outputJpBuffer.toString()

            logger.debug { "서버가 대답을 완료했습니다. STT: [$finalInput], KrResponse: [$finalKrOutput], JpResponse: [$finalJpOutput]" }

            if (finalInput.isNotEmpty() || finalKrOutput.isNotEmpty() || finalJpOutput.isNotEmpty()) {
                val finalTextOutput = GeminiLiveTextResponse(finalKrOutput, finalJpOutput)
                trySend(GeminiLiveResponse(finalInput, finalTextOutput))
            }

            inputSTTBuffer.clear()
            outputKrBuffer.clear()
            outputJpBuffer.clear()
        }
    }

    private fun processFunctionCall(
        message: LiveServerMessage,
        outputKrBuffer: StringBuilder,
        outputJpBuffer: StringBuilder
    ) {
        message.toolCall().getOrNull()?.functionCalls()?.ifPresent { call ->
            call.map { Pair(it.name()?.get(), it.args()?.get()) }
                .onEach { (name, items) ->
                    logger.info { "함수 이름 : $name, items : $items" }
                }
                .forEach { (name, items) ->
                    when (name) {
                        GeminiFunctionTools.RESPONSE_TEXT -> {
                            val responseChunk = runCatching {
                                mapper.convertValue(items, GeminiLiveTextResponseChunk::class.java)
                            }.getOrElse {
                                logger.error(it) { "function params convert error" }
                                return@forEach
                            }
                            outputKrBuffer.append(responseChunk.krText)
                            outputJpBuffer.append(responseChunk.jpText)
                        }
                    }
                }
        }
    }


    private suspend fun send(
        inputData: Flow<GeminiInput>,
        session: AsyncSession
    ) {
        inputData.collect { data ->

            when (data) {
                is GeminiInput.Audio -> {
                    session.sendRealtimeInput(buildAudioContent(data.chunk))
                        .exceptionally {
                            logger.error(it) { "audio chunk 보내는 중 에러 발생 -> ${it.message}" }
                            return@exceptionally null
                        }.await()
                }

                is GeminiInput.Context -> {
                    session.sendRealtimeInput(
                        buildTextContent(data.shortTermMemory, data.longTermMemory)
                    ).exceptionally {
                        logger.error(it) { "audio chunk 보내는 중 에러 발생 -> ${it.message}" }
                        return@exceptionally null
                    }.await()
                }
            }
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
                    .startOfSpeechSensitivity(StartSensitivity.Known.START_SENSITIVITY_LOW)
                    .silenceDurationMs(config.silenceDurationMs)
            )
            .build()
    }

    private fun buildAudioContent(
        voiceChunk: ByteArray,
    ): LiveSendRealtimeInputParameters {
        return LiveSendRealtimeInputParameters.builder()
            .audio(Blob.builder().mimeType("audio/pcm;rate=16000").data(voiceChunk))
            .build()
    }

    private fun buildTextContent(
        shortTermMemory: String,
        longTermMemory: String
    ): LiveSendRealtimeInputParameters {
        return LiveSendRealtimeInputParameters.builder()
            .text("shortTermMemory: $shortTermMemory, longTermMemory: $longTermMemory")
            .build()
    }

}