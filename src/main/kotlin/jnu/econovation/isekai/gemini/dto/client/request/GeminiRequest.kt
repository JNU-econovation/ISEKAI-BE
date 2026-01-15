package jnu.econovation.isekai.gemini.dto.client.request

sealed class GeminiInput {
    data class Audio(val chunk: ByteArray) : GeminiInput()

    data class Context(val shortTermMemory: String, val midTermMemory: String) : GeminiInput()

    data class ToolResponse(
        val id: String?,
        val functionName: String,
        val result: String
    ) : GeminiInput()
}