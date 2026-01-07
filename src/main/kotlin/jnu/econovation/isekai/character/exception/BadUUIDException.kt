package jnu.econovation.isekai.character.exception

import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

class BadUUIDException : ClientException(ErrorCode.BAD_UUID) {
    override val message: String
        get() = errorCode.message
}