package jnu.econovation.isekai.chat.service.internal

import jakarta.persistence.EntityNotFoundException
import jnu.econovation.isekai.chat.model.entity.LongTermMemory
import jnu.econovation.isekai.chat.repository.LongTermMemoryRepository
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.persona.model.entity.Persona
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LongTermMemoryDataService(
    private val repository: LongTermMemoryRepository
) {
    @Transactional
    fun save(longTermMemory: LongTermMemory) {
        repository.save(longTermMemory)
    }

    @Transactional(readOnly = true)
    fun findSimilarMemories(
        persona: Persona,
        hostMember: Member,
        embedding: FloatArray,
        limit: Int
    ): List<LongTermMemory> {
        return repository.findSimilarMemories(persona.id, hostMember.id, embedding, limit)
    }

}