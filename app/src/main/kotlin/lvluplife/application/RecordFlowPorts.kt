package lvluplife.application

/** Compile-only UI contracts for T041; adapters/controllers are supplied by T042–T043. */
data class RecordOption(val id: String, val name: String, val duration: Boolean)
data class RecordDraft(val activityId: String, val date: String, val hours: String, val minutes: String)
data class RecordAttributeView(val level: Int, val xp: Int)
data class RecordProgressView(val revision: Long, val cycleId: String, val businessDate: String,
    val generalLevel: Int, val classId: String, val stage: Int, val attributes: Map<String, RecordAttributeView>,
    val className: String? = null, val computedAt: java.time.Instant? = null)
interface RecordFlowRecords {
    val online: Boolean
    suspend fun classes(uid: String): List<ClassCatalogEntry> = throw OperationBlocked(lvluplife.data.ApiFailureCode.UNREADABLE_RESPONSE)
    suspend fun catalog(uid: String): List<RecordOption>
    suspend fun draft(uid: String): RecordDraft?
    suspend fun saveDraft(uid: String, draft: RecordDraft)
    suspend fun pending(uid: String): List<OperationSnapshot>
    suspend fun submit(uid: String, intentId: String, draft: RecordDraft): OperationSnapshot
    suspend fun retry(ref: OperationRef): OperationSnapshot
}
fun interface RecordFlowProgress { suspend fun load(uid: String): RecordProgressView }
