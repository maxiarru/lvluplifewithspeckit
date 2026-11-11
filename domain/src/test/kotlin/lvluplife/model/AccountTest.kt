package lvluplife.model

import java.time.Instant
import java.util.Locale
import kotlin.test.*

class AccountTest {
    private val owner = Uid("synthetic-owner-a")
    private val cycleId = CycleId("synthetic-cycle-a")
    private val now = Instant.parse("2026-11-10T15:00:00Z")

    @Test fun validAsciiNamesPreserveVisibleSpellingAndDeriveLowercaseKey() {
        for ((visible, key) in listOf("A" to "a", "_" to "_", "User_01" to "user_01", "ABCDEFGHIJKLMNO" to "abcdefghijklmno")) {
            val name = Username(visible)
            assertEquals(visible, name.value); assertEquals(key, name.key)
        }
    }

    @Test fun invalidNamesAreRejectedRatherThanTrimmedOrTransliterated() {
        for (value in listOf("", " ", " leading", "trailing ", "two words", "Álvaro", "a-b", "a.b", "a/b", "a\n", "a".repeat(16), "🙂")) {
            assertFailsWith<IllegalArgumentException> { Username(value) }
        }
    }

    @Test fun usernameKeyIsIndependentOfDeviceOrServerLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("i_mix", Username("I_MIX").key)
        } finally { Locale.setDefault(original) }
    }

    @Test fun startBindsAccountAndOpenCycleToTheProvidedUidAndInstant() {
        val start = Account.start(owner, "Visible_Name", cycleId, now)
        assertEquals(owner, start.account.uid); assertEquals(owner, start.cycle.uid)
        assertEquals(cycleId, start.account.currentCycleId); assertEquals(cycleId, start.cycle.id)
        assertEquals(now, start.account.createdAt); assertEquals(now, start.cycle.startedAt)
        assertEquals(Username("Visible_Name"), start.account.username)
        assertEquals(Revision(1), start.account.revision); assertEquals(1, start.account.schemaVersion)
        assertEquals(RulesVersion.CURRENT, start.cycle.rulesVersion)
        assertEquals(1, RulesVersion.CURRENT.value); assertNull(start.cycle.closedAt); assertTrue(start.cycle.isOpen)
        assertSame(InitialCharacter, start.character)
    }

    @Test fun initialCharacterIsNoviceWithSixLevelOneZeroExperienceAttributes() {
        assertEquals("Novato", InitialCharacter.className); assertEquals(1, InitialCharacter.stage)
        assertEquals(1, InitialCharacter.generalLevel); assertEquals(0, InitialCharacter.generalRemainder)
        assertEquals(setOf(Attribute.STR, Attribute.DEX, Attribute.INT, Attribute.WIT, Attribute.MEN, Attribute.CON), InitialCharacter.attributes.keys)
        assertEquals(6, InitialCharacter.attributes.size)
        InitialCharacter.attributes.values.forEach { assertEquals(1, it.level); assertEquals(0, it.xp) }
    }

    @Test fun initialAttributeCollectionCannotBeChangedToAffectOtherAccounts() {
        @Suppress("UNCHECKED_CAST")
        val attributes = InitialCharacter.attributes as MutableMap<Attribute, InitialAttribute>
        assertFailsWith<UnsupportedOperationException> { attributes.clear() }
        assertEquals(6, InitialCharacter.attributes.size); assertEquals(0, InitialCharacter.attributes.getValue(Attribute.STR).xp)
    }

    @Test fun explicitInputsMakeInitialStateDeterministicWithoutReadingClockOrGeneratingIds() {
        val first = Account.start(owner, "Player", cycleId, now)
        val second = Account.start(owner, "Player", cycleId, now)
        assertEquals(first.account, second.account); assertEquals(first.cycle, second.cycle)
        assertSame(first.character, second.character)
    }

    @Test fun differentOwnersHaveSeparateAccountsAndCyclesWithoutSharingPrivateFields() {
        val a = Account.start(owner, "Account_A", cycleId, now)
        val b = Account.start(Uid("synthetic-owner-b"), "Account_B", CycleId("synthetic-cycle-b"), now.plusSeconds(1))
        assertNotEquals(a.account.uid, b.account.uid); assertNotEquals(a.cycle.id, b.cycle.id)
        assertEquals("Account_A", a.account.username.value); assertEquals("Account_B", b.account.username.value)
        assertNotSame(a.account, b.account); assertNotSame(a.cycle, b.cycle)
        assertEquals(owner, a.cycle.uid); assertEquals(b.account.uid, b.cycle.uid)
    }

    @Test fun closedCycleKeepsOriginalIdentityStartAndRulesAndCannotCloseBeforeStart() {
        val open = Cycle(owner, cycleId, now, RulesVersion(2))
        assertTrue(open.isOpen); assertNull(open.closedAt)
        val closed = open.copy(closedAt = now.plusSeconds(60))
        assertFalse(closed.isOpen); assertEquals(open.uid, closed.uid); assertEquals(open.id, closed.id)
        assertEquals(open.startedAt, closed.startedAt); assertEquals(RulesVersion(2), closed.rulesVersion)
        assertFalse(open.copy(closedAt = now).isOpen)
        assertFailsWith<IllegalArgumentException> { open.copy(closedAt = now.minusNanos(1)) }
    }

    @Test fun invalidCycleIdsRulesAndAccountMetadataAreRejected() {
        for (id in listOf("", " ", ".", "..", "a/b", "a\n", "a".repeat(1501))) {
            assertFailsWith<IllegalArgumentException> { CycleId(id) }
        }
        for (version in listOf(0, -1)) assertFailsWith<IllegalArgumentException> { RulesVersion(version) }
        assertFailsWith<IllegalArgumentException> { Account(owner, Username("Valid"), now, cycleId, Revision(0)) }
        assertFailsWith<IllegalArgumentException> { Account(owner, Username("Valid"), now, cycleId, Revision(1), schemaVersion = 0) }
    }

    @Test fun suppliedRevisionAndRulesMetadataArePreservedWhenCreatingProfileAfterOtherReceipts() {
        val start = Account.start(owner, "Player", cycleId, now, revision = Revision(4), rulesVersion = RulesVersion(2))
        assertEquals(Revision(4), start.account.revision); assertEquals(RulesVersion(2), start.cycle.rulesVersion)
        assertFailsWith<IllegalArgumentException> { Account.start(owner, "Player", cycleId, now, revision = Revision(0)) }
    }
}
