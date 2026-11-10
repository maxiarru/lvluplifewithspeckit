package lvluplife.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class PendingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract fun insertPending(operation: PendingOperation): Long

    @Query("SELECT * FROM pending_operations WHERE uid = :uid AND operationId = :operationId")
    protected abstract fun find(uid: String, operationId: String): PendingOperation?

    @Query("SELECT * FROM pending_operations WHERE uid = :uid ORDER BY createdAtEpochMillis, operationId")
    abstract fun pendingFor(uid: String): List<PendingOperation>

    @Query("UPDATE pending_operations SET state = :state WHERE uid = :uid AND operationId = :operationId")
    protected abstract fun writeState(uid: String, operationId: String, state: PendingState)

    @Query("DELETE FROM pending_operations WHERE uid = :uid AND operationId = :operationId AND state IN ('CONFIRMED', 'REJECTED')")
    protected abstract fun deleteDefinitive(uid: String, operationId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveDraft(draft: Draft)

    @Query("SELECT * FROM drafts WHERE uid = :uid")
    abstract fun draftFor(uid: String): Draft?

    @Transaction
    open fun prepare(operation: PendingOperation) {
        if (insertPending(operation) != -1L) return
        val original = checkNotNull(find(operation.uid, operation.operationId))
        require(original.commandType == operation.commandType && original.envelope == operation.envelope &&
            original.requestHash == operation.requestHash && original.cycleId == operation.cycleId) { "Operation content is immutable" }
        // Preserve persisted state/error/time on retries; never overwrite the original envelope.
    }

    @Transaction
    open fun updateState(uid: String, operationId: String, state: PendingState) {
        val original = checkNotNull(find(uid, operationId)) { "Pending operation not found" }
        check(!original.state.isDefinitive || state == original.state) { "Definitive outcome is immutable" }
        writeState(uid, operationId, state)
    }

    @Transaction
    open fun resolveAbsent(uid: String, operationId: String) {
        val original = find(uid, operationId) ?: return
        if (original.state in setOf(PendingState.SENDING, PendingState.UNKNOWN, PendingState.RESOLVING)) {
            writeState(uid, operationId, PendingState.UNKNOWN)
        }
    }

    @Transaction
    open fun removeDefinitive(uid: String, operationId: String) {
        val original = find(uid, operationId) ?: return
        check(original.state.isDefinitive) { "Unknown outcome cannot be removed" }
        check(deleteDefinitive(uid, operationId) == 1)
    }
}

private val PendingState.isDefinitive get() = this == PendingState.CONFIRMED || this == PendingState.REJECTED
