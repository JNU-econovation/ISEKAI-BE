package jnu.econovation.isekai.common.websocket.service

import jnu.econovation.isekai.common.websocket.dto.response.TicketResponse
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class WebSocketTicketService(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val TTL = 60L
    }

    fun getTicket(memberInfoDTO: MemberInfoDTO): TicketResponse {
        val uuid = UUID.randomUUID().toString()

        redisTemplate.opsForValue().set(uuid, memberInfoDTO.id.toString(), TTL, TimeUnit.SECONDS)

        return TicketResponse(ticket = uuid)
    }

    fun validateTicket(ticket: String): Long? {
        val rawValue = redisTemplate.opsForValue().get(ticket) ?: return null

        redisTemplate.delete(ticket)

        return rawValue.toLongOrNull()
    }

}