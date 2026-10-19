package io.opennoodoe.app.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SequenceStreamDecoder {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public synchronized List<SequenceFrame> feed(byte[] bytes, int length) {
        buffer.write(bytes, 0, length);
        byte[] data = buffer.toByteArray();
        List<SequenceFrame> frames = new ArrayList<>();
        int offset = 0;

        while (offset + 1 < data.length) {
            if ((data[offset] & 0xFF) != 0x5A || (data[offset + 1] & 0xFF) != 0xFF) {
                offset++;
                continue;
            }
            if (data.length - offset < SequenceFrame.HEADER_SIZE) {
                break;
            }
            int frameLength = ByteCodec.u16le(data, offset + 2);
            if (frameLength < SequenceFrame.HEADER_SIZE || frameLength > SequenceFrame.MAX_FRAME) {
                offset += 2;
                continue;
            }
            if (data.length - offset < frameLength) {
                break;
            }
            frames.add(SequenceFrame.decode(Arrays.copyOfRange(data, offset, offset + frameLength)));
            offset += frameLength;
        }

        buffer.reset();
        if (offset < data.length) {
            buffer.write(data, offset, data.length - offset);
        }
        return frames;
    }
}
