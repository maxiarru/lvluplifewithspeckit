package lvluplife.record

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import lvluplife.model.*

/** Compile-only contract for T053; authoritative admission is implemented in T056. */
object CorrectionRules {
    fun edit(record: ActivityRecord, currentCycle: Cycle, date: LocalDate,
        definition: ActivityDefinition = record.definition, hours: BigDecimal? = null, minutes: BigDecimal? = null,
        records: List<ActivityRecord>, now: Instant): ActivityRecord =
        throw NotImplementedError("Correction admission is not implemented (T056)")
    fun delete(record: ActivityRecord, currentCycle: Cycle, now: Instant): ActivityRecord =
        throw NotImplementedError("Deletion admission is not implemented (T056)")
}
