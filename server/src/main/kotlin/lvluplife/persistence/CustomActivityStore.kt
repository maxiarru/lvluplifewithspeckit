package lvluplife.persistence

import com.google.cloud.firestore.Firestore
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import lvluplife.model.ActivityId
import lvluplife.model.Attribute
import lvluplife.model.CustomActivity
import lvluplife.model.Revision
import lvluplife.model.Uid

data class CustomActivityList(val revision: Revision, val items: List<CustomActivity>)

/** UID-scoped reads and pure commit plans. All writes are performed by RevisionTransaction. */
class CustomActivityStore(private val firestore: Firestore) {
    private val documents = FirestoreAccess(firestore)
    private val accounts = AccountStore(firestore)

    fun find(uid: Uid, id: ActivityId): CustomActivity? =
        documents.read(uid, listOf("activities", id.value))?.let { decode(uid, id, it) }

    fun list(uid: Uid): CustomActivityList = safeRead {
        repeat(4) {
            val before = accounts.root(uid)
            val account = accounts.decode(uid, before) ?: throw PersistenceUnavailable()
            val query = firestore.collection("users/${uid.value}/activities").get()
            val definitions = try { query.get(10, TimeUnit.SECONDS).documents }
            catch (failure: Exception) { query.cancel(true); throw failure }
            if (before != accounts.root(uid)) return@repeat
            // Decode every document before publishing any item; corruption never becomes a partial success.
            val items = definitions.map { decode(uid, ActivityId(it.id), it.data) }
                .sortedWith(compareBy<CustomActivity> { it.createdAt }.thenBy { it.id.value })
            return@safeRead CustomActivityList(account.revision, items)
        }
        throw PersistenceUnavailable()
    }

    private fun decode(uid: Uid, id: ActivityId, fields: Map<String, Any?>): CustomActivity = safeRead {
        val name = fields["name"] as String
        require(name == name.trim()) { "Invalid stored activity name" }
        CustomActivity.create(uid, id, name, fields["category"] as String,
            Attribute.valueOf(fields["primary"] as String), (fields["secondary"] as String?)?.let(Attribute::valueOf),
            Instant.parse(fields["createdAt"] as String))
    }

    private fun <T> safeRead(block: () -> T): T = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw PersistenceUnavailable() }
    catch (_: Exception) { throw PersistenceUnavailable() }

    fun fields(activity: CustomActivity): Map<String, Any?> = mapOf(
        "name" to activity.definition.name,
        "category" to activity.definition.category,
        "primary" to activity.definition.primary.name,
        "secondary" to activity.definition.secondary?.name,
        "createdAt" to activity.createdAt.toString(),
    )

    fun creationPlan(activity: CustomActivity, revision: Revision) = OperationDecision.Confirmed(
        rootFields = emptyMap(),
        writes = listOf(DocumentWrite(listOf("activities", activity.id.value), fields(activity))),
        result = mapOf("activityId" to activity.id.value, "createdAt" to activity.createdAt.toString(), "revision" to revision.value),
    )
}
