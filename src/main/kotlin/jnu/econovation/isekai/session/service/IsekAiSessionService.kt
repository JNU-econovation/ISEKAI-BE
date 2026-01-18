package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.aiServer.dto.request.TTSRequest
import jnu.econovation.isekai.aiServer.service.AiServerTTSService
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.service.CharacterCoordinateService
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.gemini.constant.enums.GeminiEmotion
import jnu.econovation.isekai.gemini.constant.enums.GeminiFunctionSignature.EMOTION
import jnu.econovation.isekai.gemini.constant.enums.GeminiFunctionSignature.SEARCH_LONG_TERM_MEMORY_RAG
import jnu.econovation.isekai.gemini.constant.template.SystemPromptTemplate
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.gemini.dto.client.response.GeminiFunctionParams
import jnu.econovation.isekai.gemini.dto.client.response.GeminiOutput
import jnu.econovation.isekai.prompt.service.PromptService
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


@Service
class IsekAiSessionService(
    private val liveClient: GeminiLiveClient,
    private val memoryService: ChatMemoryService,
    private val promptService: PromptService,
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
        val characterDTO = characterService.getCharacter(characterId)
            ?: throw InternalServerException(cause = IllegalStateException("캐릭터를 찾지 못함 -> $characterId"))

        val prompt = promptService.getPrompt(characterDTO)

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

        val ttsInput = Channel<TTSRequest>(
            Channel.BUFFERED,
            BufferOverflow.SUSPEND
        )

        launch { onGeminiReady(geminiReadySignal, characterDTO, hostMemberId, geminiContextInput) }

        val geminiJob = launch {
            liveClient.getLiveResponse(
                geminiReadySignal = geminiReadySignal,
                sessionId = sessionId,
                inputData = geminiInput,
                prompt = SystemPromptTemplate.build(prompt)
            ).collect { output ->
                handleGeminiOutput(
                    output = output,
                    hostMemberId = hostMemberId,
                    character = characterDTO,
                    geminiInputChannel = geminiToolInput,
                    onReply = onReply
                ) { ttsRequest -> ttsInput.send(ttsRequest) }
            }
        }


        val ttsJob = launch {
            aiServerTTSService.tts(
                aiServerReadySignal = aiServerReadySignal,
                voiceId = characterDTO.voiceId,
                requestStream = ttsInput.receiveAsFlow()
            ).collect { onReply(SessionBinaryResponse(it.payload)) }
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
        output: GeminiOutput,
        hostMemberId: Long,
        character: CharacterDTO,
        geminiInputChannel: Channel<GeminiInput>,
        onReply: suspend (SessionResponse) -> Unit,
        onTTSInput: suspend (TTSRequest) -> Unit
    ) {
        when (output) {
            is GeminiOutput.InputSTT -> {
                logger.info { "gemini input stt -> ${output.text}" }

                onReply(SessionTextResponse.fromUserSubtitleChunk(output.text))
            }

            is GeminiOutput.InputOneSentenceSTT -> {
                logger.info { "gemini input one sentence stt -> ${output.text}" }


                onReply(SessionTextResponse.fromUserOneSentenceSubtitle(output.text))
            }

            is GeminiOutput.OutputSTT -> {
                logger.info { "gemini output stt -> ${output.text}" }

                onReply(SessionTextResponse.fromBotSubtitle(output.text))
            }

            is GeminiOutput.OutputOneSentenceSTT -> {
                logger.info { "gemini output one sentence stt -> ${output.text}" }

                onTTSInput(TTSRequest(output.text))
            }

            is GeminiOutput.Interrupted -> {
                logger.info { "Gemini가 응답 중에 사용자가 끼어 듦" }

                onReply(SessionTextResponse.fromInterrupted())
            }

            is GeminiOutput.FunctionCall -> {
                logger.info { "gemini output function -> ${output.signature}" }
                logger.info { "gemini output params -> ${output.params}" }


                handleFunctionCall(
                    output = output,
                    hostMemberId = hostMemberId,
                    character = character,
                    geminiInputChannel = geminiInputChannel,
                    onReply = onReply
                )
            }

            is GeminiOutput.VoiceStream -> {
//                onReply(SessionBinaryResponse(output.chunk))

                //todo: ai 서버 구현 되면 거기로 보내기
                //logger.debug { "gemini output 음성 메세지 수신 -> 크기 : ${output.chunk.size}" }
            }

            is GeminiOutput.TurnComplete -> {
                logger.info { "gemini output turn complete -> ${output.inputSTT}, ${output.outputSTT}" }

                val response = SessionTextResponse.fromTurnComplete(
                    user = output.inputSTT,
                    bot = output.outputSTT
                )

                onReply(response)

                launch {
                    saveChatHistory(hostMemberId, character, output)
                }
            }

        }
    }

    private suspend fun handleFunctionCall(
        output: GeminiOutput.FunctionCall,
        hostMemberId: Long,
        character: CharacterDTO,
        geminiInputChannel: Channel<GeminiInput>,
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

                if (!output.isMock) {
                    sendOKToGemini(output, geminiInputChannel)
                }

                val emotion = GeminiEmotion.from(params.emotion)
                    ?: run {
                        logger.warn { "Gemini가 지정되지 않는 감정을 나타냄 -> ${params.emotion}" }
                        return
                    }

                onReply(SessionTextResponse.fromEmotion(emotion))
            }
        }
    }

    private suspend fun sendOKToGemini(
        functionCall: GeminiOutput.FunctionCall,
        geminiInputChannel: Channel<GeminiInput>
    ) {
        geminiInputChannel.send(
            GeminiInput.ToolResponse(
                id = functionCall.id,
                functionName = functionCall.signature.name,
                result = OK
            )
        )
    }

    private suspend fun saveChatHistory(
        hostMemberId: Long,
        character: CharacterDTO,
        output: GeminiOutput.TurnComplete
    ) {
        supervisorScope {
            memoryService.save(
                hostMemberId = hostMemberId,
                characterDTO = character,
                chatDTO = ChatDTO(output.inputSTT, output.outputSTT)
            )
        }
    }
}