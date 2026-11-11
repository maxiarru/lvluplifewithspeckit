package lvluplife.data

import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import lvluplife.application.*
import org.junit.Assert.*
import org.junit.Test

/** Staged by scripts/test-account-installations.mjs in separate Android users/private app sandboxes.
 * Google claims are synthetic and accepted only by the local Auth emulator. No Google UI is tested.
 */
class AccountInstallationStages {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val runId: String get() = checkNotNull(InstrumentationRegistry.getArguments().getString("accountRun"))
        .also { require(UUID.fromString(it).toString() == it) }
    private val database get() = "account-installation-$runId.db"
    private val preferences get() = context.getSharedPreferences("account-installation-$runId", 0)
    private val username get() = "V" + runId.replace("-", "").take(12)
    private val json = ObjectMapper()
    private fun fingerprint(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private suspend fun identity(block: suspend (SessionController) -> Unit) {
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setApplicationId("1:123:android:abcdef")
            .setApiKey("synthetic-emulator-api-key").setProjectId("demo-lvluplife").build(), "account-$runId")
        try {
            val auth = FirebaseAuth.getInstance(app)
            assertEquals("demo-lvluplife", auth.app.options.projectId)
            auth.useEmulator("10.0.2.2", 9099)
            fun encode(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
            val subject = "synthetic-installation-$runId"
            val jwt = encode("""{"alg":"none"}""") + "." + encode(
                """{"sub":"$subject","email":"$subject@example.test","email_verified":true,"iss":"https://accounts.google.com","aud":"synthetic-client"}""") + "."
            val source = FirebaseSdkIdentitySource(auth)
            source.exchange(GoogleIdToken(jwt))
            assertTrue(source.googleUser())
            val token = checkNotNull(source.session(true))
            val session = SessionController(); assertTrue(session.authenticate(token))
            block(session)
        } finally { app.delete() }
    }
    private fun api(session: SessionController) = ApiClient.create("http://10.0.2.2:8081", session)
    private fun profile(api: ApiClient, session: SessionController): String {
        val reply = api.query(checkNotNull(session.current()).uid, ApiQuery.Me)
        assertTrue("Expected successful authoritative query", reply is ApiReply.Data)
        val body = (reply as ApiReply.Data).body
        assertFalse(body.path("profileRequired").booleanValue())
        assertTrue(body.path("profile").path("uid").textValue() == session.current()?.uid)
        assertEquals(username, body.path("profile").path("username").textValue())
        return fingerprint(json.writeValueAsString(body))
    }
    private fun checkpoint(profile: String, session: SessionController) {
        instrumentation.sendStatus(2, Bundle().apply {
            putString("profileFingerprint", profile)
            putString("ownerFingerprint", fingerprint(checkNotNull(session.current()).uid))
            putString("installationUid", Process.myUid().toString())
        })
    }

    @Test @ProcessStage fun firstInstallationCommitsButKeepsItsLostResponsePending() = runBlocking {
        identity { session ->
            val uid = checkNotNull(session.current()).uid
            assertFalse(preferences.contains("initialized"))
            PendingDatabase.open(context, database).use { store ->
                assertTrue(store.pendingFor(uid).isEmpty()); assertNull(store.draftFor(uid))
                val endpoint = ApiEndpoint.from("http://10.0.2.2:8081", true)
                val network = OkHttpTransport(endpoint); var droppedAfterConfirmedCommit = false
                val losingReply = ApiClient(endpoint, session, HttpTransport { request ->
                    val response = network.execute(request)
                    if (request.method == "POST") {
                        assertEquals(200, response.status)
                        assertEquals("CONFIRMED", json.readTree(response.body).path("outcome").textValue())
                        droppedAfterConfirmedCommit = true
                        throw IOException("Synthetic response loss after confirmed server reply")
                    }
                    response
                })
                val operations = OperationCoordinator(store, losingReply, session, { true })
                val accounts = ApiAccessAccounts(losingReply, operations, store, session)
                accounts.saveName(uid, username)
                val result = accounts.complete(uid, "installation-intent-$runId", username)
                assertTrue(droppedAfterConfirmedCommit); assertEquals(PendingState.UNKNOWN, result.state)
                assertEquals(result.ref.operationId, store.pendingFor(uid).single().operationId)
                val snapshot = profile(api(session), session)
                assertTrue(preferences.edit().putBoolean("initialized", true).putString("profile", snapshot)
                    .putString("operationId", result.ref.operationId).putString("owner", fingerprint(uid)).commit())
                checkpoint(snapshot, session)
            }
        }
    }

    @Test @ProcessStage fun secondInstallationReadsSameIdentityWithoutCreatingProfileOrImportingPending() = runBlocking {
        identity { session ->
            val uid = checkNotNull(session.current()).uid
            assertFalse("Second installation must have independent preferences", preferences.contains("initialized"))
            PendingDatabase.open(context, database).use { store ->
                assertTrue("Second installation must not import pending operations", store.pendingFor(uid).isEmpty())
                assertNull(store.draftFor(uid))
                val endpoint = ApiEndpoint.from("http://10.0.2.2:8081", true)
                val network = OkHttpTransport(endpoint)
                val readonly = ApiClient(endpoint, session, HttpTransport { request ->
                    assertEquals("Second installation only queries saved data", "GET", request.method)
                    assertEquals("/v1/me", request.url.encodedPath)
                    network.execute(request)
                })
                val accounts = ApiAccessAccounts(readonly, OperationCoordinator(store, readonly, session, { true }), store, session)
                assertEquals(ProfileLookup.Existing(uid, username), accounts.load(uid))
                checkpoint(profile(readonly, session), session)
                assertTrue(store.pendingFor(uid).isEmpty()); assertNull(store.draftFor(uid))
                assertTrue(preferences.edit().putBoolean("initialized", true).commit())
            }
        }
    }

    @Test @ProcessStage fun firstInstallationStillOwnsOriginalPendingAfterOtherInstallationReads() = runBlocking {
        identity { session ->
            val uid = checkNotNull(session.current()).uid
            assertTrue(preferences.getBoolean("initialized", false))
            assertEquals(preferences.getString("owner", null), fingerprint(uid))
            PendingDatabase.open(context, database).use { store ->
                val original = store.pendingFor(uid).single()
                assertEquals(preferences.getString("operationId", null), original.operationId)
                assertEquals(PendingState.UNKNOWN, original.state)
                val accounts = ApiAccessAccounts(api(session), OperationCoordinator(store, api(session), session, { true }), store, session)
                assertEquals(username, accounts.draft(uid))
                val snapshot = profile(api(session), session)
                assertEquals(preferences.getString("profile", null), snapshot)
                checkpoint(snapshot, session)
            }
        }
    }
}
