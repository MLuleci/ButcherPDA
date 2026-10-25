package bg.butcher.pda.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format.char
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.text.DateFormat
import java.util.Date
import kotlin.time.Clock

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** A calendar day, so it doesn't drift around daylight-saving changes. */
fun dateFromToday(days: Int): LocalDate = today().plus(days, DateTimeUnit.DAY)

// The usual format in Bulgaria, e.g. 30.10.2026
private val SHORT_DATE = LocalDate.Format {
    day()
    char('.')
    monthNumber()
    char('.')
    year()
}

fun shortDate(date: LocalDate): String = SHORT_DATE.format(date)

fun formatDate(date: LocalDate, style: Int = DateFormat.FULL): String {
    val millis = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    return DateFormat.getDateInstance(style).format(Date(millis))
}
