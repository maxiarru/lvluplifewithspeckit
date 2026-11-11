package lvluplife.progression

import java.time.LocalDate
import lvluplife.model.Attribute
import lvluplife.model.CycleId

/** Compile-time calculation contract for the T025 red fixtures. Implementation belongs to T031.
 * awardedToday is trusted effective XP already granted for this activity date/current cycle.
 * These functions neither store records nor decide concurrent transaction ordering or inactivity.
 */
object ExperienceRules {
    fun duration(minutes: Int, primary: Attribute, secondary: Attribute? = null,
        awardedToday: Map<Attribute, Int> = emptyMap(), atLevelCap: Set<Attribute> = emptySet()): Map<Attribute, Int> =
        throw NotImplementedError("Experience rules are not implemented (T031)")

    fun meal(mealsBefore: Int, awardedToday: Int = 0, atLevelCap: Boolean = false): Int =
        throw NotImplementedError("Experience rules are not implemented (T031)")

    /** Total pre-level XP for this date/cycle's healthy meal count, including an empty day. */
    fun meals(count: Int): Int = throw NotImplementedError("Experience rules are not implemented (T031)")

    fun sleep(minutes: Int, cycleId: CycleId, date: LocalDate, previous: SleepRemainder? = null,
        awardedToday: Int = 0, atLevelCap: Boolean = false): SleepContribution =
        throw NotImplementedError("Experience rules are not implemented (T031)")
}

data class SleepRemainder(val cycleId: CycleId, val date: LocalDate, val minutes: Int)
data class SleepContribution(val xp: Int, val remainder: SleepRemainder)
