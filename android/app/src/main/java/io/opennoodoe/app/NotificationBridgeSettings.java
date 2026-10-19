package io.opennoodoe.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class NotificationBridgeSettings {
    private static final String PREF_CALLS = "notification_calls";
    private static final String PREF_MESSAGES = "notification_messages";
    private static final String PREF_APP_FILTER_CONFIGURED = "notification_app_filter_configured";
    private static final String PREF_APP_PACKAGES = "notification_app_packages";
    private static final String PREF_PRIVACY_MODE = "notification_privacy_mode";

    private NotificationBridgeSettings() {
    }

    public static boolean callsEnabled(Context context) {
        return preferences(context).getBoolean(PREF_CALLS, true);
    }

    public static boolean messagesEnabled(Context context) {
        return preferences(context).getBoolean(PREF_MESSAGES, true);
    }

    public static void setCategoryOptions(Context context, boolean calls, boolean messages) {
        preferences(context).edit()
                .putBoolean(PREF_CALLS, calls)
                .putBoolean(PREF_MESSAGES, messages)
                .apply();
    }

    public static boolean appFilterConfigured(Context context) {
        return preferences(context).getBoolean(PREF_APP_FILTER_CONFIGURED, false);
    }

    public static Set<String> selectedPackages(Context context) {
        Set<String> values = preferences(context).getStringSet(PREF_APP_PACKAGES,
                Collections.emptySet());
        return new HashSet<>(values == null ? Collections.emptySet() : values);
    }

    public static void setSelectedPackages(Context context, Set<String> packages) {
        preferences(context).edit()
                .putBoolean(PREF_APP_FILTER_CONFIGURED, true)
                .putStringSet(PREF_APP_PACKAGES, new HashSet<>(packages))
                .apply();
    }

    public static boolean allowsApp(Context context, String packageName) {
        return !appFilterConfigured(context) || selectedPackages(context).contains(packageName);
    }

    public static boolean privacyMode(Context context) {
        return preferences(context).getBoolean(PREF_PRIVACY_MODE, false);
    }

    public static void setPrivacyMode(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(PREF_PRIVACY_MODE, enabled).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(NoodoeService.PREFS, Context.MODE_PRIVATE);
    }
}
