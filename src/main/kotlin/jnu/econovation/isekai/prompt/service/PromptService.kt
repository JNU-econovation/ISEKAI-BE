package jnu.econovation.isekai.prompt.service

import jnu.econovation.isekai.member.service.MemberService
import jnu.econovation.isekai.prompt.dto.internal.PromptDTO
import jnu.econovation.isekai.prompt.dto.response.PromptResponse
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
    fun save(promptDTO: PromptDTO) {
        val memberEntity = memberService.findByEmailEntity(promptDTO.author.email)

        val promptEntity = Prompt.builder()
            .author(memberEntity)
            .personaName(PersonaName(promptDTO.personaName))
            .content(Content(promptDTO.content))
            .isPublic(promptDTO.isPublic)
            .build()

        repository.save(promptEntity)
    }

    @Transactional(readOnly = true)
    fun get(pageable: Pageable): Page<PromptResponse> {
        return repository.findAll(pageable).map { PromptResponse.from(it) }
    }
}