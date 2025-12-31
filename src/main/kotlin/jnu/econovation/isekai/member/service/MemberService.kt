package jnu.econovation.isekai.member.service

import jnu.econovation.isekai.common.security.oauth.dto.internal.OAuth2MemberInfoDTO
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.repository.MemberRepository
import jnu.econovation.isekai.member.vo.Email
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val repository: MemberRepository
) {

    @Transactional
    fun save(member: Member) = repository.save(member)

    @Transactional
    fun getOrSave(oAuth2MemberInfoDTO: OAuth2MemberInfoDTO): MemberInfoDTO {
        val member = getEntityByEmail(oAuth2MemberInfoDTO.email)
            ?: repository.save(
                Member.builder()
                    .provider(oAuth2MemberInfoDTO.provider)
                    .email(oAuth2MemberInfoDTO.email)
                    .build()
            )

        return MemberInfoDTO.from(member)
    }

    @Transactional(readOnly = true)
    fun get(id: Long): MemberInfoDTO? {
        val entity = getEntity(id) ?: return null

        return MemberInfoDTO.from(entity)
    }

    @Transactional(readOnly = true)
    fun getEntity(id: Long): Member? = repository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun findByEmail(email: Email): MemberInfoDTO? {
        return findByEmailEntity(email)?.let { MemberInfoDTO.from(it) }
    }

    @Transactional(readOnly = true)
    fun findByEmailEntity(email: Email): Member? {
        return repository.findByEmail(email)
    }

    private fun getEntityByEmail(email: Email): Member? = repository.findByEmail(email)

}