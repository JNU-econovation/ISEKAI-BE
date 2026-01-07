package jnu.econovation.isekai.member.initializer

import jnu.econovation.isekai.common.security.util.JwtUtil
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_EMAIL
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_PROVIDER
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.event.MemberInitializedEvent
import jnu.econovation.isekai.member.service.MemberService
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Order(1)
@Component
@Profile("dev")
class MemberInitializer(
    private val service: MemberService,
    private val jwtUtil: JwtUtil,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    @EventListener(ApplicationReadyEvent::class)
    fun init() {
        service.findByEmail(MASTER_EMAIL)
            ?.let { logger.info { "Master Member가 이미 DB에 존재함" } }
            ?: run {
                val masterMember = Member.builder()
                    .email(MASTER_EMAIL)
                    .provider(MASTER_PROVIDER)
                    .build()
                service.save(masterMember)
                logger.info { "Master Member 저장 완료 -> $masterMember" }

                val accessToken = MemberInfoDTO.from(masterMember).let { jwtUtil.generateToken(it) }

                eventPublisher.publishEvent(MemberInitializedEvent(this))

                logger.info { "테스트 member accessToken -> $accessToken" }
            }
    }
}