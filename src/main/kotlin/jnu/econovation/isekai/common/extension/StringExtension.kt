@file:JvmName("HashUtils")
package jnu.econovation.isekai.common.extension

import java.security.MessageDigest

fun String.toHash(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}