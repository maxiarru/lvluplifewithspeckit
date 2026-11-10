package lvluplife.fixtures

import java.time.Duration
import kotlin.test.*

class FaultFixtureTest {
    @Test fun beforeCommitFailureLeavesNothingCommitted() {
        val environment = TestEnvironment().apply { fault = FaultPoint.BEFORE_COMMIT }
        assertEquals(FaultPoint.BEFORE_COMMIT, assertFailsWith<InjectedFault> {
            environment.write(environment.accountA, "synthetic-value")
        }.point)
        assertNull(environment.read(environment.accountA))
    }

    @Test fun afterCommitFailureLosesResponseButPreservesCommittedValue() {
        val environment = TestEnvironment().apply { fault = FaultPoint.AFTER_COMMIT }
        assertEquals(FaultPoint.AFTER_COMMIT, assertFailsWith<InjectedFault> {
            environment.write(environment.accountA, "synthetic-value")
        }.point)
        assertEquals("synthetic-value", environment.read(environment.accountA))
        assertNull(environment.read(environment.accountB))
    }

    @Test fun normalCommitAndClockRemainControlledByTestOnly() {
        val environment = TestEnvironment()
        val original = environment.clock.instant()
        environment.clock = java.time.Clock.offset(environment.clock, Duration.ofSeconds(300))
        assertEquals(original.plusSeconds(300), environment.clock.instant())
        assertEquals("synthetic-value", environment.write(environment.accountB, "synthetic-value"))
        assertNull(environment.read(environment.accountA))
    }
}
