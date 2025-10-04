package jnu.econovation.isekai.persona.dto.internal


data class PersonaRequest(
    val personaName: String,
    val content: String,
    val isPublic: Boolean
)