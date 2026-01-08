package jnu.econovation.isekai.common.constant

enum class SortField(
        val requestField: String,
        val dbField: String
) {
    CREATED_AT("created_at", "createdAt");

    companion object {
        fun fromRequestKey(requestKey: String): SortField? {
            return entries.find { it.requestField == requestKey }
        }
    }
}