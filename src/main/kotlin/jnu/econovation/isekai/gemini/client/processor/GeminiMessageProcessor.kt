package jnu.econovation.isekai.gemini.client.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.types.LiveServerMessage
import jnu.econovation.isekai.gemini.constant.enums.GeminiFunctionSignature
import jnu.econovation.isekai.gemini.dto.client.response.GeminiOutput
import mu.KotlinLogging
import java.util.regex.Pattern
import kotlin.jvm.optionals.getOrNull

class GeminiMessageProcessor(
    private val mapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}
    private val inputSTTOneSentenceBuffer = StringBuffer()

    fun process(sessionId: String, message: LiveServerMessage): List<GeminiOutput> {
        val outputs = mutableListOf<GeminiOutput>()

        logger.debug { "gemini received -> $message" }

        message.serverContent().flatMap { it.outputTranscription() }
            .ifPresent { transcription ->
                logger.info { "output stt -> $transcription" }
            }

        processInputSTT(message, inputSTTOneSentenceBuffer)?.let { outputs.add(it) }
        outputs.addAll(processFunctionCall(sessionId, message))
        processInterrupted(message)?.let { outputs.add(it) }

        return outputs
    }

    private fun processInputSTT(
        message: LiveServerMessage,
        buffer: StringBuffer
    ): GeminiOutput.InputSTT? {
        var result: GeminiOutput.InputSTT? = null
        message.serverContent().flatMap { it.inputTranscription() }
            .ifPresent { transcription ->
                transcription.text().ifPresent { text ->
                    buffer.append(text)
                    result = GeminiOutput.InputSTT(text)
                }
            }
        return result
    }

    private fun processFunctionCall(
        sessionId: String,
        message: LiveServerMessage
    ): List<GeminiOutput.FunctionCall> {
        val functionCalls = mutableListOf<GeminiOutput.FunctionCall>()

        message.toolCall().getOrNull()?.functionCalls()?.ifPresent { calls ->
            calls.map { Triple(it.id()?.orElse(""), it.name()?.get(), it.args()?.get()) }
                .onEach { (id, name, items) ->
                    logger.info { "[Session:$sessionId] 함수 수신됨 - ID: $id, Name: $name, Items: $items" }
                }
                .forEach { (id, name, items) ->
                    val enum = name?.let { GeminiFunctionSignature.fromText(it) }

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
                        GeminiOutput.FunctionCall(
                            id = id ?: "",
                            signature = enum,
                            params = params
                        )
                    )
                }
        }
        return functionCalls
    }

    private fun processInterrupted(message: LiveServerMessage): GeminiOutput.Interrupted? {
        val isInterrupted = message.serverContent().flatMap { it.interrupted() }.orElse(false)
        if (isInterrupted) {
            logger.info { "인터럽트 되었습니다." }
            return GeminiOutput.Interrupted()
        }
        return null
    }
}