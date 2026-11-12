package lvluplife.persistence

import com.google.cloud.firestore.Firestore
import java.time.Instant
import java.time.LocalDate
import lvluplife.application.RecordOperations
import lvluplife.application.ActivityResolver
import lvluplife.model.*

/** UID-scoped definition resolver and pure record/projection codecs; mutations use RevisionTransaction. */
class RecordStore(firestore: Firestore) {
    private val documents = FirestoreAccess(firestore)
    fun readRecord(uid: Uid, id: RecordId): ActivityRecord? =
        documents.read(uid, listOf("records", id.value))?.let { record(id.value, it) }
    private val activities = ActivityResolver(CustomActivityStore(firestore))
    fun definition(uid: Uid, id: ActivityId): ActivityDefinition? = activities.resolve(uid, id)
    fun cycle(uid: Uid, snapshot: RecordSnapshot): Cycle = decode {
        val fields = requireNotNull(snapshot.cycle)
        Cycle(uid, snapshot.cycleId, Instant.parse(fields["startedAt"] as String), RulesVersion(Math.toIntExact(fields["rulesVersion"] as Long)),
            (fields["closedAt"] as String?)?.let(Instant::parse))
    }
    fun record(id: String, fields: Map<String, Any?>): ActivityRecord = decode {
        val definition = fields["activityDefinition"] as Map<*, *>
        val activity = ActivityDefinition(ActivityId(definition["id"] as String), definition["name"] as String,
            definition["category"] as String, ActivityMode.valueOf(definition["mode"] as String),
            Attribute.valueOf(definition["primary"] as String), (definition["secondary"] as String?)?.let(Attribute::valueOf),
            ExperienceRule.valueOf(definition["experienceRule"] as String))
        require(fields["activityId"] == activity.id.value) { "Record definition mismatch" }
        ActivityRecord.restore(RecordId(id), CycleId(fields["cycleId"] as String), activity,
            LocalDate.parse(fields["activityDate"] as String), (fields["durationMinutes"] as Long?)?.let(Math::toIntExact),
            Instant.parse(fields["createdAt"] as String), Instant.parse(fields["updatedAt"] as String), fields["version"] as Long,
            (fields["deletedAt"] as String?)?.let(Instant::parse))
    }
    fun fields(record: ActivityRecord): Map<String, Any?> {
        val definition = record.definition
        val fields = linkedMapOf<String, Any?>("cycleId" to record.cycleId.value, "activityId" to definition.id.value,
            "activityDate" to record.activityDate.toString(), "createdAt" to record.createdAt.toString(),
            "updatedAt" to record.updatedAt.toString(), "version" to record.version,
            "activityDefinition" to mapOf("id" to definition.id.value, "name" to definition.name, "category" to definition.category,
                "mode" to definition.mode.name, "primary" to definition.primary.name, "secondary" to definition.secondary?.name,
                "experienceRule" to definition.experienceRule.name))
        record.durationMinutes?.let { fields["durationMinutes"] = it.toLong() }
        record.deletedAt?.let { fields["deletedAt"] = it.toString() }
        return fields
    }
    fun projectionFields(progression: Progression): Map<String, Any?> =
        (RecordOperations.progressionResult(progression) - "businessDate") + ("asOfDate" to progression.businessDate.toString())

    private fun <T> decode(block: () -> T): T = try { block() } catch (_: Exception) { throw PersistenceUnavailable() }
}
