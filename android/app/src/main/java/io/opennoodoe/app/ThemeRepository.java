package io.opennoodoe.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ThemeRepository {
    public static final String BUILTIN_CLOCK = "builtin:clock";
    public static final String BUILTIN_SPEED = "builtin:speed";

    public static final class Entry {
        public final String id;
        public final int location;
        public final boolean preservedReference;
        public final long createdAt;
        public final int fileCount;
        public final String title;
        public final String source;

        Entry(String id, int location, boolean preservedReference, long createdAt, int fileCount,
                String title, String source) {
            this.id = id;
            this.location = location;
            this.preservedReference = preservedReference;
            this.createdAt = createdAt;
            this.fileCount = fileCount;
            this.title = title;
            this.source = source;
        }
    }

    private ThemeRepository() {}

    public static List<Entry> list(Context context) {
        List<Entry> result = new ArrayList<>();
        File[] directories = root(context).listFiles(File::isDirectory);
        if (directories == null) return result;
        Arrays.sort(directories, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        for (File directory : directories) {
            try {
                JSONObject manifest = readJson(new File(directory, "manifest.json"));
                if (!io.opennoodoe.app.protocol.ProductCommandPolicy.supportsTheme(manifest.getInt("location"))) continue;
                result.add(new Entry("custom:" + directory.getName(),
                        manifest.getInt("location"), false,
                        manifest.optLong("createdAt", directory.lastModified()),
                        manifest.optInt("fileCount", countPayloadFiles(directory)),
                        manifest.optString("title", ""),
                        manifest.optString("source", "custom")));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static String save(Context context, int location,
            ContentGenerator.GeneratedBundle bundle) throws IOException {
        return save(context, location, bundle, "", "custom", "");
    }

    public static String save(Context context, int location,
            ContentGenerator.GeneratedBundle bundle, String title, String source,
            String archiveSha256) throws IOException {
        String kind;
        if (location == NoodoeService.LOCATION_CLOCK) kind = "clock";
        else if (location == NoodoeService.LOCATION_WEATHER) kind = "weather";
        else if (location == NoodoeService.LOCATION_SPEEDOMETER) kind = "speed";
        else if (location == NoodoeService.LOCATION_POI) kind = "around_me";
        else throw new IOException("unsupported theme location: " + location);
        String id = String.format(Locale.US, "%s-%d-%s", kind, System.currentTimeMillis(),
                ContentGenerator.hex(bundle.contentId).substring(0, 8));
        File temporary = new File(root(context), "." + id + ".tmp");
        deleteTree(temporary);
        if (!temporary.mkdirs()) throw new IOException("cannot create theme staging directory");
        try {
            for (ContentGenerator.GeneratedFile file : bundle.files) {
                try (FileOutputStream output = new FileOutputStream(new File(temporary, file.name))) {
                    output.write(file.data);
                    output.flush();
                    output.getFD().sync();
                }
            }
            JSONObject manifest = new JSONObject();
            manifest.put("schema", 1);
            manifest.put("id", id);
            manifest.put("location", location);
            manifest.put("createdAt", System.currentTimeMillis());
            manifest.put("contentId", ContentGenerator.hex(bundle.contentId));
            manifest.put("fileCount", bundle.files.size());
            manifest.put("title", title == null ? "" : title);
            manifest.put("source", source == null ? "" : source);
            if (archiveSha256 != null && !archiveSha256.isEmpty()) {
                manifest.put("archiveSha256", archiveSha256);
            }
            writeBytes(new File(temporary, "manifest.json"),
                    manifest.toString(2).getBytes(StandardCharsets.UTF_8));
            File target = new File(root(context), id);
            if (!temporary.renameTo(target)) throw new IOException("cannot commit custom theme");
            return "custom:" + id;
        } catch (Exception error) {
            deleteTree(temporary);
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("cannot save custom theme", error);
        }
    }

    public static ContentGenerator.GeneratedBundle load(Context context, String id)
            throws IOException {
        if (id == null || !id.startsWith("custom:")) throw new IOException("unknown theme id");
        String directoryName = id.substring("custom:".length());
        if (!directoryName.matches("[a-z_]+-[0-9]+-[0-9a-f]{8}")) {
            throw new IOException("invalid custom theme id");
        }
        return ContentGenerator.storedCreation(new File(root(context), directoryName));
    }

    public static int location(Context context, String id) throws IOException {
        if (id == null || !id.startsWith("custom:")) throw new IOException("unknown theme id");
        File manifest = new File(root(context), id.substring("custom:".length())
                + File.separator + "manifest.json");
        try {
            return readJson(manifest).getInt("location");
        } catch (Exception error) {
            throw new IOException("cannot read custom theme manifest", error);
        }
    }

    private static File root(Context context) {
        File directory = new File(context.getFilesDir(), "themes");
        if (!directory.isDirectory()) directory.mkdirs();
        return directory;
    }

    private static int countPayloadFiles(File directory) {
        File[] files = directory.listFiles(file -> file.isFile()
                && !"manifest.json".equals(file.getName()));
        return files == null ? 0 : files.length;
    }

    private static JSONObject readJson(File file) throws Exception {
        byte[] data;
        try (FileInputStream input = new FileInputStream(file)) {
            data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != data.length) throw new IOException("short read: " + file);
        }
        return new JSONObject(new String(data, StandardCharsets.UTF_8));
    }

    private static void writeBytes(File file, byte[] data) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
            output.flush();
            output.getFD().sync();
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
