package jnu.econovation.isekai.gemini.dto.client.response

sealed class GeminiLiveFunctionParams {
    data class RequestReply(val userMessage: String) : GeminiLiveFunctionParams()
}