package jnu.econovation.isekai.gemini.service.internal

import jnu.econovation.isekai.gemini.repository.LongTermMemoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LongTermMemoryDataService(
    private val repository: LongTermMemoryRepository
) {

    @Transactional(readOnly = true)
    fun findSimilarMemories(embedding: FloatArray, limit: Int) =
        repository.findSimilarMemories(embedding, limit)

}