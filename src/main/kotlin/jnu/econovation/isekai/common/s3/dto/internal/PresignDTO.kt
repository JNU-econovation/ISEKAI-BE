package jnu.econovation.isekai.common.s3.dto.internal

import java.time.ZonedDateTime

data class PresignDTO(
    val url: String,
    val fileName: String,
    val expirationTime: ZonedDateTime
)