package lvluplife.api

import com.google.api.client.auth.openidconnect.IdToken
import com.google.api.client.auth.openidconnect.IdTokenVerifier
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.json.webtoken.JsonWebSignature
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.*

class VercelWorkloadIdentityTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private var time = 1_700_000_000L
    private val env = mapOf(
        "VERCEL" to "1", "LVLUP_ENV" to "production",
        "LVLUP_WIF_PROVIDER" to "projects/123456789/locations/global/workloadIdentityPools/synthetic/providers/vercel",
        "LVLUP_WIF_SERVICE_ACCOUNT" to "synthetic-runtime@synthetic-project.iam.gserviceaccount.com",
        "LVLUP_VERCEL_TEAM_SLUG" to "synthetic-team", "LVLUP_VERCEL_TEAM_ID" to "team_synthetic",
        "LVLUP_VERCEL_PROJECT_ID" to "prj_synthetic", "LVLUP_VERCEL_PROJECT_NAME" to "synthetic-app",
        "LVLUP_VERCEL_ENVIRONMENT" to "preview",
    )

    private fun identity(): VercelWorkloadIdentity {
        val publicKey = pair.public as RSAPublicKey
        fun encoded(value: java.math.BigInteger) = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray().let { if (it[0] == 0.toByte()) it.drop(1).toByteArray() else it })
        val jwks = """{"keys":[{"alg":"RS256","kid":"synthetic-key","kty":"RSA","use":"sig","n":"${encoded(publicKey.modulus)}","e":"${encoded(publicKey.publicExponent)}"}]}"""
        val transport = MockHttpTransport.Builder().setLowLevelHttpResponse(
            MockLowLevelHttpResponse().setContentType("application/json").setContent(jwks)).build()
        return VercelWorkloadIdentity.fromEnvironment(env, { time }) { issuer, audience ->
            IdTokenVerifier.Builder().setIssuer(issuer).setAudience(listOf(audience))
                .setCertificatesLocation("$issuer/.well-known/jwks")
                .setHttpTransportFactory { transport }.setClock { time * 1000 }
                .setAcceptableTimeSkewSeconds(30).build()
        }!!
    }

    private fun token(change: IdToken.Payload.() -> Unit = {}, signingKey: java.security.PrivateKey = pair.private): String {
        val payload = IdToken.Payload().apply {
            issuer = "https://oidc.vercel.com/synthetic-team"
            audience = "https://vercel.com/synthetic-team"
            subject = "owner:synthetic-team:project:synthetic-app:environment:preview"
            issuedAtTimeSeconds = time
            expirationTimeSeconds = time + 7200
            this["owner_id"] = "team_synthetic"
            this["project_id"] = "prj_synthetic"
            this["environment"] = "preview"
            change()
        }
        return JsonWebSignature.signUsingRsaSha256(signingKey, GsonFactory.getDefaultInstance(),
            JsonWebSignature.Header().setAlgorithm("RS256").setKeyId("synthetic-key"), payload)
    }

    @Test fun otherEnvironmentsKeepTheirExistingAdcAndRequireNoVercelSettings() {
        assertNull(VercelWorkloadIdentity.fromEnvironment(emptyMap()))
        assertNull(VercelWorkloadIdentity.fromEnvironment(mapOf("LVLUP_ENV" to "emulator")))
    }

    @Test fun federationFailsClosedWithMissingSettingsOrUnsafeMode() {
        for (key in env.keys.filter { it.startsWith("LVLUP_") }) {
            assertFailsWith<ServerConfigurationException> { VercelWorkloadIdentity.fromEnvironment(env - key) }
        }
        for (replacement in listOf(mapOf("LVLUP_ENV" to "emulator"), mapOf("VERCEL" to "0"),
            mapOf("GOOGLE_APPLICATION_CREDENTIALS" to "private-path"), mapOf("OAUTH_CLIENT_SKIP_SIGNATURE" to "true"))) {
            assertFailsWith<ServerConfigurationException> { VercelWorkloadIdentity.fromEnvironment(env + replacement) }
        }
    }

    @Test fun configurationCannotRedirectCredentialExchangeToAnArbitraryHost() {
        for (key in env.keys.filter { it.startsWith("LVLUP_") && it != "LVLUP_ENV" }) {
            val error = assertFailsWith<ServerConfigurationException> {
                VercelWorkloadIdentity.fromEnvironment(env + (key to "https://private.example/secret"))
            }
            assertFalse(error.message!!.contains("private.example"))
        }
    }

    @Test fun aSignedTokenForTheExactWorkloadCanBeSuppliedToGoogle() {
        val identity = identity()
        assertFailsWith<IOException> { identity.subjectToken() }
        val signed = token()
        identity.accept(listOf(signed))
        assertEquals(signed, identity.subjectToken())
    }

    @Test fun wrongIssuerAudienceOwnerProjectAndEnvironmentAreRejected() {
        val changes: List<IdToken.Payload.() -> Unit> = listOf(
            { issuer = "https://untrusted.example" }, { audience = "https://vercel.com/another-team" },
            { subject = "owner:other:project:synthetic-app:environment:preview" },
            { this["owner_id"] = "team_other" }, { this["project_id"] = "prj_other" },
            { this["environment"] = "production" },
        )
        for (change in changes) {
            val identity = identity()
            assertFailsWith<IOException> { identity.accept(listOf(token(change))) }
            assertFailsWith<IOException> { identity.subjectToken() }
        }
    }

    @Test fun forgedSignatureCannotReplaceTheLastVerifiedToken() {
        val identity = identity()
        val valid = token()
        identity.accept(listOf(valid))
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val forged = token(signingKey = other.private)
        val error = assertFailsWith<IOException> { identity.accept(listOf(forged)) }
        assertNull(error.cause)
        assertFalse(error.message!!.contains(forged))
        assertEquals(valid, identity.subjectToken())
    }

    @Test fun missingMalformedAndDuplicateHeadersAreRejectedWithoutRetainingInput() {
        val identity = identity()
        for (values in listOf(null, emptyList(), listOf("private-input"), listOf(token(), token()), listOf("x".repeat(17000)))) {
            val error = assertFailsWith<IOException> { identity.accept(values) }
            assertEquals("Workload identity unavailable", error.message)
            assertNull(error.cause)
        }
    }

    @Test fun expiryAndRotationAreCheckedAgainWhenGoogleRequestsAToken() {
        val identity = identity()
        identity.accept(listOf(token()))
        time += 7200
        assertFailsWith<IOException> { identity.subjectToken() }
        val next = token()
        identity.accept(listOf(next))
        assertEquals(next, identity.subjectToken())
        assertFailsWith<IOException> { identity.accept(listOf(token({ expirationTimeSeconds = time - 1 }))) }
    }

    @Test fun workloadHeaderAloneNeverAuthenticatesAnAppUser() = testApplication {
        var userVerifierCalled = false
        application {
            installVercelWorkloadIdentity(identity())
            serverModule(verifyToken = { userVerifierCalled = true; "unexpected-user" })
        }
        val response = client.get("/v1/me") { header("x-vercel-oidc-token", token()) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(userVerifierCalled)
    }

    @Test fun invalidWorkloadCannotReachTheUserVerifier() = testApplication {
        var userVerifierCalled = false
        application {
            installVercelWorkloadIdentity(identity())
            serverModule(verifyToken = { userVerifierCalled = true; "unexpected-user" })
        }
        val response = client.get("/v1/me") {
            header("Authorization", "Bearer synthetic-user-token")
            header("x-vercel-oidc-token", "private-invalid-workload")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertFalse(userVerifierCalled)
    }
}
