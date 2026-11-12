package lvluplife

import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import lvluplife.data.*
import okhttp3.TlsVersion
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** JVM regression of production transport plus actual generated release configuration and manifest. */
class ReleaseConfigTest {
    private val namespace="http://schemas.android.com/apk/res/android"
    private val id="123e4567-e89b-42d3-a456-426614174000"
    private val secret="synthetic-release-private-token"
    private fun endpoint(value:String)=ApiEndpoint.from(value,isDebug=false)
    private fun client(transport:HttpTransport)=ApiClient(endpoint("https://api.example.test"),SessionTokenProvider { SessionToken("owner-a",secret) },transport)
    private fun command(type:ApiCommandType):String {
        val payload:Map<String,Any?> = when(type) {
            ApiCommandType.COMPLETE_PROFILE -> mapOf("username" to "Player")
            ApiCommandType.CREATE_CUSTOM_ACTIVITY -> mapOf("name" to "Propia","category" to "Rutina","primary" to "STR")
            ApiCommandType.CREATE_RECORD -> mapOf("activityId" to "strength","activityDate" to "2026-11-12","hours" to 0,"minutes" to 30)
            ApiCommandType.EDIT_RECORD -> mapOf("recordId" to "record-a","expectedVersion" to 1,"activityDate" to "2026-11-12")
            ApiCommandType.DELETE_RECORD -> mapOf("recordId" to "record-a","expectedVersion" to 1)
            ApiCommandType.RESET_PROGRESSION -> mapOf("confirmed" to true)
        }
        return ApiClient.encode(ApiCommand(id,type,payload,if(type in setOf(ApiCommandType.COMPLETE_PROFILE,ApiCommandType.CREATE_CUSTOM_ACTIVITY)) null else "cycle-a"))
    }
    @Test fun generatedReleaseConfigNeverEnablesDebugOrDefaultsToEmulator() {
        val generated=File("build/generated/source/buildConfig/release/lvluplife/BuildConfig.java").readText()
        assertTrue(generated.contains("boolean DEBUG = false;"));assertTrue(generated.contains("BUILD_TYPE = \"release\""))
        val literal=generated.lineSequence().single { it.contains("API_BASE_URL = ") }.substringAfter("= ").trim().removeSuffix(";")
        val releaseUrl=com.fasterxml.jackson.databind.ObjectMapper().readTree(literal).textValue()
        if(releaseUrl.isNotEmpty()) endpoint(releaseUrl)
        else try { ApiClient.create("",SessionTokenProvider { null });fail("Missing release endpoint must fail closed") }
            catch(expected:IllegalArgumentException) { assertFalse(expected.toString().contains(secret)) }
        assertEquals(BuildConfig.BUILD_TYPE=="debug",BuildConfig.DEBUG)
        if(!BuildConfig.DEBUG && BuildConfig.API_BASE_URL.isNotEmpty()) endpoint(BuildConfig.API_BASE_URL)
    }
    @Test fun actualReleaseManifestIsNotDebuggableTestOnlyOrExportingPrivateComponents() {
        val parser=DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true;setFeature("http://apache.org/xml/features/disallow-doctype-decl",true) }
        val doc=parser.newDocumentBuilder().parse(File("build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"))
        val app=doc.getElementsByTagName("application").item(0) as Element
        for(flag in listOf("debuggable","testOnly","usesCleartextTraffic","allowBackup")) assertNotEquals("true",app.getAttributeNS(namespace,flag))
        assertEquals(0,doc.getElementsByTagName("instrumentation").length)
        for(tag in listOf("activity","service","receiver","provider")) {
            val nodes=doc.getElementsByTagName(tag)
            for(i in 0 until nodes.length) {
                val element=nodes.item(i) as Element;val name=element.getAttributeNS(namespace,"name")
                if(name.startsWith("lvluplife.") && element.getAttributeNS(namespace,"exported")=="true") assertEquals("lvluplife.MainActivity",name)
            }
        }
        val permissions=doc.getElementsByTagName("uses-permission")
        val names=(0 until permissions.length).map { (permissions.item(it) as Element).getAttributeNS(namespace,"name") }
        assertTrue("android.permission.INTERNET" in names)
        for(permission in listOf("READ_CONTACTS","GET_ACCOUNTS","READ_EXTERNAL_STORAGE","WRITE_EXTERNAL_STORAGE","ACCESS_FINE_LOCATION","RECORD_AUDIO"))
            assertFalse("android.permission.$permission" in names)
    }
    @Test fun releaseEndpointRejectsLocalCleartextCredentialsAndNonOriginUrls() {
        for(value in listOf("http://api.example.test","http://10.0.2.2:8081","https://10.0.2.2","https://localhost.",
            "https://127.0.0.1","https://[::1]","https://user:password@api.example.test","https://api.example.test/path",
            "https://api.example.test/?token=$secret","https://api.example.test/#fragment","")) {
            try { endpoint(value);fail("Unsafe release endpoint accepted") } catch(expected:IllegalArgumentException) { assertFalse(expected.toString().contains(secret)) }
        }
        assertEquals("api.example.test",endpoint("https://api.example.test").base.host)
    }
    @Test fun releaseTransportRequiresModernTlsWithoutRedirectsAutomaticRetriesOrLoggingInterceptors() {
        val transport=OkHttpTransport(endpoint("https://api.example.test")).client
        assertFalse(transport.followRedirects);assertFalse(transport.followSslRedirects);assertFalse(transport.retryOnConnectionFailure)
        assertEquals(1,transport.connectionSpecs.size)
        assertEquals(setOf(TlsVersion.TLS_1_2,TlsVersion.TLS_1_3),transport.connectionSpecs.single().tlsVersions!!.toSet())
        assertTrue(transport.interceptors.isEmpty());assertTrue(transport.networkInterceptors.isEmpty())
    }
    @Test fun everyCommandKeepsCredentialsOnlyInAuthenticationHeaderAndRedactsDiagnostics() {
        val requests=mutableListOf<TransportRequest>()
        val api=client(HttpTransport { requests+=it;TransportResponse(503,"$secret private-server-detail") })
        for(type in ApiCommandType.entries) {
            val result=api.send("owner-a",command(type))
            assertTrue(result is ApiReply.Failure);assertFalse(result.toString().contains(secret))
            val request=requests.last();assertEquals("Bearer $secret",request.headers["Authorization"])
            assertFalse(request.url.toString().contains(secret));assertFalse(request.body!!.contains(secret));assertFalse(request.toString().contains(secret))
        }
        assertEquals(ApiCommandType.entries.size,requests.size)
        assertFalse(SessionToken("owner-a",secret).toString().contains(secret));assertFalse(TransportResponse(500,secret).toString().contains(secret))
    }
    @Test fun allQueriesAndReceiptLookupKeepCredentialsOutOfUrlsBodiesAndDiagnostics() {
        val requests=mutableListOf<TransportRequest>();val api=client(HttpTransport { requests+=it;TransportResponse(403,secret) })
        for(query in listOf(ApiQuery.Me,ApiQuery.Progress,ApiQuery.Catalog,ApiQuery.CustomActivities,ApiQuery.History(HistoryPeriod.day,java.time.LocalDate.parse("2026-11-12"))))
            assertFalse(api.query("owner-a",query).toString().contains(secret))
        assertFalse(api.lookup("owner-a",id).toString().contains(secret));assertEquals(6,requests.size)
        for(request in requests) { assertEquals("Bearer $secret",request.headers["Authorization"]);assertNull(request.body)
            assertFalse(request.url.toString().contains(secret));assertFalse(request.toString().contains(secret)) }
    }
    @Test fun transportAndSessionProviderFailuresDiscardOriginalSecretMessagesAndCauses() {
        for(error in listOf(IOException(secret),RuntimeException(secret))) {
            val api=client(HttpTransport { throw error })
            for(type in ApiCommandType.entries) assertFalse(api.send("owner-a",command(type)).toString().contains(secret))
        }
        val api=ApiClient(endpoint("https://api.example.test"),SessionTokenProvider { throw IllegalStateException(secret) },HttpTransport { error("Must not send") })
        val result=api.query("owner-a",ApiQuery.Me) as ApiReply.Failure
        assertEquals(ApiFailureCode.TEMPORARILY_UNAVAILABLE,result.code);assertFalse(result.toString().contains(secret))
    }
    @Test fun missingOrMismatchedOwnerNeverSendsAnyCommandOrQuery() {
        var sent=0
        for(token in listOf(null,SessionToken("owner-b",secret))) {
            val api=ApiClient(endpoint("https://api.example.test"),SessionTokenProvider { token },HttpTransport { sent++;error("Must not send") })
            for(type in ApiCommandType.entries) assertEquals(ApiFailureCode.AUTH_REQUIRED,(api.send("owner-a",command(type)) as ApiReply.Failure).code)
            assertEquals(ApiFailureCode.AUTH_REQUIRED,(api.query("owner-a",ApiQuery.Progress) as ApiReply.Failure).code)
            assertEquals(ApiFailureCode.AUTH_REQUIRED,(api.lookup("owner-a",id) as ApiReply.Failure).code)
        }
        assertEquals(0,sent)
    }
}
