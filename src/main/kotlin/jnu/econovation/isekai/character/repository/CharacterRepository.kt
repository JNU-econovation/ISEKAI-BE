package jnu.econovation.isekai.character.repository

import jnu.econovation.isekai.character.model.entity.Character
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CharacterRepository : JpaRepository<Character, Long>