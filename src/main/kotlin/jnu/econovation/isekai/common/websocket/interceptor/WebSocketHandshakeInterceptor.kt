package jnu.econovation.isekai.common.websocket.interceptor

import jnu.econovation.isekai.common.websocket.constant.WebSocketConstant.MEMBER_ID_KEY
import jnu.econovation.isekai.common.websocket.service.WebSocketTicketService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriTemplate

@Component
class WebSocketHandshakeInterceptor(
    private val webSocketTicketService: WebSocketTicketService
) : HandshakeInterceptor {
    companion object {
        const val TICKET = "ticket"
        const val CHARACTER_ID = "characterId"
        const val URI_TEMPLATE = "/characters/{characterId}/voice"
    }

    private val logger = KotlinLogging.logger {}

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val requestUri = UriComponentsBuilder.fromUri(request.uri).build()
        val ticket = requestUri.queryParams.getFirst(TICKET)

        if (ticket == null) {
            logger.warn { "티켓이 없습니다. URI: ${request.uri}" }
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }

        val memberId = webSocketTicketService.validateTicket(ticket)

        if (memberId == null) {
            logger.warn { "유효하지 않은 티켓입니다: $ticket" }
            response.setStatusCode(HttpStatus.FORBIDDEN)
            return false
        }

        attributes[MEMBER_ID_KEY] = memberId

        try {
            val path = request.uri.path
            val template = UriTemplate(URI_TEMPLATE)

            if (!template.matches(path)) {
                logger.warn { "URL 경로가 잘못되었습니다. path: $path" }
                response.setStatusCode(HttpStatus.BAD_REQUEST)
                return false
            }

            val variables = template.match(path)
            val characterId = variables[CHARACTER_ID]?.toLongOrNull()
                ?: run {
                    logger.warn { "characterId 파싱 실패. path: $path" }
                    response.setStatusCode(HttpStatus.BAD_REQUEST)
                    return false
                }

            attributes[CHARACTER_ID] = characterId

            logger.info { "Connection Request - characterId: $characterId, memberId: $memberId" }

        } catch (e: Exception) {
            logger.error(e) { "Path Variable 파싱 중 에러 발생" }
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR)
            return false
        }

        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
    }
}