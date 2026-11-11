package lvluplife.progression

import java.time.LocalDate
import lvluplife.model.InactivityBalance

/** Compile-only T026 contract; T033 implements one date's calculation after its XP was applied.
 * The caller supplies the activity date, current UTC-3 business date and effective XP for that date.
 * Ordered cycle replay, ownership and storage belong to their own layers.
 */
object InactivityRules {
    fun closeDate(current: InactivityBalance, date: LocalDate, businessDate: LocalDate,
        effectiveXp: Int): InactivityBalance =
        throw NotImplementedError("Inactivity rules are not implemented (T033)")
}
