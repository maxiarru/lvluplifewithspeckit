package lvluplife.model

import kotlin.test.*

class IdentityTest {
    @Test fun uidPreservesIdentityButRejectsUnsafeDocumentKeys() {
        assertEquals("Account_A", Uid("Account_A").value)
        for (invalid in listOf("", " ", "a/b", ".", "..", "x".repeat(129), "a\nb")) {
            assertFailsWith<IllegalArgumentException>(invalid) { Uid(invalid) }
        }
    }

    @Test fun operationIdUsesCanonicalUuidWithoutTruncatedGroups() {
        val id = "123e4567-e89b-42d3-a456-426614174000"
        assertEquals(id, OperationId.parse(id.uppercase()).value)
        listOf("not-uuid", "1-1-1-1-1", "$id/other").forEach {
            assertFailsWith<IllegalArgumentException> { OperationId.parse(it) }
        }
    }

    @Test fun revisionIsMonotonicAndCannotOverflow() {
        assertEquals(Revision(1), Revision(0).next())
        assertFailsWith<IllegalArgumentException> { Revision(-1) }
        assertFailsWith<ArithmeticException> { Revision(Long.MAX_VALUE).next() }
    }
}
