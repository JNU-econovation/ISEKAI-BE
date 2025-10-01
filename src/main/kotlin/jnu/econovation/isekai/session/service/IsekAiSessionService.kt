package jnu.econovation.isekai.session.service

import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.service.ChatMemoryService
import jnu.econovation.isekai.gemini.client.GeminiLiveClient
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_MEMBER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class IsekAiSessionService(
    private val liveClient: GeminiLiveClient,
    private val chatMemoryService: ChatMemoryService
) {
    private val independentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun processVoiceChunk(voiceStream: Flow<ByteArray>) {
        liveClient.getLiveResponse(voiceStream)
            ?.collect {
                logger.info { "gemini response -> $it" }
                independentScope.launch { saveChat(it.inputSTT, it.output) }

            }
    }

    private suspend fun saveChat(input: String, output: String) {
        try {
            //todo: 추후 MASTER MEMBER -> 사용자화
            chatMemoryService.save(MASTER_MEMBER, ChatDTO(input, output))
        } catch (e: Exception) {
            logger.error(e) { "채팅 저장 중 예외 발생" }
        }
    }
}