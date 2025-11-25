package jnu.econovation.isekai.session.dto.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jnu.econovation.isekai.enums.MessageType
import jnu.econovation.isekai.session.dto.internal.ServerReadyDTO
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
)
interface SessionResponseContent