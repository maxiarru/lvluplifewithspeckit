package lvluplife.persistence

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Transaction
import java.time.Instant
import lvluplife.api.ApiError
import lvluplife.api.CommandEnvelope
import lvluplife.api.CommandType
import lvluplife.model.OperationId
import lvluplife.model.Revision
import lvluplife.model.Uid

enum class ReceiptOutcome { CONFIRMED, REJECTED }
data class OperationReceipt(
    val operationId: OperationId,
    val commandType: CommandType,
    val requestHash: String,
    val outcome: ReceiptOutcome,
    val validatedAt: Instant,
    val revision: Revision,
    val cycleId: String?,
    val result: Map<String, Any?>? = null,
    val error: ApiError? = null,
)

/** Contract scaffold for T085. Implement in T086; application writes use RevisionTransaction. */
class OperationStore(firestore: Firestore) {
    fun canonicalHash(command: CommandEnvelope): String = error("Operation store is not implemented")
    fun find(uid: Uid, operationId: OperationId): OperationReceipt? = error("Operation store is not implemented")
    fun read(transaction: Transaction, uid: Uid, command: CommandEnvelope): OperationReceipt? = error("Operation store is not implemented")
    fun create(transaction: Transaction, uid: Uid, receipt: OperationReceipt): Unit = error("Operation store is not implemented")
}
