package lvluplife.api

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.format.DateTimeParseException
import lvluplife.model.OperationId

internal val apiJson: ObjectMapper = ObjectMapper()
    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

enum class CommandType { COMPLETE_PROFILE, CREATE_CUSTOM_ACTIVITY, CREATE_RECORD, EDIT_RECORD, DELETE_RECORD, RESET_PROGRESSION }
data class CommandEnvelope(val operationId: OperationId, val type: CommandType, val cycleId: String?, val payload: JsonNode)

internal fun invalidRequest(): Nothing = throw ApiException(ApiError.INVALID_REQUEST)

internal fun parseCommand(text: String): CommandEnvelope {
    val root = try { apiJson.readTree(text) ?: invalidRequest() }
    catch (_: JsonProcessingException) { invalidRequest() }
    root.fields(setOf("operationId", "type", "payload"), setOf("cycleId"))
    val operationId = parseOperationId(root.text("operationId"))
    val type = try { CommandType.valueOf(root.text("type")) }
    catch (_: IllegalArgumentException) { invalidRequest() }
    val hasCycle = type !in setOf(CommandType.COMPLETE_PROFILE, CommandType.CREATE_CUSTOM_ACTIVITY)
    val cycleId = if (hasCycle) root.id("cycleId") else {
        if (root.has("cycleId")) invalidRequest()
        null
    }
    val payload = root.get("payload") ?: invalidRequest()
    when (type) {
        CommandType.COMPLETE_PROFILE -> {
            payload.fields(setOf("username")); payload.text("username")
        }
        CommandType.CREATE_CUSTOM_ACTIVITY -> {
            payload.fields(setOf("name", "category", "primary"), setOf("secondary"))
            listOf("name", "category", "primary").forEach { payload.text(it) }
            if (payload.has("secondary")) payload.text("secondary")
        }
        CommandType.CREATE_RECORD -> {
            payload.fields(setOf("activityId", "activityDate"), setOf("hours", "minutes"))
            payload.id("activityId"); parseDate(payload.text("activityDate"))
            payload.duration(setOf("hours", "minutes"))
        }
        CommandType.EDIT_RECORD -> {
            payload.fields(setOf("recordId", "expectedVersion", "activityDate"), setOf("activityId", "hours", "minutes"))
            payload.id("recordId"); payload.integer("expectedVersion"); parseDate(payload.text("activityDate"))
            if (payload.has("activityId") || payload.has("hours") || payload.has("minutes")) {
                payload.id("activityId"); payload.integer("hours"); payload.integer("minutes")
            }
        }
        CommandType.DELETE_RECORD -> {
            payload.fields(setOf("recordId", "expectedVersion"))
            payload.id("recordId"); payload.integer("expectedVersion")
        }
        CommandType.RESET_PROGRESSION -> {
            payload.fields(setOf("confirmed"))
            if (payload.get("confirmed")?.isBoolean != true || !payload.get("confirmed").booleanValue()) invalidRequest()
        }
    }
    return CommandEnvelope(operationId, type, cycleId, payload)
}

internal fun parseOperationId(value: String): OperationId = try { OperationId.parse(value) }
catch (_: IllegalArgumentException) { invalidRequest() }

internal fun parseDate(value: String): LocalDate {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) invalidRequest()
    return try { LocalDate.parse(value) } catch (_: DateTimeParseException) { invalidRequest() }
}

private fun JsonNode.fields(required: Set<String>, optional: Set<String> = emptySet()) {
    if (!isObject) invalidRequest()
    val keys = fieldNames().asSequence().toSet()
    if (!keys.containsAll(required) || (keys - required - optional).isNotEmpty()) invalidRequest()
}

private fun JsonNode.text(name: String): String {
    val value = get(name) ?: invalidRequest()
    if (!value.isTextual) invalidRequest()
    return value.textValue()
}

private fun JsonNode.id(name: String): String = text(name).also {
    if (it.isBlank() || '/' in it || it == "." || it == ".." || it.any(Char::isISOControl)) invalidRequest()
}

private fun JsonNode.integer(name: String) {
    val value = get(name) ?: invalidRequest()
    if (!value.isIntegralNumber || !value.canConvertToLong()) invalidRequest()
}

private fun JsonNode.duration(fields: Set<String>) {
    if (fields.any { has(it) }) fields.forEach { integer(it) }
}
