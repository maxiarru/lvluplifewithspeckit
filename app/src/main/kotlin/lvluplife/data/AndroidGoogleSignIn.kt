package lvluplife.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import java.security.SecureRandom
import java.util.Base64
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class CredentialManagerGoogleSource(private val activity: Activity, private val clientId: String) : GoogleCredentialSource {
    private val manager = CredentialManager.create(activity)
    override suspend fun request(): GoogleIdToken {
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        return try {
            val response = manager.getCredential(activity, GoogleCredentialRequests.build(clientId, nonce))
            GoogleCredentialRequests.parse(response.credential, nonce)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: GetCredentialCancellationException) { throw SignInFailure(SignInFailureReason.CANCELLED) }
        catch (_: NoCredentialException) { throw SignInFailure(SignInFailureReason.DENIED) }
        catch (safe: SignInFailure) { throw safe }
        catch (_: GetCredentialException) { throw SignInFailure(SignInFailureReason.UNAVAILABLE) }
        catch (_: Exception) { throw SignInFailure(SignInFailureReason.UNAVAILABLE) }
    }
    override suspend fun clear() {
        try { manager.clearCredentialState(ClearCredentialStateRequest()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw SignInFailure(SignInFailureReason.UNAVAILABLE) }
    }
}

/** Firebase alone exchanges/verifies Google credentials and refreshes the Firebase ID token. */
class FirebaseSdkIdentitySource(private val auth: FirebaseAuth) : FirebaseIdentitySource {
    override suspend fun exchange(token: GoogleIdToken) = safe {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token.value, null)).await()
        Unit
    }
    override fun uid(): String? = auth.currentUser?.uid
    override fun googleUser(): Boolean = auth.currentUser?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true
    override suspend fun session(forceRefresh: Boolean): SessionToken? = safe {
        val user = auth.currentUser ?: return@safe null
        val token = user.getIdToken(forceRefresh).await().token ?: throw SignInFailure(SignInFailureReason.DENIED)
        if (auth.currentUser?.uid != user.uid) throw SignInFailure(SignInFailureReason.DENIED)
        SessionToken(user.uid, token)
    }
    override fun signOut() { auth.signOut() }
    private suspend fun <T> safe(block: suspend () -> T): T = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (safe: SignInFailure) { throw safe }
    catch (_: FirebaseAuthInvalidUserException) { throw SignInFailure(SignInFailureReason.DENIED) }
    catch (_: FirebaseAuthInvalidCredentialsException) { throw SignInFailure(SignInFailureReason.DENIED) }
    catch (_: Exception) { throw SignInFailure(SignInFailureReason.UNAVAILABLE) }
}

object AndroidGoogleSignIn {
    private val leases = WeakHashMap<FirebaseAuth, SignInCoordination>()
    fun create(activity: Activity): GoogleSignIn {
        return try {
            val resource = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
            val clientId = if (resource == 0) "" else activity.getString(resource)
            if (clientId.isBlank()) return Unconfigured
            val app = FirebaseApp.getApps(activity).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(activity) ?: return Unconfigured
            val auth = FirebaseAuth.getInstance(app)
            val lease = synchronized(leases) { leases.getOrPut(auth) { SignInCoordination() } }
            FirebaseGoogleSignIn(CredentialManagerGoogleSource(activity, clientId), FirebaseSdkIdentitySource(auth), lease)
        } catch (_: Exception) { Unconfigured }
    }
    private object Unconfigured : GoogleSignIn {
        override suspend fun signIn(): SignInOutcome = SignInOutcome.Unavailable
        override suspend fun restore(): SignInOutcome = SignInOutcome.Unavailable
        override suspend fun signOut() = Unit
    }
}
