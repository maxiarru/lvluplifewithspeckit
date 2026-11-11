package lvluplife.persistence

import com.google.cloud.firestore.Firestore
import java.time.Instant
import lvluplife.api.ApiError
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

/** Contract scaffold for T085. Implement the short commit protocol in T087. */
class RevisionTransaction(firestore: Firestore, operations: OperationStore, clock: AuthoritativeClock) {
    fun commit(uid: Uid, command: CommandEnvelope, expectedRevision: Revision?,
        conditions: List<DocumentCondition> = emptyList(),
        decide: (RevisionSnapshot, Instant) -> OperationDecision): OperationReceipt = error("Revision transaction is not implemented")
}
