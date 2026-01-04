package jnu.econovation.isekai.aiServer.dto.response

@Suppress("ArrayInDataClass")
data class TTSResponse(
    val isFinal: Boolean,
    val payload: ByteArray
)