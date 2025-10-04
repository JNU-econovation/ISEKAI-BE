package jnu.econovation.isekai.gemini.dto.client.request

sealed class GeminiInput {
    data class Audio(val chunk: ByteArray) : GeminiInput()
    data class Context(val shortTermMemory: String, val longTermMemory: String) : GeminiInput()
}