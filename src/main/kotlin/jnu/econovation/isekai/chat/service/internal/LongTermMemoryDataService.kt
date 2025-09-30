package jnu.econovation.isekai.chat.service.internal

import jnu.econovation.isekai.chat.model.entity.LongTermMemory
import jnu.econovation.isekai.chat.repository.LongTermMemoryRepository
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
    fun findSimilarMemories(embedding: FloatArray, limit: Int) =
        repository.findSimilarMemories(embedding, limit)

}