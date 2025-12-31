package jnu.econovation.isekai.common.security.oauth.service

import jnu.econovation.isekai.common.security.dto.internal.IsekAIUserDetails
import jnu.econovation.isekai.common.security.oauth.dto.internal.OAuth2MemberInfoDTO
import jnu.econovation.isekai.member.service.MemberService
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class IsekAIOAuth2UserService(
    private val memberService: MemberService
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2UserAttributes = super.loadUser(userRequest).attributes
        val registrationId = userRequest.clientRegistration.registrationId
        val userNameAttributeName = userRequest.clientRegistration
            .providerDetails.userInfoEndpoint.userNameAttributeName

        val oauth2MemberInfoDTO = OAuth2MemberInfoDTO.of(
            registrationId = registrationId,
            attributes = oAuth2UserAttributes
        )

        return IsekAIUserDetails(
            memberInfo = memberService.getOrSave(oauth2MemberInfoDTO),
            attributes = oAuth2UserAttributes,
            userNameAttributeName = userNameAttributeName
        )
    }

}