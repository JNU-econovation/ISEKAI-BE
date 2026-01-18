package jnu.econovation.isekai.gemini.dto.client.response

import jnu.econovation.isekai.gemini.constant.enums.GeminiFunctionSignature

sealed class GeminiOutput {
    data class InputSTT(val text: String) : GeminiOutput()

    data class InputOneSentenceSTT(val text: String) : GeminiOutput()

    data class OutputSTT(val text: String) : GeminiOutput()

    data class OutputOneSentenceSTT(val text: String) : GeminiOutput()

    data class Interrupted(val text: String = "Gemini가 응답 중에 사용자가 끼어듦") : GeminiOutput()

    data class TurnComplete(val inputSTT: String, val outputSTT: String) : GeminiOutput()

    data class FunctionCall(
        val id: String,
        val signature: GeminiFunctionSignature,
        val params: GeminiFunctionParams,
        val isMock: Boolean = false
    ) : GeminiOutput()

    @Suppress("ArrayInDataClass")
    data class VoiceStream(val chunk: ByteArray) : GeminiOutput()
}

