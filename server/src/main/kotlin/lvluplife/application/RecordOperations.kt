package lvluplife.application

import lvluplife.api.CommandEnvelope
import lvluplife.api.CommandType
import lvluplife.model.*
import lvluplife.persistence.*
import lvluplife.progression.ClassCatalog
import lvluplife.progression.ClassRules

/** Record receipt integration; hashing, persistence and atomic revision handling stay in the shared protocol. */
class RecordOperations(private val operations: OperationStore) {
    fun recover(uid: Uid, command: CommandEnvelope): OperationReceipt? {
        require(supports(command.type)) { "Expected a record command" }
        // No account, cycle, record, version or clock read before looking up the original definitive receipt.
        return operations.findMatching(uid, command)
    }

    fun confirmation(uid: Uid, command: CommandEnvelope, record: ActivityRecord, progression: Progression,
        writes: List<DocumentWrite>): OperationDecision.Confirmed {
        require(supports(command.type)) { "Expected a record command" }
        require(progression.uid == uid && command.cycleId == record.cycleId.value &&
            record.cycleId == progression.cycleId) { "Record result owner/cycle mismatch" }
        if (command.type != CommandType.CREATE_RECORD)
            require(command.payload.path("recordId").textValue() == record.id.value) { "Record result identity mismatch" }
        require((command.type == CommandType.DELETE_RECORD) == (record.deletedAt != null)) { "Record result deletion mismatch" }
        require(!record.updatedAt.isAfter(progression.computedAt)) { "Result precedes record update" }
        val className = requireNotNull(ClassCatalog.find(progression.classId)) { "Unknown progression class" }.name
        val snapshot = mapOf("cycleId" to progression.cycleId.value, "revision" to progression.revision.value,
            "businessDate" to progression.businessDate.toString(), "computedAt" to progression.computedAt.toString(),
            "generalLevel" to progression.general.level.toLong(), "generalRemainder" to progression.general.remaining.toLong(),
            "classId" to progression.classId, "stage" to progression.stage.toLong(),
            "unlockedStages" to ClassRules.unlockedStages(className, progression.general.level).map(Int::toLong),
            "attributes" to progression.attributes.entries.associate { (attribute, state) -> attribute.name to
                mapOf("level" to state.attribute.level.toLong(), "xp" to state.attribute.xp.toLong(), "inactiveDates" to state.inactiveDates.toLong()) })
        val result = mapOf("recordId" to record.id.value, "version" to record.version,
            "createdAt" to record.createdAt.toString(), "cycleId" to progression.cycleId.value,
            "revision" to progression.revision.value, "progression" to snapshot)
        return OperationDecision.Confirmed(emptyMap(), writes, result)
    }

    companion object {
        fun supports(type: CommandType) = type in setOf(CommandType.CREATE_RECORD, CommandType.EDIT_RECORD, CommandType.DELETE_RECORD)
    }
}
