package jnu.econovation.isekai.common.security.exception.handler

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jnu.econovation.isekai.common.dto.response.CommonResponse
import jnu.econovation.isekai.common.exception.enums.ErrorCode
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class Rest400Handler : AuthenticationEntryPoint {

    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
    }

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"

        mapper.writeValue(
            response.writer,
            CommonResponse.ofFailure(ErrorCode.BAD_REDIRECT_URL)
        )
    }
}