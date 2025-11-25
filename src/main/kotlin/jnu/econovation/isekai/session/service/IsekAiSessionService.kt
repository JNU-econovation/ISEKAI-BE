package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.aiServer.dto.request.AiServerTTSRequest
import jnu.econovation.isekai.aiServer.service.AiServerTTSService
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveResponse
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveTextResponse
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveTurnCompleteResponse
import jnu.econovation.isekai.persona.model.entity.Persona
import jnu.econovation.isekai.persona.service.PersonaService
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
    private val personaService: PersonaService,
    private val promptService: PromptService,
    private val aiServerTTSService: AiServerTTSService,
) {
    private companion object {
        val logger = KotlinLogging.logger {}
    }

    suspend fun processVoiceChunk(
        rtzrReadySignal: CompletableDeferred<Unit>,
        aiServerReadySignal: CompletableDeferred<Unit>,
        voiceStream: Flow<ByteArray>,
        personaId: Long,
        onVoiceChunk: suspend (ByteArray) -> Unit,
        onSubtitle: suspend (String) -> Unit
    ) = supervisorScope {
        val persona = personaService.getEntity(personaId)
        val prompt = promptService.getPrompt(persona)

        val geminiInputFlow = createGeminiInputStream(
            voiceStream = voiceStream,
            rtzrReadySignal = rtzrReadySignal,
            persona = persona
        )

        val geminiRawResponse = liveClient.getLiveResponse(geminiInputFlow, prompt)

        val turnCompleteJob = launch {
            handleTurnCompletion(geminiRawResponse, personaId)
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
        persona: Persona
    ): Flow<GeminiInput> {
        val sharedVoiceStream = voiceStream
            .buffer(capacity = FLOW_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .shareIn(this, SharingStarted.Lazily)

        val voiceFastInput = sharedVoiceStream.map { GeminiInput.Audio(it) }

        val contextSlowInput = memoryService
            .findMemoriesFromVoiceStream(
                rtzrReadySignal = rtzrReadySignal,
                voiceChunk = sharedVoiceStream,
                persona = persona,
                hostMemberId = 1L,
            )
            .filter { it.shortTermMemory.isNotEmpty() }
            .onEach { logger.info { "Gemini context updated: $it" } }

        return merge(voiceFastInput, contextSlowInput)
    }

    private suspend fun handleTurnCompletion(
        geminiRawResponse: Flow<GeminiLiveResponse>,
        personaId: Long
    ) {
        geminiRawResponse
            .filterIsInstance<GeminiLiveTurnCompleteResponse>()
            .collect { response ->
                logger.info { "Turn complete. Saving memory." }
                memoryService.save(
                    personaId = personaId,
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
            .map { AiServerTTSRequest(it.jpText) }

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