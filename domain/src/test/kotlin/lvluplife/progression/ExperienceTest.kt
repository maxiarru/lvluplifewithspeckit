package lvluplife.progression

import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.Attribute
import lvluplife.model.Attribute.*
import lvluplife.model.CycleId

/** Literal spec fixtures. No formula implementation, clock, Firebase, Android or external services. */
class ExperienceTest {
    private val day = LocalDate.parse("2026-11-11")
    private val cycle = CycleId("synthetic-current-cycle")
    private val otherCycle = CycleId("synthetic-other-cycle")

    // AC-25,78: each ordinary record rounds independently to complete five-minute blocks.
    @Test fun thirtyMinutesOfEachOrdinaryAttributeGiveSixXp() {
        for (attribute in listOf(STR, DEX, INT, WIT))
            assertEquals(mapOf(attribute to 6), ExperienceRules.duration(30, attribute))
    }
    @Test fun ordinaryDurationBoundariesHaveLiteralExpectedContributions() {
        val fixtures = listOf(1 to 0, 4 to 0, 5 to 1, 9 to 1, 10 to 2, 14 to 2, 15 to 3, 29 to 5, 30 to 6, 31 to 6)
        for ((minutes, expected) in fixtures) assertEquals(mapOf(STR to expected), ExperienceRules.duration(minutes, STR))
    }
    @Test fun ordinaryRemaindersDoNotAccumulateAcrossRecords() {
        assertEquals(listOf(0, 0), listOf(4, 4).map { ExperienceRules.duration(it, STR).getValue(STR) })
    }
    @Test fun customMentalDurationUsesFiveMinuteRuleInsteadOfSleepRule() {
        assertEquals(mapOf(MEN to 6), ExperienceRules.duration(30, MEN))
    }
    @Test fun zeroDurationIsRejectedBeforeAnyAward() {
        assertFailsWith<IllegalArgumentException> { ExperienceRules.duration(0, STR) }
    }
    @Test fun negativeDurationIsRejectedBeforeAnyAward() {
        assertFailsWith<IllegalArgumentException> { ExperienceRules.duration(-1, STR) }
    }

    // AC-27,28,79,100: round the primary share up, then cap each share without redistribution.
    @Test fun thirtyMinuteDualActivityGivesFourAndTwo() {
        assertEquals(mapOf(STR to 4, DEX to 2), ExperienceRules.duration(30, STR, DEX))
    }
    @Test fun dualRoundingBoundariesHaveLiteralExpectedContributions() {
        val fixtures = listOf(4 to (0 to 0), 5 to (1 to 0), 10 to (2 to 0), 15 to (2 to 1),
            20 to (3 to 1), 25 to (4 to 1), 30 to (4 to 2), 35 to (5 to 2), 40 to (6 to 2), 45 to (6 to 3))
        for ((minutes, shares) in fixtures)
            assertEquals(mapOf(STR to shares.first, DEX to shares.second), ExperienceRules.duration(minutes, STR, DEX))
    }
    @Test fun primaryAndSecondaryRolesAreNotInterchanged() {
        assertEquals(mapOf(DEX to 2, STR to 0), ExperienceRules.duration(10, DEX, STR))
    }
    @Test fun allDifferentCompatibleDurationPairsUseTheSameSplitIncludingMental() {
        val attributes = listOf(STR, DEX, INT, WIT, MEN)
        for (primary in attributes) for (secondary in attributes.filter { it != primary })
            assertEquals(mapOf(primary to 4, secondary to 2), ExperienceRules.duration(30, primary, secondary))
    }
    @Test fun dualDailyMarginsAreIndependent() {
        assertEquals(mapOf(STR to 1, DEX to 1), ExperienceRules.duration(30, STR, DEX, mapOf(STR to 47, DEX to 47)))
    }
    @Test fun cappedPrimaryDoesNotDonateItsShareToSecondary() {
        assertEquals(mapOf(STR to 0, DEX to 2), ExperienceRules.duration(30, STR, DEX, mapOf(STR to 48)))
    }
    @Test fun cappedSecondaryDoesNotDonateItsShareToPrimary() {
        assertEquals(mapOf(STR to 4, DEX to 0), ExperienceRules.duration(30, STR, DEX, mapOf(DEX to 48)))
    }
    @Test fun mentalShareUsesItsOwnThirtyXpLimitInADualActivity() {
        assertEquals(mapOf(STR to 4, MEN to 1), ExperienceRules.duration(30, STR, MEN, mapOf(MEN to 29)))
    }
    @Test fun tenMinuteDualActivityHasPositiveXpOnlyForPrimary() {
        val awarded = ExperienceRules.duration(10, STR, DEX)
        assertEquals(mapOf(STR to 2, DEX to 0), awarded)
        assertEquals(setOf(STR), awarded.filterValues { it > 0 }.keys)
    }

    // AC-26,34,35,89: the pure calculation consumes only the remaining per-attribute margin.
    @Test fun ordinaryDailyMarginsFromZeroThroughFiveClipSixXp() {
        for (attribute in listOf(STR, DEX, INT, WIT)) for (fixture in listOf(48 to 0, 47 to 1, 46 to 2, 45 to 3, 44 to 4, 43 to 5))
            assertEquals(mapOf(attribute to fixture.second), ExperienceRules.duration(30, attribute, awardedToday = mapOf(attribute to fixture.first)))
    }
    @Test fun fullOrdinaryMarginAllowsExactlySixXp() {
        assertEquals(mapOf(STR to 6), ExperienceRules.duration(30, STR, awardedToday = mapOf(STR to 42)))
    }
    @Test fun largestValidDailyDurationStillCannotExceedOrdinaryCap() {
        assertEquals(mapOf(STR to 48), ExperienceRules.duration(1440, STR))
    }
    @Test fun mentalCustomDurationSharesTheSameThirtyXpBudgetAsSleep() {
        assertEquals(mapOf(MEN to 1), ExperienceRules.duration(30, MEN, awardedToday = mapOf(MEN to 29)))
        assertEquals(mapOf(MEN to 0), ExperienceRules.duration(30, MEN, awardedToday = mapOf(MEN to 30)))
    }
    @Test fun aValidRecordAfterDailyCapHasZeroContributionRatherThanAnError() {
        assertEquals(mapOf(STR to 0), ExperienceRules.duration(5, STR, awardedToday = mapOf(STR to 48)))
    }
    @Test fun twoAcceptedFiveMinuteContributionsShareTheLastDailyPoint() {
        // Accepted ordering is supplied by the caller; a Firestore race is tested in T038, not here.
        val first = ExperienceRules.duration(5, STR, awardedToday = mapOf(STR to 47)).getValue(STR)
        val second = ExperienceRules.duration(5, STR, awardedToday = mapOf(STR to 47 + first)).getValue(STR)
        assertEquals(listOf(1, 0), listOf(first, second)); assertEquals(1, first + second)
    }
    @Test fun budgetsAreInputsAndCannotBeMutatedByCalculation() {
        val before = mapOf(STR to 47, DEX to 11)
        ExperienceRules.duration(30, STR, DEX, before)
        assertEquals(mapOf(STR to 47, DEX to 11), before)
    }
    @Test fun levelFiftyAttributeReceivesNoXp() {
        assertEquals(mapOf(STR to 0), ExperienceRules.duration(30, STR, atLevelCap = setOf(STR)))
    }
    @Test fun levelFiftyPrimaryDoesNotTransferXpToSecondary() {
        assertEquals(mapOf(STR to 0, DEX to 2), ExperienceRules.duration(30, STR, DEX, atLevelCap = setOf(STR)))
    }

    // AC-29,30,34: CON is awarded only by healthy meals, at most four per activity date/cycle.
    @Test fun eachOfFirstFourMealsGivesSevenXp() {
        for ((meals, awarded) in listOf(0 to 0, 1 to 7, 2 to 14, 3 to 21))
            assertEquals(7, ExperienceRules.meal(meals, awarded))
    }
    @Test fun fifthAndLaterMealsHaveZeroXpWithoutRejectingTheActivity() {
        for (meals in listOf(4, 5, 20)) assertEquals(0, ExperienceRules.meal(meals, 28))
    }
    @Test fun fourMealsHaveLiteralTwentyEightXpTotal() {
        assertEquals(28, ExperienceRules.meals(4))
        assertEquals(listOf(7, 7, 7, 7, 0), listOf(0 to 0, 1 to 7, 2 to 14, 3 to 21, 4 to 28)
            .map { (count, xp) -> ExperienceRules.meal(count, xp) })
    }
    @Test fun levelFiftyConstitutionReceivesNoMealXp() {
        assertEquals(0, ExperienceRules.meal(0, atLevelCap = true))
    }
    @Test fun noMealRecordsMeanZeroMealContributions() {
        assertEquals(0, ExperienceRules.meals(0))
    }
    @Test fun mealCountBoundariesHaveLiteralDailyTotals() {
        for ((count, expected) in listOf(0 to 0, 1 to 7, 2 to 14, 3 to 21, 4 to 28, 5 to 28, 20 to 28))
            assertEquals(expected, ExperienceRules.meals(count))
    }

    // AC-31,32,80,81,101: sleep accumulates only within one activity date and cycle.
    @Test fun sixtySleepMinutesGiveThreeMentalXp() {
        val result = ExperienceRules.sleep(60, cycle, day)
        assertEquals(3, result.xp); assertEquals(SleepRemainder(cycle, day, 0), result.remainder)
    }
    @Test fun sleepBoundariesHaveLiteralXpAndRemainders() {
        val fixtures = listOf(1 to (0 to 1), 30 to (0 to 30), 59 to (0 to 59), 60 to (3 to 0),
            61 to (3 to 1), 119 to (3 to 59), 120 to (6 to 0), 1440 to (30 to 0))
        for ((minutes, expected) in fixtures) {
            val result = ExperienceRules.sleep(minutes, cycle, day)
            assertEquals(expected.first, result.xp); assertEquals(SleepRemainder(cycle, day, expected.second), result.remainder)
        }
    }
    @Test fun twoHalfHoursOnSameDateAndCycleCompleteOneSleepBlock() {
        val first = ExperienceRules.sleep(30, cycle, day)
        assertEquals(0, first.xp)
        val second = ExperienceRules.sleep(30, cycle, day, first.remainder)
        assertEquals(3, second.xp); assertEquals(SleepRemainder(cycle, day, 0), second.remainder)
    }
    @Test fun sleepAcrossDifferentDatesDoesNotCombinePendingMinutes() {
        val yesterday = ExperienceRules.sleep(30, cycle, day.minusDays(1))
        val today = ExperienceRules.sleep(30, cycle, day, yesterday.remainder)
        assertEquals(0, yesterday.xp); assertEquals(0, today.xp)
        assertEquals(SleepRemainder(cycle, day, 30), today.remainder)
    }
    @Test fun sleepAcrossDifferentCyclesOnSameDateDoesNotCombinePendingMinutes() {
        val previous = ExperienceRules.sleep(30, cycle, day)
        val current = ExperienceRules.sleep(30, otherCycle, day, previous.remainder)
        assertEquals(0, current.xp); assertEquals(SleepRemainder(otherCycle, day, 30), current.remainder)
    }
    @Test fun incompleteSleepMinuteRemaindersStayWithinTheSameDay() {
        val first = ExperienceRules.sleep(59, cycle, day)
        val next = ExperienceRules.sleep(2, cycle, day, first.remainder)
        assertEquals(3, next.xp); assertEquals(SleepRemainder(cycle, day, 1), next.remainder)
    }
    @Test fun mentalDailyMarginsFromZeroThroughTwoClipThreeSleepXp() {
        for ((awarded, expected) in listOf(30 to 0, 29 to 1, 28 to 2, 27 to 3))
            assertEquals(expected, ExperienceRules.sleep(60, cycle, day, awardedToday = awarded).xp)
    }
    @Test fun sleepConsumesFullBlocksEvenWhenDailyXpIsClipped() {
        val result = ExperienceRules.sleep(61, cycle, day, awardedToday = 29)
        assertEquals(1, result.xp); assertEquals(SleepRemainder(cycle, day, 1), result.remainder)
    }
    @Test fun sleepAfterMentalCapHasZeroXpWithoutRejectingValidDuration() {
        assertEquals(0, ExperienceRules.sleep(60, cycle, day, awardedToday = 30).xp)
    }
    @Test fun customMentalDurationAndSleepUseOneSharedDailyCap() {
        val custom = ExperienceRules.duration(30, MEN, awardedToday = mapOf(MEN to 24)).getValue(MEN)
        assertEquals(6, custom)
        assertEquals(0, ExperienceRules.sleep(60, cycle, day, awardedToday = 24 + custom).xp)
    }
    @Test fun sleepAtLevelFiftyMentalGivesNoXp() {
        assertEquals(0, ExperienceRules.sleep(60, cycle, day, atLevelCap = true).xp)
    }
    @Test fun zeroSleepDurationIsRejectedBeforeAnyAward() {
        assertFailsWith<IllegalArgumentException> { ExperienceRules.sleep(0, cycle, day) }
    }
    @Test fun negativeSleepDurationIsRejectedBeforeAnyAward() {
        assertFailsWith<IllegalArgumentException> { ExperienceRules.sleep(-1, cycle, day) }
    }
    @Test fun durationRejectsConstitutionAndDuplicateAttributes() {
        assertFailsWith<IllegalArgumentException> { ExperienceRules.duration(30, CON) }
        assertFailsWith<IllegalArgumentException> { ExperienceRules.duration(30, STR, CON) }
        assertFailsWith<IllegalArgumentException> { ExperienceRules.duration(30, STR, STR) }
    }
    @Test fun excessiveDurationCannotBeScoredEvenAtLevelFifty() {
        for (minutes in listOf(1441, Int.MAX_VALUE))
            assertFailsWith<IllegalArgumentException> { ExperienceRules.duration(minutes, STR, atLevelCap = setOf(STR)) }
    }
    @Test fun invalidDailyBudgetsAreRejectedBeforeTheyCanIncreaseAnAward() {
        for (budget in listOf(mapOf(STR to -1), mapOf(STR to 49), mapOf(MEN to 31), mapOf(CON to 29)))
            assertFailsWith<IllegalArgumentException> { ExperienceRules.duration(30, STR, awardedToday = budget, atLevelCap = setOf(STR)) }
    }
    @Test fun mealCountsAndBudgetsCannotBeNegativeOrBeyondTheXpCap() {
        assertFailsWith<IllegalArgumentException> { ExperienceRules.meals(-1) }
        assertFailsWith<IllegalArgumentException> { ExperienceRules.meal(-1) }
        for (budget in listOf(-1, 29)) assertFailsWith<IllegalArgumentException> { ExperienceRules.meal(0, budget, atLevelCap = true) }
    }
    @Test fun largeMealCountsStayCappedWithoutOverflow() {
        assertEquals(28, ExperienceRules.meals(Int.MAX_VALUE))
        assertEquals(0, ExperienceRules.meal(Int.MAX_VALUE))
    }
    @Test fun sleepRejectsExcessiveDurationAndInvalidBudgetEvenAtMaximumLevel() {
        assertFailsWith<IllegalArgumentException> { ExperienceRules.sleep(1441, cycle, day) }
        for (budget in listOf(-1, 31)) assertFailsWith<IllegalArgumentException> {
            ExperienceRules.sleep(60, cycle, day, awardedToday = budget, atLevelCap = true)
        }
    }
    @Test fun pendingSleepMustContainLessThanOneHourOfNonnegativeMinutes() {
        for (minutes in listOf(-1, 60, Int.MAX_VALUE))
            assertFailsWith<IllegalArgumentException> { SleepRemainder(cycle, day, minutes) }
    }
    @Test fun outputIsImmutableAndIndependentFromMutableInputBudget() {
        val budget = mutableMapOf(STR to 47, DEX to 0)
        val result = ExperienceRules.duration(30, STR, DEX, budget)
        assertEquals(mapOf(STR to 47, DEX to 0), budget)
        assertFailsWith<UnsupportedOperationException> { (result as MutableMap)[STR] = 999 }
        budget.clear()
        assertEquals(mapOf(STR to 1, DEX to 2), result)
    }
    @Test fun dailyLimitsAreCanonicalCompleteAndImmutable() {
        val expected = mapOf(STR to 48, DEX to 48, INT to 48, WIT to 48, MEN to 30, CON to 28)
        assertEquals(expected, ExperienceRules.dailyLimits)
        assertFailsWith<UnsupportedOperationException> { (ExperienceRules.dailyLimits as MutableMap)[STR] = 999 }
        assertEquals(expected, ExperienceRules.dailyLimits)
    }
    @Test fun maximumLevelSecondaryDoesNotChangePrimaryShare() {
        assertEquals(mapOf(STR to 4, DEX to 0), ExperienceRules.duration(30, STR, DEX, atLevelCap = setOf(DEX)))
    }
}
