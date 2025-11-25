package jnu.econovation.isekai.gemini.dto.client.response

data class GeminiLiveResponse(
    val inputSTT: String,
    val output: GeminiLiveTextResponse
)

data class GeminiLiveTextResponseChunk(
    val krText: String,
    val jpText: String
)

data class GeminiLiveTextResponse(
    val krText: String,
    val jpText: String
)