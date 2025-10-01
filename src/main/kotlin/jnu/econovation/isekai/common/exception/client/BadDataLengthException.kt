
package jnu.econovation.isekai.common.exception.client

import jnu.econovation.isekai.common.exception.enums.ErrorCode

class BadDataLengthException(
    val fieldName: String,
    val minLength: Int,
    val maxLength: Int
) : ClientException(ErrorCode.BAD_DATA_MEANING) {
    override val message: String
        get() = String.format(
            errorCode.message,
            "%s의 길이는 %d자 부터 %d자 까지 가능합니다.".format(fieldName, minLength, maxLength)
        )
}