package lvluplife.model

import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.Attribute.*

class ActivityModelTest {
    private val date = LocalDate.parse("2026-11-11")
    private val created = Instant.parse("2026-11-11T15:00:00Z")
    private val id = RecordId("synthetic-record")
    private val cycle = CycleId("synthetic-cycle")
    private fun definition(id: String) = assertNotNull(ActivityCatalog.find(ActivityId(id)))
    private fun record(activity: String = "strength", minutes: Int? = 30) =
        ActivityRecord.create(id, cycle, definition(activity), date, minutes, created)
    private fun custom(primary: Attribute = MEN, secondary: Attribute? = null, rule: ExperienceRule = ExperienceRule.DURATION,
        mode: ActivityMode = ActivityMode.DURATION, name: String = "Práctica", category: String = "Personal", aid: String = "synthetic-custom") =
        ActivityDefinition(ActivityId(aid), name, category, mode, primary, secondary, rule)

    @Test fun exactElevenBasicActivitiesMatchApprovedData() {
        val expected = listOf(
            listOf("strength", "Entrenamiento de fuerza", "Actividad física", "DURATION", "STR", null, "DURATION"),
            listOf("aerobic", "Actividad aeróbica", "Actividad física", "DURATION", "DEX", null, "DURATION"),
            listOf("meal", "Comida saludable", "Nutrición", "OCCURRENCE", "CON", null, "MEAL"),
            listOf("study", "Leer / estudiar", "Aprendizaje", "DURATION", "INT", null, "DURATION"),
            listOf("hobby", "Ocio / hobby", "Bienestar", "DURATION", "WIT", null, "DURATION"),
            listOf("sleep", "Dormir", "Descanso", "DURATION", "MEN", null, "SLEEP"),
            listOf("crossfit", "CrossFit", "Actividad física", "DURATION", "STR", "DEX", "DURATION"),
            listOf("martial-arts", "Artes marciales", "Actividad física", "DURATION", "DEX", "STR", "DURATION"),
            listOf("yoga", "Yoga", "Bienestar físico", "DURATION", "WIT", "DEX", "DURATION"),
            listOf("practical-skill", "Aprender una habilidad práctica", "Aprendizaje", "DURATION", "INT", "DEX", "DURATION"),
            listOf("strategy-games", "Juegos de estrategia", "Ocio / aprendizaje", "DURATION", "WIT", "INT", "DURATION"))
        val actual = ActivityCatalog.activities.map { listOf(it.id.value, it.name, it.category, it.mode.name, it.primary.name, it.secondary?.name, it.experienceRule.name) }
        assertEquals(expected, actual); assertEquals(11, ActivityCatalog.activities.map { it.id }.toSet().size)
    }
    @Test fun unknownActivityDoesNotFallBackToABasicDefinition() { assertNull(ActivityCatalog.find(ActivityId("synthetic-unknown"))) }
    @Test fun catalogCannotBeChangedByItsCaller() {
        val before = ActivityCatalog.activities.toList()
        assertFailsWith<UnsupportedOperationException> { (ActivityCatalog.activities as MutableList).clear() }
        assertEquals(before, ActivityCatalog.activities)
    }
    @Test fun copyingADefinitionCannotModifyCanonicalCatalogEntry() {
        val original = definition("strength")
        original.copy(name = "Otro nombre", primary = DEX)
        assertEquals(original, definition("strength")); assertEquals(STR, definition("strength").primary)
    }
    @Test fun allDifferentCustomAttributePairsIncludingMentalUseOrdinaryDuration() {
        val attributes = listOf(STR, DEX, INT, WIT, MEN)
        for (primary in attributes) for (secondary in attributes.filter { it != primary }) {
            val snapshot = custom(primary, secondary)
            val r = ActivityRecord.create(id, cycle, snapshot, date, 30, created)
            assertEquals(snapshot, r.definition); assertEquals(ExperienceRule.DURATION, r.definition.experienceRule)
        }
    }
    @Test fun constitutionCannotBeUsedByCustomDurationDefinitions() {
        assertFailsWith<IllegalArgumentException> { custom(CON) }
        assertFailsWith<IllegalArgumentException> { custom(STR, CON) }
    }
    @Test fun duplicatePrimaryAndSecondaryIsInvalid() { assertFailsWith<IllegalArgumentException> { custom(STR, STR) } }
    @Test fun customDefinitionsCannotImpersonateSleepOrMealRules() {
        assertFailsWith<IllegalArgumentException> { custom(rule = ExperienceRule.SLEEP) }
        assertFailsWith<IllegalArgumentException> { custom(CON, rule = ExperienceRule.MEAL, mode = ActivityMode.OCCURRENCE) }
        assertFailsWith<IllegalArgumentException> { custom(aid = "sleep") }
        assertFailsWith<IllegalArgumentException> { custom(STR, aid = "meal") }
    }
    @Test fun sleepAndMealDefinitionsRequireTheirCanonicalAttributeAndMode() {
        assertFailsWith<IllegalArgumentException> { custom(STR, rule = ExperienceRule.SLEEP, aid = "sleep") }
        assertFailsWith<IllegalArgumentException> { custom(MEN, DEX, ExperienceRule.SLEEP, aid = "sleep") }
        assertFailsWith<IllegalArgumentException> { custom(STR, rule = ExperienceRule.MEAL, mode = ActivityMode.OCCURRENCE, aid = "meal") }
        assertFailsWith<IllegalArgumentException> { custom(CON, rule = ExperienceRule.MEAL, aid = "meal") }
    }
    @Test fun customOccurrenceModalityIsInvalid() { assertFailsWith<IllegalArgumentException> { custom(mode = ActivityMode.OCCURRENCE) } }
    @Test fun definitionsRequireNormalizedNonemptyNameAndCategory() {
        for (name in listOf("", " ", " leading", "trailing ", "a".repeat(51))) assertFailsWith<IllegalArgumentException> { custom(name = name) }
        for (category in listOf("", " ")) assertFailsWith<IllegalArgumentException> { custom(category = category) }
    }
    @Test fun definitionNameLengthExtremesAreAccepted() {
        for (name in listOf("x", "a".repeat(50), "🙂".repeat(50))) assertEquals(name, ActivityRecord.create(id, cycle, custom(name = name), date, 1, created).definition.name)
    }
    @Test fun categoryIsFreeNonemptyTextWithoutAnInventedTrimOrLengthLimit() {
        for (category in listOf(" Personal ", "a".repeat(200))) {
            val r = ActivityRecord.create(id, cycle, custom(category = category), date, 1, created)
            assertEquals(category, r.definition.category)
        }
    }
    @Test fun activityAndRecordIdsCannotEscapeDocumentPaths() {
        for (bad in listOf("", " ", ".", "..", "a/b", "bad\n", "__reserved__", "é".repeat(751))) {
            assertFailsWith<IllegalArgumentException> { ActivityId(bad) }
            assertFailsWith<IllegalArgumentException> { RecordId(bad) }
        }
    }
    @Test fun durationRecordKeepsNormalizedMinutesIdentityAndDefinition() {
        val r = record()
        assertEquals(id, r.id); assertEquals(cycle, r.cycleId); assertEquals(ActivityId("strength"), r.activityId)
        assertEquals(definition("strength"), r.definition); assertEquals(30, r.durationMinutes); assertEquals(date, r.activityDate)
        assertEquals(created, r.createdAt); assertEquals(created, r.updatedAt); assertEquals(1L, r.version); assertNull(r.deletedAt)
    }
    @Test fun sleepIsStillADurationRecord() { assertEquals(60, record("sleep", 60).durationMinutes) }
    @Test fun mealRecordHasNoDuration() { val r = record("meal", null); assertNull(r.durationMinutes); assertEquals(CON, r.definition.primary) }
    @Test fun ordinaryAndSleepDurationAreRequiredAndPositiveWithinOneDay() {
        for (activity in listOf("strength", "sleep")) for (minutes in listOf(null, 0, -1, 1441))
            assertFailsWith<IllegalArgumentException> { record(activity, minutes) }
    }
    @Test fun singleMinuteAndWholeDayAreValidNormalizedDurations() {
        for (minutes in listOf(1, 1440)) assertEquals(minutes, record(minutes = minutes).durationMinutes)
    }
    @Test fun mealCannotCarryAnyDurationIncludingZero() {
        for (minutes in listOf(0, 1, 30)) assertFailsWith<IllegalArgumentException> { record("meal", minutes) }
    }
    @Test fun correctionPreservesIdCycleAndCreationButChangesVersionAndCurrentValues() {
        val original = record(); val changed = original.correct(date.minusDays(1), definition("crossfit"), 45, created.plusSeconds(60))
        assertEquals(original.id, changed.id); assertEquals(original.cycleId, changed.cycleId); assertEquals(original.createdAt, changed.createdAt)
        assertEquals(date.minusDays(1), changed.activityDate); assertEquals(ActivityId("crossfit"), changed.activityId)
        assertEquals(45, changed.durationMinutes); assertEquals(2L, changed.version); assertEquals(created.plusSeconds(60), changed.updatedAt)
        assertEquals(30, original.durationMinutes); assertEquals(1L, original.version); assertEquals(date, original.activityDate)
    }
    @Test fun durationCorrectionCanSwitchToSleepWithoutChangingModality() {
        val changed = record().correct(date, definition("sleep"), 60, created.plusSeconds(60))
        assertEquals(ExperienceRule.SLEEP, changed.definition.experienceRule); assertEquals(60, changed.durationMinutes)
    }
    @Test fun mealCorrectionOnlyChangesDateAndKeepsNoDuration() {
        val original = record("meal", null); val changed = original.correct(date.minusDays(1), now = created.plusSeconds(60))
        assertEquals(original.definition, changed.definition); assertNull(changed.durationMinutes); assertEquals(original.createdAt, changed.createdAt)
    }
    @Test fun correctionCannotConvertBetweenMealAndDuration() {
        assertFailsWith<IllegalArgumentException> { record().correct(date, definition("meal"), null, created.plusSeconds(60)) }
        assertFailsWith<IllegalArgumentException> { record("meal", null).correct(date, definition("strength"), 30, created.plusSeconds(60)) }
    }
    @Test fun mealCorrectionCannotReplaceItsResolvedDefinition() {
        val original = record("meal", null)
        assertFailsWith<IllegalArgumentException> { original.correct(date, original.definition.copy(name = "Otro nombre"), null, created.plusSeconds(60)) }
    }
    @Test fun tombstonePreservesRecordIdentityAndValuesAndAdvancesVersion() {
        val original = record(); val deleted = original.markDeleted(created.plusSeconds(120))
        assertEquals(original.id, deleted.id); assertEquals(original.cycleId, deleted.cycleId); assertEquals(original.createdAt, deleted.createdAt)
        assertEquals(original.definition, deleted.definition); assertEquals(original.activityDate, deleted.activityDate)
        assertEquals(original.durationMinutes, deleted.durationMinutes); assertEquals(2L, deleted.version)
        assertEquals(created.plusSeconds(120), deleted.deletedAt); assertEquals(deleted.deletedAt, deleted.updatedAt); assertNull(original.deletedAt)
    }
    @Test fun tombstoneCannotBeCorrectedOrDeletedAgain() {
        val deleted = record().markDeleted(created.plusSeconds(60))
        assertFailsWith<IllegalStateException> { deleted.correct(date, now = created.plusSeconds(120)) }
        assertFailsWith<IllegalStateException> { deleted.markDeleted(created.plusSeconds(120)) }
    }
    @Test fun updatesCannotPredateCreationOrPreviousUpdate() {
        assertFailsWith<IllegalArgumentException> { record().correct(date, now = created.minusSeconds(1)) }
        val changed = record().correct(date, now = created.plusSeconds(60))
        assertFailsWith<IllegalArgumentException> { changed.markDeleted(created.plusSeconds(59)) }
    }
    @Test fun restoringStorageRequiresPositiveVersionAndConsistentTimestamps() {
        val d = definition("strength")
        assertFailsWith<IllegalArgumentException> { ActivityRecord.restore(id, cycle, d, date, 30, created, created, 0, null) }
        assertFailsWith<IllegalArgumentException> { ActivityRecord.restore(id, cycle, d, date, 30, created, created.minusSeconds(1), 1, null) }
        assertFailsWith<IllegalArgumentException> { ActivityRecord.restore(id, cycle, d, date, 30, created, created.plusSeconds(60), 2, created.plusSeconds(30)) }
    }
    @Test fun restoredRecordRetainsVersionAndDeletionMetadataWithoutAllocatingIdentity() {
        val deleted = created.plusSeconds(60)
        val r = ActivityRecord.restore(id, cycle, definition("strength"), date, 30, created, deleted, 7, deleted)
        assertEquals(id, r.id); assertEquals(7L, r.version); assertEquals(deleted, r.deletedAt); assertEquals(created, r.createdAt)
    }
    @Test fun versionCannotOverflowDuringCorrectionOrDeletion() {
        val r = ActivityRecord.restore(id, cycle, definition("strength"), date, 30, created, created, Long.MAX_VALUE, null)
        assertFailsWith<ArithmeticException> { r.correct(date, now = created.plusSeconds(1)) }
        assertFailsWith<ArithmeticException> { r.markDeleted(created.plusSeconds(1)) }
    }
}
