package jnu.econovation.isekai.member.dto.response

import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.member.vo.Nickname

data class AboutMeResponse(
    val email : String,
    val nickname: Nickname
) {
    companion object {
        fun from(memberInfoDTO: MemberInfoDTO) : AboutMeResponse {
            return AboutMeResponse(
                email = memberInfoDTO.email.value,
                nickname = memberInfoDTO.nickname
            )
        }
    }
}