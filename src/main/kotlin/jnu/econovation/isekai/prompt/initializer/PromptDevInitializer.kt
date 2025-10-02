package jnu.econovation.isekai.prompt.initializer

import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_MEMBER
import jnu.econovation.isekai.member.service.MemberService
import jnu.econovation.isekai.prompt.model.entity.Prompt
import jnu.econovation.isekai.prompt.model.vo.Content
import jnu.econovation.isekai.prompt.model.vo.PersonaName
import jnu.econovation.isekai.prompt.repository.PromptRepository
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
class PromptDevInitializer(
    private val promptRepository: PromptRepository,
    private val memberService: MemberService
) {
    private val logger = KotlinLogging.logger {}

    private companion object {
        const val PROMPT_COUNT = 100
    }

    @Transactional
    @EventListener(ApplicationReadyEvent::class)
    fun init() {
        val author = memberService.findByEmailEntity(MASTER_MEMBER.email)
            ?: throw InternalServerException(IllegalStateException("MASTER MEMBER를 찾을 수 없습니다."))

        if (promptRepository.count() < 1) {
            logger.info { "테스트 prompt ${PROMPT_COUNT}개 저장 중" }
            val testPrompts = (0 until PROMPT_COUNT).map {
                Prompt.builder()
                    .author(author)
                    .content(Content("테스트 프롬프트 ${it + 1}"))
                    .personaName(PersonaName("테스트 페르소나 ${it + 1}"))
                    .isPublic(true)
                    .build()
            }
            promptRepository.saveAll(testPrompts)
            logger.info { "테스트 prompt ${PROMPT_COUNT}개 저장 성공!" }
        }
    }

}