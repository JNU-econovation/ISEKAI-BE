package jnu.econovation.isekai.common.s3.exception

import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

class UnexpectedFileSetException(val hint: String) : ClientException(ErrorCode.NO_SUCH_FILE) {
    override val message: String
        get() = String.format(errorCode.message, hint)
}