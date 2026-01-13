package jnu.econovation.isekai.common.security.oauth.handler


import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jnu.econovation.isekai.common.cookie.constant.CookieConstant.REDIRECT_ORIGIN_KEY
import jnu.econovation.isekai.common.cookie.util.CookieUtil.removeCookie
import jnu.econovation.isekai.common.security.config.UriSecurityConfig
import jnu.econovation.isekai.common.security.dto.internal.IsekAIUserDetails
import jnu.econovation.isekai.common.security.util.JwtUtil
import mu.KotlinLogging
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.WebUtils

@Component
class OAuth2SuccessHandler(
    private val config: UriSecurityConfig,
    private val jwtUtil: JwtUtil
) : AuthenticationSuccessHandler {

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val ACCESS_TOKEN = "accessToken"
    }

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val userDetails = authentication.principal as? IsekAIUserDetails
            ?: run {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
        val accessToken: String = jwtUtil.generateToken(userDetails.memberInfo)

        val redirectOrigin: String = WebUtils
            .getCookie(request, REDIRECT_ORIGIN_KEY)
            ?.value
            ?: config.defaultRedirectOrigin

        removeCookie(
            request = request,
            response = response,
            key = REDIRECT_ORIGIN_KEY
        )

        val redirectUrl = UriComponentsBuilder.fromUriString(redirectOrigin)
            .path(config.successPath)
            .queryParam(ACCESS_TOKEN, accessToken)
            .build()
            .toUriString()

        response.sendRedirect(redirectUrl)
    }

}