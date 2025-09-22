package jnu.econovation.isekai.common.exception.client

import jnu.econovation.isekai.common.exception.enums.ErrorCode

class BadDataSyntaxException(val detailMessage: String) : ClientException(ErrorCode.BAD_DATA_SYNTAX) {
    override val message: String get() = String.format(errorCode.message, detailMessage)
}