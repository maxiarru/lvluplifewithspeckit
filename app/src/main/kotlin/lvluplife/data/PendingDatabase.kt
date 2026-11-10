package lvluplife.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.io.Closeable
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

enum class PendingState { PREPARED, SENDING, UNKNOWN, RESOLVING, CONFIRMED, REJECTED }
@Entity(tableName = "pending_operations", primaryKeys = ["uid", "operationId"])
data class PendingOperation(
    val uid: String,
    val operationId: String,
    val commandType: String,
    val envelope: String,
    val requestHash: String,
    val cycleId: String?,
    val state: PendingState,
    val safeError: String? = null,
    // Diagnostic only; the caller supplies local time, never used for server validation.
    val createdAtEpochMillis: Long = 0,
)
@Entity(tableName = "drafts", primaryKeys = ["uid"])
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
    fun open(context: Context, name: String = "pending.db"): PendingStore {
        require(Regex("[A-Za-z0-9._-]+").matches(name) && name != "." && name != "..") { "Invalid database name" }
        val database = Room.databaseBuilder(context.applicationContext, PendingRoomDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // No destructive migrations or main-thread queries: unresolved operations must survive.
            .build()
        return RoomPendingStore(database)
    }
}

@Database(entities = [PendingOperation::class, Draft::class], version = 1, exportSchema = true)
@TypeConverters(PendingStateConverter::class)
abstract class PendingRoomDatabase : RoomDatabase() {
    abstract fun pendingDao(): PendingDao
}

class PendingStateConverter {
    @TypeConverter fun decode(value: String): PendingState = PendingState.valueOf(value)
    @TypeConverter fun encode(value: PendingState): String = value.name
}

private class RoomPendingStore(private val database: PendingRoomDatabase) : PendingStore {
    private val dao get() = database.pendingDao()

    override fun prepare(operation: PendingOperation) {
        PendingContent.validate(operation)
        dao.prepare(operation)
    }
    override fun pendingFor(uid: String): List<PendingOperation> {
        PendingContent.validateUid(uid)
        return dao.pendingFor(uid)
    }
    override fun updateState(uid: String, operationId: String, state: PendingState) = dao.updateState(uid, operationId, state)
    override fun resolveAbsent(uid: String, operationId: String) = dao.resolveAbsent(uid, operationId)
    override fun removeDefinitive(uid: String, operationId: String) = dao.removeDefinitive(uid, operationId)
    override fun saveDraft(draft: Draft) {
        PendingContent.validateUid(draft.uid)
        PendingContent.rejectCredentials(draft.content, requireObject = false)
        dao.saveDraft(draft)
    }
    override fun draftFor(uid: String): Draft? {
        PendingContent.validateUid(uid)
        return dao.draftFor(uid)
    }
    override fun close() = database.close()
}

private object PendingContent {
    private val commandTypes = setOf("COMPLETE_PROFILE", "CREATE_CUSTOM_ACTIVITY", "CREATE_RECORD", "EDIT_RECORD", "DELETE_RECORD", "RESET_PROGRESSION")
    private val errorCodes = setOf("INVALID_REQUEST", "AUTH_REQUIRED", "FORBIDDEN", "USERNAME_UNAVAILABLE", "OPERATION_ID_REUSED",
        "RECORD_STALE", "RECORD_DELETED", "CYCLE_CLOSED", "INVALID_INPUT", "DATE_NOT_ALLOWED", "EDIT_WINDOW_EXPIRED",
        "DAILY_DURATION_EXCEEDED", "TEMPORARILY_UNAVAILABLE", "REQUEST_TOO_LARGE", "NETWORK_ERROR", "TIMEOUT", "UNREADABLE_RESPONSE", "UNKNOWN_ERROR")
    private val credentials = setOf("authorization", "token", "idtoken", "accesstoken", "refreshtoken", "bearer",
        "password", "credentials", "privatekey", "clientsecret")

    fun validateUid(uid: String) {
        require(uid.isNotBlank() && uid.length <= 128 && '/' !in uid && uid.none(Char::isISOControl)) { "Invalid pending owner" }
    }

    fun validate(operation: PendingOperation) {
        validateUid(operation.uid)
        val id = try { java.util.UUID.fromString(operation.operationId).toString() }
        catch (_: IllegalArgumentException) { throw IllegalArgumentException("Invalid operation identity") }
        require(id == operation.operationId) { "Operation identity must be canonical" }
        require(operation.commandType in commandTypes && operation.requestHash.isNotBlank()) { "Invalid pending command" }
        require(operation.createdAtEpochMillis >= 0 && (operation.safeError == null || operation.safeError in errorCodes)) { "Invalid pending diagnostics" }
        rejectCredentials(operation.envelope, requireObject = true)
    }

    fun rejectCredentials(content: String, requireObject: Boolean) {
        val root = try { JSONTokener(content).nextValue() } catch (_: org.json.JSONException) {
            if (requireObject) throw IllegalArgumentException("Invalid pending envelope") else return
        }
        require(!requireObject || root is JSONObject) { "Invalid pending envelope" }
        val stack = java.util.ArrayDeque<Any>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            when (val value = stack.removeLast()) {
                is JSONObject -> value.keys().forEach { key ->
                    require(key.lowercase(java.util.Locale.ROOT).replace("_", "").replace("-", "") !in credentials) { "Credentials cannot be persisted" }
                    value.opt(key)?.let { stack.add(it) }
                }
                is JSONArray -> for (index in 0 until value.length()) value.opt(index)?.let { stack.add(it) }
            }
        }
    }
}
