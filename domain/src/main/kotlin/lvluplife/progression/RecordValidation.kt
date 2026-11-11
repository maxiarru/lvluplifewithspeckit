package lvluplife.progression

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import lvluplife.model.CycleId

/** T027 compile-only validation contract. T035 supplies all validation, no clock/IO is consulted here.
 * Decimal inputs retain fractions/overflow until checked. Duration is returned only after validation.
 */
object RecordValidation {
    fun activityDate(date: LocalDate, now: Instant): Unit =
        throw NotImplementedError("Record validation is not implemented (T035)")
    fun durationMinutes(hours: BigDecimal, minutes: BigDecimal, cycleId: CycleId, date: LocalDate,
        records: List<ReplayRecord>, excludingId: String? = null): Int =
        throw NotImplementedError("Record validation is not implemented (T035)")
}
