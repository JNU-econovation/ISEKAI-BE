package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.aiServer.dto.request.AiServerTTSRequest
import jnu.econovation.isekai.aiServer.dto.response.AiServerTTSResponse
import jnu.econovation.isekai.aiServer.service.AiServerTTSService
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveResponse
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
        val currentScope: CoroutineScope = this
        val persona = personaService.getEntity(personaId)

        val prompt = promptService.getPrompt(persona)

        val sharedVoiceStream = voiceStream
            .buffer(capacity = FLOW_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .shareIn(currentScope, SharingStarted.Lazily)

        val voiceFastInput: Flow<GeminiInput.Audio> = sharedVoiceStream
            .map { GeminiInput.Audio(it) }

        val contextSlowInput: Flow<GeminiInput.Context> = memoryService
            .findMemoriesFromVoiceStream(
                rtzrReadySignal = rtzrReadySignal,
                voiceChunk = sharedVoiceStream,
                persona = persona,
                hostMemberId = 1L,
            )
            .filter { it.shortTermMemory.isNotEmpty() }
            .onEach { logger.info { "gemini slow input -> $it" } }

        val mergedStream = merge(voiceFastInput, contextSlowInput)
            .onEach { logger.debug { "gemini input -> $it" } }

        val geminiLiveResponse: Flow<GeminiLiveResponse> = liveClient
            .getLiveResponse(mergedStream, prompt)
            .onEach { logger.info { "gemini response -> $it" } }
            .onEach {
                launch {
                    memoryService.save(personaId, ChatDTO(it.inputSTT, it.output.krText))
                }
            }

        val sharedGeminiResponse: SharedFlow<GeminiLiveResponse> = geminiLiveResponse.shareIn(
            scope = this,
            started = SharingStarted.Eagerly
        )

        val subtitleQueue = Channel<String>(256)

        launch {
            sharedGeminiResponse.collect {
                subtitleQueue.send(it.output.krText)
            }
        }

        val ttsRequestStream: Flow<AiServerTTSRequest> = sharedGeminiResponse
            .map { AiServerTTSRequest(it.output.jpText) }

        val ttsAudioStream: Flow<AiServerTTSResponse> = aiServerTTSService
            .tts(ttsRequestStream, aiServerReadySignal)

        var isNewSentence = true

        ttsAudioStream.collect {
            if (isNewSentence) {
                val subtitle = subtitleQueue.receive()
                onSubtitle(subtitle)
                isNewSentence = false
            }

            if (it.isFinal) isNewSentence = true

            onVoiceChunk(it.payload)
        }

    }


}