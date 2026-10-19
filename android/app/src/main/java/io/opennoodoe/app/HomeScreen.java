package io.opennoodoe.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ArrayAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.opennoodoe.app.protocol.DeviceInfo;
import io.opennoodoe.app.protocol.OqcData;
import io.opennoodoe.app.protocol.RidingStatus;

/** Consumer-facing surface. The protocol console remains isolated in MainActivity's test mode. */
public final class HomeScreen {
    public interface Actions {
        void onConnectionPressed(boolean connected);

        void onChooseDevice();

        void onSyncClock();

        void onSetClock(long timestampMillis);

        void onWelcomeSettingsChanged(boolean enabled, int shutdownTime);






        void onChoosePhoto();

        void onSendPhoto(int slot);

        void onOpenThemeLibrary();

        void onCreateTheme();

        void onApplyPreferences(String name, int brightness, boolean breathing,
                boolean metric, boolean twentyFourHour);


        void onOpenTestMode();

        void onThemeModeChanged(int mode);

        void onLanguageChanged(String tag);
    }

    private final int BACKGROUND;
    private final int SURFACE;
    private final int INK;
    private final int MUTED;
    private final int DARK;
    private final int ACCENT;
    private final int ACCENT_DARK;
    private final int ACCENT_SOFT;
    private final int BORDER;
    private final int DANGER;

    private final Activity activity;
    private final Actions actions;
    private final FrameLayout root;
    private final List<Button> operationButtons = new ArrayList<>();

    private TextView modelNameView;
    private TextView connectionPill;
    private TextView speedHeroView;
    private TextView odometerView;
    private TextView modelCodeValueView;
    private TextView vehicleProfileValueView;
    private TextView dashboardIdentityValueView;
    private TextView productionValueView;
    private TextView hardwareValueView;
    private TextView softwareValueView;
    private TextView actionStatus;
    private TextView photoSelection;
    private Button connectionButton;
    private Switch notificationSwitch;
    private Switch callNotificationSwitch;
    private Switch messageNotificationSwitch;
    private TextView notificationAppSummary;
    private TextView privacyModeSummary;
    private ImageButton privacyModeButton;
    private final List<FrameLayout> galleryTiles = new ArrayList<>();
    private final List<ImageView> galleryImages = new ArrayList<>();
    private final List<ImageView> galleryPlaceholders = new ArrayList<>();
    private boolean[] gallerySent = new boolean[6];
    private final String[] galleryImageKeys = new String[6];
    private int selectedPhotoSlot;
    private SeekBar brightnessSeek;
    private TextView brightnessValue;
    private Switch automaticBrightness;
    private Switch welcomeLightSwitch;
    private Spinner welcomeStandbySpinner;
    private TextView welcomeStatusView;
    private boolean connected;
    private boolean updatingNotificationSwitch;
    private boolean updatingWelcomeSettings;

    public HomeScreen(Activity activity, Actions actions) {
        this.activity = activity;
        this.actions = actions;
        boolean darkMode = UiThemeSettings.isDark(activity);
        BACKGROUND = darkMode ? Color.rgb(17, 20, 19) : Color.rgb(243, 246, 244);
        SURFACE = darkMode ? Color.rgb(29, 34, 32) : Color.WHITE;
        INK = darkMode ? Color.rgb(238, 243, 240) : Color.rgb(23, 32, 29);
        MUTED = darkMode ? Color.rgb(163, 176, 170) : Color.rgb(100, 113, 107);
        DARK = darkMode ? Color.rgb(8, 11, 10) : Color.rgb(24, 34, 30);
        ACCENT = Color.rgb(0, 176, 126);
        ACCENT_DARK = darkMode ? Color.rgb(77, 226, 174) : Color.rgb(0, 103, 79);
        ACCENT_SOFT = darkMode ? Color.rgb(23, 65, 51) : Color.rgb(220, 243, 234);
        BORDER = darkMode ? Color.rgb(66, 76, 72) : Color.rgb(216, 224, 220);
        DANGER = darkMode ? Color.rgb(255, 111, 119) : Color.rgb(180, 35, 43);
        this.root = build();
    }

    public View view() {
        return root;
    }

    public void update(NoodoeService.Snapshot snapshot) {
        connected = snapshot.connected;
        modelNameView.setText(VehicleModelCatalog.displayName(snapshot.modelCode));
        RidingStatus riding = snapshot.ridingStatusData;
        boolean ridingValid = riding != null && riding.status == 0;
        if (!snapshot.connected) {
            connectionPill.setText(string(R.string.status_disconnected));
            connectionPill.setTextColor(Color.rgb(136, 154, 146));
            connectionPill.setBackground(pill(Color.rgb(42, 55, 49)));
        } else if (snapshot.keyOn == null) {
            connectionPill.setText(string(R.string.status_vehicle_waiting));
            connectionPill.setTextColor(Color.WHITE);
            connectionPill.setBackground(pill(Color.rgb(42, 55, 49)));
        } else if (snapshot.keyOn) {
            connectionPill.setText(string(R.string.status_key_on));
            connectionPill.setTextColor(Color.rgb(65, 225, 164));
            connectionPill.setBackground(pill(Color.rgb(21, 67, 50)));
        } else {
            connectionPill.setText(string(R.string.status_key_off));
            connectionPill.setTextColor(Color.WHITE);
            connectionPill.setBackground(pill(Color.rgb(42, 55, 49)));
        }
        connectionButton.setText(snapshot.connected
                ? string(R.string.action_disconnect) : string(R.string.action_connect));
        connectionButton.setBackground(snapshot.connected
                ? outlinedBackground(SURFACE, BORDER)
                : filledBackground(ACCENT));
        connectionButton.setTextColor(snapshot.connected ? INK : Color.WHITE);

        if (snapshot.odometer >= 0) {
            setOdometer(snapshot.odometer);
        } else {
            odometerView.setText("------");
        }

        setSpeed(ridingValid ? riding.currentSpeed : -1);

        updateVehicleInformation(snapshot);

        gallerySent = snapshot.gallerySent.clone();
        for (int i = 0; i < galleryTiles.size(); i++) {
            showGalleryImage(i, snapshot.galleryPaths[i]);
        }
        refreshGalleryBorders();

        updatingWelcomeSettings = true;
        welcomeLightSwitch.setChecked(snapshot.welcomeLightEnabled);
        welcomeStandbySpinner.setSelection(standbyPosition(snapshot.welcomeStandby));
        if (!snapshot.welcomeLightEnabled) {
            welcomeStatusView.setText(string(R.string.welcome_disabled));
        } else if (snapshot.welcomeStandby == WelcomeLightPolicy.SHUTDOWN_IMMEDIATELY) {
            welcomeStatusView.setText(string(R.string.welcome_immediate_disabled));
        } else {
            welcomeStatusView.setText(format(R.string.welcome_active_format,
                    snapshot.welcomeActivationCount));
        }
        updatingWelcomeSettings = false;

        boolean available = snapshot.connected && !snapshot.operationBusy;
        for (Button button : operationButtons) {
            button.setEnabled(available);
            button.setAlpha(available ? 1.0f : 0.45f);
        }
        if (snapshot.operationBusy) {
            actionStatus.setText(snapshot.operationState);
            actionStatus.setTextColor(ACCENT_DARK);
        } else if (snapshot.connected) {
            actionStatus.setText(string(R.string.status_available));
            actionStatus.setTextColor(MUTED);
        } else {
            actionStatus.setText(string(R.string.status_vehicle_disconnected));
            actionStatus.setTextColor(MUTED);
        }
    }

    public void setSelectedImage(Uri uri) {
        photoSelection.setText(uri == null
                ? format(R.string.photo_slot_selected, selectedPhotoSlot + 1)
                : format(R.string.photo_slot_ready, selectedPhotoSlot + 1));
        photoSelection.setTextColor(uri == null ? MUTED : ACCENT_DARK);
    }

    public void showActionStatus(String value, boolean error) {
        actionStatus.setText(value);
        actionStatus.setTextColor(error ? DANGER : ACCENT_DARK);
    }



    private FrameLayout build() {
        FrameLayout frame = new FrameLayout(activity);
        frame.setBackgroundColor(BACKGROUND);
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page = vertical();
        page.setBackgroundColor(BACKGROUND);

        page.addView(appBar());
        page.addView(odometerBand());
        addGap(page);
        page.addView(vehicleInformationBand());
        addGap(page);
        page.addView(quickActionsBand());
        addGap(page);
        page.addView(photoBand());
        addGap(page);
        page.addView(themeBand());
        addGap(page);
        page.addView(settingsBand());
        addGap(page);
        page.addView(deviceBand());
        page.addView(footer());

        scroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        frame.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return frame;
    }

    private View appBar() {
        LinearLayout bar = horizontal();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(16), dp(20), dp(15));
        bar.setBackgroundColor(SURFACE);
        TextView brand = text("OpenNoodoe", 23, true, INK);
        bar.addView(brand, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView beta = text("BETA", 11, true, ACCENT_DARK);
        beta.setGravity(Gravity.CENTER);
        beta.setPadding(dp(10), dp(6), dp(10), dp(6));
        beta.setBackground(pill(ACCENT_SOFT));
        bar.addView(beta);
        return bar;
    }

    private View odometerBand() {
        LinearLayout band = vertical();
        band.setPadding(dp(20), dp(20), dp(20), dp(22));
        band.setBackgroundColor(DARK);

        LinearLayout vehicleRow = horizontal();
        vehicleRow.setGravity(Gravity.CENTER_VERTICAL);
        modelNameView = text("NOODOE", 16, true, Color.WHITE);
        modelNameView.setSingleLine(true);
        modelNameView.setEllipsize(TextUtils.TruncateAt.END);
        modelNameView.setPadding(0, 0, dp(12), 0);
        vehicleRow.addView(modelNameView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        connectionPill = text(string(R.string.status_disconnected), 11, true,
                Color.rgb(136, 154, 146));
        connectionPill.setPadding(dp(10), dp(6), dp(10), dp(6));
        connectionPill.setBackground(pill(Color.rgb(42, 55, 49)));
        vehicleRow.addView(connectionPill);
        band.addView(vehicleRow);

        LinearLayout speedRow = horizontal();
        speedRow.setGravity(Gravity.CENTER_VERTICAL);
        speedRow.setPadding(0, dp(25), 0, 0);
        ImageView speedIcon = new ImageView(activity);
        speedIcon.setImageResource(R.drawable.ic_speed_rounded);
        speedIcon.setColorFilter(Color.rgb(184, 202, 194));
        speedIcon.setContentDescription(string(R.string.speed_content_description));
        speedRow.addView(speedIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout speedTextRow = horizontal();
        speedTextRow.setBaselineAligned(true);
        speedHeroView = text("---", 54, true, Color.WHITE);
        speedHeroView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        speedHeroView.setGravity(Gravity.START);
        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        speedTextRow.addView(speedHeroView, speedParams);
        TextView speedUnit = text("km/h", 15, true, Color.rgb(184, 202, 194));
        speedUnit.setPadding(dp(10), 0, 0, 0);
        speedTextRow.addView(speedUnit);
        speedTextRow.setBaselineAlignedChildIndex(0);
        LinearLayout.LayoutParams speedTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        speedTextParams.setMargins(dp(12), 0, 0, 0);
        speedRow.addView(speedTextRow, speedTextParams);
        band.addView(speedRow);

        View divider = new View(activity);
        divider.setBackgroundColor(Color.rgb(58, 73, 66));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.setMargins(0, dp(18), 0, dp(18));
        band.addView(divider, dividerParams);

        TextView label = text("ODO", 13, true, Color.rgb(184, 202, 194));
        label.setGravity(Gravity.CENTER_VERTICAL);
        odometerView = text("------", 40, true, Color.WHITE);
        odometerView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        odometerView.setGravity(Gravity.START);
        LinearLayout odometerRow = horizontal();
        odometerRow.setBaselineAligned(true);
        odometerRow.addView(label, new LinearLayout.LayoutParams(dp(38),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams odoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        odoParams.setMargins(dp(12), 0, 0, 0);
        odometerRow.addView(odometerView, odoParams);
        TextView odometerUnit = text("km", 15, true, Color.rgb(184, 202, 194));
        odometerUnit.setPadding(dp(10), 0, 0, 0);
        odometerRow.addView(odometerUnit);
        odometerRow.setBaselineAlignedChildIndex(1);
        band.addView(odometerRow);
        return band;
    }

    private View vehicleInformationBand() {
        LinearLayout band = sectionBand(string(R.string.section_vehicle_system));
        band.addView(infoGroupLabel(string(R.string.group_vehicle)));
        modelCodeValueView = addInfoRow(band, string(R.string.field_model_code), "—");
        vehicleProfileValueView = addInfoRow(band,
                string(R.string.field_vehicle_profile), "—");

        band.addView(infoGroupLabel(string(R.string.group_noodoe_unit)));
        dashboardIdentityValueView = addInfoRow(band,
                string(R.string.field_device_identifier), "—");
        productionValueView = addInfoRow(band, string(R.string.field_production_info), "—");
        hardwareValueView = addInfoRow(band, string(R.string.field_hardware), "—");

        band.addView(infoGroupLabel(string(R.string.group_software)));
        softwareValueView = addInfoRow(band, string(R.string.field_version), "—");
        return band;
    }

    private TextView infoGroupLabel(String value) {
        TextView label = text(value, 11, true, ACCENT_DARK);
        label.setPadding(0, dp(12), 0, dp(4));
        return label;
    }

    private TextView addInfoRow(LinearLayout parent, String label, String initialValue) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView labelView = text(label, 13, false, MUTED);
        labelView.setSingleLine(true);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(150),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView valueView = text(initialValue, 13, true, INK);
        valueView.setGravity(Gravity.END);
        valueView.setTextIsSelectable(true);
        row.addView(valueView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(row);
        return valueView;
    }

    private void updateVehicleInformation(NoodoeService.Snapshot snapshot) {
        DeviceInfo info = snapshot.deviceInfoData;
        OqcData oqc = snapshot.oqcData;
        modelCodeValueView.setText(valueOrDash(snapshot.modelCode));
        vehicleProfileValueView.setText(info == null ? "—"
                : format(R.string.vehicle_profile_format, info.maxSpeed,
                        info.bikeType, info.motorSeries));
        dashboardIdentityValueView.setText(info == null ? "—"
                : valueOrDash(info.bikeSeries));
        productionValueView.setText(oqc == null ? "—"
                : "S/N " + valueOrDash(oqc.serialNumber)
                        + "\nPART " + valueOrDash(oqc.partialNumber)
                        + "  ·  ASSY " + valueOrDash(oqc.assemblyNumber));
        hardwareValueView.setText(info == null ? "—"
                : "HW " + info.hardwareVersion + "  ·  PCBA " + valueOrDash(info.pcba)
                        + "\nMAC " + valueOrDash(info.mac)
                        + (oqc == null ? "" : "  ·  PANEL " + oqc.panelVersion));
        softwareValueView.setText(info == null ? "—"
                : "FW " + info.firmwareMajor + "." + info.firmwareMinor
                        + "  ·  BOOT " + info.bootMajor + "." + info.bootMinor
                        + "\nPROTOCOL " + info.protocolMajor + "." + info.protocolMinor
                        + " (" + info.protocolFamilyLabel() + ")"
                        + "  ·  RESOURCE " + info.resourceMajor + "." + info.resourceMinor
                        + "\nRESOURCE ID " + info.resourceId
                        + "  ·  LANG PACK " + info.languagePackId
                        + "  ·  DASHBOARD " + info.defaultDashboard);
    }

    private static String valueOrDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    private View quickActionsBand() {
        LinearLayout band = sectionBand(string(R.string.section_time_welcome));
        LinearLayout clockRow = horizontal();
        Button clock = primaryButton(string(R.string.action_sync_time));
        clock.setOnClickListener(view -> actions.onSyncClock());
        registerOperation(clock);
        Button customClock = secondaryButton(string(R.string.action_set_time));
        customClock.setOnClickListener(view -> showCustomTimePicker());
        registerOperation(customClock);
        clockRow.addView(clock, weighted());
        LinearLayout.LayoutParams customClockParams = weighted();
        customClockParams.setMargins(dp(8), 0, 0, 0);
        clockRow.addView(customClock, customClockParams);
        band.addView(clockRow);

        LinearLayout welcomeRow = horizontal();
        welcomeRow.setGravity(Gravity.CENTER_VERTICAL);
        welcomeRow.setPadding(0, dp(18), 0, dp(6));
        LinearLayout welcomeLabels = vertical();
        welcomeLabels.addView(text(string(R.string.welcome_light), 15, true, INK));
        welcomeLabels.addView(text(string(R.string.welcome_light_description),
                12, false, MUTED));
        welcomeRow.addView(welcomeLabels, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        welcomeLightSwitch = new Switch(activity);
        welcomeLightSwitch.setChecked(true);
        tintSwitch(welcomeLightSwitch);
        welcomeRow.addView(welcomeLightSwitch);
        band.addView(welcomeRow);

        TextView standbyLabel = text(string(R.string.welcome_standby), 13, true, INK);
        standbyLabel.setPadding(0, dp(12), 0, dp(8));
        band.addView(standbyLabel);
        welcomeStandbySpinner = spinner(activity.getResources().getStringArray(
                R.array.welcome_standby_options));
        welcomeStandbySpinner.setSelection(1);
        band.addView(welcomeStandbySpinner);

        welcomeStatusView = text(format(R.string.welcome_active_format, 0),
                12, false, MUTED);
        welcomeStatusView.setPadding(0, dp(10), 0, dp(12));
        band.addView(welcomeStatusView);
        Button saveWelcome = secondaryButton(string(R.string.action_save_welcome));
        saveWelcome.setOnClickListener(view -> {
            if (!updatingWelcomeSettings) {
                actions.onWelcomeSettingsChanged(welcomeLightSwitch.isChecked(),
                        standbyValue(welcomeStandbySpinner.getSelectedItemPosition()));
            }
        });
        band.addView(saveWelcome, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        actionStatus = text(string(R.string.status_vehicle_disconnected), 12, false, MUTED);
        actionStatus.setPadding(0, dp(12), 0, 0);
        band.addView(actionStatus);
        return band;
    }

    @SuppressWarnings("deprecation")








    private void showCustomTimePicker() {
        Calendar selected = Calendar.getInstance();
        DatePickerDialog dateDialog = new DatePickerDialog(activity,
                (datePicker, year, month, day) -> {
                    selected.set(Calendar.YEAR, year);
                    selected.set(Calendar.MONTH, month);
                    selected.set(Calendar.DAY_OF_MONTH, day);
                    TimePickerDialog timeDialog = new TimePickerDialog(activity,
                            (timePicker, hour, minute) -> {
                                selected.set(Calendar.HOUR_OF_DAY, hour);
                                selected.set(Calendar.MINUTE, minute);
                                selected.set(Calendar.SECOND, 0);
                                actions.onSetClock(selected.getTimeInMillis());
                            }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE),
                            true);
                    timeDialog.setTitle(string(R.string.dashboard_time));
                    timeDialog.show();
                }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH));
        dateDialog.setTitle(string(R.string.dashboard_date));
        dateDialog.show();
    }

    private void setOdometer(long rawValue) {
        long value = Math.max(0, Math.min(rawValue, 999_999));
        String digits = String.format(Locale.US, "%06d", value);
        int leading = 0;
        while (leading < digits.length() - 1 && digits.charAt(leading) == '0') leading++;
        SpannableString styled = new SpannableString(digits);
        if (leading > 0) {
            styled.setSpan(new ForegroundColorSpan(Color.rgb(91, 108, 100)), 0, leading,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        odometerView.setText(styled);
    }

    private void setSpeed(int rawValue) {
        if (rawValue < 0) {
            speedHeroView.setText("---");
            return;
        }
        String digits = String.format(Locale.US, "%03d", Math.min(rawValue, 999));
        int leading = 0;
        while (leading < digits.length() - 1 && digits.charAt(leading) == '0') leading++;
        SpannableString styled = new SpannableString(digits);
        if (leading > 0) {
            styled.setSpan(new ForegroundColorSpan(Color.rgb(91, 108, 100)), 0, leading,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        speedHeroView.setText(styled);
    }

    private View photoBand() {
        LinearLayout band = sectionBand(string(R.string.section_photos));
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = horizontal();
            if (rowIndex > 0) row.setPadding(0, dp(8), 0, 0);
            for (int column = 0; column < 2; column++) {
                int slot = rowIndex * 2 + column;
                SquareFrameLayout tile = galleryTile(slot);
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                if (column > 0) tileParams.setMargins(dp(8), 0, 0, 0);
                row.addView(tile, tileParams);
            }
            band.addView(row);
        }
        LinearLayout row = horizontal();
        row.setPadding(0, dp(14), 0, 0);
        Button choose = secondaryButton(string(R.string.action_choose_image));
        choose.setOnClickListener(view -> actions.onChoosePhoto());
        Button send = primaryButton(string(R.string.action_send_dashboard));
        send.setOnClickListener(view -> actions.onSendPhoto(selectedPhotoSlot));
        registerOperation(send);
        row.addView(choose, weighted());
        LinearLayout.LayoutParams sendParams = weighted();
        sendParams.setMargins(dp(8), 0, 0, 0);
        row.addView(send, sendParams);
        band.addView(row);
        photoSelection = text(format(R.string.photo_slot_selected, 1), 12, false, MUTED);
        photoSelection.setPadding(0, dp(10), 0, 0);
        band.addView(photoSelection);
        return band;
    }

    public int getSelectedPhotoSlot() {
        return selectedPhotoSlot;
    }

    private SquareFrameLayout galleryTile(int slot) {
        SquareFrameLayout tile = new SquareFrameLayout(activity);
        tile.setPadding(dp(3), dp(3), dp(3), dp(3));
        tile.setOnClickListener(view -> {
            selectedPhotoSlot = slot;
            photoSelection.setText(format(R.string.photo_slot_selected, slot + 1));
            refreshGalleryBorders();
        });

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setClipToOutline(true);
        tile.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView placeholder = new ImageView(activity);
        placeholder.setImageResource(R.drawable.ic_image_rounded);
        placeholder.setColorFilter(MUTED);
        placeholder.setAlpha(0.42f);
        FrameLayout.LayoutParams placeholderParams = new FrameLayout.LayoutParams(
                dp(46), dp(46), Gravity.CENTER);
        tile.addView(placeholder, placeholderParams);

        TextView number = text(Integer.toString(slot + 1), 11, true, Color.WHITE);
        number.setGravity(Gravity.CENTER);
        number.setBackground(pill(Color.argb(180, 18, 24, 21)));
        FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(dp(28), dp(28),
                Gravity.TOP | Gravity.START);
        numberParams.setMargins(dp(8), dp(8), 0, 0);
        tile.addView(number, numberParams);

        galleryTiles.add(tile);
        galleryImages.add(image);
        galleryPlaceholders.add(placeholder);
        return tile;
    }

    private void showGalleryImage(int slot, String path) {
        ImageView image = galleryImages.get(slot);
        ImageView placeholder = galleryPlaceholders.get(slot);
        File file = path == null ? null : new File(path);
        String imageKey = file != null && file.isFile()
                ? file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length() : "";
        if (imageKey.equals(galleryImageKeys[slot])) return;
        galleryImageKeys[slot] = imageKey;
        if (file == null || !file.isFile()) {
            image.setImageDrawable(null);
            placeholder.setVisibility(View.VISIBLE);
            return;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        image.setImageBitmap(bitmap);
        placeholder.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
    }

    private void refreshGalleryBorders() {
        for (int i = 0; i < galleryTiles.size(); i++) {
            int color = gallerySent[i] ? Color.rgb(47, 255, 144)
                    : (i == selectedPhotoSlot ? ACCENT : BORDER);
            int width = gallerySent[i] || i == selectedPhotoSlot ? 3 : 1;
            galleryTiles.get(i).setBackground(outlinedShape(SURFACE, color, 6, width));
        }
    }

    private View themeBand() {
        LinearLayout band = sectionBand(string(R.string.section_dashboard_theme));
        TextView description = text(string(R.string.theme_description), 12, false, MUTED);
        description.setPadding(0, 0, 0, dp(13));
        band.addView(description);
        LinearLayout row = horizontal();
        Button library = secondaryButton(string(R.string.action_theme_library));
        library.setOnClickListener(view -> actions.onOpenThemeLibrary());
        Button create = primaryButton(string(R.string.action_create_theme));
        create.setOnClickListener(view -> actions.onCreateTheme());
        row.addView(library, weighted());
        LinearLayout.LayoutParams createParams = weighted();
        createParams.setMargins(dp(8), 0, 0, 0);
        row.addView(create, createParams);
        band.addView(row);
        return band;
    }

    private View settingsBand() {
        LinearLayout band = sectionBand(string(R.string.section_vehicle_settings));
        EditText name = input(string(R.string.hint_display_name), "OpenNoodoe");
        band.addView(name);

        automaticBrightness = switchRow(band, string(R.string.automatic_brightness), true);
        brightnessValue = text(string(R.string.brightness_auto), 12, false, MUTED);
        brightnessValue.setPadding(0, dp(13), 0, 0);
        band.addView(brightnessValue);
        brightnessSeek = new SeekBar(activity);
        brightnessSeek.setMax(100);
        brightnessSeek.setProgress(100);
        brightnessSeek.setEnabled(false);
        brightnessSeek.setAlpha(0.4f);
        brightnessSeek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        brightnessSeek.setThumbTintList(ColorStateList.valueOf(ACCENT));
        brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessValue.setText(format(R.string.brightness_percent, progress));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        band.addView(brightnessSeek);
        automaticBrightness.setOnCheckedChangeListener((button, checked) -> {
            brightnessSeek.setEnabled(!checked);
            brightnessSeek.setAlpha(checked ? 0.4f : 1.0f);
            brightnessValue.setText(checked ? string(R.string.brightness_auto)
                    : format(R.string.brightness_percent, brightnessSeek.getProgress()));
        });
        automaticBrightness.setChecked(true);

        Switch metric = switchRow(band, string(R.string.metric_celsius), true);
        Switch twentyFourHour = switchRow(band, string(R.string.time_24_hour), true);

        Button apply = primaryButton(string(R.string.action_apply_settings));
        apply.setOnClickListener(view -> actions.onApplyPreferences(
                name.getText().toString(), automaticBrightness.isChecked()
                        ? 255 : brightnessSeek.getProgress(), welcomeLightSwitch.isChecked(),
                metric.isChecked(), twentyFourHour.isChecked()));
        registerOperation(apply);
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        applyParams.setMargins(0, dp(15), 0, 0);
        band.addView(apply, applyParams);
        return band;
    }

    private View deviceBand() {
        LinearLayout band = sectionBand(string(R.string.section_device_management));
        TextView themeLabel = text(string(R.string.app_theme), 14, true, INK);
        themeLabel.setPadding(0, 0, 0, dp(8));
        band.addView(themeLabel);
        LinearLayout themeRow = horizontal();
        int currentMode = UiThemeSettings.mode(activity);
        int[] modes = {UiThemeSettings.AUTO, UiThemeSettings.LIGHT, UiThemeSettings.DARK};
        String[] themeLabels = activity.getResources().getStringArray(R.array.theme_options);
        for (int index = 0; index < modes.length; index++) {
            Button modeButton = themeModeButton(themeLabels[index], modes[index], currentMode);
            LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (index > 0) modeParams.setMargins(dp(6), 0, 0, 0);
            themeRow.addView(modeButton, modeParams);
        }
        band.addView(themeRow);

        TextView languageLabel = text(string(R.string.app_language), 14, true, INK);
        languageLabel.setPadding(0, dp(18), 0, dp(8));
        band.addView(languageLabel);
        String[] languageLabels = activity.getResources().getStringArray(R.array.language_options);
        int selectedLanguage = AppLanguageSettings.position(activity);
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout languageRow = horizontal();
            if (rowIndex > 0) languageRow.setPadding(0, dp(6), 0, 0);
            for (int column = 0; column < 3; column++) {
                int index = rowIndex * 3 + column;
                Button languageButton = languageModeButton(languageLabels[index], index,
                        selectedLanguage);
                LinearLayout.LayoutParams languageParams = new LinearLayout.LayoutParams(
                        0, dp(44), 1);
                if (column > 0) languageParams.setMargins(dp(6), 0, 0, 0);
                languageRow.addView(languageButton, languageParams);
            }
            band.addView(languageRow);
        }

        LinearLayout connectionRow = horizontal();
        connectionRow.setPadding(0, dp(18), 0, 0);
        connectionButton = primaryButton(string(R.string.action_connect));
        connectionButton.setOnClickListener(view -> actions.onConnectionPressed(connected));
        Button choose = secondaryButton(string(R.string.action_choose_device));
        choose.setOnClickListener(view -> actions.onChooseDevice());
        connectionRow.addView(connectionButton, weighted());
        LinearLayout.LayoutParams chooseParams = weighted();
        chooseParams.setMargins(dp(8), 0, 0, 0);
        connectionRow.addView(choose, chooseParams);
        band.addView(connectionRow);

        return band;
    }

    private Button themeModeButton(String label, int mode, int selectedMode) {
        Button button = baseButton(label);
        boolean selected = mode == selectedMode;
        button.setTextColor(selected ? Color.WHITE : INK);
        button.setBackground(selected ? filledBackground(ACCENT)
                : outlinedBackground(SURFACE, BORDER));
        button.setOnClickListener(view -> actions.onThemeModeChanged(mode));
        return button;
    }

    private Button languageModeButton(String label, int position, int selectedPosition) {
        Button button = baseButton(label);
        button.setTextSize(12);
        button.setSingleLine(true);
        boolean selected = position == selectedPosition;
        button.setTextColor(selected ? Color.WHITE : INK);
        button.setBackground(selected ? filledBackground(ACCENT)
                : outlinedBackground(SURFACE, BORDER));
        button.setOnClickListener(view ->
                actions.onLanguageChanged(AppLanguageSettings.tagAt(position)));
        return button;
    }

    private View footer() {
        LinearLayout footer = vertical();
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(dp(20), dp(25), dp(20), dp(32));
        Button test = secondaryButton(string(R.string.product_system_data));
        test.setOnClickListener(view -> actions.onOpenTestMode());
        footer.addView(test, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Button privacy = secondaryButton(string(R.string.privacy_policy_title));
        privacy.setOnClickListener(view -> PrivacyPolicyDialog.show(activity));
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        privacyParams.setMargins(0, dp(8), 0, 0);
        footer.addView(privacy, privacyParams);
        Button license = secondaryButton(string(R.string.license_title));
        license.setOnClickListener(view -> LicenseDialog.show(activity));
        LinearLayout.LayoutParams licenseParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        licenseParams.setMargins(0, dp(8), 0, 0);
        footer.addView(license, licenseParams);
        TextView version = text("OpenNoodoe " + BuildConfig.VERSION_NAME, 11, false, MUTED);
        version.setPadding(0, dp(15), 0, 0);
        footer.addView(version);
        return footer;
    }

    private LinearLayout sectionBand(String title) {
        LinearLayout band = vertical();
        band.setPadding(dp(20), dp(21), dp(20), dp(22));
        band.setBackgroundColor(SURFACE);
        TextView heading = text(title, 18, true, INK);
        heading.setPadding(0, 0, 0, dp(16));
        band.addView(heading);
        return band;
    }

    private Switch switchRow(LinearLayout parent, String label, boolean checked) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView text = text(label, 14, false, INK);
        row.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch value = new Switch(activity);
        value.setChecked(checked);
        tintSwitch(value);
        row.addView(value);
        parent.addView(row);
        return value;
    }

    private EditText input(String hint, String value) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(15);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(13), 0, dp(13), 0);
        input.setBackground(ripple(outlinedShape(SURFACE, BORDER, 8)));
        input.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
        return input;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(9), 0, dp(9), 0);
        spinner.setBackground(ripple(outlinedShape(SURFACE, BORDER, 8)));
        spinner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
        return spinner;
    }

    private static int standbyValue(int position) {
        int[] values = {WelcomeLightPolicy.SHUTDOWN_IMMEDIATELY, 1, 2, 3};
        return values[Math.max(0, Math.min(position, values.length - 1))];
    }

    private static int standbyPosition(int value) {
        if (value == WelcomeLightPolicy.SHUTDOWN_IMMEDIATELY) return 0;
        if (value >= 1 && value <= 3) return value;
        return 1;
    }

    private Button primaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(Color.WHITE);
        button.setBackground(filledBackground(ACCENT));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(INK);
        button.setBackground(outlinedBackground(SURFACE, BORDER));
        return button;
    }

    private Button dangerOutlineButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(DANGER);
        button.setBackground(outlinedBackground(SURFACE, DANGER));
        return button;
    }

    private Button baseButton(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setStateListAnimator(null);
        return button;
    }

    private void tintSwitch(Switch value) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        value.setThumbTintList(new ColorStateList(states,
                new int[]{ACCENT, Color.rgb(145, 157, 151)}));
        value.setTrackTintList(new ColorStateList(states,
                new int[]{Color.rgb(139, 211, 185), Color.rgb(210, 217, 213)}));
    }

    private void registerOperation(Button button) {
        operationButtons.add(button);
        button.setEnabled(false);
        button.setAlpha(0.45f);
    }

    private RippleDrawable filledBackground(int color) {
        return ripple(shape(color, color, 0, 8));
    }

    private RippleDrawable outlinedBackground(int fill, int stroke) {
        return ripple(outlinedShape(fill, stroke, 8));
    }

    private GradientDrawable outlinedShape(int fill, int stroke, int radius) {
        return shape(fill, stroke, 1, radius);
    }

    private GradientDrawable outlinedShape(int fill, int stroke, int radius, int strokeWidth) {
        return shape(fill, stroke, strokeWidth, radius);
    }

    private GradientDrawable pill(int color) {
        return shape(color, color, 0, 40);
    }

    private GradientDrawable shape(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private RippleDrawable ripple(GradientDrawable content) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(36, 0, 0, 0)),
                content, null);
    }

    private void addGap(LinearLayout page) {
        View gap = new View(activity);
        gap.setBackgroundColor(BACKGROUND);
        page.addView(gap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLetterSpacing(0);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private String string(int resourceId) {
        return activity.getString(resourceId);
    }

    private String format(int resourceId, Object... arguments) {
        return activity.getString(resourceId, arguments);
    }

    private LinearLayout vertical() {
        LinearLayout value = new LinearLayout(activity);
        value.setOrientation(LinearLayout.VERTICAL);
        return value;
    }

    private LinearLayout horizontal() {
        LinearLayout value = new LinearLayout(activity);
        value.setOrientation(LinearLayout.HORIZONTAL);
        return value;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, dp(48), 1);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class SquareFrameLayout extends FrameLayout {
        SquareFrameLayout(Activity context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int squareSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            super.onMeasure(squareSpec, squareSpec);
        }
    }
}
