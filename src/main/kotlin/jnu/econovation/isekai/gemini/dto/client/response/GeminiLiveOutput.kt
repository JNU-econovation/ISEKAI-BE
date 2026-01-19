package jnu.econovation.isekai.gemini.dto.client.response

import jnu.econovation.isekai.gemini.constant.enums.GeminiLiveFunctionSignature

sealed class GeminiLiveOutput {
    data class InputSTT(val text: String) : GeminiLiveOutput()

    data class FunctionCall(
        val id: String,
        val signature: GeminiLiveFunctionSignature,
        val params: GeminiLiveFunctionParams,
        val isMock: Boolean = false
    ) : GeminiLiveOutput()
}

