package lvluplife.progression

import java.time.LocalDate
import lvluplife.model.InactivityBalance
import lvluplife.model.AttributeBalance

/** One date's inactivity calculation after its XP was applied.
 * The caller supplies the activity date, current UTC-3 business date and effective XP for that date.
 * Ordered cycle replay, ownership and storage belong to their own layers.
 */
object InactivityRules {
    private const val GRACE_DATES = 6
    private const val XP_PENALTY = 5
    fun closeDate(current: InactivityBalance, date: LocalDate, businessDate: LocalDate,
        effectiveXp: Int): InactivityBalance {
        require(effectiveXp >= 0) { "Invalid effective XP" }
        require(!date.isAfter(businessDate)) { "Cannot close a future activity date" }
        // Effective XP is already included in the balance; count the date as active without awarding it again.
        if (effectiveXp > 0) return InactivityBalance(current.attribute, 0)
        if (date == businessDate) return current
        val inactiveDates = Math.addExact(current.inactiveDates, 1)
        val attribute = if (inactiveDates > GRACE_DATES)
            AttributeBalance(current.attribute.level, (current.attribute.xp - XP_PENALTY).coerceAtLeast(0))
        else current.attribute
        return InactivityBalance(attribute, inactiveDates)
    }
}
