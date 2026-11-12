package lvluplife.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import lvluplife.application.*
import org.junit.Assert.*
import org.junit.Test

class HistoryRepositoryTest {
    @Test fun serverObservationTimeControlsHintDespiteDeviceClockSkewAndPreservesExactBoundary()=runBlocking {
        val f=Fixture()
        // The fixture's device clock is before this creation time; the server's observation is authoritative.
        val item=f.item.replace("14:59:00Z","15:01:00Z")
        for((observed,allowed) in listOf("15:01:00Z" to true,"15:05:59Z" to true,"15:06:00Z" to false,"15:00:59Z" to false)) {
            f.body=f.page("2026-11-11","2026-11-12",item).dropLast(1)+",\"observedAt\":\"2026-11-11T$observed\"}"
            assertEquals(allowed,f.repository.load("owner-a",f.selection).items.single().canCorrect)
        }
        f.body=f.page("2026-11-11","2026-11-12",f.item).dropLast(1)+",\"observedAt\":\"2026-11-11T16:00:00Z\"}"
        assertFalse(f.repository.load("owner-a",f.selection).items.single().canCorrect)
    }
    @Test fun malformedServerObservationCannotBecomeAnEmptyOrCorrectableHistory()=runBlocking {
        val f=Fixture();val valid=f.body
        for(value in listOf("null","123","\"not-an-instant\"")) {
            f.body=valid.dropLast(1)+",\"observedAt\":$value}"
            blocked(ApiFailureCode.UNREADABLE_RESPONSE) { f.repository.load("owner-a",f.selection) }
        }
    }
    private class Fixture {
        var token: SessionToken? = SessionToken("owner-a","synthetic-token")
        var online=true;var replaceSession=false;var fail=false;var profileUid="owner-a"
        val requests=mutableListOf<TransportRequest>()
        val item="""{"recordId":"record-one","cycleId":"cycle-current","activityId":"strength","activityDefinition":{"id":"strength","name":"Fuerza","mode":"DURATION"},"activityDate":"2026-11-11","durationMinutes":30,"createdAt":"2026-11-11T14:59:00Z","updatedAt":"2026-11-11T14:59:00Z","version":1}"""
        var body=page("2026-11-11","2026-11-12",item)
        val sessions=SessionTokenProvider { token }
        val api=ApiClient(ApiEndpoint.from("https://api.example.test",false),sessions,HttpTransport { request ->
            requests += request
            if(fail) TransportResponse(503,"{}")
            else if(request.url.pathSegments.contains("history")) {
                if(replaceSession) token=SessionToken("owner-a","synthetic-new-token")
                TransportResponse(200,body)
            } else TransportResponse(200,"""{"profileRequired":false,"profile":{"uid":"$profileUid","currentCycleId":"cycle-current"}}""")
        })
        val repository=HistoryRepository(api,sessions,{ online },Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"),ZoneOffset.UTC))
        val selection=HistorySelection(HistoryPeriod.day,"2026-11-11")
        fun page(start:String,end:String,items:String)="""{"periodStart":"$start","periodEndExclusive":"$end","revision":10,"complete":true,"items":[$items]}"""
    }
    private suspend fun blocked(code:ApiFailureCode, block:suspend ()->Unit) {
        try { block();fail("Expected rejection") } catch(failure:OperationBlocked) { assertEquals(code,failure.code) }
    }
    @Test fun completeHistoryPreservesOriginalMetadataAndUsesOnlyReadRequests()=runBlocking {
        val f=Fixture();val page=f.repository.load("owner-a",f.selection);val row=page.items.single()
        assertEquals("record-one",row.id);assertEquals(1L,row.version);assertEquals(30,row.durationMinutes);assertTrue(row.canCorrect)
        assertEquals(Instant.parse("2026-11-11T14:59:00Z"),row.createdAt);assertEquals(10L,page.revision)
        assertTrue(f.requests.all { it.method=="GET" });assertTrue(f.requests.first().url.pathSegments.contains("history"))
        assertEquals("day",f.requests.first().url.queryParameter("period"));assertEquals("2026-11-11",f.requests.first().url.queryParameter("date"))
    }
    @Test fun weekAndMonthRequireCorrectCompleteCalendarBounds()=runBlocking {
        val f=Fixture();f.body=f.page("2026-11-09","2026-11-16",f.item)
        f.repository.load("owner-a",HistorySelection(HistoryPeriod.week,"2026-11-11"))
        f.body=f.page("2026-11-01","2026-12-01",f.item)
        f.repository.load("owner-a",HistorySelection(HistoryPeriod.month,"2026-11-11"))
        f.body=f.page("2026-11-11","2026-11-12",f.item)
        blocked(ApiFailureCode.UNREADABLE_RESPONSE) { f.repository.load("owner-a",HistorySelection(HistoryPeriod.week,"2026-11-11")) }
    }
    @Test fun incompleteCorruptOrDuplicateRowsCannotBecomePartialOrEmptySuccess()=runBlocking {
        val f=Fixture();val valid=f.body
        for(value in listOf(valid.replace("\"complete\":true","\"complete\":false"),valid.replace("\"revision\":10","\"revision\":0"),
            valid.replace("\"version\":1","\"version\":1.5"),valid.replace("\"durationMinutes\":30","\"durationMinutes\":0"),
            valid.replace("2026-11-11T14:59:00Z","bad-instant"),f.page("2026-11-11","2026-11-12",f.item+","+f.item))) {
            f.body=value;blocked(ApiFailureCode.UNREADABLE_RESPONSE) { f.repository.load("owner-a",f.selection) }
        }
    }
    @Test fun serverErrorOfflineAndInvalidInputNeverProduceEmptyConfirmedHistory()=runBlocking {
        val f=Fixture();f.fail=true;blocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) { f.repository.load("owner-a",f.selection) }
        f.fail=false;f.requests.clear();f.online=false
        blocked(ApiFailureCode.NETWORK_ERROR) { f.repository.load("owner-a",f.selection) };assertTrue(f.requests.isEmpty())
        f.online=true;blocked(ApiFailureCode.INVALID_INPUT) { f.repository.load("owner-a",f.selection.copy(date="not-a-date")) };assertTrue(f.requests.isEmpty())
    }
    @Test fun previousCycleExpiredAndFutureCreationNeverGrantCorrectionPermission()=runBlocking {
        val f=Fixture()
        for(item in listOf(f.item.replace("cycle-current","cycle-previous"),f.item.replace("14:59:00Z","14:55:00Z"),f.item.replace("14:59:00Z","15:01:00Z"))) {
            f.body=f.page("2026-11-11","2026-11-12",item);assertFalse(f.repository.load("owner-a",f.selection).items.single().canCorrect)
        }
    }
    @Test fun replacedSessionOrWrongProfileOwnerCannotPublishData()=runBlocking {
        val f=Fixture();f.replaceSession=true;blocked(ApiFailureCode.AUTH_REQUIRED) { f.repository.load("owner-a",f.selection) }
        f.replaceSession=false;f.profileUid="owner-b"
        blocked(ApiFailureCode.UNREADABLE_RESPONSE) { f.repository.load("owner-a",f.selection) }
        blocked(ApiFailureCode.AUTH_REQUIRED) { f.repository.load("owner-b",f.selection) }
    }
    @Test fun tombstoneOutOfPeriodAndUnsortedResponseAreRejectedInsteadOfFilteredLocally()=runBlocking {
        val f=Fixture()
        for(item in listOf(f.item.dropLast(1)+",\"deletedAt\":\"2026-11-11T15:00:00Z\"}",f.item.replace("\"activityDate\":\"2026-11-11\"","\"activityDate\":\"2026-11-10\""),
            f.item.replace("record-one","z")+","+f.item.replace("record-one","a"))) {
            f.body=f.page("2026-11-11","2026-11-12",item);blocked(ApiFailureCode.UNREADABLE_RESPONSE) { f.repository.load("owner-a",f.selection) }
        }
    }
    @Test fun successfulEmptyQueryIsExplicitAndHistoryCollectionsAreImmutable()=runBlocking {
        val f=Fixture();val page=f.repository.load("owner-a",f.selection)
        try { (page.items as MutableList).clear();fail("Mutable history") } catch(_:UnsupportedOperationException) { }
        f.body=f.page("2026-11-11","2026-11-12","");assertTrue(f.repository.load("owner-a",f.selection).items.isEmpty())
    }
}
