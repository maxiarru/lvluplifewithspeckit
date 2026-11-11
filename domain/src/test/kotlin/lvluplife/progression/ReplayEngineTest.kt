package lvluplife.progression

import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.*
import lvluplife.model.Attribute.*

class ReplayEngineTest {
    private val start = Instant.parse("2026-01-01T15:00:00Z")
    private val day = LocalDate.parse("2026-01-01")
    private val cycle = Cycle(Uid("synthetic-owner"), CycleId("synthetic-cycle"), start)
    private fun record(id: String, minutes: Int, date: LocalDate = day, activity: String = "strength",
        created: Instant = start) = ActivityRecord.create(RecordId(id), cycle.id,
        ActivityCatalog.find(ActivityId(activity))!!, date, minutes, created)
    private fun rebuild(records: List<ActivityRecord>, now: Instant = start) = ReplayEngine.rebuild(cycle, records, now)

    @Test fun emptyOpenCycleStartsFromCanonicalInitialStateAndIsRepeatable() {
        val first = rebuild(emptyList())
        assertEquals(Attribute.entries.associateWith { InactivityBalance(AttributeBalance(1, 0), 0) }, first.attributes)
        assertEquals(GeneralBalance(1, 0), first.general)
        assertEquals(ClassAppearance("Novato", 1), first.appearance)
        assertTrue(first.recordXp.isEmpty()); assertTrue(first.durationByDate.isEmpty())
        assertEquals(first, rebuild(emptyList()))
    }
    @Test fun closedFutureAndUnsupportedCyclesAreRejected() {
        assertFailsWith<IllegalArgumentException> { ReplayEngine.rebuild(cycle.copy(closedAt = start), emptyList(), start) }
        assertFailsWith<IllegalArgumentException> { rebuild(emptyList(), start.minusSeconds(1)) }
        assertFailsWith<IllegalArgumentException> { ReplayEngine.rebuild(cycle.copy(rulesVersion = RulesVersion(2)), emptyList(), start) }
    }
    @Test fun futureActivityOrCreationAndImpossibleSnapshotChronologyAreRejected() {
        assertFailsWith<IllegalArgumentException> { rebuild(listOf(record("future-date", 5, day.plusDays(1)))) }
        assertFailsWith<IllegalArgumentException> { rebuild(listOf(record("future-created", 5, created = start.plusSeconds(1)))) }
        assertFailsWith<IllegalArgumentException> { rebuild(listOf(record("before-cycle", 5, created = start.minusSeconds(1)))) }
        val edited = record("edited", 5).correct(day, durationMinutes = 10, now = start.plusSeconds(1))
        assertFailsWith<IllegalArgumentException> { rebuild(listOf(edited)) }
    }
    @Test fun duplicateCurrentCycleIdentitiesAreRejectedInsteadOfDoubleAwarded() {
        val original = record("same", 5)
        assertFailsWith<IllegalArgumentException> { rebuild(listOf(original, original)) }
        val deleted = original.markDeleted(start)
        assertFailsWith<IllegalArgumentException> { rebuild(listOf(original, deleted)) }
    }
    @Test fun overdrawnDailyDurationCannotProduceAProjection() {
        assertFailsWith<IllegalArgumentException> { rebuild(listOf(record("a", 1440), record("b", 1))) }
    }
    @Test fun correctionAndDeletionRebuildLevelsGeneralAndClassFromZero() {
        val original = record("a", 150)
        val first = rebuild(listOf(original))
        assertEquals(AttributeBalance(2, 0), first.attributes.getValue(STR).attribute)
        assertEquals(GeneralBalance(2, 0), first.general)
        assertEquals(ClassAppearance("Guerrero", 1), first.appearance)
        val corrected = original.correct(day, durationMinutes = 5, now = start)
        val second = rebuild(listOf(corrected))
        assertEquals(AttributeBalance(1, 1), second.attributes.getValue(STR).attribute)
        assertEquals(GeneralBalance(1, 0), second.general)
        assertEquals(ClassAppearance("Novato", 1), second.appearance)
        assertEquals(rebuild(emptyList()), rebuild(listOf(original.markDeleted(start))))
        assertEquals(first, rebuild(listOf(original)))
    }
    @Test fun dailyXpBudgetsResetOnEachActivityDate() {
        val result = rebuild(listOf(record("first", 240), record("second", 240, day.plusDays(1))), start.plusSeconds(86400))
        assertEquals(mapOf(STR to 48), result.recordXp.getValue("first"))
        assertEquals(mapOf(STR to 48), result.recordXp.getValue("second"))
        assertEquals(AttributeBalance(3, 6), result.attributes.getValue(STR).attribute)
        assertEquals(GeneralBalance(2, 1), result.general)
    }
    @Test fun zeroXpAfterPositiveXpDoesNotMakeTheClosedDateInactive() {
        val result = rebuild(listOf(record("a", 5), record("b", 1)), start.plusSeconds(86400))
        assertEquals(mapOf(STR to 0), result.recordXp.getValue("b"))
        assertEquals(InactivityBalance(AttributeBalance(1, 1), 0), result.attributes.getValue(STR))
        assertEquals(1, result.attributes.getValue(DEX).inactiveDates)
    }
    @Test fun sleepAndCustomMentalDurationShareTheMentalXpBudget() {
        val custom = ActivityDefinition(ActivityId("synthetic-mental"), "Mental", "Pruebas", ActivityMode.DURATION, MEN)
        val mental = ActivityRecord.create(RecordId("b"), cycle.id, custom, day, 5, start)
        val result = rebuild(listOf(record("a", 600, activity = "sleep"), mental))
        assertEquals(mapOf(MEN to 30), result.recordXp.getValue("a"))
        assertEquals(mapOf(MEN to 0), result.recordXp.getValue("b"))
        assertEquals(AttributeBalance(2, 0), result.attributes.getValue(MEN).attribute)
        assertEquals(605, result.durationByDate.getValue(day))
    }
    @Test fun returnedCollectionsAreImmutableAndDetachedFromInput() {
        val input = mutableListOf(record("a", 5))
        val result = rebuild(input)
        input.clear()
        assertEquals(listOf("a"), result.orderedRecordIds)
        assertFailsWith<UnsupportedOperationException> { (result.attributes as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.orderedRecordIds as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.recordXp as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.recordXp.getValue("a") as MutableMap)[STR] = 999 }
        assertFailsWith<UnsupportedOperationException> { (result.durationByDate as MutableMap).clear() }
    }
    @Test fun reachingLevelFiftyKeepsThatDateActiveAndLaterAwardsBecomeZero() {
        val records = (0 until 765).map { index ->
            record("day-$index", 240, day.plusDays(index.toLong()), created = start.plusSeconds(index * 86400L))
        } + listOf(record("a-final", 150, day.plusDays(765), created = start.plusSeconds(765 * 86400L)),
            record("b-capped", 5, day.plusDays(765), created = start.plusSeconds(765 * 86400L)),
            record("next-day", 240, day.plusDays(766), created = start.plusSeconds(766 * 86400L)))
        val result = rebuild(records, start.plusSeconds(766 * 86400L))
        assertEquals(mapOf(STR to 30), result.recordXp.getValue("a-final"))
        assertEquals(mapOf(STR to 0), result.recordXp.getValue("b-capped"))
        assertEquals(mapOf(STR to 0), result.recordXp.getValue("next-day"))
        assertEquals(InactivityBalance(AttributeBalance(50, 0), 0), result.attributes.getValue(STR))
        assertEquals(GeneralBalance(16, 1), result.general)
        assertEquals(ClassAppearance("Guerrero", 2), result.appearance)
        assertEquals(1, rebuild(records, start.plusSeconds(767 * 86400L)).attributes.getValue(STR).inactiveDates)
    }
    @Test fun penaltiesCannotUndoLevelsOrGeneralProgressDuringEmptyDates() {
        val result = rebuild(listOf(record("a", 180)), start.plusSeconds(10 * 86400L))
        assertEquals(InactivityBalance(AttributeBalance(2, 0), 9), result.attributes.getValue(STR))
        assertEquals(GeneralBalance(2, 0), result.general)
        assertEquals(ClassAppearance("Guerrero", 1), result.appearance)
    }
}
