package jnu.econovation.isekai.common.s3.dto.internal

import java.time.ZonedDateTime

data class PreviewDTO(
    val url: String,
    val uuid: String,
    val expirationTime: ZonedDateTime
)