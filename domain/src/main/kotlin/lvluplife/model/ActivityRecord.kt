package lvluplife.model

import java.time.Instant
import java.time.LocalDate

@JvmInline value class RecordId(val value: String) { init { requireDocumentId(value) } }

/** Creation/identity cannot be supplied again by correction. No public copy or mutable properties. */
class ActivityRecord private constructor(val id: RecordId, val cycleId: CycleId, val definition: ActivityDefinition,
    val activityDate: LocalDate, val durationMinutes: Int?, val createdAt: Instant, val updatedAt: Instant,
    val version: Long, val deletedAt: Instant?) {
    init {
        require(version > 0) { "Invalid record version" }
        require(!updatedAt.isBefore(createdAt)) { "Invalid record chronology" }
        require(deletedAt == null || deletedAt == updatedAt) { "Invalid tombstone chronology" }
        when (definition.mode) {
            ActivityMode.DURATION -> require(durationMinutes != null && durationMinutes in 1..1440) { "Invalid normalized duration" }
            ActivityMode.OCCURRENCE -> require(durationMinutes == null) { "Occurrence cannot contain duration" }
        }
    }
    val activityId: ActivityId get() = definition.id
    companion object {
        fun create(id: RecordId, cycleId: CycleId, definition: ActivityDefinition, activityDate: LocalDate,
            durationMinutes: Int?, createdAt: Instant): ActivityRecord =
            ActivityRecord(id, cycleId, definition, activityDate, durationMinutes, createdAt, createdAt, 1, null)
        fun restore(id: RecordId, cycleId: CycleId, definition: ActivityDefinition, activityDate: LocalDate,
            durationMinutes: Int?, createdAt: Instant, updatedAt: Instant, version: Long, deletedAt: Instant?): ActivityRecord =
            ActivityRecord(id, cycleId, definition, activityDate, durationMinutes, createdAt, updatedAt, version, deletedAt)
    }
    fun correct(activityDate: LocalDate, definition: ActivityDefinition = this.definition,
        durationMinutes: Int? = this.durationMinutes, now: Instant): ActivityRecord {
        check(deletedAt == null) { "Record is deleted" }
        require(definition.mode == this.definition.mode) { "Correction cannot change modality" }
        if (this.definition.mode == ActivityMode.OCCURRENCE) require(definition == this.definition) { "Meal correction only changes date" }
        require(!now.isBefore(updatedAt)) { "Invalid correction chronology" }
        return ActivityRecord(id, cycleId, definition, activityDate, durationMinutes, createdAt, now, Math.addExact(version, 1), null)
    }
    fun markDeleted(now: Instant): ActivityRecord {
        check(deletedAt == null) { "Record is deleted" }
        require(!now.isBefore(updatedAt)) { "Invalid deletion chronology" }
        return ActivityRecord(id, cycleId, definition, activityDate, durationMinutes, createdAt, now, Math.addExact(version, 1), now)
    }
}
