package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.aiServer.dto.internal.TTSResult
import jnu.econovation.isekai.aiServer.exception.NoSuchVoiceException
import jnu.econovation.isekai.aiServer.service.AiServerTTSService
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.service.CharacterCoordinateService
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.extension.geminiRetry
import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.gemini.client.GeminiRestClient
import jnu.econovation.isekai.gemini.constant.enums.GeminiEmotion
import jnu.econovation.isekai.gemini.constant.enums.GeminiLiveFunctionSignature.REQUEST_REPLY
import jnu.econovation.isekai.gemini.constant.enums.GeminiRestFunctionSignature.FINAL_ANSWER
import jnu.econovation.isekai.gemini.constant.enums.GeminiRestFunctionSignature.SEARCH_LONG_TERM_MEMORY_RAG
import jnu.econovation.isekai.gemini.constant.template.SystemPromptTemplate
import jnu.econovation.isekai.gemini.dto.client.request.GeminiLiveInput
import jnu.econovation.isekai.gemini.dto.client.request.GeminiRestInput
import jnu.econovation.isekai.gemini.dto.client.response.*
import jnu.econovation.isekai.session.dto.internal.TurnCompleteDTO
import jnu.econovation.isekai.session.dto.request.SessionBinaryRequest
import jnu.econovation.isekai.session.dto.request.SessionRequest
import jnu.econovation.isekai.session.dto.request.SessionTextRequest
import jnu.econovation.isekai.session.dto.response.SessionBinaryResponse
import jnu.econovation.isekai.session.dto.response.SessionResponse
import jnu.econovation.isekai.session.dto.response.SessionTextResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


@Service
class IsekAiSessionService(
    private val liveClient: GeminiLiveClient,
    private val restClient: GeminiRestClient,
    private val memoryService: ChatMemoryService,
    private val aiServerTTSService: AiServerTTSService,
    private val characterService: CharacterCoordinateService
) {
    private companion object {
        const val OK = "ok"
        val sentenceRegex = Regex("(?<=[.?!~\n])\\s+")
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
        val currentTurnDTO = AtomicReference<TurnCompleteDTO?>(null)
        val thinkingJob = AtomicReference<Job?>(null)
        val isNewTurnFirstPacket = AtomicBoolean(false)

        val characterDTO = characterService.getCharacter(characterId)
            ?: throw InternalServerException(cause = IllegalStateException("캐릭터를 찾지 못함 -> $characterId"))

        val realtimeInput = inputStream.map {
            when (it) {
                is SessionBinaryRequest -> {
                    GeminiLiveInput.Audio(it.content)
                }

                is SessionTextRequest -> {
                    GeminiLiveInput.Text("[TEXT_INPUT] ${it.content.text}")
                }
            }
        }

        val geminiContextInput = Channel<GeminiLiveInput.Context>(
            Channel.BUFFERED,
            BufferOverflow.SUSPEND
        )

        val geminiToolInput = Channel<GeminiLiveInput>(Channel.BUFFERED, BufferOverflow.SUSPEND)

        val geminiLiveInput: Flow<GeminiLiveInput> = merge(
            realtimeInput,
            geminiContextInput.receiveAsFlow(),
            geminiToolInput.receiveAsFlow()
        )

        val ttsInput = Channel<String>(
            Channel.BUFFERED,
            BufferOverflow.SUSPEND
        )

        launch {
            onGeminiLiveReady(
                geminiReadySignal,
                characterDTO,
                hostMemberId,
                geminiContextInput
            )
        }

        val geminiLiveJob = launch {
            liveClient.getLiveResponse(
                geminiReadySignal = geminiReadySignal,
                sessionId = sessionId,
                inputData = geminiLiveInput
            ).collect { output ->
                handleGeminiOutput(
                    isNewTurnFirstPacket = isNewTurnFirstPacket,
                    currentTurnDTO = currentTurnDTO,
                    characterDTO = characterDTO,
                    hostMemberId = hostMemberId,
                    ttsInput = ttsInput,
                    output = output,
                    geminiLiveInputChannel = geminiToolInput,
                    onReply = onReply,
                    thinkingJob = thinkingJob
                )
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
                            if (isNewTurnFirstPacket.compareAndSet(true, false)) {
                                currentTurnDTO.get()?.let { dto ->
                                    logger.info { "TTS 첫 문장 스트리밍 시작 -> 텍스트 응답 전송: ${dto.bot}" }
                                    onReply(
                                        SessionTextResponse.fromTurnComplete(
                                            user = dto.user,
                                            bot = dto.bot
                                        )
                                    )
                                }
                            } else {
                                logger.debug { "이어지는 문장 스트리밍 시작 (클라이언트 전송 스킵)" }
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
        geminiLiveJob.join()
        ttsInput.close()
        ttsJob.join()
    }

    private suspend fun onGeminiLiveReady(
        geminiReadySignal: CompletableDeferred<Unit>,
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        contextInput: Channel<GeminiLiveInput.Context>
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

        contextInput.send(element = GeminiLiveInput.Context(shortTermMemory.content, midTermMemory))
    }

    private suspend fun CoroutineScope.handleGeminiOutput(
        isNewTurnFirstPacket: AtomicBoolean,
        currentTurnDTO: AtomicReference<TurnCompleteDTO?>,
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        ttsInput: Channel<String>,
        output: GeminiLiveOutput,
        geminiLiveInputChannel: Channel<GeminiLiveInput>,
        onReply: suspend (SessionResponse) -> Unit,
        thinkingJob: AtomicReference<Job?>
    ) {
        when (output) {
            is GeminiLiveOutput.InputSTT -> {
                logger.info { "gemini input stt -> ${output.text}" }

                onReply(SessionTextResponse.fromUserSubtitleChunk(output.text))
            }

            is GeminiLiveOutput.FunctionCall -> {
                logger.info { "gemini live output function -> ${output.signature}" }
                logger.info { "gemini live output params -> ${output.params}" }

                handleFunctionCall(
                    isNewTurnFirstPacket = isNewTurnFirstPacket,
                    currentTurnDTO = currentTurnDTO,
                    output = output,
                    geminiLiveInputChannel = geminiLiveInputChannel,
                    onReply = onReply,
                    thinkingJob = thinkingJob,
                    characterDTO = characterDTO,
                    hostMemberId = hostMemberId,
                    ttsInput = ttsInput
                )
            }
        }
    }

    private suspend fun CoroutineScope.handleFunctionCall(
        isNewTurnFirstPacket: AtomicBoolean,
        currentTurnDTO: AtomicReference<TurnCompleteDTO?>,
        output: GeminiLiveOutput.FunctionCall,
        geminiLiveInputChannel: Channel<GeminiLiveInput>,
        onReply: suspend (SessionResponse) -> Unit,
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        thinkingJob: AtomicReference<Job?>,
        ttsInput: Channel<String>
    ) {
        when (output.signature) {
            REQUEST_REPLY -> {
                val params = output.params as GeminiLiveFunctionParams.RequestReply

                sendOKToGeminiLive(output, geminiLiveInputChannel)

                // Gemini가 대답하려는 중에 새로운 응답이 필요할 시 덮어 씌우기
                val previousJob = thinkingJob.getAndSet(null)

                if (previousJob?.isActive == true) {
                    logger.info { "새로운 발화가 들어와 이전 답변 생성을 취소합니다." }
                    previousJob.cancel()

                    onReply(SessionTextResponse.fromInterrupted())
                }

                onReply(SessionTextResponse.fromBotIsThinking())

                onReply(SessionTextResponse.fromUserSubtitleComplete(params.userMessage))

                val newJob = launch {
                    val shortTermMemory = memoryService.getShortTermMemory(
                        characterDTO = characterDTO,
                        hostMemberId = hostMemberId
                    )?.content

                    val midTermMemory = shortTermMemory?.run {
                        memoryService.getMidTermMemory(
                            characterDTO = characterDTO,
                            hostMemberId = hostMemberId
                        )
                    }

                    val context = GeminiRestInput.Context(
                        shortTermMemory = shortTermMemory,
                        midTermMemory = midTermMemory
                    )

                    val systemPrompt = SystemPromptTemplate.build(characterDTO.persona.value)

                    val geminiRestOutput =
                        geminiRetry(times = 3, initialDelay = 1000, factor = 2.0) {
                            restClient.getTextDialogResponse(
                                context = context,
                                systemPrompt = systemPrompt,
                                userMessage = params.userMessage
                            )
                        }

                    val finalResponse: GeminiRestResult = handleRestFunctionCall(
                        geminiRestOutput = geminiRestOutput,
                        characterDTO = characterDTO,
                        hostMemberId = hostMemberId,
                        context = context,
                        systemPrompt = systemPrompt,
                        userMessage = output.params.userMessage
                    )

                    onReply(SessionTextResponse.fromEmotion(finalResponse.emotion))

                    val rawChunks = finalResponse.krTextResponse.split(sentenceRegex)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    val chunks = rawChunks.fold(mutableListOf<String>()) { acc, sentence ->
                        if (acc.isNotEmpty() && acc.last().length <= 10) {
                            val lastIdx = acc.size - 1
                            acc[lastIdx] = "${acc[lastIdx]} $sentence"
                        } else {
                            acc.add(sentence)
                        }
                        acc
                    }

                    chunks.forEach { chunk -> ttsInput.send(chunk) }

                    currentTurnDTO.set(
                        TurnCompleteDTO(
                            user = output.params.userMessage,
                            bot = finalResponse.krTextResponse
                        )
                    )

                    isNewTurnFirstPacket.set(true)

                    launch {
                        saveChatHistory(
                            hostMemberId = hostMemberId,
                            character = characterDTO,
                            user = output.params.userMessage,
                            bot = finalResponse.krTextResponse
                        )
                    }
                }

                thinkingJob.set(newJob)
            }
        }
    }

    private suspend fun handleRestFunctionCall(
        geminiRestOutput: GeminiRestFunctionCall,
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        context: GeminiRestInput.Context,
        systemPrompt: String,
        userMessage: String
    ): GeminiRestResult {
        return when (geminiRestOutput.signature) {
            SEARCH_LONG_TERM_MEMORY_RAG -> {
                val params = geminiRestOutput.params
                        as GeminiRestFunctionParams.SearchLongTermMemoryRAG

                val longTermMemory = memoryService.getLongTermMemory(
                    characterDTO = characterDTO,
                    hostMemberId = hostMemberId,
                    searchText = params.searchText
                )

                val functionResponse = GeminiRestInput.ToolResponse(
                    id = geminiRestOutput.id,
                    functionName = geminiRestOutput.signature.name,
                    result = longTermMemory
                )

                val geminiRestFunctionCall =
                    geminiRetry(times = 3, initialDelay = 1000, factor = 2.0) {
                        restClient.getTextDialogResponse(
                            context = context,
                            systemPrompt = systemPrompt,
                            userMessage = userMessage,
                            functionResponse = functionResponse
                        )
                    }

                GeminiRestResult.fromFunctionCall(geminiRestFunctionCall)
                    ?: throw InternalServerException(cause = IllegalStateException("잘못된 응답입니다. (final result가 아님)"))
            }

            FINAL_ANSWER -> {
                val params = geminiRestOutput.params as GeminiRestFunctionParams.FinalAnswer
                GeminiRestResult(
                    krTextResponse = params.krTextResponse,
                    emotion = GeminiEmotion.from(params.emotion)
                )
            }
        }
    }

    private suspend fun sendOKToGeminiLive(
        functionCall: GeminiLiveOutput.FunctionCall,
        geminiInputChannel: Channel<GeminiLiveInput>
    ) {
        geminiInputChannel.send(
            GeminiLiveInput.ToolResponse(
                id = functionCall.id,
                functionName = functionCall.signature.name,
                result = OK
            )
        )
    }

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
