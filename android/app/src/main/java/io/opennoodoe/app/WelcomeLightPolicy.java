package io.opennoodoe.app;

/** State machine reconstructed from the original Noodoe BreathingLightManager. */
final class WelcomeLightPolicy {
    static final int MAX_ACTIVATIONS = 3;
    static final long LIGHT_TIMEOUT_SECONDS = 120;
    static final int SHUTDOWN_IMMEDIATELY = 0xFC;

    enum Action {
        NONE,
        TURN_ON,
        TURN_OFF
    }

    private boolean enabled;
    private int shutdownTime;
    private int activationCount;
    private boolean connected;
    private boolean keyOn;
    private boolean lightOn;

    WelcomeLightPolicy(boolean enabled, int shutdownTime, int activationCount) {
        configure(enabled, shutdownTime);
        this.activationCount = clampCount(activationCount);
    }

    void configure(boolean enabled, int shutdownTime) {
        this.enabled = enabled;
        this.shutdownTime = shutdownTime;
    }

    Action onConnected() {
        if (connected) {
            return Action.NONE;
        }
        connected = true;
        if (keyOn || !enabled || shutdownTime == SHUTDOWN_IMMEDIATELY) {
            return Action.NONE;
        }
        if (activationCount >= MAX_ACTIVATIONS) {
            lightOn = false;
            return Action.TURN_OFF;
        }
        activationCount++;
        lightOn = true;
        return Action.TURN_ON;
    }

    void onDisconnected() {
        connected = false;
        keyOn = false;
        lightOn = false;
    }

    boolean onKeyState(boolean keyOn) {
        this.keyOn = keyOn;
        if (!keyOn) {
            return false;
        }
        boolean changed = activationCount != 0;
        activationCount = 0;
        lightOn = false;
        return changed;
    }

    Action onTimeout() {
        if (!lightOn) {
            return Action.NONE;
        }
        lightOn = false;
        return Action.TURN_OFF;
    }

    int activationCount() {
        return activationCount;
    }

    private static int clampCount(int value) {
        return Math.max(0, Math.min(value, MAX_ACTIVATIONS));
    }
}
