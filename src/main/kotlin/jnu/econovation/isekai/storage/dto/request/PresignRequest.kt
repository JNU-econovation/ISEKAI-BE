package jnu.econovation.isekai.storage.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jnu.econovation.isekai.common.annotaion.ZipFile

data class PresignRequest(
    @field:ZipFile
    @field:NotBlank(message = "파일 이름은 필수입니다.")
    @field:Pattern(
        regexp = "^[\\p{L}\\p{N}._\\- ]{1,100}$",
        message = "파일 이름은 문자, 숫자, 점, _, - 및 공백만 포함할 수 있습니다."
    )
    @field:Size(max = 100, message = "파일 이름은 최대 100자까지 허용됩니다.")
    val fileName: String?
)