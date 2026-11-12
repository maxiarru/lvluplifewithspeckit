package lvluplife.progression

import java.time.Instant
import lvluplife.model.*

/** Pure transition only; persistence commits these values atomically without deleting historical collections. */
data class ResetTransition(val account: Account, val closedCycle: Cycle, val newCycle: Cycle, val progression: Progression)

object ResetRules {
    fun reset(account: Account, currentCycle: Cycle, newCycleId: CycleId, now: Instant, confirmed: Boolean): ResetTransition {
        require(confirmed) { "Explicit reset confirmation is required" }
        require(account.uid == currentCycle.uid) { "Cycle owner does not match account" }
        require(account.currentCycleId == currentCycle.id && currentCycle.isOpen) { "Expected current open cycle" }
        require(newCycleId != currentCycle.id) { "Reset requires a distinct cycle identity" }
        require(currentCycle.rulesVersion == RulesVersion.CURRENT) { "Unsupported progression rules" }
        require(!now.isBefore(currentCycle.startedAt) && !now.isBefore(account.createdAt)) { "Reset precedes creation" }

        val revision = account.revision.next()
        val closed = currentCycle.copy(closedAt = now)
        val fresh = Cycle(account.uid, newCycleId, now, currentCycle.rulesVersion)
        val updated = account.copy(currentCycleId = newCycleId, revision = revision)
        return ResetTransition(updated, closed, fresh, Progression.initial(fresh, revision))
    }
}
