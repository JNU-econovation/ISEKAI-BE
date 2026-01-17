package jnu.econovation.isekai.common.util

import kotlin.math.sqrt

object AudioUtil {
    fun isSilence(chunk: ByteArray, threshold: Int = 500): Boolean {
        if (chunk.isEmpty()) return true

        var sum = 0.0
        for (i in chunk.indices step 2) {
            if (i + 1 >= chunk.size) break
            val sample = (chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xFF)
            sum += sample * sample
        }
        
        val rms = sqrt(sum / (chunk.size / 2))
        return rms < threshold
    }
}