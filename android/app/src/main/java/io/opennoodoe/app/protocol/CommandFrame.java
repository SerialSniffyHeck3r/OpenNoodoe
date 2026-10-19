package io.opennoodoe.app.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CommandFrame {
    public static final int READ = 0x01;
    public static final int WRITE = 0x02;
    public static final int WRITE_MULTIPLE = 0x06;
    public static final int REPLY = 0x08;
    public static final int NOTIFY = 0x10;
    public static final int HEADER_SIZE = 10;

    private final int commandId;
    private final int attribute;
    private final byte[] payload;

    public CommandFrame(int commandId, int attribute, byte[] payload) {
        this.commandId = commandId & 0xFF;
        this.attribute = attribute & 0xFF;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    public int getCommandId() {
        return commandId;
    }

    public int getAttribute() {
        return attribute;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public byte[] encode() {
        byte[] encoded = new byte[HEADER_SIZE + payload.length];
        encoded[0] = (byte) 0xA5;
        encoded[1] = 0x5A;
        encoded[2] = (byte) commandId;
        encoded[3] = (byte) attribute;
        ByteCodec.putU32le(encoded, 6, payload.length);
        System.arraycopy(payload, 0, encoded, HEADER_SIZE, payload.length);
        return encoded;
    }

    public static List<CommandFrame> decodeMany(byte[] data) {
        List<CommandFrame> frames = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            while (offset + 1 < data.length
                    && ((data[offset] & 0xFF) != 0xA5 || (data[offset + 1] & 0xFF) != 0x5A)) {
                offset++;
            }
            if (data.length - offset < HEADER_SIZE) {
                break;
            }
            long payloadLength = ByteCodec.u32le(data, offset + 6);
            if (payloadLength > SequenceFrame.MAX_PAYLOAD) {
                throw new IllegalArgumentException("oversized command payload: " + payloadLength);
            }
            int total = HEADER_SIZE + (int) payloadLength;
            if (data.length - offset < total) {
                throw new IllegalArgumentException("truncated command frame");
            }
            frames.add(new CommandFrame(
                    data[offset + 2] & 0xFF,
                    data[offset + 3] & 0xFF,
                    Arrays.copyOfRange(data, offset + HEADER_SIZE, offset + total)));
            offset += total;
        }
        return frames;
    }

    public static byte[] concatenate(List<CommandFrame> frames) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (CommandFrame frame : frames) {
            byte[] encoded = frame.encode();
            output.write(encoded, 0, encoded.length);
        }
        return output.toByteArray();
    }
}
