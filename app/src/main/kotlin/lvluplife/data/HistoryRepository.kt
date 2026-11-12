package lvluplife.data

import com.fasterxml.jackson.databind.JsonNode
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lvluplife.application.*

/** No private history cache: only complete authenticated query snapshots reach the session-owned UI. */
class HistoryRepository(private val api: ApiClient, private val sessions: SessionTokenProvider,
    private val connected: () -> Boolean, private val clock: Clock = Clock.systemUTC()) : HistoryFlowHistory {
    override val online get() = connected()
    override suspend fun load(uid: String, selection: HistorySelection): HistoryPage = withContext(Dispatchers.IO) {
        val lease = sessions.current()
        if (lease?.uid != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
        val date = try { LocalDate.parse(selection.date) } catch (_: Exception) { throw OperationBlocked(ApiFailureCode.INVALID_INPUT) }
        if (!online) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        fun owner() { if (sessions.current() !== lease) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED) }
        fun query(value: ApiQuery): JsonNode {
            owner()
            val body = when (val response = api.query(uid, value)) {
                is ApiReply.Data -> response.body
                is ApiReply.Failure -> throw OperationBlocked(response.code)
                else -> throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
            }
            owner(); return body
        }
        val body = query(ApiQuery.History(selection.period, date))
        // Read-only profile metadata supplies the current cycle for an advisory correction hint.
        val me = query(ApiQuery.Me)
        try {
            fun integer(node: JsonNode): Long { require(node.isIntegralNumber && node.canConvertToLong()); return node.longValue() }
            fun id(node: JsonNode): String = requireNotNull(node.textValue()).also {
                require(it.isNotBlank() && it !in setOf(".", "..") && '/' !in it && it.none(Char::isISOControl))
            }
            val revision = integer(body.path("revision")); require(revision > 0)
            require(body.path("complete").isBoolean && body.path("complete").booleanValue() && body.path("items").isArray)
            val start = LocalDate.parse(body.path("periodStart").textValue()); val end = LocalDate.parse(body.path("periodEndExclusive").textValue())
            val expectedStart = when (selection.period) {
                HistoryPeriod.day -> date
                HistoryPeriod.week -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                HistoryPeriod.month -> date.withDayOfMonth(1)
            }
            val expectedEnd = when (selection.period) {
                HistoryPeriod.day -> expectedStart.plusDays(1)
                HistoryPeriod.week -> expectedStart.plusDays(7)
                HistoryPeriod.month -> expectedStart.plusMonths(1)
            }
            require(start == expectedStart && end == expectedEnd)
            require(me.path("profileRequired").isBoolean && !me.path("profileRequired").booleanValue())
            require(me.path("profile").path("uid").textValue() == uid)
            val currentCycle = id(me.path("profile").path("currentCycleId"))
            // Server time prevents a slightly skewed device clock from hiding a just-created record's actions.
            // Old API responses retain the previous advisory fallback; the server always validates the mutation.
            val observedAt = if(body.has("observedAt")) Instant.parse(requireNotNull(body.path("observedAt").textValue())) else clock.instant()
            val items = body.path("items").map { row ->
                val recordId = id(row.path("recordId")); val cycle = id(row.path("cycleId")); val activity = id(row.path("activityId"))
                val definition = row.path("activityDefinition"); require(definition.isObject && id(definition.path("id")) == activity)
                val name = requireNotNull(definition.path("name").textValue()); require(name.isNotBlank() && name == name.trim() && name.codePointCount(0,name.length) <= 50)
                val activityDate = LocalDate.parse(row.path("activityDate").textValue()); require(activityDate >= start && activityDate < end)
                val created = Instant.parse(row.path("createdAt").textValue()); val updated = Instant.parse(row.path("updatedAt").textValue())
                require(!updated.isBefore(created) && (!row.has("deletedAt") || row.path("deletedAt").isNull))
                val version = integer(row.path("version")); require(version > 0)
                val duration = when (definition.path("mode").textValue()) {
                    "DURATION" -> Math.toIntExact(integer(row.path("durationMinutes"))).also { require(it in 1..1440 && activity != "meal") }
                    "OCCURRENCE" -> { require(activity == "meal" && (!row.has("durationMinutes") || row.path("durationMinutes").isNull)); null }
                    else -> throw IllegalArgumentException("Invalid record mode")
                }
                val age = Duration.between(created, observedAt)
                HistoryItem(recordId,cycle,activity,name,activityDate.toString(),duration,created,version,
                    cycle == currentCycle && !age.isNegative && age < Duration.ofSeconds(300))
            }
            require(items.map { it.id }.toSet().size == items.size)
            require(items == items.sortedWith(compareBy<HistoryItem> { it.date }.thenBy { it.createdAt }.thenBy { it.id }))
            owner(); HistoryPage(revision,start.toString(),end.toString(),Collections.unmodifiableList(items))
        } catch (blocked: OperationBlocked) { throw blocked }
        catch (_: Exception) { throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE) }
    }
}
