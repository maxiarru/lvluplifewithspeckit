package lvluplife.activity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.test.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.fixtures.FaultPoint
import lvluplife.fixtures.InjectedFault
import lvluplife.model.*
import lvluplife.persistence.*

/** Auth/schema/IO contracts over local Firestore; provider/time/fault ports are fixture-only. */
class ActivityContractTest {
    private val json = ObjectMapper()
    private class Fixture(val resources: ServerResources) {
        val a = Uid("synthetic-activity-${UUID.randomUUID()}"); val b = Uid("synthetic-activity-${UUID.randomUUID()}")
        val tokenA = "synthetic-activity-token-a"; val tokenB = "synthetic-activity-token-b"
        val now = Instant.parse("2026-11-11T15:00:00Z")
        val failBefore = AtomicBoolean(); val dropAfter = AtomicBoolean()
        var gate: CompletableDeferred<Unit>? = null; val arrivals = AtomicInteger()
        fun verify(token: String) = when (token) { tokenA -> a.value; tokenB -> b.value; else -> null }
        fun root(uid: Uid = a) = resources.documents.read(uid, emptyList())!!
        fun definitions(uid: Uid = a) = resources.firestore.collection("users/${uid.value}/activities").get().get(10, TimeUnit.SECONDS).documents
        fun records(uid: Uid = a) = resources.firestore.collection("users/${uid.value}/records").get().get(10, TimeUnit.SECONDS).documents
        fun receipt(id: OperationId, uid: Uid = a) = OperationStore(resources.firestore).find(uid, id)
    }
    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) {
        val config = ServerConfig.fromEnvironment()
        check(config.mode == BackendMode.EMULATOR && config.projectId == "demo-lvluplife")
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080", resources.firestore.options.host)
            assertEquals("127.0.0.1:8080", resources.firestore.options.emulatorHost)
            val f = Fixture(resources)
            testApplication {
                application {
                    val base = accountHandlers(resources.firestore, AuthoritativeClock { f.now })
                    serverModule(base.copy(command = { uid, command ->
                        if (command.type == CommandType.CREATE_CUSTOM_ACTIVITY) {
                            f.gate?.let { gate -> if (f.arrivals.incrementAndGet() == 2) gate.complete(Unit); withTimeout(10_000) { gate.await() } }
                            if (f.failBefore.compareAndSet(true, false)) throw InjectedFault(FaultPoint.BEFORE_COMMIT)
                        }
                        val response = base.command(uid, command)
                        if (command.type == CommandType.CREATE_CUSTOM_ACTIVITY && response.status == HttpStatusCode.OK &&
                            response.body.path("outcome").textValue() == "CONFIRMED" && f.dropAfter.compareAndSet(true, false))
                            throw InjectedFault(FaultPoint.AFTER_COMMIT)
                        response
                    }), f::verify)
                }
                for (token in listOf(f.tokenA, f.tokenB)) {
                    val response = client.post("/v1/commands") {
                        bearerAuth(token); contentType(ContentType.Application.Json)
                        setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value, "type" to "COMPLETE_PROFILE",
                            "payload" to mapOf("username" to ("C" + UUID.randomUUID().toString().replace("-", "").take(12))))))
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                }
                block(f)
            }
        }
    }
    private fun payload(name: String = "Práctica personal", primary: String = "STR", secondary: String? = null,
        category: String = "Categoría libre") = linkedMapOf<String, Any?>("name" to name, "category" to category, "primary" to primary).apply {
        secondary?.let { put("secondary", it) }
    }
    private suspend fun HttpClient.create(f: Fixture, data: Map<String, Any?> = payload(), id: OperationId = OperationId.create(),
        token: String = f.tokenA) = post("/v1/commands") {
        bearerAuth(token); contentType(ContentType.Application.Json)
        setBody(json.writeValueAsString(mapOf("operationId" to id.value, "type" to "CREATE_CUSTOM_ACTIVITY", "payload" to data)))
    }
    private suspend fun body(response: HttpResponse) = json.readTree(response.bodyAsText())
    private suspend fun confirmed(response: HttpResponse): JsonNode {
        assertEquals(HttpStatusCode.OK, response.status)
        return body(response).also { assertEquals("CONFIRMED", it.path("outcome").textValue()) }
    }
    private suspend fun rejected(response: HttpResponse): JsonNode {
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        return body(response).also { assertEquals("REJECTED", it.path("outcome").textValue()); assertEquals("INVALID_INPUT", it.path("error").path("code").textValue()) }
    }
    private suspend fun HttpClient.list(f: Fixture, token: String = f.tokenA): JsonNode {
        val response = get("/v1/custom-activities") { bearerAuth(token) }; assertEquals(HttpStatusCode.OK, response.status)
        return body(response).also { assertTrue(it.path("items").isArray); assertTrue(it.path("complete").booleanValue()) }
    }

    @Test fun initialListIsCompleteEmptyAndReadOnly() = scenario { f ->
        val before = f.root(); val result = client.list(f)
        assertEquals(0, result.path("items").size()); assertEquals((before["revision"] as Number).toLong(), result.path("revision").longValue())
        assertEquals(before, f.root()); assertTrue(f.definitions().isEmpty())
    }
    @Test fun creationWithoutSecondaryCommitsOwnedDefinitionRevisionAndReceipt() = scenario { f ->
        val id = OperationId.create(); val before = (f.root()["revision"] as Number).toLong()
        val response = confirmed(client.create(f, id = id)); val result = response.path("result")
        val definition = f.definitions().single()
        assertEquals(definition.id, result.path("activityId").textValue()); assertEquals("Práctica personal", definition.getString("name"))
        assertEquals("STR", definition.getString("primary")); assertNull(definition.get("secondary"))
        assertEquals(f.now.toString(), definition.getString("createdAt")); assertEquals(f.now.toString(), result.path("createdAt").textValue())
        assertEquals(before + 1, (f.root()["revision"] as Number).toLong()); assertEquals(before + 1, result.path("revision").longValue())
        assertEquals(ReceiptOutcome.CONFIRMED, f.receipt(id)?.outcome); assertNull(f.receipt(id)?.cycleId)
        assertTrue(f.definitions(f.b).isEmpty()); assertTrue(f.records().isEmpty())
    }
    @Test fun everyAllowedPrimaryCanBeCreatedWithoutSecondary() = scenario { f ->
        for (primary in listOf("STR", "DEX", "INT", "WIT", "MEN")) confirmed(client.create(f, payload(primary = primary)))
        assertEquals(setOf("STR", "DEX", "INT", "WIT", "MEN"), f.definitions().map { it.getString("primary") }.toSet())
    }
    @Test fun allDistinctOrderedPairsAreAcceptedByTheService() = scenario { f ->
        val allowed = listOf("STR", "DEX", "INT", "WIT", "MEN")
        for (primary in allowed) for (secondary in allowed.filter { it != primary }) confirmed(client.create(f, payload(primary = primary, secondary = secondary)))
        assertEquals(20, f.definitions().size)
    }
    @Test fun trimmedBoundaryNamesAndUnicodeAreStoredCanonically() = scenario { f ->
        for (name in listOf("A", "N".repeat(50), "🧠".repeat(50))) confirmed(client.create(f, payload(name = "  $name  ")))
        assertEquals(setOf("A", "N".repeat(50), "🧠".repeat(50)), f.definitions().map { it.getString("name") }.toSet())
    }
    @Test fun invalidNamesProduceDefinitiveRejectionsWithoutDefinitions() = scenario { f ->
        for (name in listOf("", "   ", "N".repeat(51))) {
            val id = OperationId.create(); rejected(client.create(f, payload(name = name), id))
            assertEquals(ReceiptOutcome.REJECTED, f.receipt(id)?.outcome)
        }
        assertTrue(f.definitions().isEmpty())
    }
    @Test fun blankCategoryCannotBeStored() = scenario { f ->
        for (category in listOf("", "  ")) rejected(client.create(f, payload(category = category)))
        assertTrue(f.definitions().isEmpty())
    }
    @Test fun freeCategoryIsPreservedWithoutInventedShortLimit() = scenario { f ->
        val category = "  Mi categoría / propia 🎨 " + "C".repeat(1000)
        confirmed(client.create(f, payload(category = category)))
        assertEquals(category, f.definitions().single().getString("category"))
    }
    @Test fun constitutionAsPrincipalIsRejected() = scenario { f ->
        rejected(client.create(f, payload(primary = "CON"))); assertTrue(f.definitions().isEmpty())
    }
    @Test fun constitutionAsSecondaryIsRejected() = scenario { f ->
        rejected(client.create(f, payload(secondary = "CON"))); assertTrue(f.definitions().isEmpty())
    }
    @Test fun repeatedAttributesAreRejected() = scenario { f ->
        rejected(client.create(f, payload(secondary = "STR"))); assertTrue(f.definitions().isEmpty())
    }
    @Test fun unknownAttributeNamesAreFunctionalRejections() = scenario { f ->
        rejected(client.create(f, payload(primary = "SPEED"))); rejected(client.create(f, payload(secondary = "SPEED")))
        assertTrue(f.definitions().isEmpty())
    }
    @Test fun missingWrongTypedAndUnknownPayloadFieldsAreSchemaErrorsWithoutReceipts() = scenario { f ->
        val data = listOf(payload() - "primary", payload() + ("secondary" to null), payload() + ("primary" to 3), payload() + ("mode" to "OCCURRENCE"))
        val before = f.root()
        for (value in data) { val id = OperationId.create(); assertEquals(HttpStatusCode.BadRequest, client.create(f, value, id).status); assertNull(f.receipt(id)) }
        assertEquals(before, f.root()); assertTrue(f.definitions().isEmpty())
    }
    @Test fun clientOwnerXpAndCreationOverridesAreRejectedBeforeWrites() = scenario { f ->
        val before = f.root()
        for ((key, value) in listOf("uid" to f.b.value, "xp" to 99, "createdAt" to f.now.toString(), "id" to "synthetic-client-id")) {
            val id = OperationId.create(); assertEquals(HttpStatusCode.BadRequest, client.create(f, payload() + (key to value), id).status); assertNull(f.receipt(id))
        }
        assertEquals(before, f.root()); assertTrue(f.definitions(f.a).isEmpty()); assertTrue(f.definitions(f.b).isEmpty())
    }
    @Test fun cycleIsNotAcceptedForAnAccountScopedDefinition() = scenario { f ->
        val id = OperationId.create(); val response = client.post("/v1/commands") {
            bearerAuth(f.tokenA); contentType(ContentType.Application.Json)
            setBody(json.writeValueAsString(mapOf("operationId" to id.value, "type" to "CREATE_CUSTOM_ACTIVITY", "cycleId" to f.root()["currentCycleId"], "payload" to payload())))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status); assertNull(f.receipt(id)); assertTrue(f.definitions().isEmpty())
    }
    @Test fun listHasStableCreationThenOrdinalIdOrderAndCompleteMetadata() = scenario { f ->
        val ids = listOf("Z", "A", "M").map { confirmed(client.create(f, payload(name = it))).path("result").path("activityId").textValue() }
        val before = f.root(); val items = client.list(f).path("items")
        assertEquals(ids.sorted(), items.map { it.path("id").textValue() })
        for (item in items) {
            assertEquals("DURATION", item.path("mode").textValue()); assertEquals("STR", item.path("primary").textValue())
            assertTrue(item.path("secondary").isNull); assertEquals(f.now.toString(), item.path("createdAt").textValue())
            assertFalse(item.has("uid")); assertFalse(item.has("requestHash"))
        }
        assertEquals(before, f.root())
    }
    @Test fun sameUuidAndContentReturnOriginalResultWithoutAnotherDefinition() = scenario { f ->
        val id = OperationId.create(); val first = confirmed(client.create(f, id = id)); val before = f.root()
        assertEquals(first, confirmed(client.create(f, id = id))); assertEquals(before, f.root()); assertEquals(1, f.definitions().size)
    }
    @Test fun reusedUuidWithChangedContentCannotReplaceOriginal() = scenario { f ->
        val id = OperationId.create(); val first = confirmed(client.create(f, id = id))
        val changed = client.create(f, payload(name = "Otro nombre"), id)
        assertEquals(HttpStatusCode.Conflict, changed.status); assertEquals("OPERATION_ID_REUSED", body(changed).path("error").path("code").textValue())
        assertEquals(first, confirmed(client.create(f, id = id))); assertEquals(1, f.definitions().size)
    }
    @Test fun rejectedUuidIsImmutableAndCorrectionNeedsANewUuid() = scenario { f ->
        val id = OperationId.create(); val bad = payload(primary = "CON")
        val first = rejected(client.create(f, bad, id)); assertEquals(first, rejected(client.create(f, bad, id)))
        assertEquals(HttpStatusCode.Conflict, client.create(f, payload(), id).status)
        confirmed(client.create(f)); assertEquals(1, f.definitions().size)
    }
    @Test fun lostReplyAfterConfirmedCommitRecoversFromOwnReceipt() = scenario { f ->
        val id = OperationId.create(); f.dropAfter.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.create(f, id = id).status)
        assertFalse(f.dropAfter.get()); assertEquals(1, f.definitions().size)
        val before = f.root(); val lookup = client.get("/v1/operations/${id.value}") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.OK, lookup.status); assertEquals(body(lookup), confirmed(client.create(f, id = id)))
        assertEquals(before, f.root()); assertEquals(1, f.definitions().size)
    }
    @Test fun failureBeforeCommitDoesNotCreateDefinitiveRejection() = scenario { f ->
        val id = OperationId.create(); val before = f.root(); f.failBefore.set(true)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.create(f, id = id).status)
        assertNull(f.receipt(id)); assertTrue(f.definitions().isEmpty()); assertEquals(before, f.root())
        confirmed(client.create(f, id = id)); assertEquals(1, f.definitions().size)
    }
    @Test fun concurrentSameUuidCreatesOnlyOneDefinition() = scenario { f ->
        val id = OperationId.create(); f.gate = CompletableDeferred()
        val responses = coroutineScope { listOf(async { client.create(f, id = id) }, async { client.create(f, id = id) }).map { it.await() } }
        assertEquals(confirmed(responses[0]), confirmed(responses[1])); assertEquals(1, f.definitions().size)
    }
    @Test fun sameNameWithNewUuidCreatesAnotherIntentionalDefinition() = scenario { f ->
        val first = confirmed(client.create(f)); val second = confirmed(client.create(f))
        assertNotEquals(first.path("result").path("activityId"), second.path("result").path("activityId")); assertEquals(2, f.definitions().size)
    }
    @Test fun otherUidCannotSeeDefinitionAndMayUseSameOperationUuidIndependently() = scenario { f ->
        val id = OperationId.create(); val a = confirmed(client.create(f, id = id))
        assertEquals(0, client.list(f, f.tokenB).path("items").size())
        val b = confirmed(client.create(f, id = id, token = f.tokenB))
        assertNotEquals(a.path("result").path("activityId"), b.path("result").path("activityId"))
        assertEquals(1, f.definitions(f.a).size); assertEquals(1, f.definitions(f.b).size)
    }
    @Test fun ownSavedDefinitionCanBeReusedAsOrdinaryMentalDuration() = scenario { f ->
        val id = confirmed(client.create(f, payload(primary = "MEN", secondary = "WIT"))).path("result").path("activityId").textValue()
        val response = client.post("/v1/commands") {
            bearerAuth(f.tokenA); contentType(ContentType.Application.Json)
            setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value, "type" to "CREATE_RECORD", "cycleId" to f.root()["currentCycleId"],
                "payload" to mapOf("activityId" to id, "activityDate" to "2026-11-11", "hours" to 0, "minutes" to 30))))
        }
        val p = confirmed(response).path("result").path("progression")
        assertEquals(4, p.path("attributes").path("MEN").path("xp").intValue()); assertEquals(2, p.path("attributes").path("WIT").path("xp").intValue())
        assertEquals(1, f.definitions().size); assertEquals(id, f.records().single().getString("activityId"))
    }
    @Test fun anotherOwnersDefinitionCannotBeUsedToCreateARecord() = scenario { f ->
        val id = confirmed(client.create(f)).path("result").path("activityId").textValue()
        val response = client.post("/v1/commands") {
            bearerAuth(f.tokenB); contentType(ContentType.Application.Json)
            setBody(json.writeValueAsString(mapOf("operationId" to OperationId.create().value, "type" to "CREATE_RECORD", "cycleId" to f.root(f.b)["currentCycleId"],
                "payload" to mapOf("activityId" to id, "activityDate" to "2026-11-11", "hours" to 0, "minutes" to 30))))
        }
        rejected(response); assertTrue(f.records(f.b).isEmpty()); assertEquals(1, f.definitions(f.a).size)
    }
    @Test fun corruptStoredDefinitionCannotBePresentedAsACompletePartialList() = scenario { f ->
        confirmed(client.create(f))
        f.resources.documents.write(f.a, listOf("activities", "synthetic-corrupt"), mapOf("name" to "", "category" to "C", "primary" to "CON", "createdAt" to f.now.toString()))
        val before = f.root(); val response = client.get("/v1/custom-activities") { bearerAuth(f.tokenA) }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val value = body(response); assertFalse(value.has("items")); assertFalse(value.has("complete")); assertEquals(before, f.root())
    }
    @Test fun commonAuthAndOwnerQueryGuardsApplyBeforeCustomHandlers() = scenario { f ->
        val before = f.root()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/custom-activities").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/custom-activities?uid=${f.b.value}") { bearerAuth(f.tokenA) }.status)
        assertEquals(before, f.root()); assertTrue(f.definitions().isEmpty())
    }
}
