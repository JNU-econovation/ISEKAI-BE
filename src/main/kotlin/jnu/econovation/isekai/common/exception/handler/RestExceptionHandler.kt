package jnu.econovation.isekai.common.exception.handler

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jnu.econovation.isekai.common.dto.response.CommonResponse
import jnu.econovation.isekai.common.exception.client.ClientException
import jnu.econovation.isekai.common.exception.enums.ErrorCode
import jnu.econovation.isekai.common.exception.server.InternalServerException
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler


@RestControllerAdvice
class RestExceptionHandler : ResponseEntityExceptionHandler() {

    companion object {
        private val kLogger = KotlinLogging.logger {}
        private const val INTERNAL_SERVER_ERROR_MESSAGE = "서버에서 예상치 못한 오류가 발생했습니다."
    }

    override fun handleMissingServletRequestParameter(
        ex: MissingServletRequestParameterException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val parameterName = ex.parameterName
        val message = "필요로 하는 파라미터 -> $parameterName 이(가) 없습니다."

        kLogger.warn { "Missing parameter: $parameterName" }

        return handleExceptionInternal(ErrorCode.INVALID_INPUT_VALUE, message)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val errorMessage = StringBuilder()

        val firstError = ex.bindingResult.allErrors.stream()
            .findFirst()
            .map { it.defaultMessage }
            .orElse("잘못된 요청입니다.")

        errorMessage.append(firstError)

        // debugging
        if (kLogger.isDebugEnabled) {
            ex.bindingResult.allErrors.forEach { error ->
                if (error is FieldError) {
                    kLogger.debug { "Validation error - Field: ${error.field}, Value: ${error.rejectedValue}, Message: ${error.defaultMessage}" }
                } else {
                    kLogger.debug("Validation error - Message: ${error.defaultMessage}")
                }
            }
        }

        return handleExceptionInternal(ErrorCode.INVALID_INPUT_VALUE, errorMessage.toString())
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        var message = "잘못된 JSON 형식입니다."
        val cause = ex.cause

        if (cause is JsonProcessingException) {
            kLogger.warn("JSON parsing error: ${cause.message}")

            when (cause) {
                is InvalidFormatException -> {
                    val fieldName =
                        if (cause.path.isEmpty()) "unknown" else cause.path.first().fieldName

                    message = "필드 ${fieldName}의 값이 올바르지 않습니다 -> ${cause.value}"
                }

                is MismatchedInputException -> {
                    val fieldName =
                        if (cause.path.isEmpty()) "unknown" else cause.path.first().fieldName

                    message = "필드 ${fieldName}의 타입이 올바르지 않습니다"
                }

                is JsonMappingException -> {
                    val fieldName: String =
                        if (cause.path.isEmpty()) "unknown" else cause.path.first().fieldName

                    val causeMessage: String? =
                        if (cause.cause != null) cause.cause?.message else ""

                    message = String.format("필드 '%s'에 문제가 있습니다: %s", fieldName, causeMessage ?: "")
                }
            }
        } else {
            kLogger.warn { "HTTP message not readable: ${ex.message}" }

            message = "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요."
        }

        return handleExceptionInternal(ErrorCode.INVALID_INPUT_VALUE, message)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(
        ex: MethodArgumentTypeMismatchException
    ): ResponseEntity<Any> {
        val message = "파라미터 ${ex.name}의 값 ${ex.value}이(가) 올바르지 않습니다."

        kLogger.warn { "Type mismatch for parameter: ${ex.name} with value: ${ex.value}" }

        return handleExceptionInternal(ErrorCode.INVALID_INPUT_VALUE, message)
    }

    @ExceptionHandler(JsonProcessingException::class)
    fun handleJsonProcessingException(
        ex: JsonProcessingException
    ): ResponseEntity<Any> {
        kLogger.warn { "JSON processing error: ${ex.message}" }
        val message = "JSON 처리 중 오류가 발생했습니다: ${ex.originalMessage}"
        return handleExceptionInternal(ErrorCode.INVALID_INPUT_VALUE, message)
    }

    @ExceptionHandler(ClientException::class)
    fun handleClientException(e: ClientException): ResponseEntity<Any> {
        kLogger.warn { "클라이언트 예외 발생: Code={${e.errorCode.getCode()}}, Message={${e.message}}" }

        return handleClientExceptionInternal(e)
    }


    @ExceptionHandler(InternalServerException::class)
    fun handleServerException(e: InternalServerException): ResponseEntity<Any> {
        val errorCode: ErrorCode = e.errorCode
        kLogger.error(e) { "서버에 의한 오류 발생: Code=${errorCode.getCode()}, Message=${errorCode.message}" }

        if (e.errorCode.status != HttpStatus.INTERNAL_SERVER_ERROR) {
            return handleExceptionInternal(errorCode, e.errorCode.message)
        }

        return handleExceptionInternal(errorCode, INTERNAL_SERVER_ERROR_MESSAGE)
    }

    private fun handleClientExceptionInternal(e: ClientException): ResponseEntity<Any> {
        val message = e.message ?: e.errorCode.message

        return ResponseEntity.status(e.errorCode.status)
            .body(CommonResponse.ofFailure(message, e.errorCode))
    }

    private fun handleExceptionInternal(
        errorCode: ErrorCode,
        customErrorMessage: String
    ): ResponseEntity<Any> {
        return ResponseEntity.status(errorCode.status)
            .body(CommonResponse.ofFailure(customErrorMessage, errorCode))
    }

}