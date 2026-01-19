package jnu.econovation.isekai.gemini.constant.enums

import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveFunctionParams
import kotlin.reflect.KClass

enum class GeminiLiveFunctionSignature(
    val paramsType: KClass<out GeminiLiveFunctionParams>
) {
    REQUEST_REPLY(GeminiLiveFunctionParams.RequestReply::class);

    val text: String
        get() = this.name

    companion object {
        fun fromText(text: String): GeminiLiveFunctionSignature? {
            return entries.find { it.text == text }
        }
    }
}
