package jnu.econovation.isekai.character.service.internal

import jnu.econovation.isekai.character.model.entity.Character
import jnu.econovation.isekai.character.repository.CharacterRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CharacterDataService(
    private val repository: CharacterRepository
) {

    @Transactional
    fun save(character: Character) : Long {
        return repository.save(character).id
    }

    @Transactional(readOnly = true)
    fun findByIdAndIsPublic(id: Long): Character? {
        return repository.findByIdAndIsPublic(id, true)
    }

    @Transactional(readOnly = true)
    fun findAllByIsPublic(pageable: Pageable) : Page<Character> {
        return repository.findAllByIsPublic(pageable, true)
    }

    @Transactional
    fun deleteById(id: Long) {
        return repository.deleteById(id)
    }

}