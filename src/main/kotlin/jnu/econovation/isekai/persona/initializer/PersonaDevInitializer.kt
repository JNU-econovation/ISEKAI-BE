package jnu.econovation.isekai.persona.initializer

import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_EMAIL
import jnu.econovation.isekai.member.service.MemberService
import jnu.econovation.isekai.persona.model.entity.Persona
import jnu.econovation.isekai.persona.model.vo.Content
import jnu.econovation.isekai.persona.model.vo.PersonaName
import jnu.econovation.isekai.persona.repository.PersonaRepository
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Profile("dev")
@Order(2)
@Component
class PersonaDevInitializer(
    private val personaRepository: PersonaRepository,
    private val memberService: MemberService
) {
    private val logger = KotlinLogging.logger {}

    private companion object {
        const val PROMPT_COUNT = 100
    }

    @Transactional
    @EventListener(ApplicationReadyEvent::class)
    fun init() {
        val author = memberService.findByEmailEntity(MASTER_EMAIL)
            ?: throw InternalServerException(IllegalStateException("MASTER MEMBER를 찾을 수 없습니다."))

        if (personaRepository.count() < 1) {
            logger.info { "테스트 prompt ${PROMPT_COUNT}개 저장 중" }
            val testPrompts = (0 until PROMPT_COUNT).map {
                Persona.builder()
                    .author(author)
                    .content(Content("테스트 프롬프트 ${it + 1}"))
                    .name(PersonaName("테스트 페르소나 ${it + 1}"))
                    .isPublic(true)
                    .build()
            }
            personaRepository.saveAll(testPrompts)
            logger.info { "테스트 prompt ${PROMPT_COUNT}개 저장 성공!" }
        }
    }

}