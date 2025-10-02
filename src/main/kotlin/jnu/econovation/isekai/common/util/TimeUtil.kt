package jnu.econovation.isekai.common.util


import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TimeUtil {
    private val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
    private val SIMPLE_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun convertInstantToZonedDateTime(
        instant: Instant, zoneId: ZoneId = SEOUL_ZONE_ID
    ): ZonedDateTime = ZonedDateTime.ofInstant(instant, zoneId)


    fun formatToSimpleString(dateTime: ZonedDateTime): String {
        return dateTime.format(SIMPLE_DATE_FORMATTER)
    }

    fun formatToSimpleString(instant: Instant): String {
        val zonedDateTime = convertInstantToZonedDateTime(instant)
        return formatToSimpleString(zonedDateTime)
    }
}