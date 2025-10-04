package jnu.econovation.isekai.rtzr.dto.client.response

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class RtzrAuthResponse(
    val accessToken: String,
    val expiresAt: Long
)