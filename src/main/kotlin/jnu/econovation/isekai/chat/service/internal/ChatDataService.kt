package jnu.econovation.isekai.chat.service.internal

import jnu.econovation.isekai.chat.model.entity.Chat
import jnu.econovation.isekai.chat.repository.ChatRepository
import jnu.econovation.isekai.member.entity.Member
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
    fun getRecentChats(hostMember: Member, limit: Int): List<Chat> {
        return repository.findRecentChatsByHostMember(hostMember, limit).reversed()
    }

}