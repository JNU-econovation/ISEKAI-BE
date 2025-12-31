package jnu.econovation.isekai.member.dto.internal

import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.vo.Email
import jnu.econovation.isekai.member.vo.Role

data class MemberInfoDTO(
    val id: Long,
    val email: Email,
    val role: Role
) {
    companion object {
        fun from(member: Member) = MemberInfoDTO(member.id, member.email, member.role)
    }
}
