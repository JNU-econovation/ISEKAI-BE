package jnu.econovation.isekai.chat.dto.internal

import java.time.ZonedDateTime

data class ShortTermMemoryDTO(
    val startTime: ZonedDateTime,
    val endTime: ZonedDateTime,
    val content: String
)