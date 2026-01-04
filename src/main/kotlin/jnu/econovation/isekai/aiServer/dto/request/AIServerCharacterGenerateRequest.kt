package jnu.econovation.isekai.aiServer.dto.request

import jnu.econovation.isekai.character.dto.request.CharacterGenerateRequest
import jnu.econovation.isekai.common.s3.dto.internal.PresignDTO

data class AIServerCharacterGenerateRequest(
    val prompt: String,
    val presignUrl: String,
    val fileName: String
) {
    companion object {
        fun from(
            characterGenerateRequest: CharacterGenerateRequest,
            presignDTO: PresignDTO
        ): AIServerCharacterGenerateRequest {
            return AIServerCharacterGenerateRequest(
                prompt = characterGenerateRequest.prompt,
                presignUrl = presignDTO.url,
                fileName = presignDTO.fileName
            )
        }
    }
}