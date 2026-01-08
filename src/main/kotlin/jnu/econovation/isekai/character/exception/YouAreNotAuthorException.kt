package jnu.econovation.isekai.character.exception

import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

class YouAreNotAuthorException : ClientException(ErrorCode.YOU_ARE_NOT_AUTHOR) {
    override val message: String
        get() = errorCode.message
}