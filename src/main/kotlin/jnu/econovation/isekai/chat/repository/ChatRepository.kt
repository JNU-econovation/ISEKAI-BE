package jnu.econovation.isekai.chat.repository

import jnu.econovation.isekai.chat.model.entity.Chat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ChatRepository : JpaRepository<Chat, Long> {
    @Query("SELECT c FROM Chat c WHERE c.character.id = :characterId AND c.hostMember.id = :hostMemberId ORDER BY c.createdAt DESC LIMIT :limit")
    fun findRecentChatsByHostMember(characterId: Long?, hostMemberId: Long, limit: Int): List<Chat>
}