package jnu.econovation.isekai.member.constant

import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.vo.Email

object MemberConstants {
    val MASTER_EMAIL = Email("admin@isekai.com")
    val MASTER_MEMBER: Member = Member.builder().email(MASTER_EMAIL).build()
}