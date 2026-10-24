package bg.butcher.pda.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.text.DateFormat
import java.util.Date
import kotlin.time.Clock

/** A calendar day, so it doesn't drift around daylight-saving changes. */
fun dateFromToday(days: Int): LocalDate =
    Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(days, DateTimeUnit.DAY)

fun formatDate(date: LocalDate, style: Int = DateFormat.FULL): String {
    val millis = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    return DateFormat.getDateInstance(style).format(Date(millis))
}
