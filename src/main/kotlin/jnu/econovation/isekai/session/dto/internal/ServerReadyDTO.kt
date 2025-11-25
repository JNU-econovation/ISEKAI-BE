package jnu.econovation.isekai.session.dto.internal

import jnu.econovation.isekai.session.dto.response.SessionResponseContent

data class ServerReadyDTO(
    val text: String = "I'M READY"
) : SessionResponseContent