package io.opennoodoe.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Keep the author and permission notice available inside the distributed APK. */
final class LicenseDialog {
    private LicenseDialog() { }
    static void show(Activity activity) {
        String notice;
        try (InputStream input = activity.getResources().openRawResource(R.raw.opennoodoe_license);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            notice = new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read bundled license", error);
        }
        TextView text = new TextView(activity);
        text.setText(notice);
        text.setTextSize(15);
        text.setTextIsSelectable(true);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        new AlertDialog.Builder(activity).setTitle(R.string.license_title).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }
}
