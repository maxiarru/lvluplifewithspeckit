package lvluplife.api

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.Application

// Composition seam. Trust-boundary adapters are deliberately absent until their red tests run.
fun Application.serverModule(verifyToken: (String) -> String? = { null }) {}

fun main() {
    // No data routes are exposed until authentication and contracts are implemented.
    embeddedServer(Netty, port = 8081) { serverModule() }.start(wait = true)
}
