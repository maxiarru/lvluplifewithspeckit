package lvluplife.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.runBlocking
import lvluplife.application.*
import org.junit.Assert.*
import org.junit.Test

class ClassCatalogRepositoryTest {
    private class Fixture {
        val json=ObjectMapper()
        val ids=listOf("novato","guerrero","explorador","guardian","sabio","monje","mistico","maestro-de-armas",
            "paladin","guerrero-arcano","luchador-monje","guerrero-mistico","agil-guardian","artifice","ninja",
            "explorador-de-suenos","guardian-de-la-sabiduria","monje-guardian","druida","ilusionista","oraculo",
            "monje-espiritual","aventurero-versatil","maestro-del-equilibrio","heroe-ascendente","heroe-integral")
        // Server shape: attributes follow the domain enum order (STR, DEX, INT, WIT, MEN, CON).
        val attributes=mapOf("guerrero" to listOf("STR"),"explorador" to listOf("DEX"),"guardian" to listOf("CON"),
            "sabio" to listOf("INT"),"monje" to listOf("WIT"),"mistico" to listOf("MEN"),
            "maestro-de-armas" to listOf("STR","DEX"),"paladin" to listOf("STR","CON"),"guerrero-arcano" to listOf("STR","INT"),
            "luchador-monje" to listOf("STR","WIT"),"guerrero-mistico" to listOf("STR","MEN"),"agil-guardian" to listOf("DEX","CON"),
            "artifice" to listOf("DEX","INT"),"ninja" to listOf("DEX","WIT"),"explorador-de-suenos" to listOf("DEX","MEN"),
            "guardian-de-la-sabiduria" to listOf("INT","CON"),"monje-guardian" to listOf("WIT","CON"),"druida" to listOf("MEN","CON"),
            "ilusionista" to listOf("INT","WIT"),"oraculo" to listOf("INT","MEN"),"monje-espiritual" to listOf("WIT","MEN"))
        val multiple=mapOf("aventurero-versatil" to 3,"maestro-del-equilibrio" to 4,"heroe-ascendente" to 5,"heroe-integral" to 6)
        var body: JsonNode=json.valueToTree(mapOf("classes" to ids.map { id ->
            val stages=if(id=="novato") listOf(1) else listOf(1,2,3)
            val own=attributes[id].orEmpty()
            mapOf("id" to id,"name" to "Clase $id","attributes" to own,
                "balancedCount" to (if(id=="novato") null else multiple[id] ?: own.size),
                "initialOnly" to (id=="novato"),"stages" to stages,"images" to stages.map { stage ->
                mapOf("stage" to stage,"resourceId" to id.replace('-','_')+mapOf(1 to "",2 to "_ii",3 to "_iii").getValue(stage),
                    "minimumLevel" to mapOf(1 to 1,2 to 15,3 to 35).getValue(stage)) })
        }))
        var token: SessionToken?=SessionToken("owner-a","synthetic-token")
        var online=true
        var status=200
        var afterRead: (() -> Unit)?=null
        val requests=mutableListOf<TransportRequest>()
        val sessions=SessionTokenProvider { token }
        val api=ApiClient(ApiEndpoint.from("https://api.example.test",false),sessions,HttpTransport { request ->
            requests+=request;afterRead?.invoke();TransportResponse(status,body.toString())
        })
        val repository=ClassCatalogRepository(api,sessions,{ online })
        fun classes()=body.path("classes") as ArrayNode
        fun image(classIndex: Int=1,imageIndex: Int=1)=classes()[classIndex].path("images")[imageIndex] as ObjectNode
        fun entry(id: String)=classes().first { it.path("id").textValue()==id } as ObjectNode
    }
    private suspend fun unreadable(f: Fixture) {
        try { f.repository.load("owner-a");fail("Catálogo inválido aceptado") }
        catch(blocked: OperationBlocked) { assertEquals(ApiFailureCode.UNREADABLE_RESPONSE,blocked.code) }
    }
    @Test fun completeCatalogueUsesOnlyGetAndPreservesServerMinimums() = runBlocking {
        val f=Fixture();f.image().put("minimumLevel",9)
        val result=f.repository.load("owner-a")
        assertEquals(26,result.size);assertEquals(76,result.sumOf { it.images.size })
        assertEquals(9,result[1].images[1].minimumLevel)
        assertEquals(1,result.first().images.size)
        assertEquals(listOf("GET"),f.requests.map { it.method })
        assertEquals("catalog",f.requests.single().url.pathSegments.last())
    }
    @Test fun incompleteDuplicateOrExtraNoviceStagesCannotBecomeSuccessfulCatalogue() = runBlocking {
        val incomplete=Fixture();incomplete.classes().remove(25);unreadable(incomplete)
        val duplicate=Fixture();duplicate.classes().set(25,duplicate.classes()[24]);unreadable(duplicate)
        val novice=Fixture();(novice.classes()[0].path("stages") as ArrayNode).add(2);unreadable(novice)
        val missing=Fixture();(missing.classes()[1] as ObjectNode).remove("images");unreadable(missing)
    }
    @Test fun duplicateUnknownShapeOrReorderedResourceEntriesAreRejected() = runBlocking {
        val duplicate=Fixture();duplicate.image().put("resourceId","novato");unreadable(duplicate)
        val invalid=Fixture();invalid.image().put("resourceId","../../private");unreadable(invalid)
        val order=Fixture();order.image().put("stage",3);unreadable(order)
        val fraction=Fixture();fraction.image().put("stage",2.5);unreadable(fraction)
    }
    @Test fun missingFractionalOutOfRangeOrDescendingMinimumsAreRejected() = runBlocking {
        val missing=Fixture();missing.image().remove("minimumLevel");unreadable(missing)
        val fraction=Fixture();fraction.image().put("minimumLevel",15.5);unreadable(fraction)
        val invalid=Fixture();invalid.image().put("minimumLevel",51);unreadable(invalid)
        val descending=Fixture();descending.image().put("minimumLevel",40);unreadable(descending)
    }
    @Test fun otherOwnerAndReplacedSameUidLeaseCannotPublishData() = runBlocking {
        val other=Fixture()
        try { other.repository.load("owner-b");fail() }
        catch(blocked: OperationBlocked) { assertEquals(ApiFailureCode.AUTH_REQUIRED,blocked.code) }
        assertTrue(other.requests.isEmpty())
        val replaced=Fixture();replaced.afterRead={ replaced.token=SessionToken("owner-a","synthetic-new-token") }
        try { replaced.repository.load("owner-a");fail() }
        catch(blocked: OperationBlocked) { assertEquals(ApiFailureCode.AUTH_REQUIRED,blocked.code) }
    }
    @Test fun offlineAndHttpFailureNeverReturnFalseEmptyCatalogue() = runBlocking {
        val offline=Fixture();offline.online=false
        try { offline.repository.load("owner-a");fail() }
        catch(blocked: OperationBlocked) { assertEquals(ApiFailureCode.NETWORK_ERROR,blocked.code) }
        assertTrue(offline.requests.isEmpty())
        val failed=Fixture();failed.status=503
        try { failed.repository.load("owner-a");fail() }
        catch(blocked: OperationBlocked) { assertEquals(ApiFailureCode.TEMPORARILY_UNAVAILABLE,blocked.code) }
    }
    // AC-61: attributes and balance conditions come from the server for every kind of class.
    @Test fun everyClassKeepsItsServerConditionInSpecificationAttributeOrder() = runBlocking {
        val result=Fixture().repository.load("owner-a").associateBy { it.id }
        assertEquals(ClassCondition.InitialLevels,result.getValue("novato").condition)
        assertEquals(ClassCondition.BalancedAttributes(listOf("STR")),result.getValue("guerrero").condition)
        assertEquals(ClassCondition.BalancedAttributes(listOf("STR","DEX")),result.getValue("maestro-de-armas").condition)
        assertEquals(ClassCondition.BalancedAttributes(listOf("CON","INT")),result.getValue("guardian-de-la-sabiduria").condition)
        assertEquals(ClassCondition.BalancedAttributes(listOf("WIT","MEN")),result.getValue("monje-espiritual").condition)
        assertEquals(ClassCondition.BalancedCount(3),result.getValue("aventurero-versatil").condition)
        assertEquals(ClassCondition.BalancedCount(6),result.getValue("heroe-integral").condition)
        assertEquals(26,result.values.map { it.condition }.toSet().size)
    }
    @Test fun pairOrderFromTheServerDoesNotChangeTheCondition() = runBlocking {
        val f=Fixture();f.entry("maestro-de-armas").putArray("attributes").add("DEX").add("STR")
        assertEquals(ClassCondition.BalancedAttributes(listOf("STR","DEX")),
            f.repository.load("owner-a").first { it.id=="maestro-de-armas" }.condition)
    }
    @Test fun missingOrInconsistentConditionsAreRejected() = runBlocking {
        val missing=Fixture();missing.entry("guerrero").remove("attributes");unreadable(missing)
        val noCount=Fixture();noCount.entry("guerrero").remove("balancedCount");unreadable(noCount)
        val noInitial=Fixture();noInitial.entry("guerrero").remove("initialOnly");unreadable(noInitial)
        val unknown=Fixture();unknown.entry("guerrero").putArray("attributes").add("LUCK");unreadable(unknown)
        val repeated=Fixture();repeated.entry("paladin").putArray("attributes").add("STR").add("STR");unreadable(repeated)
        val mismatch=Fixture();mismatch.entry("paladin").put("balancedCount",1);unreadable(mismatch)
        val zero=Fixture();zero.entry("guerrero").put("balancedCount",0);unreadable(zero)
        val seven=Fixture();seven.entry("heroe-integral").put("balancedCount",7);unreadable(seven)
        val fraction=Fixture();fraction.entry("heroe-integral").put("balancedCount",5.5);unreadable(fraction)
        val multiWithAttributes=Fixture();multiWithAttributes.entry("aventurero-versatil").putArray("attributes")
            .add("STR").add("DEX").add("CON");unreadable(multiWithAttributes)
        val initialElsewhere=Fixture();initialElsewhere.entry("guerrero").put("initialOnly",true);unreadable(initialElsewhere)
        val noviceNotInitial=Fixture();noviceNotInitial.entry("novato").put("initialOnly",false)
            .put("balancedCount",1).putArray("attributes").add("CON");unreadable(noviceNotInitial)
        val noviceCount=Fixture();noviceCount.entry("novato").put("balancedCount",1);unreadable(noviceCount)
        val textual=Fixture();textual.entry("guerrero").put("initialOnly","false");unreadable(textual)
        val duplicate=Fixture();duplicate.entry("explorador").putArray("attributes").add("STR");unreadable(duplicate)
    }
    @Test fun returnedListsCannotBeMutatedByPresentation() = runBlocking {
        val values=Fixture().repository.load("owner-a")
        try { (values as MutableList).clear();fail() } catch(_: UnsupportedOperationException) { }
        try { (values.first().images as MutableList).clear();fail() } catch(_: UnsupportedOperationException) { }
        val pair=values.first { it.id=="paladin" }.condition as ClassCondition.BalancedAttributes
        try { (pair.attributes as MutableList).clear();fail() } catch(_: UnsupportedOperationException) { }
    }
}
