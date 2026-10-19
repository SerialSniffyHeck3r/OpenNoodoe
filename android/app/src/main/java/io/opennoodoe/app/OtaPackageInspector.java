package io.opennoodoe.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import io.opennoodoe.app.protocol.ByteCodec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class OtaPackageInspector {
    private static final int MAX_PACKAGE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_RESOURCE_FILES = 10_000;

    public static final class FirmwarePackage {
        public final String displayName;
        public final byte[] data;
        public final byte[] contentId;
        public final String sha256;
        public final int major;
        public final int minor;

        private FirmwarePackage(String displayName, byte[] data, int major, int minor) {
            this.displayName = displayName;
            this.data = data;
            this.major = major;
            this.minor = minor;
            this.contentId = new byte[4];
            ByteCodec.putU16le(contentId, 0, major);
            ByteCodec.putU16le(contentId, 2, minor);
            this.sha256 = digest("SHA-256", data);
        }
    }

    public static final class ResourcePackage {
        public final String displayName;
        public final List<ContentGenerator.GeneratedFile> files;
        public final byte[] contentId;
        public final String sha256;
        public final int major;
        public final int minor;
        public final int resourceId;
        public final int languagePackId;
        public final long totalBytes;

        private ResourcePackage(String displayName, List<ContentGenerator.GeneratedFile> files,
                byte[] contentId, String sha256, int major, int minor, int resourceId,
                int languagePackId, long totalBytes) {
            this.displayName = displayName;
            this.files = files;
            this.contentId = contentId;
            this.sha256 = sha256;
            this.major = major;
            this.minor = minor;
            this.resourceId = resourceId;
            this.languagePackId = languagePackId;
            this.totalBytes = totalBytes;
        }
    }

    private OtaPackageInspector() {
    }

    public static FirmwarePackage inspectFirmware(Context context, Uri source,
            int major, int minor) throws Exception {
        requireU16("firmware major", major);
        requireU16("firmware minor", minor);
        if (major == 0 && minor == 0) {
            throw new IllegalArgumentException("firmware target version 0.0 is not allowed");
        }
        String name = displayName(context, source);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".bin")) {
            throw new IllegalArgumentException("firmware file must have a .bin extension");
        }
        byte[] data = readUri(context, source);
        if (data.length < 256) {
            throw new IllegalArgumentException("firmware image is implausibly small");
        }
        long initialStack = ByteCodec.u32le(data, 0);
        long resetVector = ByteCodec.u32le(data, 4);
        if ((initialStack & 0xFFF00000L) != 0x20000000L || (resetVector & 1) == 0) {
            throw new IllegalArgumentException("firmware does not have a plausible Cortex-M vector table");
        }
        return new FirmwarePackage(name, data, major, minor);
    }

    public static ResourcePackage inspectResource(Context context, Uri source) throws Exception {
        String name = displayName(context, source);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("resource package must have a .zip extension");
        }
        byte[] archive = readUri(context, source);
        Map<String, byte[]> entries = unzip(archive);
        byte[] configBytes = entries.get("resource_config.json");
        if (configBytes == null) {
            throw new IllegalArgumentException("resource_config.json is missing");
        }
        JSONObject config = new JSONObject(new String(configBytes, java.nio.charset.StandardCharsets.UTF_8));
        int major = checkedU16(config.getInt("majorVersion"), "resource major");
        int minor = checkedU16(config.getInt("minorVersion"), "resource minor");
        int resourceId = checkedU8(config.getInt("ResourceID"), "resource ID");
        int languagePackId = checkedU8(config.getInt("LangpackID"), "language pack ID");
        JSONArray allFiles = config.getJSONArray("allFiles");
        if (allFiles.length() == 0 || allFiles.length() > MAX_RESOURCE_FILES) {
            throw new IllegalArgumentException("invalid resource file count " + allFiles.length());
        }

        List<ContentGenerator.GeneratedFile> files = new ArrayList<>(allFiles.length());
        long total = 0;
        for (int i = 0; i < allFiles.length(); i++) {
            JSONObject item = allFiles.getJSONObject(i);
            String path = safeTargetPath(item.getString("path"));
            String md5 = item.getString("MD5").toLowerCase(Locale.ROOT);
            byte[] expected = decodeHex(md5, 16);
            String archiveName = "resource_config.json".equals(path) ? path : md5;
            byte[] data = entries.get(archiveName);
            if (data == null) {
                throw new IllegalArgumentException("archive entry missing for " + path);
            }
            files.add("resource_config.json".equals(path)
                    ? ContentGenerator.GeneratedFile.manifestIdentified(data, expected, path)
                    : ContentGenerator.GeneratedFile.verified(data, expected, path));
            total += data.length;
            if (total > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("resource payload exceeds protocol limit");
            }
        }
        byte[] contentId = new byte[6];
        ByteCodec.putU16le(contentId, 0, major);
        ByteCodec.putU16le(contentId, 2, minor);
        contentId[4] = (byte) resourceId;
        contentId[5] = (byte) languagePackId;
        return new ResourcePackage(name, files, contentId, digest("SHA-256", archive),
                major, minor, resourceId, languagePackId, total);
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.contains("/..")) {
                    throw new IllegalArgumentException("unsafe ZIP entry " + name);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[16_384];
                int count;
                while ((count = zip.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                    total += count;
                    if (total > MAX_PACKAGE_BYTES) {
                        throw new IllegalArgumentException("expanded resource package is too large");
                    }
                }
                if (result.put(name, output.toByteArray()) != null) {
                    throw new IllegalArgumentException("duplicate ZIP entry " + name);
                }
            }
        }
        return result;
    }

    private static byte[] readUri(Context context, Uri source) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) {
                throw new IOException("selected file cannot be opened");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16_384];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
                if (output.size() > MAX_PACKAGE_BYTES) {
                    throw new IOException("selected package exceeds 64 MiB safety limit");
                }
            }
            return output.toByteArray();
        }
    }

    private static String displayName(Context context, Uri source) {
        try (Cursor cursor = context.getContentResolver().query(source,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        String segment = source.getLastPathSegment();
        return segment == null ? "selected-package" : segment;
    }

    private static String safeTargetPath(String path) {
        String value = path.replace('\\', '/');
        if (value.isEmpty() || value.startsWith("/") || value.contains("../")
                || value.contains("/..") || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("unsafe resource target path " + path);
        }
        return value;
    }

    private static byte[] decodeHex(String value, int expectedLength) {
        if (value.length() != expectedLength * 2 || !value.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("invalid hexadecimal value " + value);
        }
        byte[] result = new byte[expectedLength];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String digest(String algorithm, byte[] data) {
        try {
            return ContentGenerator.hex(MessageDigest.getInstance(algorithm).digest(data));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int checkedU8(int value, String name) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " must be 0..255");
        }
        return value;
    }

    private static int checkedU16(int value, String name) {
        requireU16(name, value);
        return value;
    }

    private static void requireU16(String name, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(name + " must be 0..65535");
        }
    }
}
