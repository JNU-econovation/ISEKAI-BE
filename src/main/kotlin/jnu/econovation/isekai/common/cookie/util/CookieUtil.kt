package jnu.econovation.isekai.common.cookie.util

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie

object CookieUtil {
    fun addCookie(
        request: HttpServletRequest,
        response: HttpServletResponse,
        key: String,
        value: String?,
        maxAge: Int
    ) {
        val cookieBuilder = ResponseCookie.from(key, value ?: "")
            .path("/")
            .httpOnly(true)
            .maxAge(maxAge.toLong())
            .sameSite("Lax")

        if (request.isSecure) {
            cookieBuilder.secure(true)
            cookieBuilder.sameSite("None")
        }

        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString())
    }

    fun removeCookie(
        request: HttpServletRequest,
        response: HttpServletResponse,
        key: String
    ) {
        val cookieBuilder = ResponseCookie.from(key, "")
            .path("/")
            .httpOnly(true)
            .maxAge(0)
            .sameSite("Lax")

        if (request.isSecure) {
            cookieBuilder.secure(true)
        }

        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString())
    }
}