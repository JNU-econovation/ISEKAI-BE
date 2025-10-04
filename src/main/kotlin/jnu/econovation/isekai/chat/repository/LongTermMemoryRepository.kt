package jnu.econovation.isekai.chat.repository

import jnu.econovation.isekai.chat.model.entity.LongTermMemory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface LongTermMemoryRepository : JpaRepository<LongTermMemory, Long> {
    @Query(
        value = """
            SELECT * FROM long_term_memory
            WHERE persona_id = :personaId AND host_member_id = :hostMemberId
            ORDER BY embedding <-> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findSimilarMemories(
        personaId: Long,
        hostMemberId: Long,
        embedding: FloatArray,
        limit: Int
    ): List<LongTermMemory>
}