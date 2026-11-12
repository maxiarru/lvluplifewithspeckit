package lvluplife.application

import java.util.UUID
import lvluplife.api.*
import lvluplife.model.*
import lvluplife.persistence.*
import lvluplife.progression.ResetRules

/** Fixed-size atomic reset. Historical records/activities and username reservations are never written. */
class ResetProgression(private val accounts: AccountStore, private val records: RecordStore,
    private val operations: OperationStore, private val transactions: RevisionTransaction,
    private val newCycleId: () -> CycleId = { CycleId(UUID.randomUUID().toString()) }) {
    fun execute(uid: Uid, command: CommandEnvelope): OperationReceipt {
        if(command.type!=CommandType.RESET_PROGRESSION || command.cycleId==null) throw ApiException(ApiError.INVALID_REQUEST)
        var candidate: CycleId? = null
        repeat(4) {
            // Receipt recovery wins even after another reset, time change or obsolete expected cycle.
            operations.findMatching(uid,command)?.let { return it }
            val confirmation=command.payload.path("confirmed")
            if(!confirmation.isBoolean || !confirmation.booleanValue()) throw ApiException(ApiError.INVALID_REQUEST)
            val target=candidate ?: newCycleId().also { candidate=it }
            val root=accounts.root(uid)
            val expected=accounts.revision(root)
            val observed=accounts.decode(uid,root) ?: throw PersistenceUnavailable()
            val oldPath=listOf("cycles",observed.currentCycleId.value)
            val newPath=listOf("cycles",target.value)
            try {
                return transactions.commit(uid,command,expected,conditions=listOf(
                    DocumentCondition(oldPath,emptyMap()),DocumentCondition(newPath,null))) { snapshot,now ->
                    val account=accounts.decode(uid,snapshot.root) ?: throw PersistenceUnavailable()
                    if(account.currentCycleId!=observed.currentCycleId) throw RevisionChanged()
                    if(command.cycleId!=account.currentCycleId.value) return@commit OperationDecision.Rejected(ApiError.CYCLE_CLOSED)
                    val oldFields=snapshot.documents[oldPath] ?: throw PersistenceUnavailable()
                    val current=records.cycle(uid,account.currentCycleId,oldFields)
                    if(!current.isOpen) return@commit OperationDecision.Rejected(ApiError.CYCLE_CLOSED)
                    val reset=try { ResetRules.reset(account,current,target,now,confirmed=true) }
                    catch(_: IllegalArgumentException) { throw PersistenceUnavailable() }
                    val progression=reset.progression
                    OperationDecision.Confirmed(mapOf("currentCycleId" to target.value),listOf(
                        DocumentWrite(oldPath,oldFields+mapOf("closedAt" to reset.closedCycle.closedAt!!.toString())),
                        DocumentWrite(newPath,mapOf("startedAt" to reset.newCycle.startedAt.toString(),
                            "rulesVersion" to reset.newCycle.rulesVersion.value.toLong())),
                        DocumentWrite(listOf("projections","current"),records.projectionFields(progression))),
                        mapOf("closedCycleId" to current.id.value,"cycleId" to target.value,"revision" to progression.revision.value,
                            "progression" to RecordOperations.progressionResult(progression)))
                }
            } catch(_: RevisionChanged) { /* Re-read the current account after a concurrent record/reset. */ }
            catch(_: PreconditionChanged) { /* Never overwrite an existing cycle or use a missing current cycle. */ }
        }
        throw PersistenceUnavailable()
    }
}
