package jnu.econovation.isekai.common.security.util

import jnu.econovation.isekai.common.exception.server.InternalServerException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AES256Util(
    @param:Value("\${aes256.key}")
    private val key: String
) {
    private val secretKey: SecretKeySpec

    companion object {
        const val ALGORITHM = "AES"
        const val TRANSFORMATION = "$ALGORITHM/CBC/PKCS5Padding"
    }

    init {
        val keyBytes = key.toByteArray(StandardCharsets.UTF_8)

        if (keyBytes.size != 32) {
            throw InternalServerException(IllegalArgumentException("AES-256 키는 32 bytes 길이여야 합니다."))
        }

        secretKey = SecretKeySpec(keyBytes, ALGORITHM)
    }

    fun encrypt(plainText: String): String {
        return try {
            val ivBytes = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val ivSpec = IvParameterSpec(ivBytes)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            val combined = ivBytes + encrypted

            Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            throw InternalServerException(cause = e)
        }
    }

    fun decrypt(cipherText: String): String {
        return try {
            val decoded = Base64.getDecoder().decode(cipherText)

            if (decoded.size < 16) {
                throw InternalServerException(cause = IllegalArgumentException("잘못된 암호문 형식"))
            }

            val ivBytes = decoded.copyOfRange(0, 16)
            val contentBytes = decoded.copyOfRange(16, decoded.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ivBytes))

            String(cipher.doFinal(contentBytes), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw InternalServerException(e)
        }
    }
}