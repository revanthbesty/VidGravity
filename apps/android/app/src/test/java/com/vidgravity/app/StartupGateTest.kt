package com.vidgravity.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupGateTest {
    @Test fun requiresCommitAndNonEmptyLayoutBeforeDraw() {
        val gate = StartupGate()
        assertFalse(gate.drawn())
        gate.committed()
        assertFalse(gate.drawn())
        gate.laidOut(0, 2400)
        assertFalse(gate.drawn())
        gate.laidOut(1080, 2400)
        assertTrue(gate.drawn())
        assertFalse(gate.drawn())
    }

    @Test fun layoutAloneCannotHideRecovery() {
        val gate = StartupGate()
        gate.laidOut(1080, 2400)
        assertFalse(gate.drawn())
        gate.committed()
        assertTrue(gate.drawn())
    }

    @Test fun timeoutKeepsRecoveryVisibleEvenIfComposeLaterProgresses() {
        val gate = StartupGate()
        gate.fail()
        gate.committed()
        gate.laidOut(1080, 2400)
        assertFalse(gate.drawn())
    }
}
