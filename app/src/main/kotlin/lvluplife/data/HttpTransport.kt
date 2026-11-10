package lvluplife.data

import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.TlsVersion
import okio.BufferedSink
import java.util.concurrent.TimeUnit

internal class ApiEndpoint private constructor(val base: HttpUrl) {
    companion object {
        fun from(value: String, isDebug: Boolean): ApiEndpoint {
            val url = value.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid API endpoint")
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null && url.encodedPath == "/") {
                "API endpoint must contain only origin"
            }
            val local = url.host.trimEnd('.') in setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
            require((url.isHttps && (isDebug || !local)) ||
                (isDebug && url.scheme == "http" && url.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))) {
                "HTTPS is required outside local debug"
            }
            return ApiEndpoint(url)
        }
    }
}

class TransportRequest internal constructor(val method: String, val url: HttpUrl, val headers: Map<String, String>, val body: String?) {
    override fun toString(): String = "TransportRequest($method, redacted)"
}
class TransportResponse(val status: Int, val body: String, val contentType: String = "application/json") {
    override fun toString(): String = "TransportResponse(status=$status, redacted)"
}
fun interface HttpTransport {
    /** Blocking IO; callers must use an IO dispatcher. Transport never retries a mutation. */
    fun execute(request: TransportRequest): TransportResponse
}

internal class OkHttpTransport(endpoint: ApiEndpoint) : HttpTransport {
    internal val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .connectionSpecs(buildList {
            add(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2).build())
            if (!endpoint.base.isHttps) add(ConnectionSpec.CLEARTEXT)
        })
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun execute(request: TransportRequest): TransportResponse {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (key, value) -> builder.header(key, value) }
        val encoded = request.body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val body = if (encoded != null && request.method == "POST") object : RequestBody() {
            override fun contentType() = encoded.contentType()
            override fun contentLength() = encoded.contentLength()
            override fun writeTo(sink: BufferedSink) = encoded.writeTo(sink)
            // Also prevents status-based follow-ups (for example 503 + Retry-After: 0).
            // The coordinator must resolve the receipt before an explicit resend.
            override fun isOneShot() = true
        } else encoded
        builder.method(request.method, body)
        return client.newCall(builder.build()).execute().use { response ->
            // Authorization decisions must not depend on reading a potentially broken error body.
            // Only success/receipt statuses need JSON; other server messages are discarded.
            val body = if (response.code in setOf(200, 409, 422)) response.body.string() else ""
            TransportResponse(response.code, body, response.header("Content-Type").orEmpty())
        }
    }
}
