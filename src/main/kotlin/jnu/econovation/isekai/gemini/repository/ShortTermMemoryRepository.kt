package jnu.econovation.isekai.gemini.repository

import jnu.econovation.isekai.gemini.entity.ShortTermMemory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ShortTermMemoryRepository : JpaRepository<ShortTermMemory, Long>