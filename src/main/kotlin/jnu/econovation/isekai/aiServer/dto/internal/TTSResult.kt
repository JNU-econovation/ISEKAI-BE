package jnu.econovation.isekai.aiServer.dto.internal

sealed class TTSResult {
    class StartStreaming() : TTSResult()

    @Suppress("ArrayInDataClass")
    data class Voice(val byteArray: ByteArray) : TTSResult()
}