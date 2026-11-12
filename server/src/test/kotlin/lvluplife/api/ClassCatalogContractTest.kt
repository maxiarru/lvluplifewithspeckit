package lvluplife.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*
import lvluplife.progression.ClassCatalog
import lvluplife.progression.ClassRules

/** Real catalogue serialization through the shared HTTP route; no Firestore or UI formulas. */
class ClassCatalogContractTest {
    private val json = ObjectMapper()
    private fun catalog(check: (JsonNode) -> Unit) = testApplication {
        application {
            serverModule(ApiHandlers(query = { _, query ->
                assertEquals(ApiQuery.Catalog, query)
                catalogResponse()
            }), verifyToken = { if (it == "synthetic-catalog-token") "synthetic-catalog-owner" else null })
        }
        val response = client.get("/v1/catalog") { bearerAuth("synthetic-catalog-token") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val body = json.readTree(response.bodyAsText())
        assertTrue(body.path("classes").isArray)
        check(body)
    }

    @Test fun all26StableClassIdsArePresentExactlyOnce() = catalog { body ->
        val expected = setOf("novato", "guerrero", "explorador", "guardian", "sabio", "monje", "mistico",
            "maestro-de-armas", "paladin", "guerrero-arcano", "luchador-monje", "guerrero-mistico", "agil-guardian",
            "artifice", "ninja", "explorador-de-suenos", "guardian-de-la-sabiduria", "monje-guardian", "druida",
            "ilusionista", "oraculo", "monje-espiritual", "aventurero-versatil", "maestro-del-equilibrio",
            "heroe-ascendente", "heroe-integral")
        val classes = body.path("classes")
        assertEquals(26, classes.size())
        assertTrue(classes.all { it.path("id").isTextual })
        assertEquals(expected, classes.map { it.path("id").textValue() }.toSet())
        assertEquals(26, classes.map { it.path("name").textValue() }.toSet().size)
    }

    @Test fun publicMetadataMatchesCanonicalDomainWithoutOmittingClassesOrStages() = catalog { body ->
        val classes = body.path("classes")
        assertEquals(ClassCatalog.classes.map { it.id }, classes.map { it.path("id").textValue() })
        for (entry in classes) {
            val definition = assertNotNull(ClassCatalog.find(entry.path("id").textValue()))
            assertEquals(definition.name, entry.path("name").textValue())
            assertTrue(entry.path("attributes").isArray)
            assertEquals(definition.attributes.map { it.name }.toSet(), entry.path("attributes").map { it.textValue() }.toSet())
            assertEquals(definition.attributes.size, entry.path("attributes").size())
            assertTrue(entry.path("initialOnly").isBoolean)
            assertEquals(definition.initialOnly, entry.path("initialOnly").booleanValue())
            if (definition.balancedCount == null) assertTrue(entry.path("balancedCount").isNull)
            else {
                assertTrue(entry.path("balancedCount").isIntegralNumber)
                assertEquals(definition.balancedCount, entry.path("balancedCount").intValue())
            }
            assertTrue(entry.path("stages").isArray)
            assertTrue(entry.path("stages").all { it.isIntegralNumber })
            assertEquals(definition.stages, entry.path("stages").map { it.intValue() })
        }
    }

    @Test fun stageAvailabilityAgreesWithClassRulesAt14And15And34And35() = catalog { body ->
        val boundaries = mapOf(14 to listOf(1), 15 to listOf(1, 2), 34 to listOf(1, 2), 35 to listOf(1, 2, 3))
        for (entry in body.path("classes")) {
            val novice = entry.path("id").textValue() == "novato"
            val stages = entry.path("stages").map { it.intValue() }
            assertEquals(if (novice) listOf(1) else listOf(1, 2, 3), stages)
            for ((level, expected) in boundaries) {
                val unlocked = ClassRules.unlockedStages(entry.path("name").textValue(), level)
                assertEquals(if (novice) listOf(1) else expected, unlocked)
                assertTrue(stages.containsAll(unlocked))
            }
        }
    }

    @Test fun everyValidClassStageHasOneUniqueAndroidResourceReference() = catalog { body ->
        val resources = mutableSetOf<String>()
        var count = 0
        for (entry in body.path("classes")) {
            val images = entry.path("images")
            assertTrue(images.isArray, "${entry.path("id").asText()}: falta images para US5")
            val stages = entry.path("stages").map { it.intValue() }
            assertEquals(stages.size, images.size())
            assertTrue(images.all { it.path("stage").isIntegralNumber })
            assertEquals(stages, images.map { it.path("stage").intValue() })
            for (image in images) {
                assertTrue(image.path("resourceId").isTextual)
                val id = image.path("resourceId").textValue()
                assertTrue(Regex("[a-z][a-z0-9_]*").matches(id), "Referencia Android inválida")
                val suffix = mapOf(1 to "", 2 to "_ii", 3 to "_iii").getValue(image.path("stage").intValue())
                assertEquals(entry.path("id").textValue().replace('-', '_') + suffix, id)
                assertTrue(resources.add(id), "Referencia visual duplicada: $id")
                count++
            }
        }
        assertEquals(76, count)
        assertEquals(76, resources.size)
    }

    @Test fun noviceHasOnlyStageOneImageAndNoInventedLaterStages() = catalog { body ->
        val novice = body.path("classes").single { it.path("id").textValue() == "novato" }
        assertEquals(listOf(1), novice.path("stages").map { it.intValue() })
        val images = novice.path("images")
        assertTrue(images.isArray, "Novato requiere su única referencia visual")
        assertEquals(1, images.size())
        assertEquals(1, images.single().path("stage").intValue())
        assertTrue(images.single().path("resourceId").isTextual)
        assertTrue(images.single().path("resourceId").textValue().isNotBlank())
        assertFalse(images.any { it.path("stage").intValue() in listOf(2, 3) })
    }
}
