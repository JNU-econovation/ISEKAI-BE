package jnu.econovation.isekai.member.repository

import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.vo.Nickname
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberRepository : JpaRepository<Member, Long> {
    fun findByEmailHash(emailHash: String): Member?
    fun existsByNickname(nickName: Nickname) : Boolean
}