package jnu.econovation.isekai.persona.dto.response

data class PersonaResponse(
    val id: Long,
    val author: String,
    val personaName: String,
    val content: String,
    val isPublic: Boolean,
    val createdAt: String
)