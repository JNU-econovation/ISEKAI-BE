package jnu.econovation.isekai.gemini.dto.client.response

import jnu.econovation.isekai.gemini.constant.enums.GeminiEmotion
import jnu.econovation.isekai.gemini.constant.enums.GeminiRestFunctionSignature

data class GeminiRestFunctionCall(
    val id: String?,
    val signature: GeminiRestFunctionSignature,
    val params: GeminiRestFunctionParams
)

data class GeminiRestResult(
    val krTextResponse: String,
    val emotion: GeminiEmotion
) {
    companion object {
        fun fromFunctionCall(geminiRestFunctionCall: GeminiRestFunctionCall): GeminiRestResult? {
            val params = geminiRestFunctionCall.params as? GeminiRestFunctionParams.FinalAnswer ?: return null

            return GeminiRestResult(
                krTextResponse = params.krTextResponse,
                emotion = GeminiEmotion.from(params.emotion)
            )
        }
    }
}