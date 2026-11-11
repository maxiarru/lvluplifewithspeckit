package lvluplife.progression

import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.*
import lvluplife.model.Attribute.*

class InactivityRulesTest {
    private val today = LocalDate.parse("2026-11-11")
    private val yesterday = today.minusDays(1)
    private fun state(xp: Int = 12, days: Int = 6, level: Int = 1) = InactivityBalance(AttributeBalance(level, xp), days)

    @Test fun futureDateCannotCountOrResetInactivity() {
        for (xp in listOf(0, 6)) assertFailsWith<IllegalArgumentException> {
            InactivityRules.closeDate(state(), today.plusDays(1), today, xp)
        }
    }
    @Test fun negativeEffectiveXpIsInvalidEvenForTheOpenDate() {
        for (date in listOf(yesterday, today)) assertFailsWith<IllegalArgumentException> {
            InactivityRules.closeDate(state(), date, today, -1)
        }
    }
    @Test fun positiveEffectiveXpOnOpenDateResetsWithoutCountingOrAddingXpAgain() {
        val afterAward = state(xp = 18)
        assertEquals(state(xp = 18, days = 0), InactivityRules.closeDate(afterAward, today, today, 6))
        assertEquals(state(xp = 18), afterAward)
    }
    @Test fun openDateDoesNotIncrementAnAlreadyLargeCounter() {
        val before = state(days = Int.MAX_VALUE)
        assertEquals(before, InactivityRules.closeDate(before, today, today, 0))
    }
    @Test fun inactiveCounterCannotWrapIntoNegativeDays() {
        assertFailsWith<ArithmeticException> { InactivityRules.closeDate(state(days = Int.MAX_VALUE), yesterday, today, 0) }
    }
    @Test fun dateThatReachedLevelFiftyIsActiveDespiteFinalXpZero() {
        assertEquals(state(xp = 0, days = 0, level = 50),
            InactivityRules.closeDate(state(xp = 0, level = 50), yesterday, today, 1))
    }
    @Test fun roundedDownSecondaryDoesNotReceivePrimaryActivityCredit() {
        val xp = ExperienceRules.duration(10, STR, DEX)
        assertEquals(state(xp = 14, days = 0), InactivityRules.closeDate(state(xp = 14), yesterday, today, xp.getValue(STR)))
        assertEquals(state(xp = 7, days = 7), InactivityRules.closeDate(state(), yesterday, today, xp.getValue(DEX)))
    }
    @Test fun incompleteSleepStaysInactiveButCompletedHourAvoidsItsDatePenalty() {
        val cycle = CycleId("synthetic-cycle")
        val first = ExperienceRules.sleep(30, cycle, yesterday)
        val completed = ExperienceRules.sleep(30, cycle, yesterday, first.remainder)
        // Two fresh reconstructions of the date's final XP; this does not close the updated state twice.
        assertEquals(state(xp = 7, days = 7), InactivityRules.closeDate(state(), yesterday, today, first.xp))
        assertEquals(state(xp = 15, days = 0), InactivityRules.closeDate(state(xp = 15), yesterday, today, completed.xp))
    }
}
