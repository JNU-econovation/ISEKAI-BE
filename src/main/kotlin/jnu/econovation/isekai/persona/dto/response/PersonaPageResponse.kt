package jnu.econovation.isekai.persona.dto.response

import jnu.econovation.isekai.common.util.TimeUtil
import jnu.econovation.isekai.persona.model.entity.Persona

data class PersonaPageResponse(
    val id: Long,
    val author: String,
    val personaName: String,
    val isPublic: Boolean,
    val createdAt: String
) {
    companion object {
        fun from(persona: Persona): PersonaPageResponse {
            return PersonaPageResponse(
                id = persona.id,
                author = persona.author.email.value,
                personaName = persona.name.value,
                isPublic = persona.isPublic,
                createdAt = TimeUtil.formatToSimpleString(persona.createdAt)
            )
        }
    }
}