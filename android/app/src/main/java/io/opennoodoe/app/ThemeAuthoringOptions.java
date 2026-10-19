package io.opennoodoe.app;

public final class ThemeAuthoringOptions {
    public final int accentColor;
    public final int speedBarType;
    public final boolean showPeriod;
    public final boolean showWeekday;

    public ThemeAuthoringOptions(int accentColor, int speedBarType,
            boolean showPeriod, boolean showWeekday) {
        this.accentColor = accentColor;
        this.speedBarType = Math.max(1, Math.min(4, speedBarType));
        this.showPeriod = showPeriod;
        this.showWeekday = showWeekday;
    }
}
