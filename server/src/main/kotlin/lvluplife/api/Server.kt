package lvluplife.api

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    // No data routes are exposed until authentication and contracts are implemented.
    embeddedServer(Netty, port = 8081) {}.start(wait = true)
}
