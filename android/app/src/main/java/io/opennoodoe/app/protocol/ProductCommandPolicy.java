package io.opennoodoe.app.protocol;

/** Outbound command boundary for the local dashboard utility. */
public final class ProductCommandPolicy {
    private ProductCommandPolicy() {}

    public static boolean supportsTheme(int location) {
        return location == 0x0200 || location == 0x0400;
    }

    public static boolean allows(int command, int attribute, byte[] payload,
            int activeTask, int activeType, int activeLocation) {
        if (payload == null) return false;
        switch (command) {
            case 0x05: // Device information
            case 0x0C: // Riding information / ODO
            case 0x11: // Production information: READ only
            case 0x16: // Optional meter profile
                return attribute == CommandFrame.READ && payload.length == 0;
            case 0x02: // Time / mobile status
            case 0x04: // Vehicle preferences
            case 0x0E: // Welcome light
                return attribute == CommandFrame.WRITE;
            case 0x0A:
                return attribute == CommandFrame.WRITE && payload.length == 27
                        && ByteCodec.u16le(payload, 2) == FileTransferPayloads.TYPE_FILE
                        && supportsContent(ByteCodec.u16le(payload, 4));
            case 0x0B:
            case 0x0D:
                return attribute == CommandFrame.WRITE && payload.length >= 6
                        && activeTask >= 0 && ByteCodec.u16le(payload, 0) == activeTask
                        && activeType == FileTransferPayloads.TYPE_FILE
                        && supportsContent(activeLocation);
            default:
                return false;
        }
    }

    private static boolean supportsContent(int location) {
        return supportsTheme(location) || location == 0x0600;
    }
}
