package jnu.econovation.isekai.session.dto.request

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

sealed interface SessionRequest {
    val content: Any
}

@Suppress("ArrayInDataClass")
data class SessionBinaryRequest(
    override val content: ByteArray
) : SessionRequest

@ConsistentCopyVisibility
data class SessionTextRequest private constructor(
    val messageType: MessageType,
    override val content: SessionTextRequestContent
) : SessionRequest {
    companion object {
        fun from(text: String): SessionTextRequest {
            return SessionTextRequest(
                messageType = MessageType.TEXT_MESSAGE,
                content = TextMessageDTO(text)
            )
        }
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes(JsonSubTypes.Type(value = TextMessageDTO::class, name = "textMessage"))
interface SessionTextRequestContent {
    val text: String
}

enum class MessageType {
    TEXT_MESSAGE
}

data class TextMessageDTO(
    override val text: String
) : SessionTextRequestContent