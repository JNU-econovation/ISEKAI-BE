package jnu.econovation.isekai.common.exception

import jnu.econovation.isekai.common.exception.enums.ErrorCode
import org.springframework.core.NestedRuntimeException

abstract class BusinessException(
    val errorCode: ErrorCode,
    cause: Throwable? = null
) : NestedRuntimeException(errorCode.message, cause)