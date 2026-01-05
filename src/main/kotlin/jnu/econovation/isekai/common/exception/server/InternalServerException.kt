package jnu.econovation.isekai.common.exception.server

import jnu.econovation.isekai.common.exception.BusinessException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

open class InternalServerException(override val cause: Throwable) :
    BusinessException(ErrorCode.INTERNAL_SERVER, cause)