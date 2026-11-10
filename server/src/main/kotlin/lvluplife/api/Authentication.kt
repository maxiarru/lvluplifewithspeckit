package lvluplife.api

import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import lvluplife.application.IdentityVerifier
import lvluplife.model.Uid

class IdentityProviderUnavailable : RuntimeException("Identity provider temporarily unavailable")

class FirebaseIdentityVerifier internal constructor(
    private val verifyIdToken: (String, Boolean) -> String,
) : IdentityVerifier {
    constructor(auth: FirebaseAuth) : this({ token, checkRevoked ->
        auth.verifyIdToken(token, checkRevoked).uid
    })

    override fun verify(idToken: String): Uid? {
        if (idToken.isBlank()) return null
        return try {
            // Signature, audience, expiration, revocation and disabled-user checks belong to Admin SDK.
            Uid(verifyIdToken(idToken, true))
        } catch (failure: FirebaseAuthException) {
            when (failure.authErrorCode) {
                AuthErrorCode.INVALID_ID_TOKEN, AuthErrorCode.EXPIRED_ID_TOKEN,
                AuthErrorCode.REVOKED_ID_TOKEN, AuthErrorCode.USER_DISABLED,
                AuthErrorCode.USER_NOT_FOUND, AuthErrorCode.TENANT_ID_MISMATCH -> null
                else -> throw IdentityProviderUnavailable()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // SDK errors may contain tokens or private provider details. Do not retain/log their cause.
            throw IdentityProviderUnavailable()
        }
    }
}

private val verifiedUidKey = AttributeKey<Uid>("verified-owner")
val ApplicationCall.verifiedUid: Uid get() = attributes[verifiedUidKey]
private val bearer = Regex("Bearer[ \\t]+([^\\s,]+)", RegexOption.IGNORE_CASE)

internal fun Application.installIdentityGuard(verifyToken: (String) -> String?) {
    intercept(ApplicationCallPipeline.Call) {
        val path = call.request.path()
        if (path != "/v1" && !path.startsWith("/v1/")) return@intercept

        val header = call.request.headers.getAll(HttpHeaders.Authorization)?.singleOrNull()
        val token = header?.let { bearer.matchEntire(it)?.groupValues?.get(1) }
        if (token == null) {
            call.respondApiError(ApiError.AUTH_REQUIRED)
            finish()
            return@intercept
        }
        val uid = try {
            // Admin SDK performs blocking IO. Never run it on the HTTP event loop.
            withContext(Dispatchers.IO) { verifyToken(token)?.let(::Uid) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            call.respondApiError(ApiError.FORBIDDEN)
            finish()
            return@intercept
        } catch (_: Exception) {
            call.respondApiError(ApiError.TEMPORARILY_UNAVAILABLE)
            finish()
            return@intercept
        }
        if (uid == null) {
            call.respondApiError(ApiError.AUTH_REQUIRED)
            finish()
            return@intercept
        }
        call.attributes.put(verifiedUidKey, uid)
    }
}
