package jnu.econovation.isekai.gemini.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.AsyncSession
import com.google.genai.Client
import com.google.genai.types.*
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.gemini.constant.enums.GeminiFunctionSignature
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel
import jnu.econovation.isekai.gemini.constant.enums.GeminiVoice
import jnu.econovation.isekai.gemini.constant.function.GeminiFunctions
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.gemini.dto.client.response.GeminiOutput
import kotlinx.coroutines.CompletableDeferred
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

        val GOOGLE_SEARCH_TOOL: Tool = Tool.builder()
            .googleSearch(GoogleSearch.builder().build())
            .build()
    }

    private val client = Client.builder()
        .apiKey(config.apiKey)
        .build()

    suspend fun getLiveResponse(
        geminiReadySignal: CompletableDeferred<Unit>,
        sessionId: String,
        inputData: Flow<GeminiInput>,
        prompt: String,
        model: GeminiModel = GeminiModel.GEMINI_2_5_FLASH_NATIVE_AUDIO
    ): Flow<GeminiOutput> {
        return try {
            callbackFlow {
                val session = client.async.live
                    .connect(model.toString(), buildConfig(prompt))
                    .await()

                geminiReadySignal.complete(Unit)

                logger.info { "Gemini Live 세션이 연결되었습니다." }

                val inputSTTOneSentenceBuffer = StringBuilder()
                val inputSTTSentencesBuffer = StringBuilder()
                val outputSTTBuffer = StringBuilder()

                session.receive { message ->
                    onMessageReceived(
                        sessionId = sessionId,
                        message = message,
                        inputSTTOneSentenceBuffer = inputSTTOneSentenceBuffer,
                        inputSTTSentencesBuffer = inputSTTSentencesBuffer,
                        outputSTTBuffer = outputSTTBuffer,
                    )
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
            .outputAudioTranscription(AudioTranscriptionConfig.builder().build())
            .realtimeInputConfig(buildRealTimeInputConfig())
            .systemInstruction(Content.fromParts(Part.fromText(prompt)))
            .tools(
                listOf(
                    GOOGLE_SEARCH_TOOL,
                    Tool.builder()
                        .functionDeclarations(
                            listOf(
                                GeminiFunctions.EMOTION_FUNCTION,
                                GeminiFunctions.RAG_SEARCH_FUNCTION
                            )
                        )
                        .build()
                )
            )
            .speechConfig(
                SpeechConfig.builder()
                    .languageCode("ko-KR")
                    .voiceConfig(
                        VoiceConfig.builder()
                            .prebuiltVoiceConfig(
                                PrebuiltVoiceConfig.builder()
                                    .voiceName(
                                        GeminiVoice.ZEPHYR.text
                                    ).build()
                            ).build()
                    )
            )
            .build()
    }

    private fun ProducerScope<GeminiOutput>.onMessageReceived(
        sessionId: String,
        message: LiveServerMessage,
        inputSTTSentencesBuffer: StringBuilder,
        inputSTTOneSentenceBuffer: StringBuilder,
        outputSTTBuffer: StringBuilder,
    ) {
        logger.debug { "gemini received -> $message" }

        processInputSTT(message, inputSTTOneSentenceBuffer)
        processOutputSTT(message, outputSTTBuffer)
        processOutputVoiceStream(message)
        processFunctionCall(sessionId, message)
        processInterrupted(message)
        processTurnComplete(
            message = message,
            inputSTTOneSentenceBuffer = inputSTTOneSentenceBuffer,
            inputSTTSentencesBuffer = inputSTTSentencesBuffer,
            outputSTTBuffer = outputSTTBuffer
        )
    }


    private fun ProducerScope<GeminiOutput>.processInputSTT(
        message: LiveServerMessage,
        inputSTTOneSentenceBuffer: StringBuilder
    ) {
        message.serverContent().flatMap { it.inputTranscription() }
            .ifPresent {
                it.text().ifPresent { inputSTTChunk ->
                    inputSTTOneSentenceBuffer.append(inputSTTChunk)
                    trySend(element = GeminiOutput.InputSTT(inputSTTChunk))
                }
            }
    }

    private fun ProducerScope<GeminiOutput>.processOutputSTT(
        message: LiveServerMessage,
        outputSTTBuffer: StringBuilder,
    ) {
        message.serverContent().flatMap { it.outputTranscription() }
            .ifPresent {
                it.text()
                    .ifPresent { outputSTTChunk ->
                        outputSTTBuffer.append(outputSTTChunk)
                        trySend(element = GeminiOutput.OutputSTT(outputSTTChunk))
                    }
            }
    }

    private fun ProducerScope<GeminiOutput>.processOutputVoiceStream(
        message: LiveServerMessage
    ) {
        message.serverContent().flatMap { it.modelTurn() }.flatMap { it.parts() }
            .ifPresent { parts ->
                parts.forEach {
                    it.inlineData()
                        .map { blob -> blob.data()?.get() }
                        .ifPresent { data -> trySend(element = GeminiOutput.VoiceStream(data)) }
                }
            }
    }


    private fun ProducerScope<GeminiOutput>.processTurnComplete(
        message: LiveServerMessage,
        inputSTTOneSentenceBuffer: StringBuilder,
        inputSTTSentencesBuffer: StringBuilder,
        outputSTTBuffer: StringBuilder,
    ) {
        val isTurnComplete = message.serverContent().flatMap { it.turnComplete() }.orElse(false)

        if (isTurnComplete) {
            when {
                outputSTTBuffer.isEmpty() -> {
                    val output = GeminiOutput.InputOneSentenceSTT(
                        text = inputSTTOneSentenceBuffer.toString()
                    )

                    inputSTTSentencesBuffer.append(inputSTTOneSentenceBuffer)
                    inputSTTOneSentenceBuffer.clear()

                    trySend(element = output)
                    logger.info { "사용자 입력 1문장 turn complete. Input: [${output.text}], Output: [${outputSTTBuffer}]" }
                }

                outputSTTBuffer.isNotEmpty() -> {
                    val finalInput = if (inputSTTSentencesBuffer.isNotEmpty()) {
                        inputSTTSentencesBuffer.toString()
                    } else {
                        inputSTTOneSentenceBuffer.toString()
                    }.trim()

                    val finalOutput = outputSTTBuffer.toString().trim()

                    inputSTTSentencesBuffer.clear()
                    inputSTTOneSentenceBuffer.clear()
                    outputSTTBuffer.clear()

                    val turnComplete = GeminiOutput.TurnComplete(
                        inputSTT = finalInput,
                        outputSTT = finalOutput
                    )

                    trySend(turnComplete)
                    logger.info { "gemini 응답 turn complete. Input: [${turnComplete.inputSTT}], Output: [${turnComplete.outputSTT}]" }
                }
            }
        }
    }


    private fun ProducerScope<GeminiOutput>.processFunctionCall(
        sessionId: String,
        message: LiveServerMessage
    ) {
        message.toolCall().getOrNull()?.functionCalls()?.ifPresent { call ->
            call.map { Triple(it.id()?.orElse(""), it.name()?.get(), it.args()?.get()) }
                .onEach { (id, name, items) ->
                    logger.info { "[Session:$sessionId] 함수 수신됨 - 함수 id : $id, 함수 이름 : $name, items : $items" }
                }
                .forEach { (id, name, items) ->
                    val enum = name
                        ?.let { GeminiFunctionSignature.fromText(it) }
                        ?: run {
                            logger.error { "function name error -> $name" }
                            return@forEach
                        }

                    val params = runCatching {
                        mapper.convertValue(items, enum.paramsType.java)
                    }.getOrElse {
                        logger.error(it) { "function params convert error" }
                        return@forEach
                    }

                    val functionId = id ?: ""

                    val output = GeminiOutput.FunctionCall(
                        id = functionId,
                        signature = enum,
                        params = params
                    )

                    trySend(output)
                }
        }
    }

    private fun ProducerScope<GeminiOutput>.processInterrupted(message: LiveServerMessage) {
        val isInterrupted = message.serverContent().flatMap { it.interrupted() }.orElse(false)
        if (isInterrupted) {
            logger.info { "인터럽트 되었습니다." }

            trySend(element = GeminiOutput.Interrupted())
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
                    logger.info { "Gemini Context 전송: 단기기억 -> ${data.shortTermMemory}, 중기기억 -> ${data.midTermMemory}" }
                    val content = LiveSendClientContentParameters.builder()
                        .turns(
                            listOf(
                                Content.fromParts(Part.fromText("[이전 대화]\n(단기기억) -> ${data.shortTermMemory}\n(중기기억) -> ${data.midTermMemory})"))
                            )
                        ).build()

                    session.sendClientContent(content)
                        .exceptionally {
                            logger.error(it) { "context 보내는 중 에러 발생 -> ${it.message}" }
                            return@exceptionally null
                        }.await()
                }

                is GeminiInput.ToolResponse -> {
                    logger.info { "Tool Response 전송: ID=${data.id}, Result=${data.result}" }

                    val functionResponse = FunctionResponse.builder()
                        .id(data.id)
                        .name(data.functionName)
                        .response(mapOf("result" to data.result))
                        .build()

                    val params = LiveSendToolResponseParameters.builder()
                        .functionResponses(functionResponse)
                        .build()

                    session.sendToolResponse(params)
                        .exceptionally { e ->
                            logger.error(e) { "Tool Response 전송 실패" }
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
}