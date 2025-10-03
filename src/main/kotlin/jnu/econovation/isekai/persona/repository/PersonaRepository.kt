package jnu.econovation.isekai.persona.repository

import jnu.econovation.isekai.persona.model.entity.Persona
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PersonaRepository : JpaRepository<Persona, Long> {
    @Query("SELECT COUNT(p) FROM Persona p")
    fun countPrompts()
}