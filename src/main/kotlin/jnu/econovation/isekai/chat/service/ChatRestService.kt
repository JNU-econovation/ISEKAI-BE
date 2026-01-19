package jnu.econovation.isekai.chat.service

import jnu.econovation.isekai.chat.dto.response.ChatRestResponse
import jnu.econovation.isekai.chat.service.internal.ChatDataService
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
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
        val chatPage = chatDataService.getRecentChatsForPage(memberInfoDTO.id, characterId, pageable)

        val reversedContent = chatPage.content
            .asReversed()
            .map { ChatRestResponse.from(it) }

        return PageImpl(reversedContent, pageable, chatPage.totalElements)
    }
}