package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_MEMBER
import jnu.econovation.isekai.persona.service.PersonaService
import jnu.econovation.isekai.prompt.service.PromptService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class IsekAiSessionService(
    private val liveClient: GeminiLiveClient,
    private val memoryService: ChatMemoryService,
    private val personaService: PersonaService,
    private val promptService: PromptService
) {
    private val scopeForContext = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scopeForSave = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun processVoiceChunk(
        voiceStream: Flow<ByteArray>,
        personaId: Long,
        scope: CoroutineScope
    ) {
        val persona = personaService.getEntity(personaId)
        
        val prompt = promptService.getPrompt(persona)

        val sharedVoiceStream = voiceStream.shareIn(scopeForContext, SharingStarted.Lazily)

        val voiceFastInput: Flow<GeminiInput.Audio> = sharedVoiceStream
            .map { GeminiInput.Audio(it) }

        val contextSlowInput: Flow<GeminiInput.Context> = memoryService
            .findMemoriesFromVoiceStream(sharedVoiceStream, persona, MASTER_MEMBER, scope)

        val mergedStream = merge(voiceFastInput, contextSlowInput)
            .onEach { logger.debug { "gemini input -> $it" } }

        liveClient
            .getLiveResponse(mergedStream, prompt)
            ?.collect {
                logger.info { "gemini response -> $it" }
                scopeForSave.launch { saveChat(personaId, it.inputSTT, it.output) }
            }
    }

    private suspend fun saveChat(personaId: Long, input: String, output: String) {
        try {
            //todo: 추후 MASTER MEMBER -> 사용자화
            memoryService.save(personaId, ChatDTO(input, output))
        } catch (e: Exception) {
            logger.error(e) { "채팅 저장 중 예외 발생" }
        }
    }
}