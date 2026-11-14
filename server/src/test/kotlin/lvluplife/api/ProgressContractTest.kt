package lvluplife.api

import java.time.Instant
import kotlin.test.*
import lvluplife.application.RecordOperations
import lvluplife.model.*

/** Feature 002 (FR-010): GET /progress publishes the canonical XP required for each attribute's next level. */
class ProgressContractTest {
    private val now = Instant.parse("2026-11-14T15:00:00Z")
    private val cycle = Cycle(Uid("synthetic-progress-owner"), CycleId("synthetic-progress-cycle"), now)
    private fun progression(): Progression {
        val levels = mapOf(Attribute.STR to AttributeBalance(6, 95), Attribute.DEX to AttributeBalance(49, 0),
            Attribute.INT to AttributeBalance(50, 0), Attribute.WIT to AttributeBalance(1, 29),
            Attribute.MEN to AttributeBalance(1, 0), Attribute.CON to AttributeBalance(1, 0))
        // 5 + 48 + 49 attribute gains = 102: 43 to reach general level 15, 55 more for 11 levels, remainder 4.
        return Progression.derived(cycle, Revision(7), now, levels.mapValues { InactivityBalance(it.value, 0) },
            GeneralBalance(26, 4), "maestro-de-armas", 2)
    }
    private fun attributes(payload: Map<String, Any?>) = assertIs<Map<*, *>>(payload["attributes"])
    private fun attribute(payload: Map<String, Any?>, id: String) = assertIs<Map<*, *>>(attributes(payload)[id])

    @Test fun progressPublishesXpRequiredForTheNextLevelOfEveryAttribute() {
        val payload = progressPayload(progression())
        assertEquals(180L, attribute(payload, "STR")["xpRequired"])
        assertEquals(1470L, attribute(payload, "DEX")["xpRequired"])
        assertEquals(30L, attribute(payload, "WIT")["xpRequired"])
        assertEquals(30L, attribute(payload, "MEN")["xpRequired"])
        assertEquals(30L, attribute(payload, "CON")["xpRequired"])
        assertEquals(95L, attribute(payload, "STR")["xp"]); assertEquals(6L, attribute(payload, "STR")["level"])
    }
    @Test fun maximumLevelPublishesAnExplicitNullRequirement() {
        val payload = progressPayload(progression())
        val int = attribute(payload, "INT")
        assertTrue(int.containsKey("xpRequired")); assertNull(int["xpRequired"])
        val json = apiJson.valueToTree<com.fasterxml.jackson.databind.JsonNode>(payload)
        assertTrue(json.path("attributes").path("INT").has("xpRequired"))
        assertTrue(json.path("attributes").path("INT").path("xpRequired").isNull)
    }
    @Test fun progressKeepsEveryExistingFieldUnchanged() {
        val base = RecordOperations.progressionResult(progression())
        val payload = progressPayload(progression())
        assertEquals(base.keys, payload.keys)
        assertEquals(base - "attributes", payload - "attributes")
        for (id in Attribute.entries.map { it.name })
            assertEquals(attribute(base, id), attribute(payload, id) - "xpRequired")
    }
    @Test fun receiptsAndStoredProjectionsDoNotCarryXpRequired() {
        val base = RecordOperations.progressionResult(progression())
        for (id in Attribute.entries.map { it.name }) assertFalse(attribute(base, id).containsKey("xpRequired"))
    }
}
