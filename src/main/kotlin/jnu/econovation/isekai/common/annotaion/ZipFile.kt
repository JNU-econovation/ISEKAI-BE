package jnu.econovation.isekai.common.annotaion

import jakarta.validation.Constraint
import jakarta.validation.Payload
import jnu.econovation.isekai.common.validation.ZipFileValidator
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ZipFileValidator::class])
annotation class ZipFile(
    val message: String = "파일 확장자는 .zip이어야 합니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)