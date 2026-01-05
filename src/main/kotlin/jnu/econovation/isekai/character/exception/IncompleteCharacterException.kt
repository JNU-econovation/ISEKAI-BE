package jnu.econovation.isekai.character.exception

import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

class IncompleteCharacterException : ClientException(ErrorCode.INCOMPLETE_CHARACTER) {
    override val message: String
        get() = errorCode.message
}