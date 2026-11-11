package lvluplife.persistence

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.TransactionOptions
import com.google.api.core.ApiFuture
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import lvluplife.api.ApiError
import lvluplife.api.ApiException
import lvluplife.api.CommandEnvelope
import lvluplife.api.CommandType
import lvluplife.application.AuthoritativeClock
import lvluplife.model.Revision
import lvluplife.model.Uid
import lvluplife.model.Username
import lvluplife.model.CycleId
import lvluplife.model.BusinessTime

/** Paths are relative collection/document pairs beneath the verified UID. */
data class DocumentWrite(val path: List<String>, val fields: Map<String, Any?>)
/** Null expects absence; otherwise the specified fields must still equal their expected values. */
data class DocumentCondition(val path: List<String>, val expectedFields: Map<String, Any?>?)
data class RevisionSnapshot(val root: Map<String, Any?>?, val revision: Revision?,
    val documents: Map<List<String>, Map<String, Any?>?>, val usernameOwners: Map<String, Uid?> = emptyMap())
sealed interface OperationDecision {
    data class Confirmed(val rootFields: Map<String, Any?>, val writes: List<DocumentWrite>, val result: Map<String, Any?>,
        val claims: List<Username> = emptyList()) : OperationDecision
    data class Rejected(val error: ApiError) : OperationDecision
}
class RevisionChanged : RuntimeException("Revision changed")
class PreconditionChanged : RuntimeException("Precondition changed")

/** Strongly consistent, UID-scoped input for external pure replay. Includes tombstones. */
data class RecordSnapshot(val root: Map<String, Any?>, val revision: Revision, val cycleId: CycleId,
    val cycle: Map<String, Any?>?, val records: Map<String, Map<String, Any?>>)

/** Short commit only: callers rebuild their external snapshot on RevisionChanged/PreconditionChanged.
 * decide must be pure, may be invoked again by the SDK, and must not perform external IO.
 */
class RevisionTransaction(private val firestore: Firestore, private val operations: OperationStore, private val clock: AuthoritativeClock) {
    fun recordSnapshot(uid: Uid): RecordSnapshot {
        try {
            val rootReference = firestore.document("users/${uid.value}")
            val before = requireNotNull(await(rootReference.get()).data) { "Missing account root" }
            val revision = Revision(before["revision"] as Long)
            require(revision.value > 0) { "Invalid account revision" }
            val cycleId = CycleId(before["currentCycleId"] as String)
            val cycle = await(firestore.document("users/${uid.value}/cycles/${cycleId.value}").get()).data
            val records = await(firestore.collection("users/${uid.value}/records")
                .whereEqualTo("cycleId", cycleId.value).get()).documents.associate { document ->
                document.id to immutableFields(requireNotNull(document.data))
            }
            val after = await(rootReference.get()).data
            if (before != after) throw RevisionChanged()
            return RecordSnapshot(immutableFields(before), revision, cycleId, cycle?.let(::immutableFields),
                Collections.unmodifiableMap(records))
        } catch (changed: RevisionChanged) { throw changed }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw PersistenceUnavailable() }
        catch (_: Exception) { throw PersistenceUnavailable() }
    }

    /** prepare performs pure replay only; it may run again after conflict or for final authoritative creation time. */
    fun commitRecords(uid: Uid, command: CommandEnvelope, conditions: List<DocumentCondition> = emptyList(),
        prepare: (RecordSnapshot, Instant) -> OperationDecision): OperationReceipt {
        require(command.type in setOf(CommandType.CREATE_RECORD, CommandType.EDIT_RECORD, CommandType.DELETE_RECORD)) {
            "Expected record mutation"
        }
        repeat(4) {
            operations.findMatching(uid, command)?.let { return it }
            try {
                val snapshot = recordSnapshot(uid)
                val calculatedAt = clock.now()
                val cyclePath = listOf("cycles", snapshot.cycleId.value)
                val prepared = if (command.cycleId != snapshot.cycleId.value || snapshot.cycle == null || snapshot.cycle["closedAt"] != null)
                    OperationDecision.Rejected(ApiError.CYCLE_CLOSED) else prepare(snapshot, calculatedAt)
                return commit(uid, command, snapshot.revision,
                    conditions = conditions + DocumentCondition(cyclePath, snapshot.cycle)) { latest, validationNow ->
                    if (BusinessTime.date(calculatedAt) != BusinessTime.date(validationNow)) throw RecordDateChanged()
                    val currentCycle = latest.documents[cyclePath]
                    when {
                        latest.root?.get("currentCycleId") != command.cycleId || currentCycle == null || currentCycle["closedAt"] != null ->
                            OperationDecision.Rejected(ApiError.CYCLE_CLOSED)
                        // A final creation instant can move an ordinal tie. Rebuild purely, with no extra IO inside commit.
                        validationNow != calculatedAt -> prepare(snapshot, validationNow)
                        else -> prepared
                    }
                }
            } catch (_: RevisionChanged) { /* Discard the external replay and read root/records/root again. */ }
            catch (_: PreconditionChanged) { /* Never commit a plan with obsolete document preconditions. */ }
            catch (_: RecordDateChanged) { /* Rebuild outside the transaction for the new business date. */ }
        }
        throw PersistenceUnavailable()
    }

    private class RecordDateChanged : RuntimeException("Business date changed")

    private fun immutableFields(fields: Map<String, Any?>): Map<String, Any?> =
        Collections.unmodifiableMap(fields.mapValues { immutableValue(it.value) })

    private fun immutableValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> Collections.unmodifiableMap(value.entries.associate { (key, item) ->
            require(key is String) { "Invalid document field" }
            key to immutableValue(item)
        })
        is List<*> -> Collections.unmodifiableList(value.map(::immutableValue))
        else -> value
    }

    fun commit(uid: Uid, command: CommandEnvelope, expectedRevision: Revision?,
        conditions: List<DocumentCondition> = emptyList(),
        usernames: List<Username> = emptyList(),
        decide: (RevisionSnapshot, Instant) -> OperationDecision): OperationReceipt {
        var future: ApiFuture<OperationReceipt>? = null
        try {
            val options = TransactionOptions.createReadWriteOptionsBuilder().setNumberOfAttempts(5).build()
            future = firestore.runTransaction({ transaction ->
                // Receipt lookup always wins, including over obsolete revisions and conditions.
                val existing = operations.readForCommit(transaction, uid, command)
                if (existing != null) return@runTransaction existing
                val rootReference = firestore.document("users/${uid.value}")
                val root = await(transaction.get(rootReference)).data
                val revision = root?.let { Revision(it["revision"] as Long) }
                if (revision != expectedRevision) throw RevisionChanged()
                val documents = conditions.map { it.path }.distinct().associateWith { path ->
                    validatePath(path)
                    await(transaction.get(firestore.document("users/${uid.value}/${path.joinToString("/")}"))).data
                }
                for (condition in conditions) {
                    val actual = documents[condition.path]
                    val expected = condition.expectedFields
                    val matches = if (expected == null) actual == null else actual != null &&
                        expected.all { (key, value) -> actual.containsKey(key) && actual[key] == value }
                    if (!matches) throw PreconditionChanged()
                }
                val usernameOwners = usernames.distinctBy { it.key }.associate { name ->
                    val fields = await(transaction.get(firestore.document(usernameReservationPath(name)))).data
                    name.key to fields?.let {
                        require(it["usernameKey"] == null || it["usernameKey"] == name.key) { "Invalid name reservation" }
                        Uid(it["uid"] as String)
                    }
                }
                val next = (revision ?: Revision(0)).next()
                val validationNow = clock.now()
                val decision = try { decide(RevisionSnapshot(root, revision, documents, usernameOwners), validationNow) }
                catch (failure: Exception) { throw DecisionFailure(failure) }
                val receipt = when (decision) {
                    is OperationDecision.Confirmed -> {
                        require("revision" !in decision.rootFields) { "Revision is controlled by the commit protocol" }
                        decision.writes.forEach { validatePath(it.path) }
                        require(decision.claims.distinctBy { it.key }.size == decision.claims.size) { "Duplicate name claim" }
                        require(decision.claims.all { it.key in usernameOwners && usernameOwners[it.key] == null }) { "Name claim was not read as absent" }
                        OperationReceipt(command.operationId, command.type, operations.canonicalHash(command),
                            ReceiptOutcome.CONFIRMED, validationNow, next, command.cycleId, result = decision.result)
                    }
                    is OperationDecision.Rejected -> OperationReceipt(command.operationId, command.type,
                        operations.canonicalHash(command), ReceiptOutcome.REJECTED, validationNow, next, command.cycleId, error = decision.error)
                }
                // All reads finished. Every staged write is committed together, or none is applied.
                operations.create(transaction, uid, receipt)
                val rootFields = if (decision is OperationDecision.Confirmed) decision.rootFields else emptyMap()
                transaction.set(rootReference, rootFields + ("revision" to next.value), SetOptions.merge())
                if (decision is OperationDecision.Confirmed) decision.writes.forEach { write ->
                    transaction.set(firestore.document("users/${uid.value}/${write.path.joinToString("/")}"), write.fields)
                }
                if (decision is OperationDecision.Confirmed) decision.claims.forEach { name ->
                    transaction.create(firestore.document(usernameReservationPath(name)),
                        mapOf("uid" to uid.value, "usernameKey" to name.key, "createdAt" to validationNow.toString()))
                }
                receipt
            }, options)
            return future.get(20, TimeUnit.SECONDS)
        } catch (failure: Exception) {
            future?.cancel(true)
            val actual = unwrap(failure)
            when (actual) {
                is DecisionFailure -> throw actual.original
                is RevisionChanged -> throw actual
                is PreconditionChanged -> throw actual
                is ApiException -> throw actual
                is CancellationException -> throw actual
                is InterruptedException -> { Thread.currentThread().interrupt(); throw PersistenceUnavailable() }
                else -> throw PersistenceUnavailable()
            }
        }
    }

    private fun validatePath(path: List<String>) {
        require(path.isNotEmpty() && path.size % 2 == 0 && path.first() != "operations") { "Invalid mutation path" }
        require(path.all { it.isNotBlank() && it != "." && it != ".." && '/' !in it && it.none(Char::isISOControl) &&
            it.toByteArray(Charsets.UTF_8).size <= 1500 }) { "Invalid mutation path" }
    }

    private fun <T> await(future: ApiFuture<T>): T = try { future.get(10, TimeUnit.SECONDS) }
    catch (failure: ExecutionException) { future.cancel(true); throw (failure.cause ?: failure) }
    catch (failure: Exception) { future.cancel(true); throw failure }

    private fun unwrap(failure: Exception): Throwable =
        if (failure is ExecutionException && failure.cause != null) unwrapCause(failure.cause!!) else failure
    private fun unwrapCause(failure: Throwable): Throwable =
        if (failure is ExecutionException && failure.cause != null) unwrapCause(failure.cause!!) else failure
    private class DecisionFailure(val original: Exception) : RuntimeException("Decision failed", original)
}

/** '~' is outside the username alphabet, so escaping reserved IDs cannot alias a valid raw key. */
internal fun usernameReservationPath(name: Username): String =
    "usernames/" + if (Regex("__.*__").matches(name.key)) "~${name.key}" else name.key
