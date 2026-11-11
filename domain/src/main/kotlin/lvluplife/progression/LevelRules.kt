package lvluplife.progression

import lvluplife.model.Attribute
import lvluplife.model.AttributeBalance
import lvluplife.model.GeneralBalance

/** Compile-only T026 transition contract; T032 reuses the model's canonical normalization thresholds. */
object LevelRules {
    fun attribute(current: AttributeBalance, awardedXp: Int): AttributeAdvance =
        throw NotImplementedError("Level rules are not implemented (T032)")
    fun attributes(current: Map<Attribute, AttributeBalance>, awards: Map<Attribute, Int>): AttributesAdvance =
        throw NotImplementedError("Level rules are not implemented (T032)")
    fun general(current: GeneralBalance, attributeLevelsGained: Int): GeneralBalance =
        throw NotImplementedError("Level rules are not implemented (T032)")
}

data class AttributeAdvance(val balance: AttributeBalance, val levelsGained: Int)
data class AttributesAdvance(val attributes: Map<Attribute, AttributeBalance>, val levelsGained: Int)
