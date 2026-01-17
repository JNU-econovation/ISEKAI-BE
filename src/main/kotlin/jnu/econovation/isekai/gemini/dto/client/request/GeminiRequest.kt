package jnu.econovation.isekai.gemini.dto.client.request

sealed class GeminiInput {
    @Suppress("ArrayInDataClass")
    data class Audio(val chunk: ByteArray) : GeminiInput()

    data class Text(val value: String) : GeminiInput()

    data class Context(val shortTermMemory: String, val midTermMemory: String) : GeminiInput()

    data class ToolResponse(
        val id: String?,
        val functionName: String,
        val result: String
    ) : GeminiInput()
}