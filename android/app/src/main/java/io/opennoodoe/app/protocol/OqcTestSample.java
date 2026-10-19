package io.opennoodoe.app.protocol;

import java.util.ArrayList;
import java.util.List;

public final class OqcTestSample {
    public static final int POWER_ON = 1;
    public static final int POWER_OFF = 1 << 1;
    public static final int BUTTON_UP = 1 << 2;
    public static final int BUTTON_ENTER = 1 << 3;
    public static final int BUTTON_DOWN = 1 << 4;
    public static final int MFI_ENABLED = 1 << 5;

    public final int flags;
    public final long lightSensor;

    private OqcTestSample(int flags, long lightSensor) {
        this.flags = flags;
        this.lightSensor = lightSensor;
    }

    public static OqcTestSample fromPayload(byte[] payload) {
        ByteCodec.require(payload, 0, 5);
        return new OqcTestSample(payload[0] & 0xFF, ByteCodec.u32le(payload, 1));
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public static String activeNames(int flags) {
        List<String> names = new ArrayList<>();
        addIfSet(names, flags, POWER_ON, "POWER_ON");
        addIfSet(names, flags, POWER_OFF, "POWER_OFF");
        addIfSet(names, flags, BUTTON_UP, "UP");
        addIfSet(names, flags, BUTTON_ENTER, "ENTER");
        addIfSet(names, flags, BUTTON_DOWN, "DOWN");
        addIfSet(names, flags, MFI_ENABLED, "MFI");
        return names.isEmpty() ? "none" : String.join(" | ", names);
    }

    private static void addIfSet(List<String> names, int flags, int flag, String name) {
        if ((flags & flag) != 0) {
            names.add(name);
        }
    }
}
