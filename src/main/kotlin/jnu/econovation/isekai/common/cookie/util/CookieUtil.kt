package jnu.econovation.isekai.common.cookie.util

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

object CookieUtil {
    fun addCookie(
        request: HttpServletRequest,
        response: HttpServletResponse,
        key: String,
        value: String?,
        maxAge: Int
    ) {
        val cookie = Cookie(key, value)

        cookie.path = "/"
        cookie.isHttpOnly = true
        cookie.secure = request.isSecure
        cookie.maxAge = maxAge

        response.addCookie(cookie)
    }

    fun removeCookie(
        request: HttpServletRequest,
        response: HttpServletResponse,
        key: String
    ) {
        val cookie = Cookie(key, null)
        cookie.path = "/"
        cookie.isHttpOnly = true
        cookie.secure = request.isSecure
        cookie.maxAge = 0
        response.addCookie(cookie)
    }
}