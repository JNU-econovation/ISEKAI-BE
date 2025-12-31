package jnu.econovation.isekai.common.security.exception.handler

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jnu.econovation.isekai.common.dto.response.CommonResponse
import jnu.econovation.isekai.common.exception.enums.ErrorCode
import jnu.econovation.isekai.common.security.constant.SecurityConstant.AUTHORIZATION_HEADER
import mu.KotlinLogging
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class Rest401Handler : AuthenticationEntryPoint {
    private val logger = KotlinLogging.logger {}

    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
    }

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"

        logger.warn {
            "인증 실패 -> ${authException.message}, URI - ${request.requestURI}, " +
                    "authorization header - ${request.getHeader(AUTHORIZATION_HEADER)?.take(10)}..."
        }

        mapper.writeValue(
            response.writer,
            CommonResponse.ofFailure(ErrorCode.UNAUTHORIZED)
        )
    }
}