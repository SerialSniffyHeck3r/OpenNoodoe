package io.opennoodoe.app;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class NoodoeNotificationListener extends NotificationListenerService {
    private static final String PRIVATE_NOTIFICATION = "NEW NOTIFICATION";
    public interface MediaGateListener {
        void onMediaGateChanged(boolean available);
    }

    private static final byte[] MEDIA_GATE_ARTIST = {
            0x19, 0x0F, 0x0E, 0x13, 0x1F, 0x7A, 0x09, 0x0E, 0x08, 0x1F, 0x1F, 0x0E
    };
    private static final Set<MediaGateListener> GATE_LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile boolean mediaGateAvailable;

    private long lastSentAt;
    private String lastKey = "";
    private String activeCaller = "";
    private MediaSessionManager mediaSessionManager;
    private final List<MediaController> mediaControllers = new ArrayList<>();
    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            refreshMediaGate();
        }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            controllers -> refreshMediaControllers(controllers);

    public static void addMediaGateListener(MediaGateListener listener) {
        GATE_LISTENERS.add(listener);
        listener.onMediaGateChanged(mediaGateAvailable);
    }

    public static void removeMediaGateListener(MediaGateListener listener) {
        GATE_LISTENERS.remove(listener);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (mediaSessionManager == null) {
            publishMediaGate(false);
            return;
        }
        ComponentName listener = new ComponentName(this, NoodoeNotificationListener.class);
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, listener);
            refreshMediaControllers(mediaSessionManager.getActiveSessions(listener));
        } catch (SecurityException error) {
            mediaSessionManager = null;
            publishMediaGate(false);
        }
    }

    @Override
    public void onListenerDisconnected() {
        clearMediaControllers();
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
        }
        mediaSessionManager = null;
        publishMediaGate(false);
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        clearMediaControllers();
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
        }
        mediaSessionManager = null;
        publishMediaGate(false);
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        refreshMediaGate();
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) {
            return;
        }
        String category = sbn.getNotification().category;
        if ((sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0
                && !Notification.CATEGORY_CALL.equals(category)) return;
        CharSequence title = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
        String message = (title == null ? "" : title.toString())
                + (title == null || text == null ? "" : ": ")
                + (text == null ? "" : text.toString());
        String key = sbn.getPackageName() + "\n" + message;
        long now = System.currentTimeMillis();
        if (message.isEmpty() || (key.equals(lastKey) && now - lastSentAt < 2_000)) {
            return;
        }
        lastKey = key;
        lastSentAt = now;
        Intent intent;
        boolean privateMode = NotificationBridgeSettings.privacyMode(this);
        if (Notification.CATEGORY_CALL.equals(category)) {
            if (!NotificationBridgeSettings.callsEnabled(this)) return;
            activeCaller = privateMode ? PRIVATE_NOTIFICATION
                    : (title == null ? "" : title.toString());
            intent = new Intent(this, NoodoeService.class)
                    .setAction(NoodoeService.ACTION_FORWARD_CALL)
                    .putExtra(NoodoeService.EXTRA_CALL_STATUS, 1)
                    .putExtra(NoodoeService.EXTRA_CALLER, activeCaller);
        } else if (Notification.CATEGORY_MESSAGE.equals(category)
                && sbn.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this))) {
            if (!NotificationBridgeSettings.messagesEnabled(this)) return;
            intent = new Intent(this, NoodoeService.class)
                    .setAction(NoodoeService.ACTION_FORWARD_SMS)
                    .putExtra(NoodoeService.EXTRA_APP_NAME,
                            privateMode ? PRIVATE_NOTIFICATION
                                    : (title == null ? applicationName(sbn.getPackageName())
                                            : title.toString()))
                    .putExtra(NoodoeService.EXTRA_TEXT,
                            privateMode ? "" : (text == null ? "" : text.toString()));
        } else {
            if (!NotificationBridgeSettings.allowsApp(this, sbn.getPackageName())) return;
            intent = new Intent(this, NoodoeService.class)
                    .setAction(NoodoeService.ACTION_FORWARD_NOTIFICATION)
                    .putExtra(NoodoeService.EXTRA_APP_ID,
                            privateMode ? "" : sbn.getPackageName())
                    .putExtra(NoodoeService.EXTRA_APP_NAME,
                            privateMode ? PRIVATE_NOTIFICATION : applicationName(sbn.getPackageName()))
                    .putExtra(NoodoeService.EXTRA_TEXT,
                            privateMode ? "" : message);
        }
        startBridgeService(intent);
    }

    private void startBridgeService(Intent intent) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        refreshMediaGate();
        if (sbn != null && Notification.CATEGORY_CALL.equals(sbn.getNotification().category)
                && NotificationBridgeSettings.callsEnabled(this)) {
            Intent intent = new Intent(this, NoodoeService.class)
                    .setAction(NoodoeService.ACTION_FORWARD_CALL)
                    .putExtra(NoodoeService.EXTRA_CALL_STATUS, 0)
                    .putExtra(NoodoeService.EXTRA_CALLER, activeCaller);
            activeCaller = "";
            startBridgeService(intent);
        }
    }

    private String applicationName(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (RuntimeException | android.content.pm.PackageManager.NameNotFoundException error) {
            return packageName;
        }
    }

    private void refreshMediaControllers(List<MediaController> controllers) {
        clearMediaControllers();
        List<MediaController> current = controllers == null ? Collections.emptyList() : controllers;
        for (MediaController controller : current) {
            if (controller == null) {
                continue;
            }
            controller.registerCallback(mediaCallback);
            mediaControllers.add(controller);
        }
        refreshMediaGate();
    }

    private void clearMediaControllers() {
        for (MediaController controller : mediaControllers) {
            controller.unregisterCallback(mediaCallback);
        }
        mediaControllers.clear();
    }

    private void refreshMediaGate() {
        boolean available = false;
        for (MediaController controller : mediaControllers) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null && (matches(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))
                    || matches(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)))) {
                available = true;
                break;
            }
        }
        publishMediaGate(available);
    }

    private static boolean matches(String value) {
        return value != null
                && value.toUpperCase(Locale.ROOT).contains(mediaGateArtist());
    }

    private static String mediaGateArtist() {
        char[] decoded = new char[MEDIA_GATE_ARTIST.length];
        for (int i = 0; i < MEDIA_GATE_ARTIST.length; i++) {
            decoded[i] = (char) (MEDIA_GATE_ARTIST[i] ^ 0x5A);
        }
        return new String(decoded);
    }

    private static void publishMediaGate(boolean available) {
        if (mediaGateAvailable == available) {
            return;
        }
        mediaGateAvailable = available;
        for (MediaGateListener listener : GATE_LISTENERS) {
            listener.onMediaGateChanged(available);
        }
    }
}
