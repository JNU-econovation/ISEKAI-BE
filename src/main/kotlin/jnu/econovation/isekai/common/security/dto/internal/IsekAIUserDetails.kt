package jnu.econovation.isekai.common.security.dto.internal

import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.user.OAuth2User

data class IsekAIUserDetails(
    val memberInfo: MemberInfoDTO,
    private val attributes: Map<String, Any>? = null,
    private val userNameAttributeName: String? = null
) : UserDetails, OAuth2User {

    companion object {
        private const val ROLE_PREFIX = "ROLE_"
    }

    override fun getUsername(): String = memberInfo.email.value
    override fun getPassword(): String = "Social"
    override fun getAuthorities() = listOf(SimpleGrantedAuthority(ROLE_PREFIX + memberInfo.role))
    override fun isAccountNonLocked() = true
    override fun isAccountNonExpired() = true
    override fun isCredentialsNonExpired() = true
    override fun isEnabled() = true
    override fun getName() = attributes?.get(userNameAttributeName)?.toString() ?: "UNKNOWN"
    override fun getAttributes() = this.attributes

}
