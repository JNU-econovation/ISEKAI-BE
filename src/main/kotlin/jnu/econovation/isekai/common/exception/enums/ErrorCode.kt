package jnu.econovation.isekai.common.exception.enums;

import jnu.econovation.isekai.common.constant.Domain;
import org.springframework.http.HttpStatus;

enum class ErrorCode(
        private val domain: Domain,
        private val status: HttpStatus,
        private val number: Int,
        val message: String
) {
    INTERNAL_SERVER(Domain.COMMON, HttpStatus.INTERNAL_SERVER_ERROR, 1, "서버 내부 오류입니다."),
    BAD_DATA_SYNTAX(Domain.COMMON, HttpStatus.BAD_REQUEST, 1, "%s");

    fun getCode() = "${domain.name}_${status.value()}_%03d".format(number)
}