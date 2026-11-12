package lvluplife.application

import java.time.LocalDate
import lvluplife.api.*
import lvluplife.model.*
import lvluplife.persistence.*
import lvluplife.progression.ReplayEngine
import lvluplife.record.CorrectionRules
import lvluplife.record.CorrectionRejected

/** Version admission, mutation, replay and immutable receipt share the common account revision barrier. */
class CorrectRecord(private val store: RecordStore, private val operations: RecordOperations, private val transactions: RevisionTransaction) {
    fun execute(uid: Uid, command: CommandEnvelope): OperationReceipt {
        require(command.type in setOf(CommandType.EDIT_RECORD, CommandType.DELETE_RECORD)) { "Expected a correction command" }
        operations.recover(uid, command)?.let { return it }
        val id = try { RecordId(command.payload.path("recordId").textValue()) } catch (_: IllegalArgumentException) { null }
        // Only immutable cycle identity is consulted from this read. Current values/version come from each fresh snapshot.
        val originalCycle = id?.let { store.readRecord(uid, it)?.cycleId }
        val requestedActivity = command.payload.has("activityId")
        val definition = if (command.type == CommandType.EDIT_RECORD && requestedActivity) {
            val activityId = try { ActivityId(command.payload.path("activityId").textValue()) } catch (_: IllegalArgumentException) { null }
            activityId?.let { store.definition(uid, it) }
        } else null
        return transactions.commitRecords(uid, command) { snapshot, now ->
            if (id == null) return@commitRecords OperationDecision.Rejected(ApiError.INVALID_INPUT)
            val fields = snapshot.records[id.value] ?: return@commitRecords OperationDecision.Rejected(
                if (originalCycle != null && originalCycle != snapshot.cycleId) ApiError.CYCLE_CLOSED else ApiError.RECORD_DELETED)
            val record = store.record(id.value, fields)
            if (record.deletedAt != null) return@commitRecords OperationDecision.Rejected(ApiError.RECORD_DELETED)
            val expectedVersion = command.payload.path("expectedVersion").longValue()
            if (expectedVersion <= 0) return@commitRecords OperationDecision.Rejected(ApiError.INVALID_INPUT)
            if (record.version != expectedVersion) return@commitRecords OperationDecision.Rejected(ApiError.RECORD_STALE)
            val cycle = store.cycle(uid, snapshot)
            val records = snapshot.records.map { (key, value) -> store.record(key, value) }
            val corrected = try {
                when (command.type) {
                    CommandType.DELETE_RECORD -> CorrectionRules.delete(record, cycle, now)
                    else -> {
                        if (requestedActivity && definition == null) return@commitRecords OperationDecision.Rejected(ApiError.INVALID_INPUT)
                        CorrectionRules.edit(record, cycle, LocalDate.parse(command.payload.path("activityDate").textValue()),
                            definition ?: record.definition, command.payload.get("hours")?.decimalValue(),
                            command.payload.get("minutes")?.decimalValue(), records, now)
                    }
                }
            } catch (rejected: CorrectionRejected) {
                return@commitRecords OperationDecision.Rejected(ApiError.valueOf(rejected.reason.name))
            }
            val replay = ReplayEngine.rebuild(cycle, records.map { if (it.id == id) corrected else it }, now)
            val progression = Progression.derived(cycle, snapshot.revision.next(), now, replay.attributes,
                replay.general, replay.appearance.classId, replay.appearance.stage)
            operations.confirmation(uid, command, corrected, progression, listOf(
                DocumentWrite(listOf("records", id.value), store.fields(corrected)),
                DocumentWrite(listOf("projections", "current"), store.projectionFields(progression))))
        }
    }
}
