package bg.butcher.pda.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class ExpiryStatus { EXPIRED, TODAY, SOON, OK, UNKNOWN }

/** "Soon" is within [soonDays] after [today]; expiring today still counts as not expired. */
fun expiryStatus(expiry: LocalDate?, today: LocalDate, soonDays: Int = 1): ExpiryStatus = when {
    expiry == null -> ExpiryStatus.UNKNOWN
    expiry < today -> ExpiryStatus.EXPIRED
    expiry == today -> ExpiryStatus.TODAY
    expiry <= today.plus(soonDays, DateTimeUnit.DAY) -> ExpiryStatus.SOON
    else -> ExpiryStatus.OK
}
