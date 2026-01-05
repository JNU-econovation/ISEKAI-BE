package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.aiServer.dto.request.TTSRequest
import jnu.econovation.isekai.aiServer.service.AiServerTTSService
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.service.CharacterCoordinateService
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveResponse
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveTextResponse
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveTurnCompleteResponse
import jnu.econovation.isekai.prompt.service.PromptService
import jnu.econovation.isekai.session.constant.SessionConstant.FLOW_BUFFER_SIZE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
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
        val logger = KotlinLogging.logger {}
    }

    suspend fun processVoiceChunk(
        sessionId: String,
        rtzrReadySignal: CompletableDeferred<Unit>,
        aiServerReadySignal: CompletableDeferred<Unit>,
        voiceStream: Flow<ByteArray>,
        personaId: Long,
        onVoiceChunk: suspend (ByteArray) -> Unit,
        onSubtitle: suspend (String) -> Unit
    ) = supervisorScope {
        val characterDTO = characterService.getCharacter(personaId)
            ?: throw InternalServerException(cause = IllegalStateException("캐릭터를 찾지 못함 -> $personaId"))

        val prompt = promptService.getPrompt(characterDTO)

        val geminiInputFlow = createGeminiInputStream(
            voiceStream = voiceStream,
            rtzrReadySignal = rtzrReadySignal,
            characterDTO = characterDTO
        )

        val geminiRawResponse = liveClient
            .getLiveResponse(sessionId, geminiInputFlow, prompt)
            .shareIn(this, SharingStarted.Lazily)

        val turnCompleteJob = launch {
            handleTurnCompletion(geminiRawResponse, characterDTO)
        }

        try {
            processOutputSynchronization(
                geminiRawResponse = geminiRawResponse,
                aiServerReadySignal = aiServerReadySignal,
                onSubtitle = onSubtitle,
                onVoiceChunk = onVoiceChunk
            )
        } finally {
            turnCompleteJob.cancel()
        }
    }

    private suspend fun CoroutineScope.createGeminiInputStream(
        voiceStream: Flow<ByteArray>,
        rtzrReadySignal: CompletableDeferred<Unit>,
        characterDTO: CharacterDTO
    ): Flow<GeminiInput> {
        val sharedVoiceStream = voiceStream
            .buffer(capacity = FLOW_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .shareIn(this, SharingStarted.Lazily)

        val voiceFastInput = sharedVoiceStream.map { GeminiInput.Audio(it) }

        val contextSlowInput = memoryService
            .findMemoriesFromVoiceStream(
                rtzrReadySignal = rtzrReadySignal,
                voiceChunk = sharedVoiceStream,
                characterDTO = characterDTO,
                hostMemberId = 1L,
            )
            .filter { it.shortTermMemory.isNotEmpty() }
            .onEach { logger.info { "Gemini context updated: $it" } }

        return merge(voiceFastInput, contextSlowInput)
    }

    private suspend fun handleTurnCompletion(
        geminiRawResponse: Flow<GeminiLiveResponse>,
        characterDTO: CharacterDTO
    ) {
        geminiRawResponse
            .filterIsInstance<GeminiLiveTurnCompleteResponse>()
            .collect { response ->
                logger.info { "Turn complete. Saving memory." }
                memoryService.save(
                    characterDTO = characterDTO,
                    ChatDTO(input = response.inputSTT, output = response.krText)
                )
            }
    }

    private suspend fun processOutputSynchronization(
        geminiRawResponse: Flow<GeminiLiveResponse>,
        aiServerReadySignal: CompletableDeferred<Unit>,
        onSubtitle: suspend (String) -> Unit,
        onVoiceChunk: suspend (ByteArray) -> Unit
    ) = supervisorScope {
        val textResponseFlow = geminiRawResponse
            .filterIsInstance<GeminiLiveTextResponse>()
            .shareIn(this, SharingStarted.Lazily)

        val subtitleQueue = Channel<String>(Channel.BUFFERED)

        val subtitleJob = launch {
            try {
                textResponseFlow.collect {
                    logger.debug { "Subtitle queued: ${it.krText}" }
                    subtitleQueue.send(it.krText)
                }
            } finally {
                subtitleQueue.close()
            }
        }

        val ttsRequestStream = textResponseFlow
            .map { TTSRequest(it.jpText) }

        val ttsAudioStream = aiServerTTSService
            .tts(ttsRequestStream, aiServerReadySignal)
            .catch { e -> logger.error(e) { "TTS Stream Error" } }

        var isWaitingForNewSentence = true

        try {
            ttsAudioStream.collect { ttsResponse ->
                if (isWaitingForNewSentence) {
                    val subtitle = subtitleQueue.receiveCatching().getOrNull()
                    if (subtitle != null) {
                        onSubtitle(subtitle)
                    } else {
                        logger.warn { "오디오가 수신됐지만 자막 큐가 비어있음" }
                    }
                    isWaitingForNewSentence = false
                }

                onVoiceChunk(ttsResponse.payload)

                if (ttsResponse.isFinal) {
                    isWaitingForNewSentence = true
                    logger.info { "TTS 문장 끝" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "TTS 처리 및 싱크 루프 중 에러" }
            throw e
        } finally {
            subtitleJob.cancel()
        }
    }
}