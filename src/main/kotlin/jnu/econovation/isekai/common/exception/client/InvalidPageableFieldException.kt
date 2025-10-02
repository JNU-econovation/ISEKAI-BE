package jnu.econovation.isekai.common.exception.client

import jnu.econovation.isekai.common.exception.enums.ErrorCode

class InvalidPageableFieldException(
    val fieldName: String,
    val clientInput: String
) : ClientException(ErrorCode.INVALID_PAGEABLE_FIELD) {

    override val message: String
        get() = String.format(errorCode.message, fieldName, clientInput)
}