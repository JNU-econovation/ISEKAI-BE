package jnu.econovation.isekai.common.exception.client

import jnu.econovation.isekai.common.exception.BusinessException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

abstract class ClientException(errorCode: ErrorCode) : BusinessException(errorCode)