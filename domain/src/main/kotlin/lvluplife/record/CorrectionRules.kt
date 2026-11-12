package lvluplife.record

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import lvluplife.model.*
import lvluplife.progression.RecordValidation

/** Pure admission over the authoritative cycle, record snapshot and validation instant. */
object CorrectionRules {
    fun edit(record: ActivityRecord, currentCycle: Cycle, date: LocalDate,
        definition: ActivityDefinition = record.definition, hours: BigDecimal? = null, minutes: BigDecimal? = null,
        records: List<ActivityRecord>, now: Instant): ActivityRecord {
        admissible(record, currentCycle, now)
        require(definition.mode == record.definition.mode) { "Correction cannot change modality" }
        RecordValidation.activityDate(date, now)
        val duration = when (record.definition.mode) {
            ActivityMode.OCCURRENCE -> {
                require(definition == record.definition && hours == null && minutes == null) { "Meal correction only changes date" }
                null
            }
            ActivityMode.DURATION -> RecordValidation.durationMinutes(requireNotNull(hours), requireNotNull(minutes),
                currentCycle.id, date, records, excludingId = record.id)
        }
        return record.correct(date, definition, duration, now)
    }

    fun delete(record: ActivityRecord, currentCycle: Cycle, now: Instant): ActivityRecord {
        admissible(record, currentCycle, now)
        return record.markDeleted(now)
    }

    private fun admissible(record: ActivityRecord, currentCycle: Cycle, now: Instant) {
        require(currentCycle.isOpen && record.cycleId == currentCycle.id) { "Correction requires the current open cycle" }
        require(record.deletedAt == null) { "Record is deleted" }
        require(!record.createdAt.isBefore(currentCycle.startedAt) && !now.isBefore(record.updatedAt)) { "Invalid correction chronology" }
        val age = Duration.between(record.createdAt, now)
        require(!age.isNegative && age < Duration.ofSeconds(300)) { "Correction window expired" }
    }
}
