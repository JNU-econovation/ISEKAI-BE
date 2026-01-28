package jnu.econovation.isekai.session.service

import com.google.genai.errors.ServerException
import jnu.econovation.isekai.aiServer.dto.internal.TTSOutput
import jnu.econovation.isekai.aiServer.exception.NoSuchVoiceException
import jnu.econovation.isekai.aiServer.service.AiServerTTSService
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.service.CharacterCoordinateService
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.util.retry
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

data class SessionState(
    val currentTurn: AtomicReference<TurnCompleteDTO?>,
    val thinkingJob: AtomicReference<Job?>,
    val isNewTurnFirstPacket: AtomicBoolean
) {
    fun setNewTurn(dto: TurnCompleteDTO) {
        currentTurn.set(dto)
        isNewTurnFirstPacket.set(true)
    }

    fun setThinkingJob(job: Job) {
        thinkingJob.set(job)
    }

    fun getCurrentTurn(): TurnCompleteDTO? = currentTurn.get()

    fun consumeFirstPacketFlag(): Boolean {
        return isNewTurnFirstPacket.compareAndSet(true, false)
    }
}

class SessionChannels private constructor(
    val geminiContext: Channel<GeminiLiveInput.Context>,
    val geminiTool: Channel<GeminiLiveInput>,
    val ttsInput: Channel<String>
) {
    companion object {
        fun create(): SessionChannels {
            return SessionChannels(
                geminiContext = Channel(Channel.BUFFERED, BufferOverflow.SUSPEND),
                geminiTool = Channel(Channel.BUFFERED, BufferOverflow.SUSPEND),
                ttsInput = Channel(Channel.BUFFERED, BufferOverflow.SUSPEND)
            )
        }
    }

    fun createMergedGeminiInput(realtimeInput: Flow<GeminiLiveInput>): Flow<GeminiLiveInput> {
        return merge(realtimeInput, geminiContext.receiveAsFlow(), geminiTool.receiveAsFlow())
    }

    fun closeAll() {
        geminiContext.close()
        geminiTool.close()
        ttsInput.close()
    }
}

data class SessionContext(
    val state: SessionState,
    val channels: SessionChannels,
    val character: CharacterDTO,
    val hostMemberId: Long,
    val onReply: suspend (SessionResponse) -> Unit
)

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

        const val TEXT_INPUT_PREFIX = "[TEXT_INPUT]"

        const val GEMINI_RETRY_TIMES = 3
        const val GEMINI_RETRY_INITIAL_DELAY = 1000L
        const val GEMINI_RETRY_FACTOR = 2.0

        val GEMINI_RETRY_CONDITION: (Exception) -> Boolean = {
            it is InternalServerException || it is ServerException
        }

        val SENTENCE_REGEX = Regex("(?<=[.?!~\n])\\s+")

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
        val context: SessionContext = initializeContext(characterId, hostMemberId, onReply)

        launch {
            onGeminiLiveReady(
                geminiReadySignal = geminiReadySignal,
                characterDTO = context.character,
                hostMemberId = context.hostMemberId,
                contextInput = context.channels.geminiContext
            )
        }

        val geminiLiveJob = launch {

            val realtimeInput: Flow<GeminiLiveInput> = routeRealtimeInput(inputStream)

            liveClient.getLiveResponse(
                geminiReadySignal = geminiReadySignal,
                sessionId = sessionId,
                inputData = context.channels.createMergedGeminiInput(realtimeInput)
            ).collect { output ->
                routeGeminiLiveOutput(output, context)
            }

        }

        val ttsJob = launch {
            runCatching {

                aiServerTTSService.tts(
                    aiServerReadySignal = aiServerReadySignal,
                    voiceId = context.character.voiceId,
                    input = context.channels.ttsInput.receiveAsFlow()
                ).collect { output -> routeTTSOutput(output, context) }

            }.onFailure {
                when (it) {
                    is NoSuchVoiceException -> {
                        recoverVoiceId(characterId)
                        throw it
                    }

                    else -> throw it
                }
            }
        }

        awaitJobCompletion(geminiLiveJob, ttsJob, context)
    }

    private suspend fun initializeContext(
        characterId: Long,
        hostMemberId: Long,
        onReply: suspend (SessionResponse) -> Unit
    ): SessionContext {
        val characterDTO = characterService.getCharacter(characterId)
            ?: throw InternalServerException(cause = IllegalStateException("캐릭터를 찾지 못함 -> $characterId"))

        val state = SessionState(
            currentTurn = AtomicReference(null),
            thinkingJob = AtomicReference(null),
            isNewTurnFirstPacket = AtomicBoolean(false)
        )

        val channels = SessionChannels.create()

        return SessionContext(
            state = state,
            channels = channels,
            character = characterDTO,
            hostMemberId = hostMemberId,
            onReply = onReply
        )
    }

    private fun routeRealtimeInput(inputStream: Flow<SessionRequest>): Flow<GeminiLiveInput> {
        return inputStream.map {
            when (it) {
                is SessionBinaryRequest -> GeminiLiveInput.Audio(it.content)
                is SessionTextRequest -> GeminiLiveInput.Text("$TEXT_INPUT_PREFIX ${it.content.text}")
            }
        }
    }

    private suspend fun routeTTSOutput(output: TTSOutput, context: SessionContext) {
        when (output) {
            is TTSOutput.StartStreaming -> {
                if (context.state.consumeFirstPacketFlag()) {
                    context.state.getCurrentTurn()?.let { dto ->

                        logger.info { "TTS 첫 문장 스트리밍 시작 -> 텍스트 응답 전송: ${dto.bot}" }

                        context.onReply(
                            SessionTextResponse.fromTurnComplete(user = dto.user, bot = dto.bot)
                        )

                    }
                } else {
                    logger.debug { "이어지는 문장 스트리밍 시작 (클라이언트 전송 스킵)" }
                }
            }

            is TTSOutput.Voice -> context.onReply(SessionBinaryResponse(output.byteArray))
        }
    }

    private suspend fun awaitJobCompletion(
        geminiLiveJob: Job,
        ttsJob: Job,
        context: SessionContext
    ) {
        geminiLiveJob.join()
        context.channels.closeAll()
        ttsJob.join()
    }

    private suspend fun onGeminiLiveReady(
        geminiReadySignal: CompletableDeferred<Unit>,
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        contextInput: Channel<GeminiLiveInput.Context>
    ) {
        geminiReadySignal.await()

        val shortTermMemory = memoryService.getShortTermMemory(
            characterDTO = characterDTO,
            hostMemberId = hostMemberId
        ) ?: run {
            logger.warn { "단기 기억이 없음" }

            return
        }

        val midTermMemory = memoryService.getMidTermMemory(
            characterDTO = characterDTO,
            hostMemberId = hostMemberId
        )

        contextInput.send(element = GeminiLiveInput.Context(shortTermMemory.content, midTermMemory))
    }

    private suspend fun CoroutineScope.routeGeminiLiveOutput(
        output: GeminiLiveOutput,
        context: SessionContext
    ) {
        when (output) {
            is GeminiLiveOutput.InputSTT -> {
                logger.info { "gemini input stt -> ${output.text}" }

                context.onReply(SessionTextResponse.fromUserSubtitleChunk(output.text))
            }

            is GeminiLiveOutput.FunctionCall -> {
                logger.info { "gemini live output function -> ${output.signature}" }
                logger.info { "gemini live output params -> ${output.params}" }

                routeGeminiLiveFunctionCall(output, context)
            }
        }
    }

    private suspend fun CoroutineScope.routeGeminiLiveFunctionCall(
        output: GeminiLiveOutput.FunctionCall,
        context: SessionContext
    ) {
        when (output.signature) {
            REQUEST_REPLY -> {
                val params = output.params as GeminiLiveFunctionParams.RequestReply

                val previousJob: Job? = context.state.thinkingJob.getAndSet(null)

                if (previousJob?.isActive == true) {
                    logger.info { "새로운 발화가 들어와 이전 답변 생성을 취소합니다." }
                    previousJob.cancel()
                    context.onReply(SessionTextResponse.fromInterrupted())
                }

                context.onReply(SessionTextResponse.fromBotIsThinking())
                context.onReply(SessionTextResponse.fromUserSubtitleComplete(params.userMessage))

                val newJob: Job = launch {
                    val memoryContext = getMemoryContext(context.character, context.hostMemberId)
                    val systemPrompt = SystemPromptTemplate.build(context.character.persona.value)

                    val geminiRestOutput: GeminiRestFunctionCall = retry(
                        times = GEMINI_RETRY_TIMES,
                        initialDelay = GEMINI_RETRY_INITIAL_DELAY,
                        factor = GEMINI_RETRY_FACTOR,
                        retryCondition = GEMINI_RETRY_CONDITION
                    ) {
                        restClient.getTextDialogResponse(
                            context = memoryContext,
                            systemPrompt = systemPrompt,
                            userMessage = params.userMessage
                        )
                    }

                    val finalResponse: GeminiRestResult = routeRestFunctionCall(
                        geminiRestOutput = geminiRestOutput,
                        characterDTO = context.character,
                        hostMemberId = context.hostMemberId,
                        context = memoryContext,
                        systemPrompt = systemPrompt,
                        userMessage = output.params.userMessage
                    )

                    context.onReply(SessionTextResponse.fromEmotion(finalResponse.emotion))

                    val rawChunks = finalResponse.krTextResponse.split(SENTENCE_REGEX)
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

                    chunks.forEach { chunk -> context.channels.ttsInput.send(chunk) }

                    context.state.setNewTurn(
                        TurnCompleteDTO(
                            user = output.params.userMessage,
                            bot = finalResponse.krTextResponse
                        )
                    )

                    launch {
                        saveChatHistory(
                            hostMemberId = context.hostMemberId,
                            character = context.character,
                            user = output.params.userMessage,
                            bot = finalResponse.krTextResponse
                        )
                    }
                }

                context.state.setThinkingJob(newJob)

                sendOKToGeminiLive(
                    functionCall = output,
                    geminiInputChannel = context.channels.geminiTool
                )
            }
        }
    }

    private suspend fun routeRestFunctionCall(
        geminiRestOutput: GeminiRestFunctionCall,
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        context: GeminiRestInput.Context,
        systemPrompt: String,
        userMessage: String
    ): GeminiRestResult {
        return when (geminiRestOutput.signature) {
            SEARCH_LONG_TERM_MEMORY_RAG -> {
                val params =
                    geminiRestOutput.params as GeminiRestFunctionParams.SearchLongTermMemoryRAG

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

                val geminiRestFunctionCall = retry(
                    times = GEMINI_RETRY_TIMES,
                    initialDelay = GEMINI_RETRY_INITIAL_DELAY,
                    factor = GEMINI_RETRY_FACTOR,
                    retryCondition = GEMINI_RETRY_CONDITION
                ) {
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

    private suspend fun getMemoryContext(
        characterDTO: CharacterDTO,
        hostMemberId: Long
    ): GeminiRestInput.Context {
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

        return GeminiRestInput.Context(
            shortTermMemory = shortTermMemory,
            midTermMemory = midTermMemory
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
        characterService
            .recoverVoiceIdToDefault(characterId)
            .onFailure { exception -> logger.error(exception) { "voice id recover 실패" } }
            .onSuccess { logger.info { "voice id recover 성공" } }
    }
}
