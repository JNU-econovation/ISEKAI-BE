package jnu.econovation.isekai.character.service

import jnu.econovation.isekai.aiServer.client.CharacterGenerateClient
import jnu.econovation.isekai.aiServer.dto.request.AIServerCharacterGenerateRequest
import jnu.econovation.isekai.character.dto.request.CharacterConfirmRequest
import jnu.econovation.isekai.character.dto.request.CharacterGenerateRequest
import jnu.econovation.isekai.character.dto.response.GenerateCharacterResponse
import jnu.econovation.isekai.character.model.entity.Character
import jnu.econovation.isekai.character.repository.CharacterRepository
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.s3.dto.internal.PresignDTO
import jnu.econovation.isekai.common.s3.dto.internal.PreviewDTO
import jnu.econovation.isekai.common.s3.service.S3Service
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.service.MemberService
import org.springframework.stereotype.Service
import java.util.*

@Service
class CharacterService(
    private val client: CharacterGenerateClient,
    private val s3Service: S3Service,
    private val memberService: MemberService,
    private val repository: CharacterRepository
) {
    fun generateCharacter(
        memberInfo: MemberInfoDTO,
        request: CharacterGenerateRequest
    ): GenerateCharacterResponse {
        val uuid = UUID.randomUUID()
        val presign: PresignDTO = s3Service.generatePresignedPutUrl(memberInfo, uuid)

        val aiServerRequest = AIServerCharacterGenerateRequest.from(
            characterGenerateRequest = request,
            presignDTO = presign
        )

        client.requestGeneration(aiServerRequest)

        val previewDTO: PreviewDTO = s3Service.getPreviewUrl(memberInfo, uuid)

        return GenerateCharacterResponse.from(previewDTO)
    }

    fun confirmCharacter(
        memberInfo: MemberInfoDTO,
        request: CharacterConfirmRequest
    ) : Long {
        val persistenceUrl = s3Service.copyPreviewToPersisted(memberInfo, request.uuid)

        val author: Member = memberService.getEntity(memberInfo.id)
            ?: throw InternalServerException(cause = IllegalStateException("회원을 찾지 못함 -> $memberInfo"))

        val character: Character = Character.builder()
            .author(author)
            .live2dModelUrl(persistenceUrl)
            .build()

        return repository.save(character).id
    }
}