package lvluplife.application

import lvluplife.api.CommandEnvelope
import lvluplife.api.CommandType
import lvluplife.api.ApiException
import lvluplife.api.ApiError
import lvluplife.model.Account
import lvluplife.model.Username
import lvluplife.model.Revision
import lvluplife.model.CycleId
import lvluplife.model.Uid
import lvluplife.persistence.*
import java.util.UUID

class CompleteProfile(private val accounts: AccountStore, private val operations: OperationStore, private val transactions: RevisionTransaction,
    private val newCycleId: () -> CycleId = { CycleId(UUID.randomUUID().toString()) }) {
    /** Blocking IO. Existing receipts are recognized before account/username revalidation. */
    fun execute(uid: Uid, command: CommandEnvelope): OperationReceipt {
        if (command.type != CommandType.COMPLETE_PROFILE || command.cycleId != null) throw ApiException(ApiError.INVALID_REQUEST)
        operations.findMatching(uid, command)?.let { return it }
        val raw = command.payload.get("username")?.takeIf { it.isTextual }?.textValue() ?: throw ApiException(ApiError.INVALID_REQUEST)
        val requested = try { Username(raw) } catch (_: IllegalArgumentException) { null }
        val cycleId = newCycleId()
        repeat(4) {
            val expected = accounts.revision(accounts.root(uid))
            try {
                return transactions.commit(uid, command, expected, usernames = listOfNotNull(requested)) { snapshot, now ->
                    val next = (snapshot.revision ?: Revision(0)).next()
                    val existing = accounts.decode(uid, snapshot.root)
                    when {
                        existing != null -> accounts.existingPlan(existing, next)
                        requested == null -> OperationDecision.Rejected(ApiError.INVALID_INPUT)
                        snapshot.usernameOwners[requested.key] != null -> {
                            if (snapshot.usernameOwners[requested.key] == uid) throw PersistenceUnavailable()
                            OperationDecision.Rejected(ApiError.USERNAME_UNAVAILABLE)
                        }
                        else -> accounts.initialPlan(Account.start(uid, requested.value, cycleId, now, revision = next))
                    }
                }
            } catch (_: RevisionChanged) { /* Re-read the latest account; never replay a stale creation plan. */ }
            catch (_: PreconditionChanged) { /* Reconstruct after a changed precondition. */ }
        }
        throw PersistenceUnavailable()
    }
}
