package lvluplife.progression

import java.time.Instant
import kotlin.test.*
import lvluplife.model.*
import lvluplife.model.Attribute.*

/** Aggregate arithmetic fixtures, not a claim that one record can bypass daily XP caps. */
class LevelRulesTest {
    private fun initial() = Attribute.entries.associateWith { AttributeBalance(1, 0) }

    @Test fun negativeXpIsRejectedEvenAtMaximumLevel() {
        for (level in listOf(1, 50)) assertFailsWith<IllegalArgumentException> { LevelRules.attribute(AttributeBalance(level, 0), -1) }
    }
    @Test fun aggregateXpCanCrossMultipleThresholdsAndKeepExactSurplus() {
        assertEquals(AttributeAdvance(AttributeBalance(3, 0), 2), LevelRules.attribute(AttributeBalance(1, 0), 90))
        assertEquals(AttributeAdvance(AttributeBalance(3, 1), 2), LevelRules.attribute(AttributeBalance(1, 29), 62))
    }
    @Test fun aggregateAttributeXpHasAnExactMaximumBoundaryAndCannotOverflow() {
        assertEquals(AttributeAdvance(AttributeBalance(49, 1469), 48), LevelRules.attribute(AttributeBalance(1, 0), 36749))
        for (xp in listOf(36750, 36751, Int.MAX_VALUE))
            assertEquals(AttributeAdvance(AttributeBalance(50, 0), 49), LevelRules.attribute(AttributeBalance(1, 0), xp))
    }
    @Test fun negativeGeneralGainsAreRejectedEvenAtMaximumLevel() {
        for (level in listOf(1, 50)) assertFailsWith<IllegalArgumentException> { LevelRules.general(GeneralBalance(level, 0), -1) }
    }
    @Test fun aggregateGeneralGainsConsumeAllBandsAndDiscardSurplusAtFifty() {
        assertEquals(GeneralBalance(49, 4), LevelRules.general(GeneralBalance(1, 0), 217))
        for (gains in listOf(218, 219, Int.MAX_VALUE))
            assertEquals(GeneralBalance(50, 0), LevelRules.general(GeneralBalance(1, 0), gains))
    }
    @Test fun batchRequiresAllSixCurrentAttributeBalances() {
        for (missing in Attribute.entries) assertFailsWith<IllegalArgumentException> { LevelRules.attributes(initial() - missing, emptyMap()) }
        assertFailsWith<IllegalArgumentException> { LevelRules.attributes(emptyMap(), emptyMap()) }
    }
    @Test fun batchRejectsNegativeAwardsWithoutMutatingItsInput() {
        val before = initial().toMutableMap()
        assertFailsWith<IllegalArgumentException> { LevelRules.attributes(before, mapOf(STR to -1)) }
        assertEquals(initial(), before)
    }
    @Test fun batchResultIsImmutableAndIndependentOfMutableInputMaps() {
        val before = (initial() + mapOf(STR to AttributeBalance(1, 29), DEX to AttributeBalance(1, 29))).toMutableMap()
        val awards = mutableMapOf(STR to 4, DEX to 2)
        val result = LevelRules.attributes(before, awards)
        assertEquals(AttributeBalance(1, 29), before.getValue(STR)); assertEquals(mapOf(STR to 4, DEX to 2), awards)
        before.clear(); awards.clear()
        assertEquals(initial() + mapOf(STR to AttributeBalance(2, 3), DEX to AttributeBalance(2, 1)), result.attributes)
        assertEquals(2, result.levelsGained)
        assertFailsWith<UnsupportedOperationException> { (result.attributes as MutableMap).clear() }
    }
    @Test fun alreadyMaximumAttributeDoesNotReportAnotherGain() {
        val before = initial() + (STR to AttributeBalance(50, 0))
        val result = LevelRules.attributes(before, mapOf(STR to 48, DEX to 1))
        assertEquals(0, result.levelsGained); assertEquals(AttributeBalance(50, 0), result.attributes.getValue(STR))
        assertEquals(AttributeBalance(1, 1), result.attributes.getValue(DEX))
    }
    @Test fun actualBatchGainsProduceACoherentGeneralProjectionWithoutGeneralXp() {
        val before = initial() + mapOf(STR to AttributeBalance(1, 29), DEX to AttributeBalance(1, 29))
        val changed = LevelRules.attributes(before, mapOf(STR to 4, DEX to 2))
        val general = LevelRules.general(GeneralBalance(1, 0), changed.levelsGained)
        assertEquals(GeneralBalance(2, 1), general)
        val now = Instant.parse("2026-11-11T15:00:00Z")
        val cycle = Cycle(Uid("synthetic-owner"), CycleId("synthetic-cycle"), now, RulesVersion.CURRENT)
        val projection = Progression.derived(cycle, Revision(2), now,
            changed.attributes.mapValues { InactivityBalance(it.value, 0) }, general, "maestro-de-armas", 1)
        assertEquals(changed.attributes, projection.attributes.mapValues { it.value.attribute }); assertEquals(general, projection.general)
    }
}
