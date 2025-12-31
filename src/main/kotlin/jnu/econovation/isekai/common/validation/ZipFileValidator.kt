package jnu.econovation.isekai.common.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jnu.econovation.isekai.common.annotaion.ZipFile

class ZipFileValidator : ConstraintValidator<ZipFile, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        if (value.isNullOrEmpty()) {
            return true
        }

        return value.lowercase().endsWith(".zip")
    }
}