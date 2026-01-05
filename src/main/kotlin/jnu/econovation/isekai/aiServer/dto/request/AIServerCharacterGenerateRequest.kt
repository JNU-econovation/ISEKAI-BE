package jnu.econovation.isekai.aiServer.dto.request

import jnu.econovation.isekai.character.dto.request.GenerateCharacterRequest
import jnu.econovation.isekai.common.s3.dto.internal.PresignDTO

data class AIServerCharacterGenerateRequest(
    val prompt: String,
    val live2dModel: Live2dModelDTO,
    val nukki: NukkiDTO
) {


    companion object {
        fun from(
            generateCharacterRequest: GenerateCharacterRequest,
            live2dPresignDTO: PresignDTO,
            nukkiPresignDTO: PresignDTO
        ): AIServerCharacterGenerateRequest {
            return AIServerCharacterGenerateRequest(
                prompt = generateCharacterRequest.prompt,
                live2dModel = Live2dModelDTO.from(live2dPresignDTO),
                nukki = NukkiDTO.from(nukkiPresignDTO)
            )
        }
    }
}

data class Live2dModelDTO(
    val presignUrl: String,
    val fileName: String
) {
    companion object {
        fun from(presignDTO: PresignDTO): Live2dModelDTO {
            return Live2dModelDTO(
                presignUrl = presignDTO.url,
                fileName = presignDTO.fileName.toString()
            )
        }
    }
}

data class NukkiDTO(
    val presignUrl: String,
    val fileName: String
) {
    companion object {
        fun from(presignDTO: PresignDTO) : NukkiDTO {
            return NukkiDTO(
                presignUrl = presignDTO.url,
                fileName = presignDTO.fileName.toString()
            )
        }
    }
}