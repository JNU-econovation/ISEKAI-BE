package jnu.econovation.isekai.common.websocket.controller

import jnu.econovation.isekai.common.security.dto.internal.IsekAIUserDetails
import jnu.econovation.isekai.common.websocket.dto.response.TicketResponse
import jnu.econovation.isekai.common.websocket.service.WebSocketTicketService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/websocket/ticket")
class WebSocketTicketController(
    private val service: WebSocketTicketService
) {
    @PostMapping
    fun getTicket(@AuthenticationPrincipal userDetails: IsekAIUserDetails): TicketResponse {
        return service.getTicket(memberInfoDTO = userDetails.memberInfo)
    }
}