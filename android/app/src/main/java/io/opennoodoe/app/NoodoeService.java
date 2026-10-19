package io.opennoodoe.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import io.opennoodoe.app.protocol.ByteCodec;
import io.opennoodoe.app.protocol.CandidatePayloads;
import io.opennoodoe.app.protocol.CommandFrame;
import io.opennoodoe.app.protocol.DeviceInfo;
import io.opennoodoe.app.protocol.FileTransferPayloads;
import io.opennoodoe.app.protocol.OqcData;
import io.opennoodoe.app.protocol.OqcTestSample;
import io.opennoodoe.app.protocol.ReplyExpectation;
import io.opennoodoe.app.protocol.RidingStatus;
import io.opennoodoe.app.protocol.SequenceFrame;
import io.opennoodoe.app.protocol.SequenceStreamDecoder;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class NoodoeService extends Service {
    public static final String PREFS = "opennoodoe";
    public static final String PREF_ADDRESS = "device_address";
    private static final String PREF_LAST_ODOMETER = "last_odometer";
    private static final String PREF_LAST_ODOMETER_AT = "last_odometer_at";
    private static final String PREF_LAST_MODEL_CODE = "last_model_code";
    private static final String PREF_WELCOME_ENABLED = "welcome_light_enabled";
    private static final String PREF_WELCOME_STANDBY = "welcome_light_standby";
    private static final String PREF_WELCOME_COUNT = "welcome_light_count";
    private static final String PREF_VEHICLE_NAME = "vehicle_name";
    private static final String PREF_BRIGHTNESS = "display_brightness";
    private static final String PREF_METRIC = "metric_units";
    private static final String PREF_TWENTY_FOUR_HOUR = "twenty_four_hour";
    private static final String PREF_VEHICLE_SETTINGS_CONFIGURED = "vehicle_settings_configured";
    private static final String PREF_GALLERY_PATH_PREFIX = "gallery_path_";
    private static final String PREF_GALLERY_SENT_PREFIX = "gallery_sent_";
    public static final String ACTION_FORWARD_NOTIFICATION = "io.opennoodoe.app.FORWARD_NOTIFICATION";
    public static final String ACTION_FORWARD_CALL = "io.opennoodoe.app.FORWARD_CALL";
    public static final String ACTION_FORWARD_SMS = "io.opennoodoe.app.FORWARD_SMS";
    public static final String EXTRA_APP_ID = "app_id";
    public static final String EXTRA_APP_NAME = "app_name";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_CALLER = "caller";
    public static final String EXTRA_CALL_STATUS = "call_status";

    public static final int LOCATION_DASHBOARD = 0x000;
    public static final int LOCATION_NAVIGATION = 0x100;
    public static final int LOCATION_CLOCK = 0x200;
    public static final int LOCATION_WEATHER = 0x300;
    public static final int LOCATION_SPEEDOMETER = 0x400;
    public static final int LOCATION_POI = 0x500;
    public static final int LOCATION_GALLERY = 0x600;
    public static final int LOCATION_GROUP = 0x700;
    // Official cross-version IDs retained for research. MUSIC is absent from
    // the active sync list, while AUDIO is enabled only for V2 devices.
    public static final int LOCATION_MUSIC = 0xA00;
    public static final int LOCATION_AUDIO = 0xB00;
    public static final int LOCATION_FIRMWARE = 0x800;
    public static final int LOCATION_RESOURCE = 0x900;

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final byte[] RAW_BOOTSTRAP = {0x05, 0, 0, 0, 0};
    private static final int NOTIFICATION_ID = 550;
    private static final String CHANNEL_ID = "opennoodoe_connection";
    private static final long FILE_TASK_TIMEOUT_MS = 20_000;

    public interface Listener {
        void onSnapshot(Snapshot snapshot);

        void onLogLine(String line);
    }

    public static final class Snapshot {
        public final String state;
        public final String address;
        public final boolean connected;
        public final boolean paired;
        public final String deviceInfo;
        public final String captureId;
        public final String capturePath;
        public final String ridingStatus;
        public final String oqcStatus;
        public final String meterProfile;
        public final String transferStatus;
        public final boolean oqcTestActive;
        public final boolean notificationBridge;
        public final OqcData oqcData;
        public final boolean oqcBackupReady;
        public final String oqcBackupPath;
        public final String oqcBackupId;
        public final String hazardStatus;
        public final String operationState;
        public final boolean operationBusy;
        public final DeviceInfo deviceInfoData;
        public final RidingStatus ridingStatusData;
        public final Boolean keyOn;
        public final long odometer;
        public final long odometerUpdatedAt;
        public final boolean odometerLive;
        public final String modelCode;
        public final boolean welcomeLightEnabled;
        public final int welcomeStandby;
        public final int welcomeActivationCount;
        public final String[] galleryPaths;
        public final boolean[] gallerySent;

        private Snapshot(String state, String address, boolean connected, boolean paired,
                String deviceInfo,
                String captureId, String capturePath, String ridingStatus, String oqcStatus,
                String meterProfile, String transferStatus, boolean oqcTestActive,
                boolean notificationBridge, OqcData oqcData, boolean oqcBackupReady,
                String oqcBackupPath, String oqcBackupId, String hazardStatus,
                String operationState, boolean operationBusy, DeviceInfo deviceInfoData,
                RidingStatus ridingStatusData, Boolean keyOn, long odometer, long odometerUpdatedAt,
                boolean odometerLive, String modelCode, boolean welcomeLightEnabled,
                int welcomeStandby, int welcomeActivationCount, String[] galleryPaths,
                boolean[] gallerySent) {
            this.state = state;
            this.address = address;
            this.connected = connected;
            this.paired = paired;
            this.deviceInfo = deviceInfo;
            this.captureId = captureId;
            this.capturePath = capturePath;
            this.ridingStatus = ridingStatus;
            this.oqcStatus = oqcStatus;
            this.meterProfile = meterProfile;
            this.transferStatus = transferStatus;
            this.oqcTestActive = oqcTestActive;
            this.notificationBridge = notificationBridge;
            this.oqcData = oqcData;
            this.oqcBackupReady = oqcBackupReady;
            this.oqcBackupPath = oqcBackupPath;
            this.oqcBackupId = oqcBackupId;
            this.hazardStatus = hazardStatus;
            this.operationState = operationState;
            this.operationBusy = operationBusy;
            this.deviceInfoData = deviceInfoData;
            this.ridingStatusData = ridingStatusData;
            this.keyOn = keyOn;
            this.odometer = odometer;
            this.odometerUpdatedAt = odometerUpdatedAt;
            this.odometerLive = odometerLive;
            this.modelCode = modelCode;
            this.welcomeLightEnabled = welcomeLightEnabled;
            this.welcomeStandby = welcomeStandby;
            this.welcomeActivationCount = welcomeActivationCount;
            this.galleryPaths = galleryPaths.clone();
            this.gallerySent = gallerySent.clone();
        }
    }

    public final class LocalBinder extends Binder {
        public NoodoeService getService() {
            return NoodoeService.this;
        }
    }

    private final LocalBinder binder = new LocalBinder();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicInteger connectionGeneration = new AtomicInteger();
    private final AtomicInteger poiMotionGeneration = new AtomicInteger();
    private final AtomicInteger groupMotionGeneration = new AtomicInteger();
    private final AtomicBoolean poiMotionTickQueued = new AtomicBoolean();
    private final AtomicBoolean groupMotionTickQueued = new AtomicBoolean();
    private final AtomicBoolean transferOperationClaimed = new AtomicBoolean();
    private final AtomicBoolean transferResetRequested = new AtomicBoolean();
    private final AtomicBoolean connectionAttemptInProgress = new AtomicBoolean();
    private final AtomicBoolean initialConnectionScheduled = new AtomicBoolean();
    private final AtomicBoolean telemetryReadQueued = new AtomicBoolean();
    private final VehicleTelemetry telemetry = new VehicleTelemetry();
    private ScheduledFuture<?> telemetryFuture;
    private volatile long nextDeviceInfoRefreshAt;
    private final Object outputLock = new Object();
    private final Object ackLock = new Object();
    private final Object replyLock = new Object();
    private final Object logFileLock = new Object();
    private final Object uiLogLock = new Object();
    private final ArrayDeque<String> recentUiLog = new ArrayDeque<>();

    private BluetoothAdapter adapter;
    private SharedPreferences preferences;
    private volatile BluetoothSocket socket;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile boolean connected;
    private volatile boolean framedProtocol;
    private volatile String state = "Idle";
    private volatile String deviceInfoText = "No device information";
    private volatile int sendIndex;
    private volatile int expectedReceiveIndex = 128;
    private volatile int lastReceiveIndex;
    private volatile boolean hasReceivedPacket;
    private int pendingAckIndex = -1;
    private boolean pendingAcked;
    private ScheduledFuture<?> lightOffFuture;
    private ScheduledFuture<?> oqcStopFuture;
    private ScheduledFuture<?> groupContinueFuture;
    private ScheduledFuture<?> groupStopFuture;
    private ScheduledFuture<?> poiMotionFuture;
    private ScheduledFuture<?> groupMotionFuture;
    private volatile String captureId = "not-started";
    private volatile File captureDirectory;
    private volatile String ridingStatusText = "Not read";
    private volatile String oqcStatusText = "Not read";
    private volatile String meterProfileText = "Not read";
    private volatile String transferStatusText = "Idle";
    private volatile String hazardStatusText = "대기";
    private volatile String operationStateText = "IDLE";
    private volatile boolean hazardSessionArmed;
    private volatile DeviceInfo currentDeviceInfo;
    private volatile RidingStatus currentRidingStatus;
    private volatile OqcData latestOqcData;
    private volatile byte[] latestOqcRaw;
    private volatile int oqcBackupGeneration = -1;
    private volatile String oqcBackupPath = "";
    private volatile String oqcBackupId = "";
    private volatile boolean oqcTestActive;
    private long lastOqcDisplayAt;
    private int lastOqcFlags = -1;
    private long lastOqcLight = -1;
    private int suppressedOqcSamples;
    private int oqcSeenFlags;
    private long oqcLightMinimum = Long.MAX_VALUE;
    private long oqcLightMaximum = Long.MIN_VALUE;
    private long oqcSampleCount;
    private ReplyExpectation pendingReplyExpectation;
    private byte[] pendingReplyPayload;
    private final AtomicInteger taskIds = new AtomicInteger(1);
    private volatile int navigationTaskId = -1;
    private int navigationTransferId = 1;
    private int navigationFileId = 1;
    private volatile int groupTaskId = -1;
    private volatile int foregroundCreation = -1;
    private volatile int backgroundCreation = -1;
    private int groupTransferId = 1;
    private int poiMotionStep;
    private int groupMotionStep;
    private volatile int activeTransferTask = -1;
    private volatile int activeTransferType;
    private volatile int activeTransferLocation = -1;
    private volatile long activeTransferTotal;
    private volatile byte[] activeTransferContentId = new byte[16];
    private volatile boolean transferResetHandled;
    private volatile boolean aclConnectedRecently;
    private volatile boolean userDisconnected;
    private WelcomeLightPolicy welcomeLightPolicy;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = getBluetoothDevice(intent);
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action) && device != null) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                log("Bond state " + bondState + " for " + safeAddress(device));
                if (bondState == BluetoothDevice.BOND_BONDED
                        && safeAddress(device).equals(getSelectedAddress())) {
                    connectSaved();
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) && device != null
                    && safeAddress(device).equals(getSelectedAddress())) {
                if (!connected && !connectionAttemptInProgress.get()) {
                    aclConnectedRecently = true;
                    log("Target ACL connected; waiting for the proximity disconnect cue");
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) && device != null
                    && safeAddress(device).equals(getSelectedAddress())) {
                if (connected || socket != null) {
                    aclConnectedRecently = false;
                    handleConnectionLost("Bluetooth ACL disconnected");
                } else if (aclConnectedRecently) {
                    aclConnectedRecently = false;
                    scheduleAutomaticConnection("ACL proximity cue");
                } else {
                    log("Target ACL disconnected without a preceding proximity cue");
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (adapterState == BluetoothAdapter.STATE_ON) {
                    scheduleAutomaticConnection("Bluetooth enabled");
                } else {
                    aclConnectedRecently = false;
                    handleConnectionLost("Bluetooth adapter is not available");
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        adapter = BluetoothAdapter.getDefaultAdapter();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        preferences.edit().putBoolean("notification_bridge", false).apply();
        welcomeLightPolicy = new WelcomeLightPolicy(
                preferences.getBoolean(PREF_WELCOME_ENABLED, true),
                preferences.getInt(PREF_WELCOME_STANDBY, 1),
                preferences.getInt(PREF_WELCOME_COUNT, 0));
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Idle"));
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            // Bluetooth events originate in a privileged system package on current Android.
            // The receiver still rejects every device except the explicitly selected MAC.
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(bluetoothReceiver, filter);
        }
        beginCaptureSession("service-start");
        log("=== OpenNoodoe " + BuildConfig.VERSION_NAME
                + " service started; automatic SPP reconnect is enabled ===");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (initialConnectionScheduled.compareAndSet(false, true)) {
            scheduler.schedule(() -> scheduleAutomaticConnection("service start"),
                    750, TimeUnit.MILLISECONDS);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        connectionGeneration.incrementAndGet();
        cancelFuture(lightOffFuture);
        cancelFuture(oqcStopFuture);
        stopPoiMotionInternal(false);
        clearNavigationSessionState();
        clearGroupSessionState();
        if (oqcTestActive && connected) {
            sendCommandDirect(0x12, CommandFrame.WRITE, new byte[]{0}, 0, "OQC_TEST STOP on service exit");
        }
        closeSocket();
        unregisterReceiver(bluetoothReceiver);
        commandExecutor.shutdownNow();
        scheduler.shutdownNow();
        super.onDestroy();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        listener.onSnapshot(snapshot());
        synchronized (uiLogLock) {
            for (String line : recentUiLog) {
                listener.onLogLine(line);
            }
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public Snapshot snapshot() {
        File directory = captureDirectory;
        long cachedOdometer = preferences == null ? -1
                : preferences.getLong(PREF_LAST_ODOMETER, -1);
        long cachedOdometerAt = preferences == null ? 0
                : preferences.getLong(PREF_LAST_ODOMETER_AT, 0);
        VehicleTelemetry.Reading live = telemetry.read(SystemClock.elapsedRealtime());
        boolean odometerLive = connected && live.riding != null;
        long odometer = odometerLive ? live.riding.odometer : cachedOdometer;
        String modelCode = currentDeviceInfo != null && !currentDeviceInfo.model.isEmpty()
                ? currentDeviceInfo.model
                : (preferences == null ? "" : preferences.getString(PREF_LAST_MODEL_CODE, ""));
        return new Snapshot(state, getSelectedAddress(), connected, isSelectedDevicePaired(),
                deviceInfoText, captureId,
                directory == null ? "" : directory.getAbsolutePath(),
                odometerLive ? live.riding.toString() : "Waiting for current riding data",
                oqcStatusText, meterProfileText, transferStatusText, oqcTestActive,
                preferences != null && preferences.getBoolean("notification_bridge", false),
                latestOqcData, hasValidOqcBackup(), oqcBackupPath, oqcBackupId,
                hazardStatusText, operationStateText, transferOperationClaimed.get(),
                currentDeviceInfo, odometerLive ? live.riding : null,
                connected ? live.keyOn : null, odometer,
                odometerLive ? live.receivedAt : cachedOdometerAt, odometerLive,
                modelCode,
                preferences != null && preferences.getBoolean(PREF_WELCOME_ENABLED, true),
                preferences == null ? 1 : preferences.getInt(PREF_WELCOME_STANDBY, 1),
                welcomeLightPolicy == null ? 0 : welcomeLightPolicy.activationCount(),
                galleryPaths(), gallerySentStates());
    }

    public String getSelectedAddress() {
        return preferences == null ? "" : preferences.getString(PREF_ADDRESS, "");
    }

    private String[] galleryPaths() {
        String[] paths = new String[6];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = preferences == null ? ""
                    : preferences.getString(PREF_GALLERY_PATH_PREFIX + i, "");
        }
        return paths;
    }

    private boolean[] gallerySentStates() {
        boolean[] sent = new boolean[6];
        for (int i = 0; i < sent.length; i++) {
            sent[i] = preferences != null
                    && preferences.getBoolean(PREF_GALLERY_SENT_PREFIX + i, false);
        }
        return sent;
    }

    public void selectDevice(String address) {
        if (address == null || address.isEmpty()) {
            return;
        }
        preferences.edit().putString(PREF_ADDRESS, address).apply();
        log("Selected " + address);
        if (!hasBluetoothConnectPermission()) {
            updateState("Bluetooth permission required", false);
            return;
        }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                connectSaved();
            } else {
                updateState("Waiting for Android pairing", false);
                if (!device.createBond()) {
                    log("Android rejected createBond request");
                }
            }
        } catch (IllegalArgumentException | SecurityException error) {
            log("Pairing failed: " + error.getMessage());
        }
    }

    public void connectSaved() {
        userDisconnected = false;
        requestConnection("user", true);
    }

    private void requestConnection(String reason, boolean userInitiated) {
        String address = getSelectedAddress();
        if (address.isEmpty()) {
            updateState("Select a device first", false);
            return;
        }
        if (!hasBluetoothConnectPermission()) {
            updateState("Bluetooth permission required", false);
            return;
        }
        if (connected) {
            log("Connection request ignored; SPP is already connected (" + reason + ")");
            return;
        }
        if (!userInitiated && userDisconnected) {
            log("Automatic connection suppressed after manual disconnect");
            return;
        }
        if (!connectionAttemptInProgress.compareAndSet(false, true)) {
            log("Connection request ignored; another attempt is active (" + reason + ")");
            return;
        }
        int generation = connectionGeneration.incrementAndGet();
        clearNavigationSessionState();
        clearGroupSessionState();
        closeSocket();
        commandExecutor.execute(() -> {
            try {
                connectWithRetries(address, generation);
            } finally {
                connectionAttemptInProgress.set(false);
            }
        });
    }

    public void disconnect() {
        userDisconnected = true;
        aclConnectedRecently = false;
        connectionGeneration.incrementAndGet();
        cancelFuture(lightOffFuture);
        commandExecutor.execute(() -> {
            stopOqcTestInternal("disconnect cleanup");
            stopNavigationSessionInternal("disconnect cleanup");
            stopGroupSessionInternal("disconnect cleanup");
            closeSocket();
            updateState("Disconnected by user", false);
        });
    }

    public void requestDeviceInfo() {
        sendCommand(0x05, CommandFrame.READ, new byte[0], "DEVICE_INFO");
    }

    public void requestRidingStatus() {
        queueTelemetryRead();
    }

    private void startTelemetryPolling() {
        cancelFuture(telemetryFuture);
        nextDeviceInfoRefreshAt = SystemClock.elapsedRealtime() + 30_000;
        BluetoothSocket pollingSocket = socket;
        telemetryFuture = scheduler.scheduleWithFixedDelay(() -> {
            if (!connected || socket != pollingSocket) return;
            // Expire old readings even while a transfer owns the command executor.
            notifySnapshot();
            queueTelemetryRead();
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void queueTelemetryRead() {
        if (!connected || !framedProtocol || transferOperationClaimed.get()
                || activeTransferTask >= 0 || !telemetryReadQueued.compareAndSet(false, true)) return;
        int generation = connectionGeneration.get();
        BluetoothSocket pollingSocket = socket;
        try {
            commandExecutor.execute(() -> {
                try {
                    if (!connected || generation != connectionGeneration.get()
                            || socket != pollingSocket || transferOperationClaimed.get()
                            || activeTransferTask >= 0) return;
                    sendAndAwait(0x0C, CommandFrame.READ, new byte[0], 0,
                            "RIDING_STATUS live", 1_500);
                    if (!connected || generation != connectionGeneration.get()
                            || socket != pollingSocket || transferOperationClaimed.get()) return;
                    long now = SystemClock.elapsedRealtime();
                    if (now >= nextDeviceInfoRefreshAt) {
                        nextDeviceInfoRefreshAt = now + 30_000;
                        sendAndAwait(0x05, CommandFrame.READ, new byte[0], 0,
                                "DEVICE_INFO refresh", 1_500);
                    }
                } finally {
                    telemetryReadQueued.set(false);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            telemetryReadQueued.set(false);
        }
    }

    public void requestOqcData() {
        commandExecutor.execute(() -> performOqcReadAndBackup("manual-read"));
    }

    public void armHazardSession() {
        if (!hazardSessionArmed) {
            hazardSessionArmed = true;
            log("Hazard session armed for this application process");
        }
    }

    public void writeOqcData(OqcData desired) {
        commandExecutor.execute(() -> {
            if (!requireHazardArmed("OQC WRITE")) {
                return;
            }
            if (desired == null || !hasValidOqcBackup()) {
                setHazardStatus("OQC WRITE 차단: 현재 연결에서 성공한 READ 및 이중 백업이 필요합니다.");
                return;
            }
            if (!verifyStationary("OQC WRITE")) {
                return;
            }
            byte[] prior = latestOqcRaw == null ? null : latestOqcRaw.clone();
            OqcData fresh = performOqcReadAndBackup("pre-write");
            if (fresh == null || prior == null || !Arrays.equals(prior, latestOqcRaw)) {
                setHazardStatus("OQC WRITE 차단: 직전 백업 이후 장치 데이터가 달라졌습니다. 새 READ 내용을 검토하십시오.");
                return;
            }
            byte[] writePayload;
            try {
                writePayload = desired.toWritePayload();
            } catch (IllegalArgumentException error) {
                setHazardStatus("OQC WRITE 입력 오류: " + error.getMessage());
                return;
            }
            log("HAZARD OQC_DATA_WRITE payload=" + ByteCodec.hex(writePayload));
            byte[] reply = sendAndAwait(0x11, CommandFrame.WRITE, writePayload, 0,
                    "OQC_DATA_WRITE", 8_000);
            if (!replyStatusOk(reply)) {
                setHazardStatus("OQC WRITE 실패: status=" + status(reply == null ? new byte[0] : reply));
                return;
            }
            OqcData readback = performOqcReadAndBackup("post-write");
            if (readback == null || !desired.writableFieldsEqual(readback)) {
                setHazardStatus("OQC WRITE 응답은 성공했지만 재읽기 비교가 실패했습니다. 추가 쓰기를 중단하십시오.");
                return;
            }
            setHazardStatus("OQC WRITE 및 전체 필드 재읽기 비교 완료. 백업: " + oqcBackupPath);
        });
    }

    public void factoryReset() {
        commandExecutor.execute(() -> {
            if (!requireHazardArmed("FACTORY RESET")) {
                return;
            }
            if (!verifyStationary("FACTORY RESET")) {
                return;
            }
            byte[] reply = sendAndAwait(0x0F, CommandFrame.WRITE, new byte[0], 0,
                    "FACTORY_RESET", 8_000);
            if (replyStatusOk(reply)) {
                setHazardStatus("FACTORY RESET 명령 수락. 연결 종료 또는 계기판 재시작 가능성이 있습니다.");
            } else {
                setHazardStatus("FACTORY RESET 실패: status="
                        + status(reply == null ? new byte[0] : reply));
            }
        });
    }

    public void installFirmware(Uri source, int major, int minor) {
        commandExecutor.execute(() -> {
            try {
                if (!hazardSessionArmed) {
                    throw new IllegalStateException("hazard session is not armed");
                }
                requireHazardConnection("FIRMWARE");
                if (!verifyStationary("FIRMWARE")) {
                    return;
                }
                OtaPackageInspector.FirmwarePackage firmware =
                        OtaPackageInspector.inspectFirmware(this, source, major, minor);
                if (compareVersion(major, minor, currentDeviceInfo.firmwareMajor,
                        currentDeviceInfo.firmwareMinor) < 0) {
                    throw new IllegalArgumentException("firmware downgrade is blocked: current="
                            + currentDeviceInfo.firmwareMajor + "." + currentDeviceInfo.firmwareMinor
                            + ", target=" + major + "." + minor);
                }
                recordHazardPackage("firmware", firmware.displayName, firmware.sha256,
                        "target=" + major + "." + minor + " bytes=" + firmware.data.length);
                setHazardStatus("펌웨어 검증 완료. 전송 시작: " + firmware.displayName);
                transferFirmware(firmware);
            } catch (Exception error) {
                setHazardStatus("펌웨어 설치 차단/실패: " + error.getMessage());
            }
        });
    }

    public void installResource(Uri source) {
        submitInstrumentedTransfer("RESOURCE PACKAGE", () -> {
            try {
                if (!hazardSessionArmed) {
                    throw new IllegalStateException("hazard session is not armed");
                }
                requireHazardConnection("RESOURCE");
                if (!verifyStationary("RESOURCE")) {
                    return;
                }
                OtaPackageInspector.ResourcePackage resource =
                        OtaPackageInspector.inspectResource(this, source);
                if (compareVersion(resource.major, resource.minor,
                        currentDeviceInfo.resourceMajor, currentDeviceInfo.resourceMinor) < 0) {
                    throw new IllegalArgumentException("resource downgrade is blocked: current="
                            + currentDeviceInfo.resourceMajor + "." + currentDeviceInfo.resourceMinor
                            + ", target=" + resource.major + "." + resource.minor);
                }
                OqcData oqc = performOqcReadAndBackup("resource-preflight");
                if (oqc == null) {
                    throw new IllegalStateException("OQC resource identity could not be read and backed up");
                }
                if (resource.resourceId != oqc.resourceId
                        || resource.languagePackId != oqc.languagePack) {
                    throw new IllegalArgumentException("resource identity mismatch: package resource/lang="
                            + resource.resourceId + "/" + resource.languagePackId
                            + ", meter=" + oqc.resourceId + "/" + oqc.languagePack);
                }
                recordHazardPackage("resource", resource.displayName, resource.sha256,
                        "target=" + resource.major + "." + resource.minor
                                + " resourceId=" + resource.resourceId
                                + " languagePack=" + resource.languagePackId
                                + " files=" + resource.files.size()
                                + " bytes=" + resource.totalBytes);
                setHazardStatus("리소스 팩 검증 완료. " + resource.files.size() + "개 파일 전송 시작");
                boolean complete = transferFiles(LOCATION_RESOURCE, resource.contentId, resource.files,
                        "RESOURCE " + resource.major + "." + resource.minor);
                setHazardStatus(complete
                        ? "리소스 팩 전송 완료 응답 수신. 계기판 처리가 끝날 때까지 전원을 유지하십시오."
                        : "리소스 팩 전송 실패. 추가 작업을 중단하고 로그를 보존하십시오.");
            } catch (Exception error) {
                setHazardStatus("리소스 설치 차단/실패: " + error.getMessage());
            }
        });
    }

    public void requestMeterProfile() {
        sendCommand(0x16, CommandFrame.READ, new byte[0], "GET_METER_PROFILE");
    }

    public void sendNotificationTest() {
        sendAppNotification("io.opennoodoe.app", "OpenNoodoe", "OpenNoodoe notification test");
    }

    public void sendAppNotification(String appId, String appName, String text) {
        sendCommand(0x15, CommandFrame.WRITE,
                CandidatePayloads.appNotification(appId, appName, text), "APP_NOTIFICATION");
    }

    public void sendCall(int callStatus, String caller) {
        sendCommand(0x13, CommandFrame.WRITE, CandidatePayloads.call(callStatus, caller), "CALL_TEST");
    }

    public void sendSms(String caller, String text) {
        sendCommand(0x14, CommandFrame.WRITE, CandidatePayloads.sms(caller, text), "SMS_TEST");
    }

    public void setNotificationBridge(boolean enabled) {
        preferences.edit().putBoolean("notification_bridge", enabled).apply();
        log("Notification bridge " + (enabled ? "enabled" : "disabled"));
        notifySnapshot();
    }

    public void sendWeather(String location, int aqi, int currentTemp, int condition,
            int[] forecastTemps, int[] forecastConditions) {
        try {
            byte[] payload = CandidatePayloads.weather(location, aqi, currentTemp, condition,
                    forecastTemps, forecastConditions);
            sendCommand(0x09, CommandFrame.WRITE, payload,
                    "WEATHER_TEST wire_unit=F current=" + currentTemp
                            + " forecast=" + Arrays.toString(forecastTemps));
        } catch (IllegalArgumentException error) {
            setTransferStatus("Weather rejected locally: " + error.getMessage());
        }
    }

    public void sendPreferences(String username, int brightness, boolean breathing,
            boolean metric, boolean twentyFourHour) {
        int shutdown = preferences.getInt(PREF_WELCOME_STANDBY, 1);
        preferences.edit()
                .putString(PREF_VEHICLE_NAME, username == null ? "" : username)
                .putInt(PREF_BRIGHTNESS, brightness)
                .putBoolean(PREF_WELCOME_ENABLED, breathing)
                .putBoolean(PREF_METRIC, metric)
                .putBoolean(PREF_TWENTY_FOUR_HOUR, twentyFourHour)
                .putBoolean(PREF_VEHICLE_SETTINGS_CONFIGURED, true)
                .apply();
        welcomeLightPolicy.configure(breathing, shutdown);
        sendPreferencePacket(username, brightness, breathing, metric, twentyFourHour, shutdown,
                "PREFERENCES_SAFE_SUBSET");
        notifySnapshot();
    }

    public void configureWelcomeLight(boolean enabled, int shutdown) {
        if (!isSupportedShutdownTime(shutdown)) {
            throw new IllegalArgumentException("Unsupported Bluetooth standby value " + shutdown);
        }
        preferences.edit()
                .putBoolean(PREF_WELCOME_ENABLED, enabled)
                .putInt(PREF_WELCOME_STANDBY, shutdown)
                .putBoolean(PREF_VEHICLE_SETTINGS_CONFIGURED, true)
                .apply();
        welcomeLightPolicy.configure(enabled, shutdown);
        if (!enabled) {
            cancelFuture(lightOffFuture);
            lightOffFuture = null;
            if (connected) {
                sendWelcomeLightState(false, "WELCOME_LIGHT disabled");
            }
        }
        sendStoredPreferences("WELCOME_LIGHT_SETTINGS");
        log("Welcome light " + (enabled ? "enabled" : "disabled")
                + "; Bluetooth standby=" + shutdownTimeName(shutdown));
        notifySnapshot();
    }

    private void sendPreferencePacket(String username, int brightness, boolean breathing,
            boolean metric, boolean twentyFourHour, int shutdown, String label) {
        sendCommand(0x04, CommandFrame.WRITE,
                CandidatePayloads.preferences(metric ? 0 : 1, metric ? 0 : 1,
                        twentyFourHour ? 1 : 0, breathing, brightness, 0xFF, 12,
                        shutdown, username, 0xFF), label);
    }

    public void sendNavigation(int distance, int blockCount, int icon, boolean leftDriving,
            boolean nightMode, int speedLimit, boolean nearCamera, int cameraDistance) {
        commandExecutor.execute(() -> {
            if (!startNavigationSessionInternal()) {
                return;
            }
            boolean sent = sendCommandDirect(0x08, CommandFrame.NOTIFY,
                    navigationPayload(distance, blockCount, icon, leftDriving, nightMode,
                            speedLimit, nearCamera, cameraDistance, 0, 0), 0,
                    "NAVIGATION_METADATA_TEST");
            setTransferStatus(sent
                    ? "Navigation metadata sent; DATA session remains active"
                    : "Navigation metadata failed");
        });
    }

    public void startNavigationSession() {
        commandExecutor.execute(this::startNavigationSessionInternal);
    }

    public void stopNavigationSession() {
        commandExecutor.execute(() -> stopNavigationSessionInternal("user"));
    }

    public void resetNavigationSession() {
        commandExecutor.execute(() -> resetNavigationSessionInternal("user"));
    }

    public void sendNavigationImage(String currentRoad, String nextRoad, int distance,
            int icon, boolean leftDriving, boolean nightMode, int speedLimit,
            boolean nearCamera, int cameraDistance, int color) {
        commandExecutor.execute(() -> {
            ContentGenerator.NavigationRoadFiles roads = ContentGenerator.navigationRoads(
                    currentRoad, nextRoad, nightMode);
            if (!startNavigationSessionInternal()) {
                return;
            }
            int currentRoadFileId = nextNavigationFileId();
            int currentTransferId = nextNavigationTransferId();
            setTransferStatus("Navigation: current-road PNG fileId=" + currentRoadFileId);
            if (!transferOne(navigationTaskId, currentTransferId, roads.currentRoad,
                    currentRoadFileId, true)) {
                resetNavigationSessionInternal("current-road transfer failure");
                return;
            }
            int nextRoadFileId = nextNavigationFileId();
            int nextTransferId = nextNavigationTransferId();
            setTransferStatus("Navigation: next-road PNG fileId=" + nextRoadFileId);
            if (!transferOne(navigationTaskId, nextTransferId, roads.nextRoad,
                    nextRoadFileId, true)) {
                resetNavigationSessionInternal("next-road transfer failure");
                return;
            }
            boolean updated = sendCommandDirect(0x08, CommandFrame.NOTIFY,
                    navigationPayload(distance, 1, icon, leftDriving, nightMode,
                            speedLimit, nearCamera, cameraDistance,
                            currentRoadFileId, nextRoadFileId), 0,
                    "NAVIGATION_ROAD_TEXT currentFileId=" + currentRoadFileId
                            + " nextFileId=" + nextRoadFileId);
            setTransferStatus(updated
                    ? "Navigation road layers updated; DATA session remains active (current="
                            + currentRoadFileId + ", next=" + nextRoadFileId + ")"
                    : "Navigation road-layer command failed; session remains active");
        });
    }

    public void sendPoi(int type, int x, int y, int placeId) {
        commandExecutor.execute(() -> {
            if (!requireForeground(2, "Around Me / POI")) {
                return;
            }
            try {
                byte[] payload = CandidatePayloads.poi(type, x, y, placeId);
                boolean sent = sendCommandDirect(0x06, CommandFrame.NOTIFY, payload, 0,
                        "POI type=" + type + " x=" + x + " y=" + y + " place=" + placeId);
                setTransferStatus(sent ? "POI update sent" : "POI update failed");
            } catch (IllegalArgumentException error) {
                setTransferStatus("POI rejected locally: " + error.getMessage());
            }
        });
    }

    public void sendPoiPattern(int radius) {
        commandExecutor.execute(() -> sendPoiPatternInternal(0, radius, "POI pattern", -1));
    }

    public void clearPoiPattern() {
        commandExecutor.execute(() -> {
            stopPoiMotionInternal(false);
            if (!requireForeground(2, "Around Me / POI")) {
                return;
            }
            boolean complete = true;
            for (int type = 1; type <= 9; type++) {
                complete &= sendPoiFrame(type, -1, -1, 0,
                        "POI CLEAR type=" + type);
            }
            setTransferStatus(complete ? "All nine POI types hidden" : "POI clear incomplete");
        });
    }

    public void startPoiMotion(int radius, int intervalMs) {
        commandExecutor.execute(() -> {
            stopPoiMotionInternal(false);
            if (!requireForeground(2, "Around Me / POI")) {
                return;
            }
            int safeRadius = Math.max(0, Math.min(Math.abs(radius), 222));
            int safeInterval = Math.max(500, Math.min(intervalMs, 10_000));
            int generation = poiMotionGeneration.incrementAndGet();
            poiMotionStep = 0;
            poiMotionFuture = scheduler.scheduleAtFixedRate(
                    () -> {
                        if (generation != poiMotionGeneration.get()) {
                            return;
                        }
                        if (poiMotionTickQueued.compareAndSet(false, true)) {
                            commandExecutor.execute(() -> {
                                try {
                                    if (generation == poiMotionGeneration.get()) {
                                        sendPoiPatternInternal(poiMotionStep++, safeRadius,
                                                "POI motion", generation);
                                    }
                                } finally {
                                    poiMotionTickQueued.set(false);
                                }
                            });
                        }
                    },
                    0, safeInterval, TimeUnit.MILLISECONDS);
            setTransferStatus("POI motion active: radius=" + safeRadius
                    + ", interval=" + safeInterval + " ms");
        });
    }

    public void stopPoiMotion() {
        poiMotionGeneration.incrementAndGet();
        commandExecutor.execute(() -> stopPoiMotionInternal(true));
    }

    public void startGroupSession(int timeoutSeconds) {
        commandExecutor.execute(() -> {
            int timeout = Math.max(30, Math.min(timeoutSeconds, 3600));
            if (groupTaskId >= 0) {
                stopGroupSessionInternal("restart");
            }
            int task = nextTaskId();
            setTransferStatus("GROUP: starting DATA task " + task);
            byte[] reply = sendAndAwait(0x0A, CommandFrame.WRITE,
                    FileTransferPayloads.groupTask(task, LOCATION_GROUP,
                            FileTransferPayloads.BEGIN, timeout), 0,
                    "GROUP BEGIN timeout=" + timeout, FILE_TASK_TIMEOUT_MS);
            if (!replyStatusOk(reply)) {
                setTransferStatus("GROUP BEGIN failed, status="
                        + status(reply == null ? new byte[0] : reply));
                return;
            }
            groupTaskId = task;
            groupTransferId = 1;
            groupContinueFuture = scheduler.scheduleAtFixedRate(
                    () -> commandExecutor.execute(() -> sendGroupContinue(task)),
                    10, 10, TimeUnit.SECONDS);
            groupStopFuture = scheduler.schedule(
                    () -> commandExecutor.execute(() -> stopGroupSessionInternal("timeout")),
                    timeout, TimeUnit.SECONDS);
            setTransferStatus("GROUP DATA task " + task + " active for " + timeout + " seconds");
        });
    }

    public void stopGroupSession() {
        commandExecutor.execute(() -> stopGroupSessionInternal("user"));
    }

    public void addGroupMember(Uri avatarSource, int memberId, int x, int y) {
        commandExecutor.execute(() -> {
            if (!requireGroupSession("add member")) {
                return;
            }
            if (avatarSource == null) {
                setTransferStatus("GROUP: select a member image first");
                return;
            }
            try {
                ContentGenerator.GeneratedFile avatar =
                        ContentGenerator.groupAvatar(this, avatarSource);
                int transferId = nextGroupTransferId();
                setTransferStatus("GROUP member " + memberId + ": sending 60x60 avatar");
                if (!transferOne(groupTaskId, transferId, avatar, memberId, true)) {
                    setTransferStatus("GROUP member " + memberId + ": avatar failed");
                    return;
                }
                sendGroupMemberUpdateInternal(memberId, x, y, "ADD/UPDATE");
            } catch (Exception error) {
                setTransferStatus("GROUP member failed: " + error.getMessage());
            }
        });
    }

    public void addGroupMemberPattern(Uri avatarSource, int firstMemberId, int count,
            int radius) {
        commandExecutor.execute(() -> {
            if (!requireGroupSession("add member pattern")) {
                return;
            }
            int safeCount = Math.max(1, Math.min(count, 8));
            if (firstMemberId < 0 || firstMemberId + safeCount - 1 > 64) {
                setTransferStatus("GROUP pattern IDs must remain within 0..64");
                return;
            }
            if (avatarSource == null) {
                setTransferStatus("GROUP: select a member image first");
                return;
            }
            int safeRadius = Math.max(0, Math.min(Math.abs(radius), 222));
            try {
                ContentGenerator.GeneratedFile avatar =
                        ContentGenerator.groupAvatar(this, avatarSource);
                for (int index = 0; index < safeCount; index++) {
                    int memberId = firstMemberId + index;
                    int transferId = nextGroupTransferId();
                    setTransferStatus("GROUP pattern avatar " + (index + 1) + "/" + safeCount);
                    if (!transferOne(groupTaskId, transferId, avatar, memberId, true)) {
                        setTransferStatus("GROUP pattern stopped at member " + memberId);
                        return;
                    }
                    int[] xy = radialCoordinate(index, safeCount, safeRadius, 0);
                    if (!sendGroupMemberFrame(memberId, xy[0], xy[1], "PATTERN ADD")) {
                        setTransferStatus("GROUP pattern coordinate failed at member " + memberId);
                        return;
                    }
                }
                setTransferStatus("GROUP: added " + safeCount + " members from ID "
                        + firstMemberId);
            } catch (Exception error) {
                setTransferStatus("GROUP pattern failed: " + error.getMessage());
            }
        });
    }

    public void updateGroupMember(int memberId, int x, int y) {
        commandExecutor.execute(() -> {
            if (requireGroupSession("update member")) {
                sendGroupMemberUpdateInternal(memberId, x, y, "POSITION");
            }
        });
    }

    public void removeGroupMember(int memberId) {
        commandExecutor.execute(() -> {
            if (!requireGroupSession("remove member")) {
                return;
            }
            try {
                sendGroupMemberUpdateInternal(memberId, -1, -1, "HIDE");
                int transferId = nextGroupTransferId();
                byte[] reply = sendAndAwait(0x0B, CommandFrame.WRITE,
                        FileTransferPayloads.controlByFileId(groupTaskId,
                                FileTransferPayloads.DELETE, transferId, memberId, 0, 0),
                        0, "GROUP member " + memberId + " DELETE", 6_000);
                setTransferStatus(replyStatusOk(reply)
                        ? "GROUP member " + memberId + " removed"
                        : "GROUP member delete status="
                                + status(reply == null ? new byte[0] : reply));
            } catch (IllegalArgumentException error) {
                setTransferStatus("GROUP member rejected locally: " + error.getMessage());
            }
        });
    }

    public void startGroupMotion(int firstMemberId, int count, int radius, int intervalMs) {
        commandExecutor.execute(() -> {
            if (!requireGroupSession("start member motion")) {
                return;
            }
            int safeCount = Math.max(1, Math.min(count, 8));
            if (firstMemberId < 0 || firstMemberId + safeCount - 1 > 64) {
                setTransferStatus("GROUP motion IDs must remain within 0..64");
                return;
            }
            int safeRadius = Math.max(0, Math.min(Math.abs(radius), 222));
            int safeInterval = Math.max(500, Math.min(intervalMs, 10_000));
            stopGroupMotionInternal(false);
            int generation = groupMotionGeneration.incrementAndGet();
            groupMotionStep = 0;
            int activeTask = groupTaskId;
            groupMotionFuture = scheduler.scheduleAtFixedRate(
                    () -> {
                        if (generation != groupMotionGeneration.get()) {
                            return;
                        }
                        if (groupMotionTickQueued.compareAndSet(false, true)) {
                            commandExecutor.execute(() -> {
                                try {
                                    if (generation != groupMotionGeneration.get()
                                            || groupTaskId != activeTask
                                            || !requireForeground(1, "Group radar")) {
                                        stopGroupMotionInternal(false);
                                        return;
                                    }
                                    boolean complete = true;
                                    int step = groupMotionStep++;
                                    for (int index = 0; index < safeCount; index++) {
                                        if (generation != groupMotionGeneration.get()) {
                                            return;
                                        }
                                        int[] xy = radialCoordinate(
                                                index, safeCount, safeRadius, step);
                                        complete &= sendGroupMemberFrame(firstMemberId + index,
                                                xy[0], xy[1], "MOTION");
                                    }
                                    if (!complete) {
                                        stopGroupMotionInternal(false);
                                        setTransferStatus(
                                                "GROUP motion stopped after send failure");
                                    }
                                } finally {
                                    groupMotionTickQueued.set(false);
                                }
                            });
                        }
                    }, 0, safeInterval, TimeUnit.MILLISECONDS);
            setTransferStatus("GROUP motion active: IDs " + firstMemberId + ".."
                    + (firstMemberId + safeCount - 1));
        });
    }

    public void stopGroupMotion() {
        groupMotionGeneration.incrementAndGet();
        commandExecutor.execute(() -> stopGroupMotionInternal(true));
    }

    public void installGallery(Uri source, int slot) {
        submitInstrumentedTransfer("GALLERY slot " + (slot + 1), () -> {
            try {
                ContentGenerator.GeneratedFile file = ContentGenerator.gallery(this, source, slot);
                GallerySlotStore committed = new GallerySlotStore(new File(getFilesDir(), "gallery-committed"));
                List<ContentGenerator.GeneratedFile> files = new ArrayList<>();
                StringBuilder names = new StringBuilder();
                for (int index = 0; index < 6; index++) {
                    preserveCommittedGallerySlot(index, committed);
                    byte[] previous = index == slot ? file.data : committed.load(index);
                    if (previous == null) continue;
                    ContentGenerator.GeneratedFile item = new ContentGenerator.GeneratedFile(previous, ".jpg");
                    files.add(item);
                    names.append(item.name);
                }
                byte[] contentId = ContentGenerator.md5(names.toString());
                if (transferFiles(LOCATION_GALLERY, contentId,
                        files, "GALLERY slot " + (slot + 1))) {
                    committed.save(slot, file.data);
                    cacheGalleryImage(source, slot, true);
                }
            } catch (Exception error) {
                setTransferStatus("Gallery failed: " + error.getMessage());
            }
        });
    }

    public void stageGalleryImage(Uri source, int slot) {
        commandExecutor.execute(() -> {
            try {
                cacheGalleryImage(source, slot, false);
                log("Gallery slot " + (slot + 1) + " cached locally");
                notifySnapshot();
            } catch (IOException error) {
                log("Gallery cache failed: " + error.getMessage());
            }
        });
    }

    private void cacheGalleryImage(Uri source, int slot, boolean sent) throws IOException {
        if (source == null || slot < 0 || slot >= 6) {
            throw new IOException("invalid gallery slot or image");
        }
        File directory = new File(getFilesDir(), "gallery");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("cannot create gallery cache");
        }
        File target = new File(directory, "slot-" + slot + ".image");
        if (!sent) {
            preserveCommittedGallerySlot(slot,
                    new GallerySlotStore(new File(getFilesDir(), "gallery-committed")));
        }
        try (InputStream in = getContentResolver().openInputStream(source)) {
            GallerySlotStore.replace(target, in);
        }
        preferences.edit()
                .putString(PREF_GALLERY_PATH_PREFIX + slot, target.getAbsolutePath())
                .putBoolean(PREF_GALLERY_SENT_PREFIX + slot, sent)
                .apply();
        notifySnapshot();
    }

    private void preserveCommittedGallerySlot(int slot, GallerySlotStore committed) throws IOException {
        if (committed.load(slot) != null || !preferences.getBoolean(PREF_GALLERY_SENT_PREFIX + slot, false)) return;
        String path = preferences.getString(PREF_GALLERY_PATH_PREFIX + slot, "");
        if (!path.isEmpty() && new File(path).isFile()) {
            committed.save(slot, ContentGenerator.gallery(this, Uri.fromFile(new File(path)), slot).data);
        }
    }

    public void installGeneratedCreation(int location, String title, String subtitle, int color) {
        if (!isSafeCreationLocation(location)) {
            log("Creation blocked: location 0x" + Integer.toHexString(location) + " is not allowed");
            return;
        }
        if (location != LOCATION_POI) {
            setTransferStatus("Generated background-only creation is blocked at location 0x"
                    + Integer.toHexString(location)
                    + ": V5.16 requires location-specific widgets; use a verified reference bundle");
            return;
        }
        installV516PoiRenderer(title, subtitle, color);
    }

    public void installV516PoiRenderer(String title, String subtitle, int color) {
        submitInstrumentedTransfer("V5.16 POI CREATION", () -> {
            try {
                ContentGenerator.GeneratedBundle bundle =
                        ContentGenerator.aroundMeV516(title, subtitle, color);
                transferFiles(LOCATION_POI, bundle.contentId, bundle.files,
                        "V5.16 POI RadarWidget+LocationsWidget");
            } catch (Exception error) {
                setTransferStatus("V5.16 POI creation failed: " + error.getMessage());
            }
        });
    }

    public void installV516StaticPoiRenderer(String title, String subtitle, int color) {
        submitInstrumentedTransfer("V5.16 STATIC POI CREATION", () -> {
            try {
                ContentGenerator.GeneratedBundle bundle =
                        ContentGenerator.aroundMeStaticV516(title, subtitle, color);
                transferFiles(LOCATION_POI, bundle.contentId, bundle.files,
                        "V5.16 POI LocationsWidget without RadarWidget");
            } catch (Exception error) {
                setTransferStatus("V5.16 static POI creation failed: " + error.getMessage());
            }
        });
    }

    public void installV516ForecastWeatherRenderer(int color) {
        submitInstrumentedTransfer("V5.16 FORECAST WEATHER CREATION", () -> {
            try {
                ContentGenerator.GeneratedBundle bundle =
                        ContentGenerator.weatherForecastV516(color);
                transferFiles(LOCATION_WEATHER, bundle.contentId, bundle.files,
                        "V5.16 Weather ForecastWidget diagnostic");
            } catch (Exception error) {
                setTransferStatus("V5.16 ForecastWidget creation failed: "
                        + error.getMessage());
            }
        });
    }

    public void installReferenceCreation(int location) {
        final String assetDirectory;
        if (location == LOCATION_CLOCK) {
            assetDirectory = "reference_creations/clock";
        } else if (location == LOCATION_SPEEDOMETER) {
            assetDirectory = "reference_creations/speedometer";
        } else if (location == LOCATION_POI) {
            installV516PoiRenderer("OpenNoodoe POI", "V5.16 renderer", 0x00825F);
            return;
        } else if (location == LOCATION_GROUP) {
            assetDirectory = "reference_creations/group";
        } else {
            setTransferStatus("No packaged reference bundle for this location");
            return;
        }
        submitInstrumentedTransfer("REFERENCE CREATION 0x" + Integer.toHexString(location), () -> {
            try {
                if (location == LOCATION_GROUP && groupTaskId >= 0) {
                    stopGroupSessionInternal("renderer install");
                }
                ContentGenerator.GeneratedBundle bundle =
                        ContentGenerator.referenceCreation(this, assetDirectory);
                transferFiles(location, bundle.contentId, bundle.files,
                        "REFERENCE CREATION 0x" + Integer.toHexString(location));
            } catch (Exception error) {
                setTransferStatus("Reference creation failed: " + error.getMessage());
            }
        });
    }

    public void installTheme(String themeId) {
        submitInstrumentedTransfer("THEME LIBRARY", () -> {
            try {
                int location = ThemeRepository.location(this, themeId);
                ContentGenerator.GeneratedBundle bundle = ThemeRepository.load(this, themeId);
                transferFiles(location, bundle.contentId, bundle.files,
                        "THEME " + themeId);
            } catch (Exception error) {
                setTransferStatus("Theme load failed: " + error.getMessage());
            }
        });
    }

    public void createAndInstallTheme(int location, String templateId, Uri background,
            ThemeAuthoringOptions options) {
        if (!io.opennoodoe.app.protocol.ProductCommandPolicy.supportsTheme(location)) {
            setTransferStatus("Unsupported theme location");
            return;
        }
        submitInstrumentedTransfer("CUSTOM THEME", () -> {
            try {
                if (ThemeRepository.location(this, templateId) != location) {
                    throw new IOException("template type does not match selected destination");
                }
                ContentGenerator.GeneratedBundle bundle =
                        ContentGenerator.customTheme(this, ThemeRepository.load(this, templateId), background, options);
                String themeId = ThemeRepository.save(this, location, bundle);
                log("CUSTOM_THEME_SAVED id=" + themeId + " location=0x"
                        + Integer.toHexString(location) + " contentId="
                        + ByteCodec.hex(bundle.contentId) + " files=" + bundle.files.size());
                transferFiles(location, bundle.contentId, bundle.files,
                        "CUSTOM THEME " + themeId);
            } catch (Exception error) {
                setTransferStatus("Custom theme failed: " + error.getMessage());
            }
        });
    }

    public void importAndInstallTheme(int location, Uri archive) {
        importTheme(location, archive, true);
    }

    public void saveImportedTheme(int location, Uri archive) {
        importTheme(location, archive, false);
    }

    private void importTheme(int location, Uri archive, boolean apply) {
        if (!io.opennoodoe.app.protocol.ProductCommandPolicy.supportsTheme(location)) {
            setTransferStatus("Unsupported imported theme location");
            return;
        }
        commandExecutor.execute(() -> {
            try {
                ContentGenerator.GeneratedBundle bundle =
                        ContentGenerator.importTransmissionBundle(this, archive);
                int detectedLocation = ContentGenerator.detectCreationLocation(bundle);
                if (detectedLocation != location) {
                    throw new IOException("bundle type does not match selected destination");
                }
                String themeId = ThemeRepository.save(this, location, bundle,
                        importedThemeTitle(archive), "imported-bundle", "");
                log("IMPORTED_THEME_SAVED id=" + themeId + " location=0x"
                        + Integer.toHexString(location) + " contentId="
                        + ByteCodec.hex(bundle.contentId) + " files=" + bundle.files.size());
                if (apply) {
                    submitInstrumentedTransfer("IMPORTED THEME", () ->
                            transferFiles(location, bundle.contentId, bundle.files,
                                    "IMPORTED THEME " + themeId));
                } else {
                    setTransferStatus("Theme saved: " + importedThemeTitle(archive));
                }
            } catch (Exception error) {
                setTransferStatus("Theme import failed: " + error.getMessage());
            }
        });
    }

    private String importedThemeTitle(Uri archive) {
        String name = archive.getLastPathSegment();
        if ("content".equals(archive.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(archive,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            } catch (RuntimeException ignored) { }
        }
        if (name == null || name.isEmpty()) return "Imported theme";
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    public void removeContent(int location, byte[] contentId) {
        if (!isSafeCreationLocation(location) && location != LOCATION_GALLERY) {
            log("Remove blocked for unsafe location");
            return;
        }
        submitInstrumentedTransfer("REMOVE 0x" + Integer.toHexString(location), () -> {
            int task = nextTaskId();
            beginActiveTransfer(task, FileTransferPayloads.TYPE_FILE, location, 0, contentId);
            setOperationState("REMOVING", "task=" + task + " location=0x"
                    + Integer.toHexString(location));
            byte[] reply = sendAndAwait(0x0A, CommandFrame.WRITE,
                    FileTransferPayloads.negotiate(task, FileTransferPayloads.TYPE_FILE,
                            location, 0, FileTransferPayloads.REMOVE, contentId), 0,
                    "CONTENT_REMOVE", FILE_TASK_TIMEOUT_MS);
            int result = status(reply == null ? new byte[0] : reply);
            setTransferStatus(replyStatusOk(reply)
                    ? "Creation location 0x" + Integer.toHexString(location) + " removed"
                    : "Creation remove failed: status=" + result + " "
                            + transferStatusName(result));
            setOperationState(replyStatusOk(reply) ? "COMPLETE" : "FAILED",
                    "REMOVE status=" + result + " " + transferStatusName(result));
        });
    }

    public void removeCreation(int location) {
        removeContent(location, new byte[16]);
    }

    public void resetOperationKeepingConnection() {
        if (transferOperationClaimed.get()) {
            if (transferResetRequested.compareAndSet(false, true)) {
                setOperationState("RESET_REQUESTED",
                        "waiting for the current protocol boundary; SPP remains connected");
                setTransferStatus("RESET requested; current transfer will stop at the next safe boundary");
            } else {
                log("DUPLICATE: RESET request ignored; reset is already pending");
            }
            return;
        }
        commandExecutor.execute(() -> {
            stopPoiMotionInternal(false);
            stopGroupSessionInternal("session-preserving operation reset");
            stopOqcTestInternal("session-preserving operation reset");
            clearPendingReply();
            clearActiveTransfer();
            setOperationState("IDLE", "local operation state cleared; SPP session preserved");
            setTransferStatus("Operation state reset; Bluetooth SPP connection preserved");
        });
    }

    public void startOqcTest() {
        commandExecutor.execute(() -> {
            byte[] reply = sendAndAwait(0x12, CommandFrame.WRITE, new byte[]{1}, 0,
                    "OQC_TEST START", 5_000);
            if (replyStatusOk(reply)) {
                oqcTestActive = true;
                cancelFuture(oqcStopFuture);
                lastOqcDisplayAt = 0;
                lastOqcFlags = -1;
                lastOqcLight = -1;
                suppressedOqcSamples = 0;
                oqcSeenFlags = 0;
                oqcLightMinimum = Long.MAX_VALUE;
                oqcLightMaximum = Long.MIN_VALUE;
                oqcSampleCount = 0;
                oqcStopFuture = scheduler.schedule(this::stopOqcTest, 30, TimeUnit.SECONDS);
                oqcStatusText = "mode=ACTIVE; waiting for OQC samples; auto STOP in 30 seconds";
                notifySnapshot();
            }
        });
    }

    public void stopOqcTest() {
        commandExecutor.execute(() -> stopOqcTestInternal("user/timeout"));
    }

    public void beginCaptureSession() {
        beginCaptureSession("user");
    }

    public void markObservation(String observation) {
        log("OBSERVATION: " + observation);
        if (!transferOperationClaimed.get()
                && operationStateText.startsWith("VALIDATION_PENDING")) {
            setOperationState("OBSERVED", observation);
        }
    }

    public void syncClock() {
        syncClock(Calendar.getInstance());
    }

    public void syncClock(Calendar time) {
        Calendar selected = time == null ? Calendar.getInstance() : (Calendar) time.clone();
        sendCommand(0x02, CommandFrame.WRITE, buildMobileStatus(selected), "MOBILE_STATUS");
    }

    public void setBreathingLight(boolean enabled) {
        setBreathingLight(enabled, 120);
    }

    public void setBreathingLight(boolean enabled, int durationSeconds) {
        int safeDuration = Math.max(10, Math.min(durationSeconds, 120));
        sendCommand(0x0E, CommandFrame.WRITE, new byte[]{(byte) (enabled ? 1 : 0)},
                enabled ? "BREATHING_LIGHT ON" : "BREATHING_LIGHT OFF");
        if (enabled) {
            cancelFuture(lightOffFuture);
            lightOffFuture = scheduler.schedule(() -> setBreathingLight(false), safeDuration,
                    TimeUnit.SECONDS);
        } else {
            cancelFuture(lightOffFuture);
            lightOffFuture = null;
        }
    }

    private void onWelcomeConnectionEstablished() {
        WelcomeLightPolicy.Action action = welcomeLightPolicy.onConnected();
        preferences.edit().putInt(PREF_WELCOME_COUNT,
                welcomeLightPolicy.activationCount()).apply();
        if (action == WelcomeLightPolicy.Action.TURN_ON) {
            sendWelcomeLightState(true, "WELCOME_LIGHT automatic");
            cancelFuture(lightOffFuture);
            lightOffFuture = scheduler.schedule(this::onWelcomeLightTimeout,
                    WelcomeLightPolicy.LIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log("Automatic welcome light scheduled for 120 seconds; activation "
                    + welcomeLightPolicy.activationCount() + "/"
                    + WelcomeLightPolicy.MAX_ACTIVATIONS);
        } else if (action == WelcomeLightPolicy.Action.TURN_OFF) {
            sendWelcomeLightState(false, "WELCOME_LIGHT activation limit");
            log("Automatic welcome light blocked after three activations");
        } else {
            log("Automatic welcome light not requested: enabled="
                    + preferences.getBoolean(PREF_WELCOME_ENABLED, true)
                    + " standby=" + shutdownTimeName(
                    preferences.getInt(PREF_WELCOME_STANDBY, 1)));
        }
        notifySnapshot();
    }

    private void onWelcomeLightTimeout() {
        if (welcomeLightPolicy.onTimeout() == WelcomeLightPolicy.Action.TURN_OFF) {
            sendWelcomeLightState(false, "WELCOME_LIGHT automatic timeout");
        }
        lightOffFuture = null;
    }

    private void onDashboardKeyState(boolean keyOn) {
        if (welcomeLightPolicy.onKeyState(keyOn)) {
            preferences.edit().putInt(PREF_WELCOME_COUNT, 0).apply();
            log("Welcome light activation count reset after ignition ON");
        }
        if (keyOn) {
            cancelFuture(lightOffFuture);
            lightOffFuture = null;
        }
        notifySnapshot();
    }

    private void sendWelcomeLightState(boolean enabled, String label) {
        sendCommand(0x0E, CommandFrame.WRITE, new byte[]{(byte) (enabled ? 1 : 0)}, label);
    }

    private void sendStoredPreferences(String label) {
        sendPreferencePacket(
                preferences.getString(PREF_VEHICLE_NAME, "OpenNoodoe"),
                preferences.getInt(PREF_BRIGHTNESS, 0xFF),
                preferences.getBoolean(PREF_WELCOME_ENABLED, true),
                preferences.getBoolean(PREF_METRIC, true),
                preferences.getBoolean(PREF_TWENTY_FOUR_HOUR, true),
                preferences.getInt(PREF_WELCOME_STANDBY, 1),
                label);
    }

    private void scheduleAutomaticConnection(String reason) {
        if (userDisconnected) {
            log("Automatic connection skipped after manual disconnect (" + reason + ")");
            return;
        }
        if (!isSelectedDevicePaired()) {
            log("Automatic connection skipped; selected device is not paired (" + reason + ")");
            return;
        }
        log("Automatic SPP connection requested: " + reason);
        requestConnection(reason, false);
    }

    private static boolean isSupportedShutdownTime(int value) {
        return value == WelcomeLightPolicy.SHUTDOWN_IMMEDIATELY
                || value == 1 || value == 2 || value == 3;
    }

    private static String shutdownTimeName(int value) {
        switch (value) {
            case WelcomeLightPolicy.SHUTDOWN_IMMEDIATELY: return "10 minutes";
            case 1: return "1 day";
            case 2: return "2 days";
            case 3: return "3 days";
            default: return "unknown(" + value + ")";
        }
    }

    @SuppressLint("MissingPermission")
    private void connectWithRetries(String address, int generation) {
        for (int attempt = 1; attempt <= 3 && generation == connectionGeneration.get(); attempt++) {
            BluetoothSocket candidate = null;
            try {
                if (adapter == null || !adapter.isEnabled()) {
                    updateState("Bluetooth is off", false);
                    return;
                }
                BluetoothDevice device = adapter.getRemoteDevice(address);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    updateState("Device is not paired", false);
                    return;
                }
                adapter.cancelDiscovery();
                updateState("SPP connecting (attempt " + attempt + ")", false);
                candidate = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket = candidate;
                candidate.connect();
                InputStream candidateInput = candidate.getInputStream();
                OutputStream candidateOutput = candidate.getOutputStream();
                DeviceInfo bootstrapInfo = performRawBootstrap(candidate, candidateInput, candidateOutput);
                if (generation != connectionGeneration.get()) {
                    closeQuietly(candidate);
                    return;
                }
                input = candidateInput;
                output = candidateOutput;
                framedProtocol = bootstrapInfo.supportsFramedProtocol();
                deviceInfoText = "Bootstrap: " + bootstrapInfo;
                connected = true;
                sendIndex = 0;
                expectedReceiveIndex = 128;
                lastReceiveIndex = 0;
                hasReceivedPacket = false;
                updateState(framedProtocol ? "Connected (framed SPP)" : "Connected (legacy protocol)", true);
                startReader(candidate, candidateInput, generation);
                if (framedProtocol) {
                    startTelemetryPolling();
                    onWelcomeConnectionEstablished();
                    if (preferences.getBoolean(PREF_VEHICLE_SETTINGS_CONFIGURED, false)) {
                        sendStoredPreferences("PREFERENCES restore on connect");
                    }
                    requestDeviceInfo();
                    syncClock();
                    requestRidingStatus();
                    requestOqcData();
                } else {
                    log("Legacy V1.0 data stream is not implemented in this prototype");
                }
                return;
            } catch (IOException | RuntimeException error) {
                log("Connect/bootstrap attempt " + attempt + " failed: " + error.getMessage());
                closeQuietly(candidate);
                socket = null;
                input = null;
                output = null;
                connected = false;
                if (attempt < 3) {
                    sleep(1_000);
                }
            }
        }
        if (generation == connectionGeneration.get()) {
            updateState("Connection failed; tap Connect saved to retry", false);
        }
    }

    private DeviceInfo performRawBootstrap(BluetoothSocket candidate, InputStream in, OutputStream out)
            throws IOException {
        log("TX bootstrap: " + ByteCodec.hex(RAW_BOOTSTRAP));
        out.write(RAW_BOOTSTRAP);
        out.flush();
        ScheduledFuture<?> timeout = scheduler.schedule(() -> closeQuietly(candidate), 5, TimeUnit.SECONDS);
        try {
            byte[] header = readFully(in, 5);
            if ((header[0] & 0xFF) != 0x85) {
                throw new IOException("unexpected bootstrap reply 0x" + Integer.toHexString(header[0] & 0xFF));
            }
            long payloadLength = ByteCodec.u32le(header, 1);
            if (payloadLength < 13 || payloadLength > 4096) {
                throw new IOException("invalid bootstrap payload length " + payloadLength);
            }
            byte[] payload = readFully(in, (int) payloadLength);
            byte[] full = new byte[header.length + payload.length];
            System.arraycopy(header, 0, full, 0, header.length);
            System.arraycopy(payload, 0, full, header.length, payload.length);
            log("RX bootstrap: " + ByteCodec.hex(full));
            return DeviceInfo.fromRawBootstrap(Arrays.copyOfRange(payload, 1, payload.length));
        } finally {
            timeout.cancel(false);
        }
    }

    private void startReader(BluetoothSocket connectedSocket, InputStream connectedInput, int generation) {
        Thread reader = new Thread(() -> {
            SequenceStreamDecoder decoder = new SequenceStreamDecoder();
            byte[] buffer = new byte[4096];
            try {
                while (generation == connectionGeneration.get() && connectedSocket == socket) {
                    int count = connectedInput.read(buffer);
                    if (generation != connectionGeneration.get() || connectedSocket != socket) return;
                    if (count < 0) {
                        throw new IOException("SPP stream ended");
                    }
                    byte[] received = Arrays.copyOf(buffer, count);
                    logProtocol("RX SPP: " + ByteCodec.hex(received));
                    try {
                        List<SequenceFrame> frames = decoder.feed(received, received.length);
                        for (SequenceFrame frame : frames) {
                            processSequenceFrame(frame);
                        }
                    } catch (IllegalArgumentException protocolError) {
                        log("RX frame rejected: " + protocolError.getMessage());
                        decoder = new SequenceStreamDecoder();
                    }
                }
            } catch (IOException error) {
                if (generation == connectionGeneration.get() && connectedSocket == socket) {
                    handleConnectionLost("SPP read failed: " + error.getMessage());
                }
            }
        }, "Noodoe-SPP-reader");
        reader.start();
    }

    private void processSequenceFrame(SequenceFrame frame) {
        byte[] payload = frame.getPayload();
        synchronized (ackLock) {
            if (pendingAckIndex >= 0 && frame.getAckIndex() == pendingAckIndex
                    && (frame.isAck() || payload.length > 0)) {
                sendIndex = pendingAckIndex == 127 ? 0 : pendingAckIndex + 1;
                pendingAcked = true;
                ackLock.notifyAll();
            }
        }
        if (payload.length == 0) {
            return;
        }

        int receivedIndex = frame.getPacketIndex();
        if (hasReceivedPacket && receivedIndex == lastReceiveIndex) {
            log("RX duplicate packet index " + receivedIndex);
            sendPureAck();
            return;
        }
        if (receivedIndex != expectedReceiveIndex) {
            log("RX packet index resync: expected " + expectedReceiveIndex + ", got " + receivedIndex);
        }
        hasReceivedPacket = true;
        lastReceiveIndex = receivedIndex;
        expectedReceiveIndex = receivedIndex == 255 ? 128 : receivedIndex + 1;

        List<CommandFrame> commands = CommandFrame.decodeMany(payload);
        for (CommandFrame command : commands) {
            processCommand(command);
        }
        sendPureAck();
        for (CommandFrame command : commands) {
            signalCommandReply(command);
        }
    }

    private void processCommand(CommandFrame command) {
        byte[] payload = command.getPayload();
        if (command.getCommandId() != 0xC4) {
            log(String.format(Locale.US, "RX command id=0x%02X attr=0x%02X length=%d",
                    command.getCommandId(), command.getAttribute(), payload.length));
        }
        try {
            switch (command.getCommandId()) {
                case 0x04:
                    log("PREFERENCES reply status=" + status(payload));
                    break;
                case 0x08:
                    log("NAVIGATION reply status=" + status(payload));
                    break;
                case 0x09:
                    log("WEATHER reply status=" + status(payload));
                    break;
                case 0x0A:
                    log("FILE_NEGOTIATE reply status=" + status(payload)
                            + " (" + transferStatusName(status(payload)) + ")"
                            + " task=" + (payload.length >= 4 ? ByteCodec.u16le(payload, 2) : -1));
                    break;
                case 0x0B:
                    log("FILE_CONTROL reply status=" + status(payload)
                            + " received=" + (payload.length >= 10 ? ByteCodec.u32le(payload, 6) : -1));
                    break;
                case 0x0D:
                    if (payload.length >= 16) {
                        FileTransferPayloads.DataReply reply =
                                FileTransferPayloads.DataReply.parse(payload);
                        log("FILE_TRANSFER reply status=" + reply.status
                                + " task=" + reply.taskId
                                + " transfer=" + reply.transferId
                                + " progressWord=" + reply.chunkSize
                                + " cumulative=" + reply.cumulativeSize);
                    } else {
                        log("FILE_TRANSFER short reply status=" + status(payload)
                                + " length=" + payload.length);
                    }
                    break;
                case 0x02:
                    log("MOBILE_STATUS reply status=" + status(payload));
                    break;
                case 0x05:
                    DeviceInfo info = DeviceInfo.fromFramedReply(payload);
                    currentDeviceInfo = info;
                    if (info.status == 0 && !info.model.isEmpty() && preferences != null) {
                        preferences.edit().putString(PREF_LAST_MODEL_CODE, info.model).apply();
                    }
                    deviceInfoText = "Framed: " + info;
                    log(deviceInfoText);
                    notifySnapshot();
                    break;
                case 0x0E:
                    String lightState = payload.length >= 3 ? Integer.toString(payload[2] & 0xFF) : "?";
                    log("BREATHING_LIGHT reply status=" + status(payload) + " state=" + lightState);
                    break;
                case 0x0C:
                    if (payload.length >= 11) {
                        updateRidingStatus(RidingStatus.fromReply(payload));
                        log("RIDING_STATUS reply " + ridingStatusText);
                        notifySnapshot();
                    } else {
                        log("RIDING_STATUS reply status=" + status(payload)
                                + " without data, payload=" + ByteCodec.hex(payload));
                    }
                    break;
                case 0x11:
                    if (payload.length >= 139) {
                        latestOqcData = OqcData.fromReply(payload);
                        oqcStatusText = latestOqcData.toString();
                        log("OQC_DATA_READ reply " + oqcStatusText);
                        notifySnapshot();
                    } else {
                        log("OQC_DATA_READ reply status=" + status(payload)
                                + " without full record, payload=" + ByteCodec.hex(payload));
                    }
                    break;
                case 0x12:
                    log("OQC_TEST reply status=" + status(payload));
                    break;
                case 0x13:
                    log("CALL_TEST reply status=" + status(payload));
                    break;
                case 0x14:
                    log("SMS_TEST reply status=" + status(payload));
                    break;
                case 0x15:
                    log("APP_NOTIFICATION reply status=" + status(payload));
                    break;
                case 0x16:
                    if (payload.length >= 19) {
                        meterProfileText = "status=" + status(payload)
                                + " VCU=" + ByteCodec.hex(Arrays.copyOfRange(payload, 2, 18))
                                + " meterFW=" + (payload[18] & 0xFF)
                                + " bytes=" + payload.length;
                    } else {
                        meterProfileText = "status=" + status(payload)
                                + " bytes=" + payload.length + " (unsupported/short reply)";
                    }
                    log("GET_METER_PROFILE reply " + meterProfileText);
                    notifySnapshot();
                    break;
                case 0xC1:
                    if (payload.length >= 2) {
                        foregroundCreation = payload[0] & 0xFF;
                        backgroundCreation = payload[1] & 0xFF;
                        log("RUNNING_CREATION foreground=" + (payload[0] & 0xFF)
                                + " background=" + (payload[1] & 0xFF)
                                + " raw=" + ByteCodec.hex(payload));
                        notifySnapshot();
                    }
                    break;
                case 0xC2:
                    // This event reports moving/stopped, not speed in km/h.
                    log("NOTIFY_RIDING " + ByteCodec.hex(payload));
                    if (payload.length >= 1) requestRidingStatus();
                    break;
                case 0xC3:
                    if (payload.length >= 1 && telemetry.onKeyNotification(
                            payload[0] & 0xFF, SystemClock.elapsedRealtime())) {
                        boolean keyOn = (payload[0] & 0xFF) == 1;
                        log("Dashboard key notification: " + (keyOn ? "ON" : "OFF"));
                        onDashboardKeyState(keyOn);
                        requestRidingStatus();
                    } else {
                        log("Dashboard key notification without a valid state byte");
                    }
                    break;
                case 0xC4:
                    OqcTestSample oqcSample = OqcTestSample.fromPayload(payload);
                    int oqcFlags = oqcSample.flags;
                    long oqcLight = oqcSample.lightSensor;
                    long oqcNow = System.currentTimeMillis();
                    oqcSeenFlags |= oqcFlags;
                    oqcLightMinimum = Math.min(oqcLightMinimum, oqcLight);
                    oqcLightMaximum = Math.max(oqcLightMaximum, oqcLight);
                    oqcSampleCount++;
                    suppressedOqcSamples++;
                    if (oqcFlags != lastOqcFlags || oqcLight != lastOqcLight
                            || oqcNow - lastOqcDisplayAt >= 1_000) {
                        oqcStatusText = formatOqcTestStatus(true, oqcSample);
                        log("OQC test notify " + oqcStatusText);
                        notifySnapshot();
                        lastOqcDisplayAt = oqcNow;
                        lastOqcFlags = oqcFlags;
                        lastOqcLight = oqcLight;
                        suppressedOqcSamples = 0;
                    }
                    break;
                case 0xC5:
                    log("Dashboard requested phone time update; replying with current time");
                    syncClock();
                    break;
                case 0xC6:
                    log("BATTERY_DATA notify bytes=" + payload.length + " raw=" + ByteCodec.hex(payload));
                    break;
                default:
                    break;
            }
        } catch (IllegalArgumentException error) {
            log("Command payload parse failed: " + error.getMessage());
        }
    }

    private void signalCommandReply(CommandFrame command) {
        synchronized (replyLock) {
            if (pendingReplyExpectation != null && pendingReplyExpectation.matches(command)) {
                pendingReplyPayload = command.getPayload();
                replyLock.notifyAll();
            } else if (pendingReplyExpectation != null
                    && (command.getCommandId() == 0x0A || command.getCommandId() == 0x0B
                    || command.getCommandId() == 0x0D)) {
                log("Ignoring stale transfer reply while waiting for " + pendingReplyExpectation
                        + ": command=0x" + String.format(Locale.US, "%02X",
                        command.getCommandId()) + " payload=" + ByteCodec.hex(command.getPayload()));
            }
        }
    }

    private void sendCommand(int commandId, int attribute, byte[] payload, String label) {
        commandExecutor.execute(() -> {
            sendCommandDirect(commandId, attribute, payload, 0, label);
        });
    }

    private boolean sendCommandDirect(int commandId, int attribute, byte[] payload,
            int session, String label) {
        if (!io.opennoodoe.app.protocol.ProductCommandPolicy.allows(commandId, attribute,
                payload, activeTransferTask, activeTransferType, activeTransferLocation)) {
            log("Product scope blocked command: " + label);
            return false;
        }
        log(String.format(Locale.US, "ACTION: %s command=0x%02X attr=0x%02X length=%d",
                label, commandId, attribute, payload.length));
        if (!connected || !framedProtocol || output == null) {
            log(label + " skipped: framed SPP is not connected");
            return false;
        }
        CommandFrame command = new CommandFrame(commandId, attribute, payload);
        boolean acknowledged = sendSequenceWithRetry(command.encode(), session, label);
        log(label + (acknowledged ? " sequence ACK" : " failed: no sequence ACK"));
        return acknowledged;
    }

    private byte[] sendAndAwait(int commandId, int attribute, byte[] payload, int session,
            String label, long timeoutMs) {
        synchronized (replyLock) {
            pendingReplyExpectation = ReplyExpectation.fromRequest(commandId, payload);
            pendingReplyPayload = null;
        }
        if (!sendCommandDirect(commandId, attribute, payload, session, label)) {
            clearPendingReply();
            return null;
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (replyLock) {
            while (pendingReplyPayload == null && connected) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                try {
                    replyLock.wait(remaining);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            byte[] result = pendingReplyPayload;
            pendingReplyExpectation = null;
            pendingReplyPayload = null;
            if (result == null) {
                log(label + " failed: no command reply");
            }
            return result;
        }
    }

    private void clearPendingReply() {
        synchronized (replyLock) {
            pendingReplyExpectation = null;
            pendingReplyPayload = null;
        }
    }

    private byte[] navigationPayload(int distance, int blockCount, int icon,
            boolean leftDriving, boolean nightMode, int speedLimit, boolean nearCamera,
            int cameraDistance, int currentRoadFileId, int nextRoadFileId) {
        return CandidatePayloads.navigation(distance, blockCount, icon,
                0, currentRoadFileId, nextRoadFileId, 0, 0, 0, 0,
                0, 0, 0, 0, 0, leftDriving, nightMode,
                speedLimit, 0, nearCamera, cameraDistance, 0, 0);
    }

    private boolean startNavigationSessionInternal() {
        if (navigationTaskId >= 0) {
            setTransferStatus("Navigation DATA task " + navigationTaskId + " is already active");
            return true;
        }
        if (!connected || !framedProtocol) {
            setTransferStatus("Navigation: framed SPP is not connected");
            return false;
        }
        int task = nextTaskId();
        setTransferStatus("Navigation: starting persistent DATA task " + task);
        byte[] reply = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, FileTransferPayloads.TYPE_DATA,
                        LOCATION_NAVIGATION, 0, FileTransferPayloads.BEGIN, new byte[16]), 0,
                "NAV BEGIN", FILE_TASK_TIMEOUT_MS);
        if (!replyStatusOk(reply)) {
            setTransferStatus("Navigation BEGIN failed, status="
                    + status(reply == null ? new byte[0] : reply));
            return false;
        }
        navigationTaskId = task;
        navigationTransferId = 1;
        navigationFileId = 1;
        setTransferStatus("Navigation DATA task " + task
                + " active; use Stop only after the route test");
        return true;
    }

    private void stopNavigationSessionInternal(String reason) {
        int task = navigationTaskId;
        clearNavigationSessionState();
        if (task < 0) {
            setTransferStatus("Navigation DATA task is not active");
            return;
        }
        if (!connected || !framedProtocol) {
            setTransferStatus("Navigation DATA task cleared after disconnect");
            return;
        }
        byte[] reply = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, FileTransferPayloads.TYPE_DATA,
                        LOCATION_NAVIGATION, 0, FileTransferPayloads.DONE, new byte[16]), 0,
                "NAV DONE " + reason, FILE_TASK_TIMEOUT_MS);
        setTransferStatus(replyStatusOk(reply)
                ? "Navigation DATA task stopped (" + reason + ")"
                : "Navigation DONE failed, status="
                        + status(reply == null ? new byte[0] : reply));
    }

    private void resetNavigationSessionInternal(String reason) {
        int task = navigationTaskId;
        clearNavigationSessionState();
        if (task < 0) {
            setTransferStatus("Navigation DATA task is not active; nothing to reset");
            return;
        }
        if (!connected || !framedProtocol) {
            setTransferStatus("Navigation DATA task state cleared after disconnect");
            return;
        }
        byte[] reset = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, FileTransferPayloads.TYPE_DATA,
                        LOCATION_NAVIGATION, 0, FileTransferPayloads.RESET, new byte[16]), 0,
                "NAV RESET " + reason, FILE_TASK_TIMEOUT_MS);
        byte[] done = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, FileTransferPayloads.TYPE_DATA,
                        LOCATION_NAVIGATION, 0, FileTransferPayloads.DONE, new byte[16]), 0,
                "NAV DONE AFTER RESET " + reason, FILE_TASK_TIMEOUT_MS);
        setTransferStatus("Navigation task reset/closed; RESET="
                + status(reset == null ? new byte[0] : reset) + ", DONE="
                + status(done == null ? new byte[0] : done)
                + "; SPP remains connected");
    }

    private void clearNavigationSessionState() {
        navigationTaskId = -1;
        navigationTransferId = 1;
        navigationFileId = 1;
    }

    private int nextNavigationTransferId() {
        int result = navigationTransferId;
        navigationTransferId = navigationTransferId >= 0x7FFF ? 1 : navigationTransferId + 1;
        return result;
    }

    private int nextNavigationFileId() {
        int result = navigationFileId;
        navigationFileId = navigationFileId >= 0x7FFF ? 1 : navigationFileId + 1;
        return result;
    }

    private OqcData performOqcReadAndBackup(String reason) {
        byte[] payload = sendAndAwait(0x11, CommandFrame.READ, new byte[0], 0,
                "OQC_DATA_READ " + reason, 8_000);
        if (payload == null || payload.length < 139 || status(payload) != 0) {
            setHazardStatus("OQC READ/백업 실패: status="
                    + status(payload == null ? new byte[0] : payload)
                    + " bytes=" + (payload == null ? 0 : payload.length));
            return null;
        }
        try {
            OqcData data = OqcData.fromReply(payload);
            saveOqcBackup(payload, data, reason);
            return data;
        } catch (Exception error) {
            invalidateOqcBackup();
            setHazardStatus("OQC 백업 파일 생성 실패: " + error.getMessage());
            return null;
        }
    }

    private void saveOqcBackup(byte[] payload, OqcData data, String reason) throws Exception {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        String name = "oqc-" + timestamp + "-" + reason.replaceAll("[^A-Za-z0-9_-]", "_") + ".json";
        JSONObject json = new JSONObject();
        json.put("timestamp", timestamp);
        json.put("reason", reason);
        json.put("selectedBluetoothAddress", getSelectedAddress());
        json.put("connectionGeneration", connectionGeneration.get());
        json.put("deviceInfo", currentDeviceInfo == null ? JSONObject.NULL : currentDeviceInfo.toString());
        json.put("rawReplyHex", ByteCodec.hex(payload));
        json.put("parsed", data.toString());

        File internal = new File(new File(getFilesDir(), "backups"), "oqc");
        File externalRoot = getExternalFilesDir("backups");
        if (externalRoot == null) {
            throw new IOException("external app files directory is unavailable");
        }
        File external = new File(externalRoot, "oqc");
        File internalFile = new File(internal, name);
        File externalFile = new File(external, name);
        writeTextDurably(internalFile, json.toString(2));
        writeTextDurably(externalFile, json.toString(2));

        latestOqcData = data;
        latestOqcRaw = payload.clone();
        oqcBackupGeneration = connectionGeneration.get();
        oqcBackupPath = externalFile.getAbsolutePath();
        oqcBackupId = timestamp + ":" + reason;
        oqcStatusText = data.toString();
        log("OQC_BACKUP internal=" + internalFile.getAbsolutePath()
                + " external=" + externalFile.getAbsolutePath());
        log("OQC_BACKUP raw=" + ByteCodec.hex(payload));
        setHazardStatus("OQC READ 및 내부/외부 백업 완료: " + externalFile.getAbsolutePath());
    }

    private boolean verifyStationary(String operation) {
        try {
            requireHazardConnection(operation);
        } catch (IllegalStateException error) {
            setHazardStatus(operation + " 차단: " + error.getMessage());
            return false;
        }
        byte[] reply = sendAndAwait(0x0C, CommandFrame.READ, new byte[0], 0,
                operation + " STATIONARY CHECK", 6_000);
        if (reply == null || reply.length < 11) {
            setHazardStatus(operation + " 차단: 주행 상태를 읽지 못했습니다.");
            return false;
        }
        RidingStatus riding = RidingStatus.fromReply(reply);
        updateRidingStatus(riding);
        if (riding.status != 0 || riding.currentSpeed != 0) {
            setHazardStatus(operation + " 차단: status=" + riding.status
                    + ", currentSpeed=" + riding.currentSpeed);
            return false;
        }
        log("HAZARD preflight stationary confirmed: " + riding);
        return true;
    }

    private void requireHazardConnection(String operation) {
        if (!connected || !framedProtocol || currentDeviceInfo == null) {
            throw new IllegalStateException(operation + " requires framed SPP and current DEVICE_INFO");
        }
        if (currentDeviceInfo.status != 0) {
            throw new IllegalStateException("DEVICE_INFO status=" + currentDeviceInfo.status);
        }
    }

    private boolean requireHazardArmed(String operation) {
        if (hazardSessionArmed) {
            return true;
        }
        setHazardStatus(operation + " 차단: 위험 세션이 열리지 않았습니다.");
        return false;
    }

    private void recordHazardPackage(String type, String name, String sha256, String details)
            throws Exception {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        JSONObject json = new JSONObject();
        json.put("timestamp", timestamp);
        json.put("type", type);
        json.put("name", name);
        json.put("sha256", sha256);
        json.put("details", details);
        json.put("selectedBluetoothAddress", getSelectedAddress());
        json.put("deviceInfo", currentDeviceInfo == null ? JSONObject.NULL : currentDeviceInfo.toString());
        json.put("ridingStatus", currentRidingStatus == null ? JSONObject.NULL : currentRidingStatus.toString());
        File internal = new File(new File(getFilesDir(), "backups"), "hazard");
        File externalRoot = getExternalFilesDir("backups");
        if (externalRoot == null) {
            throw new IOException("external app files directory is unavailable");
        }
        File external = new File(externalRoot, "hazard");
        String fileName = type + "-" + timestamp + ".json";
        writeTextDurably(new File(internal, fileName), json.toString(2));
        File externalFile = new File(external, fileName);
        writeTextDurably(externalFile, json.toString(2));
        log("HAZARD_PACKAGE manifest=" + externalFile.getAbsolutePath()
                + " sha256=" + sha256);
    }

    private static void writeTextDurably(File target, String text) throws IOException {
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("cannot create " + parent);
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }

    private void transferFirmware(OtaPackageInspector.FirmwarePackage firmware) {
        ContentGenerator.GeneratedFile file = ContentGenerator.GeneratedFile.raw(firmware.data);
        int task = nextTaskId();
        setTransferStatus("FIRMWARE: negotiating");
        if (!replyStatusOk(sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, FileTransferPayloads.TYPE_FILE,
                        LOCATION_FIRMWARE, firmware.data.length, FileTransferPayloads.BEGIN,
                        firmware.contentId), 0, "FIRMWARE BEGIN", 10_000))) {
            setHazardStatus("펌웨어 BEGIN 협상 실패. 설치는 시작되지 않았습니다.");
            return;
        }
        if (!transferOne(task, 1, file, 1, true)) {
            setHazardStatus("펌웨어 파일 전송 실패. 계기판 전원을 유지하고 공식 복구 절차를 준비하십시오.");
            return;
        }
        byte[] done = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, FileTransferPayloads.TYPE_FILE,
                        LOCATION_FIRMWARE, firmware.data.length, FileTransferPayloads.DONE,
                        firmware.contentId), 0, "FIRMWARE DONE", 15_000);
        if (replyStatusOk(done)) {
            setTransferStatus("FIRMWARE: transfer accepted");
            setHazardStatus("펌웨어 전송 완료 응답 수신. 계기판 설치가 끝날 때까지 전원을 차단하지 마십시오.");
        } else {
            setHazardStatus("펌웨어 DONE 실패: status="
                    + status(done == null ? new byte[0] : done));
        }
    }

    private void submitInstrumentedTransfer(String label, Runnable operation) {
        if (!transferOperationClaimed.compareAndSet(false, true)) {
            log("DUPLICATE: " + label + " rejected; active=" + operationStateText);
            setTransferStatus(label + ": ignored because another measured transfer is active");
            return;
        }
        transferResetRequested.set(false);
        transferResetHandled = false;
        beginCaptureSession("instrumented-transfer: " + label);
        setOperationState("QUEUED", label);
        commandExecutor.execute(() -> {
            try {
                if (!runTransferPreflight(label)) {
                    setOperationState("FAILED", label + " preflight failed");
                    return;
                }
                if (transferResetRequested.get()) {
                    setOperationState("RESET_COMPLETE",
                            "canceled before a dashboard transfer task was opened");
                    setTransferStatus(label + ": canceled during preflight; SPP preserved");
                    return;
                }
                operation.run();
            } catch (RuntimeException error) {
                setOperationState("FAILED", label + ": " + error.getMessage());
                setTransferStatus(label + ": failed: " + error.getMessage());
            } finally {
                clearActiveTransfer();
                transferResetRequested.set(false);
                transferOperationClaimed.set(false);
                notifySnapshot();
            }
        });
    }

    private boolean runTransferPreflight(String label) {
        setOperationState("PREFLIGHT", label + "; read-only state snapshot");
        log("PREFLIGHT_SESSION connected=" + connected + " framed=" + framedProtocol
                + " sendIndex=" + sendIndex + " expectedReceiveIndex=" + expectedReceiveIndex
                + " lastReceiveIndex=" + lastReceiveIndex + " hasReceived=" + hasReceivedPacket
                + " navigationTask=" + navigationTaskId + " groupTask=" + groupTaskId);
        if (!connected || !framedProtocol) {
            setTransferStatus(label + ": SPP is not connected");
            return false;
        }
        if (navigationTaskId >= 0) {
            setTransferStatus(label + ": stop or reset the active Navigation DATA task first");
            return false;
        }
        logPreflightReply("DEVICE_INFO", sendAndAwait(0x05, CommandFrame.READ,
                new byte[0], 0, "PREFLIGHT DEVICE_INFO", 5_000));
        if (transferResetRequested.get()) {
            return true;
        }
        logPreflightReply("GET_METER_PROFILE", sendAndAwait(0x16, CommandFrame.READ,
                new byte[0], 0, "PREFLIGHT GET_METER_PROFILE", 5_000));
        if (transferResetRequested.get()) {
            return true;
        }
        logPreflightReply("RIDING_STATUS", sendAndAwait(0x0C, CommandFrame.READ,
                new byte[0], 0, "PREFLIGHT RIDING_STATUS", 5_000));
        log("PREFLIGHT_COMPLETE " + label + " sendIndex=" + sendIndex
                + " expectedReceiveIndex=" + expectedReceiveIndex);
        return connected && framedProtocol;
    }

    private void logPreflightReply(String name, byte[] reply) {
        log("PREFLIGHT_REPLY " + name + " status="
                + status(reply == null ? new byte[0] : reply)
                + " bytes=" + (reply == null ? 0 : reply.length));
    }

    private void beginActiveTransfer(int task, int type, int location, long total,
            byte[] contentId) {
        activeTransferTask = task;
        activeTransferType = type;
        activeTransferLocation = location;
        activeTransferTotal = total;
        activeTransferContentId = Arrays.copyOf(
                contentId == null ? new byte[0] : contentId, 16);
        transferResetHandled = false;
    }

    private void clearActiveTransfer() {
        activeTransferTask = -1;
        activeTransferType = 0;
        activeTransferLocation = -1;
        activeTransferTotal = 0;
        activeTransferContentId = new byte[16];
        transferResetHandled = false;
    }

    private boolean handleRequestedTransferReset() {
        if (!transferResetRequested.get() || activeTransferTask < 0) {
            return false;
        }
        int task = activeTransferTask;
        setOperationState("RESETTING", "task=" + task + " attribute=RESET(5)");
        byte[] reset = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, activeTransferType,
                        activeTransferLocation, activeTransferTotal,
                        FileTransferPayloads.RESET, activeTransferContentId), 0,
                "TRANSFER RESET", FILE_TASK_TIMEOUT_MS);
        int resetStatus = status(reset == null ? new byte[0] : reset);
        log("TRANSFER_RESET_RESULT task=" + task + " status=" + resetStatus + " "
                + transferStatusName(resetStatus));

        byte[] done = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, activeTransferType,
                        activeTransferLocation, activeTransferTotal,
                        FileTransferPayloads.DONE, activeTransferContentId), 0,
                "TRANSFER DONE AFTER RESET", FILE_TASK_TIMEOUT_MS);
        int doneStatus = status(done == null ? new byte[0] : done);
        transferResetHandled = true;
        setOperationState(replyStatusOk(reset) && replyStatusOk(done)
                        ? "RESET_COMPLETE" : "RESET_UNCERTAIN",
                "RESET=" + resetStatus + " DONE=" + doneStatus + "; SPP preserved");
        setTransferStatus("Transfer reset/closed; RESET=" + resetStatus + " "
                + transferStatusName(resetStatus) + ", DONE=" + doneStatus + " "
                + transferStatusName(doneStatus) + "; Bluetooth remains connected");
        return true;
    }

    private void setOperationState(String phase, String detail) {
        operationStateText = phase + (detail == null || detail.isEmpty() ? "" : " | " + detail);
        log("TASK_STATE: " + operationStateText);
        notifySnapshot();
    }

    private boolean transferFiles(int location, byte[] contentId,
            List<ContentGenerator.GeneratedFile> files, String label) {
        if (!connected || !framedProtocol) {
            setTransferStatus(label + ": SPP is not connected");
            return false;
        }
        int task = nextTaskId();
        long total = 0;
        for (ContentGenerator.GeneratedFile file : files) {
            total += file.data.length;
        }
        beginActiveTransfer(task, FileTransferPayloads.TYPE_FILE, location, total, contentId);
        log("TRANSFER_MANIFEST label=" + label + " task=" + task + " location=0x"
                + Integer.toHexString(location) + " contentId=" + ByteCodec.hex(contentId)
                + " files=" + files.size() + " total=" + total);
        for (int i = 0; i < files.size(); i++) {
            ContentGenerator.GeneratedFile file = files.get(i);
            log("TRANSFER_FILE_ORDER index=" + (i + 1) + " name=" + file.name
                    + " identity=" + ByteCodec.hex(file.md5) + " size=" + file.data.length
                    + " paddedCrc32=" + String.format(Locale.US, "%08X",
                            FileTransferPayloads.paddedCrc32(file.data)));
        }
        if (handleRequestedTransferReset()) {
            return false;
        }
        setOperationState("BEGIN", "task=" + task + " location=0x"
                + Integer.toHexString(location) + " files=" + files.size());
        setTransferStatus(label + ": negotiating " + files.size() + " files");
        byte[] begin = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, FileTransferPayloads.TYPE_FILE,
                        location, total, FileTransferPayloads.BEGIN, contentId), 0,
                label + " BEGIN", FILE_TASK_TIMEOUT_MS);
        if (!replyStatusOk(begin)) {
            int result = status(begin == null ? new byte[0] : begin);
            setTransferStatus(label + ": negotiation failed, status="
                    + result + " " + transferStatusName(result));
            setOperationState("FAILED", "BEGIN status=" + result + " "
                    + transferStatusName(result));
            return false;
        }
        if (handleRequestedTransferReset()) {
            return false;
        }
        for (int i = 0; i < files.size(); i++) {
            setOperationState("FILE", (i + 1) + "/" + files.size() + " "
                    + files.get(i).name);
            if (!transferOne(task, i + 1, files.get(i), 0, false)) {
                if (!transferResetHandled) {
                    finishFailedTransferTask(task, FileTransferPayloads.TYPE_FILE,
                            location, total, contentId, label);
                    setOperationState("FAILED", "file " + (i + 1) + "/" + files.size());
                }
                return false;
            }
        }
        if (handleRequestedTransferReset()) {
            return false;
        }
        setOperationState("COMMIT", "task=" + task + " attribute=DONE(3)");
        byte[] done = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, FileTransferPayloads.TYPE_FILE,
                        location, total, FileTransferPayloads.DONE, contentId), 0,
                label + " DONE", FILE_TASK_TIMEOUT_MS);
        if (!replyStatusOk(done)) {
            int result = status(done == null ? new byte[0] : done);
            setTransferStatus(label + ": finish failed, status="
                    + result + " " + transferStatusName(result));
            setOperationState("FAILED", "DONE status=" + result + " "
                    + transferStatusName(result));
            return false;
        }
        setTransferStatus(label + ": transport committed; renderer not yet validated");
        setOperationState("VALIDATION_PENDING",
                "transport status=0; observe the dashboard renderer result");
        return true;
    }

    private boolean transferOne(int task, int transferId, ContentGenerator.GeneratedFile file,
            int fileId, boolean byFileId) {
        if (handleRequestedTransferReset()) {
            return false;
        }
        long crc32 = FileTransferPayloads.paddedCrc32(file.data);
        byte[] start = byFileId
                ? FileTransferPayloads.controlByFileId(task, FileTransferPayloads.UPDATE,
                        transferId, fileId, crc32, file.data.length)
                : FileTransferPayloads.controlByMd5(task, FileTransferPayloads.UPDATE,
                        transferId, file.md5, crc32,
                        file.data.length, file.name);
        byte[] startReply = sendAndAwait(0x0B, CommandFrame.WRITE, start, 0,
                "FILE " + transferId + " START", 6_000);
        if (handleRequestedTransferReset()) {
            return false;
        }
        int startStatus = status(startReply == null ? new byte[0] : startReply);
        if (startStatus != 0 && startStatus != 21) {
            setTransferStatus("File " + transferId + " rejected, status=" + startStatus);
            return false;
        }
        if (startStatus == 21) {
            log("File " + transferId + " already exists; skipping data and terminate");
            return true;
        } else {
            int offset = 0;
            while (offset < file.data.length) {
                if (handleRequestedTransferReset()) {
                    return false;
                }
                int remaining = file.data.length - offset;
                int count = Math.min(11_816, remaining);
                if (remaining > count) {
                    count -= count % 4;
                }
                byte[] data = FileTransferPayloads.data(task, transferId, file.data, offset, count);
                byte[] dataReply = sendAndAwait(0x0D, CommandFrame.WRITE, data, 1,
                        "FILE " + transferId + " " + (offset + count) + "/" + file.data.length,
                        6_000);
                if (handleRequestedTransferReset()) {
                    return false;
                }
                if (!replyStatusOk(dataReply)) {
                    setTransferStatus("File " + transferId + " data rejected, status="
                            + status(dataReply == null ? new byte[0] : dataReply));
                    return false;
                }
                long expectedCumulative = offset + count;
                if (dataReply.length >= 16) {
                    FileTransferPayloads.DataReply reply =
                            FileTransferPayloads.DataReply.parse(dataReply);
                    boolean progressWordMatches = byFileId || reply.chunkSize == count;
                    if (reply.taskId != task || reply.transferId != transferId
                            || !progressWordMatches
                            || reply.cumulativeSize < expectedCumulative) {
                        setTransferStatus("File " + transferId + " progress mismatch: task="
                                + reply.taskId + " transfer=" + reply.transferId
                                + " progressWord="
                                + reply.chunkSize + "/" + count + " cumulative="
                                + reply.cumulativeSize + "/" + expectedCumulative);
                        return false;
                    }
                    if (byFileId && reply.chunkSize != count) {
                        log("DATA task " + task + " accepted opaque progressWord=0x"
                                + Long.toHexString(reply.chunkSize)
                                + "; cumulative=" + reply.cumulativeSize
                                + " confirms " + expectedCumulative + " bytes");
                    }
                }
                offset += count;
                setOperationState("FILE_DATA", "task=" + task + " file=" + transferId
                        + " bytes=" + offset + "/" + file.data.length);
                setTransferStatus("File " + transferId + ": " + offset + "/" + file.data.length);
            }
        }
        if (handleRequestedTransferReset()) {
            return false;
        }
        setOperationState("FILE_FINISH", "task=" + task + " file=" + transferId);
        byte[] finish = byFileId
                ? FileTransferPayloads.controlByFileId(task, FileTransferPayloads.UPDATE_TERMINATE,
                        transferId, fileId, crc32, file.data.length)
                : FileTransferPayloads.controlByMd5(task, FileTransferPayloads.UPDATE_TERMINATE,
                        transferId, file.md5, crc32,
                        file.data.length, file.name);
        if (!replyStatusOk(sendAndAwait(0x0B, CommandFrame.WRITE, finish, 0,
                "FILE " + transferId + " FINISH", 6_000))) {
            setTransferStatus("File " + transferId + " finish failed");
            return false;
        }
        return true;
    }

    private void finishFailedTransferTask(int task, int type, int location, long total,
            byte[] contentId, String label) {
        byte[] cleanup = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(task, type, location, total,
                        FileTransferPayloads.DONE, contentId), 0,
                label + " FAILURE DONE", FILE_TASK_TIMEOUT_MS);
        log(label + " failure cleanup status=" + status(cleanup == null ? new byte[0] : cleanup));
    }

    private void sendGroupContinue(int task) {
        if (groupTaskId != task || !connected) {
            return;
        }
        byte[] reply = sendAndAwait(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.groupTask(task, LOCATION_GROUP,
                        FileTransferPayloads.CONTINUE, 0), 0,
                "GROUP CONTINUE", 5_000);
        if (!replyStatusOk(reply)) {
            log("GROUP CONTINUE ignored failure status="
                    + status(reply == null ? new byte[0] : reply));
        }
    }

    private void stopGroupSessionInternal(String reason) {
        int task = groupTaskId;
        stopGroupMotionInternal(false);
        cancelFuture(groupContinueFuture);
        cancelFuture(groupStopFuture);
        groupContinueFuture = null;
        groupStopFuture = null;
        groupTaskId = -1;
        groupTransferId = 1;
        if (task < 0) {
            return;
        }
        if (connected && framedProtocol) {
            byte[] reply = sendAndAwait(0x0A, CommandFrame.WRITE,
                    FileTransferPayloads.groupTask(task, LOCATION_GROUP,
                            FileTransferPayloads.DONE, 0), 0,
                    "GROUP DONE " + reason, FILE_TASK_TIMEOUT_MS);
            setTransferStatus(replyStatusOk(reply)
                    ? "GROUP DATA task stopped (" + reason + ")"
                    : "GROUP DONE failed, status="
                            + status(reply == null ? new byte[0] : reply));
        } else {
            setTransferStatus("GROUP DATA task cleared after disconnect");
        }
    }

    private void clearGroupSessionState() {
        stopGroupMotionInternal(false);
        cancelFuture(groupContinueFuture);
        cancelFuture(groupStopFuture);
        groupContinueFuture = null;
        groupStopFuture = null;
        groupTaskId = -1;
        groupTransferId = 1;
    }

    private boolean requireGroupSession(String action) {
        if (groupTaskId < 0) {
            setTransferStatus("GROUP: start the DATA task before " + action);
            return false;
        }
        if (!connected || !framedProtocol) {
            setTransferStatus("GROUP: SPP is not connected");
            return false;
        }
        return requireForeground(1, "Group radar");
    }

    private boolean requireForeground(int expected, String label) {
        if (foregroundCreation == expected) {
            return true;
        }
        setTransferStatus(label + ": select this screen on Noodoe first; foreground="
                + creationName(foregroundCreation) + " (" + foregroundCreation + ")");
        return false;
    }

    private static String creationName(int value) {
        switch (value) {
            case 1: return "GROUP";
            case 2: return "AROUND_ME";
            case 4: return "NAVIGATION";
            case 8: return "WEATHER";
            case 16: return "CLOCK";
            case 32: return "SPEEDOMETER";
            default: return value < 0 ? "UNKNOWN" : "0x" + Integer.toHexString(value);
        }
    }

    private int nextGroupTransferId() {
        int result = groupTransferId;
        groupTransferId = groupTransferId >= 0x7FFF ? 1 : groupTransferId + 1;
        return result;
    }

    private void sendGroupMemberUpdateInternal(int memberId, int x, int y, String operation) {
        try {
            boolean sent = sendGroupMemberFrame(memberId, x, y, operation);
            setTransferStatus(sent
                    ? "GROUP member " + memberId + " position sent: " + x + ", " + y
                    : "GROUP member position failed");
        } catch (IllegalArgumentException error) {
            setTransferStatus("GROUP member rejected locally: " + error.getMessage());
        }
    }

    private boolean sendGroupMemberFrame(int memberId, int x, int y, String operation) {
        byte[] payload = CandidatePayloads.groupMember(memberId, x, y);
        return sendCommandDirect(0x07, CommandFrame.NOTIFY, payload, 0,
                "GROUP " + operation + " id=" + memberId + " x=" + x + " y=" + y);
    }

    private boolean sendPoiFrame(int type, int x, int y, int placeId, String label) {
        try {
            return sendCommandDirect(0x06, CommandFrame.NOTIFY,
                    CandidatePayloads.poi(type, x, y, placeId), 0, label);
        } catch (IllegalArgumentException error) {
            log(label + " rejected locally: " + error.getMessage());
            return false;
        }
    }

    private void sendPoiPatternInternal(int step, int radius, String label, int generation) {
        if (!requireForeground(2, "Around Me / POI")) {
            if (generation >= 0) {
                stopPoiMotionInternal(false);
            }
            return;
        }
        int safeRadius = Math.max(0, Math.min(Math.abs(radius), 222));
        boolean complete = true;
        for (int type = 1; type <= 9; type++) {
            if (generation >= 0 && generation != poiMotionGeneration.get()) {
                return;
            }
            int[] xy = radialCoordinate(type - 1, 9, safeRadius, step);
            complete &= sendPoiFrame(type, xy[0], xy[1], 0,
                    label + " type=" + type + " x=" + xy[0] + " y=" + xy[1]);
        }
        if (!complete) {
            stopPoiMotionInternal(false);
            setTransferStatus(label + " incomplete");
        } else if (poiMotionFuture == null) {
            setTransferStatus("Nine POI types placed at radius " + safeRadius);
        }
    }

    private void stopPoiMotionInternal(boolean updateStatus) {
        poiMotionGeneration.incrementAndGet();
        cancelFuture(poiMotionFuture);
        poiMotionFuture = null;
        poiMotionTickQueued.set(false);
        if (updateStatus) {
            setTransferStatus("POI motion stopped; markers remain at their last coordinates");
        }
    }

    private void stopGroupMotionInternal(boolean updateStatus) {
        groupMotionGeneration.incrementAndGet();
        cancelFuture(groupMotionFuture);
        groupMotionFuture = null;
        groupMotionTickQueued.set(false);
        if (updateStatus) {
            setTransferStatus("GROUP motion stopped; members remain at their last coordinates");
        }
    }

    private static int[] radialCoordinate(int index, int count, int radius, int step) {
        double angle = (Math.PI * 2.0d * index / count) + (step * Math.PI / 12.0d);
        return new int[]{239 + (int) Math.round(Math.sin(angle) * radius),
                239 - (int) Math.round(Math.cos(angle) * radius)};
    }

    private void stopOqcTestInternal(String reason) {
        cancelFuture(oqcStopFuture);
        oqcStopFuture = null;
        if (oqcTestActive && connected) {
            byte[] reply = sendAndAwait(0x12, CommandFrame.WRITE, new byte[]{0}, 0,
                    "OQC_TEST STOP " + reason, 5_000);
            log("OQC STOP status=" + status(reply == null ? new byte[0] : reply));
        }
        oqcTestActive = false;
        oqcStatusText = formatOqcTestStatus(false, null);
        notifySnapshot();
    }

    private String formatOqcTestStatus(boolean active, OqcTestSample current) {
        String currentNames = current == null ? "none" : OqcTestSample.activeNames(current.flags);
        String lightRange = oqcSampleCount == 0 ? "not sampled"
                : lastOrCurrentLight(current) + " (range " + oqcLightMinimum + ".."
                        + oqcLightMaximum + ", samples " + oqcSampleCount + ")";
        return "mode=" + (active ? "ACTIVE" : "STOPPED")
                + "\ncurrent: " + currentNames
                + "\nseen buttons: POWER_ON=" + yesNo(OqcTestSample.POWER_ON)
                + " POWER_OFF=" + yesNo(OqcTestSample.POWER_OFF)
                + " UP=" + yesNo(OqcTestSample.BUTTON_UP)
                + " ENTER=" + yesNo(OqcTestSample.BUTTON_ENTER)
                + " DOWN=" + yesNo(OqcTestSample.BUTTON_DOWN)
                + "\nMFi chip: " + (((oqcSeenFlags & OqcTestSample.MFI_ENABLED) != 0)
                        ? "enabled" : "not detected")
                + "\nlight sensor: " + lightRange;
    }

    private long lastOrCurrentLight(OqcTestSample current) {
        return current == null ? lastOqcLight : current.lightSensor;
    }

    private String yesNo(int flag) {
        return (oqcSeenFlags & flag) != 0 ? "yes" : "no";
    }

    private void setTransferStatus(String value) {
        transferStatusText = value;
        log("TRANSFER: " + value);
        notifySnapshot();
    }

    private void updateRidingStatus(RidingStatus riding) {
        currentRidingStatus = riding;
        ridingStatusText = riding.toString();
        long receivedAt = System.currentTimeMillis();
        telemetry.onRidingStatus(riding, SystemClock.elapsedRealtime(), receivedAt);
        if (riding.status == 0 && preferences != null) {
            preferences.edit()
                    .putLong(PREF_LAST_ODOMETER, riding.odometer)
                    .putLong(PREF_LAST_ODOMETER_AT, receivedAt)
                    .apply();
        }
        if (riding.status == 0 && riding.keyStateKnown) {
            onDashboardKeyState(riding.keyOn);
        }
    }

    private void setHazardStatus(String value) {
        hazardStatusText = value;
        log("HAZARD: " + value);
        notifySnapshot();
    }

    private boolean hasValidOqcBackup() {
        return connected && currentDeviceInfo != null && currentDeviceInfo.status == 0
                && latestOqcData != null && latestOqcRaw != null
                && oqcBackupGeneration == connectionGeneration.get()
                && !oqcBackupPath.isEmpty();
    }

    private void invalidateOqcBackup() {
        latestOqcData = null;
        latestOqcRaw = null;
        oqcBackupGeneration = -1;
        oqcBackupPath = "";
        oqcBackupId = "";
    }

    private int nextTaskId() {
        synchronized (taskIds) {
            int value = taskIds.get();
            taskIds.set(value >= 127 ? 1 : value + 1);
            return value;
        }
    }

    private static boolean replyStatusOk(byte[] reply) {
        return reply != null && reply.length >= 2 && ByteCodec.u16le(reply, 0) == 0;
    }

    private static int compareVersion(int leftMajor, int leftMinor,
            int rightMajor, int rightMinor) {
        int major = Integer.compare(leftMajor, rightMajor);
        return major != 0 ? major : Integer.compare(leftMinor, rightMinor);
    }

    private static String transferStatusName(int value) {
        switch (value) {
            case 0: return "SUCCESS";
            case 1: return "NO_MEMORY";
            case 2: return "NOT_FOUND";
            case 3: return "UNSUPPORTED";
            case 5: return "INVALID_STATE";
            case 6: return "INVALID_LENGTH";
            case 8: return "INVALID_DATA";
            case 10: return "TIMEOUT";
            case 13: return "BUSY";
            case 21: return "ALREADY_EXISTS";
            case 23: return "INVALID_ID";
            case 24: return "CORRUPT";
            case 25: return "SCOOTER_MOVING";
            default: return "STATUS_" + value;
        }
    }

    private static boolean isSafeCreationLocation(int location) {
        return location == LOCATION_CLOCK || location == LOCATION_WEATHER
                || location == LOCATION_SPEEDOMETER
                || location == LOCATION_POI || location == LOCATION_GROUP;
    }

    private boolean sendSequenceWithRetry(byte[] payload, int session, String label) {
        int packetIndex = sendIndex;
        int control = hasReceivedPacket ? SequenceFrame.CONTROL_ACK : 0;
        int ackIndex = hasReceivedPacket ? lastReceiveIndex : 0;
        byte[] encoded = new SequenceFrame(control, packetIndex, ackIndex, session, payload).encode();

        for (int attempt = 0; attempt < 2; attempt++) {
            synchronized (ackLock) {
                pendingAckIndex = packetIndex;
                pendingAcked = false;
            }
            try {
                logProtocolFrame("TX " + label + (attempt == 0 ? "" : " retry"), encoded);
                writeFrame(encoded);
                long deadline = System.currentTimeMillis() + 3_000;
                synchronized (ackLock) {
                    while (!pendingAcked && connected) {
                        long remaining = deadline - System.currentTimeMillis();
                        if (remaining <= 0) {
                            break;
                        }
                        ackLock.wait(remaining);
                    }
                    if (pendingAcked) {
                        pendingAckIndex = -1;
                        return true;
                    }
                }
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log(label + " send error: " + error.getMessage());
                break;
            }
        }
        synchronized (ackLock) {
            pendingAckIndex = -1;
        }
        return false;
    }

    private void sendPureAck() {
        if (!connected || !hasReceivedPacket) {
            return;
        }
        try {
            byte[] ack = new SequenceFrame(SequenceFrame.CONTROL_ACK, sendIndex,
                    lastReceiveIndex, 0, new byte[0]).encode();
            logProtocol("TX ACK: " + ByteCodec.hex(ack));
            writeFrame(ack);
        } catch (IOException error) {
            log("ACK send failed: " + error.getMessage());
        }
    }

    private void writeFrame(byte[] frame) throws IOException {
        synchronized (outputLock) {
            OutputStream current = output;
            if (current == null) {
                throw new IOException("SPP output is closed");
            }
            current.write(frame);
            current.flush();
        }
    }

    private byte[] buildMobileStatus(Calendar now) {
        byte[] payload = new byte[12];
        payload[0] = (byte) (now.get(Calendar.YEAR) - 2000);
        payload[1] = (byte) (now.get(Calendar.MONTH) + 1);
        payload[2] = (byte) now.get(Calendar.DAY_OF_MONTH);
        payload[3] = (byte) now.get(Calendar.HOUR_OF_DAY);
        payload[4] = (byte) now.get(Calendar.MINUTE);
        payload[5] = (byte) now.get(Calendar.SECOND);
        payload[6] = (byte) (((now.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1);
        payload[7] = (byte) gpsState();
        payload[8] = (byte) internetState();
        payload[9] = (byte) 0xFF;
        payload[10] = (byte) batteryLevel();
        payload[11] = 1;
        return payload;
    }

    @SuppressLint("MissingPermission")
    private boolean isSelectedDevicePaired() {
        String address = getSelectedAddress();
        if (adapter == null || address.isEmpty()) return false;
        try {
            return adapter.getRemoteDevice(address).getBondState() == BluetoothDevice.BOND_BONDED;
        } catch (IllegalArgumentException | SecurityException error) {
            return false;
        }
    }

    private int gpsState() {
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            return manager != null && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? 2 : 0;
        } catch (RuntimeException error) {
            return 0;
        }
    }

    @SuppressWarnings("deprecation")
    private int internetState() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
        return info != null && info.isConnected() ? 2 : 0;
    }

    private int batteryLevel() {
        BatteryManager manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int level = manager == null ? -1 : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return level >= 0 && level <= 100 ? level : 0xFF;
    }

    private void handleConnectionLost(String reason) {
        if (!connected && socket == null) {
            return;
        }
        log(reason);
        stopPoiMotionInternal(false);
        clearNavigationSessionState();
        clearGroupSessionState();
        closeSocket();
        oqcTestActive = false;
        cancelFuture(oqcStopFuture);
        updateState("Disconnected", false);
        synchronized (ackLock) {
            ackLock.notifyAll();
        }
        synchronized (replyLock) {
            replyLock.notifyAll();
        }
    }

    private void closeSocket() {
        BluetoothSocket current = socket;
        socket = null;
        input = null;
        output = null;
        connected = false;
        framedProtocol = false;
        cancelFuture(telemetryFuture);
        telemetryFuture = null;
        telemetry.reset();
        if (welcomeLightPolicy != null) {
            welcomeLightPolicy.onDisconnected();
        }
        cancelFuture(lightOffFuture);
        lightOffFuture = null;
        currentDeviceInfo = null;
        currentRidingStatus = null;
        latestOqcData = null;
        latestOqcRaw = null;
        deviceInfoText = "Not read";
        ridingStatusText = "Not read";
        oqcStatusText = "Not read";
        meterProfileText = "Not read";
        foregroundCreation = -1;
        backgroundCreation = -1;
        invalidateOqcBackup();
        closeQuietly(current);
    }

    private void updateState(String newState, boolean isConnected) {
        state = newState;
        connected = isConnected;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(newState));
        }
        log("STATE: " + newState);
        notifySnapshot();
    }

    private void notifySnapshot() {
        Snapshot snapshot = snapshot();
        for (Listener listener : listeners) {
            listener.onSnapshot(snapshot);
        }
    }

    private void log(String message) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date()) + "  " + message;
        appendLogFile(line);
        synchronized (uiLogLock) {
            recentUiLog.addLast(line);
            while (recentUiLog.size() > 200) {
                recentUiLog.removeFirst();
            }
        }
        for (Listener listener : listeners) {
            listener.onLogLine(line);
        }
    }

    private void logProtocol(String message) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date()) + "  " + message;
        appendLogFile(line);
    }

    private void logProtocolFrame(String label, byte[] frame) {
        if (frame.length <= 512) {
            logProtocol(label + ": " + ByteCodec.hex(frame));
            return;
        }
        int previewLength = Math.min(96, frame.length);
        byte[] preview = Arrays.copyOf(frame, previewLength);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(frame);
        logProtocol(label + ": " + ByteCodec.hex(preview)
                + " ... bytes=" + frame.length + " crc32="
                + String.format(Locale.US, "%08X", crc.getValue()));
    }

    private void appendLogFile(String line) {
        synchronized (logFileLock) {
            File root = storageRoot();
            File directory = new File(root, "logs");
            appendLine(new File(directory, "opennoodoe.log"), line);
            File activeCapture = captureDirectory;
            if (activeCapture != null) {
                appendLine(new File(activeCapture, "protocol.log"), line);
            }
        }
    }

    private void beginCaptureSession(String reason) {
        String id = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        File directory = new File(new File(storageRoot(), "captures"), id);
        synchronized (logFileLock) {
            if (!directory.exists() && !directory.mkdirs()) {
                return;
            }
            captureId = id;
            captureDirectory = directory;
            writeCaptureMetadata(directory, reason);
        }
        log("CAPTURE START: " + id + " reason=" + reason + " path=" + directory.getAbsolutePath());
        notifySnapshot();
    }

    private void writeCaptureMetadata(File directory, String reason) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("capture_id", captureId);
            metadata.put("started_at", new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date()));
            metadata.put("reason", reason);
            metadata.put("app_version", BuildConfig.VERSION_NAME);
            metadata.put("android_release", Build.VERSION.RELEASE);
            metadata.put("android_sdk", Build.VERSION.SDK_INT);
            metadata.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            metadata.put("selected_bluetooth_address", getSelectedAddress());
            metadata.put("transport", "Classic Bluetooth RFCOMM/SPP");
            metadata.put("candidate_source", "Noodoe Android static analysis; live compatibility unverified");
            try (FileOutputStream stream = new FileOutputStream(
                    new File(directory, "metadata.json"), false)) {
                stream.write(metadata.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception error) {
            appendLine(new File(directory, "protocol.log"),
                    "metadata write failed: " + error.getMessage());
        }
    }

    private File storageRoot() {
        File root = getExternalFilesDir(null);
        return root == null ? getFilesDir() : root;
    }

    private static void appendLine(File file, String line) {
        File directory = file.getParentFile();
        if (directory == null || (!directory.exists() && !directory.mkdirs())) {
            return;
        }
        try (FileOutputStream stream = new FileOutputStream(file, true)) {
            stream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private Notification buildNotification(String text) {
        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("OpenNoodoe SPP")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Noodoe connection", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private static BluetoothDevice getBluetoothDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        }
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    private static String safeAddress(BluetoothDevice device) {
        try {
            return device.getAddress();
        } catch (SecurityException error) {
            return "";
        }
    }

    private static int status(byte[] payload) {
        return payload.length >= 2 ? ByteCodec.u16le(payload, 0) : -1;
    }

    private static byte[] readFully(InputStream input, int count) throws IOException {
        byte[] result = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = input.read(result, offset, count - offset);
            if (read < 0) {
                throw new IOException("SPP stream ended during bootstrap");
            }
            offset += read;
        }
        return result;
    }

    private static void closeQuietly(BluetoothSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static void cancelFuture(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
