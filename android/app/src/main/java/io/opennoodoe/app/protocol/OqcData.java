package io.opennoodoe.app.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class OqcData {
    public final int status;
    public final String partialNumber;
    public final String serialNumber;
    public final String pcbaVersion;
    public final String mac;
    public final String model;
    public final int pin;
    public final long[] backlightThresholds;
    public final int maxSpeed;
    public final int language;
    public final int firmwareMajor;
    public final int firmwareMinor;
    public final String assemblyNumber;
    public final int panelVersion;
    public final int unit;
    public final int type;
    public final int languagePack;
    public final int motorSeries;
    public final int resourceId;
    public final int dashboardId;

    private OqcData(int status, String partialNumber, String serialNumber, String pcbaVersion,
            String mac, String model, int pin, int firmwareMajor, int firmwareMinor,
            long[] backlightThresholds, int maxSpeed, int language, String assemblyNumber,
            int panelVersion, int unit, int type, int languagePack, int motorSeries,
            int resourceId, int dashboardId) {
        this.status = status;
        this.partialNumber = partialNumber;
        this.serialNumber = serialNumber;
        this.pcbaVersion = pcbaVersion;
        this.mac = mac;
        this.model = model;
        this.pin = pin;
        this.backlightThresholds = backlightThresholds.clone();
        this.maxSpeed = maxSpeed;
        this.language = language;
        this.firmwareMajor = firmwareMajor;
        this.firmwareMinor = firmwareMinor;
        this.assemblyNumber = assemblyNumber;
        this.panelVersion = panelVersion;
        this.unit = unit;
        this.type = type;
        this.languagePack = languagePack;
        this.motorSeries = motorSeries;
        this.resourceId = resourceId;
        this.dashboardId = dashboardId;
    }

    public static OqcData create(String partialNumber, String serialNumber, String pcbaVersion,
            String mac, String model, int pin, long[] backlightThresholds, int maxSpeed,
            int language, int firmwareMajor, int firmwareMinor, String assemblyNumber,
            int panelVersion, int unit, int type, int languagePack, int motorSeries,
            int resourceId, int dashboardId) {
        validateAscii("partial number", partialNumber, 16);
        validateAscii("serial number", serialNumber, 18);
        validateAscii("PCBA version", pcbaVersion, 6);
        validateAscii("MAC", mac, 12);
        validateAscii("model", model, 10);
        validateAscii("assembly number", assemblyNumber, 16);
        if (backlightThresholds == null || backlightThresholds.length != 10) {
            throw new IllegalArgumentException("exactly 10 backlight thresholds are required");
        }
        for (long threshold : backlightThresholds) {
            requireU32("backlight threshold", threshold);
        }
        requireU16("PIN", pin);
        requireU16("max speed", maxSpeed);
        requireU16("language", language);
        requireU16("firmware major", firmwareMajor);
        requireU16("firmware minor", firmwareMinor);
        requireU16("panel version", panelVersion);
        requireU8("unit", unit);
        requireU8("type", type);
        requireU8("language pack", languagePack);
        requireU16("motor series", motorSeries);
        requireU8("resource ID", resourceId);
        requireU8("dashboard ID", dashboardId);
        return new OqcData(0, partialNumber, serialNumber, pcbaVersion, mac, model, pin,
                firmwareMajor, firmwareMinor, backlightThresholds, maxSpeed, language,
                assemblyNumber, panelVersion, unit, type, languagePack, motorSeries,
                resourceId, dashboardId);
    }

    public static OqcData fromReply(byte[] payload) {
        ByteCodec.require(payload, 0, 139);
        long[] thresholds = new long[10];
        for (int i = 0; i < thresholds.length; i++) {
            thresholds[i] = ByteCodec.u32le(payload, 66 + i * 4);
        }
        return new OqcData(
                ByteCodec.u16le(payload, 0),
                ByteCodec.ascii(payload, 2, 16),
                ByteCodec.ascii(payload, 18, 18),
                ByteCodec.ascii(payload, 36, 6),
                ByteCodec.ascii(payload, 42, 12),
                ByteCodec.ascii(payload, 54, 10),
                ByteCodec.u16le(payload, 64),
                ByteCodec.u16le(payload, 110),
                ByteCodec.u16le(payload, 112),
                thresholds,
                ByteCodec.u16le(payload, 106),
                ByteCodec.u16le(payload, 108),
                ByteCodec.ascii(payload, 114, 16),
                ByteCodec.u16le(payload, 130),
                payload[132] & 0xFF,
                payload[133] & 0xFF,
                payload[134] & 0xFF,
                ByteCodec.u16le(payload, 135),
                payload[137] & 0xFF,
                payload[138] & 0xFF);
    }

    public byte[] toWritePayload() {
        byte[] payload = new byte[137];
        putAscii(payload, 0, 16, partialNumber);
        putAscii(payload, 16, 18, serialNumber);
        putAscii(payload, 34, 6, pcbaVersion);
        putAscii(payload, 40, 12, mac);
        putAscii(payload, 52, 10, model);
        ByteCodec.putU16le(payload, 62, pin);
        for (int i = 0; i < backlightThresholds.length; i++) {
            ByteCodec.putU32le(payload, 64 + i * 4, backlightThresholds[i]);
        }
        ByteCodec.putU16le(payload, 104, maxSpeed);
        ByteCodec.putU16le(payload, 106, language);
        ByteCodec.putU16le(payload, 108, firmwareMajor);
        ByteCodec.putU16le(payload, 110, firmwareMinor);
        putAscii(payload, 112, 16, assemblyNumber);
        ByteCodec.putU16le(payload, 128, panelVersion);
        payload[130] = (byte) unit;
        payload[131] = (byte) type;
        payload[132] = (byte) languagePack;
        ByteCodec.putU16le(payload, 133, motorSeries);
        payload[135] = (byte) resourceId;
        payload[136] = (byte) dashboardId;
        return payload;
    }

    public boolean writableFieldsEqual(OqcData other) {
        return other != null && Arrays.equals(toWritePayload(), other.toWritePayload());
    }

    private static void putAscii(byte[] target, int offset, int length, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, target, offset, encoded.length);
    }

    private static void validateAscii(String name, String value, int maximumBytes) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        if (encoded.length > maximumBytes) {
            throw new IllegalArgumentException(name + " exceeds " + maximumBytes + " ASCII bytes");
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 0x20 || character > 0x7E) {
                throw new IllegalArgumentException(name + " must contain printable ASCII only");
            }
        }
    }

    private static void requireU8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " must be 0..255");
        }
    }

    private static void requireU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(name + " must be 0..65535");
        }
    }

    private static void requireU32(String name, long value) {
        if (value < 0 || value > 0xFFFFFFFFL) {
            throw new IllegalArgumentException(name + " must be 0..4294967295");
        }
    }

    @Override
    public String toString() {
        return "status=" + status
                + " partial=" + partialNumber
                + " serial=" + serialNumber
                + " pcba=" + pcbaVersion
                + " mac=" + mac
                + " model=" + model
                + " pin=" + pin
                + " backlight=" + java.util.Arrays.toString(backlightThresholds)
                + " maxSpeed=" + maxSpeed
                + " language=" + language
                + " firmware=" + firmwareMajor + "." + firmwareMinor
                + " assembly=" + assemblyNumber
                + " panel=" + panelVersion
                + " unit=" + unit
                + " type=" + type
                + " langPack=" + languagePack
                + " motorSeries=" + motorSeries
                + " resourceId=" + resourceId
                + " dashboardId=" + dashboardId;
    }
}
