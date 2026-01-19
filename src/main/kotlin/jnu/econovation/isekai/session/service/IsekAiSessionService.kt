package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.aiServer.dto.internal.TTSResult
import jnu.econovation.isekai.aiServer.exception.NoSuchVoiceException
import jnu.econovation.isekai.aiServer.service.AiServerTTSService
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.service.CharacterCoordinateService
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.extension.clear
import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.gemini.constant.enums.GeminiEmotion
import jnu.econovation.isekai.gemini.constant.enums.GeminiFunctionSignature.*
import jnu.econovation.isekai.gemini.constant.template.SystemPromptTemplate
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.gemini.dto.client.response.GeminiFunctionParams
import jnu.econovation.isekai.gemini.dto.client.response.GeminiOutput
import jnu.econovation.isekai.session.dto.internal.TurnCompleteDTO
import jnu.econovation.isekai.session.dto.request.SessionBinaryRequest
import jnu.econovation.isekai.session.dto.request.SessionRequest
import jnu.econovation.isekai.session.dto.request.SessionTextRequest
import jnu.econovation.isekai.session.dto.response.SessionBinaryResponse
import jnu.econovation.isekai.session.dto.response.SessionResponse
import jnu.econovation.isekai.session.dto.response.SessionTextResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference


@Service
class IsekAiSessionService(
    private val liveClient: GeminiLiveClient,
    private val memoryService: ChatMemoryService,
    private val aiServerTTSService: AiServerTTSService,
    private val characterService: CharacterCoordinateService
) {
    private companion object {
        const val OK = "ok"

        val logger = KotlinLogging.logger {}
    }

    suspend fun processInputStream(
        sessionId: String,
        geminiReadySignal: CompletableDeferred<Unit>,
        aiServerReadySignal: CompletableDeferred<Unit>,
        inputStream: Flow<SessionRequest>,
        characterId: Long,
        hostMemberId: Long,
        onReply: suspend (SessionResponse) -> Unit
    ) = supervisorScope {
        val inputSTTBuffer = StringBuffer()
        val currentTurnDTO = AtomicReference<TurnCompleteDTO?>(null)

        val characterDTO = characterService.getCharacter(characterId)
            ?: throw InternalServerException(cause = IllegalStateException("캐릭터를 찾지 못함 -> $characterId"))

        val realtimeInput = inputStream.map {
            when (it) {
                is SessionBinaryRequest -> {
                    GeminiInput.Audio(it.content)
                }

                is SessionTextRequest -> {
                    GeminiInput.Text(it.content.text)
                }
            }
        }

        val geminiContextInput = Channel<GeminiInput.Context>(
            Channel.BUFFERED,
            BufferOverflow.SUSPEND
        )

        val geminiToolInput = Channel<GeminiInput>(Channel.BUFFERED, BufferOverflow.SUSPEND)

        val geminiInput: Flow<GeminiInput> = merge(
            realtimeInput,
            geminiContextInput.receiveAsFlow(),
            geminiToolInput.receiveAsFlow()
        )

        val ttsInput = Channel<String>(
            Channel.BUFFERED,
            BufferOverflow.SUSPEND
        )

        launch { onGeminiReady(geminiReadySignal, characterDTO, hostMemberId, geminiContextInput) }

        val geminiJob = launch {
            liveClient.getLiveResponse(
                geminiReadySignal = geminiReadySignal,
                sessionId = sessionId,
                inputData = geminiInput,
                prompt = SystemPromptTemplate.build(characterDTO.persona.value)
            ).collect { output ->
                handleGeminiOutput(
                    inputSTTBuffer = inputSTTBuffer,
                    currentTurnDTO = currentTurnDTO,
                    output = output,
                    hostMemberId = hostMemberId,
                    character = characterDTO,
                    geminiInputChannel = geminiToolInput,
                    onReply = onReply
                ) { ttsRequest -> ttsInput.send(ttsRequest) }
            }
        }


        val ttsJob = launch {
            runCatching {
                aiServerTTSService.tts(
                    aiServerReadySignal = aiServerReadySignal,
                    voiceId = characterDTO.voiceId,
                    requestStream = ttsInput.receiveAsFlow()
                ).collect { result ->
                    when (result) {
                        is TTSResult.StartStreaming -> {
                            currentTurnDTO.get()?.let { dto ->
                                logger.info { "TTS 스트리밍 시작 -> 텍스트 응답 전송: ${dto.bot}" }
                                onReply(
                                    SessionTextResponse.fromTurnComplete(
                                        user = dto.user,
                                        bot = dto.bot
                                    )
                                )
                            }
                        }

                        is TTSResult.Voice -> {
                            onReply(SessionBinaryResponse(result.byteArray))
                        }
                    }
                }
            }.onFailure {
                when (it) {
                    is NoSuchVoiceException -> {
                        recoverVoiceId(characterId)
                        throw it
                    }

                    else -> {
                        throw it
                    }
                }
            }
        }

        //Gemini Job이 끝나면 TTS도 끝내도록 (이게 없으면 DeadLock)
        geminiJob.join()
        ttsInput.close()
        ttsJob.join()
    }

    private suspend fun onGeminiReady(
        geminiReadySignal: CompletableDeferred<Unit>,
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        contextInput: Channel<GeminiInput.Context>
    ) = supervisorScope {
        geminiReadySignal.await()

        val shortTermMemory = memoryService.getShortTermMemory(
            characterDTO = characterDTO,
            hostMemberId = hostMemberId
        ) ?: run {
            logger.warn { "단기 기억이 없음" }

            return@supervisorScope
        }

        val midTermMemory = memoryService.getMidTermMemory(
            characterDTO = characterDTO,
            hostMemberId = hostMemberId
        )

        contextInput.send(element = GeminiInput.Context(shortTermMemory.content, midTermMemory))
    }

    private suspend fun CoroutineScope.handleGeminiOutput(
        inputSTTBuffer: StringBuffer,
        currentTurnDTO: AtomicReference<TurnCompleteDTO?>,
        output: GeminiOutput,
        hostMemberId: Long,
        character: CharacterDTO,
        geminiInputChannel: Channel<GeminiInput>,
        onReply: suspend (SessionResponse) -> Unit,
        onTTSInput: suspend (String) -> Unit,
    ) {
        when (output) {
            is GeminiOutput.InputSTT -> {
                logger.info { "gemini input stt -> ${output.text}" }

                inputSTTBuffer.append(output.text)

                onReply(SessionTextResponse.fromUserSubtitleChunk(output.text))
            }

            is GeminiOutput.VoiceStream -> {
                /* no-op */
            }

            is GeminiOutput.Interrupted -> {
                logger.info { "Gemini가 응답 중에 사용자가 끼어 듦" }

                onReply(SessionTextResponse.fromInterrupted())
            }

            is GeminiOutput.FunctionCall -> {
                logger.info { "gemini output function -> ${output.signature}" }
                logger.info { "gemini output params -> ${output.params}" }

                handleFunctionCall(
                    inputSTTBuffer = inputSTTBuffer,
                    currentTurnDTO = currentTurnDTO,
                    output = output,
                    hostMemberId = hostMemberId,
                    character = character,
                    geminiInputChannel = geminiInputChannel,
                    onTTSInput = onTTSInput,
                    onReply = onReply
                )
            }

        }
    }

    private suspend fun CoroutineScope.handleFunctionCall(
        inputSTTBuffer: StringBuffer,
        currentTurnDTO: AtomicReference<TurnCompleteDTO?>,
        output: GeminiOutput.FunctionCall,
        hostMemberId: Long,
        character: CharacterDTO,
        geminiInputChannel: Channel<GeminiInput>,
        onTTSInput: suspend (String) -> Unit,
        onReply: suspend (SessionTextResponse) -> Unit
    ) {
        when (output.signature) {
            SEARCH_LONG_TERM_MEMORY_RAG -> {
                val params = output.params as GeminiFunctionParams.SearchLongTermMemoryRAG

                val longTermMemory: String = memoryService.getLongTermMemory(
                    characterDTO = character,
                    hostMemberId = hostMemberId,
                    searchText = params.searchText
                )

                val toolResponse = GeminiInput.ToolResponse(
                    id = output.id,
                    functionName = output.signature.name,
                    result = longTermMemory
                )

                geminiInputChannel.send(toolResponse)
            }

            EMOTION -> {
                val params = output.params as GeminiFunctionParams.Emotion

                logger.info { "Gemini가 현재 ${params}한 감정을 느끼고 있음" }

                val emotion = GeminiEmotion.from(params.emotion)
                    ?: run {
                        logger.warn { "Gemini가 지정되지 않는 감정을 나타냄 -> ${params.emotion}" }
                        return
                    }

                onReply(SessionTextResponse.fromEmotion(emotion))
            }

            RESPONSE_TEXT -> {
                val params = output.params as GeminiFunctionParams.ResponseText

                val inputSTTResult = inputSTTBuffer.toString()

                inputSTTBuffer.clear()

                launch {
                    saveChatHistory(
                        hostMemberId = hostMemberId,
                        character = character,
                        user = inputSTTResult,
                        bot = params.krResponseText
                    )
                }

                onReply(SessionTextResponse.fromBotIsThinking())

                val turnDTO = TurnCompleteDTO(
                    user = inputSTTResult,
                    bot = params.krResponseText
                )

                currentTurnDTO.set(turnDTO)

                onTTSInput(params.krResponseText)
            }
        }
    }

//    private suspend fun sendOKToGemini(
//        functionCall: GeminiOutput.FunctionCall,
//        geminiInputChannel: Channel<GeminiInput>
//    ) {
//        geminiInputChannel.send(
//            GeminiInput.ToolResponse(
//                id = functionCall.id,
//                functionName = functionCall.signature.name,
//                result = OK
//            )
//        )
//    }

    private suspend fun saveChatHistory(
        hostMemberId: Long,
        character: CharacterDTO,
        user: String,
        bot: String
    ) {
        supervisorScope {
            memoryService.save(
                hostMemberId = hostMemberId,
                characterDTO = character,
                chatDTO = ChatDTO(user, bot)
            )
        }
    }

    private fun recoverVoiceId(characterId: Long) {
        logger.info { "잘못된 voice id로 인해 디폴트 voice로 전환 중" }
        characterService.recoverVoiceIdToDefault(characterId)
            .onFailure { exception ->
                logger.error(exception) { "voice id recover 실패" }
            }
            .onSuccess {
                logger.info { "voice id recover 성공" }
            }
    }
}
