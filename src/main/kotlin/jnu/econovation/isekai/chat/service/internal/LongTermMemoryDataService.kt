package jnu.econovation.isekai.chat.service.internal

import jnu.econovation.isekai.chat.model.entity.LongTermMemory
import jnu.econovation.isekai.chat.repository.LongTermMemoryRepository
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
        hostMemberId: Long,
        embedding: FloatArray,
        limit: Int
    ): List<LongTermMemory> {
        return repository.findSimilarMemories(persona.id, hostMemberId, embedding, limit)
    }

}