package jnu.econovation.isekai.gemini.dto.client.response

sealed interface GeminiLiveResponse

data class GeminiLiveTurnCompleteResponse(
    val inputSTT: String,
    val krText: String,
    val jpText: String
) : GeminiLiveResponse

data class GeminiLiveTextResponse(
    val krText: String,
    val jpText: String
) : GeminiLiveResponse