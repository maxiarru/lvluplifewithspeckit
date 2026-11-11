package lvluplife.model

import java.time.Instant
import java.time.LocalDate
import kotlin.test.*
import lvluplife.model.Attribute.*

class ProgressionModelTest {
    private val start = Instant.parse("2026-11-10T15:00:00Z")
    private val now = Instant.parse("2026-11-11T15:00:00Z")
    private val owner = Uid("synthetic-owner")
    private val cycle = Cycle(owner, CycleId("synthetic-cycle"), start, RulesVersion.CURRENT)
    private val revision = Revision(7)
    private fun attributes() = Attribute.entries.associateWith { InactivityBalance(AttributeBalance(1, 0), 0) }
    private fun projection(attrs: Map<Attribute, InactivityBalance> = attributes(), general: GeneralBalance = GeneralBalance(1, 0),
        classId: String = "novato", stage: Int = 1, at: Instant = now, rev: Revision = revision, current: Cycle = cycle) =
        Progression.derived(current, rev, at, attrs, general, classId, stage)

    @Test fun snapshotRetainsExplicitMetadataAndSixAttributes() {
        val p = projection()
        assertEquals(owner, p.uid); assertEquals(cycle.id, p.cycleId); assertEquals(revision, p.revision)
        assertEquals(now, p.computedAt); assertEquals(LocalDate.parse("2026-11-11"), p.businessDate)
        assertEquals(attributes(), p.attributes); assertEquals(GeneralBalance(1, 0), p.general)
        assertEquals("novato", p.classId); assertEquals(1, p.stage)
    }
    @Test fun projectionRequiresAllSixAttributes() {
        for (missing in Attribute.entries) assertFailsWith<IllegalArgumentException> { projection(attributes() - missing) }
        assertFailsWith<IllegalArgumentException> { projection(emptyMap()) }
    }
    @Test fun callerCannotMutateSnapshotByChangingItsOriginalMap() {
        val input = attributes().toMutableMap(); val p = projection(input)
        input[STR] = InactivityBalance(AttributeBalance(1, 6), 0); input.remove(DEX)
        assertEquals(attributes(), p.attributes)
    }
    @Test fun exposedAttributeMapCannotBeModified() {
        val p = projection()
        assertFailsWith<UnsupportedOperationException> { (p.attributes as MutableMap).clear() }
        assertEquals(attributes(), p.attributes)
    }
    @Test fun revisionMustBePositiveForAPublishedProjection() {
        assertFailsWith<IllegalArgumentException> { projection(rev = Revision(0)) }
    }
    @Test fun snapshotCannotPredateItsCycleCreation() {
        assertFailsWith<IllegalArgumentException> { projection(at = start.minusNanos(1)) }
    }
    @Test fun closedCycleCannotBePublishedAsCurrentProgression() {
        assertFailsWith<IllegalArgumentException> { projection(current = cycle.copy(closedAt = now)) }
    }
    @Test fun businessDateUsesFixedUtcMinusThreeAtTheExactBoundary() {
        assertEquals(LocalDate.parse("2026-11-10"), projection(at = Instant.parse("2026-11-11T02:59:59.999999999Z")).businessDate)
        assertEquals(LocalDate.parse("2026-11-11"), projection(at = Instant.parse("2026-11-11T03:00:00Z")).businessDate)
    }
    @Test fun businessTimeHelperHasTheSameFixedBusinessDate() {
        assertEquals(LocalDate.parse("2026-11-10"), BusinessTime.date(Instant.parse("2026-11-11T02:00:00Z")))
        assertEquals(LocalDate.parse("2026-11-11"), BusinessTime.date(Instant.parse("2026-11-11T03:00:00Z")))
    }
    @Test fun initialSnapshotReusesCanonicalInitialCharacterAndCycleStart() {
        val p = Progression.initial(cycle, revision)
        assertEquals(cycle.uid, p.uid); assertEquals(cycle.id, p.cycleId); assertEquals(revision, p.revision)
        assertEquals(start, p.computedAt); assertEquals(LocalDate.parse("2026-11-10"), p.businessDate)
        assertEquals(attributes(), p.attributes); assertEquals(InitialCharacter.generalLevel, p.general.level)
        assertEquals(InitialCharacter.generalRemainder, p.general.remaining); assertEquals("novato", p.classId); assertEquals(InitialCharacter.stage, p.stage)
    }
    @Test fun separateCyclesKeepOwnerRevisionAndStartIndependent() {
        val other = Cycle(Uid("synthetic-other"), CycleId("synthetic-other-cycle"), now, RulesVersion.CURRENT)
        val a = Progression.initial(cycle, revision); val b = Progression.initial(other, Revision(1))
        assertEquals(cycle.id, a.cycleId); assertEquals(other.id, b.cycleId); assertNotEquals(a.uid, b.uid)
        assertEquals(Revision(7), a.revision); assertEquals(Revision(1), b.revision)
        assertEquals(start, a.computedAt); assertEquals(now, b.computedAt)
    }
    @Test fun attributeLevelsOutsideOneThroughFiftyAreInvalid() {
        for (level in listOf(0, -1, 51)) assertFailsWith<IllegalArgumentException> { AttributeBalance(level, 0) }
    }
    @Test fun attributeXpMustBeNormalizedAndNonnegative() {
        for ((level, xp) in listOf(1 to -1, 1 to 30, 2 to 60, 49 to 1470, 50 to 1))
            assertFailsWith<IllegalArgumentException> { AttributeBalance(level, xp) }
    }
    @Test fun validAttributeXpExtremesRemainRepresentable() {
        for ((level, xp) in listOf(1 to 0, 1 to 29, 2 to 59, 49 to 1469, 50 to 0)) {
            val a = AttributeBalance(level, xp)
            assertEquals(level, a.level); assertEquals(xp, a.xp)
        }
    }
    @Test fun canonicalAttributeThresholdsMatchLiteralSpecValues() {
        for ((level, expected) in listOf(1 to 30, 2 to 60, 10 to 300, 49 to 1470))
            assertEquals(expected, ProgressionThresholds.attribute(level))
        assertNull(ProgressionThresholds.attribute(50))
    }
    @Test fun canonicalGeneralThresholdsMatchEveryBandBoundary() {
        for ((level, expected) in listOf(1 to 1, 2 to 2, 4 to 2, 5 to 3, 8 to 3, 9 to 4, 14 to 4, 15 to 5, 49 to 5))
            assertEquals(expected, ProgressionThresholds.general(level))
        assertNull(ProgressionThresholds.general(50))
    }
    @Test fun invalidLevelsCannotBeUsedForThresholdLookup() {
        for (level in listOf(0, -1, 51)) {
            assertFailsWith<IllegalArgumentException> { ProgressionThresholds.attribute(level) }
            assertFailsWith<IllegalArgumentException> { ProgressionThresholds.general(level) }
        }
    }
    @Test fun generalLevelAndCounterMustBeNormalized() {
        for ((level, count) in listOf(0 to 0, 51 to 0, 1 to -1, 1 to 1, 2 to 2, 5 to 3, 9 to 4, 15 to 5, 50 to 1))
            assertFailsWith<IllegalArgumentException> { GeneralBalance(level, count) }
    }
    @Test fun validGeneralCounterExtremesRemainRepresentable() {
        for ((level, count) in listOf(1 to 0, 2 to 1, 4 to 1, 5 to 2, 8 to 2, 9 to 3, 14 to 3, 15 to 4, 49 to 4, 50 to 0))
            assertEquals(count, GeneralBalance(level, count).remaining)
    }
    @Test fun inactivityCounterCannotBeNegative() {
        assertFailsWith<IllegalArgumentException> { InactivityBalance(AttributeBalance(1, 0), -1) }
    }
    @Test fun inactivityDoesNotImposeAnUnspecifiedMaximumPeriod() {
        val a = InactivityBalance(AttributeBalance(1, 0), 10000)
        assertEquals(a, projection(attributes() + (STR to a)).attributes.getValue(STR))
    }
    @Test fun classIdentifierMustBeNonblankAndFreeOfControlCharacters() {
        for (id in listOf("", " ", "bad\nclass")) assertFailsWith<IllegalArgumentException> { projection(classId = id) }
    }
    @Test fun stagesMustExistAndNovatoOnlyAdmitsStageOne() {
        for (stage in listOf(0, -1, 4)) assertFailsWith<IllegalArgumentException> {
            projection(attributes() + (STR to InactivityBalance(AttributeBalance(44, 0), 0)), GeneralBalance(15, 0), "guerrero", stage)
        }
        for (stage in listOf(2, 3)) assertFailsWith<IllegalArgumentException> { projection(stage = stage) }
    }
    @Test fun derivedNonInitialClassRetainsStageAndGeneralCounter() {
        val attrs = attributes() + (STR to InactivityBalance(AttributeBalance(44, 0), 0))
        val p = projection(attrs, GeneralBalance(15, 0), "guerrero", 2)
        assertEquals("guerrero", p.classId); assertEquals(2, p.stage); assertEquals(GeneralBalance(15, 0), p.general)
    }
    @Test fun maximumLevelProjectionKeepsAllXpAndGeneralRemainderAtZero() {
        val attrs = Attribute.entries.associateWith { InactivityBalance(AttributeBalance(50, 0), 0) }
        val p = projection(attrs, GeneralBalance(50, 0), "heroe-integral", 3)
        assertTrue(p.attributes.values.all { it.attribute.xp == 0 }); assertEquals(0, p.general.remaining); assertEquals(3, p.stage)
    }
    @Test fun generalStateMustMatchTheAttributeLevelGainsBelowCap() {
        assertFailsWith<IllegalArgumentException> { projection(general = GeneralBalance(2, 0)) }
        val oneGain = attributes() + (STR to InactivityBalance(AttributeBalance(2, 0), 0))
        assertFailsWith<IllegalArgumentException> { projection(oneGain) }
        assertFailsWith<IllegalArgumentException> { projection(oneGain, GeneralBalance(2, 1), "guerrero") }
        val twoGains = oneGain + (DEX to InactivityBalance(AttributeBalance(2, 0), 0))
        assertEquals(GeneralBalance(2, 1), projection(twoGains, GeneralBalance(2, 1), "maestro-de-armas").general)
    }
    @Test fun maximumGeneralLevelRequiresEnoughAttributeLevelGains() {
        val insufficient = attributes() + (STR to InactivityBalance(AttributeBalance(50, 0), 0))
        assertFailsWith<IllegalArgumentException> { projection(insufficient, GeneralBalance(50, 0), "guerrero", 3) }
    }
}
