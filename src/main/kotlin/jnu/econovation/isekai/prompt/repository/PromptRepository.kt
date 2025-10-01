package jnu.econovation.isekai.prompt.repository

import jnu.econovation.isekai.prompt.model.entity.Prompt
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PromptRepository : JpaRepository<Prompt, Long> {
    @Query("SELECT COUNT(p) FROM Prompt p")
    fun countPrompts()
}