package jnu.econovation.isekai.prompt.exception

import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

class NoSuchPromptException() : ClientException(ErrorCode.NO_SUCH_PROMPT) {
    override val message: String get() = errorCode.message
}