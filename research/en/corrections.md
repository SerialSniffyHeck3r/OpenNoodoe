# Corrections that affect implementations

1. DEVICE_INFO READ attribute is 01, not 09; MOBILE_STATUS WRITE is 02, not 0A. Recompute checksums after fixing attributes.
2. Raw `05 00 00 00 00` bootstrap is used by the official connection path too. It is not only a dealer diagnostic.
3. Raw bootstrap body and framed DEVICE_INFO have different offsets. The framed payload is 82 bytes; historical descriptions counting some command header bytes call it 90. This repository always labels the offset origin.
4. A dealer app failed at `BluetoothSocket.connect()`. That attempt cannot prove a later device-info parser incompatibility.
5. The official/dealer SPP handlers both configured zero immediate connection retries. Service lifecycle behavior explains resilience differences; do not conflate it with packet ACK retries in this client.
6. MD5 filenames are the compiler convention, with two exceptions in historical packaged defaults. This app's import path intentionally requires exact content hashes; do not rename a mismatched file without rewriting CFG references.
7. Gallery JPEG includes the final slot byte in MD5 and CRC. It is not raw RGB565 data.
8. Temperature fields are signed Fahrenheit. C2 is a motion event, not speed. C3 uses 0=OFF, 1=ON. OQC stored firmware fields are not necessarily the running firmware version.
9. Maximum data size derived from framing (11818) differs from this client's chosen aligned chunk (11816). Task DONE can be slower than individual packet ACKs.
10. A background-only CFG can transfer successfully without being classified as a clock. Navigation DATA sessions must persist across updates.

[Research index](README.md) · [한국어](../ko/corrections.md)
