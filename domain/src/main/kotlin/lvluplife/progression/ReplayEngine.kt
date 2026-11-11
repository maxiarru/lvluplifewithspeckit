package lvluplife.progression

import java.time.Instant
import java.time.LocalDate
import lvluplife.model.Attribute
import lvluplife.model.ActivityRecord
import lvluplife.model.Cycle
import lvluplife.model.InactivityBalance
import lvluplife.model.GeneralBalance

/** T027 compile-only replay contract, integrated with ActivityRecord in T029; calculation belongs to T036.
 * Records contain server-resolved definitions, never client-supplied XP. No persistence is represented here.
 */
object ReplayEngine {
    fun rebuild(cycle: Cycle, records: List<ActivityRecord>, now: Instant): ReplayResult =
        throw NotImplementedError("Replay is not implemented (T036)")
}

data class ReplayResult(val businessDate: LocalDate, val attributes: Map<Attribute, InactivityBalance>,
    val general: GeneralBalance, val appearance: ClassAppearance, val orderedRecordIds: List<String>,
    val recordXp: Map<String, Map<Attribute, Int>>, val durationByDate: Map<LocalDate, Int>)
