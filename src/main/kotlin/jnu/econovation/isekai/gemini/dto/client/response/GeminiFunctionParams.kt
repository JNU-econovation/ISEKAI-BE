package jnu.econovation.isekai.gemini.dto.client.response

sealed class GeminiFunctionParams {
    data class ResponseText(val krText: String) : GeminiFunctionParams()
    data class SearchLongTermMemoryRAG(val searchText: String) : GeminiFunctionParams()
    data class Emotion(val emotion: String) : GeminiFunctionParams()
}