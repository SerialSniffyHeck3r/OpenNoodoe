package io.opennoodoe.app.protocol;

import java.util.Locale;

public final class DeviceInfo {
    public final int status;
    public final int firmwareMajor;
    public final int firmwareMinor;
    public final int protocolMajor;
    public final int protocolMinor;
    public final int hardwareVersion;
    public final String mac;
    public final int bootMajor;
    public final int bootMinor;
    public final int resourceMajor;
    public final int resourceMinor;
    public final int[] supportedLanguageBits;
    public final int resourceId;
    public final int languagePackId;
    public final String model;
    public final int maxSpeed;
    public final String bikeSeries;
    public final int bikeType;
    public final int pin;
    public final int motorSeries;
    public final String pcba;
    public final int defaultDashboard;
    public final boolean modern;

    private DeviceInfo(int status, int firmwareMajor, int firmwareMinor,
            int protocolMajor, int protocolMinor, int hardwareVersion, String mac,
            int bootMajor, int bootMinor, int resourceMajor, int resourceMinor,
            int[] supportedLanguageBits, int resourceId, int languagePackId, String model,
            int maxSpeed, String bikeSeries, int bikeType, int pin,
            int motorSeries, String pcba, int defaultDashboard, boolean modern) {
        this.status = status;
        this.firmwareMajor = firmwareMajor;
        this.firmwareMinor = firmwareMinor;
        this.protocolMajor = protocolMajor;
        this.protocolMinor = protocolMinor;
        this.hardwareVersion = hardwareVersion;
        this.mac = mac;
        this.bootMajor = bootMajor;
        this.bootMinor = bootMinor;
        this.resourceMajor = resourceMajor;
        this.resourceMinor = resourceMinor;
        this.supportedLanguageBits = supportedLanguageBits.clone();
        this.resourceId = resourceId;
        this.languagePackId = languagePackId;
        this.model = model;
        this.maxSpeed = maxSpeed;
        this.bikeSeries = bikeSeries;
        this.bikeType = bikeType;
        this.pin = pin;
        this.motorSeries = motorSeries;
        this.pcba = pcba;
        this.defaultDashboard = defaultDashboard;
        this.modern = modern;
    }

    public static DeviceInfo fromRawBootstrap(byte[] body) {
        ByteCodec.require(body, 0, 12);
        int packed = body[4] & 0xFF;
        return new DeviceInfo(-1,
                ByteCodec.u16le(body, 0), ByteCodec.u16le(body, 2),
                packed >>> 4, packed & 0x0F, body[5] & 0xFF,
                ByteCodec.mac(body, 6),
                body.length >= 20 ? ByteCodec.u16le(body, 12) : -1,
                body.length >= 20 ? ByteCodec.u16le(body, 14) : -1,
                body.length >= 20 ? ByteCodec.u16le(body, 16) : -1,
                body.length >= 20 ? ByteCodec.u16le(body, 18) : -1,
                languageBits(body, 20), -1, -1,
                "", -1, "", -1, -1, -1, "", -1, false);
    }

    public static DeviceInfo fromFramedReply(byte[] payload) {
        ByteCodec.require(payload, 0, 82);
        int packed = payload[6] & 0xFF;
        return new DeviceInfo(ByteCodec.u16le(payload, 0),
                ByteCodec.u16le(payload, 2), ByteCodec.u16le(payload, 4),
                packed >>> 4, packed & 0x0F, payload[7] & 0xFF,
                ByteCodec.mac(payload, 8),
                ByteCodec.u16le(payload, 14), ByteCodec.u16le(payload, 16),
                ByteCodec.u16le(payload, 18), ByteCodec.u16le(payload, 20),
                languageBits(payload, 22), payload[38] & 0xFF, payload[39] & 0xFF,
                ByteCodec.ascii(payload, 40, 10), ByteCodec.u16le(payload, 50),
                ByteCodec.ascii(payload, 52, 18), payload[70] & 0xFF,
                ByteCodec.u16le(payload, 71), ByteCodec.u16le(payload, 73),
                ByteCodec.ascii(payload, 75, 6), payload[81] & 0xFF, true);
    }

    public boolean supportsFramedProtocol() {
        return protocolMajor >= 1 || (protocolMajor == 0 && (hardwareVersion != 0 || firmwareMajor >= 4));
    }

    public String protocolFamilyLabel() {
        if (protocolMajor == 0 && protocolMinor == 0) {
            return supportsFramedProtocol() ? "SR1.5" : "SR1.0";
        }
        if (protocolMajor >= 2) {
            return "SR2.x";
        }
        return "UNKNOWN";
    }

    private static int[] languageBits(byte[] value, int offset) {
        int[] bits = new int[16];
        if (value.length < offset + bits.length) {
            return bits;
        }
        for (int i = 0; i < bits.length; i++) {
            bits[i] = value[offset + i] & 0xFF;
        }
        return bits;
    }

    @Override
    public String toString() {
        String base = String.format(Locale.US,
                "FW %d.%d | protocol %d.%d | HW %d | MAC %s | boot %d.%d | resource %d.%d",
                firmwareMajor, firmwareMinor, protocolMajor, protocolMinor, hardwareVersion,
                mac, bootMajor, bootMinor, resourceMajor, resourceMinor);
        if (!modern) {
            return base;
        }
        return base + String.format(Locale.US,
                "\nstatus=%d | resourceId=%d | languagePack=%d | model=%s | max=%d | series=%s | type=%d | PIN=%d | motor=%d | PCBA=%s | dashboard=%d",
                status, resourceId, languagePackId, model, maxSpeed, bikeSeries, bikeType,
                pin, motorSeries, pcba, defaultDashboard);
    }
}
