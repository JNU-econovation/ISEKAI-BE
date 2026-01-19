package jnu.econovation.isekai.gemini.dto.client.request

sealed class GeminiLiveInput {
    @Suppress("ArrayInDataClass")
    data class Audio(val chunk: ByteArray) : GeminiLiveInput()

    data class Text(val value: String) : GeminiLiveInput()

    data class Context(val shortTermMemory: String, val midTermMemory: String) : GeminiLiveInput()

    data class ToolResponse(
        val id: String?,
        val functionName: String,
        val result: String
    ) : GeminiLiveInput()
}