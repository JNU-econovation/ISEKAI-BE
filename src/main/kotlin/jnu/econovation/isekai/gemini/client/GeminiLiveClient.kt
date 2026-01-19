package jnu.econovation.isekai.gemini.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.AsyncSession
import com.google.genai.Client
import com.google.genai.types.*
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.client.processor.GeminiMessageProcessor
import jnu.econovation.isekai.gemini.config.GeminiConfig
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel
import jnu.econovation.isekai.gemini.constant.function.GeminiFunctions
import jnu.econovation.isekai.gemini.constant.template.SystemPromptTemplate
import jnu.econovation.isekai.gemini.dto.client.request.GeminiLiveInput
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveOutput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class GeminiLiveClient(
    private val geminiConfig: GeminiConfig,
    private val mapper: ObjectMapper
) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val client = Client.builder()
        .apiKey(geminiConfig.apiKey)
        .build()

    suspend fun getLiveResponse(
        geminiReadySignal: CompletableDeferred<Unit>,
        sessionId: String,
        inputData: Flow<GeminiLiveInput>,
        model: GeminiModel = GeminiModel.GEMINI_2_5_FLASH_NATIVE_AUDIO
    ): Flow<GeminiLiveOutput> {
        return try {
            callbackFlow {
                val session = client.async.live
                    .connect(model.toString(), buildConfig(SystemPromptTemplate.GEMINI_LIVE_TEMPLATE))
                    .await()

                geminiReadySignal.complete(Unit)

                logger.info { "Gemini Live 세션이 연결되었습니다." }

                logger.info { "Gemini 프롬프트 : ${SystemPromptTemplate.GEMINI_LIVE_TEMPLATE}" }

                val processor = GeminiMessageProcessor(mapper)

                session.receive { message ->
                    try {
                        val outputs = processor.process(sessionId, message)
                        outputs.forEach { trySend(it) }
                    } catch (e: Exception) {
                        logger.error(e) { "메시지 처리 중 에러 발생" }
                    }
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
            .thinkingConfig(ThinkingConfig.builder().thinkingBudget(-1).build())
            .realtimeInputConfig(buildRealTimeInputConfig())
            .systemInstruction(Content.fromParts(Part.fromText(prompt)))
            .tools(
                listOf(
                    Tool.builder()
                        .functionDeclarations(listOf(GeminiFunctions.REQUEST_REPLY_FUNCTION))
                        .build()
                )
            )
            .build()
    }

    private suspend fun send(
        inputData: Flow<GeminiLiveInput>,
        session: AsyncSession
    ) {
        inputData.collect { data ->
            when (data) {
                is GeminiLiveInput.Audio -> {
                    session.sendRealtimeInput(buildAudioContent(data.chunk))
                        .exceptionally {
                            logger.error(it) { "audio chunk 보내는 중 에러 발생 -> ${it.message}" }
                            return@exceptionally null
                        }.await()
                }

                is GeminiLiveInput.Text -> {
                    logger.info { "text message 전송 -> ${data.value}" }
                    val content = LiveSendClientContentParameters.builder()
                        .turns(listOf(Content.fromParts(Part.fromText(data.value))))
                        .turnComplete(true)
                        .build()

                    session.sendClientContent(content)
                        .exceptionally {
                            logger.error(it) { "text message 보내는 중 에러 발생 -> ${it.message}" }
                            throw it
                        }.await()
                }

                is GeminiLiveInput.Context -> {
                    logger.info { "Gemini Context 전송: 단기기억 -> ${data.shortTermMemory}, 중기기억 -> ${data.midTermMemory}" }
                    val content = LiveSendClientContentParameters.builder()
                        .turns(
                            listOf(
                                Content.fromParts(Part.fromText("[이전 대화]\n(단기기억) -> ${data.shortTermMemory}\n(중기기억) -> ${data.midTermMemory})"))
                            )
                        )
                        .turnComplete(false)
                        .build()

                    session.sendClientContent(content)
                        .exceptionally {
                            logger.error(it) { "context 보내는 중 에러 발생 -> ${it.message}" }
                            throw it
                        }.await()
                }

                is GeminiLiveInput.ToolResponse -> {
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
                    .silenceDurationMs(geminiConfig.silenceDurationMs)
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