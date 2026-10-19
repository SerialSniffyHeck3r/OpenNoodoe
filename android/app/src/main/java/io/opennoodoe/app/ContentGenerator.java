package io.opennoodoe.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ContentGenerator {
    public static final class GeneratedFile {
        public final byte[] data;
        public final byte[] md5;
        public final String name;

        GeneratedFile(byte[] data, String extension) {
            this.data = data;
            this.md5 = md5(data);
            this.name = hex(md5) + extension;
        }

        private GeneratedFile(byte[] data, byte[] expectedMd5, String targetName) {
            this.data = data.clone();
            this.md5 = expectedMd5.clone();
            this.name = targetName;
        }

        public static GeneratedFile verified(byte[] data, byte[] expectedMd5, String targetName) {
            byte[] actual = md5(data);
            if (!java.util.Arrays.equals(actual, expectedMd5)) {
                throw new IllegalArgumentException("MD5 mismatch for " + targetName);
            }
            return new GeneratedFile(data, expectedMd5, targetName == null ? "" : targetName);
        }

        public static GeneratedFile raw(byte[] data) {
            return new GeneratedFile(data, md5(data), "");
        }

        static GeneratedFile manifestIdentified(byte[] data, byte[] manifestMd5,
                String targetName) {
            return new GeneratedFile(data, manifestMd5, targetName);
        }
    }

    public static final class GeneratedBundle {
        public final byte[] contentId;
        public final List<GeneratedFile> files;

        GeneratedBundle(byte[] contentId, List<GeneratedFile> files) {
            this.contentId = contentId;
            this.files = files;
        }
    }

    public static final class NavigationRoadFiles {
        public final GeneratedFile currentRoad;
        public final GeneratedFile nextRoad;

        NavigationRoadFiles(GeneratedFile currentRoad, GeneratedFile nextRoad) {
            this.currentRoad = currentRoad;
            this.nextRoad = nextRoad;
        }
    }

    private ContentGenerator() {
    }

    public static GeneratedFile gallery(Context context, Uri source, int slot) throws IOException {
        if (slot < 0 || slot > 5) {
            throw new IllegalArgumentException("gallery slot must be 0..5");
        }
        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            bitmap = BitmapFactory.decodeStream(input);
        }
        if (bitmap == null) {
            throw new IOException("selected image could not be decoded");
        }
        Bitmap square = centerCrop(bitmap, 480, 480);
        byte[] jpeg = jpeg(square, 80);
        byte[] data = new byte[jpeg.length + 1];
        System.arraycopy(jpeg, 0, data, 0, jpeg.length);
        data[data.length - 1] = (byte) slot;
        bitmap.recycle();
        if (square != bitmap) {
            square.recycle();
        }
        return new GeneratedFile(data, ".jpg");
    }

    public static GeneratedFile navigation(String currentRoad, String nextRoad, int distance,
            boolean nightMode, int color) {
        int background = nightMode ? Color.rgb(20, 25, 29) : Color.rgb(235, 239, 238);
        int foreground = nightMode ? Color.WHITE : Color.rgb(22, 29, 28);
        Bitmap bitmap = Bitmap.createBitmap(480, 480, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(background);
        Paint road = paint(Color.rgb(42, 156, 112), 38, Paint.Style.STROKE);
        road.setStrokeCap(Paint.Cap.ROUND);
        Path path = new Path();
        path.moveTo(240, 500);
        path.cubicTo(230, 350, 310, 285, 310, 150);
        path.lineTo(385, 75);
        canvas.drawPath(path, road);
        Paint arrow = paint(color, 1, Paint.Style.FILL);
        Path head = new Path();
        head.moveTo(385, 45);
        head.lineTo(350, 110);
        head.lineTo(420, 95);
        head.close();
        canvas.drawPath(head, arrow);
        drawText(canvas, currentRoad, 24, 42, 36, foreground, false);
        drawText(canvas, nextRoad, 24, 94, 28, foreground, false);
        drawText(canvas, distance + " m", 24, 145, 25, Color.rgb(42, 156, 112), true);
        byte[] data = jpegUnder(bitmap, 40_960);
        bitmap.recycle();
        return new GeneratedFile(data, ".jpg");
    }

    public static NavigationRoadFiles navigationRoads(String currentRoad, String nextRoad,
            boolean nightMode) {
        return new NavigationRoadFiles(
                navigationRoadText(currentRoad, 251, 77, 30, 24,
                        nightMode ? Color.WHITE : Color.rgb(100, 100, 100)),
                navigationRoadText(nextRoad, 265, 92, 36, 24,
                        nightMode ? Color.WHITE : Color.BLACK));
    }

    private static GeneratedFile navigationRoadText(String value, int width, int height,
            float maximumTextSize, float minimumTextSize, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        Paint textPaint = paint(color, 1, Paint.Style.FILL);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setTextAlign(Paint.Align.CENTER);

        String text = value == null ? "" : value.trim();
        String[] lines = splitRoadText(text);
        float textSize = maximumTextSize;
        while (textSize > minimumTextSize) {
            textPaint.setTextSize(textSize);
            float widest = 0;
            for (String line : lines) {
                widest = Math.max(widest, textPaint.measureText(line));
            }
            if (widest <= width - 8) {
                break;
            }
            textSize -= 1;
        }
        textPaint.setTextSize(textSize);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float lineHeight = metrics.descent - metrics.ascent;
        float totalHeight = lineHeight * lines.length;
        float baseline = ((height - totalHeight) / 2.0f) - metrics.ascent;
        for (String line : lines) {
            canvas.drawText(line, width / 2.0f, baseline, textPaint);
            baseline += lineHeight;
        }

        try {
            byte[] data = removeEmptyPngIdatChunks(png(bitmap));
            return new GeneratedFile(data, ".png");
        } catch (IOException error) {
            throw new IllegalStateException("road PNG generation failed", error);
        } finally {
            bitmap.recycle();
        }
    }

    private static String[] splitRoadText(String value) {
        int parenthesis = value.indexOf('(', 1);
        if (parenthesis > 0) {
            return new String[]{value.substring(0, parenthesis), value.substring(parenthesis)};
        }
        return new String[]{value};
    }

    public static GeneratedFile groupAvatar(Context context, Uri source) throws IOException {
        Bitmap original;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            original = BitmapFactory.decodeStream(input);
        }
        if (original == null) {
            throw new IOException("selected member image could not be decoded");
        }

        Bitmap scaled = Bitmap.createScaledBitmap(original, 60, 60, true);
        Bitmap avatar = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(avatar);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawBitmap(scaled, 0, 0, null);
        Paint mask = paint(Color.WHITE, 1, Paint.Style.FILL);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawOval(new RectF(0, 0, 60, 60), mask);
        mask.setXfermode(null);

        byte[] png = removeEmptyPngIdatChunks(png(avatar));
        original.recycle();
        if (scaled != original) {
            scaled.recycle();
        }
        avatar.recycle();
        return GeneratedFile.raw(png);
    }

    public static GeneratedBundle creation(String title, String subtitle, int color) {
        Bitmap bitmap = Bitmap.createBitmap(480, 480, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(238, 241, 240));
        Paint band = paint(color, 1, Paint.Style.FILL);
        canvas.drawRect(0, 0, 480, 170, band);
        drawText(canvas, title, 28, 98, 42, Color.WHITE, true);
        drawText(canvas, subtitle, 28, 250, 28, Color.rgb(30, 38, 36), false);
        drawText(canvas, "OpenNoodoe test", 28, 420, 18, Color.rgb(80, 92, 89), false);
        GeneratedFile image = new GeneratedFile(jpeg(bitmap, 80), ".jpg");
        bitmap.recycle();
        try {
            JSONObject widget = new JSONObject();
            widget.put("name", "BackgroundWidget");
            widget.put("imageType", "single");
            widget.put("images", new JSONArray().put(image.name));
            JSONObject configuration = new JSONObject();
            configuration.put("widgets", new JSONArray().put(widget));
            JSONObject root = new JSONObject();
            root.put("files", new JSONArray().put(image.name));
            root.put("configuration", configuration);
            GeneratedFile config = new GeneratedFile(
                    root.toString().getBytes(StandardCharsets.UTF_8), ".cfg");
            List<GeneratedFile> files = new ArrayList<>();
            files.add(config);
            files.add(image);
            return new GeneratedBundle(config.md5, officialCreationOrder(files));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static GeneratedBundle aroundMeV516(String title, String subtitle, int color) {
        return aroundMeV516(title, subtitle, color, true);
    }

    public static GeneratedBundle aroundMeStaticV516(String title, String subtitle, int color) {
        return aroundMeV516(title, subtitle, color, false);
    }

    private static GeneratedBundle aroundMeV516(String title, String subtitle, int color,
            boolean includeRadarWidget) {
        Bitmap bitmap = Bitmap.createBitmap(480, 480, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(238, 241, 240));
        canvas.drawCircle(240, 258, 174, paint(Color.rgb(207, 220, 216), 2, Paint.Style.STROKE));
        canvas.drawCircle(240, 258, 112, paint(Color.rgb(180, 202, 195), 2, Paint.Style.STROKE));
        canvas.drawLine(240, 74, 240, 442, paint(Color.rgb(190, 207, 202), 2, Paint.Style.STROKE));
        canvas.drawLine(56, 258, 424, 258, paint(Color.rgb(190, 207, 202), 2, Paint.Style.STROKE));
        canvas.drawCircle(240, 258, 9, paint(color, 1, Paint.Style.FILL));
        drawText(canvas, title, 24, 44, 30, Color.rgb(25, 34, 32), true);
        drawText(canvas, subtitle, 24, 474, 18, Color.rgb(72, 88, 83), false);
        GeneratedFile image = new GeneratedFile(jpeg(bitmap, 80), ".jpg");
        bitmap.recycle();

        try {
            JSONObject background = new JSONObject();
            background.put("name", "BackgroundWidget");
            background.put("imageType", "single");
            background.put("images", new JSONArray().put(image.name));

            JSONObject radar = new JSONObject();
            radar.put("name", "RadarWidget");
            radar.put("centerColor", colorHex(color));
            radar.put("shadowColor", "#25313A");

            JSONObject locations = new JSONObject();
            locations.put("name", "LocationsWidget");
            locations.put("poiTypes", new JSONArray()
                    .put("kymco_station").put("convenience_store").put("gas_station")
                    .put("home").put("work").put("favorite")
                    .put("location1").put("location2").put("location3"));
            locations.put("poiColors", new JSONArray()
                    .put("#E53935").put("#43A047").put("#F9A825")
                    .put("#1E88E5").put("#8E24AA").put("#FB8C00")
                    .put("#00ACC1").put("#6D4C41").put("#546E7A"));
            JSONArray customLocations = new JSONArray();
            for (int index = 0; index < 3; index++) {
                JSONObject location = new JSONObject();
                location.put("lat", Float.floatToRawIntBits(Float.NaN));
                location.put("lng", Float.floatToRawIntBits(Float.NaN));
                customLocations.put(location);
            }
            locations.put("customLocations", customLocations);

            JSONObject configuration = new JSONObject();
            JSONArray widgets = new JSONArray().put(background);
            if (includeRadarWidget) {
                widgets.put(radar);
            }
            widgets.put(locations);
            configuration.put("widgets", widgets);
            JSONObject root = new JSONObject();
            root.put("files", new JSONArray().put(image.name));
            root.put("configuration", configuration);
            GeneratedFile config = new GeneratedFile(
                    root.toString().getBytes(StandardCharsets.UTF_8), ".cfg");
            List<GeneratedFile> files = new ArrayList<>();
            files.add(config);
            files.add(image);
            return new GeneratedBundle(config.md5, officialCreationOrder(files));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static GeneratedBundle weatherForecastV516(int color) {
        Bitmap bitmap = Bitmap.createBitmap(480, 480, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(28, 36, 42));
        drawText(canvas, "3-DAY FORECAST", 84, 105, 32, Color.WHITE, true);
        drawText(canvas, "ForecastWidget diagnostic", 96, 420, 20,
                Color.rgb(185, 205, 199), false);
        GeneratedFile image = new GeneratedFile(jpeg(bitmap, 80), ".jpg");
        bitmap.recycle();

        try {
            JSONObject background = new JSONObject();
            background.put("name", "BackgroundWidget");
            background.put("imageType", "single");
            background.put("images", new JSONArray().put(image.name));

            JSONObject forecast = new JSONObject();
            forecast.put("name", "ForecastWidget");
            forecast.put("centerX", 240);
            forecast.put("centerY", 285);
            forecast.put("weatherIconColor", colorHex(color));
            forecast.put("weatherIconType", 1);
            forecast.put("type", "bottom");

            JSONObject configuration = new JSONObject();
            configuration.put("widgets", new JSONArray().put(background).put(forecast));
            JSONObject root = new JSONObject();
            root.put("files", new JSONArray().put(image.name));
            root.put("configuration", configuration);
            GeneratedFile config = new GeneratedFile(
                    root.toString().getBytes(StandardCharsets.UTF_8), ".cfg");
            List<GeneratedFile> files = new ArrayList<>();
            files.add(config);
            files.add(image);
            return new GeneratedBundle(config.md5, officialCreationOrder(files));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static GeneratedBundle referenceCreation(Context context, String assetDirectory)
            throws IOException {
        String[] names = context.getAssets().list(assetDirectory);
        if (names == null || names.length == 0) {
            throw new IOException("empty reference creation: " + assetDirectory);
        }
        Arrays.sort(names, (left, right) -> {
            boolean leftConfig = left.endsWith(".cfg");
            boolean rightConfig = right.endsWith(".cfg");
            if (leftConfig != rightConfig) {
                return leftConfig ? -1 : 1;
            }
            return left.compareTo(right);
        });
        Map<String, GeneratedFile> fileMap = new HashMap<>();
        byte[] contentId = null;
        for (String name : names) {
            byte[] data;
            try (InputStream input = context.getAssets().open(assetDirectory + "/" + name);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8_192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                }
                data = output.toByteArray();
            }
            int extensionAt = name.lastIndexOf('.');
            if (extensionAt <= 0) {
                throw new IOException("reference asset has no extension: " + name);
            }
            String identity = name.substring(0, extensionAt);
            if (!identity.matches("[0-9a-fA-F]{32}")) {
                throw new IOException("reference asset has invalid identity: " + name);
            }
            GeneratedFile file = GeneratedFile.manifestIdentified(
                    data, hexToBytes(identity), name);
            fileMap.put(name.endsWith(".cfg") ? "creation" : identity, file);
            if (name.endsWith(".cfg")) {
                if (contentId != null) {
                    throw new IOException("multiple reference cfg files: " + assetDirectory);
                }
                contentId = file.md5;
            }
        }
        if (contentId == null) {
            throw new IOException("reference creation has no cfg: " + assetDirectory);
        }
        // The official transmitter rebuilds this HashMap and iterates entrySet().
        // Preserve that behavior instead of forcing the cfg to be file 1.
        return new GeneratedBundle(contentId, new ArrayList<>(fileMap.values()));
    }

    public static GeneratedBundle customTheme(Context context, GeneratedBundle template,
            Uri backgroundSource, ThemeAuthoringOptions options) throws IOException {
        Map<String, byte[]> sourceFiles = new HashMap<>();
        for (GeneratedFile file : template.files) sourceFiles.put(file.name, file.data);
        String assetDirectory = "user-selected template";
        String configName = null;
        for (String name : sourceFiles.keySet()) {
            if (name.endsWith(".cfg")) {
                if (configName != null) {
                    throw new IOException("multiple template cfg files: " + assetDirectory);
                }
                configName = name;
            }
        }
        if (configName == null) {
            throw new IOException("template has no cfg: " + assetDirectory);
        }

        Bitmap source;
        try (InputStream input = context.getContentResolver().openInputStream(backgroundSource)) {
            source = BitmapFactory.decodeStream(input);
        }
        if (source == null) {
            throw new IOException("theme background could not be decoded");
        }
        Bitmap square = centerCrop(source, 480, 480);
        GeneratedFile background = new GeneratedFile(jpeg(square, 92), ".jpg");
        source.recycle();
        if (square != source) {
            square.recycle();
        }

        try {
            JSONObject root = new JSONObject(new String(sourceFiles.get(configName),
                    StandardCharsets.UTF_8));
            JSONObject configuration = root.getJSONObject("configuration");
            JSONArray widgets = configuration.getJSONArray("widgets");
            String oldBackground = null;
            Set<String> removedAssets = new HashSet<>();
            Map<String, GeneratedFile> recoloredAssets = new HashMap<>();
            JSONArray retainedWidgets = new JSONArray();
            for (int index = 0; index < widgets.length(); index++) {
                JSONObject widget = widgets.getJSONObject(index);
                String widgetName = widget.optString("name");
                if ("BackgroundWidget".equals(widgetName)) {
                    JSONArray images = widget.optJSONArray("images");
                    if (images != null && images.length() > 0) {
                        oldBackground = images.optString(0, null);
                    }
                    widget.put("imageType", "single");
                    widget.put("images", new JSONArray().put(background.name));
                } else if ("DateWidget".equals(widgetName)
                        && ((!options.showPeriod && "a".equals(widget.optString("format")))
                        || (!options.showWeekday && "EEEE".equals(widget.optString("format"))))) {
                    collectWidgetImages(widget, removedAssets);
                    continue;
                } else if ("SpeedBarWidget".equals(widgetName)) {
                    widget.put("speedBarColor", String.format(Locale.US, "#ff%06x",
                            options.accentColor & 0xFFFFFF));
                    widget.put("speedBarType", options.speedBarType);
                } else if ("ClockDigitWidget".equals(widgetName)
                        || "DateWidget".equals(widgetName)) {
                    recolorWidgetImages(sourceFiles, widget, options.accentColor,
                            recoloredAssets);
                }
                retainedWidgets.put(widget);
            }
            if (oldBackground == null) {
                throw new IOException("template has no BackgroundWidget image");
            }
            configuration.put("widgets", retainedWidgets);

            Set<String> retainedAssets = new HashSet<>();
            for (int index = 0; index < retainedWidgets.length(); index++) {
                collectWidgetImages(retainedWidgets.getJSONObject(index), retainedAssets);
            }

            JSONArray declared = root.getJSONArray("files");
            JSONArray rewritten = new JSONArray();
            boolean replaced = false;
            for (int index = 0; index < declared.length(); index++) {
                String name = declared.getString(index);
                if (oldBackground.equals(name)) {
                    rewritten.put(background.name);
                    replaced = true;
                } else if (recoloredAssets.containsKey(name)) {
                    rewritten.put(recoloredAssets.get(name).name);
                } else if (removedAssets.contains(name) && !retainedAssets.contains(name)) {
                    continue;
                } else {
                    rewritten.put(name);
                }
            }
            if (!replaced) {
                throw new IOException("BackgroundWidget image is absent from files[]");
            }
            root.put("files", rewritten);

            List<GeneratedFile> files = new ArrayList<>();
            GeneratedFile config = new GeneratedFile(
                    root.toString().getBytes(StandardCharsets.UTF_8), ".cfg");
            files.add(config);
            for (int index = 0; index < rewritten.length(); index++) {
                String name = rewritten.getString(index);
                if (background.name.equals(name)) {
                    files.add(background);
                    continue;
                }
                GeneratedFile recolored = findGeneratedFileByName(recoloredAssets, name);
                if (recolored != null) {
                    files.add(recolored);
                    continue;
                }
                byte[] data = sourceFiles.get(name);
                if (data == null) {
                    throw new IOException("template is missing declared file: " + name);
                }
                int extensionAt = name.lastIndexOf('.');
                if (extensionAt != 32 || !name.substring(0, 32).matches("[0-9a-fA-F]{32}")) {
                    throw new IOException("template file has invalid identity: " + name);
                }
                files.add(GeneratedFile.verified(data,
                        hexToBytes(name.substring(0, extensionAt)), name));
            }
            return new GeneratedBundle(config.md5, officialCreationOrder(files));
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("theme template rewrite failed", error);
        }
    }

    private static void recolorWidgetImages(Map<String, byte[]> sourceFiles, JSONObject widget,
            int color, Map<String, GeneratedFile> replacements) throws Exception {
        String[] keys = {"images", "digitImages", "monthImages", "periodImages",
                "punctuationImages", "weekdayImages"};
        for (String key : keys) {
            JSONArray images = widget.optJSONArray(key);
            if (images == null) continue;
            JSONArray rewritten = new JSONArray();
            for (int index = 0; index < images.length(); index++) {
                String oldName = images.getString(index);
                GeneratedFile replacement = replacements.get(oldName);
                if (replacement == null) {
                    byte[] data = sourceFiles.get(oldName);
                    if (data == null) throw new IOException("template image is missing: " + oldName);
                    replacement = new GeneratedFile(recolorPng(data, color), ".png");
                    replacements.put(oldName, replacement);
                }
                rewritten.put(replacement.name);
            }
            widget.put(key, rewritten);
        }
    }

    private static byte[] recolorPng(byte[] data, int color) throws IOException {
        Bitmap source = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (source == null) throw new IOException("clock glyph is not a decodable PNG");
        Bitmap tinted = Bitmap.createBitmap(source.getWidth(), source.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tinted);
        canvas.drawBitmap(source, 0, 0, null);
        canvas.drawColor(Color.rgb(Color.red(color), Color.green(color), Color.blue(color)),
                PorterDuff.Mode.SRC_IN);
        source.recycle();
        try {
            return removeEmptyPngIdatChunks(png(tinted));
        } finally {
            tinted.recycle();
        }
    }

    private static void collectWidgetImages(JSONObject widget, Set<String> output) {
        String[] keys = {"images", "digitImages", "monthImages", "periodImages",
                "punctuationImages", "weekdayImages", "positions"};
        for (String key : keys) {
            JSONArray values = widget.optJSONArray(key);
            if (values == null) continue;
            for (int index = 0; index < values.length(); index++) {
                Object value = values.opt(index);
                if (value instanceof String) output.add((String) value);
            }
        }
    }

    private static GeneratedFile findGeneratedFileByName(
            Map<String, GeneratedFile> replacements, String name) {
        for (GeneratedFile file : replacements.values()) {
            if (file.name.equals(name)) return file;
        }
        return null;
    }

    public static GeneratedBundle storedCreation(File directory) throws IOException {
        File[] entries = directory.listFiles(File::isFile);
        if (entries == null || entries.length == 0) {
            throw new IOException("stored theme is empty: " + directory);
        }
        List<GeneratedFile> files = new ArrayList<>();
        byte[] contentId = null;
        for (File entry : entries) {
            String name = entry.getName();
            if (!(name.endsWith(".cfg") || name.endsWith(".jpg") || name.endsWith(".png"))) {
                continue;
            }
            int extensionAt = name.lastIndexOf('.');
            if (extensionAt != 32 || !name.substring(0, 32).matches("[0-9a-fA-F]{32}")) {
                throw new IOException("stored theme has invalid filename: " + name);
            }
            byte[] data;
            try (InputStream input = new java.io.FileInputStream(entry);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8_192];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                data = output.toByteArray();
            }
            GeneratedFile file = GeneratedFile.verified(data,
                    hexToBytes(name.substring(0, extensionAt)), name);
            files.add(file);
            if (name.endsWith(".cfg")) {
                if (contentId != null) throw new IOException("stored theme has multiple cfg files");
                contentId = file.md5;
            }
        }
        if (contentId == null) throw new IOException("stored theme has no cfg");
        return new GeneratedBundle(contentId, officialCreationOrder(files));
    }

    public static GeneratedBundle importTransmissionBundle(Context context, Uri source)
            throws IOException {
        Map<String, byte[]> imported = new HashMap<>();
        long total = 0;
        try (InputStream raw = context.getContentResolver().openInputStream(source);
                ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            byte[] buffer = new byte[8_192];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String normalized = entry.getName().replace('\\', '/');
                String name = normalized.substring(normalized.lastIndexOf('/') + 1);
                if (!(name.endsWith(".cfg") || name.endsWith(".jpg")
                        || name.endsWith(".png"))) continue;
                if (imported.containsKey(name)) {
                    throw new IOException("duplicate bundle filename: " + name);
                }
                int extensionAt = name.lastIndexOf('.');
                if (extensionAt != 32 || !name.substring(0, 32).matches("[0-9a-fA-F]{32}")) {
                    throw new IOException("bundle filename is not an MD5 identity: " + name);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int count;
                while ((count = zip.read(buffer)) >= 0) {
                    total += count;
                    if (total > 64L * 1024L * 1024L) {
                        throw new IOException("bundle expands beyond 64 MiB");
                    }
                    output.write(buffer, 0, count);
                }
                imported.put(name, output.toByteArray());
                if (imported.size() > 256) throw new IOException("bundle has more than 256 files");
            }
        }
        String configName = null;
        for (String name : imported.keySet()) {
            if (!name.endsWith(".cfg")) continue;
            if (configName != null) throw new IOException("bundle has multiple cfg files");
            configName = name;
        }
        if (configName == null) {
            throw new IOException("no transmission cfg found; appData Creation ZIPs must be "
                    + "compiled by the official bundler first");
        }

        List<GeneratedFile> files = new ArrayList<>();
        GeneratedFile config = verifiedImportedFile(configName, imported.get(configName));
        files.add(config);
        try {
            JSONObject root = new JSONObject(new String(config.data, StandardCharsets.UTF_8));
            JSONArray declarations = root.getJSONArray("files");
            Set<String> declaredNames = new HashSet<>();
            for (int index = 0; index < declarations.length(); index++) {
                String name = declarations.getString(index);
                if (!declaredNames.add(name)) throw new IOException("duplicate files[] entry: " + name);
                byte[] data = imported.get(name);
                if (data == null) throw new IOException("bundle is missing files[] entry: " + name);
                files.add(verifiedImportedFile(name, data));
            }
            for (String name : imported.keySet()) {
                if (!name.equals(configName) && !declaredNames.contains(name)) {
                    throw new IOException("undeclared payload in bundle: " + name);
                }
            }
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("invalid transmission cfg", error);
        }
        return new GeneratedBundle(config.md5, officialCreationOrder(files));
    }

    public static int detectCreationLocation(GeneratedBundle bundle) throws IOException {
        GeneratedFile configuration = null;
        for (GeneratedFile file : bundle.files) {
            if (!file.name.endsWith(".cfg")) continue;
            if (configuration != null) throw new IOException("bundle has multiple cfg files");
            configuration = file;
        }
        if (configuration == null) throw new IOException("bundle has no cfg file");
        Set<Integer> locations = new HashSet<>();
        try {
            JSONArray widgets = new JSONObject(new String(configuration.data,
                    StandardCharsets.UTF_8)).getJSONObject("configuration")
                    .getJSONArray("widgets");
            for (int index = 0; index < widgets.length(); index++) {
                String name = widgets.getJSONObject(index).optString("name");
                if ("ClockDigitWidget".equals(name) || "WatchHandWidget".equals(name)) {
                    locations.add(NoodoeService.LOCATION_CLOCK);
                } else if ("SpeedDigitWidget".equals(name) || "SpeedNeedleWidget".equals(name)
                        || "SpeedBarWidget".equals(name) || "OdometerWidget".equals(name)
                        || "BatteryWidget".equals(name)) {
                    locations.add(NoodoeService.LOCATION_SPEEDOMETER);
                } else if ("WeatherConditionWidget".equals(name)
                        || "TemperatureWidget".equals(name) || "ForecastWidget".equals(name)
                        || "AirQualityWidget".equals(name) || "ConditionTextWidget".equals(name)
                        || "WeatherEffectsWidget".equals(name) || "LocationWidget".equals(name)) {
                    locations.add(NoodoeService.LOCATION_WEATHER);
                } else if ("RadarWidget".equals(name) || "LocationsWidget".equals(name)
                        || "DirectionRingWidget".equals(name)
                        || "DirectionTextWidget".equals(name) || "DistanceWidget".equals(name)
                        || "DistanceTextWidget".equals(name) || "EtaTextWidget".equals(name)
                        || "MembersWidget".equals(name)) {
                    locations.add(NoodoeService.LOCATION_POI);
                }
            }
        } catch (Exception error) {
            throw new IOException("cannot identify imported theme type", error);
        }
        if (locations.size() != 1) {
            throw new IOException("imported theme type is missing or ambiguous");
        }
        return locations.iterator().next();
    }

    private static GeneratedFile verifiedImportedFile(String name, byte[] data) throws IOException {
        int extensionAt = name.lastIndexOf('.');
        try {
            return GeneratedFile.verified(data,
                    hexToBytes(name.substring(0, extensionAt)), name);
        } catch (IllegalArgumentException error) {
            throw new IOException(error.getMessage(), error);
        }
    }

    private static Map<String, byte[]> readReferenceAssets(Context context, String directory)
            throws IOException {
        String[] names = context.getAssets().list(directory);
        if (names == null || names.length == 0) {
            throw new IOException("empty reference creation: " + directory);
        }
        Map<String, byte[]> result = new HashMap<>();
        for (String name : names) {
            try (InputStream input = context.getAssets().open(directory + "/" + name);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8_192];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                result.put(name, output.toByteArray());
            }
        }
        return result;
    }

    private static List<GeneratedFile> officialCreationOrder(List<GeneratedFile> files) {
        Map<String, GeneratedFile> fileMap = new HashMap<>();
        for (GeneratedFile file : files) {
            int extensionAt = file.name.lastIndexOf('.');
            String key = file.name.endsWith(".cfg")
                    ? "creation" : file.name.substring(0, extensionAt);
            fileMap.put(key, file);
        }
        return new ArrayList<>(fileMap.values());
    }

    private static byte[] hexToBytes(String value) throws IOException {
        if ((value.length() & 1) != 0) {
            throw new IOException("odd-length hex identity");
        }
        byte[] result = new byte[value.length() / 2];
        try {
            for (int i = 0; i < result.length; i++) {
                result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException error) {
            throw new IOException("invalid hex identity", error);
        }
        return result;
    }

    public static byte[] md5(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static byte[] md5(String value) {
        return md5(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String hex(byte[] data) {
        StringBuilder result = new StringBuilder(data.length * 2);
        for (byte value : data) {
            result.append(String.format(Locale.US, "%02x", value & 0xFF));
        }
        return result.toString();
    }

    private static String colorHex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    private static Bitmap centerCrop(Bitmap source, int width, int height) {
        float scale = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        int scaledWidth = Math.round(source.getWidth() * scale);
        int scaledHeight = Math.round(source.getHeight() * scale);
        Bitmap scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
        Bitmap result = Bitmap.createBitmap(scaled,
                (scaledWidth - width) / 2, (scaledHeight - height) / 2, width, height);
        Bitmap rgb565 = result.copy(Bitmap.Config.RGB_565, false);
        if (scaled != source && scaled != result) {
            scaled.recycle();
        }
        if (result != source) {
            result.recycle();
        }
        return rgb565;
    }

    private static byte[] jpegUnder(Bitmap bitmap, int maximum) {
        for (int quality = 70; quality >= 20; quality -= 5) {
            byte[] data = jpeg(bitmap, quality);
            if (data.length <= maximum) {
                return data;
            }
        }
        return jpeg(bitmap, 15);
    }

    private static byte[] jpeg(Bitmap bitmap, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            throw new IllegalStateException("JPEG compression failed");
        }
        return output.toByteArray();
    }

    private static byte[] png(Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            throw new IllegalStateException("PNG compression failed");
        }
        return output.toByteArray();
    }

    private static byte[] removeEmptyPngIdatChunks(byte[] png) throws IOException {
        if (png.length < 8) {
            throw new IOException("truncated PNG");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(png.length);
        output.write(png, 0, 8);
        int offset = 8;
        while (offset < png.length) {
            if (offset + 12 > png.length) {
                throw new IOException("truncated PNG chunk");
            }
            int length = ((png[offset] & 0xFF) << 24)
                    | ((png[offset + 1] & 0xFF) << 16)
                    | ((png[offset + 2] & 0xFF) << 8)
                    | (png[offset + 3] & 0xFF);
            if (length < 0 || offset + 12L + length > png.length) {
                throw new IOException("invalid PNG chunk length");
            }
            boolean emptyIdat = length == 0
                    && png[offset + 4] == 'I' && png[offset + 5] == 'D'
                    && png[offset + 6] == 'A' && png[offset + 7] == 'T';
            int chunkSize = length + 12;
            if (!emptyIdat) {
                output.write(png, offset, chunkSize);
            }
            offset += chunkSize;
        }
        return output.toByteArray();
    }

    private static Paint paint(int color, float width, Paint.Style style) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        paint.setStyle(style);
        return paint;
    }

    private static void drawText(Canvas canvas, String value, float x, float y, float size,
            int color, boolean bold) {
        Paint paint = paint(color, 1, Paint.Style.FILL);
        paint.setTextSize(size);
        paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD
                : android.graphics.Typeface.DEFAULT);
        String text = value == null ? "" : value;
        while (paint.measureText(text) > 425 && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        canvas.drawText(text, x, y, paint);
    }
}
