package lvluplife.data

import android.os.Bundle
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class GoogleCredentialRequestsTest {
    private val clientId = "synthetic-client.apps.googleusercontent.com"
    private fun jwt(nonce: String): String {
        fun encode(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
        return encode("""{"alg":"RS256"}""") + "." + encode("""{"nonce":"$nonce"}""") + ".synthetic-signature"
    }
    @Test fun explicitGoogleButtonRequestContainsWebClientIdAndAttemptNonce() {
        val request = GoogleCredentialRequests.build(clientId, "synthetic-nonce")
        val option = request.credentialOptions.single() as GetSignInWithGoogleOption
        assertEquals(clientId, option.serverClientId); assertEquals("synthetic-nonce", option.nonce)
    }
    @Test fun googleCredentialIsParsedWithoutExposingItsToken() {
        val token = jwt("synthetic-nonce")
        val credential = GoogleIdTokenCredential.Builder().setId("synthetic@example.test").setIdToken(token).build()
        val parsed = GoogleCredentialRequests.parse(credential, "synthetic-nonce")
        assertFalse(parsed.toString().contains(token))
    }
    @Test fun wrongNonceAndNonGoogleCredentialsAreRejectedWithSafeTypedError() {
        val credentials = listOf(GoogleIdTokenCredential.Builder().setId("synthetic@example.test").setIdToken(jwt("wrong-nonce")).build(),
            CustomCredential("unsupported.synthetic", Bundle()))
        for (credential in credentials) {
            val failure = assertThrows(SignInFailure::class.java) { GoogleCredentialRequests.parse(credential, "expected-nonce") }
            assertEquals(SignInFailureReason.DENIED, failure.reason); assertNull(failure.cause)
        }
    }
}
