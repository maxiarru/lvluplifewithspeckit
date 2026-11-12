package lvluplife.data

import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lvluplife.application.*

/** Read-only, session-leased catalogue. No private cache, pending operation or progression calculation. */
class ClassCatalogRepository(private val api: ApiClient, private val sessions: SessionTokenProvider,
    private val connected: () -> Boolean) {
    suspend fun load(uid: String): List<ClassCatalogEntry> = withContext(Dispatchers.IO) {
        val lease = sessions.current()
        if (lease?.uid != uid) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
        fun owner() { if (sessions.current() !== lease) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED) }
        if (!connected()) throw OperationBlocked(ApiFailureCode.NETWORK_ERROR)
        val body = when (val reply = api.query(uid, ApiQuery.Catalog)) {
            is ApiReply.Data -> reply.body
            is ApiReply.Failure -> throw OperationBlocked(reply.code)
            else -> throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE)
        }
        owner()
        try {
            val nodes = body.path("classes")
            require(nodes.isArray && nodes.size()==26)
            val refs = mutableSetOf<String>()
            val entries = nodes.map { entry ->
                val id = entry.path("id").textValue(); val name = entry.path("name").textValue()
                require(id != null && Regex("[a-z]+(?:-[a-z]+)*").matches(id))
                require(!name.isNullOrBlank() && name==name.trim() && name.none(Char::isISOControl))
                val stages = entry.path("stages"); val images = entry.path("images")
                val expected = if (id=="novato") listOf(1) else listOf(1,2,3)
                require(stages.isArray && stages.all { it.isIntegralNumber && it.canConvertToInt() })
                require(stages.map { it.intValue() }==expected)
                require(images.isArray && images.size()==expected.size)
                val values = images.mapIndexed { index, image ->
                    require(image.path("stage").isIntegralNumber && image.path("stage").canConvertToInt())
                    val stage = image.path("stage").intValue(); require(stage==expected[index])
                    val resource = image.path("resourceId").textValue()
                    require(resource != null && Regex("[a-z][a-z0-9_]*").matches(resource) && refs.add(resource))
                    val minimum = image.path("minimumLevel")
                    require(minimum.isIntegralNumber && minimum.canConvertToInt() && minimum.intValue() in 1..50)
                    ClassStageImage(stage,resource,minimum.intValue())
                }
                require(values.first().minimumLevel==1 && values.zipWithNext().all { (a,b) -> a.minimumLevel<b.minimumLevel })
                ClassCatalogEntry(id,name,Collections.unmodifiableList(values))
            }
            require(entries.map { it.id }.toSet().size==26 && entries.count { it.id=="novato" }==1 && refs.size==76)
            owner(); Collections.unmodifiableList(entries)
        } catch (blocked: OperationBlocked) { throw blocked }
        catch (_: Exception) { throw OperationBlocked(ApiFailureCode.UNREADABLE_RESPONSE) }
    }
}
