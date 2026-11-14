package lvluplife.api

import com.google.api.client.auth.openidconnect.IdToken
import com.google.api.client.auth.openidconnect.IdTokenVerifier
import com.google.api.client.json.gson.GsonFactory
import com.google.auth.oauth2.IdentityPoolCredentials
import com.google.auth.oauth2.IdentityPoolSubjectTokenSupplier
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.request.path
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Workload credentials never select or authenticate an app user's account. */
internal class VercelWorkloadIdentity private constructor(
    private val settings: Settings,
    private val verifier: IdTokenVerifier,
    private val now: () -> Long,
) {
    private class Token(val value: String, val expires: Long) {
        override fun toString() = "WorkloadToken(redacted)"
    }
    private val current = AtomicReference<Token?>()
    private val reported = AtomicBoolean()
    val credentials: IdentityPoolCredentials = IdentityPoolCredentials.newBuilder()
        .setAudience("//iam.googleapis.com/${settings.provider}")
        .setSubjectTokenType("urn:ietf:params:oauth:token-type:jwt")
        .setTokenUrl("https://sts.googleapis.com/v1/token")
        .setServiceAccountImpersonationUrl(
            "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${settings.serviceAccount}:generateAccessToken")
        .setScopes(listOf("https://www.googleapis.com/auth/cloud-platform"))
        .setSubjectTokenSupplier(IdentityPoolSubjectTokenSupplier { _ -> subjectToken() })
        .build()

    fun accept(headers: List<String>?) {
        try {
            val value = headers?.singleOrNull() ?: unavailable()
            if (value.length > 16384 || !Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matches(value)) unavailable()
            // Reject duplicate JSON claims before using the JWT library's parser.
            val segments = value.split('.')
            for (segment in segments.take(2)) apiJson.readTree(Base64.getUrlDecoder().decode(segment))
            val token = IdToken.parse(GsonFactory.getDefaultInstance(), value)
            val payload = token.payload
            if (token.header.algorithm != "RS256" || payload.subject != settings.subject ||
                payload["owner_id"] != settings.teamId || payload["project_id"] != settings.projectId ||
                payload["environment"] != settings.environment ||
                payload.expirationTimeSeconds == null || payload.expirationTimeSeconds <= now() + 30) unavailable()
            // Verify signature against fixed Vercel JWKS, issuer, audience and time.
            // Google STS independently authenticates the token and the IAM condition.
            if (!verifier.verifyOrThrow(token)) unavailable()
            current.set(Token(value, payload.expirationTimeSeconds))
        } catch (_: Exception) {
            // JWT/provider errors may contain private input. Do not retain causes.
            throw IOException("Workload identity unavailable")
        }
    }

    internal fun subjectToken(): String {
        val token = current.get() ?: unavailable()
        if (token.expires <= now() + 30) unavailable()
        return token.value
    }

    fun refresh() {
        credentials.refreshIfExpired()
        if (reported.compareAndSet(false, true)) println("Workload federation ready")
    }

    private fun unavailable(): Nothing = throw IOException("Workload identity unavailable")

    private data class Settings(
        val provider: String, val serviceAccount: String, val teamSlug: String,
        val teamId: String, val projectId: String, val projectName: String, val environment: String,
    ) {
        val issuer get() = "https://oidc.vercel.com/$teamSlug"
        val audience get() = "https://vercel.com/$teamSlug"
        val subject get() = "owner:$teamSlug:project:$projectName:environment:$environment"
    }

    companion object {
        private val keys = listOf("LVLUP_WIF_PROVIDER", "LVLUP_WIF_SERVICE_ACCOUNT", "LVLUP_VERCEL_TEAM_SLUG",
            "LVLUP_VERCEL_TEAM_ID", "LVLUP_VERCEL_PROJECT_ID", "LVLUP_VERCEL_PROJECT_NAME", "LVLUP_VERCEL_ENVIRONMENT")

        fun fromEnvironment(
            env: Map<String, String> = System.getenv(),
            now: () -> Long = { System.currentTimeMillis() / 1000 },
            verifierFactory: ((String, String) -> IdTokenVerifier)? = null,
        ): VercelWorkloadIdentity? {
            if (keys.none { env.containsKey(it) }) return null
            fun required(key: String, pattern: String): String = env[key]?.takeIf { Regex(pattern).matches(it) }
                ?: throw ServerConfigurationException(key)
            if (env["LVLUP_ENV"] != "production" || env["VERCEL"] != "1" ||
                env.containsKey("GOOGLE_APPLICATION_CREDENTIALS") ||
                env["OAUTH_CLIENT_SKIP_SIGNATURE"] == "true" || System.getenv("OAUTH_CLIENT_SKIP_SIGNATURE") == "true") {
                throw ServerConfigurationException("workload identity mode")
            }
            val settings = Settings(
                required(keys[0], "projects/[0-9]+/locations/global/workloadIdentityPools/[a-z0-9-]+/providers/[a-z0-9-]+"),
                required(keys[1], "[a-z0-9-]+@[a-z0-9-]+\\.iam\\.gserviceaccount\\.com"),
                required(keys[2], "[a-z0-9][a-z0-9-]{0,62}"),
                required(keys[3], "team_[A-Za-z0-9]+"),
                required(keys[4], "prj_[A-Za-z0-9]+"),
                required(keys[5], "[a-z0-9][a-z0-9-]{0,99}"),
                required(keys[6], "preview|production"),
            )
            val verifier = verifierFactory?.invoke(settings.issuer, settings.audience) ?: IdTokenVerifier.Builder()
                .setIssuer(settings.issuer).setAudience(listOf(settings.audience))
                .setCertificatesLocation("${settings.issuer}/.well-known/jwks")
                .setAcceptableTimeSkewSeconds(30).setClock { now() * 1000 }.build()
            return VercelWorkloadIdentity(settings, verifier, now)
        }
    }
}

internal fun Application.installVercelWorkloadIdentity(identity: VercelWorkloadIdentity) {
    intercept(ApplicationCallPipeline.Call) {
        val path = call.request.path()
        if (!path.startsWith("/v1/") || call.request.headers[HttpHeaders.Authorization] == null) return@intercept
        try {
            withContext(Dispatchers.IO) {
                identity.accept(call.request.headers.getAll("x-vercel-oidc-token"))
                identity.refresh()
            }
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            call.respondApiError(ApiError.TEMPORARILY_UNAVAILABLE)
            finish()
        }
    }
}
