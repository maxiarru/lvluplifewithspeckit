package lvluplife.record

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import lvluplife.model.*
import lvluplife.progression.RecordValidation

enum class CorrectionReason { CYCLE_CLOSED, RECORD_DELETED, EDIT_WINDOW_EXPIRED, INVALID_INPUT, DATE_NOT_ALLOWED, DAILY_DURATION_EXCEEDED }
class CorrectionRejected(val reason: CorrectionReason) : IllegalArgumentException(reason.name)

/** Pure admission over the authoritative cycle, record snapshot and validation instant. */
object CorrectionRules {
    fun edit(record: ActivityRecord, currentCycle: Cycle, date: LocalDate,
        definition: ActivityDefinition = record.definition, hours: BigDecimal? = null, minutes: BigDecimal? = null,
        records: List<ActivityRecord>, now: Instant): ActivityRecord {
        admissible(record, currentCycle, now)
        checkInput(definition.mode == record.definition.mode)
        try { RecordValidation.activityDate(date, now) }
        catch (_: IllegalArgumentException) { throw CorrectionRejected(CorrectionReason.DATE_NOT_ALLOWED) }
        val duration = when (record.definition.mode) {
            ActivityMode.OCCURRENCE -> {
                checkInput(definition == record.definition && hours == null && minutes == null)
                null
            }
            ActivityMode.DURATION -> {
                checkInput(hours != null && minutes != null)
                try { RecordValidation.durationMinutes(hours!!, minutes!!, currentCycle.id, date, emptyList()) }
                catch (_: IllegalArgumentException) { throw CorrectionRejected(CorrectionReason.INVALID_INPUT) }
                try { RecordValidation.durationMinutes(hours, minutes, currentCycle.id, date, records, excludingId = record.id) }
                catch (_: IllegalArgumentException) { throw CorrectionRejected(CorrectionReason.DAILY_DURATION_EXCEEDED) }
            }
        }
        return record.correct(date, definition, duration, now)
    }
    fun delete(record: ActivityRecord, currentCycle: Cycle, now: Instant): ActivityRecord {
        admissible(record, currentCycle, now)
        return record.markDeleted(now)
    }
    private fun admissible(record: ActivityRecord, currentCycle: Cycle, now: Instant) {
        if (!currentCycle.isOpen || record.cycleId != currentCycle.id) throw CorrectionRejected(CorrectionReason.CYCLE_CLOSED)
        if (record.deletedAt != null) throw CorrectionRejected(CorrectionReason.RECORD_DELETED)
        checkInput(!record.createdAt.isBefore(currentCycle.startedAt) && !now.isBefore(record.updatedAt))
        val age = Duration.between(record.createdAt, now)
        if (age.isNegative || age >= Duration.ofSeconds(300)) throw CorrectionRejected(CorrectionReason.EDIT_WINDOW_EXPIRED)
    }
    private fun checkInput(condition: Boolean) { if (!condition) throw CorrectionRejected(CorrectionReason.INVALID_INPUT) }
}
