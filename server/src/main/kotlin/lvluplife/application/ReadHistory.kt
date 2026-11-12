package lvluplife.application

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Collections
import lvluplife.api.HistoryPeriod
import lvluplife.model.*
import lvluplife.persistence.HistoryStore

// Activity dates are already normalized to the fixed business offset; never reinterpret them in the host timezone.
data class HistoryResult(val periodStart: LocalDate, val periodEndExclusive: LocalDate, val revision: Revision,
    val items: List<ActivityRecord>)

class ReadHistory(private val store: HistoryStore) {
    fun execute(uid: Uid, period: HistoryPeriod, date: LocalDate): HistoryResult {
        val start = when (period) {
            HistoryPeriod.day -> date
            HistoryPeriod.week -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            HistoryPeriod.month -> date.withDayOfMonth(1)
        }
        val end = when (period) {
            HistoryPeriod.day -> start.plusDays(1)
            HistoryPeriod.week -> start.plusDays(7)
            HistoryPeriod.month -> start.plusMonths(1)
        }
        val snapshot = store.read(uid, start, end)
        val ordered = snapshot.records.filter { it.deletedAt == null }.sortedWith(
            compareBy<ActivityRecord> { it.activityDate }.thenBy { it.createdAt }.thenBy { it.id.value })
        return HistoryResult(start, end, snapshot.revision, Collections.unmodifiableList(ordered))
    }
}
