package lvluplife.data

/** T018 injection contract; Credential Manager/Firebase implementation belongs to T022. */
interface GoogleSignIn {
    suspend fun signIn(): SignInOutcome
    suspend fun signOut()
    suspend fun restore(): SignInOutcome = SignInOutcome.Cancelled
    suspend fun refresh(expectedUid: String): SignInOutcome = SignInOutcome.Unavailable
}

class GoogleIdToken(internal val value: String) {
    override fun toString(): String = "GoogleIdToken(redacted)"
}
enum class SignInFailureReason { CANCELLED, DENIED, UNAVAILABLE }
class SignInFailure(val reason: SignInFailureReason) : Exception(reason.name)
interface GoogleCredentialSource {
    suspend fun request(): GoogleIdToken
    suspend fun clear()
}
interface FirebaseIdentitySource {
    suspend fun exchange(token: GoogleIdToken)
    fun uid(): String?
    fun googleUser(): Boolean
    suspend fun session(forceRefresh: Boolean): SessionToken?
    fun signOut()
}

sealed interface SignInOutcome {
    data class Authenticated(val session: SessionToken) : SignInOutcome
    data object Cancelled : SignInOutcome
    data object Denied : SignInOutcome
    data object Unavailable : SignInOutcome
}
