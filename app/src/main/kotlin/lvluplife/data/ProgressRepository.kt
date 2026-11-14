package lvluplife.data

import lvluplife.application.*
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Collections

class ProgressRepository(private val api: ApiClient, private val sessions: SessionTokenProvider) : RecordFlowProgress {
    override suspend fun load(uid: String): RecordProgressView = withContext(Dispatchers.IO) {
        val lease = sessions.current()
        if (lease?.uid != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
        fun owner() { if (sessions.current() !== lease) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED) }
        fun query(query: ApiQuery): JsonNode {
            owner()
            val result = when (val reply = api.query(uid, query)) {
                is ApiReply.Data -> reply.body
                is ApiReply.Failure -> throw OperationBlocked(reply.code)
                else -> throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
            }
            owner(); return result
        }
        val body = query(ApiQuery.Progress)
        val catalog = query(ApiQuery.Catalog)
        try {
            fun integer(node: JsonNode): Long { require(node.isIntegralNumber && node.canConvertToLong()); return node.longValue() }
            val revision = integer(body.path("revision")); require(revision > 0)
            val cycle = body.path("cycleId").textValue(); require(!cycle.isNullOrBlank() && cycle !in setOf(".", "..") && '/' !in cycle && cycle.none(Char::isISOControl))
            val date = LocalDate.parse(body.path("businessDate").textValue())
            val computedAt = Instant.parse(body.path("computedAt").textValue())
            require(computedAt.atOffset(ZoneOffset.ofHours(-3)).toLocalDate() == date)
            val general = Math.toIntExact(integer(body.path("generalLevel"))); require(general in 1..50)
            require(integer(body.path("generalRemainder")) >= 0)
            val attributes = body.path("attributes"); val ids = listOf("STR", "DEX", "INT", "WIT", "MEN", "CON")
            require(attributes.isObject && attributes.fieldNames().asSequence().toSet() == ids.toSet())
            val balances = ids.associateWith { id ->
                val level = Math.toIntExact(integer(attributes.path(id).path("level")))
                val xp = Math.toIntExact(integer(attributes.path(id).path("xp")))
                require(level in 1..50 && xp >= 0 && (level != 50 || xp == 0))
                // Coherence only: the requirement itself comes from the server's canonical thresholds.
                val requiredNode = attributes.path(id).path("xpRequired")
                require(!requiredNode.isMissingNode)
                val required = if (requiredNode.isNull) null else Math.toIntExact(integer(requiredNode))
                require(if (level == 50) required == null else required != null && required > xp)
                RecordAttributeView(level, xp, required)
            }
            val classId = body.path("classId").textValue(); require(!classId.isNullOrBlank())
            val stage = Math.toIntExact(integer(body.path("stage"))); require(stage in 1..3)
            require(catalog.path("classes").isArray)
            val definition = catalog.path("classes").single { it.path("id").textValue() == classId }
            val name = definition.path("name").textValue(); require(!name.isNullOrBlank())
            require(definition.path("stages").isArray && definition.path("stages").any { integer(it) == stage.toLong() })
            owner()
            RecordProgressView(revision, cycle, date.toString(), general, classId, stage, Collections.unmodifiableMap(balances), name, computedAt)
        } catch (blocked: OperationBlocked) { throw blocked }
        catch (_: Exception) { throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE) }
    }
}
