package io.opennoodoe.app;

import io.opennoodoe.app.protocol.RidingStatus;

/** Connection-local readings. Times use the monotonic clock, not wall time. */
final class VehicleTelemetry {
    static final long FRESH_FOR_MS = 5_000;

    static final class Reading {
        final RidingStatus riding;
        final Boolean keyOn;
        final long receivedAt;

        Reading(RidingStatus riding, Boolean keyOn, long receivedAt) {
            this.riding = riding;
            this.keyOn = keyOn;
            this.receivedAt = receivedAt;
        }
    }

    private RidingStatus riding;
    private Boolean keyOn;
    private long ridingAt;
    private long keyAt;
    private long receivedAt;

    synchronized void onRidingStatus(RidingStatus value, long now, long wallTime) {
        if (value.status != 0) {
            riding = null;
            return;
        }
        riding = value;
        ridingAt = now;
        receivedAt = wallTime;
        if (value.keyStateKnown) {
            keyOn = value.keyOn;
            keyAt = now;
        }
    }

    synchronized boolean onKeyNotification(int value, long now) {
        if (value != 0 && value != 1) return false;
        boolean next = value == 1;
        // A key transition makes the previous speed sample obsolete.
        if (keyOn == null || keyOn != next) riding = null;
        keyOn = next;
        keyAt = now;
        return true;
    }

    synchronized Reading read(long now) {
        return new Reading(riding != null && fresh(now, ridingAt) ? riding : null,
                keyOn != null && fresh(now, keyAt) ? keyOn : null, receivedAt);
    }

    synchronized void reset() {
        riding = null;
        keyOn = null;
        ridingAt = keyAt = receivedAt = 0;
    }

    private static boolean fresh(long now, long at) {
        return now >= at && now - at < FRESH_FOR_MS;
    }
}
