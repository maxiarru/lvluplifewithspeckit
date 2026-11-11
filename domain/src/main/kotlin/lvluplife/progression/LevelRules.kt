package lvluplife.progression

import lvluplife.model.Attribute
import lvluplife.model.AttributeBalance
import lvluplife.model.GeneralBalance
import lvluplife.model.ProgressionThresholds
import java.util.Collections
import java.util.EnumMap

/** Pure level transitions over trusted XP awards; budgets/date/cycle are handled before this layer. */
object LevelRules {
    fun attribute(current: AttributeBalance, awardedXp: Int): AttributeAdvance {
        require(awardedXp >= 0) { "Invalid XP award" }
        var level = current.level
        var xp = current.xp.toLong() + awardedXp.toLong()
        while (true) {
            val required = ProgressionThresholds.attribute(level)
                ?: return AttributeAdvance(AttributeBalance(level, 0), level - current.level)
            if (xp < required) return AttributeAdvance(AttributeBalance(level, xp.toInt()), level - current.level)
            xp -= required
            level++
        }
    }
    fun attributes(current: Map<Attribute, AttributeBalance>, awards: Map<Attribute, Int>): AttributesAdvance {
        require(current.keys == Attribute.entries.toSet()) { "Level calculation requires all six attributes" }
        require(awards.values.all { it >= 0 }) { "Invalid XP award" }
        val result = EnumMap<Attribute, AttributeBalance>(Attribute::class.java)
        var gained = 0
        for (id in Attribute.entries) {
            val advanced = attribute(current.getValue(id), awards[id] ?: 0)
            result[id] = advanced.balance
            gained += advanced.levelsGained
        }
        return AttributesAdvance(Collections.unmodifiableMap(result), gained)
    }
    fun general(current: GeneralBalance, attributeLevelsGained: Int): GeneralBalance {
        require(attributeLevelsGained >= 0) { "Invalid attribute level gains" }
        var level = current.level
        var remaining = current.remaining.toLong() + attributeLevelsGained.toLong()
        while (true) {
            val required = ProgressionThresholds.general(level) ?: return GeneralBalance(level, 0)
            if (remaining < required) return GeneralBalance(level, remaining.toInt())
            remaining -= required
            level++
        }
    }
}

data class AttributeAdvance(val balance: AttributeBalance, val levelsGained: Int)
data class AttributesAdvance(val attributes: Map<Attribute, AttributeBalance>, val levelsGained: Int)
