package jnu.econovation.isekai.common.s3.dto.internal

import jnu.econovation.isekai.common.s3.enums.FileName

data class PersistDTO(
    val fileName: FileName,
    val url: String
)