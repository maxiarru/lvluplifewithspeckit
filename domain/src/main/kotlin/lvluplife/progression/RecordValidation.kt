package lvluplife.progression

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import lvluplife.model.BusinessTime
import lvluplife.model.CycleId
import lvluplife.model.ActivityRecord
import lvluplife.model.RecordId

/** Pure admission checks over an authoritative snapshot; no clock or IO is consulted here.
 * The transaction must revalidate the snapshot revision and validation instant before committing.
 * Decimal inputs retain fractions/overflow until checked. Duration is returned only after validation.
 */
object RecordValidation {
    fun activityDate(date: LocalDate, now: Instant) {
        val age = ChronoUnit.DAYS.between(date, BusinessTime.date(now))
        require(age in 0L..2L) { "Activity date must be today, yesterday or the day before yesterday" }
    }
    fun durationMinutes(hours: BigDecimal, minutes: BigDecimal, cycleId: CycleId, date: LocalDate,
        records: List<ActivityRecord>, excludingId: RecordId? = null): Int {
        require(hours.signum() >= 0 && minutes.signum() >= 0) { "Duration components cannot be negative" }
        require(hours <= BigDecimal(24) && minutes <= BigDecimal(59)) { "Duration components exceed their bounds" }
        require(hours.stripTrailingZeros().scale() <= 0 && minutes.stripTrailingZeros().scale() <= 0) {
            "Duration components must be integers"
        }
        // Bounds and integrality are checked before conversion or multiplication.
        val total = hours.intValueExact() * 60 + minutes.intValueExact()
        require(total in 1..1440) { "Duration must be between 1 and 1440 minutes" }
        var remaining = 1440 - total
        for (record in records) {
            if (record.cycleId != cycleId || record.activityDate != date ||
                record.deletedAt != null || record.id == excludingId) continue
            remaining -= record.durationMinutes ?: 0
            require(remaining >= 0) { "Daily duration budget exceeded" }
        }
        return total
    }
}
