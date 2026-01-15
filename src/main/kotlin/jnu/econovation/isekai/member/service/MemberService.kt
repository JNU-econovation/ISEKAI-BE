package jnu.econovation.isekai.member.service

import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.security.oauth.dto.internal.OAuth2MemberInfoDTO
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.member.dto.response.AboutMeResponse
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.repository.MemberRepository
import jnu.econovation.isekai.member.util.RandomNicknameGenerator
import jnu.econovation.isekai.member.vo.Email
import jnu.econovation.isekai.member.vo.Nickname
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
            ?: run {
                repository.save(
                    Member.builder()
                        .provider(oAuth2MemberInfoDTO.provider)
                        .email(oAuth2MemberInfoDTO.email)
                        .nickname(createUniqueNickname())
                        .build()
                )
            }

        return MemberInfoDTO.from(member)
    }

    fun getAboutMe(memberInfoDTO: MemberInfoDTO) : AboutMeResponse {
        return AboutMeResponse.from(memberInfoDTO)
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

    private fun createUniqueNickname(): Nickname {
        repeat(10) {
            val nickname = Nickname(RandomNicknameGenerator.generateNickname())

            if (!repository.existsByNickname(nickname)) {
                return nickname
            }
        }

        throw InternalServerException(cause = IllegalStateException("최대 10번 시도 후에도 고유한 닉네임을 생성하지 못했습니다. 현재 조합 가능한 닉네임이 부족할 수 있습니다."))
    }

}