package jnu.econovation.isekai.common.security.util

import jnu.econovation.isekai.common.exception.server.InternalServerException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
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
    private val iv: IvParameterSpec

    init {
        if (key.toByteArray().size != 32)
            throw InternalServerException(IllegalArgumentException("AES-256 키는 32 bytes 길이여야 합니다."))

        secretKey = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        iv = IvParameterSpec(key.toByteArray(StandardCharsets.UTF_8), 0, 16)
    }

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            Base64.getEncoder().encodeToString(encrypted)
        } catch (e: Exception) {
            throw InternalServerException(e)
        }
    }

    fun decrypt(cipherText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
            val decoded = Base64.getDecoder().decode(cipherText)
            String(cipher.doFinal(decoded), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw InternalServerException(e)
        }
    }
}