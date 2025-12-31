package jnu.econovation.isekai.common.constant

object CommonConstant {
    private const val CRITICAL_ERROR_MESSAGE = "알 수 없는 예외로 인한 %s 실패"

    val criticalError = CriticalErrorFormatter(CRITICAL_ERROR_MESSAGE)
}

class CriticalErrorFormatter(private val template: String) {
    operator fun invoke(action: String): String = template.format(action)
}