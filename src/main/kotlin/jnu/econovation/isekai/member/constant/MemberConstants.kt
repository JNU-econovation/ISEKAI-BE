package jnu.econovation.isekai.member.constant

import jnu.econovation.isekai.member.vo.Email
import jnu.econovation.isekai.member.vo.Oauth2Provider

object MemberConstants {
    val MASTER_EMAIL = Email("admin@isekai.com")
    val MASTER_PROVIDER = Oauth2Provider.KAKAO
    const val NICKNAME_MAX_LENGTH = 15
    const val NICKNAME_MIN_LENGTH = 2
}