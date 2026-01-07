package jnu.econovation.isekai.character.initializer

import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.model.entity.Character
import jnu.econovation.isekai.character.model.vo.CharacterName
import jnu.econovation.isekai.character.model.vo.Persona
import jnu.econovation.isekai.character.repository.CharacterRepository
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_EMAIL
import jnu.econovation.isekai.member.event.MemberInitializedEvent
import jnu.econovation.isekai.member.repository.MemberRepository
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("dev")
class CharacterInitializer(
    private val memberRepository: MemberRepository,
    private val repository: CharacterRepository
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    @EventListener(MemberInitializedEvent::class)
    fun init() {
        if (repository.count() == 0L) {
            val masterMember = memberRepository.findByEmail(MASTER_EMAIL)
                ?: throw InternalServerException(cause = IllegalStateException("master member not found"))

            val character = Character.builder()
                .voiceId("1")
                .name(CharacterName("테스트 캐릭터"))
                .persona(Persona("테스트 페르소나 입니다. 아무렇게나 대답하세요"))
                .author(masterMember)
                .isPublic(true)
                .backgroundUrl("asdf")
                .live2dModelNukkiUrl("asdf")
                .live2dModelUrl("asdf")
                .thumbnailUrl("asdf")
                .build()

            repository.save(character)

            logger.info { "테스트 용 캐릭터 db 저장 완료 -> ${CharacterDTO.from(character)}" }
        } else {
            logger.info { "character가 이미 DB에 존재함" }
        }
    }

}