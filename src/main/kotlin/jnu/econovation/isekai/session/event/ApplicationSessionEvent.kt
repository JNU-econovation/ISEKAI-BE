package jnu.econovation.isekai.session.event

import jnu.econovation.isekai.session.dto.internal.GeminiResponseDTO
import jnu.econovation.isekai.session.dto.internal.ServerReadyDTO
import org.springframework.context.ApplicationEvent

sealed class ApplicationSessionEvent(
    source: Any,
    open val sessionId: String,
    open val content: EventContent
) : ApplicationEvent(source)

class ServerReadyEvent(
    source: Any,
    override val sessionId: String,
    override val content: ServerReadyDTO = ServerReadyDTO()
) : ApplicationSessionEvent(source, sessionId, content)

class GeminiResponseEvent(
    source: Any,
    override val sessionId: String,
    override val content: GeminiResponseDTO
) : ApplicationSessionEvent(source, sessionId, content)

interface EventContent