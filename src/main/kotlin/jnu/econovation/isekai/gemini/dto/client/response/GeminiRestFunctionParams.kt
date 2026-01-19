package jnu.econovation.isekai.gemini.dto.client.response

sealed class GeminiRestFunctionParams {
    data class SearchLongTermMemoryRAG(val searchText: String) : GeminiRestFunctionParams()
    data class FinalAnswer(val krTextResponse: String, val emotion: String) : GeminiRestFunctionParams()
}