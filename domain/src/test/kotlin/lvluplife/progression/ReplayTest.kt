package lvluplife.progression

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.*
import lvluplife.model.Attribute.*

/** Pure fixtures with explicit server instants, activity dates and current cycle; no test-side replay algorithm. */
class ReplayTest {
    private val day = LocalDate.parse("2026-11-11")
    private val now = Instant.parse("2026-11-11T21:00:00Z")
    private val created = Instant.parse("2026-11-11T15:00:00Z")
    private val cycleId = CycleId("synthetic-current-cycle")
    private val priorId = CycleId("synthetic-prior-cycle")
    private val owner = Uid("synthetic-owner")
    private fun cycle(start: String = "2026-11-01T15:00:00Z") = Cycle(owner, cycleId, Instant.parse(start), RulesVersion.CURRENT)
    private fun duration(id: String, minutes: Int = 30, date: LocalDate = day, at: Instant = created,
        cid: CycleId = cycleId, primary: Attribute = STR, secondary: Attribute? = null) =
        ActivityRecord.create(RecordId(id), cid,
            if (primary == STR && secondary == null) ActivityCatalog.find(ActivityId("strength"))!! else
                ActivityDefinition(ActivityId("synthetic-custom"), "Actividad de prueba", "Pruebas", ActivityMode.DURATION, primary, secondary),
            date, minutes, at)
    private fun sleep(id: String, minutes: Int = 30, date: LocalDate = day, cid: CycleId = cycleId) =
        ActivityRecord.create(RecordId(id), cid, ActivityCatalog.find(ActivityId("sleep"))!!, date, minutes, created)
    private fun meal(id: String, date: LocalDate = day) = ActivityRecord.create(RecordId(id), cycleId, ActivityCatalog.find(ActivityId("meal"))!!, date, null, created)
    private fun replay(records: List<ActivityRecord>, at: Instant = now, current: Cycle = cycle()) = ReplayEngine.rebuild(current, records, at)
    private fun balance(xp: Int = 0, inactive: Int = 0, level: Int = 1) = InactivityBalance(AttributeBalance(level, xp), inactive)
    private fun validate(hours: String, minutes: String, records: List<ActivityRecord> = emptyList(), date: LocalDate = day,
        cid: CycleId = cycleId, excluding: String? = null) =
        RecordValidation.durationMinutes(BigDecimal(hours), BigDecimal(minutes), cid, date, records, excluding?.let(::RecordId))

    // AC-17/18/124: the server's validation instant, not request/device time, decides UTC-3 dates.
    @Test fun todayYesterdayAndDayBeforeYesterdayAreAccepted() {
        for (date in listOf(day, day.minusDays(1), day.minusDays(2))) RecordValidation.activityDate(date, now)
    }
    @Test fun tomorrowAndThreeDaysAgoAreRejected() {
        for (date in listOf(day.plusDays(1), day.minusDays(3)))
            assertFailsWith<IllegalArgumentException> { RecordValidation.activityDate(date, now) }
    }
    @Test fun windowBeforeUtcThreeMidnightUsesPreviousBusinessDate() {
        val before = Instant.parse("2026-11-11T02:59:59Z")
        RecordValidation.activityDate(LocalDate.parse("2026-11-08"), before)
        assertFailsWith<IllegalArgumentException> { RecordValidation.activityDate(day, before) }
    }
    @Test fun requestedAnteayerExpiresAfterUtcThreeMidnightWithoutChangingRequestedDate() {
        val requested = LocalDate.parse("2026-11-08")
        RecordValidation.activityDate(requested, Instant.parse("2026-11-11T02:59:59.999Z"))
        assertFailsWith<IllegalArgumentException> { RecordValidation.activityDate(requested, Instant.parse("2026-11-11T03:00:00Z")) }
        assertEquals(LocalDate.parse("2026-11-08"), requested)
    }
    @Test fun newBusinessDateBecomesValidExactlyAtUtcThreeMidnight() {
        RecordValidation.activityDate(day, Instant.parse("2026-11-11T03:00:00Z"))
        RecordValidation.activityDate(day.minusDays(2), Instant.parse("2026-11-11T03:00:00Z"))
    }

    // AC-96/98/104/105: validate components before converting and share one daily duration budget.
    @Test fun validDurationComponentsHaveLiteralMinuteTotals() {
        for ((hours, minutes, expected) in listOf(Triple("0", "1", 1), Triple("0", "59", 59),
            Triple("1", "0", 60), Triple("3", "30", 210), Triple("24", "0", 1440)))
            assertEquals(expected, validate(hours, minutes))
    }
    @Test fun zeroAndNegativeDurationComponentsAreRejected() {
        for ((hours, minutes) in listOf("0" to "0", "-1" to "30", "1" to "-1"))
            assertFailsWith<IllegalArgumentException> { validate(hours, minutes) }
    }
    @Test fun fractionalHoursOrMinutesAreRejectedWithoutTruncation() {
        for ((hours, minutes) in listOf("0.5" to "0", "0" to "1.5", "1.25" to "30"))
            assertFailsWith<IllegalArgumentException> { validate(hours, minutes) }
    }
    @Test fun minuteComponentSixtyOrAboveIsNotNormalizedIntoHours() {
        for (minutes in listOf("60", "61", "120")) assertFailsWith<IllegalArgumentException> { validate("0", minutes) }
    }
    @Test fun hugeDurationCannotOverflowIntoAnAcceptedSmallValue() {
        for (hours in listOf("9223372036854775807", "1000000000000000000000000000000"))
            assertFailsWith<IllegalArgumentException> { validate(hours, "0") }
    }
    @Test fun twentyThreeHoursAllowExactlyOneMoreHourButNotSixtyOneMinutes() {
        val records = listOf(duration("existing", 1380))
        assertEquals(60, validate("1", "0", records))
        assertFailsWith<IllegalArgumentException> { validate("1", "1", records) }
    }
    @Test fun durationBeyondOneWholeDayIsRejectedEvenWithoutOtherRecords() {
        assertFailsWith<IllegalArgumentException> { validate("24", "1") }
    }
    @Test fun sleepAndOrdinaryActivityConsumeTheSameDurationBudget() {
        val records = listOf(duration("existing", 1320), sleep("sleep", 60))
        assertEquals(60, validate("1", "0", records))
        assertFailsWith<IllegalArgumentException> { validate("1", "1", records) }
    }
    @Test fun mealsConsumeNoDurationBudget() {
        val records = (1..8).map { meal("meal-$it") }
        assertEquals(1440, validate("24", "0", records))
    }
    @Test fun otherDatesAndPreviousCyclesDoNotConsumeCurrentDateBudget() {
        val records = listOf(duration("old-cycle", 1440, cid = priorId), duration("yesterday", 1440, date = day.minusDays(1)))
        assertEquals(1440, validate("24", "0", records))
    }
    @Test fun deletedRecordsReleaseDurationAndReplacementExcludesOriginal() {
        val original = duration("original", 180)
        val records = listOf(original, duration("other", 1260), duration("deleted", 60).markDeleted(created.plusSeconds(60)))
        assertEquals(120, validate("2", "0", records, excluding = "original"))
        assertFailsWith<IllegalArgumentException> { validate("0", "1", records) }
    }
    @Test fun deletedDurationAloneCannotConsumeTheLastAvailableHour() {
        val records = listOf(duration("active", 1380), duration("deleted", 60).markDeleted(created.plusSeconds(60)))
        assertEquals(60, validate("1", "0", records))
    }
    @Test fun threeHoursAtTwentyThreeOClockAreNotLimitedByTimeUntilMidnight() {
        val lateNow = Instant.parse("2026-11-12T02:00:00Z") // 23:00 on Nov 11, UTC-3.
        RecordValidation.activityDate(day, lateNow)
        assertEquals(180, validate("3", "0"))
    }
    @Test fun twoAdmissionsUnderOneHourOfRemainingBudgetCannotBothConsumeIt() {
        // Models successive snapshots; the actual concurrent commit is covered in T038.
        val existing = listOf(duration("existing", 1380))
        val accepted = validate("1", "0", existing)
        assertEquals(60, accepted)
        assertFailsWith<IllegalArgumentException> { validate("1", "0", existing + duration("first", accepted)) }
    }

    // Replay: ordered reconstruction, original creation metadata, tombstones and per-cycle counters.
    @Test fun oneSimpleActivityGivesOnlyItsOwnAttributeXpOnAnOpenDate() {
        val result = replay(listOf(duration("strength")), current = cycle("2026-11-11T12:00:00Z"))
        assertEquals(Attribute.entries.associateWith { balance() } + (STR to balance(6)), result.attributes)
        assertEquals(GeneralBalance(1, 0), result.general); assertEquals(ClassAppearance("Novato", 1), result.appearance)
    }
    @Test fun activityDateIsSortedBeforeCreationTime() {
        val old = duration("older-date", date = day.minusDays(1), at = Instant.parse("2026-11-11T20:00:00Z"))
        val current = duration("today", at = Instant.parse("2026-11-11T12:00:00Z"))
        assertEquals(listOf("older-date", "today"), replay(listOf(current, old)).orderedRecordIds)
    }
    @Test fun creationTimeOrdersRecordsWithinSameActivityDate() {
        val earlier = duration("z-earlier", 5, at = Instant.parse("2026-11-11T12:00:00Z"))
        val later = duration("a-later", 240, at = Instant.parse("2026-11-11T13:00:00Z"))
        val result = replay(listOf(later, earlier))
        assertEquals(listOf("z-earlier", "a-later"), result.orderedRecordIds)
        assertEquals(mapOf(STR to 1), result.recordXp.getValue("z-earlier"))
        assertEquals(mapOf(STR to 47), result.recordXp.getValue("a-later"))
    }
    @Test fun equalDatesAndCreationInstantsUseOrdinalIdsRatherThanNumericOrder() {
        val a = duration("record-10", 5); val b = duration("record-2", 240)
        val result = replay(listOf(b, a))
        assertEquals(listOf("record-10", "record-2"), result.orderedRecordIds)
        assertEquals(mapOf(STR to 1), result.recordXp.getValue(a.id.value)); assertEquals(mapOf(STR to 47), result.recordXp.getValue(b.id.value))
        assertEquals(result, replay(listOf(a, b))); assertEquals(result, replay(listOf(b, a)))
    }
    @Test fun ordinalIdsDistinguishUpperAndLowerCase() {
        assertEquals(listOf("A", "a"), replay(listOf(duration("a"), duration("A"))).orderedRecordIds)
    }
    @Test fun editedValuesKeepTheirOriginalCreationOrder() {
        val original = duration("earlier-created", 5, at = Instant.parse("2026-11-11T12:00:00Z"))
        val edited = original.correct(day, durationMinutes = 240, now = original.createdAt.plusSeconds(60))
        val later = duration("later-created", 30, at = Instant.parse("2026-11-11T13:00:00Z"))
        val result = replay(listOf(later, edited))
        assertEquals(listOf(original.id.value, later.id.value), result.orderedRecordIds)
        assertEquals(mapOf(STR to 48), result.recordXp.getValue(original.id.value)); assertEquals(mapOf(STR to 0), result.recordXp.getValue(later.id.value))
        assertEquals(original.createdAt, edited.createdAt)
    }
    @Test fun tombstonesAreExcludedFromXpAndDurationAndOutputOrder() {
        val deleted = duration("deleted", 240).markDeleted(created.plusSeconds(60))
        val result = replay(listOf(deleted, duration("kept")))
        assertEquals(listOf("kept"), result.orderedRecordIds); assertFalse(result.recordXp.containsKey(deleted.id.value))
        assertEquals(6, result.recordXp.getValue("kept").getValue(STR)); assertEquals(30, result.durationByDate.getValue(day))
    }
    @Test fun previousCycleRecordsCannotConsumeCurrentCycleXpOrMinutes() {
        val result = replay(listOf(duration("prior", 1440, cid = priorId), duration("current")))
        assertEquals(listOf("current"), result.orderedRecordIds)
        assertEquals(mapOf(STR to 6), result.recordXp.getValue("current")); assertEquals(30, result.durationByDate.getValue(day))
    }
    @Test fun fifthMealRemainsInReplayWithZeroXpAndNoMinutes() {
        val result = replay((1..5).reversed().map { meal("meal-$it") })
        assertEquals((1..5).map { "meal-$it" }, result.orderedRecordIds)
        assertEquals(listOf(7, 7, 7, 7, 0), result.orderedRecordIds.map { result.recordXp.getValue(it).getValue(CON) })
        assertEquals(28, result.attributes.getValue(CON).attribute.xp)
        assertEquals(0, result.durationByDate[day] ?: 0)
    }
    @Test fun mealsOnAnotherActivityDateDoNotConsumeTodaysFourMealBudget() {
        val yesterday = (1..4).map { meal("yesterday-$it", day.minusDays(1)) }
        val result = replay(yesterday + meal("today"))
        assertEquals(mapOf(CON to 7), result.recordXp.getValue("today"))
        assertEquals(AttributeBalance(2, 5), result.attributes.getValue(CON).attribute)
    }
    @Test fun twoHalfHourSleepRecordsOnOneDateGiveZeroThenThreeXp() {
        val result = replay(listOf(sleep("sleep-a"), sleep("sleep-b")))
        assertEquals(mapOf(MEN to 0), result.recordXp.getValue("sleep-a")); assertEquals(mapOf(MEN to 3), result.recordXp.getValue("sleep-b"))
        assertEquals(60, result.durationByDate.getValue(day))
    }
    @Test fun sleepRemaindersDoNotCrossActivityDates() {
        val result = replay(listOf(sleep("yesterday", date = day.minusDays(1)), sleep("today")))
        assertEquals(mapOf(MEN to 0), result.recordXp.getValue("yesterday")); assertEquals(mapOf(MEN to 0), result.recordXp.getValue("today"))
        assertEquals(0, result.attributes.getValue(MEN).attribute.xp)
    }
    @Test fun priorCycleSleepCannotCompleteACurrentCycleHalfHour() {
        val result = replay(listOf(sleep("old", cid = priorId), sleep("current")))
        assertEquals(listOf("current"), result.orderedRecordIds)
        assertEquals(mapOf(MEN to 0), result.recordXp.getValue("current"))
        assertEquals(30, result.durationByDate.getValue(day))
    }
    @Test fun serverResolvedCustomMentalActivityUsesOrdinaryRule() {
        val result = replay(listOf(duration("custom", primary = MEN)))
        assertEquals(mapOf(MEN to 6), result.recordXp.getValue("custom"))
    }
    @Test fun queryRebuildsHistoricalRecordsWithoutApplyingNewRecordDateWindow() {
        val historical = duration("historical", 60, LocalDate.parse("2026-11-01"), Instant.parse("2026-11-01T15:00:00Z"))
        val result = replay(listOf(historical), at = Instant.parse("2026-11-08T15:00:00Z"))
        assertEquals(balance(12, 6), result.attributes.getValue(STR))
    }
    @Test fun backdatedActivityReversesThePenaltyItsActivityDateAvoids() {
        val first = duration("initial", 60, LocalDate.parse("2026-11-01"), Instant.parse("2026-11-01T15:00:00Z"))
        val queryAt = Instant.parse("2026-11-09T15:00:00Z")
        assertEquals(balance(7, 7), replay(listOf(first), queryAt).attributes.getValue(STR))
        val backdated = duration("backdated", 30, LocalDate.parse("2026-11-08"), Instant.parse("2026-11-09T14:00:00Z"))
        assertEquals(balance(18, 0), replay(listOf(first, backdated), queryAt).attributes.getValue(STR))
    }
    @Test fun businessDateAndFirstPenaltyChangeAtUtcThreeMidnight() {
        val current = cycle("2026-11-03T15:00:00Z")
        val record = duration("initial", 60, LocalDate.parse("2026-11-03"), Instant.parse("2026-11-03T15:00:00Z"))
        val before = replay(listOf(record), Instant.parse("2026-11-11T02:59:59Z"), current)
        val after = replay(listOf(record), Instant.parse("2026-11-11T03:00:00Z"), current)
        assertEquals(LocalDate.parse("2026-11-10"), before.businessDate); assertEquals(balance(12, 6), before.attributes.getValue(STR))
        assertEquals(day, after.businessDate); assertEquals(balance(7, 7), after.attributes.getValue(STR))
        assertEquals(after, replay(listOf(record), Instant.parse("2026-11-11T03:00:00Z"), current))
    }
    @Test fun lateCreationDateStillCountsAsFirstInactiveDateWhenItCloses() {
        val result = replay(emptyList(), Instant.parse("2026-11-02T03:00:00Z"), cycle("2026-11-02T02:30:00Z"))
        assertEquals(Attribute.entries.associateWith { balance(inactive = 1) }, result.attributes)
    }
    @Test fun resetCycleAllowsEarlierActivityXpButCountsNoPreCycleInactivity() {
        val monday = cycle("2026-11-09T15:00:00Z")
        val sunday = duration("sunday", 30, LocalDate.parse("2026-11-08"), Instant.parse("2026-11-09T16:00:00Z"))
        val result = replay(listOf(sunday), Instant.parse("2026-11-10T15:00:00Z"), monday)
        assertEquals(Attribute.entries.associateWith { balance(inactive = 1) } + (STR to balance(6, 1)), result.attributes)
        assertEquals(mapOf(STR to 6), result.recordXp.getValue(sunday.id.value))
    }
}
