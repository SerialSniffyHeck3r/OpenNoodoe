package io.opennoodoe.app;

import java.util.Locale;

/** Offline mirror of model-code lookups needed after the original service disappears. */
public final class VehicleModelCatalog {
    private VehicleModelCatalog() {
    }

    public static String displayName(String rawCode) {
        String code = normalize(rawCode);
        switch (code) {
            case "SAA1AA":
            case "SAA1AC":
                return "AK550";
            case "SBA1BA":
                return "AK550 PREMIUM";
            case "SBA1CA":
                return "CV3";
            case "SA35AA":
            case "SA35AC":
                return "KRV180";
            case "SR30JD":
                return "RACING S 150";
            default:
                if (code.startsWith("SK80") || code.startsWith("D6")) {
                    return "XCITING 400";
                }
                return code.isEmpty() ? "KYMCO NOODOE" : code;
        }
    }

    public static String normalize(String rawCode) {
        if (rawCode == null) return "";
        String value = rawCode.trim().toUpperCase(Locale.ROOT);
        int region = value.indexOf('(');
        return region < 0 ? value : value.substring(0, region).trim();
    }
}
