package io.opennoodoe.app.protocol;

public final class RidingStatus {
    public final int status;
    public final boolean keyOn;
    public final boolean keyStateKnown;
    public final long odometer;
    public final int stopDuration;
    public final int maxSpeed;
    public final int currentSpeed;

    private RidingStatus(int status, int keyState, long odometer, int stopDuration,
            int maxSpeed, int currentSpeed) {
        this.status = status;
        this.keyOn = keyState == 1;
        this.keyStateKnown = keyState == 0 || keyState == 1;
        this.odometer = odometer;
        this.stopDuration = stopDuration;
        this.maxSpeed = maxSpeed;
        this.currentSpeed = currentSpeed;
    }

    public static RidingStatus fromReply(byte[] payload) {
        ByteCodec.require(payload, 0, 11);
        return new RidingStatus(
                ByteCodec.u16le(payload, 0),
                payload[2] & 0xFF,
                ByteCodec.u32le(payload, 3),
                ByteCodec.u16le(payload, 7),
                payload[9] & 0xFF,
                payload[10] & 0xFF);
    }

    @Override
    public String toString() {
        return "status=" + status
                + " keyOn=" + (keyStateKnown ? Boolean.toString(keyOn) : "unknown")
                + " odometer=" + odometer
                + " stopDuration=" + stopDuration
                + " maxSpeed=" + maxSpeed
                + " currentSpeed=" + currentSpeed;
    }
}
