package jnu.econovation.isekai.persona.exception

import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

class NoSuchPersonaException() : ClientException(ErrorCode.NO_SUCH_PROMPT) {
    override val message: String get() = errorCode.message
}