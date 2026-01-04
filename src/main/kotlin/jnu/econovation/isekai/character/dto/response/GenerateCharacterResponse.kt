package jnu.econovation.isekai.character.dto.response

import jnu.econovation.isekai.common.s3.dto.internal.PreviewDTO
import java.time.ZonedDateTime

data class GenerateCharacterResponse(
    val previewUrl: String,
    val uuid: String,
    val expirationTime: ZonedDateTime
) {
    companion object {
        fun from(previewDTO: PreviewDTO): GenerateCharacterResponse {
            return GenerateCharacterResponse(
                previewUrl = previewDTO.url,
                uuid = previewDTO.uuid,
                expirationTime = previewDTO.expirationTime
            )
        }
    }
}