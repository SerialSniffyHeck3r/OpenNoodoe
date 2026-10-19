package io.opennoodoe.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.opennoodoe.app.protocol.OqcData;

public final class MainActivity extends Activity implements NoodoeService.Listener {
    private static final int REQUEST_PERMISSIONS = 41;
    private static final int REQUEST_IMAGE = 42;
    private static final int REQUEST_FIRMWARE = 43;
    private static final int REQUEST_RESOURCE = 44;
    private static final int REQUEST_MEMBER_IMAGE = 45;
    private static final int REQUEST_THEME_BACKGROUND = 46;
    private static final int REQUEST_THEME_BUNDLE = 47;
    private static final int ACCENT = Color.rgb(0, 112, 82);
    private static volatile boolean hazardProcessUnlocked;

    private final Map<String, BluetoothDevice> devices = new LinkedHashMap<>();
    private final List<String> deviceLabels = new ArrayList<>();
    private final List<View> pages = new ArrayList<>();
    private final List<Button> tabs = new ArrayList<>();
    private final List<Button> transferActionButtons = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder pendingLog = new StringBuilder();
    private final StringBuilder visibleLog = new StringBuilder();
    private boolean logFlushScheduled;
    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> deviceAdapter;
    private NoodoeService service;
    private boolean bound;
    private boolean serviceStartRequested;
    private boolean bindingRequested;
    private TextView stateView;
    private TextView infoView;
    private TextView ridingView;
    private TextView meterProfileView;
    private TextView oqcView;
    private TextView transferView;
    private TextView runtimeView;
    private TextView captureView;
    private TextView logView;
    private TextView operationView;
    private ScrollView logScroll;
    private HomeScreen homeScreen;
    private View homeUi;
    private View testUi;
    private boolean testModeVisible;
    private CheckBox bridgeToggle;
    private Spinner gallerySlot;
    private Uri selectedImage;
    private final Uri[] homeGallerySources = new Uri[6];
    private boolean selectingHomeImage;
    private int pendingHomeGallerySlot;
    private String pendingTemplateId;
    private int pendingThemeLocation = NoodoeService.LOCATION_CLOCK;
    private ThemeAuthoringOptions pendingThemeOptions = new ThemeAuthoringOptions(
            Color.rgb(0, 167, 122), 4, true, true);
    private Uri selectedFirmware;
    private Uri selectedResource;
    private Uri selectedMemberImage;
    private TextView memberImageView;
    private LinearLayout hazardEntryContainer;
    private LinearLayout hazardToolsContainer;
    private TextView hazardLockedView;
    private TextView hazardStatusView;
    private TextView hazardFirmwareFileView;
    private TextView hazardResourceFileView;
    private EditText hazardEntryConfirmation;
    private EditText hazardOqcConfirmation;
    private EditText hazardFactoryConfirmation;
    private EditText hazardFirmwareConfirmation;
    private EditText hazardResourceConfirmation;
    private EditText firmwareMajorInput;
    private EditText firmwareMinorInput;
    private Button hazardOqcWriteButton;
    private final Map<String, EditText> hazardOqcFields = new LinkedHashMap<>();
    private boolean mediaGateAvailable;
    private boolean hazardSessionUnlocked = hazardProcessUnlocked;
    private String displayedOqcBackupId = "";

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((NoodoeService.LocalBinder) binder).getService();
            bound = true;
            service.addListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguageSettings.wrap(newBase));
    }

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = getBluetoothDevice(intent);
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction()) && device != null) {
                addDevice(device);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())
                    && device != null) {
                addDevice(device);
                if (homeScreen != null && service != null) homeScreen.update(service.snapshot());
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                stateView.setText("검색 완료");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(UiThemeSettings.isDark(this) ? R.style.AppThemeDark : R.style.AppThemeLight);
        super.onCreate(savedInstanceState);
        configureSystemBars();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        View content = buildUi();
        setContentView(content);
        content.requestApplyInsets();
        registerDiscoveryReceiver();
        if (requestRequiredPermissions()) {
            startAndBindService();
            refreshDevices(false);
        }
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        View decor = getWindow().getDecorView();
        boolean dark = UiThemeSettings.isDark(this);
        int appearance = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appearance |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(appearance);
        int barColor = dark ? Color.rgb(17, 20, 19) : Color.rgb(243, 246, 244);
        getWindow().setStatusBarColor(barColor);
        getWindow().setNavigationBarColor(barColor);
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        if (bluetoothAdapter != null && hasScanPermission()) {
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException ignored) {
            }
        }
        unregisterReceiver(discoveryReceiver);
        if (bindingRequested) {
            if (bound) {
                service.removeListener(this);
            }
            unbindService(serviceConnection);
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && data != null) {
            selectedImage = retainUri(data);
            if (selectedImage != null) {
                if (selectingHomeImage) {
                    int slot = Math.max(0, Math.min(pendingHomeGallerySlot,
                            homeGallerySources.length - 1));
                    Uri source = selectedImage;
                    PhotoCropDialog.show(this, source, slot, croppedImage -> {
                        homeGallerySources[slot] = croppedImage;
                        if (service != null) service.stageGalleryImage(croppedImage, slot);
                        if (homeScreen != null) homeScreen.setSelectedImage(croppedImage);
                    });
                } else {
                    transferView.setText("선택한 사진: " + selectedImage);
                }
            }
            selectingHomeImage = false;
        } else if (requestCode == REQUEST_THEME_BACKGROUND && resultCode == RESULT_OK
                && data != null) {
            Uri source = retainUri(data);
            if (source != null) {
                PhotoCropDialog.showTheme(this, source, pendingThemeLocation, cropped ->
                        showThemeCreationConfirmation(pendingThemeLocation, cropped));
            }
        } else if (requestCode == REQUEST_THEME_BUNDLE && resultCode == RESULT_OK
                && data != null) {
            Uri archive = retainUri(data);
            if (archive != null) showThemeImportConfirmation(pendingThemeLocation, archive);
        }
    }

    @Override
    public void onSnapshot(NoodoeService.Snapshot snapshot) {
        runOnUiThread(() -> {
            if (homeScreen != null) homeScreen.update(snapshot);
            stateView.setText(snapshot.state + (snapshot.address.isEmpty() ? "" : "  " + snapshot.address));
            infoView.setText(snapshot.deviceInfo);
            ridingView.setText(snapshot.ridingStatus);
            meterProfileView.setText(snapshot.meterProfile);
            oqcView.setText(snapshot.oqcStatus);
            transferView.setText(snapshot.transferStatus);
            if (runtimeView != null) {
                runtimeView.setText(snapshot.transferStatus);
            }
            captureView.setText(snapshot.captureId + "\n" + snapshot.capturePath);
            operationView.setText(snapshot.operationState);
            for (Button button : transferActionButtons) {
                button.setEnabled(!snapshot.operationBusy);
            }

        });
    }



    @Override
    public void onLogLine(String line) {
        synchronized (pendingLog) {
            pendingLog.append(line).append('\n');
            if (logFlushScheduled) {
                return;
            }
            logFlushScheduled = true;
        }
        uiHandler.postDelayed(this::flushLog, 250);
    }

    private void flushLog() {
        String addition;
        synchronized (pendingLog) {
            addition = pendingLog.toString();
            pendingLog.setLength(0);
            logFlushScheduled = false;
        }
        visibleLog.append(addition);
        if (visibleLog.length() > 30_000) {
            visibleLog.delete(0, visibleLog.length() - 20_000);
        }
        logView.setText(visibleLog);
        if (logScroll != null) {
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && hasScanPermission() && hasConnectPermission()) {
            startAndBindService();
            refreshDevices(false);
        }
    }

    private View buildUi() {
        FrameLayout host = new FrameLayout(this);
        testUi = buildTestUi();
        homeScreen = new HomeScreen(this, new HomeScreen.Actions() {
            @Override
            public void onConnectionPressed(boolean connected) {
                if (connected) {
                    run(NoodoeService::disconnect);
                } else if (service != null && !service.getSelectedAddress().isEmpty()) {
                    service.connectSaved();
                } else {
                    showDeviceChooser();
                }
            }

            @Override
            public void onChooseDevice() {
                showDeviceChooser();
            }

            @Override
            public void onSyncClock() {
                run(NoodoeService::syncClock);
            }

            @Override
            public void onSetClock(long timestampMillis) {
                Calendar selected = Calendar.getInstance();
                selected.setTimeInMillis(timestampMillis);
                run(value -> value.syncClock(selected));
            }

            @Override
            public void onWelcomeSettingsChanged(boolean enabled, int shutdownTime) {
                run(value -> value.configureWelcomeLight(enabled, shutdownTime));
                if (homeScreen != null) {
                    homeScreen.showActionStatus(service != null && service.snapshot().connected
                            ? getString(R.string.welcome_sent)
                            : getString(R.string.welcome_saved), false);
                }
            }

            @Override
            public void onChoosePhoto() {
                selectingHomeImage = true;
                pendingHomeGallerySlot = homeScreen == null ? 0 : homeScreen.getSelectedPhotoSlot();
                chooseImage();
            }

            @Override
            public void onSendPhoto(int slot) {
                Uri source = slot >= 0 && slot < homeGallerySources.length
                        ? homeGallerySources[slot] : null;
                if (source == null && service != null && slot >= 0 && slot < 6) {
                    String path = service.snapshot().galleryPaths[slot];
                    if (path != null && !path.isEmpty() && new java.io.File(path).isFile()) {
                        source = Uri.fromFile(new java.io.File(path));
                    }
                }
                if (source == null) {
                    homeScreen.showActionStatus(getString(R.string.photo_select_required), true);
                } else {
                    Uri selectedSource = source;
                    run(value -> value.installGallery(selectedSource, slot));
                }
            }

            @Override
            public void onOpenThemeLibrary() {
                showThemeLibrary();
            }

            @Override
            public void onCreateTheme() {
                showThemeTypeChooser();
            }

            @Override
            public void onApplyPreferences(String name, int brightness, boolean breathing,
                    boolean metric, boolean twentyFourHour) {
                run(value -> value.sendPreferences(name, brightness, breathing,
                        metric, twentyFourHour));
            }

            @Override
            public void onOpenTestMode() {
                showTestMode(0);
            }

            @Override
            public void onThemeModeChanged(int mode) {
                if (UiThemeSettings.mode(MainActivity.this) == mode) return;
                UiThemeSettings.setMode(MainActivity.this, mode);
                recreate();
            }

            @Override
            public void onLanguageChanged(String tag) {
                if (AppLanguageSettings.set(MainActivity.this, tag)) recreate();
            }
        });
        homeUi = homeScreen.view();
        host.addView(homeUi, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        host.addView(testUi, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        showHomeMode();
        return host;
    }

    private View buildTestUi() {
        LinearLayout root = vertical();
        applySystemBarInsets(root, dp(12), dp(10), dp(12), dp(8));
        root.setBackgroundColor(Color.rgb(244, 246, 245));
        LinearLayout titleRow = horizontal();
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = text(getString(R.string.product_system_data), 20, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button home = button("일반 모드");
        home.setOnClickListener(view -> showHomeMode());
        titleRow.addView(home);
        root.addView(titleRow);
        stateView = text("서비스 시작 중", 13, true);
        stateView.setTextColor(ACCENT);
        stateView.setPadding(0, dp(3), 0, dp(7));
        root.addView(stateView);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = horizontal();
        String[] names = {"연결", "시스템 정보"};
        for (int i = 0; i < names.length; i++) {
            int index = i;
            Button tab = button(names[i]);
            tab.setMinWidth(dp(76));
            tab.setOnClickListener(view -> showPage(index));
            tabs.add(tab);
            tabRow.addView(tab);
        }
        tabScroll.addView(tabRow);
        root.addView(tabScroll);

        FrameLayout content = new FrameLayout(this);
        pages.add(page(connectionPage()));
        pages.add(page(vehiclePage()));
        for (View page : pages) {
            content.addView(page, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        LinearLayout workspace = vertical();
        workspace.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 4));
        workspace.addView(persistentLogPanel(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(workspace, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        showPage(0);
        return root;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (testModeVisible) {
            showHomeMode();
        } else {
            super.onBackPressed();
        }
    }

    private void showHomeMode() {
        testModeVisible = false;
        if (homeUi != null) homeUi.setVisibility(View.VISIBLE);
        if (testUi != null) testUi.setVisibility(View.GONE);
        if (homeScreen != null && service != null) homeScreen.update(service.snapshot());
    }

    private void showTestMode(int selectedPage) {
        testModeVisible = true;
        if (homeUi != null) homeUi.setVisibility(View.GONE);
        if (testUi != null) testUi.setVisibility(View.VISIBLE);
        if (!pages.isEmpty()) showPage(Math.max(0, Math.min(selectedPage, pages.size() - 1)));
    }

    @SuppressWarnings("deprecation")
    private static void applySystemBarInsets(View view, int left, int top,
            int right, int bottom) {
        view.setPadding(left, top, right, bottom);
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            target.setPadding(
                    left + insets.getSystemWindowInsetLeft(),
                    top + insets.getSystemWindowInsetTop(),
                    right + insets.getSystemWindowInsetRight(),
                    bottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
    }

    private LinearLayout connectionPage() {
        LinearLayout page = vertical();
        page.addView(section("Classic Bluetooth SPP"));
        Button scan = button("기기 검색 / 페어링 목록 새로고침");
        scan.setOnClickListener(view -> refreshDevices(true));
        page.addView(scan);
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceLabels);
        ListView list = new ListView(this);
        list.setAdapter(deviceAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = new ArrayList<>(devices.values()).get(position);
            run(value -> value.selectDevice(device.getAddress()));
        });
        page.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210)));
        LinearLayout row = horizontal();
        Button connect = button("저장 기기 연결");
        connect.setOnClickListener(view -> run(NoodoeService::connectSaved));
        Button disconnect = button("연결 해제");
        disconnect.setOnClickListener(view -> run(NoodoeService::disconnect));
        row.addView(connect, weighted());
        row.addView(disconnect, weighted());
        page.addView(row);
        page.addView(section("기록 세션"));
        Button capture = button("새 캡처 세션 시작");
        capture.setOnClickListener(view -> run(NoodoeService::beginCaptureSession));
        page.addView(capture);
        captureView = selectable("캡처 대기");
        page.addView(captureView);
        return page;
    }

    private LinearLayout vehiclePage() {
        LinearLayout page = vertical();
        page.addView(section("계기판 정보와 주행 상태"));
        LinearLayout readRow = horizontal();
        Button info = button("기기 정보 읽기");
        info.setOnClickListener(view -> run(NoodoeService::requestDeviceInfo));
        Button riding = button("주행 정보 읽기");
        riding.setOnClickListener(view -> run(NoodoeService::requestRidingStatus));
        readRow.addView(info, weighted());
        readRow.addView(riding, weighted());
        page.addView(readRow);
        infoView = selectable("기기 정보 없음");
        page.addView(infoView);
        ridingView = selectable("주행 정보 없음");
        page.addView(ridingView);
        Button meterProfile = button("VCU / 배터리 프로필 읽기 (Ionex 계열)");
        meterProfile.setOnClickListener(view -> run(NoodoeService::requestMeterProfile));
        page.addView(meterProfile);
        meterProfileView = selectable("미조회");
        page.addView(meterProfileView);
        Button production = button(getString(R.string.product_production_read));
        production.setOnClickListener(view -> run(NoodoeService::requestOqcData));
        page.addView(production);
        oqcView = selectable("");
        page.addView(oqcView);
        transferView = selectable("");
        page.addView(transferView);
        page.addView(section("시계와 차량 설정"));
        Button clock = button("휴대전화 시각 전송");
        clock.setOnClickListener(view -> run(NoodoeService::syncClock));
        page.addView(clock);
        LinearLayout light = horizontal();
        Button on = button("웰컴 라이트 켜기");
        on.setOnClickListener(view -> run(value -> value.setBreathingLight(true)));
        Button off = button("라이트 끄기");
        off.setOnClickListener(view -> run(value -> value.setBreathingLight(false)));
        light.addView(on, weighted());
        light.addView(off, weighted());
        page.addView(light);
        EditText user = input("사용자 이름", "OpenNoodoe");
        EditText brightness = input("밝기 0..100, 255=자동", "255");
        CheckBox breathing = check("차량 설정의 웰컴 라이트 사용", true);
        CheckBox metric = check("km / 섭씨", true);
        CheckBox time24 = check("24시간제", true);
        page.addView(user); page.addView(brightness); page.addView(breathing);
        page.addView(metric); page.addView(time24);
        Button prefs = button("안전한 설정 항목 전송");
        prefs.setOnClickListener(view -> run(value -> value.sendPreferences(user.getText().toString(),
                number(brightness, 255), breathing.isChecked(), metric.isChecked(), time24.isChecked())));
        page.addView(prefs);
        return page;
    }















    private LinearLayout persistentLogPanel() {
        LinearLayout panel = vertical();
        panel.setPadding(0, dp(4), 0, 0);
        panel.setBackgroundColor(Color.rgb(229, 234, 232));
        operationView = text("IDLE", 11, true);
        operationView.setTextColor(ACCENT);
        panel.addView(operationView);
        LinearLayout row = horizontal();
        Button visible = button("정상");
        visible.setOnClickListener(view -> run(value -> value.markObservation("VISIBLE RESULT")));
        Button none = button("무변화");
        none.setOnClickListener(view -> run(value -> value.markObservation("NO VISIBLE RESULT")));
        Button unexpected = button("오류/중단");
        unexpected.setOnClickListener(view -> run(value ->
                value.markObservation("UNEXPECTED BEHAVIOR - STOP TEST")));
        Button reset = button("RESET");
        reset.setTextColor(Color.rgb(150, 35, 35));
        reset.setOnClickListener(view -> run(NoodoeService::resetOperationKeepingConnection));
        row.addView(visible, weighted()); row.addView(none, weighted());
        row.addView(unexpected, weighted()); row.addView(reset, weighted());
        panel.addView(row);
        logView = selectable("");
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextSize(9);
        logView.setPadding(dp(4), dp(2), dp(4), dp(2));
        logScroll = new ScrollView(this);
        logScroll.setFillViewport(true);
        logScroll.addView(logView);
        panel.addView(logScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return panel;
    }

    private void showPage(int selected) {
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setVisibility(i == selected ? View.VISIBLE : View.GONE);
            tabs.get(i).setTextColor(i == selected ? Color.WHITE : Color.rgb(25, 35, 33));
            tabs.get(i).setBackgroundColor(i == selected ? ACCENT : Color.TRANSPARENT);
        }
    }

























    private void chooseFile(int requestCode, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mimeType)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void chooseImage() {
        chooseFile(REQUEST_IMAGE, "image/*");
    }

    private void showThemeLibrary() {
        List<ThemeRepository.Entry> entries = ThemeRepository.list(this);
        String[] labels = new String[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            ThemeRepository.Entry entry = entries.get(index);
            int title = themeTitle(entry);
            String displayTitle = entry.title.isEmpty() ? getString(title) : entry.title;
            int source = entry.preservedReference ? R.string.theme_source_preserved
                    : "official-archive".equals(entry.source)
                            ? R.string.theme_source_official_archive
                            : "imported-bundle".equals(entry.source)
                                    ? R.string.theme_source_imported
                                    : R.string.theme_source_custom;
            labels[index] = displayTitle + "\n" + getString(source, entry.fileCount);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_library_title)
                .setItems(labels, (dialog, which) -> showThemeApplyConfirmation(entries.get(which)))
                .setPositiveButton(R.string.action_import_theme,
                        (dialog, which) -> showThemeImportTypeChooser())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showThemeApplyConfirmation(ThemeRepository.Entry entry) {
        int name = themeTitle(entry);
        String title = entry.title.isEmpty() ? getString(name) : entry.title;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(R.string.theme_apply_description)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_apply_theme,
                        (dialog, which) -> run(value -> value.installTheme(entry.id)))
                .show();
    }

    private void showThemeTypeChooser() {
        if (ThemeRepository.list(this).isEmpty()) {
            new AlertDialog.Builder(this).setTitle(R.string.theme_choose_type)
                    .setMessage(R.string.product_template_needed)
                    .setPositiveButton(R.string.action_import_theme, (d, w) -> showThemeImportTypeChooser())
                    .setNegativeButton(R.string.action_cancel, null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_choose_type)
                .setItems(R.array.theme_authoring_type_options, (dialog, which) -> {
                    pendingThemeLocation = which == 0 ? NoodoeService.LOCATION_CLOCK
                            : NoodoeService.LOCATION_SPEEDOMETER;
                    showThemeOptions(pendingThemeLocation);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showThemeOptions(int location) {
        List<ThemeRepository.Entry> templates = ThemeRepository.list(this);
        for (int i = templates.size() - 1; i >= 0; i--) {
            if (templates.get(i).location != location) templates.remove(i);
        }
        if (templates.isEmpty()) {
            new AlertDialog.Builder(this).setMessage(R.string.product_template_needed)
                    .setPositiveButton(R.string.action_import_theme, (d, w) -> showThemeImportTypeChooser())
                    .setNegativeButton(R.string.action_cancel, null).show();
            return;
        }
        String[] labels = new String[templates.size()];
        for (int i = 0; i < labels.length; i++) {
            ThemeRepository.Entry entry = templates.get(i);
            labels[i] = entry.title.isEmpty() ? getString(themeTitle(entry)) + " · " + (i + 1) : entry.title;
        }
        new AlertDialog.Builder(this).setTitle(R.string.product_choose_template)
                .setItems(labels, (d, which) -> {
                    pendingTemplateId = templates.get(which).id;
                    showThemeEditorOptions(location);
                }).setNegativeButton(R.string.action_cancel, null).show();
    }

    private void showThemeEditorOptions(int location) {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), 0);

        form.addView(text(getString(R.string.theme_accent_color), 13, true));
        EditText accent = input(getString(R.string.theme_accent_color_hint), "#00A77A");
        accent.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        form.addView(accent);

        CheckBox showPeriod = new CheckBox(this);
        showPeriod.setText(R.string.theme_show_period);
        showPeriod.setChecked(true);
        CheckBox showWeekday = new CheckBox(this);
        showWeekday.setText(R.string.theme_show_weekday);
        showWeekday.setChecked(true);

        Spinner speedStyle = null;
        if (location == NoodoeService.LOCATION_CLOCK) {
            form.addView(showPeriod);
            form.addView(showWeekday);
        } else {
            TextView label = text(getString(R.string.theme_speed_bar_style), 13, true);
            label.setPadding(0, dp(12), 0, 0);
            form.addView(label);
            speedStyle = new Spinner(this);
            speedStyle.setAdapter(ArrayAdapter.createFromResource(this,
                    R.array.theme_speed_bar_options,
                    android.R.layout.simple_spinner_dropdown_item));
            speedStyle.setSelection(3);
            form.addView(speedStyle);
        }

        Spinner finalSpeedStyle = speedStyle;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.theme_cfg_settings_title)
                .setView(form)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_choose_background, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    try {
                        String colorText = accent.getText().toString().trim();
                        if (!colorText.matches("#[0-9a-fA-F]{6}")) {
                            throw new IllegalArgumentException("invalid color");
                        }
                        int color = Color.parseColor(colorText);
                        int barType = finalSpeedStyle == null
                                ? 4 : finalSpeedStyle.getSelectedItemPosition() + 1;
                        pendingThemeOptions = new ThemeAuthoringOptions(color, barType,
                                showPeriod.isChecked(), showWeekday.isChecked());
                        dialog.dismiss();
                        chooseFile(REQUEST_THEME_BACKGROUND, "image/*");
                    } catch (IllegalArgumentException error) {
                        Toast.makeText(this, R.string.theme_invalid_color,
                                Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
    }

    private void showThemeImportTypeChooser() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_import_choose_type)
                .setItems(R.array.theme_authoring_type_options, (dialog, which) -> {
                    int[] locations = {NoodoeService.LOCATION_CLOCK,
                            NoodoeService.LOCATION_SPEEDOMETER};
                    pendingThemeLocation = locations[which];
                    chooseFile(REQUEST_THEME_BUNDLE, "application/zip");
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showThemeImportConfirmation(int location, Uri archive) {
        int typeName = themeTypeName(location);
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_import_confirm_title)
                .setMessage(getString(R.string.theme_import_confirm_description,
                        getString(typeName)))
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.action_import_theme,
                        (dialog, which) -> run(value -> value.saveImportedTheme(location, archive)))
                .setPositiveButton(R.string.action_import_apply,
                        (dialog, which) -> run(value ->
                                value.importAndInstallTheme(location, archive)))
                .show();
    }

    private void showThemeCreationConfirmation(int location, Uri background) {
        int typeName = location == NoodoeService.LOCATION_CLOCK
                ? R.string.theme_type_clock : R.string.theme_type_speed;
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_create_confirm_title)
                .setMessage(getString(R.string.theme_create_confirm_description,
                        getString(typeName)))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_create_apply,
                        (dialog, which) -> run(value ->
                        value.createAndInstallTheme(location, pendingTemplateId, background,
                                        pendingThemeOptions)))
                .show();
    }

    private int themeTitle(ThemeRepository.Entry entry) {
        if (entry.preservedReference) {
            return entry.location == NoodoeService.LOCATION_CLOCK
                    ? R.string.theme_reference_clock : R.string.theme_reference_speed;
        }
        if (entry.location == NoodoeService.LOCATION_WEATHER) {
            return R.string.theme_custom_weather;
        }
        if (entry.location == NoodoeService.LOCATION_POI) {
            return R.string.theme_custom_around_me;
        }
        return entry.location == NoodoeService.LOCATION_CLOCK
                ? R.string.theme_custom_clock : R.string.theme_custom_speed;
    }

    private int themeTypeName(int location) {
        if (location == NoodoeService.LOCATION_WEATHER) return R.string.theme_type_weather;
        if (location == NoodoeService.LOCATION_POI) return R.string.theme_type_around_me;
        return location == NoodoeService.LOCATION_CLOCK
                ? R.string.theme_type_clock : R.string.theme_type_speed;
    }

    private void showDeviceChooser() {
        refreshDevices(false);
        List<BluetoothDevice> paired = new ArrayList<>(devices.values());
        String[] labels = new String[paired.size()];
        for (int i = 0; i < paired.size(); i++) {
            BluetoothDevice device = paired.get(i);
            try {
                String name = device.getName();
                labels[i] = (name == null || name.isEmpty()
                        ? getString(R.string.device_unnamed) : name)
                        + "\n" + device.getAddress();
            } catch (SecurityException error) {
                labels[i] = getString(R.string.device_bluetooth);
            }
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.device_choose_title)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_pair_new, (value, which) ->
                        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        if (labels.length == 0) {
            dialog.setMessage(R.string.device_none_paired);
        } else {
            dialog.setItems(labels, (value, which) -> {
                if (service == null) {
                    homeScreen.showActionStatus(getString(R.string.service_preparing), true);
                    return;
                }
                service.selectDevice(paired.get(which).getAddress());
                service.connectSaved();
            });
        }
        dialog.show();
    }





    private Uri retainUri(Intent data) {
        Uri uri = data.getData();
        if (uri != null) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }
        return uri;
    }

    private void startAndBindService() {
        if (serviceStartRequested) return;
        serviceStartRequested = true;
        Intent intent = new Intent(this, NoodoeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        bindingRequested = bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void refreshDevices(boolean discover) {
        if (bluetoothAdapter == null || !hasScanPermission() || !hasConnectPermission()) {
            stateView.setText("Bluetooth 권한 또는 어댑터 없음");
            requestRequiredPermissions();
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                return;
            }
            devices.clear();
            deviceLabels.clear();
            if (deviceAdapter != null) deviceAdapter.notifyDataSetChanged();
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) addDevice(device);
            if (discover) {
                bluetoothAdapter.cancelDiscovery();
                stateView.setText(bluetoothAdapter.startDiscovery() ? "Classic Bluetooth 검색 중" : "검색 시작 실패");
            }
        } catch (SecurityException error) {
            stateView.setText("Bluetooth 권한 거부됨");
        }
    }

    private void addDevice(BluetoothDevice device) {
        try {
            devices.put(device.getAddress(), device);
            deviceLabels.clear();
            for (BluetoothDevice item : devices.values()) {
                deviceLabels.add((item.getName() == null ? "Unknown" : item.getName()) + "\n"
                        + item.getAddress() + (item.getBondState() == BluetoothDevice.BOND_BONDED ? "  [paired]" : ""));
            }
            if (deviceAdapter != null) deviceAdapter.notifyDataSetChanged();
        } catch (SecurityException ignored) {
        }
    }

    private void registerDiscoveryReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(discoveryReceiver, filter);
    }

    private boolean requestRequiredPermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33) addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
            return false;
        }
        return true;
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
    }

    private boolean hasScanPermission() {
        return Build.VERSION.SDK_INT >= 31
                ? checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                : checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void run(ServiceAction action) {
        if (service == null) stateView.setText("서비스 연결 대기 중"); else action.run(service);
    }

    private ScrollView page(View child) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(child);
        return scroll;
    }

    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout horizontal() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1); }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); return b; }
    private Button transferButton(String value) { Button b = button(value); transferActionButtons.add(b); return b; }
    private CheckBox check(String value, boolean checked) { CheckBox c = new CheckBox(this); c.setText(value); c.setChecked(checked); return c; }
    private EditText input(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(true); return e; }
    private EditText integerInput(String hint, String value) { EditText e = input(hint, value); e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED); return e; }
    private Spinner spinner(String[] values) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); return s; }
    private TextView selectable(String value) { TextView v = text(value, 12, false); v.setTextIsSelectable(true); v.setPadding(0, dp(8), 0, dp(8)); return v; }
    private TextView section(String value) { TextView v = text(value, 15, true); v.setPadding(0, dp(14), 0, dp(5)); return v; }
    private TextView text(String value, int size, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(Color.rgb(25, 35, 33)); if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int number(EditText value, int fallback) { try { return Integer.parseInt(value.getText().toString().trim()); } catch (NumberFormatException e) { return fallback; } }

    private int creationLocation(int index) { int[] locations = {NoodoeService.LOCATION_CLOCK, NoodoeService.LOCATION_WEATHER, NoodoeService.LOCATION_SPEEDOMETER, NoodoeService.LOCATION_POI, NoodoeService.LOCATION_GROUP}; return locations[Math.max(0, Math.min(index, locations.length - 1))]; }

    private static BluetoothDevice getBluetoothDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    private interface ServiceAction { void run(NoodoeService service); }

    private static final class AppChoice {
        final String label;
        final String packageName;
        final Drawable icon;

        AppChoice(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}
