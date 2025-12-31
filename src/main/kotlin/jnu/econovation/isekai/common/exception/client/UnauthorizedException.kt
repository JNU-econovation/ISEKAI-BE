package jnu.econovation.isekai.common.exception.client

import jnu.econovation.isekai.common.exception.enums.ErrorCode
import org.springframework.security.core.AuthenticationException

class UnauthorizedException : AuthenticationException(ErrorCode.UNAUTHORIZED.message)