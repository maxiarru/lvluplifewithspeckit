package lvluplife.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.application.RecordOperations
import lvluplife.model.*
import lvluplife.progression.*

/** Real local Firestore, pure replay plans; HTTP admission is connected in T039. */
class RecordRevisionTest {
    private val json = ObjectMapper()
    private val now = Instant.parse("2026-11-11T15:00:00Z")
    private val day = LocalDate.parse("2026-11-11")
    private class Fixture(val resources: ServerResources, val owner: Uid, val cycle: Cycle,
        val operations: OperationStore, val transactions: RevisionTransaction)
    private fun scenario(clock: AuthoritativeClock = AuthoritativeClock { now }, block: (Fixture) -> Unit) {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080", resources.firestore.options.emulatorHost)
            val owner = Uid("synthetic-record-revision-${UUID.randomUUID()}")
            val cycle = Cycle(owner, CycleId("synthetic-current"), now.minusSeconds(10 * 86400))
            resources.documents.write(owner, emptyList(), mapOf("revision" to 1L, "currentCycleId" to cycle.id.value))
            resources.documents.write(owner, listOf("cycles", cycle.id.value), mapOf("startedAt" to cycle.startedAt.toString(), "rulesVersion" to 1L))
            val operations = OperationStore(resources.firestore)
            block(Fixture(resources, owner, cycle, operations, RevisionTransaction(resources.firestore, operations, clock)))
        }
    }
    private fun command(f: Fixture, minutes: Int = 30, date: LocalDate = day, id: OperationId = OperationId.create()) =
        CommandEnvelope(id, CommandType.CREATE_RECORD, f.cycle.id.value, json.valueToTree(mapOf(
            "activityId" to "strength", "activityDate" to date.toString(), "hours" to minutes / 60, "minutes" to minutes % 60)))
    private fun stored(f: Fixture, id: String, minutes: Int, at: Instant = now) =
        f.resources.documents.write(f.owner, listOf("records", id), mapOf("cycleId" to f.cycle.id.value,
            "activityDate" to day.toString(), "durationMinutes" to minutes.toLong(), "createdAt" to at.toString()))
    private fun plan(f: Fixture, command: CommandEnvelope, id: String, snapshot: RecordSnapshot, at: Instant): OperationDecision {
        val date = LocalDate.parse(command.payload.path("activityDate").textValue())
        try { RecordValidation.activityDate(date, at) } catch (_: IllegalArgumentException) {
            return OperationDecision.Rejected(ApiError.DATE_NOT_ALLOWED)
        }
        val definition = ActivityCatalog.find(ActivityId("strength"))!!
        val records = snapshot.records.map { (key, fields) -> ActivityRecord.create(RecordId(key), snapshot.cycleId,
            definition, LocalDate.parse(fields["activityDate"] as String), (fields["durationMinutes"] as Number).toInt(),
            Instant.parse(fields["createdAt"] as String)) }
        val minutes = try { RecordValidation.durationMinutes(BigDecimal(command.payload.path("hours").asText()),
            BigDecimal(command.payload.path("minutes").asText()), snapshot.cycleId, date, records) }
        catch (_: IllegalArgumentException) { return OperationDecision.Rejected(ApiError.DAILY_DURATION_EXCEEDED) }
        val record = ActivityRecord.create(RecordId(id), snapshot.cycleId, definition, date, minutes, at)
        val replay = ReplayEngine.rebuild(f.cycle, records + record, at)
        val progression = Progression.derived(f.cycle, snapshot.revision.next(), at, replay.attributes,
            replay.general, replay.appearance.classId, replay.appearance.stage)
        val fields = mapOf("cycleId" to snapshot.cycleId.value, "activityDate" to date.toString(),
            "durationMinutes" to minutes.toLong(), "createdAt" to at.toString())
        val projection = mapOf("revision" to progression.revision.value, "computedAt" to at.toString(),
            "xp" to progression.attributes.getValue(Attribute.STR).attribute.xp.toLong())
        val decision = RecordOperations(f.operations).confirmation(f.owner, command, record, progression,
            listOf(DocumentWrite(listOf("records", id), fields), DocumentWrite(listOf("projections", "current"), projection)))
        return decision.copy(result = decision.result + mapOf("orderedIds" to replay.orderedRecordIds,
            "candidateXp" to replay.recordXp.getValue(id).getValue(Attribute.STR).toLong()))
    }
    private fun commit(f: Fixture, command: CommandEnvelope, id: String) = f.transactions.commitRecords(f.owner, command,
        listOf(DocumentCondition(listOf("records", id), null))) { snapshot, at -> plan(f, command, id, snapshot, at) }

    @Test fun snapshotContainsOnlyOwnerCurrentCycleAndCannotBeMutated() = scenario { f ->
        stored(f, "current", 30)
        f.resources.documents.write(f.owner, listOf("records", "prior"), mapOf("cycleId" to "synthetic-prior"))
        val other = Uid("synthetic-other-${UUID.randomUUID()}")
        f.resources.documents.write(other, listOf("records", "foreign"), mapOf("cycleId" to f.cycle.id.value))
        val snapshot = f.transactions.recordSnapshot(f.owner)
        assertEquals(Revision(1), snapshot.revision); assertEquals(f.cycle.id, snapshot.cycleId)
        assertEquals(setOf("current"), snapshot.records.keys)
        assertFailsWith<UnsupportedOperationException> { (snapshot.root as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (snapshot.records as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (snapshot.records.getValue("current") as MutableMap).clear() }
    }
    @Test fun commitPublishesRecordProjectionRevisionAndReceiptTogether() = scenario { f ->
        val command = command(f); val receipt = commit(f, command, "record")
        assertEquals(ReceiptOutcome.CONFIRMED, receipt.outcome); assertEquals(Revision(2), receipt.revision)
        assertNotNull(f.resources.documents.read(f.owner, listOf("records", "record")))
        assertEquals(2L, f.resources.documents.read(f.owner, emptyList())!!["revision"])
        assertEquals(2L, f.resources.documents.read(f.owner, listOf("projections", "current"))!!["revision"])
        assertEquals(receipt, f.operations.find(f.owner, command.operationId))
    }
    @Test fun concurrentAdmissionsCannotBothConsumeTheLastHour() = scenario { f ->
        stored(f, "existing", 1380)
        val ready = CountDownLatch(2); val pool = Executors.newFixedThreadPool(2); val calls = AtomicInteger()
        try {
            val replies = (1..2).map { index -> pool.submit<OperationReceipt> {
                val command = command(f, 60)
                f.transactions.commitRecords(f.owner, command) { snapshot, at ->
                    val decision = plan(f, command, "new-$index", snapshot, at)
                    if (calls.incrementAndGet() <= 2) { ready.countDown(); check(ready.await(10, TimeUnit.SECONDS)) }
                    decision
                }
            } }.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, replies.count { it.outcome == ReceiptOutcome.CONFIRMED })
            assertEquals(ApiError.DAILY_DURATION_EXCEEDED, replies.single { it.outcome == ReceiptOutcome.REJECTED }.error)
            assertEquals(1440L, f.transactions.recordSnapshot(f.owner).records.values.sumOf { (it["durationMinutes"] as Number).toLong() })
            assertEquals(3L, f.resources.documents.read(f.owner, emptyList())!!["revision"])
        } finally { pool.shutdownNow() }
    }
    @Test fun concurrentRecordsShareTheLastXpPointAndBothPersist() = scenario { f ->
        stored(f, "existing", 235)
        val ready = CountDownLatch(2); val pool = Executors.newFixedThreadPool(2); val calls = AtomicInteger()
        try {
            val replies = (1..2).map { index -> pool.submit<OperationReceipt> {
                val command = command(f, 5)
                f.transactions.commitRecords(f.owner, command) { snapshot, at ->
                    val decision = plan(f, command, "new-$index", snapshot, at)
                    if (calls.incrementAndGet() <= 2) { ready.countDown(); check(ready.await(10, TimeUnit.SECONDS)) }
                    decision
                }
            } }.map { it.get(30, TimeUnit.SECONDS) }
            assertTrue(replies.all { it.outcome == ReceiptOutcome.CONFIRMED })
            assertEquals(3, f.transactions.recordSnapshot(f.owner).records.size)
            assertEquals(18L, f.resources.documents.read(f.owner, listOf("projections", "current"))!!["xp"])
            assertTrue(calls.get() >= 3, "The stale external calculation must be discarded")
        } finally { pool.shutdownNow() }
    }
    @Test fun midnightForcesFreshExternalCalculationAndDefinitiveDateRejection() {
        val before = Instant.parse("2026-11-12T02:59:59.999Z"); val after = Instant.parse("2026-11-12T03:00:00Z")
        val clockCalls = AtomicInteger(); val calculations = AtomicInteger()
        scenario(AuthoritativeClock { if (clockCalls.incrementAndGet() == 1) before else after }) { f ->
            val command = command(f, date = LocalDate.parse("2026-11-09"))
            val receipt = f.transactions.commitRecords(f.owner, command) { snapshot, at ->
                calculations.incrementAndGet(); plan(f, command, "expired", snapshot, at)
            }
            assertEquals(ApiError.DATE_NOT_ALLOWED, receipt.error); assertEquals(after, receipt.validatedAt)
            assertTrue(calculations.get() >= 2)
            assertNull(f.resources.documents.read(f.owner, listOf("records", "expired")))
            assertEquals(2L, f.resources.documents.read(f.owner, emptyList())!!["revision"])
        }
    }
    @Test fun finalCreationTimeReplaysOrdinalTieInTheAcceptedOrder() {
        val calls = AtomicInteger(); val final = now.plusSeconds(1)
        scenario(AuthoritativeClock { if (calls.incrementAndGet() == 1) now else final }) { f ->
            stored(f, "z-existing", 240)
            val receipt = commit(f, command(f, 5), "a-candidate")
            val result = assertNotNull(receipt.result)
            assertEquals(listOf("z-existing", "a-candidate"), result["orderedIds"])
            assertEquals(0L, result["candidateXp"])
            assertEquals(final, receipt.validatedAt)
            assertEquals(final.toString(), f.resources.documents.read(f.owner, listOf("records", "a-candidate"))!!["createdAt"])
        }
    }
    @Test fun recoveredReceiptWinsBeforeClockSnapshotAndObsoletePreconditions() = scenario { f ->
        val command = command(f); val original = commit(f, command, "record")
        f.resources.documents.write(f.owner, emptyList(), mapOf("corrupt" to true))
        val transactions = RevisionTransaction(f.resources.firestore, f.operations, AuthoritativeClock { error("Clock must not be read") })
        val replay = transactions.commitRecords(f.owner, command,
            listOf(DocumentCondition(listOf("records", "record"), null))) { _, _ -> error("Must not calculate") }
        assertEquals(original, replay)
    }
    @Test fun cycleChangeRejectsWithoutInvokingReplayOrMovingRecord() = scenario { f ->
        val command = command(f).copy(cycleId = "synthetic-old")
        val receipt = f.transactions.commitRecords(f.owner, command) { _, _ -> error("Wrong cycle must not replay") }
        assertEquals(ApiError.CYCLE_CLOSED, receipt.error)
        assertTrue(f.transactions.recordSnapshot(f.owner).records.isEmpty())
    }
    @Test fun failedPreparationHasNoReceiptRevisionOrPartialWrites() = scenario { f ->
        val command = command(f)
        assertFailsWith<IllegalStateException> {
            f.transactions.commitRecords(f.owner, command) { _, _ -> error("synthetic transient failure") }
        }
        assertNull(f.operations.find(f.owner, command.operationId))
        assertEquals(1L, f.resources.documents.read(f.owner, emptyList())!!["revision"])
        assertNull(f.resources.documents.read(f.owner, listOf("projections", "current")))
    }
}
