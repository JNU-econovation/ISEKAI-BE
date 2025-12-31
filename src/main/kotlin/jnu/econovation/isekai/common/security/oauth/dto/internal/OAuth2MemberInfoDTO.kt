package jnu.econovation.isekai.common.security.oauth.dto.internal

import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.member.vo.Email
import jnu.econovation.isekai.member.vo.Oauth2Provider

data class OAuth2MemberInfoDTO(
    val provider: Oauth2Provider,
    val email: Email
) {
    companion object {
        fun of(registrationId: String, attributes: Map<String, Any>): OAuth2MemberInfoDTO {
            return when (registrationId) {
                "kakao" -> ofKakao(attributes)
                else -> throw InternalServerException(IllegalStateException("예상치 못한 registrationId -> $registrationId"))
            }
        }

        private fun ofKakao(attributes: Map<String, Any>): OAuth2MemberInfoDTO {
            val account = attributes["kakao_account"] as? Map<*, *>
                ?: throw InternalServerException(IllegalStateException("kakao_account가 null일 수 없습니다."))

            val value = account["email"] as? String
                ?: throw InternalServerException(IllegalStateException("email이 null일 수 없습니다."))

            return OAuth2MemberInfoDTO(provider = Oauth2Provider.KAKAO, email = Email(value))
        }
    }
}