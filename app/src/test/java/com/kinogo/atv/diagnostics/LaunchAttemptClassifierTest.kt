package com.kinogo.atv.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchAttemptClassifierTest {
    @Test
    fun incompleteAttemptFromPreviousProcessNeedsRecovery() {
        val previous = attempt(pid = 100, stage = StartupStage.COMPOSE_ATTACHING)

        assertTrue(LaunchAttemptClassifier.needsRecovery(previous, currentPid = 200))
    }

    @Test
    fun readyOrSameProcessAttemptDoesNotCreateFalseCrash() {
        assertFalse(
            LaunchAttemptClassifier.needsRecovery(
                attempt(pid = 100, stage = StartupStage.UI_READY),
                currentPid = 200,
            ),
        )
        assertFalse(
            LaunchAttemptClassifier.needsRecovery(
                attempt(pid = 100, stage = StartupStage.COMPOSE_ATTACHING),
                currentPid = 100,
            ),
        )
        assertFalse(LaunchAttemptClassifier.needsRecovery(previous = null, currentPid = 200))
    }

    private fun attempt(pid: Int, stage: StartupStage) = LaunchAttempt(
        id = "attempt",
        pid = pid,
        startedAtEpochMs = 1L,
        stage = stage,
    )
}
