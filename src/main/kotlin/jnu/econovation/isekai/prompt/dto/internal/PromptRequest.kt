package jnu.econovation.isekai.prompt.dto.internal


data class PromptRequest(
    val personaName: String,
    val content: String,
    val isPublic: Boolean
)