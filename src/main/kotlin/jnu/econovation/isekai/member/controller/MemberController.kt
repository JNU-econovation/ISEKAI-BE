package jnu.econovation.isekai.member.controller

import jnu.econovation.isekai.common.security.dto.internal.IsekAIUserDetails
import jnu.econovation.isekai.member.dto.response.AboutMeResponse
import jnu.econovation.isekai.member.service.MemberService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/me")
class MemberController(
    private val service: MemberService
) {

    @GetMapping
    fun aboutMe(@AuthenticationPrincipal userDetails: IsekAIUserDetails): AboutMeResponse {
        return service.getAboutMe(userDetails.memberInfo)
    }

}