package lvluplife.record

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.application.RecordOperations
import lvluplife.fixtures.FaultPoint
import lvluplife.fixtures.InjectedFault
import lvluplife.model.*
import lvluplife.persistence.OperationStore
import lvluplife.persistence.ReceiptOutcome
import lvluplife.persistence.*
import lvluplife.progression.ReplayEngine

/** HTTP contracts against real local Firestore. Identity/time/fault injection exists only in this fixture. */
class CreateRecordContractTest {
    private val json = ObjectMapper()
    @Test fun rejectedReceiptLookupIsSuccessfulReadOfOriginalDefinitiveResult() = scenario { f ->
        val id = OperationId.create(); val original = rejected(client.create(f, payload(date = "2026-11-08"), id), "DATE_NOT_ALLOWED")
        val before = f.root()
        val response = client.get("/v1/operations/${id.value}") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.OK, response.status); assertEquals(original, body(response))
        assertEquals(before, f.root())
    }
    @Test fun progressRebuildIgnoresCorruptCachedProjectionAndCreatesNoOperationReceipt() = scenario { f ->
        confirmed(client.create(f))
        val before = f.root(); val receipts = f.resources.firestore.collection("users/${f.a.value}/operations").get().get(10, TimeUnit.SECONDS).size()
        f.resources.documents.write(f.a, listOf("projections", "current"), mapOf("classId" to "synthetic-corrupt", "xp" to 9999L))
        val p = progress(client, f)
        assertEquals(6, p.path("attributes").path("STR").path("xp").intValue())
        assertEquals((before["revision"] as Number).toLong() + 1, p.path("revision").longValue())
        assertEquals(f.root()["revision"], f.projection()["revision"])
        assertEquals(receipts, f.resources.firestore.collection("users/${f.a.value}/operations").get().get(10, TimeUnit.SECONDS).size())
    }
    @Test fun progressCrossingMidnightPublishesOnlyTheFinalBusinessDate() = scenario { f ->
        confirmed(client.create(f)); val beforeRevision = (f.root()["revision"] as Number).toLong()
        val before = Instant.parse("2026-11-12T02:59:59.999Z"); val after = Instant.parse("2026-11-12T03:00:00Z")
        f.now = before; f.clockSequence.addAll(listOf(before, after))
        val p = progress(client, f)
        assertEquals("2026-11-12", p.path("businessDate").textValue()); assertEquals(after.toString(), p.path("computedAt").textValue())
        assertEquals(1, p.path("attributes").path("DEX").path("inactiveDates").intValue())
        assertEquals(0, p.path("attributes").path("STR").path("inactiveDates").intValue())
        assertEquals(beforeRevision + 1, p.path("revision").longValue()); assertTrue(f.clockSequence.isEmpty())
        assertEquals("2026-11-12", f.projection()["asOfDate"])
    }
    @Test fun progressQueryCannotExposeAnotherAccountsAttributes() = scenario { f ->
        confirmed(client.create(f))
        val a = progress(client, f)
        val response = client.get("/v1/progress") { bearerAuth(f.tokenB) }
        assertEquals(HttpStatusCode.OK, response.status); val b = body(response)
        assertEquals(6, a.path("attributes").path("STR").path("xp").intValue())
        assertEquals(0, b.path("attributes").path("STR").path("xp").intValue())
        assertEquals(f.cycle(f.b), b.path("cycleId").textValue()); assertFalse(b.toString().contains(f.a.value))
    }
    @Test fun corruptStoredRecordFailsProgressWithoutPublishingPartialState() = scenario { f ->
        confirmed(client.create(f)); val before = f.root(); val projection = f.projection()
        val record = f.records().single()
        f.resources.documents.write(f.a, listOf("records", record.id), record.data + ("durationMinutes" to 0L))
        val response = client.get("/v1/progress") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(before, f.root()); assertEquals(projection, f.projection())
        assertFalse(response.bodyAsText().contains(f.a.value))
    }
    @Test fun concurrentProgressQueriesAndCreationsConvergeWithoutLostRecords() = scenario { f ->
        repeat(3) {
            coroutineScope {
                val write = async { confirmed(client.create(f)) }
                val query = async { progress(client, f) }
                write.await(); query.await()
            }
        }
        val final = progress(client, f)
        assertEquals(3, f.records().size); assertEquals(18, final.path("attributes").path("STR").path("xp").intValue())
        assertEquals(f.root()["revision"], f.projection()["revision"])
    }
    @Test fun createReplyAndStoredProjectionAgreeWithoutDependingOnProgressQuery() = scenario { f ->
        val response = confirmed(client.create(f))
        val result = response.path("result"); val p = result.path("progression")
        assertEquals(f.cycle(), result.path("cycleId").textValue())
        assertEquals(1, result.path("version").intValue()); assertEquals(6, p.path("attributes").path("STR").path("xp").intValue())
        assertEquals((f.root()["revision"] as Number).toLong(), p.path("revision").longValue())
        assertEquals(f.root()["revision"], f.projection()["revision"])
        assertStoredAttribute(f, "STR", 1, 6)
        val record = f.records().single()
        assertEquals(result.path("recordId").textValue(), record.id)
        assertEquals("strength", record.getString("activityDefinition.id"))
        assertEquals("STR", record.getString("activityDefinition.primary"))
    }
    @Test fun customDefinitionIsResolvedOnlyFromTheAuthenticatedOwnersActivities() = scenario { f ->
        val fields = mapOf("name" to "Práctica mental", "category" to "Propia", "primary" to "MEN", "createdAt" to f.now.toString())
        f.resources.documents.write(f.a, listOf("activities", "synthetic-custom"), fields)
        val result = confirmed(client.create(f, payload("synthetic-custom"))).path("result").path("progression")
        assertEquals(6, result.path("attributes").path("MEN").path("xp").intValue())
        assertStoredAttribute(f, "MEN", 1, 6)
        rejected(client.create(f, payload("synthetic-custom"), token = f.tokenB, cycle = f.cycle(f.b)), "INVALID_INPUT")
        assertTrue(f.records(f.b).isEmpty())
    }
    @Test fun sleepAndMealCreationsRebuildStoredProgressWithoutQueryEndpoints() = scenario { f ->
        confirmed(client.create(f, payload("sleep"))); confirmed(client.create(f, payload("sleep")))
        assertStoredAttribute(f, "MEN", 1, 3)
        repeat(5) { confirmed(client.create(f, mapOf("activityId" to "meal", "activityDate" to "2026-11-11"))) }
        assertStoredAttribute(f, "CON", 1, 28)
        assertEquals(7, f.records().size)
    }
    @Test fun lostCreateReplyRecoversByResendingTheSameEnvelopeWithoutNewWrites() = scenario { f ->
        val id = OperationId.create(); f.dropAfterCommit.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.create(f, id = id).status)
        assertFalse(f.dropAfterCommit.get()); assertEquals(1, f.records().size)
        val before = f.root(); val original = assertNotNull(f.receipt(id))
        f.now = Instant.parse("2026-11-20T15:00:00Z")
        val recovered = confirmed(client.create(f, id = id))
        assertEquals(original.validatedAt.toString(), recovered.path("validatedAt").textValue())
        assertEquals(before, f.root()); assertEquals(1, f.records().size)
    }
    @Test fun concurrentCreateRepliesAndProjectionRespectTheLastXpPointWithoutQueryEndpoints() = scenario { f ->
        confirmed(client.create(f, payload(minutes = 235)))
        val responses = coroutineScope { listOf(async { client.create(f, payload(minutes = 5)) }, async { client.create(f, payload(minutes = 5)) }).map { it.await() } }
        responses.forEach { confirmed(it) }
        assertEquals(3, f.records().size); assertStoredAttribute(f, "STR", 2, 18)
        assertEquals(f.root()["revision"], f.projection()["revision"])
    }
    /** T037 receipt fixture: real shared commit protocol, without pretending the T039 creation handler exists. */
    private fun seedReceipt(f: Fixture, type: CommandType = CommandType.CREATE_RECORD, reject: Boolean = false): Pair<CommandEnvelope, OperationReceipt> {
        val cycle = Cycle(f.a, CycleId(f.cycle()), f.now)
        val record = ActivityRecord.create(RecordId("synthetic-receipt-record"), cycle.id,
            ActivityCatalog.find(ActivityId("strength"))!!, BusinessTime.date(f.now), 30, f.now)
        val payload = when (type) {
            CommandType.CREATE_RECORD -> payload()
            CommandType.EDIT_RECORD -> mapOf("recordId" to record.id.value, "expectedVersion" to 1, "activityDate" to "2026-11-11")
            CommandType.DELETE_RECORD -> mapOf("recordId" to record.id.value, "expectedVersion" to 1)
            else -> error("Unsupported fixture command")
        }
        val command = CommandEnvelope(OperationId.create(), type, cycle.id.value, json.valueToTree(payload))
        val operations = OperationStore(f.resources.firestore)
        val receipt = RevisionTransaction(f.resources.firestore, operations, AuthoritativeClock { f.now })
            .commit(f.a, command, Revision((f.root()["revision"] as Number).toLong())) { snapshot, now ->
                if (reject) OperationDecision.Rejected(ApiError.DATE_NOT_ALLOWED) else {
                    val actual = when (type) {
                        CommandType.DELETE_RECORD -> record.markDeleted(now)
                        CommandType.EDIT_RECORD -> record.correct(record.activityDate, durationMinutes = 35, now = now)
                        else -> record
                    }
                    val replay = ReplayEngine.rebuild(cycle, listOf(actual), now)
                    val progression = Progression.derived(cycle, snapshot.revision!!.next(), now, replay.attributes,
                        replay.general, replay.appearance.classId, replay.appearance.stage)
                    RecordOperations(operations).confirmation(f.a, command, actual, progression,
                        listOf(DocumentWrite(listOf("synthetic", "receipt-effect"), mapOf("marker" to command.operationId.value))))
                }
            }
        return command to receipt
    }
    private suspend fun HttpClient.resend(f: Fixture, command: CommandEnvelope, token: String = f.tokenA) = post("/v1/commands") {
        bearerAuth(token); contentType(ContentType.Application.Json)
        setBody(json.writeValueAsString(mapOf("operationId" to command.operationId.value, "type" to command.type.name,
            "cycleId" to command.cycleId, "payload" to command.payload)))
    }

    @Test fun persistedRecordReceiptsRecoverBeforeExpiredDateClosedCycleOrClockRead() = scenario { f ->
        for (type in listOf(CommandType.CREATE_RECORD, CommandType.EDIT_RECORD, CommandType.DELETE_RECORD)) {
            val (command, original) = seedReceipt(f, type)
            val before = f.root()
            f.resources.documents.write(f.a, emptyList(), before + ("currentCycleId" to "synthetic-new-cycle"))
            val changed = f.root()
            f.now = Instant.parse("2026-11-20T15:00:00Z")
            f.clockSequence.add(Instant.parse("2026-11-21T15:00:00Z"))
            val replay = body(client.resend(f, command))
            assertEquals("CONFIRMED", replay.path("outcome").textValue())
            assertEquals(original.validatedAt.toString(), replay.path("validatedAt").textValue())
            assertEquals(json.readTree(json.writeValueAsString(original.result)), replay.path("result"))
            assertEquals(changed, f.root()); assertEquals(1, f.clockSequence.size)
            f.clockSequence.clear(); f.now = Instant.parse("2026-11-11T15:00:00Z")
            f.resources.documents.write(f.a, emptyList(), before)
        }
    }
    @Test fun persistedRecordRejectionRecoversWithoutRecheckingDateOrChangingRevision() = scenario { f ->
        val (command, original) = seedReceipt(f, reject = true)
        val before = f.root(); f.now = Instant.parse("2026-11-20T15:00:00Z")
        val response = client.resend(f, command)
        val r = rejected(response, "DATE_NOT_ALLOWED")
        assertEquals(original.validatedAt.toString(), r.path("validatedAt").textValue())
        assertEquals(before, f.root()); assertTrue(f.records().isEmpty())
    }
    @Test fun persistedRecordReceiptReturnsOriginalProgressionAndNoPrivateFields() = scenario { f ->
        val (command, original) = seedReceipt(f)
        val response = client.resend(f, command)
        assertEquals(HttpStatusCode.OK, response.status)
        val r = body(response)
        assertEquals(json.readTree(json.writeValueAsString(original.result)), r.path("result"))
        assertEquals(original.validatedAt.toString(), r.path("validatedAt").textValue())
        assertFalse(r.has("requestHash")); assertFalse(r.has("commandType"))
        val p = r.path("result").path("progression")
        assertEquals(original.revision.value, p.path("revision").longValue())
        assertEquals(command.cycleId, p.path("cycleId").textValue())
        assertEquals(6, p.path("attributes").path("STR").path("xp").intValue())
        assertEquals(0, p.path("attributes").path("STR").path("inactiveDates").intValue())
    }
    @Test fun persistedRecordReceiptCannotBeReadOrRecoveredByAnotherUid() = scenario { f ->
        val (command, _) = seedReceipt(f)
        val foreign = f.root(f.b)
        assertNull(OperationStore(f.resources.firestore).find(f.b, command.operationId))
        assertNull(RecordOperations(OperationStore(f.resources.firestore)).recover(f.b, command))
        assertEquals(foreign, f.root(f.b)); assertNull(f.receipt(command.operationId, f.b))
    }
    @Test fun persistedRecordUuidCollisionChecksPayloadCycleAndCommandType() = scenario { f ->
        val (command, _) = seedReceipt(f)
        val before = f.receipt(command.operationId)
        val changedPayload = command.payload.deepCopy<JsonNode>().also { (it as com.fasterxml.jackson.databind.node.ObjectNode).put("minutes", 35) }
        val variants = listOf(command.copy(payload = changedPayload), command.copy(cycleId = "synthetic-other-cycle"),
            command.copy(type = CommandType.DELETE_RECORD, payload = json.valueToTree(mapOf("recordId" to "synthetic-receipt-record", "expectedVersion" to 1))))
        for (variant in variants) {
            val response = client.resend(f, variant)
            assertEquals(HttpStatusCode.Conflict, response.status)
            assertEquals("OPERATION_ID_REUSED", body(response).path("error").path("code").textValue())
            assertEquals(before, f.receipt(command.operationId))
        }
    }
    @Test fun persistedRecordProgressionRemainsAtItsAppliedRevisionAfterNewerReceipt() = scenario { f ->
        val (command, original) = seedReceipt(f)
        seedReceipt(f)
        val before = f.root(); val projection = f.projection()
        val response = client.resend(f, command)
        assertEquals(HttpStatusCode.OK, response.status)
        val recovered = body(response).path("result")
        assertEquals(original.revision.value, recovered.path("revision").longValue())
        assertEquals(original.revision.value, recovered.path("progression").path("revision").longValue())
        assertTrue((before["revision"] as Number).toLong() > original.revision.value)
        assertEquals(before, f.root()); assertEquals(projection, f.projection())
    }
    @Test fun recordConfirmationRejectsMismatchedOwnerCycleAndIdentityBeforeAnyWrite() = scenario { f ->
        val cycle = Cycle(f.a, CycleId(f.cycle()), f.now)
        val record = ActivityRecord.create(RecordId("synthetic-plan"), cycle.id, ActivityCatalog.find(ActivityId("strength"))!!,
            BusinessTime.date(f.now), 30, f.now)
        val command = CommandEnvelope(OperationId.create(), CommandType.CREATE_RECORD, cycle.id.value, json.valueToTree(payload()))
        val progression = Progression.initial(cycle, Revision(2))
        val service = RecordOperations(OperationStore(f.resources.firestore)); val before = f.root()
        assertFailsWith<IllegalArgumentException> { service.confirmation(f.b, command, record, progression, emptyList()) }
        assertFailsWith<IllegalArgumentException> { service.confirmation(f.a, command.copy(cycleId = "synthetic-other"), record, progression, emptyList()) }
        val edit = command.copy(type = CommandType.EDIT_RECORD, payload = json.valueToTree(mapOf("recordId" to "synthetic-other")))
        assertFailsWith<IllegalArgumentException> { service.confirmation(f.a, edit, record, progression, emptyList()) }
        assertEquals(before, f.root()); assertTrue(f.records().isEmpty())
    }
    private class Fixture(val resources: ServerResources, initialNow: Instant) {
        val a = Uid("synthetic-record-${UUID.randomUUID()}"); val b = Uid("synthetic-record-${UUID.randomUUID()}")
        val tokenA = "synthetic-record-token-a"; val tokenB = "synthetic-record-token-b"
        @Volatile var now = initialNow
        val clockSequence = ConcurrentLinkedQueue<Instant>()
        fun clockInstant(): Instant { clockSequence.poll()?.let { now = it }; return now }
        val dropAfterCommit = AtomicBoolean(); val failBeforeCommit = AtomicBoolean()
        fun verify(token: String) = when (token) { tokenA -> a.value; tokenB -> b.value; else -> null }
        fun root(owner: Uid = a) = resources.documents.read(owner, emptyList())!!
        fun cycle(owner: Uid = a) = root(owner).getValue("currentCycleId") as String
        fun projection(owner: Uid = a) = resources.documents.read(owner, listOf("projections", "current"))!!
        fun records(owner: Uid = a) = resources.firestore.collection("users/${owner.value}/records").get().get(10, TimeUnit.SECONDS).documents
        fun receipt(id: OperationId, owner: Uid = a) = OperationStore(resources.firestore).find(owner, id)
    }
    private fun scenario(initialNow: Instant = Instant.parse("2026-11-11T15:00:00Z"), block: suspend ApplicationTestBuilder.(Fixture) -> Unit) {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080", resources.firestore.options.host)
            assertEquals("127.0.0.1:8080", resources.firestore.options.emulatorHost)
            val f = Fixture(resources, initialNow)
            testApplication {
                application {
                    val base = accountHandlers(resources.firestore, AuthoritativeClock { f.clockInstant() })
                    serverModule(base.copy(command = { owner, command ->
                        if (command.type == CommandType.CREATE_RECORD && f.failBeforeCommit.compareAndSet(true, false))
                            throw InjectedFault(FaultPoint.BEFORE_COMMIT)
                        val response = base.command(owner, command)
                        if (command.type == CommandType.CREATE_RECORD && response.status == HttpStatusCode.OK &&
                            response.body.path("outcome").textValue() == "CONFIRMED" && f.dropAfterCommit.compareAndSet(true, false))
                            throw InjectedFault(FaultPoint.AFTER_COMMIT)
                        response
                    }), f::verify)
                }
                for (token in listOf(f.tokenA, f.tokenB)) {
                    val response = client.post("/v1/commands") {
                        bearerAuth(token); contentType(ContentType.Application.Json)
                        setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value, "type" to "COMPLETE_PROFILE",
                            "payload" to mapOf("username" to ("R" + UUID.randomUUID().toString().replace("-", "").take(12))))))
                    }
                    assertEquals(HttpStatusCode.OK, response.status, "US1 profile fixture must succeed before US2 assertions")
                }
                block(f)
            }
        }
    }
    private fun payload(activity: String = "strength", minutes: Int = 30, date: String = "2026-11-11") =
        mapOf<String, Any?>("activityId" to activity, "activityDate" to date, "hours" to minutes / 60, "minutes" to minutes % 60)
    private suspend fun HttpClient.create(f: Fixture, data: Map<String, Any?> = payload(), id: OperationId = OperationId.create(),
        cycle: String = f.cycle(), token: String = f.tokenA) = post("/v1/commands") {
        bearerAuth(token); contentType(ContentType.Application.Json)
        setBody(json.writeValueAsString(mapOf("operationId" to id.value, "type" to "CREATE_RECORD", "cycleId" to cycle, "payload" to data)))
    }
    private suspend fun body(response: HttpResponse) = json.readTree(response.bodyAsText())
    private suspend fun confirmed(response: HttpResponse): JsonNode {
        assertEquals(HttpStatusCode.OK, response.status)
        return body(response).also { assertEquals("CONFIRMED", it.path("outcome").textValue()) }
    }
    private suspend fun progress(client: HttpClient, f: Fixture): JsonNode {
        val response = client.get("/v1/progress") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.OK, response.status); return body(response)
    }
    private suspend fun rejected(response: HttpResponse, code: String): JsonNode {
        assertEquals(if (code == "CYCLE_CLOSED") HttpStatusCode.Conflict else HttpStatusCode.UnprocessableEntity, response.status)
        return body(response).also { assertEquals("REJECTED", it.path("outcome").textValue()); assertEquals(code, it.path("error").path("code").textValue()) }
    }
    private fun assertStoredAttribute(f: Fixture, name: String, level: Long, xp: Long) {
        val projection = f.projection()
        val stored = assertIs<Map<*, *>>(assertIs<Map<*, *>>(projection["attributes"])[name])
        assertEquals(level, stored["level"]); assertEquals(xp, stored["xp"])
        assertEquals(f.root()["revision"], projection["revision"])
    }

    @Test fun catalogContainsExactlyElevenBasicDefinitions() = scenario { f ->
        val before = f.root(); val response = client.get("/v1/catalog") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.OK, response.status)
        val activities = body(response).path("activities").toList(); assertEquals(11, activities.size)
        val expected = listOf(
            listOf("strength", "Entrenamiento de fuerza", "Actividad física", "DURATION", "STR", null),
            listOf("aerobic", "Actividad aeróbica", "Actividad física", "DURATION", "DEX", null),
            listOf("meal", "Comida saludable", "Nutrición", "OCCURRENCE", "CON", null),
            listOf("study", "Leer / estudiar", "Aprendizaje", "DURATION", "INT", null),
            listOf("hobby", "Ocio / hobby", "Bienestar", "DURATION", "WIT", null),
            listOf("sleep", "Dormir", "Descanso", "DURATION", "MEN", null),
            listOf("crossfit", "CrossFit", "Actividad física", "DURATION", "STR", "DEX"),
            listOf("martial-arts", "Artes marciales", "Actividad física", "DURATION", "DEX", "STR"),
            listOf("yoga", "Yoga", "Bienestar físico", "DURATION", "WIT", "DEX"),
            listOf("practical-skill", "Aprender una habilidad práctica", "Aprendizaje", "DURATION", "INT", "DEX"),
            listOf("strategy-games", "Juegos de estrategia", "Ocio / aprendizaje", "DURATION", "WIT", "INT"))
        val actual = activities.map { activity -> listOf("id", "name", "category", "mode", "primary", "secondary").map { activity.path(it).textValue() } }
        assertEquals(expected.toSet(), actual.toSet()); assertEquals(11, activities.map { it.path("id").textValue() }.toSet().size)
        assertEquals(before, f.root()); assertTrue(f.records().isEmpty())
    }
    @Test fun catalogContainsAllClassConditionsAndSeventySixNumericStagesWithoutRequiringImages() = scenario { f ->
        val response = client.get("/v1/catalog") { bearerAuth(f.tokenA) }; assertEquals(HttpStatusCode.OK, response.status)
        val classes = body(response).path("classes").toList(); assertEquals(26, classes.size)
        val pure = mapOf("Guerrero" to setOf("STR"), "Explorador" to setOf("DEX"), "Guardián" to setOf("CON"),
            "Sabio" to setOf("INT"), "Monje" to setOf("WIT"), "Místico" to setOf("MEN"))
        val pairs = mapOf("Maestro de Armas" to setOf("STR", "DEX"), "Paladín" to setOf("STR", "CON"),
            "Guerrero Arcano" to setOf("STR", "INT"), "Luchador Monje" to setOf("STR", "WIT"), "Guerrero Místico" to setOf("STR", "MEN"),
            "Ágil Guardián" to setOf("DEX", "CON"), "Artífice" to setOf("DEX", "INT"), "Ninja" to setOf("DEX", "WIT"),
            "Explorador de Sueños" to setOf("DEX", "MEN"), "Guardián de la Sabiduría" to setOf("CON", "INT"),
            "Monje Guardián" to setOf("CON", "WIT"), "Druida" to setOf("CON", "MEN"), "Ilusionista" to setOf("INT", "WIT"),
            "Oráculo" to setOf("INT", "MEN"), "Monje Espiritual" to setOf("WIT", "MEN"))
        val multiple = mapOf("Aventurero Versátil" to 3, "Maestro del Equilibrio" to 4, "Héroe Ascendente" to 5, "Héroe Integral" to 6)
        assertEquals((pure.keys + pairs.keys + multiple.keys + "Novato").toSet(), classes.map { it.path("name").textValue() }.toSet())
        assertEquals(26, classes.map { it.path("id").textValue().also { id -> assertTrue(id.isNotBlank()) } }.toSet().size)
        for (entry in classes) {
            val name = entry.path("name").textValue(); val attrs = entry.path("attributes").map { it.textValue() }.toSet()
            assertTrue(entry.path("attributes").isArray); assertTrue(entry.path("stages").isArray); assertTrue(entry.path("initialOnly").isBoolean)
            assertEquals(if (name == "Novato") listOf(1) else listOf(1, 2, 3), entry.path("stages").map { it.intValue() })
            assertEquals(name == "Novato", entry.path("initialOnly").booleanValue())
            if (name in pure || name in pairs) { assertEquals((pure + pairs).getValue(name), attrs); assertEquals(attrs.size, entry.path("balancedCount").intValue()) }
            else { assertTrue(attrs.isEmpty()); if (name == "Novato") { assertEquals("novato", entry.path("id").textValue()); assertTrue(entry.path("balancedCount").isNull) }
                else assertEquals(multiple.getValue(name), entry.path("balancedCount").intValue()) }
        }
        assertEquals(76, classes.sumOf { it.path("stages").size() }); assertTrue(f.records().isEmpty())
    }
    @Test fun initialProgressHasSixAttributesCurrentCycleAndAuthoritativeMetadata() = scenario { f ->
        val p = progress(client, f)
        assertEquals(f.cycle(), p.path("cycleId").textValue()); assertEquals("2026-11-11", p.path("businessDate").textValue())
        assertEquals(f.now, Instant.parse(p.path("computedAt").textValue())); assertTrue(p.path("revision").longValue() > 0)
        assertEquals(Attribute.entries.map { it.name }.toSet(), p.path("attributes").fieldNames().asSequence().toSet())
        p.path("attributes").forEach { assertEquals(1, it.path("level").intValue()); assertEquals(0, it.path("xp").intValue()); assertEquals(0, it.path("inactiveDates").intValue()) }
        assertEquals(1, p.path("generalLevel").intValue()); assertEquals(0, p.path("generalRemainder").intValue())
        assertEquals("novato", p.path("classId").textValue()); assertEquals(1, p.path("stage").intValue()); assertEquals(listOf(1), p.path("unlockedStages").map { it.intValue() })
    }
    @Test fun durationCreationCommitsRecordProjectionRootAndReceiptTogether() = scenario { f ->
        val id = OperationId.create(); val r = confirmed(client.create(f, id = id))
        val record = f.records().single(); val root = f.root(); val projection = f.projection()
        assertEquals(id.value, r.path("operationId").textValue()); assertEquals(record.id, r.path("result").path("recordId").textValue())
        assertEquals(1, r.path("result").path("version").intValue()); assertEquals(f.cycle(), r.path("result").path("cycleId").textValue())
        assertEquals(root["revision"], projection["revision"]); assertEquals((root["revision"] as Number).toLong(), r.path("result").path("revision").longValue())
        assertEquals(30L, record.getLong("durationMinutes")); assertEquals("2026-11-11", record.getString("activityDate"))
        assertEquals(f.now, Instant.parse(record.getString("createdAt")!!)); assertEquals(f.now, Instant.parse(r.path("validatedAt").textValue()))
        assertEquals(ReceiptOutcome.CONFIRMED, f.receipt(id)?.outcome)
        assertStoredAttribute(f, "STR", 1, 6)
        assertEquals(6, progress(client, f).path("attributes").path("STR").path("xp").intValue())
    }
    @Test fun dualActivityUsesServerDefinitionAndFourTwoSplit() = scenario { f ->
        confirmed(client.create(f, payload("crossfit"))); val p = progress(client, f)
        assertEquals(4, p.path("attributes").path("STR").path("xp").intValue()); assertEquals(2, p.path("attributes").path("DEX").path("xp").intValue())
        assertEquals(30L, f.records().single().getLong("durationMinutes"))
    }
    @Test fun sleepAccumulatesOnlyItsOwnTwoHalfHours() = scenario { f ->
        confirmed(client.create(f, payload("sleep"))); assertEquals(0, progress(client, f).path("attributes").path("MEN").path("xp").intValue())
        confirmed(client.create(f, payload("sleep"))); assertEquals(3, progress(client, f).path("attributes").path("MEN").path("xp").intValue())
        assertEquals(2, f.records().size)
    }
    @Test fun fifthMealIsStoredWithNoAdditionalConstitutionXpOrDuration() = scenario { f ->
        repeat(5) { confirmed(client.create(f, mapOf("activityId" to "meal", "activityDate" to "2026-11-11"))) }
        assertEquals(5, f.records().size); f.records().forEach { assertNull(it.get("durationMinutes")) }
        assertEquals(28, progress(client, f).path("attributes").path("CON").path("xp").intValue())
    }
    @Test fun clientXpOrAttributeOverridesAreRejectedBeforeWrites() = scenario { f ->
        val before = f.root(); val projection = f.projection()
        for ((key, value) in listOf("xp" to 99, "primary" to "MEN", "createdAt" to "2026-01-01T00:00:00Z", "uid" to f.b.value)) {
            val id = OperationId.create(); val response = client.create(f, payload() + (key to value), id)
            assertEquals(HttpStatusCode.BadRequest, response.status); assertNull(f.receipt(id))
        }
        assertTrue(f.records().isEmpty()); assertEquals(before, f.root()); assertEquals(projection, f.projection())
    }
    @Test fun invalidDurationComponentsProduceDefinitiveRejectionsWithoutRecords() = scenario { f ->
        for ((hours, minutes) in listOf(0 to 0, -1 to 30, 0 to 60, 1 to -1)) {
            val id = OperationId.create(); rejected(client.create(f, payload() + mapOf("hours" to hours, "minutes" to minutes), id), "INVALID_INPUT")
            assertEquals(ReceiptOutcome.REJECTED, f.receipt(id)?.outcome); assertTrue(f.records().isEmpty())
        }
    }
    @Test fun fractionalDurationIsRejectedBySchemaWithNoFunctionalReceipt() = scenario { f ->
        val id = OperationId.create(); val response = client.create(f, payload() + ("hours" to 0.5), id)
        assertEquals(HttpStatusCode.BadRequest, response.status); assertTrue(f.records().isEmpty()); assertNull(f.receipt(id))
    }
    @Test fun durationAndMealModalitiesCannotBeMixed() = scenario { f ->
        rejected(client.create(f, mapOf("activityId" to "strength", "activityDate" to "2026-11-11")), "INVALID_INPUT")
        rejected(client.create(f, payload("meal")), "INVALID_INPUT"); assertTrue(f.records().isEmpty())
    }
    @Test fun unknownActivityCannotFallbackToAnArbitraryDefinition() = scenario { f ->
        val id = OperationId.create()
        rejected(client.create(f, payload("synthetic-unknown-activity"), id), "INVALID_INPUT")
        assertTrue(f.records().isEmpty()); assertEquals(ReceiptOutcome.REJECTED, f.receipt(id)?.outcome)
    }
    @Test fun acceptedActivityDatesAreStoredAsRequestedWithoutSubstitution() = scenario { f ->
        val dates = setOf("2026-11-11", "2026-11-10", "2026-11-09")
        for (date in dates) confirmed(client.create(f, payload(date = date)))
        assertEquals(dates, f.records().map { it.getString("activityDate") }.toSet()); assertEquals(3, f.records().size)
    }
    @Test fun finalValidationAfterUtcThreeMidnightRejectsPreviouslyAllowedAnteayer() =
        scenario(Instant.parse("2026-11-10T15:00:00Z")) { f ->
            val before = Instant.parse("2026-11-11T02:59:59.999Z")
            val after = Instant.parse("2026-11-11T03:00:00Z")
            f.now = before; f.clockSequence.addAll(listOf(before, after))
            val id = OperationId.create(); val requested = payload(date = "2026-11-08")
            rejected(client.create(f, requested, id), "DATE_NOT_ALLOWED")
            assertTrue(f.clockSequence.isEmpty(), "The final attempt must obtain fresh authoritative time after initial calculation")
            assertEquals(after, f.now); assertEquals(after, f.receipt(id)?.validatedAt)
            assertTrue(f.records().isEmpty()); assertEquals("2026-11-08", requested["activityDate"])
        }
    @Test fun invalidDateProducesImmutableRejectionAndNewUuidCanCorrectIt() = scenario { f ->
        val id = OperationId.create(); val data = payload(date = "2026-11-08")
        val rejection = rejected(client.create(f, data, id), "DATE_NOT_ALLOWED")
        assertEquals(rejection, body(client.create(f, data, id))); assertTrue(f.records().isEmpty())
        val changed = client.create(f, payload(), id)
        assertEquals(HttpStatusCode.Conflict, changed.status); assertEquals("OPERATION_ID_REUSED", body(changed).path("error").path("code").textValue())
        confirmed(client.create(f)); assertEquals(1, f.records().size)
    }
    @Test fun wrongOrForeignCycleIsRejectedWithoutMovingTheRecord() = scenario { f ->
        val foreignRoot = f.root(f.b); val foreignProjection = f.projection(f.b)
        rejected(client.create(f, cycle = f.cycle(f.b)), "CYCLE_CLOSED")
        assertTrue(f.records().isEmpty()); assertTrue(f.records(f.b).isEmpty())
        assertEquals(foreignRoot, f.root(f.b)); assertEquals(foreignProjection, f.projection(f.b))
    }
    @Test fun absentOperationIsUnknownRatherThanRejectedAndCreatesNoReceipt() = scenario { f ->
        val id = OperationId.create(); val before = f.root()
        val response = client.get("/v1/operations/${id.value}") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.OK, response.status); val r = body(response)
        assertEquals(id.value, r.path("operationId").textValue()); assertEquals("UNKNOWN", r.path("outcome").textValue())
        assertFalse(r.has("error")); assertFalse(r.has("result")); assertNull(f.receipt(id)); assertEquals(before, f.root())
    }
    @Test fun operationLookupReturnsExactlyItsOriginalDefinitiveReceipt() = scenario { f ->
        val id = OperationId.create(); val receipt = confirmed(client.create(f, id = id))
        val response = client.get("/v1/operations/${id.value}") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.OK, response.status); assertEquals(receipt, body(response))
        assertFalse(receipt.toString().contains("requestHash")); assertFalse(receipt.toString().contains(f.tokenA))
    }
    @Test fun anotherUidCannotReadKnownOperationUuidOrItsRecord() = scenario { f ->
        val id = OperationId.create(); confirmed(client.create(f, id = id))
        val response = client.get("/v1/operations/${id.value}") { bearerAuth(f.tokenB) }
        assertEquals(HttpStatusCode.OK, response.status); val r = body(response)
        assertEquals("UNKNOWN", r.path("outcome").textValue()); assertFalse(r.has("result")); assertFalse(r.toString().contains(f.a.value))
        assertTrue(f.records(f.b).isEmpty()); assertNull(f.receipt(id, f.b))
    }
    @Test fun lostReplyAfterActualCommitRecoversWithoutDuplicateRecordOrXp() = scenario { f ->
        val id = OperationId.create(); f.dropAfterCommit.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.create(f, id = id).status)
        assertFalse(f.dropAfterCommit.get(), "Loss must happen after an actual CONFIRMED reply, not an arbitrary 503")
        assertEquals(1, f.records().size); assertEquals(ReceiptOutcome.CONFIRMED, f.receipt(id)?.outcome)
        val lookup = client.get("/v1/operations/${id.value}") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.OK, lookup.status); val original = body(lookup)
        assertEquals(original, confirmed(client.create(f, id = id))); assertEquals(1, f.records().size)
        assertEquals(6, progress(client, f).path("attributes").path("STR").path("xp").intValue())
    }
    @Test fun failureBeforeCommitHasNoDefinitiveReceiptAndAllowsSameUuidRetry() = scenario { f ->
        val id = OperationId.create(); val before = f.projection(); f.failBeforeCommit.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.create(f, id = id).status)
        assertNull(f.receipt(id)); assertTrue(f.records().isEmpty()); assertEquals(before, f.projection())
        confirmed(client.create(f, id = id)); assertEquals(1, f.records().size)
    }
    @Test fun sameUuidDifferentContentCannotReplaceRecordOrOriginalReceipt() = scenario { f ->
        val id = OperationId.create(); val first = confirmed(client.create(f, id = id))
        val collision = client.create(f, payload(minutes = 35), id)
        assertEquals(HttpStatusCode.Conflict, collision.status); assertEquals("OPERATION_ID_REUSED", body(collision).path("error").path("code").textValue())
        assertEquals(first, confirmed(client.create(f, id = id))); assertEquals(30L, f.records().single().getLong("durationMinutes"))
    }
    @Test fun intentionalSameContentWithNewUuidCreatesAnotherRecord() = scenario { f ->
        confirmed(client.create(f)); confirmed(client.create(f))
        assertEquals(2, f.records().size); assertEquals(12, progress(client, f).path("attributes").path("STR").path("xp").intValue())
    }
    @Test fun concurrentDistinctUuidsShareLastDailyXpPointAndBothPersist() = scenario { f ->
        confirmed(client.create(f, payload(minutes = 235)))
        val replies = coroutineScope { listOf(async { client.create(f, payload(minutes = 5)) }, async { client.create(f, payload(minutes = 5)) }).map { it.await() } }
        replies.forEach { confirmed(it) }; assertEquals(3, f.records().size)
        assertStoredAttribute(f, "STR", 2, 18)
        val str = progress(client, f).path("attributes").path("STR")
        assertEquals(2, str.path("level").intValue()); assertEquals(18, str.path("xp").intValue())
    }
    @Test fun concurrentDurationsCompeteForOneRemainingHour() = scenario { f ->
        confirmed(client.create(f, payload(minutes = 1380)))
        val replies = coroutineScope { listOf(async { client.create(f, payload(minutes = 60)) }, async { client.create(f, payload(minutes = 60)) }).map { it.await() } }
        assertEquals(1, replies.count { it.status == HttpStatusCode.OK }); assertEquals(1, replies.count { it.status == HttpStatusCode.UnprocessableEntity })
        rejected(replies.single { it.status == HttpStatusCode.UnprocessableEntity }, "DAILY_DURATION_EXCEEDED")
        assertEquals(2, f.records().size); assertEquals(1440L, f.records().sumOf { it.getLong("durationMinutes")!! })
    }
    @Test fun oldReceiptAfterNewerRecordAndExpiredDateDoesNotOverwriteCurrentProgress() = scenario { f ->
        val id = OperationId.create(); val original = confirmed(client.create(f, id = id))
        confirmed(client.create(f)); val before = progress(client, f)
        f.now = Instant.parse("2026-11-15T15:00:00Z")
        assertEquals(original, confirmed(client.create(f, id = id)))
        val current = progress(client, f)
        assertTrue(current.path("revision").longValue() >= before.path("revision").longValue())
        assertEquals(12, current.path("attributes").path("STR").path("xp").intValue()); assertEquals(2, f.records().size)
        assertEquals("2026-11-15", current.path("businessDate").textValue())
    }
    @Test fun progressQueriesApplyClosedDateInactivityWithoutNewRecordsAndDoNotDoublePenalize() = scenario { f ->
        confirmed(client.create(f, payload(minutes = 60)))
        f.now = Instant.parse("2026-11-19T15:00:00Z") // Nov 12–18: seven closed inactive dates after Nov 11 activity.
        val first = progress(client, f); val second = progress(client, f)
        assertEquals(7, first.path("attributes").path("STR").path("xp").intValue())
        assertEquals(first.path("attributes"), second.path("attributes")); assertEquals(first.path("generalLevel"), second.path("generalLevel"))
        assertEquals(1, f.records().size)
    }
    @Test fun commonAuthenticationAndOwnerQueryGuardsProtectAllUs2Endpoints() = scenario { f ->
        for (path in listOf("/v1/catalog", "/v1/progress", "/v1/operations/${OperationId.create().value}"))
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/progress?uid=${f.b.value}") { bearerAuth(f.tokenA) }.status)
        assertTrue(f.records().isEmpty()); assertTrue(f.records(f.b).isEmpty())
    }
}
