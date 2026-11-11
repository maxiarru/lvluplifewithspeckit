package lvluplife.application

import kotlinx.coroutines.*
import lvluplife.data.*
import org.junit.Assert.*
import org.junit.Test

class AccessControllerTest {
    @Test fun logoutInvalidatesProviderBeforeReturningEvenWhenCleanupSuspends() = runBlocking {
        val cleanup = CompletableDeferred<Unit>(); var invalidated = false
        val google = object : GoogleSignIn {
            override suspend fun signIn() = SignInOutcome.Cancelled
            override suspend fun signOut() { invalidated = true; cleanup.await() }
        }
        val session = SessionController().also { it.authenticate(SessionToken("owner-a", "synthetic-token")) }
        val controller = AccessController(session, google, AccessAccounts { ProfileLookup.Required }, this)
        controller.logout()
        assertNull(session.current())
        assertTrue("Provider must be invalidated before the Activity can be disposed", invalidated)
        cleanup.complete(Unit)
        until { !controller.state.value.busy }
    }

    private class Google : GoogleSignIn {
        val refreshed = mutableListOf<String>()
        override suspend fun signIn() = SignInOutcome.Authenticated(SessionToken("owner-a", "synthetic-original"))
        override suspend fun refresh(expectedUid: String): SignInOutcome {
            refreshed += expectedUid
            return SignInOutcome.Authenticated(SessionToken(expectedUid, "synthetic-refreshed"))
        }
        override suspend fun signOut() = Unit
    }
    private suspend fun until(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(1)
    }

    @Test fun successfulAuthenticationRecoversOriginalPendingBeforeAuthoritativeLookup() = runBlocking {
        val steps = java.util.Collections.synchronizedList(mutableListOf<String>())
        val session = SessionController(object : SessionOperations {
            override fun resumeAuthenticated(uid: String) { steps += "resume:$uid" }
            override fun clearSessionMemory() = Unit
            override fun recover(): List<OperationSnapshot> { steps += "recover"; return emptyList() }
        })
        val accounts = AccessAccounts { uid -> steps += "lookup:$uid"; ProfileLookup.Existing(uid, "Player") }
        val controller = AccessController(session, Google(), accounts, this)
        controller.signIn()
        until { controller.state.value.profile is ProfileLookup.Existing }
        assertEquals(listOf("resume:owner-a", "recover", "lookup:owner-a"), steps.toList())
    }

    @Test fun lookupRenewsOnlyOriginalIdentityAndRetriesOnce() = runBlocking {
        val google = Google(); val session = SessionController(); var queries = 0
        val accounts = AccessAccounts { uid ->
            if (++queries == 1) throw OperationBlocked(ApiFailureCode.AUTH_REQUIRED)
            ProfileLookup.Existing(uid, "Player")
        }
        val controller = AccessController(session, google, accounts, this)
        controller.signIn()
        until { controller.state.value.profile is ProfileLookup.Existing }
        assertEquals(listOf("owner-a"), google.refreshed); assertEquals(2, queries)
        assertEquals("owner-a", session.current()?.uid)
    }

    @Test fun reopenedProfileRetainsDraftAndRetriesItsStoredOperationWithoutCreatingAnother() = runBlocking {
        val ref = OperationRef("owner-a", "123e4567-e89b-42d3-a456-426614174000")
        var retries = 0
        val accounts = object : AccessAccounts {
            override suspend fun load(uid: String) = ProfileLookup.Required
            override suspend fun draft(uid: String) = "Player"
            override suspend fun pendingProfile(uid: String) = OperationSnapshot(ref, PendingState.UNKNOWN)
            override suspend fun complete(uid: String, intentId: String, username: String): OperationSnapshot = error("Must reuse stored operation")
            override suspend fun retry(original: OperationRef): OperationSnapshot {
                assertEquals(ref, original); retries++; return OperationSnapshot(ref, PendingState.UNKNOWN)
            }
        }
        val controller = AccessController(SessionController(), Google(), accounts, this)
        controller.signIn()
        until { controller.state.value.pending != null }
        assertEquals("Player", controller.state.value.username)
        controller.submit(); assertEquals(0, retries)
        controller.retryPending()
        until { retries == 1 && !controller.state.value.busy }
        assertEquals(ref, controller.state.value.pending?.ref)
    }
}
