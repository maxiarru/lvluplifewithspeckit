package lvluplife.data

/** T018 injection contract; Credential Manager/Firebase implementation belongs to T022. */
interface GoogleSignIn {
    suspend fun signIn(): SignInOutcome
    suspend fun signOut()
}

sealed interface SignInOutcome {
    data class Authenticated(val session: SessionToken) : SignInOutcome
    data object Cancelled : SignInOutcome
    data object Denied : SignInOutcome
    data object Unavailable : SignInOutcome
}
