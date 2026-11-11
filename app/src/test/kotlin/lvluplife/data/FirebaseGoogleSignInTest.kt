package lvluplife.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FirebaseGoogleSignInTest {
    private class Google : GoogleCredentialSource {
        var requests = 0; var clears = 0; var failure: Exception? = null; var clearFailure: Exception? = null
        override suspend fun request(): GoogleIdToken { requests++; failure?.let { throw it }; return GoogleIdToken("synthetic-google-token") }
        override suspend fun clear() { clears++; clearFailure?.let { throw it } }
    }
    private class Firebase : FirebaseIdentitySource {
        var owner: String? = null; var google = true; var exchanges = 0; var signOuts = 0
        var failure: Exception? = null; var token = "synthetic-firebase-token"; var returnedUid = "owner-a"
        val refreshes = mutableListOf<Boolean>(); val entered = CompletableDeferred<Unit>(); var wait: CompletableDeferred<Unit>? = null
        override suspend fun exchange(token: GoogleIdToken) {
            assertEquals("synthetic-google-token", token.value); exchanges++; entered.complete(Unit)
            wait?.await(); failure?.let { throw it }; owner = "owner-a"
        }
        override fun uid() = owner
        override fun googleUser() = google
        override suspend fun session(forceRefresh: Boolean): SessionToken? {
            refreshes += forceRefresh; failure?.let { throw it }
            return owner?.let { SessionToken(returnedUid, token) }
        }
        override fun signOut() { signOuts++; owner = null }
    }

    @Test fun exchangesGoogleCredentialForFirebaseSessionInsteadOfSendingGoogleToken() = runBlocking {
        val google = Google(); val firebase = Firebase(); val adapter = FirebaseGoogleSignIn(google, firebase)
        val result = adapter.signIn() as SignInOutcome.Authenticated
        assertEquals("owner-a", result.session.uid); assertEquals("synthetic-firebase-token", result.session.idToken)
        assertEquals(1, google.requests); assertEquals(1, firebase.exchanges)
        assertFalse(result.toString().contains("synthetic-")); assertFalse(GoogleIdToken("synthetic-secret").toString().contains("synthetic-secret"))
    }

    @Test fun cancelledPickerDoesNotExchangeCredentialOrRetry() = runBlocking {
        val google = Google().apply { failure = SignInFailure(SignInFailureReason.CANCELLED) }; val firebase = Firebase()
        assertEquals(SignInOutcome.Cancelled, FirebaseGoogleSignIn(google, firebase).signIn())
        assertEquals(1, google.requests); assertEquals(0, firebase.exchanges); assertTrue(firebase.refreshes.isEmpty())
    }

    @Test fun deniedPickerDoesNotRetryOrExposeProviderException() = runBlocking {
        val google = Google().apply { failure = SignInFailure(SignInFailureReason.DENIED) }; val firebase = Firebase()
        assertEquals(SignInOutcome.Denied, FirebaseGoogleSignIn(google, firebase).signIn())
        assertEquals(1, google.requests); assertEquals(0, firebase.exchanges)
    }

    @Test fun unexpectedProviderFailureIsSafeUnavailable() = runBlocking {
        val google = Google().apply { failure = IllegalStateException("Bearer synthetic-secret /private/account") }
        val result = FirebaseGoogleSignIn(google, Firebase()).signIn()
        assertEquals(SignInOutcome.Unavailable, result); assertFalse(result.toString().contains("synthetic-secret"))
    }

    @Test fun rejectedFirebaseExchangeClearsPartialAuthentication() = runBlocking {
        val firebase = Firebase().apply { failure = SignInFailure(SignInFailureReason.DENIED) }
        assertEquals(SignInOutcome.Denied, FirebaseGoogleSignIn(Google(), firebase).signIn())
        assertNull(firebase.uid()); assertEquals(1, firebase.signOuts)
    }

    @Test fun wrongUidInvalidTokenOrNonGoogleUserCannotBecomeAuthenticated() = runBlocking {
        for (variant in 0..2) {
            val firebase = Firebase().apply { when (variant) { 0 -> returnedUid = "owner-b"; 1 -> token = "bad\ntoken"; else -> google = false } }
            assertEquals(SignInOutcome.Denied, FirebaseGoogleSignIn(Google(), firebase).signIn())
            assertNull(firebase.uid())
        }
    }

    @Test fun restoreUsesSdkRefreshWithoutOpeningPickerOrCreatingAnotherIdentity() = runBlocking {
        val google = Google(); val firebase = Firebase().apply { owner = "owner-a" }
        val result = FirebaseGoogleSignIn(google, firebase).restore() as SignInOutcome.Authenticated
        assertEquals("owner-a", result.session.uid); assertEquals(listOf(false), firebase.refreshes)
        assertEquals(0, google.requests); assertEquals(0, firebase.exchanges)
    }

    @Test fun missingStoredIdentityReturnsToAccess() = runBlocking {
        assertEquals(SignInOutcome.Cancelled, FirebaseGoogleSignIn(Google(), Firebase()).restore())
    }

    @Test fun refreshRequiresOriginalUidAndNeverFetchesAnotherAccountsToken() = runBlocking {
        val firebase = Firebase().apply { owner = "owner-b"; returnedUid = "owner-b" }
        assertEquals(SignInOutcome.Denied, FirebaseGoogleSignIn(Google(), firebase).refresh("owner-a"))
        assertTrue(firebase.refreshes.isEmpty()); assertEquals("owner-b", firebase.uid())
    }

    @Test fun refreshOriginalAccountForcesFreshFirebaseToken() = runBlocking {
        val firebase = Firebase().apply { owner = "owner-a" }
        val result = FirebaseGoogleSignIn(Google(), firebase).refresh("owner-a") as SignInOutcome.Authenticated
        assertEquals("owner-a", result.session.uid); assertEquals(listOf(true), firebase.refreshes)
    }

    @Test fun logoutClearsFirebaseEvenIfProviderStateClearFails() = runBlocking {
        val google = Google().apply { clearFailure = IllegalStateException("synthetic-secret") }
        val firebase = Firebase().apply { owner = "owner-a" }; val adapter = FirebaseGoogleSignIn(google, firebase)
        try { adapter.signOut(); fail("Expected safe cleanup failure") }
        catch (failure: SignInFailure) { assertEquals(SignInFailureReason.UNAVAILABLE, failure.reason); assertNull(failure.cause) }
        assertNull(firebase.uid()); assertEquals(1, google.clears)
    }

    @Test fun lateExchangeAfterLogoutCannotRestoreTheOldIdentity() = runBlocking {
        val firebase = Firebase().apply { wait = CompletableDeferred() }; val adapter = FirebaseGoogleSignIn(Google(), firebase)
        val attempt = async { adapter.signIn() }
        withTimeout(2_000) { firebase.entered.await() }; adapter.signOut(); firebase.wait!!.complete(Unit)
        assertEquals(SignInOutcome.Cancelled, attempt.await()); assertNull(firebase.uid())
    }

    @Test fun coroutineCancellationCleansUpEvenWhenFirebaseTaskFinishesLate() = runBlocking {
        val firebase = Firebase().apply { wait = CompletableDeferred() }; val adapter = FirebaseGoogleSignIn(Google(), firebase)
        val attempt = async { adapter.signIn() }
        withTimeout(2_000) { firebase.entered.await() }; attempt.cancel(); firebase.wait!!.complete(Unit)
        attempt.join(); assertTrue(attempt.isCancelled); assertNull(firebase.uid())
    }

    @Test fun successiveActivityAdaptersShareTheSameFirebaseLease() = runBlocking {
        val firebase = Firebase().apply { wait = CompletableDeferred() }; val lease = SignInCoordination()
        val old = FirebaseGoogleSignIn(Google(), firebase, lease); val next = FirebaseGoogleSignIn(Google(), firebase, lease)
        val first = async { old.signIn() }; withTimeout(2_000) { firebase.entered.await() }
        next.signOut(); val second = async { next.signIn() }; yield()
        assertEquals(1, firebase.exchanges)
        firebase.wait!!.complete(Unit)
        assertEquals(SignInOutcome.Cancelled, first.await())
        assertTrue(second.await() is SignInOutcome.Authenticated); assertEquals("owner-a", firebase.uid())
    }
}
