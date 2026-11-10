package lvluplife.api

import com.google.firebase.ErrorCode
import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuthException
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import java.util.concurrent.CancellationException
import kotlin.test.*
import lvluplife.model.Uid

class AuthenticationTest {
    private fun providerError(code: AuthErrorCode?) = FirebaseAuthException(
        ErrorCode.UNAUTHENTICATED, "private-provider-detail", null, null, code,
    )

    @Test fun verifierAlwaysChecksRevocationAndDerivesUidFromVerifiedToken() {
        var called = false
        val verifier = FirebaseIdentityVerifier { token, checkRevoked ->
            called = true
            assertEquals("synthetic-id-token", token)
            assertTrue(checkRevoked)
            "verified-owner-a"
        }
        assertEquals(Uid("verified-owner-a"), verifier.verify("synthetic-id-token"))
        assertTrue(called)
    }

    @Test fun expiredRevokedDisabledDeletedAndInvalidIdentityAreRejected() {
        for (code in listOf(AuthErrorCode.INVALID_ID_TOKEN, AuthErrorCode.EXPIRED_ID_TOKEN,
            AuthErrorCode.REVOKED_ID_TOKEN, AuthErrorCode.USER_DISABLED, AuthErrorCode.USER_NOT_FOUND,
            AuthErrorCode.TENANT_ID_MISMATCH)) {
            val verifier = FirebaseIdentityVerifier { _, checkRevoked ->
                assertTrue(checkRevoked)
                throw providerError(code)
            }
            assertNull(verifier.verify("synthetic-id-token"), code.name)
        }
    }

    @Test fun providerFailureIsUnavailableWithNoOriginalMessageOrCause() {
        for (code in listOf(AuthErrorCode.CERTIFICATE_FETCH_FAILED, AuthErrorCode.CONFIGURATION_NOT_FOUND, null)) {
            val error = assertFailsWith<IdentityProviderUnavailable> {
                FirebaseIdentityVerifier { _, _ -> throw providerError(code) }.verify("synthetic-id-token")
            }
            assertFalse(error.message.orEmpty().contains("private-provider-detail"))
            assertFalse(error.message.orEmpty().contains("synthetic-id-token"))
            assertNull(error.cause)
        }
    }

    @Test fun blankTokenNeverInvokesProviderAndInvalidProviderUidFailsClosed() {
        assertNull(FirebaseIdentityVerifier { _, _ -> error("must not run") }.verify(" "))
        assertFailsWith<IdentityProviderUnavailable> {
            FirebaseIdentityVerifier { _, _ -> "another/account" }.verify("synthetic-id-token")
        }
    }

    @Test fun cancellationIsPreserved() {
        assertFailsWith<CancellationException> {
            FirebaseIdentityVerifier { _, _ -> throw CancellationException() }.verify("synthetic-id-token")
        }
    }

    @Test fun callerCannotOverrideVerifiedOwnerThroughQueryOrOtherHeaders() = testApplication {
        application {
            serverModule { "verified-owner-a" }
            routing { get("/v1/test-owner") { call.respondText(call.verifiedUid.value) } }
        }
        val response = client.get("/v1/test-owner?uid=owner-b") {
            bearerAuth("synthetic-id-token")
            header("X-User-Id", "owner-b")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("verified-owner-a", response.bodyAsText())
    }

    @Test fun ambiguousOrMalformedAuthorizationNeverInvokesVerifier() = testApplication {
        application { serverModule { error("must not authenticate") } }
        for (value in listOf("Basic synthetic", "Bearer", "Bearer one,two", "Bearer token extra")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/me") {
                header(HttpHeaders.Authorization, value)
            }.status)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/me") {
            headers.append(HttpHeaders.Authorization, "Bearer one")
            headers.append(HttpHeaders.Authorization, "Bearer two")
        }.status)
    }

    @Test fun unavailableProviderDoesNotLeakTokenInHttpResponse() = testApplication {
        application { serverModule { throw IdentityProviderUnavailable() } }
        val response = client.get("/v1/me") { bearerAuth("synthetic-secret-token") }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("TEMPORARILY_UNAVAILABLE"))
        assertFalse(response.bodyAsText().contains("synthetic-secret-token"))
    }
}
