package lvluplife.data

import android.content.Context
import java.io.Closeable

enum class PendingState { PREPARED, SENDING, UNKNOWN, RESOLVING, CONFIRMED, REJECTED }
data class PendingOperation(
    val uid: String,
    val operationId: String,
    val commandType: String,
    val envelope: String,
    val requestHash: String,
    val cycleId: String?,
    val state: PendingState,
    val safeError: String? = null,
)
data class Draft(val uid: String, val content: String)

interface PendingStore : Closeable {
    fun prepare(operation: PendingOperation)
    fun pendingFor(uid: String): List<PendingOperation>
    fun updateState(uid: String, operationId: String, state: PendingState)
    fun resolveAbsent(uid: String, operationId: String)
    fun removeDefinitive(uid: String, operationId: String)
    fun saveDraft(draft: Draft)
    fun draftFor(uid: String): Draft?
}

object PendingDatabase {
    // Executable contract stub: T008 must fail before Room persistence is added in T013.
    fun open(context: Context, name: String = "pending.db"): PendingStore =
        error("Pending persistence is not implemented")
}
