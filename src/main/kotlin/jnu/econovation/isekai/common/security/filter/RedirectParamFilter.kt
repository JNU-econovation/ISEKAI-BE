package jnu.econovation.isekai.common.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jnu.econovation.isekai.common.cookie.constant.CookieConstant.OAUTH2_COOKIE_MAX_AGE
import jnu.econovation.isekai.common.cookie.constant.CookieConstant.REDIRECT_ORIGIN_KEY
import jnu.econovation.isekai.common.cookie.util.CookieUtil.addCookie
import jnu.econovation.isekai.common.exception.client.BadRedirectUrlException
import jnu.econovation.isekai.common.security.config.UriSecurityConfig
import jnu.econovation.isekai.common.security.exception.handler.Rest400Handler
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RedirectParamFilter(
    private val config: UriSecurityConfig,
    private val rest400Handler: Rest400Handler
) : OncePerRequestFilter() {

    companion object {
        private val kLogger = KotlinLogging.logger {}
        private const val OAUTH2_AUTHORIZATION_PATH = "/oauth2/authorization"
        private const val REDIRECT_ORIGIN_PARAM = "redirect"
    }

    private val matcher = AntPathMatcher()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!request.servletPath.startsWith(OAUTH2_AUTHORIZATION_PATH)) {
            filterChain.doFilter(request, response)
            return
        }

        val rawRedirectUri = request.getParameter(REDIRECT_ORIGIN_PARAM)
        val normalizedUri = rawRedirectUri?.let { normalizeUri(it) }

        if (!isAllowed(normalizedUri)) {
            kLogger.warn { "유효하지 않은 redirect url, raw -> $rawRedirectUri, normalized -> $normalizedUri" }

            rest400Handler.commence(
                request = request,
                response = response,
                authException = BadRedirectUrlException()
            )
            return
        }

        addCookie(
            request = request,
            response = response,
            value = normalizedUri,
            key = REDIRECT_ORIGIN_KEY,
            maxAge = OAUTH2_COOKIE_MAX_AGE
        )

        filterChain.doFilter(request, response)
    }

    @Suppress("HttpUrlsUsage")
    private fun normalizeUri(uri: String): String {
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            return uri
        }

        return if (uri.contains("localhost") || uri.contains("127.0.0.1")) {
            "http://$uri"
        } else {
            "https://$uri"
        }
    }

    private fun isAllowed(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false

        val targetUri = uri.trimEnd('/')

        return config.allowedFrontEndOrigins.stream()
            .anyMatch { pattern ->
                val normalizedPattern = pattern.trimEnd('/')
                matcher.match(normalizedPattern, targetUri)
            }
    }
}