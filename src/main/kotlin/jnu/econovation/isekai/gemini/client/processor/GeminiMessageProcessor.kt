package jnu.econovation.isekai.gemini.client.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.types.LiveServerMessage
import jnu.econovation.isekai.gemini.constant.enums.GeminiLiveFunctionSignature
import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveOutput
import mu.KotlinLogging
import kotlin.jvm.optionals.getOrNull

class GeminiMessageProcessor(
    private val mapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    fun process(sessionId: String, message: LiveServerMessage): List<GeminiLiveOutput> {
        val outputs = mutableListOf<GeminiLiveOutput>()

//        logger.info { "gemini received -> $message" }

        processInputSTT(message)?.let { outputs.add(it) }
        outputs.addAll(processFunctionCall(sessionId, message))

        return outputs
    }

    private fun processInputSTT(message: LiveServerMessage): GeminiLiveOutput.InputSTT? {
        var result: GeminiLiveOutput.InputSTT? = null
        message.serverContent().flatMap { it.inputTranscription() }
            .ifPresent { transcription ->
                transcription.text().ifPresent { text -> result = GeminiLiveOutput.InputSTT(text) }
            }
        return result
    }

    private fun processFunctionCall(
        sessionId: String,
        message: LiveServerMessage
    ): List<GeminiLiveOutput.FunctionCall> {
        val functionCalls = mutableListOf<GeminiLiveOutput.FunctionCall>()

        message.toolCall().getOrNull()?.functionCalls()?.ifPresent { calls ->
            calls.map { Triple(it.id()?.orElse(""), it.name()?.get(), it.args()?.get()) }
                .onEach { (id, name, items) ->
                    logger.info { "[Session:$sessionId] 함수 수신됨 - ID: $id, Name: $name, Items: $items" }
                }
                .forEach { (id, name, items) ->
                    val enum = name?.let { GeminiLiveFunctionSignature.fromText(it) }

                    if (enum == null) {
                        logger.error { "function name error -> $name" }
                        return@forEach
                    }

                    val params = runCatching {
                        mapper.convertValue(items, enum.paramsType.java)
                    }.getOrElse {
                        logger.error(it) { "function params convert error" }
                        return@forEach
                    }

                    functionCalls.add(
                        GeminiLiveOutput.FunctionCall(
                            id = id ?: "",
                            signature = enum,
                            params = params
                        )
                    )
                }
        }
        return functionCalls
    }
}