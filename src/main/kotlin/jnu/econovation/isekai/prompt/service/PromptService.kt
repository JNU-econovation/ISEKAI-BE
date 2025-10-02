package jnu.econovation.isekai.prompt.service

import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_EMAIL
import jnu.econovation.isekai.member.service.MemberService
import jnu.econovation.isekai.prompt.dto.internal.PromptDTO
import jnu.econovation.isekai.prompt.dto.internal.PromptRequest
import jnu.econovation.isekai.prompt.dto.response.PromptResponse
import jnu.econovation.isekai.prompt.dto.response.PromptsPageResponse
import jnu.econovation.isekai.prompt.exception.NoSuchPromptException
import jnu.econovation.isekai.prompt.model.entity.Prompt
import jnu.econovation.isekai.prompt.model.vo.Content
import jnu.econovation.isekai.prompt.model.vo.PersonaName
import jnu.econovation.isekai.prompt.repository.PromptRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PromptService(
    private val memberService: MemberService,
    private val repository: PromptRepository
) {

    @Transactional
    fun save(request: PromptRequest): Long {
        val memberEntity = memberService.findByEmailEntity(MASTER_EMAIL)

        val promptEntity = Prompt.builder()
            .author(memberEntity)
            .personaName(PersonaName(request.personaName))
            .content(Content(request.content))
            .isPublic(request.isPublic)
            .build()

        return repository.save(promptEntity).id
    }

    @Transactional(readOnly = true)
    fun get(pageable: Pageable): Page<PromptsPageResponse> {
        return repository.findAll(pageable).map { PromptsPageResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): PromptDTO {
        return repository.findById(id)
            .map { PromptDTO.from(it) }
            .orElseThrow { NoSuchPromptException() }
    }

    @Transactional(readOnly = true)
    fun getForResponse(id: Long): PromptResponse {
        return get(id).toResponseDTO()
    }

    @Transactional
    fun update(id: Long, request: PromptRequest) {
        val promptEntity = repository.findById(id)
            .orElseThrow { NoSuchPromptException() }

        promptEntity.update(
            PersonaName(request.personaName),
            Content(request.content),
            request.isPublic
        )
    }
}