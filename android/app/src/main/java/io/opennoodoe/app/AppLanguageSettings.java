package io.opennoodoe.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

public final class AppLanguageSettings {
    public static final String SYSTEM = "system";
    public static final String ENGLISH = "en";
    public static final String TRADITIONAL_CHINESE = "zh-TW";
    public static final String SIMPLIFIED_CHINESE = "zh-CN";
    public static final String KOREAN = "ko";
    public static final String JAPANESE = "ja";

    private static final String PREFS = "opennoodoe_ui";
    private static final String KEY_LANGUAGE = "language";
    private static final String[] SUPPORTED = {
            SYSTEM, ENGLISH, TRADITIONAL_CHINESE, SIMPLIFIED_CHINESE, KOREAN, JAPANESE
    };

    private AppLanguageSettings() {}

    public static Context wrap(Context context) {
        Locale locale = Locale.forLanguageTag(resolvedTag(context));
        Configuration configuration = new Configuration(
                context.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return context.createConfigurationContext(configuration);
    }

    public static String tag(Context context) {
        String value = preferences(context).getString(KEY_LANGUAGE, SYSTEM);
        for (String supported : SUPPORTED) {
            if (supported.equals(value)) return value;
        }
        return SYSTEM;
    }

    public static int position(Context context) {
        String selected = tag(context);
        for (int i = 0; i < SUPPORTED.length; i++) {
            if (SUPPORTED[i].equals(selected)) return i;
        }
        return 0;
    }

    public static String tagAt(int position) {
        return SUPPORTED[Math.max(0, Math.min(position, SUPPORTED.length - 1))];
    }

    public static boolean set(Context context, String tag) {
        if (tag(context).equals(tag)) return false;
        preferences(context).edit().putString(KEY_LANGUAGE, tag).apply();
        return true;
    }

    private static String resolvedTag(Context context) {
        String selected = tag(context);
        if (!SYSTEM.equals(selected)) return selected;

        Configuration configuration = context.getResources().getConfiguration();
        Locale system = BuildVersionLocale.first(configuration);
        String language = system.getLanguage();
        if (Locale.KOREAN.getLanguage().equals(language)) return KOREAN;
        if (Locale.JAPANESE.getLanguage().equals(language)) return JAPANESE;
        if (Locale.CHINESE.getLanguage().equals(language)) {
            String country = system.getCountry();
            return "TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country)
                    || "MO".equalsIgnoreCase(country)
                    ? TRADITIONAL_CHINESE : SIMPLIFIED_CHINESE;
        }
        return ENGLISH;
    }

    private static final class BuildVersionLocale {
        private BuildVersionLocale() {}

        static Locale first(Configuration configuration) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                return configuration.getLocales().get(0);
            }
            return configuration.locale;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
