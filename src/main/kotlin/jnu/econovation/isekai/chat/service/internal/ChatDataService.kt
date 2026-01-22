package jnu.econovation.isekai.chat.service.internal

import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.chat.model.entity.Chat
import jnu.econovation.isekai.chat.repository.ChatRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChatDataService(
    private val repository: ChatRepository
) {

    @Transactional
    fun save(chat: Chat) {
        repository.save(chat)
    }

    @Transactional(readOnly = true)
    fun getRecentChats(characterDTO: CharacterDTO, hostMemberId: Long, limit: Int): List<Chat> {
        return repository.findRecentChatsByHostMember(
            characterId = characterDTO.id,
            hostMemberId = hostMemberId,
            limit = limit
        ).reversed()
    }

    @Transactional(readOnly = true)
    fun getRecentChatsForPage(
        characterId: Long,
        hostMemberId: Long,
        pageable: Pageable
    ): Page<Chat> {
        val chats =  repository.findRecentChatsByHostMember(
            characterId = characterId,
            hostMemberId = hostMemberId,
            pageable
        )

        val reversedContent = chats.content.asReversed()

        return PageImpl(reversedContent, pageable, chats.totalElements)
    }

}