package jnu.econovation.isekai.persona.dto.internal

import jnu.econovation.isekai.common.util.TimeUtil
import jnu.econovation.isekai.persona.dto.response.PersonaResponse
import jnu.econovation.isekai.persona.model.entity.Persona

data class PersonaDTO(
    val id: Long,
    val author: String,
    val personaName: String,
    val content: String,
    val isPublic: Boolean,
    val createdAt: String
) {
    companion object {
        fun from(persona: Persona): PersonaDTO {
            return PersonaDTO(
                id = persona.id,
                author = persona.author.email.value,
                personaName = persona.name.value,
                content = persona.content.value,
                isPublic = persona.isPublic,
                createdAt = TimeUtil.formatToSimpleString(persona.createdAt)
            )
        }
    }

    fun toResponseDTO(): PersonaResponse {
        return PersonaResponse(
            id = id,
            author = author,
            personaName = personaName,
            content = content,
            isPublic = isPublic,
            createdAt = createdAt
        )
    }
}