package io.opennoodoe.app;

import io.opennoodoe.app.protocol.ByteCodec;
import io.opennoodoe.app.protocol.RidingStatus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class VehicleTelemetryTest {
    private final VehicleTelemetry telemetry = new VehicleTelemetry();

    @Test public void newConnectionDoesNotInventKeyOffOrZeroSpeed() {
        assertNull(telemetry.read(0).keyOn);
        assertNull(telemetry.read(0).riding);
    }

    @Test public void successiveRepliesUpdateSpeedOdoAndKey() {
        telemetry.onRidingStatus(reply(0, 0, 12000, 0), 100, 9000);
        assertEquals(Boolean.FALSE, telemetry.read(100).keyOn);
        telemetry.onRidingStatus(reply(0, 1, 12001, 47), 1100, 10000);
        VehicleTelemetry.Reading live = telemetry.read(1100);
        assertEquals(Boolean.TRUE, live.keyOn);
        assertEquals(47, live.riding.currentSpeed);
        assertEquals(12001, live.riding.odometer);
        assertEquals(10000, live.receivedAt);
        telemetry.onRidingStatus(reply(0, 1, 12002, 0), 2100, 11000);
        assertEquals(0, telemetry.read(2100).riding.currentSpeed);
    }

    @Test public void keyEventsUpdateImmediatelyAndInvalidatePreviousSpeed() {
        telemetry.onRidingStatus(reply(0, 1, 200, 35), 0, 1000);
        assertTrue(telemetry.onKeyNotification(0, 100));
        assertEquals(Boolean.FALSE, telemetry.read(100).keyOn);
        assertNull(telemetry.read(100).riding);
        assertTrue(telemetry.onKeyNotification(1, 200));
        assertEquals(Boolean.TRUE, telemetry.read(200).keyOn);
        assertNull(telemetry.read(200).riding);
    }

    @Test public void readsDoNotExtendFreshnessOrChangeReceiptTime() {
        telemetry.onRidingStatus(reply(0, 1, 200, 35), 100, 8000);
        assertEquals(8000, telemetry.read(2000).receivedAt);
        assertNotNull(telemetry.read(5099).riding);
        assertNull(telemetry.read(5100).riding);
        assertNull(telemetry.read(5100).keyOn);
        assertEquals(8000, telemetry.read(9000).receivedAt);
    }

    @Test public void keyEventsDoNotKeepOldSpeedFresh() {
        telemetry.onRidingStatus(reply(0, 1, 200, 35), 0, 1000);
        telemetry.onKeyNotification(1, 4000);
        assertNull(telemetry.read(5000).riding);
        assertEquals(Boolean.TRUE, telemetry.read(5000).keyOn);
        assertNull(telemetry.read(9000).keyOn);
    }

    @Test public void invalidKeyByteDoesNotBecomeKeyOn() {
        RidingStatus invalid = reply(0, 255, 200, 35);
        assertFalse(invalid.keyOn);
        assertFalse(invalid.keyStateKnown);
        telemetry.onRidingStatus(invalid, 0, 1000);
        assertNull(telemetry.read(0).keyOn);
        assertFalse(telemetry.onKeyNotification(255, 100));
        assertNull(telemetry.read(100).keyOn);
    }

    @Test public void failedReplyDoesNotPublishItsSpeedOdoOrIgnition() {
        telemetry.onRidingStatus(reply(0, 0, 200, 0), 0, 1000);
        telemetry.onRidingStatus(reply(1, 1, 99999, 255), 100, 1100);
        assertNull(telemetry.read(100).riding);
        assertEquals(Boolean.FALSE, telemetry.read(100).keyOn);
        assertEquals(1000, telemetry.read(100).receivedAt);
    }

    @Test public void reconnectStartsWithoutPreviousVehiclesReadings() {
        telemetry.onRidingStatus(reply(0, 1, 4294967295L, 100), 100, 9000);
        assertEquals(4294967295L, telemetry.read(100).riding.odometer);
        telemetry.reset();
        assertNull(telemetry.read(200).riding);
        assertNull(telemetry.read(200).keyOn);
        assertEquals(0, telemetry.read(200).receivedAt);
        telemetry.onRidingStatus(reply(0, 0, 20, 0), 300, 9200);
        assertEquals(20, telemetry.read(300).riding.odometer);
        assertEquals(Boolean.FALSE, telemetry.read(300).keyOn);
    }

    private static RidingStatus reply(int status, int key, long odo, int speed) {
        byte[] payload = new byte[11];
        ByteCodec.putU16le(payload, 0, status);
        payload[2] = (byte) key;
        ByteCodec.putU32le(payload, 3, odo);
        payload[9] = (byte) 180;
        payload[10] = (byte) speed;
        return RidingStatus.fromReply(payload);
    }
}
