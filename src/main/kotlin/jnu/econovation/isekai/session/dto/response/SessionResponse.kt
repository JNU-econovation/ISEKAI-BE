package jnu.econovation.isekai.session.dto.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jnu.econovation.isekai.common.exception.enums.ErrorCode
import jnu.econovation.isekai.enums.MessageType
import jnu.econovation.isekai.session.dto.internal.ServerReadyDTO
import jnu.econovation.isekai.session.dto.internal.SessionErrorDTO
import jnu.econovation.isekai.session.dto.internal.SubtitleDTO

@ConsistentCopyVisibility
data class SessionResponse private constructor(
    val messageType: MessageType,
    val content: SessionResponseContent
) {
    companion object {
        fun fromServerReady(): SessionResponse {
            return SessionResponse(MessageType.SERVER_READY, ServerReadyDTO())
        }

        fun fromSubtitle(text: String): SessionResponse = SessionResponse(
            MessageType.SUBTITLE,
            SubtitleDTO(text)
        )

        fun fromError(errorCode: ErrorCode): SessionResponse {
            return SessionResponse(
                MessageType.ERROR,
                SessionErrorDTO(errorCode.getCode(), errorCode.message)
            )
        }

        fun fromError(errorCode: ErrorCode, customMessage: String): SessionResponse {
            return SessionResponse(
                MessageType.ERROR,
                SessionErrorDTO(errorCode.getCode(), customMessage)
            )
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
    JsonSubTypes.Type(value = SubtitleDTO::class, name = "subtitle"),
    JsonSubTypes.Type(value = SessionErrorDTO::class, name = "error")
)
interface SessionResponseContent