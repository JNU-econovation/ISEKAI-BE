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
            WHERE character_id = :characterId AND host_member_id = :hostMemberId
            ORDER BY embedding <-> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findSimilarMemories(
        characterId: Long?,
        hostMemberId: Long,
        embedding: FloatArray,
        limit: Int
    ): List<LongTermMemory>
}