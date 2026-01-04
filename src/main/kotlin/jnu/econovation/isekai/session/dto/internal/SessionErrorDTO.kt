package jnu.econovation.isekai.session.dto.internal

import jnu.econovation.isekai.session.dto.response.SessionResponseContent

data class SessionErrorDTO(
    val errorCode: String,
    val message: String
) : SessionResponseContent