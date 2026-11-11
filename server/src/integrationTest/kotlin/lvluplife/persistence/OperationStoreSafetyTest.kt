package lvluplife.persistence

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.*
import lvluplife.api.*
import lvluplife.model.*

class OperationStoreSafetyTest {
    private fun uid() = Uid("synthetic-receipt-${UUID.randomUUID()}")
    private fun open(): ServerResources {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        return ServerResources.open(config).also {
            assertEquals("127.0.0.1:8080", it.firestore.options.host)
            assertEquals("127.0.0.1:8080", it.firestore.options.emulatorHost)
        }
    }
    private fun receipt() = OperationReceipt(OperationId.create(), CommandType.CREATE_RECORD, "0".repeat(64),
        ReceiptOutcome.CONFIRMED, Instant.parse("2026-11-10T15:00:00.123456789Z"), Revision(1), "synthetic-cycle",
        result = mapOf("marker" to "original"))

    @Test fun createCannotOverwriteEvenWhenCallerSkipsRead() = open().use { resources ->
        val owner = uid(); val original = receipt(); val store = OperationStore(resources.firestore)
        resources.firestore.runTransaction { tx -> store.create(tx, owner, original) }.get(15, TimeUnit.SECONDS)
        assertFailsWith<ExecutionException> {
            resources.firestore.runTransaction { tx -> store.create(tx, owner, original.copy(result = mapOf("marker" to "changed"))) }
                .get(15, TimeUnit.SECONDS)
        }
        assertEquals(original, store.find(owner, original.operationId))
    }

    @Test fun corruptExistingReceiptIsUnavailableRatherThanAbsent() = open().use { resources ->
        val owner = uid(); val id = OperationId.create(); val store = OperationStore(resources.firestore)
        // Deliberately malformed low-level fixture in an isolated emulator, never application data.
        resources.documents.write(owner, listOf("operations", id.value), mapOf("outcome" to "CONFIRMED"))
        val failure = assertFailsWith<PersistenceUnavailable> { store.find(owner, id) }
        assertNull(failure.cause); assertFalse(failure.message.orEmpty().contains(owner.value))
        assertFalse(failure.message.orEmpty().contains(id.value))
    }

    @Test fun unavailableReadDoesNotLeakPrivatePathsOrBecomeAbsence() {
        val resources = open(); val owner = uid(); val id = OperationId.create()
        val store = OperationStore(resources.firestore); resources.close()
        val failure = assertFailsWith<PersistenceUnavailable> { store.find(owner, id) }
        assertNull(failure.cause); assertFalse(failure.message.orEmpty().contains(owner.value))
        assertFalse(failure.message.orEmpty().contains(id.value))
    }

    @Test fun credentialsCannotBeStoredInsideReceiptResult() = open().use { resources ->
        val owner = uid(); val original = receipt(); val store = OperationStore(resources.firestore)
        val invalid = original.copy(result = mapOf("details" to listOf(mapOf("refresh_token" to "synthetic-secret"))))
        val failure = assertFailsWith<ExecutionException> {
            resources.firestore.runTransaction { tx -> store.create(tx, owner, invalid) }.get(15, TimeUnit.SECONDS)
        }
        var cause: Throwable? = failure
        while (cause != null && cause !is IllegalArgumentException) cause = cause.cause
        assertIs<IllegalArgumentException>(cause)
        assertNull(store.find(owner, original.operationId))
    }
}
