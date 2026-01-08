package jnu.econovation.isekai.character.exception

import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

class NoSuchCharacterException : ClientException(ErrorCode.NO_SUCH_CHARACTER) {
    override val message: String
        get() = errorCode.message
}