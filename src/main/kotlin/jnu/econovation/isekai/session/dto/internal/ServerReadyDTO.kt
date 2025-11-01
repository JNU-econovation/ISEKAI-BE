package jnu.econovation.isekai.session.dto.internal

import jnu.econovation.isekai.session.dto.response.SessionResponseContent
import jnu.econovation.isekai.session.event.EventContent

data class ServerReadyDTO(
    val text: String = "I'M READY"
) : EventContent, SessionResponseContent