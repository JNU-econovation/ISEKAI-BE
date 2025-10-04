package jnu.econovation.isekai.persona.service

import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_EMAIL
import jnu.econovation.isekai.member.service.MemberService
import jnu.econovation.isekai.persona.dto.internal.PersonaDTO
import jnu.econovation.isekai.persona.dto.internal.PersonaRequest
import jnu.econovation.isekai.persona.dto.response.PersonaResponse
import jnu.econovation.isekai.persona.dto.response.PersonaPageResponse
import jnu.econovation.isekai.persona.exception.NoSuchPersonaException
import jnu.econovation.isekai.persona.model.entity.Persona
import jnu.econovation.isekai.persona.model.vo.Content
import jnu.econovation.isekai.persona.model.vo.PersonaName
import jnu.econovation.isekai.persona.repository.PersonaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PersonaService(
    private val memberService: MemberService,
    private val repository: PersonaRepository
) {

    @Transactional
    fun save(request: PersonaRequest): Long {
        val memberEntity = memberService.findByEmailEntity(MASTER_EMAIL)

        val personaEntity = Persona.builder()
            .author(memberEntity)
            .name(PersonaName(request.personaName))
            .content(Content(request.content))
            .isPublic(request.isPublic)
            .build()

        return repository.save(personaEntity).id
    }

    @Transactional(readOnly = true)
    fun getDTO(pageable: Pageable): Page<PersonaPageResponse> {
        return repository.findAll(pageable).map { PersonaPageResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun getEntity(id: Long): Persona {
        return repository.findById(id)
            .orElseThrow { NoSuchPersonaException() }
    }

    @Transactional(readOnly = true)
    fun getDTO(id: Long): PersonaDTO {
        return PersonaDTO.from(getEntity(id))
    }

    @Transactional(readOnly = true)
    fun getResponse(id: Long): PersonaResponse {
        return getDTO(id).toResponseDTO()
    }

    @Transactional
    fun update(id: Long, request: PersonaRequest) {
        val promptEntity = repository.findById(id)
            .orElseThrow { NoSuchPersonaException() }

        promptEntity.update(
            PersonaName(request.personaName),
            Content(request.content),
            request.isPublic
        )
    }
}