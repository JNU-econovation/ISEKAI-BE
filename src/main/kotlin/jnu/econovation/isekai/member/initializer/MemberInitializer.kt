package jnu.econovation.isekai.member.initializer

import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_EMAIL
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.service.MemberService
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Order(1)
@Component
class MemberInitializer(private val service: MemberService) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    @EventListener(ApplicationReadyEvent::class)
    fun init() {
        service.findByEmail(MASTER_EMAIL)
            ?.let { logger.info { "Master Member가 이미 DB에 존재함" } }
            ?: run {
                val masterMember = Member.builder().email(MASTER_EMAIL).build()
                service.save(masterMember)
                logger.info { "Master Member 저장 완료 -> $masterMember" }
            }
    }
}