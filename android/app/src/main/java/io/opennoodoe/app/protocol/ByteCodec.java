package io.opennoodoe.app.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ByteCodec {
    private ByteCodec() {
    }

    public static int u16le(byte[] data, int offset) {
        require(data, offset, 2);
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    public static long u32le(byte[] data, int offset) {
        require(data, offset, 4);
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    public static void putU16le(byte[] data, int offset, int value) {
        require(data, offset, 2);
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    public static void putU32le(byte[] data, int offset, long value) {
        require(data, offset, 4);
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    public static String ascii(byte[] data, int offset, int length) {
        require(data, offset, length);
        int end = offset + length;
        while (end > offset && (data[end - 1] == 0 || data[end - 1] == ' ')) {
            end--;
        }
        return new String(data, offset, end - offset, StandardCharsets.US_ASCII);
    }

    public static String mac(byte[] data, int offset) {
        require(data, offset, 6);
        return String.format(Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
                data[offset + 5] & 0xFF, data[offset + 4] & 0xFF,
                data[offset + 3] & 0xFF, data[offset + 2] & 0xFF,
                data[offset + 1] & 0xFF, data[offset] & 0xFF);
    }

    public static String hex(byte[] data) {
        StringBuilder result = new StringBuilder(data.length * 3);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            result.append(String.format(Locale.US, "%02X", data[i] & 0xFF));
        }
        return result.toString();
    }

    public static void require(byte[] data, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("truncated protocol data");
        }
    }
}
