package lvluplife.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.model.*

class RevisionTransactionSafetyTest {
    private val now = Instant.parse("2026-11-10T15:00:00Z")
    private fun uid() = Uid("synthetic-transaction-${UUID.randomUUID()}")
    private fun command() = CommandEnvelope(OperationId.create(), CommandType.CREATE_RECORD, "synthetic-cycle",
        ObjectMapper().readTree("""{"activityId":"strength","activityDate":"2026-11-10","hours":0,"minutes":30}"""))
    private fun open(): ServerResources {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        return ServerResources.open(config).also {
            assertEquals("127.0.0.1:8080", it.firestore.options.host)
            assertEquals("127.0.0.1:8080", it.firestore.options.emulatorHost)
        }
    }
    private fun plan(path: List<String> = listOf("synthetic", "effect"), root: Map<String, Any?> = emptyMap()) =
        OperationDecision.Confirmed(root, listOf(DocumentWrite(path, mapOf("marker" to "original"))), mapOf("ok" to true))

    @Test fun invalidPathsReservedReceiptsAndManualRevisionCannotLeavePartialWrites() = open().use { resources ->
        val store = OperationStore(resources.firestore)
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        val invalid = listOf(plan(emptyList()), plan(listOf("synthetic", "a/b")), plan(listOf("synthetic", "..")),
            plan(listOf("operations", OperationId.create().value)), plan(root = mapOf("revision" to 999L)))
        for (candidate in invalid) {
            val owner = uid(); val command = command()
            assertFailsWith<PersistenceUnavailable> { tx.commit(owner, command, null) { _, _ -> candidate } }
            assertNull(resources.documents.read(owner, emptyList()))
            assertNull(resources.documents.read(owner, listOf("synthetic", "effect")))
            assertNull(store.find(owner, command.operationId))
        }
    }

    @Test fun malformedOrOverflowingRootRevisionCannotBeReplaced() = open().use { resources ->
        val store = OperationStore(resources.firestore)
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        for (root in listOf(mapOf("marker" to "original"), mapOf("revision" to -1L), mapOf("revision" to Long.MAX_VALUE))) {
            val owner = uid(); val command = command()
            resources.documents.write(owner, emptyList(), root)
            val expected = if (root["revision"] == Long.MAX_VALUE) Revision(Long.MAX_VALUE) else null
            assertFailsWith<PersistenceUnavailable> { tx.commit(owner, command, expected) { _, _ -> plan() } }
            assertEquals(root, resources.documents.read(owner, emptyList()))
            assertNull(store.find(owner, command.operationId))
            assertNull(resources.documents.read(owner, listOf("synthetic", "effect")))
        }
    }

    @Test fun replayAndFailedPreconditionsDoNotReadClockOrReevaluateDecision() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid(); val command = command()
        val clockReads = AtomicInteger()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { clockReads.incrementAndGet(); now })
        val original = tx.commit(owner, command, null) { _, instant -> assertEquals(now, instant); plan() }
        assertEquals(original, tx.commit(owner, command, null) { _, _ -> error("No replay evaluation") })
        assertFailsWith<RevisionChanged> { tx.commit(owner, command(), Revision(0)) { _, _ -> error("No obsolete evaluation") } }
        assertFailsWith<PreconditionChanged> {
            tx.commit(owner, command(), Revision(1), listOf(DocumentCondition(listOf("synthetic", "effect"), null))) { _, _ -> error("No stale evaluation") }
        }
        val collision = assertFailsWith<ApiException> {
            tx.commit(owner, command.copy(cycleId = "another-cycle"), Revision(1)) { _, _ -> error("No collision evaluation") }
        }
        assertEquals(ApiError.OPERATION_ID_REUSED, collision.error)
        assertEquals(1, clockReads.get()); assertEquals(original, store.find(owner, command.operationId))
    }

    @Test fun unavailableTransactionReturnsSafeTransientFailureWithoutDecision() {
        val resources = open(); val store = OperationStore(resources.firestore)
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        val owner = uid(); val command = command(); resources.close()
        val failure = assertFailsWith<PersistenceUnavailable> { tx.commit(owner, command, null) { _, _ -> error("No unavailable evaluation") } }
        assertNull(failure.cause)
        assertFalse(failure.message.orEmpty().contains(owner.value)); assertFalse(failure.message.orEmpty().contains(command.operationId.value))
    }
}
