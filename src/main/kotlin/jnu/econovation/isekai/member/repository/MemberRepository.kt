package jnu.econovation.isekai.member.repository

import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.vo.Email
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: Email): Member?
}