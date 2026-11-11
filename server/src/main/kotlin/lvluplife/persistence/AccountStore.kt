package lvluplife.persistence

import com.google.cloud.firestore.Firestore
import lvluplife.model.Account
import lvluplife.model.AccountStart
import lvluplife.model.Username
import lvluplife.model.CycleId
import lvluplife.model.Revision
import lvluplife.model.Uid
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** UID-scoped reads and pure account codecs/plans. All mutations go through RevisionTransaction. */
class AccountStore(firestore: Firestore) {
    private val documents = FirestoreAccess(firestore)
    fun root(uid: Uid): Map<String, Any?>? = documents.read(uid, emptyList())
    fun read(uid: Uid): Account? = decode(uid, root(uid))
    fun revision(root: Map<String, Any?>?): Revision? = safeDecode { root?.let { Revision(it["revision"] as Long) } }

    fun decode(uid: Uid, root: Map<String, Any?>?): Account? = safeDecode {
        if (root == null) return@safeDecode null
        val revision = requireNotNull(revision(root))
        if (!root.containsKey("username")) {
            require(root.keys == setOf("revision") && revision.value > 0) { "Incomplete account root" }
            return@safeDecode null
        }
        val username = Username(root["username"] as String)
        require(root["usernameKey"] == username.key && root["schemaVersion"] == 1L) { "Invalid account metadata" }
        Account(uid, username, Instant.parse(root["createdAt"] as String), CycleId(root["currentCycleId"] as String), revision)
    }

    fun initialPlan(start: AccountStart): OperationDecision.Confirmed {
        val account = start.account; val cycle = start.cycle; val character = start.character
        val root = mapOf("username" to account.username.value, "usernameKey" to account.username.key,
            "createdAt" to account.createdAt.toString(), "currentCycleId" to cycle.id.value, "schemaVersion" to account.schemaVersion.toLong())
        val cycleFields = mapOf("startedAt" to cycle.startedAt.toString(), "rulesVersion" to cycle.rulesVersion.value.toLong())
        val projection = mapOf("cycleId" to cycle.id.value, "revision" to account.revision.value,
            "computedAt" to account.createdAt.toString(),
            "asOfDate" to account.createdAt.atOffset(ZoneOffset.ofHours(-3)).toLocalDate().toString(),
            "classId" to character.className.lowercase(Locale.ROOT), "stage" to character.stage.toLong(),
            "generalLevel" to character.generalLevel.toLong(), "generalRemainder" to character.generalRemainder.toLong(),
            "attributes" to character.attributes.entries.associate { (id, value) -> id.name to mapOf("level" to value.level.toLong(), "xp" to value.xp.toLong()) })
        return OperationDecision.Confirmed(root, listOf(DocumentWrite(listOf("cycles", cycle.id.value), cycleFields),
            DocumentWrite(listOf("projections", "current"), projection)), result(account, account.revision), listOf(account.username))
    }

    fun existingPlan(account: Account, nextRevision: Revision) =
        OperationDecision.Confirmed(emptyMap(), emptyList(), result(account, nextRevision))

    private fun result(account: Account, revision: Revision): Map<String, Any?> =
        mapOf("username" to account.username.value, "cycleId" to account.currentCycleId.value, "revision" to revision.value)

    private fun <T> safeDecode(block: () -> T): T = try { block() }
    catch (_: Exception) { throw PersistenceUnavailable() }
}
