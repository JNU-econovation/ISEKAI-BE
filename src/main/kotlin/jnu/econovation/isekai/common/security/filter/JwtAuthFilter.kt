package jnu.econovation.isekai.common.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jnu.econovation.isekai.common.constant.CommonConstant.criticalError
import jnu.econovation.isekai.common.security.constant.SecurityConstant.AUTHORIZATION_HEADER
import jnu.econovation.isekai.common.security.exception.handler.Rest401Handler
import jnu.econovation.isekai.common.security.exception.handler.Rest500Handler
import jnu.econovation.isekai.common.security.helper.JwtAuthHelper
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val helper: JwtAuthHelper,
    private val rest401Handler: Rest401Handler,
    private val rest500Handler: Rest500Handler
) : OncePerRequestFilter() {

    companion object {
        private val WHITELIST = setOf(
            "/h2-console/**",
            "/auth/success",
            "/error",
            "/favicon.ico",
            "/oauth2/authorization/**",
            "/login/**",
            "/characters/*/voice/**"
        )

        private val BLACKLIST = setOf(
            "/websocket/ticket",
            "/me/**"
        )

        private val GREYLIST = setOf(
            "/characters/**"
        )
        private val pathMatcher: AntPathMatcher = AntPathMatcher()
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI

        val isWhitelisted = WHITELIST.any { pathMatcher.match(it, path) }
        val isBlacklisted = BLACKLIST.any { pathMatcher.match(it, path) }

        return isWhitelisted && !isBlacklisted
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        val isGreyList = GREYLIST
            .stream()
            .anyMatch { pathMatcher.match(it, request.requestURI) }

        val isGetMethod = request.method == HttpMethod.GET.name()

        helper.authenticate(request.getHeader(AUTHORIZATION_HEADER))
            .onSuccess { auth ->
                if (auth is AbstractAuthenticationToken) {
                    auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                }
                SecurityContextHolder.getContext().authentication = auth
                filterChain.doFilter(request, response)
            }
            .onFailure { exception ->
                if (isGreyList && isGetMethod) {
                    SecurityContextHolder.clearContext()
                    filterChain.doFilter(request, response)
                } else {
                    handleException(exception, request, response)
                }
            }
    }

    private fun handleException(
        e: Throwable,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        SecurityContextHolder.clearContext()

        when (e) {
            is AuthenticationException -> {
                rest401Handler.commence(request, response, e)
            }

            else -> {
                val serviceException = AuthenticationServiceException(criticalError("인증"), e)
                rest500Handler.commence(request, response, serviceException)
            }
        }
    }
}
