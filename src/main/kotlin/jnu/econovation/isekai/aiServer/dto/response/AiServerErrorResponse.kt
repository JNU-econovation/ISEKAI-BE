package jnu.econovation.isekai.aiServer.dto.response

data class AiServerErrorResponse(
    val status: String,
    val code: String,
    val message: String
)