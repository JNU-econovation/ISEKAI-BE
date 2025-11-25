package jnu.econovation.isekai.aiServer.dto.response

@Suppress("ArrayInDataClass")
data class AiServerTTSResponse(
    val isFinal: Boolean,
    val payload: ByteArray
)