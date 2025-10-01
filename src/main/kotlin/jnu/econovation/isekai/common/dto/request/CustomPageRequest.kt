package jnu.econovation.isekai.common.dto.request

data class CustomPageRequest(
    val sort: String?,
    val direction: String?,
    val size: Int?,
    val page: Int?
)