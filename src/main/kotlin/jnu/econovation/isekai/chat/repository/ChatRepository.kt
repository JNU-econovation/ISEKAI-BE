package jnu.econovation.isekai.chat.repository

import jnu.econovation.isekai.chat.model.entity.Chat
import jnu.econovation.isekai.persona.model.entity.Persona
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ChatRepository : JpaRepository<Chat, Long> {
    @Query("SELECT c FROM Chat c WHERE c.persona = :persona AND c.hostMember.id = :hostMemberId ORDER BY c.createdAt DESC LIMIT :limit")
    fun findRecentChatsByHostMember(persona: Persona, hostMemberId: Long, limit: Int): List<Chat>
}