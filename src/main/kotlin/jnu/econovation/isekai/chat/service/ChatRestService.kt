package jnu.econovation.isekai.chat.service

import jnu.econovation.isekai.chat.dto.response.ChatRestResponse
import jnu.econovation.isekai.chat.service.internal.ChatDataService
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class ChatRestService(
    private val chatDataService: ChatDataService
) {
    fun getChats(
        memberInfoDTO: MemberInfoDTO,
        characterId: Long,
        pageable: Pageable
    ): Page<ChatRestResponse> {
        return chatDataService.getRecentChatsForPage(
            characterId = characterId,
            hostMemberId = memberInfoDTO.id,
            pageable = pageable
        ).map { ChatRestResponse.from(it) }
    }
}