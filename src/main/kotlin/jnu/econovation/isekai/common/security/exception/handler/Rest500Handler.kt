package jnu.econovation.isekai.common.security.exception.handler

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jnu.econovation.isekai.common.dto.response.CommonResponse
import jnu.econovation.isekai.common.exception.enums.ErrorCode
import mu.KotlinLogging
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class Rest500Handler : AuthenticationEntryPoint {
    private val mapper: ObjectMapper = ObjectMapper()
    private val logger = KotlinLogging.logger {}

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        logger.error(authException.message, authException.cause)

        response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"

        mapper.writeValue(
            response.writer,
            CommonResponse.ofFailure(ErrorCode.INTERNAL_SERVER)
        )
    }
}