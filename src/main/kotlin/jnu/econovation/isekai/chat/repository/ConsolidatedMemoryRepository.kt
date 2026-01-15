package jnu.econovation.isekai.chat.repository

import jnu.econovation.isekai.chat.model.entity.ConsolidatedMemory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ConsolidatedMemoryRepository : JpaRepository<ConsolidatedMemory, Long> {
    @Query(
        value = """
            SELECT c FROM ConsolidatedMemory c
            WHERE c.character.id = :characterId AND c.hostMember.id = :hostMemberId
            ORDER BY c.createdAt DESC
            LIMIT :limit
        """
    )
    fun findRecentMemories(
        characterId: Long?,
        hostMemberId: Long,
        limit: Int
    ): List<ConsolidatedMemory>


    @Query(
        value = """
            SELECT * FROM consolidated_memory
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
    ): List<ConsolidatedMemory>
}