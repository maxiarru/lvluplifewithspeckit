package lvluplife.record

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.*
import lvluplife.model.Attribute.*
import lvluplife.progression.*

/** Literal spec fixtures; no copied XP formula, system clock or IO. HTTP history filtering belongs to T054. */
class CorrectionTest {
    private val created = Instant.parse("2026-11-11T15:00:00Z")
    private val day = LocalDate.parse("2026-11-11")
    private val cycle = Cycle(Uid("synthetic-correction-owner"), CycleId("synthetic-current"), created.minusSeconds(3600))
    private fun definition(id: String) = ActivityCatalog.find(ActivityId(id))!!
    private fun record(id: String = "synthetic-record", duration: Int? = 30, activity: String = "strength",
        date: LocalDate = day, recordCycle: CycleId = cycle.id, born: Instant = created) =
        ActivityRecord.create(RecordId(id), recordCycle, definition(activity), date, duration, born)
    private fun edit(r: ActivityRecord, duration: Int = r.durationMinutes ?: 0, date: LocalDate = r.activityDate,
        activity: String = r.activityId.value, records: List<ActivityRecord> = listOf(r), now: Instant = created.plusSeconds(299)) =
        CorrectionRules.edit(r, cycle, date, definition(activity),
            if (r.durationMinutes == null) null else BigDecimal(duration / 60),
            if (r.durationMinutes == null) null else BigDecimal(duration % 60), records, now)
    private fun replay(records: List<ActivityRecord>, now: Instant = created.plusSeconds(299)) = ReplayEngine.rebuild(cycle, records, now)

    @Test fun durationEditAt299SecondsPreservesIdentityCreationAndIncrementsVersion() {
        val r = record(); val updated = edit(r, duration = 60, activity = "aerobic", date = day.minusDays(1))
        assertEquals(r.id, updated.id); assertEquals(r.cycleId, updated.cycleId); assertEquals(r.createdAt, updated.createdAt)
        assertEquals(created.plusSeconds(299), updated.updatedAt); assertEquals(2, updated.version)
        assertEquals(60, updated.durationMinutes); assertEquals(ActivityId("aerobic"), updated.activityId)
        assertEquals(day.minusDays(1), updated.activityDate); assertNull(updated.deletedAt)
        assertEquals(30, r.durationMinutes); assertEquals(1, r.version)
    }
    @Test fun deletionAt299SecondsCreatesTombstoneWithoutChangingOriginalIdentity() {
        val r = record(); val deleted = CorrectionRules.delete(r, cycle, created.plusSeconds(299))
        assertEquals(r.id, deleted.id); assertEquals(r.cycleId, deleted.cycleId); assertEquals(r.createdAt, deleted.createdAt)
        assertEquals(2, deleted.version); assertEquals(created.plusSeconds(299), deleted.deletedAt)
        assertEquals(deleted.deletedAt, deleted.updatedAt); assertEquals(r.definition, deleted.definition); assertNull(r.deletedAt)
    }
    @Test fun nanosecondBefore300SecondsIsStillInsideWindowForBothMutations() {
        val now = created.plusSeconds(300).minusNanos(1); val r = record()
        assertEquals(now, edit(r, now = now).updatedAt); assertEquals(now, CorrectionRules.delete(r, cycle, now).deletedAt)
    }
    @Test fun editingAtExactly300SecondsIsRejectedWithoutChangingRecord() {
        val r = record(); assertFailsWith<IllegalArgumentException> { edit(r, now = created.plusSeconds(300)) }
        assertEquals(1, r.version); assertEquals(30, r.durationMinutes); assertEquals(created, r.updatedAt)
    }
    @Test fun deletionAtExactly300SecondsAndLaterIsRejected() {
        val r = record()
        for (age in listOf(300L, 301L, 86400L)) assertFailsWith<IllegalArgumentException> { CorrectionRules.delete(r, cycle, created.plusSeconds(age)) }
        assertNull(r.deletedAt); assertEquals(1, r.version)
    }
    @Test fun clockBeforeCreationCannotAdmitEditOrDeletion() {
        val r = record(); val before = created.minusNanos(1)
        assertFailsWith<IllegalArgumentException> { edit(r, now = before) }
        assertFailsWith<IllegalArgumentException> { CorrectionRules.delete(r, cycle, before) }
    }
    @Test fun repeatedEditDoesNotRestartFiveMinuteWindowFromUpdatedAt() {
        val first = edit(record(), now = created.plusSeconds(299))
        assertFailsWith<IllegalArgumentException> { edit(first, now = created.plusSeconds(300)) }
        assertFailsWith<IllegalArgumentException> { CorrectionRules.delete(first, cycle, created.plusSeconds(300)) }
    }
    @Test fun updatedAtCannotMoveBackwardsWithinOriginalWindow() {
        val first = edit(record(), now = created.plusSeconds(100))
        assertFailsWith<IllegalArgumentException> { edit(first, now = created.plusSeconds(99)) }
        assertFailsWith<IllegalArgumentException> { CorrectionRules.delete(first, cycle, created.plusSeconds(99)) }
    }
    @Test fun recentPreviousCycleRecordCannotBeEditedOrDeleted() {
        val old = record(recordCycle = CycleId("synthetic-previous"))
        assertFailsWith<IllegalArgumentException> { edit(old) }
        assertFailsWith<IllegalArgumentException> { CorrectionRules.delete(old, cycle, created.plusSeconds(1)) }
        assertEquals(1, old.version); assertNull(old.deletedAt)
    }
    @Test fun closedCycleCannotAdmitCorrectionsEvenWhenIdMatches() {
        val r = record(); val closed = cycle.copy(closedAt = created)
        assertFailsWith<IllegalArgumentException> { CorrectionRules.edit(r, closed, day, hours = BigDecimal.ZERO, minutes = BigDecimal.TEN, records = listOf(r), now = created.plusSeconds(1)) }
        assertFailsWith<IllegalArgumentException> { CorrectionRules.delete(r, closed, created.plusSeconds(1)) }
    }
    @Test fun tombstoneCannotBeEditedOrDeletedAgain() {
        val deleted = record().markDeleted(created.plusSeconds(1))
        assertFailsWith<IllegalArgumentException> { edit(deleted) }
        assertFailsWith<IllegalArgumentException> { CorrectionRules.delete(deleted, cycle, created.plusSeconds(2)) }
    }
    @Test fun mealCorrectionOnlyMovesDateAndNeverAddsDuration() {
        val meal = record(duration = null, activity = "meal"); val updated = edit(meal, date = day.minusDays(1))
        assertEquals(meal.id, updated.id); assertEquals(meal.createdAt, updated.createdAt)
        assertEquals(meal.definition, updated.definition); assertNull(updated.durationMinutes); assertEquals(day.minusDays(1), updated.activityDate)
    }
    @Test fun durationCannotBecomeMealAndMealCannotBecomeDuration() {
        val duration = record(); val meal = record(duration = null, activity = "meal")
        assertFailsWith<IllegalArgumentException> { edit(duration, activity = "meal") }
        assertFailsWith<IllegalArgumentException> { edit(meal, activity = "strength") }
    }
    @Test fun mealRejectsAnyDurationComponentsEvenZero() {
        val meal = record(duration = null, activity = "meal")
        assertFailsWith<IllegalArgumentException> { CorrectionRules.edit(meal, cycle, day, hours = BigDecimal.ZERO, minutes = BigDecimal.ZERO, records = listOf(meal), now = created.plusSeconds(1)) }
    }
    @Test fun mealDefinitionCannotBeRenamedDuringCorrection() {
        val meal = record(duration = null, activity = "meal")
        assertFailsWith<IllegalArgumentException> { CorrectionRules.edit(meal, cycle, day, meal.definition.copy(name = "Otra comida"), records = listOf(meal), now = created.plusSeconds(1)) }
    }
    @Test fun durationRequiresBothWholeNonnegativeComponentsAndMinutesBelow60() {
        val r = record()
        for ((hours, minutes) in listOf(null to BigDecimal.TEN, BigDecimal.ZERO to null, BigDecimal("0.5") to BigDecimal.ZERO,
            BigDecimal.ZERO to BigDecimal("1.5"), BigDecimal(-1) to BigDecimal.ZERO, BigDecimal.ZERO to BigDecimal(60),
            BigDecimal.ZERO to BigDecimal.ZERO, BigDecimal(24) to BigDecimal.ONE, BigDecimal("1E100") to BigDecimal.ZERO))
            assertFailsWith<IllegalArgumentException> { CorrectionRules.edit(r, cycle, day, hours = hours, minutes = minutes, records = listOf(r), now = created.plusSeconds(1)) }
    }
    @Test fun targetDateMustBeTodayYesterdayOrDayBeforeYesterdayAtValidationInstant() {
        val r = record()
        for (age in 0L..2L) assertEquals(day.minusDays(age), edit(r, date = day.minusDays(age)).activityDate)
        for (date in listOf(day.plusDays(1), day.minusDays(3))) assertFailsWith<IllegalArgumentException> { edit(r, date = date) }
    }
    @Test fun businessMidnightRevalidatesTargetDateInUtcMinusThree() {
        val born = Instant.parse("2026-11-12T02:59:00Z"); val r = record(date = day.minusDays(2), born = born)
        val before = born.plusSeconds(59); val after = born.plusSeconds(60)
        assertEquals(day.minusDays(2), edit(r, now = before).activityDate)
        assertFailsWith<IllegalArgumentException> { edit(r, now = after) }
        assertNotNull(CorrectionRules.delete(r, cycle, after).deletedAt)
    }
    @Test fun fullDayReplacementExcludesPriorDurationRatherThanAddingItTwice() {
        val original = record(duration = 180); val other = record("other", 1260)
        val shorter = edit(original, duration = 120, records = listOf(original, other))
        val result = replay(listOf(shorter, other)); assertEquals(1380, result.durationByDate.getValue(day))
        assertEquals(60, RecordValidation.durationMinutes(BigDecimal.ONE, BigDecimal.ZERO, cycle.id, day, listOf(shorter, other)))
        assertFailsWith<IllegalArgumentException> { RecordValidation.durationMinutes(BigDecimal(2), BigDecimal.ZERO, cycle.id, day, listOf(shorter, other)) }
        val deleted = CorrectionRules.delete(shorter, cycle, created.plusSeconds(299))
        assertEquals(1260, replay(listOf(deleted, other)).durationByDate.getValue(day))
        assertEquals(180, RecordValidation.durationMinutes(BigDecimal(3), BigDecimal.ZERO, cycle.id, day, listOf(deleted, other)))
    }
    @Test fun movingDateFreesOriginAndConsumesOnlyDestinationBudget() {
        val original = record(duration = 180); val origin = record("origin", 1260); val target = record("target", 1320, date = day.minusDays(1))
        val updated = edit(original, duration = 120, date = day.minusDays(1), records = listOf(original, origin, target))
        val result = replay(listOf(updated, origin, target))
        assertEquals(1260, result.durationByDate.getValue(day)); assertEquals(1440, result.durationByDate.getValue(day.minusDays(1)))
        assertEquals(180, RecordValidation.durationMinutes(BigDecimal(3), BigDecimal.ZERO, cycle.id, day, listOf(updated, origin, target)))
    }
    @Test fun overdrawnDestinationRejectsEditAndPreservesSource() {
        val original = record(duration = 180); val target = record("target", 1320, date = day.minusDays(1))
        assertFailsWith<IllegalArgumentException> { edit(original, date = day.minusDays(1), records = listOf(original, target)) }
        assertEquals(day, original.activityDate); assertEquals(180, original.durationMinutes); assertEquals(1, original.version)
    }
    @Test fun oldCyclesAndTombstonesDoNotConsumeCorrectionBudget() {
        val original = record(duration = 60)
        val old = record("old", 1440, recordCycle = CycleId("previous")); val tombstone = record("deleted", 1440).markDeleted(created)
        assertEquals(1440, edit(original, duration = 1440, records = listOf(original, old, tombstone)).durationMinutes)
    }
    @Test fun durationCorrectionRebuildsLevelsGeneralAndClassFromSurvivingRecords() {
        val r = record(duration = 150); val before = replay(listOf(r)); val after = replay(listOf(edit(r, duration = 5)))
        assertEquals(AttributeBalance(2, 0), before.attributes.getValue(STR).attribute); assertEquals(GeneralBalance(2, 0), before.general)
        assertEquals(ClassAppearance("Guerrero", 1), before.appearance)
        assertEquals(AttributeBalance(1, 1), after.attributes.getValue(STR).attribute); assertEquals(GeneralBalance(1, 0), after.general)
        assertEquals(ClassAppearance("Novato", 1), after.appearance)
    }
    @Test fun deletionRebuildsCanonicalStateAndKeepsOtherCycleHistoryUntouched() {
        val r = record(duration = 150); val old = record("old", 240, recordCycle = CycleId("previous"))
        val deleted = CorrectionRules.delete(r, cycle, created.plusSeconds(1))
        assertEquals(replay(emptyList()), replay(listOf(deleted, old)))
        assertEquals(240, old.durationMinutes); assertNull(old.deletedAt); assertEquals(1, old.version)
        assertEquals(2, listOf(deleted, old).size)
    }
    @Test fun shorteningEarlierRecordRedistributesDailyXpToLaterRecord() {
        val first = record("first", 240); val second = record("second", 30, born = created.plusSeconds(1))
        assertEquals(0, replay(listOf(first, second)).recordXp.getValue("second").getValue(STR))
        val updated = edit(first, duration = 30, records = listOf(first, second))
        val result = replay(listOf(updated, second))
        assertEquals(6, result.recordXp.getValue("first").getValue(STR)); assertEquals(6, result.recordXp.getValue("second").getValue(STR))
        assertEquals(AttributeBalance(1, 12), result.attributes.getValue(STR).attribute)
    }
    @Test fun movingMealRecalculatesFirstFourOccurrencesOnEachDate() {
        val meals = (0..4).map { record("meal-$it", null, "meal", born = created.plusSeconds(it.toLong())) }
        assertEquals(0, replay(meals).recordXp.getValue("meal-4").getValue(CON))
        val moved = edit(meals[0], date = day.minusDays(1))
        val result = replay(listOf(moved) + meals.drop(1))
        assertEquals(7, result.recordXp.getValue("meal-0").getValue(CON)); assertEquals(7, result.recordXp.getValue("meal-4").getValue(CON))
        assertTrue(result.durationByDate.isEmpty())
    }
    @Test fun deletingSleepReconstructsRemainderWithoutGrantingRemovedXp() {
        val first = record("sleep-first", 30, "sleep"); val second = record("sleep-second", 30, "sleep", born = created.plusSeconds(1))
        assertEquals(3, replay(listOf(first, second)).attributes.getValue(MEN).attribute.xp)
        val removed = CorrectionRules.delete(first, cycle, created.plusSeconds(2))
        assertEquals(0, replay(listOf(removed, second)).attributes.getValue(MEN).attribute.xp)
        assertEquals(30, replay(listOf(removed, second)).durationByDate.getValue(day))
    }
    @Test fun backdatedCorrectionRestoresPenaltyAvoidedByThatDate() {
        val start = created.minusSeconds(8 * 86400L); val longCycle = cycle.copy(startedAt = start)
        val base = record("base", 200, date = day.minusDays(8), born = start)
        val recent = record("recent", 5, born = created.minusSeconds(10))
        val before = ReplayEngine.rebuild(longCycle, listOf(base, recent), created)
        assertEquals(AttributeBalance(2, 6), before.attributes.getValue(STR).attribute)
        val moved = CorrectionRules.edit(recent, longCycle, day.minusDays(1), hours = BigDecimal.ZERO, minutes = BigDecimal(5), records = listOf(base, recent), now = created)
        val after = ReplayEngine.rebuild(longCycle, listOf(base, moved), created)
        assertEquals(AttributeBalance(2, 11), after.attributes.getValue(STR).attribute)
        assertEquals(after, ReplayEngine.rebuild(longCycle, listOf(base, moved), created))
    }
}
