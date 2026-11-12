package lvluplife.application

import java.time.Instant
import lvluplife.data.HistoryPeriod

/** Compile-only contracts for T055; query and correction adapters/UI are supplied by T061–T062. */
data class HistorySelection(val period: HistoryPeriod, val date: String)
/** canCorrect is an advisory display hint; the server revalidates every mutation. */
data class HistoryItem(val id: String, val cycleId: String, val activityId: String, val name: String,
    val date: String, val durationMinutes: Int?, val createdAt: Instant, val version: Long, val canCorrect: Boolean)
data class HistoryPage(val revision: Long, val periodStart: String, val periodEndExclusive: String, val items: List<HistoryItem>)
data class CorrectionDraft(val recordId: String, val cycleId: String, val expectedVersion: Long,
    val activityId: String, val date: String, val hours: String?, val minutes: String?)
fun interface HistoryFlowHistory { suspend fun load(uid: String, selection: HistorySelection): HistoryPage }
interface HistoryFlowCorrections {
    val online: Boolean
    suspend fun draft(uid: String): CorrectionDraft?
    suspend fun saveDraft(uid: String, draft: CorrectionDraft)
    suspend fun pending(uid: String): List<OperationSnapshot>
    suspend fun edit(uid: String, intentId: String, draft: CorrectionDraft): OperationSnapshot
    suspend fun delete(uid: String, intentId: String, item: HistoryItem): OperationSnapshot
    suspend fun retry(ref: OperationRef): OperationSnapshot
}
