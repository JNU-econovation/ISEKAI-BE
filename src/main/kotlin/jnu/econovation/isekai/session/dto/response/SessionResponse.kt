package jnu.econovation.isekai.session.dto.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jnu.econovation.isekai.common.exception.enums.ErrorCode
import jnu.econovation.isekai.gemini.constant.enums.GeminiEmotion

sealed interface SessionResponse {
    val content: Any
}

@Suppress("ArrayInDataClass")
data class SessionBinaryResponse(
    override val content: ByteArray
) : SessionResponse

@ConsistentCopyVisibility
data class SessionTextResponse private constructor(
    val messageType: MessageType,
    override val content: SessionTextResponseContent
) : SessionResponse {
    companion object {
        fun fromServerReady(): SessionTextResponse {
            return SessionTextResponse(MessageType.SERVER_READY, ServerReadyDTO())
        }

        fun fromUserSubtitleChunk(text: String): SessionTextResponse = SessionTextResponse(
            MessageType.USER_SUBTITLE_CHUNK,
            SubtitleDTO(text)
        )

        fun fromUserOneSentenceSubtitle(text: String): SessionTextResponse = SessionTextResponse(
            MessageType.USER_ONE_SENTENCE_SUBTITLE,
            SubtitleDTO(text)
        )

        fun fromBotSubtitle(text: String): SessionTextResponse = SessionTextResponse(
            MessageType.BOT_SUBTITLE,
            SubtitleDTO(text)
        )

        fun fromInterrupted(): SessionTextResponse = SessionTextResponse(
            MessageType.INTERRUPTED,
            InterruptedDTO()
        )

        fun fromEmotion(emotion: GeminiEmotion) = SessionTextResponse(
            MessageType.EMOTION,
            EmotionDTO(emotion.text)
        )

        fun fromTurnComplete(user: String, bot: String) = SessionTextResponse(
            MessageType.TURN_COMPLETE,
            TurnCompleteSubtitleDTO(user = user, bot = bot)
        )

        fun fromError(errorCode: ErrorCode): SessionTextResponse {
            return SessionTextResponse(
                MessageType.ERROR,
                SessionTextErrorDTO(errorCode.getCode(), errorCode.message)
            )
        }

        fun fromError(errorCode: ErrorCode, customMessage: String): SessionTextResponse {
            return SessionTextResponse(
                MessageType.ERROR,
                SessionTextErrorDTO(errorCode.getCode(), customMessage)
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
    JsonSubTypes.Type(value = TurnCompleteSubtitleDTO::class, name = "turnComplete"),
    JsonSubTypes.Type(value = InterruptedDTO::class, name = "interrupted"),
    JsonSubTypes.Type(value = SessionTextErrorDTO::class, name = "error"),
    JsonSubTypes.Type(value = EmotionDTO::class, name = "emotion")
)
interface SessionTextResponseContent

enum class MessageType {
    SERVER_READY,
    USER_SUBTITLE_CHUNK,
    USER_ONE_SENTENCE_SUBTITLE,
    BOT_SUBTITLE,
    INTERRUPTED,
    TURN_COMPLETE,
    EMOTION,
    ERROR
}

data class ServerReadyDTO(
    val text: String = "I'M READY"
) : SessionTextResponseContent

data class SessionTextErrorDTO(
    val errorCode: String,
    val message: String
) : SessionTextResponseContent

data class SubtitleDTO(
    val text: String
) : SessionTextResponseContent

data class EmotionDTO(
    val emotion: String
) : SessionTextResponseContent

data class TurnCompleteSubtitleDTO(
    val user: String,
    val bot: String
) : SessionTextResponseContent

data class InterruptedDTO(
    val text: String = "Gemini가 응답 중에 사용자가 끼어듦"
) : SessionTextResponseContent