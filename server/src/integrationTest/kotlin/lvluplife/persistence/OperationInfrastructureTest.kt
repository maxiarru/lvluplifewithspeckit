package lvluplife.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.fixtures.FaultPoint
import lvluplife.fixtures.InjectedFault
import lvluplife.model.*

/** Synthetic storage protocol only: no Account, Cycle, replay, or application endpoints. */
class OperationInfrastructureTest {
    private val now = Instant.parse("2026-11-10T15:00:00Z")
    private val mapper = ObjectMapper()
    private val path = listOf("synthetic", "effect")
    private fun uid() = Uid("synthetic-infra-${UUID.randomUUID()}")
    private fun command(id: OperationId = OperationId.create(), payload: String = """{"activityId":"strength","activityDate":"2026-11-10","hours":0,"minutes":30}""") =
        CommandEnvelope(id, CommandType.CREATE_RECORD, "synthetic-cycle", mapper.readTree(payload))
    private fun open(): ServerResources {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        return ServerResources.open(config).also {
            // Fail closed before any RPC. Never use ADC or the remote service in these tests.
            assertEquals("127.0.0.1:8080", it.firestore.options.host)
            assertEquals("127.0.0.1:8080", it.firestore.options.emulatorHost)
        }
    }
    private fun receipt(store: OperationStore, command: CommandEnvelope, rejected: Boolean = false) = OperationReceipt(
        command.operationId, command.type, store.canonicalHash(command), if (rejected) ReceiptOutcome.REJECTED else ReceiptOutcome.CONFIRMED,
        now, Revision(1), command.cycleId,
        result = if (rejected) null else mapOf("marker" to "original", "revision" to 1L),
        error = if (rejected) ApiError.DATE_NOT_ALLOWED else null,
    )
    private fun save(resources: ServerResources, store: OperationStore, owner: Uid, command: CommandEnvelope,
        candidate: OperationReceipt): OperationReceipt = resources.firestore.runTransaction { tx ->
        store.read(tx, owner, command) ?: candidate.also { store.create(tx, owner, it) }
    }.get(15, TimeUnit.SECONDS)
    private fun confirmed(marker: String = "original") = OperationDecision.Confirmed(
        mapOf("marker" to marker), listOf(DocumentWrite(path, mapOf("marker" to marker, "version" to 1L))),
        mapOf("marker" to marker),
    )
    private fun revision(resources: ServerResources, owner: Uid) = resources.documents.read(owner, emptyList())?.get("revision")

    @Test fun canonicalHashIgnoresObjectOrderAndOperationIdentity() = open().use { resources ->
        val store = OperationStore(resources.firestore)
        val first = command()
        val reordered = command(payload = """{"minutes":30,"hours":0,"activityDate":"2026-11-10","activityId":"strength"}""")
        assertEquals(store.canonicalHash(first), store.canonicalHash(reordered))
        assertTrue(Regex("[0-9a-f]{64}").matches(store.canonicalHash(first)))
    }

    @Test fun canonicalHashIncludesTypeCycleAndPayload() = open().use { resources ->
        val store = OperationStore(resources.firestore); val original = command()
        val hash = store.canonicalHash(original)
        assertNotEquals(hash, store.canonicalHash(original.copy(type = CommandType.EDIT_RECORD)))
        assertNotEquals(hash, store.canonicalHash(original.copy(cycleId = "another-cycle")))
        assertNotEquals(hash, store.canonicalHash(command(original.operationId,
            """{"activityId":"strength","activityDate":"2026-11-10","hours":0,"minutes":45}""")))
    }

    @Test fun absentReceiptIsUnknownAndSameUuidIsScopedToUid() = open().use { resources ->
        val store = OperationStore(resources.firestore); val a = uid(); val b = uid(); val command = command()
        assertNull(store.find(a, command.operationId))
        val original = receipt(store, command)
        save(resources, store, a, command, original)
        assertEquals(original, store.find(a, command.operationId))
        val stored = resources.documents.read(a, listOf("operations", command.operationId.value))!!
        assertEquals(command.type.name, stored["commandType"])
        assertTrue(stored.keys.none { it.lowercase() in setOf("ttl", "expiresat", "token", "authorization") })
        assertNull(store.find(b, command.operationId))
        val other = original.copy(result = mapOf("marker" to "account-b"))
        save(resources, store, b, command, other)
        assertEquals(other, store.find(b, command.operationId))
        assertEquals(original, store.find(a, command.operationId))
    }

    @Test fun confirmedReceiptRemainsOriginalAcrossReconnectAndTimeChanges() {
        val owner = uid(); val command = command(); lateinit var original: OperationReceipt
        open().use { resources ->
            val store = OperationStore(resources.firestore); original = receipt(store, command)
            assertEquals(original, save(resources, store, owner, command, original))
        }
        open().use { resources ->
            val store = OperationStore(resources.firestore)
            assertEquals(original, save(resources, store, owner, command,
                original.copy(validatedAt = now.plusSeconds(86_400), result = mapOf("marker" to "must-not-replace"))))
            assertEquals(original, store.find(owner, command.operationId))
        }
    }

    @Test fun rejectedReceiptIsImmutableAndCorrectionUsesNewUuid() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid(); val command = command()
        val rejected = receipt(store, command, rejected = true)
        save(resources, store, owner, command, rejected)
        assertEquals(rejected, save(resources, store, owner, command, receipt(store, command)))
        val corrected = command.copy(operationId = OperationId.create())
        assertEquals(ReceiptOutcome.CONFIRMED, save(resources, store, owner, corrected, receipt(store, corrected)).outcome)
        assertEquals(rejected, store.find(owner, command.operationId))
    }

    @Test fun sameUuidWithDifferentContentConflictsWithoutReplacingReceipt() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid(); val command = command()
        val original = receipt(store, command); save(resources, store, owner, command, original)
        val changed = command.copy(cycleId = "changed-cycle")
        val failure = assertFailsWith<ExecutionException> { save(resources, store, owner, changed, receipt(store, changed)) }
        var cause: Throwable? = failure
        while (cause != null && cause !is ApiException) cause = cause.cause
        assertEquals(ApiError.OPERATION_ID_REUSED, assertIs<ApiException>(cause).error)
        assertEquals(original, store.find(owner, command.operationId))
    }

    @Test fun transientErrorCannotBePersistedAsFunctionalRejection() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid(); val command = command()
        val invalid = receipt(store, command, rejected = true).copy(error = ApiError.TEMPORARILY_UNAVAILABLE)
        val failure = assertFailsWith<ExecutionException> { save(resources, store, owner, command, invalid) }
        var cause: Throwable? = failure
        while (cause != null && cause !is IllegalArgumentException) cause = cause.cause
        assertIs<IllegalArgumentException>(cause)
        assertNull(store.find(owner, command.operationId))
    }

    @Test fun firstCommitCreatesRootEffectAndReceiptTogether() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid(); val command = command()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        val result = tx.commit(owner, command, null) { snapshot, validationNow ->
            assertNull(snapshot.root); assertNull(snapshot.revision); assertEquals(now, validationNow)
            confirmed()
        }
        assertEquals(ReceiptOutcome.CONFIRMED, result.outcome); assertEquals(Revision(1), result.revision)
        assertEquals(now, result.validatedAt); assertEquals(command.cycleId, result.cycleId)
        assertEquals(1L, revision(resources, owner))
        assertEquals("original", resources.documents.read(owner, path)?.get("marker"))
        assertEquals(result, store.find(owner, command.operationId))
    }

    @Test fun receiptIsReadBeforeObsoleteRevisionPreconditionsOrDecision() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid(); val command = command()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        val original = tx.commit(owner, command, null) { _, _ -> confirmed() }
        val later = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now.plusSeconds(86_400) })
        assertEquals(original, later.commit(owner, command, null,
            listOf(DocumentCondition(path, mapOf("version" to 999L)))) { _, _ -> error("A receipt must bypass reevaluation") })
        assertEquals(1L, revision(resources, owner))
        assertEquals("original", resources.documents.read(owner, path)?.get("marker"))
    }

    @Test fun revisionMismatchDoesNotWriteEffectOrReceipt() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        tx.commit(owner, command(), null) { _, _ -> confirmed() }
        val stale = command()
        assertFailsWith<RevisionChanged> { tx.commit(owner, stale, Revision(0)) { _, _ -> error("Must reject obsolete snapshot") } }
        assertEquals(1L, revision(resources, owner)); assertNull(store.find(owner, stale.operationId))
        assertEquals("original", resources.documents.read(owner, path)?.get("marker"))
    }

    @Test fun preconditionsAreReadBeforeWritesAndChangedDocumentAbortsWholeCommit() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        tx.commit(owner, command(), null) { _, _ -> confirmed() }
        val second = command(); val absentPath = listOf("synthetic", "additional")
        tx.commit(owner, second, Revision(1), listOf(DocumentCondition(path, mapOf("version" to 1L)), DocumentCondition(absentPath, null))) { snapshot, _ ->
            assertEquals(1L, snapshot.documents.getValue(path)?.get("version"))
            assertNull(snapshot.documents.getValue(absentPath))
            OperationDecision.Confirmed(emptyMap(), listOf(DocumentWrite(path, mapOf("version" to 2L)), DocumentWrite(absentPath, mapOf("created" to true))), mapOf("ok" to true))
        }
        val stale = command()
        assertFailsWith<PreconditionChanged> {
            tx.commit(owner, stale, Revision(2), listOf(DocumentCondition(path, mapOf("version" to 1L)))) { _, _ -> error("Must reject stale precondition") }
        }
        assertEquals(2L, revision(resources, owner)); assertNull(store.find(owner, stale.operationId))
        assertEquals("original", resources.documents.read(owner, emptyList())?.get("marker"))
        assertEquals(2L, resources.documents.read(owner, path)?.get("version"))
        assertEquals(true, resources.documents.read(owner, absentPath)?.get("created"))
    }

    @Test fun definitiveRejectionChangesOnlyReceiptAndRevision() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        tx.commit(owner, command(), null) { _, _ -> confirmed() }
        val rejected = command()
        val result = tx.commit(owner, rejected, Revision(1)) { _, _ -> OperationDecision.Rejected(ApiError.DATE_NOT_ALLOWED) }
        assertEquals(ReceiptOutcome.REJECTED, result.outcome); assertEquals(ApiError.DATE_NOT_ALLOWED, result.error)
        assertNull(result.result); assertEquals(2L, revision(resources, owner))
        assertEquals("original", resources.documents.read(owner, emptyList())?.get("marker"))
        assertEquals("original", resources.documents.read(owner, path)?.get("marker"))
        assertEquals(result, store.find(owner, rejected.operationId))
    }

    @Test fun failureBeforeCommitLeavesNoRootEffectOrReceiptAndRetryCanSucceed() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid(); val command = command()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        assertFailsWith<InjectedFault> { tx.commit(owner, command, null) { _, _ -> throw InjectedFault(FaultPoint.BEFORE_COMMIT) } }
        assertNull(resources.documents.read(owner, emptyList())); assertNull(resources.documents.read(owner, path))
        assertNull(store.find(owner, command.operationId))
        assertEquals(ReceiptOutcome.CONFIRMED, tx.commit(owner, command, null) { _, _ -> confirmed() }.outcome)
        assertEquals(1L, revision(resources, owner))
    }

    @Test fun transientDependencyFailureCreatesNoDefinitiveReceipt() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid(); val command = command()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        assertFailsWith<PersistenceUnavailable> { tx.commit(owner, command, null) { _, _ -> throw PersistenceUnavailable() } }
        assertNull(store.find(owner, command.operationId)); assertNull(revision(resources, owner))
        assertNull(resources.documents.read(owner, path))
    }

    @Test fun lostResponseAfterCommitRecoversOriginalReceiptWithoutSecondEffect() {
        val owner = uid(); val command = command(); lateinit var original: OperationReceipt
        open().use { resources ->
            val store = OperationStore(resources.firestore)
            val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
            assertFailsWith<InjectedFault> {
                original = tx.commit(owner, command, null) { _, _ -> confirmed() }
                // Test-only loss after the actual emulator commit, never a production fault hook.
                throw InjectedFault(FaultPoint.AFTER_COMMIT)
            }
            assertEquals(1L, revision(resources, owner)); assertEquals(original, store.find(owner, command.operationId))
            assertEquals("original", resources.documents.read(owner, path)?.get("marker"))
        }
        open().use { resources ->
            val store = OperationStore(resources.firestore)
            val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now.plusSeconds(86_400) })
            assertEquals(original, tx.commit(owner, command, null) { _, _ -> error("Must not apply twice") })
            assertEquals(1L, revision(resources, owner))
        }
    }

    @Test fun concurrentSameUuidCommitsOnlyOneEffectAndReturnsSameReceipt() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid(); val command = command()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        val pool = Executors.newFixedThreadPool(2); val start = CountDownLatch(1)
        try {
            val futures = (1..2).map { pool.submit<OperationReceipt> { start.await(); tx.commit(owner, command, null) { _, _ -> confirmed() } } }
            start.countDown()
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(results.first(), results.last()); assertEquals(1L, revision(resources, owner))
            assertEquals(results.first(), store.find(owner, command.operationId))
            assertEquals("original", resources.documents.read(owner, path)?.get("marker"))
        } finally { pool.shutdownNow() }
    }

    @Test fun concurrentDifferentUuidsCannotBothUseTheSameRevision() = open().use { resources ->
        val store = OperationStore(resources.firestore); val owner = uid()
        val tx = RevisionTransaction(resources.firestore, store, AuthoritativeClock { now })
        tx.commit(owner, command(), null) { _, _ -> confirmed() }
        val commands = listOf(command(), command()); val successes = AtomicInteger(); val conflicts = AtomicInteger()
        val pool = Executors.newFixedThreadPool(2); val start = CountDownLatch(1)
        try {
            val futures = commands.mapIndexed { index, command -> pool.submit {
                start.await()
                try { tx.commit(owner, command, Revision(1), listOf(DocumentCondition(path, mapOf("version" to 1L)))) { _, _ -> confirmed("winner-$index") }; successes.incrementAndGet() }
                catch (_: RevisionChanged) { conflicts.incrementAndGet() }
            } }
            start.countDown(); futures.forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, successes.get()); assertEquals(1, conflicts.get()); assertEquals(2L, revision(resources, owner))
            val receipts = commands.mapNotNull { store.find(owner, it.operationId) }
            assertEquals(1, receipts.size)
            assertEquals(receipts.single().result?.get("marker"), resources.documents.read(owner, path)?.get("marker"))
        } finally { pool.shutdownNow() }
    }
}
