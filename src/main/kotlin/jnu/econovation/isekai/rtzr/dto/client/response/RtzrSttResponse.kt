package jnu.econovation.isekai.rtzr.dto.client.response

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class RtzrSttResponse(
    val seq: Int,
    val startAt: Int,
    val duration: Int,
    val final: Boolean,
    val alternatives: List<Alternatives>
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Alternatives(
    val text: String,
    val confidence: Float,
    val words: List<Word>?
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Word(
    val text: String,
    val startAt: Int,
    val duration: Int,
    val confidence: Float
)