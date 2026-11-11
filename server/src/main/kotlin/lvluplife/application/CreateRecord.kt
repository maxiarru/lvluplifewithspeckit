package lvluplife.application

import java.time.LocalDate
import java.util.UUID
import lvluplife.api.*
import lvluplife.model.*
import lvluplife.persistence.*
import lvluplife.progression.*

/** Blocking application admission for schema-validated commands. No client XP or creation time is used. */
class CreateRecord(private val store: RecordStore, private val operations: RecordOperations,
    private val transactions: RevisionTransaction, private val newId: () -> RecordId = { RecordId(UUID.randomUUID().toString()) }) {
    fun execute(uid: Uid, command: CommandEnvelope): OperationReceipt {
        require(command.type == CommandType.CREATE_RECORD) { "Expected CREATE_RECORD" }
        operations.recover(uid, command)?.let { return it }
        val activityId = try { ActivityId(command.payload.path("activityId").textValue()) } catch (_: IllegalArgumentException) { null }
        val definition = activityId?.let { store.definition(uid, it) }
        val id = newId()
        return transactions.commitRecords(uid, command, listOf(DocumentCondition(listOf("records", id.value), null))) { snapshot, now ->
            val date = LocalDate.parse(command.payload.path("activityDate").textValue())
            val allowedDate = try { RecordValidation.activityDate(date, now); true } catch (_: IllegalArgumentException) { false }
            if (!allowedDate) return@commitRecords OperationDecision.Rejected(ApiError.DATE_NOT_ALLOWED)
            if (definition == null) return@commitRecords OperationDecision.Rejected(ApiError.INVALID_INPUT)
            val cycle = store.cycle(uid, snapshot)
            val records = snapshot.records.map { (key, fields) -> store.record(key, fields) }
            val minutes = when (definition.mode) {
                ActivityMode.OCCURRENCE -> {
                    if (command.payload.has("hours") || command.payload.has("minutes"))
                        return@commitRecords OperationDecision.Rejected(ApiError.INVALID_INPUT)
                    null
                }
                ActivityMode.DURATION -> {
                    if (!command.payload.has("hours") || !command.payload.has("minutes"))
                        return@commitRecords OperationDecision.Rejected(ApiError.INVALID_INPUT)
                    val hours = command.payload.get("hours").decimalValue()
                    val minuteComponent = command.payload.get("minutes").decimalValue()
                    try { RecordValidation.durationMinutes(hours, minuteComponent, snapshot.cycleId, date, emptyList()) }
                    catch (_: IllegalArgumentException) { return@commitRecords OperationDecision.Rejected(ApiError.INVALID_INPUT) }
                    try { RecordValidation.durationMinutes(hours, minuteComponent, snapshot.cycleId, date, records) }
                    catch (_: IllegalArgumentException) { return@commitRecords OperationDecision.Rejected(ApiError.DAILY_DURATION_EXCEEDED) }
                }
            }
            val record = ActivityRecord.create(id, snapshot.cycleId, definition, date, minutes, now)
            val replay = ReplayEngine.rebuild(cycle, records + record, now)
            val progression = Progression.derived(cycle, snapshot.revision.next(), now, replay.attributes,
                replay.general, replay.appearance.classId, replay.appearance.stage)
            operations.confirmation(uid, command, record, progression, listOf(
                DocumentWrite(listOf("records", id.value), store.fields(record)),
                DocumentWrite(listOf("projections", "current"), store.projectionFields(progression))))
        }
    }
}
