# Architecture and source map

`MainActivity` binds to `NoodoeService`; `HomeScreen` renders snapshots and dispatches user actions. The service owns the Bluetooth socket, a reader loop, one serialized command executor and a scheduler. A foreground connected-device notification keeps the connection visible. UI code never reads the socket.

Connection generations invalidate work queued for an old socket. Output writes share a lock. Sequence ACK waiting and application reply waiting are separate. `SequenceStreamDecoder` handles stream fragmentation; `processCommand` updates state immediately; `signalCommandReply` releases the matching waiter. Transfers claim exclusive command ownership so status polling does not interleave with file transactions.

`VehicleTelemetry` uses monotonic freshness times and clears speed after an ignition transition. A cached last ODO is stored separately. This avoids presenting a previous connection's ON/OFF or speed as current state. The poller refreshes riding information every second when idle and device information every 30 seconds; incoming C3 events are processed immediately.

`ProductCommandPolicy` is the outbound boundary. Allowed: 02/04/0E WRITE; 05/0C/11/16 empty READ; 0A WRITE with 27-byte FILE negotiation for 0200/0400/0600; 0B/0D WRITE matching the active FILE task and allowed location. Historical navigation, notification and stock maintenance helper methods remain as reference, but the product UI/policy does not expose their writes. No CFW installer implementation is included.

There are no runtime third-party libraries, analytics, account backend or Internet permission. Android file pickers supply photos and ZIPs. The five UI languages use resource XML. `LicenseDialog` displays the MIT author/permission notice offline; `PrivacyPolicyDialog` displays the local policy.

Build verification includes the existing codec, reply correlation, product policy, telemetry freshness, gallery persistence, model catalog and welcome-light tests. Standalone examples additionally cross-check wire vectors against the same Java protocol classes without Android.

| Component | Source |
|---|---|
| Lifecycle / user actions | [MainActivity](../../android/app/src/main/java/io/opennoodoe/app/MainActivity.java) |
| Product UI | [HomeScreen](../../android/app/src/main/java/io/opennoodoe/app/HomeScreen.java) |
| Connection / queues / transactions | [NoodoeService](../../android/app/src/main/java/io/opennoodoe/app/NoodoeService.java) |
| Wire primitives / parsers / policy | [protocol/](../../android/app/src/main/java/io/opennoodoe/app/protocol/) |
| Photo / theme conversion | [ContentGenerator](../../android/app/src/main/java/io/opennoodoe/app/ContentGenerator.java) |
| Theme persistence | [ThemeRepository](../../android/app/src/main/java/io/opennoodoe/app/ThemeRepository.java) |
| Confirmed gallery payloads | [GallerySlotStore](../../android/app/src/main/java/io/opennoodoe/app/GallerySlotStore.java) |
| Current vs stale vehicle state | [VehicleTelemetry](../../android/app/src/main/java/io/opennoodoe/app/VehicleTelemetry.java) |
| Language / appearance | [AppLanguageSettings](../../android/app/src/main/java/io/opennoodoe/app/AppLanguageSettings.java), [UiThemeSettings](../../android/app/src/main/java/io/opennoodoe/app/UiThemeSettings.java) |
| Tests | [src/test](../../android/app/src/test/) |


[한국어](../ko/architecture.md) · [Index](../../README.md)
