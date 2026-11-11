package lvluplife.data

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real Android Firebase SDK against local Auth emulator, with synthetic Google claims; not Google sign-in. */
class FirebaseSdkIdentityTest {
    @Test fun firebaseExchangesGoogleCredentialAndRefreshesTheSameUidInLocalEmulator() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setApplicationId("1:123:android:abcdef")
            .setApiKey("synthetic-emulator-api-key").setProjectId("demo-lvluplife").build(), "synthetic-${UUID.randomUUID()}")
        try {
            val auth = FirebaseAuth.getInstance(app)
            assertEquals("demo-lvluplife", auth.app.options.projectId)
            auth.useEmulator("10.0.2.2", 9099)
            fun encode(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
            val subject = "synthetic-${UUID.randomUUID()}"
            val jwt = encode("""{"alg":"none"}""") + "." +
                encode("""{"sub":"$subject","email":"$subject@example.test","email_verified":true,"iss":"https://accounts.google.com","aud":"synthetic-client"}""") + "."
            val source = FirebaseSdkIdentitySource(auth)
            source.exchange(GoogleIdToken(jwt))
            val uid = source.uid(); assertNotNull(uid); assertTrue(source.googleUser())
            val cached = source.session(false); val refreshed = source.session(true)
            assertTrue(cached?.uid == uid && refreshed?.uid == uid)
            assertFalse(refreshed.toString().contains("eyJ"))
            source.signOut(); assertNull(source.uid())
        } finally { app.delete() }
    }
}
