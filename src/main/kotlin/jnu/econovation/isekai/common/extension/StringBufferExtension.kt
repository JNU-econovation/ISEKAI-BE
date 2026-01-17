package jnu.econovation.isekai.common.extension

fun StringBuffer.clear(): StringBuffer {
    this.setLength(0)
    return this
}