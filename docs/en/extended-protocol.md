# Extended protocol reference

These encodings explain the research and help implement independent clients. OpenNoodoe's current product policy does not transmit navigation, weather, POI, group, calls, SMS, notification forwarding or OQC writes/tests.

## Navigation

Use a persistent DATA task at location 0100 and send `08 NOTIFY` updates referencing files uploaded by numeric file ID. Do not end the task after every navigation frame. The 2026-09-03 work identified this lifetime requirement; BEGIN/DONE per frame discarded the session context. Images include road-name rasters and map/turn views; `ContentGenerator.navigationRoads` uses 251×77 and 265×92 transparent PNGs. There is no map provider, route engine or live navigation service in the released app.

## Weather

`09 WRITE` is 51 bytes: 1-byte UTF-8 location length + 32-byte zero-padded location, AQI u16, current temperature i16, condition u16, then three (temperature i16, condition u16) pairs. Temperatures are **Fahrenheit**, even when the UI displays Celsius. Condition range is 0–8. A 32-byte bound is a UTF-8 byte limit, not 32 arbitrary characters.

## POI and group

POI `06 NOTIFY`: type u8 (1–9), signed X/Y i16, place ID u32. Types 1–3 retain the supplied place ID; others encode 0. Group member `07 NOTIFY`: member ID u16 (0–64), X/Y i16. Group DATA BEGIN stores a timeout u32 in content-ID bytes 0–3. Coordinate interpretation is renderer-specific; accepted transfer is not a universal POI renderer implementation.

## Notifications and buttons

`15 WRITE`: three length-prefixed strings (u16 byte length + bytes): ASCII app ID, UTF-8 app name, UTF-8 notification text. `13 WRITE`: status u8 then UTF-8 caller string16. `14 WRITE`: UTF-8 caller and message string16.

`C4` is a diagnostic OQC sample, not a demonstrated always-on ordinary button stream: flags u8 + light sensor u32. Flag bits are listed below. The product does not start OQC test mode to receive buttons. `C1/C2/C3/C5/C6` are covered in Commands.

## 08 NOTIFY — 42 bytes

| Offset | Type | Field |
|---:|---|---|
| 0 | u32 | distance |
| 4 / 6 | u16 / u8 | block count / icon |
| 7 / 9 / 11 / 13 / 15 | u16 each | next turn / current road / next road / overall / best-fit file IDs |
| 17 / 19 | u16 each | best-fit X / Y |
| 21 / 23 / 25 | u16 each | 2D map file ID / X / Y |
| 27 / 29 | u16 each | next-turn X / Y |
| 31 / 32 | u8 each | left-driving / night-mode booleans |
| 33 / 35 | u16 / u8 | speed limit / unit |
| 36 / 37 | u8 / u16 | near-camera boolean / camera distance |
| 39 / 41 | u16 / u8 | angle / icon after turn |

The final field is at offset 41. / 마지막 필드의 오프셋은 41입니다.

## C4 flags

| Bit | Meaning |
|---:|---|
| 0 | POWER_ON |
| 1 | POWER_OFF |
| 2 | UP |
| 3 | ENTER |
| 4 | DOWN |
| 5 | MFI_ENABLED |

Source: [`CandidatePayloads`](../../android/app/src/main/java/io/opennoodoe/app/protocol/CandidatePayloads.java), [`OqcTestSample`](../../android/app/src/main/java/io/opennoodoe/app/protocol/OqcTestSample.java).


[한국어](../ko/extended-protocol.md) · [Index](../../README.md)
