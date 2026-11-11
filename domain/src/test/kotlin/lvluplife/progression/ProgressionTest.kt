package lvluplife.progression

import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.Attribute
import lvluplife.model.Attribute.*

/** Spec-derived numeric/metadata fixtures only; no implementations of level/inactivity/class formulas. */
class ProgressionTest {
    private val firstDate = LocalDate.parse("2026-11-01")
    private val businessDate = LocalDate.parse("2026-11-20")
    private fun levels(vararg overrides: Pair<Attribute, Int>) = Attribute.entries.associateWith { 1 } + overrides.toMap()
    private fun inactive(xp: Int, days: Int, level: Int = 1) = InactivityBalance(AttributeBalance(level, xp), days)
    private fun close(state: InactivityBalance, offset: Long = 0, xp: Int = 0) =
        InactivityRules.closeDate(state, firstDate.plusDays(offset), businessDate, xp)
    private val pure = listOf(STR to "Guerrero", DEX to "Explorador", CON to "Guardián", INT to "Sabio", WIT to "Monje", MEN to "Místico")
    private val pairs = listOf(
        Triple(STR, DEX, "Maestro de Armas"), Triple(STR, CON, "Paladín"), Triple(STR, INT, "Guerrero Arcano"),
        Triple(STR, WIT, "Luchador Monje"), Triple(STR, MEN, "Guerrero Místico"), Triple(DEX, CON, "Ágil Guardián"),
        Triple(DEX, INT, "Artífice"), Triple(DEX, WIT, "Ninja"), Triple(DEX, MEN, "Explorador de Sueños"),
        Triple(CON, INT, "Guardián de la Sabiduría"), Triple(CON, WIT, "Monje Guardián"), Triple(CON, MEN, "Druida"),
        Triple(INT, WIT, "Ilusionista"), Triple(INT, MEN, "Oráculo"), Triple(WIT, MEN, "Monje Espiritual"))
    private val multiple = listOf("Aventurero Versátil", "Maestro del Equilibrio", "Héroe Ascendente", "Héroe Integral")
    private val classNames get() = listOf("Novato") + pure.map { it.second } + pairs.map { it.third } + multiple

    // AC-36–39,82: normalized attribute progress, independent awards and cap at 50.
    @Test fun simpleAwardChangesOnlyItsAttribute() {
        val before = mapOf(STR to AttributeBalance(1, 10), DEX to AttributeBalance(2, 11), CON to AttributeBalance(3, 12),
            INT to AttributeBalance(4, 13), WIT to AttributeBalance(5, 14), MEN to AttributeBalance(6, 15))
        val result = LevelRules.attributes(before, mapOf(STR to 6))
        assertEquals(before + (STR to AttributeBalance(1, 16)), result.attributes)
        assertEquals(0, result.levelsGained)
        assertEquals(AttributeBalance(1, 10), before.getValue(STR))
    }
    @Test fun literalAttributeThresholdsAdvanceExactlyOneLevel() {
        for ((level, threshold) in listOf(1 to 30, 2 to 60, 3 to 90, 10 to 300, 25 to 750, 49 to 1470)) {
            assertEquals(AttributeAdvance(AttributeBalance(level, threshold - 1), 0), LevelRules.attribute(AttributeBalance(level, threshold - 2), 1))
            assertEquals(AttributeAdvance(AttributeBalance(level + 1, 0), 1), LevelRules.attribute(AttributeBalance(level, threshold - 1), 1))
        }
    }
    @Test fun attributeSurplusIsPreservedBelowLevelFifty() {
        assertEquals(AttributeAdvance(AttributeBalance(2, 5), 1), LevelRules.attribute(AttributeBalance(1, 28), 7))
    }
    @Test fun levelFiftyDiscardsSurplusWhenReached() {
        assertEquals(AttributeAdvance(AttributeBalance(50, 0), 1), LevelRules.attribute(AttributeBalance(49, 1469), 6))
    }
    @Test fun levelFiftyNeverAdvancesAndKeepsZeroXp() {
        for (xp in listOf(0, 1, 6, 48))
            assertEquals(AttributeAdvance(AttributeBalance(50, 0), 0), LevelRules.attribute(AttributeBalance(50, 0), xp))
    }
    @Test fun zeroXpDoesNotCreateAttributeLevelGains() {
        assertEquals(AttributeAdvance(AttributeBalance(7, 19), 0), LevelRules.attribute(AttributeBalance(7, 19), 0))
    }
    @Test fun twoAttributesCrossingThresholdsReportTwoGainsWithoutChangingOtherFour() {
        val before = Attribute.entries.associateWith { AttributeBalance(1, 0) } +
            mapOf(STR to AttributeBalance(1, 29), DEX to AttributeBalance(1, 29))
        val result = LevelRules.attributes(before, mapOf(STR to 4, DEX to 2))
        assertEquals(before + mapOf(STR to AttributeBalance(2, 3), DEX to AttributeBalance(2, 1)), result.attributes)
        assertEquals(2, result.levelsGained)
    }

    // AC-40–44: general progress counts attribute level gains, never an independent XP pool.
    @Test fun firstAttributeGainAdvancesGeneralLevelOne() {
        assertEquals(GeneralBalance(2, 0), LevelRules.general(GeneralBalance(1, 0), 1))
    }
    @Test fun literalGeneralThresholdBoundariesConsumeOnlyRequiredGains() {
        val fixtures = listOf(Triple(1, 0, 2), Triple(2, 1, 3), Triple(4, 1, 5), Triple(5, 2, 6),
            Triple(8, 2, 9), Triple(9, 3, 10), Triple(14, 3, 15), Triple(15, 4, 16), Triple(49, 4, 50))
        for ((level, remaining, expected) in fixtures)
            assertEquals(GeneralBalance(expected, 0), LevelRules.general(GeneralBalance(level, remaining), 1))
    }
    @Test fun generalCounterBelowThresholdDoesNotAdvance() {
        for ((level, expectedRemaining) in listOf(2 to 1, 4 to 1, 5 to 2, 8 to 2, 9 to 3, 14 to 3, 15 to 4, 49 to 4))
            assertEquals(GeneralBalance(level, expectedRemaining), LevelRules.general(GeneralBalance(level, 0), expectedRemaining))
    }
    @Test fun twoAttributeGainsAtGeneralOnePreserveOneForNextTransition() {
        assertEquals(GeneralBalance(2, 1), LevelRules.general(GeneralBalance(1, 0), 2))
    }
    @Test fun generalSurplusSurvivesARequirementBandChange() {
        assertEquals(GeneralBalance(5, 1), LevelRules.general(GeneralBalance(4, 1), 2))
        assertEquals(GeneralBalance(15, 1), LevelRules.general(GeneralBalance(14, 3), 2))
    }
    @Test fun generalLevelFiftyDoesNotStartAnotherTransition() {
        assertEquals(GeneralBalance(50, 0), LevelRules.general(GeneralBalance(50, 0), 2))
        assertEquals(GeneralBalance(50, 0), LevelRules.general(GeneralBalance(49, 4), 2))
    }
    @Test fun noAttributeGainsKeepGeneralLevelAndCounter() {
        assertEquals(GeneralBalance(10, 2), LevelRules.general(GeneralBalance(10, 2), 0))
    }

    // AC-45–50,83–85,106–107: effective XP for a date is already reflected in its balance.
    @Test fun firstSixClosedInactiveDatesHaveNoPenalty() {
        var state = inactive(12, 0)
        for (offset in 0L..5L) {
            state = close(state, offset)
            assertEquals(inactive(12, offset.toInt() + 1), state)
        }
    }
    @Test fun seventhClosedInactiveDateLosesFiveXpWithoutLosingLevel() {
        assertEquals(inactive(7, 7, 4), close(inactive(12, 6, 4)))
    }
    @Test fun consecutivePenaltiesProduceLiteralSevenTwoZeroBalances() {
        var state = inactive(12, 6)
        for ((offset, expected) in listOf(0L to inactive(7, 7), 1L to inactive(2, 8), 2L to inactive(0, 9))) {
            state = close(state, offset); assertEquals(expected, state)
        }
    }
    @Test fun inactivityNeverMakesXpNegativeOrLowersAnAttributeLevel() {
        for (xp in listOf(0, 1, 2, 3, 4)) assertEquals(inactive(0, 7, 8), close(inactive(xp, 6, 8)))
    }
    @Test fun activityWithPositiveEffectiveXpResetsOnlyTheInactiveCounter() {
        assertEquals(inactive(18, 0), close(inactive(18, 6), xp = 6))
    }
    @Test fun dualFourAndTwoEffectiveXpInterruptBothAttributes() {
        assertEquals(inactive(16, 0), close(inactive(16, 6), xp = 4))
        assertEquals(inactive(14, 0), close(inactive(14, 6), xp = 2))
    }
    @Test fun zeroEffectiveXpStillCountsAsInactiveAtSeventhClose() {
        // Four minutes, a rounded-down secondary, or incomplete sleep all have effective XP zero.
        assertEquals(inactive(7, 7), close(inactive(12, 6), xp = 0))
    }
    @Test fun earlierXpAtDailyCapKeepsTheDateActiveDespiteLaterZeroXpRecords() {
        assertEquals(inactive(18, 0, 2), close(inactive(18, 6, 2), xp = 48))
    }
    @Test fun theCurrentOpenDateIsNotCountedOrPenalized() {
        val before = inactive(12, 6)
        assertEquals(before, InactivityRules.closeDate(before, businessDate, businessDate, 0))
    }
    @Test fun levelFiftyKeepsZeroXpWhenPenalized() {
        assertEquals(inactive(0, 7, 50), close(inactive(0, 6, 50)))
    }
    @Test fun newCharacterHasSixGraceDatesThenFirstZeroXpPenalty() {
        for (attribute in Attribute.entries) {
            var state = inactive(0, 0)
            for (offset in 0L..6L) {
                state = close(state, offset)
                assertEquals(inactive(0, offset.toInt() + 1), state, attribute.name)
            }
        }
    }
    @Test fun activityThroughThirdDateLeavesTwelveXpUntilNinthCloseThenSevenTwoZero() {
        fun reconstruct(): List<InactivityBalance> {
            var state = inactive(12, 0) // Balance after activity on the third; the fourth starts the streak.
            return (3L..11L).map { offset -> close(state, offset).also { state = it } }
        }
        val results = reconstruct()
        assertEquals(listOf(inactive(12, 6), inactive(7, 7), inactive(2, 8), inactive(0, 9)), results.takeLast(4))
        assertEquals(results, reconstruct())
    }
    @Test fun effectiveXpOnThePenalizableDateAvoidsThatDatePenalty() {
        assertEquals(inactive(7, 7), close(inactive(12, 6)))
        assertEquals(inactive(18, 0), close(inactive(18, 6), xp = 6))
        // Loading/correcting yesterday and replaying earlier dates is exercised by ReplayTest/T027.
    }

    // AC-51–57,91: Novato priority and exact 80% comparison, without rounding the threshold.
    @Test fun allSixLevelOneAlwaysClassifyAsNovato() {
        assertEquals("Novato", ClassRules.name(levels()))
    }
    @Test fun allSixPureClassesMatchTheSpecification() {
        for ((attribute, expected) in pure) assertEquals(expected, ClassRules.name(levels(attribute to 10)))
    }
    @Test fun allFifteenPairsAreOrderIndependentAndMatchTheSpecification() {
        for ((a, b, expected) in pairs) {
            assertEquals(expected, ClassRules.name(levels(a to 10, b to 8)))
            assertEquals(expected, ClassRules.name(levels(b to 10, a to 8)))
        }
    }
    @Test fun exactEightyPercentIsIncludedButOneLevelBelowIsNot() {
        assertEquals("Maestro de Armas", ClassRules.name(levels(STR to 10, DEX to 8)))
        assertEquals("Guerrero", ClassRules.name(levels(STR to 10, DEX to 7)))
        assertEquals("Maestro de Armas", ClassRules.name(levels(STR to 50, DEX to 40)))
    }
    @Test fun fractionalEightyPercentThresholdIsNotRoundedDown() {
        assertEquals("Guerrero", ClassRules.name(levels(STR to 3, DEX to 2)))
        assertEquals("Guerrero", ClassRules.name(levels(STR to 49, DEX to 39)))
        assertEquals("Maestro de Armas", ClassRules.name(levels(STR to 49, DEX to 40)))
    }
    @Test fun threeFourFiveAndSixBalancedAttributesMatchTheirClasses() {
        val order = listOf(STR, DEX, CON, INT, WIT, MEN)
        for ((index, expected) in multiple.withIndex())
            assertEquals(expected, ClassRules.name(levels(*order.take(index + 3).map { it to 10 }.toTypedArray())))
    }
    @Test fun tiedMaximumAttributesAreBothCounted() {
        assertEquals("Maestro de Armas", ClassRules.name(levels(STR to 8, DEX to 8)))
    }

    // AC-58–62,92: stage/catalog metadata only; resource and UI verification belongs to US5.
    @Test fun activeStageBoundariesAreOneTwoTwoThree() {
        for ((level, expected) in listOf(1 to 1, 14 to 1, 15 to 2, 34 to 2, 35 to 3, 50 to 3))
            assertEquals(ClassAppearance("Guerrero", expected), ClassRules.appearance(levels(STR to 10), level))
    }
    @Test fun novatoOnlyHasStageOneRegardlessOfGeneralLevel() {
        for (level in listOf(1, 14, 15, 34, 35, 50)) {
            assertEquals(ClassAppearance("Novato", 1), ClassRules.appearance(levels(), level))
            assertEquals(listOf(1), ClassRules.unlockedStages("Novato", level))
        }
    }
    @Test fun classChangeAtSameGeneralLevelKeepsTheStageAndUsesTheNewClass() {
        assertEquals(ClassAppearance("Guerrero", 2), ClassRules.appearance(levels(STR to 10, DEX to 7), 20))
        assertEquals(ClassAppearance("Maestro de Armas", 2), ClassRules.appearance(levels(STR to 10, DEX to 8), 20))
    }
    @Test fun correctedGeneralLevelRecomputesAppearanceInsteadOfKeepingHighestPastStage() {
        for ((level, stage) in listOf(35 to 3, 34 to 2, 14 to 1))
            assertEquals(ClassAppearance("Guerrero", stage), ClassRules.appearance(levels(STR to 10), level))
    }
    @Test fun unlockedStageBoundariesMatchTheCurrentGeneralLevel() {
        for ((level, expected) in listOf(1 to listOf(1), 14 to listOf(1), 15 to listOf(1, 2),
            34 to listOf(1, 2), 35 to listOf(1, 2, 3), 50 to listOf(1, 2, 3)))
            for (name in classNames.filter { it != "Novato" }) assertEquals(expected, ClassRules.unlockedStages(name, level))
    }
    @Test fun catalogContainsExactlyTwentySixNamesAndSeventySixStageCombinations() {
        val catalog = ClassRules.catalogStages()
        assertEquals(classNames.toSet(), catalog.keys); assertEquals(26, catalog.size)
        assertEquals(76, catalog.values.sumOf { it.size })
        assertEquals(listOf(1), catalog.getValue("Novato"))
        for (name in classNames.filter { it != "Novato" }) assertEquals(listOf(1, 2, 3), catalog.getValue(name))
    }
}
