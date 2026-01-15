package jnu.econovation.isekai.chat.service.internal

import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.chat.model.entity.ConsolidatedMemory
import jnu.econovation.isekai.chat.repository.ConsolidatedMemoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConsolidatedMemoryDataService(
    private val repository: ConsolidatedMemoryRepository
) {
    @Transactional
    fun save(consolidatedMemory: ConsolidatedMemory) {
        repository.save(consolidatedMemory)
    }

    @Transactional(readOnly = true)
    fun findRecentMemories(
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        limit: Int
    ): List<ConsolidatedMemory> {
        return repository.findRecentMemories(characterDTO.id, hostMemberId, limit)
    }

    @Transactional(readOnly = true)
    fun findSimilarMemories(
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        embedding: FloatArray,
        limit: Int
    ): List<ConsolidatedMemory> {
        return repository.findSimilarMemories(characterDTO.id, hostMemberId, embedding, limit)
    }

}