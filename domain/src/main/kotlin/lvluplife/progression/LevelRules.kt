package lvluplife.progression

import lvluplife.model.Attribute

/** Compile-only contract for T026. Actual rules belong to T032; no level math is implemented. */
object LevelRules {
    fun attribute(current: AttributeBalance, awardedXp: Int): AttributeAdvance =
        throw NotImplementedError("Level rules are not implemented (T032)")
    fun attributes(current: Map<Attribute, AttributeBalance>, awards: Map<Attribute, Int>): AttributesAdvance =
        throw NotImplementedError("Level rules are not implemented (T032)")
    fun general(current: GeneralBalance, attributeLevelsGained: Int): GeneralBalance =
        throw NotImplementedError("Level rules are not implemented (T032)")
}

data class AttributeBalance(val level: Int, val xp: Int)
data class AttributeAdvance(val balance: AttributeBalance, val levelsGained: Int)
data class AttributesAdvance(val attributes: Map<Attribute, AttributeBalance>, val levelsGained: Int)
data class GeneralBalance(val level: Int, val remaining: Int)
