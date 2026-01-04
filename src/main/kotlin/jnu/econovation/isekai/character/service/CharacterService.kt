package jnu.econovation.isekai.character.service

import jnu.econovation.isekai.aiServer.client.CharacterGenerateClient
import jnu.econovation.isekai.aiServer.dto.request.AIServerCharacterGenerateRequest
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.dto.request.ConfirmCharacterRequest
import jnu.econovation.isekai.character.dto.request.GenerateBackgroundImageRequest
import jnu.econovation.isekai.character.dto.request.GenerateCharacterRequest
import jnu.econovation.isekai.character.dto.response.GenerateBackgroundImageResponse
import jnu.econovation.isekai.character.dto.response.GenerateCharacterResponse
import jnu.econovation.isekai.character.model.entity.Character
import jnu.econovation.isekai.character.model.vo.Persona
import jnu.econovation.isekai.character.repository.CharacterRepository
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.s3.dto.internal.PersistResultDTO
import jnu.econovation.isekai.common.s3.dto.internal.PresignDTO
import jnu.econovation.isekai.common.s3.dto.internal.PreviewDTO
import jnu.econovation.isekai.common.s3.enums.FileName
import jnu.econovation.isekai.common.s3.service.S3Service
import jnu.econovation.isekai.gemini.client.GeminiClient
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.service.MemberService
import org.springframework.stereotype.Service
import java.util.*

@Service
class CharacterService(
    private val client: CharacterGenerateClient,
    private val geminiClient: GeminiClient,
    private val s3Service: S3Service,
    private val memberService: MemberService,
    private val repository: CharacterRepository
) {
    companion object {
        private val ALL_OF_BASE_NAME = listOf(
            FileName.LIVE2D_MODEL_FILE_NAME,
            FileName.BACKGROUND_IMAGE_FILE_NAME
        )
    }

    fun generateCharacter(
        memberInfo: MemberInfoDTO,
        request: GenerateCharacterRequest
    ): GenerateCharacterResponse {
        val presign: PresignDTO = s3Service.generatePresignedPutUrl(
            memberInfo = memberInfo,
            uuid = UUID.fromString(request.uuid),
            fileName = FileName.LIVE2D_MODEL_FILE_NAME,
        )

        val aiServerRequest = AIServerCharacterGenerateRequest.from(
            generateCharacterRequest = request,
            presignDTO = presign
        )

        client.requestGeneration(aiServerRequest)
            .onFailure { cause -> throw InternalServerException(cause = cause) }

        val previewDTO = s3Service.getPreviewUrl(
            memberInfo = memberInfo,
            uuid = UUID.fromString(request.uuid),
            fileName = FileName.LIVE2D_MODEL_FILE_NAME
        )

        return GenerateCharacterResponse.from(previewDTO)
    }

    fun generateBackgroundImage(
        memberInfo: MemberInfoDTO,
        request: GenerateBackgroundImageRequest
    ): GenerateBackgroundImageResponse {
        val image: ByteArray = geminiClient.getImageResponse(request.prompt)

        s3Service.uploadImageImmediately(
            memberInfo = memberInfo,
            uuid = UUID.fromString(request.uuid),
            fileName = FileName.BACKGROUND_IMAGE_FILE_NAME,
            image = image
        )

        val previewDTO: PreviewDTO = s3Service.getPreviewUrl(
            memberInfo = memberInfo,
            uuid = UUID.fromString(request.uuid),
            fileName = FileName.BACKGROUND_IMAGE_FILE_NAME
        )

        return GenerateBackgroundImageResponse.from(previewDTO)
    }

    fun confirmCharacter(
        memberInfo: MemberInfoDTO,
        request: ConfirmCharacterRequest
    ): Long {
        val persistenceUrls: List<PersistResultDTO> = s3Service.copyPreviewToPersisted(
            memberInfo = memberInfo,
            uuid = UUID.fromString(request.uuid),
            allOfFileName = ALL_OF_BASE_NAME
        )

        val author: Member = memberService.getEntity(memberInfo.id)
            ?: throw InternalServerException(cause = IllegalStateException("회원을 찾지 못함 -> $memberInfo"))

        val characterBuilder: Character.CharacterBuilder = Character.builder()
            .author(author)
            .persona(Persona(request.persona))
            .isPublic(true)

        persistenceUrls.forEach { persistResultDTO ->
            when (persistResultDTO.fileName) {
                FileName.LIVE2D_MODEL_FILE_NAME -> {
                    characterBuilder.live2dModelUrl(persistResultDTO.url)
                }

                FileName.BACKGROUND_IMAGE_FILE_NAME -> {
                    characterBuilder.backgroundUrl(persistResultDTO.url)
                }
            }
        }

        return repository.save(characterBuilder.build()).id
    }

    fun getCharacter(id: Long?): CharacterDTO? {
        if (id == null) return null

        val entity = getCharacterEntity(id) ?: return null

        return CharacterDTO.from(entity)
    }

    fun getCharacterEntity(id: Long?): Character? {
        if (id == null) return null

        return repository.findByIdAndIsPublic(id, true)
    }
}