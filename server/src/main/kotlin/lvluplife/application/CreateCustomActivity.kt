package lvluplife.application

import java.util.UUID
import lvluplife.api.*
import lvluplife.model.*
import lvluplife.persistence.*

/** Account-scoped creation, independent of the current progression cycle. */
class CreateCustomActivity(private val accounts: AccountStore, private val store: CustomActivityStore,
    private val operations: OperationStore, private val transactions: RevisionTransaction,
    private val newId: () -> ActivityId = { ActivityId(UUID.randomUUID().toString()) }) {
    fun execute(uid: Uid, command: CommandEnvelope): OperationReceipt {
        require(command.type == CommandType.CREATE_CUSTOM_ACTIVITY && command.cycleId == null) { "Expected CREATE_CUSTOM_ACTIVITY" }
        operations.findMatching(uid, command)?.let { return it }
        val id = newId()
        repeat(4) {
            val expected = accounts.revision(accounts.root(uid))
            try {
                return transactions.commit(uid, command, expected,
                    conditions = listOf(DocumentCondition(listOf("activities", id.value), null))) { snapshot, now ->
                    // Never manufacture a partial account when identity has not completed profile creation.
                    val account = accounts.decode(uid, snapshot.root) ?: throw PersistenceUnavailable()
                    val activity = try {
                        val payload = command.payload
                        CustomActivity.create(uid, id, payload.path("name").textValue(), payload.path("category").textValue(),
                            Attribute.valueOf(payload.path("primary").textValue()),
                            payload.get("secondary")?.let { Attribute.valueOf(it.textValue()) }, now)
                    } catch (_: IllegalArgumentException) {
                        return@commit OperationDecision.Rejected(ApiError.INVALID_INPUT)
                    }
                    store.creationPlan(activity, account.revision.next())
                }
            } catch (_: RevisionChanged) { /* Re-read the account after another mutation. */ }
            catch (_: PreconditionChanged) { /* Never replace an existing definition. */ }
        }
        throw PersistenceUnavailable()
    }
}
