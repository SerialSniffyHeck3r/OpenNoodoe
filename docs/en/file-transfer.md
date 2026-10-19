# File transfer

Sequence: `0A BEGIN → [0B UPDATE → 0D chunks → 0B UPDATE_TERMINATE] for each file → 0A DONE`. Use FILE type 2 and the target location. Keep task ID stable through the transaction; transfer ID identifies each file, starting at 1 in `transferFiles`. Control commands use outer session 0; file data uses session 1.

Total size is the sum of original file lengths. `0A BEGIN/DONE` wait up to 20 seconds; file control/data wait up to 6 seconds, separately from sequence ACK timeouts. A delayed DONE reply must not satisfy a new task: correlate command ID, task ID and, for 0B/0D, transfer ID. Status 21 on UPDATE means file exists: this client skips its data and terminate, then proceeds to final task DONE.

Current chunks are **11816 bytes**, with nonfinal chunks aligned to 4 bytes. The framing arithmetic allows 11818 data bytes (`11834−10−6`), but that is not the chosen application chunk size. Do not confuse either value with an ATT MTU.

CRC is standard reflected CRC-32/ISO-HDLC (`java.util.zip.CRC32`, polynomial 0xEDB88320, initial/final XOR FFFFFFFF), calculated over the file followed by zero bytes until length is divisible by 4. Only CRC calculation includes padding; transmitted bytes and declared size remain unchanged. MD5 is over the original exact file bytes and is sent as 16 raw digest bytes, not 32 ASCII hex characters. See runnable vectors for aligned and unaligned inputs.

Transport ACK, command success, task DONE and successful widget rendering are distinct. Logs expose BEGIN/FILE/COMMIT and the replies so a developer can locate the failing stage. On failure the client attempts task cleanup; never infer that the last timed-out command was not received.

## 0A negotiation — 27 bytes

| Offset | Type | Field |
|---:|---|---|
| 0 / 2 / 4 | u16 each | task ID / type (DATA=1, FILE=2) / location |
| 6 | u32 | total size |
| 10 | u8 | BEGIN=1, CONTINUE=2, DONE=3, REMOVE=4, RESET=5, CANCEL=6 |
| 11 | bytes[16] | content ID |

Locations: clock `0200`, weather `0300`, speedometer `0400`, POI `0500`, gallery `0600`, group `0700` (hex). App permits clock, speedometer and gallery FILE tasks only.

## 0B control — 32 + UTF-8 path length

| Offset | Type | Field |
|---:|---|---|
| 0 / 2 / 4 | u16 each | task / operation / transfer ID |
| 6 | bytes[16] | MD5 identity, or file ID u16 + 14 zeros in DATA tasks |
| 22 / 26 | u32 each | padded CRC32 / original file size |
| 30 | u16 | UTF-8 target path byte count |
| 32 | bytes[N] | target path; no terminator |

Operations: UPDATE=1, UPDATE_TERMINATE=2, DELETE=3.

## 0D data / reply

| Direction | Offset | Type | Field |
|---|---:|---|---|
| TX | 0 / 2 / 4 | u16 each | task / transfer ID / constant 1 |
| TX | 6 | bytes[N] | file slice; no separate offset field |
| RX | 0 / 2 / 4 | u16 each | status / task / transfer ID |
| RX | 6 | 2 bytes | reserved |
| RX | 8 / 12 | u32 each | chunk/progress word / cumulative bytes |

FILE responses are checked against sent chunk length and expected cumulative progress. Some historical DATA tasks use the first progress word differently; cumulative progress remains the relevant count.

FILE 응답은 청크 길이·누적 진행량을 대조합니다. 과거 DATA 작업에서는 첫 progress word의 의미가 다를 수 있으므로 누적 진행량과 구분합니다.

| Status (decimal) | Meaning |
|---:|---|
| 0 | success |
| 1 / 2 / 3 | no memory / not found / not supported |
| 5 / 6 / 8 | invalid state / invalid length / invalid data |
| 10 / 13 / 14 | timeout / busy / device error |
| 15 / 16 | delete error / cancelled |
| 21 / 23 / 24 / 25 | exists / invalid ID / corrupt / moving |
| 255 | unknown |

Source: [`FileTransferPayloads`](../../android/app/src/main/java/io/opennoodoe/app/protocol/FileTransferPayloads.java), `transferFiles`, `transferOne`, `sendAndAwait` in [`NoodoeService`](../../android/app/src/main/java/io/opennoodoe/app/NoodoeService.java).


[한국어](../ko/file-transfer.md) · [Index](../../README.md)
