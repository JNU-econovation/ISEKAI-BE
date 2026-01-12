package jnu.econovation.isekai.common.exception.client

import jnu.econovation.isekai.common.exception.enums.ErrorCode
import org.springframework.security.core.AuthenticationException

class BadRedirectUrlException : AuthenticationException(ErrorCode.BAD_REDIRECT_URL.message)