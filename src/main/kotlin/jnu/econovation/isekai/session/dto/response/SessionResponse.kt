package jnu.econovation.isekai.session.dto.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jnu.econovation.isekai.enums.MessageType
import jnu.econovation.isekai.session.dto.internal.GeminiOutputDTO
import jnu.econovation.isekai.session.dto.internal.ServerReadyDTO

@ConsistentCopyVisibility
data class SessionResponse private constructor(
    val messageType: MessageType,
    val content: SessionResponseContent
) {
    companion object {
        fun fromServerReady(): SessionResponse {
            return SessionResponse(MessageType.SERVER_READY, ServerReadyDTO())
        }

        fun fromGeminiOutput(text: String): SessionResponse {
            return SessionResponse(MessageType.GEMINI_OUTPUT, GeminiOutputDTO(text))
        }
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ServerReadyDTO::class, name = "serverReady"),
    JsonSubTypes.Type(value = GeminiOutputDTO::class, name = "geminiOutput")
)
interface SessionResponseContent