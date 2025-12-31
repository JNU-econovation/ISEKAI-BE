package jnu.econovation.isekai.common.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import jnu.econovation.isekai.common.exception.enums.ErrorCode

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CommonResponse<T>(
    val isSuccess: Boolean,
    val message: String,
    val errorCode: String? = null,
    val result: T? = null
) {
    companion object {
        private const val SUCCESS_MESSAGE = "success"

        fun ofSuccess(): CommonResponse<Unit> =
            CommonResponse(true, SUCCESS_MESSAGE)

        fun <T> ofSuccess(result: T): CommonResponse<T> =
            CommonResponse(true, SUCCESS_MESSAGE, null, result)

        fun ofFailure(errorCode: ErrorCode): CommonResponse<Unit> =
            CommonResponse(false, errorCode.message, errorCode.getCode())

        fun ofFailure(customMessage: String, errorCode: ErrorCode): CommonResponse<Unit> =
            CommonResponse(false, customMessage, errorCode.getCode())
    }
}