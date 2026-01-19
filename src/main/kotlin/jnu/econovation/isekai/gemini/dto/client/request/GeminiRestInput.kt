package jnu.econovation.isekai.gemini.dto.client.request

sealed class GeminiRestInput {
    data class Context(val shortTermMemory: String?, val midTermMemory: String?) : GeminiRestInput()
    data class ToolResponse(
        val id: String?,
        val functionName: String,
        val result: String
    ) : GeminiRestInput()
}