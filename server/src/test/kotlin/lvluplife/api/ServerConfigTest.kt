package lvluplife.api

import kotlin.test.*

class ServerConfigTest {
    @Test fun localRealBackendCanBindOnlyLoopbackWithoutSelectingEmulators() {
        val env = mapOf("GOOGLE_CLOUD_PROJECT" to "synthetic-project", "LVLUP_BIND_HOST" to "127.0.0.1")
        val config = ServerConfig.fromEnvironment(env)
        assertEquals("127.0.0.1", config.bindHost)
        assertEquals(BackendMode.PRODUCTION, config.mode)
        assertEquals("0.0.0.0", ServerConfig.fromEnvironment(env - "LVLUP_BIND_HOST").bindHost)
        for (host in listOf("", "private-host", "http://127.0.0.1", "192.168.1.10")) {
            val failure = assertFailsWith<ServerConfigurationException> {
                ServerConfig.fromEnvironment(env + ("LVLUP_BIND_HOST" to host))
            }
            assertEquals("Invalid server configuration: LVLUP_BIND_HOST", failure.message)
        }
    }

    private val emulator = mapOf("LVLUP_ENV" to "emulator", "GOOGLE_CLOUD_PROJECT" to "demo-lvluplife",
        "FIRESTORE_EMULATOR_HOST" to "127.0.0.1:8080", "FIREBASE_AUTH_EMULATOR_HOST" to "127.0.0.1:9099")

    @Test fun productionIsDefaultAndRequiresAnExplicitProject() {
        val config = ServerConfig.fromEnvironment(mapOf("GOOGLE_CLOUD_PROJECT" to "synthetic-project"))
        assertEquals(BackendMode.PRODUCTION, config.mode)
        assertEquals(8081, config.port)
        assertNull(config.firestoreEmulatorHost)
        assertFailsWith<ServerConfigurationException> { ServerConfig.fromEnvironment(emptyMap()) }
    }

    @Test fun productionRejectsEveryEmulatorVariableEvenIfEmpty() {
        for (key in listOf("FIRESTORE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST")) {
            for (value in listOf("", "127.0.0.1:9099")) {
                assertFailsWith<ServerConfigurationException> {
                    ServerConfig.fromEnvironment(mapOf("GOOGLE_CLOUD_PROJECT" to "synthetic-project", key to value))
                }
            }
        }
        assertFailsWith<ServerConfigurationException> {
            ServerConfig.fromEnvironment(mapOf("GOOGLE_CLOUD_PROJECT" to "demo-lvluplife"))
        }
    }

    @Test fun emulatorRequiresDemoProjectAndBothLocalEndpoints() {
        assertEquals(BackendMode.EMULATOR, ServerConfig.fromEnvironment(emulator).mode)
        for (key in listOf("GOOGLE_CLOUD_PROJECT", "FIRESTORE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST")) {
            assertFailsWith<ServerConfigurationException> { ServerConfig.fromEnvironment(emulator - key) }
        }
        assertFailsWith<ServerConfigurationException> {
            ServerConfig.fromEnvironment(emulator + ("GOOGLE_CLOUD_PROJECT" to "synthetic-project"))
        }
    }

    @Test fun emulatorCannotPointToRemoteHostsOrUrlEndpoints() {
        for (host in listOf("example.com:8080", "http://127.0.0.1:8080", "0.0.0.0:8080", "127.0.0.1:443")) {
            assertFailsWith<ServerConfigurationException> {
                ServerConfig.fromEnvironment(emulator + ("FIRESTORE_EMULATOR_HOST" to host))
            }
        }
    }

    @Test fun conflictingProjectsAndInvalidPortsFailWithoutEchoingValues() {
        assertFailsWith<ServerConfigurationException> {
            ServerConfig.fromEnvironment(emulator + ("GCLOUD_PROJECT" to "another-project"))
        }
        for (value in listOf("0", "65536", "private-configuration-value")) {
            val failure = assertFailsWith<ServerConfigurationException> {
                ServerConfig.fromEnvironment(emulator + ("PORT" to value))
            }
            assertFalse(failure.message.orEmpty().contains(value))
            assertNull(failure.cause)
        }
        assertEquals(8082, ServerConfig.fromEnvironment(emulator + ("PORT" to "8082")).port)
    }
}
