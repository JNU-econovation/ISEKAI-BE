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
    INCOMPLETE_CHARACTER(Domain.CHARACTER,  HttpStatus.BAD_REQUEST, 1, "캐릭터 생성 시 필수적인 요소가 누락되었습니다."),
    BAD_UUID(Domain.CHARACTER, HttpStatus.BAD_REQUEST, 2, "UUID가 올바른 형식이 아닙니다."),
    NO_SUCH_PROMPT(Domain.PERSONA, HttpStatus.NOT_FOUND, 1, "존재하지 않는 프롬프트입니다.");

    fun getCode() = "${domain.name}_${status.value()}_%03d".format(number)
}