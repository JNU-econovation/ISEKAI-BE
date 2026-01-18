package jnu.econovation.isekai.aiServer.exception

import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode

class NoSuchVoiceException : ClientException(ErrorCode.NO_SUCH_VOICE_ID) {
    override val message: String
        get() = errorCode.message
}