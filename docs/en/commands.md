# Commands and payloads

Offsets below start at the **inner command payload**, after its 10-byte header. `u16/u32` are little-endian. Status in replies is normally `u16` at offset 0. An outer ACK confirms receipt; only the matching command response describes operation status.

`0C READ` returns current speed, ODO and ignition. `C2` reports moving/stopped, not a numeric speed; it schedules a status read. `C3` updates ignition immediately and schedules a fresh status read. The app also requests status once per second while idle, serializes requests and pauses this polling during file transfers. Freshness expires after 5 seconds; disconnect resets live readings. The last saved ODO is separate from live telemetry.

`02 WRITE` carries local wall-clock time with no timezone field. Date bytes are year−2000, month 1–12, day, hour, minute, second; weekday is Monday=1 … Sunday=7. Current implementation sends GPS accuracy FF and map availability 1. `C5` requests a new time packet.

`04 WRITE` has eight fixed bytes followed by UTF-8 name length/name and one notification-on-navigation byte. Name length counts bytes, max 58; empty name emits FF instead of a zero length. See `sendPreferencePacket` for UI defaults and preserved fields.

`0E WRITE` takes one byte (0 off, 1 on). Reply has u16 status plus one state byte. Firmware 5.16 can return status 5 when ignition is ON. The welcome policy permits three standby activations, turns off after 120 seconds and resets its activation count when ignition turns ON. These are app policy values, not BLE properties.

The app permits only the commands marked App below. ODO and production information are read-only. Reference command encoders elsewhere in the source do not override `ProductCommandPolicy`.

## IDs (hex)

| ID | Attribute | Function | Scope |
|---|---|---|---|
| 02 | WRITE | Mobile status / time | App |
| 04 | WRITE | Preferences | App |
| 05 | READ | Device information | App |
| 06 / 07 | NOTIFY | POI / group | Reference |
| 08 | NOTIFY | Navigation | Reference |
| 09 | WRITE | Weather | Reference |
| 0A | WRITE | Task negotiation | App: FILE clock/speed/gallery |
| 0B | WRITE | File control | App: matching active task |
| 0C | READ | Riding / ODO | App |
| 0D | WRITE | File data | App: matching active task |
| 0E | WRITE | Welcome/breathing light | App |
| 11 | READ | Production/OQC record | App |
| 12 | WRITE | OQC test mode | Reference |
| 13 / 14 / 15 | WRITE | Call / SMS / app notification | Reference |
| 16 | READ | Meter profile | App; may return short/unsupported reply |

## 05 REPLY — 82 bytes

| Offset | Type / bytes | Field |
|---:|---|---|
| 0 | u16 | status |
| 2 / 4 | u16 each | firmware major / minor |
| 6 | u8 | protocol major high nibble, minor low nibble |
| 7 | u8 | hardware version |
| 8 | 6 bytes | MAC, reverse byte order for display |
| 14 / 16 | u16 each | boot major / minor |
| 18 / 20 | u16 each | resource major / minor |
| 22 | 16 bytes | supported language bits |
| 38 / 39 | u8 each | resource ID / language pack ID |
| 40 | ASCII[10] | model |
| 50 | u16 | maximum speed |
| 52 | ASCII[18] | bike series |
| 70 | u8 | bike type |
| 71 / 73 | u16 each | PIN / motor series |
| 75 | ASCII[6] | PCBA |
| 81 | u8 | default dashboard |

## 0C REPLY — 11 bytes

| Offset | Type | Field |
|---:|---|---|
| 0 | u16 | status |
| 2 | u8 | ignition: 0 OFF, 1 ON, other unknown |
| 3 | u32 | odometer raw counter |
| 7 | u16 | stop duration |
| 9 / 10 | u8 each | maximum / current speed |

## 02 WRITE — 12 bytes

| Offset | Type | Field |
|---:|---|---|
| 0–5 | u8 each | year−2000, month, day, hour, minute, second |
| 6 | u8 | weekday 1–7 |
| 7 / 8 / 9 | u8 each | GPS state / Internet state / GPS accuracy |
| 10 / 11 | u8 each | phone battery / map availability |

## 04 WRITE

| Offset | Type | Field |
|---:|---|---|
| 0 / 1 / 2 | u8 each | distance unit / temperature unit / time format |
| 3 / 4 | u8 each | breathing light boolean / brightness |
| 5 / 6 / 7 | u8 each | default dashboard / language / shutdown |
| 8 | u8 | UTF-8 name byte count, or FF for empty |
| 9 | N bytes | name (absent for FF) |
| 9+N | u8 | notify on navigation |

## 11 REPLY — 139 bytes

| Offset | Type / bytes | Field |
|---:|---|---|
| 0 | u16 | status |
| 2 / 18 / 36 | ASCII[16] / [18] / [6] | part number / serial / PCBA |
| 42 / 54 | ASCII[12] / [10] | MAC text / model |
| 64 | u16 | PIN |
| 66 | u32[10] | backlight thresholds |
| 106 / 108 | u16 each | maximum speed / language |
| 110 / 112 | u16 each | firmware major / minor |
| 114 | ASCII[16] | assembly number |
| 130 | u16 | panel version |
| 132 / 133 / 134 | u8 each | unit / type / language pack |
| 135 | u16 | motor series |
| 137 / 138 | u8 each | resource ID / dashboard ID |

## Device events / 기기 이벤트

| ID | Payload / action |
|---|---|
| C1 | first 2 bytes: foreground/background creation |
| C2 | moving/stopped event; schedule 0C READ |
| C3 | first byte: ignition 0/1; update immediately, request 0C |
| C4 | OQC flags:u8 + light sensor:u32; see extended protocol |
| C5 | device requests phone time; send 02 WRITE |
| C6 | battery data bytes; this app logs them without a field decoder |

Source: [`DeviceInfo`](../../android/app/src/main/java/io/opennoodoe/app/protocol/DeviceInfo.java), [`RidingStatus`](../../android/app/src/main/java/io/opennoodoe/app/protocol/RidingStatus.java), [`OqcData`](../../android/app/src/main/java/io/opennoodoe/app/protocol/OqcData.java), [`CandidatePayloads`](../../android/app/src/main/java/io/opennoodoe/app/protocol/CandidatePayloads.java), [`VehicleTelemetry`](../../android/app/src/main/java/io/opennoodoe/app/VehicleTelemetry.java), [`WelcomeLightPolicy`](../../android/app/src/main/java/io/opennoodoe/app/WelcomeLightPolicy.java).


[한국어](../ko/commands.md) · [Index](../../README.md)
