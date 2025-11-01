package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_MEMBER
import jnu.econovation.isekai.persona.service.PersonaService
import jnu.econovation.isekai.prompt.service.PromptService
import jnu.econovation.isekai.session.constant.SessionConstant.FLOW_BUFFER_SIZE
import jnu.econovation.isekai.session.dto.internal.GeminiResponseDTO
import jnu.econovation.isekai.session.event.GeminiResponseEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service


@Service
class IsekAiSessionService(
    private val liveClient: GeminiLiveClient,
    private val memoryService: ChatMemoryService,
    private val personaService: PersonaService,
    private val promptService: PromptService,
    private val eventPublisher: ApplicationEventPublisher
) {

    private companion object {
        val logger = KotlinLogging.logger {}
    }

    suspend fun processVoiceChunk(
        rtzrReadySignal: CompletableDeferred<Unit>,
        voiceStream: Flow<ByteArray>,
        personaId: Long,
        sessionId: String,
        webSocketSessionScope: CoroutineScope
    ) {
        val persona = personaService.getEntity(personaId)

        val prompt = promptService.getPrompt(persona)

        val sharedVoiceStream = voiceStream
            .buffer(capacity = FLOW_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .shareIn(webSocketSessionScope, SharingStarted.Lazily)

        val voiceFastInput: Flow<GeminiInput.Audio> = sharedVoiceStream
            .map { GeminiInput.Audio(it) }

        val contextSlowInput: Flow<GeminiInput.Context> = memoryService
            .findMemoriesFromVoiceStream(
                rtzrReadySignal,
                sharedVoiceStream,
                persona,
                MASTER_MEMBER,
                webSocketSessionScope
            )

        val mergedStream = merge(voiceFastInput, contextSlowInput)
            .onEach { logger.debug { "gemini input -> $it" } }

        liveClient
            .getLiveResponse(mergedStream, prompt)
            ?.collect {
                logger.info { "gemini response -> $it" }

                val event = GeminiResponseEvent(
                    source = this,
                    sessionId = sessionId,
                    content = GeminiResponseDTO(it, personaId)
                )

                eventPublisher.publishEvent(event)
            }
    }


}