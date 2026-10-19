# Troubleshooting

| Symptom | Check |
|---|---|
| Device missing | Pair in Android settings; grant Bluetooth/Nearby devices; select the paired address. BLE scanner discovery is not the SPP device selector. |
| Socket connect fails | Close other phone/official-app sessions. Confirm bond and selected address. Distinguish socket failure from raw-bootstrap parse failure. |
| Socket open, no framed reply | Capture the raw 05 request and 85 response first. Do not wrap bootstrap in 5A FF. Check SR1.5 detection. |
| All commands rejected | Check READ=01/WRITE=02, little-endian payload length and payload-only additive checksum. |
| ACK received, operation times out | Outer ACK is not the command reply. Check command/task/transfer IDs and delayed replies. |
| Speed freezes or ON/OFF persists | Inspect 0C replies and C3 events. Values older than 5 seconds should disappear. Transfer ownership pauses polling. |
| Welcome light status 5 | Check ignition: 5.16 rejects this command in the ON state. |
| Existing photo fails to replace | Check trailing slot byte, new MD5, ordered gallery identity and committed-vs-draft local payloads. |
| ZIP rejected | Require one MD5-named CFG, declared MD5-named images, no duplicate basenames, valid widget type. Creation/appData ZIP is a different format. |
| Theme transferred but no clock | A background-only CFG is not a clock; inspect ClockDigitWidget/WatchHandWidget and references. |
| 0B status 21 | Existing file; skip data/terminate but still finish the task with DONE. |
| APK update rejected | Package/certificate mismatch; compare certificate, not the displayed app name. |

Useful log sequence: raw bootstrap → DEVICE_INFO → TX/RX sequence indexes → command/task/transfer ID → TRANSFER_MANIFEST/TRANSFER_FILE_ORDER → per-file progress → DONE. Large-frame logs may contain only a prefix and CRC, not the full payload. Do not label them raw HCI captures.

For an issue, include app version, phone/Android version, dashboard firmware/resource versions, command/status and the surrounding transaction. Replace MAC, serial, PIN, addresses and personal filenames with placeholders before publishing logs.


[한국어](../ko/troubleshooting.md) · [Index](../../README.md)
