package lvluplife.progression

import java.time.Instant
import lvluplife.model.*

/** Compile-only transition contract for T071. Pure implementation belongs to T074. */
data class ResetTransition(val account: Account, val closedCycle: Cycle, val newCycle: Cycle, val progression: Progression)

object ResetRules {
    @Suppress("UNUSED_PARAMETER")
    fun reset(account: Account, currentCycle: Cycle, newCycleId: CycleId, now: Instant, confirmed: Boolean): ResetTransition =
        TODO("T074: confirmed pure reset transition")
}
