package jnu.econovation.isekai.aiServer.dto.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "status",
    visible = true,
    defaultImpl = TTSTextResponse.GeneralResponse::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = TTSTextResponse.ErrorResponse::class, name = "error")
)
sealed class TTSTextResponse {
    abstract val status: String

    data class ErrorResponse(
        override val status: String,
        val code: String,
        val message: String
    ) : TTSTextResponse()

    data class GeneralResponse(
        override val status: String
    ) : TTSTextResponse()
}
