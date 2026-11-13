package lvluplife.api

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import lvluplife.application.IdentityVerifier
import lvluplife.persistence.FirestoreAccess

enum class BackendMode { PRODUCTION, EMULATOR }
class ServerConfigurationException(key: String) : RuntimeException("Invalid server configuration: $key")
class ServerStartupUnavailable : RuntimeException("Backend initialization temporarily unavailable")

class ServerConfig private constructor(
    val projectId: String,
    val mode: BackendMode,
    val port: Int,
    val firestoreEmulatorHost: String?,
    val authEmulatorHost: String?,
    val bindHost: String,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): ServerConfig {
            val project = env["GOOGLE_CLOUD_PROJECT"] ?: invalid("GOOGLE_CLOUD_PROJECT")
            if (!Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]").matches(project)) invalid("GOOGLE_CLOUD_PROJECT")
            if (env["GCLOUD_PROJECT"]?.let { it != project } == true) invalid("GCLOUD_PROJECT")
            val mode = when (env["LVLUP_ENV"] ?: "production") {
                "production" -> BackendMode.PRODUCTION
                "emulator" -> BackendMode.EMULATOR
                else -> invalid("LVLUP_ENV")
            }
            val port = (env["PORT"] ?: "8081").toIntOrNull()?.takeIf { it in 1..65535 } ?: invalid("PORT")
            val firestore = env["FIRESTORE_EMULATOR_HOST"]
            val auth = env["FIREBASE_AUTH_EMULATOR_HOST"]
            if (mode == BackendMode.EMULATOR) {
                if (project != "demo-lvluplife") invalid("GOOGLE_CLOUD_PROJECT")
                if (firestore !in setOf("127.0.0.1:8080", "localhost:8080")) invalid("FIRESTORE_EMULATOR_HOST")
                if (auth !in setOf("127.0.0.1:9099", "localhost:9099")) invalid("FIREBASE_AUTH_EMULATOR_HOST")
            } else {
                if (env.containsKey("FIRESTORE_EMULATOR_HOST")) invalid("FIRESTORE_EMULATOR_HOST")
                if (env.containsKey("FIREBASE_AUTH_EMULATOR_HOST")) invalid("FIREBASE_AUTH_EMULATOR_HOST")
                if (project.startsWith("demo-")) invalid("GOOGLE_CLOUD_PROJECT")
            }
            val bindHost = env["LVLUP_BIND_HOST"] ?: "0.0.0.0"
            if (bindHost !in setOf("127.0.0.1", "0.0.0.0")) invalid("LVLUP_BIND_HOST")
            return ServerConfig(project, mode, port, firestore, auth, bindHost)
        }

        private fun invalid(key: String): Nothing = throw ServerConfigurationException(key)
    }
}

class ServerResources private constructor(
    val firestore: Firestore,
    private val firebaseApp: FirebaseApp,
) : AutoCloseable {
    val documents = FirestoreAccess(firestore)
    val identityVerifier: IdentityVerifier = FirebaseIdentityVerifier(FirebaseAuth.getInstance(firebaseApp))
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try { firestore.close() } finally { firebaseApp.delete() }
        }
    }

    companion object {
        fun open(config: ServerConfig = ServerConfig.fromEnvironment()): ServerResources {
            // Firebase Admin reads emulator variables from the actual process environment.
            // An injected config map must never bypass those production/emulator guards.
            val actual = ServerConfig.fromEnvironment()
            if (actual.mode != config.mode || actual.projectId != config.projectId ||
                actual.authEmulatorHost != config.authEmulatorHost || actual.firestoreEmulatorHost != config.firestoreEmulatorHost) {
                throw ServerConfigurationException("runtime environment")
            }
            var firestore: Firestore? = null
            var app: FirebaseApp? = null
            try {
                val emulator = config.mode == BackendMode.EMULATOR
                // Empty credentials have no token and cannot refresh against Google. Only the explicit
                // local emulator mode may use them; production always obtains ADC.
                val credentials = if (emulator) GoogleCredentials.newBuilder().build() else GoogleCredentials.getApplicationDefault()
                val options = FirestoreOptions.newBuilder().setProjectId(config.projectId).setDatabaseId("(default)")
                if (emulator) {
                    // Pin both endpoints. NoCredentials activates a legacy SDK path that ignores
                    // emulatorHost; "localhost" also selects that path, so normalize to loopback IP.
                    val endpoint = config.firestoreEmulatorHost!!.replace("localhost:", "127.0.0.1:")
                    options.setHost(endpoint).setEmulatorHost(endpoint).setCredentials(credentials)
                } else options.setCredentials(credentials)
                firestore = options.build().service
                val firebaseOptions = FirebaseOptions.builder().setProjectId(config.projectId).setCredentials(credentials)
                    .setConnectTimeout(5000).setReadTimeout(5000).build()
                app = FirebaseApp.initializeApp(firebaseOptions, "lvluplife-${UUID.randomUUID()}")
                return ServerResources(firestore, app)
            } catch (failure: Exception) {
                if (failure is InterruptedException) Thread.currentThread().interrupt()
                try { firestore?.close() } catch (_: Exception) { /* No private SDK diagnostics in logs. */ }
                try { app?.delete() } catch (_: Exception) { /* Preserve a safe startup error. */ }
                throw ServerStartupUnavailable()
            }
        }
    }
}
