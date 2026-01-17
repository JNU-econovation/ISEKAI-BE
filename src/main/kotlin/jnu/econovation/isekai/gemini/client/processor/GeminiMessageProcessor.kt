package jnu.econovation.isekai.gemini.client.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.types.LiveServerMessage
import jnu.econovation.isekai.common.extension.clear
import jnu.econovation.isekai.gemini.constant.enums.GeminiFunctionSignature
import jnu.econovation.isekai.gemini.dto.client.response.GeminiOutput
import mu.KotlinLogging
import kotlin.jvm.optionals.getOrNull

class GeminiMessageProcessor(
    private val mapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    private val inputSTTOneSentenceBuffer = StringBuffer()
    private val inputSTTSentencesBuffer = StringBuffer()
    private val outputSTTBuffer = StringBuffer()

    fun onUserInputText(text: String) {
        logger.debug { "User Text Input Buffer에 추가: $text" }
        if (inputSTTOneSentenceBuffer.isNotEmpty()) {
            inputSTTOneSentenceBuffer.append(" ")
        }
        inputSTTOneSentenceBuffer.append(text)
    }

    fun process(sessionId: String, message: LiveServerMessage): List<GeminiOutput> {
        val outputs = mutableListOf<GeminiOutput>()

        logger.debug { "gemini received -> $message" }

        processInputSTT(message, inputSTTOneSentenceBuffer)?.let { outputs.add(it) }
        processOutputSTT(message, outputSTTBuffer)?.let { outputs.add(it) }
        outputs.addAll(processOutputVoiceStream(message))
        outputs.addAll(processFunctionCall(sessionId, message))
        processInterrupted(message)?.let { outputs.add(it) }
        processTurnComplete(message)?.let { outputs.add(it) }

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

    private fun processOutputSTT(
        message: LiveServerMessage,
        buffer: StringBuffer
    ): GeminiOutput.OutputSTT? {
        var result: GeminiOutput.OutputSTT? = null
        message.serverContent().flatMap { it.outputTranscription() }
            .ifPresent { transcription ->
                transcription.text().ifPresent { text ->
                    buffer.append(text)
                    result = GeminiOutput.OutputSTT(text)
                }
            }
        return result
    }

    private fun processOutputVoiceStream(message: LiveServerMessage): List<GeminiOutput.VoiceStream> {
        val voiceOutputs = mutableListOf<GeminiOutput.VoiceStream>()
        message.serverContent().flatMap { it.modelTurn() }.flatMap { it.parts() }
            .ifPresent { parts ->
                parts.forEach { part ->
                    part.inlineData()
                        .map { blob -> blob.data()?.get() }
                        .ifPresent { data ->
                            voiceOutputs.add(GeminiOutput.VoiceStream(data))
                        }
                }
            }
        return voiceOutputs
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

    private fun processTurnComplete(message: LiveServerMessage): GeminiOutput? {
        val isTurnComplete = message.serverContent().flatMap { it.turnComplete() }.orElse(false)

        if (isTurnComplete) {
            return when {
                outputSTTBuffer.isEmpty() -> {
                    val text = inputSTTOneSentenceBuffer.toString()
                    val output = GeminiOutput.InputOneSentenceSTT(text)

                    inputSTTSentencesBuffer.append(text)
                    inputSTTOneSentenceBuffer.clear()

                    logger.info { "사용자 입력 1문장 turn complete. Input: [${output.text}], Output: [${outputSTTBuffer}]" }
                    output
                }

                outputSTTBuffer.isNotEmpty() -> {
                    val finalInput = if (inputSTTSentencesBuffer.isNotEmpty()) {
                        inputSTTSentencesBuffer.toString()
                    } else {
                        inputSTTOneSentenceBuffer.toString()
                    }.trim()

                    val finalOutput = outputSTTBuffer.toString().trim()

                    inputSTTSentencesBuffer.clear()
                    inputSTTOneSentenceBuffer.clear()
                    outputSTTBuffer.clear()

                    val turnComplete = GeminiOutput.TurnComplete(
                        inputSTT = finalInput,
                        outputSTT = finalOutput
                    )

                    logger.info { "gemini 응답 turn complete. Input: [${turnComplete.inputSTT}], Output: [${turnComplete.outputSTT}]" }
                    turnComplete
                }

                else -> null
            }
        }
        return null
    }
}