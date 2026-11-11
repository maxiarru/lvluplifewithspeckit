package lvluplife.persistence

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.TransactionOptions
import com.google.api.core.ApiFuture
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import lvluplife.api.ApiError
import lvluplife.api.ApiException
import lvluplife.api.CommandEnvelope
import lvluplife.application.AuthoritativeClock
import lvluplife.model.Revision
import lvluplife.model.Uid

/** Paths are relative collection/document pairs beneath the verified UID. */
data class DocumentWrite(val path: List<String>, val fields: Map<String, Any?>)
/** Null expects absence; otherwise the specified fields must still equal their expected values. */
data class DocumentCondition(val path: List<String>, val expectedFields: Map<String, Any?>?)
data class RevisionSnapshot(val root: Map<String, Any?>?, val revision: Revision?,
    val documents: Map<List<String>, Map<String, Any?>?>)
sealed interface OperationDecision {
    data class Confirmed(val rootFields: Map<String, Any?>, val writes: List<DocumentWrite>, val result: Map<String, Any?>) : OperationDecision
    data class Rejected(val error: ApiError) : OperationDecision
}
class RevisionChanged : RuntimeException("Revision changed")
class PreconditionChanged : RuntimeException("Precondition changed")

/** Short commit only: callers rebuild their external snapshot on RevisionChanged/PreconditionChanged.
 * decide must be pure, may be invoked again by the SDK, and must not perform external IO.
 */
class RevisionTransaction(private val firestore: Firestore, private val operations: OperationStore, private val clock: AuthoritativeClock) {
    fun commit(uid: Uid, command: CommandEnvelope, expectedRevision: Revision?,
        conditions: List<DocumentCondition> = emptyList(),
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
                val next = (revision ?: Revision(0)).next()
                val validationNow = clock.now()
                val decision = try { decide(RevisionSnapshot(root, revision, documents), validationNow) }
                catch (failure: Exception) { throw DecisionFailure(failure) }
                val receipt = when (decision) {
                    is OperationDecision.Confirmed -> {
                        require("revision" !in decision.rootFields) { "Revision is controlled by the commit protocol" }
                        decision.writes.forEach { validatePath(it.path) }
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
