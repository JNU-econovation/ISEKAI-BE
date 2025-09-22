package jnu.econovation.isekai.gemini.repository

import jnu.econovation.isekai.gemini.entity.LongTermMemory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface LongTermMemoryRepository : JpaRepository<LongTermMemory, Long> {
    @Query(
        value = """
            SELECT * FROM long_term_memory
            ORDER BY embedding <-> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findSimilarMemories(
        embedding: FloatArray,
        limit: Int
    ): List<LongTermMemory>
}