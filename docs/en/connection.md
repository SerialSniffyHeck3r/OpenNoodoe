# Stock connection

The implemented path is stock AK550 Noodoe firmware 5.16 / resources 5.14 → Bluetooth Classic → secure RFCOMM/SPP → raw bootstrap → SR1.5 frames. The SPP UUID is `00001101-0000-1000-8000-00805F9B34FB`. The internal cluster UART is a separate link.

The name normally follows `KYMCO Noodoe` plus a device suffix. Selection uses the paired device address, not an iBeacon identifier. The client requires `BOND_BONDED`, cancels discovery, opens `createRfcommSocketToServiceRecord(SPP_UUID)` and calls `connect()` on a worker. Pair through Android's system dialog; the app does not implement passkey handling or assume a universal PIN.

There is no BLE MTU negotiation in this path. RFCOMM is a byte stream: one read can contain a partial frame or multiple frames. No Noodoe-account token or cloud challenge is sent by this implementation.

## Handshake

1. Connect the secure RFCOMM socket to the selected paired address.
2. Send exactly `05 00 00 00 00` without a sequence wrapper.
3. Read the five-byte header `85 <length:u32le>` and exactly `length` further bytes. The client skips the first payload byte; the rest is the raw device-information body.
4. Parse firmware, protocol and hardware. `supportsFramedProtocol()` accepts protocol major ≥1, or major 0 with nonzero hardware / firmware major ≥4. Legacy SR1.0 commands are not implemented.
5. Start the sequence reader and issue framed `DEVICE_INFO` (`05 READ`). Subsequent clock, status and settings operations use the serialized command queue.

Raw-body offsets: firmware major/minor 0/2, packed protocol 4, hardware 5, reversed MAC 6; optional boot/resource versions 12/14/16/18, language bytes 20. The framed response's two-byte status prefix is absent here.

## BLE findings

Static APK analysis found the UUIDs below. These are library candidates, not a GATT dump of the stock AK550. Live characteristic properties, Write Without Response, pairing mode and negotiated ATT MTU are not established by these strings. The working Android implementation uses SPP.

| Library symbol | UUID |
|---|---|
| SPPLE service | `14839ac4-7d7e-415c-9a42-167340cf2339` |
| Output | `8b00ace7-eb0b-49b0-bbe9-9aee0a26e1a3` |
| Input | `0734594a-a8e7-4b1a-a6b1-cd5243059a57` |
| InputCredit | `ba04c4b2-892b-43be-b69c-5d13f2195392` |
| OutputCredit | `e06d5efb-4f4a-45c0-9eb1-371ae5a14ad4` |

Source: [`NoodoeService.java`](../../android/app/src/main/java/io/opennoodoe/app/NoodoeService.java), [`DeviceInfo.java`](../../android/app/src/main/java/io/opennoodoe/app/protocol/DeviceInfo.java).


[한국어](../ko/connection.md) · [Index](../../README.md)
