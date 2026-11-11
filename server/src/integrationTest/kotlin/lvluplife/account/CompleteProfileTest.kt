package lvluplife.account

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.*
import lvluplife.model.*
import lvluplife.persistence.*

class CompleteProfileTest {
    private val now = Instant.parse("2026-11-10T15:00:00.123456789Z")
    private val json = ObjectMapper()
    private fun uid() = Uid("synthetic-profile-${UUID.randomUUID()}")
    private fun name() = "P" + UUID.randomUUID().toString().replace("-", "").take(10)
    private fun command(name: String, id: OperationId = OperationId.create()) = CommandEnvelope(id, CommandType.COMPLETE_PROFILE, null,
        json.valueToTree(mapOf("username" to name)))
    private fun open(): ServerResources {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        return ServerResources.open(config).also {
            assertEquals("127.0.0.1:8080", it.firestore.options.host)
            assertEquals("127.0.0.1:8080", it.firestore.options.emulatorHost)
        }
    }
    private fun service(resources: ServerResources): CompleteProfile {
        val operations = OperationStore(resources.firestore)
        return CompleteProfile(AccountStore(resources.firestore), operations,
            RevisionTransaction(resources.firestore, operations, AuthoritativeClock { now }))
    }
    private fun cycles(resources: ServerResources, owner: Uid) =
        resources.firestore.collection("users/${owner.value}/cycles").get().get(10, TimeUnit.SECONDS).documents
    private fun reservation(resources: ServerResources, username: String): Map<String, Any?>? {
        val key = username.lowercase(Locale.ROOT)
        val id = if (Regex("__.*__").matches(key)) "~$key" else key
        return resources.firestore.document("usernames/$id").get().get(10, TimeUnit.SECONDS).data
    }
    private fun noProfile(resources: ServerResources, owner: Uid) {
        assertNull(AccountStore(resources.firestore).read(owner)); assertTrue(cycles(resources, owner).isEmpty())
        assertNull(resources.documents.read(owner, listOf("projections", "current")))
    }

    @Test fun profileCycleProjectionNameAndReceiptAreCommittedTogether() = open().use { r ->
        val owner = uid(); val name = name(); val command = command(name)
        assertNull(AccountStore(r.firestore).read(owner)); assertNull(r.documents.read(owner, emptyList()))
        val result = service(r).execute(owner, command)
        val account = assertNotNull(AccountStore(r.firestore).read(owner))
        assertEquals(name, account.username.value); assertEquals(now, account.createdAt); assertEquals(Revision(1), account.revision)
        assertEquals(owner.value, reservation(r, name)?.get("uid")); assertEquals(1, cycles(r, owner).size)
        val cycle = assertNotNull(r.documents.read(owner, listOf("cycles", account.currentCycleId.value)))
        assertEquals(now.toString(), cycle["startedAt"]); assertEquals(1L, cycle["rulesVersion"]); assertNull(cycle["closedAt"])
        val projection = assertNotNull(r.documents.read(owner, listOf("projections", "current")))
        assertEquals(account.currentCycleId.value, projection["cycleId"]); assertEquals(1L, projection["revision"])
        assertEquals("2026-11-10", projection["asOfDate"]); assertEquals(now.toString(), projection["computedAt"])
        assertEquals("novato", projection["classId"]); assertEquals(1L, projection["stage"])
        assertEquals(1L, projection["generalLevel"]); assertEquals(0L, projection["generalRemainder"])
        val attributes = assertIs<Map<*, *>>(projection["attributes"])
        assertEquals(Attribute.entries.map { it.name }.toSet(), attributes.keys)
        attributes.values.forEach { assertEquals(mapOf("level" to 1L, "xp" to 0L), it) }
        assertEquals(ReceiptOutcome.CONFIRMED, result.outcome)
        assertEquals(result, OperationStore(r.firestore).find(owner, command.operationId))
    }

    @Test fun invalidNameRejectsWithoutProfileAndCorrectionUsesNewUuidAndNextRevision() = open().use { r ->
        val owner = uid(); val service = service(r); val invalid = command("invalid name")
        val rejected = service.execute(owner, invalid)
        assertEquals(ReceiptOutcome.REJECTED, rejected.outcome); assertEquals(ApiError.INVALID_INPUT, rejected.error)
        noProfile(r, owner)
        val valid = command(name()); val confirmed = service.execute(owner, valid)
        assertEquals(ReceiptOutcome.CONFIRMED, confirmed.outcome); assertEquals(Revision(2), confirmed.revision)
        assertNotEquals(invalid.operationId, valid.operationId)
        assertEquals(rejected, service.execute(owner, invalid))
        assertEquals(Revision(2), AccountStore(r.firestore).read(owner)?.revision)
    }

    @Test fun replayReturnsReceiptBeforeReadingEvenACorruptRoot() = open().use { r ->
        val owner = uid(); val command = command(name()); val service = service(r)
        val original = service.execute(owner, command)
        // Deliberate emulator corruption after commit tests precedence; never an application writer.
        r.documents.write(owner, emptyList(), mapOf("broken" to true))
        assertEquals(original, service.execute(owner, command))
        assertEquals(1, cycles(r, owner).size)
    }

    @Test fun existingProfileDoesNotRenameOrResetCycleOrProjection() = open().use { r ->
        val owner = uid(); val originalName = name(); val another = name(); val service = service(r)
        service.execute(owner, command(originalName))
        val before = assertNotNull(AccountStore(r.firestore).read(owner))
        val projection = r.documents.read(owner, listOf("projections", "current"))
        service.execute(owner, command(another))
        val after = assertNotNull(AccountStore(r.firestore).read(owner))
        assertEquals(before.username, after.username); assertEquals(before.currentCycleId, after.currentCycleId)
        assertEquals(before.createdAt, after.createdAt); assertEquals(1, cycles(r, owner).size)
        assertEquals(projection, r.documents.read(owner, listOf("projections", "current"))); assertNull(reservation(r, another))
    }

    @Test fun reusedUuidWithDifferentNameCannotOverwriteAnything() = open().use { r ->
        val owner = uid(); val original = command(name()); val changed = command(name(), original.operationId); val service = service(r)
        val receipt = service.execute(owner, original)
        val failure = assertFailsWith<ApiException> { service.execute(owner, changed) }
        assertEquals(ApiError.OPERATION_ID_REUSED, failure.error)
        assertEquals(receipt, OperationStore(r.firestore).find(owner, original.operationId)); assertEquals(1, cycles(r, owner).size)
        assertNull(reservation(r, changed.payload.get("username").textValue()))
    }

    @Test fun twoAccountsCompetingForCaseVariantsHaveOneWinnerAndImmutableRejection() = open().use { r ->
        val owners = listOf(uid(), uid()); val username = name()
        val commands = listOf(command(username), command(username.lowercase(Locale.ROOT)))
        val service = service(r); val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = owners.zip(commands).map { (owner, command) -> pool.submit<OperationReceipt> { start.await(); service.execute(owner, command) } }
            start.countDown(); val results = futures.map { it.get(40, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.outcome == ReceiptOutcome.CONFIRMED })
            val loser = results.indexOfFirst { it.outcome == ReceiptOutcome.REJECTED }; assertTrue(loser >= 0)
            assertEquals(ApiError.USERNAME_UNAVAILABLE, results[loser].error); noProfile(r, owners[loser])
            assertEquals(owners[1 - loser].value, reservation(r, username)?.get("uid"))
            assertEquals(results[loser], service.execute(owners[loser], commands[loser]))
        } finally { pool.shutdownNow() }
    }

    @Test fun concurrentDifferentNamesForOneUidCreateOnlyOneCharacter() = open().use { r ->
        val owner = uid(); val names = listOf(name(), name()); val service = service(r)
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = names.map { name -> pool.submit<OperationReceipt> { start.await(); service.execute(owner, command(name)) } }
            start.countDown(); assertTrue(futures.map { it.get(40, TimeUnit.SECONDS) }.all { it.outcome == ReceiptOutcome.CONFIRMED })
            assertEquals(1, cycles(r, owner).size); assertEquals(1, names.count { reservation(r, it)?.get("uid") == owner.value })
        } finally { pool.shutdownNow() }
    }

    @Test fun allowedUnderscoreNamesDoNotCollideWithFirestoreReservedDocumentIds() = open().use { r ->
        val username = "__Ow_${UUID.randomUUID().toString().take(4)}__"; val owner = uid()
        assertTrue(Regex("[A-Za-z0-9_]{1,15}").matches(username))
        val receipt = service(r).execute(owner, command(username))
        assertEquals(ReceiptOutcome.CONFIRMED, receipt.outcome)
        assertEquals(username, AccountStore(r.firestore).read(owner)?.username?.value)
        assertEquals(owner.value, reservation(r, username)?.get("uid"))
        val other = service(r).execute(uid(), command(username.lowercase(Locale.ROOT)))
        assertEquals(ApiError.USERNAME_UNAVAILABLE, other.error)
    }

    @Test fun corruptPartialProfileFailsSafelyAndDoesNotReserveRequestedName() = open().use { r ->
        val owner = uid(); val requested = name(); val command = command(requested)
        r.documents.write(owner, emptyList(), mapOf("revision" to 1L, "username" to "bad name"))
        val failure = assertFailsWith<PersistenceUnavailable> { service(r).execute(owner, command) }
        assertNull(failure.cause); assertFalse(failure.message.orEmpty().contains(owner.value))
        assertNull(reservation(r, requested)); assertNull(OperationStore(r.firestore).find(owner, command.operationId))
    }

    @Test fun unavailablePersistenceNeverCreatesAFunctionalRejection() {
        val r = open(); val service = service(r); val owner = uid(); val command = command(name()); r.close()
        val failure = assertFailsWith<PersistenceUnavailable> { service.execute(owner, command) }
        assertNull(failure.cause); assertFalse(failure.message.orEmpty().contains(owner.value))
    }
}
