package jnu.econovation.isekai.common.exception.enums

import jnu.econovation.isekai.common.constant.Domain
import org.springframework.http.HttpStatus

enum class ErrorCode(
    private val domain: Domain,
    val status: HttpStatus,
    private val number: Int,
    val message: String
) {
    INTERNAL_SERVER(Domain.COMMON, HttpStatus.INTERNAL_SERVER_ERROR, 1, "서버 내부 오류입니다."),
    INVALID_INPUT_VALUE(Domain.COMMON, HttpStatus.BAD_REQUEST, 1, "유효하지 않은 입력 값입니다."),
    BAD_DATA_SYNTAX(Domain.COMMON, HttpStatus.BAD_REQUEST, 2, "%s"),
    INVALID_PAGEABLE_FIELD(
        Domain.COMMON,
        HttpStatus.BAD_REQUEST,
        2,
        "페이징 가능한 필드가 아닙니다. -> %s = %s"
    ),
    BAD_DATA_MEANING(Domain.COMMON, HttpStatus.UNPROCESSABLE_ENTITY, 1, "%s"),
    UNAUTHORIZED(Domain.COMMON, HttpStatus.UNAUTHORIZED, 1, "인증되지 않은 사용자 입니다."),

    NO_SUCH_PROMPT(Domain.PERSONA, HttpStatus.NOT_FOUND, 1, "존재하지 않는 프롬프트입니다."),

    NO_SUCH_FILE(
        Domain.CLOUD_STORAGE,
        HttpStatus.NOT_FOUND,
        1,
        "해당 파일이 존재하지 않습니다. UUID가 올바른지 확인해주세요."
    ),

    UNEXPECTED_FILE_SET(
        Domain.CLOUD_STORAGE,
        HttpStatus.BAD_REQUEST,
        1,
        "파일 구성이 올바르지 않습니다. -> %s"
    );

    fun getCode() = "${domain.name}_${status.value()}_%03d".format(number)
}