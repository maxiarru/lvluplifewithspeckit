package lvluplife.progression

import java.time.LocalDate
import java.util.Collections
import lvluplife.model.Attribute
import lvluplife.model.Attribute.*
import lvluplife.model.CycleId

/** Pure canonical XP calculation.
 * awardedToday is trusted effective XP already granted for this activity date/current cycle.
 * These functions neither store records nor decide concurrent transaction ordering or inactivity.
 */
object ExperienceRules {
    private const val SCORED_MEALS = 4
    val dailyLimits: Map<Attribute, Int> = Collections.unmodifiableMap(linkedMapOf(
        STR to 48, DEX to 48, INT to 48, WIT to 48, MEN to 30, CON to 28,
    ))
    fun duration(minutes: Int, primary: Attribute, secondary: Attribute? = null,
        awardedToday: Map<Attribute, Int> = emptyMap(), atLevelCap: Set<Attribute> = emptySet()): Map<Attribute, Int> {
        requireDuration(minutes)
        require(primary != CON && secondary != CON && secondary != primary) { "Invalid duration attributes" }
        awardedToday.forEach { (attribute, awarded) -> requireBudget(attribute, awarded) }
        val total = minutes / 5
        // floor(total/3) for secondary leaves ceil(2*total/3) for primary, before independent caps.
        val secondaryXp = if (secondary == null) 0 else total / 3
        val result = linkedMapOf(primary to effective(primary, total - secondaryXp, awardedToday[primary] ?: 0, primary in atLevelCap))
        if (secondary != null) result[secondary] = effective(secondary, secondaryXp, awardedToday[secondary] ?: 0, secondary in atLevelCap)
        return Collections.unmodifiableMap(result)
    }

    fun meal(mealsBefore: Int, awardedToday: Int = 0, atLevelCap: Boolean = false): Int {
        require(mealsBefore >= 0) { "Invalid meal count" }
        requireBudget(CON, awardedToday)
        // Saturate the count before adding one, so even a very large valid count cannot overflow.
        val raw = meals(mealsBefore.coerceAtMost(SCORED_MEALS) + 1) - meals(mealsBefore)
        return effective(CON, raw, awardedToday, atLevelCap)
    }

    /** Total pre-level XP for this date/cycle's healthy meal count, including an empty day. */
    fun meals(count: Int): Int {
        require(count >= 0) { "Invalid meal count" }
        return count.coerceAtMost(SCORED_MEALS) * 7
    }

    fun sleep(minutes: Int, cycleId: CycleId, date: LocalDate, previous: SleepRemainder? = null,
        awardedToday: Int = 0, atLevelCap: Boolean = false): SleepContribution {
        requireDuration(minutes)
        requireBudget(MEN, awardedToday)
        val pending = previous?.takeIf { it.cycleId == cycleId && it.date == date }?.minutes ?: 0
        val total = pending + minutes
        return SleepContribution(effective(MEN, (total / 60) * 3, awardedToday, atLevelCap),
            SleepRemainder(cycleId, date, total % 60))
    }

    private fun requireDuration(minutes: Int) {
        require(minutes in 1..1440) { "Invalid normalized duration" }
    }
    private fun requireBudget(attribute: Attribute, awarded: Int) {
        require(awarded in 0..dailyLimits.getValue(attribute)) { "Invalid awarded XP budget" }
    }
    private fun effective(attribute: Attribute, calculated: Int, awarded: Int, atLevelCap: Boolean): Int =
        if (atLevelCap) 0 else minOf(calculated, dailyLimits.getValue(attribute) - awarded)
}

data class SleepRemainder(val cycleId: CycleId, val date: LocalDate, val minutes: Int) {
    init { require(minutes in 0..59) { "Invalid pending sleep minutes" } }
}
data class SleepContribution(val xp: Int, val remainder: SleepRemainder)
