package jnu.econovation.isekai.common.security.util

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtil(
    @param:Value("\${jwt.secret-key}")
    private val secretKey: String
) {
    companion object {
        private val EXPIRATION = Duration.ofHours(6)
    }

    private lateinit var key: SecretKey
    private lateinit var jwtParser: JwtParser

    @PostConstruct
    fun init() {
        key = Keys.hmacShaKeyFor(secretKey.toByteArray())
        jwtParser = Jwts.parser().verifyWith(key).build()
    }

    fun extractId(token: String) = jwtParser.parseSignedClaims(token).payload.subject.toLongOrNull()

    fun generateToken(memberInfo: MemberInfoDTO): String {
        return Jwts.builder()
            .subject(memberInfo.id.toString())
            .claim("id", memberInfo.id)
            .claim("email", memberInfo.email.value)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + EXPIRATION.toMillis()))
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return try {
            jwtParser.parseSignedClaims(token)
            !isTokenExpired(token)
        } catch (_: JwtException) {
            false
        }
    }

    private fun isTokenExpired(token: String?): Boolean {
        return jwtParser.parseSignedClaims(token).payload.expiration.before(Date())
    }

}