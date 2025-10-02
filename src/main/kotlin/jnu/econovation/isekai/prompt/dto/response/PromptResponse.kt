package jnu.econovation.isekai.prompt.dto.response

data class PromptResponse(
    val id: Long,
    val author: String,
    val personaName: String,
    val content: String,
    val isPublic: Boolean,
    val createdAt: String
)