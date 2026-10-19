# Stock-link research notes

This is an implementation-oriented reconstruction of the original notes, reconciled with the released Java code. It separates the phone link from internal UART and firmware work. Packet examples in `protocol/` are synthetic, with no device owner identifiers.

| Date / original note | Analysis and result | Current reference |
|---|---|---|
| 2026-08-30 official-app-static-audit, PROTOCOL, DOSSIER-ERRATA | Official Android app 2.1.14/code 579: SPP bootstrap, sequence/command serializers, commands, content conversion; corrected request attributes and checksum vectors | [Framing](../../docs/en/framing.md), [Commands](../../docs/en/commands.md), [Corrections](corrections.md) |
| 2026-08-31 opennoodoe-live-validation | Stock AK550 5.16 / resources 5.14: device info, riding, production reads, mobile time, notification and ignition-dependent welcome light responses | [Stock session](stock-session.md) |
| 2026-09-01 official-app-runtime | Runtime creation compilation/transmission artifacts; distinction between source appData and compiled CFG/image bundles | [Themes](../../docs/en/themes.md) |
| 2026-09-02 field-gallery-theme-media | Slot suffix and gallery identity, 11816-byte chunks, delayed task DONE; background-only theme classification failure | [Content findings](content-findings.md) |
| 2026-09-02 measured-transfer-state-machine | Command/task correlation, progress accounting, transfer stages and logging limits | [File transfer](../../docs/en/file-transfer.md) |
| 2026-09-03 s24-v061-live-validation | Persistent navigation DATA task; radar renderer recognition; POI marker interpretation remained partial | [Extended protocol](../../docs/en/extended-protocol.md) |
| 2026-09 product scope / subsequent source | Committed gallery storage, immediate key events plus serialized status reads, theme import policy | [Architecture](../../docs/en/architecture.md) |

## What the research does and does not contain

The phone side was implemented against **stock firmware**; it was not bypassed using a custom firmware-only app link. The internal `F5` UART research concerns cluster↔module communication. The stock phone path has its own `5A FF` sequence / `A5 5A` command layers.

Available findings include handshake, command serializers/parsers, file transaction state, CRC/MD5 identities, photo conversion, compiled widget schemas, runtime logs and selected live responses. APK BLE UUID strings are catalogued, but no nRF Connect GATT dump establishing the active stock data service was located. Preserved Android btsnooz extracts contain connection/HCI events rather than a complete RFCOMM photo/theme payload trace. Application protocol logs provided the payload evidence; some large-frame entries contain only a prefix and CRC.

Navigation transport/session behavior was studied, but this repository does not ship route generation or a navigation product. POI marker identity/coordinates were not fully resolved. C6 battery contents and stop-duration units remain undecoded. Legacy SR1.0 and a general stock GATT client are not implemented. The separate firmware/flash/filesystem/boot-chain research belongs to [noodoe_cfw](https://github.com/SerialSniffyHeck3r/noodoe_cfw), not this Android source release.

[한국어](../ko/README.md)
