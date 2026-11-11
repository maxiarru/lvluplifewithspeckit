package lvluplife.activity

import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.*
import lvluplife.model.Attribute.*

/** Spec fixtures only. The five allowed attributes form every ordered distinct pair; no invented exclusions. */
class CustomActivityTest {
    private val owner = Uid("synthetic-custom-owner")
    private val id = ActivityId("synthetic-custom-id")
    private val now = Instant.parse("2026-11-11T15:00:00Z")
    private val allowed = listOf(STR, DEX, INT, WIT, MEN)
    private fun create(name: String = "Práctica personal", category: String = "Mi categoría", primary: Attribute = STR,
        secondary: Attribute? = null, uid: Uid = owner, activityId: ActivityId = id) =
        CustomActivity.create(uid, activityId, name, category, primary, secondary, now)

    @Test fun everyAllowedPrimaryCanOmitSecondary() {
        for (primary in allowed) {
            val activity = create(primary = primary)
            assertEquals(primary, activity.definition.primary); assertNull(activity.definition.secondary)
            assertEquals(ActivityMode.DURATION, activity.definition.mode)
        }
    }
    @Test fun allTwentyOrderedDistinctPairsAreCompatible() {
        for (primary in allowed) for (secondary in allowed.filter { it != primary }) {
            val activity = create(primary = primary, secondary = secondary)
            assertEquals(primary, activity.definition.primary); assertEquals(secondary, activity.definition.secondary)
        }
    }
    @Test fun constitutionCannotBePrincipalWithOrWithoutSecondary() {
        for (secondary in listOf(null) + Attribute.entries)
            assertFailsWith<IllegalArgumentException> { create(primary = CON, secondary = secondary) }
    }
    @Test fun constitutionCannotBeSecondaryForAnyAllowedPrincipal() {
        for (primary in allowed) assertFailsWith<IllegalArgumentException> { create(primary = primary, secondary = CON) }
    }
    @Test fun repeatedPrincipalAndSecondaryAreRejected() {
        for (primary in allowed) assertFailsWith<IllegalArgumentException> { create(primary = primary, secondary = primary) }
    }
    @Test fun oneCharacterNameIsAccepted() { assertEquals("A", create(name = "A").definition.name) }
    @Test fun fiftyCharacterNameIsAccepted() {
        val name = "A".repeat(50); assertEquals(name, create(name = name).definition.name)
    }
    @Test fun emptyOrWhitespaceOnlyTrimmedNamesAreRejected() {
        for (name in listOf("", " ", "\t\n  ", "\u2003\u2003"))
            assertFailsWith<IllegalArgumentException> { create(name = name) }
    }
    @Test fun fiftyOneCharactersAfterTrimmingAreRejected() {
        assertFailsWith<IllegalArgumentException> { create(name = "  " + "A".repeat(51) + "  ") }
    }
    @Test fun nameIsTrimmedBeforeCheckingLengthAndPersistingDefinition() {
        val name = "A".repeat(50)
        assertEquals(name, create(name = " \t" + name + "\n ").definition.name)
        assertEquals("Leer en voz alta", create(name = "  Leer en voz alta  ").definition.name)
    }
    @Test fun unicodeCharactersAreCountedAsCodePointsNotUtf16Units() {
        val name = "🧠".repeat(50)
        assertEquals(name, create(name = name).definition.name)
    }
    @Test fun fiftyOneUnicodeCharactersAreRejected() {
        assertFailsWith<IllegalArgumentException> { create(name = "🧠".repeat(51)) }
    }
    @Test fun categoryIsFreeTextAndHasNoInventedShortLimitOrNormalization() {
        for (category in listOf("Trabajo / estudio: propio 🎨", "  Mi categoría libre  ", "C".repeat(10_000)))
            assertEquals(category, create(category = category).definition.category)
    }
    @Test fun emptyOrBlankCategoryIsRejected() {
        for (category in listOf("", "  ", "\t\n")) assertFailsWith<IllegalArgumentException> { create(category = category) }
    }
    @Test fun identityOwnerAndCreationComeFromExplicitServerInputs() {
        val activity = create()
        assertEquals(owner, activity.uid); assertEquals(id, activity.id); assertEquals(now, activity.createdAt)
        assertEquals(id, activity.definition.id)
    }
    @Test fun mentalCustomActivityUsesOrdinaryDurationRuleRatherThanSleep() {
        val activity = create(primary = MEN)
        assertEquals(ActivityMode.DURATION, activity.definition.mode)
        assertEquals(ExperienceRule.DURATION, activity.definition.experienceRule)
        assertNotEquals(ActivityId("sleep"), activity.id)
    }
    @Test fun basicCatalogueIdentitiesCannotBeReusedForACustomDefinition() {
        for (basic in ActivityCatalog.activities)
            assertFailsWith<IllegalArgumentException> { create(activityId = basic.id) }
    }
    @Test fun savedDefinitionIsReusableAcrossRecordsAndCyclesWithoutChangingItsIdentity() {
        val activity = create(primary = INT, secondary = WIT)
        val first = ActivityRecord.create(RecordId("synthetic-first"), CycleId("synthetic-cycle-one"), activity.definition,
            LocalDate.parse("2026-11-11"), 30, now)
        val next = ActivityRecord.create(RecordId("synthetic-next"), CycleId("synthetic-cycle-two"), activity.definition,
            LocalDate.parse("2026-11-12"), 60, now.plusSeconds(86400))
        assertEquals(activity.id, first.activityId); assertEquals(activity.id, next.activityId)
        assertEquals(activity.definition, next.definition); assertEquals(owner, activity.uid); assertEquals(now, activity.createdAt)
    }
    @Test fun equalNamesAreNotAnUnspecifiedUniquenessConstraint() {
        val first = create(activityId = ActivityId("synthetic-first-definition"))
        val second = create(activityId = ActivityId("synthetic-second-definition"))
        assertNotEquals(first.id, second.id); assertEquals(first.definition.name, second.definition.name)
    }
    @Test fun sameLocalIdentityInDifferentOwnersHasNoGlobalSharedState() {
        val first = create(); val other = create(uid = Uid("synthetic-other-owner"))
        assertEquals(first.id, other.id); assertNotEquals(first.uid, other.uid)
    }
}
