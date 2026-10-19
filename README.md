# OpenNoodoe

[한국어](README.ko.md) · [Download APK](https://github.com/SerialSniffyHeck3r/OpenNoodoe/releases/latest) · [MIT license](LICENSE)

An Android companion for a stock KYMCO Noodoe dashboard, developed by **Veritas Studio / SerialSniffyHeck3r**. Connect directly over Bluetooth without a Noodoe account or server. No firmware replacement is involved.

- Six photo slots: crop, upload and replace existing photos.
- Import compiled clock and speedometer theme ZIPs; customize supported properties and backgrounds.
- Vehicle settings, phone/manual time, welcome light, system/production information and read-only odometer.
- Live speed and ignition state. English, Korean, Japanese, Simplified and Traditional Chinese UI.

Version **0.10.1 (37)** restores the OpenNoodoe name from ReNudo, including internal branding and build settings. Package ID remains `io.opennoodoe.app`. No official themes, extracted artwork, firmware or personal themes are bundled. Navigation, weather and notification protocols are documented as research; they are outside this app's command policy.

## Start here

1. Install the signed APK from Releases and pair the dashboard in Android Bluetooth settings.
2. Select the paired device in OpenNoodoe and connect.
3. Use the photo slots or import a compiled theme ZIP. Source Creation/appData archives must first be compiled into transmission files.

## Developer reference

The stock AK550 link implemented here is **Bluetooth Classic RFCOMM/SPP**, not BLE GATT. Its `5A FF` / `A5 5A` frames are different from the internal vehicle UART's `F5` frames.

| Topic | Document |
|---|---|
| Build, installation and signing | [Quickstart](docs/en/quickstart.md) |
| Discovery, pairing, raw bootstrap, BLE findings | [Connection](docs/en/connection.md) |
| Frames, ACKs, checksums, indexes | [Framing](docs/en/framing.md) |
| Command IDs, offsets, time, preferences, ODO, events | [Commands](docs/en/commands.md) |
| BEGIN, control, chunks, CRC32, replies, DONE | [File transfer](docs/en/file-transfer.md) |
| JPEG slot byte, replacement, local persistence | [Gallery](docs/en/gallery.md) |
| CFG, assets, hashes, ZIP import and authoring | [Themes](docs/en/themes.md) |
| Navigation/weather/POI/button reference | [Extended protocol](docs/en/extended-protocol.md) |
| Threads, product boundary and source map | [Architecture](docs/en/architecture.md) |
| Symptoms and diagnostic steps | [Troubleshooting](docs/en/troubleshooting.md) |
| Research inventory, chronology and corrected interpretations | [Research](research/en/README.md) |

[Runnable tools and Java cross-check](examples/README.md) · [Synthetic vectors](protocol/test-vectors/stock.json) · [Synthetic exchange](protocol/traces/device-info.json) · [Privacy](docs/en/privacy.md) · [Changelog](CHANGELOG.md)

## Repository boundaries

`android/` contains the stock companion source. `docs/`, `research/`, `examples/` and `protocol/` provide the implementation reference. CFW/NewDoe has a separate source tree and release line; its installer and firmware are not part of OpenNoodoe.

MIT permits modification, redistribution and commercial use while requiring preservation of the copyright and permission notice. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Support: **shin6361@gmail.com**.
