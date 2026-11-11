package lvluplife.progression

import java.time.Instant
import java.time.LocalDate
import lvluplife.model.Attribute
import lvluplife.model.Cycle
import lvluplife.model.CycleId

/** T027 compile-only replay contract; T036 implements calculation after T029/T030 model integration.
 * Entries contain server-resolved definitions, never client-supplied XP. No persistence is represented here.
 */
object ReplayEngine {
    fun rebuild(cycle: Cycle, records: List<ReplayRecord>, now: Instant): ReplayResult =
        throw NotImplementedError("Replay is not implemented (T036)")
}

enum class ReplayKind { DURATION, SLEEP, MEAL }
data class ReplayRecord(val id: String, val cycleId: CycleId, val activityDate: LocalDate, val createdAt: Instant,
    val kind: ReplayKind, val primary: Attribute, val secondary: Attribute? = null, val durationMinutes: Int? = null,
    val deletedAt: Instant? = null)
data class ReplayResult(val businessDate: LocalDate, val attributes: Map<Attribute, InactivityBalance>,
    val general: GeneralBalance, val appearance: ClassAppearance, val orderedRecordIds: List<String>,
    val recordXp: Map<String, Map<Attribute, Int>>, val durationByDate: Map<LocalDate, Int>)
