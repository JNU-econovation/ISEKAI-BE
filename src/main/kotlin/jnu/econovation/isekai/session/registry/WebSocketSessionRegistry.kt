package jnu.econovation.isekai.session.registry

import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Service
class WebSocketSessionRegistry {
    // 만약 서버가 여러대다 -> redis로 세션 아이디랑 서버 아이디 매핑해서 라우팅 하는 방식으로 구현
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    fun register(session: WebSocketSession) {
        sessions[session.id] = session
    }

    fun unregister(sessionId: String) {
        sessions.remove(sessionId)
    }

    fun getSession(sessionId: String): WebSocketSession? {
        return sessions[sessionId]
    }
}