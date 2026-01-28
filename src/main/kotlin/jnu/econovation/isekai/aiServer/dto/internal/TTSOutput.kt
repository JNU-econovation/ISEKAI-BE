package jnu.econovation.isekai.aiServer.dto.internal

sealed class TTSOutput {
    class StartStreaming() : TTSOutput()

    @Suppress("ArrayInDataClass")
    data class Voice(val byteArray: ByteArray) : TTSOutput()
}