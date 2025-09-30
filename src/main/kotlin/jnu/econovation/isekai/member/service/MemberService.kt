package jnu.econovation.isekai.member.service

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

    @Transactional(readOnly = true)
    fun findByEmail(email: Email) = repository.findByEmail(email)

}