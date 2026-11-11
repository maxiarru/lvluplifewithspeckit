package lvluplife.data

import androidx.credentials.Credential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64

object GoogleCredentialRequests {
    private val json = ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    fun build(clientId: String, nonce: String): GetCredentialRequest {
        require(clientId.isNotBlank() && nonce.isNotBlank())
        val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
        return GetCredentialRequest.Builder().addCredentialOption(option).build()
    }
    fun parse(credential: Credential, nonce: String): GoogleIdToken = try {
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val parts = token.split('.')
        require(parts.size == 3 && nonce.isNotBlank())
        val claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]))
        require(claims.isObject && claims.get("nonce")?.isTextual == true && claims.get("nonce").textValue() == nonce)
        // This only binds the attempt nonce. Firebase performs actual Google signature/issuer verification.
        GoogleIdToken(token)
    } catch (_: Exception) { throw SignInFailure(SignInFailureReason.DENIED) }
}
