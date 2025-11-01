package jnu.econovation.isekai.session.dto.internal

import jnu.econovation.isekai.gemini.dto.client.response.GeminiLiveResponse
import jnu.econovation.isekai.session.event.EventContent

data class GeminiResponseDTO(
    val response: GeminiLiveResponse,
    val personaId: Long
) : EventContent