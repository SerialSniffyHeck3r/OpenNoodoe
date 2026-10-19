package io.opennoodoe.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WelcomeLightPolicyTest {
    @Test
    public void connectionTurnsOnOnceAndTimeoutTurnsOff() {
        WelcomeLightPolicy policy = new WelcomeLightPolicy(true, 1, 0);

        assertEquals(WelcomeLightPolicy.Action.TURN_ON, policy.onConnected());
        assertEquals(1, policy.activationCount());
        assertEquals(WelcomeLightPolicy.Action.NONE, policy.onConnected());
        assertEquals(WelcomeLightPolicy.Action.TURN_OFF, policy.onTimeout());
        assertEquals(WelcomeLightPolicy.Action.NONE, policy.onTimeout());
    }

    @Test
    public void threeConnectionsAreAllowedUntilKeyOnResetsCounter() {
        WelcomeLightPolicy policy = new WelcomeLightPolicy(true, 3, 0);
        for (int i = 0; i < 3; i++) {
            assertEquals(WelcomeLightPolicy.Action.TURN_ON, policy.onConnected());
            policy.onDisconnected();
        }

        assertEquals(WelcomeLightPolicy.Action.TURN_OFF, policy.onConnected());
        assertEquals(3, policy.activationCount());
        assertTrue(policy.onKeyState(true));
        assertEquals(0, policy.activationCount());
        assertFalse(policy.onKeyState(false));
        policy.onDisconnected();
        assertEquals(WelcomeLightPolicy.Action.TURN_ON, policy.onConnected());
    }

    @Test
    public void disabledAndImmediateShutdownNeverTurnOn() {
        WelcomeLightPolicy disabled = new WelcomeLightPolicy(false, 1, 0);
        WelcomeLightPolicy immediate = new WelcomeLightPolicy(true,
                WelcomeLightPolicy.SHUTDOWN_IMMEDIATELY, 0);

        assertEquals(WelcomeLightPolicy.Action.NONE, disabled.onConnected());
        assertEquals(WelcomeLightPolicy.Action.NONE, immediate.onConnected());
        assertEquals(0, disabled.activationCount());
        assertEquals(0, immediate.activationCount());
    }
}
