package io.opennoodoe.app.protocol;

import java.util.Arrays;

public final class SequenceFrame {
    public static final int HEADER_SIZE = 9;
    public static final int MAX_FRAME = 11_844;
    public static final int MAX_PAYLOAD = 11_834;
    public static final int CONTROL_ACK = 0x40;
    public static final int CONTROL_RESET = 0x10;

    private final int control;
    private final int packetIndex;
    private final int ackIndex;
    private final int session;
    private final byte[] payload;

    public SequenceFrame(int control, int packetIndex, int ackIndex, int session, byte[] payload) {
        this.control = control & 0xFF;
        this.packetIndex = packetIndex & 0xFF;
        this.ackIndex = ackIndex & 0xFF;
        this.session = session & 0xFF;
        this.payload = payload == null ? new byte[0] : payload.clone();
        if (this.payload.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("sequence payload too large");
        }
    }

    public int getControl() {
        return control;
    }

    public boolean isAck() {
        return (control & CONTROL_ACK) != 0;
    }

    public int getPacketIndex() {
        return packetIndex;
    }

    public int getAckIndex() {
        return ackIndex;
    }

    public int getSession() {
        return session;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public byte[] encode() {
        int total = payload.length == 0 ? HEADER_SIZE : HEADER_SIZE + payload.length + 1;
        byte[] encoded = new byte[total];
        encoded[0] = 0x5A;
        encoded[1] = (byte) 0xFF;
        ByteCodec.putU16le(encoded, 2, total);
        encoded[4] = (byte) control;
        encoded[5] = (byte) packetIndex;
        encoded[6] = (byte) ackIndex;
        encoded[7] = (byte) session;
        if (payload.length > 0) {
            System.arraycopy(payload, 0, encoded, HEADER_SIZE, payload.length);
            encoded[encoded.length - 1] = checksum(payload);
        }
        return encoded;
    }

    public static SequenceFrame decode(byte[] encoded) {
        if (encoded.length < HEADER_SIZE || (encoded[0] & 0xFF) != 0x5A
                || (encoded[1] & 0xFF) != 0xFF) {
            throw new IllegalArgumentException("invalid sequence header");
        }
        int length = ByteCodec.u16le(encoded, 2);
        if (length != encoded.length || length > MAX_FRAME) {
            throw new IllegalArgumentException("invalid sequence length: " + length);
        }
        int control = encoded[4] & 0xFF;
        if ((control & ~(CONTROL_ACK | CONTROL_RESET)) != 0) {
            throw new IllegalArgumentException("invalid sequence control");
        }
        int session = encoded[7] & 0xFF;
        if (session > 1) {
            throw new IllegalArgumentException("invalid sequence session: " + session);
        }
        byte[] payload = new byte[0];
        if (length > HEADER_SIZE) {
            payload = Arrays.copyOfRange(encoded, HEADER_SIZE, length - 1);
            if (encoded[length - 1] != checksum(payload)) {
                throw new IllegalArgumentException("sequence checksum mismatch");
            }
        }
        return new SequenceFrame(control, encoded[5] & 0xFF,
                encoded[6] & 0xFF, session, payload);
    }

    public static byte checksum(byte[] payload) {
        int sum = 0;
        for (byte value : payload) {
            sum = (sum + (value & 0xFF)) & 0xFF;
        }
        return (byte) ((-sum) & 0xFF);
    }
}
