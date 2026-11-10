package lvluplife.api

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.Application
import com.google.firebase.auth.FirebaseAuth

fun Application.serverModule(verifyToken: (String) -> String? = { token ->
    FirebaseIdentityVerifier(FirebaseAuth.getInstance()).verify(token)?.value
}) {
    installIdentityGuard(verifyToken)
}

fun main() {
    // No data routes are exposed until authentication and contracts are implemented.
    embeddedServer(Netty, port = 8081) { serverModule() }.start(wait = true)
}
