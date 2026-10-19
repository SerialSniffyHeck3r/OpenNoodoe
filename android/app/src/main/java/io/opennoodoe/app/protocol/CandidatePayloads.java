package io.opennoodoe.app.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class CandidatePayloads {
    private CandidatePayloads() {
    }

    public static byte[] appNotification(String appId, String appName, String notification) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeString16(output, appId, StandardCharsets.US_ASCII);
        writeString16(output, appName, StandardCharsets.UTF_8);
        writeString16(output, notification, StandardCharsets.UTF_8);
        return output.toByteArray();
    }

    public static byte[] call(int status, String caller) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(status & 0xFF);
        writeString16(output, caller, StandardCharsets.UTF_8);
        return output.toByteArray();
    }

    public static byte[] sms(String caller, String message) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeString16(output, caller, StandardCharsets.UTF_8);
        writeString16(output, message, StandardCharsets.UTF_8);
        return output.toByteArray();
    }

    public static byte[] weather(String location, int aqi, int currentTemp, int condition,
            int[] forecastTemps, int[] forecastConditions) {
        if (forecastTemps == null || forecastConditions == null
                || forecastTemps.length != 3 || forecastConditions.length != 3) {
            throw new IllegalArgumentException("weather requires three forecast entries");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] name = bytes(location, StandardCharsets.UTF_8);
        if (name.length > 32) {
            throw new IllegalArgumentException("location exceeds 32 UTF-8 bytes");
        }
        requireUnsigned16(aqi, "AQI");
        requireSigned16(currentTemp, "current Fahrenheit temperature");
        requireWeatherCondition(condition, "current condition");
        for (int i = 0; i < 3; i++) {
            requireSigned16(forecastTemps[i], "forecast Fahrenheit temperature " + (i + 1));
            requireWeatherCondition(forecastConditions[i], "forecast condition " + (i + 1));
        }
        output.write(name.length);
        output.write(name, 0, name.length);
        for (int i = name.length; i < 32; i++) {
            output.write(0);
        }
        writeU16(output, aqi);
        writeU16(output, currentTemp);
        writeU16(output, condition);
        for (int i = 0; i < 3; i++) {
            writeU16(output, forecastTemps[i]);
            writeU16(output, forecastConditions[i]);
        }
        return output.toByteArray();
    }

    public static byte[] preferences(int distanceUnit, int temperatureUnit, int timeFormat,
            boolean breathingLight, int brightness, int defaultDashboard, int language,
            int shutdown, String username, int notifyOnNavigation) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(distanceUnit & 0xFF);
        output.write(temperatureUnit & 0xFF);
        output.write(timeFormat & 0xFF);
        output.write(breathingLight ? 1 : 0);
        output.write(brightness & 0xFF);
        output.write(defaultDashboard & 0xFF);
        output.write(language & 0xFF);
        output.write(shutdown & 0xFF);
        byte[] encoded = bytes(username, StandardCharsets.UTF_8);
        if (encoded.length > 58) {
            throw new IllegalArgumentException("username exceeds 58 UTF-8 bytes");
        }
        if (encoded.length == 0) {
            output.write(0xFF);
        } else {
            output.write(encoded.length);
            output.write(encoded, 0, encoded.length);
        }
        output.write(notifyOnNavigation & 0xFF);
        return output.toByteArray();
    }

    public static byte[] navigation(long distance, int blockCount, int icon,
            int nextTurnFileId, int currentRoadFileId, int nextRoadFileId,
            int overallFileId, int bestFitFileId, int bestFitX, int bestFitY,
            int map2dFileId, int map2dX, int map2dY, int nextTurnX, int nextTurnY,
            boolean leftDriving, boolean nightMode, int speedLimit, int speedLimitUnit,
            boolean nearCamera, int cameraDistance, int angle, int iconAfterTurn) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeU32(output, distance);
        writeU16(output, blockCount);
        output.write(icon & 0xFF);
        writeU16(output, nextTurnFileId);
        writeU16(output, currentRoadFileId);
        writeU16(output, nextRoadFileId);
        writeU16(output, overallFileId);
        writeU16(output, bestFitFileId);
        writeU16(output, bestFitX);
        writeU16(output, bestFitY);
        writeU16(output, map2dFileId);
        writeU16(output, map2dX);
        writeU16(output, map2dY);
        writeU16(output, nextTurnX);
        writeU16(output, nextTurnY);
        output.write(leftDriving ? 1 : 0);
        output.write(nightMode ? 1 : 0);
        writeU16(output, speedLimit);
        output.write(speedLimitUnit & 0xFF);
        output.write(nearCamera ? 1 : 0);
        writeU16(output, cameraDistance);
        writeU16(output, angle);
        output.write(iconAfterTurn & 0xFF);
        return output.toByteArray();
    }

    public static byte[] poi(int type, int x, int y, int placeId) {
        if (type < 1 || type > 9) {
            throw new IllegalArgumentException("POI type must be 1..9");
        }
        requireSigned16(x, "POI X");
        requireSigned16(y, "POI Y");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(type);
        writeU16(output, x);
        writeU16(output, y);
        writeU32(output, type <= 3 ? placeId : 0);
        return output.toByteArray();
    }

    public static byte[] groupMember(int memberId, int x, int y) {
        if (memberId < 0 || memberId > 64) {
            throw new IllegalArgumentException("member ID must be 0..64");
        }
        requireSigned16(x, "member X");
        requireSigned16(y, "member Y");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeU16(output, memberId);
        writeU16(output, x);
        writeU16(output, y);
        return output.toByteArray();
    }

    private static byte[] bytes(String value, Charset charset) {
        return value == null ? new byte[0] : value.getBytes(charset);
    }

    private static void requireSigned16(int value, String label) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must fit signed 16-bit");
        }
    }

    private static void requireUnsigned16(int value, String label) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(label + " must fit unsigned 16-bit");
        }
    }

    private static void requireWeatherCondition(int value, String label) {
        if (value < 0 || value > 8) {
            throw new IllegalArgumentException(label + " must be 0..8");
        }
    }

    private static void writeString16(ByteArrayOutputStream output, String value,
            Charset charset) {
        byte[] encoded = bytes(value, charset);
        if (encoded.length > 0x7FFF) {
            throw new IllegalArgumentException("string is too long for Noodoe payload");
        }
        writeU16(output, encoded.length);
        output.write(encoded, 0, encoded.length);
    }

    private static void writeU16(ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
    }

    private static void writeU32(ByteArrayOutputStream output, long value) {
        output.write((int) value & 0xFF);
        output.write((int) (value >>> 8) & 0xFF);
        output.write((int) (value >>> 16) & 0xFF);
        output.write((int) (value >>> 24) & 0xFF);
    }
}
