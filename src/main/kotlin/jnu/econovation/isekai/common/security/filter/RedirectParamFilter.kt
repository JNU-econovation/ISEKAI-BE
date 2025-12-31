package jnu.econovation.isekai.common.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jnu.econovation.isekai.common.cookie.constant.CookieConstant.OAUTH2_COOKIE_MAX_AGE
import jnu.econovation.isekai.common.cookie.constant.CookieConstant.REDIRECT_ORIGIN_KEY
import jnu.econovation.isekai.common.security.config.UriSecurityConfig
import jnu.econovation.isekai.common.cookie.util.CookieUtil.addCookie
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RedirectParamFilter(
    private val config: UriSecurityConfig
) : OncePerRequestFilter() {

    companion object {
        private const val OAUTH2_AUTHORIZATION_PATH = "/oauth2/authorization"
        private const val REDIRECT_ORIGIN_PARAM = "redirect"
    }

    private val matcher = AntPathMatcher()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.servletPath.startsWith(OAUTH2_AUTHORIZATION_PATH)) {
            val redirectUri: String? = request.getParameter(REDIRECT_ORIGIN_PARAM)

            if (isAllowed(redirectUri)) {
                addCookie(
                    request = request,
                    response = response,
                    value = redirectUri,
                    key = REDIRECT_ORIGIN_KEY,
                    maxAge = OAUTH2_COOKIE_MAX_AGE
                )
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun isAllowed(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false

        return config.allowedFrontEndOrigins.stream()
            .anyMatch { pattern -> matcher.match(pattern, uri) }
    }
}